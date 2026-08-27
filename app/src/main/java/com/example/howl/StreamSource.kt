package com.example.howl

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class StreamSource(
    pulseRateHz: Int,
    bufferSize: Int,
    private val interpolate: Boolean,
    title: String = "Streaming",
) : PulseSource {

    companion object {
        const val MIN_BUFFER_SIZE = 2
        const val MAX_BUFFER_SIZE = 40
        const val MIN_PULSE_RATE_HZ = 1
        const val MAX_PULSE_RATE_HZ = MAIN_LOOP_RATE_HZ

        val STREAM_TIMEOUT_PULSE_MS = 5000.milliseconds
        val STREAM_TIMEOUT_GET_PULSE_MS = 15000.milliseconds
    }

    private val _displayName = MutableStateFlow(title)
    override val displayName = _displayName.asStateFlow()

    private val effectiveRate = pulseRateHz.coerceAtLeast(MIN_PULSE_RATE_HZ)
    private val _displayInfo = MutableStateFlow(buildString {
        append("${effectiveRate}Hz remote stream")
        if (interpolate) append(" (interpolated)")
    })
    override val displayInfo = _displayInfo.asStateFlow()

    override val duration: Double? = null
    override val seekable: Boolean = false
    override val shouldLoop: Boolean = false
    override val readyToPlay: Boolean = true
    override val latencyCompensation: Boolean = false

    private val pulseInterval = 1.0 / effectiveRate
    private val effectiveBufferSize = bufferSize.coerceAtLeast(MIN_BUFFER_SIZE)

    private val pulseBuffer = CircularBuffer<Pulse>(effectiveBufferSize)

    private var currentPulse = Pulse()
    private var targetPulse: Pulse? = null
    private var currentPulseTime = 0.0
    private var nextPulseTime = 0.0

    private val timeSource = TimeSource.Monotonic
    @Volatile private var lastPulseReceivedMark = timeSource.markNow()
    @Volatile private var lastGetPulseMark = timeSource.markNow()

    override fun getPulse(time: Double, deltaTime: Double): Pulse {
        lastGetPulseMark = timeSource.markNow()

        while (time >= nextPulseTime) {
            if (interpolate) {
                // Lookahead mode: promote target → current, then fetch new target.
                targetPulse?.let { currentPulse = it }
                targetPulse = pulseBuffer.removeFirstOrNull()
            } else {
                // Direct mode: buffer → current immediately, zero latency.
                pulseBuffer.removeFirstOrNull()?.let { currentPulse = it }
            }
            currentPulseTime = nextPulseTime
            nextPulseTime += pulseInterval
        }

        if (!interpolate) {
            return currentPulse
        }

        val target = targetPulse ?: return currentPulse

        val proportion = ((time - currentPulseTime) / pulseInterval)
            .coerceIn(0.0, 1.0)
            .toFloat()

        return currentPulse.blend(target, proportion)
    }

    fun addPulses(pulses: Collection<Pulse>) {
        lastPulseReceivedMark = timeSource.markNow()
        pulseBuffer.addAll(pulses, overwrite = true)
    }

    fun isTimedOut(): Boolean {
        return lastPulseReceivedMark.elapsedNow() > STREAM_TIMEOUT_PULSE_MS ||
                lastGetPulseMark.elapsedNow() > STREAM_TIMEOUT_GET_PULSE_MS
    }
}