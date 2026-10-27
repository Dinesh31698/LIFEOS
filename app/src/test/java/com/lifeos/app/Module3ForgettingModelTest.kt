package com.lifeos.app

import com.lifeos.app.data.*
import com.lifeos.app.engine.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LIFEOS Module 3: Personal Forgetting Model & Habit Learning Engine Test Suite.
 * Validates:
 *  1. Correct formula calculation for Forgetfulness Score = (1.5 * forgetCount) - (0.3 * confirmCount).
 *  2. Escalation trigger and accelerated priority boost (+0.75) when forgetCount reaches >= 3 (HIGH_RISK).
 *  3. De-escalation when confirmCount increases upon tapping [Mark as Ready] (-0.10 priority boost, floored at 0.0).
 *  4. Persistence and retrieval of ItemHabitEntity records in Room DB across sessions.
 *  5. Geofence departure handling elevating unchecked, non-waived requirements.
 *  6. Habit diagnostics and evaluation reporting.
 */
class Module3ForgettingModelTest {

    private lateinit var fakeDao: FakeLifeOsDao

    @Before
    fun setUp() {
        fakeDao = FakeLifeOsDao()
        seedTestData(fakeDao)
    }

    private fun seedTestData(dao: FakeLifeOsDao) {
        dao.modes.add(LifeModeEntity(id = 1, name = "College", iconRes = "school", isActive = true))
        dao.modes.add(LifeModeEntity(id = 2, name = "Gym", iconRes = "fitness", isActive = false))

        // Initial items for College Mode
        dao.items.add(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 2, modeId = 1, itemName = "DBMS Record", isReady = false, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 3, modeId = 1, itemName = "Lab Apron", isReady = false, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 4, modeId = 1, itemName = "Waived Book", isReady = false, isWaived = true))
    }

    // =========================================================================
    // 1. Habit Math Formula Tests
    // =========================================================================

    @Test
    fun testForgetfulnessScoreCalculation() {
        // forgetCount = 0, confirmCount = 0 -> 0.0
        assertEquals(0.0f, ForgettingModelEngine.calculateForgetfulnessScore(0, 0), 0.001f)

        // forgetCount = 1, confirmCount = 0 -> (1 * 1.5) - 0 = 1.5
        assertEquals(1.5f, ForgettingModelEngine.calculateForgetfulnessScore(1, 0), 0.001f)

        // forgetCount = 2, confirmCount = 1 -> (2 * 1.5) - (1 * 0.3) = 2.7
        assertEquals(2.7f, ForgettingModelEngine.calculateForgetfulnessScore(2, 1), 0.001f)

        // forgetCount = 3, confirmCount = 0 -> (3 * 1.5) - 0 = 4.5
        assertEquals(4.5f, ForgettingModelEngine.calculateForgetfulnessScore(3, 0), 0.001f)

        // forgetCount = 3, confirmCount = 2 -> 4.5 - 0.6 = 3.9
        assertEquals(3.9f, ForgettingModelEngine.calculateForgetfulnessScore(3, 2), 0.001f)

        // forgetCount = 4, confirmCount = 3 -> (4 * 1.5) - (3 * 0.3) = 6.0 - 0.9 = 5.1
        assertEquals(5.1f, ForgettingModelEngine.calculateForgetfulnessScore(4, 3), 0.001f)

        // Floor test: confirmCount exceeds forgetCount -> clamped at 0.0
        assertEquals(0.0f, ForgettingModelEngine.calculateForgetfulnessScore(0, 5), 0.001f)
        assertEquals(0.0f, ForgettingModelEngine.calculateForgetfulnessScore(1, 10), 0.001f)
    }

    // =========================================================================
    // 2. Escalation Trigger & Priority Boost Tests (forgetCount >= 3)
    // =========================================================================

    @Test
    fun testEscalationTriggerAndPriorityBoost() = runBlocking {
        val itemName = "DBMS Record"
        val modeId = 1

        // 1st Forget Event
        val habit1 = ForgettingModelEngine.recordItemForgotten(fakeDao, itemName, modeId)
        assertEquals(1, habit1.forgetCount)
        assertEquals(0.25f, habit1.priorityBoost, 0.001f)
        assertEquals(RiskLevel.NORMAL, ForgettingModelEngine.getRiskLevel(habit1.forgetCount))
        assertFalse(ForgettingModelEngine.isFrequentlyForgotten(habit1))

        // 2nd Forget Event
        val habit2 = ForgettingModelEngine.recordItemForgotten(fakeDao, itemName, modeId)
        assertEquals(2, habit2.forgetCount)
        assertEquals(0.50f, habit2.priorityBoost, 0.001f)
        assertEquals(RiskLevel.NORMAL, ForgettingModelEngine.getRiskLevel(habit2.forgetCount))
        assertFalse(ForgettingModelEngine.isFrequentlyForgotten(habit2))

        // 3rd Forget Event -> Breaches ESCALATION_THRESHOLD (>= 3) -> HIGH_RISK!
        val habit3 = ForgettingModelEngine.recordItemForgotten(fakeDao, itemName, modeId)
        assertEquals(3, habit3.forgetCount)
        // High risk applies accelerated +0.75f boost: 0.50 + 0.75 = 1.25f
        assertEquals(1.25f, habit3.priorityBoost, 0.001f)
        assertEquals(RiskLevel.HIGH_RISK, ForgettingModelEngine.getRiskLevel(habit3.forgetCount))
        assertTrue(ForgettingModelEngine.isFrequentlyForgotten(habit3))

        // Verify escalation alert message
        val warning = ForgettingModelEngine.getEscalationAlertMessage(itemName, habit3.forgetCount)
        assertTrue(warning.contains("HIGH FORGET RISK"))
        assertTrue(warning.contains("DBMS Record"))
        assertTrue(warning.contains("3 times"))
    }

    // =========================================================================
    // 3. De-Escalation on Confirmation ([Mark as Ready])
    // =========================================================================

    @Test
    fun testDeEscalationOnConfirm() = runBlocking {
        val itemName = "DBMS Record"
        val modeId = 1

        // Escalate item to 3 forgets
        repeat(3) {
            ForgettingModelEngine.recordItemForgotten(fakeDao, itemName, modeId)
        }

        val escalatedHabit = fakeDao.getHabit(itemName, modeId)!!
        assertEquals(3, escalatedHabit.forgetCount)
        assertEquals(0, escalatedHabit.confirmCount)
        assertEquals(1.25f, escalatedHabit.priorityBoost, 0.001f)
        assertEquals(4.5f, ForgettingModelEngine.calculateForgetfulnessScore(3, 0), 0.001f)

        // User marks item as ready (1st Confirmation)
        val confirmed1 = ForgettingModelEngine.recordItemConfirmed(fakeDao, itemName, modeId)
        assertEquals(1, confirmed1.confirmCount)
        // Priority boost decreases by -0.10: 1.25 - 0.10 = 1.15f
        assertEquals(1.15f, confirmed1.priorityBoost, 0.001f)
        assertEquals(4.2f, ForgettingModelEngine.calculateForgetfulnessScore(3, 1), 0.001f)

        // 2nd Confirmation
        val confirmed2 = ForgettingModelEngine.recordItemConfirmed(fakeDao, itemName, modeId)
        assertEquals(2, confirmed2.confirmCount)
        assertEquals(1.05f, confirmed2.priorityBoost, 0.001f)
        assertEquals(3.9f, ForgettingModelEngine.calculateForgetfulnessScore(3, 2), 0.001f)

        // Verify priority boost floors safely at 0.0f without negative underflow
        val zeroHabit = ItemHabitEntity(itemName = "Test Item", modeId = 1, confirmCount = 0, priorityBoost = 0.05f)
        fakeDao.insertOrUpdateHabit(zeroHabit)
        val clamped = ForgettingModelEngine.recordItemConfirmed(fakeDao, "Test Item", 1)
        assertEquals(0.0f, clamped.priorityBoost, 0.001f)
    }

    // =========================================================================
    // 4. Room DB Habit Persistence Across Sessions
    // =========================================================================

    @Test
    fun testRoomDbHabitPersistence() = runBlocking {
        // Record habits for multiple items across modes
        ForgettingModelEngine.recordItemForgotten(fakeDao, "DBMS Record", modeId = 1)
        ForgettingModelEngine.recordItemForgotten(fakeDao, "DBMS Record", modeId = 1)
        ForgettingModelEngine.recordItemForgotten(fakeDao, "DBMS Record", modeId = 1)

        ForgettingModelEngine.recordItemForgotten(fakeDao, "Shaker Bottle", modeId = 2)
        ForgettingModelEngine.recordItemConfirmed(fakeDao, "Shaker Bottle", modeId = 2)

        // Query mode habits
        val collegeHabits = fakeDao.getHabitsForMode(1)
        assertEquals(1, collegeHabits.size)
        val dbmsHabit = collegeHabits.first()
        assertEquals("DBMS Record", dbmsHabit.itemName)
        assertEquals(3, dbmsHabit.forgetCount)
        assertEquals(1.25f, dbmsHabit.priorityBoost, 0.001f)

        val gymHabits = fakeDao.getHabitsForMode(2)
        assertEquals(1, gymHabits.size)
        val gymHabit = gymHabits.first()
        assertEquals("Shaker Bottle", gymHabit.itemName)
        assertEquals(1, gymHabit.forgetCount)
        assertEquals(1, gymHabit.confirmCount)

        // Verify frequently forgotten query filter (threshold = 3)
        val escalatedList = fakeDao.getFrequentlyForgottenHabitsFlow(threshold = 3).first()
        assertEquals(1, escalatedList.size)
        assertEquals("DBMS Record", escalatedList.first().itemName)
    }

    // =========================================================================
    // 5. Geofence Departure Unchecked Items Handling
    // =========================================================================

    @Test
    fun testGeofenceDepartureElevatesUncheckedItemsOnly() = runBlocking {
        // College mode has:
        // - ID Card (isReady = true) -> SHOULD NOT be escalated
        // - DBMS Record (isReady = false, isWaived = false) -> SHOULD be escalated
        // - Lab Apron (isReady = false, isWaived = false) -> SHOULD be escalated
        // - Waived Book (isWaived = true) -> SHOULD NOT be escalated

        val escalatedHabits = ForgettingModelEngine.handleGeofenceDeparture(fakeDao, departingModeId = 1, recordAsConfirmed = true)
        assertEquals(2, escalatedHabits.size)

        val names = escalatedHabits.map { it.itemName }
        assertTrue("DBMS Record should be recorded as forgotten on departure", names.contains("DBMS Record"))
        assertTrue("Lab Apron should be recorded as forgotten on departure", names.contains("Lab Apron"))
        assertFalse("Ready ID Card must not be recorded as forgotten", names.contains("ID Card"))
        assertFalse("Waived items must not be recorded as forgotten", names.contains("Waived Book"))

        // Check persisted forget counts
        assertEquals(1, fakeDao.getHabit("DBMS Record", 1)?.forgetCount)
        assertEquals(1, fakeDao.getHabit("Lab Apron", 1)?.forgetCount)
        assertNull("ID Card should not have a habit record created for forgetting", fakeDao.getHabit("ID Card", 1))
    }

    // =========================================================================
    // 6. Habit Diagnostics & Evaluation Model
    // =========================================================================

    @Test
    fun testEvaluateItemHabitDetailedReporting() {
        val habit = ItemHabitEntity(
            itemName = "DBMS Record",
            modeId = 1,
            forgetCount = 4,
            confirmCount = 2,
            priorityBoost = 1.85f
        )

        val evaluation = ForgettingModelEngine.evaluateItemHabit("DBMS Record", habit)
        assertEquals("DBMS Record", evaluation.itemName)
        assertEquals(4, evaluation.forgetCount)
        assertEquals(2, evaluation.confirmCount)
        // Score = (4 * 1.5) - (2 * 0.3) = 6.0 - 0.6 = 5.4
        assertEquals(5.4f, evaluation.score, 0.001f)
        assertEquals(RiskLevel.HIGH_RISK, evaluation.riskLevel)
        assertTrue(evaluation.isEscalated)
        assertNotNull(evaluation.escalationWarning)
        assertEquals(1.85f, evaluation.priorityBoost, 0.001f)
    }
}
