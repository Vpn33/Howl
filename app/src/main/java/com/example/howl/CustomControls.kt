package com.example.howl

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun SliderWithLabel(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueDisplay: (Float) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column( modifier = modifier) {
        if (label != "") {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),//.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = valueDisplay(value),
                modifier = Modifier.widthIn(45.dp)
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled
            )
        }
    }
}

@Composable
fun SwitchWithLabel(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun NiceSmootherControl(
    smoother: NiceSmoother,
    targetRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    rateRange: ClosedFloatingPointRange<Float> = 0.1f..1.0f,
    targetLabel: String = "Target",
    rateLabel: String = "Rate",
    targetSteps: Int = 0,
    rateSteps: Int = 0,
    targetValueDisplay: (Double) -> String = { "%.2f".format(it) },
    rateValueDisplay: (Double) -> String = { "%.2f".format(it) },
    adjustableRate: Boolean = true,
    enabled: Boolean = true
) {
    val target by smoother.targetFlow.collectAsState()
    val rate by smoother.rateFlow.collectAsState()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = targetLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = targetValueDisplay(target),
                    modifier = Modifier.widthIn(min = 45.dp)
                )
                Slider(
                    value = target.toFloat(),
                    onValueChange = { newTarget ->
                        smoother.setTarget(newTarget.toDouble())
                    },
                    valueRange = targetRange,
                    steps = targetSteps,
                    modifier = Modifier.weight(1f),
                    enabled = enabled
                )
            }
        }

        if (adjustableRate) {
            Column {
                Text(
                    text = rateLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = rateValueDisplay(rate),
                        modifier = Modifier.widthIn(min = 45.dp)
                    )
                    Slider(
                        value = rate.toFloat(),
                        onValueChange = { newRate ->
                            smoother.rate = newRate.toDouble()
                        },
                        valueRange = rateRange,
                        steps = rateSteps,
                        modifier = Modifier.width(80.dp),
                        enabled = enabled
                    )
                }
            }
        }
    }
}

enum class OptionPickerSize {
    Standard,
    Large
}

@Composable
fun <T> OptionPicker(
    currentValue: T,
    onValueChange: (T) -> Unit,
    options: List<T>,
    getText: (T) -> String,
    modifier: Modifier = Modifier,
    getIcon: (T) -> Int? = { null },
    textColor: (T) -> Color = { Color.Unspecified },
    size: OptionPickerSize = OptionPickerSize.Standard,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val sortedOptions = remember(options, getText) {
        options.sortedBy { getText(it) }
    }

    val textStyle = when (size) {
        OptionPickerSize.Standard -> MaterialTheme.typography.labelLarge
        OptionPickerSize.Large -> MaterialTheme.typography.titleMedium // Bigger text
    }

    val iconSize = when (size) {
        OptionPickerSize.Standard -> ButtonDefaults.IconSize
        OptionPickerSize.Large -> 24.dp // Bigger icon
    }

    val contentPadding = when (size) {
        OptionPickerSize.Standard -> ButtonDefaults.ContentPadding
        OptionPickerSize.Large -> PaddingValues(horizontal = 24.dp, vertical = 12.dp) // More padding for larger touch target
    }

    Box(modifier = modifier.wrapContentSize(Alignment.TopStart)) {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = contentPadding,
            enabled = enabled
        ) {
            getIcon(currentValue)?.let { iconRes ->
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            }

            Text(
                text = getText(currentValue),
                color = if (enabled) textColor(currentValue) else Color.Unspecified,
                style = textStyle
            )
        }

        // DropdownMenu remains standard size
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            sortedOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(getText(option), color = textColor(option)) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                    leadingIcon = getIcon(option)?.let { res ->
                        {
                            Icon(
                                painter = painterResource(id = res),
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun LongPressButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                indication = ripple(),
                interactionSource = remember { MutableInteractionSource() }
            ),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = shape,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun DualCircularTouchpadSurface(
    leftPadPosition: CartesianPosition,
    rightPadPosition: CartesianPosition,
    onLeftPadPositionChange: (CartesianPosition) -> Unit,
    onRightPadPositionChange: (CartesianPosition) -> Unit,
    returnRate: Float = 0f // Normalized units per second. 0f disables return animation.
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        CircularTouchpad(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .sizeIn(maxWidth = 300.dp, maxHeight = 300.dp),
            position = leftPadPosition,
            onPositionChange = onLeftPadPositionChange,
            returnRate = returnRate
        )
        CircularTouchpad(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .sizeIn(maxWidth = 300.dp, maxHeight = 300.dp),
            position = rightPadPosition,
            onPositionChange = onRightPadPositionChange,
            returnRate = returnRate
        )
    }
}

@Composable
fun CircularTouchpad(
    position: CartesianPosition,
    onPositionChange: (CartesianPosition) -> Unit,
    modifier: Modifier = Modifier,
    returnRate: Float = 0f
) {
    // Track whether the user's finger is currently pressing the touchpad
    val isTouched = remember { mutableStateOf(false) }

    // Extract MaterialTheme colours here while we are still in a @Composable context
    val touchpadBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val touchpadBorderColor = MaterialTheme.colorScheme.outline
    val indicatorColor = MaterialTheme.colorScheme.primary

    // Define constants for styling
    val borderWidth = 4.dp
    val indicatorRadius = 8.dp

    // Calculate the pixel inset needed to keep the indicator inside the border.
    // Inset = Border Width + Indicator Radius.
    val insetPx = with(LocalDensity.current) { (borderWidth + indicatorRadius).toPx() }

    DisposableEffect(Unit) {
        onDispose {
            onPositionChange(CartesianPosition(0f, 0f))
        }
    }

    // LaunchedEffect handles centering when the finger is lifted
    LaunchedEffect(isTouched.value, returnRate) {
        if (!isTouched.value && returnRate > 0f) {
            val startX = position.x
            val startY = position.y
            val distance = hypot(startX, startY)

            if (distance > 0f) {
                // Calculate total duration in milliseconds based on distance and return rate
                val durationMillis = (distance / returnRate * 1000f)
                var accumulatedTime = 0f

                // Wait for the first frame to establish a baseline time
                var lastFrameTime = withFrameMillis { it }

                // Smoothly interpolate position frame-by-frame
                while (isActive) {
                    val currentFrameTime = withFrameMillis { it }
                    val deltaTime = currentFrameTime - lastFrameTime
                    lastFrameTime = currentFrameTime
                    accumulatedTime += deltaTime

                    val progress = (accumulatedTime / durationMillis).coerceIn(0f, 1f)

                    // Move progressively closer to (0,0)
                    val currentX = startX * (1f - progress)
                    val currentY = startY * (1f - progress)

                    onPositionChange(CartesianPosition(currentX, currentY))

                    // Break the loop once we've fully returned to the centre
                    if (progress >= 1f) {
                        break
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .systemGestureExclusion()
            .clip(CircleShape)
            .background(touchpadBackgroundColor)
            .border(
                width = borderWidth,
                color = touchpadBorderColor,
                shape = CircleShape
            )
            .pointerInput(insetPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()

                    val center = Offset(size.width / 2f, size.height / 2f)

                    // The maximum radius for the touch input (full size)
                    val maxInputRadius = size.width / 2f

                    // The effective radius for clamping and drawing (reduced by inset)
                    // This ensures the dot stops before crossing the border
                    val innerRadius = (maxInputRadius - insetPx).coerceAtLeast(0f)

                    // Helper function to handle coordinates constrained to a circle
                    fun getCartesian(offset: Offset, isDragging: Boolean): CartesianPosition? {
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val distance = hypot(dx, dy)

                        // Allow touch initiation anywhere in the box (including the border area),
                        // but ignore touches strictly outside the circle.
                        if (!isDragging && distance > maxInputRadius) return null

                        // Coerce the position to the inner radius so the dot stays visually inside the border
                        val clampedDistance = distance.coerceAtMost(innerRadius)
                        val scale = if (distance == 0f) 0f else clampedDistance / distance

                        // Normalise against the inner radius so output is -1..1 relative to the drawable area
                        val normalizedX = ((dx * scale) / innerRadius).coerceIn(-1f, 1f)
                        val normalizedY = ((dy * scale) / innerRadius).coerceIn(-1f, 1f)

                        return CartesianPosition(normalizedX, normalizedY)
                    }

                    // Process the initial touch (isDragging = false)
                    val initialCartesian = getCartesian(down.position, isDragging = false)

                    // Only consume and start tracking if the touch was actually inside the circle
                    if (initialCartesian != null) {
                        isTouched.value = true // Halt any ongoing return animation
                        onPositionChange(initialCartesian)
                        down.consume()

                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }

                                if (change != null && change.pressed) {
                                    val dragCartesian = getCartesian(change.position, isDragging = true)
                                    if (dragCartesian != null) {
                                        onPositionChange(dragCartesian)
                                    }
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            // When the finger is lifted (or gesture cancelled), trigger the return animation
                            isTouched.value = false
                        }
                    }
                }
            }
    ) {
        // Re-calculate pixel coordinates to draw the visual indicator
        Canvas(modifier = Modifier.matchParentSize()) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)

            // Use the same inner radius calculation used for input clamping
            val drawRadius = (size.width / 2f - insetPx).coerceAtLeast(0f)

            val indicatorX = centerOffset.x + (position.x * drawRadius)
            val indicatorY = centerOffset.y + (position.y * drawRadius)

            drawCircle(
                color = indicatorColor,
                radius = indicatorRadius.toPx(),
                center = Offset(indicatorX, indicatorY)
            )
        }
    }
}

@Composable
fun ConfirmationDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "Yes",
    dismissText: String = "Cancel"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm", style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

/**
 * Vertical meter for linear axes (L0, L1, L2).
 * Shows a position indicator (horizontal bar) within a vertical track.
 * 0.0 = bottom, 1.0 = top.
 */
@Composable
fun VerticalPositionMeter(
    position: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val animatedPosition by animateFloatAsState(
        targetValue = position.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 25, easing = LinearEasing),
        label = "vertical_meter"
    )

    val borderColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .width(28.dp)
            .border(1.dp, borderColor, MaterialTheme.shapes.extraSmall)
            .padding(1.dp)
            .drawBehind {
                // Track background
                drawRect(color = trackColor)

                // Position indicator: a horizontal bar at the current height
                val indicatorHeight = 4.dp.toPx()
                // 0.0 = bottom, 1.0 = top, so invert Y
                val posY = size.height * (1f - animatedPosition)
                val top = (posY - indicatorHeight / 2f).coerceIn(0f, size.height - indicatorHeight)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, indicatorHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                )
            }
    )
}

/**
 * Circular dial meter for rotation axes (R0, R1, R2).
 * 270° sweep with the central/neutral position (0.5) at the bottom (6 o'clock).
 * The track is drawn as a 270° arc with a gap at the top, so the rotation
 * limits are clearly visible.
 *   position 0.0 → needle at upper-left  (~10:30)
 *   position 0.5 → needle at bottom      ( 6:00)
 *   position 1.0 → needle at upper-right (~ 1:30)
 */
@Composable
fun RotationDialMeter(
    position: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val animatedPosition by animateFloatAsState(
        targetValue = position.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 25, easing = LinearEasing),
        label = "rotation_meter"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .drawBehind {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f
                val strokeWidth = 2.dp.toPx()
                val arcRadius = radius - strokeWidth

                // Angle mapping (canvas degrees: 0°=3 o'clock, clockwise positive)
                // 270° total sweep, neutral (0.5) at bottom (90°)
                val sweepTotal = 270f
                val angleAtZero = -45f       // position 0.0 → upper-right
                val angleAtCenter = 90f      // position 0.5 → bottom
                val currentAngle = angleAtZero + sweepTotal * (1f - animatedPosition)

                // Track: a 270° arc with a gap at the top (between the two limits)
                drawArc(
                    color = trackColor,
                    startAngle = angleAtZero,
                    sweepAngle = sweepTotal,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Arc from neutral (bottom) to current position
                val arcSweep = currentAngle - angleAtCenter
                if (abs(arcSweep) > 1f) {
                    // drawArc needs a positive sweepAngle; pick the correct start
                    val drawStart = if (arcSweep > 0f) angleAtCenter else currentAngle
                    val drawSweep = abs(arcSweep)

                    drawArc(
                        color = color,
                        startAngle = drawStart,
                        sweepAngle = drawSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                        size = Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = strokeWidth * 1.5f, cap = StrokeCap.Round)
                    )
                }

                // Needle from centre to edge
                val needleLength = arcRadius * 0.72f
                val angleRad = Math.toRadians(currentAngle.toDouble())
                val needleEnd = Offset(
                    center.x + (needleLength * cos(angleRad)).toFloat(),
                    center.y + (needleLength * sin(angleRad)).toFloat()
                )
                drawLine(
                    color = color,
                    start = center,
                    end = needleEnd,
                    strokeWidth = strokeWidth * 1.2f,
                    cap = StrokeCap.Round
                )

                // Centre dot
                drawCircle(
                    color = color,
                    radius = strokeWidth * 1.5f,
                    center = center
                )
            }
    )
}

private enum class MeterType {
    LINEAR,     // L0, L1, L2 → vertical position meters
    ROTATION    // R0, R1, R2 → circular dials
}

private fun meterTypeForAxis(axisId: String): MeterType = when (axisId) {
    "L0", "L1", "L2" -> MeterType.LINEAR
    "R0", "R1", "R2" -> MeterType.ROTATION
    else -> MeterType.LINEAR
}

private fun friendlyAxisName(axisId: String): String = when (axisId) {
    "L0" -> "Stroke"
    "L1" -> "Surge"
    "L2" -> "Sway"
    "R0" -> "Twist"
    "R1" -> "Roll"
    "R2" -> "Pitch"
    else -> axisId
}

@Composable
fun FunscriptMeters(
    funscriptSource: FunscriptPulseSource,
    modifier: Modifier = Modifier
) {
    val currentPosition by Player.playerPosition.collectAsStateWithLifecycle()
    val axisIds = remember(funscriptSource) { funscriptSource.axisIds }

    val linearAxes = axisIds.filter { meterTypeForAxis(it) == MeterType.LINEAR }
    val rotationAxes = axisIds.filter { meterTypeForAxis(it) == MeterType.ROTATION }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        //horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // ── Linear axes: vertical position meters ──
        if (linearAxes.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (axisId in linearAxes) {
                    val position = funscriptSource.getAxisPosition(axisId, currentPosition) ?: 0.0
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = friendlyAxisName(axisId),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        VerticalPositionMeter(
                            position = position.toFloat(),
                            modifier = Modifier.height(48.dp)
                        )
                    }
                }
            }
        }

        // ── Rotation axes: circular dials ──
        if (rotationAxes.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (axisId in rotationAxes) {
                    val position = funscriptSource.getAxisPosition(axisId, currentPosition) ?: 0.0
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = friendlyAxisName(axisId),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        RotationDialMeter(
                            position = position.toFloat(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A wrapper around Material 3's [RangeSlider] that enforces a minimum gap
 * between the two handles without pushing the stationary handle around.
 *
 * Behaviour:
 * - Dragging the lower handle towards the upper handle stops it at
 *   `upper - minimumGap`.
 * - Dragging the upper handle towards the lower handle stops it at
 *   `lower + minimumGap`.
 * - The stationary handle remains fixed.
 */
@Composable
fun ConstrainedRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    minimumGap: Float = 0f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors()
) {
    var currentValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        currentValue = value
    }

    RangeSlider(
        modifier = modifier,
        enabled = enabled,
        value = currentValue,
        valueRange = valueRange,
        steps = steps,
        colors = colors,
        onValueChange = { proposed ->
            val constrained = if (minimumGap <= 0f) {
                proposed
            } else {
                constrainRange(
                    proposed = proposed,
                    current = currentValue,
                    minimumGap = minimumGap,
                    bounds = valueRange
                )
            }

            currentValue = constrained
            onValueChange(constrained)
        },
        onValueChangeFinished = {
            onValueChangeFinished?.invoke()
        }
    )
}

private fun constrainRange(
    proposed: ClosedFloatingPointRange<Float>,
    current: ClosedFloatingPointRange<Float>,
    minimumGap: Float,
    bounds: ClosedFloatingPointRange<Float>
): ClosedFloatingPointRange<Float> {
    var start = proposed.start.coerceIn(bounds.start, bounds.endInclusive)
    var end = proposed.endInclusive.coerceIn(bounds.start, bounds.endInclusive)

    val startMoved = start != current.start
    val endMoved = end != current.endInclusive

    when {
        // Only the lower handle moved.
        startMoved && !endMoved -> {
            val maxStart = (end - minimumGap).coerceIn(bounds.start, bounds.endInclusive)
            start = start.coerceIn(bounds.start, maxStart)
        }

        // Only the upper handle moved.
        endMoved && !startMoved -> {
            val minEnd = (start + minimumGap).coerceIn(bounds.start, bounds.endInclusive)
            end = end.coerceIn(minEnd, bounds.endInclusive)
        }

        // Rare fallback if both handles moved. Clamp whichever moved further.
        else -> {
            val startDelta = abs(start - current.start)
            val endDelta = abs(end - current.endInclusive)

            if (startDelta >= endDelta) {
                val maxStart = (end - minimumGap).coerceIn(bounds.start, bounds.endInclusive)
                start = start.coerceIn(bounds.start, maxStart)
            } else {
                val minEnd = (start + minimumGap).coerceIn(bounds.start, bounds.endInclusive)
                end = end.coerceIn(minEnd, bounds.endInclusive)
            }
        }
    }

    // Guard against weird edge cases if we somehow breached our minimum gap
    if (end - start < minimumGap) {
        end = (start + minimumGap).coerceIn(bounds.start, bounds.endInclusive)
        start = (end - minimumGap).coerceIn(bounds.start, bounds.endInclusive)
    }

    return start..end
}