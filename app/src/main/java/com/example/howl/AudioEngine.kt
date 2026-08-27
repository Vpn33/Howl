package com.example.howl

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Audio source that fills stereo PCM-FLOAT buffers on demand. */
interface AudioBlockProvider {
    /** Fill [buffer] with [frameCount] interleaved stereo frames. Must return swiftly. */
    fun onFillBlock(buffer: FloatArray, frameCount: Int)
}

enum class PlaybackState {
    Idle,
    Playing,
}

/**
 * Manages AudioTrack lifecycle, the producer thread, and the playback state machine.
 * Sample generation is delegated to an [AudioBlockProvider].
 * All mutable state is guarded by a single [ReentrantLock] + [Condition].
 */
class AudioEngine(
    private val mainLoopIntervalSecs: Double = MAIN_LOOP_INTERVAL_SECS,
    private val bufferMultiplier: Double = 8.0
) {
    companion object {
        private const val TAG = "AudioEngine"
        private const val CHANNELS = 2
        private const val BYTES_PER_SAMPLE = 4
        private const val BYTES_PER_FRAME = CHANNELS * BYTES_PER_SAMPLE
        private const val JOIN_TIMEOUT_MS = 1000L
        private const val ATTRIBUTION_TAG = "audioPlayback"
        private const val FADE_LENGTH_SECS = 0.5
    }

    // ── Public read-only configuration ─────────────────────────

    var sampleRate: Int = 48000
        private set

    var bufferSizeFrames: Int = 0
        private set

    var blockSizeFrames: Int = 0
        private set

    // ── Synchronisation ────────────────────────────────────────

    private val lock = ReentrantLock()
    private val stateChanged: Condition = lock.newCondition()

    // Guarded by [lock].
    private var playbackState: PlaybackState = PlaybackState.Idle
    private var running: Boolean = false
    private var currentProvider: AudioBlockProvider? = null

    // ── Internal handles ───────────────────────────────────────

    private var audioTrack: AudioTrack? = null
    private var producerThread: Thread? = null

    // ── Lifecycle ──────────────────────────────────────────────

    /** Creates the AudioTrack and starts the producer thread. Returns false on failure. */
    fun initialise(): Boolean {
        lock.withLock {
            if (running) return true
        }
        if (!createAudioTrack()) {
            return false
        }
        lock.withLock {
            running = true
        }
        startProducerThread()
        return true
    }

    /** Stops immediately, joins the producer thread, and releases resources. */
    fun destroy() {
        lock.withLock {
            running = false
            playbackState = PlaybackState.Idle
            stateChanged.signalAll()
        }

        producerThread?.join(JOIN_TIMEOUT_MS)
        if (producerThread?.isAlive == true) {
            Log.w(TAG, "Producer thread did not exit within ${JOIN_TIMEOUT_MS}ms; interrupting")
            producerThread?.interrupt()
            producerThread?.join(JOIN_TIMEOUT_MS)
        }
        producerThread = null

        try {
            audioTrack?.stop()
        } catch (e: IllegalStateException) {
            Log.d(TAG, "AudioTrack.stop() during destroy: ${e.message}")
        }

        audioTrack?.release()
        audioTrack = null
        Log.d(TAG, "AudioTrack released")
    }

    /** Dynamically sets the active AudioBlockProvider. */
    fun setProvider(newProvider: AudioBlockProvider?) {
        lock.withLock {
            if (currentProvider == newProvider) return
            currentProvider = newProvider
            if (newProvider == null && playbackState != PlaybackState.Idle) {
                playbackState = PlaybackState.Idle
            }
            stateChanged.signalAll()
        }
    }

    /** Updates to playing state, signalling the producer thread. */
    fun play() {
        lock.withLock {
            if (playbackState == PlaybackState.Playing) return
            playbackState = PlaybackState.Playing
            stateChanged.signalAll()
        }
    }

    /** Updates to idle state, signalling the producer thread. */
    fun stop() {
        lock.withLock {
            if (playbackState != PlaybackState.Playing) return
            playbackState = PlaybackState.Idle
            stateChanged.signalAll()
        }
    }

    val isPlaying: Boolean
        get() = lock.withLock { playbackState == PlaybackState.Playing }

    /** Blocks until the engine reaches [PlaybackState.Idle]. */
    fun awaitIdle() {
        lock.withLock {
            while (playbackState != PlaybackState.Idle) {
                stateChanged.await()
            }
        }
    }

    // ── AudioTrack creation ────────────────────────────────────

    private fun createAudioTrack(): Boolean {
        sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        HLog.v(TAG, "Device native sample rate: ${sampleRate}Hz")

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        Log.d(TAG, "Minimum buffer size (bytes): $minBufferSize")

        val desiredBufferSizeFrames = (sampleRate * mainLoopIntervalSecs * bufferMultiplier).toInt()
        val bufferSizeInBytes = maxOf(desiredBufferSizeFrames * BYTES_PER_FRAME, minBufferSize)
        bufferSizeFrames = bufferSizeInBytes / BYTES_PER_FRAME
        blockSizeFrames = (mainLoopIntervalSecs * sampleRate).toInt()

        HLog.v(TAG, "Using $bufferSizeFrames frame buffer and $blockSizeFrames frame blocks.")

        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeInBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)

        // Attach attribution context so the system can match this
        // AudioTrack to the <attribution> tag in the manifest.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
            val attributionContext =  // API 30
                HowlApp.context.createAttributionContext(ATTRIBUTION_TAG)
            builder.setContext(attributionContext)
        }

        audioTrack = builder.build()

        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            Log.d(TAG, "AudioTrack initialised")
            return true
        } else {
            Log.e(TAG, "Failed to initialise AudioTrack, state: ${audioTrack?.state}")
            return false
        }
    }

    // ── Producer thread and its helper functions ────────────────

    private fun startProducerThread() {
        check(producerThread == null) { "startProducerThread called but producerThread already set." }

        producerThread = thread(name = "AudioThread", priority = Thread.MAX_PRIORITY) {
            val buffer = FloatArray(blockSizeFrames * CHANNELS)
            val fader = VolumeFader(blockSizeFrames, FADE_LENGTH_SECS, sampleRate)

            try {
                while (true) {
                    val state = lock.withLock {
                        if (!running) throw InterruptedException()
                        playbackState
                    }

                    val targetVolume = if (state == PlaybackState.Playing) 1f else 0f
                    val startVolume = fader.currentVolume
                    val endVolume = fader.stepTowards(targetVolume)

                    val track = audioTrack ?: break

                    val isAudible = startVolume > 0f || endVolume > 0f

                    if (isAudible) {
                        fader.isFadedOut = false
                        fillAndApplyGain(buffer, startVolume, endVolume)
                        writeAndPlay(track, buffer)
                    } else {
                        handleSilence(track, buffer, fader)
                    }
                }
            } catch (_: InterruptedException) {
                // Normal exit when running becomes false
            }
        }
    }

    private class VolumeFader(blockSizeFrames: Int, fadeLengthSecs: Double, sampleRate: Int) {
        var currentVolume = 0f
        var isFadedOut = false
        private val fadeStep = blockSizeFrames.toFloat() / (fadeLengthSecs * sampleRate).toFloat()

        fun stepTowards(target: Float): Float {
            val delta = target - currentVolume
            val step = delta.coerceIn(-fadeStep, fadeStep)
            currentVolume += step
            return currentVolume
        }
    }

    private fun fillAndApplyGain(buffer: FloatArray, startVolume: Float, endVolume: Float) {
        val provider = lock.withLock { currentProvider }
        if (provider != null) {
            provider.onFillBlock(buffer, blockSizeFrames)
        } else {
            buffer.fill(0f)
        }

        if (startVolume < 1f || endVolume < 1f) {
            val volumeStep = (endVolume - startVolume) / blockSizeFrames
            var vol = startVolume
            for (frame in 0 until blockSizeFrames) {
                buffer[frame * 2] *= vol
                buffer[frame * 2 + 1] *= vol
                vol += volumeStep
            }
        }
    }

    private fun writeAndPlay(track: AudioTrack, buffer: FloatArray) {
        // Write before playing to avoid startup underruns
        track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)

        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            try {
                track.play()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "AudioTrack.play() failed: ${e.message}")
            }
        }
    }

    private fun handleSilence(track: AudioTrack, buffer: FloatArray, fader: VolumeFader) {
        if (!fader.isFadedOut) {
            // Write one block of zeros to ensure the absolute end of the buffer is silent
            buffer.fill(0f)
            track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            fader.isFadedOut = true
        }

        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            val stateChangedToPlaying = awaitDrainOrStateChange()
            if (!stateChangedToPlaying) {
                // Timed out waiting for state change, meaning drain completed
                stopAndFlushTrack(track)
            } else {
                // State became Playing during the drain wait, abort stopping
                fader.isFadedOut = false
            }
        } else {
            // Track is already stopped safely. Just wait indefinitely for a state change.
            awaitStateChange()
        }
    }

    private fun awaitDrainOrStateChange(): Boolean {
        val drainDuration = (bufferSizeFrames * 1000L / sampleRate).milliseconds + 50.milliseconds
        val deadline = TimeSource.Monotonic.markNow() + drainDuration

        lock.withLock {
            while (running && playbackState == PlaybackState.Idle && TimeSource.Monotonic.markNow() < deadline) {
                val remaining = deadline - TimeSource.Monotonic.markNow()
                if (remaining.isPositive()) {
                    stateChanged.awaitNanos(remaining.inWholeNanoseconds)
                }
            }
            if (!running) throw InterruptedException()
            return playbackState == PlaybackState.Playing
        }
    }

    private fun awaitStateChange() {
        lock.withLock {
            while (running && playbackState == PlaybackState.Idle) {
                stateChanged.await()
            }
            if (!running) throw InterruptedException()
        }
    }

    private fun stopAndFlushTrack(track: AudioTrack) {
        try {
            track.stop()
        } catch (e: IllegalStateException) {
            Log.d(TAG, "AudioTrack.stop() failed: ${e.message}")
        }
        try {
            track.flush()
        } catch (e: IllegalStateException) {
            Log.d(TAG, "AudioTrack.flush() failed: ${e.message}")
        }
    }
}