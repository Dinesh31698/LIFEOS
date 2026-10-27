package com.lifeos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.lifeos.app.ui.components.GlassCard
import com.lifeos.app.ui.theme.*

data class MoreHubItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val route: String,
    val badgeCount: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreHubScreen(
    viewModel: MainViewModel,
    onNavigateToRoute: (String) -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val ghostItems by viewModel.ghostItems.collectAsState()
    val ghostEvents by viewModel.ghostEvents.collectAsState()
    val pendingProposalCount = remember(ghostItems, ghostEvents) { ghostItems.size + ghostEvents.size }
    val userProfile by viewModel.userProfile.collectAsState()
    val profileName = userProfile?.preferredName?.trim()?.ifEmpty { "Dinesh" } ?: "Dinesh"

    val hubItems = remember(pendingProposalCount) {
        listOf(
            MoreHubItem(
                title = "Calendar",
                subtitle = "Events & Timelines",
                icon = Icons.Default.DateRange,
                accentColor = Color(0xFF0984E3),
                route = "calendar"
            ),
            MoreHubItem(
                title = "Notifications",
                subtitle = "Alerts & Proposals",
                icon = Icons.Default.Notifications,
                accentColor = CyanAccent,
                route = "notifications",
                badgeCount = pendingProposalCount
            ),
            MoreHubItem(
                title = "Ghost Mode",
                subtitle = "Autonomous Diff Engine",
                icon = Icons.Default.Warning,
                accentColor = WarningAmber,
                route = "ghost_mode",
                badgeCount = pendingProposalCount
            ),
            MoreHubItem(
                title = "Time Audit",
                subtitle = "App Usage & Focus",
                icon = Icons.Default.DateRange,
                accentColor = ContextStudy,
                route = "time_audit"
            ),
            MoreHubItem(
                title = "Memory Vault",
                subtitle = "Semantic Notes & TTL",
                icon = Icons.Default.Edit,
                accentColor = ContextTravel,
                route = "memory_vault"
            ),
            MoreHubItem(
                title = "Executive",
                subtitle = "Pre-Event Briefings",
                icon = Icons.Default.Info,
                accentColor = PurpleLight,
                route = "executive"
            ),
            MoreHubItem(
                title = "Emergency SOS",
                subtitle = "Safety & Rapid Dispatch",
                icon = Icons.Default.Warning,
                accentColor = AlertRed,
                route = "sos"
            ),
            MoreHubItem(
                title = "Assistant",
                subtitle = "Prompt & Queries",
                icon = Icons.Default.Star,
                accentColor = PrimaryPurple,
                route = "assistant"
            ),
            MoreHubItem(
                title = "Privacy Dashboard",
                subtitle = "Zero-Cloud & Audit",
                icon = Icons.Default.Lock,
                accentColor = SuccessGreen,
                route = "privacy"
            ),
            MoreHubItem(
                title = "Settings",
                subtitle = "System & Hardware",
                icon = Icons.Default.Settings,
                accentColor = PrimaryPurple,
                route = "settings"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("More Features", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)
                        Text("Connected Personal Operating System", fontSize = 12.sp, color = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(PrimaryPurple.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profileName.firstOrNull()?.uppercase() ?: "D",
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                fontSize = 14.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
        ) {
            items(hubItems) { item ->
                GlassCard(
                    onClick = { onNavigateToRoute(item.route) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(item.accentColor.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = item.accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        if (item.badgeCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AlertRed
                            ) {
                                Text(
                                    text = "${item.badgeCount} New",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = item.subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
