package com.example.howl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun CivetSensorPanel(
    civetViewModel: CivetSensorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        civetViewModel.initialize(context)
    }
    
    val displayPressure by civetViewModel.displayPressure.collectAsStateWithLifecycle()
    val pressureHistory by civetViewModel.pressureHistory.collectAsStateWithLifecycle()
    val testPressure by civetViewModel.testPressure.collectAsStateWithLifecycle()
    val connectionState by civetViewModel.connectionStatus.collectAsStateWithLifecycle()
    val pressureRange by civetViewModel.pressureRange.collectAsStateWithLifecycle()
    val powerRange by civetViewModel.powerRange.collectAsStateWithLifecycle()
    val smoothingTime by civetViewModel.smoothingTime.collectAsStateWithLifecycle()
    val screenRotation by civetViewModel.screenRotation.collectAsStateWithLifecycle()
    val powerMappingEnabled by civetViewModel.powerMappingEnabled.collectAsStateWithLifecycle()
    val batteryLevel by civetViewModel.batteryLevel.collectAsStateWithLifecycle()
    val denials by civetViewModel.denials.collectAsStateWithLifecycle()
    val denialsCount by civetViewModel.denialsCount.collectAsStateWithLifecycle()
    val cooldownTime by civetViewModel.cooldownTime.collectAsStateWithLifecycle()
    val cooldownRemaining by civetViewModel.cooldownRemaining.collectAsStateWithLifecycle()
    val denialToZero by civetViewModel.denialToZero.collectAsStateWithLifecycle()
    val opossumMappingEnabled by civetViewModel.opossumMappingEnabled.collectAsStateWithLifecycle()
    val opossumIntensityRange by civetViewModel.opossumIntensityRange.collectAsStateWithLifecycle()
    val opossumMapped by civetViewModel.opossumMapped.collectAsStateWithLifecycle()
    
    var statusText by remember { mutableStateOf("Disconnected") }
    var mappedPower by remember { mutableStateOf(0) }
    
    LaunchedEffect(connectionState, batteryLevel) {
        statusText = civetViewModel.getStatusText()
    }
    
    LaunchedEffect(displayPressure, pressureRange, powerRange, opossumIntensityRange, opossumMappingEnabled) {
        mappedPower = civetViewModel.calculateMappedPower()
    }
    
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Civet Sensor",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        // Connection status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
//                Icon(
//                    painter = painterResource(R.drawable.civet_sensor),
//                    contentDescription = null,
//                    tint = MaterialTheme.colorScheme.primary
//                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (connectionState == CivetSensorConnectionStatus.Connected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        
        // Connection buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    civetViewModel.checkPermissions { granted ->
                        if (granted) {
                            coroutineScope.launch {
                                civetViewModel.attemptConnection()
                            }
                        } else {
                            HLog.d("CivetSensor", "Bluetooth permissions denied.")
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.bluetooth),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(18.dp)
                )
                Spacer(modifier = Modifier.padding(2.dp))
                Text(text = "Scan & Connect", maxLines = 1, softWrap = false)
            }
            
            OutlinedButton(
                onClick = { civetViewModel.disconnect() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.bin),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.height(18.dp)
                )
                Spacer(modifier = Modifier.padding(2.dp))
                Text(text = "Disconnect", maxLines = 1, softWrap = false)
            }
        }
        
        // Pressure display card with reset button
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pressure",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { civetViewModel.resetBaseline() }
                        ) {
                            Text(text = "Zero")
                        }
                        OutlinedButton(
                            onClick = { civetViewModel.flipScreenRotation() }
                        ) {
                            Text(text = "Flip Screen")
                        }
                    }
                }
                
                Text(
                    text = String.format("%.1f kPa", displayPressure),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = buildString {
                        append("Mapped Power: $mappedPower")
                        if (opossumMappingEnabled) {
                            append("  ·  Opossum: $opossumMapped")
                        }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                PressureChart(
                    pressureHistory = pressureHistory,
                    pressureRange = pressureRange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        }
        
        // Test pressure slider for offline testing
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Test Pressure (kPa)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = String.format("%.1f", testPressure),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Slider(
                    value = testPressure,
                    onValueChange = { civetViewModel.setTestPressure(it.coerceAtMost(99.9f)) },
                    onValueChangeFinished = { },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Pressure range slider
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Pressure Range (kPa)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%.1f", pressureRange.start),
                        modifier = Modifier.widthIn(55.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                    RangeSlider(
                        modifier = Modifier.weight(1f),
                        value = pressureRange,
                        valueRange = 0f..99.9f,
                        onValueChange = { newRange ->
                            val start = (newRange.start * 10).roundToInt() / 10f
                            val end = (newRange.endInclusive * 10).roundToInt() / 10f
                            val minSeparation = 0.1f
                            if (end - start >= minSeparation) {
                                civetViewModel.setPressureRange(start..end)
                            }
                        },
                        onValueChangeFinished = { }
                    )
                    Text(
                        text = String.format("%.1f", pressureRange.endInclusive),
                        modifier = Modifier.widthIn(55.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        
        // Power intensity range slider
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Power Intensity Range",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (powerMappingEnabled) "ON" else "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (powerMappingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = powerMappingEnabled,
                            onCheckedChange = { civetViewModel.setPowerMappingEnabled(it) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%.0f", powerRange.start),
                        modifier = Modifier.widthIn(45.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                    RangeSlider(
                        modifier = Modifier.weight(1f),
                        value = powerRange,
                        steps = 198,
                        valueRange = 0f..200f,
                        onValueChange = { newRange ->
                            val minSeparation = 1f
                            if (newRange.endInclusive - newRange.start >= minSeparation) {
                                civetViewModel.setPowerRange(newRange)
                            }
                        },
                        onValueChangeFinished = { }
                    )
                    Text(
                        text = String.format("%.0f", powerRange.endInclusive),
                        modifier = Modifier.widthIn(45.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        
        // Opossum intensity range slider
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Opossum Intensity Range",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (opossumMappingEnabled) "ON" else "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (opossumMappingEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = opossumMappingEnabled,
                            onCheckedChange = { civetViewModel.setOpossumMappingEnabled(it) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%.0f", opossumIntensityRange.start),
                        modifier = Modifier.widthIn(45.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                    RangeSlider(
                        modifier = Modifier.weight(1f),
                        value = opossumIntensityRange,
                        steps = 198,
                        valueRange = 0f..200f,
                        onValueChange = { newRange ->
                            val minSeparation = 1f
                            if (newRange.endInclusive - newRange.start >= minSeparation) {
                                civetViewModel.setOpossumIntensityRange(newRange)
                            }
                        },
                        onValueChangeFinished = { }
                    )
                    Text(
                        text = String.format("%.0f", opossumIntensityRange.endInclusive),
                        modifier = Modifier.widthIn(45.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Denials and cooldown
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Denials",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (denialsCount > 0 && cooldownRemaining > 0) {
                            Text(
                                text = "${cooldownRemaining}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = if (denialsCount > 0) {
                                if (denials == 0) "∞ ($denialsCount)" else "$denialsCount / $denials"
                            } else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "0",
                        modifier = Modifier.widthIn(45.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        modifier = Modifier.weight(1f),
                        value = denials.toFloat(),
                        onValueChange = { civetViewModel.setDenials(it.toInt()) },
                        onValueChangeFinished = { },
                        valueRange = 0f..999f,
                        steps = 998
                    )
                    Text(
                        text = "999",
                        modifier = Modifier.widthIn(45.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Denial to zero toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Denial → Zero",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = denialToZero,
                        onCheckedChange = { civetViewModel.setDenialToZero(it) }
                    )
                }

                // Cooldown time slider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Min Cooldown (s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.0f", cooldownTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "0",
                        modifier = Modifier.widthIn(45.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        modifier = Modifier.weight(1f),
                        value = cooldownTime,
                        onValueChange = { civetViewModel.setCooldownTime(it) },
                        onValueChangeFinished = { },
                        valueRange = 0f..60f,
                        steps = 59
                    )
                    Text(
                        text = "60",
                        modifier = Modifier.widthIn(45.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        
        // Smoothing time slider
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Smoothing Time (s)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%.1f s", smoothingTime),
                        modifier = Modifier.widthIn(45.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        modifier = Modifier.weight(1f),
                        value = (smoothingTime * 10f).roundToInt().toFloat(),
                        onValueChange = { civetViewModel.setSmoothingTime(it / 10f) },
                        onValueChangeFinished = { },
                        valueRange = 0f..600f,
                        steps = 599
                    )
                }
            }
        }
    }
}

@Composable
private fun PressureChart(
    pressureHistory: List<Float>,
    pressureRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val lineColor = Color(0xFF4CAF50)
    val minLineColor = Color(0xFF2196F3)
    val maxLineColor = Color(0xFFE53935)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val bgColor = MaterialTheme.colorScheme.surface

    val rangeMin = pressureRange.start
    val rangeMax = pressureRange.endInclusive

    // Y 轴范围根据压力范围动态调整
    val minPressure = (rangeMin - 2f).coerceAtLeast(0f)
    val maxPressure = (rangeMax + 2f).coerceAtMost(99.9f)

    Canvas(modifier = modifier.background(bgColor)) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val leftPadding = 30f
        val rightPadding = 10f
        val topPadding = 10f
        val bottomPadding = 10f

        val chartWidth = canvasWidth - leftPadding - rightPadding
        val chartHeight = canvasHeight - topPadding - bottomPadding

        fun pressureToY(pressure: Float): Float {
            val ratio = (pressure - minPressure) / (maxPressure - minPressure)
            return topPadding + chartHeight * (1f - ratio.coerceIn(0f, 1f))
        }

        // Draw background grid lines (5 horizontal lines)
        for (i in 0..4) {
            val y = topPadding + chartHeight * i / 4f
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, y),
                end = Offset(canvasWidth - rightPadding, y),
                strokeWidth = 1f
            )
        }

        // Draw range minimum line (blue dashed)
        val rangeMinY = pressureToY(rangeMin)
        drawLine(
            color = minLineColor,
            start = Offset(leftPadding, rangeMinY),
            end = Offset(canvasWidth - rightPadding, rangeMinY),
            strokeWidth = 2.5f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
        )
        // Range min label
        drawContext.canvas.nativeCanvas.drawText(
            String.format("%.1f", rangeMin),
            2f,
            rangeMinY + 4f,
            android.graphics.Paint().apply {
                color = 0xFF2196F3.toInt()
                textSize = 28f
                isAntiAlias = true
            }
        )

        // Draw range maximum line (red dashed)
        val rangeMaxY = pressureToY(rangeMax)
        drawLine(
            color = maxLineColor,
            start = Offset(leftPadding, rangeMaxY),
            end = Offset(canvasWidth - rightPadding, rangeMaxY),
            strokeWidth = 2.5f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
        )
        // Range max label
        drawContext.canvas.nativeCanvas.drawText(
            String.format("%.1f", rangeMax),
            2f,
            rangeMaxY + 4f,
            android.graphics.Paint().apply {
                color = 0xFFE53935.toInt()
                textSize = 28f
                isAntiAlias = true
            }
        )

        // Draw pressure waveform
        if (pressureHistory.size >= 2) {
            val stepX = chartWidth / (pressureHistory.size - 1).coerceAtLeast(1)
            val path = Path()

            for (i in pressureHistory.indices) {
                val x = leftPadding + i * stepX
                val y = pressureToY(pressureHistory[i])
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.5f)
            )

            // Draw current value dot
            val lastX = leftPadding + (pressureHistory.size - 1) * stepX
            val lastY = pressureToY(pressureHistory.last())
            drawCircle(
                color = lineColor,
                radius = 4f,
                center = Offset(lastX, lastY)
            )
        }

    }
}
