package io.github.cespresso.clumo.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Acquisition only. Each snapshot also confirms the reader is alive, even if unchanged. */
internal class MediaVolumeSource(private val readPercent: () -> Int?) {
    fun snapshots(): Flow<Int?> =
        flow {
            while (true) {
                val value = try {
                    readPercent()?.takeIf { it in 0..100 }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                emit(value)
                delay(500)
            }
        }
}
