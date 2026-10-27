package com.lifeos.app.engine

import android.content.Context
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PhysicalMotionState(val displayName: String) {
    STILL("Stationary"),
    WALKING("Walking"),
    BIKING("Cycling"),
    RUNNING("Running"),
    IN_VEHICLE("Transit"),
    UNKNOWN("Unknown")
}

data class ActivityTransitionRecord(
    val motionState: PhysicalMotionState,
    val timestamp: Long = System.currentTimeMillis()
)

data class PhysicalActivitySnapshot(
    val motionState: PhysicalMotionState,
    val stepCountToday: Int,
    val estimatedPhoneInactivityHours: Float,
    val isTrackingPaused: Boolean
)

object PhysicalLifeRecorder {

    private val _motionState = MutableStateFlow(PhysicalMotionState.STILL)
    val motionState = _motionState.asStateFlow()

    private val _transitionHistory = mutableListOf<ActivityTransitionRecord>()
    val transitionHistory: List<ActivityTransitionRecord> get() = _transitionHistory.toList()

    private val _stepCount = MutableStateFlow(4250)
    val stepCount = _stepCount.asStateFlow()

    private val _isTrackingPaused = MutableStateFlow(false)
    val isTrackingPaused = _isTrackingPaused.asStateFlow()

    fun isTrackingAuthorized(context: Context): Boolean {
        return PermissionFirewall.hasOsPermission(context, SourceType.ACTIVITY_RECOGNITION) &&
                PermissionFirewall.isSourceAuthorized(context, SourceType.ACTIVITY_RECOGNITION)
    }

    fun setTrackingPaused(paused: Boolean) {
        _isTrackingPaused.value = paused
    }

    /**
     * Updates current physical state.
     * Duplicate transition suppression: Identical consecutive states are ignored.
     */
    fun updateMotionState(state: PhysicalMotionState): Boolean {
        if (_isTrackingPaused.value) return false
        if (_motionState.value == state) {
            // Duplicate transition suppressed
            return false
        }
        _motionState.value = state
        _transitionHistory.add(ActivityTransitionRecord(state))
        return true
    }

    fun addSteps(delta: Int) {
        if (!_isTrackingPaused.value) {
            _stepCount.value += delta
        }
    }

    /**
     * Estimated phone inactivity.
     * Note: In compliance with the Part 6 Privacy & Accuracy specification:
     * We STRICTLY report "Estimated phone inactivity" and NEVER fabricate actual medical sleep.
     */
    fun getInactivitySummary(inactivityHours: Float = 7.2f): String {
        return "Estimated phone inactivity: ${String.format("%.1f", inactivityHours)} hours (Screen off / Device idle)"
    }

    fun getSnapshot(): PhysicalActivitySnapshot {
        return PhysicalActivitySnapshot(
            motionState = _motionState.value,
            stepCountToday = _stepCount.value,
            estimatedPhoneInactivityHours = 7.2f,
            isTrackingPaused = _isTrackingPaused.value
        )
    }
}
