package com.lifeos.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.DashboardState
import com.lifeos.app.MainViewModel
import com.lifeos.app.data.*
import com.lifeos.app.engine.*
import com.lifeos.app.engine.ForgettingModelEngine
import com.lifeos.app.engine.PowerProfile
import com.lifeos.app.engine.SafeModeManager
import com.lifeos.app.engine.UserProfileEngine
import com.lifeos.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    viewModel: MainViewModel,
    onNavigateToReadiness: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToModes: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    onNavigateToTimeAudit: () -> Unit = {},
    onNavigateToMemoryVault: () -> Unit = {},
    onNavigateToExecutive: () -> Unit = {},
    onNavigateToMap: () -> Unit = {},
    onNavigateToSos: () -> Unit = {}
) {

    val dashboardState by viewModel.dashboardState.collectAsState()
    val ghostItems by viewModel.ghostItems.collectAsState()
    val ghostEvents by viewModel.ghostEvents.collectAsState()
    val habits by viewModel.allHabits.collectAsState()
    val habitMap = remember(habits) { habits.associateBy { it.itemName.lowercase() } }
    val pendingNotificationCount = remember(ghostItems, ghostEvents) { ghostItems.size + ghostEvents.size }
    val replayState by viewModel.replayState.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val profileName = userProfile?.preferredName?.trim()?.ifEmpty { "Dinesh" } ?: "Dinesh"
    val avatarInitial = profileName.firstOrNull()?.uppercase() ?: "D"

    var assistantInput by remember { mutableStateOf("") }

    var assistantFeedback by remember { mutableStateOf<String?>(null) }
    var isProcessingAssistant by remember { mutableStateOf(false) }

    val currentBatteryLevel by BatteryBudgetManager.currentBatteryLevel.collectAsState()
    val currentPowerProfile by BatteryBudgetManager.currentPowerProfile.collectAsState()
    var showBatteryOverrideMenu by remember { mutableStateOf(false) }

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val (greetingTitle, greetingIcon) = remember(currentHour) {
        when (currentHour) {
            in 5..11 -> "Good Morning" to "☀️"
            in 12..16 -> "Good Afternoon" to "🌤️"
            in 17..21 -> "Good Evening" to "🌇"
            else -> "Good Night" to "🌙"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp)
    ) {
        // 1. Top Bar: App Brand & Action Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Logo & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(listOf(SuccessGreen, CyanAccent))),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(BackgroundDark)
                        )
                    }

                    Text(
                        text = "LIFEOS",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = TextPrimary
                    )
                }

                // Top Action Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Module 5: Interactive Battery Budget Status Chip with Dev Override Menu
                    Box {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = when (currentPowerProfile) {
                                PowerProfile.CRITICAL -> AlertRed.copy(alpha = 0.18f)
                                PowerProfile.SAVER -> WarningAmber.copy(alpha = 0.18f)
                                PowerProfile.NORMAL -> SurfaceDark
                            },
                            border = BorderStroke(
                                1.dp,
                                when (currentPowerProfile) {
                                    PowerProfile.CRITICAL -> AlertRed
                                    PowerProfile.SAVER -> WarningAmber
                                    PowerProfile.NORMAL -> SuccessGreen.copy(alpha = 0.4f)
                                }
                            ),
                            modifier = Modifier.clickable { showBatteryOverrideMenu = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = when (currentPowerProfile) {
                                        PowerProfile.CRITICAL -> "⚠️"
                                        PowerProfile.SAVER -> "🪫"
                                        PowerProfile.NORMAL -> "🔋"
                                    },
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "$currentBatteryLevel% ${currentPowerProfile.name}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (currentPowerProfile) {
                                        PowerProfile.CRITICAL -> AlertRed
                                        PowerProfile.SAVER -> WarningAmber
                                        PowerProfile.NORMAL -> SuccessGreen
                                    }
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showBatteryOverrideMenu,
                            onDismissRequest = { showBatteryOverrideMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("🔋 100% NORMAL (Full scans enabled)") },
                                onClick = {
                                    BatteryBudgetManager.setSimulatedBattery(100, false)
                                    showBatteryOverrideMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🪫 15% SAVER (Pause BLE scans)") },
                                onClick = {
                                    BatteryBudgetManager.setSimulatedBattery(15, false)
                                    showBatteryOverrideMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("⚠️ 5% CRITICAL (Pause all sensors)") },
                                onClick = {
                                    BatteryBudgetManager.setSimulatedBattery(5, false)
                                    showBatteryOverrideMenu = false
                                }
                            )
                        }
                    }

                    // System Privacy Status Chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (dashboardState.isSafeModeEnabled) AlertRed.copy(alpha = 0.18f) else SurfaceDark,
                        border = BorderStroke(
                            1.dp,
                            if (dashboardState.isSafeModeEnabled) AlertRed else SuccessGreen.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.clickable { SafeModeManager.toggleSafeMode() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (dashboardState.isSafeModeEnabled) AlertRed else SuccessGreen)
                            )
                            Text(
                                text = if (dashboardState.isSafeModeEnabled) "Safe Mode" else "100% Local",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (dashboardState.isSafeModeEnabled) AlertRed else SuccessGreen
                            )
                        }
                    }

                    // Search Icon Button
                    IconButton(
                        onClick = onNavigateToCalendar,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Notifications Bell with Alert Badge
                    Box(
                        contentAlignment = Alignment.TopEnd
                    ) {
                        IconButton(
                            onClick = onNavigateToNotifications,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (pendingNotificationCount > 0) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp, end = 6.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AlertRed)
                            )
                        }
                    }

                    // User Profile Avatar
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(PrimaryPurple, CyanAccent)))
                            .clickable { onNavigateToProfile() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = avatarInitial,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // 2. Time-Based Greeting Section
        item {
            val greetingString = userProfile?.let { UserProfileEngine.generateGreeting(it, currentHour) } ?: "$greetingTitle, $profileName"
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "$greetingString $greetingIcon",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Stay ready, stay ahead • Role: ${userProfile?.primaryRole ?: "Student"}",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }

        // 2.5 Advanced Hub Quick Access Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item {
                    AssistChip(
                        onClick = onNavigateToVoice,
                        label = { Text("🎙️ Voice") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceDark)
                    )
                }
                item {
                    AssistChip(
                        onClick = onNavigateToTimeAudit,
                        label = { Text("⏱️ Time Audit") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceDark)
                    )
                }
                item {
                    AssistChip(
                        onClick = onNavigateToMemoryVault,
                        label = { Text("🧠 Memory") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceDark)
                    )
                }
                item {
                    AssistChip(
                        onClick = onNavigateToExecutive,
                        label = { Text("📋 Executive") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceDark)
                    )
                }
                item {
                    AssistChip(
                        onClick = onNavigateToMap,
                        label = { Text("🗺️ Map") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceDark)
                    )
                }
                item {
                    AssistChip(
                        onClick = onNavigateToSos,
                        label = { Text("🆘 SOS") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = AlertRed.copy(alpha = 0.2f))
                    )
                }
            }
        }


        // Module 6: Prominent Amber Safety Banner when Safe Mode is active
        if (dashboardState.isSafeModeEnabled) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f)),
                    border = BorderStroke(1.5.dp, WarningAmber)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "🛡️",
                                fontSize = 22.sp
                            )
                            Column {
                                Text(
                                    text = "SAFE MODE ACTIVE",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WarningAmber,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Autonomous Mutations Suppressed",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = {
                                SafeModeManager.setSafeMode(false)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Restore",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // 2b. Context Auto-Detection Proposal Banner
        if (dashboardState.detectedMode != null &&
            dashboardState.detectedMode?.id != dashboardState.activeMode?.id &&
            (dashboardState.contextActionDecision == ContextActionDecision.PROPOSE ||
             dashboardState.detectedConfidenceLevel == ContextConfidenceLevel.MEDIUM)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "💡", fontSize = 16.sp)
                                Column {
                                    Text(
                                        text = "CONTEXT DETECTED",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanAccent,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "Switch to ${dashboardState.detectedMode?.name}? (${(dashboardState.detectedConfidenceScore * 100).toInt()}% match)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.dismissContextProposal() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (dashboardState.contextSignals.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = dashboardState.contextSignals.take(2).joinToString(" • "),
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.applyProposedMode() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Switch to ${dashboardState.detectedMode?.name}",
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.dismissContextProposal() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                border = BorderStroke(1.dp, BorderDark)
                            ) {
                                Text("Keep Current", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 3. Central Animated Readiness Ring Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToReadiness() },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Row inside Readiness Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CURRENT READINESS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            letterSpacing = 1.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PrimaryPurple.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { onNavigateToModes() }
                        ) {
                            Text(
                                text = dashboardState.activeMode?.name ?: "Detecting",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PurpleLight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Gauge Row with Context Navigation Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Mode Settings Button
                        IconButton(
                            onClick = onNavigateToModes,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceDark)
                                .border(1.dp, BorderDark, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "Modes",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Central Animated Ring Gauge
                        ReadinessRingGauge(
                            percentage = dashboardState.readinessPercentage,
                            modifier = Modifier.size(165.dp)
                        )

                        // Right Arrow Navigation to Readiness Hub
                        IconButton(
                            onClick = onNavigateToReadiness,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceDark)
                                .border(1.dp, BorderDark, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "View Details",
                                tint = CyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when {
                            dashboardState.activeItems.isEmpty() -> "No requirements configured for this mode"
                            dashboardState.missingItems.isEmpty() -> "All critical items ready for departure"
                            else -> "${dashboardState.missingItems.size} items required before departure"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (dashboardState.activeItems.isEmpty()) TextMuted else TextPrimary
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (dashboardState.activeItems.isEmpty()) {
                            "Tap to add requirements"
                        } else {
                            "${dashboardState.readyItems.size} of ${dashboardState.activeItems.size} items prepared • Tap for details"
                        },
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // 4. Quick Stat Pills Row (Today, Tasks, Items)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickStatCard(
                    icon = Icons.Default.DateRange,
                    label = "Today",
                    countText = "${dashboardState.todayStats.todayEventsCount} events",
                    accentColor = Color(0xFF0984E3),
                    onClick = onNavigateToCalendar,
                    modifier = Modifier.weight(1f)
                )

                QuickStatCard(
                    icon = Icons.Default.CheckCircle,
                    label = "Tasks",
                    countText = "${dashboardState.todayStats.pendingTasksCount} pending",
                    accentColor = SuccessGreen,
                    onClick = onNavigateToReadiness,
                    modifier = Modifier.weight(1f)
                )

                QuickStatCard(
                    icon = Icons.Default.Warning,
                    label = "Items",
                    countText = "${dashboardState.todayStats.missingItemsCount} missing",
                    accentColor = if (dashboardState.todayStats.missingItemsCount > 0) AlertRed else TextMuted,
                    onClick = onNavigateToReadiness,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 5. Consequence Engine Alert Banner (if active)
        if (dashboardState.consequenceAlert != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, AlertRed)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Consequence Alert",
                                tint = AlertRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = dashboardState.consequenceAlert!!.title,
                                fontWeight = FontWeight.Bold,
                                color = AlertRed,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dashboardState.consequenceAlert!!.description,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.dismissConsequenceAlert() }) {
                                Text("Dismiss", color = TextSecondary, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { dashboardState.activeMode?.let { viewModel.resetWaivedItems(it.id) } },
                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Undo Waive", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 6. "Next Up" Active Event Card
        item {
            val nextEvent = dashboardState.nextUpEvent
            val countdown = remember(nextEvent) {
                nextEvent?.let { calculateCountdown(it.startTime) } ?: "In 1h 20m"
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToCalendar() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Header: "NEXT UP" and Countdown chip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PrimaryPurple.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Next Up",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "NEXT UP",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                letterSpacing = 1.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PrimaryPurple.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = countdown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PurpleLight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = nextEvent?.title ?: "DBMS Lab",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${formatEventTime(nextEvent?.startTime ?: 0L, nextEvent?.endTime ?: 0L)} • ${nextEvent?.location ?: "Room 204"}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Inline Requirement Chips
                    Text(
                        text = "Required Gear & Documents:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sortedItems = dashboardState.activeItems.sortedWith(
                            compareByDescending<ItemRequirementEntity> { item ->
                                val habit = habitMap[item.itemName.lowercase()]
                                val isHigh = habit != null && ForgettingModelEngine.isFrequentlyForgotten(habit)
                                val boost = habit?.priorityBoost ?: 0.0f
                                if (!item.isReady && !item.isWaived && isHigh) 2000f + boost
                                else if (!item.isReady && !item.isWaived) 1000f + boost
                                else boost
                            }.thenBy { it.id }
                        )
                        items(sortedItems.take(5)) { item ->
                            InlineRequirementChip(
                                item = item,
                                habit = habitMap[item.itemName.lowercase()],
                                onToggle = { viewModel.toggleItemStatus(item) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Priority Action Row with [ PREPARE ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dashboardState.missingItems.isNotEmpty()) {
                            Text(
                                text = "⚠ ${dashboardState.missingItems.first().itemName} missing",
                                color = WarningAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "✓ All gear confirmed",
                                color = SuccessGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { onNavigateToReadiness() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "PREPARE",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }


        // 7. Quick AI Prompt Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "AI",
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )

                        OutlinedTextField(
                            value = assistantInput,
                            onValueChange = { assistantInput = it },
                            placeholder = {
                                Text(
                                    text = "Ask anything...",
                                    fontSize = 13.sp,
                                    color = TextMuted
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = SurfaceDark,
                                unfocusedContainerColor = SurfaceDark
                            ),
                            singleLine = true
                        )

                        IconButton(
                            onClick = onNavigateToAssistant,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Voice Input",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (assistantInput.isNotBlank()) {
                                    isProcessingAssistant = true
                                    viewModel.processAssistantPrompt(assistantInput) { feedback ->
                                        assistantFeedback = feedback
                                        assistantInput = ""
                                        isProcessingAssistant = false
                                    }
                                } else {
                                    onNavigateToAssistant()
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(PrimaryPurple, CyanAccent))
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = assistantFeedback != null) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = PrimaryPurple.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = assistantFeedback ?: "",
                                        fontSize = 12.sp,
                                        color = PurpleLight,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { assistantFeedback = null },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = PurpleLight,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Module 6: End-to-End 3-Stage Replay & Simulation Engine Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, if (replayState.isRunning) CyanAccent else BorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "🎬", fontSize = 18.sp)
                            Column {
                                Text(
                                    text = "3-STAGE SCENARIO REPLAY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = if (replayState.isRunning) "Executing Stage ${replayState.currentStep} of ${replayState.totalSteps}..." else "Ready for Simulation",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (replayState.isRunning) SuccessGreen.copy(alpha = 0.2f) else SurfaceDark,
                            border = BorderStroke(1.dp, if (replayState.isRunning) SuccessGreen else BorderDark)
                        ) {
                            Text(
                                text = if (replayState.isRunning) "RUNNING" else if (replayState.currentStep == 3) "COMPLETE" else "IDLE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (replayState.isRunning) SuccessGreen else if (replayState.currentStep == 3) CyanAccent else TextMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = replayState.statusText,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )

                    if (replayState.history.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            replayState.history.forEach { step ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SurfaceDark,
                                    border = BorderStroke(0.5.dp, BorderDark),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = step.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyanAccent
                                        )
                                        Text(
                                            text = step.detail,
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startReplaySimulation() },
                            enabled = !replayState.isRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (replayState.isRunning) "Simulating..." else "▶ Run 3-Stage Replay",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.resetReplaySimulation() },
                            enabled = !replayState.isRunning,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            border = BorderStroke(1.dp, BorderDark)
                        ) {
                            Text("Reset", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReadinessRingGauge(
    percentage: Int,
    modifier: Modifier = Modifier
) {
    // Treat -1 (not configured) as 0 for the arc animation
    val displayPercentage = percentage.coerceAtLeast(0)
    val animatedProgress by animateFloatAsState(
        targetValue = displayPercentage / 100f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "ReadinessRingAnimation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            val strokeWidth = 15.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)

            // Background Track
            drawArc(
                color = Color(0xFF141924),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Dynamic Gradient Sweep Arc
            val gradientBrush = Brush.sweepGradient(
                0.0f to CyanAccent,
                0.5f to PrimaryPurple,
                0.85f to SuccessGreen,
                1.0f to CyanAccent
            )

            if (animatedProgress > 0f) {
                drawArc(
                    brush = gradientBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (percentage < 0) "—" else "$percentage%",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (percentage < 0) TextMuted else TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    percentage < 0 -> "Not Set"
                    percentage >= 80 -> "Ready"
                    else -> "Pending"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    percentage < 0 -> TextMuted
                    percentage >= 80 -> SuccessGreen
                    else -> CyanAccent
                },
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
fun QuickStatCard(
    icon: ImageVector,
    label: String,
    countText: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = countText,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun InlineRequirementChip(
    item: ItemRequirementEntity,
    habit: ItemHabitEntity? = null,
    onToggle: () -> Unit
) {
    val isReady = item.isReady
    val isHighRisk = habit != null && ForgettingModelEngine.isFrequentlyForgotten(habit)
    val accentColor = when {
        isReady -> SuccessGreen
        isHighRisk -> Color(0xFFFF7043)
        else -> AlertRed
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = if (isHighRisk && !isReady) 0.18f else 0.12f),
        border = BorderStroke(
            if (isHighRisk && !isReady) 1.5.dp else 1.dp,
            accentColor.copy(alpha = if (isHighRisk && !isReady) 0.9f else 0.45f)
        ),
        modifier = Modifier.clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = item.itemName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = accentColor.copy(alpha = 0.25f)
            ) {
                Text(
                    text = when {
                        isReady -> "Ready"
                        isHighRisk -> "HIGH RISK"
                        else -> "Missing"
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

fun calculateCountdown(startTime: Long): String {
    if (startTime <= 0L) return "In 1h 20m"
    val now = System.currentTimeMillis()
    val diff = startTime - now
    if (diff <= 0) return "Now"
    val hours = diff / (1000 * 60 * 60)
    val minutes = (diff / (1000 * 60)) % 60
    return when {
        hours > 0 -> "In ${hours}h ${minutes}m"
        minutes > 0 -> "In ${minutes}m"
        else -> "Starting soon"
    }
}

fun formatEventTime(startTime: Long, endTime: Long): String {
    if (startTime <= 0L || endTime <= 0L) return "10:00 AM - 12:00 PM"
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return "${formatter.format(Date(startTime))} - ${formatter.format(Date(endTime))}"
}
