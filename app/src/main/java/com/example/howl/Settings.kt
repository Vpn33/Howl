package com.example.howl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.howl.ui.theme.HowlTheme
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.math.roundToInt

fun IntRange.toClosedFloatingPointRange(): ClosedFloatingPointRange<Float> {
    return this.first.toFloat()..this.last.toFloat()
}

class SettingsViewModel() : ViewModel() {
    fun setRemoteAccess(enabled: Boolean) {
        Prefs.remoteAccess.value = enabled
        Prefs.remoteAccess.save()
        if (enabled) {
            RemoteControlServer.start()
        }
        else {
            RemoteControlServer.stop()
        }
    }

    fun setLanguage(language: String, context: Context) {
        // 保存语言设置到Prefs
        Prefs.language.value = language
        // 阻塞等待保存完成
        runBlocking {
            Prefs.save(pref = Prefs.language)
        }

        // 重启应用以应用语言设置
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val restartIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(restartIntent)
        System.exit(0)
    }
    fun setOutputType(outputType: OutputType) {
        BluetoothHandler.disconnect()
        Prefs.outputType.value = outputType
        Prefs.outputType.save()
        Player.switchOutput(outputType)
    }
    fun setAudioOutputMinFrequency(newFrequency: Int) {
        val currentMax = Prefs.outputAudioMaxFrequency.value
        val clampedMin = newFrequency.coerceAtMost(currentMax - 50).coerceAtLeast(10)
        Prefs.outputAudioMinFrequency.value = clampedMin
        audioOutputFrequencyRangeUpdated()
    }
    fun setAudioOutputMaxFrequency(newFrequency: Int) {
        val currentMin = Prefs.outputAudioMinFrequency.value
        val clampedMax = newFrequency.coerceAtLeast(currentMin + 50).coerceAtLeast(10)
        Prefs.outputAudioMaxFrequency.value = clampedMax
        audioOutputFrequencyRangeUpdated()
    }
    fun audioOutputFrequencyRangeUpdated() {
        val newRange = Prefs.outputAudioMinFrequency.value .. Prefs.outputAudioMaxFrequency.value
        Player.output.allowedFrequencyRange = newRange
        MainOptions.setFrequencyRange(newRange)
    }
    fun syncCoyoteParameters() {
        if (Player.output is Coyote3Output) {
            val output = Player.output as Coyote3Output
            if (output.ready)
                output.syncParameters()
        }
    }
}

@Composable
fun OutputSettingsPanel(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val outputType by Prefs.outputType.collectAsStateWithLifecycle()
    val outputC3FrequencyBalanceA by Prefs.outputC3FrequencyBalanceA.collectAsStateWithLifecycle()
    val outputC3FrequencyBalanceB by Prefs.outputC3FrequencyBalanceB.collectAsStateWithLifecycle()
    val outputC3IntensityBalanceA by Prefs.outputC3IntensityBalanceA.collectAsStateWithLifecycle()
    val outputC3IntensityBalanceB by Prefs.outputC3IntensityBalanceB.collectAsStateWithLifecycle()
    val outputAudioWaveShape by Prefs.outputAudioWaveShape.collectAsStateWithLifecycle()
    val outputAudioCarrierShape by Prefs.outputAudioCarrierShape.collectAsStateWithLifecycle()
    val outputAudioMaxFrequency by Prefs.outputAudioMaxFrequency.collectAsStateWithLifecycle()
    val outputAudioMinFrequency by Prefs.outputAudioMinFrequency.collectAsStateWithLifecycle()
    val outputAudioCarrierPhaseType by Prefs.outputAudioCarrierPhaseType.collectAsStateWithLifecycle()
    val outputAudioCarrierFrequency by Prefs.outputAudioCarrierFrequency.collectAsStateWithLifecycle()
    val outputAudioWaveletWidth by Prefs.outputAudioWaveletWidth.collectAsStateWithLifecycle()
    val outputAudioWaveletFade by Prefs.outputAudioWaveletFade.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.settings_version, howlVersion), style = MaterialTheme.typography.labelLarge)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.settings_output_options), style = MaterialTheme.typography.headlineSmall)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = stringResource(R.string.settings_output_type), style = MaterialTheme.typography.labelLarge)
        OptionPicker(
            currentValue = outputType,
            onValueChange = { viewModel.setOutputType(it) },
            options = OutputType.entries,
            getText = { it.displayName }
        )
    }

    if(outputType == OutputType.AUDIO_WAVELET) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = stringResource(R.string.settings_audio_wavelet), style = MaterialTheme.typography.headlineSmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = stringResource(R.string.settings_carrier_wave_shape), style = MaterialTheme.typography.labelLarge)
            OptionPicker(
                currentValue = outputAudioCarrierShape,
                onValueChange = {
                    Prefs.outputAudioCarrierShape.value = it
                    Prefs.outputAudioCarrierShape.save()
                },
                options = AudioWaveShape.entries,
                getText = { it.displayName }
            )
        }
        SliderWithLabel(
            label = stringResource(R.string.settings_carrier_wave_frequency),
            value = outputAudioCarrierFrequency.toFloat(),
            onValueChange = {
                Prefs.outputAudioCarrierFrequency.value = it.roundToInt()
            },
            onValueChangeFinished = { Prefs.outputAudioCarrierFrequency.save() },
            valueRange = 600.0f..2000.0f,
            steps = 139,
            valueDisplay = { it.roundToInt().toString() }
        )
        val waveletWidthRange = 3..10
        SliderWithLabel(
            label = "Wavelet width (in carrier wave cycles)",
            value = outputAudioWaveletWidth.toFloat(),
            onValueChange = { Prefs.outputAudioWaveletWidth.value = it.roundToInt() },
            onValueChangeFinished = { Prefs.outputAudioWaveletWidth.save() },
            valueRange = waveletWidthRange.first.toFloat()..waveletWidthRange.last.toFloat(),
            steps = (waveletWidthRange.last - waveletWidthRange.first) - 1,
            valueDisplay = { it.roundToInt().toString() }
        )
        SliderWithLabel(
            label = "Wavelet fade in/out proportion",
            value = outputAudioWaveletFade,
            onValueChange = { Prefs.outputAudioWaveletFade.value = it },
            onValueChangeFinished = { Prefs.outputAudioWaveletFade.save() },
            valueRange = 0.0f..1.0f,
            steps = 99,
            valueDisplay = { String.format(Locale.US, "%03.2f", it) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Carrier phase on each channel",
                style = MaterialTheme.typography.labelLarge
            )
            OptionPicker(
                currentValue = outputAudioCarrierPhaseType,
                onValueChange = {
                    Prefs.outputAudioCarrierPhaseType.value = it
                    Prefs.outputAudioCarrierPhaseType.save()
                },
                options = AudioPhaseType.entries,
                getText = { it.displayName }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            val dutyCycle =
                ((1.0 / outputAudioCarrierFrequency) * outputAudioWaveletWidth) / 0.01
            val displayDutyCycle = (dutyCycle * 100.0).coerceIn(0.0..100.0).roundToInt()
            Text(
                text = "Estimated duty cycle at 100Hz: $displayDutyCycle%",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }

    if(outputType == OutputType.AUDIO_CONTINUOUS) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = stringResource(R.string.settings_audio_continuous), style = MaterialTheme.typography.headlineSmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "WARNING: Continuous output is intended for devices with their own audio processing. Do not use it with directly driven devices such as DIY stereostim or the Tingler, as many frequency range choices are unsafe.", style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = stringResource(R.string.settings_wave_shape), style = MaterialTheme.typography.labelLarge)
            OptionPicker(
                currentValue = outputAudioWaveShape,
                onValueChange = {
                    Prefs.outputAudioWaveShape.value = it
                    Prefs.outputAudioWaveShape.save()
                },
                options = AudioWaveShape.entries,
                getText = { it.displayName }
            )
        }
        val frequencySliderRange = 50..4000
        SliderWithLabel(
            label = stringResource(R.string.settings_minimum_frequency),
            value = outputAudioMinFrequency.toFloat(),
            onValueChange = { viewModel.setAudioOutputMinFrequency(it.roundToInt()) },
            onValueChangeFinished = { Prefs.outputAudioMinFrequency.save() },
            valueRange = frequencySliderRange.toClosedFloatingPointRange(),
            steps = calculateSliderSteps(frequencySliderRange.toClosedFloatingPointRange(), 50.0f),
            valueDisplay = { it.roundToInt().toString() }
        )
        SliderWithLabel(
            label = stringResource(R.string.settings_maximum_frequency),
            value = outputAudioMaxFrequency.toFloat(),
            onValueChange = { viewModel.setAudioOutputMaxFrequency(it.roundToInt()) },
            onValueChangeFinished = { Prefs.outputAudioMaxFrequency.save() },
            valueRange = frequencySliderRange.toClosedFloatingPointRange(),
            steps = calculateSliderSteps(frequencySliderRange.toClosedFloatingPointRange(), 50.0f),
            valueDisplay = { it.roundToInt().toString() }
        )
    }

    if(outputType == OutputType.COYOTE3) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = stringResource(R.string.settings_coyote_parameters), style = MaterialTheme.typography.headlineSmall)
        }
        SliderWithLabel(
            label = stringResource(R.string.settings_channel_a_frequency_balance),
            value = outputC3FrequencyBalanceA.toFloat(),
            onValueChange = {
                Prefs.outputC3FrequencyBalanceA.value = it.roundToInt()
            },
            onValueChangeFinished = {
                Prefs.outputC3FrequencyBalanceA.save()
                viewModel.syncCoyoteParameters()
            },
            valueRange = Coyote3Output.FREQUENCY_BALANCE_RANGE.toClosedFloatingPointRange(),
            steps = Coyote3Output.FREQUENCY_BALANCE_RANGE.last - 1,
            valueDisplay = { it.roundToInt().toString() }
        )
        SliderWithLabel(
            label = stringResource(R.string.settings_channel_b_frequency_balance),
            value = outputC3FrequencyBalanceB.toFloat(),
            onValueChange = {
                Prefs.outputC3FrequencyBalanceB.value = it.roundToInt()
            },
            onValueChangeFinished = {
                Prefs.outputC3FrequencyBalanceB.save()
                viewModel.syncCoyoteParameters()
            },
            valueRange = Coyote3Output.FREQUENCY_BALANCE_RANGE.toClosedFloatingPointRange(),
            steps = Coyote3Output.FREQUENCY_BALANCE_RANGE.last - 1,
            valueDisplay = { it.roundToInt().toString() }
        )
        SliderWithLabel(
            label = stringResource(R.string.settings_channel_a_intensity_balance),
            value = outputC3IntensityBalanceA.toFloat(),
            onValueChange = {
                Prefs.outputC3IntensityBalanceA.value = it.roundToInt()
            },
            onValueChangeFinished = {
                Prefs.outputC3IntensityBalanceA.save()
                viewModel.syncCoyoteParameters()
            },
            valueRange = Coyote3Output.INTENSITY_BALANCE_RANGE.toClosedFloatingPointRange(),
            steps = Coyote3Output.INTENSITY_BALANCE_RANGE.last - 1,
            valueDisplay = { it.roundToInt().toString() }
        )
        SliderWithLabel(
            label = stringResource(R.string.settings_channel_b_intensity_balance),
            value = outputC3IntensityBalanceB.toFloat(),
            onValueChange = {
                Prefs.outputC3IntensityBalanceB.value = it.roundToInt()
            },
            onValueChangeFinished = {
                Prefs.outputC3IntensityBalanceB.save()
                viewModel.syncCoyoteParameters()
            },
            valueRange = Coyote3Output.INTENSITY_BALANCE_RANGE.toClosedFloatingPointRange(),
            steps = Coyote3Output.INTENSITY_BALANCE_RANGE.last - 1,
            valueDisplay = { it.roundToInt().toString() }
        )
    }
}

@Composable
fun PowerSettingsPanel(
    viewModel: SettingsViewModel,
    powerRampViewModel: PowerRampViewModel,
    modifier: Modifier = Modifier
) {
    val powerLimitA by Prefs.powerLimitA.collectAsStateWithLifecycle()
    val powerLimitB by Prefs.powerLimitB.collectAsStateWithLifecycle()
    val powerStepA by Prefs.powerStepA.collectAsStateWithLifecycle()
    val powerStepB by Prefs.powerStepB.collectAsStateWithLifecycle()
    val powerAutoIncrementDelayA by Prefs.powerAutoIncrementDelayA.collectAsStateWithLifecycle()
    val powerAutoIncrementDelayB by Prefs.powerAutoIncrementDelayB.collectAsStateWithLifecycle()


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.settings_power_options), style = MaterialTheme.typography.headlineSmall)
    }
    SliderWithLabel(
        label = stringResource(R.string.settings_channel_a_power_limit),
        value = powerLimitA.toFloat(),
        onValueChange = {
            Prefs.powerLimitA.value = it.roundToInt()
        },
        onValueChangeFinished = {
            Prefs.powerLimitA.save()
            viewModel.syncCoyoteParameters()
        },
        valueRange = Coyote3Output.POWER_RANGE.toClosedFloatingPointRange(),
        steps = Coyote3Output.POWER_RANGE.last - 1,
        valueDisplay = { it.roundToInt().toString() }
    )
    SliderWithLabel(
        label = stringResource(R.string.settings_channel_b_power_limit),
        value = powerLimitB.toFloat(),
        onValueChange = {
            Prefs.powerLimitB.value = it.roundToInt()
        },
        onValueChangeFinished = {
            Prefs.powerLimitB.save()
            viewModel.syncCoyoteParameters()
        },
        valueRange = Coyote3Output.POWER_RANGE.toClosedFloatingPointRange(),
        steps = Coyote3Output.POWER_RANGE.last - 1,
        valueDisplay = { it.roundToInt().toString() }
    )
    val powerStepRange: IntRange = 1..10
    SliderWithLabel(
            label = stringResource(R.string.settings_power_step_size_a),
            value = powerStepA.toFloat(),
            onValueChange = { Prefs.powerStepA.value = it.roundToInt() },
            onValueChangeFinished = { Prefs.powerStepA.save() },
        valueRange = powerStepRange.toClosedFloatingPointRange(),
        steps = powerStepRange.last - 1,
        valueDisplay = { it.roundToInt().toString() }
    )
    SliderWithLabel(
            label =  stringResource(R.string.settings_power_step_size_b),
            value = powerStepB.toFloat(),
            onValueChange = { Prefs.powerStepB.value = it.roundToInt() },
            onValueChangeFinished = { Prefs.powerStepB.save() },
        valueRange = powerStepRange.toClosedFloatingPointRange(),
        steps = powerStepRange.last - 1,
        valueDisplay = { it.roundToInt().toString() }
    )
    val autoIncrementRange: IntRange = 5..300
    SliderWithLabel(
        label = stringResource(R.string.settings_power_auto_increase_delay_a),
        value = powerAutoIncrementDelayA.toFloat(),
        onValueChange = { Prefs.powerAutoIncrementDelayA.value = it.roundToInt() },
        onValueChangeFinished = { Prefs.powerAutoIncrementDelayA.save() },
        valueRange = autoIncrementRange.toClosedFloatingPointRange(),
        steps = ((autoIncrementRange.last - autoIncrementRange.first) * 0.2 - 1).roundToInt(),
        valueDisplay = { it.roundToInt().toString() }
    )
    SliderWithLabel(
        label = stringResource(R.string.settings_power_auto_increase_delay_b),
        value = powerAutoIncrementDelayB.toFloat(),
        onValueChange = { Prefs.powerAutoIncrementDelayB.value = it.roundToInt() },
        onValueChangeFinished = { Prefs.powerAutoIncrementDelayB.save() },
        valueRange = autoIncrementRange.toClosedFloatingPointRange(),
        steps = ((autoIncrementRange.last - autoIncrementRange.first) * 0.2 - 1).roundToInt(),
        valueDisplay = { it.roundToInt().toString() }
    )

    PowerRampPanel(powerRampViewModel)
}

@Composable
fun CalibrationSettingsPanel(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val positionalEffectCurve by Prefs.calibrationPositionalEffectCurve.collectAsStateWithLifecycle()
    val powerBalance by Prefs.calibrationPowerBalance.collectAsStateWithLifecycle()
    val frequencyBalanceA by Prefs.calibrationFrequencyBalanceA.collectAsStateWithLifecycle()
    val frequencyBalanceB by Prefs.calibrationFrequencyBalanceB.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.calibration_options), style = MaterialTheme.typography.headlineSmall)
    }
    SliderWithLabel(
        label = stringResource(R.string.cpt_power_balance),
        value = powerBalance,
        onValueChange = { Prefs.calibrationPowerBalance.value = it },
        onValueChangeFinished = { Prefs.calibrationPowerBalance.save() },
        valueRange = 0.0f..1.0f,
        steps = 99,
        valueDisplay = { String.format(Locale.US, "%03.2f", it) }
    )
    SliderWithLabel(
        label = stringResource(R.string.cpt_frequency_balance_a),
        value = frequencyBalanceA,
        onValueChange = { Prefs.calibrationFrequencyBalanceA.value = it },
        onValueChangeFinished = { Prefs.calibrationFrequencyBalanceA.save() },
        valueRange = 0.0f..1.0f,
        steps = 99,
        valueDisplay = { String.format(Locale.US, "%03.2f", it) }
    )
    SliderWithLabel(
        label = stringResource(R.string.cpt_frequency_balance_b),
        value = frequencyBalanceB,
        onValueChange = { Prefs.calibrationFrequencyBalanceB.value = it },
        onValueChangeFinished = { Prefs.calibrationFrequencyBalanceB.save() },
        valueRange = 0.0f..1.0f,
        steps = 99,
        valueDisplay = { String.format(Locale.US, "%03.2f", it) }
    )
    SliderWithLabel(
        label = stringResource(R.string.cpt_positional_effect_curve),
        value = positionalEffectCurve,
        onValueChange = { Prefs.calibrationPositionalEffectCurve.value = it },
        onValueChangeFinished = { Prefs.calibrationPositionalEffectCurve.save() },
        valueRange = 0.1f..1.0f,
        steps = 89,
        valueDisplay = { String.format(Locale.US, "%03.2f", it) }
    )
}

@Composable
fun SettingsPanel(
    viewModel: SettingsViewModel,
    powerRampViewModel: PowerRampViewModel,
    onRequestPermissions: (Array<String>, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val miscShowPowerMeter by Prefs.miscShowPowerMeter.collectAsStateWithLifecycle()
    val miscShowDebugLog by Prefs.miscShowDebugLog.collectAsStateWithLifecycle()
    val remoteAccess by Prefs.remoteAccess.collectAsStateWithLifecycle()
    val remoteAPIKey by Prefs.remoteAPIKey.collectAsStateWithLifecycle()

    val localNetworkPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
        arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK)
    } else {
        emptyArray()
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        OutputSettingsPanel(viewModel, modifier)
        PowerSettingsPanel(viewModel, powerRampViewModel, modifier)
        CalibrationSettingsPanel(viewModel, modifier)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = "Remote access options", style = MaterialTheme.typography.headlineSmall)
        }
        SwitchWithLabel(
            label = "Allow remote access",
            checked = remoteAccess,
            onCheckedChange = { isEnabling ->
                if (isEnabling) {
                    onRequestPermissions(localNetworkPermissions) { granted ->
                        if (granted) {
                            HLog.d("Settings", "Local network permissions granted.")
                            viewModel.setRemoteAccess(true)
                        } else {
                            HLog.d("Settings", "Local network permissions denied.")
                        }
                    }
                } else {
                    viewModel.setRemoteAccess(false)
                }
            }
        )
        val bearerRegex = Regex("[^A-Za-z0-9._~+/=-]")
        OutlinedTextField(
            value = remoteAPIKey,
            onValueChange = { input ->
                val filtered = input.replace(bearerRegex, "")
                Prefs.remoteAPIKey.value = filtered
                Prefs.remoteAPIKey.save()
            },
            label = { Text("Remote access key") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = stringResource(R.string.settings_misc_options), style = MaterialTheme.typography.headlineSmall)
        }
        SwitchWithLabel(
            label = stringResource(R.string.settings_show_power_meter),
            checked = miscShowPowerMeter,
            onCheckedChange = {
                Prefs.miscShowPowerMeter.value = it
                Prefs.miscShowPowerMeter.save()
            }
        )
        SwitchWithLabel(
            label = stringResource(R.string.settings_show_debug_log),
            checked = miscShowDebugLog,
            onCheckedChange = {
                Prefs.miscShowDebugLog.value = it
                Prefs.miscShowDebugLog.save()
            }
        )

        // Language settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = stringResource(R.string.settings_language), style = MaterialTheme.typography.headlineSmall)
        }
        val language by Prefs.language.collectAsStateWithLifecycle()
        val context = LocalContext.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    viewModel.setLanguage("zh", context)
                },
                modifier = Modifier.padding(4.dp),
                colors = if (language == "zh") ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ) else ButtonDefaults.buttonColors()
            ) {
                Text("中文")
            }
            Button(
                onClick = {
                    viewModel.setLanguage("en", context)
                },
                modifier = Modifier.padding(4.dp),
                colors = if (language == "en") ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ) else ButtonDefaults.buttonColors()
            ) {
                Text("English")
            }
        }
    }
}



@Preview
@Composable
fun SettingsPanelPreview() {
    HowlTheme {
        val viewModel: SettingsViewModel = viewModel()
        val powerRampViewModel: PowerRampViewModel = viewModel()
        SettingsPanel(
            viewModel = viewModel,
            powerRampViewModel = powerRampViewModel,
            onRequestPermissions = { _, _ -> },
            modifier = Modifier.fillMaxHeight()
        )
    }
}