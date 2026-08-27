package com.example.howl

import kotlin.math.roundToLong
import kotlin.time.DurationUnit
import kotlin.time.TimeMark
import kotlin.time.TimeSource.Monotonic.markNow

/**
 * Encapsulates the player's position tracking.
 *
 * Two modes:
 *  - TIME_ACCURATE (default): position is derived from wall-clock elapsed time.
 *    Self-corrects for loop jitter. Used for normal playback / video sync.
 *
 *  - PULSE_ACCURATE: position is derived from an integer tick counter, advancing
 *    exactly LOOP_INTERVAL_SECS per tick. Guarantees positions land on exact
 *    1/40s boundaries so HWL data is never interpolated. Used during recording.
 *
 * In both modes, [tickIndex] is maintained and always corresponds to the
 * nearest 1/40s boundary for the current position.
 */
class Playhead {

    enum class Mode { TIME_ACCURATE, PULSE_ACCURATE }

    var mode: Mode = Mode.TIME_ACCURATE
        private set

    // --- Tick-accurate state (integer, no floating-point drift) ---
    private var _tickIndex: Long = 0
    val tickIndex: Long get() = _tickIndex

    // --- Time-accurate state (wall-clock reference) ---
    private var anchorTime: TimeMark? = null // set to null when playback is stopped
    private var anchorPosition: Double = 0.0
    private var playbackSpeed: Double = 1.0

    // --- Fractional tick accumulator for PULSE_ACCURATE mode ---
    private var tickAccumulator: Double = 0.0

    val position: Double
        get() = when (mode) {
            Mode.TIME_ACCURATE -> {
                val elapsed = anchorTime?.elapsedNow()?.toDouble(DurationUnit.SECONDS) ?: 0.0
                anchorPosition + elapsed * playbackSpeed
            }
            Mode.PULSE_ACCURATE -> {
                _tickIndex * MAIN_LOOP_INTERVAL_SECS
            }
        }

    val quantizedPosition: Double
        get() = _tickIndex * MAIN_LOOP_INTERVAL_SECS

    fun tick() {
        when (mode) {
            Mode.TIME_ACCURATE -> {
                _tickIndex = (position / MAIN_LOOP_INTERVAL_SECS).roundToLong()
            }
            Mode.PULSE_ACCURATE -> {
                // Advance by playbackSpeed ticks per loop iteration,
                // using an accumulator to handle fractional speeds correctly
                // while keeping _tickIndex on exact 1/40s boundaries.
                tickAccumulator += playbackSpeed
                _tickIndex = tickAccumulator.roundToLong()
            }
        }
    }

    fun seek(to: Double) {
        anchorPosition = to
        if (anchorTime != null) {  // only re-anchor if currently playing
            anchorTime = markNow()
        }
        _tickIndex = (to / MAIN_LOOP_INTERVAL_SECS).roundToLong()
        tickAccumulator = _tickIndex.toDouble()
    }

    fun setSpeed(speed: Double) {
        anchorPosition = position  // capture current position before changing speed
        if (anchorTime != null) {  // only re-anchor if currently playing
            anchorTime = markNow()
        }
        playbackSpeed = speed
    }

    fun start(from: Double) {
        anchorPosition = from
        anchorTime = markNow()
        _tickIndex = (from / MAIN_LOOP_INTERVAL_SECS).roundToLong()
        tickAccumulator = _tickIndex.toDouble()
    }

    fun stop() {
        anchorPosition = position
        anchorTime = null
    }

    fun reset() {
        anchorTime = null
        _tickIndex = 0
        anchorPosition = 0.0
        playbackSpeed = 1.0
        tickAccumulator = 0.0
    }

    fun setMode(newMode: Mode) {
        if (mode == newMode) return

        when (newMode) {
            Mode.TIME_ACCURATE -> {
                anchorPosition = _tickIndex * MAIN_LOOP_INTERVAL_SECS
                if (anchorTime != null) {  // only re-anchor if currently playing
                    anchorTime = markNow()
                }
            }
            Mode.PULSE_ACCURATE -> {
                _tickIndex = (position / MAIN_LOOP_INTERVAL_SECS).roundToLong()
                tickAccumulator = _tickIndex.toDouble()
            }
        }
        mode = newMode
    }
}