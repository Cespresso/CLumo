package io.github.cespresso.clumo.service

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundServiceTypesTest {

    private val special = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    private val microphone = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

    @Test
    fun claimsSpecialUseWheneverTheHubIsUp() {
        assertEquals(
            special,
            foregroundServiceTypes(capturingAudio = false, recordAudioGranted = true),
        )
    }

    @Test
    fun addsMicrophoneWhileCapturing() {
        assertEquals(
            special or microphone,
            foregroundServiceTypes(capturingAudio = true, recordAudioGranted = true),
        )
    }

    @Test
    fun withholdsMicrophoneWithoutThePermissionBehindIt() {
        assertEquals(
            special,
            foregroundServiceTypes(capturingAudio = true, recordAudioGranted = false),
        )
    }
}
