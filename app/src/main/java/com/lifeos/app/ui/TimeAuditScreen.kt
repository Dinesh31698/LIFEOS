package com.lifeos.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.engine.TimeAuditManager
import com.lifeos.app.ui.components.*
import com.lifeos.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeAuditScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val hasUsagePerm = remember { TimeAuditManager.hasUsageAccess(context) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("TODAY", "WEEK", "CONTEXT")

    val usageRules by viewModel.appUsageRules.collectAsState()
    val focusReport = remember(hasUsagePerm, usageRules) {
        TimeAuditManager.generateDailyFocusReport(context, usageRules)
    }
    val timeline = remember { TimeAuditManager.generateTimeline() }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time Audit", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // 1. Tab Selector
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceDark,
                    contentColor = CyanAccent
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                        )
                    }
                }
            }

            // 2. Permission Banner (if not granted)
            if (!hasUsagePerm) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.15f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Usage Access Required",
                                fontWeight = FontWeight.Bold,
                                color = AlertRed,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "LIFEOS requires Android Usage Access to calculate local screen time and focus distraction metrics. No data leaves your device.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Button(
                                onClick = { TimeAuditManager.openUsageAccessSettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Grant Access", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 3. Focus Score & Time Metrics Card
            item {
                val totalMinutes = if (hasUsagePerm) (focusReport.productiveMinutes + focusReport.neutralMinutes + focusReport.distractionMinutes).coerceAtLeast(138) else 0
                val totalHours = totalMinutes / 60
                val remMinutes = totalMinutes % 60

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Phone Usage Today", fontSize = 12.sp, color = TextSecondary)
                                Text(
                                    text = if (hasUsagePerm) "${totalHours}h ${remMinutes}m" else "--",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CyanAccent.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = if (hasUsagePerm) "${focusReport.focusScorePercent}% Focus" else "Offline",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = BorderDark)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Productive", fontSize = 11.sp, color = TextSecondary)
                                Text(
                                    text = if (hasUsagePerm) "${focusReport.productiveMinutes}m" else "0m",
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen,
                                    fontSize = 16.sp
                                )
                            }
                            Column {
                                Text("Neutral", fontSize = 11.sp, color = TextSecondary)
                                Text(
                                    text = if (hasUsagePerm) "${focusReport.neutralMinutes}m" else "0m",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 16.sp
                                )
                            }
                            Column {
                                Text("Distraction", fontSize = 11.sp, color = TextSecondary)
                                Text(
                                    text = if (hasUsagePerm) "${focusReport.distractionMinutes}m" else "0m",
                                    fontWeight = FontWeight.Bold,
                                    color = WarningAmber,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // 3.5 Contextual Distraction Insight Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = WarningAmber.copy(alpha = 0.5f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("⚠", fontSize = 14.sp)
                                Text(
                                    text = "CONTEXTUAL INSIGHT",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = WarningAmber,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            GlassChip(text = "College Mode", accentColor = ContextCollege, showDot = true)
                        }

                        Text(
                            text = "⚠ 45 min potential distraction during scheduled study time.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Instagram and non-academic apps were active during DBMS Lab preparation block.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { /* ignore */ }) {
                                Text("Ignore", color = TextSecondary, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            GlassButton(
                                onClick = { /* review */ },
                                backgroundColor = WarningAmber.copy(alpha = 0.2f),
                                borderColor = WarningAmber
                            ) {
                                Text("Review", fontSize = 12.sp, color = WarningAmber, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }


            // 4. Content based on tab
            when (selectedTab) {
                0, 1 -> { // TODAY or WEEK
                    item {
                        Text(
                            text = if (selectedTab == 0) "Top Applications Today" else "Weekly Overview",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                    }

                    if (focusReport.topApps.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CardDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (hasUsagePerm) "No foreground apps recorded yet today." else "Usage statistics unavailable without permission.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(focusReport.topApps.size) { index ->
                            val app = focusReport.topApps[index]
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CardDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.appName, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(app.packageName, fontSize = 11.sp, color = TextSecondary)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = when (app.category) {
                                                "PRODUCTIVE" -> SuccessGreen.copy(alpha = 0.2f)
                                                "DISTRACTION" -> WarningAmber.copy(alpha = 0.2f)
                                                else -> SurfaceDark
                                            }
                                        ) {
                                            Text(
                                                text = app.category,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (app.category) {
                                                    "PRODUCTIVE" -> SuccessGreen
                                                    "DISTRACTION" -> WarningAmber
                                                    else -> TextSecondary
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text("${app.durationMinutes}m", fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> { // CONTEXT tab
                    item {
                        Text(
                            text = "Contextual Life Timeline",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                    }

                    items(timeline.size) { index ->
                        val entry = timeline[index]
                        Card(

                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = entry.timeLabel,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent,
                                    fontSize = 13.sp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text(entry.subtitle, fontSize = 11.sp, color = TextSecondary)
                                }
                                if (entry.durationMinutes != null) {
                                    Text("${entry.durationMinutes}m", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
