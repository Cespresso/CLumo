package io.github.cespresso.clumo.ui.components.product

import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentInstance
import io.github.cespresso.clumo.domain.DeviceKind
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode

/** Scene-lifetime cache: destinations and scan updates never replace existing model instances. */
internal class ProductModels(private val engine: Engine, private val loader: ModelLoader, private val materials: MaterialLoader) {
    private data class Entry(val node: ModelNode, val binding: LiveProductBinding?)
    private val entries = mutableMapOf<Pair<String, String>, Entry>()
    private val prototypes = mutableMapOf<String, FilamentInstance>()

    fun nodes(devices: List<GalleryDevice>, mode: Int): List<ModelNode> =
        devices.map { device ->
            val asset = device.asset(mode)
            val entry = entries.getOrPut(device.id to asset) {
                val prototype = prototypes[asset]
                val instance = if (prototype == null) {
                    loader.createInstancedModel(asset, 1).single().also { prototypes[asset] = it }
                } else {
                    requireNotNull(loader.createInstance(prototype.asset))
                }
                val node = ModelNode(instance, scaleToUnits = if (device.kind == DeviceKind.CLUMO) .6f else 1f).apply {
                    name = "product-${device.id}"
                    isShadowCaster = true
                    // Imported renderables have no collision shape until explicitly initialized.
                    renderableNodes.forEach { it.updateCollisionShape() }
                }
                Entry(node, if (device.display != null) LiveProductBinding(engine, materials, node) else null)
            }
            device.display?.let { entry.binding?.update(it) }
            entry.node
        }

    fun select(node: ModelNode?, part: StandPart?) {
        entries.values.forEach { it.binding?.select(if (it.node === node) part else null) }
    }

    fun destroy() {
        entries.values.forEach {
            it.node.destroy()
            it.binding?.destroy()
        }
        entries.clear()
        // The shared ModelLoader releases the assets and all of their instances on disposal.
        prototypes.clear()
    }
}
