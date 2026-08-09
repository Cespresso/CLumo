package io.github.cespresso.clumo.data

import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceAppearanceJsonTest {

    @Test
    fun roundTripKeepsDifferentProfilesSeparate() {
        val first = DeviceAppearance.DEFAULT.copy(ledColor = color("#E85D53"))
        val second = DeviceAppearance.DEFAULT.copy(enclosureColor = color("#538BC5"))

        val decoded = decodeDeviceAppearances(
            encodeDeviceAppearances(mapOf("one" to first, "two" to second))
        )

        assertEquals(first, decoded["one"])
        assertEquals(second, decoded["two"])
    }

    @Test
    fun malformedFieldFallsBackWithoutDiscardingValidFields() {
        val raw = """{"device":{"enclosure":"broken","buttonA":"#538BC5","buttonB":"#FFFFFF","led":"#54A36A"}}"""

        val appearance = requireNotNull(decodeDeviceAppearances(raw)["device"])

        assertEquals(DeviceAppearance.DEFAULT.enclosureColor, appearance.enclosureColor)
        assertEquals(color("#538BC5"), appearance.buttonAColor)
        assertEquals(color("#FFFFFF"), appearance.buttonBColor)
        assertEquals(color("#54A36A"), appearance.ledColor)
    }

    @Test
    fun missingFieldsFallBackIndividuallyAndUnknownFieldsAreIgnored() {
        val raw = """{"device":{"enclosure":"#A99ABC","future":"value"}}"""

        val appearance = requireNotNull(decodeDeviceAppearances(raw)["device"])

        assertEquals(color("#A99ABC"), appearance.enclosureColor)
        assertEquals(DeviceAppearance.DEFAULT.buttonAColor, appearance.buttonAColor)
        assertEquals(DeviceAppearance.DEFAULT.buttonBColor, appearance.buttonBColor)
        assertEquals(DeviceAppearance.DEFAULT.ledColor, appearance.ledColor)
    }

    @Test
    fun malformedTopLevelJsonReturnsEmptyMap() {
        assertEquals(emptyMap<String, DeviceAppearance>(), decodeDeviceAppearances("{"))
        assertEquals(emptyMap<String, DeviceAppearance>(), decodeDeviceAppearances(null))
    }

    @Test
    fun updateChangesOnlyTheRequestedDevice() {
        val first = DeviceAppearance.DEFAULT
        val second = DeviceAppearance.DEFAULT.copy(ledColor = color("#538BC5"))
        val updatedFirst = first.copy(buttonAColor = color("#A99ABC"))

        val result = updatedDeviceAppearances(
            current = mapOf("one" to first, "two" to second),
            deviceId = "one",
            appearance = updatedFirst,
        )

        assertEquals(updatedFirst, result["one"])
        assertEquals(second, result["two"])
    }

    @Test
    fun resetRemovesOnlyTheRequestedDevice() {
        val result = updatedDeviceAppearances(
            current = mapOf("one" to DeviceAppearance.DEFAULT, "two" to DeviceAppearance.DEFAULT),
            deviceId = "one",
            appearance = null,
        )

        assertFalse(result.containsKey("one"))
        assertEquals(DeviceAppearance.DEFAULT, result["two"])
    }

    private fun color(hex: String): RgbColor = requireNotNull(RgbColor.parseOrNull(hex))
}
