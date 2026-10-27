package com.lifeos.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.ui.theme.*

enum class NavigationItem(
    val label: String,
    val icon: ImageVector
) {
    HOME("Home", Icons.Default.Home),
    CALENDAR("Calendar", Icons.Default.DateRange),
    ASSISTANT("Assistant", Icons.Default.Notifications),
    MODES("Modes", Icons.Default.Place),
    MORE("More", Icons.Default.Settings)
}

enum class SubScreen {
    NONE,
    READINESS,
    MODE_DETAIL
}

@Composable
fun LifeOsMainScaffold(
    viewModel: MainViewModel
) {
    var currentTab by rememberSaveable { mutableStateOf(NavigationItem.HOME) }
    var currentSubScreen by rememberSaveable { mutableStateOf(SubScreen.NONE) }
    var selectedModeForDetail by remember { mutableStateOf<LifeModeEntity?>(null) }

    // Handle Back Press
    BackHandler(enabled = currentSubScreen != SubScreen.NONE || currentTab != NavigationItem.HOME) {
        if (currentSubScreen != SubScreen.NONE) {
            currentSubScreen = SubScreen.NONE
        } else {
            currentTab = NavigationItem.HOME
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = LifeOsBackground,
        bottomBar = {
            if (currentSubScreen == SubScreen.NONE) {
                NavigationBar(
                    containerColor = LifeOsSurface,
                    contentColor = LifeOsTextPrimary,
                    tonalElevation = 8.dp
                ) {
                    NavigationItem.values().forEach { item ->
                        val isSelected = currentTab == item
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                currentTab = item
                                currentSubScreen = SubScreen.NONE
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (isSelected) LifeOsAccentCyan else LifeOsTextSecondary
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    color = if (isSelected) LifeOsAccentCyan else LifeOsTextSecondary
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = LifeOsSurfaceElevated
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = Pair(currentTab, currentSubScreen),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { (tab, subScreen) ->
                when (subScreen) {
                    SubScreen.READINESS -> {
                        ReadinessScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentSubScreen = SubScreen.NONE }
                        )
                    }
                    SubScreen.MODE_DETAIL -> {
                        val mode = selectedModeForDetail
                        if (mode != null) {
                            ModeDetailScreen(
                                viewModel = viewModel,
                                mode = mode,
                                onNavigateBack = { currentSubScreen = SubScreen.NONE }
                            )
                        } else {
                            currentSubScreen = SubScreen.NONE
                        }
                    }
                    SubScreen.NONE -> {
                        when (tab) {
                            NavigationItem.HOME -> {
                                HomeDashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToReadiness = { currentSubScreen = SubScreen.READINESS },
                                    onNavigateToCalendar = { currentTab = NavigationItem.CALENDAR },
                                    onNavigateToAssistant = { currentTab = NavigationItem.ASSISTANT },
                                    onNavigateToModes = { currentTab = NavigationItem.MODES },
                                    onNavigateToSettings = { currentTab = NavigationItem.MORE }
                                )
                            }
                            NavigationItem.CALENDAR -> {
                                CalendarScreen(viewModel = viewModel)
                            }
                            NavigationItem.ASSISTANT -> {
                                NotificationsScreen(viewModel = viewModel)
                            }
                            NavigationItem.MODES -> {
                                LifeModesScreen(
                                    viewModel = viewModel,
                                    onOpenModeDetail = { mode ->
                                        selectedModeForDetail = mode
                                        currentSubScreen = SubScreen.MODE_DETAIL
                                    }
                                )
                            }
                            NavigationItem.MORE -> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onNavigateToReadiness = { currentSubScreen = SubScreen.READINESS }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
