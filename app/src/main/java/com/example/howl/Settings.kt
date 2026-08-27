package com.example.howl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.howl.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.math.roundToInt

class SettingsViewModel : ViewModel() {
    private val _selectedSettingsTab = MutableStateFlow(SettingsTab.POWER)
    val selectedSettingsTab: StateFlow<SettingsTab> = _selectedSettingsTab.asStateFlow()

    fun setSettingsTab(tab: SettingsTab) {
        _selectedSettingsTab.value = tab
    }

    fun setRemoteAccess(enabled: Boolean) {
        Prefs.remoteAccess.value = enabled
        Prefs.remoteAccess.save()
        if (enabled) {
            RemoteControlServer.start()
        } else {
            RemoteControlServer.stop()
        }
    }

    fun resetAll() {
        Player.stopPlayer()
        MainOptions.zeroPower()
        Prefs.resetAll(
            exceptions = listOf(
                Prefs.remoteAccess,
                Prefs.remoteAPIKey,
            )
        )
        OutputManager.restoreOutputs(Prefs.outputStates.value)
    }

    fun resetFunscript() {
        Prefs.resetByPrefix("funscript_")
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
}

enum class SettingsTab(val label: String) {
    POWER("Power"),
    POWER_RAMP("PowerRamp"),
    FUNSCRIPT("Funscript"),
    REMOTE_ACCESS("Remote access"),
    CHARTS_METERS("Charts & meters"),
    LANGUAGES("Languages"),
    MISC("Misc"),
}

@Composable
fun PowerSettingsPanel(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val powerLimitA by Prefs.powerLimitA.collectAsStateWithLifecycle()
    val powerLimitB by Prefs.powerLimitB.collectAsStateWithLifecycle()
    val powerStepA by Prefs.powerStepA.collectAsStateWithLifecycle()
    val powerStepB by Prefs.powerStepB.collectAsStateWithLifecycle()
    val powerAutoIncrementDelayA by Prefs.powerAutoIncrementDelayA.collectAsStateWithLifecycle()
    val powerAutoIncrementDelayB by Prefs.powerAutoIncrementDelayB.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        SliderWithLabel(
            label = "Power limit A",
            value = powerLimitA.toFloat(),
            onValueChange = {
                Prefs.powerLimitA.value = it.roundToInt()
            },
            onValueChangeFinished = {
                Prefs.powerLimitA.save()
                OutputManager.powerLimitsUpdated()
            },
            valueRange = MainOptions.POWER_RANGE.toClosedFloatingPointRange(),
            steps = MainOptions.POWER_RANGE.last - 1,
            valueDisplay = { it.roundToInt().toString() }
        )
        SliderWithLabel(
            label = "Power limit B",
            value = powerLimitB.toFloat(),
            onValueChange = {
                Prefs.powerLimitB.value = it.roundToInt()
            },
            onValueChangeFinished = {
                Prefs.powerLimitB.save()
                OutputManager.powerLimitsUpdated()
            },
            valueRange = MainOptions.POWER_RANGE.toClosedFloatingPointRange(),
            steps = MainOptions.POWER_RANGE.last - 1,
            valueDisplay = { it.roundToInt().toString() }
        )
        val powerStepRange: IntRange = 1..10
        SliderWithLabel(
            label = "Power step size A",
            value = powerStepA.toFloat(),
            onValueChange = { Prefs.powerStepA.value = it.roundToInt() },
            onValueChangeFinished = { Prefs.powerStepA.save() },
            valueRange = powerStepRange.toClosedFloatingPointRange(),
            steps = powerStepRange.last - 1,
            valueDisplay = { it.roundToInt().toString() }
        )
        SliderWithLabel(
            label = "Power step size B",
            value = powerStepB.toFloat(),
            onValueChange = { Prefs.powerStepB.value = it.roundToInt() },
            onValueChangeFinished = { Prefs.powerStepB.save() },
            valueRange = powerStepRange.toClosedFloatingPointRange(),
            steps = powerStepRange.last - 1,
            valueDisplay = { it.roundToInt().toString() }
        )
        val autoIncrementRange: IntRange = 5..300
        SliderWithLabel(
            label = "Power auto increase delay A (seconds)",
            value = powerAutoIncrementDelayA.toFloat(),
            onValueChange = { Prefs.powerAutoIncrementDelayA.value = it.roundToInt() },
            onValueChangeFinished = { Prefs.powerAutoIncrementDelayA.save() },
            valueRange = autoIncrementRange.toClosedFloatingPointRange(),
            steps = ((autoIncrementRange.last - autoIncrementRange.first) * 0.2 - 1).roundToInt(),
            valueDisplay = { it.roundToInt().toString() }
        )
        SliderWithLabel(
            label = "Power auto increase delay B (seconds)",
            value = powerAutoIncrementDelayB.toFloat(),
            onValueChange = { Prefs.powerAutoIncrementDelayB.value = it.roundToInt() },
            onValueChangeFinished = { Prefs.powerAutoIncrementDelayB.save() },
            valueRange = autoIncrementRange.toClosedFloatingPointRange(),
            steps = ((autoIncrementRange.last - autoIncrementRange.first) * 0.2 - 1).roundToInt(),
            valueDisplay = { it.roundToInt().toString() }
        )
    }
}
@Composable
fun PowerRampSettingsPanel(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val powerRampViewModel: PowerRampViewModel = viewModel()
    PowerRampPanel(powerRampViewModel)
}

@Composable
fun FunscriptSettingsPanel(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val funscriptVolume by Prefs.funscriptVolume.collectAsStateWithLifecycle()
    val funscriptPositionalEffectStrength by Prefs.funscriptPositionalEffectStrength.collectAsStateWithLifecycle()
    val funscriptFreqEnergyProportion by Prefs.funscriptFreqEnergyProportion.collectAsStateWithLifecycle()
    val funscriptDirectionalFreqShift by Prefs.funscriptDirectionalFreqShift.collectAsStateWithLifecycle()
    val funscriptFlipDirectionalFreqShift by Prefs.funscriptFlipDirectionalFreqShift.collectAsStateWithLifecycle()
    val funscriptNormaliseAxes by Prefs.funscriptNormaliseAxes.collectAsStateWithLifecycle()
    val funscriptSmoothingSigma by Prefs.funscriptSmoothingSigma.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Advanced parameters for tuning Howl's funscript algorithm.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "用于调整 Howl 脚本算法的高级参数",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Most users should stick with the default values.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "大多数用户应坚持使用默认值",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SliderWithLabel(
            label = "Scaling coefficient",
            subtitle = "缩放系数 (0.5 - 1.0)",
            value = funscriptVolume,
            onValueChange = { Prefs.funscriptVolume.value = it },
            onValueChangeFinished = { Prefs.funscriptVolume.save() },
            valueRange = 0.5f..1.0f,
            steps = 49,
            valueDisplay = { String.format(Locale.US, "%03.2f", it) }
        )
        SliderWithLabel(
            label = "Positional effect strength",
            subtitle = "位置效应强度 (0 - 1.0)",
            value = funscriptPositionalEffectStrength,
            onValueChange = { Prefs.funscriptPositionalEffectStrength.value = it },
            onValueChangeFinished = { Prefs.funscriptPositionalEffectStrength.save() },
            valueRange = 0f..1.0f,
            steps = 99,
            valueDisplay = { String.format(Locale.US, "%03.2f", it) }
        )
        SliderWithLabel(
            label = "Amplitude calculation window",
            subtitle = "振幅计算窗口 (0 - 0.5)",
            value = funscriptSmoothingSigma,
            onValueChange = { Prefs.funscriptSmoothingSigma.value = it },
            onValueChangeFinished = { Prefs.funscriptSmoothingSigma.save() },
            valueRange = 0f..0.5f,
            steps = 49,
            valueDisplay = { String.format(Locale.US, "%03.2f", it) }
        )
        SliderWithLabel(
            label = "Frequency energy proportion",
            subtitle = "频率能量比例 (0 - 1.0)",
            value = funscriptFreqEnergyProportion,
            onValueChange = { Prefs.funscriptFreqEnergyProportion.value = it },
            onValueChangeFinished = { Prefs.funscriptFreqEnergyProportion.save() },
            valueRange = 0f..1.0f,
            steps = 99,
            valueDisplay = { String.format(Locale.US, "%03.2f", it) }
        )
        SliderWithLabel(
            label = "Frequency directional shift",
            subtitle = "频率方向偏移 (0 - 0.5)",
            value = funscriptDirectionalFreqShift,
            onValueChange = { Prefs.funscriptDirectionalFreqShift.value = it },
            onValueChangeFinished = { Prefs.funscriptDirectionalFreqShift.save() },
            valueRange = 0f..0.5f,
            steps = 49,
            valueDisplay = { String.format(Locale.US, "%03.2f", it) }
        )
        SwitchWithLabel(
            label = "Flip frequency directional shift",
            subtitle = "翻转频率方向偏移",
            checked = funscriptFlipDirectionalFreqShift,
            onCheckedChange = {
                Prefs.funscriptFlipDirectionalFreqShift.value = it
                Prefs.funscriptFlipDirectionalFreqShift.save()
            }
        )
        SwitchWithLabel(
            label = "Normalise axes (when loading)",
            subtitle = "归一化轴（加载时）",
            checked = funscriptNormaliseAxes,
            onCheckedChange = {
                Prefs.funscriptNormaliseAxes.value = it
                Prefs.funscriptNormaliseAxes.save()
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { viewModel.resetFunscript() },
            ) {
                Text("Reset funscript settings")
            }
        }
    }
}

@Composable
fun RemoteAccessSettingsPanel(
    viewModel: SettingsViewModel,
    onRequestPermissions: (Array<String>, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val remoteAccess by Prefs.remoteAccess.collectAsStateWithLifecycle()
    val remoteAPIKey by Prefs.remoteAPIKey.collectAsStateWithLifecycle()

    val localNetworkPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
        arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK)
    } else {
        emptyArray()
    }

    Column(modifier = modifier) {
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
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
        )
    }
}

@Composable
fun ChartsMetersSettingsPanel(
    modifier: Modifier = Modifier
) {
    val miscShowPowerMeter by Prefs.miscShowPowerMeter.collectAsStateWithLifecycle()
    val miscShowFunscriptMeters by Prefs.miscShowFunscriptMeters.collectAsStateWithLifecycle()
    val miscPulseChartStyle by Prefs.miscPulseChartStyle.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Chart style", style = MaterialTheme.typography.labelLarge)
            OptionPicker(
                currentValue = miscPulseChartStyle,
                onValueChange = {
                    Prefs.miscPulseChartStyle.value = it
                    Prefs.miscPulseChartStyle.save()
                },
                options = PulseChartStyle.entries,
                getText = { it.displayName }
            )
        }
        SwitchWithLabel(
            label = "Show power meters",
            checked = miscShowPowerMeter,
            onCheckedChange = {
                Prefs.miscShowPowerMeter.value = it
                Prefs.miscShowPowerMeter.save()
            }
        )
        SwitchWithLabel(
            label = "Show funscript meters",
            checked = miscShowFunscriptMeters,
            onCheckedChange = {
                Prefs.miscShowFunscriptMeters.value = it
                Prefs.miscShowFunscriptMeters.save()
            }
        )
    }
}

@Composable
fun MiscSettingsPanel(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val miscShowDebugLog by Prefs.miscShowDebugLog.collectAsStateWithLifecycle()
    val miscMaxPlaybackDurationHours by Prefs.miscMaxPlaybackDurationHours.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Howl version $howlVersion",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SwitchWithLabel(
            label = "Show debug log tab",
            checked = miscShowDebugLog,
            onCheckedChange = {
                Prefs.miscShowDebugLog.value = it
                Prefs.miscShowDebugLog.save()
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SliderWithLabel(
            label = "Max playback duration (hours)",
            value = miscMaxPlaybackDurationHours.toFloat(),
            onValueChange = { Prefs.miscMaxPlaybackDurationHours.value = it.toInt() },
            onValueChangeFinished = { Prefs.miscMaxPlaybackDurationHours.save() },
            valueRange = 1f..24f,
            steps = 22,
            valueDisplay = { "${it.toInt()}h" }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Reset everything")
            }
        }

        if (showResetDialog) {
            ConfirmationDialog(
                message = "This will reset all Howl's saved data, except remote access options. All preferences on all screens will be reset, and all of your outputs will be removed. Are you sure?",
                onConfirm = {
                    viewModel.resetAll()
                    showResetDialog = false
                },
                onDismiss = { showResetDialog = false },
                confirmText = "Reset",
                dismissText = "Cancel"
            )
        }
    }
}

@Composable
fun LanguagesSettingsPanel(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
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

@Composable
fun SettingsPanel(
    viewModel: SettingsViewModel,

    onRequestPermissions: (Array<String>, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedSettingsTab.collectAsStateWithLifecycle()
    val tabs = SettingsTab.entries

    Column(modifier = modifier.fillMaxWidth()) {
        SecondaryScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 0.dp,
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { viewModel.setSettingsTab(tab) },
                    text = { Text(tab.label) },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (selectedTab) {
                SettingsTab.POWER ->
                    PowerSettingsPanel(viewModel = viewModel)
                SettingsTab.POWER_RAMP ->
                    PowerRampSettingsPanel(viewModel = viewModel)
                SettingsTab.FUNSCRIPT ->
                    FunscriptSettingsPanel(viewModel = viewModel)
                SettingsTab.REMOTE_ACCESS ->
                    RemoteAccessSettingsPanel(
                        viewModel = viewModel,
                        onRequestPermissions = onRequestPermissions
                    )
                SettingsTab.CHARTS_METERS ->
                    ChartsMetersSettingsPanel()
                SettingsTab.LANGUAGES ->
                    LanguagesSettingsPanel(viewModel = viewModel)
                SettingsTab.MISC ->
                    MiscSettingsPanel(viewModel = viewModel)
            }
        }
    }
}

@Preview
@Composable
fun SettingsPanelPreview() {
    AppTheme {
        val viewModel: SettingsViewModel = viewModel()
        SettingsPanel(
            viewModel = viewModel,
            onRequestPermissions = { _, _ -> },
            modifier = Modifier.fillMaxHeight()
        )
    }
}