package io.github.cespresso.clumo.data.ble

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.cespresso.clumo.data.session.DeviceSession
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in test against a physical CLumo. Normal connected test runs skip it unless
 * `deviceAddress` is supplied as an instrumentation argument.
 */
@RunWith(AndroidJUnit4::class)
class BleHardwareIntegrationTest {
    private var session: DeviceSession? = null

    @Before
    fun prepareDevice() {
        assumeTrue(deviceAddress() != null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val packageName = instrumentation.targetContext.packageName
            instrumentation.uiAutomation.grantRuntimePermission(
                packageName,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
            instrumentation.uiAutomation.grantRuntimePermission(
                packageName,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        }
    }

    @After
    fun disconnect() {
        session?.dispose()
    }

    @Test
    fun countdownKeepsRunningAcrossModeSwitchesAndInitialStateIsCanonical(): Unit =
        runBlocking {
            val address = requireNotNull(deviceAddress())
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            val link = DeviceConnection(targetContext, address, "CLumo hardware test")
            val deviceSession = DeviceSession(
                link,
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                SystemClock::elapsedRealtime,
            )
            session = deviceSession
            deviceSession.connect()

            withTimeout(CONNECT_TIMEOUT_MS) {
                deviceSession.state.first { it.link == ConnectionState.Ready }
            }
            assertTrue(deviceSession.state.value.observed != null)
            assertTrue(requireNotNull(link.deviceId.value).isNotBlank())
            assertTrue(requireNotNull(link.brightness.value) in 0..15)
            val originalMode = requireNotNull(link.currentMode.value)

            try {
                deviceSession.setMode(DeviceMode.TIMER)
                awaitMode(link, DeviceMode.TIMER)
                deviceSession.timerCancel()
                awaitTimer(link) { it.isIdle }
                val originalSetting = requireNotNull(link.timerStatus.value)

                deviceSession.timerSetDuration(0, TEST_DURATION_SECONDS)
                awaitTimer(link) {
                    it.isIdle && it.configuredTotalSec == TEST_DURATION_SECONDS
                }
                deviceSession.timerStart()
                val started = awaitTimer(link) { it.isRunning }

                delay(1_200)
                deviceSession.setMode(DeviceMode.DISPLAY)
                awaitMode(link, DeviceMode.DISPLAY)
                delay(1_200)
                deviceSession.setMode(DeviceMode.TIMER)
                awaitMode(link, DeviceMode.TIMER)

                val resumed = awaitTimer(link) {
                    it.isRunning && it.remainingSec < started.remainingSec
                }
                assertTrue(resumed.remainingSec <= TEST_DURATION_SECONDS - 2)

                deviceSession.timerCancel()
                awaitTimer(link) { it.isIdle }
                deviceSession.timerSetDuration(
                    originalSetting.configuredMin,
                    originalSetting.configuredSec,
                )
                awaitTimer(link) {
                    it.isIdle && it.configuredTotalSec == originalSetting.configuredTotalSec
                }
            } finally {
                deviceSession.setMode(originalMode)
                awaitMode(link, originalMode)
            }
        }

    private suspend fun awaitMode(link: DeviceConnection, expected: Int) {
        withTimeout(COMMAND_TIMEOUT_MS) {
            link.currentMode.first { it == expected }
        }
        assertEquals(expected, link.currentMode.value)
    }

    private suspend fun awaitTimer(
        link: DeviceConnection,
        predicate: (io.github.cespresso.clumo.domain.CountdownTimerStatus) -> Boolean,
    ): io.github.cespresso.clumo.domain.CountdownTimerStatus =
        withTimeout(COMMAND_TIMEOUT_MS) {
            link.timerStatus.first { it != null && predicate(it) }!!
        }

    private fun deviceAddress(): String? = InstrumentationRegistry.getArguments().getString("deviceAddress")

    private companion object {
        const val TEST_DURATION_SECONDS = 8
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val COMMAND_TIMEOUT_MS = 10_000L
    }
}
