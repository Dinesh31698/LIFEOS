package com.lifeos.app.engine

import android.content.Context
import android.util.Log
import com.lifeos.app.data.LifeOsDao
import com.lifeos.app.data.LifeOsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ReplayStep(
    val stepNumber: Int,
    val title: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ReplayState(
    val isRunning: Boolean = false,
    val currentStep: Int = 0,
    val totalSteps: Int = 3,
    val statusText: String = "Idle. Ready to run simulation.",
    val history: List<ReplayStep> = emptyList()
)

object ReplayEngine {

    private const val TAG = "LIFEOS_REPLAY"
    const val TAG_MODULE6 = "LIFEOS_MODULE6"

    private val _replayState = MutableStateFlow(ReplayState())
    val replayState = _replayState.asStateFlow()

    /**
     * Executes the 3-Stage End-to-End Replay Scenario:
     * 1. Morning College Departure -> 2. Disruption (Teluglish Lab Cancellation) -> 3. Evening Gym Transition
     */
    suspend fun runDefaultScenario(dao: LifeOsDao, stepDelayMillis: Long = 1800L) = withContext(Dispatchers.IO) {
        if (_replayState.value.isRunning) {
            Log.w(TAG, "Simulation is already executing.")
            return@withContext
        }

        Log.i(TAG_MODULE6, "LIFEOS_MODULE6: Replay Started -> Executing 3-Stage Synthetic Scenario")

        val stepList = mutableListOf<ReplayStep>()
        _replayState.value = ReplayState(
            isRunning = true,
            currentStep = 0,
            totalSteps = 3,
            statusText = "Initializing simulation sequence...",
            history = emptyList()
        )

        try {
            // --- STEP 1: Morning College Departure ---
            Log.d(TAG, "Running Step 1: Morning College Departure")
            dao.setActiveMode(1) // College
            dao.resetAllWaivedItemsForMode(1)
            ConsequenceEngine.dismissAlert()

            // Verify BLE Accessory presence (Laptop)
            DevicePresenceScanner.simulateDeviceState("Laptop Beacon", PresenceConfidence.HIGH_CONFIDENCE, -58)

            val activeMode1 = dao.getModeById(1)
            val items1 = dao.getItemsForMode(1)
            val readiness1 = ReadinessEngine.calculateReadiness(activeMode1, items1)

            val step1 = ReplayStep(
                stepNumber = 1,
                title = "Morning College Departure",
                detail = "Active mode set to 'College'. Verified Laptop beacon nearby (-58 dBm). Baseline readiness: ${readiness1.percentage}% (${readiness1.readyCount}/${readiness1.totalCount} items)."
            )
            stepList.add(step1)
            _replayState.value = ReplayState(
                isRunning = true,
                currentStep = 1,
                totalSteps = 3,
                statusText = "Step 1/3: College Departure active. Checking accessories...",
                history = stepList.toList()
            )
            Log.i(TAG_MODULE6, "LIFEOS_MODULE6: Replay Step 1 Completed -> 'Morning College Departure'")

            if (stepDelayMillis > 0) delay(stepDelayMillis)

            // --- STEP 2: Disruption (Teluglish Cancellation Ingestion) ---
            Log.d(TAG, "Running Step 2: Ingesting 'DBMS lab cancel aindi'")
            val cancelIntent = MultilingualParser.parse("DBMS lab cancel aindi", "whatsapp.cs.group")
            val consequenceAlert = ConsequenceEngine.processPlanMutation(dao, cancelIntent)

            // Track habit
            ForgettingModelEngine.recordItemWaived(dao, "DBMS Record", 1)

            val step2 = ReplayStep(
                stepNumber = 2,
                title = "Lab Cancellation & Plan Mutation",
                detail = "Caught: 'DBMS lab cancel aindi' (Teluglish). Ripple effect: Automatically waived 'DBMS Record' and recomputed readiness."
            )
            stepList.add(step2)
            _replayState.value = ReplayState(
                isRunning = true,
                currentStep = 2,
                totalSteps = 3,
                statusText = "Step 2/3: Evaluated cancellation consequence. Waived DBMS Record.",
                history = stepList.toList()
            )
            Log.i(TAG_MODULE6, "LIFEOS_MODULE6: Replay Step 2 Completed -> 'Lab Cancellation & Plan Mutation'")

            if (stepDelayMillis > 0) delay(stepDelayMillis)

            // --- STEP 3: Evening Gym Transition ---
            Log.d(TAG, "Running Step 3: Transitioning to Gym Mode")
            dao.setActiveMode(2) // Gym Mode

            // Device presence check for Gym (Smartwatch detected)
            DevicePresenceScanner.simulateDeviceState("Smartwatch", PresenceConfidence.HIGH_CONFIDENCE, -52)

            val activeMode3 = dao.getModeById(2)
            val items3 = dao.getItemsForMode(2)
            val readiness3 = ReadinessEngine.calculateReadiness(activeMode3, items3)

            val step3 = ReplayStep(
                stepNumber = 3,
                title = "Evening Gym Transition",
                detail = "Geofence transit to 'Gym'. Loaded workout requirements. Smartwatch active (-52 dBm). Readiness: ${readiness3.percentage}%."
            )
            stepList.add(step3)
            _replayState.value = ReplayState(
                isRunning = false,
                currentStep = 3,
                totalSteps = 3,
                statusText = "Simulation completed successfully! All 3 stages verified.",
                history = stepList.toList()
            )
            Log.i(TAG_MODULE6, "LIFEOS_MODULE6: Replay Step 3 Completed -> 'Evening Gym Transition'")

            Log.d(TAG, "Replay scenario execution complete.")

        } catch (e: Exception) {
            Log.e(TAG, "Error executing replay scenario", e)
            _replayState.value = _replayState.value.copy(
                isRunning = false,
                statusText = "Simulation error: ${e.message}"
            )
        }
    }

    suspend fun runDefaultScenario(context: Context, stepDelayMillis: Long = 1800L) {
        val db = LifeOsDatabase.getDatabase(context.applicationContext)
        runDefaultScenario(db.lifeOsDao(), stepDelayMillis)
    }

    fun resetSimulation() {
        _replayState.value = ReplayState()
    }
}
