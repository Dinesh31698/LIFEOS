package com.lifeos.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lifeos.app.engine.DecisionGate
import com.lifeos.app.engine.DecisionResult
import com.lifeos.app.engine.LightweightEventFilter
import com.lifeos.app.engine.SafeModeManager
import com.lifeos.app.security.DataMinimizer
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TestNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.lifeos.app.SET_FIREWALL" || intent.hasExtra("set_channel")) {
            val channelName = intent.getStringExtra("channel")
            val enabled = intent.getBooleanExtra("enabled", false)
            val source = SourceType.entries.find { it.name.equals(channelName, ignoreCase = true) }
            if (source != null) {
                PermissionFirewall.setChannelAuthorized(source, enabled)
                Log.i("LIFEOS_MODULE6", "LIFEOS_MODULE6: Firewall Channel Updated -> ${source.name} = $enabled")
            }
            return
        }

        if (intent.action == "com.lifeos.app.SET_SAFE_MODE" || intent.hasExtra("safe_mode")) {
            val enabled = intent.getBooleanExtra("safe_mode", false)
            SafeModeManager.isSafeModeActive = enabled
            Log.i("LIFEOS_MODULE6", "LIFEOS_MODULE6: Safe Mode Toggled -> isSafeModeActive = $enabled")
            return
        }

        if (intent.action == "com.lifeos.app.TEST_BLE_SCAN" || intent.getStringExtra("action") == "ble_scan") {
            if (intent.hasExtra("firewall_enabled")) {
                val enabled = intent.getBooleanExtra("firewall_enabled", true)
                PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, enabled)
            }
            val device = intent.getStringExtra("device") ?: "Laptop Beacon"
            val rssi = if (intent.hasExtra("rssi")) intent.getIntExtra("rssi", -62) else -62
            val mockList = listOf(
                device to rssi,
                "Smartwatch" to -75,
                "Bluetooth Headphones" to null
            )
            val pendingBle = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = com.lifeos.app.data.LifeOsDatabase.getDatabase(context.applicationContext)
                    com.lifeos.app.engine.DevicePresenceScanner.executeMockScan(mockList, context)
                    com.lifeos.app.engine.DevicePresenceScanner.syncDeviceWithChecklist(db.lifeOsDao(), device, rssi, "Laptop")
                } finally {
                    pendingBle.finish()
                }
            }
            return
        }

        if (intent.action == "com.lifeos.app.TEST_CONTEXT_EVALUATION" || intent.getStringExtra("action") == "evaluate_context") {
            val locationHint = intent.getStringExtra("location_hint")
            val isEnter = intent.getBooleanExtra("is_enter", false)
            val isExit = intent.getBooleanExtra("is_exit", false)
            val pendingCtx = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = com.lifeos.app.data.LifeOsDatabase.getDatabase(context.applicationContext)
                    val result = com.lifeos.app.engine.ContextRecognitionEngine.evaluateAndApplyDecision(
                        dao = db.lifeOsDao(),
                        currentLocationHint = locationHint,
                        isGeofenceExit = isExit,
                        isGeofenceEnter = isEnter,
                        context = context
                    )
                    Log.i("LIFEOS_PHASE4", "PHASE4 Context Evaluated: Winner=${result.topCandidateMode?.name ?: "None"} (Score=${result.totalConfidence}, Decision=${result.actionDecision}, Signals=${result.matchedSignals.size})")
                } catch (e: Exception) {
                    Log.e("LIFEOS_PHASE4", "Error in TEST_CONTEXT_EVALUATION", e)
                } finally {
                    pendingCtx.finish()
                }
            }
            return
        }

        val text = intent.getStringExtra("text") ?: ""
        val sanitizedLog = DataMinimizer.stripPii(text)
        Log.d(TAG_MODULE1, "Raw Text Received -> '$sanitizedLog'")
        Log.d(TAG, "Standalone BroadcastReceiver caught ADB text: '$sanitizedLog'")

        // Module 1: Route through PermissionFirewall & SafeModeManager before parsing
        if (!PermissionFirewall.isSourceAuthorized(context, SourceType.NOTIFICATION_INGESTION)) {
            Log.w(TAG_MODULE1, "BLOCKED by Permission Firewall: Notification Ingestion channel is disabled.")
            Log.d(TAG_MODULE1, "Decision Gate -> DROP")
            return
        }

        if (!SafeModeManager.canAutoPromoteGhostItems()) {
            Log.i(TAG_MODULE1, "Safe Mode ACTIVE: Autonomous auto-commit suppressed. All items will route to Ghost Mode.")
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = LightweightEventFilter.filter(text, sourcePackage = "adb.test")
                val decisionGate = DecisionGate(context)
                when (val result = decisionGate.processPayload(payload)) {
                    is DecisionResult.AutoCommitted -> {
                        Log.d(TAG, "Successfully Auto-Committed: '${result.item.itemName}' (Confidence: ${(result.item.confidenceScore * 100).toInt()}%)")
                    }
                    is DecisionResult.GhostModeRouted -> {
                        Log.d(TAG, "Successfully routed to GHOST MODE: '${result.item.itemName}' (Confidence: ${(result.item.confidenceScore * 100).toInt()}%)")
                    }
                    is DecisionResult.ConsequenceApplied -> {
                        Log.d(TAG, "Successfully evaluated CONSEQUENCE: '${result.alert.title}' -> Action: '${result.alert.actionTaken}'")
                    }
                    is DecisionResult.ConflictDetected -> {
                        Log.w(TAG, "Conflict Detected: '${result.conflictAlert.activityName}' -> ${result.conflictAlert.reason}")
                    }
                    is DecisionResult.DuplicateDiscarded -> {
                        Log.d(TAG, "Duplicate Discarded: '${result.itemOrEventName}' (${result.reason})")
                    }
                    is DecisionResult.Ignored -> {
                        Log.d(TAG, "Payload ignored due to low confidence (< 0.50) or untrusted/irrelevant input")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing ADB text in DecisionGate", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "LIFEOS_TEST"
        private const val TAG_MODULE1 = "LIFEOS_MODULE1"
    }
}
