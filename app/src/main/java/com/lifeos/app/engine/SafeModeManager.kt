package com.lifeos.app.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SafeModeManager {

    private const val TAG = "LIFEOS_SAFEMODE"
    const val TAG_MODULE6 = "LIFEOS_MODULE6"
    private const val PREFS_NAME = "lifeos_safe_mode_prefs"
    private const val KEY_SAFE_MODE = "is_safe_mode_enabled"

    private val _isSafeModeEnabled = MutableStateFlow(false)
    val isSafeModeEnabled = _isSafeModeEnabled.asStateFlow()

    private var sharedPrefs: SharedPreferences? = null

    /**
     * Initializes persistence from SharedPreferences on app cold start.
     */
    fun init(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs = prefs
            val savedState = prefs.getBoolean(KEY_SAFE_MODE, false)
            _isSafeModeEnabled.value = savedState
            Log.i(TAG, "SafeModeManager initialized from persistent storage: isSafeMode = $savedState")
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize SafeModeManager SharedPreferences: ${e.message}")
        }
    }

    var isSafeModeActive: Boolean
        get() = _isSafeModeEnabled.value
        set(value) {
            setSafeMode(value)
        }

    fun setSafeMode(enabled: Boolean) {
        _isSafeModeEnabled.value = enabled
        sharedPrefs?.edit()?.putBoolean(KEY_SAFE_MODE, enabled)?.apply()
        Log.i(TAG, "Autonomous Action Kill-Switch (Safe Mode) set to: ${if (enabled) "ENABLED" else "DISABLED"}")
        Log.i(TAG_MODULE6, "LIFEOS_MODULE6: Safe Mode State -> ${if (enabled) "ACTIVE" else "INACTIVE"}")
    }

    fun toggleSafeMode() {
        setSafeMode(!_isSafeModeEnabled.value)
    }

    /**
     * For unit test teardown.
     */
    fun resetForTesting() {
        _isSafeModeEnabled.value = false
        sharedPrefs?.edit()?.clear()?.apply()
    }

    /**
     * Determines whether dynamic AI plan mutations & item waiving are permitted.
     * When Safe Mode is active, all automatic mutations are blocked.
     */
    fun canMutatePlans(): Boolean {
        return !_isSafeModeEnabled.value
    }

    /**
     * Determines whether incoming high-confidence items may be auto-committed.
     * When Safe Mode is active, items are isolated or held for manual confirmation.
     */
    fun canAutoPromoteGhostItems(): Boolean {
        return !_isSafeModeEnabled.value
    }

    /**
     * Phase 8A: Determines whether autonomous background habit learning or forgetting recording is allowed.
     * When Safe Mode is active, all unconfirmed autonomous forgetting updates are suppressed.
     */
    fun canLearnAutonomously(): Boolean {
        return !_isSafeModeEnabled.value
    }
}
