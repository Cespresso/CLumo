package io.github.cespresso.clumo.ui.components.product

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.safeDestroyTexture
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

/** Small static UI textures; input is handled by the scene's existing ray hit test. */
internal class FloorActions(private val engine: Engine, private val materials: MaterialLoader) {
    private val textures = List(2) { index ->
        val bitmap = createBitmap(256, 256)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(247, 246, 241)
        canvas.drawCircle(128f, 128f, 120f, paint)
        paint.color = Color.rgb(104, 134, 101)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 12f
        paint.strokeCap = Paint.Cap.ROUND
        if (index == 0) {
            canvas.drawCircle(128f, 128f, 49f, paint)
            canvas.drawCircle(128f, 128f, 20f, paint)
            for (tooth in 0 until 8) {
                val angle = tooth * Math.PI / 4
                canvas.drawLine(
                    128f + cos(angle).toFloat() * 48f,
                    128f + sin(angle).toFloat() * 48f,
                    128f + cos(angle).toFloat() * 65f,
                    128f + sin(angle).toFloat() * 65f,
                    paint,
                )
            }
        } else {
            canvas.drawCircle(113f, 113f, 43f, paint)
            canvas.drawLine(146f, 146f, 187f, 187f, paint)
        }
        val buffer = ByteBuffer.allocateDirect(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        buffer.flip()
        bitmap.recycle()
        Texture.Builder().width(256).height(256).levels(1).sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.SRGB8_A8).build(engine).apply {
                setImage(engine, 0, Texture.PixelBufferDescriptor(buffer, Texture.Format.RGBA, Texture.Type.UBYTE))
            }
    }
    private val instances = textures.map { materials.createImageInstance(it) }
    val nodes = instances.mapIndexed { index, instance ->
        PlaneNode(
            engine = engine,
            size = Size(.64f, 0f, .64f),
            materialInstance = instance,
            builderApply = {
                castShadows(false)
                receiveShadows(false)
            },
        ).apply { name = if (index == 0) "floor-settings" else "floor-find" }
    }

    fun update(deviceCount: Int, visible: Boolean) {
        nodes.forEachIndexed { action, node ->
            node.position = Position(galleryX(if (action == 0) -1 else deviceCount), -.5008f, 0f)
            node.isVisible = visible
            node.isTouchable = visible
        }
    }

    fun destroy() {
        nodes.forEach { it.destroy() }
        instances.forEach(materials::destroyMaterialInstance)
        textures.forEach { engine.safeDestroyTexture(it) }
    }
}
