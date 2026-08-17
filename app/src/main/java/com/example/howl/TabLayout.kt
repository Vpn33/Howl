package com.example.howl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.howl.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class TabLayoutViewModel : ViewModel() {
    private val fixedTabs = listOf("Player", "Generator", "Activity", "Manual", "Settings")
    private val debugTab = "Debug"

    private val _tabIndex = MutableStateFlow(0)
    val tabIndex: StateFlow<Int> = _tabIndex.asStateFlow()

    val visibleTabs: StateFlow<List<String>> = Prefs.miscShowDebugLog.flow
        .map { showDebugLog ->
            if (showDebugLog) {
                fixedTabs + debugTab
            } else {
                fixedTabs
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, fixedTabs)

    fun setTabIndex(index: Int) {
        _tabIndex.update { index }
    }
}

@Composable
fun TabLayout(
    tabLayoutViewModel: TabLayoutViewModel,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel,
    generatorViewModel: GeneratorViewModel,
    activityHostViewModel: ActivityHostViewModel,
    manualViewModel: ManualViewModel,
    onRequestPermissions: (Array<String>, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabIndex by tabLayoutViewModel.tabIndex.collectAsState()
    val visibleTabs by tabLayoutViewModel.visibleTabs.collectAsState()

    // Reset tab index if current index is invalid
    LaunchedEffect(visibleTabs) {
        if (tabIndex >= visibleTabs.size) {
            tabLayoutViewModel.setTabIndex(0)
        }
    }

    // Use a safe selected index so the tab row never receives an out-of-range index.
    val selectedTabIndex = if (visibleTabs.isEmpty()) {
        0
    } else {
        tabIndex.coerceIn(0, visibleTabs.lastIndex)
    }

    val currentTab = visibleTabs.getOrNull(selectedTabIndex)
    val contentScrollState = rememberScrollState()

    // Reset scroll position when switching tabs.
    LaunchedEffect(currentTab) {
        contentScrollState.scrollTo(0)
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 0.dp,
            divider = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            visibleTabs.forEachIndexed { index, title ->
                Tab(
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    selected = selectedTabIndex == index,
                    onClick = { tabLayoutViewModel.setTabIndex(index) },
                    modifier = Modifier.weight(1f, fill = false),
                    /*icon = {
                        when (title) {
                            "Player" -> Icon(painterResource(R.drawable.player), contentDescription = null)
                            "Generator" -> Icon(painterResource(R.drawable.wave), contentDescription = null)
                            "Activity" -> Icon(painterResource(R.drawable.rocket), contentDescription = null)
                            "Manual" -> Icon(painterResource(R.drawable.joystick), contentDescription = null)
                            "Settings" -> Icon(painterResource(R.drawable.settings), contentDescription = null)
                            "Debug" -> Icon(painterResource(R.drawable.debug), contentDescription = null)
                        }
                    }*/
                )
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )

        // Only this area scrolls.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(contentScrollState)
        ) {
            currentTab?.let { tab ->
                when (tab) {
                    "Player" -> CombinedPanel(viewModel = playerViewModel)
                    "Generator" -> GeneratorPanel(viewModel = generatorViewModel)
                    "Activity" -> ActivityHostPanel(viewModel = activityHostViewModel)
                    "Manual" -> ManualPanel(viewModel = manualViewModel)
                    "Settings" -> SettingsPanel(
                        viewModel = settingsViewModel,
                        onRequestPermissions = onRequestPermissions
                    )
                    "Debug" -> LogViewer()
                }
            }
        }
    }
}

@Preview
@Composable
fun TabLayoutPreview() {
    AppTheme {
        val viewModel: TabLayoutViewModel = viewModel()
        val playerViewModel: PlayerViewModel = viewModel()
        val settingsViewModel: SettingsViewModel = viewModel()
        val generatorViewModel: GeneratorViewModel = viewModel()
        val activityHostViewModel: ActivityHostViewModel = viewModel()
        val manualViewModel: ManualViewModel = viewModel()
        TabLayout (
            tabLayoutViewModel = viewModel,
            playerViewModel = playerViewModel,
            settingsViewModel = settingsViewModel,
            generatorViewModel = generatorViewModel,
            activityHostViewModel = activityHostViewModel,
            manualViewModel = manualViewModel,
            onRequestPermissions = { _, _ -> },
            modifier = Modifier.fillMaxHeight()
        )
    }
}