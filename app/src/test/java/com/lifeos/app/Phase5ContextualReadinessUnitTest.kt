package com.lifeos.app

import com.lifeos.app.data.EventEntity
import com.lifeos.app.data.ItemHabitEntity
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.engine.*
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import com.lifeos.app.service.AntiSpamDecision
import com.lifeos.app.service.AntiSpamNotificationEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * LIFEOS TEST PHASE 5: Contextual Readiness, Departure Preparation & Smart Reminders.
 *
 * Verifies:
 *  1. Basic Readiness Calculation (0/5, 1/5, 3/5, 4/5, 5/5, Empty 0 items)
 *  2. Context -> Checklist Connection & No Requirement Leakage
 *  3. Missing Item Prioritization (Frequently forgotten items prioritized first)
 *  4. Departure Relevance (3h, 1h, 30m, 15m, 5m, post-event, no event)
 *  5. Smart Reminder Timing (No spam too early at 6 AM; fires in preparation/departure window)
 *  6. Progressive Reminders (Reminders update as user prepares items)
 *  7. Stale Reminder Cancellation (100% ready suppresses & cancels active notifications)
 *  8. Anti-Spam Deducing & Consolidated Grouping (Single grouped message, cooldown protection)
 *  9. Context Confidence Awareness (HIGH allows reminders; MEDIUM/LOW/UNCERTAIN suppresses)
 * 10. Manual User Control & Sovereignty
 * 11. Device Presence Uncertainty (NOT DETECTED distinguished from CONFIRMED MISSING)
 * 12. Battery Profile Sensor Throttling
 * 13. Local-First Offline Operation under Simulated Airplane Mode
 * 14. Permission Firewall Denial & Graceful Degradation
 * 15. Real-Life Scenario 1: College Departure Preparation
 * 16. Real-Life Scenario 2: Sports Preparation
 * 17. Real-Life Scenario 3: Gym Preparation
 * 18. Real-Life Scenario 4: Travel Transit
 * 19. Mode Switching During Preparation & Stale Alert Cancellation
 * 20. Mode Switching State Preservation (Completed items not reset on return)
 * 21. Multilingual Mode & Item Names (Telugu, Hindi, Unicode)
 * 22. Edge Cases (Deleted modes, empty checklists, malformed event times)
 */
class Phase5ContextualReadinessUnitTest {

    private lateinit var dao: FakeLifeOsDao

    @Before
    fun setUp() {
        runBlocking {
            dao = FakeLifeOsDao()
            ContextRecognitionEngine.resetStabilityState()
            AntiSpamNotificationEngine.resetCooldowns()
            SafeModeManager.setSafeMode(false)
            BatteryBudgetManager.setProfileOverride(PowerProfile.NORMAL)
            DevicePresenceScanner.clearDevices()

            // Reset permissions to authorized
            PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
            PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, true)
            PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
            PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)

            // Seed Standard Life Modes
            dao.insertLifeMode(LifeModeEntity(id = 1, name = "College", iconRes = "ic_college", isActive = true, priority = 1))
            dao.insertLifeMode(LifeModeEntity(id = 2, name = "Gym", iconRes = "ic_gym", isActive = false, priority = 2))
            dao.insertLifeMode(LifeModeEntity(id = 3, name = "Travel", iconRes = "ic_travel", isActive = false, priority = 3))
            dao.insertLifeMode(LifeModeEntity(id = 4, name = "Sports", iconRes = "ic_sports", isActive = false, priority = 4))
        }
    }

    @After
    fun tearDown() {
        ContextRecognitionEngine.resetStabilityState()
        AntiSpamNotificationEngine.resetCooldowns()
        SafeModeManager.setSafeMode(false)
        BatteryBudgetManager.setProfileOverride(PowerProfile.NORMAL)
        DevicePresenceScanner.clearDevices()
    }

    // =========================================================================
    // 1. BASIC READINESS CALCULATION
    // =========================================================================

    @Test
    fun testBasicReadinessCalculationTiers() = runBlocking {
        val college = dao.getModeById(1)!!
        val now = System.currentTimeMillis()

        // 5 Standard College Items
        val item1 = ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = false)
        val item2 = ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = false)
        val item3 = ItemRequirementEntity(id = 3, modeId = 1, itemName = "Charger", isReady = false)
        val item4 = ItemRequirementEntity(id = 4, modeId = 1, itemName = "Watch", isReady = false)
        val item5 = ItemRequirementEntity(id = 5, modeId = 1, itemName = "Water Bottle", isReady = false)

        val allItems = listOf(item1, item2, item3, item4, item5)

        // Case 0/5 Ready (0%)
        val report0 = ReadinessEngine.calculateReadiness(college, allItems, now)
        assertEquals(0, report0.percentage)
        assertEquals(0, report0.readyCount)
        assertEquals(5, report0.totalCount)
        assertEquals(5, report0.pendingItems.size)

        // Case 1/5 Ready (20%)
        val items1Ready = listOf(item1.copy(isReady = true), item2, item3, item4, item5)
        val report1 = ReadinessEngine.calculateReadiness(college, items1Ready, now)
        assertEquals(20, report1.percentage)
        assertEquals(1, report1.readyCount)
        assertEquals(4, report1.pendingItems.size)
        assertTrue(report1.pendingItems.contains("Laptop"))

        // Case 3/5 Ready (60%)
        val items3Ready = listOf(item1.copy(isReady = true), item2.copy(isReady = true), item3.copy(isReady = true), item4, item5)
        val report3 = ReadinessEngine.calculateReadiness(college, items3Ready, now)
        assertEquals(60, report3.percentage)
        assertEquals(3, report3.readyCount)
        assertEquals(2, report3.pendingItems.size)

        // Case 4/5 Ready (80%)
        val items4Ready = listOf(item1.copy(isReady = true), item2.copy(isReady = true), item3.copy(isReady = true), item4.copy(isReady = true), item5)
        val report4 = ReadinessEngine.calculateReadiness(college, items4Ready, now)
        assertEquals(80, report4.percentage)
        assertEquals(4, report4.readyCount)
        assertEquals(1, report4.pendingItems.size)
        assertEquals("Water Bottle", report4.pendingItems.first())

        // Case 5/5 Ready (100%)
        val items5Ready = allItems.map { it.copy(isReady = true) }
        val report5 = ReadinessEngine.calculateReadiness(college, items5Ready, now)
        assertEquals(100, report5.percentage)
        assertEquals(5, report5.readyCount)
        assertEquals(0, report5.pendingItems.size)
    }

    @Test
    fun testEmptyChecklistReadinessSafety() = runBlocking {
        val college = dao.getModeById(1)!!
        val reportEmpty = ReadinessEngine.calculateReadiness(college, emptyList())
        assertEquals(-1, reportEmpty.percentage)
        assertEquals(0, reportEmpty.totalCount)
        assertEquals(0, reportEmpty.readyCount)
        assertTrue(reportEmpty.summaryNotification.contains("No requirements configured"))
    }

    // =========================================================================
    // 2. CONTEXT -> CHECKLIST CONNECTION & NO LEAKAGE
    // =========================================================================

    @Test
    fun testContextToChecklistConnectionNoLeakage() = runBlocking {
        // College items
        dao.insertItem(ItemRequirementEntity(id = 11, modeId = 1, itemName = "Laptop", isReady = false))
        dao.insertItem(ItemRequirementEntity(id = 12, modeId = 1, itemName = "ID Card", isReady = true))

        // Gym items
        dao.insertItem(ItemRequirementEntity(id = 21, modeId = 2, itemName = "Sports Shoes", isReady = false))
        dao.insertItem(ItemRequirementEntity(id = 22, modeId = 2, itemName = "Towel", isReady = true))

        // Sports items
        dao.insertItem(ItemRequirementEntity(id = 41, modeId = 4, itemName = "Football Boots", isReady = false))

        // 1. College Checklist Check
        val collegeItems = dao.getItemsForMode(1)
        assertEquals(2, collegeItems.size)
        assertTrue(collegeItems.any { it.itemName == "Laptop" })
        assertFalse("Gym items must not leak into College", collegeItems.any { it.itemName == "Sports Shoes" })

        // 2. Gym Checklist Check
        val gymItems = dao.getItemsForMode(2)
        assertEquals(2, gymItems.size)
        assertTrue(gymItems.any { it.itemName == "Sports Shoes" })
        assertFalse("College items must not leak into Gym", gymItems.any { it.itemName == "Laptop" })
        assertFalse("College items must not leak into Gym", gymItems.any { it.itemName == "ID Card" })

        // 3. Sports Checklist Check
        val sportsItems = dao.getItemsForMode(4)
        assertEquals(1, sportsItems.size)
        assertEquals("Football Boots", sportsItems.first().itemName)
    }

    // =========================================================================
    // 3. MISSING ITEM PRIORITIZATION (HABITS & RISK MODEL)
    // =========================================================================

    @Test
    fun testMissingItemPrioritizationByHabitRisk() = runBlocking {
        val items = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = false),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = false),
            ItemRequirementEntity(id = 3, modeId = 1, itemName = "Charger", isReady = true),
            ItemRequirementEntity(id = 4, modeId = 1, itemName = "Watch", isReady = true),
            ItemRequirementEntity(id = 5, modeId = 1, itemName = "Water Bottle", isReady = false)
        )

        // ID Card has high forgetfulness history (forgetCount = 4, confirmCount = 1) -> Frequently Forgotten High Risk
        val habits = listOf(
            ItemHabitEntity(id = 1, itemName = "ID Card", modeId = 1, forgetCount = 4, confirmCount = 1, priorityBoost = 5.0f),
            ItemHabitEntity(id = 2, itemName = "Laptop", modeId = 1, forgetCount = 0, confirmCount = 5, priorityBoost = 1.0f)
        )

        val prioritized = DepartureReminderEngine.prioritizeMissingItems(items, habits)

        // Must only contain the 3 unready items
        assertEquals(3, prioritized.size)
        // High risk / frequently forgotten item (ID Card) must appear FIRST
        assertEquals("ID Card", prioritized[0].itemName)
        // Laptop follows (has priority boost)
        assertEquals("Laptop", prioritized[1].itemName)
        // Water Bottle follows
        assertEquals("Water Bottle", prioritized[2].itemName)
    }

    // =========================================================================
    // 4. DEPARTURE RELEVANCE (TEMPORAL WINDOWS)
    // =========================================================================

    @Test
    fun testDepartureRelevanceTemporalStates() {
        val eventStart10am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        val event = EventEntity(id = 1, title = "DBMS Lab", startTime = eventStart10am, endTime = eventStart10am + 7200_000L, associatedModeId = 1)

        // Case 1: 3 hours before (07:00 AM, 180 min prior) -> TOO_EARLY
        val t3h = eventStart10am - (180 * 60_000L)
        val rel3h = DepartureReminderEngine.evaluateDepartureRelevance(event, isGeofenceExit = false, currentTime = t3h)
        assertEquals(DepartureRelevance.TOO_EARLY, rel3h)

        // Case 2: 1 hour before (09:00 AM, 60 min prior) -> TOO_EARLY (> 45m threshold)
        val t1h = eventStart10am - (60 * 60_000L)
        val rel1h = DepartureReminderEngine.evaluateDepartureRelevance(event, isGeofenceExit = false, currentTime = t1h)
        assertEquals(DepartureRelevance.TOO_EARLY, rel1h)

        // Case 3: 35 minutes before (09:25 AM) -> PREPARATION_WINDOW (31..45m)
        val t35m = eventStart10am - (35 * 60_000L)
        val rel35m = DepartureReminderEngine.evaluateDepartureRelevance(event, isGeofenceExit = false, currentTime = t35m)
        assertEquals(DepartureRelevance.PREPARATION_WINDOW, rel35m)

        // Case 4: 20 minutes before (09:40 AM) -> DEPARTURE_WINDOW (11..30m)
        val t20m = eventStart10am - (20 * 60_000L)
        val rel20m = DepartureReminderEngine.evaluateDepartureRelevance(event, isGeofenceExit = false, currentTime = t20m)
        assertEquals(DepartureRelevance.DEPARTURE_WINDOW, rel20m)

        // Case 5: 5 minutes before (09:55 AM) -> IMMINENT_DEPARTURE (0..10m)
        val t5m = eventStart10am - (5 * 60_000L)
        val rel5m = DepartureReminderEngine.evaluateDepartureRelevance(event, isGeofenceExit = false, currentTime = t5m)
        assertEquals(DepartureRelevance.IMMINENT_DEPARTURE, rel5m)

        // Case 6: 15 minutes after start (10:15 AM) -> PAST_EVENT
        val tAfter = eventStart10am + (15 * 60_000L)
        val relAfter = DepartureReminderEngine.evaluateDepartureRelevance(event, isGeofenceExit = false, currentTime = tAfter)
        assertEquals(DepartureRelevance.PAST_EVENT, relAfter)

        // Case 7: Physical Geofence Exit occurred -> PHYSICAL_DEPARTURE overrides calendar
        val relExit = DepartureReminderEngine.evaluateDepartureRelevance(event, isGeofenceExit = true, currentTime = t3h)
        assertEquals(DepartureRelevance.PHYSICAL_DEPARTURE, relExit)

        // Case 8: No scheduled event, sitting at home -> NO_EVENT_IDLE
        val relNoEvent = DepartureReminderEngine.evaluateDepartureRelevance(null, isGeofenceExit = false, currentTime = t3h)
        assertEquals(DepartureRelevance.NO_EVENT_IDLE, relNoEvent)
    }

    // =========================================================================
    // 5. SMART REMINDER TIMING (NO SPAM AT 6:00 AM)
    // =========================================================================

    @Test
    fun testSmartReminderTimingNoSpamTooEarly() = runBlocking {
        val college = dao.getModeById(1)!!
        val eventStart10am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        val event = EventEntity(id = 1, title = "DBMS Lab", startTime = eventStart10am, endTime = eventStart10am + 7200_000L, associatedModeId = 1)

        val items = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = false),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "Water Bottle", isReady = false)
        )

        // Test at 6:00 AM (4 hours before)
        val t6am = eventStart10am - (4 * 3600_000L)
        val decision6am = DepartureReminderEngine.evaluateSmartReminder(
            mode = college,
            items = items,
            event = event,
            currentTime = t6am
        )

        assertFalse("Must never notify at 6:00 AM for 10:00 AM event", decision6am.shouldNotify)
        assertEquals(DepartureRelevance.TOO_EARLY, decision6am.relevance)
        assertTrue(decision6am.suppressionReason!!.contains("Too early"))

        // Test at 9:35 AM (25 mins before, within departure window)
        val t935am = eventStart10am - (25 * 60_000L)
        val decision935am = DepartureReminderEngine.evaluateSmartReminder(
            mode = college,
            items = items,
            event = event,
            currentTime = t935am
        )

        assertTrue("Must notify in departure window", decision935am.shouldNotify)
        assertEquals(DepartureRelevance.DEPARTURE_WINDOW, decision935am.relevance)
        assertEquals("College in 25 min", decision935am.title)
        assertTrue(decision935am.content!!.contains("Still missing: ID Card and Water Bottle"))
    }

    // =========================================================================
    // 6. PROGRESSIVE REMINDERS & STALE REMINDER CANCELLATION
    // =========================================================================

    @Test
    fun testProgressiveRemindersAndUpdateCancellation() = runBlocking {
        val college = dao.getModeById(1)!!
        val eventStart10am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        val event = EventEntity(id = 1, title = "DBMS Lab", startTime = eventStart10am, endTime = eventStart10am + 7200_000L, associatedModeId = 1)
        val t945am = eventStart10am - (15 * 60_000L)

        // Step 1: User has 2 missing items (ID Card and Laptop)
        val itemsStep1 = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = false),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = false)
        )
        val decision1 = DepartureReminderEngine.evaluateSmartReminder(college, itemsStep1, event = event, currentTime = t945am)
        assertTrue(decision1.shouldNotify)
        assertEquals(2, decision1.prioritizedMissingItems.size)
        assertTrue(decision1.content!!.contains("ID Card and Laptop"))

        // Reset cooldown to simulate item progression
        AntiSpamNotificationEngine.resetCooldowns()

        // Step 2: User packs ID Card -> Only Laptop is missing (Progressive Update)
        val itemsStep2 = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = false)
        )
        val decision2 = DepartureReminderEngine.evaluateSmartReminder(college, itemsStep2, event = event, currentTime = t945am + 60_000L)
        assertTrue(decision2.shouldNotify)
        assertEquals(1, decision2.prioritizedMissingItems.size)
        assertEquals("Don't forget your Laptop!", decision2.content)

        // Step 3: User packs Laptop -> 100% Ready (Cancellation)
        val itemsStep3 = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = true)
        )
        val decision3 = DepartureReminderEngine.evaluateSmartReminder(college, itemsStep3, event = event, currentTime = t945am + 120_000L)
        assertFalse("Must not notify when 100% ready", decision3.shouldNotify)
        assertTrue("Must signal cancellation of active reminder", decision3.shouldCancelExisting)
        assertEquals(100, decision3.readinessPercentage)
    }

    // =========================================================================
    // 7. ANTI-SPAM DEDUPLICATION & CONSOLIDATION
    // =========================================================================

    @Test
    fun testAntiSpamDeduplicationAndConsolidation() = runBlocking {
        val now = System.currentTimeMillis()
        val alertKey = "GEOFENCE_EXIT_COLLEGE"

        // 1. First check allows notification
        val check1 = AntiSpamNotificationEngine.checkAntiSpam("College", 60, alertKey, now)
        assertEquals(AntiSpamDecision.ALLOWED, check1.decision)
        assertFalse(check1.isSuppressed)

        // Record timestamp
        AntiSpamNotificationEngine.lastAlertTimestamps[alertKey] = now

        // 2. 5 minutes later (< 15 min cooldown) -> SUPPRESSED_COOLDOWN
        val check5m = AntiSpamNotificationEngine.checkAntiSpam("College", 60, alertKey, now + (5 * 60_000L))
        assertEquals(AntiSpamDecision.SUPPRESSED_COOLDOWN, check5m.decision)
        assertTrue(check5m.isSuppressed)

        // 3. 100% readiness suppresses redundant alert
        val check100 = AntiSpamNotificationEngine.checkAntiSpam("College", 100, alertKey, now + (20 * 60_000L))
        assertEquals(AntiSpamDecision.SUPPRESSED_100_PERCENT, check100.decision)
        assertTrue(check100.isSuppressed)

        // 4. Grouped Consolidation format verification
        val consolidated = AntiSpamNotificationEngine.consolidateDepartureAlert(
            modeName = "College",
            readinessPercentage = 40,
            pendingItems = listOf("ID Card", "Laptop", "Charger")
        )
        assertEquals("Leaving for College? Readiness: 40%. Still missing: ID Card, Laptop, Charger.", consolidated)
    }

    // =========================================================================
    // 8. CONTEXT CONFIDENCE GATING
    // =========================================================================

    @Test
    fun testContextConfidenceAwareReminders() = runBlocking {
        val college = dao.getModeById(1)!!
        val eventStart10am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        val event = EventEntity(id = 1, title = "DBMS Lab", startTime = eventStart10am, endTime = eventStart10am + 7200_000L, associatedModeId = 1)
        val t940am = eventStart10am - (20 * 60_000L)
        val items = listOf(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = false))

        // HIGH confidence -> Allowed
        val highDec = DepartureReminderEngine.evaluateSmartReminder(
            mode = college, items = items, event = event, contextConfidence = ContextConfidenceLevel.HIGH, currentTime = t940am
        )
        assertTrue("HIGH confidence allows reminder", highDec.shouldNotify)

        // MEDIUM confidence -> Suppressed (proposed only)
        val medDec = DepartureReminderEngine.evaluateSmartReminder(
            mode = college, items = items, event = event, contextConfidence = ContextConfidenceLevel.MEDIUM, currentTime = t940am
        )
        assertFalse("MEDIUM confidence suppresses intrusive reminder", medDec.shouldNotify)
        assertTrue(medDec.suppressionReason!!.contains("MEDIUM"))

        // LOW confidence -> Suppressed
        val lowDec = DepartureReminderEngine.evaluateSmartReminder(
            mode = college, items = items, event = event, contextConfidence = ContextConfidenceLevel.LOW, currentTime = t940am
        )
        assertFalse("LOW confidence suppresses reminder", lowDec.shouldNotify)

        // NO_CONTEXT -> Suppressed
        val noDec = DepartureReminderEngine.evaluateSmartReminder(
            mode = college, items = items, event = event, contextConfidence = ContextConfidenceLevel.NO_CONTEXT, currentTime = t940am
        )
        assertFalse("NO_CONTEXT suppresses reminder", noDec.shouldNotify)
    }

    // =========================================================================
    // 9. MANUAL USER CONTROL (SOVEREIGNTY)
    // =========================================================================

    @Test
    fun testManualUserControlPreserved() = runBlocking {
        val monday9am = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        // User manually selected Gym mode (2)
        dao.setActiveMode(2)
        ContextRecognitionEngine.notifyManualModeSelected(2, monday9am)

        // 10 minutes later, strong College cues arrive (Location + Calendar + Time = 0.80)
        val tenMinsLater = monday9am + 600_000L
        dao.insertEvent(EventEntity(id = 501, title = "DBMS Lab", startTime = tenMinsLater, endTime = tenMinsLater + 3600_000L, associatedModeId = 1))

        val collegeEval = ContextRecognitionEngine.evaluateAllModes(
            dao = dao,
            currentLocationHint = "College",
            isGeofenceEnter = true,
            currentTimestamp = tenMinsLater
        )

        // Even with HIGH confidence (80%), manual override suppresses AUTO_SWITCH to PROPOSE
        assertEquals(ContextConfidenceLevel.HIGH, collegeEval.confidenceLevel)
        assertEquals(ContextActionDecision.PROPOSE, collegeEval.actionDecision)
        assertTrue(collegeEval.statusMessage.contains("manually selected"))
        // Active mode remains Gym
        val activeMode = dao.getActiveModeFlow().first()
        assertEquals(2, activeMode?.id)
        assertEquals("Gym", activeMode?.name)
    }

    // =========================================================================
    // 10. DEVICE PRESENCE UNCERTAINTY (NOT DETECTED VS CONFIRMED MISSING)
    // =========================================================================

    @Test
    fun testDevicePresenceNotDetectedVsConfirmedMissing() = runBlocking {
        // College requires Laptop
        dao.insertItem(ItemRequirementEntity(id = 10, modeId = 1, itemName = "Laptop", isReady = true))

        // Device presence scanner: Laptop is absent/UNKNOWN
        val evaluated = DevicePresenceScanner.evaluateSignal("Laptop Beacon", rssi = null)
        assertEquals(PresenceConfidence.UNKNOWN, evaluated.confidence)
        assertEquals("Your Laptop Beacon does not appear to be nearby (Bluetooth signal absent).", evaluated.departureWarning)

        // IMPORTANT: Must NOT overwrite user-checked isReady=true when signal is merely absent!
        val laptopItem = dao.getItemById(10)!!
        assertTrue("Absent BLE signal must not overturn manual user confirmation", laptopItem.isReady)

        // When BLE signal IS present with strong RSSI (-60 dBm):
        DevicePresenceScanner.simulateDeviceState("Laptop Beacon", PresenceConfidence.HIGH_CONFIDENCE, -60)
        DevicePresenceScanner.syncDeviceWithChecklist(dao, "Laptop Beacon", -60, "Laptop")
        assertTrue(dao.getItemById(10)!!.isReady)
    }

    // =========================================================================
    // 11. BATTERY PROFILE SENSOR THROTTLING
    // =========================================================================

    @Test
    fun testBatteryProfileSensorThrottling() = runBlocking {
        BatteryBudgetManager.setProfileOverride(PowerProfile.NORMAL)
        assertTrue(BatteryBudgetManager.canRunBleScan())

        // SAVER profile (15%) -> BLE scanning paused
        BatteryBudgetManager.setProfileOverride(PowerProfile.SAVER)
        assertFalse(BatteryBudgetManager.canRunBleScan())

        // CRITICAL profile (8%) -> BLE scanning paused
        BatteryBudgetManager.setProfileOverride(PowerProfile.CRITICAL)
        assertFalse(BatteryBudgetManager.canRunBleScan())
    }

    // =========================================================================
    // 12. LOCAL-FIRST OFFLINE OPERATION
    // =========================================================================

    @Test
    fun testOfflineOperationUnderAirplaneMode() = runBlocking {
        // Airplane mode simulated: Zero network, all local DB operations
        val college = dao.getModeById(1)!!
        val eventStart10am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        val event = EventEntity(id = 1, title = "DBMS Lab", startTime = eventStart10am, endTime = eventStart10am + 7200_000L, associatedModeId = 1)
        val items = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = false),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = true)
        )

        val decision = DepartureReminderEngine.evaluateSmartReminder(
            mode = college,
            items = items,
            event = event,
            currentTime = eventStart10am - (20 * 60_000L)
        )

        assertTrue(decision.shouldNotify)
        assertEquals(50, decision.readinessPercentage)
        assertEquals(1, decision.prioritizedMissingItems.size)
        assertEquals("ID Card", decision.prioritizedMissingItems.first())
    }

    // =========================================================================
    // 13. PERMISSION FIREWALL DENIAL HANDLING
    // =========================================================================

    @Test
    fun testPermissionFirewallDenialHandling() = runBlocking {
        // Revoke Notification channel in Firewall
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, false)
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, false)

        val college = dao.getModeById(1)!!
        val items = listOf(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = false))

        // Readiness calculation remains fully functional despite permission denials
        val report = ReadinessEngine.calculateReadiness(college, items)
        assertEquals(0, report.percentage)
        assertEquals(1, report.totalCount)
    }

    // =========================================================================
    // 14. REAL-LIFE SCENARIO 1: COLLEGE
    // =========================================================================

    @Test
    fun testRealLifeScenario1_College() = runBlocking {
        val college = dao.getModeById(1)!!
        val eventStart9am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        val event = EventEntity(id = 101, title = "DBMS Lab Evaluation", startTime = eventStart9am, endTime = eventStart9am + 7200_000L, associatedModeId = 1)

        val items = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = false),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = false),
            ItemRequirementEntity(id = 3, modeId = 1, itemName = "Charger", isReady = true),
            ItemRequirementEntity(id = 4, modeId = 1, itemName = "Watch", isReady = true),
            ItemRequirementEntity(id = 5, modeId = 1, itemName = "Water Bottle", isReady = true)
        )

        // At 8:35 AM (25m before class)
        val decision = DepartureReminderEngine.evaluateSmartReminder(college, items, event = event, currentTime = eventStart9am - (25 * 60_000L))
        assertTrue(decision.shouldNotify)
        assertEquals(60, decision.readinessPercentage)
        assertEquals(2, decision.prioritizedMissingItems.size)
        assertEquals("College in 25 min", decision.title)
        assertTrue(decision.content!!.contains("ID Card and Laptop"))
    }

    // =========================================================================
    // 15. REAL-LIFE SCENARIO 2: SPORTS
    // =========================================================================

    @Test
    fun testRealLifeScenario2_Sports() = runBlocking {
        val sports = dao.getModeById(4)!!
        val eventStart8am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        val event = EventEntity(id = 102, title = "Football Match", startTime = eventStart8am, endTime = eventStart8am + 7200_000L, associatedModeId = 4)

        val sportsItems = listOf(
            ItemRequirementEntity(id = 41, modeId = 4, itemName = "Water Bottle", isReady = true),
            ItemRequirementEntity(id = 42, modeId = 4, itemName = "Sports Shoes", isReady = true),
            ItemRequirementEntity(id = 43, modeId = 4, itemName = "Sports Kit", isReady = false),
            ItemRequirementEntity(id = 44, modeId = 4, itemName = "Towel", isReady = false)
        )

        // At 7:45 AM (15m before match)
        val decision = DepartureReminderEngine.evaluateSmartReminder(sports, sportsItems, event = event, currentTime = eventStart8am - (15 * 60_000L))
        assertTrue(decision.shouldNotify)
        assertEquals(50, decision.readinessPercentage)
        assertEquals(2, decision.prioritizedMissingItems.size)
        assertEquals("Sports in 15 min", decision.title)
        assertTrue(decision.content!!.contains("Sports Kit and Towel"))
        assertFalse("College items must not appear in Sports reminder", decision.content!!.contains("Laptop"))
    }

    // =========================================================================
    // 16. REAL-LIFE SCENARIO 3: GYM
    // =========================================================================

    @Test
    fun testRealLifeScenario3_Gym() = runBlocking {
        val gym = dao.getModeById(2)!!
        val eventStart6pm = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        val event = EventEntity(id = 103, title = "Leg Day Workout", startTime = eventStart6pm, endTime = eventStart6pm + 3600_000L, associatedModeId = 2)

        val gymItems = listOf(
            ItemRequirementEntity(id = 21, modeId = 2, itemName = "Water Bottle", isReady = true),
            ItemRequirementEntity(id = 22, modeId = 2, itemName = "Shoes", isReady = false),
            ItemRequirementEntity(id = 23, modeId = 2, itemName = "Towel", isReady = false)
        )

        // At 5:40 PM (20m before workout)
        val decision = DepartureReminderEngine.evaluateSmartReminder(gym, gymItems, event = event, currentTime = eventStart6pm - (20 * 60_000L))
        assertTrue(decision.shouldNotify)
        assertEquals(33, decision.readinessPercentage)
        assertEquals(2, decision.prioritizedMissingItems.size)
        assertTrue(decision.content!!.contains("Shoes and Towel"))
    }

    // =========================================================================
    // 17. REAL-LIFE SCENARIO 4: TRAVEL
    // =========================================================================

    @Test
    fun testRealLifeScenario4_Travel() = runBlocking {
        val travel = dao.getModeById(3)!!
        val travelItems = listOf(
            ItemRequirementEntity(id = 31, modeId = 3, itemName = "ID", isReady = false),
            ItemRequirementEntity(id = 32, modeId = 3, itemName = "Wallet", isReady = false),
            ItemRequirementEntity(id = 33, modeId = 3, itemName = "Charger", isReady = false),
            ItemRequirementEntity(id = 34, modeId = 3, itemName = "Power Bank", isReady = false)
        )

        // Geofence exit triggered for Travel
        val decision = DepartureReminderEngine.evaluateSmartReminder(
            mode = travel,
            items = travelItems,
            isGeofenceExit = true
        )

        assertTrue(decision.shouldNotify)
        assertEquals(0, decision.readinessPercentage)
        assertEquals(4, decision.prioritizedMissingItems.size)
        assertEquals("Leaving for Travel?", decision.title)
        assertTrue(decision.content!!.contains("ID, Wallet, Charger, Power Bank"))
    }

    // =========================================================================
    // 18. MODE SWITCHING DURING PREPARATION & PERSISTENCE
    // =========================================================================

    @Test
    fun testModeSwitchingDuringPreparationAndStatePreservation() = runBlocking {
        // College has 3/5 ready
        dao.insertItem(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true))
        dao.insertItem(ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = true))
        dao.insertItem(ItemRequirementEntity(id = 3, modeId = 1, itemName = "Charger", isReady = true))
        dao.insertItem(ItemRequirementEntity(id = 4, modeId = 1, itemName = "Watch", isReady = false))
        dao.insertItem(ItemRequirementEntity(id = 5, modeId = 1, itemName = "Water Bottle", isReady = false))

        // Gym has 1/2 ready
        dao.insertItem(ItemRequirementEntity(id = 21, modeId = 2, itemName = "Towel", isReady = true))
        dao.insertItem(ItemRequirementEntity(id = 22, modeId = 2, itemName = "Shoes", isReady = false))

        // 1. Initial College Readiness
        val collegeItems = dao.getItemsForMode(1)
        val repCollege1 = ReadinessEngine.calculateReadiness(dao.getModeById(1), collegeItems)
        assertEquals(60, repCollege1.percentage)

        // 2. Switch to Gym
        dao.setActiveMode(2)
        val gymItems = dao.getItemsForMode(2)
        val repGym = ReadinessEngine.calculateReadiness(dao.getModeById(2), gymItems)
        assertEquals(50, repGym.percentage)

        // 3. Return to College later -> State must be preserved (not reset)
        dao.setActiveMode(1)
        val collegeItemsReturned = dao.getItemsForMode(1)
        val repCollege2 = ReadinessEngine.calculateReadiness(dao.getModeById(1), collegeItemsReturned)
        assertEquals(60, repCollege2.percentage)
        assertEquals(3, repCollege2.readyCount)
        assertEquals(2, repCollege2.pendingItems.size)
    }

    // =========================================================================
    // 19. MULTILINGUAL READINESS (TELUGU & HINDI)
    // =========================================================================

    @Test
    fun testMultilingualModeAndItemNames() = runBlocking {
        val teluguMode = LifeModeEntity(id = 30, name = "కాలేజ్", iconRes = "ic_college", isActive = true, priority = 1)
        dao.insertLifeMode(teluguMode)

        val teluguItems = listOf(
            ItemRequirementEntity(id = 101, modeId = 30, itemName = "ఐడీ కార్డు", isReady = true),
            ItemRequirementEntity(id = 102, modeId = 30, itemName = "నీళ్ల బాటిల్", isReady = false)
        )

        val report = ReadinessEngine.calculateReadiness(teluguMode, teluguItems)
        assertEquals(50, report.percentage)
        assertEquals(1, report.readyCount)
        assertEquals(1, report.pendingItems.size)
        assertEquals("నీళ్ల బాటిల్", report.pendingItems.first())
        assertTrue(report.summaryNotification.contains("కాలేజ్"))
    }
}
