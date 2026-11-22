package io.github.cespresso.clumo.ui.components.product

enum class MatrixPage {
    Overview,
    Display,
    Mode,
    MainButton,
    SubButton,
    ;

    companion object {
        fun forNode(name: String?) =
            when (name) {
                "matrix_surface", "led_matrix" -> Display
                "button_a" -> MainButton
                "button_b" -> SubButton
                else -> null
            }
    }
}
