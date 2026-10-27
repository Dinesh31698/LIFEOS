package com.lifeos.app.ui.screens

import androidx.compose.runtime.Composable
import com.lifeos.app.MainViewModel

/**
 * Package forwarder for AiAssistantScreen to ui.screens
 */
@Composable
fun AiAssistantScreen(
    viewModel: MainViewModel,
    onNavigateToGhostCenter: () -> Unit = {},
    onNavigateToReadiness: () -> Unit = {}
) {
    com.lifeos.app.ui.AiAssistantScreen(
        viewModel = viewModel,
        onNavigateToGhostCenter = onNavigateToGhostCenter,
        onNavigateToReadiness = onNavigateToReadiness
    )
}
