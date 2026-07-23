package com.example.howl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

data class PowerRampState(
    val powerRampEnabled: Boolean = false,
    val powerRampChannelMode: String = "AB_SYNC",
    val powerRampIntensityARangeStart: Int = -40,
    val powerRampIntensityARangeEnd: Int = 40,
    val powerRampIntensityBRangeStart: Int = -10,
    val powerRampIntensityBRangeEnd: Int = 10,
    val powerRampSpeedModeA: String = "FIXED",
    val powerRampSpeedA: Float = 8.0f,
    val powerRampSpeedRandomMinA: Float = 1.0f,
    val powerRampSpeedRandomMaxA: Float = 10.0f,
    val powerRampSpeedModeB: String = "FIXED",
    val powerRampSpeedB: Float = 2.0f,
    val powerRampSpeedRandomMinB: Float = 1.0f,
    val powerRampSpeedRandomMaxB: Float = 10.0f,
    val powerRampSpeedIntervalModeA: String = "INITIAL",
    val powerRampSpeedIntervalModeB: String = "INITIAL",
    val powerRampNadirChangeModeA: String = "FIXED",
    val powerRampNadirChangeModeB: String = "FIXED",
    val powerRampNadirIntensityARangeStart: Int = 0,
    val powerRampNadirIntensityARangeEnd: Int = 5,
    val powerRampNadirIntensityBRangeStart: Int = 0,
    val powerRampNadirIntensityBRangeEnd: Int = 5,
    val powerRampPeakTimeModeA: String = "RANDOM",
    val powerRampPeakTimeFixedA: Int = 5,
    val powerRampPeakTimeRandomMinA: Int = 2,
    val powerRampPeakTimeRandomMaxA: Int = 8,
    val powerRampCycleModeA: String = "LOOP",
    val powerRampPeakTimeModeB: String = "RANDOM",
    val powerRampPeakTimeFixedB: Int = 5,
    val powerRampPeakTimeRandomMinB: Int = 2,
    val powerRampPeakTimeRandomMaxB: Int = 8,
    val powerRampCycleModeB: String = "LOOP",
)

class PowerRampViewModel : ViewModel() {
    private val _state = MutableStateFlow(PowerRampState())
    val state: StateFlow<PowerRampState> = _state.asStateFlow()

    // 爬坡计数器
    private var powerRampTallyA: Long = 0L
    private var powerRampTallyB: Long = 0L

    // 爬坡计时器
    private var powerRampCounterA: Long = 0L
    private var powerRampCounterB: Long = 0L

    // 开启爬坡时的通道强度
    private var powerRampRecordA: Int = -1
    private var powerRampRecordB: Int = -1

    // 坡底随机强度
    private var powerRampNadirRecordA: Int = -1
    private var powerRampNadirRecordB: Int = -1

    // 爬坡强度最大值
    private var powerRampMaxA: Int = -1
    private var powerRampMaxB: Int = -1

    // 爬坡计时器
    private var powerRampPeakCounterA: Long = 0L
    private var powerRampPeakCounterB: Long = 0L

    // 坡顶计时器
    private var powerRampCurrentPeakTimeA: Long = 0L
    private var powerRampCurrentPeakTimeB: Long = 0L

    // 爬坡方向 (1: 增加, -1: 减少)
    private var powerRampDirectionA: Int = 1
    private var powerRampDirectionB: Int = 1

    // 爬坡往复完成
    private var powerRampRepeatA: Boolean = false
    private var powerRampRepeatB: Boolean = false

    // 开启爬坡时是否随机时间
    private var powerRampIntervalTimeA: Float = -1f
    private var powerRampIntervalTimeB: Float = -1f

    init {
        // Load initial state from Prefs
        _state.update {
            PowerRampState(
                powerRampEnabled = Prefs.powerRampEnabled.value,
                powerRampChannelMode = Prefs.powerRampChannelMode.value,
                powerRampIntensityARangeStart = Prefs.powerRampIntensityARangeStart.value,
                powerRampIntensityARangeEnd = Prefs.powerRampIntensityARangeEnd.value,
                powerRampIntensityBRangeStart = Prefs.powerRampIntensityBRangeStart.value,
                powerRampIntensityBRangeEnd = Prefs.powerRampIntensityBRangeEnd.value,
                powerRampSpeedModeA = Prefs.powerRampSpeedModeA.value,
                powerRampSpeedA = Prefs.powerRampSpeedA.value,
                powerRampSpeedRandomMinA = Prefs.powerRampSpeedRandomMinA.value,
                powerRampSpeedRandomMaxA = Prefs.powerRampSpeedRandomMaxA.value,
                powerRampSpeedModeB = Prefs.powerRampSpeedModeB.value,
                powerRampSpeedB = Prefs.powerRampSpeedB.value,
                powerRampSpeedRandomMinB = Prefs.powerRampSpeedRandomMinB.value,
                powerRampSpeedRandomMaxB = Prefs.powerRampSpeedRandomMaxB.value,
                powerRampSpeedIntervalModeA = Prefs.powerRampSpeedIntervalModeA.value,
                powerRampSpeedIntervalModeB = Prefs.powerRampSpeedIntervalModeB.value,
                powerRampNadirChangeModeA = Prefs.powerRampNadirChangeModeA.value,
                powerRampNadirChangeModeB = Prefs.powerRampNadirChangeModeB.value,
                powerRampNadirIntensityARangeStart = Prefs.powerRampNadirIntensityARangeStart.value,
                powerRampNadirIntensityARangeEnd = Prefs.powerRampNadirIntensityARangeEnd.value,
                powerRampNadirIntensityBRangeStart = Prefs.powerRampNadirIntensityBRangeStart.value,
                powerRampNadirIntensityBRangeEnd = Prefs.powerRampNadirIntensityBRangeEnd.value,
                powerRampPeakTimeModeA = Prefs.powerRampPeakTimeModeA.value,
                powerRampPeakTimeFixedA = Prefs.powerRampPeakTimeFixedA.value,
                powerRampPeakTimeRandomMinA = Prefs.powerRampPeakTimeRandomMinA.value,
                powerRampPeakTimeRandomMaxA = Prefs.powerRampPeakTimeRandomMaxA.value,
                powerRampCycleModeA = Prefs.powerRampCycleModeA.value,
                powerRampPeakTimeModeB = Prefs.powerRampPeakTimeModeB.value,
                powerRampPeakTimeFixedB = Prefs.powerRampPeakTimeFixedB.value,
                powerRampPeakTimeRandomMinB = Prefs.powerRampPeakTimeRandomMinB.value,
                powerRampPeakTimeRandomMaxB = Prefs.powerRampPeakTimeRandomMaxB.value,
                powerRampCycleModeB = Prefs.powerRampCycleModeB.value,
            )
        }
    }

    // Update methods that save to Prefs
    fun setPowerRampEnabled(enabled: Boolean) {
        Prefs.powerRampEnabled.value = enabled
        Prefs.powerRampEnabled.save()
        _state.update { it.copy(powerRampEnabled = enabled) }
    }

    fun setPowerRampChannelMode(mode: String) {
        Prefs.powerRampChannelMode.value = mode
        Prefs.powerRampChannelMode.save()
        _state.update { it.copy(powerRampChannelMode = mode) }
    }

    fun setPowerRampIntensityARange(start: Int, end: Int) {
        Prefs.powerRampIntensityARangeStart.value = start
        Prefs.powerRampIntensityARangeEnd.value = end
        Prefs.powerRampIntensityARangeStart.save()
        Prefs.powerRampIntensityARangeEnd.save()
        _state.update { it.copy(powerRampIntensityARangeStart = start, powerRampIntensityARangeEnd = end) }
    }

    fun setPowerRampIntensityBRange(start: Int, end: Int) {
        Prefs.powerRampIntensityBRangeStart.value = start
        Prefs.powerRampIntensityBRangeEnd.value = end
        Prefs.powerRampIntensityBRangeStart.save()
        Prefs.powerRampIntensityBRangeEnd.save()
        _state.update { it.copy(powerRampIntensityBRangeStart = start, powerRampIntensityBRangeEnd = end) }
    }

    fun setPowerRampSpeedModeA(mode: String) {
        Prefs.powerRampSpeedModeA.value = mode
        Prefs.powerRampSpeedModeA.save()
        _state.update { it.copy(powerRampSpeedModeA = mode) }
    }

    fun setPowerRampSpeedA(speed: Float) {
        Prefs.powerRampSpeedA.value = speed
        Prefs.powerRampSpeedA.save()
        _state.update { it.copy(powerRampSpeedA = speed) }
    }

    fun setPowerRampSpeedRandomA(min: Float, max: Float) {
        Prefs.powerRampSpeedRandomMinA.value = min
        Prefs.powerRampSpeedRandomMaxA.value = max
        Prefs.powerRampSpeedRandomMinA.save()
        Prefs.powerRampSpeedRandomMaxA.save()
        _state.update { it.copy(powerRampSpeedRandomMinA = min, powerRampSpeedRandomMaxA = max) }
    }

    fun setPowerRampSpeedModeB(mode: String) {
        Prefs.powerRampSpeedModeB.value = mode
        Prefs.powerRampSpeedModeB.save()
        _state.update { it.copy(powerRampSpeedModeB = mode) }
    }

    fun setPowerRampSpeedB(speed: Float) {
        Prefs.powerRampSpeedB.value = speed
        Prefs.powerRampSpeedB.save()
        _state.update { it.copy(powerRampSpeedB = speed) }
    }

    fun setPowerRampSpeedRandomB(min: Float, max: Float) {
        Prefs.powerRampSpeedRandomMinB.value = min
        Prefs.powerRampSpeedRandomMaxB.value = max
        Prefs.powerRampSpeedRandomMinB.save()
        Prefs.powerRampSpeedRandomMaxB.save()
        _state.update { it.copy(powerRampSpeedRandomMinB = min, powerRampSpeedRandomMaxB = max) }
    }

    fun setPowerRampSpeedIntervalModeA(mode: String) {
        Prefs.powerRampSpeedIntervalModeA.value = mode
        Prefs.powerRampSpeedIntervalModeA.save()
        _state.update { it.copy(powerRampSpeedIntervalModeA = mode) }
    }

    fun setPowerRampSpeedIntervalModeB(mode: String) {
        Prefs.powerRampSpeedIntervalModeB.value = mode
        Prefs.powerRampSpeedIntervalModeB.save()
        _state.update { it.copy(powerRampSpeedIntervalModeB = mode) }
    }

    fun setPowerRampNadirChangeModeA(mode: String) {
        Prefs.powerRampNadirChangeModeA.value = mode
        Prefs.powerRampNadirChangeModeA.save()
        _state.update { it.copy(powerRampNadirChangeModeA = mode) }
    }

    fun setPowerRampNadirChangeModeB(mode: String) {
        Prefs.powerRampNadirChangeModeB.value = mode
        Prefs.powerRampNadirChangeModeB.save()
        _state.update { it.copy(powerRampNadirChangeModeB = mode) }
    }

    fun setPowerRampNadirIntensityARange(start: Int, end: Int) {
        Prefs.powerRampNadirIntensityARangeStart.value = start
        Prefs.powerRampNadirIntensityARangeEnd.value = end
        Prefs.powerRampNadirIntensityARangeStart.save()
        Prefs.powerRampNadirIntensityARangeEnd.save()
        _state.update { it.copy(powerRampNadirIntensityARangeStart = start, powerRampNadirIntensityARangeEnd = end) }
    }

    fun setPowerRampNadirIntensityBRange(start: Int, end: Int) {
        Prefs.powerRampNadirIntensityBRangeStart.value = start
        Prefs.powerRampNadirIntensityBRangeEnd.value = end
        Prefs.powerRampNadirIntensityBRangeStart.save()
        Prefs.powerRampNadirIntensityBRangeEnd.save()
        _state.update { it.copy(powerRampNadirIntensityBRangeStart = start, powerRampNadirIntensityBRangeEnd = end) }
    }

    fun setPowerRampPeakTimeModeA(mode: String) {
        Prefs.powerRampPeakTimeModeA.value = mode
        Prefs.powerRampPeakTimeModeA.save()
        _state.update { it.copy(powerRampPeakTimeModeA = mode) }
    }

    fun setPowerRampPeakTimeFixedA(time: Int) {
        Prefs.powerRampPeakTimeFixedA.value = time
        Prefs.powerRampPeakTimeFixedA.save()
        _state.update { it.copy(powerRampPeakTimeFixedA = time) }
    }

    fun setPowerRampPeakTimeRandomA(min: Int, max: Int) {
        Prefs.powerRampPeakTimeRandomMinA.value = min
        Prefs.powerRampPeakTimeRandomMaxA.value = max
        Prefs.powerRampPeakTimeRandomMinA.save()
        Prefs.powerRampPeakTimeRandomMaxA.save()
        _state.update { it.copy(powerRampPeakTimeRandomMinA = min, powerRampPeakTimeRandomMaxA = max) }
    }

    fun setPowerRampCycleModeA(mode: String) {
        Prefs.powerRampCycleModeA.value = mode
        Prefs.powerRampCycleModeA.save()
        _state.update { it.copy(powerRampCycleModeA = mode) }
    }

    fun setPowerRampPeakTimeModeB(mode: String) {
        Prefs.powerRampPeakTimeModeB.value = mode
        Prefs.powerRampPeakTimeModeB.save()
        _state.update { it.copy(powerRampPeakTimeModeB = mode) }
    }

    fun setPowerRampPeakTimeFixedB(time: Int) {
        Prefs.powerRampPeakTimeFixedB.value = time
        Prefs.powerRampPeakTimeFixedB.save()
        _state.update { it.copy(powerRampPeakTimeFixedB = time) }
    }

    fun setPowerRampPeakTimeRandomB(min: Int, max: Int) {
        Prefs.powerRampPeakTimeRandomMinB.value = min
        Prefs.powerRampPeakTimeRandomMaxB.value = max
        Prefs.powerRampPeakTimeRandomMinB.save()
        Prefs.powerRampPeakTimeRandomMaxB.save()
        _state.update { it.copy(powerRampPeakTimeRandomMinB = min, powerRampPeakTimeRandomMaxB = max) }
    }

    fun setPowerRampCycleModeB(mode: String) {
        Prefs.powerRampCycleModeB.value = mode
        Prefs.powerRampCycleModeB.save()
        _state.update { it.copy(powerRampCycleModeB = mode) }
    }

    // Reset all power ramp runtime variables
    fun resetPowerRamp() {
        powerRampTallyA = 0L
        powerRampTallyB = 0L
        powerRampCounterA = 0L
        powerRampCounterB = 0L
        powerRampRecordA = -1
        powerRampRecordB = -1
        powerRampMaxA = -1
        powerRampMaxB = -1
        powerRampPeakCounterA = 0L
        powerRampPeakCounterB = 0L
        powerRampCurrentPeakTimeA = 0L
        powerRampCurrentPeakTimeB = 0L
        powerRampDirectionA = 1
        powerRampDirectionB = 1
        powerRampIntervalTimeA = -1f
        powerRampIntervalTimeB = -1f
        powerRampRepeatA = false
        powerRampRepeatB = false
        powerRampNadirRecordA = -1
        powerRampNadirRecordB = -1
    }

    // Process power ramp logic - called from MainOptions.autoIncreasePower
    fun processPowerRamp(elapsed: Double, options: MainOptionsState) {
        val powerRampEnabled = _state.value.powerRampEnabled
        val powerRampChannelMode = _state.value.powerRampChannelMode

        if (!powerRampEnabled) return

        // 获取当前电源强度
        var powerRampCurrentA = MainOptions.getChannelPower(0)
        var powerRampCurrentB = MainOptions.getChannelPower(1)
        var powerRampStart = false

        // 是否有开始爬坡时的强度记录
        if (powerRampRecordA < 0 && powerRampMaxA < 0) {
            powerRampStart = true
            // 如果有坡底随机时间的话 要设置成坡底时间 只是不改变初始的记录强度
            if (powerRampNadirRecordA > 0 || powerRampNadirRecordB > 0) {
                var nextRampRecordA = powerRampCurrentA + powerRampNadirRecordA
                var nextRampRecordB = powerRampCurrentB + powerRampNadirRecordB
                if (powerRampChannelMode == "AB_SYNC") {
                    MainOptions.setChannelPower(0, nextRampRecordA)
                    MainOptions.setChannelPower(1, nextRampRecordB)
                }
                if (powerRampChannelMode == "AB_INDEPENDENT" || powerRampChannelMode == "A_ONLY") {
                    MainOptions.setChannelPower(0, nextRampRecordA)
                }
                if (powerRampChannelMode == "AB_INDEPENDENT" || powerRampChannelMode == "B_ONLY") {
                    MainOptions.setChannelPower(1, nextRampRecordB)
                }
                powerRampCurrentA = nextRampRecordA
                powerRampCurrentB = nextRampRecordB
            }
            // 保存开始爬坡时的强度记录
            powerRampRecordA = powerRampCurrentA
            // 计算最小强度
            val minA = _state.value.powerRampIntensityARangeStart
            powerRampCurrentA += minA
            // 不能小于1
            if (powerRampCurrentA < 1) {
                powerRampCurrentA = 1
                powerRampRecordA = 1
            }
            // 计算爬坡最大强度
            powerRampMaxA = powerRampCurrentA + _state.value.powerRampIntensityARangeEnd
            if (powerRampMaxA > Prefs.powerLimitA.value) {
                powerRampMaxA = Prefs.powerLimitA.value
            }
            // 根据坡顶时间模式计算持续时间
            val peakTimeMode = _state.value.powerRampPeakTimeModeA
            // 固定时间
            if (peakTimeMode == "FIXED") {
                powerRampCurrentPeakTimeA =
                    (_state.value.powerRampPeakTimeFixedA * 1000).toLong()
            } else {
                // 随机时间
                val minTime = _state.value.powerRampPeakTimeRandomMinA
                val maxTime = _state.value.powerRampPeakTimeRandomMaxA
                val randomTime = (minTime..maxTime).random().toLong()
                powerRampCurrentPeakTimeA = randomTime * 1000
            }

            // 爬坡触发时间 随机
            if (_state.value.powerRampSpeedModeA == "RANDOM") {
                val minSpeed = _state.value.powerRampSpeedRandomMinA
                val maxSpeed = _state.value.powerRampSpeedRandomMaxA
                powerRampIntervalTimeA = Random.nextDouble(minSpeed.toDouble(), maxSpeed.toDouble() + 0.1).toFloat()
            } else {
                // 爬坡触发时间 固定
                powerRampIntervalTimeA = _state.value.powerRampSpeedA
            }
            if (powerRampChannelMode == "AB_SYNC") {
                // 保存开始爬坡时的强度记录
                powerRampRecordB = powerRampCurrentB
                // 计算最小强度
                val minA = _state.value.powerRampIntensityARangeStart
                powerRampCurrentB += minA
                // 不能小于1
                if (powerRampCurrentB < 1) {
                    powerRampCurrentB = 1
                    powerRampRecordB = 1
                }
                // 计算爬坡最大强度
                powerRampMaxB = powerRampCurrentB + _state.value.powerRampIntensityARangeEnd
                if (powerRampMaxB > Prefs.powerLimitB.value) {
                    powerRampMaxB = Prefs.powerLimitB.value
                }
                // 固定时间
                if (peakTimeMode == "FIXED") {
                    powerRampCurrentPeakTimeB =
                        (_state.value.powerRampPeakTimeFixedA * 1000).toLong()
                } else {
                    // 随机时间
                    val minTime = _state.value.powerRampPeakTimeRandomMinA
                    val maxTime = _state.value.powerRampPeakTimeRandomMaxA
                    val randomTime = (minTime..maxTime).random().toLong()
                    powerRampCurrentPeakTimeB = randomTime * 1000
                }

                // 爬坡触发时间 随机
                if (_state.value.powerRampSpeedModeA == "RANDOM") {
                    val minSpeed = _state.value.powerRampSpeedRandomMinA
                    val maxSpeed = _state.value.powerRampSpeedRandomMaxA
                    powerRampIntervalTimeA = Random.nextDouble(minSpeed.toDouble(), maxSpeed.toDouble() + 0.1).toFloat()
                } else {
                    // 爬坡触发时间 固定
                    powerRampIntervalTimeA = _state.value.powerRampSpeedA
                }
            }
        }
        if (powerRampRecordB < 0 && powerRampMaxB < 0) {
            powerRampStart = true
            // 保存开始爬坡时的强度记录
            powerRampRecordB = powerRampCurrentB
            // 计算最小强度
            val minB = _state.value.powerRampIntensityBRangeStart
            powerRampCurrentB += minB
            // 不能小于1
            if (powerRampCurrentB < 1) {
                powerRampCurrentB = 1
                powerRampRecordA = 1
            }
            // 计算爬坡最大强度
            powerRampMaxB = powerRampCurrentB + _state.value.powerRampIntensityBRangeEnd
            if (powerRampMaxB > Prefs.powerLimitB.value) {
                powerRampMaxB = Prefs.powerLimitB.value
            }
            // 根据坡顶时间模式计算持续时间
            val peakTimeMode = _state.value.powerRampPeakTimeModeB
            // 固定时间
            if (peakTimeMode == "FIXED") {
                powerRampCurrentPeakTimeB =
                    (_state.value.powerRampPeakTimeFixedB * 1000).toLong()
            } else {
                // 随机时间
                val minTime = _state.value.powerRampPeakTimeRandomMinB
                val maxTime = _state.value.powerRampPeakTimeRandomMaxB
                val randomTime =
                    (minTime + Math.random() * (maxTime - minTime)).toLong()
                powerRampCurrentPeakTimeB = randomTime * 1000
            }
            // 爬坡触发时间 随机
            if (_state.value.powerRampSpeedModeB == "RANDOM") {
                val minSpeed = _state.value.powerRampSpeedRandomMinB
                val maxSpeed = _state.value.powerRampSpeedRandomMaxB
                powerRampIntervalTimeB = Random.nextDouble(minSpeed.toDouble(), maxSpeed.toDouble() + 0.1).toFloat()
            } else {
                // 爬坡触发时间 固定
                powerRampIntervalTimeB = _state.value.powerRampSpeedB
            }
        }
        if (powerRampStart) {
            var message = "Starting power auto ramp";
            if (powerRampChannelMode == "AB_SYNC") {
                message += "channel_A&B: A:[${powerRampCurrentA}~${powerRampMaxA}] B:[${powerRampCurrentB}~${powerRampMaxB}]"
            } else {
                if (powerRampChannelMode == "AB_INDEPENDENT" || powerRampChannelMode == "A_ONLY") {
                    message += " channel_A:[${powerRampCurrentA}~${powerRampMaxA}]"
                }
                if (powerRampChannelMode == "AB_INDEPENDENT" || powerRampChannelMode == "B_ONLY") {
                    message += " channel_B:[${powerRampCurrentB}~${powerRampMaxB}]"
                }
            }
            HLog.d("Power Ramp", message)
        }

        // Power ramp logic
        val elapsedMs = (elapsed * 1000).toLong()
        // 获取爬坡触发时间
        var currentSpeedA = powerRampIntervalTimeA
        var currentSpeedB = powerRampIntervalTimeB
        // 每次计数器重置之后需要判断是否需要每次坡度变化
        if (powerRampPeakCounterA.toInt() == 0) {
            // 如果是A通道每次坡度变化 且是随机 就重新生成一个时间
            if (_state.value.powerRampSpeedIntervalModeA == "EVERY" && _state.value.powerRampSpeedModeA == "RANDOM") {
                val minSpeed = _state.value.powerRampSpeedRandomMinA
                val maxSpeed = _state.value.powerRampSpeedRandomMaxA
                powerRampIntervalTimeA = Random.nextDouble(minSpeed.toDouble(), maxSpeed.toDouble() + 0.1).toFloat()
                HLog.d("Power Ramp", "EVERY RANDOM SpeedA:${powerRampIntervalTimeA}")
            }
        }
        // 每次计数器重置之后需要判断是否需要每次坡度变化
        if (powerRampPeakCounterB.toInt() == 0) {
            // 如果是B通道每次坡度变化 且是随机 就重新生成一个时间
            if (_state.value.powerRampSpeedIntervalModeB == "EVERY" && _state.value.powerRampSpeedModeB == "RANDOM") {
                val minSpeed = _state.value.powerRampSpeedRandomMinB
                val maxSpeed = _state.value.powerRampSpeedRandomMaxB
                powerRampIntervalTimeB = Random.nextDouble(minSpeed.toDouble(), maxSpeed.toDouble() + 0.1).toFloat()
                HLog.d("Power Ramp", "SpeedB:${powerRampIntervalTimeB}")
            }
        }

        var autoIncrementDelayA = (currentSpeedA * 1000).toLong()
        // 如果到达坡顶 就需要增加坡顶的持续时间
        if (powerRampCurrentA > 0 && powerRampCurrentA == powerRampMaxA) {
            autoIncrementDelayA += powerRampCurrentPeakTimeA
        }
        var autoIncrementDelayB = (currentSpeedB * 1000).toLong()
        // 如果到达坡顶 就需要增加坡顶的持续时间
        if (powerRampCurrentB > 0 && powerRampCurrentB == powerRampMaxB) {
            autoIncrementDelayB += powerRampCurrentPeakTimeB
        }

        if (options.channelAPower > 0) powerRampPeakCounterA += elapsedMs
        if (options.channelBPower > 0) powerRampPeakCounterB += elapsedMs

        if (powerRampChannelMode == "AB_SYNC") {
            if (powerRampPeakCounterA >= autoIncrementDelayA) {
                val cycleMode = _state.value.powerRampCycleModeA
                var repeatComplete = false

                val tempPowerA = powerRampCurrentA + powerRampDirectionA
                val tempPowerB = powerRampCurrentB + powerRampDirectionA

                // 如果是往复模式 且 变化的强度大于或小于开启时的强度记录 就说明已经循环一圈
                if (powerRampRepeatA && powerRampCurrentA == powerRampRecordA) {
                    repeatComplete = true
                }

                if (tempPowerA in powerRampRecordA..powerRampMaxA
                    && tempPowerB in powerRampRecordB..powerRampMaxB
                ) {
                    // 增加强度
                    if (powerRampDirectionA > 0) {
                        MainOptions.incrementChannelPower(-1, 1, false)
                    } else {
                        // 减少强度
                        MainOptions.decrementChannelPower(-1, 1, false)
                    }
                } else {
                    if (cycleMode == "LOOP" || repeatComplete) {
                        // 如果有坡底随机时间的话 要重置成变化前的记录值
                        if (powerRampNadirRecordA > 0 || powerRampNadirRecordB > 0) {
                            MainOptions.setChannelPower(0, powerRampRecordA - powerRampNadirRecordA)
                            MainOptions.setChannelPower(1, powerRampRecordB - powerRampNadirRecordB)
                        } else {
                            MainOptions.setChannelPower(0, powerRampRecordA)
                            MainOptions.setChannelPower(1, powerRampRecordB)
                        }
                        // 循环模式：重置强度重新开始爬坡
                        if (_state.value.powerRampNadirChangeModeA == "RANDOM") {
                            val minNadir = _state.value.powerRampNadirIntensityARangeStart
                            val maxNadir = _state.value.powerRampNadirIntensityARangeEnd

                            val randomNadir = (minNadir..maxNadir).random()
                            powerRampNadirRecordA = randomNadir
                            powerRampNadirRecordB = randomNadir
                        }
                        powerRampTallyA++
                        powerRampTallyB++
                        HLog.d(
                            "Power Ramp",
                            "Power to nadir A:${powerRampRecordA} B:${powerRampRecordB} RampTally:${powerRampTallyA}"
                        )
                        powerRampRecordA = -1
                        powerRampMaxA = -1
                        powerRampPeakCounterA = 0L
                        powerRampDirectionA = 1

                        powerRampRecordB = -1
                        powerRampMaxB = -1
                        powerRampPeakCounterB = 0L
                        powerRampDirectionB = 1
                    } else {
                        // 往复模式：反向爬坡
                        powerRampDirectionA = -powerRampDirectionA
                        powerRampDirectionB = -powerRampDirectionB
                        powerRampRepeatA = true
                    }
                }
                // 重置爬坡计时器
                powerRampPeakCounterA = 0L
                powerRampPeakCounterB = 0L
            }
        } else {
            if (powerRampChannelMode == "AB_INDEPENDENT" || powerRampChannelMode == "A_ONLY") {
                if (powerRampPeakCounterA >= autoIncrementDelayA) {
                    val cycleMode = _state.value.powerRampCycleModeA
                    var repeatComplete = false

                    val tempPowerA = powerRampCurrentA + powerRampDirectionA

                    // 如果是往复模式 且 变化的强度大于或小于开启时的强度记录 就说明已经循环一圈
                    if (powerRampRepeatA && powerRampCurrentA == powerRampRecordA) {
                        repeatComplete = true
                    }
                    if (tempPowerA in powerRampRecordA..powerRampMaxA
                    ) {
                        // 增加强度
                        if (powerRampDirectionA > 0) {
                            MainOptions.incrementChannelPower(0, 1, false)
                        } else {
                            // 减少强度
                            MainOptions.decrementChannelPower(0, 1, false)
                        }
                    } else {
                        // 如果是循环模式或者 往复完成一圈后
                        if (cycleMode == "LOOP" || repeatComplete) {
                            // 如果有坡底随机时间的话 要重置成变化前的记录值
                            if (powerRampNadirRecordA != 0) {
                                MainOptions.setChannelPower(0, powerRampRecordA - powerRampNadirRecordA)
                            } else {
                                MainOptions.setChannelPower(0, powerRampRecordA)
                            }
                            // 循环模式：重置强度重新开始爬坡
                            if (_state.value.powerRampNadirChangeModeA == "RANDOM") {
                                val minNadir = _state.value.powerRampNadirIntensityARangeStart
                                val maxNadir = _state.value.powerRampNadirIntensityARangeEnd
                                powerRampNadirRecordA = (minNadir..maxNadir).random()
                            }
                            powerRampTallyA++
                            HLog.d(
                                "Power Ramp",
                                "Power to nadir A:${powerRampRecordA} RampTallyA:${powerRampTallyA}"
                            )
                            powerRampRecordA = -1
                            powerRampMaxA = -1
                            powerRampPeakCounterA = 0L
                            powerRampDirectionA = 1
                        } else {
                            // 往复模式：反向爬坡
                            powerRampDirectionA = -powerRampDirectionA
                            powerRampRepeatA = true
                        }
                    }
                    // 重置爬坡计时器
                    powerRampPeakCounterA = 0L
                }
            }
            if (powerRampChannelMode == "AB_INDEPENDENT" || powerRampChannelMode == "B_ONLY") {
                if (powerRampPeakCounterB >= autoIncrementDelayB) {
                    val cycleMode = _state.value.powerRampCycleModeB
                    var repeatComplete = false

                    val tempPowerB = powerRampCurrentB + powerRampDirectionB

                    // 如果是往复模式 且 变化的强度大于或小于开启时的强度记录 就说明已经循环一圈
                    if (powerRampRepeatA && powerRampCurrentA == powerRampRecordA) {
                        repeatComplete = true
                    }
                    if (tempPowerB in powerRampRecordB..powerRampMaxB
                    ) {
                        // 增加强度
                        if (powerRampDirectionB > 0) {
                            MainOptions.incrementChannelPower(1, 1, false)
                        } else {
                            // 减少强度
                            MainOptions.decrementChannelPower(1, 1, false)
                        }
                    } else {
                        val cycleMode = _state.value.powerRampCycleModeB
                        if (cycleMode == "LOOP" || repeatComplete) {
                            // 如果有坡底随机时间的话 要重置成变化前的记录值
                            if (powerRampNadirRecordB != 0) {
                                MainOptions.setChannelPower(1, powerRampRecordB - powerRampNadirRecordB)
                            } else {
                                MainOptions.setChannelPower(1, powerRampRecordB)
                            }
                            // 循环模式：重置强度重新开始爬坡
                            if (_state.value.powerRampNadirChangeModeB == "RANDOM") {
                                val minNadir = _state.value.powerRampNadirIntensityBRangeStart
                                val maxNadir = _state.value.powerRampNadirIntensityBRangeEnd
                                powerRampNadirRecordB = (minNadir..maxNadir).random()
                            }
                            HLog.d(
                                "Power Ramp",
                                "Power to nadir B:${powerRampRecordB} RampTallyB:${powerRampTallyB}"
                            )
                            powerRampRecordB = -1
                            powerRampMaxB = -1
                            powerRampPeakCounterB = 0L
                            powerRampDirectionB = 1
                        } else {
                            // 往复模式：反向爬坡
                            powerRampDirectionB = -powerRampDirectionB
                            powerRampRepeatB = true
                        }
                    }
                    // 重置爬坡计时器
                    powerRampPeakCounterB = 0L
                }
            }
        }
    }
}

@Composable
fun PowerRampPanel(
    viewModel: PowerRampViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val powerSyncEnabled by Prefs.powerSyncEnabled.collectAsStateWithLifecycle()
    val powerRampEnabled by Prefs.powerRampEnabled.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = "电源强度自动选项", style = MaterialTheme.typography.headlineSmall)
    }

    SwitchWithLabel(
        label = "电源强度同步",
        checked = powerSyncEnabled,
        onCheckedChange = {
            Prefs.powerSyncEnabled.value = it
            Prefs.powerSyncEnabled.save()
        }
    )

    SwitchWithLabel(
        label = "电源强度自动爬坡",
        checked = powerRampEnabled,
        onCheckedChange = { viewModel.setPowerRampEnabled(it) }
    )

    if (state.powerRampEnabled) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "爬坡通道", style = MaterialTheme.typography.labelLarge)
            OptionPicker(
                currentValue = state.powerRampChannelMode,
                onValueChange = { viewModel.setPowerRampChannelMode(it) },
                options = listOf("AB_SYNC", "AB_INDEPENDENT", "A_ONLY", "B_ONLY"),
                getText = {
                    when (it) {
                        "AB_SYNC" -> "AB同步"
                        "AB_INDEPENDENT" -> "AB单独"
                        "A_ONLY" -> "仅A"
                        "B_ONLY" -> "仅B"
                        else -> it
                    }
                }
            )
        }

        when (state.powerRampChannelMode) {
            "AB_SYNC" -> {
                Text(text = "A&B通道强度：从 ${state.powerRampIntensityARangeStart} 逐渐变化到 +${state.powerRampIntensityARangeEnd}", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "${state.powerRampIntensityARangeStart}", modifier = Modifier.widthIn(40.dp))
                    RangeSlider(
                        modifier = Modifier.weight(1f),
                        value = state.powerRampIntensityARangeStart.toFloat()..state.powerRampIntensityARangeEnd.toFloat(),
                        onValueChange = { newRange ->
                            viewModel.setPowerRampIntensityARange(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                        },
                        valueRange = -50.0f..50.0f,
                        steps = 99
                    )
                    Text(text = "${state.powerRampIntensityARangeEnd}", modifier = Modifier.widthIn(40.dp))
                }

                // 变化速度类型
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "变化速度类型", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampSpeedModeA,
                        onValueChange = { viewModel.setPowerRampSpeedModeA(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // 变化速度间隔
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "变化速度间隔", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampSpeedIntervalModeA,
                        onValueChange = { viewModel.setPowerRampSpeedIntervalModeA(it) },
                        options = listOf("INITIAL", "EVERY"),
                        getText = {
                            when (it) {
                                "INITIAL" -> "每次爬坡初始"
                                "EVERY" -> "每次坡度变化"
                                else -> it
                            }
                        }
                    )
                }

                // 变化速度
                if (state.powerRampSpeedModeA == "FIXED") {
                    val changeCountA = state.powerRampIntensityARangeEnd - state.powerRampIntensityARangeStart
                    val totalTimeA = state.powerRampSpeedA * changeCountA
                    Text(text = "变化速度: 每 ${String.format(Locale.US, "%.1f", state.powerRampSpeedA)} 秒变化一次 共 ${String.format(Locale.US, "%.1f", totalTimeA)} 秒", style = MaterialTheme.typography.labelLarge)
                    SliderWithLabel(
                        label = "",
                        value = state.powerRampSpeedA,
                        onValueChange = { viewModel.setPowerRampSpeedA(it) },
                        onValueChangeFinished = {},
                        valueRange = 0.1f..60.0f,
                        steps = 599,
                        valueDisplay = { String.format(Locale.US, "%.1f", it) }
                    )
                } else {
                    val changeCountA = state.powerRampIntensityARangeEnd - state.powerRampIntensityARangeStart
                    val totalTimeMinA = state.powerRampSpeedRandomMinA * changeCountA
                    val totalTimeMaxA = state.powerRampSpeedRandomMaxA * changeCountA
                    Text(text = "变化速度: 每 ${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMinA)}~${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMaxA)} 秒变化一次 共 ${String.format(Locale.US, "%.1f", totalTimeMinA)}~${String.format(Locale.US, "%.1f", totalTimeMaxA)} 秒", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMinA)}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampSpeedRandomMinA.toFloat()..state.powerRampSpeedRandomMaxA.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampSpeedRandomA(newRange.start, newRange.endInclusive)
                            },
                            valueRange = 0.1f..60.0f,
                            steps = 599
                        )
                        Text(text = "${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMaxA)}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // 坡底变化模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "坡底变化模式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampNadirChangeModeA,
                        onValueChange = { viewModel.setPowerRampNadirChangeModeA(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // 坡底变化强度
                if (state.powerRampNadirChangeModeA == "RANDOM") {
                    Text(text = "坡底变化强度: ${state.powerRampNadirIntensityARangeStart} - ${state.powerRampNadirIntensityARangeEnd} 内随机", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${state.powerRampNadirIntensityARangeStart}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampNadirIntensityARangeStart.toFloat()..state.powerRampNadirIntensityARangeEnd.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampNadirIntensityARange(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                            },
                            valueRange = -50.0f..50.0f,
                            steps = 99
                        )
                        Text(text = "${state.powerRampNadirIntensityARangeEnd}", modifier = Modifier.widthIn(40.dp))
                    }
                } else {
                    Text(text = "坡底强度为开启时的电源强度值", style = MaterialTheme.typography.labelLarge)
                }

                // 坡顶时间模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "坡顶时间模式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampPeakTimeModeA,
                        onValueChange = { viewModel.setPowerRampPeakTimeModeA(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // 坡顶持续时间
                if (state.powerRampPeakTimeModeA == "FIXED") {
                    SliderWithLabel(
                        label = "坡顶持续时间(秒)",
                        value = state.powerRampPeakTimeFixedA.toFloat(),
                        onValueChange = { viewModel.setPowerRampPeakTimeFixedA(it.roundToInt()) },
                        onValueChangeFinished = {},
                        valueRange = 0.0f..60.0f,
                        steps = 59,
                        valueDisplay = { it.roundToInt().toString() }
                    )
                } else {
                    Text(text = "坡顶持续时间(秒): ${state.powerRampPeakTimeRandomMinA} - ${state.powerRampPeakTimeRandomMaxA}", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${state.powerRampPeakTimeRandomMinA}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampPeakTimeRandomMinA.toFloat()..state.powerRampPeakTimeRandomMaxA.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampPeakTimeRandomA(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                            },
                            valueRange = 0.0f..60.0f,
                            steps = 59
                        )
                        Text(text = "${state.powerRampPeakTimeRandomMaxA}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // 循环方式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "循环方式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampCycleModeA,
                        onValueChange = { viewModel.setPowerRampCycleModeA(it) },
                        options = listOf("LOOP", "REPEAT"),
                        getText = {
                            when (it) {
                                "LOOP" -> "循环"
                                "REPEAT" -> "往复"
                                else -> it
                            }
                        }
                    )
                }
            }
            "AB_INDEPENDENT" -> {
                Text(text = "A通道：强度从 ${state.powerRampIntensityARangeStart} 逐渐变化到 ${state.powerRampIntensityARangeEnd}", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "${state.powerRampIntensityARangeStart}", modifier = Modifier.widthIn(40.dp))
                    RangeSlider(
                        modifier = Modifier.weight(1f),
                        value = state.powerRampIntensityARangeStart.toFloat()..state.powerRampIntensityARangeEnd.toFloat(),
                        onValueChange = { newRange ->
                            viewModel.setPowerRampIntensityARange(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                        },
                        valueRange = -50.0f..50.0f,
                        steps = 99
                    )
                    Text(text = "${state.powerRampIntensityARangeEnd}", modifier = Modifier.widthIn(40.dp))
                }
                // A通道变化速度类型
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "A通道变化速度类型", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampSpeedModeA,
                        onValueChange = { viewModel.setPowerRampSpeedModeA(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // A通道变化速度间隔
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "A通道变化速度间隔", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampSpeedIntervalModeA,
                        onValueChange = { viewModel.setPowerRampSpeedIntervalModeA(it) },
                        options = listOf("INITIAL", "EVERY"),
                        getText = {
                            when (it) {
                                "INITIAL" -> "每次爬坡初始"
                                "EVERY" -> "每次坡度变化"
                                else -> it
                            }
                        }
                    )
                }

                // A通道变化速度间隔
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "A通道变化速度间隔", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampSpeedIntervalModeA,
                        onValueChange = { viewModel.setPowerRampSpeedIntervalModeA(it) },
                        options = listOf("INITIAL", "EVERY"),
                        getText = {
                            when (it) {
                                "INITIAL" -> "每次爬坡初始"
                                "EVERY" -> "每次坡度变化"
                                else -> it
                            }
                        }
                    )
                }

                // A通道变化速度
                if (state.powerRampSpeedModeA == "FIXED") {
                    val changeCountA = state.powerRampIntensityARangeEnd - state.powerRampIntensityARangeStart
                    val totalTimeA = state.powerRampSpeedA * changeCountA
                    Text(text = "A通道变化速度: 每 ${String.format(Locale.US, "%.1f", state.powerRampSpeedA)} 秒变化一次 共 ${String.format(Locale.US, "%.1f", totalTimeA)} 秒", style = MaterialTheme.typography.labelLarge)
                    SliderWithLabel(
                        label = "",
                        value = state.powerRampSpeedA,
                        onValueChange = { viewModel.setPowerRampSpeedA(it) },
                        onValueChangeFinished = {},
                        valueRange = 0.1f..60.0f,
                        steps = 599,
                        valueDisplay = { String.format(Locale.US, "%.1f", it) }
                    )
                } else {
                    val changeCountA = state.powerRampIntensityARangeEnd - state.powerRampIntensityARangeStart
                    val totalTimeMinA = state.powerRampSpeedRandomMinA * changeCountA
                    val totalTimeMaxA = state.powerRampSpeedRandomMaxA * changeCountA
                    Text(text = "A通道变化速度: 每 ${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMinA)}~${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMaxA)} 秒变化一次 共 ${String.format(Locale.US, "%.1f", totalTimeMinA)}~${String.format(Locale.US, "%.1f", totalTimeMaxA)} 秒", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMinA)}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampSpeedRandomMinA.toFloat()..state.powerRampSpeedRandomMaxA.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampSpeedRandomA(newRange.start, newRange.endInclusive)
                            },
                            valueRange = 0.1f..60.0f,
                            steps = 599
                        )
                        Text(text = "${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMaxA)}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // A通道坡底变化模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "A通道坡底变化模式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampNadirChangeModeA,
                        onValueChange = { viewModel.setPowerRampNadirChangeModeA(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // 坡底变化强度
                if (state.powerRampNadirChangeModeA == "RANDOM") {
                    Text(text = "A通道坡底变化强度: ${state.powerRampNadirIntensityARangeStart} - ${state.powerRampNadirIntensityARangeEnd} 内随机", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${state.powerRampNadirIntensityARangeStart}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampNadirIntensityARangeStart.toFloat()..state.powerRampNadirIntensityARangeEnd.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampNadirIntensityARange(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                            },
                            valueRange = -50.0f..50.0f,
                            steps = 99
                        )
                        Text(text = "${state.powerRampNadirIntensityARangeEnd}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // A通道坡顶时间模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "A通道坡顶时间模式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampPeakTimeModeA,
                        onValueChange = { viewModel.setPowerRampPeakTimeModeA(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // A通道坡顶持续时间
                if (state.powerRampPeakTimeModeA == "FIXED") {
                    SliderWithLabel(
                        label = "A通道坡顶持续时间(秒)",
                        value = state.powerRampPeakTimeFixedA.toFloat(),
                        onValueChange = { viewModel.setPowerRampPeakTimeFixedA(it.roundToInt()) },
                        onValueChangeFinished = {},
                        valueRange = 0.0f..60.0f,
                        steps = 59,
                        valueDisplay = { it.roundToInt().toString() }
                    )
                } else {
                    Text(text = "A通道坡顶持续时间(秒): ${state.powerRampPeakTimeRandomMinA} - ${state.powerRampPeakTimeRandomMaxA}", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${state.powerRampPeakTimeRandomMinA}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampPeakTimeRandomMinA.toFloat()..state.powerRampPeakTimeRandomMaxA.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampPeakTimeRandomA(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                            },
                            valueRange = 0.0f..60.0f,
                            steps = 59
                        )
                        Text(text = "${state.powerRampPeakTimeRandomMaxA}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // A通道循环方式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "A通道循环方式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampCycleModeA,
                        onValueChange = { viewModel.setPowerRampCycleModeA(it) },
                        options = listOf("LOOP", "REPEAT"),
                        getText = {
                            when (it) {
                                "LOOP" -> "循环"
                                "REPEAT" -> "往复"
                                else -> it
                            }
                        }
                    )
                }

                Text(text = "B通道：强度从 ${state.powerRampIntensityBRangeStart} 逐渐变化到 ${state.powerRampIntensityBRangeEnd}", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "${state.powerRampIntensityBRangeStart}", modifier = Modifier.widthIn(40.dp))
                    RangeSlider(
                        modifier = Modifier.weight(1f),
                        value = state.powerRampIntensityBRangeStart.toFloat()..state.powerRampIntensityBRangeEnd.toFloat(),
                        onValueChange = { newRange ->
                            viewModel.setPowerRampIntensityBRange(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                        },
                        valueRange = -50.0f..50.0f,
                        steps = 99
                    )
                    Text(text = "${state.powerRampIntensityBRangeEnd}", modifier = Modifier.widthIn(40.dp))
                }
                // B通道变化速度类型
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "B通道变化速度类型", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampSpeedModeB,
                        onValueChange = { viewModel.setPowerRampSpeedModeB(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // B通道变化速度间隔
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "B通道变化速度间隔", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampSpeedIntervalModeB,
                        onValueChange = { viewModel.setPowerRampSpeedIntervalModeB(it) },
                        options = listOf("INITIAL", "EVERY"),
                        getText = {
                            when (it) {
                                "INITIAL" -> "每次爬坡初始"
                                "EVERY" -> "每次坡度变化"
                                else -> it
                            }
                        }
                    )
                }

                // B通道变化速度
                if (state.powerRampSpeedModeB == "FIXED") {
                    val changeCountB = state.powerRampIntensityBRangeEnd - state.powerRampIntensityBRangeStart
                    val totalTimeB = state.powerRampSpeedB * changeCountB
                    Text(text = "B通道变化速度: 每 ${String.format(Locale.US, "%.1f", state.powerRampSpeedB)} 秒变化一次 共 ${String.format(Locale.US, "%.1f", totalTimeB)} 秒", style = MaterialTheme.typography.labelLarge)
                    SliderWithLabel(
                        label = "",
                        value = state.powerRampSpeedB,
                        onValueChange = { viewModel.setPowerRampSpeedB(it) },
                        onValueChangeFinished = {},
                        valueRange = 0.1f..60.0f,
                        steps = 599,
                        valueDisplay = { String.format(Locale.US, "%.1f", it) }
                    )
                } else {
                    val changeCountB = state.powerRampIntensityBRangeEnd - state.powerRampIntensityBRangeStart
                    val totalTimeMinB = state.powerRampSpeedRandomMinB * changeCountB
                    val totalTimeMaxB = state.powerRampSpeedRandomMaxB * changeCountB
                    Text(text = "B通道变化速度: 每 ${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMinB)}~${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMaxB)} 秒变化一次 共 ${String.format(Locale.US, "%.1f", totalTimeMinB)}~${String.format(Locale.US, "%.1f", totalTimeMaxB)} 秒", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMinB)}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampSpeedRandomMinB.toFloat()..state.powerRampSpeedRandomMaxB.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampSpeedRandomB(newRange.start, newRange.endInclusive)
                            },
                            valueRange = 0.1f..60.0f,
                            steps = 599
                        )
                        Text(text = "${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMaxB)}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // B通道坡底变化模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "B通道坡底变化模式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampNadirChangeModeB,
                        onValueChange = { viewModel.setPowerRampNadirChangeModeB(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // B通道坡底变化强度
                if (state.powerRampNadirChangeModeB == "RANDOM") {
                    Text(text = "B通道坡底变化强度: ${state.powerRampNadirIntensityBRangeStart} - ${state.powerRampNadirIntensityBRangeEnd} 内随机", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${state.powerRampNadirIntensityBRangeStart}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampNadirIntensityBRangeStart.toFloat()..state.powerRampNadirIntensityBRangeEnd.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampNadirIntensityBRange(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                            },
                            valueRange = -50.0f..50.0f,
                            steps = 99
                        )
                        Text(text = "${state.powerRampNadirIntensityBRangeEnd}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // B通道坡顶时间模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "B通道坡顶时间模式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampPeakTimeModeB,
                        onValueChange = { viewModel.setPowerRampPeakTimeModeB(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // B通道坡顶持续时间
                if (state.powerRampPeakTimeModeB == "FIXED") {
                    SliderWithLabel(
                        label = "B通道坡顶持续时间(秒)",
                        value = state.powerRampPeakTimeFixedB.toFloat(),
                        onValueChange = { viewModel.setPowerRampPeakTimeFixedB(it.roundToInt()) },
                        onValueChangeFinished = {},
                        valueRange = 0.0f..60.0f,
                        steps = 59,
                        valueDisplay = { it.roundToInt().toString() }
                    )
                } else {
                    Text(text = "B通道坡顶持续时间(秒): ${state.powerRampPeakTimeRandomMinB} - ${state.powerRampPeakTimeRandomMaxB}", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${state.powerRampPeakTimeRandomMinB}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampPeakTimeRandomMinB.toFloat()..state.powerRampPeakTimeRandomMaxB.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampPeakTimeRandomB(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                            },
                            valueRange = 0.0f..60.0f,
                            steps = 59
                        )
                        Text(text = "${state.powerRampPeakTimeRandomMaxB}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // B通道循环方式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "B通道循环方式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampCycleModeB,
                        onValueChange = { viewModel.setPowerRampCycleModeB(it) },
                        options = listOf("LOOP", "REPEAT"),
                        getText = {
                            when (it) {
                                "LOOP" -> "循环"
                                "REPEAT" -> "往复"
                                else -> it
                            }
                        }
                    )
                }
            }
            "A_ONLY" -> {
                Text(text = "A通道：强度从 ${state.powerRampIntensityARangeStart} 逐渐变化到 ${state.powerRampIntensityARangeEnd}", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "${state.powerRampIntensityARangeStart}", modifier = Modifier.widthIn(40.dp))
                    RangeSlider(
                        modifier = Modifier.weight(1f),
                        value = state.powerRampIntensityARangeStart.toFloat()..state.powerRampIntensityARangeEnd.toFloat(),
                        onValueChange = { newRange ->
                            viewModel.setPowerRampIntensityARange(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                        },
                        valueRange = -50.0f..50.0f,
                        steps = 99
                    )
                    Text(text = "${state.powerRampIntensityARangeEnd}", modifier = Modifier.widthIn(40.dp))
                }
                // 变化速度类型
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "变化速度类型", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampSpeedModeA,
                        onValueChange = { viewModel.setPowerRampSpeedModeA(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // 变化速度
                if (state.powerRampSpeedModeA == "FIXED") {
                    val changeCountA = state.powerRampIntensityARangeEnd - state.powerRampIntensityARangeStart
                    val totalTimeA = state.powerRampSpeedA * changeCountA
                    Text(text = "变化速度: 每 ${String.format(Locale.US, "%.1f", state.powerRampSpeedA)} 秒变化一次 共 ${String.format(Locale.US, "%.1f", totalTimeA)} 秒", style = MaterialTheme.typography.labelLarge)
                    SliderWithLabel(
                        label = "",
                        value = state.powerRampSpeedA,
                        onValueChange = { viewModel.setPowerRampSpeedA(it) },
                        onValueChangeFinished = {},
                        valueRange = 0.1f..60.0f,
                        steps = 599,
                        valueDisplay = { String.format(Locale.US, "%.1f", it) }
                    )
                } else {
                    val changeCountA = state.powerRampIntensityARangeEnd - state.powerRampIntensityARangeStart
                    val totalTimeMinA = state.powerRampSpeedRandomMinA * changeCountA
                    val totalTimeMaxA = state.powerRampSpeedRandomMaxA * changeCountA
                    Text(text = "变化速度: 每 ${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMinA)}~${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMaxA)} 秒变化一次 共 ${String.format(Locale.US, "%.1f", totalTimeMinA)}~${String.format(Locale.US, "%.1f", totalTimeMaxA)} 秒", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMinA)}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampSpeedRandomMinA.toFloat()..state.powerRampSpeedRandomMaxA.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampSpeedRandomA(newRange.start, newRange.endInclusive)
                            },
                            valueRange = 0.1f..60.0f,
                            steps = 599
                        )
                        Text(text = "${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMaxA)}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // A通道坡底变化模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "坡底变化模式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampNadirChangeModeA,
                        onValueChange = { viewModel.setPowerRampNadirChangeModeA(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // 坡底变化强度
                if (state.powerRampNadirChangeModeA == "RANDOM") {
                    Text(text = "坡底变化强度: ${state.powerRampNadirIntensityARangeStart} - ${state.powerRampNadirIntensityARangeEnd} 内随机", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${state.powerRampNadirIntensityARangeStart}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampNadirIntensityARangeStart.toFloat()..state.powerRampNadirIntensityARangeEnd.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampNadirIntensityARange(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                            },
                            valueRange = -50.0f..50.0f,
                            steps = 99
                        )
                        Text(text = "${state.powerRampNadirIntensityARangeEnd}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // 坡顶时间模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "坡顶时间模式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampPeakTimeModeA,
                        onValueChange = { viewModel.setPowerRampPeakTimeModeA(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // 坡顶持续时间
                if (state.powerRampPeakTimeModeA == "FIXED") {
                    SliderWithLabel(
                        label = "坡顶持续时间(秒)",
                        value = state.powerRampPeakTimeFixedA.toFloat(),
                        onValueChange = { viewModel.setPowerRampPeakTimeFixedA(it.roundToInt()) },
                        onValueChangeFinished = {},
                        valueRange = 0.0f..60.0f,
                        steps = 59,
                        valueDisplay = { it.roundToInt().toString() }
                    )
                } else {
                    Text(text = "坡顶持续时间(秒): ${state.powerRampPeakTimeRandomMinA} - ${state.powerRampPeakTimeRandomMaxA}", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${state.powerRampPeakTimeRandomMinA}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampPeakTimeRandomMinA.toFloat()..state.powerRampPeakTimeRandomMaxA.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampPeakTimeRandomA(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                            },
                            valueRange = 0.0f..60.0f,
                            steps = 59
                        )
                        Text(text = "${state.powerRampPeakTimeRandomMaxA}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // 循环方式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "循环方式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampCycleModeA,
                        onValueChange = { viewModel.setPowerRampCycleModeA(it) },
                        options = listOf("LOOP", "REPEAT"),
                        getText = {
                            when (it) {
                                "LOOP" -> "循环"
                                "REPEAT" -> "往复"
                                else -> it
                            }
                        }
                    )
                }
            }
            "B_ONLY" -> {
                Text(text = "B通道：强度从 ${state.powerRampIntensityBRangeStart} 逐渐变化到 ${state.powerRampIntensityBRangeEnd}", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "${state.powerRampIntensityBRangeStart}", modifier = Modifier.widthIn(40.dp))
                    RangeSlider(
                        modifier = Modifier.weight(1f),
                        value = state.powerRampIntensityBRangeStart.toFloat()..state.powerRampIntensityBRangeEnd.toFloat(),
                        onValueChange = { newRange ->
                            viewModel.setPowerRampIntensityBRange(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                        },
                        valueRange = -50.0f..50.0f,
                        steps = 99
                    )
                    Text(text = "${state.powerRampIntensityBRangeEnd}", modifier = Modifier.widthIn(40.dp))
                }
                // 变化速度类型
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "变化速度类型", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampSpeedModeB,
                        onValueChange = { viewModel.setPowerRampSpeedModeB(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // 变化速度
                if (state.powerRampSpeedModeB == "FIXED") {
                    val changeCountB = state.powerRampIntensityBRangeEnd - state.powerRampIntensityBRangeStart
                    val totalTimeB = state.powerRampSpeedB * changeCountB
                    Text(text = "变化速度: 每 ${String.format(Locale.US, "%.1f", state.powerRampSpeedB)} 秒变化一次 共 ${String.format(Locale.US, "%.1f", totalTimeB)} 秒", style = MaterialTheme.typography.labelLarge)
                    SliderWithLabel(
                        label = "",
                        value = state.powerRampSpeedB,
                        onValueChange = { viewModel.setPowerRampSpeedB(it) },
                        onValueChangeFinished = {},
                        valueRange = 0.1f..60.0f,
                        steps = 599,
                        valueDisplay = { String.format(Locale.US, "%.1f", it) }
                    )
                } else {
                    Text(text = "变化速度(秒): ${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMinB)} - ${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMaxB)}", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMinB)}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampSpeedRandomMinB.toFloat()..state.powerRampSpeedRandomMaxB.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampSpeedRandomB(newRange.start, newRange.endInclusive)
                            },
                            valueRange = 0.1f..60.0f,
                            steps = 599
                        )
                        Text(text = "${String.format(Locale.US, "%.1f", state.powerRampSpeedRandomMaxB)}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // B通道坡底变化模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "坡底变化模式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampNadirChangeModeB,
                        onValueChange = { viewModel.setPowerRampNadirChangeModeB(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // B通道坡底变化强度
                if (state.powerRampNadirChangeModeB == "RANDOM") {
                    Text(text = "坡底变化强度: ${state.powerRampNadirIntensityBRangeStart} - ${state.powerRampNadirIntensityBRangeEnd} 内随机", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${state.powerRampNadirIntensityBRangeStart}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampNadirIntensityBRangeStart.toFloat()..state.powerRampNadirIntensityBRangeEnd.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampNadirIntensityBRange(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                            },
                            valueRange = -50.0f..50.0f,
                            steps = 99
                        )
                        Text(text = "${state.powerRampNadirIntensityBRangeEnd}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // 坡顶时间模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "坡顶时间模式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampPeakTimeModeB,
                        onValueChange = { viewModel.setPowerRampPeakTimeModeB(it) },
                        options = listOf("FIXED", "RANDOM"),
                        getText = {
                            when (it) {
                                "FIXED" -> "固定"
                                "RANDOM" -> "随机"
                                else -> it
                            }
                        }
                    )
                }

                // 坡顶持续时间
                if (state.powerRampPeakTimeModeB == "FIXED") {
                    SliderWithLabel(
                        label = "坡顶持续时间(秒)",
                        value = state.powerRampPeakTimeFixedB.toFloat(),
                        onValueChange = { viewModel.setPowerRampPeakTimeFixedB(it.roundToInt()) },
                        onValueChangeFinished = {},
                        valueRange = 0.0f..60.0f,
                        steps = 59,
                        valueDisplay = { it.roundToInt().toString() }
                    )
                } else {
                    Text(text = "坡顶持续时间(秒): ${state.powerRampPeakTimeRandomMinB} - ${state.powerRampPeakTimeRandomMaxB}", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${state.powerRampPeakTimeRandomMinB}", modifier = Modifier.widthIn(40.dp))
                        RangeSlider(
                            modifier = Modifier.weight(1f),
                            value = state.powerRampPeakTimeRandomMinB.toFloat()..state.powerRampPeakTimeRandomMaxB.toFloat(),
                            onValueChange = { newRange ->
                                viewModel.setPowerRampPeakTimeRandomB(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                            },
                            valueRange = 0.0f..60.0f,
                            steps = 59
                        )
                        Text(text = "${state.powerRampPeakTimeRandomMaxB}", modifier = Modifier.widthIn(40.dp))
                    }
                }

                // 循环方式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "循环方式", style = MaterialTheme.typography.labelLarge)
                    OptionPicker(
                        currentValue = state.powerRampCycleModeB,
                        onValueChange = { viewModel.setPowerRampCycleModeB(it) },
                        options = listOf("LOOP", "REPEAT"),
                        getText = {
                            when (it) {
                                "LOOP" -> "循环"
                                "REPEAT" -> "往复"
                                else -> it
                            }
                        }
                    )
                }
            }
        }
    }
}
