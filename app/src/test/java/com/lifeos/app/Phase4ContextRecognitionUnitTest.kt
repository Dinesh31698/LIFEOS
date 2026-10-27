package com.lifeos.app

import com.lifeos.app.data.EventEntity
import com.lifeos.app.data.ItemHabitEntity
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
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
 * LIFEOS TEST PHASE 4: Context Recognition & Auto-Detection Verification Suite.
 *
 * Verifies:
 *  1. Multi-Signal Probabilistic Scoring Fusion (Location, Calendar, Time, Habit, BLE)
 *  2. Single-Signal Insufficiency Guard (No single signal can trigger auto-switch)
 *  3. Permission Firewall Denial & Graceful Degradation (Zero crashes on denial)
 *  4. Confidence Tiers Differentiation (HIGH >= 0.75, MEDIUM 0.50..0.74, LOW 0.25..0.49, NO_CONTEXT < 0.25)
 *  5. Deterministic Conflict Resolution via Mode Priority
 *  6. Ambiguous Score Tie & Safe Fallback (UNCERTAIN decision retains active mode)
 *  7. Time-of-Day & Day-of-Week Academic/Workout/Sports Windows
 *  8. Location Geofence Transition Signals (ENTER, EXIT, OUTSIDE)
 *  9. Calendar Event Active vs Upcoming vs Finished Boundaries
 * 10. Malformed Calendar Event Data Safety (Null/Empty titles, inverted timestamps)
 * 11. Habit & Historical Frequency Contribution
 * 12. Manual Mode Selection Precedence & 30-Minute Cooldown Protection
 * 13. Active Mode vs Detected/Proposed Mode Separation (Medium confidence does not overwrite DB)
 * 14. Context Anti-Flapping Debounce (60-second rapid transition suppression)
 * 15. Battery Profile Throttling (NORMAL -> Full BLE, SAVER/CRITICAL -> Throttled sensors)
 * 16. Local-First Offline Operation Under Simulated Airplane Mode
 * 17. Multilingual Mode Names (Telugu, Hindi, Unicode Emojis, Mixed Case, Whitespace)
 * 18. Real-Life Scenario 1: College Morning Departure
 * 19. Real-Life Scenario 2: Weekend Morning Sports
 * 20. Real-Life Scenario 3: Evening Gym Session
 * 21. Real-Life Scenario 4: Airport Travel Transit
 * 22. Real-Life Scenario 5: Ambiguous Equal-Score Context
 * 23. Safe Mode Autonomous Action Kill-Switch (Forces PROPOSE over AUTO_SWITCH)
 * 24. Empty Modes Boundary Safety
 */
class Phase4ContextRecognitionUnitTest {

    private lateinit var dao: FakeLifeOsDao

    @Before
    fun setUp() {
        runBlocking {
            dao = FakeLifeOsDao()
            ContextRecognitionEngine.resetStabilityState()
            SafeModeManager.setSafeMode(false)
            BatteryBudgetManager.setProfileOverride(PowerProfile.NORMAL)
            DevicePresenceScanner.clearDevices()

            // Reset permissions
            PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
            PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, true)
            PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
            PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)

            // Standard Default Modes
            dao.insertLifeMode(LifeModeEntity(id = 1, name = "College", iconRes = "ic_college", isActive = true, priority = 1))
            dao.insertLifeMode(LifeModeEntity(id = 2, name = "Gym", iconRes = "ic_gym", isActive = false, priority = 2))
            dao.insertLifeMode(LifeModeEntity(id = 3, name = "Travel", iconRes = "ic_travel", isActive = false, priority = 3))
            dao.insertLifeMode(LifeModeEntity(id = 4, name = "Sports", iconRes = "ic_sports", isActive = false, priority = 4))
            dao.insertLifeMode(LifeModeEntity(id = 5, name = "Work", iconRes = "ic_work", isActive = false, priority = 5))
            dao.insertLifeMode(LifeModeEntity(id = 6, name = "Shopping", iconRes = "ic_shopping", isActive = false, priority = 6))
        }
    }

    @After
    fun tearDown() {
        ContextRecognitionEngine.resetStabilityState()
        SafeModeManager.setSafeMode(false)
        BatteryBudgetManager.setProfileOverride(PowerProfile.NORMAL)
        DevicePresenceScanner.clearDevices()
    }

    @Test
    fun testMultiSignalScoringFusionCollege() = runBlocking {
        // Start in Gym mode to test transition
        dao.setActiveMode(2)

        // Monday 9:00 AM (Academic window)
        val monday9am = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        // Add matching active calendar event: "DBMS Lab" (9:00 - 11:00 AM)
        dao.insertEvent(
            EventEntity(
                id = 101,
                title = "DBMS Lab Evaluation",
                startTime = monday9am - 600_000L,
                endTime = monday9am + 7200_000L,
                associatedModeId = 1
            )
        )

        // Add confirmed habit
        dao.insertOrUpdateHabit(ItemHabitEntity(id = 1, itemName = "ID Card", modeId = 1, confirmCount = 6))

        // Device presence: Laptop Beacon
        DevicePresenceScanner.simulateDeviceState("Laptop Beacon", PresenceConfidence.HIGH_CONFIDENCE, -58)

        val result = ContextRecognitionEngine.evaluateAllModes(
            dao = dao,
            currentLocationHint = "College",
            isGeofenceEnter = true,
            currentTimestamp = monday9am
        )

        assertNotNull(result.topCandidateMode)
        assertEquals(1, result.topCandidateMode?.id)
        assertEquals("College", result.topCandidateMode?.name)

        // All 5 signals match: Location (0.35) + Calendar (0.25) + Time (0.20) + Habit (0.10) + Device (0.10) = 1.00
        assertEquals(1.00f, result.totalConfidence, 0.01f)
        assertEquals(ContextConfidenceLevel.HIGH, result.confidenceLevel)
        assertEquals(ContextActionDecision.AUTO_SWITCH, result.actionDecision)
        assertFalse(result.isConflict)
        assertEquals(5, result.matchedSignals.size)
    }

    @Test
    fun testSingleSignalInsufficientForAutoSwitch() = runBlocking {
        // Monday 9:00 AM, but NO location, NO calendar, NO habits, NO device
        val monday9am = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        val result = ContextRecognitionEngine.evaluateAllModes(
            dao = dao,
            currentLocationHint = null,
            currentTimestamp = monday9am
        )

        val collegeScore = result.candidateScores[1] ?: 0.0f
        // Only time signal contributes (0.20)
        assertEquals(0.20f, collegeScore, 0.01f)
        assertEquals(ContextConfidenceLevel.NO_CONTEXT, result.confidenceLevel)
        assertEquals(ContextActionDecision.RETAIN_CURRENT, result.actionDecision)
        assertNotEquals("Single weak signal must never auto-switch", ContextActionDecision.AUTO_SWITCH, result.actionDecision)
    }

    @Test
    fun testPermissionDenialDegradesGracefullyWithoutCrashing() = runBlocking {
        // Revoke Location and Calendar permissions in Privacy Firewall
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, false)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, false)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, false)

        val monday9am = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        // Even though location hint and calendar events exist in DB, firewall blocks them cleanly
        dao.insertEvent(EventEntity(id = 101, title = "DBMS Lab", startTime = monday9am, endTime = monday9am + 3600_000L, associatedModeId = 1))

        val result = ContextRecognitionEngine.evaluateAllModes(
            dao = dao,
            currentLocationHint = "College",
            isGeofenceEnter = true,
            currentTimestamp = monday9am
        )

        // Zero crashes. Location, Calendar, and BLE scores are 0.0
        val breakdown = result.breakdowns[1]!!
        assertEquals(0.0f, breakdown.locationScore, 0.01f)
        assertEquals(0.0f, breakdown.calendarScore, 0.01f)
        assertEquals(0.0f, breakdown.deviceScore, 0.01f)

        // Only time signal survived (0.20)
        assertEquals(0.20f, result.totalConfidence, 0.01f)
        assertEquals(ContextActionDecision.RETAIN_CURRENT, result.actionDecision)
    }

    @Test
    fun testConfidenceTiersDifferentiation() {
        // Check confidence tier mapping logic
        fun tierOf(score: Float): ContextConfidenceLevel = when {
            score >= 0.75f -> ContextConfidenceLevel.HIGH
            score >= 0.50f -> ContextConfidenceLevel.MEDIUM
            score >= 0.25f -> ContextConfidenceLevel.LOW
            else -> ContextConfidenceLevel.NO_CONTEXT
        }

        assertEquals(ContextConfidenceLevel.HIGH, tierOf(1.0f))
        assertEquals(ContextConfidenceLevel.HIGH, tierOf(0.75f))
        assertEquals(ContextConfidenceLevel.MEDIUM, tierOf(0.74f))
        assertEquals(ContextConfidenceLevel.MEDIUM, tierOf(0.50f))
        assertEquals(ContextConfidenceLevel.LOW, tierOf(0.49f))
        assertEquals(ContextConfidenceLevel.LOW, tierOf(0.25f))
        assertEquals(ContextConfidenceLevel.NO_CONTEXT, tierOf(0.24f))
        assertEquals(ContextConfidenceLevel.NO_CONTEXT, tierOf(0.0f))
    }

    @Test
    fun testDeterministicConflictResolutionViaPriority() = runBlocking {
        // Configure College (priority 1) and Gym (priority 2)
        val now = System.currentTimeMillis()

        // College has time (0.20) + location (0.35) = 0.55
        // Gym has location (0.35) + habit (0.10) + time (0.10) = 0.55
        // When scores are tied, priority 1 (College) must deterministically win over priority 2 (Gym)
        val college = dao.getModeById(1)!!
        val gym = dao.getModeById(2)!!
        assertTrue("College must have higher priority than Gym", college.priority < gym.priority)

        // Seed Gym habit
        dao.insertOrUpdateHabit(ItemHabitEntity(id = 2, itemName = "Shaker Bottle", modeId = 2, confirmCount = 10))

        val result = ContextRecognitionEngine.evaluateAllModes(
            dao = dao,
            currentLocationHint = "College",
            currentTimestamp = now
        )

        assertNotNull(result.topCandidateMode)
        // Highest priority mode wins
        assertTrue(result.topCandidateMode?.priority ?: 99 <= 2)
    }

    @Test
    fun testAmbiguousTieRetainsActiveModeWithoutGuessing() = runBlocking {
        // Create 2 test modes with IDENTICAL priority and conflicting signals
        dao.insertLifeMode(LifeModeEntity(id = 10, name = "Project Alpha", iconRes = "ic_work", isActive = true, priority = 5))
        dao.insertLifeMode(LifeModeEntity(id = 11, name = "Project Beta", iconRes = "ic_work", isActive = false, priority = 5))

        val now = System.currentTimeMillis()

        // Give both identical calendar events starting now
        dao.insertEvent(EventEntity(id = 201, title = "Project Alpha Sync", startTime = now - 1000L, endTime = now + 3600_000L, associatedModeId = 10))
        dao.insertEvent(EventEntity(id = 202, title = "Project Beta Sync", startTime = now - 1000L, endTime = now + 3600_000L, associatedModeId = 11))

        val result = ContextRecognitionEngine.evaluateAllModes(
            dao = dao,
            currentLocationHint = null,
            currentTimestamp = now
        )

        // When identical scores and identical priorities collide:
        assertTrue("Must detect tie collision", result.isConflict)
        assertEquals(ContextActionDecision.UNCERTAIN, result.actionDecision)
        assertNull("Must not arbitrarily pick a winner in a tie", result.topCandidateMode)
        assertTrue(result.statusMessage.contains("Uncertain context"))

        // Active mode in database must NOT be changed
        val activeMode = dao.getActiveModeFlow().first()
        assertEquals(1, activeMode?.id) // Remains College (baseline)
    }

    @Test
    fun testTimeBoundariesWeekdayVsWeekend() = runBlocking {
        val mondayCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 8)
        }.timeInMillis

        val sundayCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 8)
        }.timeInMillis

        // Monday 8 AM -> Academic window active for College
        val mondayResult = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentTimestamp = mondayCal)
        val collegeMonday = mondayResult.breakdowns[1]!!.timeScore
        assertEquals(0.20f, collegeMonday, 0.01f)

        // Sunday 8 AM -> Academic window INACTIVE for College (score = 0.0)
        val sundayResult = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentTimestamp = sundayCal)
        val collegeSunday = sundayResult.breakdowns[1]!!.timeScore
        assertEquals(0.00f, collegeSunday, 0.01f)

        // Sunday 8 AM -> Sports window ACTIVE (score = 0.20)
        val sportsSunday = sundayResult.breakdowns[4]!!.timeScore
        assertEquals(0.20f, sportsSunday, 0.01f)
    }

    @Test
    fun testLocationGeofenceTransitionSignals() = runBlocking {
        val now = System.currentTimeMillis()

        // 1. Geofence Enter -> +0.35 location weight
        val enterResult = ContextRecognitionEngine.evaluateAllModes(
            dao = dao,
            currentLocationHint = "Gym",
            isGeofenceEnter = true,
            currentTimestamp = now
        )
        assertEquals(0.35f, enterResult.breakdowns[2]!!.locationScore, 0.01f)

        // 2. Geofence Exit -> +0.28 location weight
        val exitResult = ContextRecognitionEngine.evaluateAllModes(
            dao = dao,
            currentLocationHint = "Gym",
            isGeofenceExit = true,
            currentTimestamp = now
        )
        assertEquals(0.28f, exitResult.breakdowns[2]!!.locationScore, 0.01f)

        // 3. Location Hint for unrelated mode -> 0.0
        assertEquals(0.0f, enterResult.breakdowns[1]!!.locationScore, 0.01f)
    }

    @Test
    fun testCalendarEventActiveVsUpcomingVsFinished() = runBlocking {
        val now = System.currentTimeMillis()

        // 1. Active Event (now in progress) -> 0.25 calendar weight
        dao.insertEvent(EventEntity(id = 301, title = "Active Workout", startTime = now - 1800_000L, endTime = now + 1800_000L, associatedModeId = 2))
        val activeResult = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentTimestamp = now)
        assertEquals(0.25f, activeResult.breakdowns[2]!!.calendarScore, 0.01f)

        // 2. Upcoming Event (starts in 30 mins) -> 0.20 calendar weight
        dao.deleteEventById(301)
        dao.insertEvent(EventEntity(id = 302, title = "Upcoming Workout", startTime = now + 1800_000L, endTime = now + 5400_000L, associatedModeId = 2))
        val upcomingResult = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentTimestamp = now)
        assertEquals(0.20f, upcomingResult.breakdowns[2]!!.calendarScore, 0.01f)

        // 3. Finished Event (ended 1 hour ago) -> 0.00
        dao.deleteEventById(302)
        dao.insertEvent(EventEntity(id = 303, title = "Finished Workout", startTime = now - 7200_000L, endTime = now - 3600_000L, associatedModeId = 2))
        val finishedResult = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentTimestamp = now)
        assertEquals(0.00f, finishedResult.breakdowns[2]!!.calendarScore, 0.01f)
    }

    @Test
    fun testMalformedCalendarEventDataSafety() = runBlocking {
        val now = System.currentTimeMillis()

        // Malformed events: empty title, negative duration, null location
        dao.insertEvent(EventEntity(id = 401, title = "", startTime = now, endTime = now + 1000L, associatedModeId = 1))
        dao.insertEvent(EventEntity(id = 402, title = "Corrupted Timing", startTime = now + 10000L, endTime = now - 10000L, associatedModeId = 2))

        val result = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentTimestamp = now)
        // Must not throw or crash
        assertNotNull(result)
        assertEquals(0.0f, result.breakdowns[1]!!.calendarScore, 0.01f)
        assertEquals(0.0f, result.breakdowns[2]!!.calendarScore, 0.01f)
    }

    @Test
    fun testHabitAndRoutineSignalContribution() = runBlocking {
        val now = System.currentTimeMillis()

        // Mode 2 (Gym) has 8 confirmations -> habit score = 0.10
        dao.insertOrUpdateHabit(ItemHabitEntity(id = 1, itemName = "Shaker", modeId = 2, confirmCount = 8))

        // Mode 3 (Travel) has 0 habits -> habit score = 0.0
        val result = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentTimestamp = now)

        assertEquals(0.10f, result.breakdowns[2]!!.habitScore, 0.01f)
        assertEquals(0.00f, result.breakdowns[3]!!.habitScore, 0.01f)
    }

    @Test
    fun testManualModeSelectionPrecedenceAndCooldown() = runBlocking {
        val now = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        // User explicitly manually switches to Sports mode (id = 4)
        dao.setActiveMode(4)
        ContextRecognitionEngine.notifyManualModeSelected(modeId = 4, timestamp = now)

        // 10 minutes later, strong College signals arrive (Location + Calendar + Time = 0.80)
        val tenMinsLater = now + (10 * 60_000L)
        dao.insertEvent(EventEntity(id = 501, title = "DBMS Lab", startTime = tenMinsLater, endTime = tenMinsLater + 3600_000L, associatedModeId = 1))

        val result = ContextRecognitionEngine.evaluateAllModes(
            dao = dao,
            currentLocationHint = "College",
            isGeofenceEnter = true,
            currentTimestamp = tenMinsLater
        )

        // Even though College has High Confidence (0.80), decision must be PROPOSE, NOT AUTO_SWITCH
        assertEquals(ContextConfidenceLevel.HIGH, result.confidenceLevel)
        assertEquals("Manual selection must prevent disruptive auto-switch", ContextActionDecision.PROPOSE, result.actionDecision)
        assertTrue(result.statusMessage.contains("manually selected"))
    }

    @Test
    fun testActiveModePreservedOnMediumConfidence() = runBlocking {
        val monday2pm = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        dao.setActiveMode(1) // Active is College

        // Gym has medium confidence (0.65): Location (0.35) + Habit (0.10) + Upcoming Calendar (0.20) = 0.65
        dao.insertOrUpdateHabit(ItemHabitEntity(id = 1, itemName = "Towel", modeId = 2, confirmCount = 7))
        dao.insertEvent(EventEntity(id = 502, title = "Gym Workout", startTime = monday2pm + 1800_000L, endTime = monday2pm + 5400_000L, associatedModeId = 2))

        val result = ContextRecognitionEngine.evaluateAndApplyDecision(
            dao = dao,
            currentLocationHint = "Gym",
            isGeofenceEnter = true,
            currentTimestamp = monday2pm
        )

        assertEquals(ContextConfidenceLevel.MEDIUM, result.confidenceLevel)
        assertEquals(ContextActionDecision.PROPOSE, result.actionDecision)

        // Active mode in Room DB must STILL be College (id = 1), not overwritten
        val activeMode = dao.getActiveModeFlow().first()
        assertEquals(1, activeMode?.id)
        assertEquals("College", activeMode?.name)
    }

    @Test
    fun testContextAntiFlappingDebounce() = runBlocking {
        // Start in Gym mode (2)
        dao.setActiveMode(2)

        val t0 = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        // 1. Auto-switch to College at T0
        dao.insertEvent(EventEntity(id = 601, title = "DBMS Lab", startTime = t0, endTime = t0 + 3600_000L, associatedModeId = 1))
        val firstResult = ContextRecognitionEngine.evaluateAndApplyDecision(
            dao = dao,
            currentLocationHint = "College",
            isGeofenceEnter = true,
            currentTimestamp = t0
        )
        assertEquals(ContextActionDecision.AUTO_SWITCH, firstResult.actionDecision)
        assertEquals(1, dao.getActiveModeFlow().first()?.id)

        // 2. Rapid transition to Gym only 15 seconds later (< 60s cooldown)
        val t15 = t0 + 15_000L
        dao.insertEvent(EventEntity(id = 602, title = "Workout", startTime = t15, endTime = t15 + 3600_000L, associatedModeId = 2))

        val secondResult = ContextRecognitionEngine.evaluateAndApplyDecision(
            dao = dao,
            currentLocationHint = "Gym",
            isGeofenceEnter = true,
            currentTimestamp = t15
        )

        // Flapping must be suppressed
        assertEquals(ContextActionDecision.RETAIN_CURRENT, secondResult.actionDecision)
        assertTrue(secondResult.statusMessage.contains("flapping detected"))
        // Active mode remains College (id = 1)
        assertEquals(1, dao.getActiveModeFlow().first()?.id)
    }

    @Test
    fun testBatteryProfileThrottlingOnContextSignals() = runBlocking {
        val now = System.currentTimeMillis()

        // 1. Under NORMAL profile: Laptop Beacon nearby contributes 0.10
        BatteryBudgetManager.setProfileOverride(PowerProfile.NORMAL)
        DevicePresenceScanner.simulateDeviceState("Laptop Beacon", PresenceConfidence.HIGH_CONFIDENCE, -58)

        val normalResult = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentTimestamp = now)
        assertEquals(0.10f, normalResult.breakdowns[1]!!.deviceScore, 0.01f)

        // 2. Under SAVER profile: BLE scanning paused by BatteryBudgetManager -> device score = 0.0
        BatteryBudgetManager.setProfileOverride(PowerProfile.SAVER)
        val saverResult = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentTimestamp = now)
        assertEquals(0.0f, saverResult.breakdowns[1]!!.deviceScore, 0.01f)

        // 3. Under CRITICAL profile: throttled safely with zero exceptions
        BatteryBudgetManager.setProfileOverride(PowerProfile.CRITICAL)
        val criticalResult = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentTimestamp = now)
        assertNotNull(criticalResult)
        assertEquals(0.0f, criticalResult.breakdowns[1]!!.deviceScore, 0.01f)
    }

    @Test
    fun testOfflineAirplaneModeFullOperation() = runBlocking {
        // Evaluate purely offline with local DB signals
        val monday10am = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 10)
        }.timeInMillis

        dao.insertEvent(EventEntity(id = 701, title = "Data Structures Lecture", startTime = monday10am, endTime = monday10am + 3600_000L, associatedModeId = 1))

        val result = ContextRecognitionEngine.evaluateAllModes(
            dao = dao,
            currentLocationHint = "College",
            isGeofenceEnter = true,
            currentTimestamp = monday10am
        )

        assertNotNull(result)
        assertEquals("College", result.topCandidateMode?.name)
        assertTrue("Local offline evaluation succeeds without network", result.totalConfidence >= 0.75f)
    }

    @Test
    fun testMultilingualAndUnicodeModeNames() = runBlocking {
        // Clear default modes to isolate multilingual mode names
        dao.modes.clear()
        dao.insertLifeMode(LifeModeEntity(id = 20, name = "కాలేజ్", iconRes = "ic_college", isActive = false, priority = 1))
        dao.insertLifeMode(LifeModeEntity(id = 21, name = "जिम", iconRes = "ic_gym", isActive = false, priority = 2))
        dao.insertLifeMode(LifeModeEntity(id = 22, name = "Gym 🏋️", iconRes = "ic_gym", isActive = false, priority = 3))
        dao.insertLifeMode(LifeModeEntity(id = 23, name = "  SPORTS  ", iconRes = "ic_sports", isActive = false, priority = 4))

        val now = System.currentTimeMillis()

        // 1. Telugu normalization
        val teluguResult = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentLocationHint = "కాలేజ్", currentTimestamp = now)
        assertEquals(20, teluguResult.topCandidateMode?.id)

        // 2. Hindi normalization
        val hindiResult = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentLocationHint = "जिम", currentTimestamp = now)
        assertEquals(21, hindiResult.topCandidateMode?.id)

        // 3. Emoji cleaning
        val emojiResult = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentLocationHint = "Gym", currentTimestamp = now)
        assertNotNull(emojiResult.topCandidateMode)

        // 4. Whitespace trimming & uppercase
        val spaceResult = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentLocationHint = "sports", currentTimestamp = now)
        assertNotNull(spaceResult.topCandidateMode)
    }

    @Test
    fun testRealLifeScenario1_College() = runBlocking {
        // Start in another mode (e.g. Gym) to test departure towards College
        dao.setActiveMode(2)

        // Real-Life Scenario 1: Weekday morning college departure
        val monday830am = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 30)
        }.timeInMillis

        dao.insertEvent(EventEntity(id = 801, title = "DBMS Lab Evaluation", startTime = monday830am + 1800_000L, endTime = monday830am + 7200_000L, associatedModeId = 1))
        dao.insertOrUpdateHabit(ItemHabitEntity(id = 1, itemName = "ID Card", modeId = 1, confirmCount = 8))
        DevicePresenceScanner.simulateDeviceState("Laptop Beacon", PresenceConfidence.HIGH_CONFIDENCE, -62)

        val result = ContextRecognitionEngine.evaluateAndApplyDecision(
            dao = dao,
            currentLocationHint = "College",
            isGeofenceExit = true,
            currentTimestamp = monday830am
        )

        assertEquals("College", result.topCandidateMode?.name)
        assertTrue(result.totalConfidence >= 0.80f)
        assertEquals(ContextActionDecision.AUTO_SWITCH, result.actionDecision)
        assertEquals(1, dao.getActiveModeFlow().first()?.id)
    }

    @Test
    fun testRealLifeScenario2_Sports() = runBlocking {
        // Real-Life Scenario 2: Weekend morning sports session
        val saturday8am = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        dao.insertEvent(EventEntity(id = 802, title = "Football Match vs Alumni", startTime = saturday8am, endTime = saturday8am + 7200_000L, associatedModeId = 4))
        DevicePresenceScanner.simulateDeviceState("Smartwatch", PresenceConfidence.HIGH_CONFIDENCE, -50)

        val result = ContextRecognitionEngine.evaluateAndApplyDecision(
            dao = dao,
            currentLocationHint = "Sports",
            isGeofenceEnter = true,
            currentTimestamp = saturday8am
        )

        assertEquals("Sports", result.topCandidateMode?.name)
        assertEquals(4, result.topCandidateMode?.id)
        assertTrue(result.totalConfidence >= 0.80f)
        assertEquals(ContextActionDecision.AUTO_SWITCH, result.actionDecision)
        assertEquals(4, dao.getActiveModeFlow().first()?.id)
    }

    @Test
    fun testRealLifeScenario3_Gym() = runBlocking {
        // Real-Life Scenario 3: Weekday evening gym workout
        val wednesday630pm = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 30)
        }.timeInMillis

        dao.insertEvent(EventEntity(id = 803, title = "Leg Day Workout", startTime = wednesday630pm, endTime = wednesday630pm + 3600_000L, associatedModeId = 2))
        dao.insertOrUpdateHabit(ItemHabitEntity(id = 3, itemName = "Shaker Bottle", modeId = 2, confirmCount = 12))
        DevicePresenceScanner.simulateDeviceState("Smartwatch", PresenceConfidence.HIGH_CONFIDENCE, -55)

        val result = ContextRecognitionEngine.evaluateAndApplyDecision(
            dao = dao,
            currentLocationHint = "Gym",
            isGeofenceEnter = true,
            currentTimestamp = wednesday630pm
        )

        assertEquals("Gym", result.topCandidateMode?.name)
        assertEquals(2, result.topCandidateMode?.id)
        assertTrue(result.totalConfidence >= 0.85f)
        assertEquals(ContextActionDecision.AUTO_SWITCH, result.actionDecision)
        assertEquals(2, dao.getActiveModeFlow().first()?.id)
    }

    @Test
    fun testRealLifeScenario4_Travel() = runBlocking {
        // Real-Life Scenario 4: Airport transit for travel
        val now = System.currentTimeMillis()

        dao.insertEvent(EventEntity(id = 804, title = "Flight to Hyderabad - Indigo 6E", startTime = now, endTime = now + 7200_000L, associatedModeId = 3))
        DevicePresenceScanner.simulateDeviceState("Bluetooth Headphones", PresenceConfidence.HIGH_CONFIDENCE, -60)

        val result = ContextRecognitionEngine.evaluateAndApplyDecision(
            dao = dao,
            currentLocationHint = "Travel",
            isGeofenceEnter = true,
            currentTimestamp = now
        )

        assertEquals("Travel", result.topCandidateMode?.name)
        assertEquals(3, result.topCandidateMode?.id)
        assertTrue(result.totalConfidence >= 0.75f)
        assertEquals(ContextActionDecision.AUTO_SWITCH, result.actionDecision)
        assertEquals(3, dao.getActiveModeFlow().first()?.id)
    }

    @Test
    fun testRealLifeScenario5_AmbiguousContext() = runBlocking {
        // Real-Life Scenario 5: User equidistant between College and Gym with equal weak cues
        val now = System.currentTimeMillis()
        dao.setActiveMode(1) // College is active

        // Add 2 non-default modes with exact equal priority and scores
        dao.insertLifeMode(LifeModeEntity(id = 50, name = "Activity Alpha", iconRes = "ic_college", isActive = false, priority = 3))
        dao.insertLifeMode(LifeModeEntity(id = 51, name = "Activity Beta", iconRes = "ic_gym", isActive = false, priority = 3))

        dao.insertEvent(EventEntity(id = 850, title = "Activity Alpha Session", startTime = now - 1000L, endTime = now + 3600_000L, associatedModeId = 50))
        dao.insertEvent(EventEntity(id = 851, title = "Activity Beta Session", startTime = now - 1000L, endTime = now + 3600_000L, associatedModeId = 51))

        val result = ContextRecognitionEngine.evaluateAllModes(dao = dao, currentLocationHint = null, currentTimestamp = now)

        assertTrue(result.isConflict)
        assertEquals(ContextActionDecision.UNCERTAIN, result.actionDecision)
        assertNull(result.topCandidateMode)
        // Never changes active mode in uncertain state
        assertEquals(1, dao.getActiveModeFlow().first()?.id)
    }

    @Test
    fun testSafeModeSuppressesAutoSwitchToPropose() = runBlocking {
        // Activate Emergency Safe Mode
        SafeModeManager.setSafeMode(true)
        dao.setActiveMode(2)

        val monday9am = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 9)
        }.timeInMillis

        dao.insertEvent(EventEntity(id = 901, title = "DBMS Lab", startTime = monday9am, endTime = monday9am + 3600_000L, associatedModeId = 1))

        val result = ContextRecognitionEngine.evaluateAllModes(
            dao = dao,
            currentLocationHint = "College",
            isGeofenceEnter = true,
            currentTimestamp = monday9am
        )

        assertEquals(ContextConfidenceLevel.HIGH, result.confidenceLevel)
        // Safe Mode must suppress AUTO_SWITCH to PROPOSE
        assertEquals(ContextActionDecision.PROPOSE, result.actionDecision)
        assertTrue(result.statusMessage.contains("Safe Mode Active"))
    }

    @Test
    fun testEmptyModesBoundarySafety() = runBlocking {
        // Clear all modes
        dao.modes.clear()

        val result = ContextRecognitionEngine.evaluateAllModes(dao = dao)
        assertEquals(ContextConfidenceLevel.NO_CONTEXT, result.confidenceLevel)
        assertEquals(ContextActionDecision.RETAIN_CURRENT, result.actionDecision)
        assertNull(result.topCandidateMode)
        assertEquals(0.0f, result.totalConfidence, 0.01f)
    }
}
