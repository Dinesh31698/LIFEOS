package com.lifeos.app

import com.lifeos.app.data.EvidenceEntity
import com.lifeos.app.data.ItemHabitEntity
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.data.NodeEntity
import com.lifeos.app.data.EdgeEntity
import com.lifeos.app.engine.*
import com.lifeos.app.security.DataMinimizer
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LIFEOS Module 7: Privacy Firewall, Data Minimization & Release Integrity Test Suite.
 * Validates:
 *  1. Permission Firewall blocks all 4 channels (LOCATION, NOTIFICATION_INGESTION, BLE_SCANNING, CALENDAR_SYNC)
 *     when disabled by user and authorizes when enabled.
 *  2. PII Sanitizer strips phone numbers (10-digit, international +91, dashed, parenthesized) -> [PHONE REDACTED].
 *  3. PII Sanitizer strips email addresses -> [EMAIL REDACTED].
 *  4. PII Sanitizer strips personal greetings and names -> [GREETING REDACTED].
 *  5. Data cleanup routines (clearEvidenceLogs, clearTemporaryItems, pruneEvidenceLogs) maintain Room DB hygiene.
 *  6. Full 7-stage sequential integration pipeline test connecting all Modules (1 through 7).
 */
class Module7PrivacyReleaseTest {

    private lateinit var fakeDao: FakeLifeOsDao

    @Before
    fun setUp() {
        SafeModeManager.setSafeMode(false)
        ConsequenceEngine.dismissAlert()
        ReplayEngine.resetSimulation()

        // Reset Firewall channel toggles to default
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, false)

        fakeDao = FakeLifeOsDao()
        seedTestData(fakeDao)
    }

    @After
    fun tearDown() {
        SafeModeManager.setSafeMode(false)
        ConsequenceEngine.dismissAlert()
        ReplayEngine.resetSimulation()

        // Restore defaults
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, false)
    }

    private fun seedTestData(dao: FakeLifeOsDao) {
        dao.modes.add(LifeModeEntity(id = 1, name = "College", iconRes = "school", isActive = true))
        dao.modes.add(LifeModeEntity(id = 2, name = "Gym", iconRes = "fitness_center", isActive = false))

        dao.items.add(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 2, modeId = 1, itemName = "DBMS Record", isReady = false, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 3, modeId = 1, itemName = "Laptop", isReady = false, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 4, modeId = 1, itemName = "Scientific Calculator", isReady = false, isWaived = false))

        // Activity Node: "DBMS Lab" (Node 1) -> ITEM: "DBMS Record" (Node 2)
        dao.nodes.add(NodeEntity(id = 1, name = "DBMS Lab", type = "ACTIVITY", metadataJson = "{}"))
        dao.nodes.add(NodeEntity(id = 2, name = "DBMS Record", type = "ITEM", metadataJson = "{}"))
        dao.edges.add(EdgeEntity(id = 1, sourceNodeId = 1, targetNodeId = 2, relationType = "REQUIRES"))
    }

    // =========================================================================
    // TEST 1: Permission Firewall Blocks All 4 Channels When Disabled
    // =========================================================================
    @Test
    fun testPermissionFirewallBlocksAllChannelsWhenDisabled() {
        // Channel 1: LOCATION
        assertTrue("LOCATION default must be authorized", PermissionFirewall.isSourceAuthorized(SourceType.LOCATION))
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, false)
        assertFalse("LOCATION disabled must be blocked by firewall", PermissionFirewall.isSourceAuthorized(SourceType.LOCATION))
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        assertTrue("LOCATION re-enabled must be authorized", PermissionFirewall.isSourceAuthorized(SourceType.LOCATION))

        // Channel 2: NOTIFICATION_INGESTION
        assertTrue("NOTIFICATION default must be authorized", PermissionFirewall.isSourceAuthorized(SourceType.NOTIFICATION_INGESTION))
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, false)
        assertFalse("NOTIFICATION disabled must be blocked by firewall", PermissionFirewall.isSourceAuthorized(SourceType.NOTIFICATION_INGESTION))
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        assertTrue("NOTIFICATION re-enabled must be authorized", PermissionFirewall.isSourceAuthorized(SourceType.NOTIFICATION_INGESTION))

        // Channel 3: BLE_SCANNING
        assertTrue("BLE default must be authorized", PermissionFirewall.isSourceAuthorized(SourceType.BLE_SCANNING))
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, false)
        assertFalse("BLE disabled must be blocked by firewall", PermissionFirewall.isSourceAuthorized(SourceType.BLE_SCANNING))
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        assertTrue("BLE re-enabled must be authorized", PermissionFirewall.isSourceAuthorized(SourceType.BLE_SCANNING))

        // Channel 4: CALENDAR_SYNC
        assertFalse("CALENDAR default must be disabled", PermissionFirewall.isSourceAuthorized(SourceType.CALENDAR_SYNC))
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, true)
        assertTrue("CALENDAR enabled must be authorized", PermissionFirewall.isSourceAuthorized(SourceType.CALENDAR_SYNC))
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, false)
        assertFalse("CALENDAR disabled must be blocked by firewall", PermissionFirewall.isSourceAuthorized(SourceType.CALENDAR_SYNC))
    }

    // =========================================================================
    // TEST 2: PII Sanitization Strips Phone Numbers
    // =========================================================================
    @Test
    fun testPiiSanitizationStripsPhoneNumbers() {
        // 10-digit standard
        val raw1 = "Please call 9876543210 to collect your DBMS Record."
        val sanitized1 = DataMinimizer.stripPii(raw1)
        assertFalse("Raw 10-digit number must not appear in output", sanitized1.contains("9876543210"))
        assertTrue("Placeholder [PHONE REDACTED] must be present", sanitized1.contains("[PHONE REDACTED]"))

        // International with country code (+91)
        val raw2 = "Urgent: Message +91 9876543210 or +91-98765-43210 regarding the lab timetable."
        val sanitized2 = DataMinimizer.stripPii(raw2)
        assertFalse("International number must not appear in output", sanitized2.contains("9876543210"))
        assertFalse("International dashed number must not appear in output", sanitized2.contains("+91-98765-43210"))
        assertTrue("Placeholder [PHONE REDACTED] must be present", sanitized2.contains("[PHONE REDACTED]"))

        // Dashed formatted (US style)
        val raw3 = "Security desk helpline: 123-456-7890."
        val sanitized3 = DataMinimizer.stripPii(raw3)
        assertFalse("Dashed phone number must not appear in output", sanitized3.contains("123-456-7890"))
        assertTrue("Placeholder [PHONE REDACTED] must be present", sanitized3.contains("[PHONE REDACTED]"))

        // Parenthesized formatted
        val raw4 = "Reach the coordinator at (555) 123-4567 for hall pass."
        val sanitized4 = DataMinimizer.stripPii(raw4)
        assertFalse("Parenthesized phone number must not appear in output", sanitized4.contains("(555) 123-4567"))
        assertTrue("Placeholder [PHONE REDACTED] must be present", sanitized4.contains("[PHONE REDACTED]"))
    }

    // =========================================================================
    // TEST 3: PII Sanitization Strips Email Addresses
    // =========================================================================
    @Test
    fun testPiiSanitizationStripsEmailAddresses() {
        val raw1 = "Submit your assignment to prof.sharma@college.edu before 5:00 PM."
        val sanitized1 = DataMinimizer.stripPii(raw1)
        assertFalse("Professor email must be redacted", sanitized1.contains("prof.sharma@college.edu"))
        assertTrue("Placeholder [EMAIL REDACTED] must be present", sanitized1.contains("[EMAIL REDACTED]"))

        val raw2 = "Queries to support@lifeos.app or student.test@university.ac.in."
        val sanitized2 = DataMinimizer.stripPii(raw2)
        assertFalse("Support email must be redacted", sanitized2.contains("support@lifeos.app"))
        assertFalse("Student email must be redacted", sanitized2.contains("student.test@university.ac.in"))
        assertTrue("Placeholder [EMAIL REDACTED] must be present", sanitized2.contains("[EMAIL REDACTED]"))
    }

    // =========================================================================
    // TEST 4: PII Sanitization Strips Personal Greetings & Names
    // =========================================================================
    @Test
    fun testPiiSanitizationStripsGreetingsAndNames() {
        val raw1 = "Dear Dr. Smith, submitting the DBMS Record."
        val sanitized1 = DataMinimizer.stripPii(raw1)
        assertFalse("Salutation 'Dear Dr. Smith' must be redacted", sanitized1.contains("Dear Dr. Smith"))
        assertTrue("Placeholder [GREETING REDACTED] must be present", sanitized1.contains("[GREETING REDACTED]"))

        val raw2 = "Hi Rahul, please remember your Calculator for the exam."
        val sanitized2 = DataMinimizer.stripPii(raw2)
        assertFalse("Greeting 'Hi Rahul' must be redacted", sanitized2.contains("Hi Rahul"))
        assertTrue("Placeholder [GREETING REDACTED] must be present", sanitized2.contains("[GREETING REDACTED]"))

        val raw3 = "Hello Priya, lab starts at 10 AM."
        val sanitized3 = DataMinimizer.stripPii(raw3)
        assertFalse("Greeting 'Hello Priya' must be redacted", sanitized3.contains("Hello Priya"))
        assertTrue("Placeholder [GREETING REDACTED] must be present", sanitized3.contains("[GREETING REDACTED]"))

        val raw4 = "Hey Suresh, bring your gym shoes."
        val sanitized4 = DataMinimizer.stripPii(raw4)
        assertFalse("Greeting 'Hey Suresh' must be redacted", sanitized4.contains("Hey Suresh"))
        assertTrue("Placeholder [GREETING REDACTED] must be present", sanitized4.contains("[GREETING REDACTED]"))

        val raw5 = "Bhai Ramesh, note the schedule change."
        val sanitized5 = DataMinimizer.stripPii(raw5)
        assertFalse("Greeting 'Bhai Ramesh' must be redacted", sanitized5.contains("Bhai Ramesh"))
        assertTrue("Placeholder [GREETING REDACTED] must be present", sanitized5.contains("[GREETING REDACTED]"))
    }

    // =========================================================================
    // TEST 5: Data Cleanup Utilities
    // =========================================================================
    @Test
    fun testDataCleanupUtilities() = runBlocking {
        // 1. Evidence Logs Pruning & Purging
        fakeDao.insertEvidence(EvidenceEntity(id = 1, eventId = 1, sourcePackage = "SMS", rawText = "Call 9876543210 urgent", timestamp = System.currentTimeMillis()))
        fakeDao.insertEvidence(EvidenceEntity(id = 2, eventId = 1, sourcePackage = "WhatsApp", rawText = "Email prof@college.edu", timestamp = System.currentTimeMillis()))
        assertEquals(2, fakeDao.evidence.size)

        // Prune (Sanitize) historical evidence
        DataMinimizer.pruneEvidenceLogs(fakeDao)
        assertEquals(2, fakeDao.evidence.size)
        assertTrue("Evidence 1 should be sanitized", fakeDao.evidence[0].rawText.contains("[Sanitized"))
        assertTrue("Evidence 2 should be sanitized", fakeDao.evidence[1].rawText.contains("[Sanitized"))

        // Purge (Clear) all evidence
        DataMinimizer.clearEvidenceLogs(fakeDao)
        assertEquals(0, fakeDao.evidence.size)

        // 2. Temporary Items Cleanup
        fakeDao.items.add(ItemRequirementEntity(id = 101, modeId = 1, itemName = "Permanent Pass", isTemporary = false))
        fakeDao.items.add(ItemRequirementEntity(id = 102, modeId = 1, itemName = "Temp Hall Ticket", isTemporary = true))
        fakeDao.items.add(ItemRequirementEntity(id = 103, modeId = 1, itemName = "One-time Permission Slip", isTemporary = true))

        val deletedCount = DataMinimizer.clearTemporaryItems(fakeDao)
        assertEquals("Should delete exactly 2 temporary items", 2, deletedCount)
        assertNull("Temp item 102 should be removed", fakeDao.items.find { it.id == 102 })
        assertNull("Temp item 103 should be removed", fakeDao.items.find { it.id == 103 })
        assertNotNull("Permanent item 101 must remain", fakeDao.items.find { it.id == 101 })
    }

    // =========================================================================
    // TEST 6: Full 7-Stage End-to-End Sequential Integration Pipeline Test
    // =========================================================================
    @Test
    fun testFullSevenStageSequentialPipeline() = runBlocking {
        // --- STAGE 1: Notification Ingestion & Multilingual NLP ---
        val teluglishNotice = "Repu lab undi DBMS record teesuko"
        val parsedIntent = MultilingualParser.parse(teluglishNotice)
        assertEquals(CanonicalAction.REQUIRED_ITEM, parsedIntent.canonicalAction)
        assertEquals("DBMS Record", parsedIntent.relatedItem)
        assertTrue("Multilingual parsing confidence should be >= 0.90", parsedIntent.confidence >= 0.90f)

        // --- STAGE 2: Consequence Engine & Plan Mutation ---
        val cancelNotice = "DBMS lab cancel aindi"
        val cancelIntent = MultilingualParser.parse(cancelNotice)
        assertEquals(CanonicalAction.EVENT_CANCELLED, cancelIntent.canonicalAction)

        val alert = ConsequenceEngine.processPlanMutation(fakeDao, cancelIntent)
        assertNotNull("Consequence alert must be generated", alert)
        assertTrue("Waived items must include DBMS Record", alert!!.waivedItems.contains("DBMS Record"))

        val dbmsRecordItem = fakeDao.items.find { it.itemName == "DBMS Record" }!!
        assertTrue("DBMS Record requirement must be set to isWaived = true", dbmsRecordItem.isWaived)

        // --- STAGE 3: Personal Forgetting Model & Habit Learning ---
        val habitScore = ForgettingModelEngine.calculateForgetfulnessScore(forgetCount = 3, confirmCount = 1)
        // Score = (1.5 * 3) - (0.3 * 1) = 4.5 - 0.3 = 4.2
        assertEquals(4.2f, habitScore, 0.001f)
        val riskLevel = ForgettingModelEngine.getRiskLevel(forgetCount = 3)
        assertEquals(RiskLevel.HIGH_RISK, riskLevel)

        // --- STAGE 4: Contextual Readiness & Anti-Spam Aggregation ---
        val activeMode = fakeDao.getModeById(1)!!
        val currentItems = fakeDao.getItemsForMode(1)
        val readiness = ReadinessEngine.calculateReadiness(activeMode, currentItems)

        // Initial items: 1 ready (ID Card), 1 waived (DBMS Record), 2 pending (Laptop, Scientific Calculator)
        // Non-waived total = 3, ready = 1 -> 33%
        assertEquals(33, readiness.percentage)
        assertEquals(1, readiness.waivedCount)
        assertFalse("Waived item must not be listed in pending items", readiness.pendingItems.contains("DBMS Record"))

        // --- STAGE 5: Device Presence & Battery Budget Manager ---
        val highRssi = -65
        val proximity = DevicePresenceScanner.classifyRssi(highRssi)
        assertEquals(PresenceConfidence.HIGH_CONFIDENCE, proximity)

        val lowBattery = 15
        val powerProfile = BatteryBudgetManager.determineProfile(lowBattery)
        assertEquals(PowerProfile.SAVER, powerProfile)

        val criticalBattery = 5
        val criticalProfile = BatteryBudgetManager.determineProfile(criticalBattery)
        assertEquals(PowerProfile.CRITICAL, criticalProfile)

        // --- STAGE 6: Replay Engine & Safe Mode Isolation ---
        SafeModeManager.setSafeMode(true)
        assertTrue("Safe Mode must be active", SafeModeManager.isSafeModeActive)
        assertFalse("Plan mutations must be blocked under Safe Mode", SafeModeManager.canMutatePlans())
        assertTrue("Ghost mode routing must be forced under Safe Mode", DecisionGate.isGhostRoutingRequired(0.99f))
        SafeModeManager.setSafeMode(false)

        // --- STAGE 7: Privacy Firewall & Data Minimization ---
        // Verify firewall channel isolation
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, false)
        assertFalse("BLE scanning must be blocked by firewall", PermissionFirewall.isSourceAuthorized(SourceType.BLE_SCANNING))
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        assertTrue("BLE scanning re-authorized", PermissionFirewall.isSourceAuthorized(SourceType.BLE_SCANNING))

        // Verify PII minimization before storage
        val rawWithPii = "Hi Dr. Sharma, call 9876543210 or email sharma@college.edu to collect DBMS Record."
        val sanitizedPayload = DataMinimizer.sanitizeRawPayload(rawWithPii, extractedItem = "DBMS Record", extractedAction = "Collect")

        assertFalse("Phone number must not leak into database payload", sanitizedPayload.contains("9876543210"))
        assertFalse("Email must not leak into database payload", sanitizedPayload.contains("sharma@college.edu"))
        assertFalse("Personal greeting must not leak into database payload", sanitizedPayload.contains("Hi Dr. Sharma"))
        assertTrue("Payload should have [PHONE REDACTED]", sanitizedPayload.contains("[PHONE REDACTED]"))
        assertTrue("Payload should have [EMAIL REDACTED]", sanitizedPayload.contains("[EMAIL REDACTED]"))
        assertTrue("Payload should have [GREETING REDACTED]", sanitizedPayload.contains("[GREETING REDACTED]"))

        // Cleanup temporary items
        val tempPurged = DataMinimizer.clearTemporaryItems(fakeDao)
        assertEquals(0, tempPurged) // None were temporary in this flow
    }
}
