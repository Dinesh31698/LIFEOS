package com.lifeos.app.security

import android.app.AppOpsManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SourceType(
    val displayName: String,
    val description: String,
    val whatIsUsed: String = "",
    val whatIsStored: String = "",
    val whatHappensIfDenied: String = ""
) {
    PROFILE(
        "User Profile & Identity",
        "Preferred name, language, role, and baseline routines.",
        "User-entered preferred name, role, language preferences",
        "Saved locally in room database user_profile table",
        "Defaults to generic 'User' with standard routine"
    ),
    LOCATION(
        "Location & Geofencing",
        "Automatic mode switching when arriving at College or Gym.",
        "Coarse/fine location and geofence events",
        "Anchor places names and coordinates locally; no continuous location trails",
        "Manual mode switching only; no automatic geofence triggers"
    ),
    ACTIVITY_RECOGNITION(
        "Activity Recognition",
        "Physical life step detection and motion states.",
        "Device sensor step counter and motion transition events",
        "Daily aggregate activity counts; no raw sensor logs",
        "Physical life recorder marked limited; no auto-detected walking/transit"
    ),
    MICROPHONE(
        "Microphone & Voice Input",
        "Hands-free voice orb triggers and temporary SOS audio recording.",
        "Audio buffer during active voice interaction or SOS countdown",
        "Voice orb audio discarded immediately after STT; SOS temporary clip stored locally and auto-deleted",
        "Voice features and audio SOS unavailable; text input remains available"
    ),
    NOTIFICATION_INGESTION(
        "Notification Ingestion",
        "Parsing schedule changes and required items from incoming chats/alerts.",
        "Notification text and title from authorized apps",
        "PII-stripped summarized evidence snippet in evidence_logs",
        "Automatic schedule mutations from alerts disabled; manual entry only"
    ),
    USAGE_ACCESS(
        "App Usage Statistics",
        "Screen time audit and distraction vs focus monitoring.",
        "UsageStatsManager foreground time aggregates",
        "Daily and weekly usage durations per package locally",
        "Time audit shows disabled state; contextual app warnings disabled"
    ),
    BLE_SCANNING(
        "BLE Accessory Scanning",
        "Probabilistic detection of Laptop Beacon, Smartwatch, and Headphones.",
        "Local BLE scan advertisements",
        "Device match confidence score",
        "Accessory presence detection disabled"
    ),
    CALENDAR_SYNC(
        "Calendar Sync",
        "Integration with scheduled campus classes and academic timetables.",
        "Local Android calendar events",
        "Imported schedule event titles and times",
        "External calendar events not imported"
    ),
    SMS(
        "Emergency SMS",
        "Dispatching distress text messages to emergency contacts during SOS.",
        "Direct telephony SMS manager dispatch",
        "Emergency contact phone numbers stored locally",
        "Direct SMS disabled; user prompted to use system SMS app"
    ),
    VOICE_MODEL(
        "On-Device Voice Model",
        "Local speech recognition model weights.",
        "On-device STT/TTS engine",
        "Configured language preference",
        "Speech recognition unavailable; fallback to text"
    )
}

enum class PermissionStatus(val label: String) {
    GRANTED("Active"),
    DENIED("OS Permission Required"),
    DISABLED_BY_USER("Disabled in Privacy")
}

object PermissionFirewall {

    private const val TAG = "LIFEOS_FIREWALL"
    const val TAG_MODULE7 = "LIFEOS_MODULE7"
    private const val PREFS_NAME = "lifeos_firewall_prefs"

    private val DEFAULT_TOGGLES = mapOf(
        SourceType.PROFILE to true,
        SourceType.LOCATION to true,
        SourceType.ACTIVITY_RECOGNITION to true,
        SourceType.MICROPHONE to true,
        SourceType.NOTIFICATION_INGESTION to true,
        SourceType.USAGE_ACCESS to true,
        SourceType.BLE_SCANNING to true,
        SourceType.CALENDAR_SYNC to false,
        SourceType.SMS to true,
        SourceType.VOICE_MODEL to true
    )

    private val _channelToggles = MutableStateFlow(DEFAULT_TOGGLES)
    val channelToggles = _channelToggles.asStateFlow()

    private var sharedPrefs: SharedPreferences? = null

    fun init(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs = prefs
            val updated = _channelToggles.value.toMutableMap()
            SourceType.entries.forEach { source ->
                if (prefs.contains(source.name)) {
                    updated[source] = prefs.getBoolean(source.name, updated[source] ?: true)
                }
            }
            _channelToggles.value = updated
            Log.i(TAG, "PermissionFirewall initialized from SharedPreferences")
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize PermissionFirewall SharedPreferences: ${e.message}")
        }
    }

    fun setChannelAuthorized(source: SourceType, enabled: Boolean) {
        val updated = _channelToggles.value.toMutableMap()
        updated[source] = enabled
        _channelToggles.value = updated
        sharedPrefs?.edit()?.putBoolean(source.name, enabled)?.apply()
        Log.i(TAG, "Privacy Firewall channel '${source.displayName}' set to: ${if (enabled) "ENABLED" else "DISABLED"}")
        Log.i(TAG_MODULE7, "LIFEOS_MODULE7: Firewall Channel State -> ${source.name} = ${if (enabled) "AUTHORIZED" else "BLOCKED"}")
    }

    fun hasOsPermission(context: Context, source: SourceType): Boolean {
        return when (source) {
            SourceType.PROFILE -> true
            SourceType.VOICE_MODEL -> true
            SourceType.CALENDAR_SYNC -> true
            SourceType.LOCATION -> {
                val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                val coarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
            }
            SourceType.ACTIVITY_RECOGNITION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
                } else true
            }
            SourceType.MICROPHONE -> {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            }
            SourceType.SMS -> {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            }
            SourceType.USAGE_ACCESS -> {
                try {
                    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        appOps?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
                    } else {
                        appOps?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
                    }
                    mode == AppOpsManager.MODE_ALLOWED
                } catch (e: Exception) {
                    false
                }
            }
            SourceType.NOTIFICATION_INGESTION -> {
                try {
                    val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                    listeners?.contains(context.packageName) == true
                } catch (e: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    } else true
                }
            }
            SourceType.BLE_SCANNING -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val scan = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN)
                    val connect = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
                    scan == PackageManager.PERMISSION_GRANTED && connect == PackageManager.PERMISSION_GRANTED
                } else {
                    val bt = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH)
                    bt == PackageManager.PERMISSION_GRANTED
                }
            }
        }
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getChannelStatus(context: Context, source: SourceType): PermissionStatus {
        val userEnabled = _channelToggles.value[source] ?: true
        if (!userEnabled) {
            return PermissionStatus.DISABLED_BY_USER
        }
        val osPerm = hasOsPermission(context, source)
        return if (osPerm) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }

    /**
     * Primary security gate: All module requests must pass this check.
     */
    fun isSourceAuthorized(context: Context, source: SourceType): Boolean {
        val userEnabled = _channelToggles.value[source] ?: true
        if (!userEnabled) {
            Log.w(TAG, "BLOCKED by Firewall: '${source.displayName}' is disabled by user in Privacy Dashboard.")
            Log.w(TAG_MODULE7, "LIFEOS_MODULE7: Firewall Blocked -> Channel '${source.name}' is disabled by user.")
            Log.w("LIFEOS_MODULE6", "LIFEOS_MODULE6: Permission Blocked -> Channel '${source.name}' is disabled in Privacy Firewall (PERMISSION_BLOCKED)")
            return false
        }
        return true
    }

    /**
     * For unit tests and headless environments.
     */
    fun isSourceAuthorized(source: SourceType): Boolean {
        val userEnabled = _channelToggles.value[source] ?: true
        if (!userEnabled) {
            Log.w(TAG_MODULE7, "LIFEOS_MODULE7: Firewall Blocked -> Channel '${source.name}' is disabled by user.")
            Log.w("LIFEOS_MODULE6", "LIFEOS_MODULE6: Permission Blocked -> Channel '${source.name}' is disabled in Privacy Firewall (PERMISSION_BLOCKED)")
            return false
        }
        return true
    }

    /**
     * For unit test teardown.
     */
    fun resetForTesting() {
        _channelToggles.value = DEFAULT_TOGGLES
        sharedPrefs?.edit()?.clear()?.apply()
    }
}
