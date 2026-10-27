package com.lifeos.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.engine.HandsFreeVoiceManager
import com.lifeos.app.engine.VoiceState
import com.lifeos.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val voiceState by viewModel.voiceState.collectAsState()
    val currentLang by viewModel.currentVoiceLanguage.collectAsState()
    var spokenResponseText by remember { mutableStateOf<String?>(null) }
    var inputQuery by remember { mutableStateOf("") }

    // Pulsing animation for the Voice Orb
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val orbColor = when (voiceState) {
        VoiceState.IDLE -> listOf(PrimaryPurple.copy(alpha = 0.8f), CyanAccent.copy(alpha = 0.8f))
        VoiceState.LISTENING -> listOf(CyanAccent, SuccessGreen)
        VoiceState.PROCESSING -> listOf(WarningAmber, PrimaryPurple)
        VoiceState.SPEAKING -> listOf(SuccessGreen, CyanAccent)
        VoiceState.ERROR, VoiceState.UNAVAILABLE -> listOf(AlertRed, SurfaceDark)
        VoiceState.ARMED -> listOf(PrimaryPurple, WarningAmber)
    }

    val sampleQueries = listOf(
        "Show my college readiness",
        "Add water bottle to sports",
        "Where did I put my locker key?",
        "What's next on my schedule?",
        "Move gym to 6 PM"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Assistant", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Hardware Trigger Capability Notice
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("ℹ️", fontSize = 16.sp)
                    Column {
                        Text(
                            text = "Hardware Wake: PLATFORM LIMITED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber
                        )
                        Text(
                            text = "5x Volume Up wake is not available on standard Android. Supported trigger: Quick Settings Tile, Lock Screen action, or in-app Voice Orb.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // 2. Animated Glass Voice Orb Centerpiece
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                val orbState = when (voiceState) {
                    VoiceState.IDLE -> com.lifeos.app.ui.components.VoiceOrbState.IDLE
                    VoiceState.LISTENING -> com.lifeos.app.ui.components.VoiceOrbState.LISTENING
                    VoiceState.PROCESSING -> com.lifeos.app.ui.components.VoiceOrbState.PROCESSING
                    VoiceState.SPEAKING -> com.lifeos.app.ui.components.VoiceOrbState.SPEAKING
                    VoiceState.ERROR, VoiceState.UNAVAILABLE -> com.lifeos.app.ui.components.VoiceOrbState.COMPLETE
                    VoiceState.ARMED -> com.lifeos.app.ui.components.VoiceOrbState.PROCESSING
                }

                com.lifeos.app.ui.components.GlassVoiceOrb(
                    state = orbState,
                    modifier = Modifier.size(170.dp),
                    accentColor = when (voiceState) {
                        VoiceState.LISTENING -> CyanAccent
                        VoiceState.SPEAKING -> SuccessGreen
                        VoiceState.PROCESSING -> WarningAmber
                        else -> PurpleLight
                    },
                    onClick = {
                        if (voiceState == VoiceState.IDLE) {
                            viewModel.setVoiceState(VoiceState.LISTENING)
                        } else {
                            viewModel.setVoiceState(VoiceState.IDLE)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "State: ${voiceState.name}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (voiceState) {
                        VoiceState.LISTENING -> CyanAccent
                        VoiceState.SPEAKING -> SuccessGreen
                        VoiceState.PROCESSING -> WarningAmber
                        else -> TextPrimary
                    }
                )
                Text(
                    text = "Language: $currentLang • On-Device Model",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                if (spokenResponseText != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = spokenResponseText ?: "",
                            fontSize = 13.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // 3. Spoken Utterance Test Bar & Suggestions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Suggested Voice Inquiries:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sampleQueries) { query ->
                        SuggestionChip(
                            onClick = {
                                inputQuery = query
                                viewModel.processVoiceUtterance(query) { result ->
                                    spokenResponseText = result.spokenResponse
                                }
                            },
                            label = { Text(query, fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        placeholder = { Text("Speak or type command...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    IconButton(
                        onClick = {
                            if (inputQuery.isNotBlank()) {
                                viewModel.processVoiceUtterance(inputQuery) { result ->
                                    spokenResponseText = result.spokenResponse
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CyanAccent)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Run", tint = BackgroundDark)
                    }
                }
            }
        }
    }
}
