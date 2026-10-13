package com.timelog.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.widget.Toast
import com.timelog.app.TimeLogApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TimeLogApplication
        val pending = goAsync()
        val wake = context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TimeLog:reminder")
        wake.acquire(10_000)
        app.scope.launch {
            try {
                withTimeout(9_000) {
                    val event = parseEvent(intent.getStringExtra(EVENT_EXTRA)) ?: return@withTimeout
                    when (intent.action) {
                        ACTION_REGULAR, ACTION_SNOOZE_ALARM -> app.reminders.deliver(event)
                        ACTION_DISMISS -> app.reminders.dismiss(event)
                        ACTION_SNOOZE -> if (!app.reminders.snooze(event)) withContext(Dispatchers.Main) {
                            Toast.makeText(app, "Snooze unavailable. Fill this gap in Time Log.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (_: Exception) { app.reminderOutput.markFailure() }
            finally { if (wake.isHeld) wake.release(); pending.finish() }
        }
    }
}

class ReminderReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TimeLogApplication
        val pending = goAsync()
        app.scope.launch {
            try { withTimeout(9_000) {
                app.reminders.reconcile(invalidate = intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED)
            } }
            catch (_: Exception) { app.reminderOutput.markFailure() }
            finally { pending.finish() }
        }
    }
}
