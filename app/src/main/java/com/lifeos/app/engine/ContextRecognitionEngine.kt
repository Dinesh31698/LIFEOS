package com.lifeos.app.engine

import android.content.Context
import android.util.Log
import com.lifeos.app.data.EventEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.data.LifeOsDao
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Standard confidence tiers for context recognition.
 */
enum class ContextConfidenceLevel(val label: String) {
    HIGH("High Confidence"),
    MEDIUM("Medium Confidence"),
    LOW("Low Confidence"),
    NO_CONTEXT("No Meaningful Context")
}

/**
 * Action decisions following context evaluation.
 */
enum class ContextActionDecision {
    AUTO_SWITCH,      // High confidence (>=0.75), non-conflicting, cooldown elapsed, SafeMode off
    PROPOSE,          // Medium confidence (0.50..0.74), SafeMode active, or manual override active
    RETAIN_CURRENT,   // Low confidence (<0.50), no context, or debounce in effect
    UNCERTAIN         // Conflicting candidates with near-identical scores; do not guess
}

/**
 * Detailed breakdown of individual weighted context signal scores.
 */
data class SignalBreakdown(
    val locationScore: Float,       // Weight 0.35
    val calendarScore: Float,       // Weight 0.25
    val timeScore: Float,           // Weight 0.20
    val habitScore: Float,          // Weight 0.10
    val deviceScore: Float,         // Weight 0.10
    val totalScore: Float,
    val matchedSignals: List<String>
)

/**
 * Complete evaluation result across all candidate Life Modes.
 */
data class MultiModeContextResult(
    val topCandidateMode: LifeModeEntity?,
    val confidenceLevel: ContextConfidenceLevel,
    val totalConfidence: Float,
    val candidateScores: Map<Int, Float>,
    val breakdowns: Map<Int, SignalBreakdown>,
    val isConflict: Boolean,
    val conflictModes: List<LifeModeEntity>,
    val matchedSignals: List<String>,
    val actionDecision: ContextActionDecision,
    val statusMessage: String
)

/**
 * Backward-compatible evaluation report used across Module 2 tests.
 */
data class ContextSignalEvaluation(
    val locationConfidence: Float,
    val timeConfidence: Float,
    val calendarConfidence: Float,
    val totalConfidence: Float,
    val inferredModeId: Int,
    val inferredModeName: String,
    val matchedSignals: List<String>,
    val confidenceLevel: ContextConfidenceLevel = when {
        totalConfidence >= 0.75f -> ContextConfidenceLevel.HIGH
        totalConfidence >= 0.50f -> ContextConfidenceLevel.MEDIUM
        totalConfidence >= 0.25f -> ContextConfidenceLevel.LOW
        else -> ContextConfidenceLevel.NO_CONTEXT
    },
    val actionDecision: ContextActionDecision = if (totalConfidence >= 0.80f) ContextActionDecision.AUTO_SWITCH else ContextActionDecision.RETAIN_CURRENT
)

/**
 * LIFEOS Phase 4: Unified Context Recognition & Auto-Detection Engine.
 * Fuses Location (Geofences), Time-of-Day, Day-of-Week, Calendar Events, Device Presence (BLE),
 * and Personal Habits into a robust, probabilistic context score.
 *
 * Enforces:
 * - Multi-signal scoring model (no single signal alone can trigger auto-switch)
 * - Strict permission gating via PermissionFirewall
 * - Battery safety via BatteryBudgetManager
 * - Confidence tiers (HIGH, MEDIUM, LOW, NO_CONTEXT)
 * - Deterministic conflict resolution
 * - Manual override precedence
 * - Anti-flapping stability debounce
 * - 100% offline execution
 * - Full Unicode and multilingual script support
 */
object ContextRecognitionEngine {

    private const val TAG = "LIFEOS_CONTEXT"
    const val TAG_MODULE2 = "LIFEOS_MODULE2"
    const val CONFIDENCE_THRESHOLD = 0.80f
    const val MULTI_MODE_HIGH_THRESHOLD = 0.75f
    const val MULTI_MODE_MEDIUM_THRESHOLD = 0.50f

    // Stability & Anti-flapping state
    private var lastSwitchTimestamp: Long = 0L
    private var lastSwitchedModeId: Int? = null
    private var manualOverrideTimestamp: Long = 0L
    private var manualOverrideModeId: Int? = null

    const val FLAPPING_COOLDOWN_MS = 60_000L // 60-second debounce between auto switches
    const val MANUAL_OVERRIDE_COOLDOWN_MS = 30 * 60_000L // 30-minute manual override protection

    /**
     * Records an explicit manual mode selection by the user.
     * Prevents automated context detection from aggressively overriding user intent.
     */
    fun notifyManualModeSelected(modeId: Int, timestamp: Long = System.currentTimeMillis()) {
        manualOverrideTimestamp = timestamp
        manualOverrideModeId = modeId
        Log.i(TAG, "Manual mode selection registered: modeId=$modeId (Protected for ${MANUAL_OVERRIDE_COOLDOWN_MS / 60000} mins)")
    }

    /**
     * Resets stability state (for unit testing and clean resets).
     */
    fun resetStabilityState() {
        lastSwitchTimestamp = 0L
        lastSwitchedModeId = null
        manualOverrideTimestamp = 0L
        manualOverrideModeId = null
    }

    /**
     * Normalizes mode titles across multiple languages (Telugu, Hindi, English),
     * stripping emojis and punctuation for robust semantic matching.
     */
    fun normalizeModeName(name: String): String {
        val trimmed = name.trim().lowercase()
        // Telugu keywords
        if (trimmed.contains("కాలేజ్") || trimmed.contains("కాలేజీ")) return "college"
        if (trimmed.contains("జిమ్")) return "gym"
        if (trimmed.contains("ఆట") || trimmed.contains("క్రీడ")) return "sports"
        if (trimmed.contains("ప్రయాణ")) return "travel"
        if (trimmed.contains("పని") || trimmed.contains("ఆఫీస్")) return "work"
        if (trimmed.contains("షాపింగ్")) return "shopping"

        // Hindi keywords
        if (trimmed.contains("कॉलेज")) return "college"
        if (trimmed.contains("जिम")) return "gym"
        if (trimmed.contains("खेल")) return "sports"
        if (trimmed.contains("यात्रा")) return "travel"
        if (trimmed.contains("काम") || trimmed.contains("दफ्तर")) return "work"
        if (trimmed.contains("खरीदारी")) return "shopping"

        // English & clean emoji
        val cleaned = trimmed.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
        return when {
            cleaned.contains("college") -> "college"
            cleaned.contains("gym") -> "gym"
            cleaned.contains("sport") -> "sports"
            cleaned.contains("travel") -> "travel"
            cleaned.contains("work") -> "work"
            cleaned.contains("shop") -> "shopping"
            else -> cleaned
        }
    }

    /**
     * Evaluates probabilistic context score for a single target mode hint.
     * Maintained for backwards compatibility with Module 2 tests.
     * Signal Weights:
     * - Location Signal (e.g., Geofence Exit toward College): 0.40
     * - Time of Day (e.g., Morning routine window 7:00 AM - 12:00 PM): 0.30
     * - Calendar Event (e.g., Scheduled Academic Event): 0.30
     */
    suspend fun evaluateContext(
        dao: LifeOsDao,
        isGeofenceExit: Boolean = true,
        targetModeHint: String = "College",
        currentTimestamp: Long = System.currentTimeMillis(),
        context: Context? = null
    ): ContextSignalEvaluation {
        val matchedSignals = mutableListOf<String>()

        // 1. Location Signal (Weight: 0.40)
        val locationAuthorized = if (context != null) {
            PermissionFirewall.isSourceAuthorized(context, SourceType.LOCATION)
        } else {
            PermissionFirewall.isSourceAuthorized(SourceType.LOCATION)
        }

        val locationScore = if (locationAuthorized && isGeofenceExit) {
            matchedSignals.add("Location: Geofence Exit toward $targetModeHint")
            0.40f
        } else 0.0f

        // 2. Time-of-Day Signal (Weight: 0.30)
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimestamp }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val isCollegeTimeWindow = hour in 7..12
        val timeScore = if (isCollegeTimeWindow) {
            matchedSignals.add("Time: Morning Class Window ($hour:00 hrs)")
            0.30f
        } else {
            0.10f
        }

        // 3. Calendar Signal (Weight: 0.30)
        val calendarAuthorized = if (context != null) {
            PermissionFirewall.isSourceAuthorized(context, SourceType.CALENDAR_SYNC)
        } else {
            PermissionFirewall.isSourceAuthorized(SourceType.CALENDAR_SYNC)
        }

        var calendarScore = 0.0f
        if (calendarAuthorized) {
            val relevantEvent = dao.findEventByTitleLike("Data Structures")
                ?: dao.findEventByTitleLike("DBMS")
                ?: dao.findEventByTitleLike("Lab")
                ?: dao.findEventByTitleLike("Lecture")
                ?: dao.findEventByTitleLike("Class")

            if (relevantEvent != null) {
                matchedSignals.add("Calendar: Scheduled Event '${relevantEvent.title}'")
                calendarScore = 0.30f
            }
        }

        val totalConfidence = (locationScore + timeScore + calendarScore).coerceAtMost(1.0f)
        val targetModeId = when {
            targetModeHint.contains("College", ignoreCase = true) -> 1
            targetModeHint.contains("Gym", ignoreCase = true) -> 2
            targetModeHint.contains("Travel", ignoreCase = true) -> 3
            else -> 1
        }

        Log.i(
            TAG_MODULE2,
            "LIFEOS_MODULE2: Context Recognition -> Inferred Mode '$targetModeHint' with Confidence ${(totalConfidence * 100).toInt()}% (Signals: ${matchedSignals.joinToString(" | ")})"
        )

        return ContextSignalEvaluation(
            locationConfidence = locationScore,
            timeConfidence = timeScore,
            calendarConfidence = calendarScore,
            totalConfidence = totalConfidence,
            inferredModeId = targetModeId,
            inferredModeName = targetModeHint,
            matchedSignals = matchedSignals
        )
    }

    /**
     * Backward-compatible evaluation and switch method.
     */
    suspend fun evaluateAndSwitchMode(
        dao: LifeOsDao,
        isGeofenceExit: Boolean = true,
        targetModeHint: String = "College",
        currentTimestamp: Long = System.currentTimeMillis(),
        context: Context? = null
    ): ContextSignalEvaluation {
        val evaluation = evaluateContext(dao, isGeofenceExit, targetModeHint, currentTimestamp, context)

        if (evaluation.totalConfidence >= CONFIDENCE_THRESHOLD) {
            dao.setActiveMode(evaluation.inferredModeId)
            lastSwitchTimestamp = currentTimestamp
            lastSwitchedModeId = evaluation.inferredModeId
            Log.i(
                TAG_MODULE2,
                "LIFEOS_MODULE2: Mode Switch Completed -> Set Active Mode '${evaluation.inferredModeName}' (id=${evaluation.inferredModeId}) [Confidence: ${(evaluation.totalConfidence * 100).toInt()}%]"
            )
        } else {
            Log.w(
                TAG_MODULE2,
                "LIFEOS_MODULE2: Mode Switch Suppressed -> Confidence ${(evaluation.totalConfidence * 100).toInt()}% below threshold ${(CONFIDENCE_THRESHOLD * 100).toInt()}%"
            )
        }

        return evaluation
    }

    /**
     * Phase 4: Full Multi-Signal, Multi-Candidate Context Evaluation Engine.
     * Evaluates ALL registered life modes against available, permission-authorized signals.
     *
     * Signal Weights:
     * - Location (Geofence Match / Proximity): 0.35
     * - Calendar Event (Active / Upcoming Event): 0.25
     * - Time of Day & Day of Week: 0.20
     * - Habit & Historical Frequency: 0.10
     * - Device Presence (BLE Beacons): 0.10
     * Sum of Weights = 1.00
     */
    suspend fun evaluateAllModes(
        dao: LifeOsDao,
        currentLocationHint: String? = null,
        isGeofenceExit: Boolean = false,
        isGeofenceEnter: Boolean = false,
        currentTimestamp: Long = System.currentTimeMillis(),
        context: Context? = null
    ): MultiModeContextResult {
        val allModes = try {
            dao.getAllModesFlow().first()
        } catch (e: Exception) {
            emptyList()
        }

        if (allModes.isEmpty()) {
            return MultiModeContextResult(
                topCandidateMode = null,
                confidenceLevel = ContextConfidenceLevel.NO_CONTEXT,
                totalConfidence = 0.0f,
                candidateScores = emptyMap(),
                breakdowns = emptyMap(),
                isConflict = false,
                conflictModes = emptyList(),
                matchedSignals = emptyList(),
                actionDecision = ContextActionDecision.RETAIN_CURRENT,
                statusMessage = "No life modes registered in database."
            )
        }

        // Authorization Checks
        val locationAuth = if (context != null) PermissionFirewall.isSourceAuthorized(context, SourceType.LOCATION)
                           else PermissionFirewall.isSourceAuthorized(SourceType.LOCATION)
        val calendarAuth = if (context != null) PermissionFirewall.isSourceAuthorized(context, SourceType.CALENDAR_SYNC)
                           else PermissionFirewall.isSourceAuthorized(SourceType.CALENDAR_SYNC)
        val bleAuth = if (context != null) PermissionFirewall.isSourceAuthorized(context, SourceType.BLE_SCANNING)
                      else PermissionFirewall.isSourceAuthorized(SourceType.BLE_SCANNING)
        val bleScanAllowed = bleAuth && BatteryBudgetManager.canRunBleScan()

        // Time signals
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimestamp }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon ... 7=Sat
        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

        // Query events from Room DB safely
        val allEvents: List<EventEntity> = try {
            if (calendarAuth) dao.getAllEventsFlow().first().filter { !it.isGhostMode } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // Query habits safely
        val habits = try {
            dao.getAllHabits()
        } catch (e: Exception) {
            emptyList()
        }
        val habitMap = habits.groupBy { it.modeId }

        // Query detected BLE devices safely
        val detectedDevices = if (bleScanAllowed) DevicePresenceScanner.detectedDevices.value else emptyList()

        val candidateScores = mutableMapOf<Int, Float>()
        val breakdowns = mutableMapOf<Int, SignalBreakdown>()

        for (mode in allModes) {
            val matched = mutableListOf<String>()
            val normName = normalizeModeName(mode.name)

            // 1. Location Signal (Weight: 0.35)
            var locScore = 0.0f
            if (locationAuth && !currentLocationHint.isNullOrBlank()) {
                val normHint = normalizeModeName(currentLocationHint)
                if (normHint == normName || currentLocationHint.contains(mode.name, ignoreCase = true)) {
                    locScore = if (isGeofenceEnter) {
                        matched.add("Location: Arrived at ${mode.name} region")
                        0.35f
                    } else if (isGeofenceExit) {
                        matched.add("Location: Departed toward ${mode.name}")
                        0.28f
                    } else {
                        matched.add("Location: Present near ${mode.name}")
                        0.35f
                    }
                }
            }

            // 2. Calendar Event Signal (Weight: 0.25)
            var calScore = 0.0f
            if (calendarAuth && allEvents.isNotEmpty()) {
                val matchingEvent = allEvents.firstOrNull { event ->
                    // Guard against malformed events
                    if (event.title.isBlank() || event.endTime < event.startTime) return@firstOrNull false
                    val titleNorm = event.title.lowercase()

                    val matchesModeId = event.associatedModeId == mode.id
                    val matchesKeyword = when (normName) {
                        "college" -> titleNorm.contains("dbms") || titleNorm.contains("lab") || titleNorm.contains("lecture") || titleNorm.contains("class") || titleNorm.contains("data structures") || titleNorm.contains("exam")
                        "gym" -> titleNorm.contains("gym") || titleNorm.contains("workout") || titleNorm.contains("cardio") || titleNorm.contains("fitness") || titleNorm.contains("leg day")
                        "sports" -> titleNorm.contains("sport") || titleNorm.contains("match") || titleNorm.contains("football") || titleNorm.contains("cricket") || titleNorm.contains("practice")
                        "travel" -> titleNorm.contains("flight") || titleNorm.contains("airport") || titleNorm.contains("trip") || titleNorm.contains("hotel") || titleNorm.contains("travel") || titleNorm.contains("train")
                        "work" -> titleNorm.contains("work") || titleNorm.contains("meeting") || titleNorm.contains("standup") || titleNorm.contains("sync") || titleNorm.contains("office")
                        "shopping" -> titleNorm.contains("shopping") || titleNorm.contains("supermarket") || titleNorm.contains("groceries") || titleNorm.contains("mall")
                        else -> titleNorm.contains(normName)
                    }
                    matchesModeId || (event.associatedModeId <= 0 && matchesKeyword)
                }

                if (matchingEvent != null) {
                    val isActiveNow = matchingEvent.startTime <= currentTimestamp && matchingEvent.endTime >= currentTimestamp
                    val isUpcomingSoon = matchingEvent.startTime > currentTimestamp && (matchingEvent.startTime - currentTimestamp) <= 45 * 60_000L

                    if (isActiveNow) {
                        matched.add("Calendar: Active event '${matchingEvent.title}'")
                        calScore = 0.25f
                    } else if (isUpcomingSoon) {
                        val minutesToStart = ((matchingEvent.startTime - currentTimestamp) / 60_000L).coerceAtLeast(1)
                        matched.add("Calendar: '${matchingEvent.title}' in ${minutesToStart}m")
                        calScore = 0.20f
                    }
                }
            }

            // 3. Time of Day & Day of Week Signal (Weight: 0.20)
            var timeScore = 0.0f
            when (normName) {
                "college" -> {
                    if (isWeekday && hour in 7..14) {
                        matched.add("Time: Weekday academic window ($hour:00 hrs)")
                        timeScore = 0.20f
                    } else if (isWeekday && hour in 15..17) {
                        timeScore = 0.08f
                    }
                }
                "gym" -> {
                    if (hour in 6..9 || hour in 17..21) {
                        matched.add("Time: Standard workout window ($hour:00 hrs)")
                        timeScore = 0.20f
                    }
                }
                "sports" -> {
                    if (isWeekend && hour in 6..12) {
                        matched.add("Time: Weekend sports window ($hour:00 hrs)")
                        timeScore = 0.20f
                    } else if (isWeekday && hour in 16..19) {
                        matched.add("Time: Evening practice window ($hour:00 hrs)")
                        timeScore = 0.14f
                    }
                }
                "travel" -> {
                    if (locScore > 0f || calScore > 0f) {
                        matched.add("Time: Active transit period")
                        timeScore = 0.15f
                    }
                }
                "work" -> {
                    if (isWeekday && hour in 8..18) {
                        matched.add("Time: Business hours ($hour:00 hrs)")
                        timeScore = 0.20f
                    }
                }
                "shopping" -> {
                    if (hour in 11..21) {
                        timeScore = 0.15f
                    }
                }
                else -> {
                    // Custom user modes default to mild baseline
                    timeScore = 0.08f
                }
            }

            // 4. Habit / Historical Routine Signal (Weight: 0.10)
            var habitScore = 0.0f
            val modeHabits = habitMap[mode.id] ?: emptyList()
            if (modeHabits.isNotEmpty()) {
                val totalConfirms = modeHabits.sumOf { it.confirmCount }
                if (totalConfirms >= 5) {
                    matched.add("Habit: Highly practiced routine ($totalConfirms confirms)")
                    habitScore = 0.10f
                } else if (totalConfirms in 1..4) {
                    matched.add("Habit: Recognized routine")
                    habitScore = 0.06f
                } else {
                    habitScore = 0.03f
                }
            }

            // 5. Device Presence Signal (Weight: 0.10)
            var deviceScore = 0.0f
            if (bleScanAllowed && detectedDevices.isNotEmpty()) {
                val laptop = detectedDevices.firstOrNull { it.deviceName.contains("Laptop", ignoreCase = true) && it.confidence == PresenceConfidence.HIGH_CONFIDENCE }
                val watch = detectedDevices.firstOrNull { it.deviceName.contains("Watch", ignoreCase = true) && it.confidence == PresenceConfidence.HIGH_CONFIDENCE }
                val headphones = detectedDevices.firstOrNull { it.deviceName.contains("Headphone", ignoreCase = true) && it.confidence == PresenceConfidence.HIGH_CONFIDENCE }

                when (normName) {
                    "college", "work" -> {
                        if (laptop != null) {
                            matched.add("Device: ${laptop.deviceName} nearby (${laptop.rssi} dBm)")
                            deviceScore = 0.10f
                        }
                    }
                    "gym", "sports" -> {
                        if (watch != null) {
                            matched.add("Device: ${watch.deviceName} nearby (${watch.rssi} dBm)")
                            deviceScore = 0.10f
                        }
                    }
                    "travel" -> {
                        if (headphones != null) {
                            matched.add("Device: ${headphones.deviceName} nearby (${headphones.rssi} dBm)")
                            deviceScore = 0.08f
                        }
                    }
                }
            }

            val totalScore = (locScore + calScore + timeScore + habitScore + deviceScore).coerceIn(0.0f, 1.0f)
            candidateScores[mode.id] = totalScore
            breakdowns[mode.id] = SignalBreakdown(
                locationScore = locScore,
                calendarScore = calScore,
                timeScore = timeScore,
                habitScore = habitScore,
                deviceScore = deviceScore,
                totalScore = totalScore,
                matchedSignals = matched
            )
        }

        // Rank candidates
        val sortedCandidates = allModes.sortedByDescending { candidateScores[it.id] ?: 0.0f }
        val topCandidate = sortedCandidates.firstOrNull()
        val topScore = candidateScores[topCandidate?.id] ?: 0.0f
        val runnerUp = sortedCandidates.getOrNull(1)
        val runnerUpScore = candidateScores[runnerUp?.id] ?: 0.0f

        // Conflict check: if top candidate and runner-up are within 0.08 margin and both have meaningful scores
        val isScoreTie = runnerUp != null && topScore >= 0.25f && kotlin.math.abs(topScore - runnerUpScore) <= 0.08f
        var isUncertainConflict = false
        var conflictList = emptyList<LifeModeEntity>()
        var finalWinner = topCandidate

        if (isScoreTie && topCandidate != null) {
            conflictList = listOf(topCandidate, runnerUp)
            if (topCandidate.priority == runnerUp.priority) {
                // True tie in priority and score: Do not arbitrarily guess
                isUncertainConflict = true
                Log.w(TAG, "Ambiguous context conflict detected: '${topCandidate.name}' ($topScore) vs '${runnerUp.name}' ($runnerUpScore). Unresolvable tie.")
            } else if (topCandidate.priority > runnerUp.priority) {
                // Invert winner if runner-up has higher priority (lower priority number)
                finalWinner = runnerUp
                Log.i(TAG, "Resolved context conflict via Priority: Selected '${runnerUp.name}' (priority ${runnerUp.priority}) over '${topCandidate.name}'")
            }
        }

        // Determine confidence level
        val winnerScore = candidateScores[finalWinner?.id] ?: 0.0f
        val confidenceLevel = when {
            isUncertainConflict -> ContextConfidenceLevel.LOW
            winnerScore >= MULTI_MODE_HIGH_THRESHOLD -> ContextConfidenceLevel.HIGH
            winnerScore >= MULTI_MODE_MEDIUM_THRESHOLD -> ContextConfidenceLevel.MEDIUM
            winnerScore >= 0.25f -> ContextConfidenceLevel.LOW
            else -> ContextConfidenceLevel.NO_CONTEXT
        }

        // Determine Action Decision
        val activeMode = allModes.firstOrNull { it.isActive }
        val now = currentTimestamp

        val actionDecision: ContextActionDecision
        val statusMessage: String

        when {
            isUncertainConflict -> {
                actionDecision = ContextActionDecision.UNCERTAIN
                statusMessage = "Uncertain context: '${topCandidate?.name}' and '${runnerUp?.name}' have conflicting signals. Preserving active mode."
            }
            confidenceLevel == ContextConfidenceLevel.HIGH && finalWinner != null -> {
                val isSameAsActive = finalWinner.id == activeMode?.id
                val isWithinFlappingWindow = (now - lastSwitchTimestamp) < FLAPPING_COOLDOWN_MS && lastSwitchedModeId != null && lastSwitchedModeId != finalWinner.id
                val isManualOverrideActive = (now - manualOverrideTimestamp) < MANUAL_OVERRIDE_COOLDOWN_MS && manualOverrideModeId != null && manualOverrideModeId != finalWinner.id
                val isSafeMode = SafeModeManager.isSafeModeActive

                when {
                    isSameAsActive -> {
                        actionDecision = ContextActionDecision.RETAIN_CURRENT
                        statusMessage = "Confirmed active mode '${finalWinner.name}' with High Confidence (${(winnerScore * 100).toInt()}%)."
                    }
                    isWithinFlappingWindow -> {
                        actionDecision = ContextActionDecision.RETAIN_CURRENT
                        statusMessage = "Rapid context flapping detected. Retaining current mode '${activeMode?.name ?: "Unknown"}' during debounce window."
                    }
                    isManualOverrideActive -> {
                        actionDecision = ContextActionDecision.PROPOSE
                        statusMessage = "User manually selected '${activeMode?.name}'. Proposing '${finalWinner.name}' (${(winnerScore * 100).toInt()}%) without force-switching."
                    }
                    isSafeMode -> {
                        actionDecision = ContextActionDecision.PROPOSE
                        statusMessage = "Safe Mode Active: Automatic mode mutations suppressed. Proposing '${finalWinner.name}'."
                    }
                    else -> {
                        actionDecision = ContextActionDecision.AUTO_SWITCH
                        statusMessage = "High confidence (${(winnerScore * 100).toInt()}%) for '${finalWinner.name}'. Ready for automatic switch."
                    }
                }
            }
            confidenceLevel == ContextConfidenceLevel.MEDIUM && finalWinner != null -> {
                actionDecision = ContextActionDecision.PROPOSE
                statusMessage = "Medium confidence (${(winnerScore * 100).toInt()}%) suggests '${finalWinner.name}'. Proposed as suggestion."
            }
            else -> {
                actionDecision = ContextActionDecision.RETAIN_CURRENT
                statusMessage = "Confidence (${(winnerScore * 100).toInt()}%) is insufficient to change mode. Retaining active mode."
            }
        }

        return MultiModeContextResult(
            topCandidateMode = if (isUncertainConflict) null else finalWinner,
            confidenceLevel = confidenceLevel,
            totalConfidence = winnerScore,
            candidateScores = candidateScores,
            breakdowns = breakdowns,
            isConflict = isUncertainConflict || isScoreTie,
            conflictModes = conflictList,
            matchedSignals = breakdowns[finalWinner?.id]?.matchedSignals ?: emptyList(),
            actionDecision = actionDecision,
            statusMessage = statusMessage
        )
    }

    /**
     * Executes multi-mode evaluation and switches active mode if decision is AUTO_SWITCH.
     */
    suspend fun evaluateAndApplyDecision(
        dao: LifeOsDao,
        currentLocationHint: String? = null,
        isGeofenceExit: Boolean = false,
        isGeofenceEnter: Boolean = false,
        currentTimestamp: Long = System.currentTimeMillis(),
        context: Context? = null
    ): MultiModeContextResult {
        val result = evaluateAllModes(dao, currentLocationHint, isGeofenceExit, isGeofenceEnter, currentTimestamp, context)

        if (result.actionDecision == ContextActionDecision.AUTO_SWITCH && result.topCandidateMode != null) {
            dao.setActiveMode(result.topCandidateMode.id)
            lastSwitchTimestamp = currentTimestamp
            lastSwitchedModeId = result.topCandidateMode.id
            Log.i(TAG, "Context Auto-Switch Executed -> Set Active Mode '${result.topCandidateMode.name}' (id=${result.topCandidateMode.id})")
        } else {
            Log.d(TAG, "Context Evaluation Finished -> Decision: ${result.actionDecision} (${result.statusMessage})")
        }

        return result
    }
}
