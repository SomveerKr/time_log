package com.timelog.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.timelog.app.MainActivity
import com.timelog.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val EVENT_EXTRA = "time_log_event"
const val TEST_REQUEST = "test"
const val ACTION_REGULAR = "com.timelog.app.REGULAR"
const val ACTION_SNOOZE_ALARM = "com.timelog.app.SNOOZE_ALARM"
const val ACTION_SNOOZE = "com.timelog.app.SNOOZE"
const val ACTION_DISMISS = "com.timelog.app.DISMISS"
const val REMINDER_CHANNEL = "time_check_ins"
private const val NOTIFICATION_ID = 1

class AndroidReminderOutput(private val context: Context) : ReminderOutput {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    var failure by mutableStateOf<String?>(null); private set

    init {
        notifications.createNotificationChannel(NotificationChannel(REMINDER_CHANNEL, "Time check-ins", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Local reminders to record a time block"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
    }

    fun notificationsAllowed() = (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        notifications.areNotificationsEnabled() && notifications.getNotificationChannel(REMINDER_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun status(enabled: Boolean): String = when {
        !enabled -> "Reminders off"
        !notificationsAllowed() -> "Notifications blocked"
        failure != null -> failure!!
        !exactAllowed() -> "Timing may vary"
        else -> "Reminders on"
    }

    override fun schedule(event: ScheduledEvent) {
        if (!notificationsAllowed()) return
        val pending = alarmIntent(event, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)!!
        try {
            if (exactAllowed()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.atMillis, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.atMillis, pending)
            failure = null
        } catch (_: SecurityException) {
            try { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.atMillis, pending); failure = null }
            catch (_: RuntimeException) { markFailure() }
        } catch (_: RuntimeException) { markFailure() }
    }

    override fun cancel(snooze: Boolean) {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(if (snooze) ACTION_SNOOZE_ALARM else ACTION_REGULAR)
        val pending = PendingIntent.getBroadcast(context, if (snooze) 2 else 1, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) { alarms.cancel(pending); pending.cancel() }
    }

    private fun alarmIntent(event: ScheduledEvent, flags: Int): PendingIntent? = PendingIntent.getBroadcast(context,
        if (event.snooze) 2 else 1, Intent(context, ReminderReceiver::class.java)
            .setAction(if (event.snooze) ACTION_SNOOZE_ALARM else ACTION_REGULAR).putExtra(EVENT_EXTRA, event.key), flags)

    override fun show(event: ScheduledEvent, alert: Boolean) {
        if (!notificationsAllowed()) return
        if (!alert && notifications.activeNotifications.any { it.id == NOTIFICATION_ID && it.notification.extras.getString(EVENT_EXTRA) == event.key }) return
        val formatter = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.getDefault())
        val zone = ZoneId.of(event.zoneId)
        val start = Instant.ofEpochMilli(event.block.startMillis).atZone(zone).format(formatter)
        val end = Instant.ofEpochMilli(event.block.endMillis).atZone(zone).format(formatter)
        val open = openIntent(event.key)
        val snooze = PendingIntent.getBroadcast(context, 3, Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_SNOOZE).putExtra(EVENT_EXTRA, event.key), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val delete = PendingIntent.getBroadcast(context, 4, Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_DISMISS).putExtra(EVENT_EXTRA, event.key), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = Notification.Builder(context, REMINDER_CHANNEL).setSmallIcon(R.drawable.ic_today)
            .setContentTitle("Time check-in").setContentText("Unlock to record your time.").build()
        val notification = Notification.Builder(context, REMINDER_CHANNEL).setSmallIcon(R.drawable.ic_today)
            .setContentTitle("Time check-in").setContentText("What did you do from $start to $end?")
            .setContentIntent(open).setDeleteIntent(delete).setAutoCancel(true).setOnlyAlertOnce(!alert)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public)
            .addAction(Notification.Action.Builder(null, "Log time", open).build())
            .addAction(Notification.Action.Builder(null, "Snooze 15 min", snooze).build())
            .addExtras(android.os.Bundle().apply { putString(EVENT_EXTRA, event.key) })
            .setGroup("time_check_in").setGroupAlertBehavior(if (alert) Notification.GROUP_ALERT_ALL else Notification.GROUP_ALERT_SUMMARY).build()
        try { notifications.notify(NOTIFICATION_ID, notification) } catch (_: SecurityException) { markFailure() }
    }

    override fun dismiss() {
        if (notifications.activeNotifications.any { it.id == NOTIFICATION_ID && it.notification.extras.getString(EVENT_EXTRA) == TEST_REQUEST }) return
        notifications.cancel(NOTIFICATION_ID)
    }

    fun sendTest() {
        if (!notificationsAllowed()) return
        val notification = Notification.Builder(context, REMINDER_CHANNEL).setSmallIcon(R.drawable.ic_today)
            .setContentTitle("Time check-in").setContentText("Test reminder. Open Time Log to record a recent block.")
            .setContentIntent(openIntent(TEST_REQUEST)).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addExtras(android.os.Bundle().apply { putString(EVENT_EXTRA, TEST_REQUEST) }).build()
        try { notifications.notify(NOTIFICATION_ID, notification) } catch (_: SecurityException) { markFailure() }
    }

    private fun openIntent(key: String) = PendingIntent.getActivity(context, if (key == TEST_REQUEST) 6 else 5,
        Intent(context, MainActivity::class.java).putExtra(EVENT_EXTRA, key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun markFailure() { failure = "Reminders could not be refreshed. Reopen the app to retry." }
}
