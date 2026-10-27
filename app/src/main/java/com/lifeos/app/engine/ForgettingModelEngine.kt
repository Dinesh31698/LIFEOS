package com.lifeos.app.engine

import android.content.Context
import android.util.Log
import com.lifeos.app.data.ItemHabitEntity
import com.lifeos.app.data.LifeOsDao
import com.lifeos.app.data.LifeOsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Legacy binary risk level for backward compatibility with Modules 3-7.
 */
enum class RiskLevel {
    NORMAL,
    HIGH_RISK
}

/**
 * Phase 8A: Multi-tier personalized risk levels.
 */
enum class PersonalizedRiskLevel {
    UNKNOWN, // Cold start (< 3 meaningful observations)
    LOW,     // Regularly prepared, minimal forgetting
    MEDIUM,  // Moderately or occasionally forgotten
    HIGH     // Frequently forgotten or recent consecutive miss streak
}

data class HabitEscalationInfo(
    val itemName: String,
    val forgetCount: Int,
    val confirmCount: Int,
    val isEscalated: Boolean,
    val escalationWarning: String?,
    val score: Float = 0.0f,
    val riskLevel: RiskLevel = RiskLevel.NORMAL,
    val priorityBoost: Float = 0.0f
)

/**
 * Detailed personalized evaluation record containing explainable context.
 */
data class PersonalizedHabitEvaluation(
    val itemName: String,
    val modeId: Int,
    val riskLevel: PersonalizedRiskLevel,
    val compositeRiskScore: Float,
    val totalOpportunityCount: Int,
    val forgetCount: Int,
    val confirmCount: Int,
    val consecutiveSuccessCount: Int,
    val consecutiveForgetCount: Int,
    val explanation: String,
    val isColdStart: Boolean
)

object ForgettingModelEngine {

    private const val TAG = "LIFEOS_FORGETTING"
    private const val TAG_MODULE3 = "LIFEOS_MODULE3"
    const val ESCALATION_THRESHOLD = 3
    const val MIN_OBSERVATIONS_THRESHOLD = 3

    /**
     * Determines whether an item habit has breached the frequent forgetting threshold (>= 3).
     */
    fun isFrequentlyForgotten(habit: ItemHabitEntity?): Boolean {
        if (habit == null) return false
        return habit.forgetCount >= ESCALATION_THRESHOLD
    }

    /**
     * Legacy Dynamic Risk Classification based on forgetCount:
     * - NORMAL: forgetCount < 3
     * - HIGH_RISK: forgetCount >= 3
     */
    fun getRiskLevel(forgetCount: Int): RiskLevel {
        return if (forgetCount >= ESCALATION_THRESHOLD) RiskLevel.HIGH_RISK else RiskLevel.NORMAL
    }

    /**
     * Habit Math Formula:
     * Forgetfulness Score = (1.5 * forgetCount) - (0.3 * confirmCount)
     * Floored at 0.0f.
     */
    fun calculateForgetfulnessScore(forgetCount: Int, confirmCount: Int): Float {
        val raw = (forgetCount * 1.5f) - (confirmCount * 0.3f)
        return raw.coerceAtLeast(0.0f)
    }

    /**
     * Formats an adaptive escalation warning message for high-risk forgotten items.
     */
    fun getEscalationAlertMessage(itemName: String, forgetCount: Int): String {
        return "⚠️ HIGH FORGET RISK: You have forgotten '$itemName' $forgetCount times. Pack it now before leaving!"
    }

    /**
     * Standardized diagnostic logger for Module 3 & 4.
     */
    fun logHabitDiagnostics(itemName: String, forgetCount: Int, confirmCount: Int, priorityBoost: Float) {
        val score = calculateForgetfulnessScore(forgetCount, confirmCount)
        val riskLevel = getRiskLevel(forgetCount)
        Log.d(TAG_MODULE3, "Habit Evaluated for '$itemName' -> ForgetCount: $forgetCount, ConfirmCount: $confirmCount")
        Log.d(TAG_MODULE3, "Forgetfulness Score -> $score | Risk Level -> $riskLevel")
        Log.d(TAG_MODULE3, "Priority Boost Applied -> $priorityBoost")
        Log.i("LIFEOS_MODULE4", "LIFEOS_MODULE4: Habit Evaluated -> Item '$itemName' | ForgetCount: $forgetCount | ConfirmCount: $confirmCount | Score: $score | Risk: $riskLevel | Boost: $priorityBoost")
        if (riskLevel == RiskLevel.HIGH_RISK) {
            Log.i("LIFEOS_MODULE4", "LIFEOS_MODULE4: Risk Escalated -> '$itemName' is HIGH_RISK (Score: $score >= 4.5)")
        }
    }

    // =========================================================================
    // Phase 8A: Advanced Deterministic Personalized Mathematical Model
    // =========================================================================

    /**
     * Computes the normalized continuous composite risk score [0.0, 1.0].
     * Takes into account:
     * 1. Base Forget Rate: forgetCount / (forgetCount + confirmCount)
     * 2. Consecutive streaks: consecutive forgets boost score; consecutive successes decay score.
     * 3. Recency: recent confirmed events weight more than older events.
     */
    fun calculateCompositeRiskScore(habit: ItemHabitEntity?): Float {
        if (habit == null) return 0.0f
        val f = habit.forgetCount
        val c = habit.confirmCount
        if (f + c == 0) return 0.0f

        val baseRate = f.toFloat() / (f + c).toFloat()

        // Streak modifier
        val streakMod = when {
            habit.consecutiveForgetCount > 0 -> 0.15f * min(habit.consecutiveForgetCount, 3)
            habit.consecutiveSuccessCount > 0 -> -0.10f * min(habit.consecutiveSuccessCount, 4)
            else -> 0.0f
        }

        // Recency modifier: recent forget increases risk, recent consecutive successes decay risk
        val recencyMod = when {
            habit.lastForgottenTimestamp > habit.lastPreparedTimestamp && habit.lastForgottenTimestamp > 0L -> 0.10f
            habit.lastPreparedTimestamp > habit.lastForgottenTimestamp && habit.consecutiveSuccessCount >= 3 -> -0.15f
            else -> 0.0f
        }

        return (baseRate + streakMod + recencyMod).coerceIn(0.0f, 1.0f)
    }

    /**
     * Phase 8A Multi-tier Personalized Risk Evaluation with cold-start gating and explainability.
     */
    fun evaluatePersonalizedRisk(
        habit: ItemHabitEntity?,
        itemName: String? = null,
        modeId: Int? = null
    ): PersonalizedHabitEvaluation {
        val targetName = habit?.itemName ?: itemName ?: "Unknown Item"
        val targetMode = habit?.modeId ?: modeId ?: 0

        if (habit == null) {
            return PersonalizedHabitEvaluation(
                itemName = targetName,
                modeId = targetMode,
                riskLevel = PersonalizedRiskLevel.UNKNOWN,
                compositeRiskScore = 0.0f,
                totalOpportunityCount = 0,
                forgetCount = 0,
                confirmCount = 0,
                consecutiveSuccessCount = 0,
                consecutiveForgetCount = 0,
                explanation = "Not enough history yet.",
                isColdStart = true
            )
        }

        val totalOpportunities = maxOf(habit.totalOpportunityCount, habit.forgetCount + habit.confirmCount)
        val score = calculateCompositeRiskScore(habit)

        // Cold Start Gate: at least MIN_OBSERVATIONS_THRESHOLD (3) observations required
        if (totalOpportunities < MIN_OBSERVATIONS_THRESHOLD) {
            return PersonalizedHabitEvaluation(
                itemName = targetName,
                modeId = targetMode,
                riskLevel = PersonalizedRiskLevel.UNKNOWN,
                compositeRiskScore = score,
                totalOpportunityCount = totalOpportunities,
                forgetCount = habit.forgetCount,
                confirmCount = habit.confirmCount,
                consecutiveSuccessCount = habit.consecutiveSuccessCount,
                consecutiveForgetCount = habit.consecutiveForgetCount,
                explanation = "Not enough history yet.",
                isColdStart = true
            )
        }

        // Deterministic Multi-tier Classification
        val riskLevel = when {
            score >= 0.60f || (habit.forgetCount >= 3 && habit.consecutiveForgetCount >= 2) -> PersonalizedRiskLevel.HIGH
            score >= 0.30f -> PersonalizedRiskLevel.MEDIUM
            else -> PersonalizedRiskLevel.LOW
        }

        // Explainability Generation
        val explanation = when (riskLevel) {
            PersonalizedRiskLevel.HIGH -> {
                if (habit.consecutiveForgetCount >= 2) {
                    "Risk is increasing because you've recently missed this item several times."
                } else {
                    "Frequently forgotten based on recent history."
                }
            }
            PersonalizedRiskLevel.MEDIUM -> "Sometimes forgotten."
            PersonalizedRiskLevel.LOW -> {
                if (habit.consecutiveSuccessCount >= 2 && habit.forgetCount > 0) {
                    "Risk is decreasing because you've been preparing this consistently."
                } else {
                    "Usually prepared."
                }
            }
            PersonalizedRiskLevel.UNKNOWN -> "Not enough history yet."
        }

        return PersonalizedHabitEvaluation(
            itemName = targetName,
            modeId = targetMode,
            riskLevel = riskLevel,
            compositeRiskScore = score,
            totalOpportunityCount = totalOpportunities,
            forgetCount = habit.forgetCount,
            confirmCount = habit.confirmCount,
            consecutiveSuccessCount = habit.consecutiveSuccessCount,
            consecutiveForgetCount = habit.consecutiveForgetCount,
            explanation = explanation,
            isColdStart = false
        )
    }

    fun getPersonalizedRiskExplanation(habit: ItemHabitEntity?): String {
        return evaluatePersonalizedRisk(habit).explanation
    }

    // =========================================================================
    // Habit Recording Lifecycle Methods
    // =========================================================================

    /**
     * Phase 8A: Explicit User-Confirmed Forgetting Event.
     * Only explicit, verified user confirmations increment confirmed forgetting history.
     */
    suspend fun recordConfirmedForgetting(
        dao: LifeOsDao,
        itemName: String,
        modeId: Int,
        timestamp: Long = System.currentTimeMillis()
    ): ItemHabitEntity = withContext(Dispatchers.IO) {
        val existing = dao.getHabit(itemName, modeId)
        val habit = if (existing != null) {
            val newForgetCount = existing.forgetCount + 1
            val riskLevel = getRiskLevel(newForgetCount)
            val boostDelta = if (riskLevel == RiskLevel.HIGH_RISK) 0.75f else 0.25f
            val newPriorityBoost = existing.priorityBoost + boostDelta
            val updated = existing.copy(
                forgetCount = newForgetCount,
                priorityBoost = newPriorityBoost,
                totalOpportunityCount = existing.totalOpportunityCount + 1,
                lastForgottenTimestamp = timestamp,
                consecutiveForgetCount = existing.consecutiveForgetCount + 1,
                consecutiveSuccessCount = 0
            )
            dao.updateHabit(updated)
            logHabitDiagnostics(itemName, updated.forgetCount, updated.confirmCount, updated.priorityBoost)
            Log.d(TAG, "Confirmed forget event for '$itemName' (mode=$modeId): forgetCount=${updated.forgetCount}")
            updated
        } else {
            val newHabit = ItemHabitEntity(
                itemName = itemName,
                modeId = modeId,
                forgetCount = 1,
                confirmCount = 0,
                priorityBoost = 0.25f,
                totalOpportunityCount = 1,
                lastForgottenTimestamp = timestamp,
                consecutiveForgetCount = 1,
                consecutiveSuccessCount = 0
            )
            val newId = dao.insertOrUpdateHabit(newHabit)
            val saved = newHabit.copy(id = newId.toInt())
            logHabitDiagnostics(itemName, saved.forgetCount, saved.confirmCount, saved.priorityBoost)
            Log.d(TAG, "Created initial confirmed forget habit for '$itemName' (id=$newId)")
            saved
        }
        habit
    }

    suspend fun recordConfirmedForgetting(context: Context, itemName: String, modeId: Int): ItemHabitEntity {
        val db = LifeOsDatabase.getDatabase(context.applicationContext)
        return recordConfirmedForgetting(db.lifeOsDao(), itemName, modeId)
    }

    /**
     * Backward-compatible alias for recordConfirmedForgetting.
     */
    suspend fun recordItemForgotten(
        dao: LifeOsDao,
        itemName: String,
        modeId: Int
    ): ItemHabitEntity = recordConfirmedForgetting(dao, itemName, modeId)

    suspend fun recordItemForgotten(context: Context, itemName: String, modeId: Int): ItemHabitEntity {
        return recordConfirmedForgetting(context, itemName, modeId)
    }

    /**
     * Records when an item was successfully packed/verified by the user.
     * Increments confirmCount, totalOpportunityCount, consecutiveSuccessCount, and resets consecutiveForgetCount.
     * De-escalates priority boost (-0.10f).
     */
    suspend fun recordItemConfirmed(
        dao: LifeOsDao,
        itemName: String,
        modeId: Int,
        timestamp: Long = System.currentTimeMillis()
    ): ItemHabitEntity = withContext(Dispatchers.IO) {
        val existing = dao.getHabit(itemName, modeId)
        val habit = if (existing != null) {
            val newConfirmCount = existing.confirmCount + 1
            val newPriorityBoost = (existing.priorityBoost - 0.10f).coerceAtLeast(0.0f)
            val updated = existing.copy(
                confirmCount = newConfirmCount,
                priorityBoost = newPriorityBoost,
                totalOpportunityCount = existing.totalOpportunityCount + 1,
                lastPreparedTimestamp = timestamp,
                consecutiveSuccessCount = existing.consecutiveSuccessCount + 1,
                consecutiveForgetCount = 0
            )
            dao.updateHabit(updated)
            logHabitDiagnostics(itemName, updated.forgetCount, updated.confirmCount, updated.priorityBoost)
            Log.d(TAG, "Recorded confirmation for '$itemName': confirmCount=${updated.confirmCount} (boost: ${updated.priorityBoost})")
            updated
        } else {
            val newHabit = ItemHabitEntity(
                itemName = itemName,
                modeId = modeId,
                confirmCount = 1,
                priorityBoost = 0.0f,
                totalOpportunityCount = 1,
                lastPreparedTimestamp = timestamp,
                consecutiveSuccessCount = 1,
                consecutiveForgetCount = 0
            )
            val newId = dao.insertOrUpdateHabit(newHabit)
            val saved = newHabit.copy(id = newId.toInt())
            logHabitDiagnostics(itemName, saved.forgetCount, saved.confirmCount, saved.priorityBoost)
            saved
        }
        habit
    }

    suspend fun recordItemConfirmed(context: Context, itemName: String, modeId: Int): ItemHabitEntity {
        val db = LifeOsDatabase.getDatabase(context.applicationContext)
        return recordItemConfirmed(db.lifeOsDao(), itemName, modeId)
    }

    /**
     * Phase 8A: Handles geofence departure.
     * CRITICAL FIX: Unchecked items at departure represent "unconfirmed preparation" and
     * MUST NOT automatically increment confirmed forgetting counters without explicit confirmation.
     *
     * @param recordAsConfirmed If false (default), returns unchecked items as unconfirmed departure alerts
     *                          WITHOUT incrementing confirmed forgetCount in Room database.
     *                          If true (legacy path), records confirmed forgetting.
     */
    suspend fun handleGeofenceDeparture(
        dao: LifeOsDao,
        departingModeId: Int,
        recordAsConfirmed: Boolean = false
    ): List<ItemHabitEntity> = withContext(Dispatchers.IO) {
        val items = dao.getItemsForMode(departingModeId)
        val uncheckedItems = items.filter { !it.isReady && !it.isWaived && !it.isGhostMode }
        Log.d(TAG, "Handling geofence departure for mode $departingModeId: ${uncheckedItems.size} unchecked items found (recordAsConfirmed=$recordAsConfirmed)")

        val updatedHabits = mutableListOf<ItemHabitEntity>()
        for (item in uncheckedItems) {
            if (recordAsConfirmed) {
                val updated = recordConfirmedForgetting(dao, item.itemName, departingModeId)
                updatedHabits.add(updated)
            } else {
                // Return unconfirmed warning habit without altering database forget history
                val existing = dao.getHabit(item.itemName, departingModeId)
                val alertHabit = existing ?: ItemHabitEntity(
                    itemName = item.itemName,
                    modeId = departingModeId,
                    forgetCount = 0,
                    confirmCount = 0,
                    priorityBoost = 0.0f
                )
                updatedHabits.add(alertHabit)
            }
        }
        updatedHabits
    }

    suspend fun handleGeofenceDeparture(context: Context, departingModeId: Int): List<ItemHabitEntity> {
        val db = LifeOsDatabase.getDatabase(context.applicationContext)
        return handleGeofenceDeparture(db.lifeOsDao(), departingModeId, recordAsConfirmed = false)
    }

    /**
     * Records when an item was waived by the consequence engine.
     */
    suspend fun recordItemWaived(
        dao: LifeOsDao,
        itemName: String,
        modeId: Int
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getHabit(itemName, modeId)
        if (existing != null) {
            dao.incrementWaiveCountById(existing.id)
            Log.d(TAG, "Recorded waive event for '$itemName'")
        }
    }

    /**
     * Cleanups on item or mode deletion to prevent orphaned habit records.
     */
    suspend fun deleteHabitForItem(dao: LifeOsDao, itemName: String, modeId: Int): Int = withContext(Dispatchers.IO) {
        dao.deleteHabit(itemName, modeId)
    }

    suspend fun deleteHabitsForMode(dao: LifeOsDao, modeId: Int) = withContext(Dispatchers.IO) {
        dao.deleteHabitsForMode(modeId)
    }

    /**
     * Evaluates an individual item habit against the legacy forgetting model.
     */
    fun evaluateItemHabit(
        itemName: String,
        habit: ItemHabitEntity?
    ): HabitEscalationInfo {
        val forgetCount = habit?.forgetCount ?: 0
        val confirmCount = habit?.confirmCount ?: 0
        val score = calculateForgetfulnessScore(forgetCount, confirmCount)
        val riskLevel = getRiskLevel(forgetCount)
        val priorityBoost = habit?.priorityBoost ?: 0.0f
        val isEscalated = riskLevel == RiskLevel.HIGH_RISK

        logHabitDiagnostics(itemName, forgetCount, confirmCount, priorityBoost)

        return HabitEscalationInfo(
            itemName = itemName,
            forgetCount = forgetCount,
            confirmCount = confirmCount,
            isEscalated = isEscalated,
            escalationWarning = if (isEscalated) getEscalationAlertMessage(itemName, forgetCount) else null,
            score = score,
            riskLevel = riskLevel,
            priorityBoost = priorityBoost
        )
    }

    /**
     * Evaluates a collection of items against habits to locate escalated, high-risk items.
     */
    fun evaluateEscalatedItems(
        itemNames: List<String>,
        habits: List<ItemHabitEntity>
    ): List<HabitEscalationInfo> {
        val habitMap = habits.associateBy { it.itemName.lowercase() }
        return itemNames.map { name ->
            val habit = habitMap[name.lowercase()]
            evaluateItemHabit(name, habit)
        }
    }
}
