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
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.safeDestroyTexture
import java.nio.ByteBuffer

/** The assignment destination lives beside its parent in the same SceneView. */
internal class FloorAssignmentPanel(private val engine: Engine, private val materials: MaterialLoader, loader: ModelLoader) {
    private val solid = ModelNode(loader.createInstancedModel("models/floor-assignment.glb", 1).single(), autoAnimate = false, scaleToUnits = null, centerOrigin = null).apply {
        scale = Scale(1f / .244f)
        isTouchable = false
        renderableNodes.forEach { it.isTouchable = false }
    }
    private val ribbon = FloorRibbon(engine, materials, branch = true)
    private val reveal = ReversibleReveal(AssignmentMotion.DURATION)
    val progress get() = reveal.progress
    private var origin = 0f
    private var galleryX = 0f
    private var lastFrame = 0L
    fun setColor(rgb: Int) = ribbon.setColor(rgb)
    private val texture = Texture.Builder().width(1024).height(2048).levels(1).sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.SRGB8_A8).build(engine)
    private val material = materials.createMaterial("materials/floor_assignment.filamat")
    private fun instance(min: Float, max: Float) =
        materials.createInstance(material).apply {
            setParameter("image", texture, TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE))
            setParameter("minU", min)
            setParameter("maxU", max)
            setParameter("reveal", 1f)
            setParameter("firstThreshold", AssignmentLayout.rowThreshold(0))
            setParameter("stepThreshold", AssignmentLayout.rowThreshold(1) - AssignmentLayout.rowThreshold(0))
            setParameter("firstRow", (AssignmentLayout.FIRST_Z - CENTER_Z) / DEPTH + .5f)
            setParameter("rowStep", AssignmentLayout.ROW_STEP / DEPTH)
        }
    private val labelsMaterial = instance(CUT, 1f)
    private val iconsMaterial = instance(0f, CUT)
    private val labels = PlaneNode(engine, size = Size(WIDTH, 0f, DEPTH), materialInstance = labelsMaterial, builderApply = {
        castShadows(false)
        receiveShadows(false)
    })
    private val icons = PlaneNode(engine, size = Size(WIDTH, 0f, DEPTH), materialInstance = iconsMaterial, builderApply = {
        castShadows(false)
        receiveShadows(false)
    })
    val nodes: List<Node> = listOf(solid, labels, icons, ribbon.node)
    private val bitmap = createBitmap(1024, 2048)
    private var applied: FloorSettingsSnapshot? = null
    private var state: FloorSettingsState? = null
    private var shown = false
    fun owns(node: Node) = shown && (node == labels || node == icons)

    fun update(value: FloorSettingsState?, x: Float, show: Boolean, snapshot: FloorSettingsSnapshot?) {
        if (show && (!shown || snapshot?.bindingIndex != applied?.bindingIndex)) reveal.reset()
        state = value
        shown = show
        galleryX = x
        origin = AssignmentLayout.sourceOffset(snapshot?.bindingIndex)
        solid.position = Position(x, -.502f, origin)
        solid.isVisible = show
        labels.position = Position(x + AssignmentLayout.CENTER_X, SolidFloorControls.LABEL_Y, CENTER_Z + origin)
        icons.position = Position(x + AssignmentLayout.CENTER_X, SolidFloorControls.ICON_Y, CENTER_Z + origin)
        labels.isVisible = show
        icons.isVisible = show
        labels.isTouchable = show
        icons.isTouchable = show
        if (show && snapshot != null && snapshot != applied) {
            draw(snapshot)
            applied = snapshot
        }
        applyReveal()
    }

    private fun applyReveal() {
        val keys = listOf("play", "next", "previous", "mute", "voice", "none", "forward", "rewind")
        solid.renderableNodes.forEach { node ->
            val name = node.name.orEmpty()
            val row = keys.indexOfFirst { name.startsWith("choice_plate_" + it) || name.startsWith("choice_icon_" + it) || name.startsWith("choice_" + it + "_") }
            node.isVisible = shown && row in 0 until (applied?.items?.size ?: 0) && reveal.progress >= AssignmentLayout.rowThreshold(row)
        }
        labelsMaterial.setParameter("reveal", reveal.progress)
        iconsMaterial.setParameter("reveal", reveal.progress)
        ribbon.update(galleryX, shown, reveal.progress, origin)
    }

    fun onFrame(time: Long) {
        val dt = if (lastFrame == 0L) 0f else ((time - lastFrame) / 1_000_000_000f).coerceIn(0f, .05f)
        lastFrame = time
        if (!shown) return
        val closing = state?.assignmentCloseToken
        val previous = reveal.progress
        reveal.advance(dt, opening = closing == null)
        if (previous != reveal.progress) applyReveal()
        if (closing != null && reveal.progress == 0f) state?.finishAssignmentClose(closing)
    }

    fun hit(world: Position): Boolean {
        if (!shown || state?.assignmentCloseToken != null) return false
        val local = labels.getLocalPosition(world)
        val x = local.x + AssignmentLayout.CENTER_X
        val z = local.z + CENTER_Z
        if (x !in (.152f / .244f)..(.312f / .244f)) return false
        if (z in 1.04f..1.17f && reveal.progress > .1f) {
            state?.onAction?.invoke("close", null)
            return true
        }
        val row = applied?.items?.indices?.firstOrNull { kotlin.math.abs(z - AssignmentLayout.rowZ(it)) <= AssignmentLayout.HALF_ROW } ?: return false
        if (reveal.progress < AssignmentLayout.rowThreshold(row)) return false
        if (applied?.enabled == true) state?.onAction?.invoke(applied!!.items[row].id, null)
        return true
    }

    private fun draw(snapshot: FloorSettingsSnapshot) {
        bitmap.eraseColor(Color.TRANSPARENT)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val ink = Color.rgb(49, 51, 46)
        val muted = Color.rgb(112, 111, 102)
        fun px(x: Float) = ((x - AssignmentLayout.CENTER_X) / WIDTH + .5f) * 1024f
        fun py(z: Float) = ((z - CENTER_Z) / DEPTH + .5f) * 2048f
        fun text(body: String, x: Float, y: Float, size: Float, color: Int = ink) {
            p.style = Paint.Style.FILL
            p.color = color
            p.textSize = size
            p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            val fit = minOf(1f, 355f / p.measureText(body).coerceAtLeast(1f))
            c.withTranslation(x, y) {
                c.scale(PIXEL_ASPECT * fit, 1f)
                c.drawText(body, 0f, 0f, p)
            }
        }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
            p.style = Paint.Style.STROKE
            p.color = color
            p.strokeWidth = width
            p.strokeCap = Paint.Cap.ROUND
            c.drawLine(x1, y1, x2, y2, p)
        }
        fun icon(value: String, x: Float, y: Float, rotate: Boolean) {
            val color = if (snapshot.enabled) ink else muted
            p.color = color
            p.style = Paint.Style.FILL
            c.withTranslation(x, y) {
                c.scale(PIXEL_ASPECT, 1f)
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
                    value.startsWith("Fast forward") || value.startsWith("Rewind") -> {
                        val d = if (value.startsWith("Rewind")) -1f else 1f
                        for (offset in listOf(-16f, 16f)) {
                            c.drawPath(
                                Path().apply {
                                    moveTo(x + offset + d * 14f, y)
                                    lineTo(x + offset - d * 12f, y - 20f)
                                    lineTo(x + offset - d * 12f, y + 20f)
                                    close()
                                },
                                p,
                            )
                        }
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

        text("‹ " + (if (snapshot.part == StandPart.Knob) "Knob" else "Button"), px(.200f / .244f), py(1.12f), 28f, muted)
        text(snapshot.title, px(.200f / .244f), py(1.24f), 48f)
        text(if (snapshot.busy) snapshot.status else "ASSIGN ACTION", px(.200f / .244f), py(1.33f), 22f, muted)
        snapshot.items.forEachIndexed { index, item ->
            val z = AssignmentLayout.rowZ(index)
            icon(item.title, px(.172f / .244f), py(z), item.title == "Volume" || item.title == "Disabled")
            val caption = "%02d / ".format(index + 1) + if (item.selected) "CURRENT" else "ASSIGN ACTION"
            text(caption, px(.2002f / .244f), py(z - .006f / .244f), 25f, muted)
            text(item.title, px(.2002f / .244f), py(z + .013f / .244f), 42f)
            if (item.selected) line(px(.159f / .244f), py(z - .009f / .244f), px(.159f / .244f), py(z + .009f / .244f), Color.rgb(220, 95, 50), 5f)
        }
        val end = AssignmentLayout.rowZ(snapshot.items.lastIndex) + .19f
        text("End of actions", px(.200f / .244f), py(end), 23f, muted)
        val pixels = ByteBuffer.allocateDirect(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(pixels)
        pixels.flip()
        texture.setImage(engine, 0, Texture.PixelBufferDescriptor(pixels, Texture.Format.RGBA, Texture.Type.UBYTE))
    }
    fun destroy() {
        listOf(solid, labels, icons).forEach { it.destroy() }
        ribbon.destroy()
        materials.destroyMaterialInstance(labelsMaterial)
        materials.destroyMaterialInstance(iconsMaterial)
        materials.destroyMaterial(material)
        engine.safeDestroyTexture(texture)
        bitmap.recycle()
    }
    companion object {
        private const val WIDTH = .9f
        private const val DEPTH = 2.6f
        private const val CENTER_Z = 2.2f
        private const val PIXEL_ASPECT = 1024f / 2048f * DEPTH / WIDTH
        private const val CUT = (.191f / .244f - AssignmentLayout.CENTER_X) / WIDTH + .5f
    }
}
