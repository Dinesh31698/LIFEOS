package com.lifeos.app

import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.engine.ReadinessEngine
import com.lifeos.app.service.AntiSpamDecision
import com.lifeos.app.service.AntiSpamNotificationEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LIFEOS Module 4: Contextual Readiness & Anti-Spam Aggregator Test Suite.
 * Validates:
 *  1. Accurate mathematical readiness percentage calculations including/excluding waived items.
 *  2. Suppression of alerts when readiness reaches 100%.
 *  3. Cooldown window deduplication blocking repeated alerts within 15 minutes.
 *  4. Generation of single consolidated departure strings upon simulated geofence exit.
 *  5. Geofence departure alert flow integration with room/in-memory DAO.
 */
class Module4AntiSpamReadinessTest {

    private lateinit var fakeDao: FakeLifeOsDao

    @Before
    fun setUp() {
        fakeDao = FakeLifeOsDao()
        AntiSpamNotificationEngine.resetCooldowns()
        seedTestData(fakeDao)
    }

    private fun seedTestData(dao: FakeLifeOsDao) {
        dao.modes.add(LifeModeEntity(id = 1, name = "College", iconRes = "school", isActive = true))
        dao.modes.add(LifeModeEntity(id = 2, name = "Gym", iconRes = "fitness", isActive = false))

        // Initial items for College Mode: 2 ready, 1 unready, 1 waived
        dao.items.add(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 2, modeId = 1, itemName = "DBMS Record", isReady = false, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 3, modeId = 1, itemName = "College Bag", isReady = true, isWaived = false))
        dao.items.add(ItemRequirementEntity(id = 4, modeId = 1, itemName = "Lab Apron", isReady = false, isWaived = true))
    }

    // =========================================================================
    // 1. Mathematical Readiness Aggregation Tests
    // =========================================================================

    @Test
    fun testReadinessCalculationExcludesWaivedItems() {
        val mode = fakeDao.modes.find { it.id == 1 }
        val allItems = fakeDao.items.filter { it.modeId == 1 }

        // In seeded data:
        // Active non-waived items = ID Card (ready), DBMS Record (not ready), College Bag (ready) -> 3 items
        // Ready count = 2
        // Waived items = Lab Apron (waived) -> 1 item
        // Expected percentage = (2 / 3) * 100 = 66.67% -> rounded to 67%
        val report = ReadinessEngine.calculateReadiness(mode, allItems)

        assertEquals("College", report.modeName)
        assertEquals(3, report.totalCount)
        assertEquals(2, report.readyCount)
        assertEquals(1, report.waivedCount)
        assertEquals(67, report.percentage)

        // Pending items must NOT contain the waived item "Lab Apron"
        assertEquals(1, report.pendingItems.size)
        assertEquals("DBMS Record", report.pendingItems.first())
        assertFalse(report.pendingItems.contains("Lab Apron"))

        // Consolidated summary notification text mentions waived item
        assertTrue(report.summaryNotification.contains("67% ready"))
        assertTrue(report.summaryNotification.contains("1 waived due to schedule changes"))
    }

    @Test
    fun testReadinessCalculationAllReadyReaches100Percent() {
        val mode = fakeDao.modes.find { it.id == 1 }
        val items = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true, isWaived = false),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "DBMS Record", isReady = true, isWaived = false),
            ItemRequirementEntity(id = 3, modeId = 1, itemName = "Lab Apron", isReady = false, isWaived = true) // Waived
        )

        val report = ReadinessEngine.calculateReadiness(mode, items)
        assertEquals(2, report.totalCount)
        assertEquals(2, report.readyCount)
        assertEquals(1, report.waivedCount)
        assertEquals(100, report.percentage)
        assertTrue(report.pendingItems.isEmpty())
        assertTrue(report.summaryNotification.contains("100% ready"))
    }

    @Test
    fun testReadinessCalculationEmptyReturnsSentinelAndAllWaivedDefaultsTo100Percent() {
        val mode = fakeDao.modes.find { it.id == 2 } // Gym
        // No items in Gym mode -> returns -1 ("No requirements configured") instead of misleading 100%
        val reportEmpty = ReadinessEngine.calculateReadiness(mode, emptyList())
        assertEquals(0, reportEmpty.totalCount)
        assertEquals(0, reportEmpty.readyCount)
        assertEquals(-1, reportEmpty.percentage)
        assertTrue(reportEmpty.summaryNotification.contains("No requirements configured"))

        // All items waived -> returns 100% because all requirements were legitimately waived
        val allWaivedItems = listOf(
            ItemRequirementEntity(id = 10, modeId = 2, itemName = "Shoes", isReady = false, isWaived = true),
            ItemRequirementEntity(id = 11, modeId = 2, itemName = "Towel", isReady = false, isWaived = true)
        )
        val reportAllWaived = ReadinessEngine.calculateReadiness(mode, allWaivedItems)
        assertEquals(0, reportAllWaived.totalCount)
        assertEquals(2, reportAllWaived.waivedCount)
        assertEquals(100, reportAllWaived.percentage)
    }

    // =========================================================================
    // 2. Anti-Spam 100% Readiness Suppression Tests
    // =========================================================================

    @Test
    fun testAlertSuppressedWhenReadinessIs100Percent() {
        val alertKey = "departure_mode_1"

        // At 100% readiness, alert must be suppressed with SUPPRESSED_100_PERCENT
        val result100 = AntiSpamNotificationEngine.checkAntiSpam(
            modeName = "College",
            readinessPercentage = 100,
            alertKey = alertKey
        )
        assertTrue("Alert must be suppressed when readiness is 100%", result100.isSuppressed)
        assertEquals(AntiSpamDecision.SUPPRESSED_100_PERCENT, result100.decision)
        assertTrue(result100.reason.contains("100% readiness"))

        // Also verify backwards-compatible shouldSuppressAlert
        val (suppressedLegacy, reasonLegacy) = AntiSpamNotificationEngine.shouldSuppressAlert(
            modeName = "College",
            readinessPercentage = 100,
            alertKey = alertKey
        )
        assertTrue(suppressedLegacy)
        assertTrue(reasonLegacy.contains("100% readiness"))
    }

    @Test
    fun testAlertAllowedWhenReadinessIsBelow100Percent() {
        val alertKey = "departure_mode_1"

        val result67 = AntiSpamNotificationEngine.checkAntiSpam(
            modeName = "College",
            readinessPercentage = 67,
            alertKey = alertKey
        )
        assertFalse("Alert must not be suppressed when readiness < 100%", result67.isSuppressed)
        assertEquals(AntiSpamDecision.ALLOWED, result67.decision)
        assertEquals("Allowed", result67.reason)
    }

    // =========================================================================
    // 3. Cooldown Window Deduplication Tests (15-Minute Rule)
    // =========================================================================

    @Test
    fun testCooldownWindowSuppressesDuplicatesWithin15Minutes() {
        val alertKey = "departure_mode_1"
        val baseTime = 1_000_000_000L

        // 1. First alert at baseTime -> ALLOWED
        val result1 = AntiSpamNotificationEngine.checkAntiSpam(
            modeName = "College",
            readinessPercentage = 67,
            alertKey = alertKey,
            currentTime = baseTime
        )
        assertEquals(AntiSpamDecision.ALLOWED, result1.decision)
        assertFalse(result1.isSuppressed)

        // Simulate posting alert: update timestamp
        AntiSpamNotificationEngine.lastAlertTimestamps[alertKey] = baseTime

        // 2. Second alert at baseTime + 5 minutes (300,000 ms) -> SUPPRESSED_COOLDOWN
        val time5Min = baseTime + (5 * 60 * 1000L)
        val result2 = AntiSpamNotificationEngine.checkAntiSpam(
            modeName = "College",
            readinessPercentage = 67,
            alertKey = alertKey,
            currentTime = time5Min
        )
        assertTrue("Duplicate alert within 5m must be suppressed", result2.isSuppressed)
        assertEquals(AntiSpamDecision.SUPPRESSED_COOLDOWN, result2.decision)
        assertTrue(result2.reason.contains("Cooldown active"))

        // 3. Third alert at baseTime + 14 minutes 59 seconds -> SUPPRESSED_COOLDOWN
        val time14Min59Sec = baseTime + (14 * 60 * 1000L) + 59_000L
        val result3 = AntiSpamNotificationEngine.checkAntiSpam(
            modeName = "College",
            readinessPercentage = 67,
            alertKey = alertKey,
            currentTime = time14Min59Sec
        )
        assertTrue("Alert right before 15m mark must still be suppressed", result3.isSuppressed)
        assertEquals(AntiSpamDecision.SUPPRESSED_COOLDOWN, result3.decision)

        // 4. Alert after 15 minutes elapsed -> ALLOWED
        val time15Min1Sec = baseTime + (15 * 60 * 1000L) + 1_000L
        val result4 = AntiSpamNotificationEngine.checkAntiSpam(
            modeName = "College",
            readinessPercentage = 67,
            alertKey = alertKey,
            currentTime = time15Min1Sec
        )
        assertFalse("Alert after 15m cooldown must be allowed", result4.isSuppressed)
        assertEquals(AntiSpamDecision.ALLOWED, result4.decision)
    }

    @Test
    fun testIndependentCooldownKeys() {
        val time = 1_000_000_000L
        val collegeKey = "departure_mode_1"
        val gymKey = "departure_mode_2"

        // Fire alert for College mode
        AntiSpamNotificationEngine.lastAlertTimestamps[collegeKey] = time

        // College is in cooldown
        val collegeCheck = AntiSpamNotificationEngine.checkAntiSpam("College", 50, collegeKey, time + 60_000L)
        assertEquals(AntiSpamDecision.SUPPRESSED_COOLDOWN, collegeCheck.decision)

        // Gym mode has no recorded alert -> ALLOWED
        val gymCheck = AntiSpamNotificationEngine.checkAntiSpam("Gym", 50, gymKey, time + 60_000L)
        assertEquals(AntiSpamDecision.ALLOWED, gymCheck.decision)
    }

    @Test
    fun testResetCooldownsClearsSuppressionHistory() {
        val alertKey = "departure_mode_1"
        val time = System.currentTimeMillis()
        AntiSpamNotificationEngine.lastAlertTimestamps[alertKey] = time

        // Cooldown active
        val checkBefore = AntiSpamNotificationEngine.checkAntiSpam("College", 67, alertKey, time + 10_000L)
        assertTrue(checkBefore.isSuppressed)

        // Reset cooldowns
        AntiSpamNotificationEngine.resetCooldowns()

        // Immediate check now allowed
        val checkAfter = AntiSpamNotificationEngine.checkAntiSpam("College", 67, alertKey, time + 10_000L)
        assertFalse(checkAfter.isSuppressed)
        assertEquals(AntiSpamDecision.ALLOWED, checkAfter.decision)
    }

    // =========================================================================
    // 4. Consolidated Departure Alert Generator Tests
    // =========================================================================

    @Test
    fun testConsolidatedDepartureAlertFormatExact() {
        // Example from spec: "Leaving for College? Readiness: 67%. Still missing: ID Card, DBMS Record."
        val text = AntiSpamNotificationEngine.consolidateDepartureAlert(
            modeName = "College",
            readinessPercentage = 67,
            pendingItems = listOf("ID Card", "DBMS Record"),
            waivedCount = 0
        )
        assertEquals("Leaving for College? Readiness: 67%. Still missing: ID Card, DBMS Record.", text)
    }

    @Test
    fun testConsolidatedDepartureAlertWithWaivedItems() {
        val textWithWaived = AntiSpamNotificationEngine.consolidateDepartureAlert(
            modeName = "College",
            readinessPercentage = 67,
            pendingItems = listOf("ID Card", "DBMS Record"),
            waivedCount = 1
        )
        assertEquals("Leaving for College? Readiness: 67%. Still missing: ID Card, DBMS Record (1 waived due to schedule changes).", textWithWaived)
    }

    @Test
    fun testConsolidatedDepartureAlertWhen100PercentReady() {
        val text100 = AntiSpamNotificationEngine.consolidateDepartureAlert(
            modeName = "College",
            readinessPercentage = 100,
            pendingItems = emptyList(),
            waivedCount = 0
        )
        assertEquals("Leaving for College? Readiness: 100%. All items are packed.", text100)
    }

    // =========================================================================
    // 5. Geofence Departure Workflow Simulation with DAO
    // =========================================================================

    @Test
    fun testSimulatedGeofenceDepartureGeneratesConsolidatedAlert() = runBlocking {
        // Setup: Mode 1 (College) has 2 items: ID Card (ready), DBMS Record (not ready)
        fakeDao.items.clear()
        fakeDao.items.add(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true, isWaived = false))
        fakeDao.items.add(ItemRequirementEntity(id = 2, modeId = 1, itemName = "DBMS Record", isReady = false, isWaived = false))
        fakeDao.items.add(ItemRequirementEntity(id = 3, modeId = 1, itemName = "Lab Apron", isReady = false, isWaived = false))

        val mode = fakeDao.getModeById(1)!!
        val items = fakeDao.getItemsForMode(1)

        // Evaluate readiness: 1 ready out of 3 = 33%
        val report = ReadinessEngine.calculateReadiness(mode, items)
        assertEquals(33, report.percentage)
        assertEquals(2, report.pendingItems.size)

        // Check Anti-Spam
        val alertKey = "departure_mode_1"
        val check = AntiSpamNotificationEngine.checkAntiSpam("College", report.percentage, alertKey)
        assertEquals(AntiSpamDecision.ALLOWED, check.decision)

        // Generate consolidated string
        val consolidated = AntiSpamNotificationEngine.consolidateDepartureAlert(
            modeName = "College",
            readinessPercentage = report.percentage,
            pendingItems = report.pendingItems,
            waivedCount = report.waivedCount
        )
        assertEquals("Leaving for College? Readiness: 33%. Still missing: DBMS Record, Lab Apron.", consolidated)
    }

    @Test
    fun testSimulatedGeofenceDepartureSuppressedAt100Percent() = runBlocking {
        // Setup: all items ready
        fakeDao.items.clear()
        fakeDao.items.add(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true, isWaived = false))
        fakeDao.items.add(ItemRequirementEntity(id = 2, modeId = 1, itemName = "DBMS Record", isReady = true, isWaived = false))

        val mode = fakeDao.getModeById(1)!!
        val items = fakeDao.getItemsForMode(1)

        val report = ReadinessEngine.calculateReadiness(mode, items)
        assertEquals(100, report.percentage)

        val check = AntiSpamNotificationEngine.checkAntiSpam("College", report.percentage, "departure_mode_1")
        assertEquals(AntiSpamDecision.SUPPRESSED_100_PERCENT, check.decision)
        assertTrue(check.isSuppressed)
    }
}
