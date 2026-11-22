package io.github.cespresso.clumo.ui.components.product

/** UI selection only: never transmitted as device LED state. */
enum class StandPart(val title: String, val nodeName: String, val bindingIndices: List<Int>) {
    Matrix("LEDマトリクス", "matrix_surface", emptyList()),
    LeftButton("左ボタン", "stand_left", listOf(0)),
    RightButton("右ボタン", "stand_right", listOf(1)),
    Knob("ノブ", "stand_knob", listOf(2, 3, 4, 5)),
    ;

    companion object {
        fun fromNode(name: String?): StandPart? = if (name == "led_matrix") Matrix else entries.firstOrNull { it.nodeName == name }
    }
}
