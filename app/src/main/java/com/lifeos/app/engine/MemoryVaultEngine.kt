package com.lifeos.app.engine

import com.lifeos.app.data.LifeOsDao
import com.lifeos.app.data.MemoryEntity
import com.lifeos.app.security.DataMinimizer
import kotlinx.coroutines.flow.Flow

enum class MemoryTtlOption(val displayName: String, val durationMillis: Long) {
    ONE_HOUR("1 Hour", 3600_000L),
    SIX_HOURS("6 Hours", 21600_000L),
    TWENTY_FOUR_HOURS("24 Hours", 86400_000L),
    THREE_DAYS("3 Days", 259200_000L),
    SEVEN_DAYS("7 Days", 604800_000L),
    PERMANENT("Permanent / Pinned", 0L)
}

object MemoryVaultEngine {

    fun getAllMemoriesFlow(dao: LifeOsDao): Flow<List<MemoryEntity>> {
        return dao.getAllMemoriesFlow()
    }

    /**
     * Explicit Storage Rule: Only save when the user explicitly requests to remember something.
     * Absolutely zero silent eavesdropping or ambient recording.
     */
    suspend fun storeMemory(
        dao: LifeOsDao,
        content: String,
        memoryType: String = "SHORT_TERM",
        tags: String = "",
        ttlOption: MemoryTtlOption = MemoryTtlOption.TWENTY_FOUR_HOURS
    ): Long {
        val sanitized = DataMinimizer.minimizeMemoryNote(content)
        val now = System.currentTimeMillis()
        val expiresAt = if (ttlOption == MemoryTtlOption.PERMANENT) 0L else now + ttlOption.durationMillis
        val isPinned = ttlOption == MemoryTtlOption.PERMANENT || memoryType == "PINNED"

        val entity = MemoryEntity(
            content = sanitized,
            memoryType = if (isPinned) "PINNED" else memoryType,
            tags = tags,
            createdAt = now,
            expiresAt = expiresAt,
            isPinned = isPinned
        )
        return dao.insertMemory(entity)
    }

    suspend fun deleteMemory(dao: LifeOsDao, id: Int) {
        dao.deleteMemoryById(id)
    }

    suspend fun cleanupExpiredMemories(dao: LifeOsDao): Int {
        val now = System.currentTimeMillis()
        return dao.deleteExpiredMemories(now)
    }

    /**
     * Semantic retrieval pipeline:
     * 1. Normalize query
     * 2. Structured filter
     * 3. Keyword / Token match
     * 4. Rank
     * 5. Answer strictly from actual stored memory
     *
     * Never hallucinate or invent a memory.
     */
    suspend fun askLifeOsMemory(dao: LifeOsDao, query: String): Pair<Boolean, String> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return false to "Please provide a query to search your Memory Vault."

        // Stop words removal
        val stopWords = setOf(
            "where", "did", "i", "put", "keep", "leave", "store", "save", "place", "set",
            "my", "what", "is", "the", "when", "na", "ekkada", "petanu", "pettanu", "kaha",
            "rakha", "tell", "me", "about", "find", "search", "show", "remember"
        )
        val searchTokens = normalized
            .replace(Regex("[?,.!]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in stopWords }

        // Query Room DB across candidate tokens to maximize recall
        val candidates = mutableSetOf<MemoryEntity>()
        for (token in searchTokens) {
            if (token.length >= 2) {
                candidates.addAll(dao.searchMemories(token))
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(dao.searchMemories(normalized))
        }

        // Filter out expired memories if cleanup hasn't run yet
        val now = System.currentTimeMillis()
        val validMemories = candidates.filter { it.isPinned || it.expiresAt == 0L || it.expiresAt > now }

        if (validMemories.isEmpty()) {
            return false to "I don't have a saved memory for that."
        }

        // Rank by token overlaps
        val bestMatch = validMemories.maxByOrNull { memory ->
            val contentLower = memory.content.lowercase()
            searchTokens.count { token -> contentLower.contains(token) }
        }

        return if (bestMatch != null && searchTokens.any { bestMatch.content.lowercase().contains(it) }) {
            true to bestMatch.content
        } else {
            false to "I don't have a saved memory for that."
        }
    }
}
