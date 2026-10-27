package com.lifeos.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lifeos.app.MainActivity
import com.lifeos.app.data.LifeOsDao
import com.lifeos.app.engine.ReadinessEngine
import com.lifeos.app.engine.ReadinessReport
import java.util.concurrent.ConcurrentHashMap

enum class AntiSpamDecision {
    ALLOWED,
    SUPPRESSED_100_PERCENT,
    SUPPRESSED_COOLDOWN
}

data class AntiSpamCheckResult(
    val decision: AntiSpamDecision,
    val isSuppressed: Boolean,
    val reason: String
)

data class DepartureEvaluationResult(
    val modeId: Int,
    val modeName: String,
    val report: ReadinessReport,
    val antiSpamResult: AntiSpamCheckResult,
    val consolidatedText: String?,
    val isNotificationPosted: Boolean
)

object AntiSpamNotificationEngine {

    const val TAG = "LIFEOS_ANTISPAM"
    const val TAG_MODULE4 = "LIFEOS_MODULE4"
    const val TAG_MODULE2 = "LIFEOS_MODULE2"
    const val CHANNEL_ID = "lifeos_readiness_alerts"
    const val CHANNEL_NAME = "LIFEOS Readiness Alerts"
    const val COOLDOWN_MILLIS = 15 * 60 * 1000L // 15 minutes cooldown

    // Cache of last posted timestamps by alertKey
    val lastAlertTimestamps = ConcurrentHashMap<String, Long>()

    /**
     * Diagnostic logging helper for readiness evaluations.
     * LIFEOS_MODULE4: Readiness Evaluated for '[ModeName]' -> [Score]% ([ReadyCount]/[TotalCount] Ready)
     */
    fun logReadinessEvaluated(modeName: String, percentage: Int, readyCount: Int, totalCount: Int) {
        Log.i(TAG_MODULE4, "LIFEOS_MODULE4: Readiness Evaluated for '$modeName' -> $percentage% ($readyCount/$totalCount Ready)")
    }

    /**
     * Checks anti-spam rules and returns detailed decision and status.
     * Rules:
     *  1. 100% Suppression: If readinessPercentage >= 100%, suppress alert.
     *  2. Cooldown Window: If alert for alertKey was posted < 15 mins ago, suppress duplicate.
     * Emits: LIFEOS_MODULE4: Anti-Spam Filter Check -> [ALLOWED / SUPPRESSED_100_PERCENT / SUPPRESSED_COOLDOWN]
     */
    fun checkAntiSpam(
        modeName: String,
        readinessPercentage: Int,
        alertKey: String,
        currentTime: Long = System.currentTimeMillis()
    ): AntiSpamCheckResult {
        // 1. Context-Aware Alert Suppression: 100% Readiness
        if (readinessPercentage >= 100) {
            val reason = "Suppressed redundant alert: User is at 100% readiness for $modeName (all items packed)."
            Log.d(TAG, reason)
            Log.i(TAG_MODULE4, "LIFEOS_MODULE4: Anti-Spam Filter Check -> SUPPRESSED_100_PERCENT")
            Log.i(TAG_MODULE2, "LIFEOS_MODULE2: Anti-Spam Filter Check -> SUPPRESSED_100_PERCENT (Key: $alertKey)")
            return AntiSpamCheckResult(AntiSpamDecision.SUPPRESSED_100_PERCENT, true, reason)
        }

        // 2. Cooldown De-duplication: Prevent repetitive alerts for the same item/event within 15 minutes
        val lastTime = lastAlertTimestamps[alertKey] ?: 0L
        if (currentTime - lastTime < COOLDOWN_MILLIS) {
            val remainingMins = ((COOLDOWN_MILLIS - (currentTime - lastTime)) / 60000) + 1
            val reason = "Suppressed duplicate alert for '$alertKey': Cooldown active (~${remainingMins}m remaining)."
            Log.d(TAG, reason)
            Log.i(TAG_MODULE4, "LIFEOS_MODULE4: Anti-Spam Filter Check -> SUPPRESSED_COOLDOWN")
            Log.i(TAG_MODULE2, "LIFEOS_MODULE2: Anti-Spam Filter Check -> SUPPRESSED_COOLDOWN (Key: $alertKey, Remaining: ~${remainingMins}m)")
            return AntiSpamCheckResult(AntiSpamDecision.SUPPRESSED_COOLDOWN, true, reason)
        }

        Log.i(TAG_MODULE4, "LIFEOS_MODULE4: Anti-Spam Filter Check -> ALLOWED")
        Log.i(TAG_MODULE2, "LIFEOS_MODULE2: Anti-Spam Filter Check -> ALLOWED (Key: $alertKey)")
        return AntiSpamCheckResult(AntiSpamDecision.ALLOWED, false, "Allowed")
    }

    /**
     * Backwards-compatible wrapper for shouldSuppressAlert.
     */
    fun shouldSuppressAlert(
        modeName: String,
        readinessPercentage: Int,
        alertKey: String
    ): Pair<Boolean, String> {
        val result = checkAntiSpam(modeName, readinessPercentage, alertKey)
        return Pair(result.isSuppressed, result.reason)
    }

    /**
     * Consolidates multiple missing items into a single unified departure alert.
     * Legacy signature maintained for compatibility.
     */
    fun consolidateDepartureAlert(
        modeName: String,
        pendingItems: List<String>
    ): String {
        return when {
            pendingItems.isEmpty() -> {
                "Leaving for $modeName? You're 100% ready! All items are packed."
            }
            pendingItems.size == 1 -> {
                "Leaving for $modeName? Don't forget your ${pendingItems.first()}!"
            }
            pendingItems.size == 2 -> {
                "Leaving for $modeName? Don't forget: ${pendingItems[0]} and ${pendingItems[1]}."
            }
            else -> {
                val leading = pendingItems.take(2).joinToString(", ")
                val remainingCount = pendingItems.size - 2
                "Leaving for $modeName? Don't forget: $leading + $remainingCount more items."
            }
        }
    }

    /**
     * Module 4: Consolidated Departure Alert Generator.
     * Example: "Leaving for College? Readiness: 67%. Still missing: ID Card, DBMS Record."
     */
    fun consolidateDepartureAlert(
        modeName: String,
        readinessPercentage: Int,
        pendingItems: List<String>,
        waivedCount: Int = 0
    ): String {
        val waivedNotice = if (waivedCount > 0) " ($waivedCount waived due to schedule changes)" else ""
        return if (pendingItems.isEmpty()) {
            "Leaving for $modeName? Readiness: 100%. All items are packed.$waivedNotice"
        } else {
            "Leaving for $modeName? Readiness: $readinessPercentage%. Still missing: ${pendingItems.joinToString(", ")}$waivedNotice."
        }
    }

    fun consolidateDepartureAlert(report: ReadinessReport): String {
        return consolidateDepartureAlert(
            modeName = report.modeName,
            readinessPercentage = report.percentage,
            pendingItems = report.pendingItems,
            waivedCount = report.waivedCount
        )
    }

    /**
     * Evaluates mode readiness and triggers a consolidated departure alert upon geofence exit.
     */
    suspend fun triggerDepartureAlert(
        context: Context,
        dao: LifeOsDao,
        modeId: Int,
        modeName: String? = null,
        currentTime: Long = System.currentTimeMillis()
    ): DepartureEvaluationResult {
        val mode = dao.getModeById(modeId)
        val resolvedModeName = modeName ?: mode?.name ?: "Current Mode"
        val items = dao.getItemsForMode(modeId)

        // 1. Readiness Evaluation
        val report = ReadinessEngine.calculateReadiness(mode, items)

        // 2. Anti-Spam Check
        val alertKey = if (resolvedModeName.equals("College", ignoreCase = true) || modeId == 1) {
            "GEOFENCE_EXIT_COLLEGE"
        } else {
            "GEOFENCE_EXIT_${resolvedModeName.uppercase().replace(" ", "_")}"
        }
        val antiSpamResult = checkAntiSpam(resolvedModeName, report.percentage, alertKey, currentTime)

        if (antiSpamResult.isSuppressed) {
            return DepartureEvaluationResult(
                modeId = modeId,
                modeName = resolvedModeName,
                report = report,
                antiSpamResult = antiSpamResult,
                consolidatedText = null,
                isNotificationPosted = false
            )
        }

        // 3. Consolidated Departure Alert Generator
        val consolidatedText = consolidateDepartureAlert(
            modeName = resolvedModeName,
            readinessPercentage = report.percentage,
            pendingItems = report.pendingItems,
            waivedCount = report.waivedCount
        )

        // 4. Log Departure Alert Posted
        Log.i(TAG_MODULE4, "LIFEOS_MODULE4: Departure Alert Posted -> '$consolidatedText'")
        Log.i(TAG_MODULE2, "LIFEOS_MODULE2: Departure Alert Posted -> '$consolidatedText' (Key: $alertKey)")

        // 5. Post System Notification with PendingIntent opening HomeDashboardScreen pre-selected
        val isPosted = postSystemNotification(
            context = context,
            title = "Leaving for $resolvedModeName?",
            content = consolidatedText,
            modeId = modeId,
            modeName = resolvedModeName,
            alertKey = alertKey,
            currentTime = currentTime
        )

        return DepartureEvaluationResult(
            modeId = modeId,
            modeName = resolvedModeName,
            report = report,
            antiSpamResult = antiSpamResult,
            consolidatedText = consolidatedText,
            isNotificationPosted = isPosted
        )
    }

    /**
     * Posts a system notification with an intent opening MainActivity on HomeDashboardScreen with target mode.
     */
    fun postSystemNotification(
        context: Context,
        title: String,
        content: String,
        modeId: Int,
        modeName: String,
        alertKey: String,
        notificationId: Int = 1001 + modeId,
        currentTime: Long = System.currentTimeMillis()
    ): Boolean {
        createNotificationChannel(context)

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Skipping notification display.")
                lastAlertTimestamps[alertKey] = currentTime
                return false
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("target_mode_id", modeId)
            putExtra("mode_name", modeName)
            putExtra("open_screen", "home")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            modeId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager?.notify(notificationId, builder.build())
        lastAlertTimestamps[alertKey] = currentTime
        Log.d(TAG, "Successfully posted notification [$notificationId]: '$title' - '$content'")
        return true
    }

    /**
     * Posts a system notification after checking cooldown and 100% readiness suppression.
     */
    fun postNotificationIfAllowed(
        context: Context,
        title: String,
        content: String,
        alertKey: String,
        readinessPercentage: Int,
        modeName: String,
        notificationId: Int = 1001,
        modeId: Int = 1
    ): Boolean {
        val check = checkAntiSpam(modeName, readinessPercentage, alertKey)
        if (check.isSuppressed) {
            Log.i(TAG, "Notification blocked by AntiSpam: ${check.reason}")
            return false
        }

        Log.i(TAG_MODULE4, "LIFEOS_MODULE4: Departure Alert Posted -> '$content'")
        return postSystemNotification(
            context = context,
            title = title,
            content = content,
            modeId = modeId,
            modeName = modeName,
            alertKey = alertKey,
            notificationId = notificationId
        )
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for LIFEOS context-aware readiness and departure alerts"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Clears cooldown history (useful for testing).
     */
    fun resetCooldowns() {
        lastAlertTimestamps.clear()
        Log.d(TAG, "Anti-spam cooldown history reset.")
    }

    /**
     * Cancels an active system notification from the Android notification tray.
     */
    fun cancelNotification(context: Context, notificationId: Int = 1001) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(notificationId)
        Log.d(TAG, "Successfully cancelled notification [$notificationId]")
    }

    /**
     * Cancels an active readiness/departure alert for the specified mode ID.
     */
    fun cancelAlertForMode(context: Context, modeId: Int) {
        cancelNotification(context, 1001 + modeId)
    }
}
