package com.lifeos.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.ui.theme.*

/**
 * Sprint 4: Mode Detail Screen (Screen 7 in wireframe)
 */
@Composable
fun ModeDetailScreen(
    viewModel: MainViewModel,
    mode: LifeModeEntity,
    onNavigateBack: () -> Unit
) {
    val activeMode by viewModel.activeMode.collectAsState()
    val allItems by viewModel.allItems.collectAsState()

    val isActive = activeMode?.id == mode.id
    val modeItems = remember(allItems, mode.id) { allItems.filter { it.modeId == mode.id } }
    val readyItems = remember(modeItems) { modeItems.filter { it.isReady } }
    val missingItems = remember(modeItems) { modeItems.filter { !it.isReady && !it.isWaived } }

    val readinessPercentage = remember(modeItems, readyItems) {
        if (modeItems.isEmpty()) 100 else ((readyItems.size.toFloat() / modeItems.size.toFloat()) * 100).toInt()
    }
    val animatedProgress by animateFloatAsState(
        targetValue = readinessPercentage / 100f,
        label = "modeReadinessProgress"
    )

    // Sub-Tabs: 0 = Overview, 1 = Items, 2 = Routines, 3 = Settings
    var selectedSubTab by remember { mutableStateOf(0) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ItemRequirementEntity?>(null) }

    val modeConfig = getModeConfig(mode.name)
    val modeRoutines = remember(mode.id) {
        DEFAULT_MODE_ROUTINES.filter { it.modeId == mode.id }.ifEmpty {
            listOf(
                ModeRoutineSpec(99, mode.id, "${mode.name} Proximity Check", "Geofence Exit", "Audits required items on departure"),
                ModeRoutineSpec(100, mode.id, "${mode.name} Arrival Routine", "Location Proximity", "Prepares active contextual tools")
            )
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SurfaceDark)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "${mode.name} Mode",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                // Edit Button on Top Right
                TextButton(
                    onClick = { showEditDialog = true }
                ) {
                    Text(
                        text = "Edit",
                        color = CyanAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // 1. Horizontal Sub-Tab Navigation Bar: Overview, Items, Routines, Settings
            val tabs = listOf("Overview", "Items", "Routines", "Settings")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedSubTab == index
                    Column(
                        modifier = Modifier
                            .clickable { selectedSubTab = index }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) PrimaryPurple else TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .height(2.5.dp)
                                .width(if (isSelected) 36.dp else 0.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSelected) PrimaryPurple else Color.Transparent)
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderDark, thickness = 1.dp)

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-View Routing
            when (selectedSubTab) {
                0 -> OverviewSubView(
                    mode = mode,
                    modeConfig = modeConfig,
                    readinessPercentage = readinessPercentage,
                    animatedProgress = animatedProgress,
                    modeItems = modeItems,
                    onItemToggle = { item -> viewModel.toggleItemStatus(item) },
                    onOpenAddDialog = { showAddItemDialog = true }
                )
                1 -> ItemsSubView(
                    modeItems = modeItems,
                    onItemToggle = { item -> viewModel.toggleItemStatus(item) },
                    onEditItem = { item -> itemToEdit = item },
                    onDeleteItem = { itemId -> viewModel.deleteItem(itemId) },
                    onOpenAddDialog = { showAddItemDialog = true }
                )
                2 -> RoutinesSubView(
                    modeRoutines = modeRoutines
                )
                3 -> SettingsSubView(
                    mode = mode,
                    isActive = isActive,
                    onToggleActive = {
                        if (isActive) viewModel.deactivateMode(mode.id)
                        else viewModel.setActiveMode(mode.id)
                    },
                    onDeleteMode = {
                        viewModel.deleteMode(mode.id) { success ->
                            if (success) {
                                onNavigateBack()
                            }
                        }
                    }
                )
            }
        }
    }

    // Add Item Dialog
    if (showAddItemDialog) {
        AddItemToModeDialog(
            modeName = mode.name,
            onDismiss = { showAddItemDialog = false },
            onConfirm = { itemName, isTemporary ->
                viewModel.addItemToMode(mode.id, itemName, isTemporary)
                showAddItemDialog = false
            }
        )
    }

    // Edit Mode Dialog
    if (showEditDialog) {
        EditModeDialog(
            mode = mode,
            onDismiss = { showEditDialog = false },
            onConfirm = { updatedMode ->
                viewModel.updateMode(updatedMode)
                showEditDialog = false
            }
        )
    }

    // Edit Item Dialog
    if (itemToEdit != null) {
        EditItemDialog(
            item = itemToEdit!!,
            onDismiss = { itemToEdit = null },
            onConfirm = { newName, isTemporary ->
                viewModel.renameItem(itemToEdit!!.id, newName, isTemporary)
                itemToEdit = null
            }
        )
    }
}

/**
 * Overview Sub-View matching Screen 7 wireframe
 */
@Composable
fun OverviewSubView(
    mode: LifeModeEntity,
    modeConfig: ModeVisualConfig,
    readinessPercentage: Int,
    animatedProgress: Float,
    modeItems: List<ItemRequirementEntity>,
    onItemToggle: (ItemRequirementEntity) -> Unit,
    onOpenAddDialog: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // A. "Today's Readiness" Gauge Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "Today's Readiness",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$readinessPercentage%",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        // Mode Icon Badge
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(modeConfig.accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = modeConfig.icon,
                                contentDescription = mode.name,
                                tint = modeConfig.accentColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = PrimaryPurple,
                        trackColor = SurfaceDark
                    )
                }
            }
        }

        // B. "Required Items" Checklist Section
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
                    color = TextPrimary
                )

                TextButton(
                    onClick = onOpenAddDialog,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "+ Add",
                        color = CyanAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Checklist Items
        if (modeItems.isEmpty()) {
            item {
                Text(
                    text = "No items attached to this mode yet.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(modeItems, key = { it.id }) { item ->
                ModeChecklistItemRow(
                    item = item,
                    onToggle = { onItemToggle(item) }
                )
            }
        }
    }
}

/**
 * Checklist item row matching Screen 7 wireframe
 */
@Composable
fun ModeChecklistItemRow(
    item: ItemRequirementEntity,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardDark,
        border = BorderStroke(1.dp, BorderDark),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Circular status badge: Green checkmark if ready, Red alert if missing
                if (item.isReady) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Ready",
                            tint = SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(AlertRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Missing",
                            tint = AlertRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = item.itemName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (item.isReady) "Prepared • Tap to toggle" else "Action needed • Tap to mark ready",
                        fontSize = 11.sp,
                        color = if (item.isReady) SuccessGreen.copy(alpha = 0.8f) else AlertRed.copy(alpha = 0.8f)
                    )
                }
            }

            // Right options affordance
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Item Options",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Items Sub-View: Comprehensive management of all permanent & temporary items
 */
@Composable
fun ItemsSubView(
    modeItems: List<ItemRequirementEntity>,
    onItemToggle: (ItemRequirementEntity) -> Unit,
    onEditItem: (ItemRequirementEntity) -> Unit,
    onDeleteItem: (Int) -> Unit,
    onOpenAddDialog: () -> Unit
) {
    val permanent = modeItems.filter { !it.isTemporary }
    val temporary = modeItems.filter { it.isTemporary }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Permanent Checklist (${permanent.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                TextButton(onClick = onOpenAddDialog) {
                    Text("+ Add Item", color = CyanAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (permanent.isEmpty()) {
            item {
                Text(
                    text = "No permanent checklist items configured yet.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }

        items(permanent, key = { it.id }) { item ->
            ItemManagerRow(
                item = item,
                onToggle = { onItemToggle(item) },
                onEdit = { onEditItem(item) },
                onDelete = { onDeleteItem(item.id) }
            )
        }

        if (temporary.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Temporary / Session Gear (${temporary.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber
                )
            }

            items(temporary, key = { it.id }) { item ->
                ItemManagerRow(
                    item = item,
                    onToggle = { onItemToggle(item) },
                    onEdit = { onEditItem(item) },
                    onDelete = { onDeleteItem(item.id) }
                )
            }
        }
    }
}

@Composable
fun ItemManagerRow(
    item: ItemRequirementEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardDark,
        border = BorderStroke(1.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.itemName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = item.sourceEvidence ?: "Manual Entry",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Switch(
                    checked = item.isReady,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SuccessGreen,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SurfaceDark
                    )
                )

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Item",
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Item",
                        tint = AlertRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Routines Sub-View: Lists automated triggers and checks for this mode
 */
@Composable
fun RoutinesSubView(
    modeRoutines: List<ModeRoutineSpec>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Text(
                text = "Automated Mode Routines (${modeRoutines.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Triggers executed automatically by context & geofencing",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        items(modeRoutines, key = { it.id }) { routine ->
            var isEnabled by remember { mutableStateOf(routine.isEnabled) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = routine.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = PrimaryPurple.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = routine.trigger,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PurpleLight,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryPurple,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = routine.description,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

/**
 * Settings Sub-View: Mode parameters, priority and triggers
 */
@Composable
fun SettingsSubView(
    mode: LifeModeEntity,
    isActive: Boolean,
    onToggleActive: () -> Unit,
    onDeleteMode: () -> Unit
) {
    var geofenceEnabled by remember { mutableStateOf(true) }
    var calendarSyncEnabled by remember { mutableStateOf(true) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Activation State",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isActive) "Currently Active" else "Standby Mode",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isActive) SuccessGreen else TextSecondary
                            )
                            Text(
                                text = "When active, checklist appears on Home and Readiness Hub",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        Button(
                            onClick = onToggleActive,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isActive) AlertRed.copy(alpha = 0.2f) else PrimaryPurple
                            )
                        ) {
                            Text(
                                text = if (isActive) "Deactivate" else "Set Active",
                                color = if (isActive) AlertRed else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Smart Triggers",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Geofence Automation", fontSize = 13.sp, color = TextPrimary)
                            Text("Auto-switch on campus/facility arrival", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = geofenceEnabled,
                            onCheckedChange = { geofenceEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = PrimaryPurple)
                        )
                    }

                    HorizontalDivider(color = BorderDark)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Calendar Schedule Sync", fontSize = 13.sp, color = TextPrimary)
                            Text("Pre-activate 15 minutes before class or event", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = calendarSyncEnabled,
                            onCheckedChange = { calendarSyncEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = PrimaryPurple)
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, if (mode.id in 1..6) BorderDark else AlertRed.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (mode.id in 1..6) "System Mode Protection" else "Danger Zone",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (mode.id in 1..6) TextPrimary else AlertRed
                    )

                    if (mode.id in 1..6) {
                        Text(
                            text = "This is a default LIFEOS mode (${mode.name}). System modes are protected against deletion to maintain core routines.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )
                    } else {
                        Text(
                            text = "Permanently delete this custom life mode. All checklist requirements and configured routines for this mode will be purged.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Mode", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete ${mode.name}?", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("Are you sure you want to delete '${mode.name}'? All items and routines belonging to this mode will be permanently deleted.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteMode()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CardDark,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

/**
 * Add Item To Mode Dialog
 */
@Composable
fun AddItemToModeDialog(
    modeName: String,
    onDismiss: () -> Unit,
    onConfirm: (itemName: String, isTemporary: Boolean) -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var isTemporary by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Add Item to $modeName", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Item Name") },
                    placeholder = { Text("e.g. Lab Manual, Scientific Calculator") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Temporary Item", fontSize = 13.sp, color = TextPrimary)
                        Text("Expires after current session", fontSize = 11.sp, color = TextMuted)
                    }
                    Switch(
                        checked = isTemporary,
                        onCheckedChange = { isTemporary = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = PrimaryPurple)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (itemName.isNotBlank()) {
                        onConfirm(itemName.trim(), isTemporary)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Save Item")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CardDark,
        shape = RoundedCornerShape(18.dp)
    )
}

/**
 * Edit Mode Dialog
 */
@Composable
fun EditModeDialog(
    mode: LifeModeEntity,
    onDismiss: () -> Unit,
    onConfirm: (LifeModeEntity) -> Unit
) {
    var name by remember { mutableStateOf(mode.name) }
    var priority by remember { mutableStateOf(mode.priority.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Edit ${mode.name} Mode", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Mode Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it },
                    label = { Text("Priority Weight (1 - 10)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = priority.toIntOrNull() ?: mode.priority
                    onConfirm(mode.copy(name = name.trim(), priority = p))
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CardDark,
        shape = RoundedCornerShape(18.dp)
    )
}

/**
 * Edit Item Dialog
 */
@Composable
fun EditItemDialog(
    item: ItemRequirementEntity,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, isTemporary: Boolean) -> Unit
) {
    var itemName by remember { mutableStateOf(item.itemName) }
    var isTemporary by remember { mutableStateOf(item.isTemporary) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Edit Item", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Item Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Temporary Item", fontSize = 13.sp, color = TextPrimary)
                        Text("Expires after current session", fontSize = 11.sp, color = TextMuted)
                    }
                    Switch(
                        checked = isTemporary,
                        onCheckedChange = { isTemporary = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = PrimaryPurple)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (itemName.isNotBlank()) {
                        onConfirm(itemName.trim(), isTemporary)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CardDark,
        shape = RoundedCornerShape(18.dp)
    )
}
