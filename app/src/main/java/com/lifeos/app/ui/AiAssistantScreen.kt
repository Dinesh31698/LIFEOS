package com.lifeos.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.ChatMessage
import com.lifeos.app.ChatSender
import com.lifeos.app.MainViewModel
import com.lifeos.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AiAssistantScreen(
    viewModel: MainViewModel,
    onNavigateToGhostCenter: () -> Unit = {},
    onNavigateToReadiness: () -> Unit = {}
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val activeItems by viewModel.activeItems.collectAsState()
    val readinessReport by viewModel.readinessReport.collectAsState()
    val appliedProposalIds by viewModel.appliedProposalIds.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val promptSuggestions = listOf(
        "I'm going to college",
        "DBMS record teesuko",
        "Cancel DBMS lab tomorrow",
        "Pack shaker bottle for gym",
        "Chemistry lab coat repu kavali"
    )

    // Auto-scroll when new messages arrive
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

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
                Column {
                    Text(
                        text = "LIFEOS AI",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Pulsing Green Online Indicator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Text(
                            text = "Online (Local)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = SuccessGreen
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.clearChat() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceDark)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear Chat",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onNavigateToGhostCenter,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceDark)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundDark)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Suggestion Chips Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(promptSuggestions) { suggestion ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceDark,
                            border = BorderStroke(1.dp, BorderDark),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.sendChatMessage(suggestion)
                                }
                        ) {
                            Text(
                                text = suggestion,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Floating Input Bar
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = inputPrompt,
                            onValueChange = { inputPrompt = it },
                            placeholder = {
                                Text(
                                    text = "Ask anything...",
                                    fontSize = 14.sp,
                                    color = TextMuted
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputPrompt.isNotBlank()) {
                                        viewModel.sendChatMessage(inputPrompt)
                                        inputPrompt = ""
                                    }
                                }
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Mic Button
                        IconButton(
                            onClick = {
                                // Simulate Voice Input
                                inputPrompt = "I'm going to college"
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Send Button
                        IconButton(
                            onClick = {
                                if (inputPrompt.isNotBlank()) {
                                    viewModel.sendChatMessage(inputPrompt)
                                    inputPrompt = ""
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PrimaryPurple)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
        ) {
            items(chatMessages, key = { it.id }) { message ->
                if (message.sender == ChatSender.USER) {
                    UserMessageBubble(message = message)
                } else {
                    AiMessageCard(
                        message = message,
                        liveScore = readinessReport.percentage,
                        isDbmsWaived = 99 in appliedProposalIds || activeItems.find { it.itemName.contains("DBMS Record", ignoreCase = true) }?.isWaived == true,
                        onNavigateToReadiness = onNavigateToReadiness
                    )
                }
            }
        }
    }
}

@Composable
fun UserMessageBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp),
            color = PrimaryPurple,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message.timestamp,
            fontSize = 11.sp,
            color = TextMuted
        )
    }
}

@Composable
fun AiMessageCard(
    message: ChatMessage,
    liveScore: Int,
    isDbmsWaived: Boolean,
    onNavigateToReadiness: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 340.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Main AI Response Card
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // If message includes checklist items
                if (message.readinessItems != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        message.readinessItems.forEach { (itemName, isDefaultReady) ->
                            val isWaived = itemName.contains("DBMS Record", ignoreCase = true) && isDbmsWaived
                            val isReady = if (isWaived) true else isDefaultReady

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isWaived) {
                                            Icons.Default.RemoveCircleOutline
                                        } else if (isReady) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.Warning
                                        },
                                        contentDescription = null,
                                        tint = if (isWaived) CyanAccent else if (isReady) SuccessGreen else AlertRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = itemName,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                }

                                Text(
                                    text = if (isWaived) "Waived" else if (isReady) "Ready" else "Missing",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isWaived) CyanAccent else if (isReady) SuccessGreen else AlertRed
                                )
                            }
                        }
                    }

                    // Embedded Readiness Metric Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardDark,
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Readiness",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = if (isDbmsWaived) "83%" else "${message.readinessPercentage ?: liveScore}%",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onNavigateToReadiness() }
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Show Details",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryPurple
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Follow-up recommendation card (if present)
        if (message.suggestion != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardDark,
                border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PrimaryPurple.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = if (isDbmsWaived) "DBMS Lab cancelled. Record requirement waived." else message.suggestion,
                        fontSize = 12.sp,
                        color = TextPrimary,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Text(
            text = message.timestamp,
            fontSize = 11.sp,
            color = TextMuted,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
