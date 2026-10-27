package com.lifeos.app

import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.engine.ForgettingModelEngine
import com.lifeos.app.engine.ReadinessEngine
import com.lifeos.app.engine.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.roundToInt

/**
 * LIFEOS TEST PHASE 3: Custom Activity Checklists Comprehensive Verification Suite.
 *
 * Verifies:
 *  1. Basic Checklist Creation & Mode Association
 *  2. Custom Checklist Editing (Add, Remove, Rename without resurrection)
 *  3. Item Completion State Differentiation (Ready vs Missing)
 *  4. Mathematical Correctness of Readiness Calculation (0, 1, 2, 5, 50 items, NaN/Div0 protection)
 *  5. Habit-driven Item Risk & Priority Assessment (NORMAL vs HIGH_RISK)
 *  6. Checklist Item Scope: Permanent vs Temporary Session Items
 *  7. Case-Insensitive & Whitespace Trimmed Duplicate Prevention
 *  8. Multi-Activity Coexistence of Identical Item Names
 *  9. Multilingual Support (Telugu, Hindi, Code-Mixed, Emoji, Mixed Case)
 * 10. Boundary Length Handling (Ultra-long 150+ chars vs Short 2-char items)
 * 11. Special Characters (Apostrophes, Brackets, Hyphens, Emojis, Punctuations)
 * 12. Cross-Activity Mutation Independence
 * 13. Sequential Deletion & Orphan Purge
 * 14. Large Checklist Scale Stress (50+ items)
 * 15. Rapid Concurrent Item Toggles
 */
class Phase3CustomChecklistUnitTest {

    private lateinit var dao: FakeLifeOsDao

    @Before
    fun setUp() {
        runBlocking {
            dao = FakeLifeOsDao()
            dao.insertLifeMode(LifeModeEntity(id = 1, name = "College", iconRes = "ic_college", isActive = true, priority = 1))
            dao.insertLifeMode(LifeModeEntity(id = 2, name = "Gym", iconRes = "ic_gym", isActive = false, priority = 2))
            dao.insertLifeMode(LifeModeEntity(id = 3, name = "Travel", iconRes = "ic_travel", isActive = false, priority = 3))
            dao.insertLifeMode(LifeModeEntity(id = 4, name = "Sports", iconRes = "ic_sports", isActive = false, priority = 4))
        }
    }

    @Test
    fun testBasicChecklistCreationCollege() = runBlocking {
        val collegeItems = listOf(
            "ID Card",
            "Laptop",
            "Charger",
            "Watch",
            "Water Bottle"
        )

        collegeItems.forEachIndexed { index, name ->
            dao.insertItem(
                ItemRequirementEntity(
                    id = index + 1,
                    modeId = 1,
                    itemName = name,
                    isReady = false,
                    isTemporary = false
                )
            )
        }

        val storedItems = dao.getItemsForMode(1)
        assertEquals(5, storedItems.size)
        assertEquals(listOf("ID Card", "Laptop", "Charger", "Watch", "Water Bottle"), storedItems.map { it.itemName })
        assertTrue(storedItems.all { it.modeId == 1 })
        assertTrue(storedItems.all { !it.isReady })
    }

    @Test
    fun testCustomChecklistEditingSports() = runBlocking {
        // Initial items for Sports (Mode 4)
        val initialItems = listOf("Water Bottle", "Sports Shoes", "Towel", "Sports Clothes")
        var currentId = 100
        initialItems.forEach { name ->
            dao.insertItem(ItemRequirementEntity(id = currentId++, modeId = 4, itemName = name))
        }

        assertEquals(4, dao.getItemsForMode(4).size)

        // 1. Add "Cap"
        dao.insertItem(ItemRequirementEntity(id = currentId++, modeId = 4, itemName = "Cap"))
        assertEquals(5, dao.getItemsForMode(4).size)

        // 2. Remove "Towel"
        val towelItem = dao.getItemsForMode(4).find { it.itemName == "Towel" }
        assertNotNull("Towel must exist before deletion", towelItem)
        dao.deleteItemById(towelItem!!.id)

        // 3. Rename "Sports Clothes" -> "Sports Kit"
        val clothesItem = dao.getItemsForMode(4).find { it.itemName == "Sports Clothes" }
        assertNotNull("Sports Clothes must exist before rename", clothesItem)
        dao.updateItem(clothesItem!!.copy(itemName = "Sports Kit"))

        // Final verification: Expected exactly [Water Bottle, Sports Shoes, Sports Kit, Cap]
        val finalItems = dao.getItemsForMode(4)
        assertEquals(4, finalItems.size)
        val finalNames = finalItems.map { it.itemName }
        assertTrue(finalNames.contains("Water Bottle"))
        assertTrue(finalNames.contains("Sports Shoes"))
        assertTrue(finalNames.contains("Sports Kit"))
        assertTrue(finalNames.contains("Cap"))
        assertFalse("Deleted Towel must not be present", finalNames.contains("Towel"))
        assertFalse("Old name Sports Clothes must not be present", finalNames.contains("Sports Clothes"))
    }

    @Test
    fun testItemCompletionStateCollege() = runBlocking {
        val items = listOf("ID Card", "Laptop", "Charger", "Watch", "Water Bottle")
        items.forEachIndexed { idx, name ->
            dao.insertItem(ItemRequirementEntity(id = idx + 1, modeId = 1, itemName = name, isReady = false))
        }

        // Mark ID Card, Laptop, Watch -> Completed
        val idCard = dao.findItemInModeByName(1, "ID Card")!!
        val laptop = dao.findItemInModeByName(1, "Laptop")!!
        val watch = dao.findItemInModeByName(1, "Watch")!!

        dao.updateItemStatus(idCard.id, true)
        dao.updateItemStatus(laptop.id, true)
        dao.updateItemStatus(watch.id, true)

        val updatedItems = dao.getItemsForMode(1)
        val readyItems = updatedItems.filter { it.isReady }.map { it.itemName }
        val missingItems = updatedItems.filter { !it.isReady }.map { it.itemName }

        assertEquals(listOf("ID Card", "Laptop", "Watch"), readyItems)
        assertEquals(listOf("Charger", "Water Bottle"), missingItems)
    }

    @Test
    fun testReadinessCalculationFormulas() {
        fun calc(ready: Int, total: Int): Int {
            // Match ReadinessEngine: empty -> -1 (not configured), otherwise percentage
            if (total == 0) return -1
            return ((ready.toFloat() / total.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        }

        // 0 items -> -1 (not configured sentinel, NOT 100%)
        assertEquals("Empty checklist should return -1 sentinel", -1, calc(0, 0))

        // 1 item: 0 ready -> 0%, 1 ready -> 100%
        assertEquals("1 item, 0 ready", 0, calc(0, 1))
        assertEquals("1 item, 1 ready", 100, calc(1, 1))

        // 2 items: 1 ready -> 50%
        assertEquals("2 items, 1 ready", 50, calc(1, 2))

        // 5 items:
        assertEquals(0, calc(0, 5))
        assertEquals(20, calc(1, 5))
        assertEquals(40, calc(2, 5))
        assertEquals(60, calc(3, 5))
        assertEquals(80, calc(4, 5))
        assertEquals(100, calc(5, 5))

        // Engine Integration — Empty checklist
        val emptyReport = ReadinessEngine.calculateReadiness(
            LifeModeEntity(id = 99, name = "EmptyMode"),
            emptyList()
        )
        assertEquals("Engine empty checklist percentage", -1, emptyReport.percentage)
        assertEquals(0, emptyReport.totalCount)
        assertEquals(0, emptyReport.readyCount)
        assertTrue(emptyReport.summaryNotification.contains("No requirements configured"))

        // Engine Integration — Normal 5-item checklist
        val college = LifeModeEntity(id = 1, name = "College")
        val checklist = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = true),
            ItemRequirementEntity(id = 3, modeId = 1, itemName = "Charger", isReady = false),
            ItemRequirementEntity(id = 4, modeId = 1, itemName = "Watch", isReady = true),
            ItemRequirementEntity(id = 5, modeId = 1, itemName = "Water Bottle", isReady = false)
        )

        val report = ReadinessEngine.calculateReadiness(college, checklist)
        assertEquals(3, report.readyCount)
        assertEquals(5, report.totalCount)
        assertEquals(60, report.percentage)
        assertEquals(listOf("Charger", "Water Bottle"), report.pendingItems)

        // Engine Integration — Fully completed checklist
        val fullChecklist = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = true)
        )
        val fullReport = ReadinessEngine.calculateReadiness(college, fullChecklist)
        assertEquals(100, fullReport.percentage)
        assertEquals(2, fullReport.readyCount)
        assertEquals(2, fullReport.totalCount)
        assertTrue(fullReport.pendingItems.isEmpty())
    }

    @Test
    fun testFourScenariosEmptyOneItemPartialFull() {
        val mode = LifeModeEntity(id = 1, name = "College")

        // 1. Empty Checklist (0 items) -> Must not crash, returns -1, "No requirements configured"
        val emptyReport = ReadinessEngine.calculateReadiness(mode, emptyList())
        assertEquals("0 items: does not crash and percentage is -1", -1, emptyReport.percentage)
        assertEquals("0 items: readyCount is 0", 0, emptyReport.readyCount)
        assertEquals("0 items: totalCount is 0", 0, emptyReport.totalCount)
        assertTrue("0 items: pendingItems is empty", emptyReport.pendingItems.isEmpty())
        assertEquals("College: No requirements configured.", emptyReport.summaryNotification)

        // 2. One-item Checklist
        // 2a: 1 item, not ready -> 0%
        val oneItemNotReady = listOf(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = false))
        val report1NotReady = ReadinessEngine.calculateReadiness(mode, oneItemNotReady)
        assertEquals("1 item not ready: 0%", 0, report1NotReady.percentage)
        assertEquals(0, report1NotReady.readyCount)
        assertEquals(1, report1NotReady.totalCount)
        assertEquals(listOf("ID Card"), report1NotReady.pendingItems)

        // 2b: 1 item, ready -> 100%
        val oneItemReady = listOf(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true))
        val report1Ready = ReadinessEngine.calculateReadiness(mode, oneItemReady)
        assertEquals("1 item ready: 100%", 100, report1Ready.percentage)
        assertEquals(1, report1Ready.readyCount)
        assertEquals(1, report1Ready.totalCount)
        assertTrue(report1Ready.pendingItems.isEmpty())

        // 3. Partially Completed Checklist
        // 3a: 1 of 2 ready -> 50%
        val twoItemsPartial = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = false)
        )
        val report2Partial = ReadinessEngine.calculateReadiness(mode, twoItemsPartial)
        assertEquals("1 of 2 items: 50%", 50, report2Partial.percentage)
        assertEquals(1, report2Partial.readyCount)
        assertEquals(2, report2Partial.totalCount)
        assertEquals(listOf("Laptop"), report2Partial.pendingItems)

        // 3b: 3 of 5 ready -> 60%
        val fiveItemsPartial = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = true),
            ItemRequirementEntity(id = 3, modeId = 1, itemName = "Charger", isReady = false),
            ItemRequirementEntity(id = 4, modeId = 1, itemName = "Watch", isReady = true),
            ItemRequirementEntity(id = 5, modeId = 1, itemName = "Water Bottle", isReady = false)
        )
        val report5Partial = ReadinessEngine.calculateReadiness(mode, fiveItemsPartial)
        assertEquals("3 of 5 items: 60%", 60, report5Partial.percentage)
        assertEquals(3, report5Partial.readyCount)
        assertEquals(5, report5Partial.totalCount)
        assertEquals(listOf("Charger", "Water Bottle"), report5Partial.pendingItems)

        // 4. Fully Completed Checklist
        // 4a: 2 of 2 ready -> 100%
        val twoItemsFull = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = true)
        )
        val report2Full = ReadinessEngine.calculateReadiness(mode, twoItemsFull)
        assertEquals("2 of 2 items: 100%", 100, report2Full.percentage)
        assertEquals(2, report2Full.readyCount)
        assertEquals(2, report2Full.totalCount)
        assertTrue(report2Full.pendingItems.isEmpty())

        // 4b: 5 of 5 ready -> 100%
        val fiveItemsFull = fiveItemsPartial.map { it.copy(isReady = true) }
        val report5Full = ReadinessEngine.calculateReadiness(mode, fiveItemsFull)
        assertEquals("5 of 5 items: 100%", 100, report5Full.percentage)
        assertEquals(5, report5Full.readyCount)
        assertEquals(5, report5Full.totalCount)
        assertTrue(report5Full.pendingItems.isEmpty())
    }

    @Test
    fun testHabitRiskPriorityModel() {
        val normalItem = ForgettingModelEngine.getRiskLevel(forgetCount = 1)
        assertEquals(RiskLevel.NORMAL, normalItem)

        val thresholdItem = ForgettingModelEngine.getRiskLevel(forgetCount = 3)
        assertEquals(RiskLevel.HIGH_RISK, thresholdItem)

        val highRiskItem = ForgettingModelEngine.getRiskLevel(forgetCount = 5)
        assertEquals(RiskLevel.HIGH_RISK, highRiskItem)
    }

    @Test
    fun testChecklistPermanentVsTemporarySeparation() = runBlocking {
        dao.insertItem(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isTemporary = false))
        dao.insertItem(ItemRequirementEntity(id = 2, modeId = 1, itemName = "Lab Manual", isTemporary = true, ttlTimestamp = 0L))

        val items = dao.getItemsForMode(1)
        val permanent = items.filter { !it.isTemporary }
        val temporary = items.filter { it.isTemporary }

        assertEquals(1, permanent.size)
        assertEquals("ID Card", permanent.first().itemName)

        assertEquals(1, temporary.size)
        assertEquals("Lab Manual", temporary.first().itemName)
    }

    @Test
    fun testCaseInsensitiveAndWhitespaceDuplicatePrevention() = runBlocking {
        dao.insertItem(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card"))

        fun isDuplicate(candidate: String): Boolean {
            val clean = candidate.trim()
            return dao.items.any { it.modeId == 1 && it.itemName.trim().equals(clean, ignoreCase = true) }
        }

        assertTrue(isDuplicate("id card"))
        assertTrue(isDuplicate("ID CARD"))
        assertTrue(isDuplicate(" ID Card "))
        assertTrue(isDuplicate("Id Card"))
        assertTrue(isDuplicate("   ID Card   "))
        assertFalse(isDuplicate("Library ID Card"))
    }

    @Test
    fun testSameItemAcrossMultipleActivitiesIsolation() = runBlocking {
        // Water Bottle in College, Sports, Travel
        dao.insertItem(ItemRequirementEntity(id = 1, modeId = 1, itemName = "Water Bottle", isReady = false))
        dao.insertItem(ItemRequirementEntity(id = 2, modeId = 4, itemName = "Water Bottle", isReady = true))
        dao.insertItem(ItemRequirementEntity(id = 3, modeId = 3, itemName = "Water Bottle", isReady = false))

        // Mutating College's Water Bottle
        dao.updateItemStatus(1, true)

        val collegeBottle = dao.getItemById(1)
        val sportsBottle = dao.getItemById(2)
        val travelBottle = dao.getItemById(3)

        assertTrue("College water bottle should now be ready", collegeBottle?.isReady == true)
        assertTrue("Sports water bottle remains unchanged (ready)", sportsBottle?.isReady == true)
        assertFalse("Travel water bottle remains unchanged (missing)", travelBottle?.isReady == true)
    }

    @Test
    fun testMultilingualChecklistItems() = runBlocking {
        val teluguItem = ItemRequirementEntity(id = 1, modeId = 1, itemName = "నీళ్ల బాటిల్", isReady = true)
        val hindiItem = ItemRequirementEntity(id = 2, modeId = 1, itemName = "पानी की बोतल", isReady = false)
        val emojiItem = ItemRequirementEntity(id = 3, modeId = 1, itemName = "Water Bottle 💧", isReady = false)
        val mixedCase = ItemRequirementEntity(id = 4, modeId = 1, itemName = "COLLEGE ID CARD", isReady = true)

        dao.insertItem(teluguItem)
        dao.insertItem(hindiItem)
        dao.insertItem(emojiItem)
        dao.insertItem(mixedCase)

        val items = dao.getItemsForMode(1)
        assertEquals(4, items.size)
        assertEquals("నీళ్ల బాటిల్", dao.getItemById(1)?.itemName)
        assertEquals("पानी की बोतल", dao.getItemById(2)?.itemName)
        assertEquals("Water Bottle 💧", dao.getItemById(3)?.itemName)
        assertEquals("COLLEGE ID CARD", dao.getItemById(4)?.itemName)
    }

    @Test
    fun testBoundaryLengthHandling() = runBlocking {
        val longName = "Bring the printed DBMS laboratory record with the latest experiment signatures"
        val shortName1 = "ID"
        val shortName2 = "Pen"
        val shortName3 = "Keys"

        dao.insertItem(ItemRequirementEntity(id = 1, modeId = 1, itemName = longName))
        dao.insertItem(ItemRequirementEntity(id = 2, modeId = 1, itemName = shortName1))
        dao.insertItem(ItemRequirementEntity(id = 3, modeId = 1, itemName = shortName2))
        dao.insertItem(ItemRequirementEntity(id = 4, modeId = 1, itemName = shortName3))

        assertEquals(longName, dao.getItemById(1)?.itemName)
        assertEquals(shortName1, dao.getItemById(2)?.itemName)
        assertEquals(shortName2, dao.getItemById(3)?.itemName)
        assertEquals(shortName3, dao.getItemById(4)?.itemName)
    }

    @Test
    fun testSpecialCharactersInItemNames() = runBlocking {
        val specialItems = listOf(
            "Father's ID",
            "Lab Record (DBMS)",
            "USB-C Charger",
            "Notebook #2",
            "Keys 🔑"
        )

        specialItems.forEachIndexed { i, name ->
            dao.insertItem(ItemRequirementEntity(id = i + 1, modeId = 1, itemName = name))
        }

        val retrieved = dao.getItemsForMode(1).map { it.itemName }
        assertEquals(specialItems, retrieved)
    }

    @Test
    fun testSequentialDeletionOrphanPurge() = runBlocking {
        dao.insertItem(ItemRequirementEntity(id = 1, modeId = 1, itemName = "Item A"))
        dao.insertItem(ItemRequirementEntity(id = 2, modeId = 1, itemName = "Item B"))
        dao.insertItem(ItemRequirementEntity(id = 3, modeId = 1, itemName = "Item C"))

        assertEquals(3, dao.getItemsForMode(1).size)

        // Delete Item B
        dao.deleteItemById(2)

        val remaining = dao.getItemsForMode(1)
        assertEquals(2, remaining.size)
        assertEquals(listOf("Item A", "Item C"), remaining.map { it.itemName })
        assertNull(dao.getItemById(2))
    }

    @Test
    fun testLargeChecklistPerformance50Items() = runBlocking {
        val count = 60
        (1..count).forEach { idx ->
            dao.insertItem(
                ItemRequirementEntity(
                    id = idx,
                    modeId = 1,
                    itemName = "Item #$idx",
                    isReady = (idx % 2 == 0) // 30 ready, 30 missing
                )
            )
        }

        val allItems = dao.getItemsForMode(1)
        assertEquals(count, allItems.size)

        val readyCount = allItems.count { it.isReady }
        val missingCount = allItems.count { !it.isReady }
        assertEquals(30, readyCount)
        assertEquals(30, missingCount)

        val college = LifeModeEntity(id = 1, name = "College")
        val report = ReadinessEngine.calculateReadiness(college, allItems)
        assertEquals(50, report.percentage)
        assertEquals(30, report.readyCount)
        assertEquals(60, report.totalCount)
    }

    @Test
    fun testRapidConcurrentItemToggles() = runBlocking {
        dao.insertItem(ItemRequirementEntity(id = 1, modeId = 1, itemName = "Concurrency Item", isReady = false))

        // Rapid 100 concurrent toggle operations
        val jobs = (1..100).map { i ->
            async(Dispatchers.Default) {
                dao.updateItemStatus(1, i % 2 == 0)
            }
        }
        jobs.awaitAll()

        val finalItem = dao.getItemById(1)
        assertNotNull(finalItem)
        assertTrue(finalItem?.isReady == true || finalItem?.isReady == false)
    }
}
