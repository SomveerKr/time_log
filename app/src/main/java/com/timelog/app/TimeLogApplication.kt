package com.timelog.app

import android.app.Application
import com.timelog.app.data.TimeLogDatabase
import com.timelog.app.data.TimeLogStore
import com.timelog.app.reminders.AndroidReminderOutput
import com.timelog.app.reminders.ReminderController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TimeLogApplication : Application() {
    val store by lazy { TimeLogStore(TimeLogDatabase.open(this)) }
    val reminderOutput by lazy { AndroidReminderOutput(this) }
    val reminders by lazy { ReminderController(store, reminderOutput) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            try {
                store.initialize()
                // A cold alarm must claim its pending snooze before any future-only startup reconciliation.
                store.database.dao().observeEntryChanges().collect { reminders.refreshCoverage() }
            } catch (_: Exception) { reminderOutput.markFailure() }
        }
    }
}
