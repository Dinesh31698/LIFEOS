package com.lifeos.app

import com.lifeos.app.data.*
import com.lifeos.app.engine.*
import com.lifeos.app.security.DataMinimizer
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * LIFEOS TEST PHASE 9A: PLAN MUTATION & CONSEQUENCE ENGINE HARDENING
 * Comprehensive unit test suite verifying:
 *  1. Same-Day Time Reschedule (10 AM -> 11 AM)
 *  2. Extended Time Reschedule (10 AM -> 2 PM)
 *  3. Multi-Day Postponement (postponed to Wednesday)
 *  4. Venue Change (Block A -> Block C)
 *  5. New Item Requirement Ingestion (Added to checklist)
 *  6. Conflicting Announcements (10 AM vs 11 AM -> ConflictDetected & HOLD)
 *  7. High-Volume Idempotency (50x duplicate announcements -> 1 applied, 49 discarded)
 *  8. Outdated Announcement Protection (Stale announcement superseded by newer schedule)
 *  9. Teluglish Plan Mutation ("DBMS lab repatiki marindi")
 * 10. Telugu Plan Mutation ("DBMS ల్యాబ్ రద్దయింది")
 * 11. Hindi/Hinglish Plan Mutation ("DBMS lab kal 11 baje shift ho gayi")
 * 12. Ghost Mode Routing for Ambiguous Rumors (0.50 - 0.84 confidence)
 * 13. Safe Mode Isolation (auto-commit suppressed, routed to Ghost Mode)
 * 14. User Approval of Ghost Proposal (USER_APPROVED status & ripples applied)
 * 15. Dynamic Readiness Recalculation (waived item excluded from missing requirements)
 * 16. Un-waiving on Reschedule (waived flag cleared when event is rescheduled)
 * 17. Departure Reminder Engine Timing Adjustment (relevance shifts from DEPARTURE_WINDOW to TOO_EARLY)
 * 18. Personal Forgetting Model Isolation (Plan mutations NEVER increment forget counts)
 * 19. Structured PlanMutationDiff Validation (all fields present and validated)
 * 20. Database & Schema Integrity (Database version 7 preserved, schema intact)
 */
class Phase9PlanMutationUnitTest {

    private lateinit var fakeDao: FakeLifeOsDao
    private lateinit var decisionGate: DecisionGate

    @Before
    fun setUp() {
        SafeModeManager.setSafeMode(false)
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, false)

        fakeDao = FakeLifeOsDao()

        // Seed Life Modes
        fakeDao.modes.add(LifeModeEntity(id = 1, name = "College", iconRes = "ic_college", isActive = true, priority = 1))
        fakeDao.modes.add(LifeModeEntity(id = 2, name = "Gym", iconRes = "ic_gym", isActive = false, priority = 2))
        fakeDao.modes.add(LifeModeEntity(id = 3, name = "Travel", iconRes = "ic_travel", isActive = false, priority = 3))
        fakeDao.modes.add(LifeModeEntity(id = 4, name = "Sports", iconRes = "ic_sports", isActive = false, priority = 4))

        // Seed College items
        fakeDao.items.add(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true, isWaived = false))
        fakeDao.items.add(ItemRequirementEntity(id = 2, modeId = 1, itemName = "DBMS Record", isReady = false, isWaived = false))
        fakeDao.items.add(ItemRequirementEntity(id = 3, modeId = 1, itemName = "Laptop & Charger", isReady = false, isWaived = false))
        fakeDao.items.add(ItemRequirementEntity(id = 4, modeId = 1, itemName = "Water Bottle", isReady = false, isWaived = false))

        // Seed Sports items
        fakeDao.items.add(ItemRequirementEntity(id = 10, modeId = 4, itemName = "Football Boots", isReady = false, isWaived = false))
        fakeDao.items.add(ItemRequirementEntity(id = 11, modeId = 4, itemName = "Shin Guards", isReady = false, isWaived = false))

        // Seed Life Graph Nodes & Edges: DBMS Lab requires DBMS Record
        fakeDao.nodes.add(NodeEntity(id = 101, name = "DBMS Lab", type = "ACTIVITY"))
        fakeDao.nodes.add(NodeEntity(id = 102, name = "DBMS Record", type = "ITEM"))
        fakeDao.edges.add(EdgeEntity(id = 201, sourceNodeId = 101, targetNodeId = 102, relationType = "REQUIRES"))

        // Seed Personal Life Graph for Sports
        fakeDao.nodes.add(NodeEntity(id = 103, name = "Football Practice", type = "ACTIVITY"))
        fakeDao.nodes.add(NodeEntity(id = 104, name = "Football Boots", type = "ITEM"))
        fakeDao.edges.add(EdgeEntity(id = 202, sourceNodeId = 103, targetNodeId = 104, relationType = "REQUIRES"))

        // Seed default habits
        fakeDao.habits.add(ItemHabitEntity(id = 1, itemName = "DBMS Record", modeId = 1, forgetCount = 0, confirmCount = 3))
        fakeDao.habits.add(ItemHabitEntity(id = 2, itemName = "Football Boots", modeId = 4, forgetCount = 0, confirmCount = 4))

        decisionGate = DecisionGate()
    }

    @After
    fun tearDown() {
        SafeModeManager.setSafeMode(false)
        ConsequenceEngine.dismissAlert()
    }

    // =========================================================================
    // 1. Same-Day Time Reschedule: 10 AM -> 11 AM
    // =========================================================================
    @Test
    fun testScenario1_SameDayTimeReschedule() = runBlocking {
        val now = System.currentTimeMillis()
        fakeDao.events.add(EventEntity(id = 1, title = "DBMS Lab", startTime = now, endTime = now + 7200000L, associatedModeId = 1))

        val msg = "DBMS Lab moved from 10 AM to 11 AM"
        val payload = LightweightEventFilter.filter(msg, "com.college.portal")
        assertEquals(CanonicalAction.EVENT_RESCHEDULED, payload.canonicalAction)

        val result = decisionGate.processPayload(payload, fakeDao)
        assertTrue("Must apply consequence automatically", result is DecisionResult.ConsequenceApplied)

        val alert = (result as DecisionResult.ConsequenceApplied).alert
        assertNotNull("Must generate structured diff", alert.diff)
        val diff = alert.diff!!
        assertEquals(MutationType.TIME_RESCHEDULE, diff.mutationType)
        assertEquals("DBMS Lab", diff.activityName)
        assertEquals(1, diff.affectedModeId)
        assertEquals(MutationStatus.AUTOMATIC, diff.executionStatus)
        assertTrue(diff.changedFields.contains("startTime"))

        val updatedEvent = fakeDao.findEventByTitleLike("DBMS Lab")
        assertNotNull(updatedEvent)
        assertTrue(updatedEvent!!.evidenceText?.contains("Rescheduled") == true)
    }

    // =========================================================================
    // 2. Extended Time Reschedule: 10 AM -> 2 PM
    // =========================================================================
    @Test
    fun testScenario2_ExtendedTimeReschedule() = runBlocking {
        fakeDao.events.add(EventEntity(id = 2, title = "DBMS Lab", startTime = System.currentTimeMillis(), endTime = System.currentTimeMillis() + 7200000L, associatedModeId = 1))

        val msg = "Tomorrow's DBMS lab is moved from 10 AM to 2 PM"
        val payload = LightweightEventFilter.filter(msg, "com.whatsapp")
        assertEquals(CanonicalAction.EVENT_RESCHEDULED, payload.canonicalAction)

        val result = decisionGate.processPayload(payload, fakeDao)
        assertTrue(result is DecisionResult.ConsequenceApplied)

        val alert = (result as DecisionResult.ConsequenceApplied).alert
        val diff = alert.diff!!
        assertEquals(MutationType.TIME_RESCHEDULE, diff.mutationType)
        assertTrue(diff.afterState.contains("2 PM") || diff.afterState.contains("14:"))

        // Verify requirements remain active
        val dbmsRecord = fakeDao.items.find { it.itemName == "DBMS Record" }
        assertFalse("Requirements must NOT be waived on reschedule", dbmsRecord?.isWaived == true)
    }

    // =========================================================================
    // 3. Multi-Day Postponement: Postponed to Wednesday
    // =========================================================================
    @Test
    fun testScenario3_MultiDayPostponement() = runBlocking {
        fakeDao.events.add(EventEntity(id = 3, title = "DBMS Lab", startTime = System.currentTimeMillis(), associatedModeId = 1))

        val msg = "DBMS Lab postponed to Wednesday"
        val payload = LightweightEventFilter.filter(msg, "com.whatsapp")
        assertEquals(CanonicalAction.EVENT_RESCHEDULED, payload.canonicalAction)

        val result = decisionGate.processPayload(payload, fakeDao)
        assertTrue(result is DecisionResult.ConsequenceApplied)

        val diff = (result as DecisionResult.ConsequenceApplied).alert.diff!!
        assertEquals(MutationType.POSTPONEMENT, diff.mutationType)
        assertEquals(MutationStatus.AUTOMATIC, diff.executionStatus)

        // Verify target epoch is on a Wednesday
        val event = fakeDao.findEventByTitleLike("DBMS Lab")
        assertNotNull(event)
        val cal = Calendar.getInstance().apply { timeInMillis = event!!.startTime }
        assertEquals("Start time must be Wednesday", Calendar.WEDNESDAY, cal.get(Calendar.DAY_OF_WEEK))
    }

    // =========================================================================
    // 4. Venue Change: Block A -> Block C
    // =========================================================================
    @Test
    fun testScenario4_VenueChange() = runBlocking {
        fakeDao.events.add(EventEntity(id = 4, title = "DBMS Lab", location = "Block A", associatedModeId = 1))

        val msg = "DBMS Lab shifted from Block A to Block C"
        val payload = LightweightEventFilter.filter(msg, "com.college.portal")
        assertEquals(CanonicalAction.EVENT_VENUE_CHANGED, payload.canonicalAction)
        assertEquals("Block C", payload.location)

        val result = decisionGate.processPayload(payload, fakeDao)
        assertTrue(result is DecisionResult.ConsequenceApplied)

        val diff = (result as DecisionResult.ConsequenceApplied).alert.diff!!
        assertEquals(MutationType.VENUE_CHANGE, diff.mutationType)
        assertEquals("Venue: Block A", diff.beforeState)
        assertEquals("Venue: Block C", diff.afterState)
        assertTrue(diff.changedFields.contains("location"))

        val updatedEvent = fakeDao.findEventByTitleLike("DBMS Lab")
        assertEquals("Block C", updatedEvent?.location)
    }

    // =========================================================================
    // 5. New Item Requirement Ingestion
    // =========================================================================
    @Test
    fun testScenario5_NewItemRequirementIngestion() = runBlocking {
        val initialItemCount = fakeDao.items.size
        val msg = "Please bring assignment file definitely tomorrow for college"
        val payload = LightweightEventFilter.filter(msg, "com.college.portal")

        assertEquals(CanonicalAction.REQUIRED_ITEM, payload.canonicalAction)
        assertEquals("Assignment File", payload.extractedItemName)

        val result = decisionGate.processPayload(payload, fakeDao)
        assertTrue(result is DecisionResult.AutoCommitted)

        val diff = (result as DecisionResult.AutoCommitted).diff
        assertNotNull(diff)
        assertEquals(MutationType.REQUIREMENT_ADDED, diff!!.mutationType)
        assertEquals(MutationStatus.AUTOMATIC, diff.executionStatus)

        assertEquals(initialItemCount + 1, fakeDao.items.size)
        val newItem = fakeDao.items.find { it.itemName == "Assignment File" }
        assertNotNull(newItem)
        assertFalse(newItem!!.isGhostMode)
    }

    // =========================================================================
    // 6. Conflicting Announcements: 10 AM vs 11 AM
    // =========================================================================
    @Test
    fun testScenario6_ConflictingAnnouncements_HoldState() = runBlocking {
        // Step 1: Reschedule notice arrives
        val msgA = "DBMS Lab moved to 11 AM"
        val payloadA = LightweightEventFilter.filter(msgA, "com.whatsapp")
        val resultA = decisionGate.processPayload(payloadA, fakeDao)
        assertTrue(resultA is DecisionResult.ConsequenceApplied)

        // Step 2: Contradictory announcement arrives claiming it remains at 10 AM
        val msgB = "DBMS Lab remains at 10 AM"
        val payloadB = LightweightEventFilter.filter(msgB, "com.telegram.messenger")
        val resultB = decisionGate.processPayload(payloadB, fakeDao)

        assertTrue("Contradictory announcement must trigger conflict hold", resultB is DecisionResult.ConflictDetected)
        val conflictResult = resultB as DecisionResult.ConflictDetected
        assertEquals("DBMS Lab", conflictResult.conflictAlert.activityName)
        assertEquals(MutationStatus.CONFLICT_HOLD, conflictResult.diff?.executionStatus)
        assertTrue(fakeDao.evidence.any { it.parsedIntent?.contains("CONFLICT") == true })
    }

    // =========================================================================
    // 7. High-Volume Idempotency: 50x Identical Announcements
    // =========================================================================
    @Test
    fun testScenario7_HighVolumeIdempotency_50xAnnouncements() = runBlocking {
        fakeDao.events.add(EventEntity(id = 7, title = "DBMS Lab", startTime = 1000L, associatedModeId = 1))

        val msg = "Tomorrow DBMS Lab is moved to 11 AM"
        val payload = LightweightEventFilter.filter(msg, "com.whatsapp")

        // Ingest the first time -> Should Apply
        val resultFirst = decisionGate.processPayload(payload, fakeDao)
        assertTrue("First receipt must apply consequence", resultFirst is DecisionResult.ConsequenceApplied)

        val eventCountAfterFirst = fakeDao.events.filter { it.title.contains("DBMS Lab") }.size
        assertEquals(1, eventCountAfterFirst)

        // Ingest 49 additional identical messages
        var discardedCount = 0
        for (i in 2..50) {
            val duplicateResult = decisionGate.processPayload(payload, fakeDao)
            if (duplicateResult is DecisionResult.DuplicateDiscarded) {
                discardedCount++
            }
        }

        assertEquals("All 49 subsequent duplicates must be discarded cleanly", 49, discardedCount)
        val finalEventCount = fakeDao.events.filter { it.title.contains("DBMS Lab") }.size
        assertEquals("Zero duplicate rows allowed in Room DB", 1, finalEventCount)
    }

    // =========================================================================
    // 8. Outdated Announcement Protection
    // =========================================================================
    @Test
    fun testScenario8_OutdatedAnnouncementProtection() = runBlocking {
        // Event is already updated and scheduled for 11 AM tomorrow
        val targetEpoch = MultilingualParser.parseDateTimeToEpoch("tomorrow", "11 AM")!!
        fakeDao.events.add(EventEntity(
            id = 8,
            title = "DBMS Lab",
            startTime = targetEpoch,
            endTime = targetEpoch + 7200000L,
            evidenceText = "Rescheduled: Moved to 11 AM",
            associatedModeId = 1
        ))

        // An older / stale notification arrives claiming 10 AM (which is earlier than the verified 11 AM)
        val staleMsg = "DBMS Lab at 10 AM tomorrow"
        val payload = LightweightEventFilter.filter(staleMsg, "com.whatsapp")

        val result = decisionGate.processPayload(payload, fakeDao)
        assertTrue("Outdated announcement superseded by newer schedule must be discarded", result is DecisionResult.DuplicateDiscarded)

        val event = fakeDao.findEventByTitleLike("DBMS Lab")
        assertEquals("Schedule must remain at 11 AM", targetEpoch, event?.startTime)
    }

    // =========================================================================
    // 9. Teluglish Plan Mutation: "DBMS lab repatiki marindi"
    // =========================================================================
    @Test
    fun testScenario9_TeluglishPlanMutation() = runBlocking {
        fakeDao.events.add(EventEntity(id = 9, title = "DBMS Lab", associatedModeId = 1))

        val msg = "DBMS lab repatiki marindi"
        val payload = LightweightEventFilter.filter(msg, "com.whatsapp")

        assertEquals("Teluglish", payload.detectedLanguage)
        assertEquals(CanonicalAction.EVENT_RESCHEDULED, payload.canonicalAction)

        val result = decisionGate.processPayload(payload, fakeDao)
        assertTrue(result is DecisionResult.ConsequenceApplied)

        val diff = (result as DecisionResult.ConsequenceApplied).alert.diff!!
        assertEquals(MutationType.POSTPONEMENT, diff.mutationType)
        assertEquals("Teluglish", payload.detectedLanguage)
    }

    // =========================================================================
    // 10. Telugu Script Plan Mutation: "DBMS ల్యాబ్ రద్దయింది"
    // =========================================================================
    @Test
    fun testScenario10_TeluguScriptPlanMutation() = runBlocking {
        fakeDao.events.add(EventEntity(id = 10, title = "DBMS Lab", associatedModeId = 1))

        val msg = "DBMS ల్యాబ్ రద్దయింది"
        val payload = LightweightEventFilter.filter(msg, "com.whatsapp")

        assertEquals("Telugu", payload.detectedLanguage)
        assertEquals(CanonicalAction.EVENT_CANCELLED, payload.canonicalAction)

        val result = decisionGate.processPayload(payload, fakeDao)
        assertTrue(result is DecisionResult.ConsequenceApplied)

        val diff = (result as DecisionResult.ConsequenceApplied).alert.diff!!
        assertEquals(MutationType.CANCELLATION, diff.mutationType)

        val record = fakeDao.items.find { it.itemName == "DBMS Record" }
        assertTrue("DBMS Record must be waived", record?.isWaived == true)
    }

    // =========================================================================
    // 11. Hindi/Hinglish Plan Mutation: "DBMS lab kal 11 baje shift ho gayi"
    // =========================================================================
    @Test
    fun testScenario11_HindiHinglishPlanMutation() = runBlocking {
        fakeDao.events.add(EventEntity(id = 11, title = "DBMS Lab", associatedModeId = 1))

        val msg = "DBMS lab kal 11 baje shift ho gayi"
        val payload = LightweightEventFilter.filter(msg, "com.whatsapp")

        assertEquals(CanonicalAction.EVENT_RESCHEDULED, payload.canonicalAction)
        val result = decisionGate.processPayload(payload, fakeDao)
        assertTrue(result is DecisionResult.ConsequenceApplied)

        val diff = (result as DecisionResult.ConsequenceApplied).alert.diff!!
        assertEquals(MutationType.POSTPONEMENT, diff.mutationType)
    }

    // =========================================================================
    // 12. Ghost Mode Routing for Ambiguous Rumors
    // =========================================================================
    @Test
    fun testScenario12_GhostModeRoutingForAmbiguousRumors() = runBlocking {
        val initialEvent = EventEntity(id = 12, title = "DBMS Lab", startTime = 5000L, associatedModeId = 1)
        fakeDao.events.add(initialEvent)

        val rumor = "Bro I think tomorrow's DBMS lab is cancelled, sir is not feeling well"
        val payload = LightweightEventFilter.filter(rumor, "com.whatsapp")

        assertTrue("Confidence must be in Ghost Mode range [0.50, 0.84]", payload.relevanceScore in 0.50f..0.84f)
        val result = decisionGate.processPayload(payload, fakeDao)

        assertTrue("Ambiguous rumor must be routed to Ghost Mode", result is DecisionResult.GhostModeRouted)
        val diff = (result as DecisionResult.GhostModeRouted).diff
        assertNotNull(diff)
        assertEquals(MutationStatus.PROPOSED, diff!!.executionStatus)

        // Room DB active plan remains unmutated
        val record = fakeDao.items.find { it.itemName == "DBMS Record" }
        assertFalse("Real checklist item must NOT be waived on rumor", record?.isWaived == true)
    }

    // =========================================================================
    // 13. Safe Mode Isolation: Auto-Commit Suppressed
    // =========================================================================
    @Test
    fun testScenario13_SafeModeIsolation_SuppressesAutoCommit() = runBlocking {
        SafeModeManager.setSafeMode(true)
        fakeDao.events.add(EventEntity(id = 13, title = "DBMS Lab", associatedModeId = 1))

        val highConfidenceCancel = "Official: DBMS Lab is cancelled today"
        val payload = LightweightEventFilter.filter(highConfidenceCancel, "com.college.portal")
        assertTrue(payload.relevanceScore >= 0.85f)

        val result = decisionGate.processPayload(payload, fakeDao)
        assertTrue("Safe Mode must force plan mutation to Ghost Mode", result is DecisionResult.GhostModeRouted)

        val diff = (result as DecisionResult.GhostModeRouted).diff
        assertNotNull(diff)
        assertEquals(MutationStatus.SUPPRESSED_SAFE_MODE, diff!!.executionStatus)

        val record = fakeDao.items.find { it.itemName == "DBMS Record" }
        assertFalse("Safe Mode must completely block auto-waiving", record?.isWaived == true)

        SafeModeManager.setSafeMode(false)
    }

    // =========================================================================
    // 14. User Approval of Ghost Proposal
    // =========================================================================
    @Test
    fun testScenario14_UserApprovalOfGhostProposal() = runBlocking {
        fakeDao.events.add(EventEntity(id = 14, title = "DBMS Lab", associatedModeId = 1))

        // Create ghost items
        fakeDao.items.add(ItemRequirementEntity(id = 99, modeId = 1, itemName = "Possible Cancellation: DBMS Lab", isGhostMode = true))
        fakeDao.events.add(EventEntity(id = 100, title = "DBMS Lab (Cancelled)", isGhostMode = true, associatedModeId = 1))

        val alert = ConsequenceEngine.applyConfirmedProposal(
            dao = fakeDao,
            activityName = "DBMS Lab",
            rawText = "Confirmed by user",
            confidence = 0.90f,
            targetModeId = 1
        )

        assertNotNull(alert.diff)
        assertEquals(MutationStatus.USER_APPROVED, alert.diff!!.executionStatus)
        assertEquals(MutationType.CANCELLATION, alert.diff!!.mutationType)

        val record = fakeDao.items.find { it.itemName == "DBMS Record" }
        assertTrue("Item must be waived upon user approval", record?.isWaived == true)
    }

    // =========================================================================
    // 15. Dynamic Readiness Recalculation
    // =========================================================================
    @Test
    fun testScenario15_DynamicReadinessRecalculation() = runBlocking {
        val mode = fakeDao.modes.find { it.id == 1 }!!
        // 4 items: ID Card (ready=true), DBMS Record (ready=false), Laptop (ready=false), Water Bottle (ready=false)
        val initialReadiness = ReadinessEngine.calculateReadiness(mode, fakeDao.items.filter { it.modeId == 1 })
        assertEquals(25, initialReadiness.percentage) // 1 of 4 ready

        // Cancel DBMS Lab -> waives DBMS Record
        ConsequenceEngine.processPlanMutation(fakeDao, ParsedIntentResult(
            canonicalAction = CanonicalAction.EVENT_CANCELLED,
            detectedLanguage = "English",
            rawText = "DBMS Lab Cancelled",
            subjectActivity = "DBMS Lab",
            relatedItem = "DBMS Record",
            timeOrDateInfo = "Today",
            confidence = 0.95f,
            targetModeId = 1
        ))

        // Recalculate: 3 remaining non-waived items (ID Card, Laptop, Water Bottle). 1 is ready.
        val updatedItems = fakeDao.getItemsForMode(1)
        val newReadiness = ReadinessEngine.calculateReadiness(mode, updatedItems)
        assertEquals(33, newReadiness.percentage) // 1 of 3 ready = 33%
    }

    // =========================================================================
    // 16. Un-Waiving on Reschedule
    // =========================================================================
    @Test
    fun testScenario16_UnWaivingOnReschedule() = runBlocking {
        fakeDao.events.add(EventEntity(id = 16, title = "DBMS Lab", associatedModeId = 1))

        // First cancel to waive the record
        ConsequenceEngine.processPlanMutation(fakeDao, ParsedIntentResult(
            canonicalAction = CanonicalAction.EVENT_CANCELLED,
            detectedLanguage = "English",
            rawText = "DBMS Lab Cancelled",
            subjectActivity = "DBMS Lab",
            relatedItem = "DBMS Record",
            timeOrDateInfo = "Today",
            confidence = 0.95f,
            targetModeId = 1
        ))
        assertTrue(fakeDao.items.find { it.itemName == "DBMS Record" }!!.isWaived)

        // Then reschedule notice arrives
        ConsequenceEngine.processPlanMutation(fakeDao, ParsedIntentResult(
            canonicalAction = CanonicalAction.EVENT_RESCHEDULED,
            detectedLanguage = "English",
            rawText = "DBMS Lab moved to Wednesday",
            subjectActivity = "DBMS Lab",
            relatedItem = "DBMS Record",
            timeOrDateInfo = "Wednesday",
            confidence = 0.95f,
            targetModeId = 1
        ))

        val record = fakeDao.items.find { it.itemName == "DBMS Record" }!!
        assertFalse("Reschedule must clear waived flag and reactivate requirement", record.isWaived)
    }

    // =========================================================================
    // 17. Departure Reminder Timing Adjustment
    // =========================================================================
    @Test
    fun testScenario17_DepartureReminderTimingAdjustment() = runBlocking {
        val now = 1_700_000_000_000L
        val originalStartTime = now + (25 * 60 * 1000L) // 25 minutes away

        val event = EventEntity(id = 17, title = "DBMS Lab", startTime = originalStartTime, associatedModeId = 1)
        val initialRelevance = DepartureReminderEngine.evaluateDepartureRelevance(event, isGeofenceExit = false, currentTime = now)
        assertEquals("25 minutes out must be DEPARTURE_WINDOW", DepartureRelevance.DEPARTURE_WINDOW, initialRelevance)

        // Event is rescheduled to 2 hours away
        val newStartTime = now + (120 * 60 * 1000L)
        val rescheduledEvent = event.copy(startTime = newStartTime)
        val updatedRelevance = DepartureReminderEngine.evaluateDepartureRelevance(rescheduledEvent, isGeofenceExit = false, currentTime = now)

        assertEquals("120 minutes out must shift to TOO_EARLY", DepartureRelevance.TOO_EARLY, updatedRelevance)
    }

    // =========================================================================
    // 18. Personal Forgetting Model Isolation
    // =========================================================================
    @Test
    fun testScenario18_PersonalForgettingModelIsolation() = runBlocking {
        val initialHabit = fakeDao.getHabit("DBMS Record", 1)!!
        val initialForgetCount = initialHabit.forgetCount

        // Execute multiple plan mutations: Reschedule, Cancel, Venue Change
        val cancelIntent = ParsedIntentResult(
            canonicalAction = CanonicalAction.EVENT_CANCELLED,
            detectedLanguage = "English",
            rawText = "DBMS Lab Cancelled",
            subjectActivity = "DBMS Lab",
            relatedItem = "DBMS Record",
            timeOrDateInfo = "Today",
            confidence = 0.95f,
            targetModeId = 1
        )
        ConsequenceEngine.processPlanMutation(fakeDao, cancelIntent)

        val rescheduleIntent = ParsedIntentResult(
            canonicalAction = CanonicalAction.EVENT_RESCHEDULED,
            detectedLanguage = "English",
            rawText = "DBMS Lab moved to tomorrow 11 AM",
            subjectActivity = "DBMS Lab",
            relatedItem = "DBMS Record",
            timeOrDateInfo = "Tomorrow 11 AM",
            confidence = 0.95f,
            targetModeId = 1
        )
        ConsequenceEngine.processPlanMutation(fakeDao, rescheduleIntent)

        val finalHabit = fakeDao.getHabit("DBMS Record", 1)!!
        assertEquals("Plan mutations must NEVER increment habit forget count", initialForgetCount, finalHabit.forgetCount)
    }

    // =========================================================================
    // 19. Structured PlanMutationDiff Validation
    // =========================================================================
    @Test
    fun testScenario19_StructuredPlanMutationDiffValidation() {
        val diff = PlanMutationDiff(
            mutationType = MutationType.POSTPONEMENT,
            activityName = "DBMS Lab",
            affectedModeId = 1,
            beforeState = "Scheduled: Monday 10:00 AM",
            afterState = "Rescheduled: Wednesday 11:00 AM",
            changedFields = listOf("startTime", "endTime"),
            confidence = 0.92f,
            evidenceSummary = "DBMS Lab postponed to Wednesday 11 AM",
            executionStatus = MutationStatus.AUTOMATIC,
            explanation = "Postponed by faculty announcement"
        )

        assertNotNull(diff.id)
        assertTrue(diff.id.isNotBlank())
        assertEquals(MutationType.POSTPONEMENT, diff.mutationType)
        assertEquals("DBMS Lab", diff.activityName)
        assertEquals(1, diff.affectedModeId)
        assertEquals("Scheduled: Monday 10:00 AM", diff.beforeState)
        assertEquals("Rescheduled: Wednesday 11:00 AM", diff.afterState)
        assertEquals(listOf("startTime", "endTime"), diff.changedFields)
        assertEquals(0.92f, diff.confidence, 0.001f)
        assertEquals(MutationStatus.AUTOMATIC, diff.executionStatus)
        assertTrue(diff.explanation.isNotBlank())
        assertTrue(diff.timestamp > 0L)
    }

    // =========================================================================
    // 20. Database & Schema Integrity
    // =========================================================================
    @Test
    fun testScenario20_DatabaseAndSchemaIntegrity() = runBlocking {
        // Verify database version is at least 7 (version 8 for final integration)
        val dbVersion = LifeOsDatabase.DATABASE_VERSION
        assertTrue("Database version must be at least 7", dbVersion >= 7)

        // Verify entities can be instantiated and mapped without schema conflict
        val event = EventEntity(
            title = "Test Event",
            startTime = 1000L,
            endTime = 2000L,
            location = "Block C",
            associatedModeId = 1,
            isGhostMode = false,
            confidence = 0.95f,
            evidenceText = "Verified"
        )
        val eventId = fakeDao.insertEvent(event)
        assertTrue(eventId > 0)

        val evidence = EvidenceEntity(
            sourcePackage = "test.package",
            rawText = "Evidence sanitized",
            parsedIntent = "TEST_INTENT",
            confidenceScore = 0.95f
        )
        val evidenceId = fakeDao.insertEvidence(evidence)
        assertTrue(evidenceId > 0)

        // Verify that all Room entities are fully functional
        assertNotNull(fakeDao.getModeById(1))
        assertTrue(fakeDao.getItemsForMode(1).isNotEmpty())
    }
}
