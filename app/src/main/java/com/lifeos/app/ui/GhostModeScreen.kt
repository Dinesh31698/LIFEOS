package com.lifeos.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.engine.GhostApprovalResult
import com.lifeos.app.engine.GhostModeEngine
import com.lifeos.app.engine.GhostProposalLifecycle
import com.lifeos.app.ui.theme.*

@Composable
fun GhostModeScreen(
    viewModel: MainViewModel,
    proposalId: Int = 99,
    onNavigateBack: () -> Unit = {}
) {
    val ghostProposals by viewModel.ghostProposalsFlow.collectAsState()
    val appliedProposalIds by viewModel.appliedProposalIds.collectAsState()
    val ignoredProposalIds by viewModel.ignoredProposalIds.collectAsState()
    val engineProposals by GhostModeEngine.proposalsFlow.collectAsState()

    val proposal = remember(ghostProposals, proposalId) {
        ghostProposals.find { it.id == proposalId } ?: ghostProposals.firstOrNull()
    }

    val engineProposal = remember(engineProposals, proposalId, proposal) {
        GhostModeEngine.getProposalByNumericId(proposalId)
            ?: (if (proposal != null) GhostModeEngine.getProposalByNumericId(proposal.id) else null)
            ?: engineProposals.firstOrNull()
    }

    val isApplied = remember(appliedProposalIds, proposalId, proposal, engineProposal) {
        proposalId in appliedProposalIds ||
        (proposal != null && proposal.id in appliedProposalIds) ||
        engineProposal?.lifecycle == GhostProposalLifecycle.APPLIED
    }

    val isIgnored = remember(ignoredProposalIds, proposalId, proposal, engineProposal) {
        proposalId in ignoredProposalIds ||
        (proposal != null && proposal.id in ignoredProposalIds) ||
        engineProposal?.lifecycle == GhostProposalLifecycle.USER_REJECTED
    }

    var showActionSnackbar by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                            tint = CyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "Ghost Mode",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Red pending count badge
                    val pendingCount = ghostProposals.count { it.id !in appliedProposalIds && it.id !in ignoredProposalIds }
                    if (pendingCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(AlertRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$pendingCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { /* More options */ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        bottomBar = {
            if (!isApplied && !isIgnored && proposal != null) {
                Surface(
                    color = BackgroundDark,
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    when (engineProposal?.lifecycle) {
                        GhostProposalLifecycle.EXPIRED -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(
                                    onClick = { onNavigateBack() },
                                    enabled = true,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, BorderDark),
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) {
                                    Text("Proposal Expired (Event Ended)", color = TextSecondary)
                                }
                            }
                        }
                        GhostProposalLifecycle.SUPERSEDED -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(
                                    onClick = { onNavigateBack() },
                                    enabled = true,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, BorderDark),
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) {
                                    Text("Superseded by Newer Update", color = TextSecondary)
                                }
                            }
                        }
                        else -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Ignore / Reject button
                                OutlinedButton(
                                    onClick = {
                                        viewModel.ignoreGhostProposal(proposal.id)
                                        showActionSnackbar = "Proposal rejected"
                                        onNavigateBack()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, BorderDark),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = SurfaceDark,
                                        contentColor = TextSecondary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                ) {
                                    Text(
                                        text = "Reject",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Apply Change button
                                Button(
                                    onClick = {
                                        viewModel.applyGhostProposal(proposal.id)
                                        showActionSnackbar = "Schedule updated: Proposal approved"
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryPurple,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1.6f)
                                        .height(52.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Approve Change",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Confirmation Banner if Applied
            if (isApplied) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SuccessGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Change Applied Successfully",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                            Text(
                                text = "DBMS Lab was removed and DBMS Record marked as waived.",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // 1. Top Alert Banner Card: Possible Lab Cancellation
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, if (isApplied) SuccessGreen.copy(alpha = 0.3f) else AlertRed.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isApplied) SuccessGreen.copy(alpha = 0.2f) else AlertRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isApplied) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isApplied) SuccessGreen else AlertRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isApplied) "Lab Cancellation Applied" else "Possible Lab Cancellation",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isApplied) "Active schedule updated" else "Medium confidence • Detected 8:30 AM",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // 2. Detected Information Summary Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Detected Information",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    // Message quote bubble
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CardDark,
                        border = BorderStroke(1.dp, BorderDark.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "\"${proposal?.evidenceText ?: "Maybe tomorrow's lab is cancelled"}\"",
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }

                    // Key-Value rows
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GhostDetailRow(label = "Activity", value = engineProposal?.activityName ?: proposal?.title ?: "DBMS Lab")
                        GhostDetailRow(label = "Current State", value = engineProposal?.previousState ?: "Scheduled")
                        GhostDetailRow(label = "Proposed State", value = engineProposal?.proposedState ?: "Proposed Change")
                        GhostDetailRow(label = "Change Type", value = engineProposal?.mutationType?.name ?: "SCHEDULE_MUTATION")
                        GhostDetailRow(label = "Source", value = engineProposal?.sourceCategory ?: "Notification (Message)")
                        val conf = ((engineProposal?.confidence ?: proposal?.confidence ?: 0.62f) * 100).toInt()
                        GhostDetailRow(
                            label = "Confidence",
                            value = "$conf% (${if (conf >= 85) "High" else if (conf >= 50) "Medium" else "Low"})",
                            valueColor = if (conf >= 85) SuccessGreen else WarningAmber,
                            isBold = true
                        )
                    }
                }
            }

            // 3. "What Might Change" Breakdown Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "What Might Change",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ImpactBulletItem(
                            text = engineProposal?.potentialConsequences ?: "DBMS Lab may be removed from your schedule and DBMS Record waived.",
                            dotColor = AlertRed
                        )
                        ImpactBulletItem(
                            text = "DBMS Record may no longer be needed",
                            dotColor = AlertRed
                        )
                        ImpactBulletItem(
                            text = "Your morning plan may change",
                            dotColor = WarningAmber
                        )
                    }
                }
            }

            // If already applied, provide a button to return
            if (isApplied) {
                Button(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceDark,
                        contentColor = TextPrimary
                    ),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Back to Notifications",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GhostDetailRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextSecondary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
private fun ImpactBulletItem(
    text: String,
    dotColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            fontSize = 13.sp,
            color = TextPrimary,
            lineHeight = 18.sp
        )
    }
}
