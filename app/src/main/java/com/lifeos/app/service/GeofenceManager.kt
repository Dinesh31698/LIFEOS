package com.lifeos.app.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

data class GeofenceArea(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 300f
)

class GeofenceManager(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = "com.lifeos.app.ACTION_GEOFENCE_EVENT"
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(context, 1001, intent, flags)
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun registerGeofences(
        geofences: List<GeofenceArea> = DEFAULT_GEOFENCES,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (!hasLocationPermission()) {
            val ex = SecurityException("ACCESS_FINE_LOCATION permission not granted")
            Log.e(TAG, "Cannot register geofences without fine location permission", ex)
            onFailure(ex)
            return
        }

        try {
            val geofenceList = geofences.map { area ->
                Geofence.Builder()
                    .setRequestId(area.id)
                    .setCircularRegion(area.latitude, area.longitude, area.radiusMeters)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or
                        Geofence.GEOFENCE_TRANSITION_DWELL or
                        Geofence.GEOFENCE_TRANSITION_EXIT
                    )
                    .setLoiteringDelay(10_000) // 10 seconds dwell time
                    .build()
            }

            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofenceList)
                .build()

            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully registered ${geofenceList.size} geofences")
                    onSuccess()
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Failed to register geofences: ${error.message}", error)
                    onFailure(error)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while adding geofences", e)
            onFailure(e)
        }
    }

    fun removeGeofences(
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully removed geofences")
                onSuccess()
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to remove geofences: ${error.message}", error)
                onFailure(error)
            }
    }

    companion object {
        private const val TAG = "GeofenceManager"

        val DEFAULT_GEOFENCES = listOf(
            GeofenceArea(id = "College", latitude = 12.9716, longitude = 77.5946, radiusMeters = 300f),
            GeofenceArea(id = "Gym", latitude = 12.9780, longitude = 77.6000, radiusMeters = 200f),
            GeofenceArea(id = "Travel", latitude = 13.1986, longitude = 77.7066, radiusMeters = 500f)
        )
    }
}
