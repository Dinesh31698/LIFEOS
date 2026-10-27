package com.lifeos.app.ui.screens

import androidx.compose.runtime.Composable
import com.lifeos.app.MainViewModel
import com.lifeos.app.ui.SettingsScreen

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToSplash: () -> Unit = {},
    onNavigateToOnboarding: () -> Unit = {},
    onNavigateToReadiness: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToLifeModes: () -> Unit = {},
    onNavigateToLifeMap: () -> Unit = {},
    onNavigateToTimeAudit: () -> Unit = {},
    onNavigateToMemoryVault: () -> Unit = {},
    onNavigateToSos: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {}
) {
    SettingsScreen(
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        onNavigateToSplash = onNavigateToSplash,
        onNavigateToOnboarding = onNavigateToOnboarding,
        onNavigateToReadiness = onNavigateToReadiness,
        onNavigateToPrivacy = onNavigateToPrivacy,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToLifeModes = onNavigateToLifeModes,
        onNavigateToLifeMap = onNavigateToLifeMap,
        onNavigateToTimeAudit = onNavigateToTimeAudit,
        onNavigateToMemoryVault = onNavigateToMemoryVault,
        onNavigateToSos = onNavigateToSos,
        onNavigateToVoice = onNavigateToVoice
    )
}
