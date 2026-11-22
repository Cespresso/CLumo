package io.github.cespresso.clumo.ui.components.product

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.safeDestroyTexture
import java.nio.ByteBuffer

/** Low-contrast floor inlay; textures update only when the device accent changes. */
internal class FloorSelectionHalo(private val engine: Engine, private val materials: MaterialLoader, devices: List<GalleryDevice>) {
    private val textures = devices.map {
        Texture.Builder().width(512).height(512).levels(1).sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.SRGB8_A8).build(engine)
    }
    private val instances = textures.map { materials.createImageInstance(it) }
    private val colors = arrayOfNulls<Triple<Int, Float, Float>>(devices.size)
    private val widths = FloatArray(devices.size) { .16f }
    private val depths = FloatArray(devices.size) { .14f }
    val nodes = instances.mapIndexed { index, instance ->
        PlaneNode(
            engine = engine,
            size = Size(.80f, 0f, .80f),
            materialInstance = instance,
            builderApply = {
                castShadows(false)
                receiveShadows(false)
            },
        ).apply {
            name = "selection-inlay-${devices[index].id}"
            position = Position(galleryX(index), -.501f, 0f)
            isTouchable = false
            isVisible = false
        }
    }

    fun place(index: Int, x: Float, yaw: Float, halfWidth: Float, halfDepth: Float) {
        widths[index] = halfWidth
        depths[index] = halfDepth
        nodes[index].position = Position(x, -.501f, 0f)
        nodes[index].rotation = Rotation(0f, yaw, 0f)
    }

    fun updateColors(devices: List<GalleryDevice>) {
        devices.forEachIndexed { index, device ->
            val accent = device.display?.appearance?.buttonBColor?.value ?: 0xFFB87858.toInt()
            val key = Triple(accent, widths[index], depths[index])
            if (colors[index] == key) return@forEachIndexed
            colors[index] = key
            val bitmap = createBitmap(512, 512)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = .012f * 640f
                strokeCap = Paint.Cap.BUTT
                color = 0x40988B77
            }

            // Texture coordinates map directly to the .8-unit floor plane. Keep a small
            // uniform gap from the rotated model footprint instead of a screen-aligned L.
            fun uv(world: Float) = 256f + world * 640f
            val right = uv(widths[index] + .018f)
            val left = uv(-widths[index] + .005f)
            val front = uv(depths[index] + .025f)
            val radius = .024f * 640f
            val end = front - .058f * 640f
            val path = Path().apply {
                moveTo(left, front)
                lineTo(right - radius, front)
                arcTo(right - 2f * radius, front - 2f * radius, right, front, 90f, -90f, false)
                lineTo(right, end)
            }
            canvas.drawPath(path, paint)
            paint.color = (accent and 0x00FFFFFF) or 0x80000000.toInt()
            canvas.drawLine(right, end + .019f * 640f, right, end, paint)
            val buffer = ByteBuffer.allocateDirect(bitmap.byteCount)
            bitmap.copyPixelsToBuffer(buffer)
            buffer.flip()
            bitmap.recycle()
            textures[index].setImage(engine, 0, Texture.PixelBufferDescriptor(buffer, Texture.Format.RGBA, Texture.Type.UBYTE))
        }
    }

    fun onFrame(frameTimeNanos: Long, selectedIndex: Int, controls: Boolean) {
        nodes.forEachIndexed { index, node -> node.isVisible = index == selectedIndex && !controls }
    }

    fun destroy() {
        nodes.forEach { it.destroy() }
        instances.forEach(materials::destroyMaterialInstance)
        textures.forEach { engine.safeDestroyTexture(it) }
    }
}
