package com.lifeos.app.ui.screens

import androidx.compose.runtime.Composable
import com.lifeos.app.MainViewModel
import com.lifeos.app.ui.OnboardingScreen

@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    onFinishOnboarding: () -> Unit
) {
    OnboardingScreen(
        viewModel = viewModel,
        onFinishOnboarding = onFinishOnboarding
    )
}
