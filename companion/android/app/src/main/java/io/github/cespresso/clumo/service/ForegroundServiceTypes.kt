package io.github.cespresso.clumo.service

import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * The foreground service types the hub claims right now.
 *
 * Special use covers the BLE link the hub exists for and is always claimed. The microphone
 * type is what keeps the visualizer capturing once the app leaves the foreground, but from
 * Android 14 a type may only be claimed with the permission behind it in hand, so it is added
 * only while a session is really capturing: a hub that is merely holding a link must not
 * present itself as a microphone user.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal fun foregroundServiceTypes(capturingAudio: Boolean, recordAudioGranted: Boolean): Int =
    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
        if (capturingAudio && recordAudioGranted) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
