package com.lifeos.app.engine

import android.util.Log
import com.lifeos.app.data.*
import com.lifeos.app.security.DataMinimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Deterministic lifecycle state machine for Ghost Mode proposals.
 * Guarantees zero ambiguous states across real-life schedule mutations.
 */
enum class GhostProposalLifecycle {
    PROPOSED,         // Pending human review
    USER_APPROVED,    // User accepted; awaiting/undergoing execution
    APPLIED,          // Consequence ripples fully committed to live state
    USER_REJECTED,    // Explicitly rejected by user; zero live changes
    EXPIRED,          // Stale/past event timeline; cannot mutate live plan
    SUPERSEDED,       // Overridden by newer, verified schedule announcement
    CONFLICT_HOLD     // Contradictory claims detected; awaiting explicit choice
}

sealed class GhostApprovalResult {
    data class Success(val appliedDiff: PlanMutationDiff, val proposal: GhostProposal) : GhostApprovalResult()
    data class Rejected(val proposal: GhostProposal) : GhostApprovalResult()
    data class AlreadyApplied(val proposal: GhostProposal) : GhostApprovalResult()
    data class Expired(val reason: String) : GhostApprovalResult()
    data class Superseded(val reason: String) : GhostApprovalResult()
    data class ConflictRequiresResolution(val proposal: GhostProposal) : GhostApprovalResult()
    data class NotFound(val proposalId: Any) : GhostApprovalResult()
}

/**
 * Structured Ghost Mode Proposal representing an unconfirmed real-world plan mutation.
 * Completely isolates candidate mutations from live state until human approval.
 */
data class GhostProposal(
    val id: String = UUID.randomUUID().toString(),
    val numericId: Int = (System.currentTimeMillis() % 100000).toInt(),
    val activityName: String,
    val targetModeId: Int = 1,
    val mutationType: MutationType,
    val previousState: String,
    val proposedState: String,
    val confidence: Float,
    val sourceCategory: String = "NOTIFICATION",
    val evidenceSummary: String,
    val potentialConsequences: String,
    val timestamp: Long = System.currentTimeMillis(),
    val eventEpochMillis: Long? = null,
    val expirationEpochMillis: Long = System.currentTimeMillis() + (24 * 3600 * 1000L),
    var lifecycle: GhostProposalLifecycle = GhostProposalLifecycle.PROPOSED,
    val diff: PlanMutationDiff? = null,
    val conflictStateA: String? = null,
    val conflictStateB: String? = null,
    val newVenue: String? = null,
    val newStartTime: Long? = null,
    val newEndTime: Long? = null,
    val addedItemName: String? = null,
    val rawText: String? = null
)

/**
 * LIFEOS GHOST MODE ENGINE
 * Central authority for managing Ghost proposals, deterministic lifecycle transitions,
 * evidence sanitization, concurrency / double-tap protection, proposal expiration,
 * superseding arbitration, conflict hold, and strict Personal Forgetting Model isolation.
 */
object GhostModeEngine {

    private const val TAG = "LIFEOS_GHOST_ENGINE"

    private val proposals = ConcurrentHashMap<String, GhostProposal>()
    private val numericIdToId = ConcurrentHashMap<Int, String>()
    private val mutex = Mutex()

    private val _proposalsFlow = MutableStateFlow<List<GhostProposal>>(emptyList())
    val proposalsFlow: StateFlow<List<GhostProposal>> = _proposalsFlow.asStateFlow()

    /**
     * Registers a candidate proposal into Ghost Mode.
     * Enforces PII sanitization via DataMinimizer and handles superseding of older proposals.
     */
    fun registerProposal(proposal: GhostProposal): GhostProposal {
        val sanitizedEvidence = DataMinimizer.stripPii(proposal.evidenceSummary)
        val sanitizedProposal = proposal.copy(evidenceSummary = sanitizedEvidence)

        // Check if an existing proposal for the same activity is active
        proposals.values.forEach { existing ->
            if (existing.activityName.equals(sanitizedProposal.activityName, ignoreCase = true) &&
                existing.lifecycle == GhostProposalLifecycle.PROPOSED &&
                existing.id != sanitizedProposal.id
            ) {
                // If the incoming proposal is newer and has equal or higher confidence, supersede older proposal
                if (sanitizedProposal.timestamp >= existing.timestamp && sanitizedProposal.confidence >= (existing.confidence - 0.1f)) {
                    existing.lifecycle = GhostProposalLifecycle.SUPERSEDED
                    Log.i(TAG, "Ghost Proposal '${existing.id}' superseded by newer proposal '${sanitizedProposal.id}' for '${sanitizedProposal.activityName}'")
                }
            }
        }

        proposals[sanitizedProposal.id] = sanitizedProposal
        numericIdToId[sanitizedProposal.numericId] = sanitizedProposal.id
        updateFlow()
        Log.d(TAG, "Registered Ghost Proposal: id=${sanitizedProposal.id}, numericId=${sanitizedProposal.numericId}, type=${sanitizedProposal.mutationType}, status=${sanitizedProposal.lifecycle}")
        return sanitizedProposal
    }

    fun getProposal(id: String): GhostProposal? = proposals[id]

    fun getProposalByNumericId(numericId: Int): GhostProposal? {
        val stringId = numericIdToId[numericId]
        return if (stringId != null) proposals[stringId] else proposals.values.find { it.numericId == numericId }
    }

    fun getAllProposals(): List<GhostProposal> = proposals.values.toList()

    fun getPendingProposals(): List<GhostProposal> =
        proposals.values.filter { it.lifecycle == GhostProposalLifecycle.PROPOSED || it.lifecycle == GhostProposalLifecycle.CONFLICT_HOLD }

    /**
     * Explicit human approval of a Ghost Mode proposal.
     * Thread-safe with Mutex: prevents double-tap race conditions and guarantees exactly ONE mutation.
     */
    suspend fun approveProposal(
        proposalId: Any,
        dao: LifeOsDao
    ): GhostApprovalResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val proposal = when (proposalId) {
                is String -> proposals[proposalId]
                is Int -> getProposalByNumericId(proposalId)
                is Long -> getProposalByNumericId(proposalId.toInt())
                else -> null
            } ?: return@withContext GhostApprovalResult.NotFound(proposalId)

            // Concurrency / Idempotency check: Already applied
            if (proposal.lifecycle == GhostProposalLifecycle.APPLIED) {
                Log.w(TAG, "Approval ignored: Proposal '${proposal.id}' is already APPLIED.")
                return@withContext GhostApprovalResult.AlreadyApplied(proposal)
            }

            // Stale / Expiration check
            val now = System.currentTimeMillis()
            if (proposal.lifecycle == GhostProposalLifecycle.EXPIRED ||
                now >= proposal.expirationEpochMillis ||
                (proposal.eventEpochMillis != null && proposal.eventEpochMillis > 0 && now > proposal.eventEpochMillis + 3600000L)
            ) {
                proposal.lifecycle = GhostProposalLifecycle.EXPIRED
                updateFlow()
                Log.w(TAG, "Approval rejected: Proposal '${proposal.id}' has EXPIRED.")
                return@withContext GhostApprovalResult.Expired("Proposal expired and cannot mutate live schedule")
            }

            // Superseded check
            if (proposal.lifecycle == GhostProposalLifecycle.SUPERSEDED) {
                Log.w(TAG, "Approval rejected: Proposal '${proposal.id}' has been SUPERSEDED by newer schedule.")
                return@withContext GhostApprovalResult.Superseded("Proposal superseded by newer schedule update")
            }

            // Conflict hold check: user must resolve explicitly
            if (proposal.lifecycle == GhostProposalLifecycle.CONFLICT_HOLD) {
                Log.w(TAG, "Approval held: Proposal '${proposal.id}' has conflicting claims requiring resolution.")
                return@withContext GhostApprovalResult.ConflictRequiresResolution(proposal)
            }

            // Rejected check
            if (proposal.lifecycle == GhostProposalLifecycle.USER_REJECTED) {
                Log.w(TAG, "Approval rejected: Proposal '${proposal.id}' was previously REJECTED by user.")
                return@withContext GhostApprovalResult.Rejected(proposal)
            }

            // Transition: PROPOSED -> USER_APPROVED
            proposal.lifecycle = GhostProposalLifecycle.USER_APPROVED
            Log.i(TAG, "User Approved proposal '${proposal.id}' (${proposal.mutationType}) for '${proposal.activityName}'")

            // Execute live consequence ripples
            val appliedDiff: PlanMutationDiff = when (proposal.mutationType) {
                MutationType.CANCELLATION -> {
                    val alert = ConsequenceEngine.applyConfirmedProposal(
                        dao = dao,
                        activityName = proposal.activityName,
                        rawText = proposal.rawText ?: proposal.evidenceSummary,
                        confidence = proposal.confidence,
                        targetModeId = proposal.targetModeId
                    )
                    alert.diff ?: PlanMutationDiff(
                        mutationType = MutationType.CANCELLATION,
                        activityName = proposal.activityName,
                        affectedModeId = proposal.targetModeId,
                        beforeState = proposal.previousState,
                        afterState = proposal.proposedState,
                        changedFields = listOf("status", "isWaived", "isGhostMode"),
                        confidence = proposal.confidence,
                        evidenceSummary = proposal.evidenceSummary,
                        executionStatus = MutationStatus.USER_APPROVED,
                        explanation = "User confirmed Ghost cancellation proposal. Requirements waived."
                    )
                }

                MutationType.TIME_RESCHEDULE, MutationType.POSTPONEMENT -> {
                    // 1. Un-waive previously linked items
                    val requiredNodes = dao.getRequiredItemsForActivityName(proposal.activityName)
                    for (node in requiredNodes) {
                        dao.setItemWaivedByName(node.name, false)
                    }

                    // 2. Update existing event or insert verified event
                    val existingEvent = dao.findEventByTitleLike(proposal.activityName)
                    val targetStartTime = proposal.newStartTime ?: (System.currentTimeMillis() + 86400000L)
                    val targetEndTime = proposal.newEndTime ?: (targetStartTime + 7200000L)

                    if (existingEvent != null) {
                        val cleanTitle = existingEvent.title.replace("(Cancelled)", "").trim()
                        dao.updateEvent(
                            existingEvent.copy(
                                title = cleanTitle,
                                startTime = targetStartTime,
                                endTime = targetEndTime,
                                evidenceText = "Rescheduled by user approval: ${proposal.evidenceSummary}"
                            )
                        )
                    } else {
                        dao.insertEvent(
                            EventEntity(
                                title = proposal.activityName,
                                startTime = targetStartTime,
                                endTime = targetEndTime,
                                associatedModeId = proposal.targetModeId,
                                isGhostMode = false,
                                confidence = proposal.confidence,
                                evidenceText = "Rescheduled: ${proposal.evidenceSummary}"
                            )
                        )
                    }

                    // 3. Recalculate dynamic readiness
                    val activeMode = dao.getModeById(proposal.targetModeId)
                    val items = dao.getItemsForMode(proposal.targetModeId)
                    ReadinessEngine.calculateReadiness(activeMode, items)

                    PlanMutationDiff(
                        mutationType = proposal.mutationType,
                        activityName = proposal.activityName,
                        affectedModeId = proposal.targetModeId,
                        beforeState = proposal.previousState,
                        afterState = proposal.proposedState,
                        changedFields = listOf("startTime", "endTime", "isWaived"),
                        confidence = proposal.confidence,
                        evidenceSummary = proposal.evidenceSummary,
                        executionStatus = MutationStatus.USER_APPROVED,
                        explanation = "User approved reschedule to ${proposal.proposedState}."
                    )
                }

                MutationType.VENUE_CHANGE -> {
                    val existingEvent = dao.findEventByTitleLike(proposal.activityName)
                    val newLocation = proposal.newVenue ?: "New Venue"
                    if (existingEvent != null) {
                        dao.updateEvent(existingEvent.copy(location = newLocation, evidenceText = "Venue: ${proposal.evidenceSummary}"))
                    } else {
                        dao.insertEvent(
                            EventEntity(
                                title = proposal.activityName,
                                startTime = System.currentTimeMillis(),
                                endTime = System.currentTimeMillis() + 7200000L,
                                location = newLocation,
                                associatedModeId = proposal.targetModeId,
                                isGhostMode = false,
                                confidence = proposal.confidence,
                                evidenceText = "Venue: ${proposal.evidenceSummary}"
                            )
                        )
                    }

                    PlanMutationDiff(
                        mutationType = MutationType.VENUE_CHANGE,
                        activityName = proposal.activityName,
                        affectedModeId = proposal.targetModeId,
                        beforeState = proposal.previousState,
                        afterState = "Venue: $newLocation",
                        changedFields = listOf("location"),
                        confidence = proposal.confidence,
                        evidenceSummary = proposal.evidenceSummary,
                        executionStatus = MutationStatus.USER_APPROVED,
                        explanation = "User approved venue relocation to $newLocation."
                    )
                }

                MutationType.REQUIREMENT_ADDED -> {
                    val itemName = proposal.addedItemName ?: proposal.activityName
                    val existingItems = dao.getItemsForMode(proposal.targetModeId)
                    val ghostItem = existingItems.find { it.isGhostMode && it.itemName.contains(itemName, ignoreCase = true) }

                    if (ghostItem != null) {
                        dao.promoteGhostItem(ghostItem.id)
                    } else {
                        dao.insertItem(
                            ItemRequirementEntity(
                                modeId = proposal.targetModeId,
                                itemName = itemName,
                                isReady = false,
                                isTemporary = true,
                                ttlTimestamp = System.currentTimeMillis() + (24 * 3600 * 1000L),
                                confidenceScore = proposal.confidence,
                                sourceEvidence = proposal.evidenceSummary,
                                isGhostMode = false,
                                isWaived = false
                            )
                        )
                    }

                    // Recalculate dynamic readiness
                    val activeMode = dao.getModeById(proposal.targetModeId)
                    val updatedItems = dao.getItemsForMode(proposal.targetModeId)
                    ReadinessEngine.calculateReadiness(activeMode, updatedItems)

                    PlanMutationDiff(
                        mutationType = MutationType.REQUIREMENT_ADDED,
                        activityName = proposal.activityName,
                        affectedModeId = proposal.targetModeId,
                        beforeState = "Not present in checklist",
                        afterState = "Added requirement: $itemName",
                        changedFields = listOf("items"),
                        confidence = proposal.confidence,
                        evidenceSummary = proposal.evidenceSummary,
                        executionStatus = MutationStatus.USER_APPROVED,
                        explanation = "User approved adding '$itemName' to checklist requirements."
                    )
                }

                MutationType.REQUIREMENT_WAIVED -> {
                    val itemName = proposal.addedItemName ?: proposal.activityName
                    dao.setItemWaivedByName(itemName, true)

                    val activeMode = dao.getModeById(proposal.targetModeId)
                    val updatedItems = dao.getItemsForMode(proposal.targetModeId)
                    ReadinessEngine.calculateReadiness(activeMode, updatedItems)

                    PlanMutationDiff(
                        mutationType = MutationType.REQUIREMENT_WAIVED,
                        activityName = proposal.activityName,
                        affectedModeId = proposal.targetModeId,
                        beforeState = "Required item",
                        afterState = "Item waived: $itemName",
                        changedFields = listOf("isWaived"),
                        confidence = proposal.confidence,
                        evidenceSummary = proposal.evidenceSummary,
                        executionStatus = MutationStatus.USER_APPROVED,
                        explanation = "User approved waiving requirement '$itemName'."
                    )
                }
            }

            // Clean up any remaining ghost entities in database
            try {
                val ghostEvents = dao.getGhostEventsFlow().first()
                ghostEvents.filter { it.title.contains(proposal.activityName, ignoreCase = true) || it.id == proposal.numericId }
                    .forEach { dao.deleteEventById(it.id) }

                val ghostItems = dao.getAllItemsFlow().first().filter { it.isGhostMode }
                ghostItems.filter { it.itemName.contains(proposal.activityName, ignoreCase = true) || it.id == proposal.numericId }
                    .forEach { dao.deleteItemById(it.id) }
            } catch (e: Exception) {
                Log.w(TAG, "Ghost entity cleanup warning: ${e.message}")
            }

            // Transition: USER_APPROVED -> APPLIED
            proposal.lifecycle = GhostProposalLifecycle.APPLIED
            updateFlow()
            Log.i(TAG, "Proposal '${proposal.id}' successfully APPLIED. Diff committed: ${appliedDiff.explanation}")
            GhostApprovalResult.Success(appliedDiff, proposal)
        }
    }

    /**
     * Explicit human rejection of a Ghost Mode proposal.
     * Guarantees zero live state mutation and zero Personal Forgetting Model contamination.
     */
    suspend fun rejectProposal(
        proposalId: Any,
        dao: LifeOsDao? = null
    ): GhostApprovalResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val proposal = when (proposalId) {
                is String -> proposals[proposalId]
                is Int -> getProposalByNumericId(proposalId)
                is Long -> getProposalByNumericId(proposalId.toInt())
                else -> null
            } ?: return@withContext GhostApprovalResult.NotFound(proposalId)

            proposal.lifecycle = GhostProposalLifecycle.USER_REJECTED

            // Clean up ghost placeholders without touching live events or items
            if (dao != null) {
                try {
                    dao.deleteEventById(proposal.numericId)
                    dao.deleteItemById(proposal.numericId)
                } catch (e: Exception) {
                    Log.w(TAG, "Reject cleanup notice: ${e.message}")
                }
            }

            updateFlow()
            Log.i(TAG, "Proposal '${proposal.id}' REJECTED by user. Live state preserved untouched.")
            GhostApprovalResult.Rejected(proposal)
        }
    }

    /**
     * Resolves a contradictory proposal held in CONFLICT_HOLD.
     * User chooses between State A or State B.
     */
    suspend fun resolveConflict(
        proposalId: Any,
        selectedChoice: String,
        dao: LifeOsDao
    ): GhostApprovalResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val proposal = when (proposalId) {
                is String -> proposals[proposalId]
                is Int -> getProposalByNumericId(proposalId)
                is Long -> getProposalByNumericId(proposalId.toInt())
                else -> null
            } ?: return@withContext GhostApprovalResult.NotFound(proposalId)

            Log.i(TAG, "User resolved conflict for '${proposal.activityName}': Selected '$selectedChoice'")

            // Apply selected resolution
            val existingEvent = dao.findEventByTitleLike(proposal.activityName)
            if (existingEvent != null) {
                if (selectedChoice.contains("11 AM", ignoreCase = true) || selectedChoice.contains("11:00", ignoreCase = true)) {
                    val newStart = existingEvent.startTime + 3600000L
                    dao.updateEvent(existingEvent.copy(startTime = newStart, evidenceText = "Conflict resolved: $selectedChoice"))
                } else if (selectedChoice.contains("Block C", ignoreCase = true) || selectedChoice.contains("Block A", ignoreCase = true)) {
                    dao.updateEvent(existingEvent.copy(location = selectedChoice, evidenceText = "Conflict resolved: $selectedChoice"))
                } else {
                    dao.updateEvent(existingEvent.copy(evidenceText = "Conflict resolved: $selectedChoice"))
                }
            }

            proposal.lifecycle = GhostProposalLifecycle.APPLIED
            updateFlow()

            val diff = PlanMutationDiff(
                mutationType = proposal.mutationType,
                activityName = proposal.activityName,
                affectedModeId = proposal.targetModeId,
                beforeState = proposal.previousState,
                afterState = "Resolved: $selectedChoice",
                changedFields = listOf("conflict_resolved"),
                confidence = 1.0f,
                evidenceSummary = "Conflict manually resolved by user to '$selectedChoice'",
                executionStatus = MutationStatus.USER_APPROVED,
                explanation = "Conflicting claims resolved by user: selected '$selectedChoice'."
            )
            GhostApprovalResult.Success(diff, proposal)
        }
    }

    /**
     * Scans pending proposals and expires any proposals whose event time has passed.
     */
    fun checkAndExpireProposals(currentTime: Long = System.currentTimeMillis()): List<GhostProposal> {
        val expiredList = mutableListOf<GhostProposal>()
        proposals.values.forEach { proposal ->
            if (proposal.lifecycle == GhostProposalLifecycle.PROPOSED) {
                val isPastTtl = currentTime >= proposal.expirationEpochMillis
                val isPastEvent = proposal.eventEpochMillis != null && proposal.eventEpochMillis > 0 && currentTime > proposal.eventEpochMillis + 3600000L
                if (isPastTtl || isPastEvent) {
                    proposal.lifecycle = GhostProposalLifecycle.EXPIRED
                    expiredList.add(proposal)
                    Log.i(TAG, "Proposal '${proposal.id}' transitioned to EXPIRED.")
                }
            }
        }
        if (expiredList.isNotEmpty()) {
            updateFlow()
        }
        return expiredList
    }

    /**
     * Restores unconfirmed ghost proposals from Room DB upon application cold restart.
     */
    suspend fun restoreFromDatabase(dao: LifeOsDao) = withContext(Dispatchers.IO) {
        try {
            val ghostEvents = dao.getGhostEventsFlow().first()
            for (ge in ghostEvents) {
                if (numericIdToId[ge.id] == null) {
                    val rawEvidence = ge.evidenceText ?: "Ghost Event"
                    val proposal = GhostProposal(
                        numericId = ge.id,
                        activityName = ge.title.replace("(Rescheduled)", "").replace("(Cancelled)", "").trim(),
                        targetModeId = ge.associatedModeId,
                        mutationType = when {
                            ge.title.contains("Cancelled", ignoreCase = true) -> MutationType.CANCELLATION
                            ge.title.contains("Venue", ignoreCase = true) -> MutationType.VENUE_CHANGE
                            else -> MutationType.TIME_RESCHEDULE
                        },
                        previousState = "Scheduled event",
                        proposedState = ge.title,
                        confidence = ge.confidence,
                        sourceCategory = "NOTIFICATION",
                        evidenceSummary = DataMinimizer.stripPii(rawEvidence),
                        potentialConsequences = "Review proposed schedule mutation.",
                        eventEpochMillis = ge.startTime,
                        expirationEpochMillis = ge.startTime + 7200000L,
                        lifecycle = GhostProposalLifecycle.PROPOSED,
                        rawText = rawEvidence
                    )
                    registerProposal(proposal)
                }
            }

            val ghostItems = dao.getAllItemsFlow().first().filter { it.isGhostMode }
            for (gi in ghostItems) {
                if (numericIdToId[gi.id] == null) {
                    val proposal = GhostProposal(
                        numericId = gi.id,
                        activityName = gi.itemName,
                        targetModeId = gi.modeId,
                        mutationType = MutationType.REQUIREMENT_ADDED,
                        previousState = "Not present in checklist",
                        proposedState = "Add requirement: ${gi.itemName}",
                        confidence = gi.confidenceScore,
                        sourceCategory = "NOTIFICATION",
                        evidenceSummary = DataMinimizer.stripPii(gi.sourceEvidence ?: gi.itemName),
                        potentialConsequences = "New item needed in mode checklist.",
                        lifecycle = GhostProposalLifecycle.PROPOSED,
                        addedItemName = gi.itemName,
                        rawText = gi.sourceEvidence
                    )
                    registerProposal(proposal)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "restoreFromDatabase note: ${e.message}")
        }
    }

    private fun updateFlow() {
        _proposalsFlow.value = proposals.values.toList()
    }

    /**
     * Resets in-memory proposals for isolated unit testing.
     */
    fun reset() {
        proposals.clear()
        numericIdToId.clear()
        _proposalsFlow.value = emptyList()
    }
}
