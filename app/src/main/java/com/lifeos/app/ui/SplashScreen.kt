package com.lifeos.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    // Auto-advance after 6.0 seconds or on tap
    LaunchedEffect(Unit) {
        delay(6000)
        onSplashFinished()
    }

    // Glowing animation transitions
    val infiniteTransition = rememberInfiniteTransition(label = "SplashOrbAnimation")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween( durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbRotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbPulse"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    val orbBrush = Brush.sweepGradient(
        listOf(
            Color(0xFF6C5CE7), // Neon Purple
            Color(0xFF00CEC9), // Cyan Accent
            Color(0xFF0984E3), // Electric Blue
            Color(0xFFE84393), // Magenta Glow
            Color(0xFF6C5CE7)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onSplashFinished()
            },
        contentAlignment = Alignment.Center
    ) {
        // Background Ambient Glow
        Box(
            modifier = Modifier
                .size(320.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6C5CE7).copy(alpha = 0.25f * glowAlpha),
                            Color(0xFF00CEC9).copy(alpha = 0.15f * glowAlpha),
                            Color.Transparent
                        )
                    )
                )
        )

        // Center Glowing Orb (Screen 1 Design)
        Box(
            modifier = Modifier
                .size(260.dp)
                .scale(pulseScale)
                .rotate(rotation)
                .border(width = 3.dp, brush = orbBrush, shape = CircleShape)
                .padding(6.dp)
                .border(
                    width = 1.5.dp,
                    color = Color(0xFF00CEC9).copy(alpha = glowAlpha * 0.7f),
                    shape = CircleShape
                )
        )

        // Center Typography
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "LIFEOS",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Be Ready",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB0B9C6),
                letterSpacing = 0.5.sp
            )

            Text(
                text = "for What's Next",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB0B9C6),
                letterSpacing = 0.5.sp
            )
        }

        // Bottom Footer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 54.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "A Smarter You",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF718096),
                letterSpacing = 1.sp
            )
            Text(
                text = "Everyday",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF718096),
                letterSpacing = 1.sp
            )
        }
    }
}
