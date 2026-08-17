package com.example.howl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * Central store for recent pulse data.
 *
 * Two consumers:
 * - **Charts** observe [revision] and pull data from the circular buffer on demand.
 * - **Meters / other UI** observe [lastPulse] or [lastPulseWithPlayerState]
 */
object PulseHistory {

    const val PULSE_HISTORY_SIZE = 301 // 7.5 seconds at 40 pulses/sec (+1 so line chart last point can join up)

    private val buffer = CircularBuffer<Pulse>(PULSE_HISTORY_SIZE)

    // ---- Revision counter for chart invalidation ----
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    // ---- Kept for meters and other non-chart UI ----

    private val _lastPulse = MutableStateFlow(Pulse())
    val lastPulse: StateFlow<Pulse> = _lastPulse.asStateFlow()

    val lastPulseWithPlayerState: Flow<Pulse> = combine(
        _lastPulse,
        Player.playerState
    ) { pulse, playerState ->
        if (playerState.isPlaying) pulse else Pulse()
    }

    fun addPulse(pulse: Pulse) {
        buffer.add(pulse, overwrite = true)
        _lastPulse.update { pulse }
        _revision.update { it + 1 }
    }

    fun clear() {
        buffer.clear()
        _lastPulse.update { Pulse() }
        _revision.update { it + 1 }
    }

    /** Returns up to [count] most-recent pulses, oldest first. */
    fun getRecentPulses(count: Int): List<Pulse> = buffer.lastN(count)
}