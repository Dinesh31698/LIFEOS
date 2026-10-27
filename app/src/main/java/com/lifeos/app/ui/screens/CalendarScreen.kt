package com.lifeos.app.ui.screens

import androidx.compose.runtime.Composable
import com.lifeos.app.MainViewModel

/**
 * Package forwarder for CalendarScreen to ui.screens
 */
@Composable
fun CalendarScreen(viewModel: MainViewModel) {
    com.lifeos.app.ui.CalendarScreen(viewModel = viewModel)
}
