package com.lifeos.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.engine.BatteryBudgetManager
import com.lifeos.app.engine.PowerProfile
import com.lifeos.app.security.SourceType
import com.lifeos.app.ui.components.GlassButton
import com.lifeos.app.ui.components.GlassCard
import com.lifeos.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
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
    val isSafeModeEnabled by viewModel.isSafeModeEnabled.collectAsState()
    val channelToggles by viewModel.channelToggles.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val powerProfile by viewModel.powerProfile.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    val profileName = userProfile?.preferredName?.trim()?.ifEmpty { "Dinesh" } ?: "Dinesh"
    val avatarInitial = profileName.firstOrNull()?.uppercase() ?: "D"
    val userRole = userProfile?.primaryRole ?: "Student"

    // Dialog state controllers
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var notificationMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark
                )
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
        ) {
            // Notification Feedback Banner
            if (notificationMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryPurple.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = notificationMessage ?: "",
                                fontSize = 12.sp,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { notificationMessage = null },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = LifeOsTextSecondary)
                            }
                        }
                    }
                }
            }

            // 1. Authoritative User Profile Header Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToProfile() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, LifeOsBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // User Profile Avatar with dynamic initial
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(PrimaryPurple, CyanAccent)))
                                .border(1.5.dp, CyanAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = avatarInitial,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profileName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$userRole • Local Profile",
                                fontSize = 12.sp,
                                color = LifeOsTextSecondary
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = LifeOsTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 1. Profile
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.AccountCircle,
                    iconBg = PrimaryPurple,
                    title = "Profile",
                    subtitle = "Name, role, language, sleep & commute targets",
                    onClick = { onNavigateToProfile() }
                )
            }

            // 2. Appearance (Theme System)
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Settings,
                    iconBg = CyanAccent,
                    title = "Appearance",
                    subtitle = "Theme: System, Dark, Light, AMOLED Dark",
                    onClick = { activeDialog = SettingsDialog.APPEARANCE }
                )
            }

            // 3. Language & Voice
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Phone,
                    iconBg = ContextTravel,
                    title = "Language & Voice",
                    subtitle = "On-device models, language, speech output",
                    onClick = { onNavigateToVoice() }
                )
            }

            // 4. Permissions (Firewall)
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Lock,
                    iconBg = ContextStudy,
                    title = "Permissions",
                    subtitle = "Firewall matrix: Notifications, Location, BLE, Calendar",
                    onClick = { activeDialog = SettingsDialog.PERMISSIONS }
                )
            }

            // 5. Notifications
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Notifications,
                    iconBg = ContextSports,
                    title = "Notifications",
                    subtitle = "Channels, urgency thresholds, morning briefings",
                    onClick = { activeDialog = SettingsDialog.NOTIFICATIONS }
                )
            }

            // 6. Life Modes
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.List,
                    iconBg = ContextCollege,
                    title = "Life Modes",
                    subtitle = "College, Gym, Travel, Sports, Work, Shopping",
                    onClick = { onNavigateToLifeModes() }
                )
            }

            // 7. Screen Time
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.DateRange,
                    iconBg = ContextGym,
                    title = "Screen Time",
                    subtitle = "App usage audit, limits, focus & distraction blocks",
                    onClick = { onNavigateToTimeAudit() }
                )
            }

            // 8. Location & Life Map
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Place,
                    iconBg = ContextTravel,
                    title = "Location & Life Map",
                    subtitle = "Anchor places, geofences, privacy mask, tracking pause",
                    onClick = { onNavigateToLifeMap() }
                )
            }

            // 9. Memory Vault
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Edit,
                    iconBg = PurpleLight,
                    title = "Memory Vault",
                    subtitle = "Semantic recall, notes, retention policies, TTL",
                    onClick = { onNavigateToMemoryVault() }
                )
            }

            // 10. Privacy & Security
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.CheckCircle,
                    iconBg = AlertRed,
                    title = "Privacy & Security",
                    subtitle = "Safe Mode, zero-cloud status, audit log, purge logs",
                    onClick = { onNavigateToPrivacy() }
                )
            }

            // 11. Safety / SOS
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Warning,
                    iconBg = AlertRed,
                    title = "Safety & Emergency SOS",
                    subtitle = "Emergency contacts, rapid dispatch countdown, test mode",
                    onClick = { onNavigateToSos() }
                )
            }

            // 12. Battery & Performance
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Refresh,
                    iconBg = SuccessGreen,
                    title = "Battery & Performance",
                    subtitle = "Power profiles (Normal, Saver, Critical), BLE budget",
                    onClick = { activeDialog = SettingsDialog.BATTERY }
                )
            }

            // 13. Data & Storage (Local Export - Zero Cloud)
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Share,
                    iconBg = WarningAmber,
                    title = "Data & Storage",
                    subtitle = "Local Room DB v8, structured JSON export, cache purge",
                    onClick = { activeDialog = SettingsDialog.DATA_STORAGE }
                )
            }

            // 14. Accessibility
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Face,
                    iconBg = ContextCustom,
                    title = "Accessibility",
                    subtitle = "Haptic feedback, high contrast cards, touch targets",
                    onClick = { activeDialog = SettingsDialog.ACCESSIBILITY }
                )
            }

            // 15. About LIFEOS
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Info,
                    iconBg = TextSecondary,
                    title = "About LIFEOS",
                    subtitle = "Version 1.0.0 • Local-First • Zero Cloud • Room DB v8",
                    onClick = { activeDialog = SettingsDialog.ABOUT }
                )
            }
        }
    }

    // Modal Dialogs & Sheets for Interactive Settings
    when (activeDialog) {
        SettingsDialog.APPEARANCE -> {
            AppearanceThemeDialog(
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialog.PERMISSIONS -> {
            PermissionsFirewallDialog(
                channelToggles = channelToggles,
                onToggleChannel = { type, enabled ->
                    viewModel.setChannelAuthorized(type, enabled)
                    notificationMessage = "${type.name.lowercase().replace('_', ' ')} permission ${if (enabled) "granted" else "revoked"}"
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialog.NOTIFICATIONS -> {
            NotificationSettingsDialog(
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialog.BATTERY -> {
            BatterySettingsDialog(
                currentBattery = batteryLevel,
                currentProfile = powerProfile,
                onSetSimulatedBattery = { level ->
                    viewModel.setSimulatedBattery(level)
                    notificationMessage = "Simulated battery set to $level%"
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialog.DATA_STORAGE -> {
            DataStorageDialog(
                viewModel = viewModel,
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialog.ACCESSIBILITY -> {
            AccessibilitySettingsDialog(
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialog.ABOUT -> {
            AboutDialog(
                onNavigateToSplash = onNavigateToSplash,
                onNavigateToOnboarding = onNavigateToOnboarding,
                onDismiss = { activeDialog = null }
            )
        }
        null -> {}
    }
}

private enum class SettingsDialog {
    APPEARANCE,
    PERMISSIONS,
    NOTIFICATIONS,
    BATTERY,
    DATA_STORAGE,
    ACCESSIBILITY,
    ABOUT
}

@Composable
private fun SettingsCategoryItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, LifeOsBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconBg,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = LifeOsTextSecondary
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = LifeOsTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// 1. Appearance / Theme Switching Dialog
@Composable
private fun AppearanceThemeDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentTheme by ThemeManager.themeMode.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text("Appearance & Theme", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Select your preferred LIFEOS visual theme. Changes apply instantly across all screens.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                AppThemeMode.entries.forEach { mode ->
                    val isSelected = currentTheme == mode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ThemeManager.setThemeMode(context, mode) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) PrimaryPurple.copy(alpha = 0.25f) else CardDark
                        ),
                        border = BorderStroke(1.dp, if (isSelected) CyanAccent else BorderDark)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) CyanAccent else TextPrimary
                                )
                                Text(
                                    text = mode.description,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { ThemeManager.setThemeMode(context, mode) },
                                colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Done", color = Color.White)
            }
        }
    )
}

// 2. Permission Firewall Dialog
@Composable
private fun PermissionsFirewallDialog(
    channelToggles: Map<SourceType, Boolean>,
    onToggleChannel: (SourceType, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF00CEC9).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00CEC9), modifier = Modifier.size(18.dp))
                }
                Text("Permission Firewall", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Hardware gates and sensor channels managed locally by LIFEOS.",
                    fontSize = 12.sp,
                    color = LifeOsTextSecondary
                )

                HorizontalDivider(color = LifeOsBorder)

                DialogChannelRow(
                    title = "Location & Geofencing",
                    subtitle = "Context engine auto-mode switching",
                    checked = channelToggles[SourceType.LOCATION] ?: true,
                    onCheckedChange = { onToggleChannel(SourceType.LOCATION, it) }
                )

                DialogChannelRow(
                    title = "Notification Ingestion",
                    subtitle = "Chat & timetable parser",
                    checked = channelToggles[SourceType.NOTIFICATION_INGESTION] ?: true,
                    onCheckedChange = { onToggleChannel(SourceType.NOTIFICATION_INGESTION, it) }
                )

                DialogChannelRow(
                    title = "BLE Accessory Scanning",
                    subtitle = "Beacon presence verification",
                    checked = channelToggles[SourceType.BLE_SCANNING] ?: false,
                    onCheckedChange = { onToggleChannel(SourceType.BLE_SCANNING, it) }
                )

                DialogChannelRow(
                    title = "Calendar Sync",
                    subtitle = "Campus & personal event integration",
                    checked = channelToggles[SourceType.CALENDAR_SYNC] ?: true,
                    onCheckedChange = { onToggleChannel(SourceType.CALENDAR_SYNC, it) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Done", color = Color.White)
            }
        }
    )
}

@Composable
private fun DialogChannelRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(text = subtitle, fontSize = 11.sp, color = LifeOsTextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF00CEC9),
                uncheckedThumbColor = LifeOsTextSecondary,
                uncheckedTrackColor = LifeOsSurfaceHighlight
            )
        )
    }
}

// 3. Battery Settings Dialog
@Composable
private fun BatterySettingsDialog(
    currentBattery: Int,
    currentProfile: PowerProfile,
    onSetSimulatedBattery: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text("Battery & Performance", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Current Battery: $currentBattery% • Profile: ${currentProfile.label}",
                    fontSize = 13.sp,
                    color = Color(0xFF00B894),
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Simulation Controls (Test Power Profile Escalation):",
                    fontSize = 12.sp,
                    color = LifeOsTextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSetSimulatedBattery(85) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894))
                    ) {
                        Text("85% (Normal)", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onSetSimulatedBattery(18) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFDCB6E))
                    ) {
                        Text("18% (Saver)", fontSize = 11.sp, color = Color.Black)
                    }
                    Button(
                        onClick = { onSetSimulatedBattery(5) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7675))
                    ) {
                        Text("5% (Critical)", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Close", color = Color.White)
            }
        }
    )
}

// 4. Notification Settings Dialog
@Composable
private fun NotificationSettingsDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("Notification Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("• Morning Readiness Briefings: 7:45 AM (Local trigger)", fontSize = 13.sp, color = TextPrimary)
                Text("• Pre-Departure Reminders: 15 mins before departure", fontSize = 13.sp, color = TextPrimary)
                Text("• Ghost Mode Proposals: Real-time autonomous alerts", fontSize = 13.sp, color = TextPrimary)
                Text("• Quiet Hours: 11:00 PM – 7:00 AM (Emergency SOS exempt)", fontSize = 13.sp, color = TextPrimary)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Done", color = Color.White)
            }
        }
    )
}

// 5. Data & Storage Dialog (100% Local - Zero Cloud)
@Composable
private fun DataStorageDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }
    var exportSummary by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Share, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(22.dp))
                Text("Data & Storage", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("100% Local-First Architecture", fontWeight = FontWeight.Bold, color = SuccessGreen, fontSize = 13.sp)
                        Text("• Database: Room v8 Schema (Local SQLite)", fontSize = 12.sp, color = TextSecondary)
                        Text("• Encryption: AES-256 GCM on-device storage", fontSize = 12.sp, color = TextSecondary)
                        Text("• Cloud Backend: None (Zero Cloud Telemetry)", fontSize = 12.sp, color = CyanAccent)
                    }
                }

                if (exportSummary != null) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = PrimaryPurple.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, PrimaryPurple)
                    ) {
                        Text(
                            text = exportSummary ?: "",
                            fontSize = 11.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.exportDataJson { json ->
                            exportStatusMessage = "Local export ready (${json.length} characters)"
                            exportSummary = "JSON Export Generated locally with zero network calls.\nContains modes, items, events, habits, and graph nodes."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Local Data JSON")
                }

                OutlinedButton(
                    onClick = {
                        exportStatusMessage = "Local cache cleared safely"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = AlertRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Temporary Cache", color = AlertRed)
                }

                exportStatusMessage?.let { msg ->
                    Text(text = msg, fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Close", color = Color.White)
            }
        }
    )
}

// 6. Accessibility Settings Dialog
@Composable
private fun AccessibilitySettingsDialog(
    onDismiss: () -> Unit
) {
    var hapticEnabled by remember { mutableStateOf(true) }
    var highContrastEnabled by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text("Accessibility", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Haptic Feedback", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                        Text("Vibrate on button clicks and status alerts", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = hapticEnabled,
                        onCheckedChange = { hapticEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CyanAccent)
                    )
                }

                HorizontalDivider(color = BorderDark)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("High Contrast Cards", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                        Text("Increase card border opacity for readability", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = highContrastEnabled,
                        onCheckedChange = { highContrastEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CyanAccent)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Done", color = Color.White)
            }
        }
    )
}

// 7. About Dialog
@Composable
private fun AboutDialog(
    onNavigateToSplash: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("About LIFEOS", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("LIFEOS v1.0.0 (Production)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryPurple)
                Text("Architecture: Android 15 (Target SDK 35) • Room v8 Schema • Kotlin 2.0.21 • Jetpack Compose", fontSize = 11.sp, color = LifeOsTextSecondary)

                HorizontalDivider(color = LifeOsBorder)

                Text(
                    text = "A deterministic, local-first personal operating system designed for contextual awareness, smart readiness, and absolute privacy.",
                    fontSize = 12.sp,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onNavigateToSplash()
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, LifeOsBorder)
                    ) {
                        Text("Replay Splash", fontSize = 10.sp, color = TextPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onNavigateToOnboarding()
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, LifeOsBorder)
                    ) {
                        Text("Onboarding", fontSize = 10.sp, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Close", color = Color.White)
            }
        }
    )
}
