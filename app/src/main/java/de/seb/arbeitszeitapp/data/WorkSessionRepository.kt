
package de.seb.arbeitszeitapp.data

import android.content.Context
import com.google.android.gms.location.Geofence
import de.seb.arbeitszeitapp.domain.TimeCalculator
import de.seb.arbeitszeitapp.geofence.GeofenceManager
import de.seb.arbeitszeitapp.notifications.WorkTimeAlertScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WorkSessionRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = WorkDatabase.getInstance(appContext)
    private val dao = database.workSessionDao()
    private val mutex = Mutex()
    private val geofenceManager = GeofenceManager(appContext)

    val sessions: Flow<List<WorkSessionEntity>> = dao.observeAllSessions()

    suspend fun addManualSession(
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        note: String?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        val start = date.atTime(startTime).atZone(zoneId).toInstant().toEpochMilli()
        val end = date.atTime(endTime).atZone(zoneId).toInstant().toEpochMilli()
        require(end > start) { "Ende muss nach dem Start liegen." }
        saveValidatedSession(
            session = WorkSessionEntity(
                startTimestamp = start,
                endTimestamp = end,
                source = SessionSource.MANUAL,
                note = note?.takeIf { it.isNotBlank() },
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        syncWorkTimeAlerts()
    }

    suspend fun updateSession(
        id: Long,
        startTimestamp: Long,
        endTimestamp: Long?,
        note: String?,
        source: SessionSource,
    ) {
        mutex.withLock {
            val existing = dao.getSessionById(id) ?: throw IllegalArgumentException("Session nicht gefunden.")
            val validationEnd = endTimestamp ?: System.currentTimeMillis()
            validateRange(startTimestamp, validationEnd, id)
            if (endTimestamp != null) {
                require(endTimestamp > startTimestamp) { "Ende muss nach dem Start liegen." }
            }
            dao.update(
                existing.copy(
                    startTimestamp = startTimestamp,
                    endTimestamp = endTimestamp,
                    source = source,
                    note = note?.takeIf { it.isNotBlank() },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            syncWorkTimeAlerts()
        }
    }

    suspend fun deleteSession(session: WorkSessionEntity) = withContext(Dispatchers.IO) {
        dao.delete(session)
        syncWorkTimeAlerts()
    }

    suspend fun getAllSessions(): List<WorkSessionEntity> = withContext(Dispatchers.IO) {
        dao.getAllSessions()
    }

    suspend fun handleGeofenceTransition(transitionType: Int, timestamp: Long) {
        mutex.withLock {
            when (transitionType) {
                Geofence.GEOFENCE_TRANSITION_ENTER,
                Geofence.GEOFENCE_TRANSITION_DWELL,
                -> {
                    if (dao.findOpenSessionBySource(SessionSource.AUTO) == null) {
                        dao.insert(
                            WorkSessionEntity(
                                startTimestamp = timestamp,
                                endTimestamp = null,
                                source = SessionSource.AUTO,
                                note = null,
                                createdAt = timestamp,
                                updatedAt = timestamp,
                            ),
                        )
                    }
                }

                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    val open = dao.findOpenSessionBySource(SessionSource.AUTO) ?: return
                    if (timestamp <= open.startTimestamp) return
                    dao.update(
                        open.copy(
                            endTimestamp = timestamp,
                            updatedAt = timestamp,
                        ),
                    )
                }
            }
            syncWorkTimeAlerts()
        }
    }

    suspend fun ensureGeofenceRegistered(): GeofenceRegistrationState = geofenceManager.ensureRegistered()

    suspend fun syncWorkTimeAlerts() {
        WorkTimeAlertScheduler.sync(appContext, dao.getAllSessions())
    }

    private suspend fun saveValidatedSession(session: WorkSessionEntity) {
        mutex.withLock {
            validateRange(session.startTimestamp, session.endTimestamp ?: System.currentTimeMillis(), excludeId = session.id)
            dao.insert(session)
        }
    }

    private suspend fun validateRange(startTimestamp: Long, validationEnd: Long, excludeId: Long) {
        val overlaps = dao.findOverlappingSessions(startTimestamp, validationEnd, excludeId)
        require(overlaps.isEmpty()) { "Die Zeit überschneidet sich mit einer bestehenden Session." }
    }
}
