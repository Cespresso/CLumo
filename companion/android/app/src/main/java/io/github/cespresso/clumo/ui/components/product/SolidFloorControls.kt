package io.github.cespresso.clumo.ui.components.product

import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode

/** Blender-authored solids. Preserve original origins; normalize against the 244mm product. */
internal class SolidFloorControls(loader: ModelLoader) {
    val node = ModelNode(
        loader.createInstancedModel("models/floor-controls-c.glb", 1).single(),
        autoAnimate = false,
        scaleToUnits = null,
        centerOrigin = null,
    ).apply {
        name = "floor-control-solids"
        scale = Scale(1f / PRODUCT_HEIGHT)
        isShadowCaster = true
        isShadowReceiver = true
        isTouchable = false
        renderableNodes.forEach { it.isTouchable = false }
    }

    fun update(x: Float, visible: Boolean, progress: Float = 1f) {
        node.position = Position(x, FLOOR_Y, 0f)
        node.isVisible = visible
        node.renderableNodes.forEach { child ->
            val row = FloorControlRows.keys.indexOfFirst { child.name?.contains(it) == true }
            child.isVisible = visible && row >= 0 && progress >= FloorControlRows.thresholds[row]
        }
    }

    fun destroy() = node.destroy()

    companion object {
        const val PRODUCT_HEIGHT = .244f
        const val FLOOR_Y = -.502f
        const val LABEL_Y = FLOOR_Y + .00230f / PRODUCT_HEIGHT
        const val ICON_Y = FLOOR_Y + .00492f / PRODUCT_HEIGHT
    }
}

/** Shared layout for solids, text, hit areas and the ribbon arrival thresholds. */
internal object FloorControlRows {
    val keys = listOf("rotation", "single", "double", "triple")
    fun z(row: Int) = .57f + row * .245f
    val thresholds = FloatArray(4) { FloorRibbon.foregroundProgress(z(it) - .0215f / .244f) }
}
