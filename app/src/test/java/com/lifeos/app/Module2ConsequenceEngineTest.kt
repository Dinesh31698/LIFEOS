package com.lifeos.app

import com.lifeos.app.data.*
import com.lifeos.app.engine.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LIFEOS Module 2: Consequence Engine & Plan Mutation Test Suite.
 * Validates:
 *  1. Automated item waiving on English, Teluglish, and Hinglish cancellation notices.
 *  2. Dynamic readiness score recalculation (waived items excluded from pending counts).
 *  3. Suppression of automatic mutations when Safe Mode is active (routes proposal to Ghost Mode).
 *  4. Reverting waived status when user triggers undo waiving.
 *  5. Reschedule handling (clears waived flags and shifts timeline).
 *  6. Life Graph Traversal resolving activity-to-item dependencies.
 */
class Module2ConsequenceEngineTest {

    private lateinit var fakeDao: FakeLifeOsDao

    @Before
    fun setUp() {
        SafeModeManager.setSafeMode(false)
        fakeDao = FakeLifeOsDao()
        seedTestData(fakeDao)
    }

    @After
    fun tearDown() {
        SafeModeManager.setSafeMode(false)
        ConsequenceEngine.dismissAlert()
    }

    private fun seedTestData(dao: FakeLifeOsDao) {
        // Mode 1: College Mode (Active)
        dao.modes.add(LifeModeEntity(id = 1, name = "College", iconRes = "school", isActive = true))

        // Item Requirements for College Mode
        dao.items.add(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 2, modeId = 1, itemName = "DBMS Record", isReady = false, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 3, modeId = 1, itemName = "Laptop", isReady = false, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 4, modeId = 1, itemName = "Scientific Calculator", isReady = false, isWaived = false))

        // Event for College Mode
        val now = System.currentTimeMillis()
        dao.events.add(EventEntity(id = 101, title = "DBMS Lab", startTime = now, endTime = now + 7200000L, associatedModeId = 1))

        // Life Graph: DBMS Lab requires DBMS Record
        dao.nodes.add(NodeEntity(id = 201, name = "DBMS Lab", type = "ACTIVITY"))
        dao.nodes.add(NodeEntity(id = 202, name = "DBMS Record", type = "ITEM"))
        dao.edges.add(EdgeEntity(id = 301, sourceNodeId = 201, targetNodeId = 202, relationType = "REQUIRES"))
    }

    // =========================================================================
    // 1. Multilingual Automated Item Waiving (English, Teluglish, Hinglish)
    // =========================================================================

    @Test
    fun testEnglishCancellationTriggersItemWaiveAndEvidence() = runBlocking {
        val input = "DBMS Lab cancelled for today"
        val intent = MultilingualParser.parse(input)

        assertEquals(CanonicalAction.EVENT_CANCELLED, intent.canonicalAction)
        assertEquals("English", intent.detectedLanguage)
        assertEquals("DBMS Lab", intent.subjectActivity)

        val alert = ConsequenceEngine.processPlanMutation(fakeDao, intent)

        assertNotNull("Consequence alert must be emitted", alert)
        assertTrue("Alert should record DBMS Record as waived", alert!!.waivedItems.contains("DBMS Record"))
        assertEquals("DBMS Lab", alert.affectedActivity)

        val dbmsRecord = fakeDao.items.find { it.itemName == "DBMS Record" }
        assertNotNull(dbmsRecord)
        assertTrue("DBMS Record isWaived must be true in Room DB", dbmsRecord!!.isWaived)

        val latestEvidence = fakeDao.evidence.lastOrNull()
        assertNotNull("Plan mutation evidence must be logged", latestEvidence)
        assertTrue("Evidence should reflect waived item", latestEvidence!!.parsedIntent?.contains("CONSEQUENCE_WAIVED_ITEM") == true)
        assertTrue("Evidence confidence should match intent", latestEvidence.confidenceScore >= 0.85f)
    }

    @Test
    fun testTeluglishCancellationTriggersItemWaive() = runBlocking {
        val input = "DBMS lab cancel aindi"
        val intent = MultilingualParser.parse(input)

        assertEquals(CanonicalAction.EVENT_CANCELLED, intent.canonicalAction)
        assertEquals("Teluglish", intent.detectedLanguage)
        assertEquals("DBMS Lab", intent.subjectActivity)

        val alert = ConsequenceEngine.processPlanMutation(fakeDao, intent)

        assertNotNull(alert)
        assertTrue(alert!!.waivedItems.contains("DBMS Record"))
        assertTrue(fakeDao.items.find { it.itemName == "DBMS Record" }!!.isWaived)
    }

    @Test
    fun testHinglishCancellationTriggersItemWaive() = runBlocking {
        val input = "Kal ka DBMS lab cancel ho gaya hai"
        val intent = MultilingualParser.parse(input)

        assertEquals(CanonicalAction.EVENT_CANCELLED, intent.canonicalAction)
        assertEquals("Hinglish", intent.detectedLanguage)
        assertEquals("DBMS Lab", intent.subjectActivity)

        val alert = ConsequenceEngine.processPlanMutation(fakeDao, intent)

        assertNotNull(alert)
        assertTrue(alert!!.waivedItems.contains("DBMS Record"))
        assertTrue(fakeDao.items.find { it.itemName == "DBMS Record" }!!.isWaived)
    }

    // =========================================================================
    // 2. Dynamic Readiness Recalculation
    // =========================================================================

    @Test
    fun testDynamicReadinessRecalculationOnItemWaive() = runBlocking {
        val activeMode = fakeDao.getModeById(1)!!
        val initialItems = fakeDao.getItemsForMode(1)

        // Initial: 1 ready out of 4 total = 25%
        val initialReadiness = ReadinessEngine.calculateReadiness(activeMode, initialItems)
        assertEquals(25, initialReadiness.percentage)
        assertEquals(4, initialReadiness.totalCount)
        assertEquals(1, initialReadiness.readyCount)
        assertEquals(0, initialReadiness.waivedCount)
        assertEquals(3, initialReadiness.pendingItems.size)
        assertTrue(initialReadiness.pendingItems.contains("DBMS Record"))

        // Apply cancellation mutation
        val intent = MultilingualParser.parse("DBMS Lab cancelled")
        ConsequenceEngine.processPlanMutation(fakeDao, intent)

        // Recalculated items from DB
        val updatedItems = fakeDao.getItemsForMode(1)
        val updatedReadiness = ReadinessEngine.calculateReadiness(activeMode, updatedItems)

        // 1 ready out of 3 active requirements = 33%
        assertEquals("Score should recalculate to 33% with waived item excluded", 33, updatedReadiness.percentage)
        assertEquals("Total active count should drop to 3", 3, updatedReadiness.totalCount)
        assertEquals("Waived count should be 1", 1, updatedReadiness.waivedCount)
        assertEquals("Pending items should now be 2", 2, updatedReadiness.pendingItems.size)
        assertFalse("DBMS Record must no longer be in pending items", updatedReadiness.pendingItems.contains("DBMS Record"))
    }

    // =========================================================================
    // 3. Safe Mode Isolation / Suppression
    // =========================================================================

    @Test
    fun testSafeModeSuppressesAutomaticPlanMutation() = runBlocking {
        // Activate Safe Mode kill-switch
        SafeModeManager.setSafeMode(true)
        assertTrue(SafeModeManager.isSafeModeActive)

        val input = "DBMS Lab cancelled"
        val intent = MultilingualParser.parse(input)

        val alert = ConsequenceEngine.processPlanMutation(fakeDao, intent)

        assertNotNull("Alert should indicate safe mode suppression", alert)
        assertTrue("Alert title must reflect Safe Mode suppression", alert!!.title.contains("Safe Mode"))
        assertEquals("Suppressed (Safe Mode Active)", alert.actionTaken)
        assertTrue("Waived items list must be empty under Safe Mode", alert.waivedItems.isEmpty())

        // In DB, item must NOT be waived
        val dbmsRecord = fakeDao.items.find { it.itemName == "DBMS Record" }!!
        assertFalse("Item must NOT be waived when Safe Mode is active", dbmsRecord.isWaived)

        // Proposal routed to Ghost Mode in events
        val ghostEvent = fakeDao.events.find { it.isGhostMode && it.title.contains("Cancelled") }
        assertNotNull("Cancellation proposal must be routed to Ghost Mode", ghostEvent)
        assertTrue(ghostEvent!!.evidenceText?.contains("Safe Mode Held") == true)

        // Evidence logs should record SAFE_MODE_BLOCKED
        val evidence = fakeDao.evidence.lastOrNull()
        assertNotNull(evidence)
        assertTrue(evidence!!.parsedIntent?.contains("SAFE_MODE_BLOCKED") == true)
    }

    // =========================================================================
    // 4. Reverting Waived Status (Undo Waive)
    // =========================================================================

    @Test
    fun testUndoWaiveRevertsItemsAndRecalculatesReadiness() = runBlocking {
        // First waive the item
        val intent = MultilingualParser.parse("DBMS Lab cancelled")
        ConsequenceEngine.processPlanMutation(fakeDao, intent)

        assertTrue(fakeDao.items.find { it.itemName == "DBMS Record" }!!.isWaived)
        assertNotNull(ConsequenceEngine.latestConsequenceAlert.value)

        // Now trigger Undo Waive
        ConsequenceEngine.resetWaivedItems(fakeDao, modeId = 1)

        // Verify item is restored to isWaived = false
        val restoredItem = fakeDao.items.find { it.itemName == "DBMS Record" }!!
        assertFalse("Item isWaived must revert to false", restoredItem.isWaived)

        // Alert dismissed
        assertNull("Consequence alert must be dismissed after undo", ConsequenceEngine.latestConsequenceAlert.value)

        // Readiness recalculated back to 25%
        val activeMode = fakeDao.getModeById(1)!!
        val restoredReadiness = ReadinessEngine.calculateReadiness(activeMode, fakeDao.getItemsForMode(1))
        assertEquals(25, restoredReadiness.percentage)
        assertEquals(4, restoredReadiness.totalCount)
        assertEquals(3, restoredReadiness.pendingItems.size)
        assertTrue(restoredReadiness.pendingItems.contains("DBMS Record"))
    }

    // =========================================================================
    // 5. Reschedule Handling (EVENT_RESCHEDULED)
    // =========================================================================

    @Test
    fun testRescheduleClearsWaivedFlagAndAdjustsTimeline() = runBlocking {
        // Manually waive DBMS Record first
        fakeDao.setItemWaivedByName("DBMS Record", true)
        assertTrue(fakeDao.items.find { it.itemName == "DBMS Record" }!!.isWaived)

        val originalStartTime = fakeDao.events.first().startTime

        val rescheduleIntent = ParsedIntentResult(
            canonicalAction = CanonicalAction.EVENT_RESCHEDULED,
            detectedLanguage = "English",
            rawText = "DBMS Lab rescheduled to tomorrow",
            subjectActivity = "DBMS Lab",
            relatedItem = "DBMS Record",
            timeOrDateInfo = "Tomorrow",
            confidence = 0.90f,
            targetModeId = 1
        )

        val alert = ConsequenceEngine.processPlanMutation(fakeDao, rescheduleIntent)

        assertNotNull(alert)
        assertEquals("Timeline Adjusted & Waived Cleared", alert!!.actionTaken)

        // Item waived flag cleared
        val item = fakeDao.items.find { it.itemName == "DBMS Record" }!!
        assertFalse("Reschedule must clear waived status for item", item.isWaived)

        // Event timing adjusted
        val updatedEvent = fakeDao.events.find { it.title.contains("DBMS Lab") }!!
        assertTrue("Event start time must be shifted forward", updatedEvent.startTime > originalStartTime)
    }

    // =========================================================================
    // 6. Life Graph Traversal Test
    // =========================================================================

    @Test
    fun testLifeGraphTraversalIdentifiesLinkedRequirements() = runBlocking {
        val linkedNodes = fakeDao.getRequiredItemsForActivityName("DBMS Lab")
        assertEquals(1, linkedNodes.size)
        assertEquals("DBMS Record", linkedNodes[0].name)
        assertEquals("ITEM", linkedNodes[0].type)
    }
}

/**
 * In-memory hermetic Fake for [LifeOsDao] to support zero-latency, isolated unit tests.
 */
class FakeLifeOsDao : LifeOsDao {
    val modes = mutableListOf<LifeModeEntity>()
    val items = mutableListOf<ItemRequirementEntity>()
    val events = mutableListOf<EventEntity>()
    val evidence = mutableListOf<EvidenceEntity>()
    val nodes = mutableListOf<NodeEntity>()
    val edges = mutableListOf<EdgeEntity>()
    val habits = mutableListOf<ItemHabitEntity>()

    override suspend fun getModeById(id: Int): LifeModeEntity? = modes.find { it.id == id }

    override fun getActiveModeFlow(): Flow<LifeModeEntity?> = flowOf(modes.find { it.isActive })

    override fun getAllModesFlow(): Flow<List<LifeModeEntity>> = flowOf(modes)

    override suspend fun insertLifeMode(mode: LifeModeEntity): Long {
        modes.removeAll { it.id == mode.id }
        modes.add(mode)
        return mode.id.toLong()
    }

    override suspend fun updateLifeMode(mode: LifeModeEntity) {
        val index = modes.indexOfFirst { it.id == mode.id }
        if (index != -1) {
            modes[index] = mode
        }
    }

    override suspend fun resetActiveModes() {
        for (i in modes.indices) {
            modes[i] = modes[i].copy(isActive = false)
        }
    }

    override suspend fun setActiveModeById(modeId: Int) {
        for (i in modes.indices) {
            modes[i] = modes[i].copy(isActive = (modes[i].id == modeId))
        }
    }

    override suspend fun deactivateModeById(modeId: Int) {
        val index = modes.indexOfFirst { it.id == modeId }
        if (index != -1) {
            modes[index] = modes[index].copy(isActive = false)
        }
    }

    override suspend fun deleteItemsForMode(modeId: Int) {
        items.removeAll { it.modeId == modeId }
    }

    override suspend fun deleteEventsForMode(modeId: Int) {
        events.removeAll { it.associatedModeId == modeId }
    }

    override suspend fun deleteHabitsForMode(modeId: Int) {
        habits.removeAll { it.modeId == modeId }
    }

    override suspend fun deleteLifeModeById(modeId: Int) {
        modes.removeAll { it.id == modeId }
    }

    override suspend fun findItemInModeByName(modeId: Int, name: String): ItemRequirementEntity? {
        return items.find { it.modeId == modeId && it.itemName.trim().equals(name.trim(), ignoreCase = true) }
    }

    override fun getItemsForModeFlow(modeId: Int): Flow<List<ItemRequirementEntity>> =
        flowOf(items.filter { it.modeId == modeId })

    override fun getActiveItemsForModeFlow(modeId: Int): Flow<List<ItemRequirementEntity>> =
        flowOf(items.filter { it.modeId == modeId && !it.isGhostMode })

    override fun getGhostItemsForModeFlow(modeId: Int): Flow<List<ItemRequirementEntity>> =
        flowOf(items.filter { it.modeId == modeId && it.isGhostMode })

    override fun getAllItemsFlow(): Flow<List<ItemRequirementEntity>> = flowOf(items)

    override suspend fun getItemById(id: Int): ItemRequirementEntity? = items.find { it.id == id }

    override suspend fun insertItem(item: ItemRequirementEntity): Long {
        val newId = if (item.id == 0) (items.maxOfOrNull { it.id } ?: 0) + 1 else item.id
        val newItem = item.copy(id = newId)
        items.removeAll { it.id == newId }
        items.add(newItem)
        return newId.toLong()
    }

    override suspend fun updateItem(item: ItemRequirementEntity) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index != -1) {
            items[index] = item
        }
    }

    override suspend fun updateItemStatus(itemId: Int, isReady: Boolean) {
        val index = items.indexOfFirst { it.id == itemId }
        if (index != -1) {
            items[index] = items[index].copy(isReady = isReady)
        }
    }

    override suspend fun setItemWaived(itemId: Int, isWaived: Boolean) {
        val index = items.indexOfFirst { it.id == itemId }
        if (index != -1) {
            items[index] = items[index].copy(isWaived = isWaived)
        }
    }

    override suspend fun setItemWaivedByName(nameKeyword: String, isWaived: Boolean): Int {
        var count = 0
        for (i in items.indices) {
            if (items[i].itemName.contains(nameKeyword, ignoreCase = true)) {
                items[i] = items[i].copy(isWaived = isWaived)
                count++
            }
        }
        return count
    }

    override suspend fun resetAllWaivedItemsForMode(modeId: Int): Int {
        var count = 0
        for (i in items.indices) {
            if (items[i].modeId == modeId && items[i].isWaived) {
                items[i] = items[i].copy(isWaived = false)
                count++
            }
        }
        return count
    }

    override suspend fun getItemsByNameLike(nameKeyword: String): List<ItemRequirementEntity> =
        items.filter { it.itemName.contains(nameKeyword, ignoreCase = true) }

    override suspend fun promoteGhostItem(itemId: Int) {
        val index = items.indexOfFirst { it.id == itemId }
        if (index != -1) {
            items[index] = items[index].copy(isGhostMode = false, isTemporary = true)
        }
    }

    override suspend fun deleteItemById(itemId: Int) {
        items.removeAll { it.id == itemId }
    }

    override suspend fun deleteExpiredItems(currentTime: Long): Int {
        val before = items.size
        items.removeAll { it.isTemporary && it.ttlTimestamp > 0 && it.ttlTimestamp < currentTime }
        return before - items.size
    }

    override suspend fun clearTemporaryItems(): Int {
        val before = items.size
        items.removeAll { it.isTemporary }
        return before - items.size
    }

    override suspend fun getItemsForMode(modeId: Int): List<ItemRequirementEntity> =
        items.filter { it.modeId == modeId }

    override fun getEventsForModeFlow(modeId: Int): Flow<List<EventEntity>> =
        flowOf(events.filter { it.associatedModeId == modeId })

    override fun getGhostEventsFlow(): Flow<List<EventEntity>> =
        flowOf(events.filter { it.isGhostMode })

    override fun getAllEventsFlow(): Flow<List<EventEntity>> = flowOf(events)

    override suspend fun findEventByTitleLike(titleKeyword: String): EventEntity? =
        events.find { it.title.contains(titleKeyword, ignoreCase = true) }

    override suspend fun updateEventTiming(eventId: Int, startTime: Long, endTime: Long) {
        val index = events.indexOfFirst { it.id == eventId }
        if (index != -1) {
            events[index] = events[index].copy(startTime = startTime, endTime = endTime)
        }
    }

    override suspend fun updateEvent(event: EventEntity) {
        val index = events.indexOfFirst { it.id == event.id }
        if (index != -1) {
            events[index] = event
        }
    }

    override suspend fun insertEvent(event: EventEntity): Long {
        val newId = if (event.id == 0) (events.maxOfOrNull { it.id } ?: 0) + 1 else event.id
        val newEvent = event.copy(id = newId)
        events.removeAll { it.id == newId }
        events.add(newEvent)
        return newId.toLong()
    }

    override suspend fun promoteGhostEvent(eventId: Int) {
        val index = events.indexOfFirst { it.id == eventId }
        if (index != -1) {
            events[index] = events[index].copy(isGhostMode = false)
        }
    }

    override suspend fun deleteEventById(eventId: Int) {
        events.removeAll { it.id == eventId }
    }

    override fun getEvidenceForEventFlow(eventId: Int): Flow<List<EvidenceEntity>> =
        flowOf(evidence.filter { it.eventId == eventId })

    override fun getLatestEvidenceFlow(): Flow<List<EvidenceEntity>> =
        flowOf(evidence.sortedByDescending { it.timestamp })

    override suspend fun getEvidenceById(id: Int): EvidenceEntity? = evidence.find { it.id == id }

    override suspend fun insertEvidence(evidenceEntity: EvidenceEntity): Long {
        val newId = if (evidenceEntity.id == 0) (evidence.maxOfOrNull { it.id } ?: 0) + 1 else evidenceEntity.id
        val newEvidence = evidenceEntity.copy(id = newId)
        evidence.add(newEvidence)
        return newId.toLong()
    }

    override fun getAllNodesFlow(): Flow<List<NodeEntity>> = flowOf(nodes)

    override fun getNodesByTypeFlow(type: String): Flow<List<NodeEntity>> =
        flowOf(nodes.filter { it.type.equals(type, ignoreCase = true) })

    override suspend fun getNodeById(id: Int): NodeEntity? = nodes.find { it.id == id }

    override suspend fun findNodeByName(name: String): NodeEntity? =
        nodes.find { it.name.equals(name, ignoreCase = true) }

    override suspend fun findNodeByNameLike(nameKeyword: String): NodeEntity? =
        nodes.find { it.name.contains(nameKeyword, ignoreCase = true) }

    override suspend fun insertNode(node: NodeEntity): Long {
        val newId = if (node.id == 0) (nodes.maxOfOrNull { it.id } ?: 0) + 1 else node.id
        val newNode = node.copy(id = newId)
        nodes.removeAll { it.id == newId }
        nodes.add(newNode)
        return newId.toLong()
    }

    override suspend fun deleteNodeById(id: Int) {
        nodes.removeAll { it.id == id }
    }

    override fun getAllEdgesFlow(): Flow<List<EdgeEntity>> = flowOf(edges)

    override fun getEdgesForSourceFlow(sourceNodeId: Int): Flow<List<EdgeEntity>> =
        flowOf(edges.filter { it.sourceNodeId == sourceNodeId })

    override suspend fun getEdgesBySourceId(sourceNodeId: Int): List<EdgeEntity> =
        edges.filter { it.sourceNodeId == sourceNodeId }

    override suspend fun insertEdge(edge: EdgeEntity): Long {
        val newId = if (edge.id == 0) (edges.maxOfOrNull { it.id } ?: 0) + 1 else edge.id
        val newEdge = edge.copy(id = newId)
        edges.removeAll { it.id == newId }
        edges.add(newEdge)
        return newId.toLong()
    }

    override suspend fun deleteEdgeById(id: Int) {
        edges.removeAll { it.id == id }
    }

    override suspend fun getRequiredItemNodesForActivity(activityNodeId: Int): List<NodeEntity> {
        val targetNodeIds = edges.filter { it.sourceNodeId == activityNodeId && it.relationType == "REQUIRES" }
            .map { it.targetNodeId }
        return nodes.filter { it.id in targetNodeIds && it.type.equals("ITEM", ignoreCase = true) }
    }

    override suspend fun getRequiredItemsForActivityName(activityName: String): List<NodeEntity> {
        val matchingActivityNodes = nodes.filter {
            it.name.contains(activityName, ignoreCase = true)
        }
        val targetItemIds = mutableSetOf<Int>()
        for (actNode in matchingActivityNodes) {
            val targets = edges.filter { it.sourceNodeId == actNode.id && it.relationType == "REQUIRES" }
                .map { it.targetNodeId }
            targetItemIds.addAll(targets)
        }
        return nodes.filter { it.id in targetItemIds && it.type.equals("ITEM", ignoreCase = true) }
    }

    override fun getAllHabitsFlow(): Flow<List<ItemHabitEntity>> = flowOf(habits)

    override fun getHabitsForModeFlow(modeId: Int): Flow<List<ItemHabitEntity>> =
        flowOf(habits.filter { it.modeId == modeId })

    override suspend fun getHabitsForMode(modeId: Int): List<ItemHabitEntity> =
        habits.filter { it.modeId == modeId }

    override suspend fun getAllHabits(): List<ItemHabitEntity> = habits.toList()

    override suspend fun getHabit(itemName: String, modeId: Int): ItemHabitEntity? =
        habits.find { it.itemName.equals(itemName, ignoreCase = true) && it.modeId == modeId }

    override fun getFrequentlyForgottenHabitsFlow(threshold: Int): Flow<List<ItemHabitEntity>> =
        flowOf(habits.filter { it.forgetCount >= threshold })

    override suspend fun insertOrUpdateHabit(habit: ItemHabitEntity): Long {
        val newId = if (habit.id == 0) (habits.maxOfOrNull { it.id } ?: 0) + 1 else habit.id
        val newHabit = habit.copy(id = newId)
        habits.removeAll { it.id == newId }
        habits.add(newHabit)
        return newId.toLong()
    }

    override suspend fun updateHabit(habit: ItemHabitEntity) {
        val index = habits.indexOfFirst { it.id == habit.id }
        if (index != -1) {
            habits[index] = habit
        }
    }

    override suspend fun incrementForgetCountById(id: Int) {
        val index = habits.indexOfFirst { it.id == id }
        if (index != -1) {
            val h = habits[index]
            habits[index] = h.copy(forgetCount = h.forgetCount + 1, priorityBoost = h.priorityBoost + 0.25f)
        }
    }

    override suspend fun incrementConfirmCountById(id: Int) {
        val index = habits.indexOfFirst { it.id == id }
        if (index != -1) {
            val h = habits[index]
            habits[index] = h.copy(confirmCount = h.confirmCount + 1, priorityBoost = maxOf(0.0f, h.priorityBoost - 0.1f))
        }
    }

    override suspend fun incrementWaiveCountById(id: Int) {
        val index = habits.indexOfFirst { it.id == id }
        if (index != -1) {
            val h = habits[index]
            habits[index] = h.copy(waiveCount = h.waiveCount + 1)
        }
    }

    override suspend fun deleteHabit(itemName: String, modeId: Int): Int {
        val before = habits.size
        habits.removeAll { it.modeId == modeId && it.itemName.equals(itemName, ignoreCase = true) }
        return before - habits.size
    }

    override suspend fun resetAllHabits() {
        habits.clear()
    }

    override suspend fun sanitizeAllEvidence() {
        for (i in evidence.indices) {
            evidence[i] = evidence[i].copy(rawText = "[Sanitized under Data Minimization]")
        }
    }

    override suspend fun clearAllEvidence() {
        evidence.clear()
    }

    // --- Offline Event Queue ---
    val offlineTasks = mutableListOf<OfflineEventQueueEntity>()

    override suspend fun enqueueOfflineTask(task: OfflineEventQueueEntity): Long {
        val newId = if (task.id == 0) (offlineTasks.maxOfOrNull { it.id } ?: 0) + 1 else task.id
        val newTask = task.copy(id = newId)
        offlineTasks.removeAll { it.id == newId }
        offlineTasks.add(newTask)
        return newId.toLong()
    }

    override suspend fun getPendingOfflineTasks(): List<OfflineEventQueueEntity> =
        offlineTasks.filter { it.status == "PENDING" }

    override fun getPendingOfflineTasksFlow(): Flow<List<OfflineEventQueueEntity>> =
        flowOf(offlineTasks.filter { it.status == "PENDING" })

    override suspend fun updateOfflineTaskStatus(id: Int, status: String) {
        val index = offlineTasks.indexOfFirst { it.id == id }
        if (index != -1) {
            offlineTasks[index] = offlineTasks[index].copy(status = status)
        }
    }

    override suspend fun clearSyncedOfflineTasks(): Int {
        val count = offlineTasks.count { it.status == "SYNCED" }
        offlineTasks.removeAll { it.status == "SYNCED" }
        return count
    }

    override suspend fun clearAllOfflineTasks(): Int {
        val count = offlineTasks.size
        offlineTasks.clear()
        return count
    }

    override suspend fun deleteAllLifeModes() { modes.clear() }
    override suspend fun deleteAllItems() { items.clear() }
    override suspend fun deleteAllEvents() { events.clear() }
    override suspend fun deleteAllNodes() { nodes.clear() }
    override suspend fun deleteAllEdges() { edges.clear() }

    var userProfile: UserProfileEntity? = null
    val anchorPlaces = mutableListOf<AnchorPlaceEntity>()
    val memories = mutableListOf<MemoryEntity>()
    val contacts = mutableListOf<EmergencyContactEntity>()
    val usageRules = mutableListOf<AppUsageRuleEntity>()

    override fun getUserProfileFlow(): Flow<UserProfileEntity?> = flowOf(userProfile)
    override suspend fun getUserProfile(): UserProfileEntity? = userProfile
    override suspend fun insertOrUpdateUserProfile(profile: UserProfileEntity): Long {
        userProfile = profile
        return 1L
    }
    override suspend fun deleteUserProfile() { userProfile = null }

    override fun getAllAnchorPlacesFlow(): Flow<List<AnchorPlaceEntity>> = flowOf(anchorPlaces)
    override suspend fun getActiveAnchorPlaces(): List<AnchorPlaceEntity> = anchorPlaces.filter { it.isEnabled && !it.isTemporarilyIgnored }
    override suspend fun getAnchorPlaceById(id: Int): AnchorPlaceEntity? = anchorPlaces.find { it.id == id }
    override suspend fun insertAnchorPlace(place: AnchorPlaceEntity): Long {
        val newId = if (place.id == 0) (anchorPlaces.maxOfOrNull { it.id } ?: 0) + 1 else place.id
        val newPlace = place.copy(id = newId)
        anchorPlaces.removeAll { it.id == newId }
        anchorPlaces.add(newPlace)
        return newId.toLong()
    }
    override suspend fun updateAnchorPlace(place: AnchorPlaceEntity) {
        val index = anchorPlaces.indexOfFirst { it.id == place.id }
        if (index != -1) anchorPlaces[index] = place
    }
    override suspend fun deleteAnchorPlaceById(id: Int) { anchorPlaces.removeAll { it.id == id } }
    override suspend fun setAnchorPlaceIgnored(id: Int, ignored: Boolean) {
        val index = anchorPlaces.indexOfFirst { it.id == id }
        if (index != -1) anchorPlaces[index] = anchorPlaces[index].copy(isTemporarilyIgnored = ignored)
    }
    override suspend fun deleteAllAnchorPlaces() { anchorPlaces.clear() }

    override fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> = flowOf(memories)
    override fun getMemoriesByTypeFlow(type: String): Flow<List<MemoryEntity>> = flowOf(memories.filter { it.memoryType == type })
    override suspend fun getMemoryById(id: Int): MemoryEntity? = memories.find { it.id == id }
    override suspend fun searchMemories(keyword: String): List<MemoryEntity> =
        memories.filter { it.content.contains(keyword, ignoreCase = true) || it.tags.contains(keyword, ignoreCase = true) }
    override suspend fun insertMemory(memory: MemoryEntity): Long {
        val newId = if (memory.id == 0) (memories.maxOfOrNull { it.id } ?: 0) + 1 else memory.id
        val newMem = memory.copy(id = newId)
        memories.removeAll { it.id == newId }
        memories.add(newMem)
        return newId.toLong()
    }
    override suspend fun updateMemory(memory: MemoryEntity) {
        val index = memories.indexOfFirst { it.id == memory.id }
        if (index != -1) memories[index] = memory
    }
    override suspend fun deleteMemoryById(id: Int) { memories.removeAll { it.id == id } }
    override suspend fun deleteExpiredMemories(currentTime: Long): Int {
        val count = memories.count { !it.isPinned && it.expiresAt > 0 && it.expiresAt < currentTime }
        memories.removeAll { !it.isPinned && it.expiresAt > 0 && it.expiresAt < currentTime }
        return count
    }
    override suspend fun deleteAllMemories() { memories.clear() }

    override fun getAllEmergencyContactsFlow(): Flow<List<EmergencyContactEntity>> = flowOf(contacts)
    override suspend fun getAllEmergencyContacts(): List<EmergencyContactEntity> = contacts.toList()
    override suspend fun insertEmergencyContact(contact: EmergencyContactEntity): Long {
        val newId = if (contact.id == 0) (contacts.maxOfOrNull { it.id } ?: 0) + 1 else contact.id
        val newC = contact.copy(id = newId)
        contacts.removeAll { it.id == newId }
        contacts.add(newC)
        return newId.toLong()
    }
    override suspend fun updateEmergencyContact(contact: EmergencyContactEntity) {
        val index = contacts.indexOfFirst { it.id == contact.id }
        if (index != -1) contacts[index] = contact
    }
    override suspend fun deleteEmergencyContactById(id: Int) { contacts.removeAll { it.id == id } }
    override suspend fun deleteAllEmergencyContacts() { contacts.clear() }

    override fun getAllAppUsageRulesFlow(): Flow<List<AppUsageRuleEntity>> = flowOf(usageRules)
    override suspend fun getUsageRuleForPackage(packageName: String): AppUsageRuleEntity? = usageRules.find { it.packageName == packageName }
    override suspend fun insertOrUpdateAppUsageRule(rule: AppUsageRuleEntity): Long {
        val newId = if (rule.id == 0) (usageRules.maxOfOrNull { it.id } ?: 0) + 1 else rule.id
        val newR = rule.copy(id = newId)
        usageRules.removeAll { it.packageName == rule.packageName }
        usageRules.add(newR)
        return newId.toLong()
    }
    override suspend fun deleteAppUsageRuleById(id: Int) { usageRules.removeAll { it.id == id } }
    override suspend fun deleteAllAppUsageRules() { usageRules.clear() }
}

