package com.example.howl

import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class OutputOpossum : BluetoothOutput("47L127000") {
    override val type = OutputType.OPOSSUM
    override val pulseDivider = 1
    override val pulseBatchSize = 4
    override val sendSilenceWhenMuted = false
    override var minFrequencyLimit: Int = 5
    override var maxFrequencyLimit: Int = 200
    override var ready = false
    override val hasCalibrationAndTweaks: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var setupJob: Job? = null
    private var batteryPollJob: Job? = null
    private var notificationJob: Job? = null
    private var playerSyncJob: Job? = null

    private var lastSentChannelA = -1
    private var lastSentChannelB = -1

    private var currentChannelA = 0
    private var currentChannelB = 0

    private var playerSyncEnabled = false
    private var selectedActivity: ActivityType = ActivityType.LICKS
    private var activityChangeProbability: Float = Prefs.activityChangeProbability.value

    private val _swapChannels = MutableStateFlow(false)
    val swapChannels: StateFlow<Boolean> = _swapChannels.asStateFlow()

    private var _onB3Response: ((Int, Int) -> Unit)? = null
    private var _onIsPlayingChanged: ((Boolean) -> Unit)? = null
    private var _onSelectedActivityChanged: ((ActivityType) -> Unit)? = null
    private var _onSyncChannelsChanged: ((Boolean) -> Unit)? = null
    private var _onSwapChannelsChanged: ((Boolean) -> Unit)? = null

    companion object {
        const val TAG = "OutputOpossum"
        val AMPLITUDE_RANGE: IntRange = 0..200
        val PROBABILITY_RANGE: ClosedFloatingPointRange<Float> = 0.0f..1.0f

        val mainServiceUUID: UUID = UUID.fromString("0000180C-0000-1000-8000-00805f9b34fb")
        val writeCharacteristicUUID: UUID = UUID.fromString("0000150A-0000-1000-8000-00805f9b34fb")
        val notifyCharacteristicUUID: UUID = UUID.fromString("0000150B-0000-1000-8000-00805f9b34fb")
        val batteryServiceUUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
        val batteryCharacteristicUUID: UUID = UUID.fromString("00001500-0000-1000-8000-00805f9b34fb")
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _syncChannels = MutableStateFlow(true)
    val syncChannels: StateFlow<Boolean> = _syncChannels.asStateFlow()

    fun setChannelFlows(channelAAmpFlow: StateFlow<Int>, channelBAmpFlow: StateFlow<Int>) {
        // channel state is now owned by ViewModel, we receive updates via onChannelValuesChanged
    }

    fun setSyncChannels(enabled: Boolean) {
        _syncChannels.value = enabled
        _onSyncChannelsChanged?.invoke(enabled)
        if (enabled) {
            val curA = currentChannelA
            currentChannelB = curA
            pulseGenerator?.channelBAmp = curA
            sendB3Command(currentChannelA, currentChannelB)
        }
    }

    fun setOnSyncChannelsChanged(callback: (Boolean) -> Unit) {
        _onSyncChannelsChanged = callback
    }

    fun setOnB3Response(callback: (Int, Int) -> Unit) {
        _onB3Response = callback
    }

    fun setOnIsPlayingChanged(callback: (Boolean) -> Unit) {
        _onIsPlayingChanged = callback
    }

    fun setOnSelectedActivityChanged(callback: (ActivityType) -> Unit) {
        _onSelectedActivityChanged = callback
    }

    fun onChannelValuesChanged(channelA: Int, channelB: Int) {
        currentChannelA = channelA
        currentChannelB = channelB
        pulseGenerator?.channelAAmp = channelA
        pulseGenerator?.channelBAmp = channelB
        if (!playerSyncEnabled) {
            sendB3Command(channelA, channelB)
        }
    }

    fun setPlayerSyncEnabled(enabled: Boolean) {
        if (enabled == playerSyncEnabled) return
        playerSyncEnabled = enabled
        if (enabled) {
            stopPlayback()
            startPlayerSyncLoop()
        } else {
            stopPlayerSyncLoop()
        }
    }

    fun setSwapChannels(enabled: Boolean) {
        _swapChannels.value = enabled
        _onSwapChannelsChanged?.invoke(enabled)
        if (handlerIsConnected()) {
            sendB3Command(currentChannelA, currentChannelB)
        }
    }

    fun setOnSwapChannelsChanged(callback: (Boolean) -> Unit) {
        _onSwapChannelsChanged = callback
    }

    fun setSelectedActivity(type: ActivityType) {
        selectedActivity = type
        pulseGenerator?.currentActivityType = type
    }

    fun setActivityChangeProbability(probability: Float) {
        val clamped = probability.coerceIn(0f, 1f)
        activityChangeProbability = clamped
        pulseGenerator?.activityChangeProbability = clamped
    }

    private var pulseGenerator: OpossumPulseGenerator? = null

    fun startPlayback() {
        if (playerSyncEnabled) {
            playerSyncEnabled = false
            stopPlayerSyncLoop()
        }
        sendB3Command(currentChannelA, currentChannelB)
        pulseGenerator = OpossumPulseGenerator().apply {
            currentActivityType = selectedActivity
            activityChangeProbability = this@OutputOpossum.activityChangeProbability
            channelAAmp = currentChannelA
            channelBAmp = currentChannelB
            onActivityChanged = { newType ->
                selectedActivity = newType
                _onSelectedActivityChanged?.invoke(newType)
            }
        }
        pulseGenerator?.start { command ->
            scope.launch {
                writeCommand(command, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            }
        }
        _isPlaying.value = true
        _onIsPlayingChanged?.invoke(true)
    }

    fun stopPlayback() {
        pulseGenerator?.stop()
        pulseGenerator = null
        _isPlaying.value = false
        _onIsPlayingChanged?.invoke(false)
    }

    override fun initialise() {
        setupJob = scope.launch {
            handler.connectionState.collect { state ->
                when (state) {
                    ConnectionStatus.Connected -> startSetup()
                    ConnectionStatus.Disconnected -> cleanup()
                    else -> { }
                }
            }
        }
    }

    override fun destroy() {
        setupJob?.cancel()
        cleanup()
        stopPlayback()
        stopPlayerSyncLoop()
        scope.cancel()
    }

    private suspend fun startSetup() {
        handler.enableNotifications(mainServiceUUID, notifyCharacteristicUUID, important = true)
        handler.enableNotifications(batteryServiceUUID, batteryCharacteristicUUID, important = true)
        startNotificationListener()
        startBatteryPolling()
        startPressureReporting()
        ready = true
        HLog.d(TAG, "Opossum setup complete.")
    }

    private fun cleanup() {
        ready = false
        batteryPollJob?.cancel()
        batteryPollJob = null
        notificationJob?.cancel()
        notificationJob = null
    }

    private fun startNotificationListener() {
        notificationJob?.cancel()
        notificationJob = scope.launch {
            handler.notifications.collect { notification ->
                when (notification.characteristicUuid) {
                    notifyCharacteristicUUID -> parseNotifyMessage(notification.data)
                    batteryCharacteristicUUID -> parseBatteryLevel(notification.data)
                }
            }
        }
    }

    private fun parseNotifyMessage(data: ByteArray) {
        if (data.size < 1) return
        when (data[0].toInt() and 0xFF) {
            0xB3 -> {
                if (data.size >= 3) {
                    val channelA = data[1].toInt() and 0xFF
                    val channelB = data[2].toInt() and 0xFF
                    _onB3Response?.invoke(channelA, channelB)
                    sendB2Command(channelA, channelB)
                }
            }
            0xD0 -> { }
        }
    }

    private fun parseBatteryLevel(data: ByteArray) {
        if (data.isNotEmpty()) {
            handler.setBatteryLevel(data[0].toInt() and 0xFF)
        }
    }

    private fun startBatteryPolling() {
        batteryPollJob?.cancel()
        batteryPollJob = scope.launch {
            var first = true
            while (isActive) {
                val result = handler.readCharacteristic(
                    serviceUuid = batteryServiceUUID,
                    characteristicUuid = batteryCharacteristicUUID,
                    important = first
                )
                result.onSuccess { data ->
                    if (data.isNotEmpty()) {
                        handler.setBatteryLevel(data[0].toInt() and 0xFF)
                    }
                }
                first = false
                delay(60000)
            }
        }
    }

    private fun startPressureReporting() {
        val command = byteArrayOf(0x50.toByte(), 0x01, 0x01)
        scope.launch { writeCommand(command) }
    }

    private fun stopPressureReporting() {
        val command = byteArrayOf(0x50.toByte(), 0x01, 0x00)
        scope.launch { writeCommand(command) }
    }

    private fun startPlayerSyncLoop() {
        stopPlayerSyncLoop()
        HLog.d(TAG, "Player Sync loop started")
        lastSentChannelA = -1
        lastSentChannelB = -1

        playerSyncJob = scope.launch {
            // Match 1.1.1: each loop iteration samples 4 pulses at 25ms intervals
            // and sends them as a single B0 frame. Loop period is 100ms. When the
            // player is paused, no B0 is sent at all; the device idles after the
            // last received frame (this is what makes stopping instant, exactly
            // like the 1.1.1 version).
            val pulses = mutableListOf<Pulse>()
            val pulseIntervalMs = 25L

            while (isActive && playerSyncEnabled) {
                if (Player.playerState.value.isPlaying) {
                    pulses.clear()
                    val currentPos = Player.getCurrentPosition()

                    // Sample 4 pulses at 25ms intervals
                    for (i in 0 until 4) {
                        if (!Player.playerState.value.isPlaying) break
                        val time = currentPos + (i * pulseIntervalMs / 1000.0)
                        val dt = pulseIntervalMs / 1000.0
                        val pulse = Player.getPulse(time, dt)
                        pulses.add(pulse)
                    }

                    if (pulses.isNotEmpty() && handlerIsConnected()) {
                        val scaledPulses = pulses.map {
                            Pulse(
                                ampA = (it.ampA * currentChannelA / 200.0f).coerceIn(0f, 1f),
                                ampB = (it.ampB * currentChannelB / 200.0f).coerceIn(0f, 1f),
                                freqA = it.freqA,
                                freqB = it.freqB
                            )
                        }

                        val b0Command = buildB0FromPulses(scaledPulses)
                        writeCommand(b0Command, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                    }
                }

                // Send B3 when channel values change
                if (currentChannelA != lastSentChannelA || currentChannelB != lastSentChannelB) {
                    lastSentChannelA = currentChannelA
                    lastSentChannelB = currentChannelB
                    HLog.d(TAG, "Player Sync: B3 A=$currentChannelA B=$currentChannelB")
                    sendB3Command(currentChannelA, currentChannelB)
                }

                delay(pulseIntervalMs * 4)
            }
            HLog.d(TAG, "Player Sync loop ended")
        }
    }

    private fun stopPlayerSyncLoop() {
        playerSyncJob?.cancel()
        playerSyncJob = null
        HLog.d(TAG, "Player Sync loop stopped")
    }

    override fun start() {
        if (playerSyncEnabled) {
            sendB3Command(currentChannelA, currentChannelB)
            startPressureReporting()
        } else {
            stopPlayback()
        }
        super.start()
    }

    override fun stop() {
        if (!playerSyncEnabled) {
            stopPlayback()
            stopPressureReporting()
        }
        super.stop()
    }

    override fun sendPulses(pulses: List<OutputPulse>) {
        if (!playerSyncEnabled || !handlerIsConnected()) return
        val b0Command = buildB0FromOutputPulses(pulses)
        scope.launch {
            writeCommand(b0Command, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        }
    }

    override val settingsUI: (@Composable () -> Unit) = {
        val syncCh by syncChannels.collectAsStateWithLifecycle()
        val swapCh by swapChannels.collectAsStateWithLifecycle()
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync channels",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (syncCh) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = syncCh,
                    onCheckedChange = { setSyncChannels(it) }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Swap channels",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (swapCh) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = swapCh,
                    onCheckedChange = { setSwapChannels(it) }
                )
            }
        }
    }

    private fun handlerIsConnected(): Boolean {
        return handler.connectionState.value == ConnectionStatus.Connected
    }

    private suspend fun writeCommand(command: ByteArray, writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
        if (!handlerIsConnected()) return
        val result = handler.writeCharacteristic(
            serviceUuid = mainServiceUUID,
            characteristicUuid = writeCharacteristicUUID,
            payload = command,
            writeType = writeType,
            important = writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
        result.onFailure { e ->
            Log.w(TAG, "Command send failed: ${e.message}")
        }
    }

    private fun sendB3Command(channelA: Int, channelB: Int) {
        if (!handlerIsConnected()) return
        lastSentChannelA = channelA
        lastSentChannelB = channelB
        val command = byteArrayOf(
            0xB3.toByte(),
            channelA.toByte(),
            channelB.toByte()
        )
        scope.launch {
            writeCommand(command)
        }
    }

    private fun sendB2Command(channelA: Int, channelB: Int) {
        val displayCommand = ByteArray(24)
        displayCommand[0] = 0xB2.toByte()
        val fixedData = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0x00,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x08, 0x09
        )
        System.arraycopy(fixedData, 0, displayCommand, 1, fixedData.size)
        displayCommand[22] = channelA.toByte()
        displayCommand[23] = channelB.toByte()
        scope.launch {
            writeCommand(displayCommand)
        }
    }

    private fun buildB0FromPulses(pulses: List<Pulse>): ByteArray {
        var ampA = ByteArray(4)
        var ampB = ByteArray(4)
        for (i in 0 until 4) {
            if (i < pulses.size) {
                ampA[i] = (pulses[i].ampA * 100).toInt().coerceIn(0, 100).toByte()
                ampB[i] = (pulses[i].ampB * 100).toInt().coerceIn(0, 100).toByte()
            } else {
                ampA[i] = 0
                ampB[i] = 0
            }
        }
        if (_swapChannels.value) {
            val tmp = ampA
            ampA = ampB
            ampB = tmp
        }
        return byteArrayOf(
            0xB0.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            *ampA,
            0x00, 0x00, 0x00, 0x00,
            *ampB
        )
    }

    private fun buildB0FromOutputPulses(pulses: List<OutputPulse>): ByteArray {
        var ampA = ByteArray(4)
        var ampB = ByteArray(4)
        for (i in pulses.indices) {
            ampA[i] = (pulses[i].ampA * currentChannelA / 200.0f).toInt().coerceIn(0, 100).toByte()
            ampB[i] = (pulses[i].ampB * currentChannelB / 200.0f).toInt().coerceIn(0, 100).toByte()
        }
        return byteArrayOf(
            0xB0.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            *ampA,
            0x00, 0x00, 0x00, 0x00,
            *ampB
        )
    }
}
