package io.github.cespresso.clumo.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import io.github.cespresso.clumo.service.DeviceHubService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Binds to [DeviceHubService] and exposes the service instance to the UI.
 */
class HubViewModel(application: Application) : AndroidViewModel(application) {

    private val _service = MutableStateFlow<DeviceHubService?>(null)
    val service: StateFlow<DeviceHubService?> = _service.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            _service.value = (binder as DeviceHubService.LocalBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
        }
    }

    init {
        val ctx = getApplication<Application>()
        DeviceHubService.start(ctx)
        ctx.bindService(
            Intent(ctx, DeviceHubService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    override fun onCleared() {
        getApplication<Application>().unbindService(connection)
    }
}
