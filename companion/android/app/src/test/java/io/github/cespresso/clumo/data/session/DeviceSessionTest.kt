package io.github.cespresso.clumo.data.session

import io.github.cespresso.clumo.data.ble.ButtonEvent
import io.github.cespresso.clumo.data.ble.DeviceObservation
import io.github.cespresso.clumo.data.ble.DeviceTransport
import io.github.cespresso.clumo.domain.ConnectionFailure
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.CountdownTimerStatus
import io.github.cespresso.clumo.domain.DeviceMode
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.domain.Pattern
import io.github.cespresso.clumo.domain.PomodoroStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSessionTest {

    @Test
    fun reportedModeBecomesObservedAndClearsPendingIntent() {
        val fixture = fixture()
        fixture.ready(mode = DeviceMode.POMODORO, brightness = 5)

        fixture.session.setMode(DeviceMode.DISPLAY)
        assertEquals(DeviceMode.DISPLAY, fixture.session.state.value.effectiveMode)
        assertEquals(listOf(DeviceMode.DISPLAY), fixture.transport.modeWrites)

        fixture.transport.reportMode(DeviceMode.TIMER)
        fixture.scope.runCurrent()

        assertEquals(DeviceMode.TIMER, fixture.session.state.value.observed?.mode)
        assertNull(fixture.session.state.value.pending.mode)
    }

    @Test
    fun readyWritesNothingToTheDevice() {
        val fixture = fixture()

        fixture.ready(mode = DeviceMode.DISPLAY, brightness = 5)

        assertTrue(fixture.transport.modeWrites.isEmpty())
        assertTrue(fixture.transport.commits.isEmpty())
    }

    @Test
    fun brightnessKeepsOnlyLatestValueAndPendingClearsEvenWhenDeviceRejectsIt() {
        val fixture = fixture()
        fixture.ready(mode = DeviceMode.POMODORO, brightness = 4)

        fixture.session.setBrightnessLevel(7)
        fixture.session.setBrightnessLevel(12)
        fixture.scope.advanceTimeBy(99)
        assertTrue(fixture.transport.brightnessWrites.isEmpty())

        fixture.scope.advanceTimeBy(1)
        fixture.scope.runCurrent()
        assertEquals(listOf(12), fixture.transport.brightnessWrites)
        assertEquals(12, fixture.session.state.value.pending.brightnessLevel?.value)

        fixture.transport.reportBrightness(4)
        fixture.scope.runCurrent()
        assertEquals(4, fixture.session.state.value.observed?.brightnessLevel)
        assertNull(fixture.session.state.value.pending.brightnessLevel)
    }

    @Test
    fun reconnectReplacesSnapshotThenResendsOnlyUnexpiredPendingCommand() {
        val fixture = fixture()
        fixture.ready(mode = DeviceMode.POMODORO, brightness = 5)
        fixture.session.setMode(DeviceMode.DISPLAY)

        fixture.transport.connectionState.value = ConnectionState.Reconnecting
        fixture.transport.currentMode.value = DeviceMode.TIMER
        fixture.transport.brightness.value = 8
        fixture.scope.runCurrent()
        fixture.transport.connectionState.value = ConnectionState.Ready
        fixture.scope.runCurrent()

        assertEquals(DeviceMode.TIMER, fixture.session.state.value.observed?.mode)
        assertEquals(8, fixture.session.state.value.observed?.brightnessLevel)
        assertEquals(listOf(DeviceMode.DISPLAY, DeviceMode.DISPLAY), fixture.transport.modeWrites)
        assertEquals(DeviceMode.DISPLAY, fixture.session.state.value.pending.mode?.value)
    }

    @Test
    fun pendingCommandExpiresAfterThreeSecondsAndIsNotResent() {
        val fixture = fixture()
        fixture.ready(mode = DeviceMode.POMODORO, brightness = 5)
        fixture.session.setMode(DeviceMode.VISUALIZER)

        fixture.scope.advanceTimeBy(3_000)
        fixture.scope.runCurrent()
        assertNull(fixture.session.state.value.pending.mode)

        fixture.transport.connectionState.value = ConnectionState.Reconnecting
        fixture.transport.connectionState.value = ConnectionState.Ready
        fixture.scope.runCurrent()
        assertEquals(listOf(DeviceMode.VISUALIZER), fixture.transport.modeWrites)
    }

    @Test
    fun explicitPatternCommitIsOneDurableWriteAndNothingElse() {
        val fixture = fixture()
        fixture.ready(mode = DeviceMode.DISPLAY, brightness = 5)
        val pattern = Pattern("p1", "One", "1" + "0".repeat(63))

        fixture.session.commitPattern(pattern)

        assertEquals(1, fixture.transport.commits.size)
        assertEquals(0x80.toByte(), fixture.transport.commits.single()[0])
        assertTrue(fixture.transport.modeWrites.isEmpty())
        assertTrue(fixture.transport.previews.isEmpty())
    }

    @Test
    fun commitCancelsAQueuedLivePreviewBeforeWritingReliableFrame() {
        val fixture = fixture()
        fixture.ready(mode = DeviceMode.DISPLAY, brightness = 5)
        fixture.session.previewFrame("1" + "0".repeat(63))
        fixture.scope.runCurrent()
        fixture.session.previewFrame("01" + "0".repeat(62))

        fixture.session.commitPattern(Pattern("saved", "Saved", "001" + "0".repeat(61)))
        fixture.scope.advanceTimeBy(100)
        fixture.scope.runCurrent()

        // The first preview went out before the commit; the queued edit never did.
        assertEquals(1, fixture.transport.previews.size)
        assertEquals(1, fixture.transport.commits.size)
        assertEquals(0x20.toByte(), fixture.transport.commits.single()[0])
    }

    @Test
    fun previewKeepsResendingTheLastFrameWithoutANewEdit() {
        val fixture = fixture()
        fixture.ready(mode = DeviceMode.DISPLAY, brightness = 5)

        fixture.session.previewFrame("1" + "0".repeat(63))
        fixture.scope.runCurrent()
        assertEquals(1, fixture.transport.previews.size)

        fixture.scope.advanceTimeBy(2_000)
        fixture.scope.runCurrent()

        assertTrue(fixture.transport.previews.size >= 2)
        assertEquals(
            fixture.transport.previews.first().toList(),
            fixture.transport.previews.last().toList(),
        )
    }

    @Test
    fun cancelPreviewStopsFurtherResends() {
        val fixture = fixture()
        fixture.ready(mode = DeviceMode.DISPLAY, brightness = 5)

        fixture.session.previewFrame("1" + "0".repeat(63))
        fixture.scope.runCurrent()
        assertEquals(1, fixture.transport.previews.size)

        fixture.session.cancelPreview()
        fixture.scope.advanceTimeBy(10_000)
        fixture.scope.runCurrent()

        assertEquals(1, fixture.transport.previews.size)
    }

    @Test
    fun anEditDuringTheKeepAliveGapIsSentAtTheEditInterval() {
        val fixture = fixture()
        fixture.ready(mode = DeviceMode.DISPLAY, brightness = 5)
        fixture.session.previewFrame("1" + "0".repeat(63))
        fixture.scope.runCurrent()

        // Land mid keep-alive gap, where a plain long sleep would swallow the edit.
        fixture.scope.advanceTimeBy(500)
        fixture.scope.runCurrent()
        val before = fixture.transport.previews.size
        fixture.session.previewFrame("01" + "0".repeat(62))
        fixture.scope.advanceTimeBy(100)
        fixture.scope.runCurrent()

        assertEquals(before + 1, fixture.transport.previews.size)
        assertEquals(0x40.toByte(), fixture.transport.previews.last()[0])
    }

    @Test
    fun previewStopsWritingOnceTheLinkIsNoLongerReady() {
        val fixture = fixture()
        fixture.ready(mode = DeviceMode.DISPLAY, brightness = 5)
        fixture.session.previewFrame("1" + "0".repeat(63))
        fixture.scope.runCurrent()

        fixture.transport.connectionState.value = ConnectionState.Reconnecting
        fixture.scope.runCurrent()
        val afterDrop = fixture.transport.previews.size
        fixture.scope.advanceTimeBy(10_000)
        fixture.scope.runCurrent()

        assertEquals(afterDrop, fixture.transport.previews.size)
    }

    @Test
    fun committedFrameIsPendingUntilTheDeviceNotifiesItAndIsResentAfterAReconnect() {
        val fixture = fixture()
        fixture.ready(mode = DeviceMode.DISPLAY, brightness = 5)
        val pattern = Pattern("p1", "One", "1" + "0".repeat(63))
        val bits = FaceBits.fromBitsString(pattern.bits)

        fixture.session.commitPattern(pattern)
        fixture.scope.runCurrent()
        assertEquals(bits, fixture.session.state.value.effectiveCommittedFrame)

        // A commit that raced the link down must not be silently dropped.
        fixture.transport.connectionState.value = ConnectionState.Reconnecting
        fixture.scope.runCurrent()
        fixture.transport.displayCommittedFrame.value = FaceBits.EMPTY
        fixture.transport.connectionState.value = ConnectionState.Ready
        fixture.scope.runCurrent()
        assertEquals(2, fixture.transport.commits.size)

        fixture.transport.reportCommittedFrame(bits)
        fixture.scope.runCurrent()
        assertNull(fixture.session.state.value.pending.committedFrame)
        assertEquals(bits, fixture.session.state.value.observed?.committedFrame)
    }

    @Test
    fun aCommitTheDeviceNeverConfirmsStopsBeingShownAfterTheTtl() {
        val fixture = fixture()
        fixture.ready(mode = DeviceMode.DISPLAY, brightness = 5)
        val pattern = Pattern("p1", "One", "1" + "0".repeat(63))

        fixture.session.commitPattern(pattern)
        fixture.scope.runCurrent()
        assertEquals(FaceBits.fromBitsString(pattern.bits), fixture.session.state.value.effectiveCommittedFrame)

        // No notify ever arrives: a failed write, or a dropped notification.
        fixture.scope.advanceTimeBy(3_000)
        fixture.scope.runCurrent()

        assertNull(fixture.session.state.value.pending.committedFrame)
        assertEquals(FaceBits.EMPTY, fixture.session.state.value.effectiveCommittedFrame)
    }

    @Test
    fun stoppingTheVisualizerClearsThroughItsOwnStreamOnly() {
        val fixture = fixture()
        fixture.ready(mode = DeviceMode.VISUALIZER, brightness = 5)

        // AudioVisualizerManager was never started, so stop() is JVM-safe here.
        fixture.session.stopVisualizer()

        assertEquals(1, fixture.transport.visualizerWrites.size)
        assertEquals(List(8) { 0.toByte() }, fixture.transport.visualizerWrites.single().toList())
        assertTrue(fixture.transport.previews.isEmpty())
        assertTrue(fixture.transport.commits.isEmpty())
    }

    private fun fixture(): Fixture {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val transport = FakeDeviceTransport()
        val session = DeviceSession(
            transport = transport,
            parentScope = scope,
            nowRealtime = { scope.testScheduler.currentTime },
        )
        scope.runCurrent()
        return Fixture(scope, transport, session)
    }

    private data class Fixture(
        val scope: TestScope,
        val transport: FakeDeviceTransport,
        val session: DeviceSession,
    ) {
        fun ready(mode: Int, brightness: Int) {
            transport.currentMode.value = mode
            transport.brightness.value = brightness
            transport.pomodoroStatus.value = PomodoroStatus.DEFAULT
            transport.timerStatus.value = CountdownTimerStatus.DEFAULT
            transport.displayCommittedFrame.value = FaceBits.EMPTY
            transport.connectionState.value = ConnectionState.Ready
            scope.runCurrent()
        }
    }
}

private class FakeDeviceTransport : DeviceTransport {
    override val address = "AA:BB:CC:DD:EE:FF"
    override val connectionState = MutableStateFlow(ConnectionState.Disconnected)
    override val connectionFailure = MutableStateFlow<ConnectionFailure?>(null)
    override val reconnectAttempt = MutableStateFlow(0)
    override val currentMode = MutableStateFlow<Int?>(null)
    override val pomodoroStatus = MutableStateFlow<PomodoroStatus?>(null)
    override val timerStatus = MutableStateFlow<CountdownTimerStatus?>(null)
    override val displayCommittedFrame = MutableStateFlow<Long?>(null)
    override val brightness = MutableStateFlow<Int?>(null)
    override val deviceId = MutableStateFlow<String?>(null)
    override val deviceName = MutableStateFlow<String?>("CLumo-Test")
    override val buttonEvents = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 4)
    override val observations = MutableSharedFlow<DeviceObservation>(extraBufferCapacity = 8)

    val modeWrites = mutableListOf<Int>()
    val brightnessWrites = mutableListOf<Int>()
    val commits = mutableListOf<ByteArray>()
    val previews = mutableListOf<ByteArray>()
    val visualizerWrites = mutableListOf<ByteArray>()

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun dispose() = Unit
    override fun reconnectWithCacheRefresh() = Unit
    override fun writeMode(mode: Int) {
        modeWrites += mode
    }
    override fun commitFrame(rowBytes: ByteArray) {
        commits += rowBytes.copyOf()
    }
    override fun previewFrame(rowBytes: ByteArray) {
        previews += rowBytes.copyOf()
    }
    override fun writeVisualizerColumns(heights: ByteArray) {
        visualizerWrites += heights.copyOf()
    }
    override fun pomodoroSetDurations(workMin: Int, breakMin: Int) = Unit
    override fun pomodoroStart() = Unit
    override fun pomodoroPause() = Unit
    override fun pomodoroReset() = Unit
    override fun timerSetDuration(minutes: Int, seconds: Int) = Unit
    override fun timerStart() = Unit
    override fun timerPause() = Unit
    override fun timerCancel() = Unit
    override fun writeBrightness(level: Int) {
        brightnessWrites += level
    }

    fun reportMode(value: Int) {
        currentMode.value = value
        observations.tryEmit(DeviceObservation.Mode(value))
    }

    fun reportBrightness(value: Int) {
        brightness.value = value
        observations.tryEmit(DeviceObservation.Brightness(value))
    }

    fun reportCommittedFrame(value: Long) {
        displayCommittedFrame.value = value
        observations.tryEmit(DeviceObservation.DisplayCommittedFrame(value))
    }
}
