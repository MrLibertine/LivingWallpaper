package com.livingwallpaper.app.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livingwallpaper.app.R
import com.livingwallpaper.core.model.PerformanceLevel
import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.renderer.performance.RenderStats
import kotlinx.coroutines.delay

/**
 * 性能中心。
 *
 * 展示的数值全部是实测值：帧率/帧间隔/丢帧来自正在运行的渲染循环，
 * 内存来自 Runtime 与 ActivityManager。没有数据时明确说明原因，不编造数字。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerformanceScreen(
    config: SceneConfig,
    onSelectPerformanceLevel: (PerformanceLevel) -> Unit,
    onSetReduceMotion: (Boolean) -> Unit,
    onSetParallaxEnabled: (Boolean) -> Unit,
    onSelectTargetFps: (Int) -> Unit
) {
    val stats by com.livingwallpaper.renderer.performance.RenderStatsHolder.stats
        .collectAsStateWithLifecycle()
    val context = LocalContext.current
    val memory = rememberMemorySnapshot()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Section(title = stringResource(id = R.string.perf_level_title)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PerformanceLevel.entries.forEach { level ->
                    FilterChip(
                        selected = level == config.performanceLevel,
                        onClick = { onSelectPerformanceLevel(level) },
                        label = { Text(text = stringResource(id = level.labelRes)) }
                    )
                }
            }
            val level = config.performanceLevel
            Text(
                text = stringResource(
                    id = R.string.perf_level_effect,
                    "%.2f".format(level.particleScale),
                    "%.2f".format(level.motionScale),
                    level.suggestedFps
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Section(title = stringResource(id = R.string.label_target_fps)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SceneConfig.SUPPORTED_FPS.forEach { fps ->
                    FilterChip(
                        selected = fps == config.targetFps,
                        onClick = { onSelectTargetFps(fps) },
                        label = { Text(text = stringResource(id = R.string.fps_value, fps)) }
                    )
                }
            }
        }

        Section(title = stringResource(id = R.string.perf_reduce_motion)) {
            ToggleRow(
                title = stringResource(id = R.string.perf_reduce_motion),
                description = stringResource(id = R.string.perf_reduce_motion_desc),
                checked = config.reduceMotion,
                onCheckedChange = onSetReduceMotion
            )
            ToggleRow(
                title = stringResource(id = R.string.perf_parallax_toggle),
                description = stringResource(id = R.string.param_parallax),
                checked = config.parallaxStrength > 0f,
                onCheckedChange = onSetParallaxEnabled
            )
        }

        Section(title = stringResource(id = R.string.perf_stats_title)) {
            if (stats.hasSamples) {
                StatsLines(stats = stats)
            } else {
                Text(
                    text = stringResource(id = R.string.perf_stats_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Section(title = stringResource(id = R.string.perf_memory_title)) {
            Text(
                text = stringResource(
                    id = R.string.perf_memory_heap,
                    memory.usedHeapMb,
                    memory.maxHeapMb
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    id = R.string.perf_memory_available,
                    memory.availableSystemMb
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatsLines(stats: RenderStats) {
    val unknown = stringResource(id = R.string.perf_value_unknown)
    Text(
        text = stringResource(
            id = R.string.perf_measured_fps,
            if (stats.measuredFps > 0f) "%.1f".format(stats.measuredFps) else unknown
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = stringResource(
            id = R.string.perf_frame_time,
            "%.1f".format(stats.averageFrameMillis),
            "%.1f".format(stats.lastFrameMillis)
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = stringResource(id = R.string.perf_dropped, stats.droppedFrames),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = stringResource(id = R.string.perf_frames, stats.framesRendered),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    MaterialSurface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

private data class MemorySnapshot(
    val usedHeapMb: Long,
    val maxHeapMb: Long,
    val availableSystemMb: Long
)

/** 每秒刷新一次真实内存占用。数值来自系统 API，不做任何估算。 */
@Composable
private fun rememberMemorySnapshot(): MemorySnapshot {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(readMemory(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            snapshot = readMemory(context)
        }
    }
    return snapshot
}

private fun readMemory(context: Context): MemorySnapshot {
    val runtime = Runtime.getRuntime()
    val used = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
    val max = runtime.maxMemory()
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val info = ActivityManager.MemoryInfo()
    val available = if (activityManager != null) {
        activityManager.getMemoryInfo(info)
        info.availMem
    } else {
        0L
    }
    return MemorySnapshot(
        usedHeapMb = used / (1024 * 1024),
        maxHeapMb = max / (1024 * 1024),
        availableSystemMb = available / (1024 * 1024)
    )
}

private val PerformanceLevel.labelRes: Int
    get() = when (this) {
        PerformanceLevel.LOW -> R.string.perf_level_low
        PerformanceLevel.MEDIUM -> R.string.perf_level_medium
        PerformanceLevel.HIGH -> R.string.perf_level_high
    }
