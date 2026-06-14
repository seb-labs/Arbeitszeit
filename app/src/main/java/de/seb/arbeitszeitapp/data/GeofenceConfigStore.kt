package de.seb.arbeitszeitapp.data

import android.content.Context

class GeofenceConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): GeofenceConfig? {
        val latitude = prefs.getString(KEY_LATITUDE, null)?.toDoubleOrNull()
        val longitude = prefs.getString(KEY_LONGITUDE, null)?.toDoubleOrNull()
        return if (latitude != null && longitude != null) {
            GeofenceConfig(latitude, longitude)
        } else {
            null
        }
    }

    fun save(latitude: Double, longitude: Double) {
        prefs.edit()
            .putString(KEY_LATITUDE, latitude.toString())
            .putString(KEY_LONGITUDE, longitude.toString())
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_LATITUDE)
            .remove(KEY_LONGITUDE)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "geofence_config"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
    }
}
