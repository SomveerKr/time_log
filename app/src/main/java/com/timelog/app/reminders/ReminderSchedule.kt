package com.timelog.app.reminders

import com.timelog.app.data.AppSettings
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ReminderBlock(val startMillis: Long, val endMillis: Long)
data class ScheduledEvent(
    val generation: Long,
    val zoneId: String,
    val block: ReminderBlock,
    val atMillis: Long = block.endMillis,
    val snooze: Boolean = false,
) {
    val key get() = "$generation|$zoneId|${block.startMillis}|${block.endMillis}|$atMillis|$snooze"
}

fun validSchedule(settings: AppSettings) = settings.id == 1 && settings.trackingStartMinute in 0..1439 &&
    settings.trackingEndMinute in 0..1439 && settings.trackingEndMinute > settings.trackingStartMinute &&
    settings.intervalMinutes in listOf(60, 120)

fun resolveLocalMinute(date: LocalDate, minute: Int, zone: ZoneId): Instant {
    val local = date.atStartOfDay().plusMinutes(minute.toLong())
    val offsets = zone.rules.getValidOffsets(local)
    return if (offsets.isNotEmpty()) local.toInstant(offsets.first())
    else zone.rules.getTransition(local)!!.let { it.dateTimeAfter.toInstant(it.offsetAfter) }
}

fun dailyBlocks(settings: AppSettings, date: LocalDate, zone: ZoneId): List<ReminderBlock> {
    require(validSchedule(settings))
    val start = resolveLocalMinute(date, settings.trackingStartMinute, zone).toEpochMilli()
    val boundaries = mutableListOf<Long>()
    var minute = settings.trackingStartMinute + settings.intervalMinutes
    while (minute < settings.trackingEndMinute) {
        boundaries += resolveLocalMinute(date, minute, zone).toEpochMilli()
        minute += settings.intervalMinutes
    }
    boundaries += resolveLocalMinute(date, settings.trackingEndMinute, zone).toEpochMilli()
    var previous = start
    return boundaries.distinct().sorted().mapNotNull { end ->
        if (end > previous) ReminderBlock(previous, end).also { previous = end } else null
    }
}

fun nextBlock(settings: AppSettings, clock: Clock, zone: ZoneId): ReminderBlock {
    require(validSchedule(settings))
    val now = clock.instant()
    var date = now.atZone(zone).toLocalDate()
    while (true) {
        dailyBlocks(settings, date, zone).firstOrNull { Instant.ofEpochMilli(it.endMillis) > now }?.let { return it }
        // A skipped local date or a window entirely in a DST gap has no positive block.
        date = date.plusDays(1)
    }
}

fun latestCompletedBlock(settings: AppSettings, clock: Clock, zone: ZoneId): ReminderBlock? {
    require(validSchedule(settings))
    val now = clock.instant()
    val date = now.atZone(zone).toLocalDate()
    val end = resolveLocalMinute(date, settings.trackingEndMinute, zone)
    if (now > end) return null
    return dailyBlocks(settings, date, zone).lastOrNull { Instant.ofEpochMilli(it.endMillis) <= now }
}

fun snoozeEvent(event: ScheduledEvent, generation: Long, settings: AppSettings, clock: Clock, zone: ZoneId): ScheduledEvent? {
    if (!settings.remindersEnabled || event.generation != generation || event.zoneId != zone.id) return null
    val now = clock.millis()
    val date = Instant.ofEpochMilli(event.block.endMillis).atZone(zone).toLocalDate()
    if (clock.instant().atZone(zone).toLocalDate() != date || now < event.block.endMillis) return null
    if (event.block !in dailyBlocks(settings, date, zone)) return null
    val target = now + 15 * 60_000L
    val end = resolveLocalMinute(date, settings.trackingEndMinute, zone).toEpochMilli()
    val next = dailyBlocks(settings, date, zone).firstOrNull { it.endMillis > now }
    if (target > end || (next != null && next.endMillis <= target)) return null
    return event.copy(atMillis = target, snooze = true)
}

fun deliveryBlock(event: ScheduledEvent, generation: Long, settings: AppSettings, clock: Clock, zone: ZoneId): ReminderBlock? {
    if (!settings.remindersEnabled || event.generation != generation || event.zoneId != zone.id ||
        clock.millis() < event.atMillis) return null
    val today = clock.instant().atZone(zone).toLocalDate()
    if (event.block !in dailyBlocks(settings, today, zone)) return null
    val latest = latestCompletedBlock(settings, clock, zone) ?: return null
    if (!event.snooze) return latest
    if (latest.endMillis > event.block.endMillis) return null
    return event.block
}
