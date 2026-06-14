
package de.seb.arbeitszeitapp.domain

import de.seb.arbeitszeitapp.data.SessionSource
import de.seb.arbeitszeitapp.data.WorkSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class TimeCalculatorTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun `tagessumme berechnet über sessions hinweg korrekt`() {
        val date = LocalDate.of(2026, 5, 12)
        val sessions = listOf(
            session(date, "08:00", "12:00"),
            session(date, "12:30", "16:30"),
        )
        val summary = TimeCalculator.todaySummary(sessions, date, zoneId = zone)
        assertEquals(480, summary.totalMinutes)
        assertEquals(445, summary.netMinutes)
        assertEquals(2, summary.sessions.size)
    }

    @Test
    fun `offene session wird bis jetzt berechnet`() {
        val date = LocalDate.of(2026, 5, 13)
        val now = date.atTime(10, 0).atZone(zone).toInstant()
        val open = WorkSessionEntity(
            startTimestamp = date.atTime(8, 15).atZone(zone).toInstant().toEpochMilli(),
            endTimestamp = null,
            source = SessionSource.AUTO,
            note = null,
            createdAt = now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
        )
        val summary = TimeCalculator.todaySummary(listOf(open), date, now = now, zoneId = zone)
        assertEquals(105, summary.totalMinutes)
        assertEquals(1, summary.sessions.size)
        assertEquals(true, summary.sessions.first().open)
    }

    @Test
    fun `sessions ueber mitternacht werden fuer den tag sauber geschnitten`() {
        val date = LocalDate.of(2026, 5, 14)
        val session = WorkSessionEntity(
            startTimestamp = date.atTime(23, 30).atZone(zone).toInstant().toEpochMilli(),
            endTimestamp = date.plusDays(1).atTime(1, 0).atZone(zone).toInstant().toEpochMilli(),
            source = SessionSource.AUTO,
            note = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val dayOne = TimeCalculator.todaySummary(listOf(session), date, zoneId = zone)
        val dayTwo = TimeCalculator.todaySummary(listOf(session), date.plusDays(1), zoneId = zone)
        assertEquals(30, dayOne.totalMinutes)
        assertEquals(60, dayTwo.totalMinutes)
    }

    @Test
    fun `wochen- und monatsuebersicht summieren tage`() {
        val monday = LocalDate.of(2026, 5, 4)
        val sessions = (0..4).flatMap { offset ->
            val date = monday.plusDays(offset.toLong())
            listOf(session(date, "08:00", "12:00"), session(date, "12:30", "16:30"))
        }
        val week = TimeCalculator.weekSummary(sessions, monday, zoneId = zone)
        val month = TimeCalculator.monthSummary(sessions, YearMonth.of(2026, 5), zoneId = zone)
        assertEquals(2400, week.totalMinutes)
        assertEquals(2225, week.netMinutes)
        assertEquals(2400, month.totalMinutes)
        assertEquals(2225, month.netMinutes)
        assertEquals(7, week.days.size)
        assertEquals(31, month.days.size)
    }

    @Test
    fun `nettoarbeitszeit zieht wegzeit und pausen korrekt ab`() {
        assertEquals(355, TimeCalculator.netWorkMinutes(360))
        assertEquals(445, TimeCalculator.netWorkMinutes(480))
        assertEquals(0, TimeCalculator.netWorkMinutes(4))
    }

    @Test
    fun `current open auto session erkennt nur offene auto session`() {
        val open = WorkSessionEntity(1, 1, null, SessionSource.AUTO, null, 1, 1)
        val closed = WorkSessionEntity(2, 1, 2, SessionSource.AUTO, null, 1, 1)
        val manual = WorkSessionEntity(3, 1, null, SessionSource.MANUAL, null, 1, 1)
        assertEquals(open, TimeCalculator.currentOpenAutoSession(listOf(closed, manual, open)))
        assertNull(TimeCalculator.currentOpenAutoSession(listOf(closed, manual)))
    }

    private fun session(date: LocalDate, start: String, end: String): WorkSessionEntity {
        val startTs = date.atTime(start.split(":")[0].toInt(), start.split(":")[1].toInt()).atZone(zone).toInstant().toEpochMilli()
        val endTs = date.atTime(end.split(":")[0].toInt(), end.split(":")[1].toInt()).atZone(zone).toInstant().toEpochMilli()
        return WorkSessionEntity(
            startTimestamp = startTs,
            endTimestamp = endTs,
            source = SessionSource.AUTO,
            note = null,
            createdAt = startTs,
            updatedAt = endTs,
        )
    }
}
