package com.livingwallpaper.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.livingwallpaper.ai.api.AIProvider
import com.livingwallpaper.ai.api.InstructionNotUnderstoodException
import com.livingwallpaper.ai.provider.RuleBasedProvider
import com.livingwallpaper.core.data.SceneRepositoryProvider
import com.livingwallpaper.core.model.SceneConfig
import com.livingwallpaper.core.model.PerformanceLevel
import com.livingwallpaper.core.model.SceneType
import com.livingwallpaper.core.model.WeatherType
import com.livingwallpaper.core.model.defaultSceneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 场景配置 ViewModel。UI 只通过它与 [com.livingwallpaper.core.data.SceneRepository] 交互，
 * 壁纸进程内共用同一个仓库实例，因此这里的修改会实时反映到系统壁纸上。
 */
class SceneViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SceneRepositoryProvider.get(application.applicationContext)

    /** 当前启用的 AI 提供者。P6 唯一实现是规则模式，不是模型推理。 */
    private val aiProvider: AIProvider = RuleBasedProvider()

    val config: StateFlow<SceneConfig> = repository.config.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = repository.snapshot()
    )

    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()

    val aiProviderName: String get() = aiProvider.displayName

    fun update(transform: (SceneConfig) -> SceneConfig) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun selectScene(type: SceneType) {
        update { current ->
            current.copy(sceneType = type, sceneId = type.defaultSceneId)
        }
    }

    fun selectWeather(weather: WeatherType) {
        update { current -> current.copy(weather = weather) }
    }

    fun selectSkyColor(hex: String) {
        update { current -> current.copy(skyColor = hex) }
    }

    fun selectTargetFps(fps: Int) {
        update { current -> current.copy(targetFps = fps) }
    }

    fun selectPerformanceLevel(level: PerformanceLevel) {
        update { current -> current.copy(performanceLevel = level) }
    }

    fun setReduceMotion(enabled: Boolean) {
        update { current -> current.copy(reduceMotion = enabled) }
    }

    /** 关闭视差：强度置 0；开启时恢复默认值，不留“上次是多少”的隐式状态。 */
    fun setParallaxEnabled(enabled: Boolean) {
        update { current ->
            current.copy(
                parallaxStrength = if (enabled) {
                    SceneConfig.DEFAULT_PARALLAX_STRENGTH
                } else {
                    0f
                }
            )
        }
    }

    /**
     * 用自然语言生成配置。失败时保留原配置并在 UI 上给出真实原因。
     */
    fun generateFromPrompt(prompt: String) {
        viewModelScope.launch {
            _aiState.value = AiUiState.Loading
            _aiState.value = try {
                val result = aiProvider.generateSceneConfig(prompt, repository.snapshot())
                AiUiState.Success(
                    config = result.config,
                    explanations = result.explanations,
                    changed = result.changed,
                    elapsedMillis = result.elapsedMillis
                )
            } catch (e: InstructionNotUnderstoodException) {
                AiUiState.Error(e.message ?: "无法理解该指令")
            } catch (t: Throwable) {
                AiUiState.Error("生成失败：${t::class.simpleName}：${t.message}")
            }
        }
    }

    fun applyAiConfig(config: SceneConfig) {
        update { config }
        _aiState.value = AiUiState.Idle
    }

    fun dismissAiState() {
        _aiState.value = AiUiState.Idle
    }
}

internal val SceneType.displayNameRes: Int
    get() = when (this) {
        SceneType.FOREST -> com.livingwallpaper.app.R.string.scene_forest_night
        SceneType.AURORA -> com.livingwallpaper.app.R.string.scene_aurora_lake
        SceneType.STAR_VALLEY -> com.livingwallpaper.app.R.string.scene_star_valley
        SceneType.MEADOW -> com.livingwallpaper.app.R.string.scene_meadow
    }

internal val SceneType.descriptionRes: Int
    get() = when (this) {
        SceneType.FOREST -> com.livingwallpaper.app.R.string.scene_forest_night_desc
        SceneType.AURORA -> com.livingwallpaper.app.R.string.scene_aurora_lake_desc
        SceneType.STAR_VALLEY -> com.livingwallpaper.app.R.string.scene_star_valley_desc
        SceneType.MEADOW -> com.livingwallpaper.app.R.string.scene_meadow_desc
    }
