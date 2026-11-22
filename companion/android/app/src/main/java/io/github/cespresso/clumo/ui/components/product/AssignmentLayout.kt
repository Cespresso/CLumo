package io.github.cespresso.clumo.ui.components.product

import kotlin.math.sin

/** One layout is shared by the camera, scroll limits, labels and hit targets. */
internal object AssignmentLayout {
    const val CENTER_X = .232f / .244f
    const val FIRST_Z = .370f / .244f
    const val ROW_STEP = .05978f / .244f
    const val HALF_ROW = .0215f / .244f
    const val TOP_Z = .96f
    const val PITCH = 1.134464f // 65 degrees; readable floor labels.
    fun sourceOffset(bindingIndex: Int?): Float =
        when (bindingIndex) {
            5 -> .57f
            2 -> .815f
            3 -> 1.06f
            4 -> 1.305f
            else -> .88f + (319f / 1024f - .5f) * 1.06f
        } - 1.06f
    private val thresholds by lazy { List(8) { FloorRibbon.foregroundProgress(rowZ(it) - HALF_ROW, branch = true) } }
    fun rowThreshold(index: Int) = thresholds[index]
    fun rowZ(index: Int) = FIRST_Z + index * ROW_STEP
    fun width(widthDp: Float) = .80f * (widthDp / 380f).coerceAtLeast(1f)
    fun visibleDepth(widthDp: Float, aspect: Float) = width(widthDp) / aspect.coerceAtLeast(.2f) / sin(PITCH)
    fun maxScroll(count: Int, widthDp: Float, aspect: Float): Float = (rowZ((count - 1).coerceAtLeast(0)) + HALF_ROW + .29f - TOP_Z - visibleDepth(widthDp, aspect)).coerceAtLeast(0f)
    fun camera(x: Float, widthDp: Float, aspect: Float, scroll: Float, bindingIndex: Int? = null) =
        ProductCameraPose(
            yaw = 0f,
            pitch = PITCH,
            distance = width(widthDp) / aspect.coerceAtLeast(.2f) * 120f / 24f,
            targetX = x + CENTER_X,
            targetY = -.502f,
            targetZ = TOP_Z + visibleDepth(widthDp, aspect) / 2f + scroll + sourceOffset(bindingIndex),
            focalLength = 120f,
        )
}
