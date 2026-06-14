
package de.seb.arbeitszeitapp.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import de.seb.arbeitszeitapp.data.GeofenceConfigStore
import de.seb.arbeitszeitapp.data.GeofenceRegistrationState
import de.seb.arbeitszeitapp.data.PermissionSnapshot
import kotlinx.coroutines.tasks.await

private const val GEOFENCE_ID = "work-place"
private const val WORK_RADIUS_METERS = 200f
private const val LOITERING_DELAY_MS = 5 * 60 * 1000

class GeofenceManager(private val context: Context) {
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val appContext = context.applicationContext
    private val configStore = GeofenceConfigStore(appContext)

    suspend fun ensureRegistered(): GeofenceRegistrationState {
        val snapshot = permissionSnapshot()
        if (!snapshot.fineLocationGranted || (snapshot.requiresBackgroundPermission && !snapshot.backgroundLocationGranted)) {
            return GeofenceRegistrationState.MissingPermissions(snapshot)
        }
        if (!snapshot.locationServicesEnabled) {
            return GeofenceRegistrationState.LocationDisabled
        }
        if (!snapshot.playServicesAvailable) {
            return GeofenceRegistrationState.PlayServicesMissing
        }

        val config = configStore.load() ?: return GeofenceRegistrationState.MissingConfiguration

        return try {
            geofencingClient.removeGeofences(pendingIntent()).await()
            geofencingClient.addGeofences(buildRequest(config), pendingIntent()).await()
            GeofenceRegistrationState.Registered
        } catch (security: SecurityException) {
            GeofenceRegistrationState.MissingPermissions(permissionSnapshot())
        } catch (e: Exception) {
            GeofenceRegistrationState.Error(e.message ?: "Geofence konnte nicht registriert werden")
        }
    }

    private fun buildRequest(config: de.seb.arbeitszeitapp.data.GeofenceConfig): GeofencingRequest {
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(config.latitude, config.longitude, WORK_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT or
                    Geofence.GEOFENCE_TRANSITION_DWELL,
            )
            .setLoiteringDelay(LOITERING_DELAY_MS)
            .build()

        return GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(appContext, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun permissionSnapshot(): PermissionSnapshot {
        val fineGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val backgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val locationManager = appContext.getSystemService(LocationManager::class.java)
        val servicesEnabled = locationManager?.isLocationEnabled ?: false
        val playServicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS
        return PermissionSnapshot(
            fineLocationGranted = fineGranted,
            backgroundLocationGranted = backgroundGranted,
            locationServicesEnabled = servicesEnabled,
            playServicesAvailable = playServicesAvailable,
        )
    }
}
