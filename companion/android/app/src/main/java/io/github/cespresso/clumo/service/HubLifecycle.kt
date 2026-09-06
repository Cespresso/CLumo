package io.github.cespresso.clumo.service

import io.github.cespresso.clumo.domain.ConnectionState

/**
 * How long the hub waits with nothing to do before stopping. Long enough to outlast the gap
 * between a start and the onStartCommand that says why, and to let a link the app is about to
 * reconnect come back without the hub being torn down and started again around it.
 */
const val HUB_IDLE_GRACE_MS: Long = 5_000L

/**
 * Whether the hub has anything to protect. A link nobody is trying to use, never connected or
 * given up on after its retries, needs no foreground service behind it; everything from a
 * first connect attempt to a live Ready link does. Sessions themselves are not the measure:
 * a session stays registered after its device goes dark, so counting them would keep the hub
 * up forever after the first connection.
 */
fun hubHasWorkFor(links: Collection<ConnectionState>): Boolean = links.any { it != ConnectionState.Disconnected && it != ConnectionState.Error }
