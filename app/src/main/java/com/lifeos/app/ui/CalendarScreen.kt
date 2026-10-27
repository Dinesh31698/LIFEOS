package com.lifeos.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.app.MainViewModel
import com.lifeos.app.data.EventEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Sprint 5: Calendar Schedule View & Timeline Engine (Screen 9 in wireframe spec)
 */
@Composable
fun CalendarScreen(
    viewModel: MainViewModel
) {
    val allEvents by viewModel.allEvents.collectAsState()
    val todayEvents by viewModel.todayEvents.collectAsState()
    val allModes by viewModel.allModes.collectAsState()
    val activeItems by viewModel.activeItems.collectAsState()

    // 0 = Day, 1 = Week, 2 = Month
    var selectedViewMode by remember { mutableStateOf(0) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var showAddEventDialog by remember { mutableStateOf(false) }
    var selectedEventForDetails by remember { mutableStateOf<EventEntity?>(null) }
    var isGridView by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()) }
    val currentDateStr = remember(selectedDate) { dateFormat.format(selectedDate.time) }

    // Filter events for selected day (or fallback to todayEvents if on current day)
    val dayEvents = remember(allEvents, selectedDate) {
        val selYear = selectedDate.get(Calendar.YEAR)
        val selDayOfYear = selectedDate.get(Calendar.DAY_OF_YEAR)
        val filtered = allEvents.filter { event ->
            if (event.isGhostMode) return@filter false
            val c = Calendar.getInstance().apply { timeInMillis = event.startTime }
            c.get(Calendar.YEAR) == selYear && c.get(Calendar.DAY_OF_YEAR) == selDayOfYear
        }.sortedBy { it.startTime }

        if (filtered.isNotEmpty()) {
            filtered
        } else {
            // Check if selectedDate is today
            val todayCal = Calendar.getInstance()
            if (todayCal.get(Calendar.YEAR) == selYear && todayCal.get(Calendar.DAY_OF_YEAR) == selDayOfYear) {
                todayEvents
            } else {
                emptyList()
            }
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddEventDialog = true },
                containerColor = PrimaryPurple,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 8.dp, end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Event",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // 1. Header Row: Title & Layout Toggle Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Calendar",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Local timeline linked to active requirements",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                // Layout Toggle Button
                IconButton(
                    onClick = {
                        isGridView = !isGridView
                        selectedViewMode = (selectedViewMode + 1) % 3
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                ) {
                    Icon(
                        imageVector = if (selectedViewMode == 0) Icons.Default.CalendarToday else Icons.Default.ViewAgenda,
                        contentDescription = "Toggle View",
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. Segmented View Mode Selector: Day, Week, Month
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val tabs = listOf("Day", "Week", "Month")
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedViewMode == index
                        val bg by animateColorAsState(
                            targetValue = if (isSelected) PrimaryPurple else Color.Transparent,
                            animationSpec = tween(250),
                            label = "TabBg"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bg)
                                .clickable { selectedViewMode = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }

            // 3. Date Selector Row: <  Thu, 24 Apr 2025  >
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val newCal = (selectedDate.clone() as Calendar).apply {
                                add(Calendar.DAY_OF_MONTH, -1)
                            }
                            selectedDate = newCal
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Day",
                            tint = CyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = currentDateStr,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    IconButton(
                        onClick = {
                            val newCal = (selectedDate.clone() as Calendar).apply {
                                add(Calendar.DAY_OF_MONTH, 1)
                            }
                            selectedDate = newCal
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Day",
                            tint = CyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4. Main View Content based on View Mode
            when (selectedViewMode) {
                0 -> {
                    // Day Mode: Hourly Schedule Timeline Grid
                    HourlyTimelineGrid(
                        events = dayEvents,
                        allModes = allModes,
                        onEventClick = { selectedEventForDetails = it }
                    )
                }
                1 -> {
                    // Week Mode: 7-day selector + list view
                    WeekScheduleView(
                        selectedDate = selectedDate,
                        onSelectDate = { selectedDate = it },
                        events = dayEvents,
                        allModes = allModes,
                        onEventClick = { selectedEventForDetails = it }
                    )
                }
                2 -> {
                    // Month Mode: Month calendar grid
                    MonthCalendarView(
                        selectedDate = selectedDate,
                        onSelectDate = {
                            selectedDate = it
                            selectedViewMode = 0
                        },
                        allEvents = allEvents
                    )
                }
            }
        }
    }

    // Modal 1: Add Event Dialog
    if (showAddEventDialog) {
        AddEventDialog(
            selectedDate = selectedDate,
            allModes = allModes,
            onDismiss = { showAddEventDialog = false },
            onSave = { newEvent ->
                viewModel.addCalendarEvent(newEvent)
                showAddEventDialog = false
            }
        )
    }

    // Modal 2: Event Details & Delete Dialog
    if (selectedEventForDetails != null) {
        val event = selectedEventForDetails!!
        val mode = allModes.find { it.id == event.associatedModeId }
        EventDetailDialog(
            event = event,
            mode = mode,
            activeItems = activeItems,
            onDismiss = { selectedEventForDetails = null },
            onDelete = {
                viewModel.deleteEvent(event.id)
                selectedEventForDetails = null
            }
        )
    }
}

/**
 * Screen 9 Hourly Timeline Grid (8 AM - 6 PM)
 */
@Composable
fun HourlyTimelineGrid(
    events: List<EventEntity>,
    allModes: List<LifeModeEntity>,
    onEventClick: (EventEntity) -> Unit
) {
    val scrollState = rememberScrollState()
    val hours = remember { (8..18).toList() }
    val hourSlotHeight = 78.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 90.dp)
    ) {
        val totalGridHeight = (hours.size * 78).dp

        // A. Background Grid Lines & Hour Labels
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalGridHeight)
        ) {
            hours.forEach { h ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(hourSlotHeight),
                    contentAlignment = Alignment.TopStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatHourLabel(h),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted,
                            modifier = Modifier.width(46.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 1.dp,
                            color = BorderDark.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        // B. Event Cards positioned dynamically
        events.forEach { event ->
            val calStart = Calendar.getInstance().apply { timeInMillis = event.startTime }
            val calEnd = Calendar.getInstance().apply { timeInMillis = event.endTime }
            val startHour = calStart.get(Calendar.HOUR_OF_DAY)
            val startMin = calStart.get(Calendar.MINUTE)
            val endHour = calEnd.get(Calendar.HOUR_OF_DAY)
            val endMin = calEnd.get(Calendar.MINUTE)

            val startFloat = startHour + (startMin / 60f)
            val endFloat = maxOf(startFloat + 0.75f, endHour + (endMin / 60f))

            if (startFloat < 19f && endFloat > 7.5f) {
                val clampedStart = maxOf(8f, startFloat)
                val clampedEnd = minOf(18.5f, endFloat)

                val topOffsetDp = ((clampedStart - 8f) * 78).dp + 8.dp
                val cardHeightDp = maxOf(56.dp, ((clampedEnd - clampedStart) * 78).dp - 6.dp)

                val accentColor = getEventAccentColor(event)
                val modeName = allModes.find { it.id == event.associatedModeId }?.name ?: "Campus"

                Box(
                    modifier = Modifier
                        .padding(start = 54.dp, end = 4.dp)
                        .fillMaxWidth()
                        .offset(y = topOffsetDp)
                        .height(cardHeightDp)
                ) {
                    TimelineEventCard(
                        event = event,
                        accentColor = accentColor,
                        modeName = modeName,
                        onClick = { onEventClick(event) }
                    )
                }
            }
        }

        // C. Empty state indicator if no events for this day
        if (events.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceDark.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier
                    .padding(start = 54.dp, top = 20.dp, end = 8.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.EventAvailable,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No events scheduled for this day",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tap [+] to schedule lectures, labs, or routines",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Event Card on the Timeline Grid with category accents
 */
@Composable
fun TimelineEventCard(
    event: EventEntity,
    accentColor: Color,
    modeName: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Accent Vertical Bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = event.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val timeStr = formatEventTimeRange(event.startTime, event.endTime)
                val locationStr = if (!event.location.isNullOrBlank()) " • ${event.location}" else ""

                Text(
                    text = "$timeStr$locationStr",
                    fontSize = 11.sp,
                    color = if (accentColor != Color(0xFF636E72)) accentColor else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Mode Badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = accentColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = modeName,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Week View: 7-day picker + agenda cards
 */
@Composable
fun WeekScheduleView(
    selectedDate: Calendar,
    onSelectDate: (Calendar) -> Unit,
    events: List<EventEntity>,
    allModes: List<LifeModeEntity>,
    onEventClick: (EventEntity) -> Unit
) {
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    // Get dates for current week
    val weekDates = remember(selectedDate) {
        val base = selectedDate.clone() as Calendar
        base.firstDayOfWeek = Calendar.MONDAY
        base.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        (0..6).map { i ->
            val c = base.clone() as Calendar
            c.add(Calendar.DAY_OF_MONTH, i)
            c
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Week days row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(weekDates) { cal ->
                val isSelected = cal.get(Calendar.DAY_OF_YEAR) == selectedDate.get(Calendar.DAY_OF_YEAR) &&
                        cal.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR)
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val dayNameIndex = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - 2
                val name = dayNames.getOrElse(dayNameIndex) { "Day" }
                val dayNum = cal.get(Calendar.DAY_OF_MONTH)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) PrimaryPurple else SurfaceDark,
                    border = BorderStroke(1.dp, if (isSelected) PrimaryPurple else BorderDark),
                    modifier = Modifier.clickable { onSelectDate(cal) }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dayNum.toString(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Events list for the selected day in week view
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (events.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceDark,
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No scheduled events for this day",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(events, key = { it.id }) { event ->
                    val accentColor = getEventAccentColor(event)
                    val modeName = allModes.find { it.id == event.associatedModeId }?.name ?: "Campus"
                    Box(modifier = Modifier.height(68.dp)) {
                        TimelineEventCard(
                            event = event,
                            accentColor = accentColor,
                            modeName = modeName,
                            onClick = { onEventClick(event) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Month View: Calendar grid
 */
@Composable
fun MonthCalendarView(
    selectedDate: Calendar,
    onSelectDate: (Calendar) -> Unit,
    allEvents: List<EventEntity>
) {
    val monthCal = remember(selectedDate) {
        val c = selectedDate.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        c
    }

    val maxDays = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = monthCal.get(Calendar.DAY_OF_WEEK)
    // Shift so Monday is index 0
    val offset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2

    val daysOfWeek = listOf("M", "T", "W", "T", "F", "S", "S")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp)
    ) {
        // Weekday headers
        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Month Grid cells
        val totalCells = offset + maxDays
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - offset + 1
                    if (dayNum in 1..maxDays) {
                        val cellCal = (monthCal.clone() as Calendar).apply {
                            set(Calendar.DAY_OF_MONTH, dayNum)
                        }
                        val isSelected = cellCal.get(Calendar.DAY_OF_YEAR) == selectedDate.get(Calendar.DAY_OF_YEAR) &&
                                cellCal.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR)

                        val hasEvents = allEvents.any { event ->
                            val ec = Calendar.getInstance().apply { timeInMillis = event.startTime }
                            ec.get(Calendar.YEAR) == cellCal.get(Calendar.YEAR) &&
                                    ec.get(Calendar.DAY_OF_YEAR) == cellCal.get(Calendar.DAY_OF_YEAR)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PrimaryPurple else SurfaceDark)
                                .clickable { onSelectDate(cellCal) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dayNum.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else TextPrimary
                                )
                                if (hasEvents) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) CyanAccent else PrimaryPurple)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Add Event Dialog: User enters title, times, room, and chooses mode
 */
@Composable
fun AddEventDialog(
    selectedDate: Calendar,
    allModes: List<LifeModeEntity>,
    onDismiss: () -> Unit,
    onSave: (EventEntity) -> Unit
) {
    var eventTitle by remember { mutableStateOf("") }
    var eventLocation by remember { mutableStateOf("") }
    var startHour by remember { mutableStateOf(10) } // Default 10 AM
    var endHour by remember { mutableStateOf(11) } // Default 11 AM
    var selectedModeId by remember { mutableStateOf(allModes.firstOrNull()?.id ?: 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Schedule Event",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = eventTitle,
                    onValueChange = { eventTitle = it },
                    label = { Text("Event Title") },
                    placeholder = { Text("e.g. Software Architecture, Gym Workout") },
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

                OutlinedTextField(
                    value = eventLocation,
                    onValueChange = { eventLocation = it },
                    label = { Text("Location / Room") },
                    placeholder = { Text("e.g. Room 302, CS Lab 1") },
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

                // Time Pickers (Start Hour & End Hour with Stepper Controls)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Start Time Stepper
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceDark,
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Start Time", fontSize = 10.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = { if (startHour > 6) startHour-- },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = formatHourLabel(startHour),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                IconButton(
                                    onClick = {
                                        if (startHour < 21) {
                                            startHour++
                                            if (endHour <= startHour) endHour = startHour + 1
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // End Time Stepper
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceDark,
                        border = BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("End Time", fontSize = 10.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = { if (endHour > startHour + 1) endHour-- },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = formatHourLabel(endHour),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                IconButton(
                                    onClick = { if (endHour < 22) endHour++ },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Life Mode Selection Chips
                Text("Associated Mode:", fontSize = 11.sp, color = TextSecondary)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allModes) { mode ->
                        val isSelected = mode.id == selectedModeId
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) PrimaryPurple else SurfaceDark,
                            border = BorderStroke(1.dp, if (isSelected) PrimaryPurple else BorderDark),
                            modifier = Modifier.clickable { selectedModeId = mode.id }
                        ) {
                            Text(
                                text = mode.name,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else TextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (eventTitle.isNotBlank()) {
                        val startCal = (selectedDate.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, startHour)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }
                        val endCal = (selectedDate.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, endHour)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }

                        val newEvent = EventEntity(
                            title = eventTitle.trim(),
                            startTime = startCal.timeInMillis,
                            endTime = endCal.timeInMillis,
                            location = if (eventLocation.isBlank()) "Campus" else eventLocation.trim(),
                            associatedModeId = selectedModeId,
                            isGhostMode = false,
                            confidence = 1.0f,
                            evidenceText = "User Schedule"
                        )
                        onSave(newEvent)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Schedule Event", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(18.dp)
    )
}

/**
 * Event Detail & Delete Modal
 */
@Composable
fun EventDetailDialog(
    event: EventEntity,
    mode: LifeModeEntity?,
    activeItems: List<com.lifeos.app.data.ItemRequirementEntity>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = getEventAccentColor(event)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = mode?.name ?: "Campus",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Time: ${formatEventTimeRange(event.startTime, event.endTime)}",
                    fontSize = 13.sp,
                    color = accentColor
                )
                Text(
                    text = "Location: ${event.location ?: "Campus Room"}",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Source: ${event.evidenceText ?: "Timetable"}",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Required Gear & Documents:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                activeItems.take(4).forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isReady) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (item.isReady) SuccessGreen else AlertRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = item.itemName,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Close")
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete Event", color = AlertRed, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(18.dp)
    )
}

/**
 * Color determination matching wireframe Screen 9
 */
fun getEventAccentColor(event: EventEntity): Color {
    val titleLower = event.title.lowercase()
    return when {
        titleLower.contains("data structure") -> CyanAccent
        titleLower.contains("dbms") -> PrimaryPurple
        titleLower.contains("lunch") -> Color(0xFF636E72)
        titleLower.contains("oop") -> Color(0xFF0984E3)
        titleLower.contains("project") -> WarningAmber
        titleLower.contains("workout") || titleLower.contains("gym") -> SuccessGreen
        else -> when (event.associatedModeId) {
            1 -> PrimaryPurple
            2 -> SuccessGreen
            3 -> CyanAccent
            4 -> Color(0xFFE17055)
            5 -> WarningAmber
            else -> PrimaryPurple
        }
    }
}

fun formatHourLabel(hour: Int): String {
    return when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}

fun formatEventTimeRange(start: Long, end: Long): String {
    if (start <= 0) return "10:00 AM - 11:00 AM"
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    return "${fmt.format(Date(start))} - ${fmt.format(Date(end))}"
}
