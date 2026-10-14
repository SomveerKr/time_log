package com.timelog.app.ui

import android.app.DatePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.timelog.app.R
import com.timelog.app.reminders.nextBlock
import java.time.Clock

fun rangeText(start: Long, end: Long, zone: ZoneId, use24Hour: Boolean = true, locale: Locale = Locale.getDefault()): String {
    val first = Instant.ofEpochMilli(start).atZone(zone)
    val last = Instant.ofEpochMilli(end).atZone(zone)
    val time = if (use24Hour) "HH:mm" else "h:mm a"
    val pattern = if (first.toLocalDate() == last.toLocalDate()) time else "d MMM $time"
    val formatter = DateTimeFormatter.ofPattern(pattern, locale)
    return "${first.format(formatter)} - ${last.format(formatter)}"
}

fun pickDate(context: Context, date: LocalDate, maxDate: Long? = null, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        date.year, date.monthValue - 1, date.dayOfMonth).apply {
        maxDate?.let { datePicker.maxDate = it }
    }.show()
}

@Composable
fun RefreshLocalTime(vm: TimeLogViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, vm) {
        var registered = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { vm.refresh(reconcileReminders = intent.action != Intent.ACTION_TIME_TICK) }
        }
        fun start() {
            vm.refresh()
            if (registered) return
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else { @Suppress("DEPRECATION") context.registerReceiver(receiver, filter) }
            registered = true
        }
        fun stop() { if (registered) { context.unregisterReceiver(receiver); registered = false } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> vm.refresh()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose { stop(); lifecycle.removeObserver(observer) }
    }
}

@Composable
fun TodayScreen(vm: TimeLogViewModel) {
    val context = LocalContext.current
    val use24 = DateFormat.is24HourFormat(context)
    val locale = LocalLocale.current.platformLocale
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = { vm.selectDate(vm.date.minusDays(1)) }, modifier = Modifier.size(48.dp).testTag("previousDay")) {
                    Icon(painterResource(R.drawable.ic_back), "Previous day")
                }
                TextButton(onClick = { pickDate(context, vm.date, vm.now) { vm.selectDate(it) } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("selectedDate")) {
                    Text(vm.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)), textAlign = TextAlign.Center)
                }
                IconButton(onClick = { vm.selectDate(vm.date.plusDays(1)) }, enabled = !vm.today, modifier = Modifier.size(48.dp).testTag("nextDay")) {
                    Icon(painterResource(R.drawable.ic_back), "Next day", Modifier.rotate(180f))
                }
            }
            Text(vm.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale),
                style = MaterialTheme.typography.titleLarge)
            Text(vm.reminderStatus,
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            vm.settings?.takeIf { it.remindersEnabled && vm.reminderOutput?.notificationsAllowed() == true && vm.reminderOutput.failure == null }?.let {
                val next = nextBlock(it, Clock.fixed(Instant.ofEpochMilli(vm.now), vm.zone), vm.zone)
                Text("Next scheduled check-in: ${formattedInstant(next.endMillis, vm.zone, includeDate = true)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Button(onClick = { vm.openNew() }, enabled = vm.ready && !vm.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("logTime")) { Text(stringResource(R.string.log_time)) }
        }
        if (vm.dataError != null) item {
            Text(vm.dataError!!, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = vm::reload) { Text("Retry") }
        }
        else if (vm.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (vm.ready) {
            item {
                Text(stringResource(R.string.logged_time), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(durationText(loggedDuration(vm.day, vm.visibleEntries)), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag("loggedTotal"))
            }
            if (vm.today) item {
                Row(Modifier.fillMaxWidth()) {
                    Text("${durationText(vm.gaps.sumOf { it.duration })} unlogged in today's window",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (vm.gaps.isNotEmpty()) TextButton(onClick = { vm.openNew(fillGap = true) }) { Text("Fill a gap") }
                }
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            if (vm.visibleEntries.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.today_empty_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.today_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(vm.visibleEntries, key = { it.id }) { entry ->
                Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Edit time entry") { vm.edit(entry) }
                    .padding(vertical = 12.dp).testTag("entry-${entry.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(rangeText(entry.startMillis, entry.endMillis, vm.zone, use24, locale), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(vm.categories.find { it.id == entry.categoryId }?.name.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(durationText(entry.endMillis - entry.startMillis))
                    }
                    entry.note?.takeIf(String::isNotEmpty)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
