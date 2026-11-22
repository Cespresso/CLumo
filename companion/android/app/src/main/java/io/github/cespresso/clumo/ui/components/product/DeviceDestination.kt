package io.github.cespresso.clumo.ui.components.product

/** Nested destinations within one device; the outer app stack owns the device list. */
sealed interface DeviceDestination {
    val part: StandPart? get() = null
    val bindingIndex: Int? get() = null
    data object Detail : DeviceDestination
    data class Part(override val part: StandPart) : DeviceDestination
    data class Assignment(override val part: StandPart, override val bindingIndex: Int) : DeviceDestination {
        init {
            require(bindingIndex in part.bindingIndices)
        }
    }
    fun parent(): DeviceDestination? =
        when (this) {
            Detail -> null
            is Part -> Detail
            is Assignment -> Part(part)
        }
}
