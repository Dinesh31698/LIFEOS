package com.lifeos.app

import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.engine.*
import com.lifeos.app.security.DataMinimizer
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LIFEOS Real-Life Test & Hardening Suite — Module 6: Privacy Dashboard, Safe Mode Isolation & Release Build
 *
 * Real-Life Test Scenario:
 *  Toggling off Privacy channels, activating Emergency Safe Mode, verifying PII data sanitization,
 *  and validating release integrity.
 *
 * Features Validated:
 *  - Permission Firewall Channel Enforcement (Location & Notification Ingestion) (#14, #43, #45, #46)
 *  - Safe Mode Emergency Mutation Kill-Switch & Ghost Mode Routing (#47, #48, #54, #55)
 *  - Data Minimizer PII Sanitization (Phone & Email Redaction) (#68, #69)
 *  - Top Safe Mode Status Warning Banner (#70, #71, #72)
 */
class Module6RealLifeTest {

    private lateinit var fakeDao: FakeLifeOsDao
    private lateinit var decisionGate: DecisionGate

    @Before
    fun setUp() {
        // Reset defaults
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, false)
        SafeModeManager.isSafeModeActive = false

        fakeDao = FakeLifeOsDao()
        seedTestData(fakeDao)
        decisionGate = DecisionGate()
    }

    @After
    fun tearDown() {
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        SafeModeManager.isSafeModeActive = false
    }

    private fun seedTestData(dao: FakeLifeOsDao) = runBlocking {
        dao.insertLifeMode(LifeModeEntity(id = 1, name = "College", iconRes = "school", isActive = true, priority = 1))
        dao.insertLifeMode(LifeModeEntity(id = 2, name = "Gym", iconRes = "gym", isActive = false, priority = 2))

        dao.insertItem(ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true, isWaived = false))
        dao.insertItem(ItemRequirementEntity(id = 2, modeId = 1, itemName = "DBMS Record", isReady = false, isWaived = false))
        dao.insertItem(ItemRequirementEntity(id = 3, modeId = 1, itemName = "Laptop", isReady = false, isWaived = false))
    }

    // =========================================================================
    // 1. Permission Firewall Channel Enforcement Tests
    // =========================================================================

    @Test
    fun testPermissionFirewallTogglesBlockLocationAndNotificationIngestion() {
        // 1a. Toggle off Location
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, false)
        assertFalse("LOCATION channel must be blocked", PermissionFirewall.isSourceAuthorized(SourceType.LOCATION))

        // 1b. Toggle off Notification Ingestion
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, false)
        assertFalse("NOTIFICATION_INGESTION channel must be blocked", PermissionFirewall.isSourceAuthorized(SourceType.NOTIFICATION_INGESTION))

        // 1c. Verify BLE_SCANNING remains unaffected unless toggled
        assertTrue("BLE_SCANNING must remain true", PermissionFirewall.isSourceAuthorized(SourceType.BLE_SCANNING))

        // 1d. Re-enable Location and verify restoration
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        assertTrue("LOCATION channel must be authorized upon re-enabling", PermissionFirewall.isSourceAuthorized(SourceType.LOCATION))

        // 1e. Re-enable Notification Ingestion and verify restoration
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        assertTrue("NOTIFICATION_INGESTION channel must be authorized upon re-enabling", PermissionFirewall.isSourceAuthorized(SourceType.NOTIFICATION_INGESTION))
    }

    // =========================================================================
    // 2. Safe Mode Mutation Kill-Switch & Ghost Mode Routing Tests
    // =========================================================================

    @Test
    fun testSafeModeMutationKillSwitchForcesGhostModeOnHighConfidenceTeluglish() = runBlocking {
        // Step A: Enable Safe Mode
        SafeModeManager.isSafeModeActive = true
        assertTrue("Safe Mode must be active", SafeModeManager.isSafeModeActive)
        assertFalse("Plan mutations must be suppressed under Safe Mode", SafeModeManager.canMutatePlans())
        assertFalse("Auto-promotions must be suppressed under Safe Mode", SafeModeManager.canAutoPromoteGhostItems())

        // Step B: Send high-confidence Teluglish notice ("Repu lab cancel aindi")
        val text = "Repu lab cancel aindi"
        val payload = LightweightEventFilter.filter(text, sourcePackage = "com.whatsapp")

        // Step C: Route through DecisionGate
        val result = decisionGate.processPayload(payload, fakeDao)

        // Step D: Verify result is forced to GhostModeRouted rather than AutoCommitted/ConsequenceApplied
        assertTrue("Safe Mode must force item into GhostModeRouted", result is DecisionResult.GhostModeRouted)
        val ghostResult = result as DecisionResult.GhostModeRouted

        // Step E: Verify Room DB plan was NOT automatically mutated / waived
        val dbmsRecord = fakeDao.items.find { it.itemName == "DBMS Record" }!!
        assertFalse("DBMS Record must NOT be automatically waived when Safe Mode is active", dbmsRecord.isWaived)

        // Step F: Verify the ghost item was saved in Room DB with isGhostMode = true
        val ghostItemInDb = fakeDao.items.find { it.id == ghostResult.item.id }
        assertNotNull(ghostItemInDb)
        assertTrue(ghostItemInDb!!.isGhostMode)
    }

    // =========================================================================
    // 3. Data Minimizer PII Sanitization Tests
    // =========================================================================

    @Test
    fun testDataMinimizerRedactsPhoneAndEmailFromRawPayload() {
        val rawPiiMessage = "Contact prof at 9876543210 or prof@college.edu regarding DBMS record"

        // Step A: Strip PII
        val sanitized = DataMinimizer.stripPii(rawPiiMessage)

        // Step B: Verify phone number is redacted
        assertFalse("Raw phone number must not appear in sanitized text", sanitized.contains("9876543210"))
        assertTrue("Sanitized text must contain [PHONE REDACTED]", sanitized.contains("[PHONE REDACTED]"))

        // Step C: Verify email address is redacted
        assertFalse("Raw email address must not appear in sanitized text", sanitized.contains("prof@college.edu"))
        assertTrue("Sanitized text must contain [EMAIL REDACTED]", sanitized.contains("[EMAIL REDACTED]"))

        // Step D: Verify DataMinimizer.sanitizeRawPayload wraps the minimized format
        val finalPayload = DataMinimizer.sanitizeRawPayload(rawPiiMessage, "DBMS Record", "Action")
        assertTrue(finalPayload.contains("[Sanitized:"))
        assertFalse(finalPayload.contains("9876543210"))
        assertFalse(finalPayload.contains("prof@college.edu"))
    }

    // =========================================================================
    // 4. Safe Mode UI Banner State & Indicator Tests
    // =========================================================================

    @Test
    fun testSafeModeStateFlowControlsWarningBanner() {
        SafeModeManager.isSafeModeActive = false
        assertFalse(SafeModeManager.isSafeModeEnabled.value)

        // Activate Safe Mode
        SafeModeManager.isSafeModeActive = true
        assertTrue(SafeModeManager.isSafeModeEnabled.value)

        // Toggle Safe Mode
        SafeModeManager.toggleSafeMode()
        assertFalse(SafeModeManager.isSafeModeEnabled.value)
    }

    // =========================================================================
    // 5. Normal Operation Resumed when Safe Mode Disabled
    // =========================================================================

    @Test
    fun testNormalOperationResumesWhenSafeModeDisabled() = runBlocking {
        SafeModeManager.isSafeModeActive = false

        val text = "Repu DBMS lab cancel aindi"
        val payload = LightweightEventFilter.filter(text, sourcePackage = "com.whatsapp")
        val result = decisionGate.processPayload(payload, fakeDao)

        // Without Safe Mode, high confidence notice applies consequence
        assertTrue("Without Safe Mode, consequence must be applied or auto-committed",
            result is DecisionResult.ConsequenceApplied || result is DecisionResult.AutoCommitted)
    }
}
