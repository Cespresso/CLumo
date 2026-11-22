package io.github.cespresso.clumo.ui.components.product

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import io.github.cespresso.clumo.domain.Brightness
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.Node
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.safeDestroyTexture
import java.nio.ByteBuffer

/** A single horizontal decal. Text, controls and hit areas all share its world transform. */
internal class FloorSettingsPanel(private val engine: Engine, private val materials: MaterialLoader, loader: ModelLoader) {
    private val texture = Texture.Builder().width(1024).height(2048).levels(1)
        .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.SRGB8_A8).build(engine)
    private val material = materials.createMaterial("materials/floor_settings.filamat")
    private val instance = materials.createInstance(material).apply {
        setParameter("image", texture, TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE))
        setParameter("reveal", 0f)
        setParameter("minU", 0f)
        setParameter("maxU", 1f)
    }
    val node = PlaneNode(engine, size = Size(WIDTH, 0f, DEPTH), materialInstance = instance, builderApply = {
        castShadows(false)
        receiveShadows(false)
    }).apply { name = "floor-part-settings" }
    private val ribbon = FloorRibbon(engine, materials)
    private val solids = SolidFloorControls(loader)
    private val iconInstance = materials.createInstance(material).apply {
        setParameter("image", texture, TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE))
        setParameter("reveal", 0f)
        setParameter("minU", 0f)
        setParameter("maxU", ICON_CUT_U)
    }
    private val iconNode = PlaneNode(engine, size = Size(WIDTH, 0f, DEPTH), materialInstance = iconInstance, builderApply = {
        castShadows(false)
        receiveShadows(false)
    }).apply { name = "floor-control-icons" }
    private val assignment = FloorAssignmentPanel(engine, materials, loader)
    val assignmentProgress get() = assignment.progress
    private var choosing = false
    private var parentSnapshot: FloorSettingsSnapshot? = null
    val nodes get() = listOf(node, ribbon.node, solids.node, iconNode) + assignment.nodes
    fun setRibbonColor(rgb: Int) {
        ribbon.setColor(rgb)
        assignment.setColor(rgb)
    }
    fun owns(candidate: Node) = if (choosing) assignment.owns(candidate) else candidate == node || candidate == iconNode
    private var grouped = false
    private var galleryX = 0f
    private val bitmap = createBitmap(1024, 2048)
    private var snapshot: FloorSettingsSnapshot? = null
    private data class Target(val id: String, val rect: RectF)
    private val targets = mutableListOf<Target>()
    private val reveal = ReversibleReveal(.62f)
    private val progress get() = reveal.progress
    private var closeToken: Long? = null

    // Keep the ribbon sweep, but do not make controls wait for its 620 ms reveal.
    private val controlsProgress get() = (progress * (.62f / .12f)).coerceAtMost(1f)
    private var lastFrame = 0L
    private var visible = false
    private var state: FloorSettingsState? = null

    fun update(value: FloorSettingsState?, x: Float, show: Boolean, description: FloorSettingsSnapshot?, closing: Long? = null) {
        state = value
        closeToken = closing
        visible = show && value != null
        galleryX = x
        choosing = description?.choosing == true
        assignment.update(value, x, visible && choosing, description)
        if (!choosing) parentSnapshot = description
        val parent = if (choosing) parentSnapshot else description
        grouped = parent?.part == StandPart.Knob
        node.position = Position(x, if (grouped) SolidFloorControls.LABEL_Y else -.5003f, CENTER_Z)
        iconNode.position = Position(x, SolidFloorControls.ICON_Y, CENTER_Z)
        iconNode.isVisible = visible && grouped
        iconNode.isTouchable = visible && grouped && closing == null
        instance.setParameter("minU", if (grouped) ICON_CUT_U else 0f)
        for (material in listOf(instance, iconInstance)) {
            material.setParameter("grouped", if (grouped) 1f else 0f)
            val thresholds = FloorControlRows.thresholds
            material.setParameter("rowThresholds", thresholds[0], thresholds[1], thresholds[2], thresholds[3])
            fun cut(row: Int) = ((FloorControlRows.z(row) + FloorControlRows.z(row + 1)) / 2f - CENTER_Z) / DEPTH + .5f
            material.setParameter("rowCuts", cut(0), cut(1), cut(2))
        }
        solids.update(x, visible && grouped, progress)
        node.isVisible = visible
        node.isTouchable = visible && closing == null
        ribbon.update(x, visible, progress)
        val next = parent ?: return
        if (next != snapshot) {
            if (next.part != snapshot?.part || next.choosing != snapshot?.choosing) {
                reveal.reset()
                instance.setParameter("reveal", 0f)
                ribbon.reveal(0f)
                iconInstance.setParameter("reveal", 0f)
                solids.update(x, false)
            }
            snapshot = next
            draw(next)
        }
    }

    fun onFrame(time: Long) {
        assignment.onFrame(time)
        val dt = if (lastFrame == 0L) 0f else ((time - lastFrame) / 1_000_000_000f).coerceIn(0f, .05f)
        lastFrame = time
        val closing = closeToken
        if (visible && (progress < 1f || closing != null)) {
            reveal.advance(dt, opening = closing == null)
            instance.setParameter("reveal", if (grouped) progress else controlsProgress)
            ribbon.reveal(progress)
            iconInstance.setParameter("reveal", if (grouped) progress else controlsProgress)
            solids.update(galleryX, visible && grouped, progress)
            if (closing != null && progress == 0f) {
                // Hold the closed target until Compose applies the completed navigation.
                state?.finishClose(closing)
            }
        }
    }

    fun isBrightness(world: Position): Boolean {
        val local = node.getLocalPosition(world)
        val x = ((local.x - UI_CENTER_X) / UI_WIDTH + .5f) * 1024f
        val y = ((local.z + CENTER_Z - UI_CENTER_Z) / UI_DEPTH + .5f) * 1024f
        return !choosing && visible && closeToken == null && controlsProgress >= .95f && targets.any { it.id == "brightness" && it.rect.contains(x, y) }
    }

    fun hit(world: Position): Boolean {
        if (choosing) return assignment.hit(world)
        if (!visible || closeToken != null || controlsProgress < .95f) return false
        val local = node.getLocalPosition(world)
        val x = ((local.x - UI_CENTER_X) / UI_WIDTH + .5f) * 1024f
        val y = ((local.z + CENTER_Z - UI_CENTER_Z) / UI_DEPTH + .5f) * 1024f
        val target = targets.firstOrNull { it.rect.contains(x, y) } ?: return false
        if (grouped) {
            val row = when (target.id) {
                "binding:5" -> 0
                "binding:2" -> 1
                "binding:3" -> 2
                else -> 3
            }
            if (progress < FloorControlRows.thresholds[row]) return false
        }
        val value = if (target.id == "brightness") ((x - 170f) / 650f * 100f).coerceIn(0f, 100f) else null
        state?.onAction?.invoke(target.id, value)
        return true
    }

    /** Coordinates are also used by the device test to tap projected controls. */
    fun worldPoint(x: Float, y: Float): Position = node.getWorldPosition(controlPosition(x, y))

    private fun draw(s: FloorSettingsSnapshot) {
        bitmap.eraseColor(Color.TRANSPARENT)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val ink = Color.rgb(49, 51, 46)
        val muted = Color.rgb(112, 111, 102)
        val orange = Color.rgb(235, 92, 45)
        // Menu rows and the foreground ribbon both run along the floor's Z axis.
        // Drawing and hit testing use the same transform, without mirroring text.
        c.translate((.5f + (UI_CENTER_X - UI_WIDTH / 2f) / WIDTH) * 1024f, ((UI_CENTER_Z - UI_DEPTH / 2f - CENTER_Z) / DEPTH + .5f) * 2048f)
        c.scale(UI_WIDTH / WIDTH, UI_DEPTH / DEPTH * 2f)
        targets.clear()
        fun text(value: String, x: Float, y: Float, size: Float, color: Int = ink, bold: Boolean = false) {
            p.style = Paint.Style.FILL
            p.color = color
            p.textSize = size
            p.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            c.withTranslation(x, y) {
                c.scale(UI_DEPTH / UI_WIDTH, 1f)
                c.drawText(value, 0f, 0f, p)
            }
        }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = width
            p.strokeCap = Paint.Cap.ROUND
            p.color = color
            c.drawLine(x1, y1, x2, y2, p)
        }
        val wiring = !s.choosing && s.part == StandPart.Knob
        if (wiring) {
            fun px(x: Float) = ((x - UI_CENTER_X) / UI_WIDTH + .5f) * 1024f
            fun py(z: Float) = ((z - UI_CENTER_Z) / UI_DEPTH + .5f) * 1024f
            fun icon(value: String, x: Float, y: Float, rotate: Boolean) {
                val color = if (s.enabled) ink else muted
                p.color = color
                p.style = Paint.Style.FILL
                c.withTranslation(x, y) {
                    c.scale(UI_DEPTH / UI_WIDTH, 1f)
                    c.translate(-x, -y)
                    when {
                        rotate -> {
                            p.style = Paint.Style.STROKE
                            p.strokeWidth = 5f
                            c.drawArc(RectF(x - 32f, y - 27f, x + 32f, y + 27f), 35f, 285f, false, p)
                            line(x + 27f, y + 18f, x + 12f, y + 22f, color, 5f)
                            line(x + 27f, y + 18f, x + 26f, y + 5f, color, 5f)
                        }
                        value == "Play / Pause" -> {
                            c.drawPath(
                                Path().apply {
                                    moveTo(x - 30f, y - 20f)
                                    lineTo(x - 2f, y)
                                    lineTo(x - 30f, y + 20f)
                                    close()
                                },
                                p,
                            )
                            c.drawRect(x + 7f, y - 20f, x + 15f, y + 20f, p)
                            c.drawRect(x + 23f, y - 20f, x + 31f, y + 20f, p)
                        }
                        value == "Previous track" || value == "Next track" -> {
                            val d = if (value == "Previous track") -1f else 1f
                            c.drawPath(
                                Path().apply {
                                    moveTo(x + d * 20f, y)
                                    lineTo(x - d * 15f, y - 20f)
                                    lineTo(x - d * 15f, y + 20f)
                                    close()
                                },
                                p,
                            )
                            c.drawRect(x + d * 29f - 4f, y - 20f, x + d * 29f + 4f, y + 20f, p)
                        }
                        value == "Mute" -> {
                            c.drawPath(
                                Path().apply {
                                    moveTo(x - 31f, y - 10f)
                                    lineTo(x - 17f, y - 10f)
                                    lineTo(x, y - 23f)
                                    lineTo(x, y + 23f)
                                    lineTo(x - 17f, y + 10f)
                                    lineTo(x - 31f, y + 10f)
                                    close()
                                },
                                p,
                            )
                            line(x + 11f, y - 12f, x + 32f, y + 12f, color, 4f)
                            line(x + 32f, y - 12f, x + 11f, y + 12f, color, 4f)
                        }
                        value == "Voice assistant" -> {
                            p.style = Paint.Style.STROKE
                            p.strokeWidth = 4f
                            c.drawRoundRect(RectF(x - 10f, y - 24f, x + 10f, y + 8f), 10f, 10f, p)
                            c.drawArc(RectF(x - 20f, y - 13f, x + 20f, y + 18f), 0f, 180f, false, p)
                            line(x, y + 18f, x, y + 29f, color, 4f)
                            line(x - 12f, y + 29f, x + 12f, y + 29f, color, 4f)
                        }
                        else -> {
                            p.style = Paint.Style.STROKE
                            p.strokeWidth = 4f
                            c.drawCircle(x, y, 23f, p)
                            if (value == "None") line(x - 17f, y + 17f, x + 17f, y - 17f, color, 3f)
                        }
                    }
                }
            }
            text("Knob", px(-.042f / .244f), py(.109f / .244f), 44f)
            if (s.busy || !s.enabled) {
                text(s.status, px(-.042f / .244f), py(.119f / .244f), 24f, muted)
                if (!s.busy) targets += Target("retry", RectF(px(-.2f), py(.44f), px(.28f), py(.51f)))
            }
            val ordered = s.items.sortedBy { if (it.id == "binding:5") -1 else it.id.substringAfter(':').toIntOrNull() ?: 0 }
            ordered.forEachIndexed { i, item ->
                val z = FloorControlRows.z(i)
                val y = py(z)
                icon(item.value, px(-.3f), y, item.id == "binding:5")
                text(item.title, px(-.045f / .244f), py(z - .006f / .244f), 28f, muted)
                val size = if (item.value.length > 19) 27f else 40f
                text(item.value, px(-.045f / .244f), py(z + .013f / .244f), size, if (s.enabled) ink else muted)
                if (s.enabled) targets += Target(item.id, RectF(px(-.0932f / .244f), py(z - .0215f / .244f), px(.0668f / .244f), py(z + .0215f / .244f)))
            }
        } else {
            text(s.title, 92f, 140f, 52f, bold = true)
            if (s.busy || !s.enabled) text(s.status, 94f, 190f, 27f, if (s.busy) orange else muted)
            if (!s.enabled && !s.busy) targets += Target("retry", RectF(70f, 150f, 850f, 212f))
            if (s.part == null) {
                text("LED面 / 左ボタン / 右ボタン / ノブ", 94f, 330f, 35f)
                text("選んだ部品の設定がここに現れます", 94f, 400f, 32f, muted)
            }
            val count = s.items.size
            val rowHeight = if (count > 4) 72f else 136f
            s.items.forEachIndexed { i, item ->
                val y = 275f + i * rowHeight
                val color = if (!s.enabled) {
                    muted
                } else if (item.selected) {
                    orange
                } else {
                    ink
                }
                text("%02d".format(i + 1), 96f, y + 6f, 24f, muted)
                if (s.choosing) {
                    text(if (item.selected) "●  ${item.title}" else item.title, 165f, y + 8f, 36f, color, item.selected)
                } else {
                    text(item.title, 165f, y + 4f, 37f, color, true)
                    text(item.value, 165f, y + 52f, 32f, color)
                }
                line(165f, y + rowHeight - 31f, 820f, y + rowHeight - 31f, Color.argb(90, 90, 90, 80), 2f)
                text("›", 790f, y + 12f, 40f, color)
                if (s.enabled) targets += Target(item.id, RectF(90f, y - 28f, 956f, y + rowHeight - 20f))
            }
        }
        if (s.brightness != null) {
            text("Brightness", 165f, 540f, 36f, bold = true)
            text("${Brightness.toLevel(s.brightness) + 1} / ${Brightness.MAX_LEVEL + 1}", 165f, 650f, 88f, if (s.enabled) ink else muted, true)
            for (i in 0..10) line(170f + 65f * i, 770f, 170f + 65f * i, if (i % 5 == 0) 800f else 786f, muted, 2f)
            line(170f, 730f, 820f, 730f, muted, 10f)
            line(170f, 730f, 170f + 650f * s.brightness.coerceIn(0f, 100f) / 100f, 730f, orange, 12f)
            p.style = Paint.Style.FILL
            p.color = if (s.enabled) orange else muted
            c.drawCircle(170f + 650f * s.brightness / 100f, 730f, 24f, p)
            if (s.enabled) targets += Target("brightness", RectF(110f, 675f, 860f, 817f))
        }
        if (!wiring) {
            text(if (s.choosing) "‹ Back" else "× Close", 100f, 970f, 39f)
            if (s.part != null) targets += Target("close", RectF(75f, 908f, 410f, 1015f))
        }
        if (s.part != null && s.part != StandPart.Matrix && !s.choosing) {
            text("Reset defaults", if (wiring) 80f else 450f, if (wiring) 1120f else 970f, 22f, muted)
            if (s.enabled) targets += Target("reset", if (wiring) RectF(60f, 1060f, 500f, 1180f) else RectF(440f, 908f, 950f, 1015f))
        }
        val pixels = ByteBuffer.allocateDirect(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(pixels)
        pixels.flip()
        texture.setImage(engine, 0, Texture.PixelBufferDescriptor(pixels, Texture.Format.RGBA, Texture.Type.UBYTE))
    }

    fun destroy() {
        assignment.destroy()
        solids.destroy()
        iconNode.destroy()
        materials.destroyMaterialInstance(iconInstance)
        ribbon.destroy()
        node.destroy()
        materials.destroyMaterialInstance(instance)
        materials.destroyMaterial(material)
        engine.safeDestroyTexture(texture)
        bitmap.recycle()
    }

    companion object {
        fun controlPosition(x: Float, y: Float) =
            Position(
                UI_CENTER_X + (x / 1024f - .5f) * UI_WIDTH,
                0f,
                UI_CENTER_Z - CENTER_Z + (y / 1024f - .5f) * UI_DEPTH,
            )

        private const val ICON_CUT_U = .5f - .23f / 1.3f
        const val WIDTH = 1.3f
        const val DEPTH = 3.5f
        private const val CENTER_Z = .8f
        private const val UI_CENTER_X = .12f
        private const val UI_WIDTH = .62f
        private const val UI_DEPTH = 1.06f
        private const val UI_CENTER_Z = .88f
    }
}
