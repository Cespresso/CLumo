package io.github.cespresso.clumo.service

import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * The foreground service types the hub claims right now.
 *
 * Special use is always claimed and is what the hub falls back to. From Android 14 a type may
 * only be claimed with the permission behind it in hand, and both of the others rest on a
 * runtime permission the user may not have granted, or may have had revoked: a widget tap can
 * start the hub without BLUETOOTH_CONNECT, and RECORD_AUDIO is only asked for on the
 * visualizer screen. So connected device is claimed whenever BLUETOOTH_CONNECT is held, and
 * microphone only while a session is really capturing; a hub that is merely holding a link
 * must not present itself as a microphone user.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal fun foregroundServiceTypes(
    capturingAudio: Boolean,
    recordAudioGranted: Boolean,
    bluetoothConnectGranted: Boolean,
): Int {
    var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    if (bluetoothConnectGranted) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
    if (capturingAudio && recordAudioGranted) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    return types
}
