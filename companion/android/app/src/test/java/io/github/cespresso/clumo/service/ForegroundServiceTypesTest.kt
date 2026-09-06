package io.github.cespresso.clumo.service

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundServiceTypesTest {

    private val special = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    private val connected = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
    private val microphone = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

    private fun types(
        capturingAudio: Boolean = false,
        recordAudioGranted: Boolean = true,
        bluetoothConnectGranted: Boolean = true,
    ) = foregroundServiceTypes(capturingAudio, recordAudioGranted, bluetoothConnectGranted)

    @Test
    fun specialUseIsAlwaysClaimed() {
        assertEquals(
            special,
            types(recordAudioGranted = false, bluetoothConnectGranted = false),
        )
    }

    @Test
    fun connectedDeviceFollowsTheBluetoothPermission() {
        assertEquals(special or connected, types())
        assertEquals(special, types(bluetoothConnectGranted = false))
    }

    @Test
    fun microphoneIsClaimedOnlyWhileCapturingWithThePermission() {
        assertEquals(special or connected or microphone, types(capturingAudio = true))
        assertEquals(special or connected, types(capturingAudio = false))
        assertEquals(special or connected, types(capturingAudio = true, recordAudioGranted = false))
    }
}
