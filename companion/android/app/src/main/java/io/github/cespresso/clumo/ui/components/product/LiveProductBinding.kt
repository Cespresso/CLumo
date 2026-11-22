package io.github.cespresso.clumo.ui.components.product

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.material.setColor
import io.github.sceneview.node.ModelNode
import io.github.sceneview.safeDestroyTexture
import java.nio.ByteBuffer
import kotlin.math.pow

/** Per-device materials: geometry can be shared, appearance and LEDs must never be shared. */
internal class LiveProductBinding(
    private val engine: Engine,
    private val materials: MaterialLoader,
    model: ModelNode,
) {
    private val selectionMaterial = materials.createMaterial("materials/part_selection.filamat")
    private var powerLed: MaterialInstance? = null
    private val selectable = mutableMapOf<String, MaterialInstance>()
    private var selectedPart: StandPart? = null
    private val volumeLights = mutableMapOf<Int, MaterialInstance>()
    private val colors = mutableMapOf<String, MaterialInstance>()
    private val texture = Texture.Builder().width(8).height(8).levels(1)
        .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.SRGB8_A8).build(engine)
    private val diffuserMaterial = materials.createMaterial("materials/matrix_diffuser.filamat")
    private var applied: ProductDisplayState? = null

    private val stand = model.renderableNodes.any { it.name == "stand_knob" }

    init {
        val regions = ProductDisplayState().regionColors()
        model.renderableNodes.forEach { node ->
            val role = node.name
            if (role == "led_matrix") {
                node.isVisible = false
            } else if (role == "matrix_surface") {
                val diffuser = materials.createInstance(diffuserMaterial)
                diffuser.setParameter(
                    "pixels",
                    texture,
                    TextureSampler(
                        TextureSampler.MinFilter.NEAREST,
                        TextureSampler.MagFilter.NEAREST,
                        TextureSampler.WrapMode.CLAMP_TO_EDGE,
                    ),
                )
                // First LED centre and signed column pitch from the exported models (metres).
                if (stand) {
                    diffuser.setParameter("grid", -.0289f, .0759f, 250f)
                } else {
                    diffuser.setParameter("grid", .03f, .03f, -250f)
                }
                diffuser.setParameter("selected", 0f)
                selectable[role] = diffuser
                colors[role] = diffuser
                node.setMaterialInstances(diffuser)
            } else if (role?.startsWith("volume_") == true) {
                val level = role.substringAfter("volume_").toInt()
                val lens = createLens(level)
                setTint(lens, 0xAAA9A3)
                volumeLights[level] = lens
                node.setMaterialInstances(lens)
            } else if (role == "power_led") {
                powerLed = createLens(0).also { node.setMaterialInstances(it) }
            } else if (role in regions) {
                val material = materials.createInstance(selectionMaterial).also {
                    selectable[requireNotNull(role)] = it
                    it.setParameter("powered", 0f)
                    it.setParameter("selected", 0f)
                    setTint(it, regions.getValue(role))
                }
                colors[requireNotNull(role)] = material
                node.setMaterialInstances(material)
            }
        }
        update(ProductDisplayState())
    }

    fun update(display: ProductDisplayState) {
        if (applied == display) return
        if (applied?.appearance != display.appearance) {
            volumeLights.values.forEach { setTint(it, display.appearance.windowColor.value) }
            display.regionColors().forEach { (role, rgb) ->
                val selection = selectable[role]
                if (selection != null) setTint(selection, rgb) else colors[role]?.setColor(rgb or 0xFF000000.toInt())
            }
        }
        if (applied?.outlinedRoles != display.outlinedRoles) {
            fun rim(role: String) = if (role in display.outlinedRoles) 1f else 0f
            selectable.forEach { (role, material) -> material.setParameter("outline", rim(role)) }
            volumeLights.forEach { (level, material) -> material.setParameter("outline", rim("volume_$level")) }
            powerLed?.setParameter("outline", rim("power_led"))
        }
        if (applied?.appearance != display.appearance || applied?.connectionState != display.connectionState) {
            powerLed?.let {
                setTint(it, display.appearance.standColor("power_led").value)
                it.setParameter("powered", if (display.connectionState == io.github.cespresso.clumo.domain.ConnectionState.Ready) 1f else 0f)
            }
        }
        if (applied?.volumeSegments != display.volumeSegments || applied?.connectionState != display.connectionState) {
            val count = if (display.connectionState == io.github.cespresso.clumo.domain.ConnectionState.Ready) display.volumeSegments else 0
            volumeLights.forEach { (level, lens) -> lens.setParameter("powered", if (level <= count) 1f else 0f) }
        }
        val pixels = display.ledRgba(rotate180 = !stand)
        // The diffuser addresses rows directly, starting with the top row.
        val buffer = ByteBuffer.allocateDirect(pixels.size).apply {
            put(pixels)
            flip()
        }
        texture.setImage(engine, 0, Texture.PixelBufferDescriptor(buffer, Texture.Format.RGBA, Texture.Type.UBYTE))
        applied = display
    }

    // In model space the four 8 mm lenses form a vertical column on a 9 mm pitch.
    private fun createLens(level: Int) =
        materials.createInstance(selectionMaterial).also {
            it.setParameter("lensCenter", .02210f, .04445f + level * .009f)
            it.setParameter("selected", 0f)
            it.setParameter("powered", 0f)
        }

    private fun setTint(material: MaterialInstance, rgb: Int) {
        fun channel(shift: Int): Float {
            val value = ((rgb shr shift) and 255) / 255f
            return if (value <= .04045f) value / 12.92f else ((value + .055f) / 1.055f).pow(2.4f)
        }
        material.setParameter("tint", channel(16), channel(8), channel(0))
    }

    fun select(part: StandPart?) {
        if (selectedPart == part) return
        selectedPart = part
        selectable.forEach { (role, material) -> material.setParameter("selected", if (role == part?.nodeName) 1f else 0f) }
    }

    fun destroy() {
        colors.values.forEach(materials::destroyMaterialInstance)
        volumeLights.values.forEach(materials::destroyMaterialInstance)
        powerLed?.let(materials::destroyMaterialInstance)
        materials.destroyMaterial(selectionMaterial)
        materials.destroyMaterial(diffuserMaterial)
        engine.safeDestroyTexture(texture)
    }
}
