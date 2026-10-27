package com.lifeos.app

import com.lifeos.app.data.ItemHabitEntity
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.engine.ForgettingModelEngine
import com.lifeos.app.engine.RiskLevel
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LIFEOS Real-Life Test & Hardening Suite — Module 4: Personal Forgetting Model & Habit Learning Engine
 *
 * Real-Life Test Scenario:
 *  Simulating repeated unverified geofence departures for "ID Card" in College Mode,
 *  verifying automatic risk escalation, HIGH_RISK badge rendering, and score de-escalation upon user check-off.
 *
 * Features Validated:
 *  - Habit Tracking & Multi-Departure Risk Escalation (#24, #25, #26)
 *  - Dynamic Mathematical Scoring: Score = (1.5 * forgetCount) - (0.3 * confirmCount) (#29)
 *  - UI Priority Reordering: HIGH_RISK items elevate to the top position in checklists (#30, #31)
 *  - Adaptive De-escalation upon User Check-off: confirmCount increment & score recovery (#65, #66)
 */
class Module4RealLifeTest {

    private lateinit var fakeDao: FakeLifeOsDao

    @Before
    fun setUp() {
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, true)

        fakeDao = FakeLifeOsDao()
        seedCollegeModeData(fakeDao)
    }

    @After
    fun tearDown() {
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, false)
    }

    private fun seedCollegeModeData(dao: FakeLifeOsDao) = runBlocking {
        // Active College Mode
        dao.insertLifeMode(LifeModeEntity(id = 1, name = "College", iconRes = "school", isActive = true, priority = 1))

        // Checklist Items: ID Card (unchecked), Laptop (ready), Water Bottle (unchecked), Watch (unchecked)
        dao.insertItem(ItemRequirementEntity(id = 10, modeId = 1, itemName = "Watch", isReady = false, isTemporary = false))
        dao.insertItem(ItemRequirementEntity(id = 20, modeId = 1, itemName = "Laptop", isReady = true, isTemporary = false))
        dao.insertItem(ItemRequirementEntity(id = 30, modeId = 1, itemName = "Water Bottle", isReady = false, isTemporary = false))
        dao.insertItem(ItemRequirementEntity(id = 40, modeId = 1, itemName = "ID Card", isReady = false, isTemporary = false))
    }

    // =========================================================================
    // 1. Habit Tracking & Risk Escalation (Features #24, #25, #26, #29)
    // =========================================================================

    @Test
    fun testThreeConsecutiveGeofenceExitsEscalateIdCardToHighRisk() = runBlocking {
        val targetModeId = 1
        val itemName = "ID Card"

        // Baseline: No habit record exists initially
        assertNull(fakeDao.getHabit(itemName, targetModeId))

        // --- Departure 1: First unverified exit ---
        val departure1 = ForgettingModelEngine.handleGeofenceDeparture(fakeDao, targetModeId, recordAsConfirmed = true)
        assertTrue(departure1.any { it.itemName == itemName })
        val habit1 = fakeDao.getHabit(itemName, targetModeId)
        assertNotNull(habit1)
        assertEquals(1, habit1!!.forgetCount)
        assertEquals(0, habit1.confirmCount)
        assertEquals(0.25f, habit1.priorityBoost, 0.001f)

        val score1 = ForgettingModelEngine.calculateForgetfulnessScore(habit1.forgetCount, habit1.confirmCount)
        assertEquals(1.5f, score1, 0.001f) // 1 * 1.5 - 0 * 0.3 = 1.5
        assertEquals(RiskLevel.NORMAL, ForgettingModelEngine.getRiskLevel(habit1.forgetCount))
        assertFalse(ForgettingModelEngine.isFrequentlyForgotten(habit1))

        // --- Departure 2: Second unverified exit ---
        val departure2 = ForgettingModelEngine.handleGeofenceDeparture(fakeDao, targetModeId, recordAsConfirmed = true)
        assertTrue(departure2.any { it.itemName == itemName })
        val habit2 = fakeDao.getHabit(itemName, targetModeId)
        assertNotNull(habit2)
        assertEquals(2, habit2!!.forgetCount)
        assertEquals(0.50f, habit2.priorityBoost, 0.001f) // 0.25 + 0.25

        val score2 = ForgettingModelEngine.calculateForgetfulnessScore(habit2.forgetCount, habit2.confirmCount)
        assertEquals(3.0f, score2, 0.001f) // 2 * 1.5 - 0 * 0.3 = 3.0
        assertEquals(RiskLevel.NORMAL, ForgettingModelEngine.getRiskLevel(habit2.forgetCount))
        assertFalse(ForgettingModelEngine.isFrequentlyForgotten(habit2))

        // --- Departure 3: Third unverified exit (THRESHOLD REACHED: forgetCount >= 3) ---
        val departure3 = ForgettingModelEngine.handleGeofenceDeparture(fakeDao, targetModeId, recordAsConfirmed = true)
        assertTrue(departure3.any { it.itemName == itemName })
        val habit3 = fakeDao.getHabit(itemName, targetModeId)
        assertNotNull(habit3)
        assertEquals(3, habit3!!.forgetCount)
        assertEquals(0, habit3.confirmCount)
        // High-risk jump: 0.50 + 0.75 accelerated boost = 1.25f
        assertEquals(1.25f, habit3.priorityBoost, 0.001f)

        val score3 = ForgettingModelEngine.calculateForgetfulnessScore(habit3.forgetCount, habit3.confirmCount)
        assertTrue("Forgetfulness score must be >= 4.5 for 3 forgets, was $score3", score3 >= 4.5f)
        assertEquals(4.5f, score3, 0.001f)

        // Verify risk classification transition to HIGH_RISK
        val risk3 = ForgettingModelEngine.getRiskLevel(habit3.forgetCount)
        assertEquals(RiskLevel.HIGH_RISK, risk3)
        assertTrue(ForgettingModelEngine.isFrequentlyForgotten(habit3))

        // Verify escalation warning message is formatted
        val warningMessage = ForgettingModelEngine.getEscalationAlertMessage(itemName, habit3.forgetCount)
        assertTrue(warningMessage.contains("HIGH FORGET RISK"))
        assertTrue(warningMessage.contains("ID Card"))
        assertTrue(warningMessage.contains("3 times"))

        // CRITICAL CHECK: Verify ready items (e.g. Laptop) are NEVER penalized during geofence departures
        val laptopHabit = fakeDao.getHabit("Laptop", targetModeId)
        assertNull("Ready items must not have their forgetCount incremented", laptopHabit)
    }

    // =========================================================================
    // 2. UI Priority & Badge Rendering (Features #30, #31)
    // =========================================================================

    @Test
    fun testHighRiskItemElevatesToTopPositionInChecklist() = runBlocking {
        val targetModeId = 1
        val itemName = "ID Card"

        // User prepared other gear (Watch, Laptop, Water Bottle), but left ID Card unchecked
        fakeDao.updateItemStatus(10, true) // Watch ready
        fakeDao.updateItemStatus(20, true) // Laptop ready
        fakeDao.updateItemStatus(30, true) // Water Bottle ready

        // Simulate 3 unverified departures for ID Card
        repeat(3) {
            ForgettingModelEngine.handleGeofenceDeparture(fakeDao, targetModeId, recordAsConfirmed = true)
        }

        // Even when other items are also missing, ID Card's HIGH_RISK priority boost keeps it at the top
        fakeDao.updateItemStatus(10, false) // Watch unchecked
        fakeDao.updateItemStatus(30, false) // Water Bottle unchecked

        val habit = fakeDao.getHabit(itemName, targetModeId)
        assertNotNull(habit)
        assertEquals(3, habit!!.forgetCount)
        assertEquals(RiskLevel.HIGH_RISK, ForgettingModelEngine.getRiskLevel(habit.forgetCount))

        // Query active items for mode
        val items = fakeDao.getItemsForMode(targetModeId)
        val habits = fakeDao.getHabitsForMode(targetModeId)
        val habitMap = habits.associateBy { it.itemName.lowercase() }

        // Sort items using ViewModel / UI priority comparator:
        // High-Risk Missing (2000 + boost) > Missing (1000 + boost) > Ready (boost)
        val sortedItems = items.sortedWith(
            compareByDescending<ItemRequirementEntity> { item ->
                val h = habitMap[item.itemName.lowercase()]
                val isHigh = h != null && ForgettingModelEngine.isFrequentlyForgotten(h)
                val boost = h?.priorityBoost ?: 0.0f
                if (!item.isReady && !item.isWaived && isHigh) 2000f + boost
                else if (!item.isReady && !item.isWaived) 1000f + boost
                else boost
            }.thenBy { it.id }
        )

        // VERIFY: "ID Card" was originally row 40, but must now be at index 0 (TOP POSITION)
        assertEquals("ID Card", sortedItems.first().itemName)
        assertEquals(40, sortedItems.first().id)

        // Verify Missing items subset for ReadinessScreen also has "ID Card" at position 0
        val missingItems = sortedItems.filter { !it.isReady && !it.isWaived }
        assertEquals("ID Card", missingItems.first().itemName)

        // Verify evaluation info for UI badge rendering
        val evalInfo = ForgettingModelEngine.evaluateItemHabit(itemName, habit)
        assertTrue("isEscalated flag must be true for badge rendering", evalInfo.isEscalated)
        assertEquals(RiskLevel.HIGH_RISK, evalInfo.riskLevel)
        assertEquals(4.5f, evalInfo.score, 0.001f)
        assertNotNull("Escalation warning must be non-null for illuminated card", evalInfo.escalationWarning)
    }

    // =========================================================================
    // 3. Adaptive De-escalation via User Feedback (Features #65, #66)
    // =========================================================================

    @Test
    fun testUserCheckOffIncrementsConfirmCountAndDeescalatesScore() = runBlocking {
        val targetModeId = 1
        val itemName = "ID Card"

        // Stage ID Card to HIGH_RISK (forgetCount = 3, confirmCount = 0)
        repeat(3) {
            ForgettingModelEngine.handleGeofenceDeparture(fakeDao, targetModeId, recordAsConfirmed = true)
        }
        val beforeHabit = fakeDao.getHabit(itemName, targetModeId)!!
        assertEquals(3, beforeHabit.forgetCount)
        assertEquals(0, beforeHabit.confirmCount)
        assertEquals(1.25f, beforeHabit.priorityBoost, 0.001f)
        val beforeScore = ForgettingModelEngine.calculateForgetfulnessScore(beforeHabit.forgetCount, beforeHabit.confirmCount)
        assertEquals(4.5f, beforeScore, 0.001f)

        // --- User Interaction: Tap [Mark as Ready] ---
        // 3a. Update item status to ready in Room DB
        val idCardItem = fakeDao.items.find { it.itemName == itemName }!!
        fakeDao.updateItemStatus(idCardItem.id, true)
        assertTrue(fakeDao.getItemById(idCardItem.id)!!.isReady)

        // 3b. Call recordItemConfirmed
        val updatedHabit = ForgettingModelEngine.recordItemConfirmed(fakeDao, itemName, targetModeId)

        // 3c. Verify confirmCount incremented in Room DB
        assertEquals(1, updatedHabit.confirmCount)
        assertEquals(3, updatedHabit.forgetCount)

        // 3d. Verify priority boost de-escalation (-0.10f from 1.25f = 1.15f)
        assertEquals(1.15f, updatedHabit.priorityBoost, 0.001f)

        // 3e. Verify score reduction: (3 * 1.5) - (1 * 0.3) = 4.2f (down from 4.5f)
        val updatedScore = ForgettingModelEngine.calculateForgetfulnessScore(updatedHabit.forgetCount, updatedHabit.confirmCount)
        assertEquals(4.2f, updatedScore, 0.001f)
        assertTrue("Score must strictly decrease after user confirmation", updatedScore < beforeScore)

        // 3f. Further consecutive confirmations continue recovering the score
        val secondConfirm = ForgettingModelEngine.recordItemConfirmed(fakeDao, itemName, targetModeId)
        assertEquals(2, secondConfirm.confirmCount)
        val secondScore = ForgettingModelEngine.calculateForgetfulnessScore(secondConfirm.forgetCount, secondConfirm.confirmCount)
        assertEquals(3.9f, secondScore, 0.001f) // 4.5 - 0.6 = 3.9f
        assertEquals(1.05f, secondConfirm.priorityBoost, 0.001f)

        // 3g. Verify item moves out of missing items list
        val refreshedItems = fakeDao.getItemsForMode(targetModeId)
        val remainingMissing = refreshedItems.filter { !it.isReady && !it.isWaived }
        assertFalse("ID Card must no longer appear in missing items once verified", remainingMissing.any { it.itemName == itemName })
    }

    // =========================================================================
    // 4. Mathematical Precision of Habit Formula
    // =========================================================================

    @Test
    fun testHabitMathFormulaCalculations() {
        // Zero state
        assertEquals(0.0f, ForgettingModelEngine.calculateForgetfulnessScore(0, 0), 0.001f)

        // Floor at 0.0f: confirmations exceeding forgets must not create negative score
        assertEquals(0.0f, ForgettingModelEngine.calculateForgetfulnessScore(0, 10), 0.001f)
        assertEquals(0.0f, ForgettingModelEngine.calculateForgetfulnessScore(1, 10), 0.001f)

        // Formula: Score = (1.5 * forgetCount) - (0.3 * confirmCount)
        // 4 forgets, 2 confirms: (4 * 1.5) - (2 * 0.3) = 6.0 - 0.6 = 5.4f
        assertEquals(5.4f, ForgettingModelEngine.calculateForgetfulnessScore(4, 2), 0.001f)

        // 3 forgets, 15 confirms: 4.5 - 4.5 = 0.0f (Full score redemption)
        assertEquals(0.0f, ForgettingModelEngine.calculateForgetfulnessScore(3, 15), 0.001f)

        // Escalation threshold logic
        assertEquals(RiskLevel.NORMAL, ForgettingModelEngine.getRiskLevel(0))
        assertEquals(RiskLevel.NORMAL, ForgettingModelEngine.getRiskLevel(1))
        assertEquals(RiskLevel.NORMAL, ForgettingModelEngine.getRiskLevel(2))
        assertEquals(RiskLevel.HIGH_RISK, ForgettingModelEngine.getRiskLevel(3))
        assertEquals(RiskLevel.HIGH_RISK, ForgettingModelEngine.getRiskLevel(10))
    }

    // =========================================================================
    // 5. Geofence Exit Intent Routing & Selective Evaluation
    // =========================================================================

    @Test
    fun testSelectiveEvaluationOnlyPenalizesUncheckedItemsDuringDeparture() = runBlocking {
        val targetModeId = 1

        // Initial state:
        // ID Card -> ready = false
        // Laptop -> ready = true
        // Water Bottle -> ready = false
        // Watch -> ready = false

        ForgettingModelEngine.handleGeofenceDeparture(fakeDao, targetModeId, recordAsConfirmed = true)

        val idCardHabit = fakeDao.getHabit("ID Card", targetModeId)
        val waterHabit = fakeDao.getHabit("Water Bottle", targetModeId)
        val watchHabit = fakeDao.getHabit("Watch", targetModeId)
        val laptopHabit = fakeDao.getHabit("Laptop", targetModeId)

        assertNotNull(idCardHabit)
        assertNotNull(waterHabit)
        assertNotNull(watchHabit)
        assertNull(laptopHabit)

        assertEquals(1, idCardHabit!!.forgetCount)
        assertEquals(1, waterHabit!!.forgetCount)
        assertEquals(1, watchHabit!!.forgetCount)
    }
}
