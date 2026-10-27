package com.lifeos.app

import com.lifeos.app.data.*
import com.lifeos.app.engine.*
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * LIFEOS Real-Life Test & Hardening Suite — Module 3: Ghost Mode, Consequence Engine & Evidence Explainer
 *
 * Real-Life Test Scenario:
 *  Ingest medium-confidence cancellation rumor:
 *  "Bro I think tomorrow's DBMS lab is cancelled, sir is not feeling well"
 *
 * Verification Checks:
 *  1. Confidence Engine & Ghost Routing:
 *     - Speculative markers ("think", "not feeling well") evaluate confidence to 0.50 - 0.84.
 *     - Item routed to Ghost Mode (isGhostMode = true) without mutating active Room DB plan.
 *  2. Ghost Mode Preview & Evidence Explainer:
 *     - Amber Ghost Card preview with source text, confidence score %, and "What Might Change" breakdown.
 *     - Evidence explainer dialog data structures verified with source metadata.
 *  3. Consequence Engine Ripple Execution:
 *     - Simulate user tapping [Apply Change] to approve Ghost recommendation.
 *     - Life Graph traversal via EdgeEntity relationType = 'REQUIRES'.
 *     - Marks DBMS Lab as cancelled in EventEntity.
 *     - Mutates Room DB setting isWaived = true for dependent "DBMS Record".
 *     - Recalculates mode readiness dynamically.
 *     - Records audit trail in EvidenceEntity (sourcePackage = 'consequence.engine', parsedIntent = 'CONSEQUENCE_WAIVED_ITEM: DBMS Record').
 */
class Module3RealLifeTest {

    private lateinit var fakeDao: FakeLifeOsDao
    private lateinit var decisionGate: DecisionGate

    private val cancellationRumor = "Bro I think tomorrow's DBMS lab is cancelled, sir is not feeling well"

    @Before
    fun setUp() {
        SafeModeManager.setSafeMode(false)
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, true)

        fakeDao = FakeLifeOsDao()
        decisionGate = DecisionGate()
        seedGraphAndInitialPlan(fakeDao)
    }

    @After
    fun tearDown() {
        SafeModeManager.setSafeMode(false)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, false)
    }

    private fun seedGraphAndInitialPlan(dao: FakeLifeOsDao) = runBlocking {
        // 1. Modes
        dao.insertLifeMode(LifeModeEntity(id = 1, name = "College", iconRes = "school", isActive = true, priority = 1))
        dao.insertLifeMode(LifeModeEntity(id = 2, name = "Gym", iconRes = "fitness", isActive = false, priority = 2))

        // 2. Scheduled Events: Data Structures & DBMS Lab
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val dbmsLabEvent = EventEntity(
            id = 101,
            title = "DBMS Lab",
            startTime = cal.timeInMillis,
            endTime = cal.timeInMillis + 7200000L,
            location = "Lab 204",
            associatedModeId = 1,
            isGhostMode = false,
            confidence = 1.0f,
            evidenceText = "Semester Timetable"
        )
        dao.insertEvent(dbmsLabEvent)

        // 3. Checklist Items (Permanent + Temporary)
        dao.insertItem(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true, isTemporary = false, isWaived = false))
        dao.insertItem(ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = true, isTemporary = false, isWaived = false))
        dao.insertItem(ItemRequirementEntity(id = 3, modeId = 1, itemName = "DBMS Record", isReady = false, isTemporary = true, isWaived = false))
        dao.insertItem(ItemRequirementEntity(id = 4, modeId = 1, itemName = "Water Bottle", isReady = false, isTemporary = false, isWaived = false))

        // 4. Personal Life Graph Nodes & Edges (DBMS Lab -> REQUIRES -> DBMS Record)
        val collegeNode = NodeEntity(id = 1, name = "College", type = "PLACE")
        val dbmsLabNode = NodeEntity(id = 4, name = "DBMS Lab", type = "ACTIVITY")
        val dbmsRecordNode = NodeEntity(id = 6, name = "DBMS Record", type = "ITEM")
        val idCardNode = NodeEntity(id = 7, name = "ID Card", type = "ITEM")

        dao.insertNode(collegeNode)
        dao.insertNode(dbmsLabNode)
        dao.insertNode(dbmsRecordNode)
        dao.insertNode(idCardNode)

        // EdgeEntity: Node 4 (DBMS Lab) REQUIRES Node 6 (DBMS Record)
        dao.insertEdge(EdgeEntity(id = 1, sourceNodeId = 1, targetNodeId = 4, relationType = "CONTAINS", weight = 1.0f))
        dao.insertEdge(EdgeEntity(id = 2, sourceNodeId = 4, targetNodeId = 6, relationType = "REQUIRES", weight = 1.0f))
        dao.insertEdge(EdgeEntity(id = 3, sourceNodeId = 1, targetNodeId = 7, relationType = "REQUIRES", weight = 1.0f))
    }

    // =========================================================================
    // 1. Confidence Engine & Ghost Routing (Features #17, #18, #19)
    // =========================================================================

    @Test
    fun testCancellationRumorEvaluatesToGhostModeConfidenceBracket() {
        val parsed = MultilingualParser.parse(cancellationRumor, sourcePackage = "com.whatsapp")

        // Assert Canonical Action
        assertEquals(CanonicalAction.EVENT_CANCELLED, parsed.canonicalAction)
        assertEquals("DBMS Lab", parsed.subjectActivity)
        assertEquals("DBMS Record", parsed.relatedItem)
        assertEquals(1, parsed.targetModeId)

        // Verify confidence score is within [0.50, 0.84] due to uncertainty markers ("think", "sir is not feeling well")
        assertTrue(
            "Confidence must be >= 0.50 for speculative rumor, but was ${parsed.confidence}",
            parsed.confidence >= 0.50f
        )
        assertTrue(
            "Confidence must be <= 0.84 for speculative rumor, but was ${parsed.confidence}",
            parsed.confidence <= 0.84f
        )
        assertTrue(
            "DecisionGate must classify confidence ${parsed.confidence} as requiring Ghost Routing",
            DecisionGate.isGhostRoutingRequired(parsed.confidence)
        )
    }

    @Test
    fun testGhostRoutingPreservesActivePlanBeforeUserApproval() = runBlocking {
        // Record baseline readiness before rumor
        val collegeMode = fakeDao.getModeById(1)
        val initialItems = fakeDao.getItemsForMode(1)
        val baselineReadiness = ReadinessEngine.calculateReadiness(collegeMode, initialItems)
        assertEquals(4, baselineReadiness.totalCount)
        assertEquals(2, baselineReadiness.readyCount)
        assertEquals(50, baselineReadiness.percentage)

        // Filter and ingest through DecisionGate
        val filterResult = LightweightEventFilter.filter(cancellationRumor, sourcePackage = "com.whatsapp")
        val decision = decisionGate.processPayload(filterResult, fakeDao)

        // Assert decision result is GhostModeRouted
        assertTrue("Decision must be GhostModeRouted, was $decision", decision is DecisionResult.GhostModeRouted)
        val ghostResult = decision as DecisionResult.GhostModeRouted
        assertTrue(ghostResult.item.isGhostMode)
        assertTrue(ghostResult.item.itemName.contains("Possible Cancellation: DBMS Lab"))

        // CRITICAL CHECK: Confirm Room DB active plan was NOT mutated
        val activeEvents = fakeDao.events.filter { !it.isGhostMode }
        val activeLab = activeEvents.find { it.title.contains("DBMS Lab", ignoreCase = true) }
        assertNotNull("Active DBMS Lab event must remain present in calendar", activeLab)
        assertFalse("Active DBMS Lab event title must NOT be marked cancelled yet", activeLab!!.title.contains("(Cancelled)"))

        val dbmsRecord = fakeDao.items.find { it.itemName.equals("DBMS Record", ignoreCase = true) }
        assertNotNull(dbmsRecord)
        assertFalse("DBMS Record requirement must NOT be waived before user approval", dbmsRecord!!.isWaived)

        // Readiness must remain unaffected at 50%
        val postIngestItems = fakeDao.getItemsForMode(1).filter { !it.isGhostMode }
        val postIngestReadiness = ReadinessEngine.calculateReadiness(collegeMode, postIngestItems)
        assertEquals(50, postIngestReadiness.percentage)

        // Verify Ghost Item and Ghost Event were staged in DB
        val ghostItems = fakeDao.items.filter { it.isGhostMode }
        assertEquals(1, ghostItems.size)
        assertEquals("Possible Cancellation: DBMS Lab", ghostItems.first().itemName)

        val ghostEvents = fakeDao.events.filter { it.isGhostMode }
        assertEquals(1, ghostEvents.size)
        assertTrue(ghostEvents.first().title.contains("DBMS Lab (Cancelled)"))
    }

    // =========================================================================
    // 2. Ghost Mode Preview & Evidence Explainer (Features #20, #21, #58)
    // =========================================================================

    @Test
    fun testGhostModePreviewCardAndEvidenceExplainerMetadata() = runBlocking {
        val filterResult = LightweightEventFilter.filter(cancellationRumor, sourcePackage = "org.telegram.messenger")
        val decision = decisionGate.processPayload(filterResult, fakeDao) as DecisionResult.GhostModeRouted

        val ghostItem = decision.item
        val evidence = decision.evidence

        // 2a. Amber Ghost Card preview properties
        assertTrue("Ghost item must have isGhostMode = true", ghostItem.isGhostMode)
        assertTrue("Ghost item confidence must be in [0.50, 0.84]", ghostItem.confidenceScore in 0.50f..0.84f)
        assertEquals(cancellationRumor, ghostItem.sourceEvidence)
        assertTrue(ghostItem.ttlTimestamp > System.currentTimeMillis())

        // 2b. Evidence Explainer data structures
        assertNotNull(evidence)
        assertEquals("org.telegram.messenger", evidence.sourcePackage)
        assertTrue(evidence.parsedIntent?.contains("GHOST_PROPOSAL") == true)
        assertEquals(ghostItem.confidenceScore, evidence.confidenceScore, 0.001f)

        // 2c. Verify Habit scoring integration if present
        val habit = ItemHabitEntity(itemName = "DBMS Record", modeId = 1, forgetCount = 3, confirmCount = 1)
        val forgetfulnessScore = ForgettingModelEngine.calculateForgetfulnessScore(habit.forgetCount, habit.confirmCount)
        val riskLevel = ForgettingModelEngine.getRiskLevel(habit.forgetCount)
        assertEquals(RiskLevel.HIGH_RISK, riskLevel)
        assertEquals(4.2f, forgetfulnessScore, 0.01f)
    }

    // =========================================================================
    // 3. Consequence Engine Ripple Execution on User Approval (Features #22, #23, #27, #28)
    // =========================================================================

    @Test
    fun testConsequenceEngineRippleExecutionOnUserApproval() = runBlocking {
        // Step 1: Ingest rumor to stage Ghost recommendation
        val filterResult = LightweightEventFilter.filter(cancellationRumor, sourcePackage = "com.whatsapp")
        decisionGate.processPayload(filterResult, fakeDao)

        val beforeApprovalRecord = fakeDao.items.find { it.itemName == "DBMS Record" }
        assertFalse(beforeApprovalRecord!!.isWaived)

        // Step 2: Simulate User clicking [Apply Change] to approve the Ghost recommendation
        val alert = ConsequenceEngine.applyConfirmedProposal(
            dao = fakeDao,
            activityName = "DBMS Lab",
            rawText = cancellationRumor,
            confidence = filterResult.relevanceScore,
            targetModeId = 1
        )

        // Step 3: Verify Life Graph Traversal (Node 4 'DBMS Lab' -[REQUIRES]-> Node 6 'DBMS Record')
        val requiredNodes = fakeDao.getRequiredItemsForActivityName("DBMS Lab")
        assertEquals(1, requiredNodes.size)
        assertEquals("DBMS Record", requiredNodes.first().name)
        assertTrue(alert.waivedItems.contains("DBMS Record"))

        // Step 4: Verify EventEntity updated to cancelled
        val updatedLabEvent = fakeDao.events.find { it.id == 101 }
        assertNotNull(updatedLabEvent)
        assertTrue("Event title must be marked (Cancelled)", updatedLabEvent!!.title.contains("(Cancelled)"))
        assertTrue("Evidence text must contain cancellation context", updatedLabEvent.evidenceText?.contains(cancellationRumor) == true)

        // Step 5: Verify Room DB item waiving (isWaived = true)
        val waivedRecord = fakeDao.items.find { it.itemName == "DBMS Record" }
        assertNotNull(waivedRecord)
        assertTrue("DBMS Record requirement must now have isWaived = true", waivedRecord!!.isWaived)

        // Step 6: Verify Dynamic Readiness Recalculation
        val activeMode = fakeDao.getModeById(1)
        val activeItems = fakeDao.getItemsForMode(1).filter { !it.isGhostMode }
        val updatedReadiness = ReadinessEngine.calculateReadiness(activeMode, activeItems)

        // Total non-waived required items: ID Card (ready), Laptop (ready), Water Bottle (pending) = 3 total, 2 ready
        // Math.round(2 / 3 * 100) = 67% (previously 50% with non-waived DBMS Record)
        assertEquals(3, updatedReadiness.totalCount)
        assertEquals(2, updatedReadiness.readyCount)
        assertEquals(67, updatedReadiness.percentage)
        assertFalse(updatedReadiness.pendingItems.contains("DBMS Record"))

        // Step 7: Verify EvidenceEntity recorded in audit log
        val auditEvidence = fakeDao.evidence.find {
            it.sourcePackage == "consequence.engine" && it.parsedIntent?.contains("CONSEQUENCE_WAIVED_ITEM: DBMS Record") == true
        }
        assertNotNull("Audit trail must be logged in EvidenceEntity", auditEvidence)
        assertEquals("consequence.engine", auditEvidence!!.sourcePackage)
        assertEquals("CONSEQUENCE_WAIVED_ITEM: DBMS Record", auditEvidence.parsedIntent)
        assertTrue(auditEvidence.rawText?.contains("DBMS Record") == true)

        // Step 8: Verify Ghost items/events are cleaned up upon application
        val remainingGhostItems = fakeDao.items.filter { it.isGhostMode }
        assertTrue("Ghost items must be cleared after approval", remainingGhostItems.isEmpty())
    }

    // =========================================================================
    // 4. Edge Case: User Ignores Proposal (Plan Remains Pristine)
    // =========================================================================

    @Test
    fun testUserIgnoresProposalLeavesActivePlanUnchanged() = runBlocking {
        // Ingest rumor to stage Ghost recommendation
        val filterResult = LightweightEventFilter.filter(cancellationRumor, sourcePackage = "com.whatsapp")
        val decision = decisionGate.processPayload(filterResult, fakeDao) as DecisionResult.GhostModeRouted

        val ghostItemId = decision.item.id

        // User clicks [Ignore]
        fakeDao.deleteItemById(ghostItemId)

        // Ghost item deleted
        assertNull(fakeDao.getItemById(ghostItemId))

        // Active plan untouched
        val labEvent = fakeDao.events.find { it.id == 101 }
        assertFalse(labEvent!!.title.contains("(Cancelled)"))

        val dbmsRecord = fakeDao.items.find { it.itemName == "DBMS Record" }
        assertFalse(dbmsRecord!!.isWaived)

        val report = ReadinessEngine.calculateReadiness(fakeDao.getModeById(1), fakeDao.getItemsForMode(1))
        assertEquals(50, report.percentage)
    }

    // =========================================================================
    // 5. Comparison: High Confidence Official Notice vs Medium Confidence Rumor
    // =========================================================================

    @Test
    fun testHighConfidenceNoticeVsMediumConfidenceRumor() = runBlocking {
        val officialNotice = "DBMS Lab evaluation is definitely cancelled for today due to power maintenance"
        val officialParsed = MultilingualParser.parse(officialNotice, sourcePackage = "com.whatsapp")

        // Official notice has high confidence >= 0.85
        assertTrue(
            "Official notice without uncertainty markers must have confidence >= 0.85, was ${officialParsed.confidence}",
            officialParsed.confidence >= 0.85f
        )
        assertFalse(
            "Official notice must NOT be routed to Ghost Mode",
            DecisionGate.isGhostRoutingRequired(officialParsed.confidence)
        )

        // Rumor with uncertainty markers has confidence in [0.50, 0.84]
        val rumorParsed = MultilingualParser.parse(cancellationRumor, sourcePackage = "com.whatsapp")
        assertTrue(
            "Rumor with 'think' and 'sir is not feeling well' must have confidence in [0.50, 0.84], was ${rumorParsed.confidence}",
            rumorParsed.confidence in 0.50f..0.84f
        )
        assertTrue(
            "Rumor MUST require Ghost Mode routing",
            DecisionGate.isGhostRoutingRequired(rumorParsed.confidence)
        )
    }
}
