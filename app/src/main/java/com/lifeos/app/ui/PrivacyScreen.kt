package com.lifeos.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lifeos.app.MainViewModel
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.PermissionStatus
import com.lifeos.app.security.SourceType
import com.lifeos.app.ui.theme.*

/**
 * LIFEOS Phase 7: Central Privacy Dashboard.
 *
 * Provides complete transparency into:
 *  1. Autonomous Action Kill-Switch (Safe Mode)
 *  2. Hardware Gates & Permission Firewall (All permissions used by LIFEOS)
 *  3. Stored Data Categories & Exact Entity Counts (Room DB Transparency)
 *  4. Data Minimization & Hygiene (PII Stripping & Evidence Purging)
 *  5. Data Export (100% Local Structured JSON Export)
 *  6. Database Integrity Verification (PRAGMA integrity_check)
 *  7. Complete Data Deletion & Factory Reset
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isSafeModeEnabled by viewModel.isSafeModeEnabled.collectAsState()
    val channelToggles by viewModel.channelToggles.collectAsState()

    // Real-time Room DB entity counts for complete Data Transparency
    val allModes by viewModel.allModes.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()
    val latestEvidence by viewModel.latestEvidence.collectAsState()
    val habits by viewModel.modeHabits.collectAsState()

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var integrityStatus by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "🛡️ Privacy Dashboard",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "100% On-Device • Zero Cloud Telemetry",
                            fontSize = 11.sp,
                            color = Color(0xFF81C784),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
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
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp)
        ) {
            // Status Feedback Banner
            if (statusMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = statusMessage ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFFA5D6A7),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { statusMessage = null },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.LightGray)
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // SECTION 1: AUTONOMOUS ACTION KILL-SWITCH (SAFE MODE)
            // =========================================================================
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSafeModeEnabled) Color(0xFF332010) else SurfaceDark
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSafeModeEnabled) Color(0xFFFF9800) else LifeOsBorder
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSafeModeEnabled) Color(0xFFFF9800).copy(alpha = 0.2f)
                                            else Color(0xFF757575).copy(alpha = 0.2f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = if (isSafeModeEnabled) Color(0xFFFF9800) else Color.LightGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Emergency Safe Mode",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isSafeModeEnabled) Color(0xFFFFB74D) else Color.White
                                    )
                                    Text(
                                        text = if (isSafeModeEnabled) "ACTIVE — Mutations Blocked" else "STANDARD — Autonomous Operations",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSafeModeEnabled) Color(0xFFFF9800) else LifeOsTextMuted
                                    )
                                }
                            }
                            Switch(
                                checked = isSafeModeEnabled,
                                onCheckedChange = {
                                    viewModel.toggleSafeMode()
                                    statusMessage = if (!isSafeModeEnabled) {
                                        "Safe Mode Engaged: Autonomous plan mutations, item waiving & context auto-switching halted."
                                    } else {
                                        "Safe Mode Disengaged: Standard autonomous operations resumed."
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFF9800),
                                    checkedTrackColor = Color(0xFFE65100)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "When active, LIFEOS blocks autonomous plan alterations, suppresses automatic checklist waiving, forces context auto-switching into proposals only, and routes all incoming notices to Ghost Mode quarantine.",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // =========================================================================
            // SECTION 2: PERMISSION AUDIT & DATA INGESTION FIREWALL
            // =========================================================================
            item {
                Text(
                    text = "PERMISSION AUDIT & HARDWARE FIREWALL",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF90CAF9)
                )
            }

            // Permission 1: Notification Ingestion
            item {
                PermissionAuditCard(
                    title = "Notification Ingestion (Listener Service)",
                    purpose = "Parses schedule changes, room updates, and required items from university notices.",
                    dependency = "Automated timetable adjustments and Ghost Mode proposal generation.",
                    howToDisable = "Toggle switch below to block at app firewall, or disable in Android System Settings > Notification Access.",
                    status = PermissionFirewall.getChannelStatus(context, SourceType.NOTIFICATION_INGESTION),
                    isEnabled = channelToggles[SourceType.NOTIFICATION_INGESTION] ?: true,
                    onToggle = { enabled ->
                        viewModel.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, enabled)
                        statusMessage = "Notification Ingestion channel set to ${if (enabled) "ENABLED" else "BLOCKED"}."
                    }
                )
            }

            // Permission 2: Post Notifications
            item {
                val hasPostPerm = PermissionFirewall.hasPostNotificationsPermission(context)
                PermissionAuditCard(
                    title = "Notification Alerts (POST_NOTIFICATIONS)",
                    purpose = "Delivers morning readiness briefings, departure alerts, and conflict notifications.",
                    dependency = "Departure reminders and anti-spam notification dispatch.",
                    howToDisable = "Adjust in Android System Settings > Apps > LIFEOS > Notifications.",
                    status = if (hasPostPerm) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                    isEnabled = hasPostPerm,
                    isOsControlled = true
                )
            }

            // Permission 3: Location & Geofencing
            item {
                PermissionAuditCard(
                    title = "Location & Geofencing (ACCESS_FINE_LOCATION)",
                    purpose = "Monitors boundary transitions for College, Gym, and Home geofences.",
                    dependency = "Autonomous Life Mode auto-switching and departure readiness checks.",
                    howToDisable = "Toggle switch below to block at firewall, or revoke in Android Settings > Apps > LIFEOS > Permissions > Location.",
                    status = PermissionFirewall.getChannelStatus(context, SourceType.LOCATION),
                    isEnabled = channelToggles[SourceType.LOCATION] ?: true,
                    onToggle = { enabled ->
                        viewModel.setChannelAuthorized(SourceType.LOCATION, enabled)
                        statusMessage = "Location channel set to ${if (enabled) "ENABLED" else "BLOCKED"}."
                    }
                )
            }

            // Permission 4: BLE Accessory Scanning
            item {
                PermissionAuditCard(
                    title = "BLE Accessory Scanning (BLUETOOTH_SCAN)",
                    purpose = "Performs probabilistic proximity scans for Laptop Beacon, Smartwatch, and Headphones.",
                    dependency = "Automatic item verification via Bluetooth beacon presence.",
                    howToDisable = "Toggle switch below to block at firewall, or revoke in Android Settings > Apps > LIFEOS > Nearby Devices.",
                    status = PermissionFirewall.getChannelStatus(context, SourceType.BLE_SCANNING),
                    isEnabled = channelToggles[SourceType.BLE_SCANNING] ?: false,
                    onToggle = { enabled ->
                        viewModel.setChannelAuthorized(SourceType.BLE_SCANNING, enabled)
                        statusMessage = "BLE Scanning channel set to ${if (enabled) "ENABLED" else "BLOCKED"}."
                    }
                )
            }

            // Permission 5: Calendar Sync
            item {
                PermissionAuditCard(
                    title = "Academic Calendar Integration",
                    purpose = "Fuses lecture schedules and lab timings into the local contextual readiness model.",
                    dependency = "Time-of-day contextual mode inference.",
                    howToDisable = "Toggle switch below to disable local calendar integration.",
                    status = PermissionFirewall.getChannelStatus(context, SourceType.CALENDAR_SYNC),
                    isEnabled = channelToggles[SourceType.CALENDAR_SYNC] ?: false,
                    onToggle = { enabled ->
                        viewModel.setChannelAuthorized(SourceType.CALENDAR_SYNC, enabled)
                        statusMessage = "Calendar Sync channel set to ${if (enabled) "ENABLED" else "BLOCKED"}."
                    }
                )
            }

            // =========================================================================
            // SECTION 3: DATA TRANSPARENCY (WHAT LIFEOS STORES LOCALLY)
            // =========================================================================
            item {
                Text(
                    text = "DATA TRANSPARENCY (STORED LOCALLY IN ROOM DB)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF90CAF9)
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, LifeOsBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Stored Data Breakdown",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = "LIFEOS stores structured operational state in an encrypted SQLite database on your physical device. Zero data is sent to the cloud.",
                            fontSize = 11.sp,
                            color = LifeOsTextSecondary
                        )

                        HorizontalDivider(color = LifeOsBorder)

                        DataTransparencyRow("Life Modes (Categories)", "${allModes.size} configured", "User-defined activity modes (College, Gym, Travel, etc.)")
                        DataTransparencyRow("Checklist Items", "${allItems.size} items", "Required equipment, readiness states, and verification flags")
                        DataTransparencyRow("Scheduled Events", "${allEvents.size} events", "Academic timetable slots, lab sessions, and room numbers")
                        DataTransparencyRow("Habit Learning Records", "${habits.size} tracked", "Forgetting frequency scores and dynamic prioritization boosts")
                        DataTransparencyRow("Evidence Logs (Sanitized)", "${latestEvidence.size} records", "Audit trail of processed notices with all PII scrubbed")
                        DataTransparencyRow("Preferences & Safe Mode", "Local only", "Onboarding flags and Safe Mode toggle in private SharedPreferences")
                    }
                }
            }

            // =========================================================================
            // SECTION 4: DATA MINIMIZATION & HYGIENE
            // =========================================================================
            item {
                Text(
                    text = "DATA MINIMIZATION & HYGIENE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF90CAF9)
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, LifeOsBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Automated PII Scrubbing",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Phone numbers, emails, URLs, OTPs, personal names, and conversational fillers are automatically stripped by DataMinimizer before persistent storage.",
                            fontSize = 11.sp,
                            color = LifeOsTextSecondary,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.pruneEvidenceLogs()
                                    statusMessage = "All historical evidence records redacted and sanitized under Data Minimization standard."
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF64B5F6))
                            ) {
                                Text("Sanitize Logs", fontSize = 11.sp, color = Color(0xFF90CAF9))
                            }

                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.clearEvidenceLogs()
                                    statusMessage = "All historical evidence records permanently purged."
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Purge Evidence", fontSize = 11.sp, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.clearTemporaryItems { count ->
                                    statusMessage = "Cleared $count temporary items from database."
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, LifeOsBorder)
                        ) {
                            Text("Clear Temporary Ingested Items", fontSize = 11.sp, color = LifeOsTextSecondary)
                        }
                    }
                }
            }

            // =========================================================================
            // SECTION 5: DATA CONTROL & LOCAL EXPORT
            // =========================================================================
            item {
                Text(
                    text = "DATA CONTROL & INTEGRITY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF90CAF9)
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, LifeOsBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Structured Local Export",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Export a clean, transparent JSON document of your entire local dataset without connecting to any external cloud or server.",
                            fontSize = 11.sp,
                            color = LifeOsTextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.exportLocalData { json ->
                                        showExportDialog = json
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export JSON", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.checkDatabaseIntegrity(context) { result ->
                                        integrityStatus = result
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF00CEC9))
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00CEC9), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check DB", fontSize = 12.sp, color = Color(0xFF00CEC9))
                            }
                        }

                        if (integrityStatus != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF00B894).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "SQLite Integrity: $integrityStatus",
                                    fontSize = 11.sp,
                                    color = Color(0xFF55EFC4),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // SECTION 6: DANGER ZONE — DATA DELETION
            // =========================================================================
            item {
                Text(
                    text = "DANGER ZONE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF5252)
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1515)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Clear All LIFEOS Data (Factory Reset)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFFF5252)
                        )
                        Text(
                            text = "Permanently wipes all user-generated modes, checklist items, events, habits, and evidence logs. Reinitializes clean factory default state without leaving orphaned records.",
                            fontSize = 11.sp,
                            color = Color(0xFFFFCDD2),
                            lineHeight = 15.sp
                        )

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear All Data & Reset", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog: Local JSON Export Viewer
    if (showExportDialog != null) {
        val exportJson = showExportDialog ?: ""
        AlertDialog(
            onDismissRequest = { showExportDialog = null },
            containerColor = SurfaceDark,
            title = {
                Text("Local Structured JSON Export", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "100% On-Device structured representation (${exportJson.length} bytes):",
                        fontSize = 12.sp,
                        color = LifeOsTextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = exportJson.take(1500) + if (exportJson.length > 1500) "\n\n... (truncated for preview)" else "",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF81C784)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("LIFEOS Export", exportJson)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Export copied to clipboard", Toast.LENGTH_SHORT).show()
                        showExportDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExportDialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Modal Dialog: Factory Reset Confirmation
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = SurfaceDark,
            title = {
                Text("Confirm Factory Reset", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all user-created data? This will purge all customs modes, checklist items, timetable events, and evidence logs, restoring clean factory defaults.",
                    fontSize = 13.sp,
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.resetToFactoryDefaults {
                            statusMessage = "All user data cleared and factory default state restored. Database integrity verified."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Yes, Clear Everything")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PermissionAuditCard(
    title: String,
    purpose: String,
    dependency: String,
    howToDisable: String,
    status: PermissionStatus,
    isEnabled: Boolean,
    isOsControlled: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, LifeOsBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                if (!isOsControlled && onToggle != null) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00CEC9),
                            uncheckedThumbColor = LifeOsTextSecondary,
                            uncheckedTrackColor = LifeOsSurfaceHighlight
                        )
                    )
                }
            }

            // Status Badge
            val (badgeBg, badgeTextColor) = when (status) {
                PermissionStatus.GRANTED -> Color(0xFF4CAF50).copy(alpha = 0.2f) to Color(0xFF81C784)
                PermissionStatus.DENIED -> Color(0xFFFF9800).copy(alpha = 0.2f) to Color(0xFFFFB74D)
                PermissionStatus.DISABLED_BY_USER -> Color(0xFF757575).copy(alpha = 0.2f) to Color.LightGray
            }

            Box(
                modifier = Modifier
                    .background(badgeBg, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Status: ${status.label}",
                    fontSize = 10.sp,
                    color = badgeTextColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "• Purpose: $purpose",
                fontSize = 11.sp,
                color = LifeOsTextSecondary
            )
            Text(
                text = "• Dependent Features: $dependency",
                fontSize = 11.sp,
                color = LifeOsTextSecondary
            )
            Text(
                text = "• How to disable: $howToDisable",
                fontSize = 11.sp,
                color = Color(0xFF90CAF9)
            )
        }
    }
}

@Composable
private fun DataTransparencyRow(
    category: String,
    count: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = category, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(text = description, fontSize = 10.sp, color = LifeOsTextMuted)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = count, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784))
    }
}

/**
 * Backwards-compatible Dialog representation of the Privacy Gate.
 */
@Composable
fun PrivacyDashboardDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = BackgroundDark
        ) {
            PrivacyScreen(
                viewModel = viewModel,
                onNavigateBack = onDismiss
            )
        }
    }
}
