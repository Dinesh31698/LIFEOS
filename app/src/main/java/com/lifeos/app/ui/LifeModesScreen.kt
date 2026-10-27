package com.lifeos.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.ui.theme.*

/**
 * Mode Routine descriptor for routine counts & catalog summaries.
 */
data class ModeRoutineSpec(
    val id: Int,
    val modeId: Int,
    val title: String,
    val trigger: String,
    val description: String,
    val isEnabled: Boolean = true
)

// Curated default routines matching wireframe routine counts
val DEFAULT_MODE_ROUTINES = listOf(
    // College: 5 routines
    ModeRoutineSpec(1, 1, "Morning Campus Departure", "Geofence Exit • 8:00 AM", "Verify Laptop, Charger, ID Card, Notebook"),
    ModeRoutineSpec(2, 1, "DBMS Lab Preparation", "Calendar Match • 15m prior", "Promotes DBMS Record to critical priority"),
    ModeRoutineSpec(3, 1, "Library Study Focus", "Location Proximity", "Mutes non-essential alerts & prompts notebook"),
    ModeRoutineSpec(4, 1, "Campus Departure Check", "Campus Geofence Exit", "Audits all gear before leaving university grounds"),
    ModeRoutineSpec(5, 1, "Exam Day Protocol", "Calendar Match", "Heightened alert for Hall Ticket & Stationary"),

    // Sports: 3 routines
    ModeRoutineSpec(6, 4, "Pre-Match Kit Check", "1h Before Match", "Football boots, jersey, and shin guards check"),
    ModeRoutineSpec(7, 4, "Hydration & Nutrition", "30m Before Match", "Energy drink & electrolyte reminder"),
    ModeRoutineSpec(8, 4, "Post-Game Gear Audit", "Sports Facility Exit", "Pack wet gear and confirm laundry checklist"),

    // Gym: 2 routines
    ModeRoutineSpec(9, 2, "Pre-Workout Shake", "30m Before Departure", "Shaker bottle and pre-workout supplement"),
    ModeRoutineSpec(10, 2, "Locker Room Checkout", "Gym Exit Geofence", "Confirm towel, shoes, and locker lock retrieved"),

    // Work: 4 routines
    ModeRoutineSpec(11, 5, "Office Departure Protocol", "Home Exit • 8:30 AM", "Company badge, laptop, charger, notebook"),
    ModeRoutineSpec(12, 5, "Meeting Room Standby", "Calendar Event Match", "Pre-load presentation assets & notebook"),
    ModeRoutineSpec(13, 5, "Desk Clearance Routine", "Office Exit • 6:00 PM", "Access keycard, badge & laptop packed"),
    ModeRoutineSpec(14, 5, "Night Device Recharging", "10:00 PM Schedule", "Reminder to charge work devices overnight"),

    // Travel: 3 routines
    ModeRoutineSpec(15, 3, "Airport Departure Check", "4h Before Departure", "Passport, travel adapter, power bank & tickets"),
    ModeRoutineSpec(16, 3, "Hotel Transit Arrival", "Arrival Geofence", "Adapter setup & power bank recharge"),
    ModeRoutineSpec(17, 3, "Checkout Final Sweep", "2h Before Checkout", "Full room inventory sweep before exit"),

    // Shopping: 2 routines
    ModeRoutineSpec(18, 6, "Store Proximity Alert", "Supermarket Entrance", "Prompt reusable grocery bags & shopping list"),
    ModeRoutineSpec(19, 6, "Checkout Payment Check", "POS Register Proximity", "Wallet, discount cards & receipt confirmation")
)

/**
 * Curated preset templates for the "Templates" tab
 */
data class ModeTemplate(
    val name: String,
    val iconRes: String,
    val icon: ImageVector,
    val iconColor: Color,
    val description: String,
    val itemNames: List<String>,
    val routineCount: Int
)

val PRESET_TEMPLATES = listOf(
    ModeTemplate(
        name = "Exam Season",
        iconRes = "ic_exam",
        icon = Icons.Default.School,
        iconColor = PrimaryPurple,
        description = "High-stakes academic preparation checklist and strict notification silencing.",
        itemNames = listOf("Admit Card / Hall Ticket", "Scientific Calculator", "Geometry Box", "Transparent Water Bottle", "Wristwatch"),
        routineCount = 4
    ),
    ModeTemplate(
        name = "Hackathon / Tech Fest",
        iconRes = "ic_hackathon",
        icon = Icons.Default.Work,
        iconColor = CyanAccent,
        description = "Non-stop sprint mode with power backup and essential hardware kit.",
        itemNames = listOf("Laptop & 100W Charger", "Power Strip / Spike", "Ethernet Dongle", "Toothbrush & Towel", "Energy Snacks"),
        routineCount = 3
    ),
    ModeTemplate(
        name = "Weekend Trekking",
        iconRes = "ic_trekking",
        icon = Icons.AutoMirrored.Filled.DirectionsRun,
        iconColor = SuccessGreen,
        description = "Outdoor adventure readiness with hydration, emergency beacon, and weather gear.",
        itemNames = listOf("Hiking Shoes & Socks", "2L Hydration Bladder", "First Aid Kit", "Waterproof Poncho", "High-Lumen Flashlight"),
        routineCount = 4
    ),
    ModeTemplate(
        name = "Client Meeting",
        iconRes = "ic_client",
        icon = Icons.Default.Work,
        iconColor = WarningAmber,
        description = "Executive professional setting with business collateral and presentation clicker.",
        itemNames = listOf("Business Cards", "Presentation Clicker", "iPad / Tablet", "Notebook & Pen", "Formal Blazer"),
        routineCount = 2
    )
)

/**
 * Sprint 4: Life Modes Catalog Screen (Screen 6 in wireframe)
 */
@Composable
fun LifeModesScreen(
    viewModel: MainViewModel,
    onOpenModeDetail: (LifeModeEntity) -> Unit
) {
    val modes by viewModel.allModes.collectAsState()
    val activeMode by viewModel.activeMode.collectAsState()
    val allItems by viewModel.allItems.collectAsState()

    // 0 = My Modes, 1 = Templates
    var selectedTab by remember { mutableStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val itemsByMode = remember(allItems) { allItems.groupBy { it.modeId } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp)
    ) {
        // 1. Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Life Modes",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Contextual environments & smart routines",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // Top '+' Action Button
            IconButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Mode",
                    tint = CyanAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 2. Segmented Tab Bar: My Modes vs. Templates
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tab 0: My Modes
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 0) PrimaryPurple else Color.Transparent)
                        .clickable { selectedTab = 0 },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "My Modes",
                        fontSize = 13.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == 0) Color.White else TextSecondary
                    )
                }

                // Tab 1: Templates
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 1) PrimaryPurple else Color.Transparent)
                        .clickable { selectedTab = 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Templates",
                        fontSize = 13.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == 1) Color.White else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content Area
        if (selectedTab == 0) {
            // Tab 0: 2-Column Grid of My Modes
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(modes, key = { it.id }) { mode ->
                    val isActive = mode.id == activeMode?.id
                    val modeItems = itemsByMode[mode.id] ?: emptyList()
                    val routineCount = DEFAULT_MODE_ROUTINES.count { it.modeId == mode.id }.coerceAtLeast(2)
                    val modeConfig = getModeConfig(mode.name)

                    val readinessText = if (modeItems.isEmpty()) {
                        "Not Set"
                    } else {
                        val readyCount = modeItems.count { it.isReady || it.isWaived }
                        "${(readyCount * 100 / modeItems.size)}% Ready"
                    }

                    ModeCard(
                        mode = mode,
                        isActive = isActive,
                        itemCount = modeItems.size,
                        routineCount = routineCount,
                        readinessText = readinessText,
                        icon = modeConfig.icon,
                        accentColor = modeConfig.accentColor,
                        onCardClick = {
                            viewModel.selectModeForDetail(mode.id)
                            onOpenModeDetail(mode)
                        },
                        onActivateClick = { viewModel.setActiveMode(mode.id) }
                    )
                }
            }

        } else {
            // Tab 1: Mode Templates List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(PRESET_TEMPLATES) { template ->
                    TemplateCard(
                        template = template,
                        onUseTemplate = {
                            viewModel.importTemplateMode(
                                templateName = template.name,
                                iconRes = template.iconRes,
                                items = template.itemNames
                            )
                            selectedTab = 0
                        }
                    )
                }
            }
        }
    }

    // 3. Create Custom Mode Dialog
    if (showCreateDialog) {
        CreateModeDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, iconRes, priority ->
                viewModel.createMode(name, iconRes, priority)
                showCreateDialog = false
            }
        )
    }
}

/**
 * 2-Column Grid Item Card matching Screen 6 wireframe
 */
@Composable
fun ModeCard(
    mode: LifeModeEntity,
    isActive: Boolean,
    itemCount: Int,
    routineCount: Int,
    readinessText: String,
    icon: ImageVector,
    accentColor: Color,
    onCardClick: () -> Unit,
    onActivateClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(
            if (isActive) 2.dp else 1.dp,
            if (isActive) accentColor else BorderDark
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: Icon Squircle + Active / Readiness Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colored Icon Squircle
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = mode.name,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessGreen.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = mode.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Readiness Pill: Shows e.g. "87% Ready" or "Not Set" (Never fake 100%)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (readinessText == "Not Set") Color(0x1A64748B) else accentColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (readinessText == "Not Set") BorderDark else accentColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = readinessText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (readinessText == "Not Set") TextMuted else accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "$itemCount items",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Affordance Button
            if (!isActive) {
                OutlinedButton(
                    onClick = onActivateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, BorderDark),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Activate",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanAccent
                    )
                }
            } else {
                Button(
                    onClick = onCardClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Manage Mode",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}


/**
 * Template Card for preset modes
 */
@Composable
fun TemplateCard(
    template: ModeTemplate,
    onUseTemplate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(template.iconColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = template.icon,
                        contentDescription = template.name,
                        tint = template.iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${template.itemNames.size} items • ${template.routineCount} routines",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = onUseTemplate,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "+ Use",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = template.description,
                fontSize = 12.sp,
                color = TextMuted,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Dialog to create a new Life Mode
 */
@Composable
fun CreateModeDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, iconRes: String, priority: Int) -> Unit
) {
    var modeName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("ic_college") }
    var priority by remember { mutableStateOf(1) }

    val iconOptions = listOf(
        Pair("ic_college", Icons.Default.School),
        Pair("ic_sports", Icons.AutoMirrored.Filled.DirectionsRun),
        Pair("ic_gym", Icons.Default.FitnessCenter),
        Pair("ic_work", Icons.Default.Work),
        Pair("ic_travel", Icons.Default.Flight),
        Pair("ic_shopping", Icons.Default.ShoppingCart)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Create Life Mode", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = modeName,
                    onValueChange = { modeName = it },
                    label = { Text("Mode Name") },
                    placeholder = { Text("e.g. Research, Hiking") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Select Icon:",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    iconOptions.forEach { (res, vector) ->
                        val isSelected = selectedIcon == res
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) PrimaryPurple else SurfaceDark)
                                .clickable { selectedIcon = res },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = res,
                                tint = if (isSelected) Color.White else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (modeName.isNotBlank()) {
                        onConfirm(modeName.trim(), selectedIcon, priority)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Create Mode")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CardDark,
        shape = RoundedCornerShape(18.dp)
    )
}

/**
 * Visual styling configuration helper for mode cards
 */
data class ModeVisualConfig(
    val icon: ImageVector,
    val accentColor: Color
)

fun getModeConfig(modeName: String): ModeVisualConfig {
    return when (modeName.lowercase().trim()) {
        "college" -> ModeVisualConfig(Icons.Default.School, ContextCollege)
        "sports" -> ModeVisualConfig(Icons.AutoMirrored.Filled.DirectionsRun, ContextSports)
        "gym" -> ModeVisualConfig(Icons.Default.FitnessCenter, ContextGym)
        "work" -> ModeVisualConfig(Icons.Default.Work, ContextWork)
        "travel" -> ModeVisualConfig(Icons.Default.Flight, ContextTravel)
        "home" -> ModeVisualConfig(Icons.Default.Home, ContextHome)
        "study" -> ModeVisualConfig(Icons.Default.Star, ContextStudy)
        else -> ModeVisualConfig(Icons.Default.Place, CyanAccent)
    }
}

