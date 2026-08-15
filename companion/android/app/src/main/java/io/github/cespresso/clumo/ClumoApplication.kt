package io.github.cespresso.clumo

import android.app.Application
import android.content.Context

class ClumoApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * The one way to reach the graph. A widget tap can start the process straight into a service,
 * so this has to work from any context, not just from an activity.
 */
val Context.appContainer: AppContainer
    get() = (applicationContext as ClumoApplication).container
