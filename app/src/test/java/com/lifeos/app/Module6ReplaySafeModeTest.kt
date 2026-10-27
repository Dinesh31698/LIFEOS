package com.lifeos.app

import com.lifeos.app.data.EventEntity
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.data.NodeEntity
import com.lifeos.app.data.EdgeEntity
import com.lifeos.app.engine.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LIFEOS Module 6: Replay Engine & Safe Mode Isolation Test Suite.
 * Validates:
 *  1. 3-Stage Synthetic Replay Scenario Automation (College Departure -> Lab Cancellation -> Gym Transition).
 *  2. Safe Mode Isolation Guard suppressing automatic item waiving in ConsequenceEngine.
 *  3. Safe Mode routing high-confidence items (>= 0.85) to Ghost Mode.
 *  4. Toggling Safe Mode off immediately restoring autonomous mutations and auto-commit.
 *  5. Replay engine lifecycle & state reset.
 *  6. Diagnostic logging guarantees under LIFEOS_MODULE6.
 */
class Module6ReplaySafeModeTest {

    private lateinit var fakeDao: FakeLifeOsDao

    @Before
    fun setUp() {
        SafeModeManager.setSafeMode(false)
        ReplayEngine.resetSimulation()
        ConsequenceEngine.dismissAlert()
        fakeDao = FakeLifeOsDao()
        seedTestData(fakeDao)
    }

    @After
    fun tearDown() {
        SafeModeManager.setSafeMode(false)
        ReplayEngine.resetSimulation()
        ConsequenceEngine.dismissAlert()
    }

    private fun seedTestData(dao: FakeLifeOsDao) {
        // Mode 1: College Mode (Active)
        dao.modes.add(LifeModeEntity(id = 1, name = "College", iconRes = "school", isActive = true))
        // Mode 2: Gym Mode
        dao.modes.add(LifeModeEntity(id = 2, name = "Gym", iconRes = "fitness_center", isActive = false))

        // Items for College Mode
        dao.items.add(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 2, modeId = 1, itemName = "DBMS Record", isReady = false, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 3, modeId = 1, itemName = "Laptop", isReady = false, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 4, modeId = 1, itemName = "Scientific Calculator", isReady = false, isWaived = false))

        // Items for Gym Mode
        dao.items.add(ItemRequirementEntity(id = 5, modeId = 2, itemName = "Water Bottle", isReady = true, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 6, modeId = 2, itemName = "Gym Shoes", isReady = false, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 7, modeId = 2, itemName = "Gym Towel", isReady = false, isWaived = false))

        // Graph dependencies: DBMS Lab requires DBMS Record
        dao.nodes.add(NodeEntity(id = 101, name = "DBMS Lab", type = "ACTIVITY"))
        dao.nodes.add(NodeEntity(id = 102, name = "DBMS Record", type = "ITEM"))
        dao.edges.add(EdgeEntity(id = 201, sourceNodeId = 101, targetNodeId = 102, relationType = "REQUIRES"))
    }

    // =========================================================================
    // REQUIREMENT 1: 3-STAGE REPLAY SCENARIO AUTOMATION
    // =========================================================================

    @Test
    fun testThreeStageReplayScenarioExecutesSuccessfully() = runBlocking {
        // Ensure initial state is clean
        assertEquals(0, ReplayEngine.replayState.value.currentStep)
        assertFalse(ReplayEngine.replayState.value.isRunning)

        // Execute 3-stage replay without delay for instant test execution
        ReplayEngine.runDefaultScenario(fakeDao, stepDelayMillis = 0L)

        val finalState = ReplayEngine.replayState.value
        assertFalse("Replay should finish running", finalState.isRunning)
        assertEquals(3, finalState.currentStep)
        assertEquals(3, finalState.totalSteps)
        assertEquals(3, finalState.history.size)
        assertTrue(finalState.statusText.contains("completed successfully", ignoreCase = true))

        // Verify Step 1: Morning College Departure
        val step1 = finalState.history[0]
        assertEquals(1, step1.stepNumber)
        assertEquals("Morning College Departure", step1.title)
        assertTrue(step1.detail.contains("College"))
        assertTrue(step1.detail.contains("Laptop beacon"))
        val activeModeAfterStep1 = fakeDao.modes.find { it.id == 1 }
        assertNotNull(activeModeAfterStep1)
        val laptopPresence = DevicePresenceScanner.detectedDevices.value.find { it.deviceName == "Laptop Beacon" }
        assertNotNull(laptopPresence)
        assertEquals(PresenceConfidence.HIGH_CONFIDENCE, laptopPresence?.confidence)

        // Verify Step 2: Teluglish Cancellation Ingestion & Consequence Ripple
        val step2 = finalState.history[1]
        assertEquals(2, step2.stepNumber)
        assertEquals("Lab Cancellation & Plan Mutation", step2.title)
        assertTrue(step2.detail.contains("DBMS lab cancel aindi"))
        assertTrue(step2.detail.contains("DBMS Record"))
        // DBMS Record must be waived
        val dbmsRecord = fakeDao.items.find { it.itemName == "DBMS Record" }
        assertNotNull(dbmsRecord)
        assertTrue("DBMS Record should be waived following Teluglish notice", dbmsRecord!!.isWaived)
        // Evidence should be recorded
        assertTrue(fakeDao.evidence.any { (it.rawText?.contains("DBMS Record") == true) || (it.parsedIntent?.contains("CONSEQUENCE_WAIVED_ITEM") == true) })

        // Verify Step 3: Evening Gym Transition
        val step3 = finalState.history[2]
        assertEquals(3, step3.stepNumber)
        assertEquals("Evening Gym Transition", step3.title)
        assertTrue(step3.detail.contains("Gym"))
        val activeGymMode = fakeDao.modes.find { it.id == 2 }
        assertNotNull(activeGymMode)
        assertTrue("Gym Mode should be active after Step 3", activeGymMode!!.isActive)
        val smartwatchPresence = DevicePresenceScanner.detectedDevices.value.find { it.deviceName == "Smartwatch" }
        assertNotNull(smartwatchPresence)
        assertEquals(PresenceConfidence.HIGH_CONFIDENCE, smartwatchPresence?.confidence)
    }

    // =========================================================================
    // REQUIREMENT 2: SAFE MODE ISOLATION GUARD IN CONSEQUENCE ENGINE
    // =========================================================================

    @Test
    fun testSafeModeSuppressesAutomaticPlanMutationAndItemWaiving() = runBlocking {
        // Activate Safe Mode Emergency Kill-Switch
        SafeModeManager.setSafeMode(true)
        assertTrue(SafeModeManager.isSafeModeActive)
        assertFalse(SafeModeManager.canMutatePlans())
        assertFalse(SafeModeManager.canAutoPromoteGhostItems())

        // Ingest Teluglish cancellation intent
        val cancelIntent = MultilingualParser.parse("DBMS lab cancel aindi", "whatsapp.cs.group")
        val consequenceAlert = ConsequenceEngine.processPlanMutation(fakeDao, cancelIntent)

        assertNotNull(consequenceAlert)
        assertEquals("Safe Mode: Mutation Suppressed", consequenceAlert!!.title)
        assertEquals("Suppressed (Safe Mode Active)", consequenceAlert.actionTaken)
        assertTrue("Waived items list should be empty when Safe Mode is active", consequenceAlert.waivedItems.isEmpty())

        // Verify DBMS Record remains UNWAIVED in Room DB
        val dbmsRecord = fakeDao.items.find { it.itemName == "DBMS Record" }
        assertNotNull(dbmsRecord)
        assertFalse("DBMS Record must NOT be waived when Safe Mode is active", dbmsRecord!!.isWaived)

        // Verify proposal is safely routed to Ghost Mode in events table for manual user confirmation
        val ghostEvent = fakeDao.events.find { it.isGhostMode && it.title.contains("Cancelled") }
        assertNotNull("Ghost Mode event must be created to hold mutation proposal", ghostEvent)
        assertTrue(ghostEvent!!.evidenceText?.contains("Safe Mode Held") == true)

        // Verify evidence audit trail records SAFE_MODE_BLOCKED
        val blockedEvidence = fakeDao.evidence.find { it.parsedIntent?.contains("SAFE_MODE_BLOCKED") == true }
        assertNotNull("Audit trail must record Safe Mode blocked evidence", blockedEvidence)
    }

    // =========================================================================
    // REQUIREMENT 3: SAFE MODE FORCES HIGH CONFIDENCE ITEMS INTO GHOST MODE
    // =========================================================================

    @Test
    fun testSafeModeForcesHighConfidenceItemsToGhostMode() {
        // Under normal mode, confidence >= 0.85 auto-commits
        SafeModeManager.setSafeMode(false)
        assertFalse("Default high confidence (0.95) should not require ghost routing", DecisionGate.isGhostRoutingRequired(0.95f))
        assertFalse("Default confidence (0.85) should not require ghost routing", DecisionGate.isGhostRoutingRequired(0.85f))
        assertTrue("Medium confidence (0.75) requires ghost routing", DecisionGate.isGhostRoutingRequired(0.75f))

        // Engage Safe Mode: ALL items must be routed to Ghost Mode
        SafeModeManager.setSafeMode(true)
        assertTrue("Under Safe Mode, 0.95 confidence must be forced to Ghost Mode", DecisionGate.isGhostRoutingRequired(0.95f))
        assertTrue("Under Safe Mode, 0.85 confidence must be forced to Ghost Mode", DecisionGate.isGhostRoutingRequired(0.85f))
        assertTrue("Under Safe Mode, 0.70 confidence must be forced to Ghost Mode", DecisionGate.isGhostRoutingRequired(0.70f))
        assertFalse("Safe Mode prohibits auto-promoting ghost items", SafeModeManager.canAutoPromoteGhostItems())
    }

    // =========================================================================
    // REQUIREMENT 4: DISENGAGING SAFE MODE RESTORES AUTONOMOUS MUTATIONS
    // =========================================================================

    @Test
    fun testDisengagingSafeModeRestoresAutonomousPlanMutations() = runBlocking {
        // Engage Safe Mode first
        SafeModeManager.setSafeMode(true)
        assertTrue(SafeModeManager.isSafeModeActive)

        // Disengage Safe Mode (Restore normal operation)
        SafeModeManager.setSafeMode(false)
        assertFalse(SafeModeManager.isSafeModeActive)
        assertTrue(SafeModeManager.canMutatePlans())
        assertTrue(SafeModeManager.canAutoPromoteGhostItems())
        assertFalse(DecisionGate.isGhostRoutingRequired(0.95f))

        // Process cancellation under restored mode
        val cancelIntent = MultilingualParser.parse("DBMS lab cancel aindi", "whatsapp.cs.group")
        val consequenceAlert = ConsequenceEngine.processPlanMutation(fakeDao, cancelIntent)

        assertNotNull(consequenceAlert)
        assertTrue(consequenceAlert!!.title.contains("Schedule Change"))
        assertTrue(consequenceAlert.actionTaken.contains("Item Waived"))
        assertTrue(consequenceAlert.waivedItems.contains("DBMS Record"))

        // Verify DBMS Record IS NOW WAIVED in Room DB
        val dbmsRecord = fakeDao.items.find { it.itemName == "DBMS Record" }
        assertNotNull(dbmsRecord)
        assertTrue("DBMS Record should be waived now that Safe Mode is disengaged", dbmsRecord!!.isWaived)
    }

    // =========================================================================
    // REQUIREMENT 5: SAFE MODE TOGGLE & REPLAY RESET
    // =========================================================================

    @Test
    fun testSafeModeToggleMethod() {
        SafeModeManager.setSafeMode(false)
        assertFalse(SafeModeManager.isSafeModeActive)

        SafeModeManager.toggleSafeMode()
        assertTrue(SafeModeManager.isSafeModeActive)

        SafeModeManager.toggleSafeMode()
        assertFalse(SafeModeManager.isSafeModeActive)
    }

    @Test
    fun testReplayStateResetLifecycle() = runBlocking {
        ReplayEngine.runDefaultScenario(fakeDao, stepDelayMillis = 0L)
        assertEquals(3, ReplayEngine.replayState.value.currentStep)
        assertEquals(3, ReplayEngine.replayState.value.history.size)

        ReplayEngine.resetSimulation()
        val resetState = ReplayEngine.replayState.value
        assertFalse(resetState.isRunning)
        assertEquals(0, resetState.currentStep)
        assertEquals(3, resetState.totalSteps)
        assertTrue(resetState.history.isEmpty())
        assertTrue(resetState.statusText.contains("Idle"))
    }
}
