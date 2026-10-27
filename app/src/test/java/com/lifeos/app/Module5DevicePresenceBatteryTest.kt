package com.lifeos.app

import com.lifeos.app.engine.*
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LIFEOS Module 5: Device Presence & Battery Budget Manager Test Suite.
 * Validates:
 *  1. Probabilistic RSSI signal classification logic (HIGH_CONFIDENCE, LOW_CONFIDENCE, UNKNOWN).
 *  2. Probabilistic departure warning formatting strings.
 *  3. Dynamic power profile determination across NORMAL, SAVER, and CRITICAL battery levels.
 *  4. Automatic pausing/throttling of BLE scans under SAVER and CRITICAL profiles.
 *  5. Permission Firewall blocking when the BLE_SCANNING channel is disabled by the user.
 *  6. Mock BLE scan execution and RSSI parsing without hardware exceptions.
 *  7. Interactive dev profile override cycling for UI testing.
 */
class Module5DevicePresenceBatteryTest {

    @Before
    fun setUp() {
        // Reset firewall to default (all authorized)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        // Reset battery budget to 100% NORMAL
        BatteryBudgetManager.setSimulatedBattery(100, isPowerSave = false)
    }

    // =========================================================================
    // 1. Probabilistic RSSI Signal Classification Tests
    // =========================================================================

    @Test
    fun testRssiClassificationBoundaries() {
        // HIGH_CONFIDENCE: RSSI >= -70 dBm
        assertEquals(PresenceConfidence.HIGH_CONFIDENCE, DevicePresenceScanner.classifyRssi(-50))
        assertEquals(PresenceConfidence.HIGH_CONFIDENCE, DevicePresenceScanner.classifyRssi(-65))
        assertEquals(PresenceConfidence.HIGH_CONFIDENCE, DevicePresenceScanner.classifyRssi(-70))

        // LOW_CONFIDENCE: -71 to -88 dBm
        assertEquals(PresenceConfidence.LOW_CONFIDENCE, DevicePresenceScanner.classifyRssi(-71))
        assertEquals(PresenceConfidence.LOW_CONFIDENCE, DevicePresenceScanner.classifyRssi(-80))
        assertEquals(PresenceConfidence.LOW_CONFIDENCE, DevicePresenceScanner.classifyRssi(-88))

        // UNKNOWN: < -88 dBm or absent (null)
        assertEquals(PresenceConfidence.UNKNOWN, DevicePresenceScanner.classifyRssi(-89))
        assertEquals(PresenceConfidence.UNKNOWN, DevicePresenceScanner.classifyRssi(-95))
        assertEquals(PresenceConfidence.UNKNOWN, DevicePresenceScanner.classifyRssi(null))
    }

    @Test
    fun testProbabilisticAlertPhrasing() {
        // Absent signal (null RSSI)
        val absentWarning = DevicePresenceScanner.formatDepartureWarning("Laptop", PresenceConfidence.UNKNOWN, null)
        assertEquals("Your Laptop does not appear to be nearby (Bluetooth signal absent).", absentWarning)

        // Weak signal (< -88 dBm)
        val weakSignalWarning = DevicePresenceScanner.formatDepartureWarning("Laptop", PresenceConfidence.UNKNOWN, -92)
        assertTrue(weakSignalWarning.contains("does not appear to be nearby"))

        // Low confidence
        val lowConfWarning = DevicePresenceScanner.formatDepartureWarning("Smartwatch", PresenceConfidence.LOW_CONFIDENCE, -80)
        assertEquals("Your Smartwatch may be nearby, signal is weak.", lowConfWarning)

        // High confidence
        val highConfNotice = DevicePresenceScanner.formatDepartureWarning("Laptop Beacon", PresenceConfidence.HIGH_CONFIDENCE, -62)
        assertEquals("Your Laptop Beacon is detected nearby (Strong signal).", highConfNotice)
    }

    @Test
    fun testEvaluateSignalGeneratesCorrectResultStructure() {
        val laptopResult = DevicePresenceScanner.evaluateSignal("Laptop", -65)
        assertEquals("Laptop", laptopResult.deviceName)
        assertEquals(PresenceConfidence.HIGH_CONFIDENCE, laptopResult.confidence)
        assertEquals(-65, laptopResult.rssi)
        assertTrue(laptopResult.formattedMessage.contains("Strong signal"))
        assertNull(laptopResult.departureWarning)

        val headphonesResult = DevicePresenceScanner.evaluateSignal("Headphones", null)
        assertEquals("Headphones", headphonesResult.deviceName)
        assertEquals(PresenceConfidence.UNKNOWN, headphonesResult.confidence)
        assertNull(headphonesResult.rssi)
        assertEquals("Your Headphones does not appear to be nearby (Bluetooth signal absent).", headphonesResult.departureWarning)
    }

    // =========================================================================
    // 2. Battery Budget Dynamic Power Profile Tests
    // =========================================================================

    @Test
    fun testDynamicPowerProfileDetermination() {
        // NORMAL: Battery > 20%
        assertEquals(PowerProfile.NORMAL, BatteryBudgetManager.determineProfile(100, false))
        assertEquals(PowerProfile.NORMAL, BatteryBudgetManager.determineProfile(50, false))
        assertEquals(PowerProfile.NORMAL, BatteryBudgetManager.determineProfile(21, false))

        // SAVER: 10% <= Battery <= 20%
        assertEquals(PowerProfile.SAVER, BatteryBudgetManager.determineProfile(20, false))
        assertEquals(PowerProfile.SAVER, BatteryBudgetManager.determineProfile(15, false))
        assertEquals(PowerProfile.SAVER, BatteryBudgetManager.determineProfile(10, false))

        // CRITICAL: Battery < 10% or OS Power Saver active
        assertEquals(PowerProfile.CRITICAL, BatteryBudgetManager.determineProfile(9, false))
        assertEquals(PowerProfile.CRITICAL, BatteryBudgetManager.determineProfile(5, false))
        assertEquals(PowerProfile.CRITICAL, BatteryBudgetManager.determineProfile(0, false))
        assertEquals(PowerProfile.CRITICAL, BatteryBudgetManager.determineProfile(85, isPowerSaveMode = true))
    }

    @Test
    fun testBleScanThrottledUnderSaverAndCriticalProfiles() {
        // NORMAL profile: BLE scan allowed
        BatteryBudgetManager.setSimulatedBattery(80, false)
        assertTrue("BLE scans must be allowed under NORMAL profile", BatteryBudgetManager.canRunBleScan())
        assertNull(DevicePresenceScanner.checkScanPreconditions())

        // SAVER profile: BLE scan throttled
        BatteryBudgetManager.setSimulatedBattery(15, false)
        assertFalse("BLE scans must be paused under SAVER profile", BatteryBudgetManager.canRunBleScan())
        val saverCheck = DevicePresenceScanner.checkScanPreconditions()
        assertNotNull(saverCheck)
        assertTrue(saverCheck is ScanExecutionResult.Throttled)
        assertEquals(ScanThrottleReason.SAVER_MODE_ACTIVE, (saverCheck as ScanExecutionResult.Throttled).reason)

        // CRITICAL profile: BLE scan throttled
        BatteryBudgetManager.setSimulatedBattery(5, false)
        assertFalse("BLE scans must be paused under CRITICAL profile", BatteryBudgetManager.canRunBleScan())
        val criticalCheck = DevicePresenceScanner.checkScanPreconditions()
        assertNotNull(criticalCheck)
        assertTrue(criticalCheck is ScanExecutionResult.Throttled)
        assertEquals(ScanThrottleReason.SAVER_MODE_ACTIVE, (criticalCheck as ScanExecutionResult.Throttled).reason)
    }

    // =========================================================================
    // 3. Permission Firewall Blocking Tests
    // =========================================================================

    @Test
    fun testPermissionFirewallBlocksBleScanWhenDisabled() {
        // Battery is at 100% NORMAL, but user disables BLE_SCANNING in Firewall
        BatteryBudgetManager.setSimulatedBattery(100, false)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, false)

        val check = DevicePresenceScanner.checkScanPreconditions()
        assertNotNull(check)
        assertTrue(check is ScanExecutionResult.Throttled)
        assertEquals(ScanThrottleReason.PERMISSION_DISABLED, (check as ScanExecutionResult.Throttled).reason)
    }

    // =========================================================================
    // 4. Mock BLE Scan Execution Tests
    // =========================================================================

    @Test
    fun testMockBleScanExecutionSuccess() {
        BatteryBudgetManager.setSimulatedBattery(100, false)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)

        val mockDevices = listOf(
            "Laptop Beacon" to -62,
            "Smartwatch" to -75,
            "Bluetooth Headphones" to null
        )

        val result = DevicePresenceScanner.executeMockScan(mockDevices)
        assertTrue("Mock scan must succeed under NORMAL battery and enabled firewall", result is ScanExecutionResult.Success)
        assertEquals(3, (result as ScanExecutionResult.Success).devicesFound)

        // Verify detected devices state flow was updated
        val devices = DevicePresenceScanner.detectedDevices.value
        val laptop = devices.find { it.deviceName == "Laptop Beacon" }
        assertNotNull(laptop)
        assertEquals(PresenceConfidence.HIGH_CONFIDENCE, laptop!!.confidence)

        val watch = devices.find { it.deviceName == "Smartwatch" }
        assertNotNull(watch)
        assertEquals(PresenceConfidence.LOW_CONFIDENCE, watch!!.confidence)

        val headphones = devices.find { it.deviceName == "Bluetooth Headphones" }
        assertNotNull(headphones)
        assertEquals(PresenceConfidence.UNKNOWN, headphones!!.confidence)
    }

    @Test
    fun testMockBleScanThrottledWhenSaverActive() {
        BatteryBudgetManager.setSimulatedBattery(15, false) // SAVER

        val mockDevices = listOf("Laptop" to -60)
        val result = DevicePresenceScanner.executeMockScan(mockDevices)
        assertTrue("Mock scan must be throttled when in SAVER profile", result is ScanExecutionResult.Throttled)
        assertEquals(ScanThrottleReason.SAVER_MODE_ACTIVE, (result as ScanExecutionResult.Throttled).reason)
    }

    // =========================================================================
    // 5. Interactive Dev Profile Override Toggling Tests
    // =========================================================================

    @Test
    fun testDevProfileOverrideCycle() {
        // Start at NORMAL (100%)
        BatteryBudgetManager.setSimulatedBattery(100, false)
        assertEquals(PowerProfile.NORMAL, BatteryBudgetManager.currentPowerProfile.value)

        // Cycle 1: NORMAL -> SAVER (15%)
        BatteryBudgetManager.cycleNextProfile()
        assertEquals(PowerProfile.SAVER, BatteryBudgetManager.currentPowerProfile.value)
        assertEquals(15, BatteryBudgetManager.currentBatteryLevel.value)

        // Cycle 2: SAVER -> CRITICAL (5%)
        BatteryBudgetManager.cycleNextProfile()
        assertEquals(PowerProfile.CRITICAL, BatteryBudgetManager.currentPowerProfile.value)
        assertEquals(5, BatteryBudgetManager.currentBatteryLevel.value)

        // Cycle 3: CRITICAL -> NORMAL (100%)
        BatteryBudgetManager.cycleNextProfile()
        assertEquals(PowerProfile.NORMAL, BatteryBudgetManager.currentPowerProfile.value)
        assertEquals(100, BatteryBudgetManager.currentBatteryLevel.value)
    }

    @Test
    fun testExplicitProfileOverrides() {
        BatteryBudgetManager.setProfileOverride(PowerProfile.SAVER)
        assertEquals(PowerProfile.SAVER, BatteryBudgetManager.currentPowerProfile.value)
        assertEquals(15, BatteryBudgetManager.currentBatteryLevel.value)

        BatteryBudgetManager.setProfileOverride(PowerProfile.CRITICAL)
        assertEquals(PowerProfile.CRITICAL, BatteryBudgetManager.currentPowerProfile.value)
        assertEquals(5, BatteryBudgetManager.currentBatteryLevel.value)

        BatteryBudgetManager.setProfileOverride(PowerProfile.NORMAL)
        assertEquals(PowerProfile.NORMAL, BatteryBudgetManager.currentPowerProfile.value)
        assertEquals(100, BatteryBudgetManager.currentBatteryLevel.value)
    }
}
