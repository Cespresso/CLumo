package io.github.cespresso.clumo.data.ble

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceKind
import io.github.cespresso.clumo.domain.DeviceMode
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.Pattern
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in test: the supplied standAddress must identify a paired test device. */
@RunWith(AndroidJUnit4::class)
class HeadphoneStandHardwareTest {
    @Test
    fun bindingsRoundTrip(): Unit =
        runBlocking {
            val address = InstrumentationRegistry.getArguments().getString("standAddress")
            assumeTrue(address != null)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN).forEach {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it)
            }
            val link = DeviceConnection(context, requireNotNull(address), "CLumo bindings test")
            try {
                link.connect()
                withTimeout(30_000) { link.connectionState.first { it == ConnectionState.Ready } }
                val original = link.readControlBindings()
                val display = link.readStandDisplay().encode()
                try {
                    for (action in 0..7) {
                        val changed = original.changed(0, action).changed(5, action % 2)
                        org.junit.Assert.assertArrayEquals(changed.encode(), link.saveControlBindings(changed).encode())
                    }
                    org.junit.Assert.assertArrayEquals(ControlBindings.DEFAULT.encode(), link.saveControlBindings(ControlBindings.DEFAULT).encode())
                    link.disconnect()
                    delay(700)
                    link.connect()
                    withTimeout(30_000) { link.connectionState.first { it == ConnectionState.Ready } }
                    org.junit.Assert.assertArrayEquals(ControlBindings.DEFAULT.encode(), link.readControlBindings().encode())
                    org.junit.Assert.assertArrayEquals(display, link.readStandDisplay().encode())
                } finally {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { link.saveControlBindings(original) }
                }
            } finally {
                link.dispose()
            }
        }

    @Test
    fun sharedDisplayRoundTrip(): Unit =
        runBlocking {
            val address = InstrumentationRegistry.getArguments().getString("standAddress")
            assumeTrue(address != null)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN).forEach {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it)
            }
            val link = DeviceConnection(context, requireNotNull(address), "CLumo shared editor test")
            try {
                link.connect()
                withTimeout(30_000) { link.connectionState.first { it == ConnectionState.Ready } }
                val original = link.readStandDisplay()
                try {
                    for (count in listOf(1, 8)) {
                        for (mode in 0..2) {
                            val bytes = original.encode()
                            bytes[1] = mode.toByte()
                            bytes[10] = 1
                            bytes[11] = count.toByte()
                            bytes[12] = 44
                            bytes[13] = 1
                            repeat(8) { frame -> repeat(8) { row -> bytes[14 + frame * 8 + row] = (frame * 17 + row).toByte() } }
                            bytes.copyInto(bytes, 2, 14, 22)
                            val settings = requireNotNull(StandDisplaySettings.decode(bytes))
                            org.junit.Assert.assertArrayEquals(bytes, link.saveStandDisplay(settings).encode())
                            org.junit.Assert.assertArrayEquals(bytes, link.readStandDisplay().encode())
                        }
                    }
                } finally {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        org.junit.Assert.assertArrayEquals(original.encode(), link.saveStandDisplay(original).encode())
                    }
                }
            } finally {
                link.dispose()
            }
        }

    /** Read settings only; volume is volatile. ESP32 serial logs verify application/expiry. */
    @Test
    fun volumeV2AndReconnect(): Unit =
        runBlocking {
            val address = InstrumentationRegistry.getArguments().getString("standAddress")
            assumeTrue(address != null)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN).forEach {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it)
            }
            val link = DeviceConnection(context, requireNotNull(address), "CLumo volume v2 test")
            try {
                link.connect()
                withTimeout(30_000) { link.connectionState.first { it == ConnectionState.Ready } }
                assertEquals(DeviceKind.HEADPHONE_STAND, link.kind.value)
                val id = link.deviceId.value
                assertNotNull(id)
                for (value in listOf(0, 33, 34, 66, 67, 100, null)) {
                    android.util.Log.i("StandVolumeTest", "sample=$value")
                    link.writeMediaVolume(value)
                    delay(700)
                }
                // Repeated identical reports must keep the observation alive beyond 2s.
                repeat(7) {
                    link.writeMediaVolume(50)
                    delay(500)
                }
                android.util.Log.i("StandVolumeTest", "pause for expiry")
                delay(2600)
                link.writeMediaVolume(50)
                delay(700)
                link.disconnect()
                withTimeout(5_000) { link.connectionState.first { it != ConnectionState.Ready } }
                delay(700)
                link.connect()
                withTimeout(30_000) { link.connectionState.first { it == ConnectionState.Ready } }
                assertEquals(id, link.deviceId.value)
                link.writeMediaVolume(67)
                delay(700)
                assertEquals(ConnectionState.Ready, link.connectionState.value)
            } finally {
                link.dispose()
            }
        }

    @Test
    fun dedicatedGattSettingsAndReconnect(): Unit =
        runBlocking {
            val address = InstrumentationRegistry.getArguments().getString("standAddress")
            assumeTrue(address != null)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN).forEach {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it)
            }
            val link = DeviceConnection(context, requireNotNull(address), "CLumo-Stand hardware test")
            try {
                link.connect()
                withTimeout(30_000) { link.connectionState.first { it == ConnectionState.Ready } }
                assertEquals(DeviceKind.HEADPHONE_STAND, link.kind.value)
                assertEquals(DeviceMode.VISUALIZER, link.currentMode.value)
                val id = link.deviceId.value
                assertNotNull(id)
                val brightness = requireNotNull(link.brightness.value)
                val standby = requireNotNull(link.standbyMode.value)
                val originalFrame = requireNotNull(link.displayCommittedFrame.value)
                val testFrame = byteArrayOf(-127, 66, 36, 24, 24, 36, 66, -127)
                try {
                    link.writeBrightness(if (brightness == 5) 6 else 5)
                    withTimeout(10_000) { link.brightness.first { it != brightness } }
                    for (mode in listOf(1, 2, 0)) {
                        link.writeStandbyMode(mode)
                        withTimeout(10_000) { link.standbyMode.first { it == mode } }
                    }
                    link.commitFrame(testFrame)
                    withTimeout(10_000) { link.displayCommittedFrame.first { it == FaceBits.fromRowBytes(testFrame) } }
                    for (volume in listOf(0, 33, 34, 66, 67, 100, null)) {
                        link.writeMediaVolume(volume)
                        link.writeVisualizerColumns(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
                        delay(100)
                    }
                    link.disconnect()
                    link.connect()
                    withTimeout(30_000) { link.connectionState.first { it == ConnectionState.Ready } }
                    assertEquals(id, link.deviceId.value)
                    assertEquals(0, link.standbyMode.value)
                    assertEquals(FaceBits.fromRowBytes(testFrame), link.displayCommittedFrame.value)
                    link.writeMode(DeviceMode.TIMER)
                    link.timerStart()
                    link.pomodoroStart()
                    delay(300)
                    assertEquals(DeviceMode.VISUALIZER, link.currentMode.value)
                    assertEquals(ConnectionState.Ready, link.connectionState.value)
                } finally {
                    link.writeBrightness(brightness)
                    link.writeStandbyMode(standby)
                    link.commitFrame(Pattern.bitsToRowBytes(FaceBits.toBitsString(originalFrame)))
                    withTimeout(10_000) {
                        link.displayCommittedFrame.first { it == originalFrame }
                        link.brightness.first { it == brightness }
                        link.standbyMode.first { it == standby }
                    }
                }
            } finally {
                link.dispose()
            }
        }
}
