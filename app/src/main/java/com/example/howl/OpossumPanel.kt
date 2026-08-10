package com.example.howl

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@SuppressLint("UnrememberedMutableState")
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
@Composable
fun OpossumPanel(
    opossumViewModel: OpossumViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        opossumViewModel.initialize(context)
    }

    val connectionState by opossumViewModel.connectionStatus.collectAsStateWithLifecycle()
    val batteryLevel by opossumViewModel.batteryLevel.collectAsStateWithLifecycle()
    val channelAAmp by opossumViewModel.channelAAmp.collectAsStateWithLifecycle()
    val channelBAmp by opossumViewModel.channelBAmp.collectAsStateWithLifecycle()
    val isPlaying by opossumViewModel.isPlaying.collectAsStateWithLifecycle()
    val selectedActivity by opossumViewModel.selectedActivity.collectAsStateWithLifecycle()
    val activityChangeProbability by opossumViewModel.activityChangeProbability.collectAsStateWithLifecycle()
    val playerSyncEnabled by opossumViewModel.playerSyncEnabled.collectAsStateWithLifecycle()
    val swapChannels by opossumViewModel.swapChannels.collectAsStateWithLifecycle()
    val syncChannels by opossumViewModel.syncChannels.collectAsStateWithLifecycle()

    var statusText by mutableStateOf("Disconnected")
    LaunchedEffect(connectionState, batteryLevel) {
        statusText = opossumViewModel.getStatusText()
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Opossum",
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
                Icon(
                    painter = painterResource(R.drawable.opossum),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (connectionState == OpossumConnectionStatus.Connected) {
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
                    opossumViewModel.checkPermissions { granted ->
                        if (granted) {
                            coroutineScope.launch {
                                opossumViewModel.attemptConnection()
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = connectionState != OpossumConnectionStatus.Connected
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
                onClick = { opossumViewModel.disconnect() },
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

        // Activity selector
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top Row: Activity picker + Play/Pause
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionPicker(
                        currentValue = selectedActivity,
                        onValueChange = { opossumViewModel.setSelectedActivity(it) },
                        options = ActivityType.entries,
                        getText = { stringResource(it.displayNameResId) },
                        getIcon = { it.iconResId },
                        textColor = { Color.Unspecified },
                        size = OptionPickerSize.Large,
                        modifier = Modifier.weight(1f),
                        enabled = !playerSyncEnabled
                    )

                    Button(
                        onClick = {
                            if (isPlaying)
                                opossumViewModel.stopPlayback()
                            else
                                opossumViewModel.startPlayback()
                        },
                        enabled = !playerSyncEnabled
                    ) {
                        if (isPlaying) {
                            Icon(
                                painter = painterResource(R.drawable.pause),
                                contentDescription = "Pause"
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = "Play"
                            )
                        }
                    }
                }

                // Current activity info
                Text(
                    text = stringResource(selectedActivity.displayNameResId),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // Activity change probability slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Random Change",
                        modifier = Modifier.widthIn(80.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        modifier = Modifier.weight(1f),
                        value = activityChangeProbability,
                        onValueChange = { opossumViewModel.setActivityChangeProbability(it) },
                        valueRange = OutputOpossum.PROBABILITY_RANGE,
                        steps = ((OutputOpossum.PROBABILITY_RANGE.endInclusive - OutputOpossum.PROBABILITY_RANGE.start) * 100).toInt() - 1
                    )
                    Text(
                        text = String.format("%.2f", activityChangeProbability),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Player Sync toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (playerSyncEnabled) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Player Sync",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Output Player content to Opossum device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = playerSyncEnabled,
                    onCheckedChange = { opossumViewModel.setPlayerSyncEnabled(it) }
                )
            }
        }

        // Swap Channels toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (swapChannels) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Swap Channels",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (swapChannels) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = swapChannels,
                    onCheckedChange = { opossumViewModel.setSwapChannels(it) }
                )
            }
        }

        // Sync Channels toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (syncChannels) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync Channels",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (syncChannels) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = syncChannels,
                    onCheckedChange = { opossumViewModel.setSyncChannels(it) }
                )
            }
        }

        // Channel A control
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Channel A",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$channelAAmp",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "0",
                        modifier = Modifier.widthIn(30.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        modifier = Modifier.weight(1f),
                        value = channelAAmp.toFloat(),
                        onValueChange = { opossumViewModel.setChannelA(it.roundToInt()) },
                        onValueChangeFinished = { },
                        valueRange = 0f..200f,
                        steps = 0
                    )
                    Text(
                        text = "200",
                        modifier = Modifier.widthIn(30.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Channel B control
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Channel B",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$channelBAmp",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "0",
                        modifier = Modifier.widthIn(30.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        modifier = Modifier.weight(1f),
                        value = channelBAmp.toFloat(),
                        onValueChange = { opossumViewModel.setChannelB(it.roundToInt()) },
                        onValueChangeFinished = { },
                        valueRange = 0f..200f,
                        steps = 0
                    )
                    Text(
                        text = "200",
                        modifier = Modifier.widthIn(30.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
