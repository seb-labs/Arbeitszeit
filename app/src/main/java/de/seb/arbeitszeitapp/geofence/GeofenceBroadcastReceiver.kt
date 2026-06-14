
package de.seb.arbeitszeitapp.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import de.seb.arbeitszeitapp.data.WorkSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val event = GeofencingEvent.fromIntent(intent)
                if (event != null && !event.hasError()) {
                    val transition = event.geofenceTransition
                    if (transition == Geofence.GEOFENCE_TRANSITION_ENTER || transition == Geofence.GEOFENCE_TRANSITION_EXIT || transition == Geofence.GEOFENCE_TRANSITION_DWELL) {
                        val timestamp = event.triggeringLocation?.time ?: System.currentTimeMillis()
                        WorkSessionRepository(context.applicationContext).handleGeofenceTransition(transition, timestamp)
                    }
                } else if (event != null) {
                    Log.w("GeofenceReceiver", "Geofence error: ${event.errorCode}")
                }
            } catch (e: Exception) {
                Log.e("GeofenceReceiver", "Geofence broadcast failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
