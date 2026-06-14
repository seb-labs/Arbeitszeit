package de.seb.arbeitszeitapp.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.seb.arbeitszeitapp.R
import de.seb.arbeitszeitapp.data.WorkSessionEntity
import de.seb.arbeitszeitapp.domain.TimeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

object WorkTimeAlertScheduler {
    private const val CHANNEL_ID = "worktime_thresholds"
    private const val PREFS_NAME = "worktime_thresholds_state"
    private const val KEY_LAST_DATE = "last_date"
    private const val KEY_LAST_TOTAL = "last_total_minutes"
    private const val KEY_NOTIFIED_7_5 = "notified_7_5"
    private const val KEY_NOTIFIED_8_5 = "notified_8_5"
    private const val KEY_NOTIFIED_9 = "notified_9"
    private const val KEY_NOTIFIED_9_5 = "notified_9_5"
    private const val KEY_NOTIFIED_10 = "notified_10"
    private const val THRESHOLD_7_5 = 7 * 60 + 30
    private const val THRESHOLD_8_5 = 8 * 60 + 30
    private const val THRESHOLD_9 = 9 * 60
    private const val THRESHOLD_9_5 = 9 * 60 + 30
    private const val THRESHOLD_10 = 10 * 60
    private const val REQUEST_CODE_7_5 = 91075
    private const val REQUEST_CODE_8_5 = 9108
    private const val REQUEST_CODE_9 = 9109
    private const val REQUEST_CODE_9_5 = 9115
    private const val REQUEST_CODE_10 = 9110

    data class DebugState(
        val todayMinutes: Int,
        val yesterdayMinutes: Int,
        val openAutoSession: Boolean,
        val earlyLeaveReminderEnabled: Boolean,
        val sevenPointFiveReached: Boolean,
        val sevenPointFiveNotified: Boolean,
        val eightPointFiveNotified: Boolean,
        val nineNotified: Boolean,
        val ninePointFiveNotified: Boolean,
        val tenNotified: Boolean,
        val shouldNotifySevenPointFiveNow: Boolean,
        val storedDateMatchesToday: Boolean,
    )

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Arbeitszeit-Warnungen",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Warnungen bei 8,5, 9, 9,5 und 10 Stunden Anwesenheit"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    suspend fun sync(context: Context, sessions: List<WorkSessionEntity>, zoneId: ZoneId = ZoneId.systemDefault()) {
        withContext(Dispatchers.Default) {
            ensureChannel(context)
            val today = LocalDate.now(zoneId)
            val summary = TimeCalculator.todaySummary(sessions, today, zoneId = zoneId)
            val currentTotal = summary.totalMinutes
            val yesterdayTotal = TimeCalculator.todaySummary(sessions, today.minusDays(1), zoneId = zoneId).totalMinutes
            val earlyLeaveReminderEnabled = yesterdayTotal >= THRESHOLD_9_5
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val storedDate = prefs.getString(KEY_LAST_DATE, null)
            val notified7_5 = if (storedDate == today.toString()) prefs.getBoolean(KEY_NOTIFIED_7_5, false) else false
            val notified8_5 = if (storedDate == today.toString()) prefs.getBoolean(KEY_NOTIFIED_8_5, false) else false
            val notified9 = if (storedDate == today.toString()) prefs.getBoolean(KEY_NOTIFIED_9, false) else false
            val notified9_5 = if (storedDate == today.toString()) prefs.getBoolean(KEY_NOTIFIED_9_5, false) else false
            val notified10 = if (storedDate == today.toString()) prefs.getBoolean(KEY_NOTIFIED_10, false) else false

            val newlyNotified7_5 = earlyLeaveReminderEnabled && currentTotal >= THRESHOLD_7_5 && !notified7_5 && showNotification(
                    context = context,
                    notificationId = REQUEST_CODE_7_5,
                    title = "Mach heute mal was früher frei :-)",
                    text = "Mach heute mal was früher frei :-)",
                )
            val newlyNotified8_5 = currentTotal >= THRESHOLD_8_5 && !notified8_5 && showNotification(
                    context = context,
                    notificationId = THRESHOLD_8_5,
                    title = "Soll erreicht.",
                    text = "8,5 Stunden Arbeitszeit erreicht.",
                )
            val newlyNotified9 = currentTotal >= THRESHOLD_9 && !notified9 && showNotification(
                    context = context,
                    notificationId = THRESHOLD_9,
                    title = "9 Stunden überschritten",
                    text = "Achtung, 9 Stunden Anwesenheit überschritten. Bald Feierabend machen.",
                )
            val newlyNotified9_5 = currentTotal >= THRESHOLD_9_5 && !notified9_5 && showNotification(
                    context = context,
                    notificationId = THRESHOLD_9_5,
                    title = "Achtung! 9,5 Stunden!",
                    text = "Achtung! 9,5 Stunden!",
                )
            val newlyNotified10 = currentTotal >= THRESHOLD_10 && !notified10 && showNotification(
                    context = context,
                    notificationId = THRESHOLD_10,
                    title = "10 Stunden überschritten",
                    text = "10 Stunden überschritten. Jetzt Feierabend machen!",
                )
            

            prefs.edit()
                .putString(KEY_LAST_DATE, today.toString())
                .putInt(KEY_LAST_TOTAL, currentTotal)
                .putBoolean(KEY_NOTIFIED_7_5, notified7_5 || newlyNotified7_5)
                .putBoolean(KEY_NOTIFIED_8_5, notified8_5 || newlyNotified8_5)
                .putBoolean(KEY_NOTIFIED_9, notified9 || newlyNotified9)
                .putBoolean(KEY_NOTIFIED_9_5, notified9_5 || newlyNotified9_5)
                .putBoolean(KEY_NOTIFIED_10, notified10 || newlyNotified10)
                .apply()

            rescheduleAlarms(context, sessions, currentTotal, zoneId)
        }
    }

    fun debugState(context: Context, sessions: List<WorkSessionEntity>, zoneId: ZoneId = ZoneId.systemDefault()): DebugState {
        val today = LocalDate.now(zoneId)
        val summary = TimeCalculator.todaySummary(sessions, today, zoneId = zoneId)
        val yesterdayTotal = TimeCalculator.todaySummary(sessions, today.minusDays(1), zoneId = zoneId).totalMinutes
        val openAutoSession = sessions.any { it.source == de.seb.arbeitszeitapp.data.SessionSource.AUTO && it.endTimestamp == null }
        val earlyLeaveReminderEnabled = yesterdayTotal >= THRESHOLD_9_5
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedDate = prefs.getString(KEY_LAST_DATE, null)
        val storedDateMatchesToday = storedDate == today.toString()
        val notified7_5 = if (storedDateMatchesToday) prefs.getBoolean(KEY_NOTIFIED_7_5, false) else false
        val notified8_5 = if (storedDateMatchesToday) prefs.getBoolean(KEY_NOTIFIED_8_5, false) else false
        val notified9 = if (storedDateMatchesToday) prefs.getBoolean(KEY_NOTIFIED_9, false) else false
        val notified9_5 = if (storedDateMatchesToday) prefs.getBoolean(KEY_NOTIFIED_9_5, false) else false
        val notified10 = if (storedDateMatchesToday) prefs.getBoolean(KEY_NOTIFIED_10, false) else false

        return DebugState(
            todayMinutes = summary.totalMinutes,
            yesterdayMinutes = yesterdayTotal,
            openAutoSession = openAutoSession,
            earlyLeaveReminderEnabled = earlyLeaveReminderEnabled,
            sevenPointFiveReached = summary.totalMinutes >= THRESHOLD_7_5,
            sevenPointFiveNotified = notified7_5,
            eightPointFiveNotified = notified8_5,
            nineNotified = notified9,
            ninePointFiveNotified = notified9_5,
            tenNotified = notified10,
            shouldNotifySevenPointFiveNow = earlyLeaveReminderEnabled && openAutoSession && summary.totalMinutes >= THRESHOLD_7_5 && !notified7_5,
            storedDateMatchesToday = storedDateMatchesToday,
        )
    }

    private fun rescheduleAlarms(context: Context, sessions: List<WorkSessionEntity>, currentTotalMinutes: Int, zoneId: ZoneId) {
        val openSession = sessions.firstOrNull { it.source == de.seb.arbeitszeitapp.data.SessionSource.AUTO && it.endTimestamp == null }
        if (openSession == null) {
            cancelAlarm(context, REQUEST_CODE_7_5)
            cancelAlarm(context, REQUEST_CODE_8_5)
            cancelAlarm(context, REQUEST_CODE_9)
            cancelAlarm(context, REQUEST_CODE_9_5)
            cancelAlarm(context, REQUEST_CODE_10)
            return
        }

        val now = System.currentTimeMillis()
        val today = LocalDate.now(zoneId)
        val yesterdayTotal = TimeCalculator.todaySummary(sessions, today.minusDays(1), zoneId = zoneId).totalMinutes
        val earlyLeaveReminderEnabled = yesterdayTotal >= THRESHOLD_9_5
        if (earlyLeaveReminderEnabled) {
            scheduleOrCancel(context, REQUEST_CODE_7_5, THRESHOLD_7_5, currentTotalMinutes, now)
        } else {
            cancelAlarm(context, REQUEST_CODE_7_5)
        }
        scheduleOrCancel(context, REQUEST_CODE_8_5, THRESHOLD_8_5, currentTotalMinutes, now)
        scheduleOrCancel(context, REQUEST_CODE_9, THRESHOLD_9, currentTotalMinutes, now)
        scheduleOrCancel(context, REQUEST_CODE_9_5, THRESHOLD_9_5, currentTotalMinutes, now)
        scheduleOrCancel(context, REQUEST_CODE_10, THRESHOLD_10, currentTotalMinutes, now)
    }

    private fun scheduleOrCancel(context: Context, requestCode: Int, thresholdMinutes: Int, currentTotalMinutes: Int, now: Long) {
        val remainingMinutes = thresholdMinutes - currentTotalMinutes
        if (remainingMinutes <= 0) {
            cancelAlarm(context, requestCode)
            return
        }

        val triggerAtMillis = now + remainingMinutes * 60_000L
        val intent = Intent(context, WorkTimeThresholdReceiver::class.java).apply {
            putExtra(WorkTimeThresholdReceiver.EXTRA_THRESHOLD_MINUTES, thresholdMinutes)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(context: Context, requestCode: Int) {
        val intent = Intent(context, WorkTimeThresholdReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun showNotification(context: Context, notificationId: Int, title: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }
}
