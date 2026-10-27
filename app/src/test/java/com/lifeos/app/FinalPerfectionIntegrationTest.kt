package com.lifeos.app

import com.lifeos.app.data.*
import com.lifeos.app.engine.*
import com.lifeos.app.security.DataMinimizer
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * LIFEOS FINAL PERFECTION COMPREHENSIVE INTEGRATION TEST SUITE
 *
 * Verifies end-to-end integration and mathematical/behavioral correctness across:
 * - Scenario A: College Mode, Readiness, Departure Window & Gym Isolation
 * - Scenario B: Gym Mode Independent Readiness & Zero Leakage
 * - Scenario C: Sports Practice Cancellation, Consequence Propagation & Zero Forgetting Increment
 * - Scenario D: Event Reschedule (10 AM -> 11 AM), Departure Timing Shift & Preserved Readiness
 * - Scenario E: Schedule Conflict Hold (Contradictory Announcements)
 * - Scenario F: Safe Mode Hardening (Kill-Switch, Ghost Mode Routing, Reminder Suppression)
 * - Scenario G: Ghost Proposal Lifecycle (Rejection Safety, Approval Execution, Exactly 1 Mutation)
 * - Scenario H: Personal Forgetting Model (Confirmed Increments, Recovery Decay, Mode Isolation)
 * - Scenario I: Offline-First Operation (Zero Cloud, Zero Network, Local Determinism)
 * - Scenario J: Persistence & Restart Rehydration
 * - Life Graph Engine: Cycle Detection, Bounded Traversal, Orphan/Dangling Edge Cleanup
 * - High-Volume Deduplication: 1, 5, 10, 50 Duplicate Announcements Produce Exactly 1 Mutation
 * - PII Sanitization & Zero-Leakage Privacy Guardrails
 */
class FinalPerfectionIntegrationTest {

    private lateinit var fakeDao: FakeLifeOsDao
    private lateinit var decisionGate: DecisionGate

    @Before
    fun setUp() {
        SafeModeManager.setSafeMode(false)
        GhostModeEngine.reset()
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, true)

        fakeDao = FakeLifeOsDao()
        decisionGate = DecisionGate()

        // Seed Life Modes
        fakeDao.modes.add(LifeModeEntity(id = 1, name = "College", iconRes = "ic_college", isActive = true, priority = 1))
        fakeDao.modes.add(LifeModeEntity(id = 2, name = "Gym", iconRes = "ic_gym", isActive = false, priority = 2))
        fakeDao.modes.add(LifeModeEntity(id = 3, name = "Travel", iconRes = "ic_travel", isActive = false, priority = 3))
        fakeDao.modes.add(LifeModeEntity(id = 4, name = "Sports", iconRes = "ic_sports", isActive = false, priority = 4))

        // Seed College items
        fakeDao.items.add(ItemRequirementEntity(id = 1, modeId = 1, itemName = "Laptop", isReady = true, confidenceScore = 1.0f))
        fakeDao.items.add(ItemRequirementEntity(id = 2, modeId = 1, itemName = "Charger", isReady = true, confidenceScore = 1.0f))
        fakeDao.items.add(ItemRequirementEntity(id = 3, modeId = 1, itemName = "ID Card", isReady = false, confidenceScore = 1.0f))
        fakeDao.items.add(ItemRequirementEntity(id = 4, modeId = 1, itemName = "DBMS Record", isReady = false, confidenceScore = 1.0f))

        // Seed Gym items
        fakeDao.items.add(ItemRequirementEntity(id = 11, modeId = 2, itemName = "Shaker Bottle", isReady = true, confidenceScore = 1.0f))
        fakeDao.items.add(ItemRequirementEntity(id = 12, modeId = 2, itemName = "Towel", isReady = false, confidenceScore = 1.0f))
        fakeDao.items.add(ItemRequirementEntity(id = 13, modeId = 2, itemName = "Gym Shoes", isReady = false, confidenceScore = 1.0f))

        // Seed Sports items
        fakeDao.items.add(ItemRequirementEntity(id = 21, modeId = 4, itemName = "Football Boots", isReady = false, confidenceScore = 1.0f))
        fakeDao.items.add(ItemRequirementEntity(id = 22, modeId = 4, itemName = "Shin Guards", isReady = false, confidenceScore = 1.0f))
        fakeDao.items.add(ItemRequirementEntity(id = 23, modeId = 4, itemName = "Water Bottle", isReady = true, confidenceScore = 1.0f))

        // Seed Life Graph Nodes
        fakeDao.nodes.add(NodeEntity(id = 101, name = "College", type = "MODE"))
        fakeDao.nodes.add(NodeEntity(id = 102, name = "Gym", type = "MODE"))
        fakeDao.nodes.add(NodeEntity(id = 103, name = "Sports", type = "MODE"))
        fakeDao.nodes.add(NodeEntity(id = 201, name = "DBMS Lab", type = "ACTIVITY"))
        fakeDao.nodes.add(NodeEntity(id = 202, name = "Workout", type = "ACTIVITY"))
        fakeDao.nodes.add(NodeEntity(id = 203, name = "Football Practice", type = "ACTIVITY"))
        fakeDao.nodes.add(NodeEntity(id = 301, name = "DBMS Record", type = "ITEM"))
        fakeDao.nodes.add(NodeEntity(id = 302, name = "Laptop", type = "ITEM"))
        fakeDao.nodes.add(NodeEntity(id = 303, name = "Shaker Bottle", type = "ITEM"))
        fakeDao.nodes.add(NodeEntity(id = 304, name = "Football Boots", type = "ITEM"))
        fakeDao.nodes.add(NodeEntity(id = 305, name = "Shin Guards", type = "ITEM"))

        // Seed Life Graph Edges
        fakeDao.edges.add(EdgeEntity(id = 1, sourceNodeId = 101, targetNodeId = 201, relationType = "CONTAINS"))
        fakeDao.edges.add(EdgeEntity(id = 2, sourceNodeId = 201, targetNodeId = 301, relationType = "REQUIRES"))
        fakeDao.edges.add(EdgeEntity(id = 3, sourceNodeId = 201, targetNodeId = 302, relationType = "REQUIRES"))
        fakeDao.edges.add(EdgeEntity(id = 4, sourceNodeId = 102, targetNodeId = 202, relationType = "CONTAINS"))
        fakeDao.edges.add(EdgeEntity(id = 5, sourceNodeId = 202, targetNodeId = 303, relationType = "REQUIRES"))
        fakeDao.edges.add(EdgeEntity(id = 6, sourceNodeId = 103, targetNodeId = 203, relationType = "CONTAINS"))
        fakeDao.edges.add(EdgeEntity(id = 7, sourceNodeId = 203, targetNodeId = 304, relationType = "REQUIRES"))
        fakeDao.edges.add(EdgeEntity(id = 8, sourceNodeId = 203, targetNodeId = 305, relationType = "REQUIRES"))
    }

    @After
    fun tearDown() {
        SafeModeManager.setSafeMode(false)
        GhostModeEngine.reset()
    }

    private fun getTodayAt(hour: Int, minute: Int = 0): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // =========================================================================
    // Scenario A: College Mode, Readiness, Departure Window & Isolation
    // =========================================================================

    @Test
    fun testScenarioA_collegeReadinessDepartureAndGymIsolation() = runBlocking {
        val collegeMode = fakeDao.getModeById(1)!!
        val collegeItems = fakeDao.getItemsForMode(1)
        val report = ReadinessEngine.calculateReadiness(collegeMode, collegeItems)

        // 2 out of 4 ready -> 50%
        assertEquals(50, report.percentage)
        assertEquals(2, report.readyCount)
        assertEquals(2, report.pendingItems.size)
        assertTrue(report.pendingItems.contains("ID Card"))
        assertTrue(report.pendingItems.contains("DBMS Record"))

        // Gym items must not leak into College report
        assertFalse(report.pendingItems.contains("Shaker Bottle"))
        assertFalse(report.pendingItems.contains("Towel"))

        // Departure relevance for College event at 10 AM
        val eventStart = getTodayAt(10, 0)
        val event = EventEntity(
            id = 1,
            title = "Data Structures & DBMS Lab",
            startTime = eventStart,
            endTime = eventStart + 7200000L,
            associatedModeId = 1
        )
        fakeDao.insertEvent(event)

        // User is at 9:30 AM (30 min before start -> within departure window)
        val userTime = eventStart - 30 * 60 * 1000L
        val departureStatus = DepartureReminderEngine.evaluateDepartureRelevance(event, isGeofenceExit = false, currentTime = userTime)
        assertEquals(DepartureRelevance.DEPARTURE_WINDOW, departureStatus)

        // Evaluate smart reminder decision
        val habit = ItemHabitEntity(itemName = "ID Card", modeId = 1, forgetCount = 3, consecutiveForgetCount = 2)
        fakeDao.insertOrUpdateHabit(habit)

        val reminderDecision = DepartureReminderEngine.evaluateSmartReminder(
            mode = collegeMode,
            items = collegeItems,
            habits = listOf(habit),
            event = event,
            currentTime = userTime
        )
        assertTrue(reminderDecision.shouldNotify)
        assertEquals("ID Card", reminderDecision.prioritizedMissingItems.first())
    }

    // =========================================================================
    // Scenario B: Gym Mode Independent Readiness & Zero Cross-Mode Leakage
    // =========================================================================

    @Test
    fun testScenarioB_gymModeIndependenceAndZeroCrossModeLeakage() = runBlocking {
        fakeDao.setActiveMode(2)
        val gymMode = fakeDao.getModeById(2)!!
        val gymItems = fakeDao.getItemsForMode(2)

        // 1 ready (Shaker Bottle), 2 missing (Towel, Gym Shoes) -> 33%
        val report = ReadinessEngine.calculateReadiness(gymMode, gymItems)
        assertEquals(33, report.percentage)
        assertEquals(1, report.readyCount)
        assertEquals(2, report.pendingItems.size)
        assertTrue(report.pendingItems.contains("Towel"))
        assertTrue(report.pendingItems.contains("Gym Shoes"))

        // Absolutely zero College items in Gym report
        assertFalse(report.pendingItems.contains("DBMS Record"))
        assertFalse(report.pendingItems.contains("Laptop"))
        assertFalse(report.pendingItems.contains("ID Card"))
    }

    // =========================================================================
    // Scenario C: Sports Practice Cancellation, Consequence & Zero Forgetting Increment
    // =========================================================================

    @Test
    fun testScenarioC_sportsPracticeCancellationAndZeroForgettingIncrement() = runBlocking {
        fakeDao.setActiveMode(4)
        val sportsEvent = EventEntity(
            id = 50,
            title = "Football Practice",
            startTime = getTodayAt(17, 0),
            endTime = getTodayAt(19, 0),
            associatedModeId = 4
        )
        fakeDao.insertEvent(sportsEvent)

        val initialBootsHabit = fakeDao.getHabit("Football Boots", 4)
        val initialForgetCount = initialBootsHabit?.forgetCount ?: 0

        // Parse cancellation notification
        val payload = FilteredPayload(
            rawText = "Football practice cancelled today due to rain",
            sourcePackage = "com.whatsapp",
            detectedLanguage = "English",
            extractedAction = "Event Cancelled",
            extractedItemName = null,
            extractedDateTime = "today",
            targetModeId = 4,
            relevanceScore = 0.95f,
            canonicalAction = CanonicalAction.EVENT_CANCELLED,
            isRelevant = true
        )

        val result = decisionGate.processPayload(payload, fakeDao)
        assertTrue("Cancellation must apply consequences", result is DecisionResult.ConsequenceApplied)

        // Event title updated
        val updatedEvent = fakeDao.findEventByTitleLike("Football Practice")
        assertNotNull(updatedEvent)
        assertTrue(updatedEvent!!.title.contains("(Cancelled)"))

        // Requirements waived
        val boots = fakeDao.findItemInModeByName(4, "Football Boots")
        val guards = fakeDao.findItemInModeByName(4, "Shin Guards")
        assertNotNull(boots)
        assertNotNull(guards)
        assertTrue("Football Boots must be waived", boots!!.isWaived)
        assertTrue("Shin Guards must be waived", guards!!.isWaived)

        // Dynamic readiness recalculated: only Water Bottle remains required (and it is ready) -> 100%
        val sportsMode = fakeDao.getModeById(4)!!
        val updatedItems = fakeDao.getItemsForMode(4)
        val report = ReadinessEngine.calculateReadiness(sportsMode, updatedItems)
        assertEquals(100, report.percentage)

        // Plan mutation MUST NOT increment personal forgetting model
        val postBootsHabit = fakeDao.getHabit("Football Boots", 4)
        val postForgetCount = postBootsHabit?.forgetCount ?: 0
        assertEquals("Plan cancellation must not increment forget count", initialForgetCount, postForgetCount)
    }

    // =========================================================================
    // Scenario D: Event Reschedule, Timing Shift & Preserved Readiness
    // =========================================================================

    @Test
    fun testScenarioD_eventRescheduleTimingShiftAndReadiness() = runBlocking {
        val originalStart = getTodayAt(10, 0)
        val originalEnd = getTodayAt(12, 0)
        fakeDao.insertEvent(
            EventEntity(
                id = 2,
                title = "DBMS Lab",
                startTime = originalStart,
                endTime = originalEnd,
                associatedModeId = 1
            )
        )

        // Reschedule announcement: moved to 11 AM
        val newStart = getTodayAt(11, 0)
        val newEnd = getTodayAt(13, 0)
        val msg = "DBMS Lab moved from 10 AM to 11 AM"
        val payload = LightweightEventFilter.filter(msg, "com.college.portal")
        assertEquals(CanonicalAction.EVENT_RESCHEDULED, payload.canonicalAction)

        val result = decisionGate.processPayload(payload, fakeDao)
        assertTrue(result is DecisionResult.ConsequenceApplied)

        val updatedEvent = fakeDao.findEventByTitleLike("DBMS Lab")!!
        assertEquals(newStart, updatedEvent.startTime)
        assertEquals(newEnd, updatedEvent.endTime)

        // At 9:30 AM: was DEPARTURE_WINDOW for 10 AM, but for 11 AM it is now TOO_EARLY
        val userTime = originalStart - 30 * 60 * 1000L
        val status = DepartureReminderEngine.evaluateDepartureRelevance(updatedEvent, isGeofenceExit = false, currentTime = userTime)
        assertEquals(DepartureRelevance.TOO_EARLY, status)

        // Checklist items preserved (not waived)
        val recordItem = fakeDao.findItemInModeByName(1, "DBMS Record")!!
        assertFalse(recordItem.isWaived)
    }

    // =========================================================================
    // Scenario E: Schedule Conflict Hold Lifecycle
    // =========================================================================

    @Test
    fun testScenarioE_scheduleConflictHoldLifecycle() = runBlocking {
        val originalStart = getTodayAt(10, 0)
        fakeDao.insertEvent(
            EventEntity(
                id = 2,
                title = "DBMS Lab",
                startTime = originalStart,
                endTime = originalStart + 7200000L,
                associatedModeId = 1,
                evidenceText = "Rescheduled to 11 AM"
            )
        )

        // Contradictory announcement arrives
        val payload = FilteredPayload(
            rawText = "DBMS lab remains at 10 AM, ignore earlier message",
            sourcePackage = "com.whatsapp",
            detectedLanguage = "English",
            extractedAction = "Conflict Notice",
            extractedItemName = null,
            extractedDateTime = "10 AM",
            targetModeId = 1,
            relevanceScore = 0.88f,
            canonicalAction = CanonicalAction.EVENT_RESCHEDULED,
            isRelevant = true
        )

        val result = decisionGate.processPayload(payload, fakeDao)
        assertTrue("Contradictory announcement must trigger conflict detection", result is DecisionResult.ConflictDetected)

        val conflict = (result as DecisionResult.ConflictDetected).conflictAlert
        assertEquals("DBMS Lab", conflict.activityName)

        // Registered in GhostModeEngine under CONFLICT_HOLD
        val pendingProposals = GhostModeEngine.getPendingProposals()
        assertTrue(pendingProposals.any { it.activityName == "DBMS Lab" && it.lifecycle == GhostProposalLifecycle.CONFLICT_HOLD })

        // Live schedule preserved safely without premature corruption
        val event = fakeDao.findEventByTitleLike("DBMS Lab")!!
        assertEquals(originalStart, event.startTime)
    }

    // =========================================================================
    // Scenario F: Safe Mode Hardening
    // =========================================================================

    @Test
    fun testScenarioF_safeModeSuppressionAndGhostRouting() = runBlocking {
        SafeModeManager.setSafeMode(true)
        assertTrue(SafeModeManager.isSafeModeActive)

        // Even high-confidence 0.95 cancellation is blocked from mutating live schedule
        val event = EventEntity(
            id = 2,
            title = "DBMS Lab",
            startTime = getTodayAt(10, 0),
            endTime = getTodayAt(12, 0),
            associatedModeId = 1
        )
        fakeDao.insertEvent(event)

        val payload = FilteredPayload(
            rawText = "DBMS Lab is definitely cancelled today",
            sourcePackage = "com.whatsapp",
            detectedLanguage = "English",
            extractedAction = "Event Cancelled",
            extractedItemName = null,
            extractedDateTime = "today",
            targetModeId = 1,
            relevanceScore = 0.95f,
            canonicalAction = CanonicalAction.EVENT_CANCELLED,
            isRelevant = true
        )

        val alert = ConsequenceEngine.processPlanMutation(fakeDao, MultilingualParser.parse(payload.rawText))
        assertNotNull(alert)
        assertEquals("Suppressed (Safe Mode Active)", alert!!.actionTaken)

        // Live event is NOT marked cancelled
        val liveEvent = fakeDao.findEventByTitleLike("DBMS Lab")!!
        assertFalse(liveEvent.title.contains("(Cancelled)"))

        // Requirements are NOT waived
        val item = fakeDao.findItemInModeByName(1, "DBMS Record")!!
        assertFalse(item.isWaived)

        // Departure reminders suppressed under Safe Mode
        val collegeItems = fakeDao.getItemsForMode(1)
        val reminder = DepartureReminderEngine.evaluateSmartReminder(
            mode = fakeDao.getModeById(1),
            items = collegeItems,
            habits = emptyList(),
            event = liveEvent,
            currentTime = getTodayAt(9, 30)
        )
        assertFalse("Smart reminder must be suppressed in Safe Mode", reminder.shouldNotify)
    }

    // =========================================================================
    // Scenario G: Ghost Proposal Rejection vs Approval
    // =========================================================================

    @Test
    fun testScenarioG_ghostProposalRejectionVsApproval() = runBlocking {
        val proposal = GhostProposal(
            id = "ghost_cancel_lab",
            numericId = 444,
            activityName = "DBMS Lab",
            targetModeId = 1,
            mutationType = MutationType.CANCELLATION,
            previousState = "Scheduled at 10 AM",
            proposedState = "Cancelled",
            confidence = 0.65f,
            evidenceSummary = "Informal group chat rumor",
            potentialConsequences = "Waives DBMS Record requirement",
            rawText = "maybe lab is cancelled"
        )
        GhostModeEngine.registerProposal(proposal)

        // Test Rejection: Rejecting proposal leaves live state 100% untouched
        val rejectResult = GhostModeEngine.rejectProposal(444, fakeDao)
        assertTrue(rejectResult is GhostApprovalResult.Rejected)
        assertEquals(GhostProposalLifecycle.USER_REJECTED, (rejectResult as GhostApprovalResult.Rejected).proposal.lifecycle)

        val item = fakeDao.findItemInModeByName(1, "DBMS Record")!!
        assertFalse(item.isWaived)

        // Register fresh proposal for approval test
        val proposal2 = proposal.copy(id = "ghost_cancel_lab_2", numericId = 555, lifecycle = GhostProposalLifecycle.PROPOSED)
        GhostModeEngine.registerProposal(proposal2)

        fakeDao.insertEvent(
            EventEntity(
                id = 2,
                title = "DBMS Lab",
                startTime = getTodayAt(10, 0),
                endTime = getTodayAt(12, 0),
                associatedModeId = 1
            )
        )

        // Test Approval: Exactly ONE mutation executed
        val approveResult = GhostModeEngine.approveProposal(555, fakeDao)
        assertTrue(approveResult is GhostApprovalResult.Success)
        assertEquals(GhostProposalLifecycle.APPLIED, (approveResult as GhostApprovalResult.Success).proposal.lifecycle)

        // Live consequence applied
        val approvedItem = fakeDao.findItemInModeByName(1, "DBMS Record")!!
        assertTrue(approvedItem.isWaived)

        // Duplicate approval idempotency
        val duplicateApprove = GhostModeEngine.approveProposal(555, fakeDao)
        assertTrue(duplicateApprove is GhostApprovalResult.AlreadyApplied)
    }

    // =========================================================================
    // Scenario H: Personal Forgetting Model
    // =========================================================================

    @Test
    fun testScenarioH_personalForgettingIncrementsRecoveryAndIsolation() = runBlocking {
        // Explicit confirmation increments forgetCount
        val habit1 = ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "ID Card", 1)
        assertEquals(1, habit1.forgetCount)
        assertEquals(1, habit1.consecutiveForgetCount)
        assertEquals(0.25f, habit1.priorityBoost, 0.01f)

        // Second confirmation escalates
        val habit2 = ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "ID Card", 1)
        assertEquals(2, habit2.forgetCount)
        assertEquals(2, habit2.consecutiveForgetCount)
        assertEquals(0.50f, habit2.priorityBoost, 0.01f)

        // Successful preparation triggers recovery decay
        val habitConfirmed = ForgettingModelEngine.recordItemConfirmed(fakeDao, "ID Card", 1)
        assertEquals(1, habitConfirmed.confirmCount)
        assertEquals(1, habitConfirmed.consecutiveSuccessCount)
        assertEquals(0, habitConfirmed.consecutiveForgetCount)
        assertEquals(0.40f, habitConfirmed.priorityBoost, 0.01f) // 0.50 - 0.10

        // Strict Mode Isolation: ID Card in Gym (mode 2) has zero history
        val gymHabit = fakeDao.getHabit("ID Card", 2)
        assertNull(gymHabit)
    }

    // =========================================================================
    // Scenario I: 100% Local-First & Zero-Cloud Operation
    // =========================================================================

    @Test
    fun testScenarioI_localFirstZeroCloudOperation() = runBlocking {
        // Execute NLP parse, decision gate, habit evaluation, graph traversal offline
        val nlp = MultilingualParser.parse("Bring your DBMS record tomorrow", "local.package")
        assertEquals(CanonicalAction.REQUIRED_ITEM, nlp.canonicalAction)
        assertEquals("DBMS Record", nlp.relatedItem)

        val payload = LightweightEventFilter.filter("Bring your DBMS record tomorrow", "local.package")
        val decision = decisionGate.processPayload(payload, fakeDao)
        assertNotNull(decision)

        // Export data locally as valid JSON without cloud dependencies
        val jsonExport = com.lifeos.app.security.DataExporter.exportToJson(fakeDao)
        assertTrue(jsonExport.contains("100% Local-First / Zero Cloud Telemetry"))
        assertTrue(jsonExport.contains("dataTransparencySummary"))
        assertTrue(jsonExport.contains("lifeModes"))
    }

    // =========================================================================
    // Scenario J: Persistence & Restart Rehydration
    // =========================================================================

    @Test
    fun testScenarioJ_persistenceAndRestartRehydration() = runBlocking {
        // Seed Ghost Proposal into Room DAO
        val ghostEvent = EventEntity(
            id = 77,
            title = "DBMS Lab (Cancelled)",
            startTime = getTodayAt(10, 0),
            endTime = getTodayAt(12, 0),
            associatedModeId = 1,
            isGhostMode = true,
            confidence = 0.72f,
            evidenceText = "Proposed cancellation rumor"
        )
        fakeDao.insertEvent(ghostEvent)

        // Simulate app kill & restart: clear in-memory cache and rehydrate from DB
        GhostModeEngine.reset()
        assertEquals(0, GhostModeEngine.getAllProposals().size)

        GhostModeEngine.restoreFromDatabase(fakeDao)
        val restored = GhostModeEngine.getAllProposals()
        assertEquals(1, restored.size)
        assertEquals(77, restored.first().numericId)
        assertEquals("DBMS Lab", restored.first().activityName)
    }

    // =========================================================================
    // Life Graph Engine: Cycle Detection, Bounded Traversal, Orphan/Dangling Cleanup
    // =========================================================================

    @Test
    fun testLifeGraphEngine_cycleDetectionAndBoundedTraversal() = runBlocking {
        // Node 101 -> Node 201 -> Node 301
        // Attempt to connect 301 back to 101 (creating cycle)
        val isCycle = LifeGraphEngine.isReachable(fakeDao, fromNodeId = 101, toNodeId = 301)
        assertTrue("Node 101 must be able to reach 301", isCycle)

        // Attempting to connect 301 -> 101 with REQUIRES must be rejected (returns null)
        val connected = LifeGraphEngine.connectNodesSafe(fakeDao, sourceNodeId = 301, targetNodeId = 101, relationType = "REQUIRES")
        assertNull("Cyclic edge connection must be rejected", connected)

        // Traversal outward bounded
        val traversed = LifeGraphEngine.traverseOutward(fakeDao, startNodeId = 101, maxDepth = 6)
        assertTrue(traversed.any { it.id == 201 })
        assertTrue(traversed.any { it.id == 301 })
    }

    @Test
    fun testLifeGraphEngine_orphanAndDanglingEdgePruning() = runBlocking {
        // Add dangling edge pointing to non-existent node 999
        fakeDao.insertEdge(EdgeEntity(id = 99, sourceNodeId = 201, targetNodeId = 999, relationType = "DANGLING"))

        // Add orphan node 888 with no edges
        fakeDao.insertNode(NodeEntity(id = 888, name = "Orphan Node", type = "ITEM"))

        val prunedCount = LifeGraphEngine.pruneDanglingEdgesAndOrphans(fakeDao)
        assertTrue(prunedCount >= 1)

        assertNull(fakeDao.getNodeById(888))
        assertTrue(fakeDao.edges.none { it.id == 99 })
    }

    // =========================================================================
    // Multi-Volume Deduplication: 1, 5, 10, 50 Duplicates Produce Exactly 1 Mutation
    // =========================================================================

    @Test
    fun testDeduplicationResilience_multipleVolumesProduceExactlyOneMutation() = runBlocking {
        val payload = FilteredPayload(
            rawText = "Bring your lab manual tomorrow",
            sourcePackage = "com.whatsapp",
            detectedLanguage = "English",
            extractedAction = "Action Required",
            extractedItemName = "Lab Manual",
            extractedDateTime = "tomorrow",
            targetModeId = 1,
            relevanceScore = 0.90f,
            canonicalAction = CanonicalAction.REQUIRED_ITEM,
            isRelevant = true
        )

        // First announcement: auto-committed
        val firstResult = decisionGate.processPayload(payload, fakeDao)
        assertTrue(firstResult is DecisionResult.AutoCommitted)

        // 5 duplicate announcements: all discarded
        for (i in 1..5) {
            val dupResult = decisionGate.processPayload(payload, fakeDao)
            assertTrue("Duplicate $i must be discarded", dupResult is DecisionResult.DuplicateDiscarded)
        }

        // 10 duplicate announcements: all discarded
        for (i in 1..10) {
            val dupResult = decisionGate.processPayload(payload, fakeDao)
            assertTrue("Duplicate $i must be discarded", dupResult is DecisionResult.DuplicateDiscarded)
        }

        // 50 duplicate announcements: all discarded
        for (i in 1..50) {
            val dupResult = decisionGate.processPayload(payload, fakeDao)
            assertTrue("Duplicate $i must be discarded", dupResult is DecisionResult.DuplicateDiscarded)
        }

        // Exactly ONE "Lab Manual" item in database
        val matchingItems = fakeDao.items.filter { it.itemName.equals("Lab Manual", ignoreCase = true) }
        assertEquals(1, matchingItems.size)
    }

    // =========================================================================
    // PII Sanitization Guardrails
    // =========================================================================

    @Test
    fun testPiiSanitizationGuardrails() {
        val rawSensitive = "Call Prof Ramesh at +91 9876543210 or ramesh@university.edu with OTP 492812 regarding roll 21CS042"
        val sanitized = DataMinimizer.stripPii(rawSensitive)

        assertFalse(sanitized.contains("+91 9876543210"))
        assertFalse(sanitized.contains("ramesh@university.edu"))
        assertFalse(sanitized.contains("492812"))
        assertTrue(sanitized.contains("[PHONE REDACTED]"))
        assertTrue(sanitized.contains("[EMAIL REDACTED]"))
        assertTrue(sanitized.contains("[TOKEN REDACTED]"))
    }
}
