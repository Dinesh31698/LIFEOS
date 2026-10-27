package com.lifeos.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.lifeos.app.data.LifeOsDatabase
import com.lifeos.app.engine.ForgettingModelEngine
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!PermissionFirewall.isSourceAuthorized(context, SourceType.LOCATION)) {
            Log.w(TAG, "Geofence transition ignored: LOCATION source is disabled in Privacy Firewall.")
            return
        }

        var modeName = intent.getStringExtra("mode_name") ?: intent.getStringExtra("id")
        var transitionType = intent.getStringExtra("transition")

        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent != null && !geofencingEvent.hasError()) {
            val transition = geofencingEvent.geofenceTransition
            if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                transitionType = "EXIT"
            }
            if (modeName == null) {
                modeName = geofencingEvent.triggeringGeofences?.firstOrNull()?.requestId
            }
        }

        if (intent.action == "com.lifeos.app.ACTION_GEOFENCE_EXIT" ||
            intent.action == "ACTION_GEOFENCE_EXIT" ||
            (intent.action in listOf("com.lifeos.app.ACTION_GEOFENCE_EVENT", "ACTION_GEOFENCE_EVENT") &&
                (transitionType == null || transitionType.equals("EXIT", ignoreCase = true) || transitionType.equals("GEOFENCE_TRANSITION_EXIT", ignoreCase = true)))
        ) {
            transitionType = "EXIT"
        }

        if (modeName == null) modeName = "College"

        val targetModeId = when {
            modeName.contains("College", ignoreCase = true) -> 1
            modeName.contains("Gym", ignoreCase = true) -> 2
            modeName.contains("Travel", ignoreCase = true) -> 3
            else -> 1
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = LifeOsDatabase.getDatabase(context.applicationContext)
                val isExit = transitionType?.equals("EXIT", ignoreCase = true) == true
                if (isExit) {
                    Log.i(TAG, "Geofence EXIT detected for mode $modeName (id=$targetModeId) -> Evaluating unchecked items")
                    Log.i("LIFEOS_MODULE4", "LIFEOS_MODULE4: Geofence EXIT Triggered -> Evaluating unchecked items for Mode '$modeName' (id=$targetModeId)")
                    // Module 2: Context Recognition & Mode Switch
                    com.lifeos.app.engine.ContextRecognitionEngine.evaluateAndSwitchMode(
                        dao = db.lifeOsDao(),
                        isGeofenceExit = true,
                        targetModeHint = modeName,
                        context = context
                    )

                    val updatedHabits = ForgettingModelEngine.handleGeofenceDeparture(db.lifeOsDao(), targetModeId)
                    Log.d(TAG, "Departed geofence: ${updatedHabits.size} forgotten items escalated")
                    Log.i("LIFEOS_MODULE4", "LIFEOS_MODULE4: Geofence Departure Evaluated -> Escalated ${updatedHabits.size} unchecked items")

                    // Module 4: Contextual Readiness & Anti-Spam Departure Alert
                    AntiSpamNotificationEngine.triggerDepartureAlert(
                        context = context.applicationContext,
                        dao = db.lifeOsDao(),
                        modeId = targetModeId,
                        modeName = modeName
                    )
                } else {
                    Log.i(TAG, "Geofence ENTER/DWELL detected for mode $modeName (id=$targetModeId)")
                    db.lifeOsDao().setActiveMode(targetModeId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "LIFEOS_GEOFENCE"
    }
}
