package com.livingwallpaper.app.ui

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.livingwallpaper.app.R
import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.core.model.SceneType
import com.livingwallpaper.core.model.WeatherType

/**
 * 参数编辑器。滑块变化会立即写入仓库，App 内预览与系统壁纸同步更新。
 *
 * 只显示当前场景适用的参数：例如萤火虫密度只在森林雨夜出现，
 * 流星密度只在星空山谷出现（计划书 2.1）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParameterScreen(
    config: SceneConfig,
    onUpdate: ((SceneConfig) -> SceneConfig) -> Unit,
    onSelectWeather: (WeatherType) -> Unit,
    onSelectSkyColor: (String) -> Unit,
    onSelectTargetFps: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Section(title = stringResource(id = R.string.section_appearance)) {
            Text(
                text = stringResource(id = R.string.label_weather),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeatherType.entries.forEach { weather ->
                    FilterChip(
                        selected = weather == config.weather,
                        onClick = { onSelectWeather(weather) },
                        label = { Text(text = stringResource(id = weather.labelRes)) }
                    )
                }
            }

            Text(
                text = stringResource(id = R.string.label_sky_color),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SKY_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = preset.equals(config.skyColor, ignoreCase = true),
                        onClick = { onSelectSkyColor(preset) },
                        label = { Text(text = preset) }
                    )
                }
            }
        }

        Section(title = stringResource(id = R.string.section_motion)) {
            applicableParams(config.sceneType).forEach { spec ->
                ParamSlider(
                    label = stringResource(id = spec.labelRes),
                    value = spec.value(config),
                    range = spec.range,
                    onValueChange = { value -> onUpdate { current -> spec.apply(current, value) } }
                )
            }

            Text(
                text = stringResource(id = R.string.label_target_fps),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SceneConfig.SUPPORTED_FPS.forEach { fps ->
                    FilterChip(
                        selected = fps == config.targetFps,
                        onClick = { onSelectTargetFps(fps) },
                        label = {
                            Text(text = stringResource(id = R.string.fps_value, fps))
                        }
                    )
                }
            }
        }
    }
}

private data class ParamSpec(
    val labelRes: Int,
    val range: ClosedFloatingPointRange<Float>,
    val value: (SceneConfig) -> Float,
    val apply: (SceneConfig, Float) -> SceneConfig
)

private val RAIN_PARAM = ParamSpec(
    labelRes = R.string.param_rain,
    range = 0f..1f,
    value = { it.rainIntensity },
    apply = { config, value -> config.copy(rainIntensity = value) }
)

private val WIND_PARAM = ParamSpec(
    labelRes = R.string.param_wind,
    range = 0f..1f,
    value = { it.windStrength },
    apply = { config, value -> config.copy(windStrength = value) }
)

private val LIGHT_PARAM = ParamSpec(
    labelRes = R.string.param_light,
    range = 0f..1f,
    value = { it.lightIntensity },
    apply = { config, value -> config.copy(lightIntensity = value) }
)

private val ANIMATION_SPEED_PARAM = ParamSpec(
    labelRes = R.string.param_animation_speed,
    range = SceneConfig.MIN_ANIMATION_SPEED..SceneConfig.MAX_ANIMATION_SPEED,
    value = { it.animationSpeed },
    apply = { config, value -> config.copy(animationSpeed = value) }
)

private val PARALLAX_PARAM = ParamSpec(
    labelRes = R.string.param_parallax,
    range = 0f..1f,
    value = { it.parallaxStrength },
    apply = { config, value -> config.copy(parallaxStrength = value) }
)

private val FIREFLY_PARAM = ParamSpec(
    labelRes = R.string.param_firefly,
    range = 0f..1f,
    value = { it.particleDensity },
    apply = { config, value -> config.copy(particleDensity = value) }
)

private val SNOW_PARAM = ParamSpec(
    labelRes = R.string.param_snow,
    range = 0f..1f,
    value = { it.particleDensity },
    apply = { config, value -> config.copy(particleDensity = value) }
)

private val METEOR_PARAM = ParamSpec(
    labelRes = R.string.param_meteor,
    range = 0f..1f,
    value = { it.particleDensity },
    apply = { config, value -> config.copy(particleDensity = value) }
)

private fun applicableParams(sceneType: SceneType): List<ParamSpec> = when (sceneType) {
    SceneType.FOREST -> listOf(
        RAIN_PARAM,
        WIND_PARAM,
        FIREFLY_PARAM,
        LIGHT_PARAM,
        ANIMATION_SPEED_PARAM,
        PARALLAX_PARAM
    )
    SceneType.AURORA -> listOf(
        SNOW_PARAM,
        WIND_PARAM,
        LIGHT_PARAM,
        ANIMATION_SPEED_PARAM,
        PARALLAX_PARAM
    )
    SceneType.STAR_VALLEY -> listOf(
        METEOR_PARAM,
        WIND_PARAM,
        LIGHT_PARAM,
        ANIMATION_SPEED_PARAM,
        PARALLAX_PARAM
    )
    SceneType.MEADOW -> listOf(
        RAIN_PARAM,
        WIND_PARAM,
        LIGHT_PARAM,
        ANIMATION_SPEED_PARAM,
        PARALLAX_PARAM
    )
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val display = if (value <= 0.0001f && range.start == 0f) {
                stringResource(id = R.string.param_off)
            } else {
                "%.2f".format(value)
            }
            Text(
                text = display,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
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

private val WeatherType.labelRes: Int
    get() = when (this) {
        WeatherType.CLEAR -> R.string.weather_clear
        WeatherType.RAIN -> R.string.weather_rain
        WeatherType.CLOUDY -> R.string.weather_cloudy
    }

private val SKY_PRESETS = listOf(
    "#0B1626",
    "#131B33",
    "#2B1B3D",
    "#3A2A20",
    "#123243",
    "#1E3326"
)
