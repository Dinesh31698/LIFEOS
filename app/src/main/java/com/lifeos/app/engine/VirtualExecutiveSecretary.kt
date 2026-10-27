package com.lifeos.app.engine

import com.lifeos.app.data.EventEntity
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeOsDao

enum class BriefingMode {
    BRIEF,
    DETAILED,
    SILENT
}

data class PreEventBriefing(
    val eventTitle: String,
    val timeLabel: String,
    val location: String,
    val minutesUntilStart: Int,
    val missingItems: List<String>,
    val readinessScorePercent: Int,
    val departureAlert: String,
    val spokenSummary: String
)

data class ConflictProposal(
    val id: String,
    val eventA: String,
    val eventB: String,
    val currentSchedule: String = "10:00 AM - 11:30 AM",
    val proposedSchedule: String = "06:00 PM - 07:30 PM",
    val affectedItems: List<String> = listOf("Shaker Bottle", "Gym Clothes & Shoes"),
    val affectedReminders: List<String> = listOf("Departure reminder shifted to 05:30 PM"),
    val proposedAction: String,
    val reason: String,
    val confidence: Float = 0.95f,
    val requiresApproval: Boolean = true,
    var isApproved: Boolean = false,
    var isRejected: Boolean = false
)

data class DailyExecutiveSummary(
    val morningBriefingText: String,
    val eveningRecapText: String,
    val upcomingEventsCount: Int,
    val pendingRequirementsCount: Int,
    val detectedConflicts: List<ConflictProposal>
)

object VirtualExecutiveSecretary {

    private val _storedProposals = mutableMapOf<String, ConflictProposal>()

    fun getStoredProposals(): List<ConflictProposal> = _storedProposals.values.toList()

    fun approveConflictProposal(proposalId: String): Boolean {
        val proposal = _storedProposals[proposalId] ?: return false
        if (proposal.isApproved) {
            // Idempotent guarantee
            return true
        }
        proposal.isApproved = true
        proposal.isRejected = false
        return true
    }

    fun rejectConflictProposal(proposalId: String): Boolean {
        val proposal = _storedProposals[proposalId] ?: return false
        proposal.isApproved = false
        proposal.isRejected = true
        return true
    }

    /**
     * Generates a pre-event briefing card and spoken summary (typically 10 min prior to event).
     */
    fun generatePreEventBriefing(
        event: EventEntity,
        checklistItems: List<ItemRequirementEntity>,
        mode: BriefingMode = BriefingMode.BRIEF,
        leadMinutes: Int = 10
    ): PreEventBriefing {
        val missing = checklistItems.filter { !it.isReady && !it.isWaived }.map { it.itemName }
        val readyCount = checklistItems.count { it.isReady || it.isWaived }
        val totalCount = checklistItems.size
        val readinessScore = if (totalCount > 0) ((readyCount.toDouble() / totalCount.toDouble()) * 100).toInt() else 100

        val locationText = event.location ?: "Campus Location"
        val departureAlert = if (missing.isNotEmpty()) {
            "Action required: ${missing.first()} is not packed. Recommended departure in 5 minutes."
        } else {
            "All items verified. Recommended departure in 5 minutes."
        }

        val spokenSummary = when (mode) {
            BriefingMode.SILENT -> ""
            BriefingMode.BRIEF -> "${event.title} begins in $leadMinutes minutes at $locationText. ${if (missing.isNotEmpty()) "${missing.size} checklist items still needed." else "You are 100% ready."}"
            BriefingMode.DETAILED -> "${event.title} is scheduled at $locationText in $leadMinutes minutes. Current readiness is $readinessScore%. Missing items: ${missing.joinToString(", ")}. Recommended departure in 5 minutes."
        }

        return PreEventBriefing(
            eventTitle = event.title,
            timeLabel = "In $leadMinutes minutes",
            location = locationText,
            minutesUntilStart = leadMinutes,
            missingItems = missing,
            readinessScorePercent = readinessScore,
            departureAlert = departureAlert,
            spokenSummary = spokenSummary
        )
    }

    /**
     * Evaluates calendar schedule for overlapping commitments and proposes priority resolution.
     */
    fun detectAndResolveConflicts(events: List<EventEntity>): List<ConflictProposal> {
        val proposals = mutableListOf<ConflictProposal>()
        if (events.size < 2) return proposals

        for (i in 0 until events.size - 1) {
            for (j in i + 1 until events.size) {
                val e1 = events[i]
                val e2 = events[j]
                val overlaps = (e1.startTime < e2.endTime && e2.startTime < e1.endTime) ||
                        (e1.startTime == e2.startTime && e1.startTime > 0)

                if (overlaps) {
                    // DBMS Lab / Class takes priority over Gym
                    val isE1Academic = e1.title.contains("Lab", ignoreCase = true) || e1.title.contains("Class", ignoreCase = true)
                    val (higher, lower) = if (isE1Academic) e1 to e2 else e2 to e1

                    val prop = ConflictProposal(
                        id = "conflict_${e1.id}_${e2.id}",
                        eventA = higher.title,
                        eventB = lower.title,
                        currentSchedule = "10:00 AM - 11:30 AM",
                        proposedSchedule = "06:00 PM - 07:30 PM",
                        affectedItems = listOf("Shaker Bottle", "Gym Clothes & Shoes"),
                        affectedReminders = listOf("Departure reminder shifted to 05:30 PM"),
                        proposedAction = "Reschedule '${lower.title}' to 06:00 PM after academic hours",
                        reason = "Priority arbitration: ${higher.title} (Academic) prioritized over ${lower.title}",
                        confidence = 0.95f,
                        requiresApproval = true
                    )
                    proposals.add(prop)
                    _storedProposals[prop.id] = prop
                }
            }
        }
        return proposals
    }

    /**
     * Generates Morning Briefing (08:00) and Evening Recap (21:30) summaries.
     */
    suspend fun generateDailySummary(
        dao: LifeOsDao,
        userName: String = "User"
    ): DailyExecutiveSummary {
        val events = dao.findEventByTitleLike("") // sample query
        val items = dao.getItemsForMode(1)
        val missingCount = items.count { !it.isReady && !it.isWaived }

        val morningText = "Good morning, $userName. You have ${if (events != null) "1 scheduled session ('${events.title}')" else "no morning classes"} today. $missingCount items remain on your checklist."
        val eveningText = "Good evening, $userName. Today's academic and fitness tasks are recorded. Tomorrow's preparation buffer is 15 minutes."

        return DailyExecutiveSummary(
            morningBriefingText = morningText,
            eveningRecapText = eveningText,
            upcomingEventsCount = if (events != null) 1 else 0,
            pendingRequirementsCount = missingCount,
            detectedConflicts = emptyList()
        )
    }
}
