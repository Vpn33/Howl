package com.example.howl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import android.util.Log

import androidx.core.app.ServiceCompat
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class PlayerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playerJob: Job? = null

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "PlayerServiceChannel"
        const val NOTIFICATION_ID = 1
        const val TAG = "PlayerService"
        const val MAIN_LOOP_INTERVAL_NANOS = (MAIN_LOOP_INTERVAL_SECS * 1_000_000_000).toLong()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //Log.d(TAG, "onStartCommand")
        startForegroundService()
        startPlayerLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        HLog.v(TAG, "Player service ended")
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notification = createNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+ allows us to specify the foreground service type
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    0
                )
            }
        } catch (e: Exception) {
            // Android limits when apps are allowed to elevate to a foreground service. For example,
            // if the app is in the background, and we start playback due to a remote API request,
            // we will typically be blocked.
            val isFgNotAllowed =
                e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
            if (isFgNotAllowed) {
                HLog.w(
                    TAG,
                    "Not allowed to elevate playback service while Howl is running in the background. Turning off battery optimization for the app might help."
                )
            }
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Player Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background playback service"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Howl")
            .setContentText("Playing in the background.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startPlayerLoop() {
        if (playerJob?.isActive == true) return
        HLog.v(TAG, "Player service running")
        playerJob = serviceScope.launch {
            try {
                val startTime = System.nanoTime()
                var nextTickNanos = startTime
                var lastAdjustedTime: Double? = null
                val maxDurationNanos = Prefs.miscMaxPlaybackDurationHours.value.hours.inWholeNanoseconds
                while (isActive) {
                    nextTickNanos += MAIN_LOOP_INTERVAL_NANOS
                    //val startTime = System.nanoTime()
                    //Log.d(TAG, "Player loop start time=$currentTime")
                    val activeOutputs = OutputManager.outputs.value
                    val outputs = activeOutputs + Player.recorder
                    val playerState = Player.playerState.value

                    if (!playerState.isPlaying) break

                    val currentSource = playerState.activePulseSource ?: run {
                        Player.stopPlayer()
                        break
                    }

                    val currentPosition = Player.getCurrentPosition()
                    //Log.d(TAG, "$currentPosition")

                    val duration = currentSource.duration
                    if (duration != null && duration > 0 && currentPosition > duration) {
                        // We have passed the end of the playback source
                        if (currentSource.seekable && currentSource.shouldLoop) {
                            Player.seek(0.0)
                            lastAdjustedTime = null
                            continue
                        }
                        Player.stopPlayer()
                        break
                    }

                    val mainOptionsState = MainOptions.state.value
                    val playbackSpeed = Prefs.playerPlaybackSpeed.value.toDouble()
                    val timeAdjustment = Player.getTimeAdjustment()
                    val adjustedTime = (currentPosition + timeAdjustment * playbackSpeed).coerceAtLeast(0.0)
                    //Log.d(TAG, "Time adjustment: $timeAdjustment    Adjusted time: $adjustedTime")

                    // Compute delta, clamping to zero on first call or after a backward seek
                    val deltaTime = lastAdjustedTime
                        ?.let { (adjustedTime - it).coerceAtLeast(0.0) }
                        ?: 0.0
                    lastAdjustedTime = adjustedTime

                    //Log.d(TAG, "Adjusted time again: $adjustedTime")

                    val sourcePulse = Player.getPulse(adjustedTime, deltaTime)
                    val pulse = Player.applyPostProcessing(sourcePulse)

                    //Log.d(TAG, "< $pulse")

                    PulseHistory.addPulse(pulse)

                    val channelAPower = mainOptionsState.channelAPower
                    val channelBPower = mainOptionsState.channelBPower

                    if (channelAPower !in MainOptions.POWER_RANGE || channelBPower !in MainOptions.POWER_RANGE) {
                        HLog.e(TAG, "Critical safety error, invalid power values received in main loop")
                        break
                    }

                    for (output in outputs) {
                        if (!output.ready) continue

                        val pulseToSend = when {
                            output == Player.recorder -> sourcePulse
                            !mainOptionsState.globalMute -> pulse
                            output.sendSilenceWhenMuted -> Pulse() // silence
                            else -> continue // skip entirely
                        }

                        output.receivePulse(pulseToSend, channelAPower, channelBPower)
                    }

                    Player.playhead.tick()
                    Player.setPlayerPosition(adjustedTime)

                    MainOptions.autoIncreasePower(MAIN_LOOP_INTERVAL_SECS)

                    val now = System.nanoTime()

                    if (now - startTime >= maxDurationNanos) {
                        HLog.w(TAG, "Maximum playback duration of ${Prefs.miscMaxPlaybackDurationHours.value}h reached. Stopping player.")
                        Player.stopPlayer()
                        break
                    }

                    val sleepNanos = nextTickNanos - now

                    if (sleepNanos > 0) {
                        delay(sleepNanos.nanoseconds)
                    } else if (sleepNanos < -MAIN_LOOP_INTERVAL_NANOS) {
                        // Overrun guard: if we're more than one full interval behind, snap forward to
                        // the current time to prevent a rapid burst of catch up ticks.
                        nextTickNanos = now
                    }
                }
            } catch (_: CancellationException) {
                // Normal cancellation
                HLog.v(TAG, "Foreground service cancelled")
            } finally {
                stopSelf()
            }
        }
    }
}