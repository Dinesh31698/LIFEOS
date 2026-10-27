package com.lifeos.app.ui.screens

import androidx.compose.runtime.Composable
import com.lifeos.app.MainViewModel

@Composable
fun ReadinessScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    com.lifeos.app.ui.ReadinessScreen(
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        onNavigateToSettings = onNavigateToSettings
    )
}
