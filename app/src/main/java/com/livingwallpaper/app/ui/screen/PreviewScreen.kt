package com.livingwallpaper.app.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.Surface
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livingwallpaper.app.BuildConfig
import com.livingwallpaper.app.R
import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.renderer.gl.GLRenderer
import com.livingwallpaper.renderer.scene.SceneFactory
import com.livingwallpaper.wallpaper.LivingWallpaperService
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun PreviewScreen(
    configFlow: StateFlow<SceneConfig>,
    snackbarHostState: SnackbarHostState
) {
    val config by configFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(id = config.sceneType.displayNameRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1626))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                GlPreview(configFlow = configFlow)
                Text(
                    text = stringResource(id = R.string.preview_caption),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB9C7DA),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                )
            }
        }

        Button(
            onClick = {
                val failure = openSystemLiveWallpaperPicker(context)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = if (failure == null) {
                            context.getString(R.string.msg_wallpaper_picker_opened)
                        } else {
                            context.getString(R.string.msg_wallpaper_picker_failed, failure)
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text(text = stringResource(id = R.string.entry_set_wallpaper))
        }

        BuildInfoCard()
    }
}

/**
 * App 内预览：与系统壁纸共用 [SceneFactory] 的场景实现，
 * 但持有独立的 EGL Surface 与 GL 线程，不共享 GL 对象。
 */
@Composable
private fun GlPreview(configFlow: StateFlow<SceneConfig>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidExternalSurface(modifier = modifier.fillMaxSize()) {
        onSurface { surface: Surface, width: Int, height: Int ->
            val renderer = GLRenderer(
                sceneFactory = { type -> SceneFactory.create(type, context) },
                initialConfig = configFlow.value
            )
            renderer.start(surface, width, height)
            try {
                coroutineScope {
                    val collector = launch { configFlow.collect { renderer.applyConfig(it) } }
                    try {
                        awaitCancellation()
                    } finally {
                        collector.cancel()
                    }
                }
            } finally {
                renderer.stop()
            }
        }
    }
}

/**
 * 打开系统动态壁纸设置界面。返回 null 表示已成功启动，否则返回失败原因。
 * 最终是否应用由用户在系统界面确认，本应用不绕过系统流程。
 */
private fun openSystemLiveWallpaperPicker(context: Context): String? {
    val component = ComponentName(context, LivingWallpaperService::class.java)
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        "ActivityNotFoundException"
    } catch (e: SecurityException) {
        "SecurityException: ${e.message}"
    }
}

@Composable
private fun BuildInfoCard() {
    MaterialSurface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BuildInfoLine(
                stringResource(id = R.string.build_application_id, BuildConfig.APPLICATION_ID)
            )
            BuildInfoLine(
                stringResource(
                    id = R.string.build_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                )
            )
            BuildInfoLine(stringResource(id = R.string.build_type, BuildConfig.BUILD_TYPE))
            BuildInfoLine(
                stringResource(
                    id = R.string.build_sdk,
                    BuildConfig.MIN_SDK_VERSION,
                    BuildConfig.TARGET_SDK_VERSION,
                    BuildConfig.COMPILE_SDK_VERSION
                )
            )
            BuildInfoLine(stringResource(id = R.string.build_persisted))
        }
    }
}

@Composable
private fun BuildInfoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
