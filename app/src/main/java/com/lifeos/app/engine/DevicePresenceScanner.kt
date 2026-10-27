package com.lifeos.app.engine

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.lifeos.app.data.LifeOsDao
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PresenceConfidence(val label: String, val shortLabel: String) {
    HIGH_CONFIDENCE("Detected nearby (Strong signal).", "HIGH"),
    LOW_CONFIDENCE("May be nearby, signal is weak.", "LOW"),
    UNKNOWN("Does not appear to be nearby.", "UNKNOWN")
}

enum class ScanThrottleReason {
    SAVER_MODE_ACTIVE,
    PERMISSION_DISABLED
}

sealed class ScanExecutionResult {
    data class Success(val message: String, val devicesFound: Int = 0) : ScanExecutionResult()
    data class Throttled(val reason: ScanThrottleReason, val message: String) : ScanExecutionResult()
    data class Unavailable(val message: String) : ScanExecutionResult()
}

data class DevicePresenceResult(
    val deviceName: String,
    val confidence: PresenceConfidence,
    val rssi: Int?,
    val formattedMessage: String,
    val departureWarning: String? = null
)

object DevicePresenceScanner {

    private const val TAG = "LIFEOS_BLE"
    const val TAG_MODULE5 = "LIFEOS_MODULE5"
    const val SCAN_TIMEOUT_MILLIS = 6000L // 6-second automatic scan timeout

    private var isScanning = false
    private var activeScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var activeCallback: ScanCallback? = null

    fun isScanning(): Boolean = isScanning

    /**
     * Stops active BLE scanning loop immediately to conserve power.
     * Invoked when battery enters SAVER or CRITICAL mode.
     */
    fun stopScan() {
        if (isScanning || activeScanner != null) {
            try {
                activeScanner?.stopScan(activeCallback)
                Log.d(TAG, "BLE scan successfully terminated.")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping BLE scan", e)
            } finally {
                isScanning = false
                activeScanner = null
                activeCallback = null
                Log.i(TAG_MODULE5, "LIFEOS_MODULE5: BLE Scan Stopped -> Active scan paused by BatteryBudgetManager")
            }
        } else {
            Log.i(TAG_MODULE5, "LIFEOS_MODULE5: BLE Scan Throttled -> Reason: SAVER_MODE_ACTIVE")
        }
    }

    private val _detectedDevices = MutableStateFlow<List<DevicePresenceResult>>(
        listOf(
            DevicePresenceResult(
                deviceName = "Laptop Beacon",
                confidence = PresenceConfidence.HIGH_CONFIDENCE,
                rssi = -62,
                formattedMessage = "Your Laptop Beacon is detected nearby (Strong signal, -62 dBm).",
                departureWarning = null
            ),
            DevicePresenceResult(
                deviceName = "Smartwatch",
                confidence = PresenceConfidence.HIGH_CONFIDENCE,
                rssi = -55,
                formattedMessage = "Your Smartwatch is detected nearby (Strong signal, -55 dBm).",
                departureWarning = null
            ),
            DevicePresenceResult(
                deviceName = "Bluetooth Headphones",
                confidence = PresenceConfidence.UNKNOWN,
                rssi = null,
                formattedMessage = "Your Bluetooth Headphones does not appear to be nearby (Bluetooth signal absent).",
                departureWarning = "Your Bluetooth Headphones does not appear to be nearby (Bluetooth signal absent)."
            )
        )
    )
    val detectedDevices = _detectedDevices.asStateFlow()

    /**
     * Resets simulated and discovered devices. Useful for test environments and teardowns.
     */
    fun clearDevices() {
        _detectedDevices.value = emptyList()
    }

    /**
     * Translates raw RSSI signal strength into probabilistic confidence:
     * - HIGH_CONFIDENCE: RSSI >= -70 dBm ("Detected nearby (Strong signal).")
     * - LOW_CONFIDENCE:  -71 to -88 dBm ("May be nearby, signal is weak.")
     * - UNKNOWN:         < -88 dBm or absent ("Does not appear to be nearby.")
     */
    fun classifyRssi(rssi: Int?): PresenceConfidence {
        return when {
            rssi == null -> PresenceConfidence.UNKNOWN
            rssi >= -70 -> PresenceConfidence.HIGH_CONFIDENCE
            rssi >= -88 -> PresenceConfidence.LOW_CONFIDENCE
            else -> PresenceConfidence.UNKNOWN
        }
    }

    /**
     * Formats probabilistic departure warnings.
     * Example: "Your Laptop does not appear to be nearby (Bluetooth signal absent)."
     */
    fun formatDepartureWarning(deviceName: String, confidence: PresenceConfidence, rssi: Int? = null): String {
        return when (confidence) {
            PresenceConfidence.HIGH_CONFIDENCE -> "Your $deviceName is detected nearby (Strong signal)."
            PresenceConfidence.LOW_CONFIDENCE -> "Your $deviceName may be nearby, signal is weak."
            PresenceConfidence.UNKNOWN -> {
                if (rssi != null && rssi < -88) {
                    "Your $deviceName does not appear to be nearby (Bluetooth signal weak: $rssi dBm)."
                } else {
                    "Your $deviceName does not appear to be nearby (Bluetooth signal absent)."
                }
            }
        }
    }

    /**
     * Translates raw RSSI signal strength into probabilistic confidence, message, and warnings.
     */
    fun evaluateSignal(deviceName: String, rssi: Int?): DevicePresenceResult {
        val confidence = classifyRssi(rssi)
        val message = when (confidence) {
            PresenceConfidence.HIGH_CONFIDENCE -> "Your $deviceName is detected nearby (Strong signal, ${rssi} dBm)."
            PresenceConfidence.LOW_CONFIDENCE -> "Your $deviceName may be nearby, signal is weak (${rssi} dBm)."
            PresenceConfidence.UNKNOWN -> formatDepartureWarning(deviceName, confidence, rssi)
        }
        val warning = if (confidence != PresenceConfidence.HIGH_CONFIDENCE) {
            formatDepartureWarning(deviceName, confidence, rssi)
        } else null

        return DevicePresenceResult(
            deviceName = deviceName,
            confidence = confidence,
            rssi = rssi,
            formattedMessage = message,
            departureWarning = warning
        )
    }

    /**
     * Evaluates signal, records device state in state flow, and emits diagnostic log.
     * LIFEOS_MODULE5: Device Found -> '[DeviceName]' | RSSI: [N] dBm | Confidence: [HIGH / LOW / UNKNOWN]
     */
    fun evaluateAndRecordSignal(deviceName: String, rssi: Int?): DevicePresenceResult {
        val evaluated = evaluateSignal(deviceName, rssi)
        val currentList = _detectedDevices.value.toMutableList()
        val index = currentList.indexOfFirst { it.deviceName.equals(deviceName, ignoreCase = true) }
        if (index >= 0) {
            currentList[index] = evaluated
        } else {
            currentList.add(evaluated)
        }
        _detectedDevices.value = currentList

        // Diagnostic log: LIFEOS_MODULE5: Device Found -> '[DeviceName]' | RSSI: [N] dBm | Confidence: [HIGH / LOW / UNKNOWN]
        val rssiStr = rssi?.toString() ?: "N/A"
        Log.i(TAG_MODULE5, "LIFEOS_MODULE5: Device Found -> '$deviceName' | RSSI: $rssiStr dBm | Confidence: ${evaluated.confidence.shortLabel}")

        return evaluated
    }

    /**
     * Checks all scan preconditions: Permission Firewall and Battery Budget Manager.
     * Emits: LIFEOS_MODULE5: Scan Throttled -> Reason: [SAVER_MODE_ACTIVE / PERMISSION_DISABLED]
     */
    fun checkScanPreconditions(context: Context? = null): ScanExecutionResult? {
        // 1. Check Permission Firewall
        val isAuthorized = if (context != null) {
            PermissionFirewall.isSourceAuthorized(context, SourceType.BLE_SCANNING)
        } else {
            PermissionFirewall.isSourceAuthorized(SourceType.BLE_SCANNING)
        }

        if (!isAuthorized) {
            Log.w(TAG_MODULE5, "LIFEOS_MODULE5: Scan Throttled -> Reason: PERMISSION_DISABLED")
            Log.w(TAG, "BLE Hardware Scan blocked: BLE_SCANNING is disabled in Privacy Firewall.")
            return ScanExecutionResult.Throttled(
                reason = ScanThrottleReason.PERMISSION_DISABLED,
                message = "BLE scanning channel disabled in Privacy Firewall."
            )
        }

        // 2. Check Battery Budget Manager
        if (!BatteryBudgetManager.canRunBleScan()) {
            Log.w(TAG_MODULE5, "LIFEOS_MODULE5: Scan Throttled -> Reason: SAVER_MODE_ACTIVE")
            Log.w(TAG, "BLE Hardware Scan throttled: Active battery profile is ${BatteryBudgetManager.currentPowerProfile.value}.")
            return ScanExecutionResult.Throttled(
                reason = ScanThrottleReason.SAVER_MODE_ACTIVE,
                message = "BLE scan paused under battery profile ${BatteryBudgetManager.currentPowerProfile.value}."
            )
        }

        return null // All clear
    }

    /**
     * Determines whether necessary Bluetooth OS permissions are granted.
     */
    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scan = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN)
            val connect = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
            scan == PackageManager.PERMISSION_GRANTED && connect == PackageManager.PERMISSION_GRANTED
        } else {
            val bt = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH)
            val admin = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADMIN)
            bt == PackageManager.PERMISSION_GRANTED && admin == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Safely initiates BLE hardware scan if permitted and supported.
     * Enforces 6-second timeout window to prevent background thread leaks.
     */
    fun performHardwareScan(context: Context): ScanExecutionResult {
        // Precondition check: Firewall & Battery
        val throttleResult = checkScanPreconditions(context)
        if (throttleResult != null) {
            return throttleResult
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = try { bluetoothManager?.adapter } catch (e: Exception) { null }

        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth hardware is absent or disabled. Falling back to probabilistic estimator.")
            return ScanExecutionResult.Unavailable("Bluetooth hardware absent or disabled.")
        }

        if (!hasBluetoothPermissions(context)) {
            Log.w(TAG_MODULE5, "LIFEOS_MODULE5: Scan Throttled -> Reason: PERMISSION_DISABLED")
            Log.w(TAG, "Bluetooth OS permissions not granted.")
            return ScanExecutionResult.Throttled(
                reason = ScanThrottleReason.PERMISSION_DISABLED,
                message = "Bluetooth OS runtime permissions not granted."
            )
        }

        try {
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                Log.w(TAG, "BluetoothLeScanner is unavailable.")
                return ScanExecutionResult.Unavailable("BluetoothLeScanner is null.")
            }

            // Diagnostic log: LIFEOS_MODULE5: BLE Scan Initiated -> Scanning for tracked accessories...
            Log.i(TAG_MODULE5, "LIFEOS_MODULE5: BLE Scan Initiated -> Scanning for tracked accessories...")

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.let {
                        val name = it.device?.name ?: "Tracked Accessory"
                        val rssi = it.rssi
                        evaluateAndRecordSignal(name, rssi)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w(TAG, "BLE scan failed with error code: $errorCode")
                }
            }

            activeScanner = scanner
            activeCallback = callback
            isScanning = true
            scanner.startScan(callback)

            // Auto stop scan after 6-second window to conserve power and prevent thread leaks
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                stopScan()
            }, SCAN_TIMEOUT_MILLIS)

            return ScanExecutionResult.Success("BLE hardware scan active for $SCAN_TIMEOUT_MILLIS ms.")

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while accessing Bluetooth scanner", e)
            return ScanExecutionResult.Throttled(ScanThrottleReason.PERMISSION_DISABLED, "SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in Bluetooth scanner", e)
            return ScanExecutionResult.Unavailable("Exception: ${e.message}")
        }
    }

    /**
     * Executes mock scan for test suites and dev simulations.
     * Validates preconditions, logs scan initiation, evaluates devices, and emits diagnostic logs.
     */
    fun executeMockScan(
        devices: List<Pair<String, Int?>>,
        context: Context? = null
    ): ScanExecutionResult {
        val throttleResult = checkScanPreconditions(context)
        if (throttleResult != null) {
            return throttleResult
        }

        // Diagnostic log: LIFEOS_MODULE5: BLE Scan Initiated -> Scanning for tracked accessories...
        Log.i(TAG_MODULE5, "LIFEOS_MODULE5: BLE Scan Initiated -> Scanning for tracked accessories...")

        devices.forEach { (name, rssi) ->
            evaluateAndRecordSignal(name, rssi)
        }

        return ScanExecutionResult.Success(
            message = "Mock scan completed successfully.",
            devicesFound = devices.size
        )
    }

    /**
     * Simulation method for emulator & dev tools testing.
     */
    fun simulateDeviceState(deviceName: String, confidence: PresenceConfidence, rssi: Int? = null) {
        val calculatedRssi = rssi ?: when (confidence) {
            PresenceConfidence.HIGH_CONFIDENCE -> -58
            PresenceConfidence.LOW_CONFIDENCE -> -82
            PresenceConfidence.UNKNOWN -> null
        }
        evaluateAndRecordSignal(deviceName, calculatedRssi)
    }

    /**
     * Module 2: Evaluates device presence and if high confidence (RSSI >= -70 dBm),
     * automatically marks matching checklist items as ready (isReady = true) in Room DB.
     */
    suspend fun syncDeviceWithChecklist(
        dao: LifeOsDao,
        deviceName: String,
        rssi: Int?,
        targetItemKeyword: String = "Laptop"
    ): DevicePresenceResult {
        val result = evaluateAndRecordSignal(deviceName, rssi)
        if (result.confidence == PresenceConfidence.HIGH_CONFIDENCE) {
            val matchingItems = dao.getItemsByNameLike(targetItemKeyword)
            matchingItems.forEach { item ->
                if (!item.isReady) {
                    dao.updateItemStatus(item.id, true)
                    Log.i(TAG_MODULE5, "LIFEOS_MODULE5: Auto-marked '${item.itemName}' as isReady=true via BLE Proximity (RSSI: ${rssi} dBm)")
                    Log.i("LIFEOS_MODULE2", "LIFEOS_MODULE2: BLE Proximity Check -> Auto-marked '${item.itemName}' ready (Device: '$deviceName', RSSI: ${rssi} dBm)")
                }
            }
        } else {
            Log.i("LIFEOS_MODULE2", "LIFEOS_MODULE2: BLE Proximity Check -> ${result.departureWarning ?: result.formattedMessage}")
        }
        return result
    }
}
