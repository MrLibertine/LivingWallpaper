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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livingwallpaper.app.R
import com.livingwallpaper.core.model.SceneConfig

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiScreen(
    aiState: AiUiState,
    providerName: String,
    onGenerate: (String) -> Unit,
    onApply: (SceneConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var prompt by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 必须明确标注当前不是模型推理（计划书执行规范第 8 条）
        Text(
            text = stringResource(id = R.string.ai_mode_label, providerName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text(text = stringResource(id = R.string.ai_input_label)) },
            placeholder = { Text(text = stringResource(id = R.string.ai_input_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Button(
            onClick = { onGenerate(prompt) },
            modifier = Modifier.fillMaxWidth(),
            enabled = aiState !is AiUiState.Loading && prompt.isNotBlank()
        ) {
            Text(
                text = stringResource(
                    id = if (aiState is AiUiState.Loading) {
                        R.string.ai_generating
                    } else {
                        R.string.ai_generate
                    }
                )
            )
        }

        Text(
            text = stringResource(id = R.string.ai_examples),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                R.string.ai_example_1,
                R.string.ai_example_2,
                R.string.ai_example_3,
                R.string.ai_example_4
            ).forEach { res ->
                val example = stringResource(id = res)
                SuggestionChip(
                    onClick = { prompt = example },
                    label = { Text(text = example) }
                )
            }
        }

        when (val state = aiState) {
            AiUiState.Idle -> Unit
            AiUiState.Loading -> CircularProgressIndicator()
            is AiUiState.Error -> ErrorCard(message = state.message, onDismiss = onDismiss)
            is AiUiState.Success -> SuccessCard(
                state = state,
                onApply = onApply,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun SuccessCard(
    state: AiUiState.Success,
    onApply: (SceneConfig) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.ai_result_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            state.explanations.forEach { explanation ->
                Text(
                    text = "· $explanation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(id = R.string.ai_elapsed, state.elapsedMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!state.changed) {
                Text(
                    text = stringResource(id = R.string.ai_no_change),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onApply(state.config) }) {
                    Text(text = stringResource(id = R.string.ai_apply))
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(text = stringResource(id = R.string.ai_discard))
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    MaterialSurface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.ai_error_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.ai_discard))
            }
        }
    }
}
