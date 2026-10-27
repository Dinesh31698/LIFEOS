package com.lifeos.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LifeNotificationListener : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val packageName = sbn?.packageName ?: "unknown"
        val extras = sbn?.notification?.extras ?: return
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val fullText = "$title $text".trim()

        val sanitizedLog = DataMinimizer.stripPii(fullText)
        Log.d(TAG_MODULE1, "Raw Text Received -> '$sanitizedLog'")
        Log.d(TAG, "Notification caught from [$packageName]: Title='${DataMinimizer.stripPii(title)}', Text='${DataMinimizer.stripPii(text)}'")

        // Module 1: Route through PermissionFirewall & SafeModeManager before parsing
        if (!PermissionFirewall.isSourceAuthorized(applicationContext, SourceType.NOTIFICATION_INGESTION)) {
            Log.w(TAG_MODULE1, "BLOCKED by Permission Firewall: Notification Ingestion channel is disabled.")
            Log.d(TAG_MODULE1, "Decision Gate -> DROP")
            return
        }

        if (!SafeModeManager.canAutoPromoteGhostItems()) {
            Log.i(TAG_MODULE1, "Safe Mode ACTIVE: Autonomous auto-commit suppressed. All items will route to Ghost Mode.")
        }

        scope.launch {
            try {
                val payload = LightweightEventFilter.filter(fullText, sourcePackage = packageName)
                val decisionGate = DecisionGate(applicationContext)
                when (val result = decisionGate.processPayload(payload)) {
                    is DecisionResult.AutoCommitted -> {
                        Log.d(TAG, "Auto-Committed notification item: '${result.item.itemName}'")
                    }
                    is DecisionResult.GhostModeRouted -> {
                        Log.d(TAG, "Routed notification item to Ghost Mode: '${result.item.itemName}'")
                    }
                    is DecisionResult.ConsequenceApplied -> {
                        Log.d(TAG, "Consequence applied from notification: '${result.alert.title}' -> '${result.alert.actionTaken}'")
                    }
                    is DecisionResult.ConflictDetected -> {
                        Log.w(TAG, "Conflict detected from notification: '${result.conflictAlert.activityName}' -> ${result.conflictAlert.reason}")
                    }
                    is DecisionResult.DuplicateDiscarded -> {
                        Log.d(TAG, "Duplicate notification discarded: '${result.itemOrEventName}' (${result.reason})")
                    }
                    is DecisionResult.Ignored -> {
                        Log.d(TAG, "Notification payload ignored by DecisionGate (< 0.50 confidence or untrusted/irrelevant)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process notification through AI Pipeline", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        private const val TAG = "LIFEOS_LISTENER"
        private const val TAG_MODULE1 = "LIFEOS_MODULE1"
    }
}