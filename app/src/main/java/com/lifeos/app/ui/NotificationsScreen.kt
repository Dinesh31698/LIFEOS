package com.lifeos.app.ui

import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.NotificationCategory
import com.lifeos.app.NotificationItem
import com.lifeos.app.ui.theme.*

@Composable
fun NotificationsScreen(
    viewModel: MainViewModel,
    onOpenGhostMode: (Int) -> Unit = {},
    onNavigateToReadiness: () -> Unit = {}
) {
    val notifications by viewModel.notificationsFlow.collectAsState()
    val appliedProposalIds by viewModel.appliedProposalIds.collectAsState()

    // Filter categories: All, Now, Soon, Proposals
    val filterTabs = listOf("All", "Now", "Soon", "Proposals")
    var selectedFilterIndex by remember { mutableStateOf(0) }

    val filteredNotifications = remember(notifications, selectedFilterIndex) {
        when (selectedFilterIndex) {
            1 -> notifications.filter { it.category == NotificationCategory.IMPORTANT } // NOW
            2 -> notifications.filter { it.category == NotificationCategory.AI || it.category == NotificationCategory.SYSTEM } // SOON
            3 -> notifications.filter { it.category == NotificationCategory.GHOST_MODE } // PROPOSALS
            else -> notifications
        }
    }


    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Notifications",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Local intelligence & ghost proposals",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                IconButton(
                    onClick = { /* More options */ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
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
            // 1. Horizontal Segmented Filter Bar
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterTabs.indices.toList()) { index ->
                    val title = filterTabs[index]
                    val isSelected = selectedFilterIndex == index

                    val bg by animateColorAsState(
                        targetValue = if (isSelected) PrimaryPurple else SurfaceDark,
                        animationSpec = tween(200),
                        label = "filterBg"
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bg,
                        border = BorderStroke(1.dp, if (isSelected) PrimaryPurple else BorderDark),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedFilterIndex = index }
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // 2. Section Header: "Today"
            Text(
                text = "Today",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // 3. Notifications Stream
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredNotifications, key = { it.id }) { item ->
                    NotificationCardItem(
                        item = item,
                        isApplied = item.ghostProposalId != null && item.ghostProposalId in appliedProposalIds,
                        onClick = {
                            if (item.category == NotificationCategory.GHOST_MODE) {
                                onOpenGhostMode(item.ghostProposalId ?: 99)
                            } else if (item.category == NotificationCategory.SYSTEM) {
                                onNavigateToReadiness()
                            }
                        }
                    )
                }

                if (filteredNotifications.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No notifications in this filter",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCardItem(
    item: NotificationItem,
    isApplied: Boolean,
    onClick: () -> Unit
) {
    val (iconRes, iconTint, iconBg) = when (item.category) {
        NotificationCategory.AI -> {
            if (item.title.contains("Sports", ignoreCase = true)) {
                Triple(Icons.Default.FitnessCenter, Color(0xFFFF7675), Color(0xFFFF7675).copy(alpha = 0.15f))
            } else {
                Triple(Icons.Default.MenuBook, PrimaryPurple, PrimaryPurple.copy(alpha = 0.15f))
            }
        }
        NotificationCategory.GHOST_MODE -> {
            if (isApplied) {
                Triple(Icons.Default.CheckCircle, SuccessGreen, SuccessGreen.copy(alpha = 0.15f))
            } else {
                Triple(Icons.Default.Warning, WarningAmber, WarningAmber.copy(alpha = 0.15f))
            }
        }
        NotificationCategory.SYSTEM -> Triple(Icons.Default.CheckCircleOutline, CyanAccent, CyanAccent.copy(alpha = 0.15f))
        NotificationCategory.IMPORTANT -> Triple(Icons.Default.DirectionsBus, Color(0xFFA29BFE), Color(0xFFA29BFE).copy(alpha = 0.15f))
        else -> Triple(Icons.Default.Notifications, TextSecondary, SurfaceDark)
    }

    if (item.category == NotificationCategory.GHOST_MODE && !isApplied) {
        // Distinctive Ghost Mode Proposal Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardDark,
            border = BorderStroke(1.5.dp, WarningAmber.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✦", color = WarningAmber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "CHANGE PROPOSED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WarningAmber,
                            letterSpacing = 1.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WarningAmber.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Confidence 86%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Before -> After Diff Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CURRENT", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text("10:00 AM", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Block A", fontSize = 11.sp, color = TextSecondary)
                        }

                        Text("→", fontSize = 18.sp, color = CyanAccent, fontWeight = FontWeight.Bold)

                        Column(horizontalAlignment = Alignment.End) {
                            Text("PROPOSED", fontSize = 10.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
                            Text("11:00 AM", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                            Text("Block B", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }

                Text(
                    text = "Affected: ✓ Lab Record, ✓ Departure, ✓ Readiness",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Review Proposal", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon Square
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconRes,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Title & Message Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.message,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }

                // Timestamp & Category Badge
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.timestamp,
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    val (badgeText, badgeBg, badgeTextColor) = when {
                        isApplied -> Triple("Applied", SuccessGreen.copy(alpha = 0.2f), SuccessGreen)
                        item.category == NotificationCategory.GHOST_MODE -> Triple("Ghost Mode", WarningAmber.copy(alpha = 0.2f), WarningAmber)
                        item.category == NotificationCategory.AI -> Triple("AI", PrimaryPurple.copy(alpha = 0.2f), PurpleLight)
                        item.category == NotificationCategory.SYSTEM -> Triple("System", CyanAccent.copy(alpha = 0.2f), CyanAccent)
                        item.category == NotificationCategory.IMPORTANT -> Triple("Alert", AlertRed.copy(alpha = 0.2f), AlertRed)
                        else -> Triple("Info", SurfaceDark, TextSecondary)
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

