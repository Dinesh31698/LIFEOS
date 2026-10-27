package com.lifeos.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.lifeos.app.ui.theme.*

@Composable
fun LifeOsBottomBar(
    navController: NavController,
    pendingGhostCount: Int = 0
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Frosted Glass Base Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            color = SurfaceDark.copy(alpha = 0.94f),
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Screen.bottomNavItems.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    val isVoice = screen == Screen.Voice

                    if (isVoice) {
                        // Spacer placeholder for the central floating Voice orb
                        Spacer(modifier = Modifier.width(64.dp))
                    } else {
                        val iconTint by animateColorAsState(
                            targetValue = if (isSelected) CyanAccent else TextSecondary,
                            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                            label = "IconTint"
                        )

                        val pillBackground by animateColorAsState(
                            targetValue = if (isSelected) PrimaryPurple.copy(alpha = 0.22f) else Color.Transparent,
                            animationSpec = tween(durationMillis = 250),
                            label = "PillBg"
                        )

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = pillBackground,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) CyanAccent.copy(alpha = 0.5f) else Color.Transparent
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.title,
                                        tint = iconTint,
                                        modifier = Modifier.size(22.dp)
                                    )

                                    // Badge on More tab if proposals exist
                                    if (screen == Screen.More && pendingGhostCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 1.dp, end = 1.dp)
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(AlertRed)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                Text(
                                    text = screen.title,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) CyanAccent else TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Elevated Central Voice Orb
        val isVoiceSelected = currentRoute == Screen.Voice.route
        Box(
            modifier = Modifier
                .offset(y = (-14).dp)
                .size(62.dp)
                .shadow(elevation = 12.dp, shape = CircleShape, spotColor = PrimaryPurple)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isVoiceSelected) CyanAccent else PurpleLight,
                            PrimaryPurple,
                            BackgroundDark
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            if (isVoiceSelected) CyanAccent else PurpleLight,
                            CyanAccent.copy(alpha = 0.5f)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (currentRoute != Screen.Voice.route) {
                        navController.navigate(Screen.Voice.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Assistant",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

