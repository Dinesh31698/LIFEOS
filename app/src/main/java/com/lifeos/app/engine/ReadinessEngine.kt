package com.lifeos.app.engine

import android.util.Log
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.data.LifeOsDao
import com.lifeos.app.service.AntiSpamNotificationEngine
import kotlin.math.roundToInt

data class ReadinessReport(
    val modeName: String,
    val readyCount: Int,
    val totalCount: Int,
    val percentage: Int,
    val pendingItems: List<String>,
    val waivedCount: Int = 0,
    val summaryNotification: String
)

object ReadinessEngine {

    /**
     * Filters active hybrid checklist items:
     * - Retains permanent items (!isTemporary)
     * - Retains valid temporary items (isTemporary and ttlTimestamp > currentTime or ttlTimestamp == 0)
     * - Filters out expired temporary items (isTemporary and ttlTimestamp in 1..currentTime)
     * - Filters out ghost items (isGhostMode = true)
     */
    fun filterActiveHybridItems(
        items: List<ItemRequirementEntity>,
        currentTime: Long = System.currentTimeMillis()
    ): List<ItemRequirementEntity> {
        return items.filter { item ->
            !item.isGhostMode && (!item.isTemporary || (item.ttlTimestamp == 0L || item.ttlTimestamp > currentTime))
        }
    }

    /**
     * Queries Room DB for both permanent (isTemporary = 0) AND active temporary (isTemporary = 1) items
     * under the specified modeId, filtering out expired temporary items and ghost items.
     */
    suspend fun getHybridChecklist(
        dao: LifeOsDao,
        modeId: Int,
        currentTime: Long = System.currentTimeMillis()
    ): List<ItemRequirementEntity> {
        val allItems = dao.getItemsForMode(modeId)
        return filterActiveHybridItems(allItems, currentTime)
    }

    fun calculateReadiness(
        mode: LifeModeEntity?,
        items: List<ItemRequirementEntity>,
        currentTime: Long = System.currentTimeMillis()
    ): ReadinessReport {
        val modeName = mode?.name ?: "Current Mode"

        // Filter valid hybrid items (excludes ghost items and expired temporary items)
        val validItems = filterActiveHybridItems(items, currentTime)

        // Active verified items only (ghost items and waived items excluded from active requirement count)
        val activeItems = validItems.filter { !it.isWaived }
        val waivedItems = validItems.filter { it.isWaived }
        val readyCount = activeItems.count { it.isReady }
        val totalCount = activeItems.size
        // Return -1 when no items are configured (sentinel for "not configured")
        // so the UI can clearly distinguish "empty checklist" from "all items ready".
        // If items existed but all were waived due to schedule changes, user is 100% ready.
        val percentage = when {
            totalCount > 0 -> ((readyCount.toFloat() / totalCount.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
            waivedItems.isNotEmpty() -> 100
            else -> -1
        }

        val pendingItems = activeItems.filter { !it.isReady }.map { it.itemName }

        val waivedNotice = if (waivedItems.isNotEmpty()) " (${waivedItems.size} waived due to schedule changes)" else ""

        val summary = when {
            totalCount == 0 && waivedItems.isEmpty() -> "$modeName: No requirements configured."
            percentage == 100 -> "You're 100% ready for $modeName! All required items packed.$waivedNotice"
            pendingItems.size <= 2 -> "$modeName: $percentage% ready. Still need: ${pendingItems.joinToString(", ")}.$waivedNotice"
            else -> "$modeName: $percentage% ready. $readyCount of $totalCount items ready (${pendingItems.size} pending).$waivedNotice"
        }

        // Module 4 & Module 2: Diagnostic logging
        AntiSpamNotificationEngine.logReadinessEvaluated(modeName, percentage, readyCount, totalCount)
        Log.i("LIFEOS_MODULE2", "LIFEOS_MODULE2: Dynamic Readiness Evaluated -> Mode: '$modeName' | Score: $percentage% ($readyCount/$totalCount Ready) | Pending: [${pendingItems.joinToString(", ")}]")

        return ReadinessReport(
            modeName = modeName,
            readyCount = readyCount,
            totalCount = totalCount,
            percentage = percentage,
            pendingItems = pendingItems,
            waivedCount = waivedItems.size,
            summaryNotification = summary
        )
    }
}
