package com.lifeos.app.ui.screens

import androidx.compose.runtime.Composable
import com.lifeos.app.MainViewModel

/**
 * Package forwarder for NotificationsScreen to ui.screens
 */
@Composable
fun NotificationsScreen(
    viewModel: MainViewModel,
    onOpenGhostMode: (Int) -> Unit = {},
    onNavigateToReadiness: () -> Unit = {}
) {
    com.lifeos.app.ui.NotificationsScreen(
        viewModel = viewModel,
        onOpenGhostMode = onOpenGhostMode,
        onNavigateToReadiness = onNavigateToReadiness
    )
}
