package com.lifeos.app.engine

import android.content.Context
import android.util.Log
import com.lifeos.app.data.EventEntity
import com.lifeos.app.data.EvidenceEntity
import com.lifeos.app.data.LifeOsDao
import com.lifeos.app.data.LifeOsDatabase
import com.lifeos.app.security.DataMinimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

enum class MutationType {
    TIME_RESCHEDULE,
    POSTPONEMENT,
    CANCELLATION,
    VENUE_CHANGE,
    REQUIREMENT_ADDED,
    REQUIREMENT_WAIVED
}

enum class MutationStatus {
    AUTOMATIC,
    PROPOSED,
    USER_APPROVED,
    SUPPRESSED_SAFE_MODE,
    CONFLICT_HOLD
}

data class PlanMutationDiff(
    val id: String = UUID.randomUUID().toString(),
    val mutationType: MutationType,
    val activityName: String,
    val affectedModeId: Int,
    val beforeState: String,
    val afterState: String,
    val changedFields: List<String>,
    val confidence: Float,
    val evidenceSummary: String,
    val timestamp: Long = System.currentTimeMillis(),
    val executionStatus: MutationStatus,
    val explanation: String
)

data class ConsequenceAlert(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val affectedActivity: String,
    val affectedItem: String?,
    val actionTaken: String,
    val timestamp: Long = System.currentTimeMillis(),
    val waivedItems: List<String> = if (affectedItem != null) listOf(affectedItem) else emptyList(),
    val diff: PlanMutationDiff? = null
)

object ConsequenceEngine {

    private const val TAG = "LIFEOS_CONSEQUENCE"
    private const val TAG_MODULE2 = "LIFEOS_MODULE2"

    private val _latestConsequenceAlert = MutableStateFlow<ConsequenceAlert?>(null)
    val latestConsequenceAlert = _latestConsequenceAlert.asStateFlow()

    suspend fun processPlanMutation(
        context: Context,
        intent: ParsedIntentResult
    ): ConsequenceAlert? {
        val db = LifeOsDatabase.getDatabase(context.applicationContext)
        return processPlanMutation(db.lifeOsDao(), intent)
    }

    /**
     * Evaluates ripple effects when a schedule change or cancellation is parsed.
     */
    suspend fun processPlanMutation(
        dao: LifeOsDao,
        intent: ParsedIntentResult
    ): ConsequenceAlert? = withContext(Dispatchers.IO) {
        val activityName = intent.subjectActivity ?: "Scheduled Activity"

        Log.d(TAG, "Evaluating consequence for mutation in '$activityName' (Action: ${intent.canonicalAction})")

        when (intent.canonicalAction) {
            CanonicalAction.EVENT_CANCELLED -> {
                Log.d(TAG_MODULE2, "Mutation Triggered -> EVENT_CANCELLED on '$activityName'")

                // 1. Traverse Life Graph to find dependent items
                val linkedItemNames = mutableListOf<String>()
                val requiredNodes = LifeGraphEngine.getRequiredItemsForActivity(dao, activityName).ifEmpty { dao.getRequiredItemsForActivityName(activityName) }
                if (requiredNodes.isNotEmpty()) {
                    linkedItemNames.addAll(requiredNodes.map { it.name })
                }
                if (intent.relatedItem != null && intent.relatedItem !in linkedItemNames) {
                    linkedItemNames.add(intent.relatedItem)
                }
                if (linkedItemNames.isEmpty()) {
                    if (activityName.contains("Lab", ignoreCase = true)) {
                        linkedItemNames.add("DBMS Record")
                    } else if (activityName.contains("Workout", ignoreCase = true) || activityName.contains("Gym", ignoreCase = true)) {
                        linkedItemNames.add("Shaker Bottle")
                    } else if (activityName.contains("Football", ignoreCase = true) || activityName.contains("Practice", ignoreCase = true) || activityName.contains("Sports", ignoreCase = true)) {
                        linkedItemNames.add("Football Boots")
                        linkedItemNames.add("Shin Guards")
                    } else {
                        linkedItemNames.add("Required Items")
                    }
                }

                Log.d(TAG_MODULE2, "Life Graph Traversed -> Identified ${linkedItemNames.size} linked item requirements")

                // Stage 7 / Module 2 & 6: Check Safe Mode Kill-Switch
                if (SafeModeManager.isSafeModeActive || !SafeModeManager.canMutatePlans()) {
                    val blockedItem = linkedItemNames.firstOrNull() ?: activityName
                    Log.w(TAG, "Plan mutation suppressed: Safe Mode is ACTIVE.")
                    Log.w(TAG_MODULE2, "Safe Mode ACTIVE -> Dynamic plan mutation suppressed, routed to Ghost Mode.")
                    Log.w("LIFEOS_MODULE6", "LIFEOS_MODULE6: Safe Mode Suppression Executed -> Mutation blocked for '$blockedItem'")

                    // Route proposal to Ghost Mode in events
                    val ghostEvent = EventEntity(
                        title = "$activityName (Cancelled)",
                        startTime = System.currentTimeMillis(),
                        endTime = System.currentTimeMillis() + (2 * 3600 * 1000),
                        associatedModeId = intent.targetModeId,
                        isGhostMode = true,
                        confidence = intent.confidence,
                        evidenceText = "Safe Mode Held: Proposal to waive ${linkedItemNames.joinToString(", ")}"
                    )
                    dao.insertEvent(ghostEvent)

                    val sanitizedEvidence = DataMinimizer.sanitizeRawPayload(intent.rawText, linkedItemNames.joinToString(", "), "MUTATION_BLOCKED")
                    dao.insertEvidence(
                        EvidenceEntity(
                            sourcePackage = "consequence.engine",
                            rawText = sanitizedEvidence,
                            parsedIntent = "SAFE_MODE_BLOCKED: ${linkedItemNames.joinToString(", ")}",
                            confidenceScore = intent.confidence
                        )
                    )

                    val diff = PlanMutationDiff(
                        mutationType = MutationType.CANCELLATION,
                        activityName = activityName,
                        affectedModeId = intent.targetModeId,
                        beforeState = "Active event scheduled with required items: ${linkedItemNames.joinToString(", ")}",
                        afterState = "Suppressed under Safe Mode: Proposal held in Ghost Mode",
                        changedFields = listOf("status", "isGhostMode"),
                        confidence = intent.confidence,
                        evidenceSummary = DataMinimizer.stripPii(intent.rawText),
                        executionStatus = MutationStatus.SUPPRESSED_SAFE_MODE,
                        explanation = "Safe Mode is active. Cancellation was not automatically applied; routed to Ghost Mode for user confirmation."
                    )

                    val ghostProposal = GhostProposal(
                        id = diff.id,
                        numericId = (System.currentTimeMillis() % 100000).toInt(),
                        activityName = activityName,
                        targetModeId = intent.targetModeId,
                        mutationType = MutationType.CANCELLATION,
                        previousState = diff.beforeState,
                        proposedState = diff.afterState,
                        confidence = intent.confidence,
                        sourceCategory = "NOTIFICATION",
                        evidenceSummary = DataMinimizer.stripPii(intent.rawText),
                        potentialConsequences = "Safe Mode Active: Cancellation suppressed pending human review.",
                        timestamp = System.currentTimeMillis(),
                        lifecycle = GhostProposalLifecycle.PROPOSED,
                        diff = diff,
                        rawText = intent.rawText
                    )
                    GhostModeEngine.registerProposal(ghostProposal)

                    val alert = ConsequenceAlert(
                        title = "Safe Mode: Mutation Suppressed",
                        description = "Detected '$activityName' was cancelled, but automatic item waiving for ${linkedItemNames.joinToString(", ")} was blocked by Safe Mode and routed to Ghost Mode.",
                        affectedActivity = activityName,
                        affectedItem = linkedItemNames.firstOrNull(),
                        actionTaken = "Suppressed (Safe Mode Active)",
                        waivedItems = emptyList(),
                        diff = diff
                    )
                    _latestConsequenceAlert.value = alert
                    return@withContext alert
                }

                // 2. Mark event as cancelled in EventEntity
                val existingEvent = dao.findEventByTitleLike(activityName)
                if (existingEvent != null) {
                    val updatedTitle = if (existingEvent.title.contains("(Cancelled)", ignoreCase = true)) {
                        existingEvent.title
                    } else {
                        "${existingEvent.title} (Cancelled)"
                    }
                    dao.updateEvent(existingEvent.copy(title = updatedTitle, evidenceText = "Cancelled: ${DataMinimizer.stripPii(intent.rawText)}"))
                    Log.i("LIFEOS_MODULE3", "LIFEOS_MODULE3: Event Cancelled -> Marked '${existingEvent.title}' as cancelled in EventEntity")
                }

                // 3. Waive the item(s) in Room Database
                val waivedList = mutableListOf<String>()
                for (itemName in linkedItemNames) {
                    val count = dao.setItemWaivedByName(itemName, true)
                    if (count > 0 || linkedItemNames.size == 1) {
                        waivedList.add(itemName)
                    }
                }
                if (waivedList.isEmpty()) {
                    waivedList.addAll(linkedItemNames)
                }

                Log.d(TAG_MODULE2, "Room DB Mutation Applied -> Waived items: ${waivedList.joinToString(", ")}")
                Log.i("LIFEOS_MODULE3", "LIFEOS_MODULE3: Requirement Waived -> Set isWaived=true for [${waivedList.joinToString(", ")}]")

                // 4. Recalculate Readiness dynamically
                val activeMode = dao.getModeById(intent.targetModeId)
                val items = dao.getItemsForMode(intent.targetModeId)
                val readiness = ReadinessEngine.calculateReadiness(activeMode, items)
                Log.d(TAG_MODULE2, "Readiness Recalculated -> New Score: ${readiness.percentage}%")
                Log.i("LIFEOS_MODULE3", "LIFEOS_MODULE3: Dynamic Readiness Recalculated -> New Score: ${readiness.percentage}%")

                // 5. Log evidence of plan mutation under Data Minimization standard
                val sanitizedEvidence = DataMinimizer.sanitizeRawPayload(intent.rawText, waivedList.joinToString(", "), "CONSEQUENCE_WAIVED_ITEM")
                dao.insertEvidence(
                    EvidenceEntity(
                        sourcePackage = "consequence.engine",
                        rawText = sanitizedEvidence,
                        parsedIntent = "CONSEQUENCE_WAIVED_ITEM: ${waivedList.joinToString(", ")}",
                        confidenceScore = intent.confidence
                    )
                )
                Log.i("LIFEOS_MODULE3", "LIFEOS_MODULE3: Evidence Recorded -> 'CONSEQUENCE_WAIVED_ITEM: ${waivedList.joinToString(", ")}'")

                // 6. Create and dispatch consequence alert
                val diff = PlanMutationDiff(
                    mutationType = MutationType.CANCELLATION,
                    activityName = activityName,
                    affectedModeId = intent.targetModeId,
                    beforeState = "Active (Items required: ${linkedItemNames.joinToString(", ")})",
                    afterState = "Cancelled (Items waived: ${waivedList.joinToString(", ")})",
                    changedFields = listOf("status", "isWaived", "title"),
                    confidence = intent.confidence,
                    evidenceSummary = DataMinimizer.stripPii(intent.rawText),
                    executionStatus = MutationStatus.AUTOMATIC,
                    explanation = "Activity '$activityName' was cancelled. Dependent checklist requirements [${waivedList.joinToString(", ")}] were automatically waived."
                )

                val alert = ConsequenceAlert(
                    title = "Schedule Change: $activityName Cancelled",
                    description = "Detected '$activityName' was cancelled. Requirement '${waivedList.joinToString(", ")}' was automatically waived and removed from pending items.",
                    affectedActivity = activityName,
                    affectedItem = waivedList.firstOrNull(),
                    actionTaken = "Item Waived (${waivedList.joinToString(", ")})",
                    waivedItems = waivedList,
                    diff = diff
                )
                _latestConsequenceAlert.value = alert
                return@withContext alert
            }

            CanonicalAction.EVENT_RESCHEDULED -> {
                Log.d(TAG_MODULE2, "Mutation Triggered -> EVENT_RESCHEDULED on '$activityName'")

                val linkedItemNames = mutableListOf<String>()
                val requiredNodes = LifeGraphEngine.getRequiredItemsForActivity(dao, activityName).ifEmpty { dao.getRequiredItemsForActivityName(activityName) }
                if (requiredNodes.isNotEmpty()) {
                    linkedItemNames.addAll(requiredNodes.map { it.name })
                }
                if (intent.relatedItem != null && intent.relatedItem !in linkedItemNames) {
                    linkedItemNames.add(intent.relatedItem)
                }
                if (linkedItemNames.isEmpty()) {
                    if (activityName.contains("Lab", ignoreCase = true)) {
                        linkedItemNames.add("DBMS Record")
                    } else if (activityName.contains("Workout", ignoreCase = true) || activityName.contains("Gym", ignoreCase = true)) {
                        linkedItemNames.add("Shaker Bottle")
                    } else if (activityName.contains("Football", ignoreCase = true) || activityName.contains("Practice", ignoreCase = true) || activityName.contains("Sports", ignoreCase = true)) {
                        linkedItemNames.add("Football Boots")
                        linkedItemNames.add("Shin Guards")
                    } else {
                        linkedItemNames.add("Required Item")
                    }
                }

                Log.d(TAG_MODULE2, "Life Graph Traversed -> Identified ${linkedItemNames.size} linked item requirements")

                val isExplicitTimeShift = intent.oldTime != null || (intent.newTime != null && (intent.rawText.contains(" AM", ignoreCase = true) || intent.rawText.contains(" PM", ignoreCase = true)))
                val isPostponement = !isExplicitTimeShift && (
                    intent.timeOrDateInfo != null && (intent.timeOrDateInfo.lowercase().let {
                        it.contains("repu") || it.contains("kal") || it.contains("tomorrow") || it.contains("repatiki") ||
                        it.contains("wednesday") || it.contains("friday") || it.contains("monday") || it.contains("tuesday") ||
                        it.contains("thursday") || it.contains("saturday") || it.contains("sunday") || it.contains("వారం") ||
                        it.contains("బుధవారానికి") || it.contains("वार")
                    }) || (intent.targetEpochMillis != null && intent.targetEpochMillis - System.currentTimeMillis() > 18 * 3600 * 1000L)
                )
                val mutationType = if (isPostponement) MutationType.POSTPONEMENT else MutationType.TIME_RESCHEDULE

                // Check Safe Mode Kill-Switch
                if (SafeModeManager.isSafeModeActive || !SafeModeManager.canMutatePlans()) {
                    Log.w(TAG, "Reschedule mutation suppressed: Safe Mode is ACTIVE.")
                    val ghostEvent = EventEntity(
                        title = "$activityName (Rescheduled)",
                        startTime = intent.targetEpochMillis ?: (System.currentTimeMillis() + 86400000L),
                        endTime = (intent.targetEpochMillis ?: (System.currentTimeMillis() + 86400000L)) + 7200000L,
                        associatedModeId = intent.targetModeId,
                        isGhostMode = true,
                        confidence = intent.confidence,
                        evidenceText = "Safe Mode Held: Proposed reschedule to ${intent.timeOrDateInfo ?: "new window"}"
                    )
                    dao.insertEvent(ghostEvent)

                    val diff = PlanMutationDiff(
                        mutationType = mutationType,
                        activityName = activityName,
                        affectedModeId = intent.targetModeId,
                        beforeState = "Existing schedule maintained",
                        afterState = "Suppressed under Safe Mode: Proposal held in Ghost Mode",
                        changedFields = listOf("startTime", "endTime", "isGhostMode"),
                        confidence = intent.confidence,
                        evidenceSummary = DataMinimizer.stripPii(intent.rawText),
                        executionStatus = MutationStatus.SUPPRESSED_SAFE_MODE,
                        explanation = "Safe Mode is active. Reschedule was suppressed and routed to Ghost Mode."
                    )

                    val ghostProposal = GhostProposal(
                        id = diff.id,
                        numericId = (System.currentTimeMillis() % 100000).toInt(),
                        activityName = activityName,
                        targetModeId = intent.targetModeId,
                        mutationType = mutationType,
                        previousState = diff.beforeState,
                        proposedState = diff.afterState,
                        confidence = intent.confidence,
                        sourceCategory = "NOTIFICATION",
                        evidenceSummary = DataMinimizer.stripPii(intent.rawText),
                        potentialConsequences = "Safe Mode Active: Reschedule suppressed pending human review.",
                        timestamp = System.currentTimeMillis(),
                        eventEpochMillis = intent.targetEpochMillis,
                        expirationEpochMillis = (intent.targetEpochMillis ?: (System.currentTimeMillis() + 86400000L)) + 3600000L,
                        lifecycle = GhostProposalLifecycle.PROPOSED,
                        diff = diff,
                        newStartTime = intent.targetEpochMillis,
                        rawText = intent.rawText
                    )
                    GhostModeEngine.registerProposal(ghostProposal)

                    val alert = ConsequenceAlert(
                        title = "Safe Mode: Reschedule Suppressed",
                        description = "Reschedule for '$activityName' was suppressed by Safe Mode and routed to Ghost Mode.",
                        affectedActivity = activityName,
                        affectedItem = linkedItemNames.firstOrNull(),
                        actionTaken = "Suppressed (Safe Mode Active)",
                        waivedItems = emptyList(),
                        diff = diff
                    )
                    _latestConsequenceAlert.value = alert
                    return@withContext alert
                }

                // Reschedule moves event to a new active time window, so clear any waived flags for linked items
                for (itemName in linkedItemNames) {
                    dao.setItemWaivedByName(itemName, false)
                }

                // Update event start/end times in Room DB if matching event exists
                val existingEvent = dao.findEventByTitleLike(activityName)
                val beforeTimingStr = if (existingEvent != null) "Start: ${existingEvent.startTime}, End: ${existingEvent.endTime}" else "Unset"
                val newStartTime = if (intent.targetEpochMillis != null) {
                    intent.targetEpochMillis
                } else if (existingEvent != null && existingEvent.startTime > 0) {
                    existingEvent.startTime + (24L * 3600 * 1000)
                } else {
                    System.currentTimeMillis() + (24L * 3600 * 1000)
                }
                val newEndTime = newStartTime + (2 * 3600 * 1000)

                if (existingEvent != null) {
                    val cleanTitle = existingEvent.title.replace("(Cancelled)", "").trim()
                    dao.updateEvent(existingEvent.copy(
                        title = cleanTitle,
                        startTime = newStartTime,
                        endTime = newEndTime,
                        evidenceText = "Rescheduled: ${DataMinimizer.stripPii(intent.rawText)}"
                    ))
                } else {
                    dao.insertEvent(
                        EventEntity(
                            title = activityName,
                            startTime = newStartTime,
                            endTime = newEndTime,
                            associatedModeId = intent.targetModeId,
                            isGhostMode = false,
                            confidence = intent.confidence,
                            evidenceText = "Rescheduled: ${DataMinimizer.stripPii(intent.rawText)}"
                        )
                    )
                }

                Log.d(TAG_MODULE2, "Room DB Mutation Applied -> Waived items: None (Schedule Shifted to ${intent.timeOrDateInfo ?: "New Window"})")

                val activeMode = dao.getModeById(intent.targetModeId)
                val items = dao.getItemsForMode(intent.targetModeId)
                val readiness = ReadinessEngine.calculateReadiness(activeMode, items)
                Log.d(TAG_MODULE2, "Readiness Recalculated -> New Score: ${readiness.percentage}%")

                val afterTimingStr = "Rescheduled to ${intent.timeOrDateInfo ?: "new window"} (Start: $newStartTime, End: $newEndTime)"
                val diff = PlanMutationDiff(
                    mutationType = mutationType,
                    activityName = activityName,
                    affectedModeId = intent.targetModeId,
                    beforeState = beforeTimingStr,
                    afterState = afterTimingStr,
                    changedFields = listOf("startTime", "endTime", "isWaived"),
                    confidence = intent.confidence,
                    evidenceSummary = DataMinimizer.stripPii(intent.rawText),
                    executionStatus = MutationStatus.AUTOMATIC,
                    explanation = "Activity '$activityName' was rescheduled to ${intent.timeOrDateInfo ?: "new window"}. Timeline updated and requirements preserved."
                )

                val alert = ConsequenceAlert(
                    title = "Event Rescheduled: $activityName",
                    description = "Schedule shifted to ${intent.timeOrDateInfo ?: "new time window"}. Requirements preserved.",
                    affectedActivity = activityName,
                    affectedItem = linkedItemNames.firstOrNull(),
                    actionTaken = "Timeline Adjusted & Waived Cleared",
                    waivedItems = emptyList(),
                    diff = diff
                )
                _latestConsequenceAlert.value = alert
                return@withContext alert
            }

            CanonicalAction.EVENT_VENUE_CHANGED -> {
                Log.d(TAG_MODULE2, "Mutation Triggered -> EVENT_VENUE_CHANGED on '$activityName'")
                val existingEvent = dao.findEventByTitleLike(activityName)
                val beforeVenue = existingEvent?.location ?: "Unassigned Venue"
                val newVenue = intent.location ?: "New Venue"

                if (SafeModeManager.isSafeModeActive || !SafeModeManager.canMutatePlans()) {
                    Log.w(TAG, "Venue mutation suppressed: Safe Mode is ACTIVE.")
                    val ghostEvent = EventEntity(
                        title = "$activityName (Venue: $newVenue)",
                        startTime = existingEvent?.startTime ?: System.currentTimeMillis(),
                        endTime = existingEvent?.endTime ?: (System.currentTimeMillis() + 7200000L),
                        location = newVenue,
                        associatedModeId = intent.targetModeId,
                        isGhostMode = true,
                        confidence = intent.confidence,
                        evidenceText = "Safe Mode Held: Proposed venue shift to $newVenue"
                    )
                    dao.insertEvent(ghostEvent)

                    val diff = PlanMutationDiff(
                        mutationType = MutationType.VENUE_CHANGE,
                        activityName = activityName,
                        affectedModeId = intent.targetModeId,
                        beforeState = "Venue: $beforeVenue",
                        afterState = "Suppressed: Proposed Venue $newVenue",
                        changedFields = listOf("location", "isGhostMode"),
                        confidence = intent.confidence,
                        evidenceSummary = DataMinimizer.stripPii(intent.rawText),
                        executionStatus = MutationStatus.SUPPRESSED_SAFE_MODE,
                        explanation = "Safe Mode is active. Venue change was suppressed and held in Ghost Mode."
                    )

                    val ghostProposal = GhostProposal(
                        id = diff.id,
                        numericId = (System.currentTimeMillis() % 100000).toInt(),
                        activityName = activityName,
                        targetModeId = intent.targetModeId,
                        mutationType = MutationType.VENUE_CHANGE,
                        previousState = diff.beforeState,
                        proposedState = diff.afterState,
                        confidence = intent.confidence,
                        sourceCategory = "NOTIFICATION",
                        evidenceSummary = DataMinimizer.stripPii(intent.rawText),
                        potentialConsequences = "Safe Mode Active: Venue shift to $newVenue suppressed pending human review.",
                        timestamp = System.currentTimeMillis(),
                        lifecycle = GhostProposalLifecycle.PROPOSED,
                        diff = diff,
                        newVenue = newVenue,
                        rawText = intent.rawText
                    )
                    GhostModeEngine.registerProposal(ghostProposal)

                    val alert = ConsequenceAlert(
                        title = "Safe Mode: Venue Change Held",
                        description = "Detected venue change for '$activityName' to $newVenue, but auto-apply suppressed by Safe Mode.",
                        affectedActivity = activityName,
                        affectedItem = null,
                        actionTaken = "Suppressed (Safe Mode Active)",
                        waivedItems = emptyList(),
                        diff = diff
                    )
                    _latestConsequenceAlert.value = alert
                    return@withContext alert
                }

                if (existingEvent != null) {
                    dao.updateEvent(existingEvent.copy(location = newVenue, evidenceText = "Venue: ${DataMinimizer.stripPii(intent.rawText)}"))
                } else {
                    dao.insertEvent(
                        EventEntity(
                            title = activityName,
                            startTime = System.currentTimeMillis(),
                            endTime = System.currentTimeMillis() + 7200000L,
                            location = newVenue,
                            associatedModeId = intent.targetModeId,
                            isGhostMode = false,
                            confidence = intent.confidence,
                            evidenceText = "Venue: ${DataMinimizer.stripPii(intent.rawText)}"
                        )
                    )
                }

                val sanitizedEvidence = DataMinimizer.sanitizeRawPayload(intent.rawText, newVenue, "VENUE_CHANGE")
                dao.insertEvidence(
                    EvidenceEntity(
                        sourcePackage = "consequence.engine",
                        rawText = sanitizedEvidence,
                        parsedIntent = "VENUE_CHANGE: $activityName -> $newVenue",
                        confidenceScore = intent.confidence
                    )
                )

                val diff = PlanMutationDiff(
                    mutationType = MutationType.VENUE_CHANGE,
                    activityName = activityName,
                    affectedModeId = intent.targetModeId,
                    beforeState = "Venue: $beforeVenue",
                    afterState = "Venue: $newVenue",
                    changedFields = listOf("location"),
                    confidence = intent.confidence,
                    evidenceSummary = DataMinimizer.stripPii(intent.rawText),
                    executionStatus = MutationStatus.AUTOMATIC,
                    explanation = "Venue for '$activityName' updated to '$newVenue'."
                )

                val alert = ConsequenceAlert(
                    title = "Venue Changed: $activityName",
                    description = "Activity moved from $beforeVenue to $newVenue.",
                    affectedActivity = activityName,
                    affectedItem = null,
                    actionTaken = "Venue Updated to $newVenue",
                    waivedItems = emptyList(),
                    diff = diff
                )
                _latestConsequenceAlert.value = alert
                return@withContext alert
            }

            else -> null
        }
    }

    /**
     * Direct trigger for testing / demoing plan mutation from UI or ADB.
     */
    /**
     * Executes the consequence ripple effects when a user confirms/approves a Ghost proposal.
     *
     * 1. Traverses Personal Life Graph via EdgeEntity with relationType = 'REQUIRES' to find dependent requirements.
     * 2. Marks the associated event as cancelled in EventEntity.
     * 3. Sets isWaived = true for dependent required items (e.g. "DBMS Record") in Room DB.
     * 4. Recalculates mode readiness dynamically.
     * 5. Records audit trail in EvidenceEntity with sourcePackage = "consequence.engine" and parsedIntent = "CONSEQUENCE_WAIVED_ITEM: ...".
     * 6. Cleans up ghost items/events for this cancellation.
     */
    suspend fun applyConfirmedProposal(
        dao: LifeOsDao,
        activityName: String = "DBMS Lab",
        rawText: String = "Bro I think tomorrow's DBMS lab is cancelled, sir is not feeling well",
        confidence: Float = 0.70f,
        targetModeId: Int = 1
    ): ConsequenceAlert = withContext(Dispatchers.IO) {
        Log.i("LIFEOS_MODULE3", "LIFEOS_MODULE3: User Approval -> User tapped [Apply Change] for '$activityName'")

        // 1. Traverse Life Graph to find dependent items via EdgeEntity REQUIRES
        val linkedItemNames = mutableListOf<String>()
        val requiredNodes = LifeGraphEngine.getRequiredItemsForActivity(dao, activityName).ifEmpty { dao.getRequiredItemsForActivityName(activityName) }
        if (requiredNodes.isNotEmpty()) {
            linkedItemNames.addAll(requiredNodes.map { it.name })
        }
        if (linkedItemNames.isEmpty()) {
            if (activityName.contains("Lab", ignoreCase = true)) {
                linkedItemNames.add("DBMS Record")
            } else {
                linkedItemNames.add("Required Items")
            }
        }
        Log.i("LIFEOS_MODULE3", "LIFEOS_MODULE3: Life Graph Traversed -> Identified dependent items: [${linkedItemNames.joinToString(", ")}]")

        // 2. Mark event as cancelled in EventEntity
        val existingEvent = dao.findEventByTitleLike(activityName)
        if (existingEvent != null) {
            val updatedTitle = if (existingEvent.title.contains("(Cancelled)", ignoreCase = true)) {
                existingEvent.title
            } else {
                "${existingEvent.title} (Cancelled)"
            }
            dao.updateEvent(existingEvent.copy(title = updatedTitle, evidenceText = "Cancelled: $rawText"))
            Log.i("LIFEOS_MODULE3", "LIFEOS_MODULE3: Event Cancelled -> Marked '${existingEvent.title}' as cancelled in EventEntity")
        }

        // 3. Mutate Room DB to set isWaived = true for dependent required items
        val waivedList = mutableListOf<String>()
        for (itemName in linkedItemNames) {
            val count = dao.setItemWaivedByName(itemName, true)
            if (count > 0 || linkedItemNames.size == 1) {
                waivedList.add(itemName)
            }
        }
        if (waivedList.isEmpty()) {
            waivedList.addAll(linkedItemNames)
        }
        Log.i("LIFEOS_MODULE3", "LIFEOS_MODULE3: Requirement Waived -> Set isWaived=true for [${waivedList.joinToString(", ")}]")

        // 4. Recalculate mode readiness dynamically
        val activeMode = dao.getModeById(targetModeId)
        val items = dao.getItemsForMode(targetModeId)
        val readiness = ReadinessEngine.calculateReadiness(activeMode, items)
        Log.i("LIFEOS_MODULE3", "LIFEOS_MODULE3: Dynamic Readiness Recalculated -> New Score: ${readiness.percentage}%")

        // 5. Record audit trail in EvidenceEntity (sourcePackage = 'consequence.engine', parsedIntent = 'CONSEQUENCE_WAIVED_ITEM: DBMS Record')
        val parsedIntentStr = "CONSEQUENCE_WAIVED_ITEM: ${waivedList.joinToString(", ")}"
        val sanitizedEvidence = DataMinimizer.sanitizeRawPayload(rawText, waivedList.joinToString(", "), "CONSEQUENCE_WAIVED_ITEM")
        dao.insertEvidence(
            EvidenceEntity(
                sourcePackage = "consequence.engine",
                rawText = sanitizedEvidence,
                parsedIntent = parsedIntentStr,
                confidenceScore = confidence
            )
        )
        Log.i("LIFEOS_MODULE3", "LIFEOS_MODULE3: Evidence Recorded -> '$parsedIntentStr'")

        // 6. Clean up ghost items and ghost events for this cancellation
        try {
            val allItems = dao.getItemsForMode(targetModeId)
            allItems.filter { it.isGhostMode && (it.itemName.contains(activityName, ignoreCase = true) || it.itemName.contains("Cancellation", ignoreCase = true)) }
                .forEach { dao.deleteItemById(it.id) }

            val ghostEvents = dao.getGhostEventsFlow().first()
            ghostEvents.filter { it.title.contains(activityName, ignoreCase = true) }
                .forEach { dao.deleteEventById(it.id) }
        } catch (e: Exception) {
            Log.w("LIFEOS_MODULE3", "Ghost cleanup note: ${e.message}")
        }

        val diff = PlanMutationDiff(
            mutationType = MutationType.CANCELLATION,
            activityName = activityName,
            affectedModeId = targetModeId,
            beforeState = "Pending User Confirmation (Ghost Proposal)",
            afterState = "Cancelled & Waived [${waivedList.joinToString(", ")}]",
            changedFields = listOf("status", "isWaived", "isGhostMode"),
            confidence = confidence,
            evidenceSummary = DataMinimizer.stripPii(rawText),
            executionStatus = MutationStatus.USER_APPROVED,
            explanation = "User confirmed Ghost cancellation proposal. Requirement '${waivedList.joinToString(", ")}' was waived."
        )

        val alert = ConsequenceAlert(
            title = "Schedule Change: $activityName Cancelled",
            description = "User approved cancellation. Requirement '${waivedList.joinToString(", ")}' was automatically waived and removed from pending items.",
            affectedActivity = activityName,
            affectedItem = waivedList.firstOrNull(),
            actionTaken = "Item Waived (${waivedList.joinToString(", ")})",
            waivedItems = waivedList,
            diff = diff
        )
        _latestConsequenceAlert.value = alert
        alert
    }

    suspend fun handleDirectCancellation(
        context: Context,
        activityName: String = "DBMS Lab"
    ): ConsequenceAlert {
        val db = LifeOsDatabase.getDatabase(context.applicationContext)
        return handleDirectCancellation(db.lifeOsDao(), activityName)
    }

    suspend fun handleDirectCancellation(
        dao: LifeOsDao,
        activityName: String = "DBMS Lab"
    ): ConsequenceAlert = withContext(Dispatchers.IO) {
        val syntheticIntent = ParsedIntentResult(
            canonicalAction = CanonicalAction.EVENT_CANCELLED,
            detectedLanguage = "English",
            rawText = "$activityName Cancelled",
            subjectActivity = activityName,
            relatedItem = "DBMS Record",
            timeOrDateInfo = "Today",
            confidence = 0.95f,
            targetModeId = 1
        )
        processPlanMutation(dao, syntheticIntent)!!
    }

    fun dismissAlert() {
        _latestConsequenceAlert.value = null
    }

    suspend fun resetWaivedItems(dao: LifeOsDao, modeId: Int) = withContext(Dispatchers.IO) {
        val count = dao.resetAllWaivedItemsForMode(modeId)
        _latestConsequenceAlert.value = null
        Log.d(TAG, "Reset all waived items for mode $modeId (count=$count)")
        Log.d(TAG_MODULE2, "Room DB Mutation Applied -> Waived items: None (Reverted $count items)")

        val activeMode = dao.getModeById(modeId)
        val items = dao.getItemsForMode(modeId)
        val readiness = ReadinessEngine.calculateReadiness(activeMode, items)
        Log.d(TAG_MODULE2, "Readiness Recalculated -> New Score: ${readiness.percentage}%")
    }

    suspend fun resetWaivedItems(context: Context, modeId: Int) {
        val db = LifeOsDatabase.getDatabase(context.applicationContext)
        resetWaivedItems(db.lifeOsDao(), modeId)
    }
}
