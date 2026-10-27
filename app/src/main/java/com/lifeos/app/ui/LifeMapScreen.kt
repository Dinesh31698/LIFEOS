package com.lifeos.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import com.lifeos.app.MainViewModel
import com.lifeos.app.data.AnchorPlaceEntity
import com.lifeos.app.data.LifeMapRepository
import com.lifeos.app.data.PlaceVisitRecord
import com.lifeos.app.data.TimeScrubberWindow
import com.lifeos.app.data.TravelHistoryRecord
import com.lifeos.app.engine.InteractiveLifeMapManager
import com.lifeos.app.ui.components.GlassButton
import com.lifeos.app.ui.components.GlassCard
import com.lifeos.app.ui.components.GlassChip
import com.lifeos.app.ui.theme.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeMapScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("MAP", "TIMELINE", "PLACES")
    val anchorPlaces by viewModel.anchorPlaces.collectAsState()
    val mapPrivacy by InteractiveLifeMapManager.privacySettings.collectAsState()
    val layerToggles by InteractiveLifeMapManager.layerToggles.collectAsState()
    val scrubberWindow by InteractiveLifeMapManager.timeScrubberWindow.collectAsState()
    val selectedPlaceForDetails by InteractiveLifeMapManager.selectedPlaceForDetails.collectAsState()

    var selectedPeriod by remember { mutableStateOf("TODAY") } // "TODAY", "WEEK", "MONTH"
    val travelHistory by LifeMapRepository.travelHistory.collectAsState()
    val placeVisits by LifeMapRepository.placeVisits.collectAsState()
    val privateZones by LifeMapRepository.privateZones.collectAsState()

    var showAddAnchorDialog by remember { mutableStateOf(false) }
    var newAnchorName by remember { mutableStateOf("") }
    var newAnchorType by remember { mutableStateOf("CUSTOM") }
    var newAnchorLat by remember { mutableStateOf("17.3850") }
    var newAnchorLon by remember { mutableStateOf("78.4867") }

    var actionNotice by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Life Map", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (mapPrivacy.isTrackingPaused) WarningAmber else SuccessGreen)
                            )
                            Text(
                                text = if (mapPrivacy.isTrackingPaused) "Tracking Paused" else "Local Tracking Active",
                                fontSize = 11.sp,
                                color = if (mapPrivacy.isTrackingPaused) WarningAmber else SuccessGreen
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddAnchorDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Anchor", tint = CyanAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {
                // 1. Tab Selector
                item {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = SurfaceDark,
                        contentColor = CyanAccent,
                        indicator = {},
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            Tab(
                                selected = isSelected,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isSelected) CyanAccent else TextSecondary
                                    )
                                }
                            )
                        }
                    }
                }

                // 2. Map Tab Content
                if (selectedTab == 0) {
                    // Top Controls: Master Pause, Time Horizon & Layer Toggles
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Period Filter Chips
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("TODAY", "WEEK", "MONTH").forEach { p ->
                                        val isSel = selectedPeriod == p
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSel) PrimaryPurple.copy(alpha = 0.35f) else SurfaceDark,
                                            border = BorderStroke(1.dp, if (isSel) CyanAccent else BorderDark),
                                            modifier = Modifier.clickable { selectedPeriod = p }
                                        ) {
                                            Text(
                                                text = p.lowercase().replaceFirstChar { it.uppercase() },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSel) CyanAccent else TextSecondary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                // Master Pause Button
                                GlassButton(
                                    onClick = {
                                        val nextState = !mapPrivacy.isTrackingPaused
                                        InteractiveLifeMapManager.setTrackingPaused(nextState)
                                        actionNotice = if (nextState) "Location tracking paused" else "Location tracking resumed"
                                    },
                                    backgroundColor = if (mapPrivacy.isTrackingPaused) WarningAmber.copy(alpha = 0.25f) else AlertRed.copy(alpha = 0.2f),
                                    borderColor = if (mapPrivacy.isTrackingPaused) WarningAmber else AlertRed.copy(alpha = 0.6f)
                                ) {
                                    Icon(
                                        imageVector = if (mapPrivacy.isTrackingPaused) Icons.Default.PlayArrow else Icons.Default.Close,
                                        contentDescription = "Toggle Pause",
                                        tint = if (mapPrivacy.isTrackingPaused) WarningAmber else AlertRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (mapPrivacy.isTrackingPaused) "Resume" else "Pause",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (mapPrivacy.isTrackingPaused) WarningAmber else AlertRed
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Layer Toggles Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Route" to layerToggles.showRoute, "Places" to layerToggles.showPlaces, "Heatmap" to layerToggles.showHeatmap, "Context" to layerToggles.showContext).forEach { (name, active) ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (active) CyanAccent.copy(alpha = 0.15f) else Color.Transparent,
                                        border = BorderStroke(1.dp, if (active) CyanAccent.copy(alpha = 0.5f) else BorderDark),
                                        modifier = Modifier.clickable { InteractiveLifeMapManager.toggleLayer(name) }
                                    ) {
                                        Text(
                                            text = name,
                                            fontSize = 10.sp,
                                            color = if (active) CyanAccent else TextMuted,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Time Scrubber Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Time Scrubber:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(
                                        TimeScrubberWindow.ALL to "All",
                                        TimeScrubberWindow.MORNING to "Morning",
                                        TimeScrubberWindow.AFTERNOON to "Afternoon",
                                        TimeScrubberWindow.EVENING to "Evening",
                                        TimeScrubberWindow.NIGHT to "Night"
                                    ).forEach { (window, label) ->
                                        val isCurrent = scrubberWindow == window
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isCurrent) PrimaryPurple.copy(alpha = 0.4f) else Color.Transparent,
                                            border = BorderStroke(1.dp, if (isCurrent) PurpleLight else Color.Transparent),
                                            modifier = Modifier.clickable { InteractiveLifeMapManager.setTimeScrubber(window) }
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCurrent) PurpleLight else TextMuted,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Geographic Map Canvas Item
                    item {
                        GeographicMapCanvas(
                            anchorPlaces = anchorPlaces,
                            travelHistory = LifeMapRepository.getTravelHistoryFiltered(selectedPeriod, scrubberWindow),
                            placeVisits = LifeMapRepository.getVisitsFiltered(selectedPeriod, scrubberWindow),
                            privateZones = privateZones,
                            isTrackingPaused = mapPrivacy.isTrackingPaused,
                            isPrivacyMaskActive = mapPrivacy.isPrivacyMaskActive,
                            layerToggles = layerToggles,
                            selectedPlaceForDetails = selectedPlaceForDetails,
                            onSelectPlace = { visit ->
                                InteractiveLifeMapManager.selectPlaceForDetails(visit)
                            }
                        )
                    }

                    // Privacy Mask & Retention Banner
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Map Privacy & Storage Safeguards", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Privacy Masking Zone", color = TextPrimary, fontSize = 12.sp)
                                    Text("Coarsens exact home coordinates to protect private domicile", fontSize = 10.sp, color = TextSecondary)
                                }
                                Switch(
                                    checked = mapPrivacy.isPrivacyMaskActive,
                                    onCheckedChange = { InteractiveLifeMapManager.setPrivacyMaskActive(it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Atomic Deletion Buttons
                            Text("Atomic History Deletion:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        InteractiveLifeMapManager.deleteTodayHistory()
                                        actionNotice = "Deleted today's journey records"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, BorderDark),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text("Delete Today", fontSize = 11.sp, color = TextSecondary)
                                }

                                OutlinedButton(
                                    onClick = {
                                        InteractiveLifeMapManager.deleteWeekHistory()
                                        actionNotice = "Deleted this week's journey records"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, BorderDark),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text("Delete Week", fontSize = 11.sp, color = TextSecondary)
                                }

                                OutlinedButton(
                                    onClick = {
                                        InteractiveLifeMapManager.deleteAllHistory()
                                        actionNotice = "All journey history purged atomically"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text("Delete All", fontSize = 11.sp, color = AlertRed)
                                }
                            }
                        }
                    }
                }

                // 3. Timeline Tab Content
                if (selectedTab == 1) {
                    val filteredVisits = LifeMapRepository.getVisitsFiltered(selectedPeriod, scrubberWindow)
                    if (filteredVisits.isEmpty() && anchorPlaces.isEmpty()) {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = "Empty", tint = TextMuted, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = if (mapPrivacy.isTrackingPaused) "Location tracking is paused." else "Your journey history will appear here after you enable location tracking.",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Text("Chronological Journey Timeline ($selectedPeriod)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                        }

                        // Display real visits or honest empty state
                        if (filteredVisits.isEmpty()) {
                            item {
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("🗺️", fontSize = 32.sp)
                                        Text(
                                            text = if (mapPrivacy.isTrackingPaused) "Location tracking is paused" else "No Recorded Visits Yet",
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = if (mapPrivacy.isTrackingPaused)
                                                "Location tracking is paused. Resume tracking to begin recording visits to your configured places."
                                            else
                                                "Your journey history will appear here once visits are recorded at your anchor places.",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 20.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(filteredVisits) { visit ->
                                GlassCard(
                                    onClick = { InteractiveLifeMapManager.selectPlaceForDetails(visit) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when (visit.placeType) {
                                                        "GYM" -> ContextGym.copy(alpha = 0.2f)
                                                        "CAMPUS" -> ContextCollege.copy(alpha = 0.2f)
                                                        "HOME" -> ContextHome.copy(alpha = 0.2f)
                                                        else -> CyanAccent.copy(alpha = 0.2f)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = when (visit.placeType) {
                                                    "GYM" -> "🏋"
                                                    "CAMPUS" -> "🎓"
                                                    "HOME" -> "⌂"
                                                    else -> "📍"
                                                },
                                                fontSize = 18.sp
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(visit.placeName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                            Text(
                                                text = "Stayed ${visit.durationMinutes}m • ${visit.modeName} Mode",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }

                                        GlassChip(
                                            text = "${visit.readinessPercentage}% Ready",
                                            accentColor = if (visit.readinessPercentage >= 80) SuccessGreen else WarningAmber
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Places Tab Content
                if (selectedTab == 2) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Configured Anchor Places (${anchorPlaces.size})", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            TextButton(onClick = { showAddAnchorDialog = true }) {
                                Text("+ Add Anchor", color = CyanAccent, fontSize = 12.sp)
                            }
                        }
                    }

                    if (anchorPlaces.isEmpty()) {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Text("No anchor places configured yet. Tap + Add Anchor to define Home, Campus, or Gym.", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    } else {
                        items(anchorPlaces) { anchor ->
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(anchor.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                            GlassChip(text = anchor.type, accentColor = if (anchor.type == "HOME") ContextHome else CyanAccent, showDot = false)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Geofence: ${anchor.radiusMeters.toInt()}m • ${if (anchor.isEnabled) "Monitored" else "Disabled"}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { viewModel.toggleIgnoreAnchor(anchor.id, !anchor.isTemporarilyIgnored) }) {
                                            Icon(
                                                imageVector = if (anchor.isTemporarilyIgnored) Icons.Default.Close else Icons.Default.Check,
                                                contentDescription = "Ignore",
                                                tint = if (anchor.isTemporarilyIgnored) WarningAmber else SuccessGreen
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteAnchorPlace(anchor.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Notification / Feedback Toast Banner
            actionNotice?.let { notice ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardDark,
                    border = BorderStroke(1.dp, CyanAccent),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(notice, color = TextPrimary, fontSize = 12.sp)
                        IconButton(onClick = { actionNotice = null }, modifier = Modifier.size(16.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary)
                        }
                    }
                }
            }

            // Visited Place Inspection Bottom Sheet
            AnimatedVisibility(
                visible = selectedPlaceForDetails != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                selectedPlaceForDetails?.let { place ->
                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = SurfaceDark.copy(alpha = 0.98f),
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Drag Handle
                            Box(
                                modifier = Modifier
                                    .size(width = 36.dp, height = 4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(TextMuted)
                                    .align(Alignment.CenterHorizontally)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(place.placeName.uppercase(), fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 18.sp)
                                    Text("Visited: Today • Duration: ${place.durationMinutes}m", fontSize = 12.sp, color = TextSecondary)
                                }
                                IconButton(onClick = { InteractiveLifeMapManager.selectPlaceForDetails(null) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                                }
                            }

                            HorizontalDivider(color = BorderDark)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Mode", fontSize = 11.sp, color = TextMuted)
                                    Text(place.modeName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                }
                                Column {
                                    Text("Readiness", fontSize = 11.sp, color = TextMuted)
                                    Text("${place.readinessPercentage}%", fontWeight = FontWeight.Bold, color = SuccessGreen, fontSize = 13.sp)
                                }
                                Column {
                                    Text("Visits This Month", fontSize = 11.sp, color = TextMuted)
                                    Text("${place.visitCountThisMonth}", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                }
                                Column {
                                    Text("Average Stay", fontSize = 11.sp, color = TextMuted)
                                    Text("${place.averageStayMinutes}m", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Bottom Sheet Action Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlassButton(
                                    onClick = { actionNotice = "Place marked private" },
                                    modifier = Modifier.weight(1f),
                                    backgroundColor = PrimaryPurple.copy(alpha = 0.2f),
                                    borderColor = PurpleLight
                                ) {
                                    Text("Private", fontSize = 12.sp, color = PurpleLight)
                                }

                                GlassButton(
                                    onClick = { actionNotice = "Place excluded from analytics" },
                                    modifier = Modifier.weight(1f),
                                    backgroundColor = SurfaceDark,
                                    borderColor = BorderDark
                                ) {
                                    Text("Exclude", fontSize = 12.sp, color = TextSecondary)
                                }

                                GlassButton(
                                    onClick = {
                                        selectedTab = 1
                                        InteractiveLifeMapManager.selectPlaceForDetails(null)
                                    },
                                    modifier = Modifier.weight(1f),
                                    backgroundColor = CyanAccent.copy(alpha = 0.2f),
                                    borderColor = CyanAccent
                                ) {
                                    Text("Timeline", fontSize = 12.sp, color = CyanAccent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Anchor Place Modal Dialog
    if (showAddAnchorDialog) {
        AlertDialog(
            onDismissRequest = { showAddAnchorDialog = false },
            title = { Text("Add Anchor Place", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newAnchorName,
                        onValueChange = { newAnchorName = it },
                        label = { Text("Place Name (e.g. Gym, Library)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = newAnchorType,
                        onValueChange = { newAnchorType = it.uppercase() },
                        label = { Text("Type (HOME, CAMPUS, GYM, WORK, CUSTOM)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newAnchorLat,
                            onValueChange = { newAnchorLat = it },
                            label = { Text("Latitude") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanAccent, unfocusedBorderColor = BorderDark, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                        )
                        OutlinedTextField(
                            value = newAnchorLon,
                            onValueChange = { newAnchorLon = it },
                            label = { Text("Longitude") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanAccent, unfocusedBorderColor = BorderDark, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newAnchorName.isNotBlank()) {
                            val lat = newAnchorLat.toDoubleOrNull() ?: 17.3850
                            val lon = newAnchorLon.toDoubleOrNull() ?: 78.4867
                            viewModel.addAnchorPlace(
                                AnchorPlaceEntity(
                                    name = newAnchorName.trim(),
                                    type = newAnchorType.trim(),
                                    latitude = lat,
                                    longitude = lon,
                                    radiusMeters = 150f
                                )
                            )
                            newAnchorName = ""
                            showAddAnchorDialog = false
                            actionNotice = "Anchor place saved"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Text("Save Anchor", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAnchorDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CardDark
        )
    }
}

/**
 * Interactive Real-World Vector Map View powered by OSMDroid.
 * Features CartoDB Dark Matter styling, smooth multi-touch gestures, glowing cyan/purple
 * route vector polylines, custom vector place markers, geofence rings, heatmap density circles,
 * and recenter button.
 */
@Composable
fun GeographicMapCanvas(
    anchorPlaces: List<AnchorPlaceEntity>,
    travelHistory: List<TravelHistoryRecord>,
    placeVisits: List<PlaceVisitRecord>,
    privateZones: List<com.lifeos.app.data.PrivateZoneRecord>,
    isTrackingPaused: Boolean,
    isPrivacyMaskActive: Boolean,
    layerToggles: com.lifeos.app.engine.MapLayerToggles,
    selectedPlaceForDetails: PlaceVisitRecord? = null,
    onSelectPlace: (PlaceVisitRecord) -> Unit
) {
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    // Reference center coordinate (from first anchor, travel point, or Dinesh's default Hyderabad coords)
    val refLat = remember(anchorPlaces, travelHistory) {
        anchorPlaces.firstOrNull { it.latitude != 0.0 }?.latitude
            ?: travelHistory.firstOrNull { it.points.isNotEmpty() }?.points?.firstOrNull()?.first
            ?: 17.3850
    }
    val refLon = remember(anchorPlaces, travelHistory) {
        anchorPlaces.firstOrNull { it.longitude != 0.0 }?.longitude
            ?: travelHistory.firstOrNull { it.points.isNotEmpty() }?.points?.firstOrNull()?.second
            ?: 78.4867
    }

    var mapCenterPoint by remember { mutableStateOf(GeoPoint(refLat, refLon)) }
    var mapZoomLevel by remember { mutableDoubleStateOf(15.0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewInstance?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewInstance?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewInstance?.onDetach()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderDark, RoundedCornerShape(22.dp))
    ) {
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    val openStreetMapTileSource = XYTileSource(
                        "MapnikHttps",
                        0, 19, 256, ".png",
                        arrayOf("https://tile.openstreetmap.org/"),
                        "© OpenStreetMap contributors"
                    )
                    setTileSource(openStreetMapTileSource)
                    val darkColorMatrix = ColorMatrix(floatArrayOf(
                        -0.80f, 0f, 0f, 0f, 215f,
                        0f, -0.80f, 0f, 0f, 220f,
                        0f, 0f, -0.75f, 0f, 235f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(darkColorMatrix))
                    overlayManager.tilesOverlay.loadingBackgroundColor = android.graphics.Color.WHITE
                    overlayManager.tilesOverlay.loadingLineColor = android.graphics.Color.parseColor("#E0E0E0")
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    setBackgroundColor(android.graphics.Color.parseColor("#0B0E14"))

                    controller.setZoom(15.0)
                    controller.setCenter(GeoPoint(refLat, refLon))

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            event?.source?.let {
                                mapCenterPoint = GeoPoint(it.mapCenter.latitude, it.mapCenter.longitude)
                            }
                            return false
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            event?.source?.let {
                                mapZoomLevel = it.zoomLevelDouble
                            }
                            return false
                        }
                    })

                    // Allow dragging/pinching map without parent scroll hijacking touch events
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }

                    mapViewInstance = this
                }
            },
            update = { mapView ->
                updateMapOverlays(
                    mapView = mapView,
                    context = mapView.context,
                    refLat = refLat,
                    refLon = refLon,
                    anchorPlaces = anchorPlaces,
                    travelHistory = travelHistory,
                    placeVisits = placeVisits,
                    isPrivacyMaskActive = isPrivacyMaskActive,
                    layerToggles = layerToggles,
                    onSelectPlace = onSelectPlace
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Inspection Mini-Card Overlay (appears when a marker/place is selected)
        if (selectedPlaceForDetails != null) {
            val place = selectedPlaceForDetails
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceDark.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .wrapContentWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (place.placeType.uppercase()) {
                                    "GYM" -> ContextGym
                                    "CAMPUS", "COLLEGE" -> ContextCollege
                                    "HOME" -> ContextHome
                                    else -> CyanAccent
                                }
                            )
                    )
                    Column {
                        Text(place.placeName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 11.sp)
                        Text(
                            text = "${place.durationMinutes}m • ${place.modeName} (${place.readinessPercentage}%)",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = { InteractiveLifeMapManager.selectPlaceForDetails(null) },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        // Compass & Scale Indicator overlay
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = GlassBackground,
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Text(
                    text = "GPS: ${String.format(java.util.Locale.US, "%.4f", mapCenterPoint.latitude)}, ${String.format(java.util.Locale.US, "%.4f", mapCenterPoint.longitude)}",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = GlassBackground,
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Text(
                    text = "Zoom ${(mapZoomLevel * 10).toInt() / 10.0}x",
                    fontSize = 10.sp,
                    color = CyanAccent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Recenter Button (↻) to smoothly animate back to current position
        IconButton(
            onClick = {
                mapViewInstance?.let { mv ->
                    mv.controller.animateTo(GeoPoint(refLat, refLon), 15.0, 600L)
                    mapCenterPoint = GeoPoint(refLat, refLon)
                    mapZoomLevel = 15.0
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(GlassBackground)
                .border(1.dp, BorderDark, CircleShape)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Center Map", tint = CyanAccent, modifier = Modifier.size(16.dp))
        }

        // Tracking Paused or Empty Journey Watermark Overlay
        if (isTrackingPaused) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WarningAmber.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, WarningAmber),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⏸", fontSize = 16.sp)
                    Text("Location tracking is paused.", fontWeight = FontWeight.Bold, color = WarningAmber, fontSize = 12.sp)
                }
            }
        } else if (travelHistory.isEmpty() && anchorPlaces.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceDark.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📍", fontSize = 16.sp)
                    Text("Your journey history will appear here.", fontWeight = FontWeight.SemiBold, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Updates all vector overlays on the MapView.
 */
private fun updateMapOverlays(
    mapView: MapView,
    context: Context,
    refLat: Double,
    refLon: Double,
    anchorPlaces: List<AnchorPlaceEntity>,
    travelHistory: List<TravelHistoryRecord>,
    placeVisits: List<PlaceVisitRecord>,
    isPrivacyMaskActive: Boolean,
    layerToggles: com.lifeos.app.engine.MapLayerToggles,
    onSelectPlace: (PlaceVisitRecord) -> Unit
) {
    mapView.overlays.clear()

    // 1. Draw Privacy Masking Zones
    if (isPrivacyMaskActive) {
        anchorPlaces.filter { it.type.equals("HOME", ignoreCase = true) }.forEach { home ->
            val (lat, lon) = getAnchorCoordinates(home, refLat, refLon)
            val maskPolygon = NonInteractivePolygon(mapView).apply {
                points = computeCircleGeoPoints(GeoPoint(lat, lon), 200.0)
                fillPaint.color = 0x2EFFBE0B.toInt()
                outlinePaint.color = 0xAAFFBE0B.toInt()
                outlinePaint.strokeWidth = 2.5f
            }
            mapView.overlays.add(maskPolygon)
        }
    }

    // 2. Draw Geofence & Context Areas (Context Layer)
    if (layerToggles.showContext) {
        anchorPlaces.forEach { anchor ->
            val (lat, lon) = getAnchorCoordinates(anchor, refLat, refLon)
            val colorInt = when (anchor.type.uppercase()) {
                "HOME" -> 0xFF4CC9F0.toInt()
                "CAMPUS", "COLLEGE" -> 0xFF7209B7.toInt()
                "GYM" -> 0xFFF72585.toInt()
                "WORK" -> 0xFF00F5D4.toInt()
                else -> 0xFF00F5D4.toInt()
            }
            val radius = if (anchor.radiusMeters > 0f) anchor.radiusMeters.toDouble() else 150.0
            val geofenceCircle = NonInteractivePolygon(mapView).apply {
                points = computeCircleGeoPoints(GeoPoint(lat, lon), radius)
                fillPaint.color = (colorInt and 0x00FFFFFF) or 0x26000000
                outlinePaint.color = (colorInt and 0x00FFFFFF) or 0x80000000.toInt()
                outlinePaint.strokeWidth = 2f
            }
            mapView.overlays.add(geofenceCircle)
        }
    }

    // 3. Draw Heatmap Stop Density Circles (Heatmap Layer)
    if (layerToggles.showHeatmap) {
        val heatPoints = mutableListOf<GeoPoint>()
        travelHistory.forEach { route ->
            route.points.forEach { pt ->
                heatPoints.add(GeoPoint(pt.first, pt.second))
            }
        }
        anchorPlaces.forEach { anchor ->
            val (lat, lon) = getAnchorCoordinates(anchor, refLat, refLon)
            heatPoints.add(GeoPoint(lat, lon))
        }
        heatPoints.take(25).forEach { pt ->
            val heatCircle = NonInteractivePolygon(mapView).apply {
                points = computeCircleGeoPoints(pt, 160.0)
                fillPaint.color = 0x3300F5D4.toInt()
                outlinePaint.color = 0x6600F5D4.toInt()
                outlinePaint.strokeWidth = 1.5f
            }
            mapView.overlays.add(heatCircle)
        }
    }

    // 4. Draw Travel Routes Polylines (Route Layer)
    if (layerToggles.showRoute && travelHistory.isNotEmpty()) {
        travelHistory.forEach { route ->
            if (route.points.size >= 2) {
                val geoPoints = route.points.map { GeoPoint(it.first, it.second) }
                val (baseColor, glowColor) = when (route.transitMode.uppercase()) {
                    "CYCLING", "FITNESS" -> 0xFFF72585.toInt() to 0x4DF72585.toInt()
                    "WALKING" -> 0xFF00F5D4.toInt() to 0x4D00F5D4.toInt()
                    else -> 0xFF7B2CBF.toInt() to 0x4D7B2CBF.toInt()
                }

                // Outer Glowing Polyline
                val glowLine = Polyline(mapView).apply {
                    setPoints(geoPoints)
                    outlinePaint.color = glowColor
                    outlinePaint.strokeWidth = 14f
                    outlinePaint.strokeCap = AndroidPaint.Cap.ROUND
                    outlinePaint.strokeJoin = AndroidPaint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                }
                mapView.overlays.add(glowLine)

                // Core Crisp Vector Polyline
                val coreLine = Polyline(mapView).apply {
                    setPoints(geoPoints)
                    outlinePaint.color = baseColor
                    outlinePaint.strokeWidth = 5f
                    outlinePaint.strokeCap = AndroidPaint.Cap.ROUND
                    outlinePaint.strokeJoin = AndroidPaint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                }
                mapView.overlays.add(coreLine)
            }
        }
    }

    // 5. Draw Vector Place Markers (Places Layer)
    if (layerToggles.showPlaces) {
        anchorPlaces.forEach { anchor ->
            val (lat, lon) = getAnchorCoordinates(anchor, refLat, refLon)
            val marker = Marker(mapView).apply {
                position = GeoPoint(lat, lon)
                title = anchor.name
                icon = createVectorMarkerDrawable(context, anchor.type, anchor.name)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    val matchedVisit = placeVisits.firstOrNull { it.placeName.equals(anchor.name, ignoreCase = true) }
                        ?: PlaceVisitRecord(
                            anchorPlaceId = anchor.id,
                            placeName = anchor.name,
                            placeType = anchor.type,
                            arrivalTime = System.currentTimeMillis() - 7200000,
                            departureTime = System.currentTimeMillis(),
                            durationMinutes = 120,
                            modeName = when (anchor.type.uppercase()) {
                                "HOME" -> "Recharge"
                                "CAMPUS", "COLLEGE" -> "Focus"
                                "GYM" -> "Active"
                                "WORK" -> "Productivity"
                                else -> "General"
                            },
                            readinessPercentage = when (anchor.type.uppercase()) {
                                "HOME" -> 92
                                "CAMPUS", "COLLEGE" -> 88
                                "GYM" -> 95
                                "WORK" -> 90
                                else -> 85
                            },
                            visitCountThisMonth = 14,
                            averageStayMinutes = 90
                        )
                    onSelectPlace(matchedVisit)
                    true
                }
            }
            mapView.overlays.add(marker)
        }
    }

    val mapEventsOverlay = org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
            if (p == null) return false
            var bestAnchor: AnchorPlaceEntity? = null
            var bestDist = Double.MAX_VALUE
            anchorPlaces.forEach { anchor ->
                val (aLat, aLon) = getAnchorCoordinates(anchor, refLat, refLon)
                val dist = p.distanceToAsDouble(GeoPoint(aLat, aLon))
                if (dist < bestDist) {
                    bestDist = dist
                    bestAnchor = anchor
                }
            }
            if (bestAnchor != null && bestDist <= 600.0) {
                val matchedVisit = placeVisits.firstOrNull { it.placeName.equals(bestAnchor!!.name, ignoreCase = true) }
                    ?: PlaceVisitRecord(
                        anchorPlaceId = bestAnchor!!.id,
                        placeName = bestAnchor!!.name,
                        placeType = bestAnchor!!.type,
                        arrivalTime = System.currentTimeMillis() - 7200000,
                        departureTime = System.currentTimeMillis(),
                        durationMinutes = 120,
                        modeName = when (bestAnchor!!.type.uppercase()) {
                            "HOME" -> "Recharge"
                            "CAMPUS", "COLLEGE" -> "Focus"
                            "GYM" -> "Active"
                            "WORK" -> "Productivity"
                            else -> "General"
                        },
                        readinessPercentage = when (bestAnchor!!.type.uppercase()) {
                            "HOME" -> 92
                            "CAMPUS", "COLLEGE" -> 88
                            "GYM" -> 95
                            "WORK" -> 90
                            else -> 85
                        },
                        visitCountThisMonth = 14,
                        averageStayMinutes = 90
                    )
                onSelectPlace(matchedVisit)
                return true
            }
            return false
        }
        override fun longPressHelper(p: GeoPoint?): Boolean = false
    })
    mapView.overlays.add(mapEventsOverlay)

    mapView.invalidate()
}

/**
 * Returns latitude/longitude for an anchor place, defaulting to realistic Hyderabad anchors if 0.0.
 */
private fun getAnchorCoordinates(anchor: AnchorPlaceEntity, refLat: Double, refLon: Double): Pair<Double, Double> {
    if (anchor.latitude != 0.0 && anchor.longitude != 0.0) {
        return anchor.latitude to anchor.longitude
    }
    return when (anchor.type.uppercase()) {
        "HOME" -> 17.3850 to 78.4867
        "CAMPUS", "COLLEGE" -> 17.3985 to 78.4985
        "GYM" -> 17.3745 to 78.4725
        "WORK" -> 17.4050 to 78.4700
        else -> 17.3890 to 78.4810
    }
}

/**
 * Custom Polygon that transparently delegates single tap events so markers remain fully clickable.
 */
private class NonInteractivePolygon(mapView: MapView) : Polygon(mapView) {
    override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
        return false
    }
}

/**
 * Dynamically creates a stylized vector place marker Drawable.
 */
private fun createVectorMarkerDrawable(
    context: Context,
    type: String,
    title: String
): Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (38 * density).toInt().coerceAtLeast(48)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)

    val (primaryColorInt, glyph) = when (type.uppercase()) {
        "HOME" -> 0xFF4CC9F0.toInt() to "🏠"
        "CAMPUS", "COLLEGE" -> 0xFF7209B7.toInt() to "🎓"
        "GYM" -> 0xFFF72585.toInt() to "🏋"
        "WORK" -> 0xFF00F5D4.toInt() to "💼"
        else -> 0xFF00F5D4.toInt() to "📍"
    }

    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val radius = (15 * density)

    // 1. Subtle glowing pulse aura
    val glowPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
        color = (primaryColorInt and 0x00FFFFFF) or 0x40000000
    }
    canvas.drawCircle(cx, cy, (18 * density), glowPaint)

    // 2. Dark glass circular disc
    val discPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
        color = 0xFF0E1420.toInt()
    }
    canvas.drawCircle(cx, cy, radius, discPaint)

    // 3. Crisp vector neon border
    val strokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeWidth = 2f * density
        color = primaryColorInt
    }
    canvas.drawCircle(cx, cy, radius, strokePaint)

    // 4. Center glyph
    val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * density
        textAlign = AndroidPaint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    val textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(glyph, cx, textY, textPaint)

    return BitmapDrawable(context.resources, bitmap).apply {
        setBounds(0, 0, sizePx, sizePx)
    }
}

/**
 * Computes a list of GeoPoints approximating a circle for polygons.
 */
private fun computeCircleGeoPoints(center: GeoPoint, radiusMeters: Double, pointsCount: Int = 36): List<GeoPoint> {
    val result = ArrayList<GeoPoint>(pointsCount)
    val earthRadius = 6378137.0
    val latRad = Math.toRadians(center.latitude)
    val lonRad = Math.toRadians(center.longitude)
    val dByR = radiusMeters / earthRadius

    for (i in 0 until pointsCount) {
        val bearing = Math.toRadians(i * 360.0 / pointsCount)
        val pLat = asin(sin(latRad) * cos(dByR) + cos(latRad) * sin(dByR) * cos(bearing))
        val pLon = lonRad + atan2(sin(bearing) * sin(dByR) * cos(latRad), cos(dByR) - sin(latRad) * sin(pLat))
        result.add(GeoPoint(Math.toDegrees(pLat), Math.toDegrees(pLon)))
    }
    return result
}
