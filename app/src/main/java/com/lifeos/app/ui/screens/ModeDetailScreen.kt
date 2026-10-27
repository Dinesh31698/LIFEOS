package com.lifeos.app.ui.screens

import androidx.compose.runtime.Composable
import com.lifeos.app.MainViewModel
import com.lifeos.app.data.LifeModeEntity

@Composable
fun ModeDetailScreen(
    viewModel: MainViewModel,
    mode: LifeModeEntity,
    onNavigateBack: () -> Unit
) {
    com.lifeos.app.ui.ModeDetailScreen(
        viewModel = viewModel,
        mode = mode,
        onNavigateBack = onNavigateBack
    )
}
