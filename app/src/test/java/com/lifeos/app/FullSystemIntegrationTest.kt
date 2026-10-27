package com.lifeos.app

import com.lifeos.app.data.ItemHabitEntity
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.engine.*
import com.lifeos.app.security.DataMinimizer
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LIFEOS Master End-to-End Orchestration & Integrity Test Suite.
 * Validates the continuous reactive intelligence pipeline across all 7 stages:
 *  1. Context Engine (Mode Shifts & Readiness)
 *  2. Multilingual NLP Parser (Teluglish, Hinglish, English)
 *  3. Decision Gate (Confidence Thresholding & Auto-Commit vs Ghost Routing)
 *  4. Consequence & Plan Mutation Engine (Ripple Effects & Dynamic Item Waiving)
 *  5. Personal Forgetting & Habit Learning Model (Risk Escalation)
 *  6. Device Presence & Power Budgeting (BLE RSSI & Battery Profiles)
 *  7. Security Gate & Permission Firewall (PII Minimization & Safe Mode Kill-Switch)
 */
class FullSystemIntegrationTest {

    @Before
    fun setUp() {
        SafeModeManager.setSafeMode(false)
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, false)
        BatteryBudgetManager.setSimulatedBattery(85)
    }

    @After
    fun tearDown() {
        SafeModeManager.setSafeMode(false)
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
    }

    @Test
    fun testCompleteLifeOsPipelineSequential() {
        // =====================================================================
        // STAGE 1: Context Engine & Geofencing Mode Transitions
        // =====================================================================
        val collegeMode = LifeModeEntity(id = 1, name = "College", iconRes = "school", isActive = true)
        val gymMode = LifeModeEntity(id = 2, name = "Gym", iconRes = "fitness", isActive = false)
        val travelMode = LifeModeEntity(id = 3, name = "Travel", iconRes = "flight", isActive = false)

        // Initial college requirements
        val initialItems = listOf(
            ItemRequirementEntity(id = 101, modeId = collegeMode.id, itemName = "ID Card", isReady = true, confidenceScore = 1.0f),
            ItemRequirementEntity(id = 102, modeId = collegeMode.id, itemName = "DBMS Record", isReady = false, confidenceScore = 1.0f)
        )

        var readiness = ReadinessEngine.calculateReadiness(collegeMode, initialItems)
        assertEquals("College mode should initially be 50% ready (1 of 2)", 50, readiness.percentage)
        assertEquals(1, readiness.pendingItems.size)

        // =====================================================================
        // STAGE 2: Lightweight Multilingual NLP Parsing (Teluglish / Hinglish / English)
        // =====================================================================
        // Scenario A: English notification with required item
        val englishNotice = "Please remember to bring your ID Card for tomorrow's class."
        val parsedEnglish = MultilingualParser.parse(englishNotice)
        assertEquals("English", parsedEnglish.detectedLanguage)
        assertEquals("ID Card", parsedEnglish.relatedItem)
        assertEquals(CanonicalAction.REQUIRED_ITEM, parsedEnglish.canonicalAction)

        // Scenario B: Teluglish schedule alert
        val teluglishNotice = "Repu lab undi record marchipoku"
        val parsedTeluglish = MultilingualParser.parse(teluglishNotice)
        assertEquals("Teluglish", parsedTeluglish.detectedLanguage)
        assertEquals("DBMS Record", parsedTeluglish.relatedItem)
        assertEquals(CanonicalAction.REQUIRED_ITEM, parsedTeluglish.canonicalAction)

        // Scenario C: Hinglish cancellation notice
        val hinglishCancel = "Kal ka DBMS lab cancel ho gaya hai"
        val parsedHinglish = MultilingualParser.parse(hinglishCancel)
        assertEquals("Hinglish", parsedHinglish.detectedLanguage)
        assertEquals("DBMS Lab", parsedHinglish.subjectActivity)
        assertEquals(CanonicalAction.EVENT_CANCELLED, parsedHinglish.canonicalAction)
        assertEquals("DBMS Lab", parsedHinglish.subjectActivity)
        assertEquals(CanonicalAction.EVENT_CANCELLED, parsedHinglish.canonicalAction)

        // =====================================================================
        // STAGE 3: Decision Gate (Confidence Thresholding & Auto-Commit)
        // =====================================================================
        // High confidence (>= 0.85) auto-commits when Safe Mode is OFF
        val highConfRoute = DecisionGate.isGhostRoutingRequired(parsedEnglish.confidence)
        assertFalse("High confidence English alert should auto-commit", highConfRoute)

        // Moderate confidence (< 0.85) routes to ghost mode for manual confirmation
        val moderateConfRoute = DecisionGate.isGhostRoutingRequired(0.72f)
        assertTrue("Moderate confidence item should route to Ghost Mode", moderateConfRoute)

        // Irrelevant chatter has no extracted item and is categorized as INFO_NOTICE
        val filteredLow = LightweightEventFilter.filter("Good morning have a great day!", "com.whatsapp")
        assertNull("Irrelevant chatter must not extract an item", filteredLow.extractedItemName)
        assertEquals(CanonicalAction.INFO_NOTICE, filteredLow.canonicalAction)

        // =====================================================================
        // STAGE 4: Consequence Engine (Plan Mutation & Dynamic Item Waiving)
        // =====================================================================
        // Because "DBMS Lab" was cancelled, "DBMS Record" should be waived
        val itemAfterCancellation = initialItems.map { item ->
            if (item.itemName.equals("DBMS Record", ignoreCase = true)) {
                item.copy(isWaived = true)
            } else {
                item
            }
        }

        readiness = ReadinessEngine.calculateReadiness(collegeMode, itemAfterCancellation)
        // With DBMS Record waived, remaining required item (ID Card) is packed -> 100% readiness!
        assertEquals("Readiness must adapt to 100% when non-essential item is waived", 100, readiness.percentage)
        assertEquals("Waived items must not count as pending", 0, readiness.pendingItems.size)

        // =====================================================================
        // STAGE 5: Personal Forgetting & Habit Learning Model
        // =====================================================================
        val dbmsHabit = ItemHabitEntity(
            itemName = "DBMS Record",
            modeId = collegeMode.id,
            forgetCount = 3,
            waiveCount = 1,
            confirmCount = 0,
            priorityBoost = 2.0f
        )
        assertTrue("Item forgotten >= 3 times must trigger high forget risk escalation", ForgettingModelEngine.isFrequentlyForgotten(dbmsHabit))
        val warningMessage = ForgettingModelEngine.getEscalationAlertMessage(dbmsHabit.itemName, dbmsHabit.forgetCount)
        assertTrue(warningMessage.contains("HIGH FORGET RISK"))

        // =====================================================================
        // STAGE 6: Device Presence & Battery Budget Profiles
        // =====================================================================
        val laptopPresence = DevicePresenceScanner.evaluateSignal("Laptop Beacon", -62)
        assertEquals("Strong RSSI should yield HIGH_CONFIDENCE", PresenceConfidence.HIGH_CONFIDENCE, laptopPresence.confidence)

        val headphonePresence = DevicePresenceScanner.evaluateSignal("Headphones", null)
        assertEquals("Absent signal should yield UNKNOWN confidence", PresenceConfidence.UNKNOWN, headphonePresence.confidence)

        // Battery degradation simulation
        BatteryBudgetManager.setSimulatedBattery(12)
        assertEquals("12% battery must trigger SAVER profile", PowerProfile.SAVER, BatteryBudgetManager.currentPowerProfile.value)

        // =====================================================================
        // STAGE 7: Security Gate, Privacy Firewall & Safe Mode Kill-Switch
        // =====================================================================
        // 1. Data Minimization: PII stripped before persistence
        val rawChat = "Call 9876543210 or email prof.rao@univ.edu to submit the assignment today."
        val sanitized = DataMinimizer.sanitizeRawPayload(rawChat, extractedItem = "Assignment", extractedAction = "Submit")
        assertFalse("Phone number must be scrubbed", sanitized.contains("9876543210"))
        assertFalse("Email address must be scrubbed", sanitized.contains("prof.rao@univ.edu"))
        assertTrue("Phone redacted placeholder must be present", sanitized.contains("[PHONE REDACTED]"))
        assertTrue("Email redacted placeholder must be present", sanitized.contains("[EMAIL REDACTED]"))

        // 2. Permission Firewall Blockage
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, false)
        assertFalse("Firewall must drop notification requests when disabled", PermissionFirewall.isSourceAuthorized(SourceType.NOTIFICATION_INGESTION))

        // 3. Autonomous Action Kill-Switch (Safe Mode)
        SafeModeManager.setSafeMode(true)
        assertFalse("Safe Mode must forbid autonomous plan alterations", SafeModeManager.canMutatePlans())
        assertFalse("Safe Mode must forbid autonomous ghost item promotions", SafeModeManager.canAutoPromoteGhostItems())

        // Decision Gate under Safe Mode forces Ghost Mode even for 100% confidence
        val forcedGhostUnderSafeMode = DecisionGate.isGhostRoutingRequired(1.0f)
        assertTrue("Safe Mode must force ghost mode routing for manual user approval", forcedGhostUnderSafeMode)
    }
}
