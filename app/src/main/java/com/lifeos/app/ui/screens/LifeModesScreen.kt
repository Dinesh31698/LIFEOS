package com.lifeos.app.ui.screens

import androidx.compose.runtime.Composable
import com.lifeos.app.MainViewModel
import com.lifeos.app.data.LifeModeEntity

@Composable
fun LifeModesScreen(
    viewModel: MainViewModel,
    onOpenModeDetail: (LifeModeEntity) -> Unit
) {
    com.lifeos.app.ui.LifeModesScreen(
        viewModel = viewModel,
        onOpenModeDetail = onOpenModeDetail
    )
}
