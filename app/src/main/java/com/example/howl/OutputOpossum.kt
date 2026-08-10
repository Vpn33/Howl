package com.example.howl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class OutputOpossum {

    companion object {
        const val TAG = "OutputOpossum"
        const val PULSES_PER_BATCH = 4
        const val PULSE_INTERVAL_MS = 25L
        val AMPLITUDE_RANGE: IntRange = 0..100
        val PROBABILITY_RANGE: ClosedFloatingPointRange<Float> = 0.0f..1.0f
    }

    private var playbackJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.Default)

    var channelAAmp: Int = 50
    var channelBAmp: Int = 50
    var activity: Activity? = null
    var activityChangeProbability: Float = 0.0f
    var currentActivityType: ActivityType = ActivityType.LICKS

    private var lastTime = 0.0
    private var lastUpdateTime = 0.0

    var onActivityChanged: ((ActivityType) -> Unit)? = null

    private fun switchActivity(type: ActivityType) {
        currentActivityType = type
        activity = type.create().apply { initialise() }
        lastUpdateTime = lastTime
        onActivityChanged?.invoke(type)
    }

    private fun maybeRandomActivityChange() {
        if (activityChangeProbability <= 0f) return
        val timeDelta = lastTime - lastUpdateTime
        val probability = (activityChangeProbability * 3.0 * timeDelta) / 60.0
        if (Random.nextDouble() < probability) {
            val excluded = Prefs.activityExcludedFromRandom.value.toSet()
            val candidates = ActivityType.entries.filter {
                it !in excluded && it != currentActivityType
            }
            val nextType = candidates.randomOrNull()
                ?: ActivityType.entries.filter { it != currentActivityType }.randomOrNull()
                ?: ActivityType.entries.random()
            switchActivity(nextType)
        }
    }

    fun start(onSend: (ByteArray) -> Unit) {
        stop()
        lastTime = 0.0
        lastUpdateTime = 0.0
        switchActivity(currentActivityType)
        playbackJob = scope.launch {
            val pulses = mutableListOf<Pulse>()
            while (isActive) {
                pulses.clear()
                for (i in 0 until PULSES_PER_BATCH) {
                    val dt = PULSE_INTERVAL_MS / 1000.0
                    lastTime += dt
                    activity?.runSimulation(dt)
                    val pulse = activity?.getPulse() ?: Pulse()
                    pulses.add(pulse)
                }
                // Check for random activity change once per batch (every 100ms),
                // matching the Player's updateState frequency
                maybeRandomActivityChange()
                val b0 = buildB0Command(pulses)
                onSend(b0)
                delay(PULSE_INTERVAL_MS * PULSES_PER_BATCH)
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
    }

    fun isPlaying(): Boolean = playbackJob?.isActive == true

    private fun buildB0Command(pulses: List<Pulse>): ByteArray {
        val ampA = ByteArray(4)
        val ampB = ByteArray(4)
        for (i in 0 until 4) {
            ampA[i] = (pulses[i].ampA * 100).toInt().coerceIn(0, 100).toByte()
            ampB[i] = (pulses[i].ampB * 100).toInt().coerceIn(0, 100).toByte()
        }
        return byteArrayOf(
            0xB0.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            *ampA,
            0x00, 0x00, 0x00, 0x00,
            *ampB
        )
    }

    private fun buildB3Command(chAmp: Int, chBamp: Int): ByteArray {
        return byteArrayOf(
            0xB3.toByte(),
            chAmp.toByte(),
            chBamp.toByte()
        )
    }
}
