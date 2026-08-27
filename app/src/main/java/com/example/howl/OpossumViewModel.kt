package com.example.howl

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

enum class OpossumConnectionStatus {
    Disconnected,
    Scanning,
    Connecting,
    Connected,
    Error
}

class OpossumViewModel : ViewModel() {

    private val _opossumOutput = MutableStateFlow<OutputOpossum?>(null)

    private val _channelAAmp = MutableStateFlow(0)
    val channelAAmp: StateFlow<Int> = _channelAAmp.asStateFlow()

    private val _channelBAmp = MutableStateFlow(0)
    val channelBAmp: StateFlow<Int> = _channelBAmp.asStateFlow()

    private var lastSentChannelA = -1
    private var lastSentChannelB = -1

    private val _playerSyncEnabled = MutableStateFlow(false)
    val playerSyncEnabled: StateFlow<Boolean> = _playerSyncEnabled.asStateFlow()

    private val _syncChannels = MutableStateFlow(true)
    val syncChannels: StateFlow<Boolean> = _syncChannels.asStateFlow()

    private val _swapChannels = MutableStateFlow(false)
    val swapChannels: StateFlow<Boolean> = _swapChannels.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _selectedActivity = MutableStateFlow(ActivityType.LICKS)
    val selectedActivity: StateFlow<ActivityType> = _selectedActivity.asStateFlow()

    private val _activityChangeProbability = MutableStateFlow(Prefs.activityChangeProbability.value)
    val activityChangeProbability: StateFlow<Float> = _activityChangeProbability.asStateFlow()

    val connectionStatus: StateFlow<OpossumConnectionStatus> = _opossumOutput.flatMapLatest { output ->
        output?.connectionStatus?.map { status ->
            when (status) {
                ConnectionStatus.Disconnected -> OpossumConnectionStatus.Disconnected
                ConnectionStatus.Scanning -> OpossumConnectionStatus.Scanning
                ConnectionStatus.Connecting -> OpossumConnectionStatus.Connecting
                ConnectionStatus.Connected -> OpossumConnectionStatus.Connected
            }
        } ?: flowOf(OpossumConnectionStatus.Disconnected)
    }.let { flow ->
        MutableStateFlow(OpossumConnectionStatus.Disconnected).apply {
            viewModelScope.launch { flow.collect { value = it } }
        }
    }.asStateFlow()

    val batteryLevel: StateFlow<Int> = _opossumOutput.flatMapLatest { output ->
        output?.batteryLevel ?: flowOf(-1)
    }.let { flow ->
        MutableStateFlow(-1).apply {
            viewModelScope.launch { flow.collect { value = it } }
        }
    }.asStateFlow()

    var onManualChannelChange: ((Int, Int) -> Unit)? = null

    fun findAndSetOutput() {
        viewModelScope.launch {
            OutputManager.outputs.collect { outputs ->
                val output = outputs.filterIsInstance<OutputOpossum>().firstOrNull()
                _opossumOutput.value = output
                output?.setChannelFlows(channelAAmp, channelBAmp)
                output?.setOnB3Response { chA, chB -> handleB3Response(chA, chB) }
                output?.setOnIsPlayingChanged { playing -> _isPlaying.value = playing }
                output?.setOnSelectedActivityChanged { type -> _selectedActivity.value = type }
                output?.setOnSyncChannelsChanged { enabled -> _syncChannels.value = enabled }
                output?.setOnSwapChannelsChanged { enabled -> _swapChannels.value = enabled }
            }
        }
    }

    private fun handleB3Response(chA: Int, chB: Int) {
        val effectiveA = if (_swapChannels.value) chB else chA
        val effectiveB = if (_swapChannels.value) chA else chB

        if (effectiveA == lastSentChannelA && effectiveB == lastSentChannelB) {
            HLog.d("OpossumVM", "B3 echo ignored: ($chA,$chB) matches last sent ($lastSentChannelA,$lastSentChannelB)")
            return
        }

        HLog.d("OpossumVM", "B3 response update: ($chA,$chB) -> effective ($effectiveA,$effectiveB)")
        if (_swapChannels.value) {
            _channelAAmp.value = chB
            _channelBAmp.value = chA
        } else {
            _channelAAmp.value = chA
            _channelBAmp.value = chB
        }
    }

    fun setChannelA(value: Int) {
        val clamped = value.coerceIn(0, 200)
        _channelAAmp.update { clamped }
        if (_syncChannels.value) {
            _channelBAmp.update { clamped }
        }
    }

    fun setChannelB(value: Int) {
        val clamped = value.coerceIn(0, 200)
        _channelBAmp.update { clamped }
        if (_syncChannels.value) {
            _channelAAmp.update { clamped }
        }
    }

    fun onChannelAChanged() {
        val (effChA, effChB) = getEffectiveChannels()
        lastSentChannelA = effChA
        lastSentChannelB = effChB
        onManualChannelChange?.invoke(effChA, effChB)
        _opossumOutput.value?.onChannelValuesChanged(effChA, effChB)
    }

    fun onChannelBChanged() {
        val (effChA, effChB) = getEffectiveChannels()
        lastSentChannelA = effChA
        lastSentChannelB = effChB
        onManualChannelChange?.invoke(effChA, effChB)
        _opossumOutput.value?.onChannelValuesChanged(effChA, effChB)
    }

    fun setChannels(a: Int, b: Int) {
        val clampedA = a.coerceIn(0, 200)
        val clampedB = b.coerceIn(0, 200)
        if (_syncChannels.value) {
            _channelAAmp.update { clampedA }
            _channelBAmp.update { clampedA }
        } else {
            _channelAAmp.update { clampedA }
            _channelBAmp.update { clampedB }
        }
        val (effChA, effChB) = getEffectiveChannels()
        lastSentChannelA = effChA
        lastSentChannelB = effChB
        _opossumOutput.value?.onChannelValuesChanged(effChA, effChB)
    }

    fun setPlayerSyncEnabled(enabled: Boolean) {
        _playerSyncEnabled.value = enabled
        _opossumOutput.value?.setPlayerSyncEnabled(enabled)
    }

    fun setSyncChannels(enabled: Boolean) {
        _syncChannels.value = enabled
        _opossumOutput.value?.setSyncChannels(enabled)
        if (enabled) {
            val current = _channelAAmp.value
            _channelBAmp.value = current
            val (effChA, effChB) = getEffectiveChannels()
            lastSentChannelA = effChA
            lastSentChannelB = effChB
            _opossumOutput.value?.onChannelValuesChanged(effChA, effChB)
        }
    }

    fun setSwapChannels(enabled: Boolean) {
        _swapChannels.value = enabled
        _opossumOutput.value?.setSwapChannels(enabled)
        val (effChA, effChB) = getEffectiveChannels()
        lastSentChannelA = effChA
        lastSentChannelB = effChB
        _opossumOutput.value?.onChannelValuesChanged(effChA, effChB)
    }

    fun setSelectedActivity(type: ActivityType) {
        _selectedActivity.value = type
        _opossumOutput.value?.setSelectedActivity(type)
    }

    fun setActivityChangeProbability(probability: Float) {
        val clamped = probability.coerceIn(0f, 1f)
        _activityChangeProbability.value = clamped
        Prefs.activityChangeProbability.value = clamped
        Prefs.activityChangeProbability.save()
        _opossumOutput.value?.setActivityChangeProbability(clamped)
    }

    fun startPlayback() {
        _opossumOutput.value?.startPlayback()
    }

    fun stopPlayback() {
        _opossumOutput.value?.stopPlayback()
    }

    private fun getEffectiveChannels(): Pair<Int, Int> {
        return if (_swapChannels.value) {
            _channelBAmp.value to _channelAAmp.value
        } else {
            _channelAAmp.value to _channelBAmp.value
        }
    }

    fun getStatusText(): String {
        val state = connectionStatus.value
        return when (state) {
            OpossumConnectionStatus.Disconnected -> "Disconnected"
            OpossumConnectionStatus.Scanning -> "Scanning..."
            OpossumConnectionStatus.Connecting -> "Connecting..."
            OpossumConnectionStatus.Connected -> {
                val battery = batteryLevel.value
                if (battery >= 0) "Connected · ${battery}%" else "Connected"
            }
            OpossumConnectionStatus.Error -> "Error"
        }
    }
}
