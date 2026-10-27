package com.lifeos.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PowerProfile(val label: String, val description: String) {
    NORMAL("Normal", "Full BLE scanning + 1h TTL cleanup enabled."),
    SAVER("Battery Saver", "Paused BLE accessory scans; essential geofences only."),
    CRITICAL("Critical", "Paused all non-essential background tasks; serving local UI only.")
}

object BatteryBudgetManager {

    private const val TAG = "LIFEOS_BATTERY"
    const val TAG_MODULE5 = "LIFEOS_MODULE5"

    private val _currentBatteryLevel = MutableStateFlow(100)
    val currentBatteryLevel = _currentBatteryLevel.asStateFlow()

    private val _currentPowerProfile = MutableStateFlow(PowerProfile.NORMAL)
    val currentPowerProfile = _currentPowerProfile.asStateFlow()

    private var isReceiverRegistered = false

    /**
     * Determines power profile based on battery percentage and OS power-saver mode:
     * - NORMAL: Battery > 20%
     * - SAVER: 10% <= Battery <= 20%
     * - CRITICAL: Battery < 10% or OS Power Saver active
     */
    fun determineProfile(batteryPercentage: Int, isPowerSaveMode: Boolean = false): PowerProfile {
        return when {
            isPowerSaveMode || batteryPercentage < 10 -> PowerProfile.CRITICAL
            batteryPercentage in 10..20 -> PowerProfile.SAVER
            else -> PowerProfile.NORMAL
        }
    }

    /**
     * Checks if BLE scanning is permitted under the active battery budget.
     * Only permitted when active profile is NORMAL.
     */
    fun canRunBleScan(): Boolean {
        return _currentPowerProfile.value == PowerProfile.NORMAL
    }

    /**
     * Checks if periodic WorkManager background syncing is permitted.
     */
    fun canRunBackgroundSync(): Boolean {
        return _currentPowerProfile.value != PowerProfile.CRITICAL
    }

    /**
     * Initializes the OS Battery Intent listener.
     */
    fun initialize(context: Context) {
        if (isReceiverRegistered) return

        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isPowerSave = powerManager?.isPowerSaveMode ?: false

            val batteryFilter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction("com.lifeos.app.ACTION_BATTERY_LOW")
                addAction("com.lifeos.app.ACTION_BATTERY_UPDATE")
            }

            val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) ((level * 100) / scale) else 100

            updateState(pct, isPowerSave)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_BATTERY_CHANGED -> {
                            val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, intent.getIntExtra("level", -1))
                            val scl = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                            val batteryPct = if (lvl >= 0 && scl > 0) ((lvl * 100) / scl) else 100
                            val pm = c?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                            updateState(batteryPct, pm?.isPowerSaveMode ?: false)
                        }
                        Intent.ACTION_BATTERY_LOW,
                        "com.lifeos.app.ACTION_BATTERY_LOW",
                        "android.intent.action.BATTERY_LOW" -> {
                            val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, intent.getIntExtra("level", 12))
                            val finalLevel = if (lvl in 0..100) lvl else 12
                            val pm = c?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                            updateState(finalLevel, pm?.isPowerSaveMode ?: false)
                        }
                        Intent.ACTION_BATTERY_OKAY -> {
                            val pm = c?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                            updateState(80, pm?.isPowerSaveMode ?: false)
                        }
                        "com.lifeos.app.ACTION_BATTERY_UPDATE" -> {
                            val lvl = intent.getIntExtra("level", 12)
                            val powerSave = intent.getBooleanExtra("power_save", false)
                            updateState(lvl, powerSave)
                        }
                        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                            val pm = c?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                            updateState(_currentBatteryLevel.value, pm?.isPowerSaveMode ?: false)
                        }
                    }
                }
            }

            context.registerReceiver(receiver, batteryFilter)
            isReceiverRegistered = true
            Log.d(TAG, "BatteryBudgetManager registered successfully (Initial Level: $pct%, Profile: ${_currentPowerProfile.value})")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering Battery receiver", e)
        }
    }

    private fun updateState(level: Int, isPowerSave: Boolean) {
        val profile = determineProfile(level, isPowerSave)
        _currentBatteryLevel.value = level
        _currentPowerProfile.value = profile
        Log.d(TAG, "Battery Level: $level% | Active Profile: $profile (${profile.description})")

        // Diagnostic log: LIFEOS_MODULE5: Battery Profile Changed -> [NORMAL / SAVER / CRITICAL] | Level: [N]%
        Log.i(TAG_MODULE5, "LIFEOS_MODULE5: Battery Profile Changed -> ${profile.name} | Level: $level%")

        // Defect Resolution & Throttling: Automatically pause/stop BLE scans when entering SAVER or CRITICAL
        if (profile == PowerProfile.SAVER || profile == PowerProfile.CRITICAL) {
            DevicePresenceScanner.stopScan()
        }
    }

    /**
     * For Dev Tools and unit testing simulation.
     */
    fun setSimulatedBattery(level: Int, isPowerSave: Boolean = false) {
        updateState(level, isPowerSave)
    }

    /**
     * Cycles to next power profile for interactive UI testing.
     * NORMAL -> SAVER -> CRITICAL -> NORMAL
     */
    fun cycleNextProfile() {
        when (_currentPowerProfile.value) {
            PowerProfile.NORMAL -> setSimulatedBattery(15, false)
            PowerProfile.SAVER -> setSimulatedBattery(5, false)
            PowerProfile.CRITICAL -> setSimulatedBattery(100, false)
        }
    }

    /**
     * Sets a specific profile override with standard battery percentages.
     */
    fun setProfileOverride(profile: PowerProfile) {
        when (profile) {
            PowerProfile.NORMAL -> setSimulatedBattery(100, false)
            PowerProfile.SAVER -> setSimulatedBattery(15, false)
            PowerProfile.CRITICAL -> setSimulatedBattery(5, false)
        }
    }
}
