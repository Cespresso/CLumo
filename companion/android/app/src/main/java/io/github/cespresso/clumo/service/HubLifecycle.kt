package io.github.cespresso.clumo.service

import io.github.cespresso.clumo.domain.ConnectionState

/**
 * Whether the hub has anything to protect. A link nobody is trying to use, never connected or
 * given up on after its retries, needs no foreground service behind it; everything from a
 * first connect attempt to a live Ready link does. Sessions themselves are not the measure:
 * a session stays registered after its device goes dark, so counting them would keep the hub
 * up forever after the first connection.
 */
fun hubHasWorkFor(links: Collection<ConnectionState>): Boolean = links.any { it != ConnectionState.Disconnected && it != ConnectionState.Error }
