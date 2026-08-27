package com.example.howl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable
fun OpossumPanel(
    opossumViewModel: OpossumViewModel,
    modifier: Modifier = Modifier
) {
    val channelAAmp by opossumViewModel.channelAAmp.collectAsStateWithLifecycle()
    val channelBAmp by opossumViewModel.channelBAmp.collectAsStateWithLifecycle()
    val playerSyncEnabled by opossumViewModel.playerSyncEnabled.collectAsStateWithLifecycle()
    val syncChannels by opossumViewModel.syncChannels.collectAsStateWithLifecycle()
    val swapChannels by opossumViewModel.swapChannels.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        opossumViewModel.findAndSetOutput()
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
            text = "Opossum",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

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
                        onValueChangeFinished = { opossumViewModel.onChannelAChanged() },
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
                        onValueChangeFinished = { opossumViewModel.onChannelBChanged() },
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
