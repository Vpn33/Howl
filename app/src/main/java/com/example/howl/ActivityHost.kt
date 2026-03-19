package com.example.howl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.res.stringResource
import com.example.howl.ui.theme.HowlTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

object ActivityHost : PulseSource {
    val PROBABILITY_RANGE: ClosedFloatingPointRange<Float> = 0.0f..1.0f

    data class ActivityInfo(
        val displayNameResId: Int,
        val iconResId: Int,
        val randomlySelect: Boolean,
        val factory: () -> Activity
    )

    override var displayName: String = "Activity output"
    override var duration: Double? = null
    override val isFinite: Boolean = false
    override val shouldLoop: Boolean = false
    override var readyToPlay: Boolean = true
    override var isRemote: Boolean = false

    private val timerManager = TimerManager()

    private var lastUpdateTime = -1.0
    private var lastSimulationTime = -1.0

    val availableActivities: List<ActivityInfo> = listOf(
        ActivityInfo(R.string.activity_infinite_licks, R.drawable.grin_tongue, true) { LickActivity() },
        ActivityInfo(R.string.activity_penetration, R.drawable.rocket, true) { PenetrationActivity() },
        ActivityInfo(R.string.activity_sliding_vibrator, R.drawable.vibration, true) { VibroActivity() },
        ActivityInfo(R.string.activity_milkmaster, R.drawable.cow, true) { MilkerActivity() },
        ActivityInfo(R.string.activity_chaos, R.drawable.chaos, true) { ChaosActivity() },
        ActivityInfo(R.string.activity_luxury_hj, R.drawable.hand, true) { LuxuryHJActivity() },
        ActivityInfo(R.string.activity_opposites, R.drawable.yin_yang, true) { OppositesActivity() },
        ActivityInfo(R.string.activity_calibration_1, R.drawable.swapvert, false) { Calibration1Activity() },
        ActivityInfo(R.string.activity_calibration_2, R.drawable.calibration, false) { Calibration2Activity() },
        ActivityInfo(R.string.activity_bj_megamix, R.drawable.lips, true) { BJActivity() },
        ActivityInfo(R.string.activity_fast_slow, R.drawable.speed, true) { FastSlowActivity() },
        ActivityInfo(R.string.activity_additive, R.drawable.additive, true) { AdditiveActivity() },
        ActivityInfo(R.string.activity_simplex, R.drawable.wave_triangle, true) { SimplexActivity() },
        ActivityInfo(R.string.activity_simplex_pro, R.drawable.waveform, true) { SimplexProActivity() },
        ActivityInfo(R.string.activity_simplex_turbo, R.drawable.waveform_path, true) { SimplexTurboActivity() },
        ActivityInfo(R.string.activity_relentless, R.drawable.hammer, true) { RelentlessActivity() },
        ActivityInfo(R.string.activity_random_shapes, R.drawable.shapes, true) { RandomShapesActivity() },
        ActivityInfo(R.string.activity_overflowing, R.drawable.water_drop, true) { OverflowingActivity() },
    )
    private val randomActivities = availableActivities.filter { it.randomlySelect }
    private var currentActivityInfo: ActivityInfo? = null
    private var currentActivity: Activity? = null

    // used so the UI can respond to the current activity
    private val _currentActivityName = MutableStateFlow("")
    val currentActivityName: StateFlow<String> = _currentActivityName.asStateFlow()


    init {
        changeActivity()
    }

    override fun updateState(currentTime: Double) {
        if (lastUpdateTime !in 0.0..currentTime)
            lastUpdateTime = currentTime
        val changeProbability = Prefs.activityChangeProbability.value
        val timeDelta = currentTime - lastUpdateTime

        val probability = (changeProbability * 3.0 * timeDelta) / 60.0
        if (Random.nextDouble() < probability) {
            changeActivity()
        }

        lastUpdateTime = currentTime
    }

    override fun getPulseAtTime(time: Double): Pulse {
        if (lastSimulationTime !in 0.0..time) {
            lastSimulationTime = time
        }

        val simulationTimeDelta = time - lastSimulationTime
        lastSimulationTime = time
        timerManager.update(simulationTimeDelta)

        currentActivity?.runSimulation(simulationTimeDelta)
        return currentActivity?.getPulse() ?: Pulse()
    }

    fun setCurrentActivity(newActivityInfo: ActivityInfo) {
        currentActivityInfo = newActivityInfo
        currentActivity = newActivityInfo.factory().apply { initialise() }
        lastSimulationTime = -1.0
        lastUpdateTime = -1.0
        _currentActivityName.value = ""
    }

    fun changeActivity() {
        val current = currentActivityInfo
        val candidates = if (current != null) {
            randomActivities.filter { it != current }
        } else {
            randomActivities
        }

        val newInfo = candidates.randomOrNull() ?: randomActivities.random()
        setCurrentActivity(newInfo)
    }
}

class ActivityHostViewModel() : ViewModel() {
    fun setCurrentActivity(activityInfo: ActivityHost.ActivityInfo) {
        ActivityHost.setCurrentActivity(activityInfo)
    }
    fun stop() {
        Player.stopPlayer()
    }
    fun start() {
        Player.switchPulseSource(ActivityHost)
        Player.startPlayer()
    }
}

@Composable
fun ActivityHostPanel(
    viewModel: ActivityHostViewModel,
    modifier: Modifier = Modifier
) {
    val activityChangeProbability by Prefs.activityChangeProbability.collectAsStateWithLifecycle()
    val playerState by Player.playerState.collectAsStateWithLifecycle()
    val isPlaying = playerState.isPlaying && playerState.activePulseSource == ActivityHost

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Play/Pause Button
            Button(
                onClick = {
                    if (isPlaying)
                        viewModel.stop()
                    else
                        viewModel.start()
                }
            ) {
                if (isPlaying) {
                    Icon(
                        painter = painterResource(R.drawable.pause),
                        contentDescription = stringResource(R.string.button_pause)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = stringResource(R.string.button_play)
                    )
                }
            }
        }
        /*Text(
            text = "Current Action: ${activityState.currentActivityDisplayName}",
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyLarge
        )*/
        SliderWithLabel(
            label =  stringResource(R.string.activity_change_probability),
            value = activityChangeProbability,
            onValueChange = { Prefs.activityChangeProbability.value = it },
            onValueChangeFinished = { Prefs.activityChangeProbability.save() },
            valueRange = ActivityHost.PROBABILITY_RANGE,
            steps = ((ActivityHost.PROBABILITY_RANGE.endInclusive - ActivityHost.PROBABILITY_RANGE.start) * 100.0 - 1).roundToInt(),
            valueDisplay = { String.format(Locale.US, "%03.2f", it) }
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().height(440.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ActivityHost.availableActivities) { info ->
                val displayName = stringResource(info.displayNameResId)
                Button(
                    onClick = {
                        viewModel.setCurrentActivity(info)
                    },
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(info.iconResId),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis

                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun ActivityHostPreview() {
    HowlTheme {
        val viewModel: ActivityHostViewModel = viewModel()
        ActivityHostPanel(
            viewModel = viewModel,
            modifier = Modifier.fillMaxHeight()
        )
    }
}