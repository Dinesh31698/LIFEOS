package com.lifeos.app.engine

import android.content.Context
import android.util.Log
import com.lifeos.app.data.EventEntity
import com.lifeos.app.data.ItemHabitEntity
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.data.LifeOsDao
import com.lifeos.app.service.AntiSpamNotificationEngine

/**
 * Enumeration of departure and readiness relevance states.
 * Connects time-until-event, physical geofence departure, and user schedule.
 */
enum class DepartureRelevance(val label: String) {
    TOO_EARLY("Too early for departure reminder (> 45m before event)"),
    PREPARATION_WINDOW("Preparation window active (31m to 45m before event)"),
    DEPARTURE_WINDOW("Departure window active (11m to 30m before event)"),
    IMMINENT_DEPARTURE("Imminent departure window active (0m to 10m before event)"),
    PAST_EVENT("Event has already started or concluded"),
    PHYSICAL_DEPARTURE("Physical geofence departure detected"),
    NO_EVENT_IDLE("No upcoming event scheduled and not departing")
}

/**
 * Comprehensive decision output from the Smart Departure & Readiness Engine.
 */
data class SmartReminderDecision(
    val shouldNotify: Boolean,
    val shouldCancelExisting: Boolean,
    val relevance: DepartureRelevance,
    val title: String?,
    val content: String?,
    val prioritizedMissingItems: List<String>,
    val readinessPercentage: Int,
    val suppressionReason: String? = null
)

object DepartureReminderEngine {

    const val TAG = "LIFEOS_REMINDER"

    /**
     * Determines temporal relevance with respect to an upcoming event or physical departure.
     *
     * Window Definitions:
     * - Physical Geofence Exit: PHYSICAL_DEPARTURE (immediate relevance)
     * - No Event & No Departure: NO_EVENT_IDLE
     * - Delta < 0 ms (Event started): PAST_EVENT
     * - Delta <= 10 mins: IMMINENT_DEPARTURE
     * - Delta <= 30 mins: DEPARTURE_WINDOW
     * - Delta <= 45 mins: PREPARATION_WINDOW
     * - Delta > 45 mins: TOO_EARLY (e.g. 1 hour, 3 hours before)
     */
    fun evaluateDepartureRelevance(
        event: EventEntity?,
        isGeofenceExit: Boolean,
        currentTime: Long = System.currentTimeMillis()
    ): DepartureRelevance {
        if (isGeofenceExit) {
            return DepartureRelevance.PHYSICAL_DEPARTURE
        }
        if (event == null) {
            return DepartureRelevance.NO_EVENT_IDLE
        }
        if (event.isGhostMode || event.title.contains("(Cancelled)", ignoreCase = true)) {
            return DepartureRelevance.PAST_EVENT
        }
        val deltaMs = event.startTime - currentTime
        val deltaMinutes = deltaMs / 60_000L

        return when {
            deltaMs < 0L -> DepartureRelevance.PAST_EVENT
            deltaMinutes <= 10L -> DepartureRelevance.IMMINENT_DEPARTURE
            deltaMinutes <= 30L -> DepartureRelevance.DEPARTURE_WINDOW
            deltaMinutes <= 45L -> DepartureRelevance.PREPARATION_WINDOW
            else -> DepartureRelevance.TOO_EARLY
        }
    }

    /**
     * Prioritizes missing checklist requirements using the habit and forgetting model:
     * 1. Frequently forgotten / high-risk items appear FIRST.
     * 2. Habit priority boost applied to regular items.
     * 3. Deterministic tie-breaking by ID.
     */
    fun prioritizeMissingItems(
        items: List<ItemRequirementEntity>,
        habits: List<ItemHabitEntity> = emptyList()
    ): List<ItemRequirementEntity> {
        val habitMap = habits.associateBy { it.itemName.lowercase() }
        return items.filter { !it.isReady && !it.isWaived && !it.isGhostMode }
            .sortedWith(
                compareByDescending<ItemRequirementEntity> { item ->
                    val habit = habitMap[item.itemName.lowercase()]
                    val evaluation = ForgettingModelEngine.evaluatePersonalizedRisk(habit)
                    val riskTierWeight = when (evaluation.riskLevel) {
                        PersonalizedRiskLevel.HIGH -> 3000f
                        PersonalizedRiskLevel.MEDIUM -> 2000f
                        PersonalizedRiskLevel.LOW -> 1000f
                        PersonalizedRiskLevel.UNKNOWN -> 1000f
                    }
                    val isLegacyHigh = habit != null && ForgettingModelEngine.isFrequentlyForgotten(habit)
                    val legacyWeight = if (isLegacyHigh) 500f else 0f
                    val boost = habit?.priorityBoost ?: 0.0f
                    riskTierWeight + legacyWeight + boost
                }.thenBy { it.id }
            )
    }

    /**
     * Synthesizes readiness, context confidence, departure relevance, and anti-spam rules
     * into a deterministic smart reminder decision.
     */
    fun evaluateSmartReminder(
        mode: LifeModeEntity?,
        items: List<ItemRequirementEntity>,
        habits: List<ItemHabitEntity> = emptyList(),
        event: EventEntity? = null,
        isGeofenceExit: Boolean = false,
        contextConfidence: ContextConfidenceLevel = ContextConfidenceLevel.HIGH,
        currentTime: Long = System.currentTimeMillis()
    ): SmartReminderDecision {
        val modeName = mode?.name ?: "Current Mode"

        // 1. Calculate base readiness
        val report = ReadinessEngine.calculateReadiness(mode, items, currentTime)
        val missingEntities = prioritizeMissingItems(items, habits)
        val missingNames = missingEntities.map { it.itemName }

        // 2. Evaluate Temporal / Physical Relevance
        val relevance = evaluateDepartureRelevance(event, isGeofenceExit, currentTime)

        // Safe Mode Emergency Kill-Switch: Suppress all automated departure notifications
        if (SafeModeManager.isSafeModeActive) {
            return SmartReminderDecision(
                shouldNotify = false,
                shouldCancelExisting = false,
                relevance = relevance,
                title = null,
                content = null,
                prioritizedMissingItems = missingNames,
                readinessPercentage = report.percentage,
                suppressionReason = "Safe Mode is ACTIVE; automated departure notifications suppressed."
            )
        }

        // Cancelled or Unconfirmed Ghost Event Protection
        if (event != null && (event.isGhostMode || event.title.contains("(Cancelled)", ignoreCase = true))) {
            return SmartReminderDecision(
                shouldNotify = false,
                shouldCancelExisting = true,
                relevance = DepartureRelevance.PAST_EVENT,
                title = null,
                content = null,
                prioritizedMissingItems = missingNames,
                readinessPercentage = report.percentage,
                suppressionReason = "Event '${event.title}' is cancelled or unconfirmed; departure reminders cancelled."
            )
        }

        // 3. Context Confidence Gates (Phase 4 integration)
        when (contextConfidence) {
            ContextConfidenceLevel.LOW, ContextConfidenceLevel.NO_CONTEXT -> {
                return SmartReminderDecision(
                    shouldNotify = false,
                    shouldCancelExisting = false,
                    relevance = relevance,
                    title = null,
                    content = null,
                    prioritizedMissingItems = missingNames,
                    readinessPercentage = report.percentage,
                    suppressionReason = "Context confidence is ${contextConfidence.name}; departure reminders suppressed to prevent false alarms."
                )
            }
            ContextConfidenceLevel.MEDIUM -> {
                return SmartReminderDecision(
                    shouldNotify = false,
                    shouldCancelExisting = false,
                    relevance = relevance,
                    title = null,
                    content = null,
                    prioritizedMissingItems = missingNames,
                    readinessPercentage = report.percentage,
                    suppressionReason = "Context confidence is MEDIUM; mode is only proposed, intrusive departure notifications suppressed."
                )
            }
            ContextConfidenceLevel.HIGH -> {
                // High confidence allows smart reminders to proceed
            }
        }

        // 4. Checklist State Gates
        if (report.totalCount == 0 && report.waivedCount == 0) {
            return SmartReminderDecision(
                shouldNotify = false,
                shouldCancelExisting = true,
                relevance = relevance,
                title = null,
                content = null,
                prioritizedMissingItems = emptyList(),
                readinessPercentage = -1,
                suppressionReason = "No requirements configured for $modeName."
            )
        }

        if (report.percentage >= 100) {
            return SmartReminderDecision(
                shouldNotify = false,
                shouldCancelExisting = true,
                relevance = relevance,
                title = null,
                content = null,
                prioritizedMissingItems = emptyList(),
                readinessPercentage = 100,
                suppressionReason = "User is 100% ready for $modeName (all items packed)."
            )
        }

        // 5. Relevance Suppression Checks
        when (relevance) {
            DepartureRelevance.TOO_EARLY -> {
                val minsUntil = ((event!!.startTime - currentTime) / 60_000L).coerceAtLeast(1)
                return SmartReminderDecision(
                    shouldNotify = false,
                    shouldCancelExisting = false,
                    relevance = relevance,
                    title = null,
                    content = null,
                    prioritizedMissingItems = missingNames,
                    readinessPercentage = report.percentage,
                    suppressionReason = "Too early for departure reminder (~${minsUntil}m until event)."
                )
            }
            DepartureRelevance.PAST_EVENT -> {
                return SmartReminderDecision(
                    shouldNotify = false,
                    shouldCancelExisting = true,
                    relevance = relevance,
                    title = null,
                    content = null,
                    prioritizedMissingItems = missingNames,
                    readinessPercentage = report.percentage,
                    suppressionReason = "Scheduled event has already started or ended."
                )
            }
            DepartureRelevance.NO_EVENT_IDLE -> {
                return SmartReminderDecision(
                    shouldNotify = false,
                    shouldCancelExisting = false,
                    relevance = relevance,
                    title = null,
                    content = null,
                    prioritizedMissingItems = missingNames,
                    readinessPercentage = report.percentage,
                    suppressionReason = "No upcoming event scheduled and no departure geofence triggered."
                )
            }
            DepartureRelevance.PREPARATION_WINDOW,
            DepartureRelevance.DEPARTURE_WINDOW,
            DepartureRelevance.IMMINENT_DEPARTURE,
            DepartureRelevance.PHYSICAL_DEPARTURE -> {
                // Legitimate reminder window
            }
        }

        // 6. Synthesize Concise, Actionable Notification Messaging
        val title: String = when (relevance) {
            DepartureRelevance.PHYSICAL_DEPARTURE -> "Leaving for $modeName?"
            DepartureRelevance.IMMINENT_DEPARTURE -> "$modeName in 10 min"
            DepartureRelevance.DEPARTURE_WINDOW -> {
                val mins = ((event!!.startTime - currentTime) / 60_000L).coerceAtLeast(1)
                "$modeName in $mins min"
            }
            DepartureRelevance.PREPARATION_WINDOW -> "$modeName preparation"
            else -> "$modeName reminder"
        }

        val content: String = when {
            missingNames.size == 1 -> {
                "Don't forget your ${missingNames[0]}!"
            }
            missingNames.size == 2 -> {
                "Still missing: ${missingNames[0]} and ${missingNames[1]}."
            }
            else -> {
                "Readiness: ${report.percentage}%. Still missing: ${missingNames.joinToString(", ")}."
            }
        }

        // 7. Anti-Spam Cooldown & Suppression Check
        val alertKey = "SMART_REMINDER_${modeName.uppercase().replace(" ", "_")}"
        val antiSpamCheck = AntiSpamNotificationEngine.checkAntiSpam(
            modeName = modeName,
            readinessPercentage = report.percentage,
            alertKey = alertKey,
            currentTime = currentTime
        )

        if (antiSpamCheck.isSuppressed) {
            return SmartReminderDecision(
                shouldNotify = false,
                shouldCancelExisting = false,
                relevance = relevance,
                title = title,
                content = content,
                prioritizedMissingItems = missingNames,
                readinessPercentage = report.percentage,
                suppressionReason = antiSpamCheck.reason
            )
        }

        return SmartReminderDecision(
            shouldNotify = true,
            shouldCancelExisting = false,
            relevance = relevance,
            title = title,
            content = content,
            prioritizedMissingItems = missingNames,
            readinessPercentage = report.percentage,
            suppressionReason = null
        )
    }

    /**
     * Executes end-to-end evaluation and dispatches or cancels notifications in Android NotificationManager.
     */
    suspend fun syncReminderWithNotificationManager(
        context: Context,
        dao: LifeOsDao,
        modeId: Int,
        event: EventEntity? = null,
        isGeofenceExit: Boolean = false,
        contextConfidence: ContextConfidenceLevel = ContextConfidenceLevel.HIGH,
        currentTime: Long = System.currentTimeMillis()
    ): SmartReminderDecision {
        val mode = dao.getModeById(modeId)
        val items = dao.getItemsForMode(modeId)
        val habits = dao.getHabitsForMode(modeId)

        val decision = evaluateSmartReminder(
            mode = mode,
            items = items,
            habits = habits,
            event = event,
            isGeofenceExit = isGeofenceExit,
            contextConfidence = contextConfidence,
            currentTime = currentTime
        )

        val modeName = mode?.name ?: "Current Mode"
        val alertKey = "SMART_REMINDER_${modeName.uppercase().replace(" ", "_")}"

        if (decision.shouldCancelExisting) {
            AntiSpamNotificationEngine.cancelAlertForMode(context, modeId)
            Log.i(TAG, "Smart Reminder Cancelled for Mode '$modeName' (id=$modeId): ${decision.suppressionReason}")
        } else if (decision.shouldNotify && decision.title != null && decision.content != null) {
            AntiSpamNotificationEngine.postSystemNotification(
                context = context,
                title = decision.title,
                content = decision.content,
                modeId = modeId,
                modeName = modeName,
                alertKey = alertKey,
                notificationId = 1001 + modeId,
                currentTime = currentTime
            )
            Log.i(TAG, "Smart Reminder Dispatched for Mode '$modeName' (id=$modeId): '${decision.title}' - '${decision.content}'")
        } else {
            Log.d(TAG, "Smart Reminder Suppressed for Mode '$modeName' (id=$modeId): ${decision.suppressionReason}")
        }

        return decision
    }
}
