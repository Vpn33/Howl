package com.example.howl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.howl.Playhead.Mode
import com.example.howl.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale
import java.lang.ref.WeakReference
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun formatTime(position: Double): String {
    val minutes = (position / 60).toInt()
    val seconds = position % 60
    return String.format(Locale.US, "%02d:%04.1f", minutes, seconds)
}

data class PlayerState(
    val activePulseSource: PulseSource? = null,
    val isPlaying: Boolean = false,
    val syncFineTune: Float = 0.0f,
)

data class RecordState(
    val duration: Float = 0.0f,
    val recordMode: Boolean = false,
    val recording: Boolean = false,
)

interface PulseSource {
    val displayName: StateFlow<String>
    val displayInfo: StateFlow<String>
    val duration: Double?
    val seekable: Boolean
    val shouldLoop: Boolean
    val readyToPlay: Boolean
    val latencyCompensation: Boolean
    fun getPulse(time: Double, deltaTime: Double): Pulse
}

object Player {
    private var contextRef: WeakReference<Context>? = null
    var recorder: RecorderOutput = RecorderOutput()
    val playhead = Playhead()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _recordState = MutableStateFlow(RecordState())
    val recordState: StateFlow<RecordState> = _recordState.asStateFlow()

    // split out of playerState because it changes very frequently and most observers don't
    // actually need it (helps reduce UI recompositions)
    private val _playerPosition = MutableStateFlow(0.0)
    val playerPosition: StateFlow<Double> = _playerPosition.asStateFlow()

    fun initialise(context: Context) {
        contextRef = WeakReference(context)
    }
    fun setRecordState(newRecordState: RecordState) {
        _recordState.update { newRecordState }
    }
    fun setPlayerPosition(position: Double) {
        _playerPosition.update { position }
    }
    fun setSyncFineTune(offset: Float) {
        _playerState.update { it.copy(syncFineTune = offset) }
    }

    fun getTimeAdjustment(): Double {
        if (recordState.value.recordMode)
            return 0.0

        val syncFineTune = playerState.value.syncFineTune
        val isRemote = playerState.value.activePulseSource?.latencyCompensation ?: false
        val latency = if (isRemote) Prefs.playerRemoteLatency.value.toDouble() else 0.0

        return syncFineTune + latency
    }

    fun applyPostProcessing(pulse: Pulse): Pulse {
        val mainOptionsState = MainOptions.state.value
        val swapChannels = mainOptionsState.swapChannels

        return if (swapChannels) {
            pulse.copy(
                ampA = pulse.ampB,
                ampB = pulse.ampA,
                freqA = pulse.freqB,
                freqB = pulse.freqA
            )
        } else {
            pulse
        }
    }

    fun getPulse(time: Double, deltaTime: Double): Pulse {
        val activePulseSource = playerState.value.activePulseSource
        return activePulseSource?.getPulse(time, deltaTime) ?: Pulse()
    }

    fun stopPlayer() {
        _playerState.update { it.copy(isPlaying = false) }
        playhead.stop()
        OutputManager.stop()
    }

    fun startPlayer(from: Double? = null) {
        val playerState = playerState.value
        val playFrom = from ?: playhead.position
        if (playerState.activePulseSource?.readyToPlay != true) return

        _playerState.update { it.copy(isPlaying = true) }
        playhead.setSpeed(Prefs.playerPlaybackSpeed.value.toDouble())
        playhead.start(playFrom)
        OutputManager.start()

        val context = contextRef?.get() ?: return
        context.startService(Intent(context, PlayerService::class.java))
    }

    fun loadFile(uri: Uri, context: Context) {
        val fileName = uri.getName(context)
        val extension = fileName.substringAfterLast('.', "").lowercase()

        val source: PulseSource? = try {
            when (extension) {
                "hwl"       -> HWLPulseSource().also { it.open(uri, context) }
                "funscript" -> FunscriptPulseSource().also { it.open(uri, context) }
                else -> {
                    HLog.e("Player", "Unsupported file type: \"$fileName\" (expected .hwl or .funscript)")
                    null
                }
            }
        } catch (e: SecurityException) {
            HLog.e("Player", "Permission denied opening \"$fileName\"", e)
            null
        } catch (e: BadFileException) {
            HLog.e("Player", "Invalid file \"$fileName\": ${e.message}")
            null
        } catch (e: IOException) {
            HLog.e("Player", "I/O error reading \"$fileName\": ${e.message}", e)
            null
        } catch (e: OutOfMemoryError) {
            HLog.e("Player", "Out of memory loading \"$fileName\" — file may be too large")
            null
        } catch (e: Exception) {
            HLog.e("Player", "Unexpected error loading \"$fileName\": ${e.message}", e)
            null
        }

        if (source != null) {
            HLog.i("Player", "Loaded local file \"$fileName\" (${extension.uppercase()}, ${source.duration?.let { "%.1fs".format(it) } ?: "∞"})")
        }

        switchPulseSource(source)
    }

    fun switchPulseSource(source: PulseSource?) {
        val playerState = playerState.value
        if (source == null || playerState.activePulseSource != source) {
            _playerState.update { it.copy(
                activePulseSource = source,
                isPlaying = false,
                syncFineTune = 0.0f
            ) }
            playhead.reset()
            setPlayerPosition(0.0)
        }
    }

    fun getCurrentPosition(): Double = playhead.position

    fun seek(position: Double? = null) {
        // May also be called with null position to resync the player, for example when changing
        // playback speed
        val seekable = playerState.value.activePulseSource?.seekable ?: false
        if (!seekable && position != null) {
            Log.d("Player", "Ignored seek command (unseekable source).")
            return
        }
        playhead.setSpeed(Prefs.playerPlaybackSpeed.value.toDouble())
        val pos = position ?: playhead.position
        playhead.seek(pos)
        setPlayerPosition(pos)
        //(output as? BaseOutput)?.clearPending()
    }
}

class PlayerViewModel : ViewModel() {
    val playerState: StateFlow<PlayerState> = Player.playerState
    val recordState: StateFlow<RecordState> = Player.recordState

    fun updateRecordState(newRecordState: RecordState) {
        Player.setRecordState(newRecordState)
    }

    fun stopPlayer() {
        Player.stopPlayer()
    }

    fun startPlayer(from: Double? = null) {
        Player.startPlayer(from)
    }

    fun seek(position: Double? = null) {
        Player.seek(position)
    }

    fun loadFile(uri: Uri, context: Context) {
        Player.loadFile(uri, context)
    }

    fun clearRecording() {
        Player.recorder.clear()
    }

    fun resizeRecordingBuffer(duration: Int, clear: Boolean = false) {
        Player.recorder.resize(duration, clear)
    }

    fun setRecordingMode(enable: Boolean) {
        val recordBufferLengthPassive = 120
        val recordBufferLengthActive = 7200

        if(enable) {
            resizeRecordingBuffer(recordBufferLengthActive, true)
            Player.playhead.setMode(Mode.PULSE_ACCURATE)
        }
        else {
            resizeRecordingBuffer(recordBufferLengthPassive, true)
            Player.playhead.setMode(Mode.TIME_ACCURATE)
            seek(null)
        }

        updateRecordState(recordState.value.copy(recordMode = enable, recording = false))
    }

    fun setRecording(enable: Boolean) {
        updateRecordState(recordState.value.copy(recording = enable))
    }

    fun saveRecording(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pulses = Player.recorder.getPulses()

                if (pulses.isEmpty()) {
                    Log.e("Player", "No pulses to save")
                    return@launch
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    writeHWLFile(outputStream, pulses)
                    Log.i("Player", "Recording saved: ${pulses.size} pulses")
                } ?: Log.e("Player", "Failed to open output stream")

            } catch (e: Exception) {
                Log.e("Player", "Failed to save recording", e)
            }
        }
    }
}

@Composable
fun AdvancedControlsPanel(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val playerShowSyncFineTune by Prefs.playerShowSyncFineTune.collectAsStateWithLifecycle()
    val playerPlaybackSpeed by Prefs.playerPlaybackSpeed.collectAsStateWithLifecycle()
    val playerRemoteLatency by Prefs.playerRemoteLatency.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = "Player settings", style = MaterialTheme.typography.headlineSmall)
        }
        SliderWithLabel(
            label = "Playback speed",
            value = playerPlaybackSpeed,
            onValueChange = {
                Prefs.playerPlaybackSpeed.value = it
                viewModel.seek()
            },
            onValueChangeFinished = { Prefs.playerPlaybackSpeed.save() },
            valueRange = 0.25f..4.0f,
            steps = 14,
            valueDisplay = { String.format(Locale.US, "%03.2f", it) }
        )
        SliderWithLabel(
            label = "Remote latency (seconds)",
            value = playerRemoteLatency,
            onValueChange = { Prefs.playerRemoteLatency.value = it },
            onValueChangeFinished = { Prefs.playerRemoteLatency.save() },
            valueRange = 0.0f..1.0f,
            steps = 99,
            valueDisplay = { String.format(Locale.US, "%03.2f", it) }
        )
        SwitchWithLabel(
            label = "Show sync fine tune",
            checked = playerShowSyncFineTune,
            onCheckedChange = {
                Prefs.playerShowSyncFineTune.value = it
                Prefs.playerShowSyncFineTune.save()
            }
        )
    }
}

@Composable
fun PlayerPositionDisplay(
    duration: Double,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPosition by Player.playerPosition.collectAsStateWithLifecycle()

    // Temporary variables that we use to ensure the player position only gets
    // updated once the user finishes dragging the drag handle on the seek bar.
    // This prevents sending garbled output when the user drags during playback.
    var isDragging by remember { mutableStateOf(false) }
    var tempPosition by remember { mutableDoubleStateOf(currentPosition) }
    val pos = if (isDragging) tempPosition else currentPosition

    Row(
        modifier = modifier
            .fillMaxWidth(),
            //.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Seek Bar
        Slider(
            modifier = Modifier.weight(1f),
            value = pos.toFloat(),
            onValueChange = {
                tempPosition = it.toDouble()
                if (!isDragging) isDragging = true
            },
            valueRange = 0f..duration.toFloat(),
            onValueChangeFinished = {
                isDragging = false
                onSeek(tempPosition)
            }
        )
        // Position Display
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = formatTime(pos),
            maxLines = 1,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
fun RecordPanel(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val recordState by viewModel.recordState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val duration = recordState.duration
    val recordMode = recordState.recordMode
    val recording = recordState.recording

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri: Uri? ->
            uri?.let { viewModel.saveRecording(it, context) }
        }
    )

    val backgroundColor =
        if (recordMode) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 2.dp
    ) {
        Column(
            //modifier = Modifier.padding(12.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Section label and switch row
            Row (
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recorder",
                    style = MaterialTheme.typography.titleLarge,
                )
                // Record mode toggle
                Switch(
                    checked = recordMode,
                    onCheckedChange = { enable ->
                        viewModel.setRecordingMode(enable)
                    }
                )
            }
            // Controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start/stop recording button
                if (recordMode) {
                    Button(
                        onClick = {
                            viewModel.setRecording(!recording)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (recording)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.record),
                            contentDescription = "Toggle recording"
                        )
                    }
                }
                // Save button
                Button(
                    enabled = duration > 0,
                    onClick = {
                        Player.stopPlayer()
                        val formatter = DateTimeFormatter.ofPattern(
                            "yyyy-MM-dd--HH-mm-ss",
                            Locale.US
                        )
                        val now = LocalDateTime.now()
                        val defaultFilename = "${now.format(formatter)}.hwl"
                        saveLauncher.launch(defaultFilename)
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.save),
                        contentDescription = "Save recording"
                    )
                }
                if (recordMode) {
                    // Clear button
                    Button(
                        onClick = {
                            viewModel.clearRecording()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.bin),
                            contentDescription = "Clear recording"
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatTime(duration.toDouble()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}

@Composable
fun PlayerPanel(
    viewModel: PlayerViewModel,
    onAdvancedSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val activeSource = playerState.activePulseSource
    val playerShowSyncFineTune by Prefs.playerShowSyncFineTune.collectAsStateWithLifecycle()
    val showFunscriptMeters by Prefs.miscShowFunscriptMeters.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val displayName by (activeSource?.displayName ?: remember { MutableStateFlow("Player") })
        .collectAsStateWithLifecycle()
    val displayInfo by (activeSource?.displayInfo ?: remember { MutableStateFlow("") })
        .collectAsStateWithLifecycle()

    val activeButtonColour = MaterialTheme.colorScheme.tertiary
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                viewModel.loadFile(uri, context)
            }
        }
    )

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // File Name Display
            Text(
                text = displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 10.dp)
            )

            if (displayInfo.isNotEmpty()) {
                Text(
                    text = displayInfo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    //modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Position display and seek bar
            PlayerPositionDisplay(
                duration = playerState.activePulseSource?.duration ?: 0.0,
                onSeek = { viewModel.seek(it) }
            )

            // Control buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Play/Pause Button
                Button(
                    onClick = {
                        if (playerState.isPlaying)
                            viewModel.stopPlayer()
                        else
                            viewModel.startPlayer()
                    }
                ) {
                    if (playerState.isPlaying) {
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

                // File Picker Button
                Button(
                    onClick = {
                        viewModel.stopPlayer()
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.folder_open),
                        contentDescription = "Open file"
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Advanced options button
                Button(
                    onClick = onAdvancedSettingsClick
                ) {
                    Icon(
                        painter = painterResource(R.drawable.settings),
                        contentDescription = "Advanced settings"
                    )
                }
            }
            if (showFunscriptMeters && activeSource is FunscriptPulseSource) {
                FunscriptMeters(
                    funscriptSource = activeSource,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            // Sync fine tune (if enabled)
            if (playerShowSyncFineTune) {
                SliderWithLabel(
                    label = "Sync fine tune (seconds)",
                    value = playerState.syncFineTune,
                    onValueChange = { Player.setSyncFineTune(it) },
                    onValueChangeFinished = { },
                    valueRange = -0.5f..0.5f,
                    steps = 99,
                    valueDisplay = { String.format(Locale.US, "%03.2f", it) }
                )
            }
        }
    }
}

@Composable
fun CombinedPanel(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    var showAdvancedSettings by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main player panel
        PlayerPanel(
            viewModel = viewModel,
            onAdvancedSettingsClick = { showAdvancedSettings = true },
            modifier = Modifier.fillMaxWidth()
        )

        // Record panel
        RecordPanel(viewModel = viewModel)
    }

    // Dialogs
    if (showAdvancedSettings) {
        Dialog(
            onDismissRequest = { showAdvancedSettings = false }
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            ) {
                AdvancedControlsPanel(
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Preview
@Composable
fun PlayerPreview() {
    AppTheme {
        val viewModel: PlayerViewModel = viewModel()
        CombinedPanel(
            viewModel = viewModel,
            modifier = Modifier.fillMaxHeight()
        )
    }
}