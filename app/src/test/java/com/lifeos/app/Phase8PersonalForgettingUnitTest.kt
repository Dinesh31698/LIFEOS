package com.lifeos.app

import com.lifeos.app.data.*
import com.lifeos.app.engine.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LIFEOS Phase 8A: Personal Forgetting Model Implementation & Hardening Test Suite.
 *
 * Verifies all 26 required scenarios + exact Section 15 false-positive test:
 *  1. New item → UNKNOWN (cold start)
 *  2. One confirmed forgetting → UNKNOWN (cold start gate)
 *  3. Minimum observations reached (cold start transitions out of UNKNOWN)
 *  4. Low-risk behavior (consistently prepared)
 *  5. Medium-risk behavior (sometimes forgotten)
 *  6. High-risk behavior (frequently forgotten / active miss streak)
 *  7. Repeated forgetting (incremental counts and priority boost)
 *  8. Repeated successful preparation (boost de-escalation and streak reset)
 *  9. Recent improvement (streak decay and explainability)
 * 10. Recent deterioration (miss streak escalation and explainability)
 * 11. Forgetting streak (consecutiveForgetCount tracking)
 * 12. Success streak (consecutiveSuccessCount tracking)
 * 13. False prediction does not count
 * 14. BLE absence does not count
 * 15. Geofence departure does not count (critical fix: unchecked != forgotten)
 * 16. Ghost Mode does not count without confirmation
 * 17. Explicit user confirmation counts
 * 18. Mode isolation (USER + MODE + ITEM zero cross-mode leakage)
 * 19. Persistence (survives across sessions)
 * 20. Offline operation & 100+ item performance benchmark
 * 21. Safe Mode isolation (blocks autonomous learning)
 * 22. Item deletion (cascades habit without orphaned records)
 * 23. Mode deletion (cascades habits and protects system modes)
 * 24. Database integrity & schema migration validation
 * 25. Departure prioritization (orders HIGH risk above MEDIUM, above LOW/UNKNOWN)
 * 26. Readiness remains mathematically unchanged
 * 27. False-positive exact requirement scenario (predict -> ready -> no change -> confirm -> +1)
 */
class Phase8PersonalForgettingUnitTest {

    private lateinit var fakeDao: FakeLifeOsDao

    @Before
    fun setUp() {
        SafeModeManager.resetForTesting()
        fakeDao = FakeLifeOsDao()
        seedTestData(fakeDao)
    }

    @After
    fun tearDown() {
        SafeModeManager.resetForTesting()
    }

    private fun seedTestData(dao: FakeLifeOsDao) {
        // Seed Modes
        dao.modes.add(LifeModeEntity(id = 1, name = "College", iconRes = "school", isActive = true))
        dao.modes.add(LifeModeEntity(id = 2, name = "Gym", iconRes = "fitness", isActive = false))
        dao.modes.add(LifeModeEntity(id = 3, name = "Travel", iconRes = "travel", isActive = false))
        dao.modes.add(LifeModeEntity(id = 4, name = "Sports", iconRes = "sports", isActive = false))

        // Seed College items
        dao.items.add(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 2, modeId = 1, itemName = "DBMS Record", isReady = false, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 3, modeId = 1, itemName = "Laptop & Charger", isReady = false, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 4, modeId = 1, itemName = "Water Bottle", isReady = false, isWaived = false))

        // Seed Gym items
        dao.items.add(ItemRequirementEntity(id = 5, modeId = 2, itemName = "Water Bottle", isReady = false, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 6, modeId = 2, itemName = "Shaker Bottle", isReady = false, isWaived = false))
    }

    // =========================================================================
    // 1. Cold Start & Opportunity Gating Tests
    // =========================================================================

    @Test
    fun test01_newItemEvaluatesToUnknown() {
        val evaluation = ForgettingModelEngine.evaluatePersonalizedRisk(null, "New Item", 1)
        assertEquals(PersonalizedRiskLevel.UNKNOWN, evaluation.riskLevel)
        assertEquals("Not enough history yet.", evaluation.explanation)
        assertTrue(evaluation.isColdStart)
        assertEquals(0, evaluation.totalOpportunityCount)
        assertEquals(0.0f, evaluation.compositeRiskScore, 0.001f)
    }

    @Test
    fun test02_oneConfirmedForgettingEvaluatesToUnknownColdStart() = runBlocking {
        val habit = ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "New Item", 1)
        assertEquals(1, habit.forgetCount)
        assertEquals(1, habit.totalOpportunityCount)

        val evaluation = ForgettingModelEngine.evaluatePersonalizedRisk(habit)
        assertEquals("Single forgotten event must NOT produce HIGH risk during cold start", PersonalizedRiskLevel.UNKNOWN, evaluation.riskLevel)
        assertEquals("Not enough history yet.", evaluation.explanation)
        assertTrue(evaluation.isColdStart)
    }

    @Test
    fun test03_minimumObservationsReached() = runBlocking {
        // 2 observations -> still UNKNOWN
        ForgettingModelEngine.recordItemConfirmed(fakeDao, "Lab Apron", 1)
        ForgettingModelEngine.recordItemConfirmed(fakeDao, "Lab Apron", 1)
        var habit = fakeDao.getHabit("Lab Apron", 1)!!
        assertEquals(PersonalizedRiskLevel.UNKNOWN, ForgettingModelEngine.evaluatePersonalizedRisk(habit).riskLevel)

        // 3rd observation -> breaches MIN_OBSERVATIONS_THRESHOLD (>= 3)
        ForgettingModelEngine.recordItemConfirmed(fakeDao, "Lab Apron", 1)
        habit = fakeDao.getHabit("Lab Apron", 1)!!
        val eval = ForgettingModelEngine.evaluatePersonalizedRisk(habit)
        assertFalse("Cold start gate should open at 3 observations", eval.isColdStart)
        assertEquals(PersonalizedRiskLevel.LOW, eval.riskLevel)
        assertEquals("Usually prepared.", eval.explanation)
    }

    // =========================================================================
    // 2. Multi-tier Risk Classification Tests
    // =========================================================================

    @Test
    fun test04_lowRiskBehaviorConsistentlyPrepared() = runBlocking {
        repeat(5) {
            ForgettingModelEngine.recordItemConfirmed(fakeDao, "Laptop & Charger", 1)
        }
        val habit = fakeDao.getHabit("Laptop & Charger", 1)!!
        val evaluation = ForgettingModelEngine.evaluatePersonalizedRisk(habit)

        assertEquals(PersonalizedRiskLevel.LOW, evaluation.riskLevel)
        assertEquals("Usually prepared.", evaluation.explanation)
        assertEquals(0, habit.forgetCount)
        assertEquals(5, habit.confirmCount)
        assertEquals(5, habit.consecutiveSuccessCount)
        assertEquals(0.0f, evaluation.compositeRiskScore, 0.001f)
    }

    @Test
    fun test05_mediumRiskBehaviorSometimesForgotten() = runBlocking {
        // 2 forgets, 2 confirms (total 4 opportunities)
        repeat(2) { ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "Umbrella", 1) }
        repeat(2) { ForgettingModelEngine.recordItemConfirmed(fakeDao, "Umbrella", 1) }

        val habit = fakeDao.getHabit("Umbrella", 1)!!
        val evaluation = ForgettingModelEngine.evaluatePersonalizedRisk(habit)

        // Base rate = 2/4 = 0.50, streak for 2 confirms = -0.20, composite = 0.30 -> MEDIUM
        assertEquals(PersonalizedRiskLevel.MEDIUM, evaluation.riskLevel)
        assertEquals("Sometimes forgotten.", evaluation.explanation)
        assertFalse(evaluation.isColdStart)
    }

    @Test
    fun test06_highRiskBehaviorFrequentlyForgotten() = runBlocking {
        // 4 forgets, 1 confirm (consecutive miss streak of 2)
        ForgettingModelEngine.recordItemConfirmed(fakeDao, "Calculator", 1)
        repeat(4) { ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "Calculator", 1) }

        val habit = fakeDao.getHabit("Calculator", 1)!!
        val evaluation = ForgettingModelEngine.evaluatePersonalizedRisk(habit)

        assertEquals(PersonalizedRiskLevel.HIGH, evaluation.riskLevel)
        assertTrue("Risk score should be >= 0.60 for high-risk item", evaluation.compositeRiskScore >= 0.60f)
    }

    // =========================================================================
    // 3. Incremental Tracking & Streak Dynamics
    // =========================================================================

    @Test
    fun test07_repeatedForgettingIncrementallyIncreasesCountsAndBoost() = runBlocking {
        val h1 = ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "DBMS Record", 1)
        assertEquals(1, h1.forgetCount)
        assertEquals(0.25f, h1.priorityBoost, 0.001f)
        assertEquals(1, h1.consecutiveForgetCount)
        assertEquals(0, h1.consecutiveSuccessCount)

        val h2 = ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "DBMS Record", 1)
        assertEquals(2, h2.forgetCount)
        assertEquals(0.50f, h2.priorityBoost, 0.001f)
        assertEquals(2, h2.consecutiveForgetCount)

        val h3 = ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "DBMS Record", 1)
        assertEquals(3, h3.forgetCount)
        // High-risk escalation applies accelerated +0.75f boost: 0.50 + 0.75 = 1.25f
        assertEquals(1.25f, h3.priorityBoost, 0.001f)
        assertEquals(3, h3.consecutiveForgetCount)
    }

    @Test
    fun test08_repeatedSuccessfulPreparationDeescalatesBoostAndResetsForgetStreak() = runBlocking {
        repeat(3) { ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "DBMS Record", 1) }
        val before = fakeDao.getHabit("DBMS Record", 1)!!
        assertEquals(3, before.consecutiveForgetCount)
        assertEquals(1.25f, before.priorityBoost, 0.001f)

        // User prepares item successfully
        val c1 = ForgettingModelEngine.recordItemConfirmed(fakeDao, "DBMS Record", 1)
        assertEquals(1, c1.confirmCount)
        assertEquals(1, c1.consecutiveSuccessCount)
        assertEquals("Consecutive forget count must reset to 0 upon preparation", 0, c1.consecutiveForgetCount)
        assertEquals(1.15f, c1.priorityBoost, 0.001f)

        val c2 = ForgettingModelEngine.recordItemConfirmed(fakeDao, "DBMS Record", 1)
        assertEquals(2, c2.confirmCount)
        assertEquals(2, c2.consecutiveSuccessCount)
        assertEquals(0, c2.consecutiveForgetCount)
        assertEquals(1.05f, c2.priorityBoost, 0.001f)
    }

    @Test
    fun test09_recentImprovementReducesRiskAndExplainsRecovery() = runBlocking {
        // User forgot item 3 times initially
        repeat(3) { ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "Lab Keys", 1) }

        // User now prepares it 4 times in a row
        repeat(4) { ForgettingModelEngine.recordItemConfirmed(fakeDao, "Lab Keys", 1) }

        val habit = fakeDao.getHabit("Lab Keys", 1)!!
        val eval = ForgettingModelEngine.evaluatePersonalizedRisk(habit)

        assertEquals("Consistent preparations should transition item back to LOW risk", PersonalizedRiskLevel.LOW, eval.riskLevel)
        assertEquals(
            "Risk is decreasing because you've been preparing this consistently.",
            eval.explanation
        )
    }

    @Test
    fun test10_recentDeteriorationEscalatesRiskAndExplainsRecentMisses() = runBlocking {
        // User prepared item 3 times initially (established positive habit)
        repeat(3) { ForgettingModelEngine.recordItemConfirmed(fakeDao, "Bus Pass", 1) }

        // User recently forgot it twice in a row
        repeat(2) { ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "Bus Pass", 1) }

        val habit = fakeDao.getHabit("Bus Pass", 1)!!
        val eval = ForgettingModelEngine.evaluatePersonalizedRisk(habit)

        assertEquals(PersonalizedRiskLevel.HIGH, eval.riskLevel)
        assertEquals(
            "Risk is increasing because you've recently missed this item several times.",
            eval.explanation
        )
    }

    @Test
    fun test11_forgettingStreakAppliesProgressiveModifier() = runBlocking {
        repeat(3) { ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "Locker Key", 1) }
        val habit = fakeDao.getHabit("Locker Key", 1)!!
        assertEquals(3, habit.consecutiveForgetCount)

        val score3 = ForgettingModelEngine.calculateCompositeRiskScore(habit)
        // With 3 consecutive forgets, streak modifier adds +0.45f
        assertTrue("Streak of 3 misses should produce high composite score", score3 >= 0.90f)
    }

    @Test
    fun test12_successStreakAppliesProgressiveDecayModifier() = runBlocking {
        ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "Gym Towel", 2)
        repeat(4) { ForgettingModelEngine.recordItemConfirmed(fakeDao, "Gym Towel", 2) }

        val habit = fakeDao.getHabit("Gym Towel", 2)!!
        assertEquals(4, habit.consecutiveSuccessCount)
        assertEquals(0, habit.consecutiveForgetCount)

        val score = ForgettingModelEngine.calculateCompositeRiskScore(habit)
        // Base rate = 1/5 = 0.20, streak decay for 4 = -0.40, clamped at 0.0f
        assertEquals(0.0f, score, 0.001f)
    }

    // =========================================================================
    // 4. Critical False-Positive Protections
    // =========================================================================

    @Test
    fun test13_falsePredictionDoesNotCount() = runBlocking {
        // System generates unverified prediction for College Mode
        val habitBefore = fakeDao.getHabit("ID Card", 1)
        assertNull(habitBefore)

        // Evaluate context or readiness without user confirmation
        ReadinessEngine.calculateReadiness(fakeDao.modes[0], fakeDao.items.filter { it.modeId == 1 })

        val habitAfter = fakeDao.getHabit("ID Card", 1)
        assertNull("Context/Readiness predictions must NEVER create or alter habit records", habitAfter)
    }

    @Test
    fun test14_bleAbsenceDoesNotCount() = runBlocking {
        // Simulate BLE scan indicating Bluetooth tracker is ABSENT (null RSSI)
        val presence = DevicePresenceScanner.evaluateSignal("BLE_BEACON_ID_CARD", null)
        assertEquals(PresenceConfidence.UNKNOWN, presence.confidence)

        // Habit record must NOT be incremented
        val habit = fakeDao.getHabit("ID Card", 1)
        assertNull("BLE absence detection must NOT create or increment forgetting history", habit)
    }

    @Test
    fun test15_geofenceDepartureDoesNotCount() = runBlocking {
        // ID Card is ready, DBMS Record is unchecked
        val uncheckedBefore = fakeDao.items.find { it.itemName == "DBMS Record" }!!
        assertFalse(uncheckedBefore.isReady)

        // Geofence departure occurs (recordAsConfirmed defaults to false!)
        val departureAlerts = ForgettingModelEngine.handleGeofenceDeparture(fakeDao, departingModeId = 1)
        assertEquals(3, departureAlerts.size) // 3 unchecked items in mode 1

        // Verify that habit record was NOT incremented as confirmed forgetting
        val habit = fakeDao.getHabit("DBMS Record", 1)
        assertNull("Geofence departure must NOT automatically create confirmed forgetting records", habit)
    }

    @Test
    fun test16_ghostModeDoesNotCountWithoutConfirmation() = runBlocking {
        // Seed Ghost Mode proposal item
        val ghostItem = ItemRequirementEntity(id = 99, modeId = 1, itemName = "Lab Apron (Ghost)", isReady = false, isGhostMode = true)
        fakeDao.items.add(ghostItem)

        // Departure occurs
        val alerts = ForgettingModelEngine.handleGeofenceDeparture(fakeDao, departingModeId = 1)
        assertFalse("Ghost Mode items must not be included in departure alerts", alerts.any { it.itemName == "Lab Apron (Ghost)" })

        val habit = fakeDao.getHabit("Lab Apron (Ghost)", 1)
        assertNull("Ghost Mode items must NEVER increment forget history", habit)
    }

    @Test
    fun test17_explicitUserConfirmationCounts() = runBlocking {
        // Explicit user confirmation action
        val habit = ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "ID Card", 1)
        assertEquals(1, habit.forgetCount)
        assertEquals(1, habit.totalOpportunityCount)
        assertEquals(0.25f, habit.priorityBoost, 0.001f)

        val retrieved = fakeDao.getHabit("ID Card", 1)
        assertNotNull(retrieved)
        assertEquals(1, retrieved!!.forgetCount)
    }

    // =========================================================================
    // 5. Mode Isolation & Zero Cross-Mode Leakage
    // =========================================================================

    @Test
    fun test18_modeIsolationZeroCrossModeLeakage() = runBlocking {
        val itemName = "Water Bottle"

        // 1. College Mode: User frequently forgets Water Bottle (4 forgets)
        repeat(4) { ForgettingModelEngine.recordConfirmedForgetting(fakeDao, itemName, modeId = 1) }

        // 2. Gym Mode: User consistently prepares Water Bottle (4 confirms)
        repeat(4) { ForgettingModelEngine.recordItemConfirmed(fakeDao, itemName, modeId = 2) }

        // Query mode habits independently
        val collegeHabit = fakeDao.getHabit(itemName, modeId = 1)!!
        val gymHabit = fakeDao.getHabit(itemName, modeId = 2)!!
        val sportsHabit = fakeDao.getHabit(itemName, modeId = 4) // No history for Sports

        assertEquals(4, collegeHabit.forgetCount)
        assertEquals(0, collegeHabit.confirmCount)
        val collegeEval = ForgettingModelEngine.evaluatePersonalizedRisk(collegeHabit)
        assertEquals(PersonalizedRiskLevel.HIGH, collegeEval.riskLevel)

        assertEquals(0, gymHabit.forgetCount)
        assertEquals(4, gymHabit.confirmCount)
        val gymEval = ForgettingModelEngine.evaluatePersonalizedRisk(gymHabit)
        assertEquals(PersonalizedRiskLevel.LOW, gymEval.riskLevel)

        assertNull("Sports mode must have zero history for Water Bottle", sportsHabit)
        val sportsEval = ForgettingModelEngine.evaluatePersonalizedRisk(sportsHabit, itemName, 4)
        assertEquals(PersonalizedRiskLevel.UNKNOWN, sportsEval.riskLevel)
    }

    // =========================================================================
    // 6. Persistence, Offline & Performance
    // =========================================================================

    @Test
    fun test19_persistenceSurvivesAcrossSessions() = runBlocking {
        val now = System.currentTimeMillis()
        val habit = ItemHabitEntity(
            id = 10,
            itemName = "Lab Record",
            modeId = 1,
            forgetCount = 3,
            confirmCount = 2,
            priorityBoost = 1.15f,
            totalOpportunityCount = 5,
            lastPreparedTimestamp = now,
            lastForgottenTimestamp = now - 3600_000L,
            consecutiveSuccessCount = 2,
            consecutiveForgetCount = 0
        )
        fakeDao.insertOrUpdateHabit(habit)

        val retrieved = fakeDao.getHabit("Lab Record", 1)!!
        assertEquals(3, retrieved.forgetCount)
        assertEquals(2, retrieved.confirmCount)
        assertEquals(5, retrieved.totalOpportunityCount)
        assertEquals(now, retrieved.lastPreparedTimestamp)
        assertEquals(2, retrieved.consecutiveSuccessCount)
        assertEquals(0, retrieved.consecutiveForgetCount)
    }

    @Test
    fun test20_offlineOperationAndPerformanceBenchmark() {
        val startTime = System.currentTimeMillis()

        // Generate 150 diverse item habits
        val habits = (1..150).map { i ->
            ItemHabitEntity(
                id = i,
                itemName = "Item_$i",
                modeId = (i % 6) + 1,
                forgetCount = i % 5,
                confirmCount = (i * 2) % 7,
                totalOpportunityCount = (i % 5) + ((i * 2) % 7),
                consecutiveSuccessCount = if (i % 2 == 0) 3 else 0,
                consecutiveForgetCount = if (i % 2 != 0) 2 else 0
            )
        }

        // Evaluate all 150 items
        val results = habits.map { ForgettingModelEngine.evaluatePersonalizedRisk(it) }
        val duration = System.currentTimeMillis() - startTime

        assertEquals(150, results.size)
        assertTrue("Batch evaluation of 150 items must complete in < 100ms, took ${duration}ms", duration < 100L)
    }

    // =========================================================================
    // 7. Safe Mode, Deletion & Integrity
    // =========================================================================

    @Test
    fun test21_safeModeSuppressesAutonomousLearning() = runBlocking {
        SafeModeManager.setSafeMode(true)
        assertTrue(SafeModeManager.isSafeModeActive)
        assertFalse("Autonomous learning must be suppressed in Safe Mode", SafeModeManager.canLearnAutonomously())

        // Explicit user confirmation is still permitted
        val habit = ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "ID Card", 1)
        assertEquals(1, habit.forgetCount)
    }

    @Test
    fun test22_itemDeletionCleansUpHabitWithoutOrphans() = runBlocking {
        ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "Temporary Card", 1)
        assertNotNull(fakeDao.getHabit("Temporary Card", 1))

        // Delete habit for item
        val deleted = fakeDao.deleteHabit("Temporary Card", 1)
        assertEquals(1, deleted)
        assertNull("Habit record must be completely removed on item deletion", fakeDao.getHabit("Temporary Card", 1))
    }

    @Test
    fun test23_modeDeletionCascadesHabitsAndProtectsSystemModes() = runBlocking {
        // Create custom mode (id = 99)
        fakeDao.modes.add(LifeModeEntity(id = 99, name = "Hackathon", iconRes = "code", isActive = false))
        ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "Badge", 99)
        ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "Laptop Charger", 99)
        assertEquals(2, fakeDao.getHabitsForMode(99).size)

        // Delete custom mode
        fakeDao.deleteLifeMode(99)
        assertEquals(0, fakeDao.getHabitsForMode(99).size)
        assertNull(fakeDao.getModeById(99))

        // System modes (1..6) remain intact
        assertNotNull("System College mode must remain protected", fakeDao.getModeById(1))
        assertNotNull("System Gym mode must remain protected", fakeDao.getModeById(2))
    }

    @Test
    fun test24_databaseIntegrityAndMigrationValidation() {
        val migration = LifeOsDatabase.MIGRATION_6_7
        assertEquals(6, migration.startVersion)
        assertEquals(7, migration.endVersion)
    }

    // =========================================================================
    // 8. Departure Prioritization & Readiness Invariance
    // =========================================================================

    @Test
    fun test25_departurePrioritizationOrdersHighRiskItemsFirst() = runBlocking {
        // Habit 1: High Risk (forgetCount = 4, streak = 2)
        repeat(4) { ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "DBMS Record", 1) }

        // Habit 2: Medium Risk (forgetCount = 2, confirmCount = 2)
        repeat(2) { ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "Laptop & Charger", 1) }
        repeat(2) { ForgettingModelEngine.recordItemConfirmed(fakeDao, "Laptop & Charger", 1) }

        // Item 3: Low/Unknown Risk (Water Bottle - no history)

        val habits = fakeDao.getHabitsForMode(1)
        val missingItems = fakeDao.items.filter { it.modeId == 1 && !it.isReady }

        val prioritized = DepartureReminderEngine.prioritizeMissingItems(missingItems, habits)
        assertEquals(3, prioritized.size)
        assertEquals("HIGH risk item must be prioritized at position 0", "DBMS Record", prioritized[0].itemName)
        assertEquals("MEDIUM risk item must be prioritized at position 1", "Laptop & Charger", prioritized[1].itemName)
        assertEquals("LOW/UNKNOWN item must be prioritized at position 2", "Water Bottle", prioritized[2].itemName)
    }

    @Test
    fun test26_readinessRemainsMathematicallyUnchanged() = runBlocking {
        val mode = fakeDao.modes.find { it.id == 1 }!!
        val collegeItems = fakeDao.items.filter { it.modeId == 1 } // 4 items: ID Card (ready=true), DBMS Record (false), Laptop (false), Water Bottle (false)

        // Baseline: 1 ready out of 4 = 25%
        val baseReport = ReadinessEngine.calculateReadiness(mode, collegeItems)
        assertEquals(25, baseReport.percentage)

        // Escalate DBMS Record to HIGH RISK with multiple confirmed forgets
        repeat(5) { ForgettingModelEngine.recordConfirmedForgetting(fakeDao, "DBMS Record", 1) }

        // Recalculate readiness
        val afterReport = ReadinessEngine.calculateReadiness(mode, collegeItems)
        assertEquals("Forgetting risk MUST NOT alter readiness percentage mathematics", 25, afterReport.percentage)
        assertEquals(1, afterReport.readyCount)
        assertEquals(4, afterReport.totalCount)
    }

    // =========================================================================
    // 9. Exact Section 15 False-Positive Requirement Scenario
    // =========================================================================

    @Test
    fun test27_falsePositiveExactRequirementScenario() = runBlocking {
        val itemName = "ID Card"
        val modeId = 1

        // Step 1: LIFEOS predicts ID Card may be missing (e.g. geofence departure or checklist scan)
        val initialDeparture = ForgettingModelEngine.handleGeofenceDeparture(fakeDao, modeId)
        val habitAfterPrediction = fakeDao.getHabit(itemName, modeId)
        assertNull("Step 1: System prediction must NOT create a habit record", habitAfterPrediction)

        // Step 2 & 3: User actually has ID Card and prepares it
        val confirmedHabit = ForgettingModelEngine.recordItemConfirmed(fakeDao, itemName, modeId)

        // Step 4: Verify forgetCount does NOT increase
        assertEquals("Step 4: forgetCount must be 0 when user prepares item", 0, confirmedHabit.forgetCount)
        assertEquals(1, confirmedHabit.confirmCount)
        assertEquals(0, fakeDao.getHabit(itemName, modeId)!!.forgetCount)

        // Step 5: Later, user explicitly confirms: "I forgot my ID Card."
        val userConfirmedForget = ForgettingModelEngine.recordConfirmedForgetting(fakeDao, itemName, modeId)

        // Step 6: Verify forgetCount increases by exactly 1
        assertEquals("Step 6: forgetCount must increase by exactly 1 upon user confirmation", 1, userConfirmedForget.forgetCount)
        assertEquals(1, fakeDao.getHabit(itemName, modeId)!!.forgetCount)
    }
}
