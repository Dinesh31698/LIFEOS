package com.lifeos.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.data.EdgeEntity
import com.lifeos.app.data.ItemHabitEntity
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.data.NodeEntity
import com.lifeos.app.engine.*

@Composable
fun LifeOsDashboardScreen(
    viewModel: MainViewModel,
    onEnableSync: () -> Unit
) {
    val modes by viewModel.allModes.collectAsState()
    val activeMode by viewModel.activeMode.collectAsState()
    val activeItems by viewModel.activeItems.collectAsState()
    val ghostItems by viewModel.ghostItems.collectAsState()
    val readiness by viewModel.readinessReport.collectAsState()
    val consequenceAlert by viewModel.consequenceAlert.collectAsState()
    val graphNodes by viewModel.graphNodes.collectAsState()
    val graphEdges by viewModel.graphEdges.collectAsState()

    // Stage 5 & 6 StateFlows
    val habits by viewModel.allHabits.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val powerProfile by viewModel.powerProfile.collectAsState()
    val detectedDevices by viewModel.detectedDevices.collectAsState()
    val replayState by viewModel.replayState.collectAsState()
    val isSafeModeEnabled by viewModel.isSafeModeEnabled.collectAsState()

    var selectedItemForEvidence by remember { mutableStateOf<ItemRequirementEntity?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showDevicesDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }

    val animatedProgress by animateFloatAsState(
        targetValue = readiness.percentage / 100f,
        animationSpec = tween(durationMillis = 600),
        label = "ReadinessAnimation"
    )

    val habitMap = remember(habits) { habits.associateBy { it.itemName.lowercase() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // --- Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LIFEOS",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Personal Intelligence & Readiness",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showPrivacyDialog = true },
                    border = BorderStroke(
                        1.dp,
                        if (isSafeModeEnabled) Color(0xFFFF9800) else Color(0xFF64B5F6)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isSafeModeEnabled) "🛡️ Safe Mode" else "🛡️ Privacy",
                        fontSize = 12.sp,
                        color = if (isSafeModeEnabled) Color(0xFFFFB74D) else Color(0xFF90CAF9),
                        fontWeight = FontWeight.Bold
                    )
                }
                FilledTonalButton(
                    onClick = onEnableSync,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Enable Sync", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Final Production Polish: System Health Badge & Release Readiness ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF162516), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIFEOS Operational | 100% Local-First",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFA5D6A7)
                )
            }
            Text(
                text = "V1.0 RELEASE READY",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF81C784)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Stage 6: System Hardware & Resource Status Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Battery Budget Chip
            val batteryColor = when (powerProfile) {
                PowerProfile.NORMAL -> Color(0xFF4CAF50)
                PowerProfile.SAVER -> Color(0xFFFF9800)
                PowerProfile.CRITICAL -> Color(0xFFF44336)
            }
            Box(
                modifier = Modifier
                    .background(batteryColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .clickable {
                        // Quick toggle battery simulation (85% -> 15% -> 5% -> 85%)
                        val next = when (batteryLevel) {
                            in 30..100 -> 15
                            in 10..29 -> 5
                            else -> 85
                        }
                        viewModel.setSimulatedBattery(next)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (powerProfile) {
                            PowerProfile.NORMAL -> "🔋 $batteryLevel% NORMAL"
                            PowerProfile.SAVER -> "🪫 $batteryLevel% SAVER"
                            PowerProfile.CRITICAL -> "⚠️ $batteryLevel% CRITICAL"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = batteryColor
                    )
                }
            }

            // BLE Device Presence Chip
            val highConfidenceCount = detectedDevices.count { it.confidence == PresenceConfidence.HIGH_CONFIDENCE }
            Box(
                modifier = Modifier
                    .background(Color(0xFF2196F3).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .clickable { showDevicesDialog = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "📡 BLE: $highConfidenceCount Nearby (Tap Details)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64B5F6)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Stage 7: Safe Mode Active Banner ---
        AnimatedVisibility(
            visible = isSafeModeEnabled,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                SafeModeActiveBanner(
                    onDeactivate = { viewModel.toggleSafeMode() },
                    onOpenSettings = { showPrivacyDialog = true }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // --- Stage 4: Consequence Alert Banner (Plan Mutation) ---
        AnimatedVisibility(
            visible = consequenceAlert != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            consequenceAlert?.let { alert ->
                ConsequenceAlertBanner(
                    alert = alert,
                    onDismiss = { viewModel.dismissConsequenceAlert() },
                    onReset = { activeMode?.let { viewModel.resetWaivedItems(it.id) } }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Life Mode Selector ---
        Text(
            text = "Active Life Mode",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { mode ->
                val isActive = mode.id == activeMode?.id
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isActive) Color(0xFF3F51B5) else Color(0xFF222222),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.selectMode(mode.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = mode.name,
                        color = Color.White,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Contextual Readiness Summary Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2640)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${readiness.modeName} Readiness",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (readiness.waivedCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF8E24AA), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${readiness.waivedCount} WAIVED",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = "${readiness.percentage}%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (readiness.percentage == 100) Color(0xFF81C784) else Color(0xFF64B5F6)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (readiness.percentage == 100) Color(0xFF4CAF50) else Color(0xFF64B5F6),
                    trackColor = Color(0xFF2C3856)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = readiness.summaryNotification,
                    fontSize = 13.sp,
                    color = Color(0xFFE0E0E0),
                    fontStyle = FontStyle.Italic
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Items and Ghost Mode Suggestions List ---
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Ghost Mode Section Header
            if (ghostItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Ghost Mode Suggestions (Unconfirmed)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB74D)
                    )
                }

                items(ghostItems, key = { "ghost_${it.id}" }) { ghostItem ->
                    GhostItemCard(
                        item = ghostItem,
                        onApply = { viewModel.applyGhostItem(ghostItem.id) },
                        onIgnore = { viewModel.ignoreGhostItem(ghostItem.id) },
                        onWhy = { selectedItemForEvidence = ghostItem }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Confirmed Required Items Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Required Items",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    TextButton(onClick = { showAddItemDialog = true }) {
                        Text("+ Add Item", color = Color(0xFF64B5F6))
                    }
                }
            }

            if (activeItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No items required for this mode.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(activeItems, key = { "active_${it.id}" }) { item ->
                    val habit = habitMap[item.itemName.lowercase()]
                    ActiveItemCard(
                        item = item,
                        habit = habit,
                        onToggle = { viewModel.toggleItemStatus(item) },
                        onWhy = { selectedItemForEvidence = item }
                    )
                }
            }

            // Stage 3 & 4: Personal Life Graph Card
            item {
                Spacer(modifier = Modifier.height(10.dp))
                PersonalLifeGraphCard(
                    nodes = graphNodes,
                    edges = graphEdges,
                    onSimulateCancel = { viewModel.simulateCancellation("DBMS Lab") },
                    onResetWaived = { activeMode?.let { viewModel.resetWaivedItems(it.id) } }
                )
            }

            // Stage 5 & 6: Dev Tools & Replay Simulation Card
            item {
                Spacer(modifier = Modifier.height(10.dp))
                DevToolsAndReplayCard(
                    replayState = replayState,
                    powerProfile = powerProfile,
                    batteryLevel = batteryLevel,
                    isSafeModeEnabled = isSafeModeEnabled,
                    onRunReplay = { viewModel.startReplaySimulation() },
                    onResetReplay = { viewModel.resetReplaySimulation() },
                    onSimulateForget = {
                        activeMode?.let { mode ->
                            viewModel.simulateForgetIncrement("DBMS Record", mode.id)
                        }
                    },
                    onToggleBattery = {
                        val next = when (batteryLevel) {
                            in 30..100 -> 15
                            in 10..29 -> 5
                            else -> 85
                        }
                        viewModel.setSimulatedBattery(next)
                    },
                    onToggleSafeMode = { viewModel.toggleSafeMode() },
                    onOpenPrivacy = { showPrivacyDialog = true }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // --- BLE Devices Modal Sheet / Dialog ---
    if (showDevicesDialog) {
        AlertDialog(
            onDismissRequest = { showDevicesDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(
                    text = "Tracked BLE Accessories",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Real-time probabilistic device presence via Bluetooth LE:",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    detectedDevices.forEach { device ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF262626)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = device.deviceName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                when (device.confidence) {
                                                    PresenceConfidence.HIGH_CONFIDENCE -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                                    PresenceConfidence.LOW_CONFIDENCE -> Color(0xFFFF9800).copy(alpha = 0.2f)
                                                    PresenceConfidence.UNKNOWN -> Color(0xFF9E9E9E).copy(alpha = 0.2f)
                                                },
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = device.confidence.name,
                                            fontSize = 9.sp,
                                            color = when (device.confidence) {
                                                PresenceConfidence.HIGH_CONFIDENCE -> Color(0xFF81C784)
                                                PresenceConfidence.LOW_CONFIDENCE -> Color(0xFFFFB74D)
                                                PresenceConfidence.UNKNOWN -> Color.LightGray
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = device.formattedMessage,
                                    fontSize = 12.sp,
                                    color = Color(0xFFE0E0E0)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevicesDialog = false }) {
                    Text("Close", color = Color(0xFF64B5F6))
                }
            }
        )
    }

    // --- Evidence Explainer Dialog ---
    selectedItemForEvidence?.let { item ->
        val habit = habitMap[item.itemName.lowercase()]
        EvidenceExplainerDialog(
            item = item,
            habit = habit,
            onDismiss = { selectedItemForEvidence = null }
        )
    }

    // --- Add Custom Item Dialog ---
    if (showAddItemDialog) {
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            containerColor = Color(0xFF222222),
            title = { Text("Add Custom Requirement", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    label = { Text("Item Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeMode?.let { mode ->
                            viewModel.addCustomItem(mode.id, newItemName)
                        }
                        newItemName = ""
                        showAddItemDialog = false
                    }
                ) {
                    Text("Add", color = Color(0xFF64B5F6))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // --- Stage 7: Privacy Dashboard Dialog ---
    if (showPrivacyDialog) {
        PrivacyDashboardDialog(
            viewModel = viewModel,
            onDismiss = { showPrivacyDialog = false }
        )
    }
}

@Composable
fun ConsequenceAlertBanner(
    alert: ConsequenceAlert,
    onDismiss: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1E22)),
        border = BorderStroke(1.5.dp, Color(0xFFE57373)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFD32F2F), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PLAN MUTATION",
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = alert.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFCDD2),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Dismiss", color = Color.LightGray, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = alert.description,
                fontSize = 13.sp,
                color = Color(0xFFEEEEEE)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    alert.affectedItem?.let { item ->
                        AssistChip(
                            onClick = {},
                            label = { Text("Affected: $item", fontSize = 11.sp, color = Color.White) }
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text("Action: ${alert.actionTaken}", fontSize = 11.sp, color = Color(0xFF81C784)) }
                    )
                }
                TextButton(onClick = onReset) {
                    Text("Undo Waive", color = Color(0xFF64B5F6), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SafeModeActiveBanner(
    onDeactivate: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1905)),
        border = BorderStroke(1.5.dp, Color(0xFFFF9800)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🛡️", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "SAFE MODE ACTIVE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFFFB74D)
                        )
                        Text(
                            text = "AI plan mutations & auto-waiving paused.",
                            fontSize = 11.sp,
                            color = Color(0xFFFFE0B2)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Firewall", fontSize = 11.sp, color = Color(0xFFFFB74D))
                    }
                    Button(
                        onClick = onDeactivate,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Resume", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveItemCard(
    item: ItemRequirementEntity,
    habit: ItemHabitEntity?,
    onToggle: () -> Unit,
    onWhy: () -> Unit
) {
    val isWaived = item.isWaived
    val isFrequentlyForgotten = habit != null && ForgettingModelEngine.isFrequentlyForgotten(habit)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isWaived) { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isWaived -> Color(0xFF1B1B1B)
                isFrequentlyForgotten -> Color(0xFF261D1A)
                else -> Color(0xFF1E1E1E)
            }
        ),
        border = when {
            isWaived -> BorderStroke(1.dp, Color(0xFF8E24AA).copy(alpha = 0.5f))
            isFrequentlyForgotten -> BorderStroke(1.5.dp, Color(0xFFFF7043))
            else -> null
        },
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isReady || isWaived,
                onCheckedChange = { if (!isWaived) onToggle() },
                enabled = !isWaived
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.itemName,
                        fontSize = 15.sp,
                        color = when {
                            isWaived -> Color(0xFFB388FF)
                            item.isReady -> Color.Gray
                            isFrequentlyForgotten -> Color(0xFFFFCCBC)
                            else -> Color.White
                        },
                        fontWeight = if (item.isReady || isWaived) FontWeight.Normal else FontWeight.Medium,
                        textDecoration = if (isWaived) TextDecoration.LineThrough else TextDecoration.None
                    )
                    if (isFrequentlyForgotten) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFF7043).copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color(0xFFFF7043))
                        ) {
                            Text(
                                text = "HIGH RISK",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFF7043),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (isWaived) {
                    Text(
                        text = "Waived: Cancelled schedule change",
                        fontSize = 11.sp,
                        color = Color(0xFFCE93D8),
                        fontStyle = FontStyle.Italic
                    )
                } else if (isFrequentlyForgotten) {
                    Text(
                        text = "⚠️ Habit Risk: Forgotten ${habit?.forgetCount ?: 0} times",
                        fontSize = 11.sp,
                        color = Color(0xFFFFAB91),
                        fontWeight = FontWeight.Bold
                    )
                } else if (item.sourceEvidence != null) {
                    Text(
                        text = "Confidence: ${(item.confidenceScore * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            if (isWaived) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF8E24AA), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("WAIVED", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(6.dp))
            } else if (isFrequentlyForgotten) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFD84315), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("HIGH RISK", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(6.dp))
            } else if (item.isTemporary) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFF9800), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("TEMP", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            TextButton(
                onClick = onWhy,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Why?", fontSize = 12.sp, color = Color(0xFF90CAF9))
            }
        }
    }
}

@Composable
fun GhostItemCard(
    item: ItemRequirementEntity,
    onApply: () -> Unit,
    onIgnore: () -> Unit,
    onWhy: () -> Unit
) {
    val isCancellation = item.itemName.contains("Cancellation", ignoreCase = true) || item.itemName.contains("Cancel", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.95f),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A241C)),
        border = BorderStroke(1.dp, Color(0xFFFFB74D)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFF9800).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("GHOST", fontSize = 10.sp, color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.itemName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = "${(item.confidenceScore * 100).toInt()}% confidence",
                    fontSize = 11.sp,
                    color = Color(0xFFFFCC80),
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Source Text / Evidence snippet
            if (!item.sourceEvidence.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\"${item.sourceEvidence}\"",
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = Color(0xFFE0E0E0),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // "What Might Change" Impact List
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E1A14),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "What Might Change:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB74D)
                    )
                    if (isCancellation) {
                        Text("• Associated event (DBMS Lab) will be cancelled", fontSize = 11.sp, color = Color.LightGray)
                        Text("• Dependent item (DBMS Record) will be waived", fontSize = 11.sp, color = Color.LightGray)
                        Text("• College readiness % will be recalculated", fontSize = 11.sp, color = Color.LightGray)
                    } else {
                        Text("• Item will be added to your active checklist", fontSize = 11.sp, color = Color.LightGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onWhy) {
                    Text("Why?", fontSize = 12.sp, color = Color(0xFF90CAF9))
                }
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedButton(
                    onClick = onIgnore,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, Color.Gray)
                ) {
                    Text("Ignore", fontSize = 12.sp, color = Color.LightGray)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onApply,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text(if (isCancellation) "Apply Change" else "Apply", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PersonalLifeGraphCard(
    nodes: List<NodeEntity>,
    edges: List<EdgeEntity>,
    onSimulateCancel: () -> Unit,
    onResetWaived: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181D28)),
        border = BorderStroke(1.dp, Color(0xFF2E3A52)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Personal Life Graph",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Relational Mapping Engine (${nodes.size} nodes, ${edges.size} edges)",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFF263238), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("GRAPH V5", fontSize = 10.sp, color = Color(0xFF80DEEA), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GraphRelationshipRow("College (PLACE)", "CONTAINS", "DBMS Lab (ACTIVITY)")
                GraphRelationshipRow("DBMS Lab (ACTIVITY)", "REQUIRES", "DBMS Record (ITEM)")
                GraphRelationshipRow("Gym (PLACE)", "CONTAINS", "Workout (ACTIVITY)")
                GraphRelationshipRow("Workout (ACTIVITY)", "REQUIRES", "Shaker Bottle (ITEM)")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onResetWaived,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, Color.Gray)
                ) {
                    Text("Reset Waived", fontSize = 11.sp, color = Color.LightGray)
                }
                Button(
                    onClick = onSimulateCancel,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Simulate 'Lab Cancelled'", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DevToolsAndReplayCard(
    replayState: ReplayState,
    powerProfile: PowerProfile,
    batteryLevel: Int,
    isSafeModeEnabled: Boolean,
    onRunReplay: () -> Unit,
    onResetReplay: () -> Unit,
    onSimulateForget: () -> Unit,
    onToggleBattery: () -> Unit,
    onToggleSafeMode: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222D)),
        border = BorderStroke(1.dp, Color(0xFF3F51B5)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Dev Tools & Scenario Replay",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Stage 5, 6 & 7 System Test Harness",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
                Box(
                    modifier = Modifier
                        .background(if (replayState.isRunning) Color(0xFF4CAF50) else Color(0xFF3949AB), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (replayState.isRunning) "RUNNING (${replayState.currentStep}/3)" else "READY",
                        fontSize = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Replay Status
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF151821), RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "Replay Engine Status:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF90CAF9)
                    )
                    Text(
                        text = replayState.statusText,
                        fontSize = 12.sp,
                        color = Color(0xFFEEEEEE),
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            if (replayState.history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    replayState.history.forEach { step ->
                        Text(
                            text = "✓ ${step.title}: ${step.detail}",
                            fontSize = 11.sp,
                            color = Color(0xFFA5D6A7)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRunReplay,
                    enabled = !replayState.isRunning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3949AB))
                ) {
                    Text("▶ Run 3-Stage Replay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onResetReplay,
                    enabled = !replayState.isRunning,
                    border = BorderStroke(1.dp, Color.Gray)
                ) {
                    Text("Reset", fontSize = 11.sp, color = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary dev actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onSimulateForget) {
                    Text("+1 Forget Count (DBMS)", fontSize = 11.sp, color = Color(0xFFFFAB91))
                }
                TextButton(onClick = onToggleBattery) {
                    Text("Cycle Battery ($batteryLevel%)", fontSize = 11.sp, color = Color(0xFF81D4FA))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Stage 7: Privacy & Safe Mode Quick Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleSafeMode,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (isSafeModeEnabled) Color(0xFFFF9800) else Color(0xFF3F51B5)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isSafeModeEnabled) "🛡️ Safe Mode ON" else "🛡️ Safe Mode OFF",
                        fontSize = 11.sp,
                        color = if (isSafeModeEnabled) Color(0xFFFFB74D) else Color(0xFF90CAF9),
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onOpenPrivacy,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color(0xFF64B5F6)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("⚙️ Privacy Gate", fontSize = 11.sp, color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun GraphRelationshipRow(from: String, relation: String, to: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF131722), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = from, fontSize = 11.sp, color = Color(0xFF90CAF9), fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .background(Color(0xFF37474F), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(text = relation, fontSize = 9.sp, color = Color(0xFFFFCC80), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "➔ $to", fontSize = 11.sp, color = Color(0xFFA5D6A7), fontWeight = FontWeight.Medium)
    }
}

@Composable
fun EvidenceExplainerDialog(
    item: ItemRequirementEntity,
    habit: ItemHabitEntity?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        title = {
            Text(
                text = "Why did LIFEOS suggest this?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Item: ${item.itemName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF90CAF9)
                )
                if (item.isWaived) {
                    Text(
                        text = "Status: WAIVED BY CONSEQUENCE ENGINE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFBA68C8)
                    )
                }

                // Stage 5 Habit Intelligence Details
                if (habit != null) {
                    val score = ForgettingModelEngine.calculateForgetfulnessScore(habit.forgetCount, habit.confirmCount)
                    val riskLevel = ForgettingModelEngine.getRiskLevel(habit.forgetCount)
                    val isHighRisk = riskLevel == RiskLevel.HIGH_RISK

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isHighRisk) Color(0xFF2E1C14) else Color(0xFF262626)
                        ),
                        border = if (isHighRisk) BorderStroke(1.dp, Color(0xFFFF7043)) else null,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Personal Habit & Forgetting Model:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFFFB74D)
                                )
                                if (isHighRisk) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFFF7043).copy(alpha = 0.25f),
                                        border = BorderStroke(1.dp, Color(0xFFFF7043))
                                    ) {
                                        Text(
                                            text = "HIGH RISK",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFFF7043),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "• Forgetfulness Score: $score (Risk: $riskLevel)\n" +
                                        "• Forgotten in past: ${habit.forgetCount} times\n" +
                                        "• Confirmed packed: ${habit.confirmCount} times\n" +
                                        "• Priority escalation boost: +${habit.priorityBoost}",
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                Text(
                    text = "Confidence Level: ${(item.confidenceScore * 100).toInt()}%",
                    fontSize = 14.sp,
                    color = if (item.confidenceScore >= 0.85f) Color(0xFF81C784) else Color(0xFFFFB74D)
                )
                Text(
                    text = "Source Evidence:\n${item.sourceEvidence ?: "Extracted from notification / test payload"}",
                    fontSize = 13.sp,
                    color = Color.LightGray
                )
                if (item.ttlTimestamp > 0) {
                    val remainingHours = ((item.ttlTimestamp - System.currentTimeMillis()) / (1000 * 3600)).coerceAtLeast(0)
                    Text(
                        text = "Time-To-Live (TTL): Expires in ~${remainingHours}h",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It", color = Color(0xFF64B5F6))
            }
        }
    )
}
