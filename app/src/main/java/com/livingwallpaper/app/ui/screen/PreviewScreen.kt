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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
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
    val entryLabel = remember(context) { wallpaperEntryLabel(context) }

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

        // 预览必须按壁纸真实比例（9:19.5）渲染。
        // 横向 4:3 卡片里跑出来的画面和真机壁纸不是一回事：
        // 着色器按"等比空间"布点，视野越宽光斑越多，横屏下密度会翻好几倍，
        // 让人以为壁纸就是这样。
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1626))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.52f)
                        .aspectRatio(9f / 19.5f)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    GlPreview(configFlow = configFlow)
                }
            }
        }

        Text(
            text = stringResource(id = R.string.preview_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                // 动态壁纸只能由系统壁纸界面确认后生效，直接打开系统动态壁纸列表
                // （为什么不能直连预览页，见 [openWallpaperPicker] 注释）
                val failure = openWallpaperPicker(context)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = if (failure == null) {
                            context.getString(R.string.msg_wallpaper_picker_opened, entryLabel)
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

        Text(
            text = stringResource(id = R.string.entry_set_wallpaper_hint, entryLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
 * 打开系统壁纸列表。返回 null 表示已成功启动，否则返回失败原因。
 *
 * 为什么只能走列表（详见 docs/development-log.md 第 15 轮）：
 * 动态壁纸必须由系统壁纸界面确认后才生效——应用无法自己应用
 * （`WallpaperManager.setWallpaperComponent()` 不在公开 SDK 里，且需要
 * signature|privileged 权限）；系统的直连预览页 `LiveWallpaperChange` 在 HyperOS 上
 * 会被 MIUI 私有 app-op 拦下并立即 finish()（logcat: `No permission to change wall paper`），
 * 而 `LiveWallpaperPreview` 未导出，第三方应用拉不起来。
 */
private fun openWallpaperPicker(context: Context): String? {
    var lastError = "no candidate"
    for (intent in wallpaperListIntents()) {
        val failure = startActivitySafely(context, intent)
        if (failure == null) return null
        lastError = failure
    }
    return lastError
}

/** 系统壁纸列表入口，按可靠性排序（`ACTION_LIVE_WALLPAPER_CHOOSER` 在 HyperOS 上可用）。 */
private fun wallpaperListIntents(): List<Intent> = listOf(
    Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),
    Intent(Intent.ACTION_SET_WALLPAPER)
)

/** 启动系统界面，返回 null 表示已成功启动，否则返回失败原因。 */
private fun startActivitySafely(context: Context, intent: Intent): String? {
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (e: ActivityNotFoundException) {
        "ActivityNotFoundException: ${intent.action}"
    } catch (e: SecurityException) {
        "SecurityException: ${intent.action} (${e.message})"
    }
}

/**
 * 系统壁纸列表里显示的条目名（服务 label），用于提示文案。
 * 直接读 PackageManager，避免与应用名（App label）不一致造成困惑。
 */
private fun wallpaperEntryLabel(context: Context): String {
    val component = ComponentName(context, LivingWallpaperService::class.java)
    return runCatching {
        context.packageManager.getServiceInfo(component, 0)
            .loadLabel(context.packageManager)
            .toString()
    }.getOrDefault(context.getString(R.string.app_name))
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
