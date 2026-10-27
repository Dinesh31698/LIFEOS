package com.lifeos.app

import com.lifeos.app.engine.DecisionGate
import com.lifeos.app.engine.SafeModeManager
import com.lifeos.app.security.DataMinimizer
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Stage7UnitTest {

    @Before
    fun setUp() {
        // Reset Safe Mode to default (false)
        SafeModeManager.setSafeMode(false)
        // Reset Firewall toggles to default
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, false)
    }

    @After
    fun tearDown() {
        SafeModeManager.setSafeMode(false)
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
    }

    // =========================================================================
    // 1. Permission Firewall Security Gate Tests
    // =========================================================================

    @Test
    fun testDefaultFirewallChannelStates() {
        assertTrue("Location should be enabled by default", PermissionFirewall.isSourceAuthorized(SourceType.LOCATION))
        assertTrue("Notification ingestion should be enabled by default", PermissionFirewall.isSourceAuthorized(SourceType.NOTIFICATION_INGESTION))
        assertTrue("BLE scanning should be enabled by default", PermissionFirewall.isSourceAuthorized(SourceType.BLE_SCANNING))
        assertFalse("Calendar sync should be disabled by default", PermissionFirewall.isSourceAuthorized(SourceType.CALENDAR_SYNC))
    }

    @Test
    fun testChannelToggleBlocksAuthorization() {
        // Disable Location
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, false)
        assertFalse("Disabled Location channel must be blocked by firewall", PermissionFirewall.isSourceAuthorized(SourceType.LOCATION))

        // Disable Notification Ingestion
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, false)
        assertFalse("Disabled Notification channel must be blocked by firewall", PermissionFirewall.isSourceAuthorized(SourceType.NOTIFICATION_INGESTION))

        // Other channels remain unaffected
        assertTrue("BLE scanning should remain authorized", PermissionFirewall.isSourceAuthorized(SourceType.BLE_SCANNING))

        // Re-enable Location
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        assertTrue("Re-enabled Location channel must be authorized", PermissionFirewall.isSourceAuthorized(SourceType.LOCATION))
    }

    @Test
    fun testFirewallChannelStateFlowSync() {
        val initialMap = PermissionFirewall.channelToggles.value
        assertTrue(initialMap[SourceType.LOCATION] == true)

        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, false)
        val updatedMap = PermissionFirewall.channelToggles.value
        assertEquals(false, updatedMap[SourceType.BLE_SCANNING])
        assertFalse(PermissionFirewall.isSourceAuthorized(SourceType.BLE_SCANNING))
    }

    // =========================================================================
    // 2. Autonomous Action Kill-Switch (Safe Mode) Tests
    // =========================================================================

    @Test
    fun testSafeModeDefaultState() {
        assertFalse("Safe Mode should be disabled by default", SafeModeManager.isSafeModeEnabled.value)
        assertTrue("Plan mutations should be permitted by default", SafeModeManager.canMutatePlans())
        assertTrue("Ghost auto-promotion should be permitted by default", SafeModeManager.canAutoPromoteGhostItems())
    }

    @Test
    fun testSafeModeActivationBlocksAutonomousActions() {
        SafeModeManager.setSafeMode(true)
        assertTrue("Safe Mode should be enabled", SafeModeManager.isSafeModeEnabled.value)
        assertFalse("Plan mutations must be blocked when Safe Mode is active", SafeModeManager.canMutatePlans())
        assertFalse("Ghost auto-promotions must be blocked when Safe Mode is active", SafeModeManager.canAutoPromoteGhostItems())
    }

    @Test
    fun testSafeModeToggle() {
        SafeModeManager.toggleSafeMode()
        assertTrue(SafeModeManager.isSafeModeEnabled.value)
        SafeModeManager.toggleSafeMode()
        assertFalse(SafeModeManager.isSafeModeEnabled.value)
    }

    // =========================================================================
    // 3. DecisionGate Safe Mode Enforcement Tests
    // =========================================================================

    @Test
    fun testDecisionGateRoutingWithSafeModeOff() {
        SafeModeManager.setSafeMode(false)

        // With Safe Mode OFF:
        // High confidence (>= 0.85) does NOT require ghost routing (Auto-Commit permitted)
        val highConfidenceGhost = DecisionGate.isGhostRoutingRequired(0.95f)
        assertFalse("High confidence should auto-commit when Safe Mode is OFF", highConfidenceGhost)

        // Moderate confidence (< 0.85) requires ghost routing
        val moderateConfidenceGhost = DecisionGate.isGhostRoutingRequired(0.70f)
        assertTrue("Moderate confidence should route to ghost mode", moderateConfidenceGhost)
    }

    @Test
    fun testDecisionGateRoutingWithSafeModeOnForcesGhostMode() {
        SafeModeManager.setSafeMode(true)

        // With Safe Mode ON:
        // Even 99% confidence item MUST be forced into Ghost Mode to prevent autonomous plan mutation
        val highConfidenceGhost = DecisionGate.isGhostRoutingRequired(0.99f)
        assertTrue("High confidence items must be forced to Ghost Mode when Safe Mode is active", highConfidenceGhost)

        val perfectConfidenceGhost = DecisionGate.isGhostRoutingRequired(1.0f)
        assertTrue("Even 100% confidence items must be forced to Ghost Mode in Safe Mode", perfectConfidenceGhost)
    }

    // =========================================================================
    // 4. Data Minimization & PII Sanitization Tests
    // =========================================================================

    @Test
    fun testDataMinimizerRedactsPhoneNumber() {
        val raw = "Dear Suresh, please call 9876543210 to collect the DBMS Record from the library."
        val sanitized = DataMinimizer.sanitizeRawPayload(raw, extractedItem = "DBMS Record", extractedAction = "Collect")

        assertFalse("Raw phone number must not appear in sanitized payload", sanitized.contains("9876543210"))
        assertTrue("Redacted phone placeholder must be present", sanitized.contains("[PHONE REDACTED]"))
    }

    @Test
    fun testDataMinimizerRedactsEmail() {
        val raw = "Send your assignment to prof.sharma@college.edu before 5pm with your ID Card."
        val sanitized = DataMinimizer.sanitizeRawPayload(raw, extractedItem = "ID Card", extractedAction = "Submit")

        assertFalse("Raw email address must not appear in sanitized payload", sanitized.contains("prof.sharma@college.edu"))
        assertTrue("Redacted email placeholder must be present", sanitized.contains("[EMAIL REDACTED]"))
    }

    @Test
    fun testDataMinimizerRedactsGreetings() {
        val raw = "Hello Rajesh, please ensure you carry your Lab Coat today."
        val sanitized = DataMinimizer.sanitizeRawPayload(raw, extractedItem = "Lab Coat", extractedAction = "Carry")

        assertFalse("Personal greeting with name must not appear", sanitized.contains("Hello Rajesh"))
        assertTrue("Redacted greeting placeholder must be present", sanitized.contains("[GREETING REDACTED]"))
    }

    @Test
    fun testDataMinimizerOutputFormatting() {
        val raw = "Bring your Shaker Bottle to the gym"
        val sanitized = DataMinimizer.sanitizeRawPayload(raw, extractedItem = "Shaker Bottle", extractedAction = "Bring")

        assertTrue("Output should include Sanitized header", sanitized.startsWith("[Sanitized: Action=Bring, Item=Shaker Bottle]"))
        assertTrue("Output should contain item keyword", sanitized.contains("Shaker Bottle"))
    }
}
