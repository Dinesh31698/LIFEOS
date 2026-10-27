package com.lifeos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.data.EmergencyContactEntity
import com.lifeos.app.engine.EmergencySosManager
import com.lifeos.app.engine.SosDispatchResult
import com.lifeos.app.engine.SosState
import com.lifeos.app.security.DataMinimizer
import com.lifeos.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val sosState by viewModel.sosState.collectAsState()
    val countdown by viewModel.countdownRemainingSeconds.collectAsState()
    val isTestMode by viewModel.isSosTestMode.collectAsState()
    val contacts by viewModel.emergencyContacts.collectAsState()

    var dispatchResult by remember { mutableStateOf<SosDispatchResult?>(null) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactRelation by remember { mutableStateOf("Family") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency SOS", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddContactDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Contact", tint = AlertRed)
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
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Test Mode Toggle Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SOS Test Mode", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Simulates the countdown and emergency flow without contacting real authorities or contacts", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = isTestMode,
                            onCheckedChange = { viewModel.setSosTestMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent)
                        )
                    }
                }
            }

            // 2. SOS Trigger Button or Countdown Box
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = androidx.compose.foundation.BorderStroke(2.dp, if (sosState == SosState.COUNTDOWN) WarningAmber else AlertRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (sosState == SosState.COUNTDOWN) {
                            Text(
                                text = "SOS ACTIVATING IN",
                                fontWeight = FontWeight.Bold,
                                color = WarningAmber,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "$countdown",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 56.sp,
                                color = AlertRed
                            )
                            Button(
                                onClick = { viewModel.cancelSos() },
                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("CANCEL SOS", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .background(AlertRed)
                                    .clickable {
                                        viewModel.startSosCountdown(5)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Warning, contentDescription = "SOS", tint = Color.White, modifier = Modifier.size(36.dp))
                                    Text("SOS", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
                                }
                            }
                            Text(
                                text = if (isTestMode) "TAP TO RUN SOS SIMULATION" else "TAP TO INITIATE EMERGENCY SOS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isTestMode) CyanAccent else AlertRed
                            )
                            Text(
                                text = "5-second cancellation grace period before dispatch.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        if (sosState == SosState.ACTIVE || sosState == SosState.COMPLETED) {
                            Button(
                                onClick = {
                                    viewModel.triggerSosDispatch(context) { result ->
                                        dispatchResult = result
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                            ) {
                                Text("Dispatch Alert", color = BackgroundDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. Dispatch Result Card
            if (dispatchResult != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Dispatch Status: ${dispatchResult?.status?.name}", fontWeight = FontWeight.Bold, color = CyanAccent)
                            Text(dispatchResult?.statusMessage ?: "", fontSize = 12.sp, color = TextPrimary)
                            Text(
                                text = "Location Attached: ${if (dispatchResult?.locationAttached == true) "YES" else "STALE / UNAVAILABLE"}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // 4. Emergency Contacts List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Emergency Contacts", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    TextButton(onClick = { showAddContactDialog = true }) {
                        Text("+ Add Contact", color = AlertRed, fontSize = 12.sp)
                    }
                }
            }

            if (contacts.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No emergency contacts configured. Tap '+ Add Contact' to specify your trusted recipients.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(contacts.size) { index ->
                    val contact = contacts[index]
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
                            Column {
                                Text(contact.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(
                                    text = "${contact.relationship} • ${DataMinimizer.maskPhoneNumber(contact.phoneNumber)}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            IconButton(onClick = { viewModel.deleteEmergencyContact(contact.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertRed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = { Text("Add Emergency Contact", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Contact Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = contactRelation,
                        onValueChange = { contactRelation = it },
                        label = { Text("Relationship (Parent, Friend, etc.)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (contactName.isNotBlank() && contactPhone.isNotBlank()) {
                            viewModel.addEmergencyContact(
                                name = contactName.trim(),
                                phoneNumber = contactPhone.trim(),
                                relationship = contactRelation.trim()
                            )
                            contactName = ""
                            contactPhone = ""
                            showAddContactDialog = false
                        }
                    }
                ) { Text("Save Contact") }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) { Text("Cancel") }
            }
        )
    }
}
