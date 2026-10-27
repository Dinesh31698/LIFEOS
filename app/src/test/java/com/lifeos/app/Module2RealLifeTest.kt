package com.lifeos.app

import com.lifeos.app.data.EventEntity
import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.engine.*
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import com.lifeos.app.service.AntiSpamDecision
import com.lifeos.app.service.AntiSpamNotificationEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * LIFEOS Real-Life Test & Hardening Suite — Module 2: Dynamic Readiness & Context Awareness
 *
 * Real-Life Test Scenario:
 *  Simulating a geofence departure event towards College with a hybrid checklist
 *  (Permanent Items: ID Card, Laptop, Charger, Water Bottle, Watch + Temporary DBMS Record).
 *
 * Features Covered:
 *  - Context Recognition & Probabilistic Scoring (#1, #2, #3, #4, #5, #6)
 *  - Dynamic Hybrid Checklist Aggregation (#32, #33, #34, #35)
 *  - BLE Device Proximity Auto-Ready & Probabilistic Warning (#49, #50, #51)
 *  - Anti-Spam Consolidated Departure Notification & 100% Suppression (#62, #63, #64)
 */
class Module2RealLifeTest {

    private lateinit var fakeDao: FakeLifeOsDao

    private val baseTimestamp = System.currentTimeMillis()

    @Before
    fun setUp() {
        SafeModeManager.setSafeMode(false)
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, true)

        AntiSpamNotificationEngine.resetCooldowns()
        BatteryBudgetManager.setSimulatedBattery(100, isPowerSave = false)

        fakeDao = FakeLifeOsDao()
        seedTestData(fakeDao)
    }

    @After
    fun tearDown() {
        SafeModeManager.setSafeMode(false)
        AntiSpamNotificationEngine.resetCooldowns()
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, false)
    }

    private fun seedTestData(dao: FakeLifeOsDao) = runBlocking {
        // Modes
        dao.insertLifeMode(LifeModeEntity(id = 1, name = "College", iconRes = "school", isActive = false, priority = 1))
        dao.insertLifeMode(LifeModeEntity(id = 2, name = "Gym", iconRes = "fitness", isActive = true, priority = 2))

        // Scheduled Event for College mode
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        dao.insertEvent(
            EventEntity(
                id = 10,
                title = "Data Structures & DBMS Lab",
                startTime = cal.timeInMillis,
                endTime = cal.timeInMillis + 7200000L,
                location = "Room 301",
                associatedModeId = 1,
                isGhostMode = false,
                confidence = 1.0f,
                evidenceText = "Semester Timetable"
            )
        )

        // Permanent Items (isTemporary = false)
        val permanentItems = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = false, isTemporary = false),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "Laptop", isReady = false, isTemporary = false),
            ItemRequirementEntity(id = 3, modeId = 1, itemName = "Charger", isReady = false, isTemporary = false),
            ItemRequirementEntity(id = 4, modeId = 1, itemName = "Water Bottle", isReady = false, isTemporary = false),
            ItemRequirementEntity(id = 5, modeId = 1, itemName = "Watch", isReady = false, isTemporary = false)
        )
        permanentItems.forEach { dao.insertItem(it) }

        // Temporary Requirement (DBMS Record: isTemporary = true, valid TTL 24h)
        val temporaryItem = ItemRequirementEntity(
            id = 6,
            modeId = 1,
            itemName = "DBMS Record",
            isReady = false,
            isTemporary = true,
            ttlTimestamp = baseTimestamp + (24 * 3600 * 1000L),
            confidenceScore = 1.0f,
            sourceEvidence = "Ingested from Teluglish notice"
        )
        dao.insertItem(temporaryItem)

        // Expired Temporary Item (should be excluded)
        val expiredItem = ItemRequirementEntity(
            id = 7,
            modeId = 1,
            itemName = "Previous Seminar Pass",
            isReady = false,
            isTemporary = true,
            ttlTimestamp = baseTimestamp - 3600000L, // 1 hour ago
            confidenceScore = 1.0f
        )
        dao.insertItem(expiredItem)

        // Ghost Item (should be excluded from active checklist)
        val ghostItem = ItemRequirementEntity(
            id = 8,
            modeId = 1,
            itemName = "Optional Workshop Notes",
            isReady = false,
            isGhostMode = true
        )
        dao.insertItem(ghostItem)
    }

    // =========================================================================
    // 1. Context Recognition & Probabilistic Scoring (Features #1 - #6)
    // =========================================================================

    @Test
    fun testContextRecognitionFusesSignalsAndActivatesCollegeMode() = runBlocking {
        // Morning college hour (9:30 AM)
        val morningCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 30)
        }

        // Trigger context evaluation with Geofence EXIT from Home toward College
        val evaluation = ContextRecognitionEngine.evaluateAndSwitchMode(
            dao = fakeDao,
            isGeofenceExit = true,
            targetModeHint = "College",
            currentTimestamp = morningCal.timeInMillis
        )

        // 1a. Validate multi-signal fusion
        assertEquals(0.40f, evaluation.locationConfidence, 0.01f)
        assertEquals(0.30f, evaluation.timeConfidence, 0.01f)
        assertEquals(0.30f, evaluation.calendarConfidence, 0.01f)

        // 1b. Total confidence must be >= 0.80 (0.40 + 0.30 + 0.30 = 1.00)
        assertTrue("Context confidence must exceed 0.80", evaluation.totalConfidence >= 0.80f)
        assertEquals(1.00f, evaluation.totalConfidence, 0.01f)
        assertEquals(1, evaluation.inferredModeId)
        assertEquals("College", evaluation.inferredModeName)
        assertEquals(3, evaluation.matchedSignals.size)

        // 1c. Verify active mode was switched from Gym (id=2) to College (id=1) in DB
        val activeMode = fakeDao.getActiveModeFlow().first()
        assertNotNull(activeMode)
        assertEquals("College", activeMode?.name)
        assertEquals(1, activeMode?.id)
        assertTrue(activeMode?.isActive == true)
    }

    @Test
    fun testContextRecognitionDropsConfidenceWhenPermissionsDisabled() = runBlocking {
        // Disable location permission in Privacy Firewall
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, false)

        val morningCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 30)
        }

        val evaluation = ContextRecognitionEngine.evaluateContext(
            dao = fakeDao,
            isGeofenceExit = true,
            targetModeHint = "College",
            currentTimestamp = morningCal.timeInMillis
        )

        // Location score must be 0.0f because channel is disabled
        assertEquals(0.00f, evaluation.locationConfidence, 0.01f)
        assertEquals(0.60f, evaluation.totalConfidence, 0.01f)
        // Total confidence (0.60) is below threshold 0.80
        assertTrue("Confidence must be below threshold when location is disabled", evaluation.totalConfidence < 0.80f)
    }

    // =========================================================================
    // 2. Hybrid Checklist Aggregation (Features #32 - #35)
    // =========================================================================

    @Test
    fun testHybridChecklistMergesPermanentAndTemporaryItemsExcludingExpired() = runBlocking {
        // Query active hybrid checklist
        val hybridItems = ReadinessEngine.getHybridChecklist(fakeDao, modeId = 1, currentTime = baseTimestamp)

        // Must contain exactly 6 items:
        // 5 Permanent (ID Card, Laptop, Charger, Water Bottle, Watch) + 1 Temporary (DBMS Record)
        assertEquals(6, hybridItems.size)

        val itemNames = hybridItems.map { it.itemName }
        assertTrue("Must contain permanent ID Card", itemNames.contains("ID Card"))
        assertTrue("Must contain permanent Laptop", itemNames.contains("Laptop"))
        assertTrue("Must contain permanent Charger", itemNames.contains("Charger"))
        assertTrue("Must contain permanent Water Bottle", itemNames.contains("Water Bottle"))
        assertTrue("Must contain permanent Watch", itemNames.contains("Watch"))
        assertTrue("Must contain temporary DBMS Record", itemNames.contains("DBMS Record"))

        // Expired temporary item and ghost item must be excluded
        assertFalse("Expired temporary item must be excluded", itemNames.contains("Previous Seminar Pass"))
        assertFalse("Ghost item must be excluded", itemNames.contains("Optional Workshop Notes"))

        // Baseline readiness: 0 of 6 ready -> 0%
        val collegeMode = fakeDao.getModeById(1)
        val initialReport = ReadinessEngine.calculateReadiness(collegeMode, hybridItems, currentTime = baseTimestamp)
        assertEquals(0, initialReport.readyCount)
        assertEquals(6, initialReport.totalCount)
        assertEquals(0, initialReport.percentage)
        assertEquals(6, initialReport.pendingItems.size)
    }

    // =========================================================================
    // 3. BLE Device Proximity Check (Features #49 - #51)
    // =========================================================================

    @Test
    fun testBleDeviceProximityMarksLaptopReadyWhenNearby() = runBlocking {
        // Ensure Laptop is initially unready
        val initialLaptop = fakeDao.getItemsByNameLike("Laptop").first()
        assertFalse(initialLaptop.isReady)

        // 3a. Simulate strong BLE signal (RSSI = -65 dBm >= -70 dBm threshold)
        val syncResult = DevicePresenceScanner.syncDeviceWithChecklist(
            dao = fakeDao,
            deviceName = "Laptop Beacon",
            rssi = -65,
            targetItemKeyword = "Laptop"
        )

        // Confidence must be HIGH_CONFIDENCE
        assertEquals(PresenceConfidence.HIGH_CONFIDENCE, syncResult.confidence)
        assertNull("Departure warning must be null for high confidence signal", syncResult.departureWarning)
        assertTrue(syncResult.formattedMessage.contains("Strong signal"))

        // Item "Laptop" must be automatically marked isReady = true in Room DB
        val updatedLaptop = fakeDao.getItemsByNameLike("Laptop").first()
        assertTrue("Laptop must be automatically marked isReady=true", updatedLaptop.isReady)

        // Readiness should increase from 0/6 to 1/6 (17%)
        val hybridItems = ReadinessEngine.getHybridChecklist(fakeDao, modeId = 1, currentTime = baseTimestamp)
        val report = ReadinessEngine.calculateReadiness(fakeDao.getModeById(1), hybridItems, currentTime = baseTimestamp)
        assertEquals(1, report.readyCount)
        assertEquals(6, report.totalCount)
        assertEquals(17, report.percentage)
        assertFalse(report.pendingItems.contains("Laptop"))
    }

    @Test
    fun testBleDeviceProximitySignalAbsentGeneratesProbabilisticWarning() = runBlocking {
        // 3b. Simulate absent BLE signal (RSSI = null)
        val syncResult = DevicePresenceScanner.syncDeviceWithChecklist(
            dao = fakeDao,
            deviceName = "Laptop Beacon",
            rssi = null,
            targetItemKeyword = "Laptop"
        )

        assertEquals(PresenceConfidence.UNKNOWN, syncResult.confidence)
        assertNotNull(syncResult.departureWarning)
        assertTrue(
            "Must use probabilistic phrasing: 'does not appear to be nearby'",
            syncResult.departureWarning!!.contains("does not appear to be nearby")
        )
    }

    // =========================================================================
    // 4. Anti-Spam Consolidated Notification (Features #62 - #64)
    // =========================================================================

    @Test
    fun testAntiSpamConsolidatesMultipleMissingItemsIntoOneDepartureAlert() = runBlocking {
        val collegeMode = fakeDao.getModeById(1)
        val hybridItems = ReadinessEngine.getHybridChecklist(fakeDao, modeId = 1, currentTime = baseTimestamp)

        // 4a. Verify multiple missing items produce ONE consolidated alert
        val report = ReadinessEngine.calculateReadiness(collegeMode, hybridItems, currentTime = baseTimestamp)
        assertEquals(6, report.pendingItems.size)

        val consolidatedText = AntiSpamNotificationEngine.consolidateDepartureAlert(report)
        assertTrue("Summary must mention mode name College", consolidatedText.contains("College"))
        assertTrue("Summary must state readiness score", consolidatedText.contains("0%"))
        assertTrue("Summary must list missing items", consolidatedText.contains("Still missing:"))
        assertTrue("Must include temporary DBMS Record in missing items", consolidatedText.contains("DBMS Record"))

        // 4b. Check anti-spam grouping key GEOFENCE_EXIT_COLLEGE
        val alertKey = "GEOFENCE_EXIT_COLLEGE"
        val antiSpamCheck = AntiSpamNotificationEngine.checkAntiSpam(
            modeName = "College",
            readinessPercentage = report.percentage,
            alertKey = alertKey,
            currentTime = baseTimestamp
        )

        assertEquals(AntiSpamDecision.ALLOWED, antiSpamCheck.decision)
        assertFalse("First departure alert must be allowed", antiSpamCheck.isSuppressed)

        // Record alert timestamp
        AntiSpamNotificationEngine.lastAlertTimestamps[alertKey] = baseTimestamp

        // 4c. Verify cooldown window (15 minutes = 900,000 ms) suppresses duplicate departure alerts
        val fiveMinutesLater = baseTimestamp + (5 * 60 * 1000L)
        val duplicateCheck = AntiSpamNotificationEngine.checkAntiSpam(
            modeName = "College",
            readinessPercentage = report.percentage,
            alertKey = alertKey,
            currentTime = fiveMinutesLater
        )

        assertEquals(AntiSpamDecision.SUPPRESSED_COOLDOWN, duplicateCheck.decision)
        assertTrue("Duplicate alert within 15 mins must be suppressed", duplicateCheck.isSuppressed)
        assertTrue(duplicateCheck.reason.contains("Cooldown active"))
    }

    @Test
    fun testAntiSpamSuppressesAlertAt100PercentReadiness() = runBlocking {
        // Mark all items as ready
        val hybridItems = ReadinessEngine.getHybridChecklist(fakeDao, modeId = 1, currentTime = baseTimestamp)
        hybridItems.forEach { fakeDao.updateItemStatus(it.id, isReady = true) }

        val readyItems = ReadinessEngine.getHybridChecklist(fakeDao, modeId = 1, currentTime = baseTimestamp)
        val report = ReadinessEngine.calculateReadiness(fakeDao.getModeById(1), readyItems, currentTime = baseTimestamp)

        assertEquals(6, report.readyCount)
        assertEquals(6, report.totalCount)
        assertEquals(100, report.percentage)
        assertTrue(report.pendingItems.isEmpty())

        // 4d. Anti-spam check at 100% readiness must suppress notification
        val alertKey = "GEOFENCE_EXIT_COLLEGE"
        val checkResult = AntiSpamNotificationEngine.checkAntiSpam(
            modeName = "College",
            readinessPercentage = report.percentage,
            alertKey = alertKey,
            currentTime = baseTimestamp
        )

        assertEquals(AntiSpamDecision.SUPPRESSED_100_PERCENT, checkResult.decision)
        assertTrue("Alert must be suppressed when user is 100% ready", checkResult.isSuppressed)
        assertTrue(checkResult.reason.contains("100% readiness"))
    }
}
