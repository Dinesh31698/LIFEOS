package com.lifeos.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.lifeos.app.data.MemoryEntity
import com.lifeos.app.engine.MemoryTtlOption
import com.lifeos.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryVaultScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResultAnswer by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("ALL", "TEMPORARY", "PINNED", "NOTES", "MEMORIES")

    val memoriesState = viewModel.memories.collectAsState()
    val allMemories = memoriesState.value
    val filteredMemories: List<MemoryEntity> = remember(allMemories, selectedTab) {
        val now = System.currentTimeMillis()
        val nonExpired = allMemories.filter { it.isPinned || it.expiresAt > now }
        when (selectedTab) {
            1 -> nonExpired.filter { !it.isPinned && it.memoryType != "PINNED" }
            2 -> nonExpired.filter { it.isPinned || it.memoryType == "PINNED" }
            3 -> nonExpired.filter { it.tags.contains("note", ignoreCase = true) || it.content.length > 50 }
            4 -> nonExpired.filter { !it.tags.contains("note", ignoreCase = true) }
            else -> nonExpired
        }
    }



    var showAddDialog by remember { mutableStateOf(false) }
    var newMemoryContent by remember { mutableStateOf("") }
    var selectedTtl by remember { mutableStateOf(MemoryTtlOption.TWENTY_FOUR_HOURS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory Vault", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Save Memory", tint = CyanAccent)
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
            // 1. Ask LIFEOS Search Bar
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Ask LIFEOS",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("e.g. Where did I put my locker key?") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            IconButton(
                                onClick = {
                                    if (searchQuery.isNotBlank()) {
                                        isSearching = true
                                        viewModel.askMemory(searchQuery) { _, answer ->
                                            searchResultAnswer = answer
                                            isSearching = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(CyanAccent, RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = BackgroundDark)
                            }
                        }

                        if (searchResultAnswer != null) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "💡 $searchResultAnswer",
                                    fontSize = 13.sp,
                                    color = CyanAccent,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Tab Row
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceDark,
                    contentColor = CyanAccent
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                        )
                    }
                }
            }

            // 3. Stored Memories List
            if (filteredMemories.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No stored memories found. Use '+ Add' to save a note with explicit TTL.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(filteredMemories.size) { index ->
                    val memory = filteredMemories[index]
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = memory.content,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val ttlLabel = if (memory.isPinned || memory.memoryType == "PINNED") {
                                    "◆ Saved Memory"
                                } else {
                                    val remHours = ((memory.expiresAt - System.currentTimeMillis()) / 3600000L).coerceAtLeast(0)
                                    "⏳ Expires in ${remHours}h"
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (memory.isPinned) ContextTravel.copy(alpha = 0.2f) else WarningAmber.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, if (memory.isPinned) ContextTravel.copy(alpha = 0.5f) else WarningAmber.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = ttlLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (memory.isPinned) ContextTravel else WarningAmber,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                            }

                            IconButton(onClick = { viewModel.deleteMemory(memory.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertRed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Save to Memory Vault", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Explicit Storage Rule: LIFEOS only saves notes you choose to preserve.", fontSize = 11.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = newMemoryContent,
                        onValueChange = { newMemoryContent = it },
                        label = { Text("Memory Content / Note") },
                        placeholder = { Text("e.g. Locker key in top drawer") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Select Retention / Expiration:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedTtl == MemoryTtlOption.TWENTY_FOUR_HOURS,
                            onClick = { selectedTtl = MemoryTtlOption.TWENTY_FOUR_HOURS },
                            label = { Text("24h") }
                        )
                        FilterChip(
                            selected = selectedTtl == MemoryTtlOption.SEVEN_DAYS,
                            onClick = { selectedTtl = MemoryTtlOption.SEVEN_DAYS },
                            label = { Text("7d") }
                        )
                        FilterChip(
                            selected = selectedTtl == MemoryTtlOption.PERMANENT,
                            onClick = { selectedTtl = MemoryTtlOption.PERMANENT },
                            label = { Text("Pinned") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newMemoryContent.isNotBlank()) {
                            viewModel.saveMemory(
                                content = newMemoryContent.trim(),
                                memoryType = if (selectedTtl == MemoryTtlOption.PERMANENT) "PINNED" else "SHORT_TERM",
                                ttlOption = selectedTtl
                            )
                            newMemoryContent = ""
                            showAddDialog = false
                        }
                    }
                ) { Text("Save Memory") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}
