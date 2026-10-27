package com.lifeos.app.ui.screens

import androidx.compose.runtime.Composable
import com.lifeos.app.MainViewModel

/**
 * Package forwarder for GhostModeScreen to ui.screens
 */
@Composable
fun GhostModeScreen(
    viewModel: MainViewModel,
    proposalId: Int = 99,
    onNavigateBack: () -> Unit = {}
) {
    com.lifeos.app.ui.GhostModeScreen(
        viewModel = viewModel,
        proposalId = proposalId,
        onNavigateBack = onNavigateBack
    )
}
