package com.lifeos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.data.EventEntity
import com.lifeos.app.engine.BriefingMode
import com.lifeos.app.engine.VirtualExecutiveSecretary
import com.lifeos.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutiveBriefingScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val activeItems by viewModel.activeItems.collectAsState()
    var briefingMode by remember { mutableStateOf(BriefingMode.BRIEF) }

    val sampleEvent = remember {
        EventEntity(
            id = 1,
            title = "DBMS Lab Session",
            startTime = System.currentTimeMillis() + 600_000L,
            endTime = System.currentTimeMillis() + 4200_000L,
            location = "Engineering Block B, Room 302"
        )
    }

    val preEventBriefing = remember(activeItems, briefingMode) {
        VirtualExecutiveSecretary.generatePreEventBriefing(
            event = sampleEvent,
            checklistItems = activeItems,
            mode = briefingMode,
            leadMinutes = 10
        )
    }

    var showConflictResolved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Executive Secretary", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
            // 1. Briefing Mode Selector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pre-Event Briefing Style:", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = briefingMode == BriefingMode.BRIEF,
                            onClick = { briefingMode = BriefingMode.BRIEF },
                            label = { Text("Brief", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = briefingMode == BriefingMode.DETAILED,
                            onClick = { briefingMode = BriefingMode.DETAILED },
                            label = { Text("Detailed", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = briefingMode == BriefingMode.SILENT,
                            onClick = { briefingMode = BriefingMode.SILENT },
                            label = { Text("Silent", fontSize = 11.sp) }
                        )
                    }
                }
            }

            // 2. Pre-Event Briefing Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = RoundedCornerShape(8.dp), color = CyanAccent.copy(alpha = 0.2f)) {
                                Text(
                                    text = "STARTS IN 10 MIN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text("Readiness: ${preEventBriefing.readinessScorePercent}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }

                        Text(preEventBriefing.eventTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("📍 ${preEventBriefing.location}", fontSize = 13.sp, color = TextSecondary)

                        if (preEventBriefing.missingItems.isNotEmpty()) {
                            Surface(shape = RoundedCornerShape(8.dp), color = AlertRed.copy(alpha = 0.15f)) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = "Alert", tint = AlertRed, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Missing: ${preEventBriefing.missingItems.joinToString(", ")}",
                                        fontSize = 12.sp,
                                        color = AlertRed,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Text(preEventBriefing.departureAlert, fontSize = 12.sp, color = WarningAmber, fontWeight = FontWeight.Medium)

                        if (briefingMode != BriefingMode.SILENT) {
                            Text(
                                text = "Assistant Utterance: \"${preEventBriefing.spokenSummary}\"",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // 3. Schedule Conflict Arbitration Card
            item {
                Text("Schedule Conflict Arbitration", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("⚠️", fontSize = 16.sp)
                            Text(
                                text = "Conflict Detected (10:00 AM)",
                                fontWeight = FontWeight.Bold,
                                color = WarningAmber,
                                fontSize = 14.sp
                            )
                        }

                        Text("Event A: 10:00 AM — DBMS Lab (Academic)", color = TextPrimary, fontSize = 13.sp)
                        Text("Event B: 10:00 AM — Fitness Center (Workout)", color = TextPrimary, fontSize = 13.sp)

                        Surface(shape = RoundedCornerShape(8.dp), color = SurfaceDark) {
                            Text(
                                text = "Executive Proposal: Reschedule Workout to 06:00 PM (Prioritizing Academic Commitment).",
                                fontSize = 12.sp,
                                color = CyanAccent,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        if (!showConflictResolved) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showConflictResolved = true }) {
                                    Text("Reject", color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { showConflictResolved = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                                ) {
                                    Text("Approve Proposal", color = BackgroundDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text("Proposal resolved. Schedule updated.", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 4. Daily Briefing Card
            item {
                Text("Daily Briefings", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("☀️ Morning Briefing (08:00 AM)", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 13.sp)
                        Text(
                            text = "Good morning. 1 academic session scheduled today. 1 checklist item requires packing before departure.",
                            fontSize = 12.sp,
                            color = TextPrimary
                        )

                        HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))

                        Text("🌙 Evening Recap (09:30 PM)", fontWeight = FontWeight.Bold, color = PrimaryPurple, fontSize = 13.sp)
                        Text(
                            text = "Daily checklist items verified. Sleep target bedtime is 22:30. Phone inactivity estimated at 7.2 hours.",
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}
