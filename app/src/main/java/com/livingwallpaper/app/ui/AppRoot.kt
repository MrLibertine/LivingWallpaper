package com.livingwallpaper.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.livingwallpaper.app.R

private enum class AppTab(val labelRes: Int, val icon: ImageVector) {
    PREVIEW(R.string.tab_preview, Icons.Filled.Wallpaper),
    SCENES(R.string.tab_scenes, Icons.Filled.PhotoLibrary),
    PARAMETERS(R.string.tab_parameters, Icons.Filled.Tune),
    AI(R.string.tab_ai, Icons.Filled.AutoAwesome),
    PERFORMANCE(R.string.tab_performance, Icons.Filled.Speed)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivingWallpaperApp(viewModel: SceneViewModel = viewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(AppTab.PREVIEW) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Icon(imageVector = item.icon, contentDescription = null)
                        },
                        label = { Text(text = stringResource(id = item.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (tab) {
                AppTab.PREVIEW -> PreviewScreen(
                    configFlow = viewModel.config,
                    snackbarHostState = snackbarHostState
                )
                AppTab.SCENES -> SceneLibraryScreen(
                    config = config,
                    snackbarHostState = snackbarHostState,
                    onSelectScene = { viewModel.selectScene(it) }
                )
                AppTab.PARAMETERS -> ParameterScreen(
                    config = config,
                    onUpdate = { viewModel.update(it) },
                    onSelectWeather = { viewModel.selectWeather(it) },
                    onSelectSkyColor = { viewModel.selectSkyColor(it) },
                    onSelectTargetFps = { viewModel.selectTargetFps(it) }
                )
                AppTab.AI -> AiScreen(
                    aiState = aiState,
                    providerName = viewModel.aiProviderName,
                    onGenerate = { viewModel.generateFromPrompt(it) },
                    onApply = { viewModel.applyAiConfig(it) },
                    onDismiss = { viewModel.dismissAiState() }
                )
                AppTab.PERFORMANCE -> PerformanceScreen(
                    config = config,
                    onSelectPerformanceLevel = { viewModel.selectPerformanceLevel(it) },
                    onSetReduceMotion = { viewModel.setReduceMotion(it) },
                    onSetParallaxEnabled = { viewModel.setParallaxEnabled(it) },
                    onSelectTargetFps = { viewModel.selectTargetFps(it) }
                )
            }
        }
    }
}
