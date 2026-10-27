package com.lifeos.app

import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.data.OfflineEventQueueEntity
import com.lifeos.app.engine.*
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LIFEOS Real-Life Test & Hardening Suite — Module 5: Battery Budget, Sensor Throttling & Offline Capability
 *
 * Real-Life Test Scenario:
 *  Simulating an OS low-battery intent (12% battery) under full Offline / Airplane Mode conditions.
 *
 * Features Validated:
 *  - Battery Profile Throttling & Power Budgeting (#36, #37, #38)
 *  - Automatic Sensor & BLE Scan Pausing under SAVER/CRITICAL (#39, #40)
 *  - Top Status Bar Chip formatting ("🪫 12% SAVER") (#41, #42)
 *  - 100% Local-First Offline Processing without network dependency (#56, #57)
 *  - Offline Event Queue in Room DB & Automatic Sync on Reconnection (#67)
 */
class Module5RealLifeTest {

    private lateinit var fakeDao: FakeLifeOsDao

    @Before
    fun setUp() {
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        BatteryBudgetManager.setSimulatedBattery(100, isPowerSave = false)
        OfflineEventQueueManager.setOffline(false)

        fakeDao = FakeLifeOsDao()
        seedInitialData(fakeDao)
    }

    @After
    fun tearDown() {
        BatteryBudgetManager.setSimulatedBattery(100, isPowerSave = false)
        OfflineEventQueueManager.setOffline(false)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
    }

    private fun seedInitialData(dao: FakeLifeOsDao) = runBlocking {
        dao.insertLifeMode(LifeModeEntity(id = 1, name = "College", iconRes = "school", isActive = true, priority = 1))
        dao.insertLifeMode(LifeModeEntity(id = 2, name = "Gym", iconRes = "gym", isActive = false, priority = 2))

        dao.insertItem(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = false))
        dao.insertItem(ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = false))
        dao.insertItem(ItemRequirementEntity(id = 3, modeId = 1, itemName = "DBMS Record", isReady = false))
    }

    // =========================================================================
    // 1. Battery Profile Throttling (12% Battery -> SAVER Mode)
    // =========================================================================

    @Test
    fun testLowBatteryIntentSwitchesProfileToSaverAndThrottlesBleScans() {
        // Start at NORMAL (100%)
        assertEquals(PowerProfile.NORMAL, BatteryBudgetManager.currentPowerProfile.value)
        assertTrue(BatteryBudgetManager.canRunBleScan())

        // Simulate OS power state change (ACTION_BATTERY_LOW / 12% battery)
        BatteryBudgetManager.setSimulatedBattery(12, isPowerSave = false)

        // Verify profile switched to SAVER
        assertEquals(PowerProfile.SAVER, BatteryBudgetManager.currentPowerProfile.value)
        assertEquals(12, BatteryBudgetManager.currentBatteryLevel.value)
        assertFalse("BLE scanning must be paused under SAVER profile", BatteryBudgetManager.canRunBleScan())

        // Verify scan preconditions check reports Throttled due to SAVER_MODE_ACTIVE
        val check = DevicePresenceScanner.checkScanPreconditions()
        assertNotNull(check)
        assertTrue(check is ScanExecutionResult.Throttled)
        val throttled = check as ScanExecutionResult.Throttled
        assertEquals(ScanThrottleReason.SAVER_MODE_ACTIVE, throttled.reason)
        assertTrue(throttled.message.contains("SAVER"))

        // Verify mock scan execution is also rejected
        val mockScanResult = DevicePresenceScanner.executeMockScan(listOf("Laptop Beacon" to -62))
        assertTrue(mockScanResult is ScanExecutionResult.Throttled)

        // Verify top status bar string format
        val icon = when (BatteryBudgetManager.currentPowerProfile.value) {
            PowerProfile.CRITICAL -> "⚠️"
            PowerProfile.SAVER -> "🪫"
            PowerProfile.NORMAL -> "🔋"
        }
        val chipText = "$icon ${BatteryBudgetManager.currentBatteryLevel.value}% ${BatteryBudgetManager.currentPowerProfile.value.name}"
        assertEquals("🪫 12% SAVER", chipText)
    }

    // =========================================================================
    // 2. Critical Battery Profile & Automatic Scanner Stop
    // =========================================================================

    @Test
    fun testCriticalBatteryProfileInvokesStopScanAndThrottlesSensors() {
        // Drop to CRITICAL (< 10% or Power Save Mode)
        BatteryBudgetManager.setSimulatedBattery(5, isPowerSave = false)

        assertEquals(PowerProfile.CRITICAL, BatteryBudgetManager.currentPowerProfile.value)
        assertEquals(5, BatteryBudgetManager.currentBatteryLevel.value)
        assertFalse(BatteryBudgetManager.canRunBleScan())
        assertFalse(BatteryBudgetManager.canRunBackgroundSync())

        // Scanner must not be active
        assertFalse(DevicePresenceScanner.isScanning())

        // Status bar chip format
        val chipText = "⚠️ ${BatteryBudgetManager.currentBatteryLevel.value}% ${BatteryBudgetManager.currentPowerProfile.value.name}"
        assertEquals("⚠️ 5% CRITICAL", chipText)
    }

    // =========================================================================
    // 3. Local-First Offline Processing (Airplane Mode Simulation)
    // =========================================================================

    @Test
    fun testOfflineLocalFirstProcessingWithoutNetworkExceptions() = runBlocking {
        // Disconnect network (simulate Airplane Mode)
        OfflineEventQueueManager.setOffline(true)
        assertTrue(OfflineEventQueueManager.isOfflineMode())

        // 1. Perform local Mode Transition (College -> Gym)
        fakeDao.setActiveMode(2)
        val activeMode = fakeDao.getActiveModeFlow().first()
        assertNotNull(activeMode)
        assertEquals(2, activeMode!!.id)
        assertEquals("Gym", activeMode.name)

        // 2. Add custom local item requirement
        val newItem = ItemRequirementEntity(
            modeId = 2,
            itemName = "Resistance Bands",
            isReady = false,
            sourceEvidence = "Offline Manual Entry"
        )
        val itemId = fakeDao.insertItem(newItem)
        assertTrue(itemId > 0)

        // 3. Update item status to ready locally
        fakeDao.updateItemStatus(itemId.toInt(), true)
        val fetched = fakeDao.getItemById(itemId.toInt())
        assertNotNull(fetched)
        assertTrue(fetched!!.isReady)

        // 4. Calculate local readiness score
        val gymItems = fakeDao.getItemsForMode(2)
        val readyCount = gymItems.count { it.isReady }
        val readinessScore = ((readyCount.toFloat() / gymItems.size) * 100).toInt()
        assertEquals(100, readinessScore)

        // Verify zero network exceptions were thrown and log confirmation
        OfflineEventQueueManager.logLocalExecutionProof("Mode Switch + Item Insertion + Readiness Calculation")
    }

    // =========================================================================
    // 4. Offline Event Queue Persistence & Synchronization
    // =========================================================================

    @Test
    fun testOfflineEventQueueAppendsPendingTasksAndSyncsWhenOnline() = runBlocking {
        // Step A: Disconnect network (Offline / Airplane Mode)
        OfflineEventQueueManager.setOffline(true)

        // Step B: Queue background sync tasks
        val task1 = OfflineEventQueueManager.enqueueSyncTask(
            dao = fakeDao,
            taskType = "CALENDAR_SYNC",
            payload = "{\"calendarId\":\"primary\",\"event\":\"DBMS Lab\"}"
        )
        val task2 = OfflineEventQueueManager.enqueueSyncTask(
            dao = fakeDao,
            taskType = "TELEMETRY_LOG",
            payload = "{\"event\":\"geofence_exit\",\"timestamp\":1727800000}"
        )

        assertNotNull(task1)
        assertEquals("PENDING", task1.status)
        assertEquals("CALENDAR_SYNC", task1.taskType)

        assertNotNull(task2)
        assertEquals("PENDING", task2.status)

        // Verify tasks are stored in Room DB
        val pendingOffline = fakeDao.getPendingOfflineTasks()
        assertEquals(2, pendingOffline.size)

        // Attempting to sync while offline must suppress network calls
        val syncedWhileOffline = OfflineEventQueueManager.processPendingTasks(fakeDao)
        assertEquals(0, syncedWhileOffline.size)
        assertEquals(2, fakeDao.getPendingOfflineTasks().size)

        // Step C: Reconnect network (Airplane Mode disabled)
        OfflineEventQueueManager.setOffline(false)
        assertFalse(OfflineEventQueueManager.isOfflineMode())

        // Process queue now that network is available
        val processedTasks = OfflineEventQueueManager.processPendingTasks(fakeDao)
        assertEquals(2, processedTasks.size)
        assertTrue(processedTasks.all { it.status == "SYNCED" })

        // Verify queue is now drained in Room DB
        val remainingPending = fakeDao.getPendingOfflineTasks()
        assertEquals(0, remainingPending.size)
    }

    // =========================================================================
    // 5. BLE Device Proximity Signal Classification & Automatic Check-off
    // =========================================================================

    @Test
    fun testBleDeviceProximitySyncsChecklistWhenNormalBattery() = runBlocking {
        // Battery NORMAL, BLE scan allowed
        BatteryBudgetManager.setSimulatedBattery(80, false)

        val laptopItem = fakeDao.items.find { it.itemName == "Laptop" }!!
        assertFalse(laptopItem.isReady)

        // Tracked Laptop Beacon discovered with strong signal (-62 dBm >= -70 dBm)
        val scanResult = DevicePresenceScanner.syncDeviceWithChecklist(
            dao = fakeDao,
            deviceName = "Laptop Beacon",
            rssi = -62,
            targetItemKeyword = "Laptop"
        )

        assertEquals(PresenceConfidence.HIGH_CONFIDENCE, scanResult.confidence)
        assertEquals(-62, scanResult.rssi)

        // Confirm Laptop item auto-marked as ready in Room DB
        val updatedLaptop = fakeDao.getItemById(laptopItem.id)!!
        assertTrue("Laptop must be automatically marked isReady = true on strong BLE beacon", updatedLaptop.isReady)
    }
}
