package com.lifeos.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.lifeos.app.security.SourceType

@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    onFinishOnboarding: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val channelToggles by viewModel.channelToggles.collectAsState()

    var emailEnabled by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FD))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Main Pager Content
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                label = "OnboardingPageTransition"
            ) { page ->
                when (page) {
                    0 -> OnboardingPageOne(
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> OnboardingPageTwo(
                        channelToggles = channelToggles,
                        emailEnabled = emailEnabled,
                        onToggleChannel = { source, enabled ->
                            viewModel.setChannelAuthorized(source, enabled)
                        },
                        onToggleEmail = { emailEnabled = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom Navigation & Pager Indicator Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Button (Skip or Back)
                if (currentPage == 0) {
                    TextButton(
                        onClick = onFinishOnboarding,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Skip",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF718096)
                        )
                    }
                } else {
                    TextButton(
                        onClick = { currentPage = 0 },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Back",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF718096)
                        )
                    }
                }

                // Center Dots (3 dots, matching wireframe indicator)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0..2) {
                        val isActive = (i == currentPage)
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isActive) 20.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (isActive) Color(0xFF5352ED) else Color(0xFFCBD5E0))
                        )
                    }
                }

                // Right Button (Next or Finish)
                Button(
                    onClick = {
                        if (currentPage == 0) {
                            currentPage = 1
                        } else {
                            onFinishOnboarding()
                        }
                    },
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5352ED)),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (currentPage == 0) "Next →" else "Next →",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageOne(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp)
    ) {
        Text(
            text = "Your Personal\nReadiness Assistant",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A202C),
            lineHeight = 34.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "LIFEOS understands your life, plans ahead, and helps you stay ready before you forget.",
            fontSize = 14.sp,
            color = Color(0xFF718096),
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 4 Feature Cards (Wireframe Screen 2)
        FeatureCard(
            icon = Icons.Default.Settings,
            iconBg = Color(0xFFE6FAF0),
            iconTint = Color(0xFF00B894),
            title = "Understands your context and activities"
        )

        Spacer(modifier = Modifier.height(14.dp))

        FeatureCard(
            icon = Icons.Default.Notifications,
            iconBg = Color(0xFFF1EEFF),
            iconTint = Color(0xFF6C5CE7),
            title = "Turns information into actionable reminders"
        )

        Spacer(modifier = Modifier.height(14.dp))

        FeatureCard(
            icon = Icons.Default.Lock,
            iconBg = Color(0xFFEBF5FF),
            iconTint = Color(0xFF0984E3),
            title = "Keeps your data private and local"
        )

        Spacer(modifier = Modifier.height(14.dp))

        FeatureCard(
            icon = Icons.Default.Share,
            iconBg = Color(0xFFFFF4EB),
            iconTint = Color(0xFFE17055),
            title = "Works offline",
            subtitle = "No laptop needed"
        )
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEDF2F7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2D3748),
                    lineHeight = 20.sp
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color(0xFF718096)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageTwo(
    channelToggles: Map<SourceType, Boolean>,
    emailEnabled: Boolean,
    onToggleChannel: (SourceType, Boolean) -> Unit,
    onToggleEmail: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp)
    ) {
        Text(
            text = "Connect What Matters",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A202C)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Choose the information sources you want LIFEOS to use.",
            fontSize = 14.sp,
            color = Color(0xFF718096),
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 1. Notifications Toggle
        SourceToggleCard(
            icon = Icons.Default.Notifications,
            iconBg = Color(0xFFFFF0EB),
            iconTint = Color(0xFFFF7675),
            title = "Notifications",
            subtitle = "Events from your apps",
            checked = channelToggles[SourceType.NOTIFICATION_INGESTION] ?: true,
            onCheckedChange = { onToggleChannel(SourceType.NOTIFICATION_INGESTION, it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Calendar Toggle
        SourceToggleCard(
            icon = Icons.Default.DateRange,
            iconBg = Color(0xFFEBF5FF),
            iconTint = Color(0xFF0984E3),
            title = "Calendar",
            subtitle = "Classes, meetings, schedules",
            checked = channelToggles[SourceType.CALENDAR_SYNC] ?: true,
            onCheckedChange = { onToggleChannel(SourceType.CALENDAR_SYNC, it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Location Toggle
        SourceToggleCard(
            icon = Icons.Default.Place,
            iconBg = Color(0xFFE6FAF9),
            iconTint = Color(0xFF00CEC9),
            title = "Location",
            subtitle = "Understand your context",
            checked = channelToggles[SourceType.LOCATION] ?: true,
            onCheckedChange = { onToggleChannel(SourceType.LOCATION, it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Bluetooth Toggle
        SourceToggleCard(
            icon = Icons.Default.Share,
            iconBg = Color(0xFFEBF5FF),
            iconTint = Color(0xFF2980B9),
            title = "Bluetooth",
            subtitle = "Device presence (laptop, etc)",
            checked = channelToggles[SourceType.BLE_SCANNING] ?: false,
            onCheckedChange = { onToggleChannel(SourceType.BLE_SCANNING, it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Email Toggle
        SourceToggleCard(
            icon = Icons.Default.Email,
            iconBg = Color(0xFFEFF6FF),
            iconTint = Color(0xFF3B82F6),
            title = "Email",
            subtitle = "Important mails and updates",
            checked = emailEnabled,
            onCheckedChange = onToggleEmail
        )
    }
}

@Composable
private fun SourceToggleCard(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEDF2F7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748)
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color(0xFF718096)
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF5352ED),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE2E8F0)
                )
            )
        }
    }
}
