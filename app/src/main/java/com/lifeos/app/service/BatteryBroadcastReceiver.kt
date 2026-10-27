package com.lifeos.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import com.lifeos.app.engine.BatteryBudgetManager

/**
 * Manifest-registered receiver for OS and ADB low-battery and power-state broadcasts.
 */
class BatteryBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG_MODULE5, "LIFEOS_MODULE5: Battery Broadcast Received -> Action: $action")

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = pm?.isPowerSaveMode ?: false

        when (action) {
            Intent.ACTION_BATTERY_LOW,
            "com.lifeos.app.ACTION_BATTERY_LOW",
            "android.intent.action.BATTERY_LOW" -> {
                val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, intent.getIntExtra("level", 12))
                val finalLevel = if (lvl in 0..100) lvl else 12
                BatteryBudgetManager.setSimulatedBattery(finalLevel, isPowerSave)
            }
            Intent.ACTION_BATTERY_OKAY -> {
                BatteryBudgetManager.setSimulatedBattery(80, isPowerSave)
            }
            "com.lifeos.app.ACTION_BATTERY_UPDATE" -> {
                val lvl = intent.getIntExtra("level", 12)
                val powerSave = intent.getBooleanExtra("power_save", false)
                BatteryBudgetManager.setSimulatedBattery(lvl, powerSave)
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scl = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (lvl >= 0 && scl > 0) ((lvl * 100) / scl) else 100
                BatteryBudgetManager.setSimulatedBattery(pct, isPowerSave)
            }
        }
    }

    companion object {
        private const val TAG_MODULE5 = "LIFEOS_MODULE5"
    }
}
