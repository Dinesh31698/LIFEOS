package com.lifeos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.data.UserProfileEntity
import com.lifeos.app.engine.UserProfileEngine
import com.lifeos.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val userProfileState = viewModel.userProfile.collectAsState()
    val profile = userProfileState.value ?: UserProfileEntity(preferredName = "Dinesh")


    var name by remember(profile) { mutableStateOf(profile.preferredName) }
    var pronunciationHint by remember(profile) { mutableStateOf(profile.pronunciationHint ?: "") }
    var selectedRole by remember(profile) { mutableStateOf(profile.primaryRole) }
    var selectedLanguage by remember(profile) { mutableStateOf(profile.preferredLanguage) }
    var wakeTarget by remember(profile) { mutableStateOf(profile.wakeTarget) }
    var bedtimeTarget by remember(profile) { mutableStateOf(profile.bedtimeTarget) }
    var commuteMode by remember(profile) { mutableStateOf(profile.defaultCommuteMode) }
    var commuteMinutes by remember(profile) { mutableStateOf(profile.typicalCommuteMinutes.toString()) }
    var isTrackingPaused by remember(profile) { mutableStateOf(profile.trackingPaused) }
    var previewMessage by remember { mutableStateOf<String?>(null) }
    var showSavedSnackbar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My LIFEOS",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val updated = profile.copy(
                                preferredName = name.trim().ifEmpty { "Dinesh" },
                                pronunciationHint = pronunciationHint.trim().ifEmpty { null },
                                primaryRole = selectedRole,
                                preferredLanguage = selectedLanguage,
                                wakeTarget = wakeTarget,
                                sleepTarget = bedtimeTarget,
                                bedtimeTarget = bedtimeTarget,
                                defaultCommuteMode = commuteMode,
                                typicalCommuteMinutes = commuteMinutes.toIntOrNull() ?: 30,
                                trackingPaused = isTrackingPaused
                            )
                            viewModel.updateUserProfile(updated)
                            showSavedSnackbar = true
                        }
                    ) {
                        Text("Save", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
            // 1. Identity Header Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(PrimaryPurple, CyanAccent))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.firstOrNull()?.uppercase() ?: "U",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = UserProfileEngine.generateGreeting(profile),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Role: $selectedRole • 100% Local Storage",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // 2. Name & Pronunciation Section
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Identity & Name",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Preferred Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = CyanAccent
                            )
                        )
                        OutlinedTextField(
                            value = pronunciationHint,
                            onValueChange = { pronunciationHint = it },
                            label = { Text("Phonetic Pronunciation Hint (Optional)") },
                            placeholder = { Text("e.g. Phonetic spelling") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = CyanAccent
                            )
                        )
                        Button(
                            onClick = {
                                val dummy = profile.copy(preferredName = name, pronunciationHint = pronunciationHint)
                                previewMessage = UserProfileEngine.generatePronunciationPreview(dummy)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Test", tint = CyanAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Spoken Pronunciation Preview", color = TextPrimary)
                        }
                        if (previewMessage != null) {
                            Text(
                                text = "Preview: \"$previewMessage\"",
                                fontSize = 12.sp,
                                color = SuccessGreen,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // 3. Primary Role
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Primary Role",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        UserProfileEngine.SUPPORTED_ROLES.chunked(3).forEach { chunk ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                chunk.forEach { role ->
                                    FilterChip(
                                        selected = selectedRole == role,
                                        onClick = { selectedRole = role },
                                        label = { Text(role) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Routine & Commute Targets
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Routine & Commute Baseline",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = wakeTarget,
                                onValueChange = { wakeTarget = it },
                                label = { Text("Wake Target") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            OutlinedTextField(
                                value = bedtimeTarget,
                                onValueChange = { bedtimeTarget = it },
                                label = { Text("Bedtime Target") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = commuteMode,
                                onValueChange = { commuteMode = it },
                                label = { Text("Commute Mode") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            OutlinedTextField(
                                value = commuteMinutes,
                                onValueChange = { commuteMinutes = it },
                                label = { Text("Duration (m)") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                        }
                    }
                }
            }

            // 5. Privacy & Tracking Kill-Switch
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Privacy Controls",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pause Sensor Tracking", color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text("Temporarily halts background motion and place tracking", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = isTrackingPaused,
                                onCheckedChange = { isTrackingPaused = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent)
                            )
                        }
                    }
                }
            }
        }
    }
}
