package io.github.cespresso.clumo.data.session

import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStateMapTest {

    @Test
    fun stateChangePropagatesWithoutReplacingSessionMap() =
        runTest {
            val first = MutableStateFlow(DeviceSessionState(ConnectionState.Connecting))
            val second = MutableStateFlow(DeviceSessionState(ConnectionState.Disconnected))
            val emissions = mutableListOf<Map<String, DeviceSessionState>>()
            val job = backgroundScope.launch {
                sessionStateMap(mapOf("one" to first, "two" to second)).collect {
                    emissions += it
                }
            }

            testScheduler.runCurrent()
            first.value = DeviceSessionState(ConnectionState.Ready)
            testScheduler.runCurrent()

            assertEquals(ConnectionState.Ready, emissions.last().getValue("one").link)
            assertEquals(ConnectionState.Disconnected, emissions.last().getValue("two").link)
            job.cancel()
        }

    @Test
    fun emptySessionMapEmitsEmptyStateMap() =
        runTest {
            assertEquals(emptyMap<String, DeviceSessionState>(), sessionStateMap(emptyMap()).first())
        }
}
