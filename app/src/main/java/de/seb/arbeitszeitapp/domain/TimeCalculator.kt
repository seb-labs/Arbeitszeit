
package de.seb.arbeitszeitapp.domain

import de.seb.arbeitszeitapp.data.DaySummary
import de.seb.arbeitszeitapp.data.RangeSummary
import de.seb.arbeitszeitapp.data.SessionSource
import de.seb.arbeitszeitapp.data.WorkSessionEntity
import de.seb.arbeitszeitapp.data.WorkSessionSlice
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

private const val PARKING_WALK_MINUTES = 5
private const val PAUSE_THRESHOLD_MINUTES = 6 * 60 + 30
private const val PAUSE_DEDUCTION_MINUTES = 30

object TimeCalculator {
    fun currentOpenAutoSession(sessions: List<WorkSessionEntity>): WorkSessionEntity? {
        return sessions.sortedByDescending { it.startTimestamp }
            .firstOrNull { it.source == SessionSource.AUTO && it.endTimestamp == null }
    }

    fun todaySummary(
        sessions: List<WorkSessionEntity>,
        date: LocalDate,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DaySummary = summarizeDay(sessions, date, now, zoneId)

    fun weekSummary(
        sessions: List<WorkSessionEntity>,
        weekAnchor: LocalDate,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): RangeSummary {
        val monday = weekAnchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = (0..6).map { offset -> summarizeDay(sessions, monday.plusDays(offset.toLong()), now, zoneId) }
        return RangeSummary(
            label = monday.toString(),
            totalMinutes = days.sumOf { it.totalMinutes },
            netMinutes = days.sumOf { it.netMinutes },
            days = days,
        )
    }

    fun monthSummary(
        sessions: List<WorkSessionEntity>,
        month: YearMonth,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): RangeSummary {
        val daysInMonth = month.lengthOfMonth()
        val days = (1..daysInMonth).map { day ->
            summarizeDay(sessions, month.atDay(day), now, zoneId)
        }
        return RangeSummary(
            label = month.toString(),
            totalMinutes = days.sumOf { it.totalMinutes },
            netMinutes = days.sumOf { it.netMinutes },
            days = days,
        )
    }

    fun summarizeDay(
        sessions: List<WorkSessionEntity>,
        date: LocalDate,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DaySummary {
        val dayStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val slices = sessions
            .mapNotNull { session -> clipSessionToDay(session, dayStart, dayEnd, now.toEpochMilli()) }
            .sortedBy { it.displayStartTimestamp }
        val grossMinutes = slices.sumOf { it.durationMinutes }
        return DaySummary(
            date = date,
            totalMinutes = grossMinutes,
            netMinutes = netWorkMinutes(grossMinutes),
            sessions = slices,
        )
    }

    fun openSessionDurationMinutes(session: WorkSessionEntity, now: Instant = Instant.now()): Int {
        val end = session.endTimestamp ?: now.toEpochMilli()
        return max(0, ((end - session.startTimestamp) / 60_000L).toInt())
    }

    fun netWorkMinutes(grossMinutes: Int): Int {
        val pauseDeduction = if (grossMinutes > PAUSE_THRESHOLD_MINUTES) PAUSE_DEDUCTION_MINUTES else 0
        return max(0, grossMinutes - PARKING_WALK_MINUTES - pauseDeduction)
    }

    private fun clipSessionToDay(
        session: WorkSessionEntity,
        dayStart: Long,
        dayEnd: Long,
        nowMillis: Long,
    ): WorkSessionSlice? {
        val actualEnd = session.endTimestamp ?: nowMillis
        val actualStart = session.startTimestamp
        if (actualEnd <= dayStart || actualStart >= dayEnd) return null

        val displayStart = max(actualStart, dayStart)
        val displayEnd = minOf(actualEnd, dayEnd)
        if (displayEnd <= displayStart) return null

        return WorkSessionSlice(
            sessionId = session.id,
            source = session.source,
            note = session.note,
            actualStartTimestamp = actualStart,
            actualEndTimestamp = actualEnd,
            displayStartTimestamp = displayStart,
            displayEndTimestamp = displayEnd,
            clipped = displayStart != actualStart || displayEnd != actualEnd,
            open = session.endTimestamp == null,
        )
    }
}
