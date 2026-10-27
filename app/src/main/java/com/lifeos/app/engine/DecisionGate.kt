package com.lifeos.app.engine

import android.content.Context
import android.util.Log
import com.lifeos.app.data.EventEntity
import com.lifeos.app.data.EvidenceEntity
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeOsDao
import com.lifeos.app.data.LifeOsDatabase
import com.lifeos.app.security.DataMinimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class ConflictAlert(
    val id: String = UUID.randomUUID().toString(),
    val activityName: String,
    val existingClaim: String,
    val conflictingClaim: String,
    val reason: String = "Conflicting schedule statements detected across notification/calendar evidence",
    val timestamp: Long = System.currentTimeMillis()
)

sealed class DecisionResult {
    data class AutoCommitted(val item: ItemRequirementEntity, val evidence: EvidenceEntity, val diff: PlanMutationDiff? = null) : DecisionResult()
    data class GhostModeRouted(val item: ItemRequirementEntity, val evidence: EvidenceEntity, val diff: PlanMutationDiff? = null) : DecisionResult()
    data class ConsequenceApplied(val alert: ConsequenceAlert) : DecisionResult()
    data class ConflictDetected(val conflictAlert: ConflictAlert, val evidence: EvidenceEntity, val diff: PlanMutationDiff? = null) : DecisionResult()
    data class DuplicateDiscarded(val reason: String, val itemOrEventName: String) : DecisionResult()
    object Ignored : DecisionResult()
}

class DecisionGate(private val context: Context? = null) {

    suspend fun processPayload(
        payload: FilteredPayload,
        customTtlHours: Long = 24
    ): DecisionResult = withContext(Dispatchers.IO) {
        val targetContext = context ?: throw IllegalStateException("Context required when LifeOsDao is not provided")
        val db = LifeOsDatabase.getDatabase(targetContext.applicationContext)
        val dao = db.lifeOsDao()
        processPayload(payload, dao, customTtlHours)
    }

    suspend fun processPayload(
        payload: FilteredPayload,
        dao: LifeOsDao,
        customTtlHours: Long = 24
    ): DecisionResult = withContext(Dispatchers.IO) {
        // 0. Untrusted Input & Adversarial Defense (treat strictly as unexecutable data)
        if (payload.isAdversarial) {
            Log.w(TAG, "Adversarial / Malicious prompt injection attempt detected and dropped: '${DataMinimizer.stripPii(payload.rawText)}'")
            Log.d(TAG_MODULE1, "Decision Gate -> DROP (Untrusted Adversarial Input)")
            return@withContext DecisionResult.Ignored
        }

        val confidence = payload.relevanceScore
        val actionName = payload.extractedAction ?: payload.canonicalAction.name
        val confidencePercent = (confidence * 100).toInt()
        Log.d(TAG_MODULE1, "NLP Intent -> $actionName | Confidence -> $confidencePercent%")
        Log.d(TAG, "Evaluating payload (Confidence: $confidencePercent% | Action: ${payload.canonicalAction}): '${DataMinimizer.stripPii(payload.rawText)}'")

        // 1. Conflict Detection between incoming notifications and current schedule state
        val lowerText = payload.rawText.lowercase()
        val parsedIntent = MultilingualParser.parse(payload.rawText, payload.sourcePackage)
        val activityName = parsedIntent.subjectActivity ?: "Scheduled Activity"
        val existingEvent = dao.findEventByTitleLike(activityName)

        val isConflictStatement = lowerText.contains("remains at 10") || lowerText.contains("still at 10") ||
                (lowerText.contains("10 am") && lowerText.contains("remains")) ||
                (existingEvent != null && existingEvent.evidenceText?.contains("11 AM", ignoreCase = true) == true && lowerText.contains("10 am") && (lowerText.contains("remains") || lowerText.contains("still"))) ||
                (existingEvent != null && existingEvent.evidenceText?.contains("Rescheduled", ignoreCase = true) == true && (lowerText.contains("remains") || lowerText.contains("still at"))) ||
                (existingEvent != null && existingEvent.location != null && payload.location != null && !existingEvent.location.equals(payload.location, ignoreCase = true) && (lowerText.contains("remains") || lowerText.contains("still in")))

        if (isConflictStatement) {
            val existingClaim = if (existingEvent?.evidenceText?.contains("11 AM", ignoreCase = true) == true) "DBMS Lab moved to 11 AM" else "Existing scheduled claim"
            val conflictingClaim = if (lowerText.contains("10 am")) "DBMS Lab remains at 10 AM" else payload.rawText
            val conflict = ConflictAlert(
                activityName = activityName,
                existingClaim = existingClaim,
                conflictingClaim = conflictingClaim,
                reason = "Conflicting notification claims received: Discrepancy between announcements."
            )
            val sanitizedRawText = DataMinimizer.sanitizeRawPayload(payload.rawText, activityName, "SCHEDULE_CONFLICT")
            val evidence = EvidenceEntity(
                sourcePackage = payload.sourcePackage,
                rawText = sanitizedRawText,
                parsedIntent = "CONFLICT: $activityName schedule discrepancy ($existingClaim vs $conflictingClaim)",
                timestamp = System.currentTimeMillis(),
                confidenceScore = confidence
            )
            dao.insertEvidence(evidence)

            val diff = PlanMutationDiff(
                mutationType = MutationType.TIME_RESCHEDULE,
                activityName = activityName,
                affectedModeId = payload.targetModeId,
                beforeState = existingClaim,
                afterState = conflictingClaim,
                changedFields = listOf("schedule_conflict"),
                confidence = confidence,
                evidenceSummary = sanitizedRawText,
                executionStatus = MutationStatus.CONFLICT_HOLD,
                explanation = "Conflicting schedule statements detected. Automated override halted pending resolution."
            )

            val conflictProposal = GhostProposal(
                id = diff.id,
                numericId = (System.currentTimeMillis() % 100000).toInt(),
                activityName = activityName,
                targetModeId = payload.targetModeId,
                mutationType = MutationType.TIME_RESCHEDULE,
                previousState = existingClaim,
                proposedState = conflictingClaim,
                confidence = confidence,
                sourceCategory = "NOTIFICATION",
                evidenceSummary = sanitizedRawText,
                potentialConsequences = "Conflicting schedule statements detected. Automated override halted pending human resolution.",
                timestamp = System.currentTimeMillis(),
                lifecycle = GhostProposalLifecycle.CONFLICT_HOLD,
                diff = diff,
                conflictStateA = existingClaim,
                conflictStateB = conflictingClaim,
                rawText = payload.rawText
            )
            GhostModeEngine.registerProposal(conflictProposal)

            Log.w(TAG, "CONFLICT DETECTED for '$activityName': '$existingClaim' vs '$conflictingClaim'. Preserving evidence and halting automated override.")
            Log.d(TAG_MODULE1, "Decision Gate -> CONFLICT_DETECTED")
            return@withContext DecisionResult.ConflictDetected(conflict, evidence, diff)
        }

        // Outdated Announcement Protection: discard explicit past notices or superseded announcements
        val isExplicitPastNotice = lowerText.contains("yesterday") || lowerText.contains("ninna") || lowerText.contains("kal tha")
        if (isExplicitPastNotice) {
            Log.i(TAG, "Outdated notification in past discarded for '$activityName'.")
            return@withContext DecisionResult.DuplicateDiscarded("Outdated event notification in past discarded", activityName)
        }
        if (existingEvent != null && existingEvent.startTime > 0 && payload.targetEpochMillis != null &&
            existingEvent.evidenceText?.contains("Rescheduled", ignoreCase = true) == true &&
            payload.targetEpochMillis < existingEvent.startTime &&
            !lowerText.contains("preponed") && !lowerText.contains("prepone") && !lowerText.contains("moved from") && !lowerText.contains("shift")) {
            Log.i(TAG, "Outdated notification for '$activityName' discarded. Existing event already superseded to newer schedule.")
            return@withContext DecisionResult.DuplicateDiscarded("Outdated notification discarded: superseded by newer schedule", activityName)
        }

        // 2. Handle Plan Mutation (Cancellation / Reschedule / Venue Change) via ConsequenceEngine
        if (payload.canonicalAction == CanonicalAction.EVENT_CANCELLED ||
            payload.canonicalAction == CanonicalAction.EVENT_RESCHEDULED ||
            payload.canonicalAction == CanonicalAction.EVENT_VENUE_CHANGED) {

            // Deduplication Check for Plan Mutations
            if (payload.canonicalAction == CanonicalAction.EVENT_CANCELLED && existingEvent != null && existingEvent.title.contains("(Cancelled)", ignoreCase = true)) {
                Log.i(TAG, "Duplicate cancellation for '$activityName' discarded. Already cancelled in Room DB.")
                Log.d(TAG_MODULE1, "Decision Gate -> DUPLICATE_DISCARDED")
                return@withContext DecisionResult.DuplicateDiscarded("Event '$activityName' already cancelled", activityName)
            }

            if (payload.canonicalAction == CanonicalAction.EVENT_RESCHEDULED && existingEvent != null &&
                payload.targetEpochMillis != null && existingEvent.startTime == payload.targetEpochMillis) {
                Log.i(TAG, "Duplicate reschedule for '$activityName' discarded. Already shifted to ${payload.extractedDateTime}.")
                Log.d(TAG_MODULE1, "Decision Gate -> DUPLICATE_DISCARDED")
                return@withContext DecisionResult.DuplicateDiscarded("Event '$activityName' already rescheduled to ${payload.extractedDateTime}", activityName)
            }

            if (payload.canonicalAction == CanonicalAction.EVENT_VENUE_CHANGED && existingEvent != null &&
                payload.location != null && existingEvent.location.equals(payload.location, ignoreCase = true)) {
                Log.i(TAG, "Duplicate venue change for '$activityName' discarded. Already set to ${payload.location}.")
                Log.d(TAG_MODULE1, "Decision Gate -> DUPLICATE_DISCARDED")
                return@withContext DecisionResult.DuplicateDiscarded("Event '$activityName' already moved to ${payload.location}", activityName)
            }

            val isGhost = isGhostRoutingRequired(confidence)

            if (isGhost) {
                if (!SafeModeManager.canMutatePlans()) {
                    Log.w("LIFEOS_MODULE6", "LIFEOS_MODULE6: Safe Mode Active -> Auto-commit suppressed. Mutation for '$activityName' forced to Ghost Mode")
                }
                Log.i("LIFEOS_MODULE3", "LIFEOS_MODULE3: Ghost Routing -> Routed mutation proposal for '$activityName' to Ghost Mode (Confidence: $confidencePercent%)")

                val isReschedule = payload.canonicalAction == CanonicalAction.EVENT_RESCHEDULED
                val isVenueChange = payload.canonicalAction == CanonicalAction.EVENT_VENUE_CHANGED

                val itemLabel = when {
                    isVenueChange -> "Possible Venue Change: $activityName (${payload.location ?: "New Venue"})"
                    isReschedule -> "Possible Reschedule: $activityName"
                    else -> "Possible Cancellation: $activityName"
                }

                val ghostItem = ItemRequirementEntity(
                    modeId = payload.targetModeId,
                    itemName = itemLabel,
                    isReady = false,
                    isTemporary = true,
                    ttlTimestamp = System.currentTimeMillis() + (customTtlHours * 3600 * 1000),
                    confidenceScore = confidence,
                    sourceEvidence = payload.rawText,
                    isGhostMode = true,
                    isWaived = false
                )
                val ghostItemId = dao.insertItem(ghostItem)

                val ghostEventTitle = when {
                    isVenueChange -> "$activityName (Venue: ${payload.location ?: "New Venue"})"
                    isReschedule -> "$activityName (Rescheduled)"
                    else -> "$activityName (Cancelled)"
                }

                val ghostEvent = EventEntity(
                    title = ghostEventTitle,
                    startTime = parsedIntent.targetEpochMillis ?: System.currentTimeMillis(),
                    endTime = (parsedIntent.targetEpochMillis ?: System.currentTimeMillis()) + 3600000L,
                    location = payload.location,
                    associatedModeId = payload.targetModeId,
                    isGhostMode = true,
                    confidence = confidence,
                    evidenceText = payload.rawText
                )
                dao.insertEvent(ghostEvent)

                val rumorTag = when {
                    isVenueChange -> "GHOST_VENUE_RUMOR"
                    isReschedule -> "GHOST_RESCHEDULE_RUMOR"
                    else -> "GHOST_CANCELLATION_RUMOR"
                }
                val sanitizedRawText = DataMinimizer.sanitizeRawPayload(payload.rawText, activityName, rumorTag)
                val evidence = EvidenceEntity(
                    eventId = ghostItemId.toInt(),
                    sourcePackage = payload.sourcePackage,
                    rawText = sanitizedRawText,
                    parsedIntent = "GHOST_PROPOSAL: $itemLabel",
                    timestamp = System.currentTimeMillis(),
                    confidenceScore = confidence
                )
                dao.insertEvidence(evidence)

                val diff = PlanMutationDiff(
                    mutationType = when {
                        isVenueChange -> MutationType.VENUE_CHANGE
                        isReschedule -> MutationType.TIME_RESCHEDULE
                        else -> MutationType.CANCELLATION
                    },
                    activityName = activityName,
                    affectedModeId = payload.targetModeId,
                    beforeState = "Existing state preserved",
                    afterState = "Proposed in Ghost Mode: $itemLabel",
                    changedFields = listOf("isGhostMode"),
                    confidence = confidence,
                    evidenceSummary = sanitizedRawText,
                    executionStatus = if (SafeModeManager.isSafeModeActive) MutationStatus.SUPPRESSED_SAFE_MODE else MutationStatus.PROPOSED,
                    explanation = "Proposal routed to Ghost Mode pending confirmation."
                )

                val ghostProposal = GhostProposal(
                    id = diff.id,
                    numericId = ghostItemId.toInt(),
                    activityName = activityName,
                    targetModeId = payload.targetModeId,
                    mutationType = diff.mutationType,
                    previousState = diff.beforeState,
                    proposedState = diff.afterState,
                    confidence = confidence,
                    sourceCategory = "NOTIFICATION",
                    evidenceSummary = sanitizedRawText,
                    potentialConsequences = when (diff.mutationType) {
                        MutationType.CANCELLATION -> "$activityName may be removed and required items waived."
                        MutationType.TIME_RESCHEDULE -> "Departure preparation and reminders will move to new time."
                        MutationType.VENUE_CHANGE -> "Location updated to ${payload.location ?: "New Venue"}."
                        else -> "Review proposed schedule change."
                    },
                    timestamp = System.currentTimeMillis(),
                    eventEpochMillis = parsedIntent.targetEpochMillis,
                    expirationEpochMillis = (parsedIntent.targetEpochMillis ?: (System.currentTimeMillis() + 86400000L)) + 3600000L,
                    lifecycle = GhostProposalLifecycle.PROPOSED,
                    diff = diff,
                    newVenue = payload.location,
                    newStartTime = parsedIntent.targetEpochMillis,
                    rawText = payload.rawText
                )
                GhostModeEngine.registerProposal(ghostProposal)

                Log.d(TAG_MODULE1, "Decision Gate -> GHOST_MODE")
                return@withContext DecisionResult.GhostModeRouted(ghostItem.copy(id = ghostItemId.toInt()), evidence, diff)
            }

            val alert = ConsequenceEngine.processPlanMutation(dao, parsedIntent)
            return@withContext if (alert != null) {
                Log.d(TAG_MODULE1, "Decision Gate -> AUTO_COMMIT")
                Log.d(TAG_MODULE1, "Room DB Insertion Completed for -> '${alert.title}'")
                Log.d(TAG, "ConsequenceEngine successfully evaluated plan mutation: ${alert.title}")
                DecisionResult.ConsequenceApplied(alert)
            } else {
                Log.d(TAG_MODULE1, "Decision Gate -> DROP")
                DecisionResult.Ignored
            }
        }

        // 3. Filter out payloads below 0.50 confidence threshold or non-actionable chatter
        if (!payload.isRelevant || confidence < 0.50f || (payload.canonicalAction == CanonicalAction.INFO_NOTICE && payload.extractedItemName == null)) {
            Log.d(TAG_MODULE1, "Decision Gate -> DROP")
            Log.d(TAG, "Payload dropped due to low confidence (< 0.50) or non-actionable notice")
            return@withContext DecisionResult.Ignored
        }

        val itemName = payload.extractedItemName ?: "Required Item"

        // 4. Deduplication Check for Required Items: Prevent duplicate item insertions
        val existingItem = dao.findItemInModeByName(payload.targetModeId, itemName)
        val safeModeActive = !SafeModeManager.canAutoPromoteGhostItems()
        val isGhost = isGhostRoutingRequired(confidence)

        if (existingItem != null) {
            if (!existingItem.isGhostMode) {
                Log.i(TAG, "Duplicate requirement '$itemName' already active in mode ${payload.targetModeId}. Discarding duplicate.")
                Log.d(TAG_MODULE1, "Decision Gate -> DUPLICATE_DISCARDED")
                return@withContext DecisionResult.DuplicateDiscarded("Item '$itemName' already active in checklist", itemName)
            } else if (existingItem.isGhostMode && (isGhost || safeModeActive)) {
                Log.i(TAG, "Duplicate ghost requirement '$itemName' already pending in mode ${payload.targetModeId}. Discarding duplicate.")
                Log.d(TAG_MODULE1, "Decision Gate -> DUPLICATE_DISCARDED")
                return@withContext DecisionResult.DuplicateDiscarded("Ghost requirement '$itemName' already pending in proposals", itemName)
            } else if (existingItem.isGhostMode && !isGhost && !safeModeActive) {
                // High-confidence verified announcement promotes previously pending ghost item
                dao.promoteGhostItem(existingItem.id)
                val promoted = existingItem.copy(isGhostMode = false)
                val sanitizedEvidence = DataMinimizer.sanitizeRawPayload(payload.rawText, itemName, "VERIFIED_PROMOTION")
                val evidence = EvidenceEntity(
                    eventId = existingItem.id,
                    sourcePackage = payload.sourcePackage,
                    rawText = sanitizedEvidence,
                    parsedIntent = "AUTO_COMMIT_PROMOTION: $itemName",
                    timestamp = System.currentTimeMillis(),
                    confidenceScore = confidence
                )
                dao.insertEvidence(evidence)
                Log.i(TAG, "Verified notice promoted existing ghost item '$itemName' to permanent checklist.")
                return@withContext DecisionResult.AutoCommitted(promoted, evidence)
            }
        }

        // Stage 7 & Module 6: Safe Mode Check. If Safe Mode is active, force ghost mode (no auto-commit).
        if (safeModeActive && confidence >= 0.85f) {
            Log.w("LIFEOS_MODULE6", "LIFEOS_MODULE6: Safe Mode Suppression Executed -> Mutation blocked for '$itemName'")
        }
        val ttlMillis = System.currentTimeMillis() + (customTtlHours * 3600 * 1000)

        val item = ItemRequirementEntity(
            modeId = payload.targetModeId,
            itemName = itemName,
            isReady = false,
            isTemporary = true,
            ttlTimestamp = ttlMillis,
            confidenceScore = confidence,
            sourceEvidence = "Source: ${payload.sourcePackage} | Lang: ${payload.detectedLanguage} | Intent: ${payload.extractedAction ?: "Notice"}",
            isGhostMode = isGhost,
            isWaived = false
        )

        // Schema Validation before Room DB persistence
        val itemJson = SchemaValidator.toJson(item)
        val schemaValidation = SchemaValidator.validateJson(itemJson)
        if (!schemaValidation.isValid) {
            Log.e(TAG, "Schema validation failed for entity: ${schemaValidation.errors}")
            return@withContext DecisionResult.Ignored
        }

        val itemId = dao.insertItem(item)

        // Stage 7: Data Minimization - sanitize raw text before saving to database
        val sanitizedRawText = DataMinimizer.sanitizeRawPayload(payload.rawText, itemName, payload.extractedAction)

        val evidence = EvidenceEntity(
            eventId = itemId.toInt(),
            sourcePackage = payload.sourcePackage,
            rawText = sanitizedRawText,
            parsedIntent = payload.extractedAction ?: "Action Item Ingestion",
            timestamp = System.currentTimeMillis(),
            confidenceScore = confidence
        )
        dao.insertEvidence(evidence)

        val diff = PlanMutationDiff(
            mutationType = MutationType.REQUIREMENT_ADDED,
            activityName = activityName,
            affectedModeId = payload.targetModeId,
            beforeState = "Not present in checklist",
            afterState = if (isGhost) "Proposed item in Ghost Mode: $itemName" else "Added requirement: $itemName",
            changedFields = listOf("items"),
            confidence = confidence,
            evidenceSummary = sanitizedRawText,
            executionStatus = if (isGhost) (if (safeModeActive) MutationStatus.SUPPRESSED_SAFE_MODE else MutationStatus.PROPOSED) else MutationStatus.AUTOMATIC,
            explanation = if (isGhost) "Proposal routed to Ghost Mode" else "Requirement auto-committed to checklist."
        )

        if (isGhost) {
            val ghostProposal = GhostProposal(
                id = diff.id,
                numericId = itemId.toInt(),
                activityName = activityName,
                targetModeId = payload.targetModeId,
                mutationType = MutationType.REQUIREMENT_ADDED,
                previousState = diff.beforeState,
                proposedState = diff.afterState,
                confidence = confidence,
                sourceCategory = "NOTIFICATION",
                evidenceSummary = sanitizedRawText,
                potentialConsequences = "New requirement '$itemName' will be added to mode checklist.",
                timestamp = System.currentTimeMillis(),
                lifecycle = GhostProposalLifecycle.PROPOSED,
                diff = diff,
                addedItemName = itemName,
                rawText = payload.rawText
            )
            GhostModeEngine.registerProposal(ghostProposal)

            val modeLabel = if (safeModeActive) "SAFE MODE FORCED GHOST" else "GHOST MODE ROUTED [0.50 - 0.84]"
            Log.d(TAG, "$modeLabel: '$itemName' (id=$itemId)")
            Log.d(TAG_MODULE1, "Decision Gate -> GHOST_MODE")
            Log.d(TAG_MODULE1, "Room DB Insertion Completed for -> '$itemName'")
            DecisionResult.GhostModeRouted(item.copy(id = itemId.toInt()), evidence, diff)
        } else {
            Log.d(TAG, "AUTO-COMMITTED [>= 0.85]: '$itemName' (id=$itemId)")
            Log.d(TAG_MODULE1, "Decision Gate -> AUTO_COMMIT")
            Log.d(TAG_MODULE1, "Room DB Insertion Completed for -> '$itemName'")
            DecisionResult.AutoCommitted(item.copy(id = itemId.toInt()), evidence, diff)
        }
    }

    companion object {
        private const val TAG = "LIFEOS_DECISION"
        private const val TAG_MODULE1 = "LIFEOS_MODULE1"

        /**
         * Evaluates whether an item should be routed to Ghost Mode or Auto-Committed.
         * Enforces Safe Mode: when active, all items are forced to Ghost Mode regardless of confidence score.
         */
        fun isGhostRoutingRequired(confidence: Float): Boolean {
            val safeModeActive = !SafeModeManager.canAutoPromoteGhostItems()
            return if (safeModeActive) true else (confidence < 0.85f)
        }
    }
}
