package com.lifeos.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeOsDao {

    // --- Life Modes ---
    @Query("SELECT * FROM life_modes WHERE id = :id")
    suspend fun getModeById(id: Int): LifeModeEntity?

    @Query("SELECT * FROM life_modes WHERE active = 1 LIMIT 1")
    fun getActiveModeFlow(): Flow<LifeModeEntity?>

    @Query("SELECT * FROM life_modes ORDER BY priority ASC")
    fun getAllModesFlow(): Flow<List<LifeModeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLifeMode(mode: LifeModeEntity): Long

    @Update
    suspend fun updateLifeMode(mode: LifeModeEntity)

    @Transaction
    suspend fun setActiveMode(modeId: Int) {
        resetActiveModes()
        setActiveModeById(modeId)
    }

    @Query("UPDATE life_modes SET active = 0")
    suspend fun resetActiveModes()

    @Query("UPDATE life_modes SET active = 1 WHERE id = :modeId")
    suspend fun setActiveModeById(modeId: Int)

    @Query("UPDATE life_modes SET active = 0 WHERE id = :modeId")
    suspend fun deactivateModeById(modeId: Int)

    @Transaction
    suspend fun deleteLifeMode(modeId: Int) {
        deleteItemsForMode(modeId)
        deleteEventsForMode(modeId)
        deleteHabitsForMode(modeId)
        deleteLifeModeById(modeId)
    }

    @Query("DELETE FROM item_requirements WHERE modeId = :modeId")
    suspend fun deleteItemsForMode(modeId: Int)

    @Query("DELETE FROM events WHERE associatedModeId = :modeId")
    suspend fun deleteEventsForMode(modeId: Int)

    @Query("DELETE FROM item_habits WHERE modeId = :modeId")
    suspend fun deleteHabitsForMode(modeId: Int)

    @Query("DELETE FROM life_modes WHERE id = :modeId")
    suspend fun deleteLifeModeById(modeId: Int)

    @Query("SELECT * FROM item_requirements WHERE modeId = :modeId AND LOWER(TRIM(itemName)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun findItemInModeByName(modeId: Int, name: String): ItemRequirementEntity?

    // --- Item Requirements ---
    @Query("SELECT * FROM item_requirements WHERE modeId = :modeId ORDER BY id ASC")
    fun getItemsForModeFlow(modeId: Int): Flow<List<ItemRequirementEntity>>

    @Query("SELECT * FROM item_requirements WHERE modeId = :modeId AND isGhostMode = 0 ORDER BY id ASC")
    fun getActiveItemsForModeFlow(modeId: Int): Flow<List<ItemRequirementEntity>>

    @Query("SELECT * FROM item_requirements WHERE modeId = :modeId AND isGhostMode = 1 ORDER BY id ASC")
    fun getGhostItemsForModeFlow(modeId: Int): Flow<List<ItemRequirementEntity>>

    @Query("SELECT * FROM item_requirements ORDER BY id ASC")
    fun getAllItemsFlow(): Flow<List<ItemRequirementEntity>>

    @Query("SELECT * FROM item_requirements WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): ItemRequirementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemRequirementEntity): Long

    @Update
    suspend fun updateItem(item: ItemRequirementEntity)

    @Query("UPDATE item_requirements SET isReady = :isReady WHERE id = :itemId")
    suspend fun updateItemStatus(itemId: Int, isReady: Boolean)

    @Query("UPDATE item_requirements SET isWaived = :isWaived WHERE id = :itemId")
    suspend fun setItemWaived(itemId: Int, isWaived: Boolean)

    @Query("UPDATE item_requirements SET isWaived = :isWaived WHERE LOWER(itemName) LIKE '%' || LOWER(:nameKeyword) || '%'")
    suspend fun setItemWaivedByName(nameKeyword: String, isWaived: Boolean): Int

    @Query("UPDATE item_requirements SET isWaived = 0 WHERE modeId = :modeId")
    suspend fun resetAllWaivedItemsForMode(modeId: Int): Int

    @Query("SELECT * FROM item_requirements WHERE LOWER(itemName) LIKE '%' || LOWER(:nameKeyword) || '%'")
    suspend fun getItemsByNameLike(nameKeyword: String): List<ItemRequirementEntity>

    @Query("UPDATE item_requirements SET isGhostMode = 0, isTemporary = 1 WHERE id = :itemId")
    suspend fun promoteGhostItem(itemId: Int)

    @Query("DELETE FROM item_requirements WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Int)

    @Query("DELETE FROM item_requirements WHERE isTemporary = 1 AND ttlTimestamp > 0 AND ttlTimestamp < :currentTime")
    suspend fun deleteExpiredItems(currentTime: Long): Int

    @Query("DELETE FROM item_requirements WHERE isTemporary = 1")
    suspend fun clearTemporaryItems(): Int

    @Query("SELECT * FROM item_requirements WHERE modeId = :modeId ORDER BY id ASC")
    suspend fun getItemsForMode(modeId: Int): List<ItemRequirementEntity>

    // --- Events ---
    @Query("SELECT * FROM events WHERE associatedModeId = :modeId")
    fun getEventsForModeFlow(modeId: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE isGhostMode = 1")
    fun getGhostEventsFlow(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events")
    fun getAllEventsFlow(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE LOWER(title) LIKE '%' || LOWER(:titleKeyword) || '%' LIMIT 1")
    suspend fun findEventByTitleLike(titleKeyword: String): EventEntity?

    @Query("UPDATE events SET startTime = :startTime, endTime = :endTime WHERE id = :eventId")
    suspend fun updateEventTiming(eventId: Int, startTime: Long, endTime: Long)

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity): Long

    @Query("UPDATE events SET isGhostMode = 0 WHERE id = :eventId")
    suspend fun promoteGhostEvent(eventId: Int)

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: Int)

    // --- Evidence Logs ---
    @Query("SELECT * FROM evidence_logs WHERE eventId = :eventId ORDER BY timestamp DESC")
    fun getEvidenceForEventFlow(eventId: Int): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidence_logs ORDER BY timestamp DESC LIMIT 50")
    fun getLatestEvidenceFlow(): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidence_logs WHERE id = :id LIMIT 1")
    suspend fun getEvidenceById(id: Int): EvidenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: EvidenceEntity): Long

    // --- Stage 3: Personal Life Graph Nodes ---
    @Query("SELECT * FROM graph_nodes ORDER BY id ASC")
    fun getAllNodesFlow(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM graph_nodes WHERE type = :type ORDER BY id ASC")
    fun getNodesByTypeFlow(type: String): Flow<List<NodeEntity>>

    @Query("SELECT * FROM graph_nodes WHERE id = :id LIMIT 1")
    suspend fun getNodeById(id: Int): NodeEntity?

    @Query("SELECT * FROM graph_nodes WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findNodeByName(name: String): NodeEntity?

    @Query("SELECT * FROM graph_nodes WHERE LOWER(name) LIKE '%' || LOWER(:nameKeyword) || '%' LIMIT 1")
    suspend fun findNodeByNameLike(nameKeyword: String): NodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: NodeEntity): Long

    @Query("DELETE FROM graph_nodes WHERE id = :id")
    suspend fun deleteNodeById(id: Int)

    // --- Stage 3: Personal Life Graph Edges ---
    @Query("SELECT * FROM graph_edges ORDER BY id ASC")
    fun getAllEdgesFlow(): Flow<List<EdgeEntity>>

    @Query("SELECT * FROM graph_edges WHERE sourceNodeId = :sourceNodeId")
    fun getEdgesForSourceFlow(sourceNodeId: Int): Flow<List<EdgeEntity>>

    @Query("SELECT * FROM graph_edges WHERE sourceNodeId = :sourceNodeId")
    suspend fun getEdgesBySourceId(sourceNodeId: Int): List<EdgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdge(edge: EdgeEntity): Long

    @Query("DELETE FROM graph_edges WHERE id = :id")
    suspend fun deleteEdgeById(id: Int)

    // --- Stage 3: Graph Traversal Queries ---
    @Query("""
        SELECT n.* FROM graph_nodes n
        INNER JOIN graph_edges e ON n.id = e.targetNodeId
        WHERE e.sourceNodeId = :activityNodeId AND e.relationType = 'REQUIRES' AND n.type = 'ITEM'
    """)
    suspend fun getRequiredItemNodesForActivity(activityNodeId: Int): List<NodeEntity>

    @Query("""
        SELECT itemNode.* FROM graph_nodes itemNode
        INNER JOIN graph_edges e ON itemNode.id = e.targetNodeId
        INNER JOIN graph_nodes actNode ON actNode.id = e.sourceNodeId
        WHERE LOWER(actNode.name) LIKE '%' || LOWER(:activityName) || '%'
          AND e.relationType = 'REQUIRES'
          AND itemNode.type = 'ITEM'
    """)
    suspend fun getRequiredItemsForActivityName(activityName: String): List<NodeEntity>

    // --- Stage 5: Habit and Forgetting Model Queries ---
    @Query("SELECT * FROM item_habits ORDER BY priorityBoost DESC")
    fun getAllHabitsFlow(): Flow<List<ItemHabitEntity>>

    @Query("SELECT * FROM item_habits WHERE modeId = :modeId ORDER BY priorityBoost DESC")
    fun getHabitsForModeFlow(modeId: Int): Flow<List<ItemHabitEntity>>

    @Query("SELECT * FROM item_habits WHERE modeId = :modeId ORDER BY priorityBoost DESC")
    suspend fun getHabitsForMode(modeId: Int): List<ItemHabitEntity>

    @Query("SELECT * FROM item_habits ORDER BY priorityBoost DESC")
    suspend fun getAllHabits(): List<ItemHabitEntity>

    @Query("SELECT * FROM item_habits WHERE LOWER(itemName) = LOWER(:itemName) AND modeId = :modeId LIMIT 1")
    suspend fun getHabit(itemName: String, modeId: Int): ItemHabitEntity?

    @Query("SELECT * FROM item_habits WHERE forgetCount >= :threshold ORDER BY forgetCount DESC")
    fun getFrequentlyForgottenHabitsFlow(threshold: Int = 3): Flow<List<ItemHabitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateHabit(habit: ItemHabitEntity): Long

    @Update
    suspend fun updateHabit(habit: ItemHabitEntity)

    @Query("UPDATE item_habits SET forgetCount = forgetCount + 1, priorityBoost = priorityBoost + 0.25 WHERE id = :id")
    suspend fun incrementForgetCountById(id: Int)

    @Query("UPDATE item_habits SET confirmCount = confirmCount + 1, priorityBoost = MAX(0.0, priorityBoost - 0.1) WHERE id = :id")
    suspend fun incrementConfirmCountById(id: Int)

    @Query("UPDATE item_habits SET waiveCount = waiveCount + 1 WHERE id = :id")
    suspend fun incrementWaiveCountById(id: Int)

    @Query("DELETE FROM item_habits WHERE LOWER(itemName) = LOWER(:itemName) AND modeId = :modeId")
    suspend fun deleteHabit(itemName: String, modeId: Int): Int

    @Query("DELETE FROM item_habits")
    suspend fun resetAllHabits()

    // --- Stage 7: Data Minimization Queries ---
    @Query("UPDATE evidence_logs SET rawText = '[Sanitized under Data Minimization]'")
    suspend fun sanitizeAllEvidence()

    @Query("DELETE FROM evidence_logs")
    suspend fun clearAllEvidence()

    // --- Stage 6 & Module 5: Offline Event Queue Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueOfflineTask(task: OfflineEventQueueEntity): Long

    @Query("SELECT * FROM offline_event_queue WHERE status = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingOfflineTasks(): List<OfflineEventQueueEntity>

    @Query("SELECT * FROM offline_event_queue WHERE status = 'PENDING' ORDER BY timestamp ASC")
    fun getPendingOfflineTasksFlow(): Flow<List<OfflineEventQueueEntity>>

    @Query("UPDATE offline_event_queue SET status = :status WHERE id = :id")
    suspend fun updateOfflineTaskStatus(id: Int, status: String)

    @Query("DELETE FROM offline_event_queue WHERE status = 'SYNCED'")
    suspend fun clearSyncedOfflineTasks(): Int

    @Query("DELETE FROM offline_event_queue")
    suspend fun clearAllOfflineTasks(): Int

    // --- Phase 7: Data Deletion & Factory Reset ---
    @Query("DELETE FROM life_modes")
    suspend fun deleteAllLifeModes()

    @Query("DELETE FROM item_requirements")
    suspend fun deleteAllItems()

    @Query("DELETE FROM events")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM graph_nodes")
    suspend fun deleteAllNodes()

    @Query("DELETE FROM graph_edges")
    suspend fun deleteAllEdges()

    // --- Final Phase: User Profile Queries ---
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfileFlow(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUserProfile(profile: UserProfileEntity): Long

    @Query("DELETE FROM user_profile")
    suspend fun deleteUserProfile()

    // --- Final Phase: Anchor Places Queries ---
    @Query("SELECT * FROM anchor_places ORDER BY id ASC")
    fun getAllAnchorPlacesFlow(): Flow<List<AnchorPlaceEntity>>

    @Query("SELECT * FROM anchor_places WHERE isEnabled = 1 AND isTemporarilyIgnored = 0")
    suspend fun getActiveAnchorPlaces(): List<AnchorPlaceEntity>

    @Query("SELECT * FROM anchor_places WHERE id = :id LIMIT 1")
    suspend fun getAnchorPlaceById(id: Int): AnchorPlaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnchorPlace(place: AnchorPlaceEntity): Long

    @Update
    suspend fun updateAnchorPlace(place: AnchorPlaceEntity)

    @Query("DELETE FROM anchor_places WHERE id = :id")
    suspend fun deleteAnchorPlaceById(id: Int)

    @Query("UPDATE anchor_places SET isTemporarilyIgnored = :ignored WHERE id = :id")
    suspend fun setAnchorPlaceIgnored(id: Int, ignored: Boolean)

    @Query("DELETE FROM anchor_places")
    suspend fun deleteAllAnchorPlaces()

    // --- Final Phase: Memory Vault Queries ---
    @Query("SELECT * FROM memory_vault ORDER BY isPinned DESC, createdAt DESC")
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory_vault WHERE memoryType = :type ORDER BY createdAt DESC")
    fun getMemoriesByTypeFlow(type: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory_vault WHERE id = :id LIMIT 1")
    suspend fun getMemoryById(id: Int): MemoryEntity?

    @Query("SELECT * FROM memory_vault WHERE LOWER(content) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(tags) LIKE '%' || LOWER(:keyword) || '%' ORDER BY isPinned DESC, createdAt DESC")
    suspend fun searchMemories(keyword: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("DELETE FROM memory_vault WHERE id = :id")
    suspend fun deleteMemoryById(id: Int)

    @Query("DELETE FROM memory_vault WHERE isPinned = 0 AND expiresAt > 0 AND expiresAt < :currentTime")
    suspend fun deleteExpiredMemories(currentTime: Long): Int

    @Query("DELETE FROM memory_vault")
    suspend fun deleteAllMemories()

    // --- Final Phase: Emergency Contacts Queries ---
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, id ASC")
    fun getAllEmergencyContactsFlow(): Flow<List<EmergencyContactEntity>>

    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, id ASC")
    suspend fun getAllEmergencyContacts(): List<EmergencyContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmergencyContact(contact: EmergencyContactEntity): Long

    @Update
    suspend fun updateEmergencyContact(contact: EmergencyContactEntity)

    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun deleteEmergencyContactById(id: Int)

    @Query("DELETE FROM emergency_contacts")
    suspend fun deleteAllEmergencyContacts()

    // --- Final Phase: App Usage Rules Queries ---
    @Query("SELECT * FROM app_usage_rules ORDER BY packageName ASC")
    fun getAllAppUsageRulesFlow(): Flow<List<AppUsageRuleEntity>>

    @Query("SELECT * FROM app_usage_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun getUsageRuleForPackage(packageName: String): AppUsageRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAppUsageRule(rule: AppUsageRuleEntity): Long

    @Query("DELETE FROM app_usage_rules WHERE id = :id")
    suspend fun deleteAppUsageRuleById(id: Int)

    @Query("DELETE FROM app_usage_rules")
    suspend fun deleteAllAppUsageRules()

    @Transaction
    suspend fun clearAllUserData() {
        deleteAllItems()
        deleteAllEvents()
        clearAllEvidence()
        resetAllHabits()
        deleteAllEdges()
        deleteAllNodes()
        clearAllOfflineTasks()
        deleteUserProfile()
        deleteAllAnchorPlaces()
        deleteAllMemories()
        deleteAllEmergencyContacts()
        deleteAllAppUsageRules()
        deleteAllLifeModes()
    }

    @Transaction
    suspend fun resetToFactoryDefaults() {
        clearAllUserData()
        // Re-insert default profile
        insertOrUpdateUserProfile(UserProfileEntity(id = 1, preferredName = "Dinesh", primaryRole = "Student", preferredGreeting = "Good Morning"))

        // Re-insert standard default seed
        insertLifeMode(LifeModeEntity(id = 1, name = "College", iconRes = "ic_college", isActive = true, priority = 1))
        insertLifeMode(LifeModeEntity(id = 2, name = "Gym", iconRes = "ic_gym", isActive = false, priority = 2))
        insertLifeMode(LifeModeEntity(id = 3, name = "Travel", iconRes = "ic_travel", isActive = false, priority = 3))
        insertLifeMode(LifeModeEntity(id = 4, name = "Sports", iconRes = "ic_sports", isActive = false, priority = 4))
        insertLifeMode(LifeModeEntity(id = 5, name = "Work", iconRes = "ic_work", isActive = false, priority = 5))
        insertLifeMode(LifeModeEntity(id = 6, name = "Shopping", iconRes = "ic_shopping", isActive = false, priority = 6))

        // Default College Items
        insertItem(ItemRequirementEntity(modeId = 1, itemName = "ID Card", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
        insertItem(ItemRequirementEntity(modeId = 1, itemName = "DBMS Record", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
        insertItem(ItemRequirementEntity(modeId = 1, itemName = "Laptop & Charger", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
        insertItem(ItemRequirementEntity(modeId = 1, itemName = "Water Bottle", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))

        // Default Gym Items
        insertItem(ItemRequirementEntity(modeId = 2, itemName = "Gym Clothes & Shoes", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
        insertItem(ItemRequirementEntity(modeId = 2, itemName = "Shaker Bottle", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
        insertItem(ItemRequirementEntity(modeId = 2, itemName = "Gym Towel", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))

        // Default Baseline Anchor Places
        insertAnchorPlace(AnchorPlaceEntity(id = 1, name = "Home", type = "HOME", radiusMeters = 100f, isEnabled = true))
        insertAnchorPlace(AnchorPlaceEntity(id = 2, name = "Campus", type = "CAMPUS", radiusMeters = 300f, isEnabled = true, associatedModeId = 1))
        insertAnchorPlace(AnchorPlaceEntity(id = 3, name = "Gym", type = "GYM", radiusMeters = 150f, isEnabled = true, associatedModeId = 2))
    }
}