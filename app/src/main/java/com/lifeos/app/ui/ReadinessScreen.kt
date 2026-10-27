package com.lifeos.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.data.ItemHabitEntity
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.engine.ForgettingModelEngine
import com.lifeos.app.ui.theme.*

@Composable
fun ReadinessScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val activeMode by viewModel.activeMode.collectAsState()
    val allModes by viewModel.allModes.collectAsState()
    val activeItems by viewModel.activeItems.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val habits by viewModel.allHabits.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Now, 1: For Later, 2: History
    var showAddItemDialog by remember { mutableStateOf(false) }
    var selectedItemForEvidence by remember { mutableStateOf<ItemRequirementEntity?>(null) }

    val habitMap = remember(habits) { habits.associateBy { it.itemName.lowercase() } }

    val currentTabItems = remember(selectedTab, activeItems, allItems, activeMode) {
        when (selectedTab) {
            0 -> activeItems
            1 -> allItems.filter { it.modeId != (activeMode?.id ?: 1) }
            else -> allItems.filter { it.isWaived || it.isReady }
        }
    }

    val missingItems = remember(currentTabItems, habitMap) {
        currentTabItems.filter { !it.isReady && !it.isWaived }
            .sortedWith(
                compareByDescending<ItemRequirementEntity> { item ->
                    val habit = habitMap[item.itemName.lowercase()]
                    val isHigh = habit != null && ForgettingModelEngine.isFrequentlyForgotten(habit)
                    val boost = habit?.priorityBoost ?: 0.0f
                    if (isHigh) 2000f + boost
                    else 1000f + boost
                }.thenBy { it.id }
            )
    }
    val readyItems = remember(currentTabItems) { currentTabItems.filter { it.isReady } }

    val percentage = remember(missingItems, readyItems, currentTabItems) {
        if (currentTabItems.isEmpty()) -1
        else ((readyItems.size.toFloat() / currentTabItems.size.toFloat()) * 100).toInt().coerceIn(0, 100)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (percentage.coerceAtLeast(0)) / 100f,
        animationSpec = tween(durationMillis = 600),
        label = "ReadinessLinearProgress"
    )

    Scaffold(
        containerColor = BackgroundDark,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddItemDialog = true },
                containerColor = PrimaryPurple,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Requirement") },
                text = { Text("Add Item", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // 1. Top Header Row: Back button, Title & Settings gear
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
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
                            .background(CardDark)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "Readiness",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CardDark)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Segmented Tab Bar (Now, For Later, History)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardDark,
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val tabs = listOf("Now", "For Later", "History")
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) PrimaryPurple else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedTab = index }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                // 3. Active Mode Readiness Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${activeMode?.name ?: "College"} Readiness",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (percentage < 0) "—" else "$percentage%",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (percentage < 0) TextMuted else TextPrimary
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = PrimaryPurple.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.4f)),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = "Mode Icon",
                                            tint = PurpleLight,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = PrimaryPurple,
                                trackColor = SurfaceDark
                            )
                        }
                    }
                }

                // 4. Missing Items Section (Action Needed)
                if (missingItems.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AlertRed)
                            )
                            Text(
                                text = "Missing Items (${missingItems.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AlertRed
                            )
                        }
                    }

                    items(missingItems, key = { it.id }) { item ->
                        val habit = habitMap[item.itemName.lowercase()]
                        MissingItemHubCard(
                            item = item,
                            habit = habit,
                            onMarkReady = { viewModel.markItemAsReady(item.id) },
                            onWhy = { selectedItemForEvidence = item }
                        )
                    }
                }

                // 5. Ready Items Section
                if (readyItems.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreen)
                            )
                            Text(
                                text = "Ready Items (${readyItems.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }

                    items(readyItems, key = { it.id }) { item ->
                        ReadyItemHubCard(
                            item = item,
                            onToggleStatus = { viewModel.toggleItemStatus(item) }
                        )
                    }
                }

                // Empty state if nothing in this tab
                if (missingItems.isEmpty() && readyItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No requirements configured",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Add items you need for this mode",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 6. Interactive Add Item Dialog
    if (showAddItemDialog) {
        AddItemDialog(
            activeMode = activeMode,
            allModes = allModes,
            onDismiss = { showAddItemDialog = false },
            onConfirm = { name, modeId, isTemporary ->
                viewModel.addNewItem(
                    ItemRequirementEntity(
                        modeId = modeId,
                        itemName = name,
                        isReady = false,
                        isTemporary = isTemporary,
                        confidenceScore = 1.0f,
                        sourceEvidence = "Manual User Entry"
                    )
                )
                showAddItemDialog = false
            }
        )
    }

    selectedItemForEvidence?.let { item ->
        val habit = habitMap[item.itemName.lowercase()]
        EvidenceExplainerDialog(
            item = item,
            habit = habit,
            onDismiss = { selectedItemForEvidence = null }
        )
    }
}

@Composable
fun MissingItemHubCard(
    item: ItemRequirementEntity,
    habit: ItemHabitEntity?,
    onMarkReady: () -> Unit,
    onWhy: () -> Unit = {}
) {
    val evaluation = remember(habit) { ForgettingModelEngine.evaluatePersonalizedRisk(habit) }
    val isHighRisk = evaluation.riskLevel == com.lifeos.app.engine.PersonalizedRiskLevel.HIGH ||
        (habit != null && ForgettingModelEngine.isFrequentlyForgotten(habit))
    val isMediumRisk = !isHighRisk && evaluation.riskLevel == com.lifeos.app.engine.PersonalizedRiskLevel.MEDIUM

    Card(
        onClick = onWhy,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isHighRisk -> Color(0xFF261D1A)
                isMediumRisk -> Color(0xFF26221A)
                else -> CardDark
            }
        ),
        border = when {
            isHighRisk -> BorderStroke(1.5.dp, Color(0xFFFF7043))
            isMediumRisk -> BorderStroke(1.2.dp, Color(0xFFFFB74D))
            else -> BorderStroke(1.dp, BorderDark)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Warning Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isHighRisk -> Color(0xFFFF7043).copy(alpha = 0.2f)
                                isMediumRisk -> Color(0xFFFFB74D).copy(alpha = 0.2f)
                                else -> AlertRed.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Missing",
                        tint = when {
                            isHighRisk -> Color(0xFFFF7043)
                            isMediumRisk -> Color(0xFFFFB74D)
                            else -> AlertRed
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.itemName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
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
                        } else if (isMediumRisk) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFFB74D).copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, Color(0xFFFFB74D))
                            ) {
                                Text(
                                    text = "MEDIUM RISK",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFB74D),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val subtitleText = when {
                        isHighRisk -> "⚠️ ${evaluation.explanation}"
                        isMediumRisk -> "⚠️ ${evaluation.explanation}"
                        habit != null && habit.forgetCount > 0 -> "⚠️ ${evaluation.explanation}"
                        else -> item.sourceEvidence ?: "Required for active mode"
                    }
                    Text(
                        text = subtitleText,
                        fontSize = 11.sp,
                        color = when {
                            isHighRisk -> Color(0xFFFFAB91)
                            isMediumRisk -> Color(0xFFFFE082)
                            else -> TextSecondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // "Why?" Info Explainer Icon Button
                IconButton(
                    onClick = onWhy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Why suggested?",
                        tint = if (isHighRisk) Color(0xFFFF7043) else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // [Mark as Ready] Button
                OutlinedButton(
                    onClick = onMarkReady,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.8f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PrimaryPurple.copy(alpha = 0.15f)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Mark as Ready",
                        color = CyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ReadyItemHubCard(
    item: ItemRequirementEntity,
    onToggleStatus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleStatus() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Vibrant Green Checkmark Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Ready",
                        tint = SuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = item.itemName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Prepared • Tap to uncheck",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Action",
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun AddItemDialog(
    activeMode: LifeModeEntity?,
    allModes: List<LifeModeEntity>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, modeId: Int, isTemporary: Boolean) -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var selectedModeId by remember { mutableStateOf(activeMode?.id ?: 1) }
    var isTemporary by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Requirement",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Item Name") },
                    placeholder = { Text("e.g. Lab Coat, Power Bank") },
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

                Text(
                    text = "Associated Life Mode:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allModes) { mode ->
                        val isSelected = selectedModeId == mode.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) PrimaryPurple else SurfaceDark,
                            border = BorderStroke(1.dp, if (isSelected) PrimaryPurple else BorderDark),
                            modifier = Modifier.clickable { selectedModeId = mode.id }
                        ) {
                            Text(
                                text = mode.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Temporary Requirement",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Expires automatically after this session",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Switch(
                        checked = isTemporary,
                        onCheckedChange = { isTemporary = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryPurple,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = SurfaceDark
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (itemName.isNotBlank()) {
                        onConfirm(itemName.trim(), selectedModeId, isTemporary)
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
