package de.seb.arbeitszeitapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import de.seb.arbeitszeitapp.data.WorkSessionRepository

class WorkTimeThresholdReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = WorkSessionRepository(context.applicationContext)
                WorkTimeAlertScheduler.sync(context.applicationContext, repository.getAllSessions())
            } catch (_: Exception) {
                // Intentionally silent; the next repository update will resync notifications.
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_THRESHOLD_MINUTES = "threshold_minutes"
    }
}
