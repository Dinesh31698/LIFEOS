package com.lifeos.app

import com.lifeos.app.data.ItemHabitEntity
import com.lifeos.app.engine.*
import org.junit.Assert.*
import org.junit.Test

class Stage5And6UnitTest {

    // --- Stage 5: Personal Forgetting & Habit Learning Model Tests ---

    @Test
    fun testFrequentForgettingThreshold() {
        val nonEscalated = ItemHabitEntity(
            itemName = "Water Bottle",
            modeId = 1,
            forgetCount = 2
        )
        assertFalse(ForgettingModelEngine.isFrequentlyForgotten(nonEscalated))

        val escalated = ItemHabitEntity(
            itemName = "DBMS Record",
            modeId = 1,
            forgetCount = 3
        )
        assertTrue(ForgettingModelEngine.isFrequentlyForgotten(escalated))

        val highRisk = ItemHabitEntity(
            itemName = "Laptop & Charger",
            modeId = 1,
            forgetCount = 5
        )
        assertTrue(ForgettingModelEngine.isFrequentlyForgotten(highRisk))
    }

    @Test
    fun testForgetfulnessScoreCalculation() {
        // forgetCount = 2, confirmCount = 1 -> (2 * 1.5) - (1 * 0.3) = 2.7
        val score = ForgettingModelEngine.calculateForgetfulnessScore(2, 1)
        assertEquals(2.7f, score, 0.01f)

        // confirmCount higher -> floor at 0.0
        val cleanScore = ForgettingModelEngine.calculateForgetfulnessScore(0, 5)
        assertEquals(0.0f, cleanScore, 0.01f)
    }

    @Test
    fun testEscalationWarningMessage() {
        val warning = ForgettingModelEngine.getEscalationAlertMessage("DBMS Record", 3)
        assertTrue(warning.contains("HIGH FORGET RISK"))
        assertTrue(warning.contains("DBMS Record"))
        assertTrue(warning.contains("3 times"))
    }

    @Test
    fun testEvaluateEscalatedItems() {
        val items = listOf("ID Card", "DBMS Record", "Water Bottle")
        val habits = listOf(
            ItemHabitEntity(itemName = "ID Card", modeId = 1, forgetCount = 0, confirmCount = 10),
            ItemHabitEntity(itemName = "DBMS Record", modeId = 1, forgetCount = 4, confirmCount = 1)
        )

        val results = ForgettingModelEngine.evaluateEscalatedItems(items, habits)
        assertEquals(3, results.size)

        val idCardInfo = results.first { it.itemName == "ID Card" }
        assertFalse(idCardInfo.isEscalated)
        assertNull(idCardInfo.escalationWarning)

        val dbmsRecordInfo = results.first { it.itemName == "DBMS Record" }
        assertTrue(dbmsRecordInfo.isEscalated)
        assertNotNull(dbmsRecordInfo.escalationWarning)
    }

    // --- Stage 6: Device Presence & BLE Scanner Tests ---

    @Test
    fun testDevicePresenceConfidenceEvaluation() {
        // Strong RSSI >= -70 dBm
        val highResult = DevicePresenceScanner.evaluateSignal("Laptop Beacon", -60)
        assertEquals(PresenceConfidence.HIGH_CONFIDENCE, highResult.confidence)
        assertTrue(highResult.formattedMessage.contains("detected nearby"))
        assertTrue(highResult.formattedMessage.contains("Strong signal"))

        // Medium / Weak RSSI (-71 to -88 dBm)
        val lowResult = DevicePresenceScanner.evaluateSignal("Smartwatch", -80)
        assertEquals(PresenceConfidence.LOW_CONFIDENCE, lowResult.confidence)
        assertTrue(lowResult.formattedMessage.contains("signal is weak"))

        // Absent / Unreachable (< -88 dBm or null)
        val absentResult = DevicePresenceScanner.evaluateSignal("Headphones", null)
        assertEquals(PresenceConfidence.UNKNOWN, absentResult.confidence)
        assertTrue(absentResult.formattedMessage.contains("does not appear to be nearby"))

        val tooWeakResult = DevicePresenceScanner.evaluateSignal("Headphones", -95)
        assertEquals(PresenceConfidence.UNKNOWN, tooWeakResult.confidence)
    }

    // --- Stage 6: Battery Budget & Resource Adaptor Tests ---

    @Test
    fun testPowerProfileDetermination() {
        // Battery > 20% -> NORMAL
        val normalProfile = BatteryBudgetManager.determineProfile(85, false)
        assertEquals(PowerProfile.NORMAL, normalProfile)

        val normalEdge = BatteryBudgetManager.determineProfile(21, false)
        assertEquals(PowerProfile.NORMAL, normalEdge)

        // Battery 10%-20% -> SAVER
        val saverProfile = BatteryBudgetManager.determineProfile(15, false)
        assertEquals(PowerProfile.SAVER, saverProfile)

        val saverEdge = BatteryBudgetManager.determineProfile(10, false)
        assertEquals(PowerProfile.SAVER, saverEdge)

        // Battery < 10% -> CRITICAL
        val criticalProfile = BatteryBudgetManager.determineProfile(9, false)
        assertEquals(PowerProfile.CRITICAL, criticalProfile)

        // OS Power-Saving Mode overrides to CRITICAL
        val powerSaveOverride = BatteryBudgetManager.determineProfile(90, true)
        assertEquals(PowerProfile.CRITICAL, powerSaveOverride)
    }

    @Test
    fun testBatterySensorThrottlingRules() {
        BatteryBudgetManager.setSimulatedBattery(85, false)
        assertTrue(BatteryBudgetManager.canRunBleScan())
        assertTrue(BatteryBudgetManager.canRunBackgroundSync())

        BatteryBudgetManager.setSimulatedBattery(15, false)
        assertFalse(BatteryBudgetManager.canRunBleScan()) // BLE paused in saver
        assertTrue(BatteryBudgetManager.canRunBackgroundSync()) // background sync allowed in saver

        BatteryBudgetManager.setSimulatedBattery(5, false)
        assertFalse(BatteryBudgetManager.canRunBleScan()) // BLE paused in critical
        assertFalse(BatteryBudgetManager.canRunBackgroundSync()) // background sync paused in critical
    }

    // --- Stage 6: Replay Engine Tests ---

    @Test
    fun testInitialReplayEngineState() {
        ReplayEngine.resetSimulation()
        val state = ReplayEngine.replayState.value
        assertFalse(state.isRunning)
        assertEquals(0, state.currentStep)
        assertEquals(3, state.totalSteps)
        assertTrue(state.history.isEmpty())
    }
}
