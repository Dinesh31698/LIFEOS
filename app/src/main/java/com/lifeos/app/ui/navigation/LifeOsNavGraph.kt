package com.lifeos.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lifeos.app.MainViewModel
import com.lifeos.app.ui.*
import com.lifeos.app.ui.theme.BackgroundDark

@Composable
fun LifeOsNavGraph(
    viewModel: MainViewModel,
    navController: NavHostController = rememberNavController(),
    initialRoute: String? = null
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val ghostItems by viewModel.ghostItems.collectAsState()
    val ghostEvents by viewModel.ghostEvents.collectAsState()
    val allModes by viewModel.allModes.collectAsState()
    val pendingGhostCount = remember(ghostItems, ghostEvents) { ghostItems.size + ghostEvents.size }

    // Bottom bar is visible on the 5 primary tabs
    val showBottomBar = currentRoute in Screen.bottomNavItems.map { it.route }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("lifeos_prefs", android.content.Context.MODE_PRIVATE) }
    val hasCompletedOnboarding = remember { prefs.getBoolean("onboarding_completed", false) }
    val startDestination = initialRoute ?: if (hasCompletedOnboarding) Screen.Home.route else Screen.Splash.route

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(BackgroundDark),
        containerColor = BackgroundDark,
        bottomBar = {
            if (showBottomBar) {
                LifeOsBottomBar(
                    navController = navController,
                    pendingGhostCount = pendingGhostCount
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            // Screen 1: Splash Screen
            composable(Screen.Splash.route) {
                SplashScreen(
                    onSplashFinished = {
                        val isDone = prefs.getBoolean("onboarding_completed", false)
                        if (isDone) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Screen.Onboarding.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            // Screens 2 & 3: Onboarding Flow
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    viewModel = viewModel,
                    onFinishOnboarding = {
                        prefs.edit().putBoolean("onboarding_completed", true).apply()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            // Tab 1: Home Dashboard
            composable(Screen.Home.route) {
                HomeDashboardScreen(
                    viewModel = viewModel,
                    onNavigateToReadiness = { navController.navigate(Screen.Readiness.route) },
                    onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                    onNavigateToAssistant = { navController.navigate(Screen.Assistant.route) },
                    onNavigateToModes = { navController.navigate(Screen.Modes.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                    onNavigateToVoice = { navController.navigate(Screen.Voice.route) },
                    onNavigateToTimeAudit = { navController.navigate(Screen.TimeAudit.route) },
                    onNavigateToMemoryVault = { navController.navigate(Screen.MemoryVault.route) },
                    onNavigateToExecutive = { navController.navigate(Screen.Executive.route) },
                    onNavigateToMap = { navController.navigate(Screen.LifeMap.route) },
                    onNavigateToSos = { navController.navigate(Screen.Sos.route) }
                )
            }

            // Tab 2: Calendar Schedule
            composable(Screen.Calendar.route) {
                CalendarScreen(viewModel = viewModel)
            }

            // Tab 3: AI Assistant & NLP Copilot
            composable(Screen.Assistant.route) {
                AiAssistantScreen(
                    viewModel = viewModel,
                    onNavigateToGhostCenter = { navController.navigate(Screen.Notifications.route) },
                    onNavigateToReadiness = { navController.navigate(Screen.Readiness.route) }
                )
            }

            // Tab 4: Life Modes Catalog
            composable(Screen.Modes.route) {
                LifeModesScreen(
                    viewModel = viewModel,
                    onOpenModeDetail = { mode ->
                        navController.navigate(Screen.ModeDetail.createRoute(mode.id))
                    }
                )
            }

            // Tab 5: Notifications & Ghost Mode Center
            composable(Screen.Notifications.route) {
                NotificationsScreen(
                    viewModel = viewModel,
                    onOpenGhostMode = { _ -> navController.navigate(Screen.GhostMode.route) },
                    onNavigateToReadiness = { navController.navigate(Screen.Readiness.route) }
                )
            }

            // Sub-Screen: Ghost Mode Proposal Inspection
            composable(Screen.GhostMode.route) {
                GhostModeScreen(
                    viewModel = viewModel,
                    proposalId = 99,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Sub-Screen: Readiness Hub
            composable(Screen.Readiness.route) {
                ReadinessScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            // Sub-Screen: Settings & Privacy
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSplash = { navController.navigate(Screen.Splash.route) },
                    onNavigateToOnboarding = { navController.navigate(Screen.Onboarding.route) },
                    onNavigateToReadiness = { navController.navigate(Screen.Readiness.route) },
                    onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                    onNavigateToLifeModes = { navController.navigate(Screen.Modes.route) },
                    onNavigateToLifeMap = { navController.navigate(Screen.LifeMap.route) },
                    onNavigateToTimeAudit = { navController.navigate(Screen.TimeAudit.route) },
                    onNavigateToMemoryVault = { navController.navigate(Screen.MemoryVault.route) },
                    onNavigateToSos = { navController.navigate(Screen.Sos.route) },
                    onNavigateToVoice = { navController.navigate(Screen.Voice.route) }
                )
            }

            // Sub-Screen: Central Privacy Dashboard
            composable(Screen.Privacy.route) {
                PrivacyScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Sub-Screen: Mode Detail
            composable(
                route = Screen.ModeDetail.route,
                arguments = listOf(navArgument("modeId") { type = NavType.IntType })
            ) { backStackEntry ->
                val modeId = backStackEntry.arguments?.getInt("modeId") ?: 1
                val mode = allModes.find { it.id == modeId } ?: allModes.firstOrNull()
                if (mode != null) {
                    ModeDetailScreen(
                        viewModel = viewModel,
                        mode = mode,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            // Final Phase: Profile Screen
            composable(Screen.Profile.route) {
                ProfileScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Final Phase: Voice Assistant Screen
            composable(Screen.Voice.route) {
                VoiceScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Final Phase: Time Audit Screen
            composable(Screen.TimeAudit.route) {
                TimeAuditScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Final Phase: Life Map Screen
            composable(Screen.LifeMap.route) {
                LifeMapScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Final Phase: Memory Vault Screen
            composable(Screen.MemoryVault.route) {
                MemoryVaultScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Final Phase: Executive Secretary Screen
            composable(Screen.Executive.route) {
                ExecutiveBriefingScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Final Phase: Emergency SOS Screen
            composable(Screen.Sos.route) {
                SosScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Tab 5: More Hub (secondary tools & settings)
            composable(Screen.More.route) {
                MoreHubScreen(
                    viewModel = viewModel,
                    onNavigateToRoute = { route -> navController.navigate(route) },
                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
                )
            }
        }
    }


}
