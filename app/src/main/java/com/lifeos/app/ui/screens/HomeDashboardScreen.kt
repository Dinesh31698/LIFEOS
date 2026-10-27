package com.lifeos.app.ui.screens

import androidx.compose.runtime.Composable
import com.lifeos.app.MainViewModel

@Composable
fun HomeDashboardScreen(
    viewModel: MainViewModel,
    onNavigateToReadiness: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToModes: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNotifications: () -> Unit = {}
) {
    com.lifeos.app.ui.HomeDashboardScreen(
        viewModel = viewModel,
        onNavigateToReadiness = onNavigateToReadiness,
        onNavigateToCalendar = onNavigateToCalendar,
        onNavigateToAssistant = onNavigateToAssistant,
        onNavigateToModes = onNavigateToModes,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToNotifications = onNavigateToNotifications
    )
}
