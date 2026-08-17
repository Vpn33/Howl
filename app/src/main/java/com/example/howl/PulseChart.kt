package com.example.howl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class PulseChartMode(val displayName: String) {
    Off("Off"),
    Combined("Combined"),
    PerChannel("Per Channel"),
    Quad("Quad");

    fun next(): PulseChartMode {
        val e = entries
        return e[(ordinal + 1) % e.size]
    }
}

enum class PulseChartStyle(val displayName: String) {
    Point("Point"),
    Line("Line");
}

// Number of pulses to show on the smaller dual/quad charts
private const val PER_CHANNEL_WINDOW = 150
// Number of pulses to show on the combined chart (roughly 2x wider)
private const val COMBINED_WINDOW = 300

// Colours
private const val CH_A_LOW  = 0xFFFF2200.toInt()
private const val CH_A_HIGH = 0xFFFFDD00.toInt()
private const val CH_B_LOW  = 0xFF0055FF.toInt()
private const val CH_B_HIGH = 0xFF00FFAA.toInt()
private val GRID_FRACTIONS = floatArrayOf(0.25f, 0.5f, 0.75f)

private fun lerpArgb(start: Int, end: Int, t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    val a = ((start ushr 24) + ((end ushr 24) - (start ushr 24)) * f).toInt()
    val r = ((start shr 16 and 0xFF) + ((end shr 16 and 0xFF) - (start shr 16 and 0xFF)) * f).toInt()
    val g = ((start shr 8  and 0xFF) + ((end shr 8  and 0xFF) - (start shr 8  and 0xFF)) * f).toInt()
    val b = ((start        and 0xFF) + ((end        and 0xFF) - (start        and 0xFF)) * f).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Functional interface to map two consecutive pulses into drawing operations.
 */
private typealias PulseDrawer = (
    p1: Pulse,
    p2: Pulse,
    drawSegment: (amp1: Float, freq1: Float, amp2: Float, freq2: Float, cLow: Int, cHigh: Int) -> Unit
) -> Unit

// -- Amplitude charts (Y = amplitude, colour = frequency) -------------------
private val channelAMapper: PulseDrawer = { p1, p2, draw ->
    draw(p1.ampA, p1.freqA, p2.ampA, p2.freqA, CH_A_LOW, CH_A_HIGH)
}

private val channelBMapper: PulseDrawer = { p1, p2, draw ->
    draw(p1.ampB, p1.freqB, p2.ampB, p2.freqB, CH_B_LOW, CH_B_HIGH)
}

private val combinedMapper: PulseDrawer = { p1, p2, draw ->
    draw(p1.ampA, p1.freqA, p2.ampA, p2.freqA, CH_A_LOW, CH_A_HIGH)
    draw(p1.ampB, p1.freqB, p2.ampB, p2.freqB, CH_B_LOW, CH_B_HIGH)
}

// For the frequency charts, we map frequency to the `amp` parameters so it scales on the Y axis
private val channelAFreqMapper: PulseDrawer = { p1, p2, draw ->
    draw(p1.freqA, p1.freqA, p2.freqA, p2.freqA, CH_A_LOW, CH_A_HIGH)
}

private val channelBFreqMapper: PulseDrawer = { p1, p2, draw ->
    draw(p1.freqB, p1.freqB, p2.freqB, p2.freqB, CH_B_LOW, CH_B_HIGH)
}

// ---------------------------------------------------------------------------
// Composables
// ---------------------------------------------------------------------------

@Composable
fun PulseChannelChart(
    modifier: Modifier = Modifier,
    window: Int,
    label: String = "",
    mapper: PulseDrawer,
) {
    val pulseChartStyleState = Prefs.miscPulseChartStyle.collectAsStateWithLifecycle()
    val revision by PulseHistory.revision.collectAsStateWithLifecycle()

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = labelColor)

    Canvas(modifier = modifier) {
        // Touching `revision` here registers a read in the draw scope,
        // guaranteeing a redraw on every buffer mutation.
        @Suppress("UNUSED_EXPRESSION")
        revision
        val isPointStyle = pulseChartStyleState.value == PulseChartStyle.Point

        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        // Static grid (drawn under the trace).
        for (frac in GRID_FRACTIONS) {
            val gy = frac * h
            drawLine(
                color = gridColor,
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f
            )
        }

        // We fetch one extra pulse so we can connect the oldest visible point to the edge
        val pulses = PulseHistory.getRecentPulses(window + 1)
        val lastIdx = pulses.size - 1

        if (lastIdx > 0) {
            val pxPerPulse = w / window
            val lineWidth = 2.2f * density
            val pointRadius = lineWidth * 0.6f

            for (j in 0 until lastIdx) {
                val p1 = pulses[j]
                val p2 = pulses[j + 1]

                // Oldest pulses are on the left, newest on the right.
                val d1 = lastIdx - j
                val d2 = lastIdx - (j + 1)

                val x1 = w - (d1 * pxPerPulse)
                val x2 = w - (d2 * pxPerPulse)

                mapper(p1, p2) { amp1, freq1, amp2, freq2, cLow, cHigh ->
                    if (isPointStyle) {
                        // Draw the first point
                        val y1 = (1f - amp1.coerceIn(0f, 1f)) * h
                        drawCircle(
                            color = Color(lerpArgb(cLow, cHigh, freq1)),
                            radius = pointRadius,
                            center = Offset(x1, y1),
                        )

                        // To avoid skipping the very last point on the right edge,
                        // draw p2 exclusively on the final iteration
                        if (j == lastIdx - 1) {
                            val y2 = (1f - amp2.coerceIn(0f, 1f)) * h
                            drawCircle(
                                color = Color(lerpArgb(cLow, cHigh, freq2)),
                                radius = pointRadius,
                                center = Offset(x2, y2),
                            )
                        }
                    } else {
                        val y1 = (1f - amp1.coerceIn(0f, 1f)) * h
                        val y2 = (1f - amp2.coerceIn(0f, 1f)) * h

                        // Fast integer math to avoid allocating Colour objects inside the loop
                        val argb = lerpArgb(cLow, cHigh, (freq1 + freq2) * 0.5f)

                        drawLine(
                            color = Color(argb),
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = lineWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }

        if (label.isNotEmpty()) {
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                style = labelStyle,
                topLeft = Offset(6.dp.toPx(), h - 18.dp.toPx())
            )
        }
    }
}

@Composable
private fun Modifier.chartBorder(): Modifier = this
    .border(
        width = 2.dp,
        color = MaterialTheme.colorScheme.outline,
        shape = MaterialTheme.shapes.medium,
    )
    .clip(MaterialTheme.shapes.medium) // Clips overflowing content to the rounded corners

@Composable
fun PulseChartPanel(mode: PulseChartMode, modifier: Modifier = Modifier) {
    val chartPadding = 3.dp
    when (mode) {
        PulseChartMode.Off -> Unit

        PulseChartMode.PerChannel -> Row(modifier = modifier.fillMaxWidth()) {
            PulseChannelChart(
                modifier = Modifier.weight(1f).aspectRatio(2f).chartBorder().padding(chartPadding),
                window = PER_CHANNEL_WINDOW, label = "A", mapper = channelAMapper,
            )
            Spacer(Modifier.width(8.dp))
            PulseChannelChart(
                modifier = Modifier.weight(1f).aspectRatio(2f).chartBorder().padding(chartPadding),
                window = PER_CHANNEL_WINDOW, label = "B", mapper = channelBMapper,
            )
        }

        PulseChartMode.Combined -> PulseChannelChart(
            modifier = modifier.fillMaxWidth().aspectRatio(2.5f).chartBorder().padding(chartPadding),
            window = COMBINED_WINDOW, label = "A+B", mapper = combinedMapper,
        )

        PulseChartMode.Quad -> Column(modifier = modifier.fillMaxWidth()) {
            // Top row – amplitude
            Row(modifier = Modifier.fillMaxWidth()) {
                PulseChannelChart(
                    modifier = Modifier.weight(1f).aspectRatio(2f).chartBorder().padding(chartPadding),
                    window = PER_CHANNEL_WINDOW, label = "A (Amp)", mapper = channelAMapper,
                )
                Spacer(Modifier.width(8.dp))
                PulseChannelChart(
                    modifier = Modifier.weight(1f).aspectRatio(2f).chartBorder().padding(chartPadding),
                    window = PER_CHANNEL_WINDOW, label = "B (Amp)", mapper = channelBMapper,
                )
            }
            Spacer(Modifier.height(8.dp))
            // Bottom row – frequency
            Row(modifier = Modifier.fillMaxWidth()) {
                PulseChannelChart(
                    modifier = Modifier.weight(1f).aspectRatio(2f).chartBorder().padding(chartPadding),
                    window = PER_CHANNEL_WINDOW, label = "A (Freq)", mapper = channelAFreqMapper,
                )
                Spacer(Modifier.width(8.dp))
                PulseChannelChart(
                    modifier = Modifier.weight(1f).aspectRatio(2f).chartBorder().padding(chartPadding),
                    window = PER_CHANNEL_WINDOW, label = "B (Freq)", mapper = channelBFreqMapper,
                )
            }
        }
    }
}