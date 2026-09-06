package io.github.cespresso.clumo.domain

/**
 * The saved pattern whose bits are exactly [committedFrame], or null when CLumo is showing
 * something the library does not have. First match wins when two patterns share a face.
 */
fun patternFor(committedFrame: Long?, library: List<Pattern>): Pattern? {
    val frame = committedFrame ?: return null
    return library.firstOrNull { FaceBits.fromBitsString(it.bits) == frame }
}
