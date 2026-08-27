package com.example.howl

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.howl.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MainOptionsState(
    val channelAPower: Int = 0,
    val channelBPower: Int = 0,
    val globalMute: Boolean = false,
    val autoIncreasePower: Boolean = false,
    val swapChannels: Boolean = false,
)

object MainOptions {
    val POWER_RANGE: IntRange = 0..200
    private val _state = MutableStateFlow(MainOptionsState())
    val state: StateFlow<MainOptionsState> = _state.asStateFlow()
    private var autoIncrementPowerCounterA: Long = 0L
    private var autoIncrementPowerCounterB: Long = 0L

    // PowerRampViewModel instance - will be set by HowlActivity
    var powerRampViewModel: PowerRampViewModel? = null

    fun setChannelPower(channel: Int, power: Int) {
        setChannelPower(channel, power, true)
    }

    fun setChannelPower(channel: Int, power: Int, sync: Boolean) {
        // 如果开启了电源强度同步
        if (Prefs.powerSyncEnabled.value) {
            if (sync) {
                setChannelPower(-1, power, false)
                return
            }
        }
        when (channel) {
            0 -> {
                val limit = Prefs.powerLimitA.value
                val newPower = power.coerceIn(0..limit)
                _state.update { it.copy(channelAPower = newPower) }
            }

            1 -> {
                val limit = Prefs.powerLimitB.value
                val newPower = power.coerceIn(0..limit)
                _state.update { it.copy(channelBPower = newPower) }
            }

            -1 -> {
                val limitA = Prefs.powerLimitA.value
                val newPowerA = power.coerceIn(0..limitA)
                _state.update { it.copy(channelAPower = newPowerA) }

                val limitB = Prefs.powerLimitB.value
                val newPowerB = power.coerceIn(0..limitB)
                _state.update { it.copy(channelBPower = newPowerB) }
            }

            else -> {}
        }
    }

    fun zeroPower() {
        _state.update { it.copy(channelAPower = 0, channelBPower = 0) }
    }

    fun incrementChannelPower(channel: Int, step: Int = 0) {
        incrementChannelPower(channel, step, true)
    }

    fun incrementChannelPower(channel: Int, step: Int = 0, sync: Boolean) {
        if (channel == -1) {
            // Apply to both channels
            incrementChannelPower(0, step, false)
            incrementChannelPower(1, step, false)
            return
        }

        val current = getChannelPower(channel)
        val stepSize = if (step == 0) getChannelPowerStep(channel) else step
        setChannelPower(channel, current + stepSize, false)

        // 如果开启了电源强度同步
        if (Prefs.powerSyncEnabled.value) {
            if (sync) {
                incrementChannelPower(if (channel == 0) 1 else 0, step, false)
            }
        }
    }

    fun decrementChannelPower(channel: Int, step: Int = 0) {
        decrementChannelPower(channel, step, true)
    }

    fun decrementChannelPower(channel: Int, step: Int = 0, sync: Boolean) {
        if (channel == -1) {
            // Apply to both channels
            decrementChannelPower(0, step, false)
            decrementChannelPower(1, step, false)
            return
        }

        val current = getChannelPower(channel)
        val stepSize = if (step == 0) getChannelPowerStep(channel) else step
        setChannelPower(channel, current - stepSize, false)

        // 如果开启了电源强度同步
        if (Prefs.powerSyncEnabled.value) {
            if (sync) {
                decrementChannelPower(if (channel == 0) 1 else 0, step, false)
            }
        }
    }

    fun getChannelPower(channel: Int): Int {
        return when (channel) {
            0 -> state.value.channelAPower
            1 -> state.value.channelBPower
            else -> 0
        }
    }

    fun getPowerLevels(): Pair<Int, Int> {
        return Pair(state.value.channelAPower, state.value.channelBPower)
    }

    fun getChannelPowerStep(channel: Int): Int {
        return when (channel) {
            0 -> Prefs.powerStepA.value
            1 -> Prefs.powerStepB.value
            else -> 1
        }
    }

    fun autoIncreasePower(elapsed: Double) {
        val options = state.value

        if (options.autoIncreasePower && !options.globalMute) {
            // Check if power ramp is enabled
            val powerRampEnabled = Prefs.powerRampEnabled.value
            if (powerRampEnabled) {
                // Delegate to PowerRampViewModel
                powerRampViewModel?.processPowerRamp(elapsed, options)
            } else {
                // Using milliseconds internally avoids an annoying issue where the channel updates
                // can desynchronise from each other over time due to floating point errors
                val elapsedMs = (elapsed * 1000).toLong()
                if (options.channelAPower > 0)
                    autoIncrementPowerCounterA += elapsedMs
                if (options.channelBPower > 0)
                    autoIncrementPowerCounterB += elapsedMs

                val autoIncrementDelayA = (Prefs.powerAutoIncrementDelayA.value * 1000).toLong()
                val autoIncrementDelayB = (Prefs.powerAutoIncrementDelayB.value * 1000).toLong()
                //Log.d("MainControls", "Auto increment calculation $autoIncrementPowerCounterA / $autoIncrementDelayA      $autoIncrementPowerCounterB / $autoIncrementDelayB")
                if (autoIncrementPowerCounterA >= autoIncrementDelayA) {
                    autoIncrementPowerCounterA = 0L
                    incrementChannelPower(0, 1)
                }
                if (autoIncrementPowerCounterB >= autoIncrementDelayB) {
                    autoIncrementPowerCounterB = 0L
                    incrementChannelPower(1, 1)
                }
            }
        }
    }

    fun singleChannelMode(): Boolean {
        val aActive = state.value.channelAPower > 0
        val bActive = state.value.channelBPower > 0
        return aActive != bActive
    }

    fun setGlobalMute(muted: Boolean) {
        _state.update { it.copy(globalMute = muted) }
    }

    fun setAutoIncreasePower(autoIncrease: Boolean) {
        autoIncrementPowerCounterA = 0L
        autoIncrementPowerCounterB = 0L
        // Reset power ramp variables via PowerRampViewModel
        powerRampViewModel?.resetPowerRamp()
        _state.update { it.copy(autoIncreasePower = autoIncrease) }
    }

    fun setSwapChannels(swap: Boolean) {
        _state.update { it.copy(swapChannels = swap) }
    }
}

class MainOptionsViewModel : ViewModel() {
    private val _pulseChartMode = MutableStateFlow(PulseChartMode.Off)
    val pulseChartMode: StateFlow<PulseChartMode> = _pulseChartMode.asStateFlow()

    fun setChannelPower(channel: Int, power: Int) {
        MainOptions.setChannelPower(channel, power)
    }

    fun incrementChannelPower(channel: Int) {
        MainOptions.incrementChannelPower(channel)
    }

    fun decrementChannelPower(channel: Int) {
        MainOptions.decrementChannelPower(channel)
    }

    fun setGlobalMute(muted: Boolean) {
        MainOptions.setGlobalMute(muted)
    }

    fun setAutoIncreasePower(autoIncrease: Boolean) {
        MainOptions.setAutoIncreasePower(autoIncrease)
    }

    fun setSwapChannels(swap: Boolean) {
        MainOptions.setSwapChannels(swap)
    }

    fun cyclePulseChart() {
        val newMode = _pulseChartMode.value.next()
        _pulseChartMode.update { newMode }
    }
}

@Composable
fun MainOptionsPanel(
    viewModel: MainOptionsViewModel,
    modifier: Modifier = Modifier
) {
    val mainOptionsState by MainOptions.state.collectAsStateWithLifecycle()
    val showPowerMeter by Prefs.miscShowPowerMeter.collectAsStateWithLifecycle()
    val pulseChartMode by viewModel.pulseChartMode.collectAsStateWithLifecycle()
    val lastPulse by PulseHistory.lastPulseWithPlayerState.collectAsStateWithLifecycle(initialValue = Pulse())

    val minSeparation = 0.05
    val muted = mainOptionsState.globalMute
    val autoIncreasePower = mainOptionsState.autoIncreasePower
    val swapChannels = mainOptionsState.swapChannels
    val toolbarButtonHeight = 50.dp
    val activeButtonColour = MaterialTheme.colorScheme.tertiary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side: Channel A controls
            PowerLevelPanel(
                channelIndex = 0,
                channelLabel = "A",
                power = mainOptionsState.channelAPower,
                viewModel = viewModel,
            )

            // Center: Power meters (grouped together)
            if (showPowerMeter) {
                Row(
                    horizontalArrangement = Arrangement.Center
                ) {
                    PowerLevelMeters()
                }
            } else {
                Spacer(modifier = Modifier.width(12.dp + 8.dp + 12.dp))
            }

            // Right side: Channel B controls
            PowerLevelPanel(
                channelIndex = 1,
                channelLabel = "B",
                power = mainOptionsState.channelBPower,
                viewModel = viewModel,
            )
        }

        // Wrap the remaining controls in a Column (outer Column already provides 16.dp horizontal padding)
        Column {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    modifier = Modifier
                        .weight(1.0f)
                        .height(toolbarButtonHeight),
                    contentPadding = PaddingValues(2.dp),
                    onClick = {
                        viewModel.setGlobalMute(!muted)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (muted) activeButtonColour else ButtonDefaults.buttonColors().containerColor
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.mute),
                        contentDescription = "Mute output"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    modifier = Modifier.height(toolbarButtonHeight),
                    contentPadding = PaddingValues(2.dp),
                    onClick = {
                        viewModel.setAutoIncreasePower(!autoIncreasePower)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (autoIncreasePower) activeButtonColour else ButtonDefaults.buttonColors().containerColor
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.auto_increase),
                        contentDescription = "Auto increase power"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    modifier = Modifier.height(toolbarButtonHeight),
                    contentPadding = PaddingValues(2.dp),
                    onClick = {
                        viewModel.cyclePulseChart()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pulseChartMode != PulseChartMode.Off) activeButtonColour else ButtonDefaults.buttonColors().containerColor
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.chart),
                        contentDescription = "Pulse chart"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    modifier = Modifier.height(toolbarButtonHeight),
                    contentPadding = PaddingValues(2.dp),
                    onClick = {
                        viewModel.setSwapChannels(!swapChannels)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (swapChannels) activeButtonColour else ButtonDefaults.buttonColors().containerColor
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.swap),
                        contentDescription = "Swap channels"
                    )
                }
            }

            if (pulseChartMode != PulseChartMode.Off) {
                Spacer(modifier = Modifier.height(8.dp))
                PulseChartPanel(
                    mode = pulseChartMode,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun PowerLevelPanel(
    channelIndex: Int,
    channelLabel: String,
    power: Int,
    viewModel: MainOptionsViewModel
) {
    Column {
        Text(
            text = "$power",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Row(
            horizontalArrangement = Arrangement.Center,
        ) {
            LongPressButton(
                onClick = { viewModel.decrementChannelPower(channelIndex) },
                onLongClick = { viewModel.setChannelPower(channelIndex, 0) },
                modifier = Modifier.size(68.dp)
            ) {
                Column {
                    Icon(
                        painter = painterResource(R.drawable.minus),
                        contentDescription = "Lower power",
                    )
                    Text(
                        text = channelLabel,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            LongPressButton(
                onClick = { viewModel.incrementChannelPower(channelIndex) },
                onLongClick = {},
                modifier = Modifier.size(68.dp)
            ) {
                Column {
                    Icon(
                        painter = painterResource(R.drawable.plus),
                        contentDescription = "Increase power",
                    )
                    Text(
                        text = channelLabel,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
private fun PowerLevelMeter(
    amplitudeProvider: () -> Float,
    frequencyProvider: () -> Float,
) {
    val startColor = Color(0xFFFF0000)
    val endColor = Color(0xFFFFFF00)

    val amplitude by animateFloatAsState(
        targetValue = amplitudeProvider(),
        animationSpec = tween(durationMillis = 25, easing = LinearEasing),
        label = "meterAmp"
    )
    val frequency by animateFloatAsState(
        targetValue = frequencyProvider(),
        animationSpec = tween(durationMillis = 25, easing = LinearEasing),
        label = "meterFreq"
    )

    Box(
        modifier = Modifier
            .width(12.dp)
            .fillMaxHeight()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.extraSmall
            )
            .padding(1.dp)
            .drawBehind {
                val powerLevel = amplitude.coerceIn(0f, 1f)
                if (powerLevel > 0f) {
                    val barHeight = size.height * powerLevel
                    val barColor = lerp(
                        startColor,
                        endColor,
                        frequency.coerceIn(0f, 1f)
                    )
                    drawRect(
                        color = barColor,
                        topLeft = Offset(0f, size.height - barHeight),
                        size = Size(size.width, barHeight)
                    )
                }
            }
    )
}

@Composable
fun PowerLevelMeters() {
    val lastPulse by PulseHistory.lastPulseWithPlayerState.collectAsStateWithLifecycle(initialValue = Pulse())

    Row {
        PowerLevelMeter(
            amplitudeProvider = { lastPulse.ampA },
            frequencyProvider = { lastPulse.freqA },
        )
        Spacer(modifier = Modifier.width(8.dp))
        PowerLevelMeter(
            amplitudeProvider = { lastPulse.ampB },
            frequencyProvider = { lastPulse.freqB },
        )
    }
}

@Preview
@Composable
fun MainOptionsPanelPreview() {
    AppTheme {
        val viewModel: MainOptionsViewModel = viewModel()
        MainOptionsPanel(
            viewModel = viewModel,
            modifier = Modifier.fillMaxHeight()
        )
    }
}