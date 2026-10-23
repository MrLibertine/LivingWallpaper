package com.livingwallpaper.app.ui

import com.livingwallpaper.core.model.SceneConfig

/** AI 创造页的界面状态。 */
sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Success(
        val config: SceneConfig,
        val explanations: List<String>,
        val changed: Boolean,
        val elapsedMillis: Long
    ) : AiUiState
    data class Error(val message: String) : AiUiState
}
