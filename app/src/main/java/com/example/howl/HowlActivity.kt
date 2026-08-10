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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.howl.ui.theme.HowlTheme

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

        // 应用语言设置
        val language = Prefs.language.value
        val locale = java.util.Locale(language)
        java.util.Locale.setDefault(locale)
        val resources = resources
        val configuration = resources.configuration
        configuration.setLocale(locale)

//        // 对于Android 7.0及以上版本，使用createConfigurationContext
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            createConfigurationContext(configuration)
//        } else {
            // 对于旧版本，使用updateConfiguration
            resources.updateConfiguration(configuration, resources.displayMetrics)
//        }

        setContent {
            HowlTheme {
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
    settingsViewModel: SettingsViewModel = viewModel(),
    waveViewModel: WaveViewModel = viewModel(),
    powerRampViewModel: PowerRampViewModel = viewModel(),
    civetSensorViewModel: CivetSensorViewModel = viewModel(),
    opossumViewModel: OpossumViewModel = viewModel(),
) {
    // Wire cross-viewModel references
    civetSensorViewModel.setOpossumViewModel(opossumViewModel)

    val connectionStatus by ConnectionManager.connectionStatus.collectAsStateWithLifecycle()
    val batteryPercent by ConnectionManager.batteryLevel.collectAsStateWithLifecycle()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()

    // Permission launcher
    val context = LocalContext.current

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

    fun onConnectClick() {
        checkAndRequestPermissions(BluetoothHandler.ALL_BLE_PERMISSIONS) { isGranted ->
            if (isGranted) {
                HLog.d("Howl", "Bluetooth permissions granted.")
                BluetoothHandler.attemptConnection()
            } else {
                HLog.d("Howl", "Bluetooth permissions denied.")
            }
        }
    }

    // Keep the screen on whenever the player is playing
    val view = LocalView.current
    LaunchedEffect(playerState.isPlaying) {
        view.keepScreenOn = playerState.isPlaying
    }

    // Provide PowerRampViewModel to MainOptions for runtime power ramp processing
    LaunchedEffect(powerRampViewModel) {
        MainOptions.powerRampViewModel = powerRampViewModel
    }

    Scaffold(
        bottomBar = {
            ConnectionStatusBar(
                connectionStatus,
                batteryPercent,
                { onConnectClick() },
                disconnectFunction = { BluetoothHandler.disconnect() },
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MainOptionsPanel(viewModel = mainOptionsViewModel)
            TabLayout(
                tabLayoutViewModel = tabLayoutViewModel,
                playerViewModel = playerViewModel,
                settingsViewModel = settingsViewModel,
                generatorViewModel = generatorViewModel,
                activityHostViewModel = activityHostViewModel,
                manualViewModel = manualViewModel,
                waveViewModel = waveViewModel,
                powerRampViewModel = powerRampViewModel,
                civetSensorViewModel = civetSensorViewModel,
                opossumViewModel = opossumViewModel,
                onRequestPermissions = { permissions, onResult ->
                    checkAndRequestPermissions(permissions, onResult)
                },
                modifier = Modifier
            )
        }
    }
}
