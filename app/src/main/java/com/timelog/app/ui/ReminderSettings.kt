package com.timelog.app.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.timelog.app.data.AppSettings
import com.timelog.app.reminders.dailyBlocks
import com.timelog.app.reminders.nextBlock
import com.timelog.app.reminders.validSchedule
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SetupScreen(vm: TimeLogViewModel, onEnable: (AppSettings) -> Unit) {
    var start by rememberSaveable { mutableIntStateOf(vm.settings!!.trackingStartMinute) }
    var end by rememberSaveable { mutableIntStateOf(vm.settings!!.trackingEndMinute) }
    var interval by rememberSaveable { mutableIntStateOf(vm.settings!!.intervalMinutes) }
    val value = vm.settings!!.copy(trackingStartMinute = start, trackingEndMinute = end, intervalMinutes = interval)
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("A simple check-in.", style = MaterialTheme.typography.headlineMedium)
        Text("Choose the hours you want to track. Record a time block when it suits you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ScheduleControls(value, !vm.busy, { start = it }, { end = it }, { interval = it })
        SchedulePreview(value, vm)
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onEnable(value.copy(onboardingComplete = true, remindersEnabled = true)) },
            enabled = !vm.busy && validSchedule(value), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("enableReminders")) { Text("Enable reminders") }
        TextButton(onClick = { vm.saveReminderSettings(vm.settings!!.copy(onboardingComplete = true, remindersEnabled = false)) },
            enabled = !vm.busy) { Text("Set up later") }
    }
}

@Composable
fun ReminderSettings(vm: TimeLogViewModel, onEnable: (AppSettings) -> Unit, onTest: () -> Unit) {
    val value = vm.settings ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Reminders", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Reminders")
                Text(vm.reminderStatus,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = value.remindersEnabled, onCheckedChange = {
                if (it) onEnable(value.copy(remindersEnabled = true)) else vm.saveReminderSettings(value.copy(remindersEnabled = false))
            }, enabled = !vm.busy, modifier = Modifier.testTag("reminderSwitch"))
        }
        ScheduleControls(value, !vm.busy, { vm.saveReminderSettings(value.copy(trackingStartMinute = it)) },
            { vm.saveReminderSettings(value.copy(trackingEndMinute = it)) },
            { vm.saveReminderSettings(value.copy(intervalMinutes = it)) })
        SchedulePreview(value, vm)
        if (value.remindersEnabled && vm.reminderOutput?.notificationsAllowed() == true && vm.reminderOutput.failure == null) {
            val next = nextBlock(value, Clock.fixed(Instant.ofEpochMilli(vm.now), vm.zone), vm.zone)
            Text("Next scheduled check-in: ${formattedInstant(next.endMillis, vm.zone, includeDate = true)}", style = MaterialTheme.typography.bodyMedium)
        }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val context = LocalContext.current
        if (vm.reminderOutput?.notificationsAllowed() == false && value.remindersEnabled) TextButton(onClick = {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text("Open notification settings") }
        if (android.os.Build.VERSION.SDK_INT >= 31 && vm.reminderOutput?.exactAllowed() == false) {
            Text("Precise reminders are optional. Otherwise timing may vary.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Allow precise reminders") }
        }
        OutlinedButton(onClick = onTest, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().testTag("sendTestReminder")) { Text("Send test reminder") }
        Text("Force-stop and phone battery restrictions can prevent reminders. Reopen Time Log after a force-stop or permission change.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScheduleControls(value: AppSettings, enabled: Boolean, onStart: (Int) -> Unit, onEnd: (Int) -> Unit, onInterval: (Int) -> Unit) {
    MinuteControl("Tracking start", "trackingStart", value.trackingStartMinute, enabled, onStart)
    MinuteControl("Tracking end", "trackingEnd", value.trackingEndMinute, enabled, onEnd)
    Text("Check in every", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(60, 120).forEach { minutes ->
            FilterChip(selected = value.intervalMinutes == minutes, onClick = { onInterval(minutes) }, enabled = enabled,
                label = { Text("$minutes minutes") }, modifier = Modifier.heightIn(min = 48.dp).testTag("interval$minutes"))
        }
    }
}

@Composable
private fun MinuteControl(label: String, tag: String, minute: Int, enabled: Boolean, onChanged: (Int) -> Unit) {
    val context = LocalContext.current
    val use24 = DateFormat.is24HourFormat(context)
    val locale = LocalLocale.current.platformLocale
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f).padding(top = 14.dp))
        TextButton(onClick = {
            TimePickerDialog(context, { _, hour, minutes -> onChanged(hour * 60 + minutes) }, minute / 60, minute % 60, use24).show()
        }, enabled = enabled, modifier = Modifier.testTag(tag)) {
            Text(LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern(if (use24) "HH:mm" else "h:mm a", locale)))
        }
    }
}

@Composable
private fun SchedulePreview(value: AppSettings, vm: TimeLogViewModel) {
    if (!validSchedule(value)) {
        Text("Choose an end after start on the same day.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("schedulePreview"))
        return
    }
    val blocks = dailyBlocks(value, localDate(vm.now, vm.zone), vm.zone)
    Text(if (blocks.isEmpty()) "No positive time block in this local window today." else
        "First check-in ${formattedInstant(blocks.first().endMillis, vm.zone)}, last check-in ${formattedInstant(blocks.last().endMillis, vm.zone)}",
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("schedulePreview"))
}

@Composable
fun formattedInstant(millis: Long, zone: ZoneId, includeDate: Boolean = false): String {
    val use24 = DateFormat.is24HourFormat(LocalContext.current)
    val time = if (use24) "HH:mm" else "h:mm a"
    return Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern(
        if (includeDate) "d MMM, $time" else time, LocalLocale.current.platformLocale))
}
