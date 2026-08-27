package com.example.howl

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.howl.ui.theme.AppTheme

private data class PermissionRequest(
    val permissions: Array<String>,
    val onResult: (allGranted: Boolean) -> Unit
)

class HowlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Leave empty to globally disable the back button/gesture
            }
        })
        enableEdgeToEdge()
        setContent {
            AppTheme {
                HowlAppScreen()
            }
        }
    }
}

@Composable
fun HowlAppScreen(
    mainOptionsViewModel: MainOptionsViewModel = viewModel(),
    tabLayoutViewModel: TabLayoutViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
    generatorViewModel: GeneratorViewModel = viewModel(),
    activityHostViewModel: ActivityHostViewModel = viewModel(),
    manualViewModel: ManualViewModel = viewModel(),
    powerRampViewModel: PowerRampViewModel = viewModel(),
    waveViewModel: WaveViewModel = viewModel(),
    civetSensorViewModel: CivetSensorViewModel = viewModel(),
    opossumViewModel: OpossumViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    // Wire cross-viewModel references
    civetSensorViewModel.setOpossumViewModel(opossumViewModel)

    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()

    // Permission launcher
    val context = LocalContext.current
    val app = context.applicationContext as HowlApp
    val scope = rememberCoroutineScope()

    var pendingPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }

    // Single, all-purpose permission launcher
    val genericPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        // Route the result back to the specific caller that triggered it
        pendingPermissionRequest?.onResult?.invoke(allGranted)
        pendingPermissionRequest = null
    }

    // Generic helper function to request permissions
    fun checkAndRequestPermissions(permissions: Array<String>, onResult: (Boolean) -> Unit) {
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            onResult(true)
        } else {
            HLog.d("Howl", "Requesting permissions: ${permissions.contentToString()}")
            pendingPermissionRequest = PermissionRequest(permissions, onResult)
            genericPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    // Keep the screen on whenever the player is playing
    val view = LocalView.current
    LaunchedEffect(playerState.isPlaying) {
        view.keepScreenOn = playerState.isPlaying
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Provide PowerRampViewModel to MainOptions for runtime power ramp processing
    LaunchedEffect(powerRampViewModel) {
        MainOptions.powerRampViewModel = powerRampViewModel
    }

    Scaffold { innerPadding ->
        val containerModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        if (isLandscape) {
            Row(modifier = containerModifier) {
                Column(
                    modifier = Modifier
                        .width(420.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    MainOptionsPanel(
                        viewModel = mainOptionsViewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutputsPanel(
                        onRequestPermissions = { permissions, onResult ->
                            checkAndRequestPermissions(permissions, onResult)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TabLayout(
                    tabLayoutViewModel = tabLayoutViewModel,
                    playerViewModel = playerViewModel,
                    settingsViewModel = settingsViewModel,
                    generatorViewModel = generatorViewModel,
                    activityHostViewModel = activityHostViewModel,
                    manualViewModel = manualViewModel,
                    waveViewModel = waveViewModel,
                    civetSensorViewModel = civetSensorViewModel,
                    opossumViewModel = opossumViewModel,
                    onRequestPermissions = { permissions, onResult ->
                        checkAndRequestPermissions(permissions, onResult)
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        } else {
            Column(modifier = containerModifier) {
                MainOptionsPanel(viewModel = mainOptionsViewModel)
                Spacer(modifier = Modifier.height(8.dp))
                OutputsPanel(
                    onRequestPermissions = { permissions, onResult ->
                        checkAndRequestPermissions(permissions, onResult)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                TabLayout(
                    tabLayoutViewModel = tabLayoutViewModel,
                    playerViewModel = playerViewModel,
                    settingsViewModel = settingsViewModel,
                    generatorViewModel = generatorViewModel,
                    activityHostViewModel = activityHostViewModel,
                    manualViewModel = manualViewModel,
                    waveViewModel = waveViewModel,
                    civetSensorViewModel = civetSensorViewModel,
                    opossumViewModel = opossumViewModel,
                    onRequestPermissions = { permissions, onResult ->
                        checkAndRequestPermissions(permissions, onResult)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}
