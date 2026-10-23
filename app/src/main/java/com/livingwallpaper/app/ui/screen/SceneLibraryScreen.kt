package com.livingwallpaper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.livingwallpaper.app.R
import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.core.model.SceneType
import kotlinx.coroutines.launch

@Composable
fun SceneLibraryScreen(
    config: SceneConfig,
    snackbarHostState: SnackbarHostState,
    onSelectScene: (SceneType) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SceneType.entries.forEach { type ->
            SceneCard(
                type = type,
                selected = type == config.sceneType,
                onClick = {
                    onSelectScene(type)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(
                                R.string.scene_applied,
                                context.getString(type.displayNameRes)
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun SceneCard(type: SceneType, selected: Boolean, onClick: () -> Unit) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(id = type.displayNameRes),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                text = stringResource(id = type.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
