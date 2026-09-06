package io.github.cespresso.clumo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatternLookupTest {

    private val heart = Pattern("heart", "Heart", "0000000001100110111111111111111101111110001111000001100000000000")
    private val star = Pattern("star", "Star", "0001100000011000011111100011110000011000001111000110011000000000")
    private val library = listOf(heart, star)

    @Test
    fun findsThePatternWhoseBitsAreTheFrame() {
        assertEquals(star, patternFor(FaceBits.fromBitsString(star.bits), library))
    }

    @Test
    fun aFrameTheLibraryLacksHasNoName() {
        assertNull(patternFor(FaceBits.fromBitsString("1".repeat(64)), library))
        assertNull(patternFor(FaceBits.EMPTY, library))
    }

    @Test
    fun noFrameYetMeansNoPattern() {
        assertNull(patternFor(null, library))
    }

    @Test
    fun twoPatternsWithTheSameFaceResolveToTheFirst() {
        val twin = heart.copy(id = "twin", name = "Twin")
        assertEquals(heart, patternFor(FaceBits.fromBitsString(heart.bits), listOf(heart, twin)))
        assertEquals(twin, patternFor(FaceBits.fromBitsString(heart.bits), listOf(twin, heart)))
    }

    @Test
    fun anAllOffPatternInTheLibraryMatchesAnAllOffFrame() {
        val blank = Pattern("blank", "Blank", "0".repeat(64))
        assertEquals(blank, patternFor(FaceBits.EMPTY, library + blank))
    }
}
