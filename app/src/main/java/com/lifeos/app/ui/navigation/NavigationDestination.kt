package com.lifeos.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Calendar : Screen("calendar", "Calendar", Icons.Default.DateRange)
    object Assistant : Screen("assistant", "Assistant", Icons.Default.Star)
    object Modes : Screen("modes", "Modes", Icons.Default.Place)
    object Notifications : Screen("notifications", "Notifications", Icons.Default.Notifications)

    // Secondary / Drill-Down Routes
    object Splash : Screen("splash", "Splash", Icons.Default.PlayArrow)
    object Onboarding : Screen("onboarding", "Onboarding", Icons.Default.Info)
    object Readiness : Screen("readiness", "Readiness", Icons.Default.CheckCircle)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Privacy : Screen("privacy", "Privacy Dashboard", Icons.Default.Lock)
    object GhostMode : Screen("ghost_mode", "Ghost Mode", Icons.Default.Warning)
    object ModeDetail : Screen("mode_detail/{modeId}", "Mode Detail", Icons.Default.Info) {
        fun createRoute(modeId: Int) = "mode_detail/$modeId"
    }

    // Advanced Feature Routes
    object Profile : Screen("profile", "My LIFEOS", Icons.Default.Person)
    object Voice : Screen("voice", "Voice Assistant", Icons.Default.Call)
    object TimeAudit : Screen("time_audit", "Time Audit", Icons.Default.DateRange)
    object LifeMap : Screen("life_map", "Life Map", Icons.Default.LocationOn)
    object MemoryVault : Screen("memory_vault", "Memory Vault", Icons.Default.Edit)
    object Executive : Screen("executive", "Executive Secretary", Icons.Default.Notifications)
    object Sos : Screen("sos", "Emergency SOS", Icons.Default.Warning)
    object More : Screen("more", "More", Icons.Default.Menu)

    companion object {
        val bottomNavItems = listOf(
            Home,
            Modes,
            Voice,
            LifeMap,
            More
        )
    }
}

