package com.lifeos.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.app.data.*
import com.lifeos.app.engine.*
import com.lifeos.app.security.DataMinimizer
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.PermissionStatus
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TodayStats(
    val todayEventsCount: Int = 0,
    val pendingTasksCount: Int = 0,
    val missingItemsCount: Int = 0
)

data class DashboardState(
    val activeMode: LifeModeEntity? = null,
    val activeItems: List<ItemRequirementEntity> = emptyList(),
    val missingItems: List<ItemRequirementEntity> = emptyList(),
    val readyItems: List<ItemRequirementEntity> = emptyList(),
    val readinessPercentage: Int = -1,
    val upcomingEvents: List<EventEntity> = emptyList(),
    val nextUpEvent: EventEntity? = null,
    val todayStats: TodayStats = TodayStats(),
    val consequenceAlert: ConsequenceAlert? = null,
    val isSafeModeEnabled: Boolean = false,
    val detectedMode: LifeModeEntity? = null,
    val detectedConfidenceLevel: ContextConfidenceLevel = ContextConfidenceLevel.NO_CONTEXT,
    val detectedConfidenceScore: Float = 0.0f,
    val contextActionDecision: ContextActionDecision = ContextActionDecision.RETAIN_CURRENT,
    val contextStatusMessage: String = "",
    val contextSignals: List<String> = emptyList()
)

enum class NotificationCategory {
    ALL,
    AI,
    IMPORTANT,
    GHOST_MODE,
    SYSTEM
}

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val category: NotificationCategory,
    val timeGroup: String = "Today",
    val ghostProposalId: Int? = null,
    val isRead: Boolean = false,
    val isActionApplied: Boolean = false
)

enum class ChatSender {
    USER,
    AI
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: ChatSender,
    val text: String,
    val timestamp: String,
    val readinessPercentage: Int? = null,
    val readinessItems: List<Pair<String, Boolean>>? = null,
    val suggestion: String? = null
)

class MainViewModel(private val dao: LifeOsDao) : ViewModel() {

    val allModes: StateFlow<List<LifeModeEntity>> = dao.getAllModesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMode: StateFlow<LifeModeEntity?> = dao.getActiveModeFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userProfile: StateFlow<UserProfileEntity?> = dao.getUserProfileFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val anchorPlaces: StateFlow<List<AnchorPlaceEntity>> = dao.getAllAnchorPlacesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryEntity>> = dao.getAllMemoriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val emergencyContacts: StateFlow<List<EmergencyContactEntity>> = dao.getAllEmergencyContactsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appUsageRules: StateFlow<List<AppUsageRuleEntity>> = dao.getAllAppUsageRulesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceState: StateFlow<VoiceState> = HandsFreeVoiceManager.voiceState
    val currentVoiceLanguage: StateFlow<String> = HandsFreeVoiceManager.currentLanguage

    val sosState: StateFlow<SosState> = EmergencySosManager.sosState
    val countdownRemainingSeconds: StateFlow<Int> = EmergencySosManager.countdownRemainingSeconds
    val isSosTestMode: StateFlow<Boolean> = EmergencySosManager.isTestMode

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getUserProfile()
            if (existing == null) {
                dao.insertOrUpdateUserProfile(
                    UserProfileEntity(
                        id = 1,
                        preferredName = "Dinesh",
                        primaryRole = "Student",
                        preferredGreeting = "Good Morning"
                    )
                )
            }
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    val modeHabits: StateFlow<List<ItemHabitEntity>> = activeMode
        .flatMapLatest { mode ->
            if (mode != null) dao.getHabitsForModeFlow(mode.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeItems: StateFlow<List<ItemRequirementEntity>> = combine(
        activeMode.flatMapLatest { mode ->
            if (mode != null) dao.getActiveItemsForModeFlow(mode.id) else flowOf(emptyList())
        },
        modeHabits
    ) { items, habits ->
        val habitMap = habits.associateBy { it.itemName.lowercase() }
        items.sortedWith(
            compareByDescending<ItemRequirementEntity> { item ->
                val habit = habitMap[item.itemName.lowercase()]
                val isHigh = habit != null && ForgettingModelEngine.isFrequentlyForgotten(habit)
                val boost = habit?.priorityBoost ?: 0.0f
                if (!item.isReady && !item.isWaived && isHigh) 2000f + boost
                else if (!item.isReady && !item.isWaived) 1000f + boost
                else boost
            }.thenBy { it.id }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val missingItems: StateFlow<List<ItemRequirementEntity>> = activeItems
        .map { list -> list.filter { !it.isReady && !it.isWaived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readyItems: StateFlow<List<ItemRequirementEntity>> = activeItems
        .map { list -> list.filter { it.isReady } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val ghostItems: StateFlow<List<ItemRequirementEntity>> = activeMode
        .flatMapLatest { mode ->
            if (mode != null) dao.getGhostItemsForModeFlow(mode.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allItems: StateFlow<List<ItemRequirementEntity>> = dao.getAllItemsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEvents: StateFlow<List<EventEntity>> = dao.getAllEventsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayEvents: StateFlow<List<EventEntity>> = allEvents
        .map { list ->
            list.filter { !it.isGhostMode }.sortedBy { it.startTime }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ghostEvents: StateFlow<List<EventEntity>> = dao.getGhostEventsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sprint 6: Ghost Proposals Stream
    val ghostProposalsFlow: StateFlow<List<EventEntity>> = ghostEvents

    val latestEvidence: StateFlow<List<EvidenceEntity>> = dao.getLatestEvidenceFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readinessReport: StateFlow<ReadinessReport> = combine(activeMode, activeItems) { mode, items ->
        ReadinessEngine.calculateReadiness(mode, items)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ReadinessReport("", 0, 0, 0, emptyList(), 0, "Calculating readiness...")
    )

    private val _appliedProposalIds = MutableStateFlow<Set<Int>>(emptySet())
    val appliedProposalIds: StateFlow<Set<Int>> = _appliedProposalIds.asStateFlow()

    private val _ignoredProposalIds = MutableStateFlow<Set<Int>>(emptySet())
    val ignoredProposalIds: StateFlow<Set<Int>> = _ignoredProposalIds.asStateFlow()

    val notificationsFlow: StateFlow<List<NotificationItem>> = combine(
        combine(ghostEvents, GhostModeEngine.proposalsFlow, _appliedProposalIds) { ge, gp, ap -> Triple(ge, gp, ap) },
        combine(_ignoredProposalIds, readinessReport, todayEvents) { ig, rr, te -> Triple(ig, rr, te) },
        ConsequenceEngine.latestConsequenceAlert
    ) { (ghostList, ghostEngineProposals, appliedIds), (ignoredIds, report, events), consequenceAlert ->
        val list = mutableListOf<NotificationItem>()
        val timeFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())

        // 1. Dynamic Consequence Alert (Important badge)
        if (consequenceAlert != null) {
            list.add(
                NotificationItem(
                    id = "notif_consequence_${consequenceAlert.id}",
                    title = consequenceAlert.title,
                    message = consequenceAlert.description,
                    timestamp = timeFmt.format(java.util.Date(consequenceAlert.timestamp)),
                    category = NotificationCategory.IMPORTANT,
                    timeGroup = "Today"
                )
            )
        }

        // 2. Dynamic Ghost Proposals (Ghost Mode badge)
        val seenProposalIds = mutableSetOf<Int>()
        for (proposal in ghostEngineProposals) {
            if (proposal.numericId in ignoredIds) continue
            seenProposalIds.add(proposal.numericId)
            val isApplied = proposal.numericId in appliedIds || proposal.lifecycle == GhostProposalLifecycle.APPLIED
            val title = if (isApplied) "${proposal.activityName} Mutation Applied" else "Proposed: ${proposal.activityName} Change"
            val message = if (isApplied) proposal.potentialConsequences else proposal.evidenceSummary
            list.add(
                NotificationItem(
                    id = "notif_ghost_p_${proposal.id}",
                    title = title,
                    message = message,
                    timestamp = timeFmt.format(java.util.Date(proposal.timestamp)),
                    category = NotificationCategory.GHOST_MODE,
                    timeGroup = "Today",
                    ghostProposalId = proposal.numericId,
                    isActionApplied = isApplied
                )
            )
        }

        // Also check any DB ghost events not in GhostModeEngine
        for (activeGhost in ghostList) {
            if (activeGhost.id in ignoredIds || activeGhost.id in seenProposalIds) continue
            val isApplied = activeGhost.id in appliedIds
            list.add(
                NotificationItem(
                    id = "notif_ghost_e_${activeGhost.id}",
                    title = if (isApplied) "${activeGhost.title} Applied" else "Possible ${activeGhost.title} Change",
                    message = activeGhost.evidenceText ?: "Evidence received from notification.",
                    timestamp = timeFmt.format(java.util.Date(activeGhost.startTime)),
                    category = NotificationCategory.GHOST_MODE,
                    timeGroup = "Today",
                    ghostProposalId = activeGhost.id,
                    isActionApplied = isApplied
                )
            )
        }

        // 3. Dynamic Events Reminders (AI badge)
        for (event in events.take(3)) {
            val eventTime = timeFmt.format(java.util.Date(event.startTime))
            list.add(
                NotificationItem(
                    id = "notif_event_${event.id}",
                    title = "${event.title} Reminder",
                    message = "Today at $eventTime${if (!event.location.isNullOrBlank()) " • ${event.location}" else ""}",
                    timestamp = eventTime,
                    category = NotificationCategory.AI,
                    timeGroup = "Today"
                )
            )
        }

        // 4. Dynamic Mode Readiness (System badge)
        if (report.totalCount > 0) {
            val missingCount = report.pendingItems.size
            val score = report.percentage
            val readinessMessage = if (score < 0) {
                "No requirements configured for this mode."
            } else {
                "You're $score% ready, $missingCount items missing."
            }
            list.add(
                NotificationItem(
                    id = "notif_readiness_status",
                    title = "${activeMode.value?.name ?: "Current"} Readiness",
                    message = readinessMessage,
                    timestamp = timeFmt.format(java.util.Date()),
                    category = NotificationCategory.SYSTEM,
                    timeGroup = "Today"
                )
            )
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sprint 6: Conversational Chat Stream for AI Assistant
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                id = "chat_welcome",
                sender = ChatSender.AI,
                text = "Welcome to LIFEOS. Your local-first, zero-cloud life assistant is ready. How can I help you organize your day?",
                timestamp = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // Stage 3 & 4: Personal Life Graph Streams
    val graphNodes: StateFlow<List<NodeEntity>> = dao.getAllNodesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val graphEdges: StateFlow<List<EdgeEntity>> = dao.getAllEdgesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Stage 4: Consequence Engine Live Alert
    val consequenceAlert: StateFlow<ConsequenceAlert?> = ConsequenceEngine.latestConsequenceAlert

    // Stage 5: Personal Forgetting & Habit Learning Model
    val allHabits: StateFlow<List<ItemHabitEntity>> = dao.getAllHabitsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val frequentlyForgottenHabits: StateFlow<List<ItemHabitEntity>> = dao.getFrequentlyForgottenHabitsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Stage 6: Device Presence & Battery Budget Streams
    val batteryLevel: StateFlow<Int> = BatteryBudgetManager.currentBatteryLevel
    val powerProfile: StateFlow<PowerProfile> = BatteryBudgetManager.currentPowerProfile
    val detectedDevices: StateFlow<List<DevicePresenceResult>> = DevicePresenceScanner.detectedDevices

    // Stage 6: Replay & Simulation Engine Stream
    val replayState: StateFlow<ReplayState> = ReplayEngine.replayState

    // Stage 7: Privacy Dashboard & Permission Firewall Streams
    val isSafeModeEnabled: StateFlow<Boolean> = SafeModeManager.isSafeModeEnabled
    val channelToggles: StateFlow<Map<SourceType, Boolean>> = PermissionFirewall.channelToggles

    private val _contextEvaluation = MutableStateFlow<MultiModeContextResult?>(null)
    val contextEvaluation: StateFlow<MultiModeContextResult?> = _contextEvaluation.asStateFlow()

    // Sprint 2: Reactive Dashboard State
    val dashboardState: StateFlow<DashboardState> = combine(
        combine(activeMode, activeItems, allEvents) { mode, items, events -> Triple(mode, items, events) },
        combine(allItems, consequenceAlert, isSafeModeEnabled) { allItems, alert, safe -> Triple(allItems, alert, safe) },
        _contextEvaluation
    ) { (mode, activeItemsList, eventsList), (allItemsList, alert, safeMode), contextEval ->
        val missing = activeItemsList.filter { !it.isReady && !it.isWaived }
        val ready = activeItemsList.filter { it.isReady }
        val percentage = if (activeItemsList.isEmpty()) -1 else ((ready.size.toFloat() / activeItemsList.size.toFloat()) * 100).toInt().coerceIn(0, 100)
        val nonGhostEvents = eventsList.filter { !it.isGhostMode }.sortedBy { it.startTime }
        val now = System.currentTimeMillis()
        val nextEvent = nonGhostEvents.firstOrNull { it.endTime >= now } ?: nonGhostEvents.firstOrNull()

        val todayCount = eventsList.count { !it.isGhostMode }
        val pendingTasks = allItemsList.count { !it.isReady && !it.isWaived }
        val missingCount = missing.size

        DashboardState(
            activeMode = mode,
            activeItems = activeItemsList,
            missingItems = missing,
            readyItems = ready,
            readinessPercentage = percentage,
            upcomingEvents = nonGhostEvents,
            nextUpEvent = nextEvent,
            todayStats = TodayStats(
                todayEventsCount = todayCount,
                pendingTasksCount = pendingTasks,
                missingItemsCount = missingCount
            ),
            consequenceAlert = alert,
            isSafeModeEnabled = safeMode,
            detectedMode = contextEval?.topCandidateMode,
            detectedConfidenceLevel = contextEval?.confidenceLevel ?: ContextConfidenceLevel.NO_CONTEXT,
            detectedConfidenceScore = contextEval?.totalConfidence ?: 0.0f,
            contextActionDecision = contextEval?.actionDecision ?: ContextActionDecision.RETAIN_CURRENT,
            contextStatusMessage = contextEval?.statusMessage ?: "",
            contextSignals = contextEval?.matchedSignals ?: emptyList()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    fun markItemReady(itemId: Int) {
        viewModelScope.launch {
            dao.updateItemStatus(itemId, true)
            dao.getItemById(itemId)?.let { item ->
                ForgettingModelEngine.recordItemConfirmed(dao, item.itemName, item.modeId)
            }
        }
    }

    fun markItemAsReady(itemId: Int) {
        markItemReady(itemId)
    }

    // Sprint 4: Mode Detail & Catalog State
    private val _selectedDetailModeId = MutableStateFlow<Int?>(1)
    val selectedModeDetail: StateFlow<LifeModeEntity?> = combine(allModes, _selectedDetailModeId) { modes, id ->
        modes.find { it.id == id } ?: modes.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectModeForDetail(modeId: Int) {
        _selectedDetailModeId.value = modeId
    }

    fun modeItemsFlow(modeId: Int): Flow<List<ItemRequirementEntity>> = dao.getItemsForModeFlow(modeId)

    fun setActiveMode(modeId: Int) {
        selectMode(modeId)
    }

    fun deactivateMode(modeId: Int) {
        viewModelScope.launch {
            dao.deactivateModeById(modeId)
        }
    }

    fun deleteMode(modeId: Int, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            if (modeId in 1..6) {
                onComplete?.invoke(false)
                return@launch
            }
            val mode = dao.getModeById(modeId)
            if (mode != null) {
                LifeGraphEngine.pruneModeFromGraph(dao, mode.name)
            }
            if (activeMode.value?.id == modeId) {
                dao.setActiveMode(1)
            }
            dao.deleteLifeMode(modeId)
            onComplete?.invoke(true)
        }
    }

    fun addItemToMode(modeId: Int, itemName: String, isTemporary: Boolean = false) {
        if (itemName.isBlank()) return
        val cleanName = itemName.trim()
        viewModelScope.launch {
            val existing = dao.findItemInModeByName(modeId, cleanName)
            if (existing != null) return@launch
            dao.insertItem(
                ItemRequirementEntity(
                    modeId = modeId,
                    itemName = cleanName,
                    isReady = false,
                    isTemporary = isTemporary,
                    confidenceScore = 1.0f,
                    sourceEvidence = "Manual User Entry",
                    isGhostMode = false,
                    isWaived = false
                )
            )
        }
    }

    fun createMode(name: String, iconRes: String = "ic_mode", priority: Int = 1) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.insertLifeMode(
                LifeModeEntity(
                    name = name.trim(),
                    iconRes = iconRes,
                    isActive = false,
                    priority = priority
                )
            )
        }
    }

    fun importTemplateMode(templateName: String, iconRes: String, items: List<String>) {
        viewModelScope.launch {
            val newPriority = (allModes.value.maxOfOrNull { it.priority } ?: 0) + 1
            val newModeId = dao.insertLifeMode(
                LifeModeEntity(
                    name = templateName,
                    iconRes = iconRes,
                    isActive = false,
                    priority = newPriority
                )
            ).toInt()
            items.forEach { itemName ->
                dao.insertItem(
                    ItemRequirementEntity(
                        modeId = newModeId,
                        itemName = itemName,
                        isReady = false,
                        sourceEvidence = "Template Checklist"
                    )
                )
            }
        }
    }

    fun updateMode(mode: LifeModeEntity) {
        viewModelScope.launch {
            dao.updateLifeMode(mode)
        }
    }

    fun addNewItem(item: ItemRequirementEntity) {
        viewModelScope.launch {
            dao.insertItem(item)
        }
    }

    init {
        ensureDefaultData()
    }

    fun ensureDefaultData() {
        viewModelScope.launch(Dispatchers.IO) {
            // Seed Sports Mode if missing
            if (dao.getModeById(4) == null) {
                dao.insertLifeMode(LifeModeEntity(id = 4, name = "Sports", iconRes = "ic_sports", isActive = false, priority = 4))
                dao.insertItem(ItemRequirementEntity(modeId = 4, itemName = "Football Boots", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
                dao.insertItem(ItemRequirementEntity(modeId = 4, itemName = "Jersey & Shorts", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
                dao.insertItem(ItemRequirementEntity(modeId = 4, itemName = "Shin Guards", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
                dao.insertItem(ItemRequirementEntity(modeId = 4, itemName = "Energy Drink", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            }
            // Seed Work Mode if missing
            if (dao.getModeById(5) == null) {
                dao.insertLifeMode(LifeModeEntity(id = 5, name = "Work", iconRes = "ic_work", isActive = false, priority = 5))
                dao.insertItem(ItemRequirementEntity(modeId = 5, itemName = "Company Badge", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
                dao.insertItem(ItemRequirementEntity(modeId = 5, itemName = "Laptop & Charger", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
                dao.insertItem(ItemRequirementEntity(modeId = 5, itemName = "Office Notebook", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
                dao.insertItem(ItemRequirementEntity(modeId = 5, itemName = "Access Keycard", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            }
            // Seed Shopping Mode if missing
            if (dao.getModeById(6) == null) {
                dao.insertLifeMode(LifeModeEntity(id = 6, name = "Shopping", iconRes = "ic_shopping", isActive = false, priority = 6))
                dao.insertItem(ItemRequirementEntity(modeId = 6, itemName = "Eco Grocery Bags", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
                dao.insertItem(ItemRequirementEntity(modeId = 6, itemName = "Shopping List", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
                dao.insertItem(ItemRequirementEntity(modeId = 6, itemName = "Wallet / Cards", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            }

            // Ensure College Mode has wireframe requirements (Notebook, Watch)
            if (dao.getItemsByNameLike("Notebook").isEmpty()) {
                dao.insertItem(ItemRequirementEntity(modeId = 1, itemName = "Notebook", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            }
            if (dao.getItemsByNameLike("Watch").isEmpty()) {
                dao.insertItem(ItemRequirementEntity(modeId = 1, itemName = "Watch", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            }

            fun getTodayAt(hour: Int, min: Int = 0): Long {
                val c = java.util.Calendar.getInstance()
                c.set(java.util.Calendar.HOUR_OF_DAY, hour)
                c.set(java.util.Calendar.MINUTE, min)
                c.set(java.util.Calendar.SECOND, 0)
                c.set(java.util.Calendar.MILLISECOND, 0)
                return c.timeInMillis
            }

            // Seed / align default events to match wireframe Screen 9
            val existingEvents = dao.getAllEventsFlow().first()
            if (existingEvents.isEmpty()) {
                dao.insertEvent(EventEntity(
                    id = 1,
                    title = "Data Structures",
                    startTime = getTodayAt(9, 0),
                    endTime = getTodayAt(10, 0),
                    location = "Room 301",
                    associatedModeId = 1,
                    isGhostMode = false,
                    confidence = 1.0f,
                    evidenceText = "Semester Schedule"
                ))
                dao.insertEvent(EventEntity(
                    id = 2,
                    title = "DBMS Lab",
                    startTime = getTodayAt(10, 0),
                    endTime = getTodayAt(12, 0),
                    location = "Lab 204",
                    associatedModeId = 1,
                    isGhostMode = false,
                    confidence = 1.0f,
                    evidenceText = "Weekly Lab Schedule"
                ))
                dao.insertEvent(EventEntity(
                    id = 3,
                    title = "Lunch Break",
                    startTime = getTodayAt(12, 0),
                    endTime = getTodayAt(13, 0),
                    location = "Campus Cafeteria",
                    associatedModeId = 1,
                    isGhostMode = false,
                    confidence = 1.0f,
                    evidenceText = "Daily Routine"
                ))
                dao.insertEvent(EventEntity(
                    id = 4,
                    title = "OOP Class",
                    startTime = getTodayAt(14, 0),
                    endTime = getTodayAt(15, 0),
                    location = "Room 205",
                    associatedModeId = 1,
                    isGhostMode = false,
                    confidence = 1.0f,
                    evidenceText = "Department Timetable"
                ))
                dao.insertEvent(EventEntity(
                    id = 5,
                    title = "Project Discussion",
                    startTime = getTodayAt(16, 0),
                    endTime = getTodayAt(17, 0),
                    location = "Room 110",
                    associatedModeId = 1,
                    isGhostMode = false,
                    confidence = 1.0f,
                    evidenceText = "Capstone Project Team"
                ))
            } else {
                // Ensure wireframe events are present and aligned
                val hasLunch = existingEvents.any { it.title.contains("Lunch", ignoreCase = true) }
                val hasProject = existingEvents.any { it.title.contains("Project", ignoreCase = true) }
                if (!hasLunch) {
                    dao.insertEvent(EventEntity(
                        title = "Lunch Break",
                        startTime = getTodayAt(12, 0),
                        endTime = getTodayAt(13, 0),
                        location = "Campus Cafeteria",
                        associatedModeId = 1,
                        isGhostMode = false,
                        confidence = 1.0f,
                        evidenceText = "Daily Routine"
                    ))
                }
                if (!hasProject) {
                    dao.insertEvent(EventEntity(
                        title = "Project Discussion",
                        startTime = getTodayAt(16, 0),
                        endTime = getTodayAt(17, 0),
                        location = "Room 110",
                        associatedModeId = 1,
                        isGhostMode = false,
                        confidence = 1.0f,
                        evidenceText = "Capstone Project Team"
                    ))
                }
                // Align existing core lectures if needed
                existingEvents.find { it.title.contains("Data Structures", ignoreCase = true) }?.let { ds ->
                    if (ds.location != "Room 301") {
                        dao.insertEvent(ds.copy(title = "Data Structures", startTime = getTodayAt(9, 0), endTime = getTodayAt(10, 0), location = "Room 301"))
                    }
                }
                existingEvents.find { it.title.contains("DBMS Lab", ignoreCase = true) }?.let { dbms ->
                    if (dbms.location != "Lab 204") {
                        dao.insertEvent(dbms.copy(title = "DBMS Lab", startTime = getTodayAt(10, 0), endTime = getTodayAt(12, 0), location = "Lab 204"))
                    }
                }
                existingEvents.find { it.title.contains("OOP Class", ignoreCase = true) }?.let { oop ->
                    if (oop.location != "Room 205") {
                        dao.insertEvent(oop.copy(title = "OOP Class", startTime = getTodayAt(14, 0), endTime = getTodayAt(15, 0), location = "Room 205"))
                    }
                }
            }

            // Seed wireframe Ghost Proposal (Screen 11: Possible Lab Cancellation)
            val currentGhostEvents = dao.getGhostEventsFlow().first()
            if (currentGhostEvents.none { it.id == 99 }) {
                dao.insertEvent(
                    EventEntity(
                        id = 99,
                        title = "DBMS Lab",
                        startTime = getTodayAt(10, 0),
                        endTime = getTodayAt(12, 0),
                        location = "Lab 204",
                        associatedModeId = 1,
                        isGhostMode = true,
                        confidence = 0.62f,
                        evidenceText = "Maybe tomorrow's lab is cancelled"
                    )
                )
            }
            GhostModeEngine.restoreFromDatabase(dao)
        }
    }

    fun addCalendarEvent(event: EventEntity) {
        viewModelScope.launch {
            dao.insertEvent(event)
        }
    }

    fun deleteEvent(eventId: Int) {
        viewModelScope.launch {
            dao.deleteEventById(eventId)
        }
    }

    fun selectMode(modeId: Int) {
        viewModelScope.launch {
            dao.setActiveMode(modeId)
            ContextRecognitionEngine.notifyManualModeSelected(modeId)
            _contextEvaluation.value = null
        }
    }

    fun evaluateCurrentContext(
        locationHint: String? = null,
        isExit: Boolean = false,
        isEnter: Boolean = false,
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val result = ContextRecognitionEngine.evaluateAllModes(
                dao = dao,
                currentLocationHint = locationHint,
                isGeofenceExit = isExit,
                isGeofenceEnter = isEnter,
                currentTimestamp = timestamp
            )
            _contextEvaluation.value = result
            if (result.actionDecision == ContextActionDecision.AUTO_SWITCH && result.topCandidateMode != null) {
                dao.setActiveMode(result.topCandidateMode.id)
            }
        }
    }

    fun applyProposedMode() {
        val proposed = _contextEvaluation.value?.topCandidateMode
        if (proposed != null) {
            viewModelScope.launch {
                dao.setActiveMode(proposed.id)
                _contextEvaluation.value = null
            }
        }
    }

    fun dismissContextProposal() {
        _contextEvaluation.value = null
    }

    fun toggleItemStatus(item: ItemRequirementEntity) {
        viewModelScope.launch {
            val nextReady = !item.isReady
            dao.updateItemStatus(item.id, nextReady)
            if (nextReady) {
                ForgettingModelEngine.recordItemConfirmed(dao, item.itemName, item.modeId)
            }
        }
    }

    fun applyGhostItem(itemId: Int) {
        viewModelScope.launch {
            val item = dao.getItemById(itemId)
            if (item != null && (item.itemName.contains("Cancellation", ignoreCase = true) || item.itemName.contains("Cancel", ignoreCase = true))) {
                val activityName = if (item.itemName.contains("DBMS", ignoreCase = true)) "DBMS Lab" else "Scheduled Activity"
                ConsequenceEngine.applyConfirmedProposal(
                    dao = dao,
                    activityName = activityName,
                    rawText = item.sourceEvidence ?: "User approved cancellation proposal",
                    confidence = item.confidenceScore,
                    targetModeId = item.modeId
                )
            } else {
                dao.promoteGhostItem(itemId)
            }
        }
    }

    fun ignoreGhostItem(itemId: Int) {
        viewModelScope.launch {
            dao.deleteItemById(itemId)
        }
    }

    fun applyGhostEvent(eventId: Int) {
        viewModelScope.launch {
            dao.promoteGhostEvent(eventId)
        }
    }

    fun ignoreGhostEvent(eventId: Int) {
        viewModelScope.launch {
            dao.deleteEventById(eventId)
        }
    }

    fun applyGhostProposal(proposalId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = GhostModeEngine.approveProposal(proposalId, dao)
            when (result) {
                is GhostApprovalResult.Success -> {
                    _appliedProposalIds.value = _appliedProposalIds.value + proposalId
                }
                is GhostApprovalResult.AlreadyApplied -> {
                    _appliedProposalIds.value = _appliedProposalIds.value + proposalId
                }
                is GhostApprovalResult.NotFound -> {
                    // Fallback to direct DB promotion if created outside engine
                    val proposal = ghostProposalsFlow.value.find { it.id == proposalId }
                        ?: dao.getAllEventsFlow().first().find { it.id == proposalId }

                    if (proposal != null) {
                        if (proposal.evidenceText?.contains("cancel", ignoreCase = true) == true ||
                            proposal.title.contains("DBMS Lab", ignoreCase = true) ||
                            proposal.title.contains("Cancel", ignoreCase = true)
                        ) {
                            val activityName = if (proposal.title.contains("DBMS", ignoreCase = true)) "DBMS Lab" else "Scheduled Activity"
                            ConsequenceEngine.applyConfirmedProposal(
                                dao = dao,
                                activityName = activityName,
                                rawText = proposal.evidenceText ?: "Lab cancelled by user confirmation",
                                confidence = proposal.confidence,
                                targetModeId = proposal.associatedModeId
                            )
                        } else {
                            dao.promoteGhostEvent(proposalId)
                        }
                        _appliedProposalIds.value = _appliedProposalIds.value + proposalId
                    } else {
                        val ghostItem = dao.getAllItemsFlow().first().find { it.id == proposalId && it.isGhostMode }
                        if (ghostItem != null) {
                            dao.promoteGhostItem(proposalId)
                            _appliedProposalIds.value = _appliedProposalIds.value + proposalId
                        }
                    }
                }
                else -> {
                    Log.w("LIFEOS_VM", "Ghost proposal approval outcome: $result")
                }
            }
        }
    }

    fun ignoreGhostProposal(proposalId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            GhostModeEngine.rejectProposal(proposalId, dao)
            dao.deleteEventById(proposalId)
            dao.deleteItemById(proposalId)
            _ignoredProposalIds.value = _ignoredProposalIds.value + proposalId
        }
    }

    fun deleteItem(itemId: Int) {
        viewModelScope.launch {
            val item = dao.getItemById(itemId)
            if (item != null) {
                LifeGraphEngine.pruneItemFromGraph(dao, item.itemName)
                dao.deleteHabit(item.itemName, item.modeId)
            }
            dao.deleteItemById(itemId)
        }
    }

    /**
     * Phase 8A: Explicit user confirmation that an item was forgotten.
     */
    fun confirmItemForgotten(itemId: Int) {
        viewModelScope.launch {
            dao.getItemById(itemId)?.let { item ->
                ForgettingModelEngine.recordConfirmedForgetting(dao, item.itemName, item.modeId)
            }
        }
    }

    fun confirmItemForgottenByName(itemName: String, modeId: Int) {
        viewModelScope.launch {
            ForgettingModelEngine.recordConfirmedForgetting(dao, itemName, modeId)
        }
    }

    fun updateItem(item: ItemRequirementEntity) {
        viewModelScope.launch {
            dao.updateItem(item)
        }
    }

    fun renameItem(itemId: Int, newName: String, isTemporary: Boolean = false) {
        if (newName.isBlank()) return
        val cleanName = newName.trim()
        viewModelScope.launch {
            val item = dao.getItemById(itemId) ?: return@launch
            val existing = dao.findItemInModeByName(item.modeId, cleanName)
            if (existing != null && existing.id != itemId) return@launch
            dao.updateItem(item.copy(itemName = cleanName, isTemporary = isTemporary))
        }
    }

    fun addCustomItem(modeId: Int, itemName: String) {
        if (itemName.isBlank()) return
        val cleanName = itemName.trim()
        viewModelScope.launch {
            val existing = dao.findItemInModeByName(modeId, cleanName)
            if (existing != null) return@launch
            dao.insertItem(
                ItemRequirementEntity(
                    modeId = modeId,
                    itemName = cleanName,
                    isReady = false,
                    isTemporary = false,
                    confidenceScore = 1.0f,
                    sourceEvidence = "Manually Added",
                    isGhostMode = false,
                    isWaived = false
                )
            )
        }
    }

    fun addCustomEvent(
        title: String,
        startTime: Long,
        endTime: Long,
        location: String,
        modeId: Int
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            dao.insertEvent(
                EventEntity(
                    title = title.trim(),
                    startTime = startTime,
                    endTime = endTime,
                    location = location.trim(),
                    associatedModeId = modeId,
                    isGhostMode = false,
                    confidence = 1.0f,
                    evidenceText = "Manually Scheduled"
                )
            )
        }
    }

    fun processAssistantPrompt(
        prompt: String,
        onResult: (String) -> Unit = {}
    ) {
        if (prompt.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val payload = LightweightEventFilter.filter(prompt, "com.lifeos.assistant")
            val gate = DecisionGate()
            val decision = gate.processPayload(payload, dao)

            when (decision) {
                is DecisionResult.AutoCommitted -> {
                    onResult("Auto-committed: '${decision.item.itemName}' added to checklist!")
                }
                is DecisionResult.GhostModeRouted -> {
                    onResult("Routed to Ghost Mode: '${decision.item.itemName}' (${(payload.relevanceScore * 100).toInt()}% conf)")
                }
                is DecisionResult.ConsequenceApplied -> {
                    onResult("Consequence applied: ${decision.alert.title}")
                }
                is DecisionResult.ConflictDetected -> {
                    onResult("Schedule conflict detected: ${decision.conflictAlert.conflictingClaim}")
                }
                is DecisionResult.DuplicateDiscarded -> {
                    onResult("Duplicate update discarded: ${decision.itemOrEventName}")
                }
                is DecisionResult.Ignored -> {
                    if (payload.isAdversarial) {
                        onResult("Security alert: Adversarial prompt injection rejected.")
                    } else {
                        val targetMode = activeMode.value?.id ?: 1
                        val sanitized = DataMinimizer.sanitizeRawPayload(prompt)
                        val isGhost = SafeModeManager.isSafeModeEnabled.value
                        val item = ItemRequirementEntity(
                            modeId = targetMode,
                            itemName = sanitized.trim(),
                            isReady = false,
                            isTemporary = false,
                            confidenceScore = 1.0f,
                            sourceEvidence = "Assistant Prompt",
                            isGhostMode = isGhost,
                            isWaived = false
                        )
                        dao.insertItem(item)
                        if (isGhost) {
                            onResult("Safe Mode Active: Item '${item.itemName}' routed to Ghost Mode.")
                        } else {
                            onResult("Added requirement '${item.itemName}' to current mode.")
                        }
                    }
                }
            }
        }
    }

    fun sendChatMessage(prompt: String) {
        if (prompt.isBlank()) return
        val nowTime = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val userMsg = ChatMessage(
            sender = ChatSender.USER,
            text = prompt.trim(),
            timestamp = nowTime
        )
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch(Dispatchers.IO) {
            val lower = prompt.trim().lowercase()
            if (lower.contains("college") || lower.contains("going to") || lower.contains("ready") || lower.contains("readiness")) {
                // Return dynamic college readiness report
                val items = dao.getItemsForModeFlow(1).first()
                val readyCount = items.count { it.isReady }
                val score = if (items.isEmpty()) -1 else ((readyCount.toFloat() / items.size.toFloat()) * 100).toInt().coerceIn(0, 100)
                val itemsList = items.map { it.itemName to it.isReady }
                val hasLab = dao.getAllEventsFlow().first().any { !it.isGhostMode && it.title.contains("DBMS Lab", ignoreCase = true) }
                val suggestion = if (hasLab) "You have DBMS Lab at 10 AM. Don't forget your record." else "Your schedule is clear for labs today."

                val aiMsg = ChatMessage(
                    sender = ChatSender.AI,
                    text = "Here's your College Readiness:",
                    timestamp = nowTime,
                    readinessPercentage = score,
                    readinessItems = itemsList,
                    suggestion = suggestion
                )
                _chatMessages.value = _chatMessages.value + aiMsg
            } else {
                // Pass through NLP parser & processAssistantPrompt
                processAssistantPrompt(prompt) { statusText ->
                    val aiMsg = ChatMessage(
                        sender = ChatSender.AI,
                        text = "I've analyzed your request: $statusText",
                        timestamp = nowTime
                    )
                    _chatMessages.value = _chatMessages.value + aiMsg
                }
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
    }

    // Stage 4 Consequence Actions
    fun dismissConsequenceAlert() {
        ConsequenceEngine.dismissAlert()
    }

    fun simulateCancellation(activityName: String = "DBMS Lab") {
        viewModelScope.launch {
            ConsequenceEngine.handleDirectCancellation(dao, activityName)
        }
    }

    fun resetWaivedItems(modeId: Int) {
        viewModelScope.launch {
            ConsequenceEngine.resetWaivedItems(dao, modeId)
        }
    }

    // Stage 5 Habit Actions
    fun simulateForgetIncrement(itemName: String, modeId: Int) {
        viewModelScope.launch {
            ForgettingModelEngine.recordItemForgotten(dao, itemName, modeId)
        }
    }

    // Stage 6 Controls
    fun setSimulatedBattery(level: Int) {
        BatteryBudgetManager.setSimulatedBattery(level)
    }

    fun simulateBlePresence(deviceName: String, confidence: PresenceConfidence) {
        DevicePresenceScanner.simulateDeviceState(deviceName, confidence)
    }

    fun startReplaySimulation() {
        viewModelScope.launch {
            ReplayEngine.runDefaultScenario(dao)
        }
    }

    fun resetReplaySimulation() {
        ReplayEngine.resetSimulation()
    }

    // Stage 7 Privacy & Hardening Controls
    fun toggleSafeMode() {
        SafeModeManager.toggleSafeMode()
    }

    fun setSafeMode(enabled: Boolean) {
        SafeModeManager.setSafeMode(enabled)
    }

    fun setChannelAuthorized(source: SourceType, enabled: Boolean) {
        PermissionFirewall.setChannelAuthorized(source, enabled)
    }

    fun pruneEvidenceLogs() {
        viewModelScope.launch {
            DataMinimizer.pruneEvidenceLogs(dao)
        }
    }

    fun clearEvidenceLogs() {
        viewModelScope.launch {
            DataMinimizer.clearEvidenceLogs(dao)
        }
    }

    fun clearTemporaryItems(onResult: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = dao.clearTemporaryItems()
            onResult(count)
        }
    }

    // Phase 7: Data Deletion, Factory Reset, Local Export, and Database Integrity
    fun clearAllUserData(onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAllUserData()
            _chatMessages.value = emptyList()
            _appliedProposalIds.value = emptySet()
            _ignoredProposalIds.value = emptySet()
            onComplete()
        }
    }

    fun resetToFactoryDefaults(onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.resetToFactoryDefaults()
            _chatMessages.value = emptyList()
            _appliedProposalIds.value = emptySet()
            _ignoredProposalIds.value = emptySet()
            onComplete()
        }
    }

    fun exportLocalData(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val json = com.lifeos.app.security.DataExporter.exportToJson(dao)
            onResult(json)
        }
    }

    fun checkDatabaseIntegrity(context: android.content.Context, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = LifeOsDatabase.getDatabase(context)
            val res = db.checkIntegrity()
            onResult(res)
        }
    }

    // --- Final Phase: User Profile & Identity ---
    fun updateUserProfile(profile: UserProfileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            UserProfileEngine.updateProfile(dao, profile)
        }
    }

    // --- Final Phase: Anchor Locations ---
    fun addAnchorPlace(place: AnchorPlaceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            AnchorLocationEngine.addAnchor(dao, place)
        }
    }

    fun deleteAnchorPlace(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            AnchorLocationEngine.deleteAnchor(dao, id)
        }
    }

    fun toggleIgnoreAnchor(id: Int, ignored: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            AnchorLocationEngine.toggleIgnoreAnchor(dao, id, ignored)
        }
    }

    // --- Final Phase: Memory Vault ---
    fun saveMemory(
        content: String,
        memoryType: String = "SHORT_TERM",
        tags: String = "",
        ttlOption: MemoryTtlOption = MemoryTtlOption.TWENTY_FOUR_HOURS,
        onResult: (Long) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = MemoryVaultEngine.storeMemory(dao, content, memoryType, tags, ttlOption)
            onResult(id)
        }
    }

    fun deleteMemory(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            MemoryVaultEngine.deleteMemory(dao, id)
        }
    }

    fun cleanupExpiredMemories(onResult: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = MemoryVaultEngine.cleanupExpiredMemories(dao)
            onResult(count)
        }
    }

    fun askMemory(query: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = MemoryVaultEngine.askLifeOsMemory(dao, query)
            onResult(res.first, res.second)
        }
    }

    // --- Final Phase: Hands-Free Voice Assistant ---
    fun processVoiceUtterance(utterance: String, onResult: (VoiceCommandResult) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = HandsFreeVoiceManager.processVoiceCommand(utterance, dao)
            onResult(result)
        }
    }

    fun setVoiceState(state: VoiceState) {
        HandsFreeVoiceManager.setVoiceState(state)
    }

    fun setVoiceLanguage(language: String) {
        HandsFreeVoiceManager.setLanguage(language)
    }

    // --- Final Phase: Emergency SOS ---
    fun addEmergencyContact(name: String, phoneNumber: String, relationship: String = "Contact", isPrimary: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertEmergencyContact(EmergencyContactEntity(name = name, phoneNumber = phoneNumber, relationship = relationship, isPrimary = isPrimary))
        }
    }

    fun deleteEmergencyContact(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteEmergencyContactById(id)
        }
    }

    fun startSosCountdown(seconds: Int = 5) {
        EmergencySosManager.startCountdown(seconds)
    }

    fun tickSosCountdown(): Int {
        return EmergencySosManager.tickCountdown()
    }

    fun cancelSos() {
        EmergencySosManager.cancelSos()
    }

    fun resetSos() {
        EmergencySosManager.resetSos()
    }

    fun setSosTestMode(enabled: Boolean) {
        EmergencySosManager.setTestMode(enabled)
    }

    fun triggerSosDispatch(
        context: android.content.Context,
        lat: Double? = null,
        lon: Double? = null,
        onResult: (SosDispatchResult) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = EmergencySosManager.executeSosDispatch(context, dao, lat, lon)
            onResult(result)
        }
    }

    // --- Final Phase: App Usage Rules & Classification ---
    fun saveAppClassification(packageName: String, appName: String, category: String, limitMinutes: Int = 60) {
        viewModelScope.launch(Dispatchers.IO) {
            TimeAuditManager.saveAppClassification(dao, packageName, appName, category, limitMinutes)
        }
    }

    // --- Final Phase: Smart Clipboard Inspection ---
    fun inspectClipboard(context: android.content.Context): ClipboardEventProposal? {
        return SmartClipboardManager.inspectClipboardOnDemand(context)
    }

    // --- Final Phase: Local Data Export ---
    fun exportDataJson(onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val json = com.lifeos.app.security.DataExporter.exportToJson(dao)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onComplete(json)
            }
        }
    }
}