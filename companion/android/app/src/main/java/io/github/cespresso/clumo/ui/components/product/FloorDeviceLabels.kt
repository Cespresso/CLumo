package io.github.cespresso.clumo.ui.components.product

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.safeDestroyTexture
import java.nio.ByteBuffer

/** Labels stay in world space and upload only when their name or connection text changes. */
internal class FloorDeviceLabels(private val engine: Engine, private val materials: MaterialLoader, devices: List<GalleryDevice>) {
    private val textures = devices.map {
        Texture.Builder().width(768).height(320).levels(1).sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.SRGB8_A8).build(engine)
    }
    private val instances = textures.map { materials.createImageInstance(it) }
    private val captions = MutableList<Pair<String, String>?>(devices.size) { null }
    val nodes = instances.mapIndexed { index, instance ->
        PlaneNode(
            engine = engine,
            size = Size(.76f, 0f, .42f),
            materialInstance = instance,
            builderApply = {
                castShadows(false)
                receiveShadows(false)
            },
        ).apply {
            name = "floor-label-${devices[index].id}"
            position = Position(galleryX(index), -.5006f, .60f)
            isTouchable = false
        }
    }

    fun update(devices: List<GalleryDevice>, visible: Boolean) {
        devices.forEachIndexed { index, device ->
            nodes[index].isVisible = visible
            val caption = device.name to device.status
            if (captions[index] != caption) {
                captions[index] = caption
                val bitmap = createBitmap(768, 320)
                val canvas = Canvas(bitmap)
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(63, 66, 59)
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 55f
                }
                val defaultName = device.name.startsWith("CLumo", ignoreCase = true)
                val title = if (defaultName && !device.unidentified) {
                    if (device.kind == io.github.cespresso.clumo.domain.DeviceKind.HEADPHONE_STAND) "Stand" else "CLumo"
                } else {
                    device.name
                }
                paint.textSize = 108f
                val name = TextUtils.ellipsize(title, paint, 736f, TextUtils.TruncateAt.END).toString()
                canvas.drawText(name, 384f, 112f, paint)
                paint.textSize = 30f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val identifier = device.name.substringAfterLast('-', "").takeIf { defaultName && it != device.name }
                canvas.drawText(if (identifier != null) "CLUMO / $identifier" else "CLUMO", 384f, 184f, paint)
                paint.textSize = 34f
                val status = when (device.status) {
                    "接続済み" -> "Connected"
                    "同期中" -> "Syncing"
                    "接続中" -> "Connecting"
                    "再接続中" -> "Reconnecting"
                    "ペアリング中" -> "Pairing"
                    "未接続" -> "Disconnected"
                    "接続エラー" -> "Connection error"
                    "デモ" -> "Demo"
                    else -> device.status
                }
                paint.color = if (device.status == "接続済み") Color.rgb(104, 134, 101) else Color.rgb(114, 110, 102)
                canvas.drawText("•  $status", 384f, 253f, paint)
                val buffer = ByteBuffer.allocateDirect(bitmap.byteCount)
                bitmap.copyPixelsToBuffer(buffer)
                buffer.flip()
                bitmap.recycle()
                textures[index].setImage(engine, 0, Texture.PixelBufferDescriptor(buffer, Texture.Format.RGBA, Texture.Type.UBYTE))
            }
        }
    }

    fun destroy() {
        nodes.forEach { it.destroy() }
        instances.forEach(materials::destroyMaterialInstance)
        textures.forEach { engine.safeDestroyTexture(it) }
    }
}
