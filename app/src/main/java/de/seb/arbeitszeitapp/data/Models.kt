
package de.seb.arbeitszeitapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class SessionSource {
    AUTO,
    MANUAL,
}

@Entity(tableName = "work_sessions")
data class WorkSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTimestamp: Long,
    val endTimestamp: Long? = null,
    val source: SessionSource,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class WorkSessionSlice(
    val sessionId: Long,
    val source: SessionSource,
    val note: String?,
    val actualStartTimestamp: Long,
    val actualEndTimestamp: Long,
    val displayStartTimestamp: Long,
    val displayEndTimestamp: Long,
    val clipped: Boolean,
    val open: Boolean,
) {
    val durationMinutes: Int
        get() = ((displayEndTimestamp - displayStartTimestamp) / 60_000L).toInt().coerceAtLeast(0)
}

data class DaySummary(
    val date: LocalDate,
    val totalMinutes: Int,
    val netMinutes: Int,
    val sessions: List<WorkSessionSlice>,
)

data class RangeSummary(
    val label: String,
    val totalMinutes: Int,
    val netMinutes: Int,
    val days: List<DaySummary>,
) {
    val sessionCount: Int
        get() = days.sumOf { it.sessions.size }
}

data class PermissionSnapshot(
    val fineLocationGranted: Boolean,
    val backgroundLocationGranted: Boolean,
    val locationServicesEnabled: Boolean,
    val playServicesAvailable: Boolean,
) {
    val hasForegroundLocation: Boolean
        get() = fineLocationGranted

    val requiresBackgroundPermission: Boolean
        get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q

    val hasAllPermissions: Boolean
        get() = fineLocationGranted && locationServicesEnabled && playServicesAvailable && (!requiresBackgroundPermission || backgroundLocationGranted)
}

sealed interface GeofenceRegistrationState {
    data object Registered : GeofenceRegistrationState

    data object MissingConfiguration : GeofenceRegistrationState

    data class MissingPermissions(val snapshot: PermissionSnapshot) : GeofenceRegistrationState

    data object LocationDisabled : GeofenceRegistrationState

    data object PlayServicesMissing : GeofenceRegistrationState

    data class Error(val message: String) : GeofenceRegistrationState
}
