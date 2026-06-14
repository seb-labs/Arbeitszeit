
package de.seb.arbeitszeitapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.seb.arbeitszeitapp.data.GeofenceConfig
import de.seb.arbeitszeitapp.data.GeofenceConfigStore
import de.seb.arbeitszeitapp.data.GeofenceRegistrationState
import de.seb.arbeitszeitapp.data.SessionSource
import de.seb.arbeitszeitapp.data.WorkSessionEntity
import de.seb.arbeitszeitapp.data.WorkSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class WorkTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WorkSessionRepository(application.applicationContext)
    private val geofenceConfigStore = GeofenceConfigStore(application.applicationContext)

    val sessions: StateFlow<List<WorkSessionEntity>> = repository.sessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _geofenceState = MutableStateFlow<GeofenceRegistrationState?>(null)
    val geofenceState: StateFlow<GeofenceRegistrationState?> = _geofenceState

    private val _geofenceConfig = MutableStateFlow(geofenceConfigStore.load())
    val geofenceConfig: StateFlow<GeofenceConfig?> = _geofenceConfig

    fun refreshGeofence() {
        viewModelScope.launch {
            _geofenceState.value = repository.ensureGeofenceRegistered()
        }
    }

    fun saveGeofenceConfig(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            geofenceConfigStore.save(latitude, longitude)
            _geofenceConfig.value = geofenceConfigStore.load()
            refreshGeofence()
        }
    }

    fun clearGeofenceConfig() {
        viewModelScope.launch {
            geofenceConfigStore.clear()
            _geofenceConfig.value = null
            refreshGeofence()
        }
    }

    fun addManualSession(date: LocalDate, startTime: LocalTime, endTime: LocalTime, note: String?) {
        viewModelScope.launch {
            runCatching { repository.addManualSession(date, startTime, endTime, note) }
                .onFailure { _geofenceState.value = GeofenceRegistrationState.Error(it.message ?: "Speichern fehlgeschlagen") }
        }
    }

    fun updateSession(
        id: Long,
        startTimestamp: Long,
        endTimestamp: Long?,
        note: String?,
        source: SessionSource,
    ) {
        viewModelScope.launch {
            runCatching { repository.updateSession(id, startTimestamp, endTimestamp, note, source) }
                .onFailure { _geofenceState.value = GeofenceRegistrationState.Error(it.message ?: "Session konnte nicht gespeichert werden") }
        }
    }

    fun deleteSession(session: WorkSessionEntity) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    fun syncWorkTimeAlerts() {
        viewModelScope.launch {
            repository.syncWorkTimeAlerts()
        }
    }

    fun resetMessage() {
        _geofenceState.value = null
    }

    fun manualZone(): ZoneId = ZoneId.systemDefault()

    fun currentMonth(): YearMonth = YearMonth.now()
}
