package com.timelog.app.ui

import com.timelog.app.data.TimeEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.timelog.app.reminders.resolveLocalMinute

data class TimeRange(val start: Long, val end: Long) {
    val duration: Long get() = (end - start).coerceAtLeast(0)
}

fun dayRange(date: LocalDate, zone: ZoneId) = TimeRange(
    date.atStartOfDay(zone).toInstant().toEpochMilli(),
    date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)

fun unloggedRanges(range: TimeRange, entries: List<TimeEntry>): List<TimeRange> {
    if (range.end <= range.start) return emptyList()
    val gaps = mutableListOf<TimeRange>()
    var cursor = range.start
    entries.sortedBy { it.startMillis }.forEach { entry ->
        val start = maxOf(range.start, entry.startMillis)
        val end = minOf(range.end, entry.endMillis)
        if (end > start) {
            if (start > cursor) gaps += TimeRange(cursor, start)
            cursor = maxOf(cursor, end)
        }
    }
    if (cursor < range.end) gaps += TimeRange(cursor, range.end)
    return gaps
}

fun elapsedWindow(date: LocalDate, zone: ZoneId, startMinute: Int, endMinute: Int, now: Long): TimeRange {
    val start = resolveLocalMinute(date, startMinute, zone).toEpochMilli()
    val end = resolveLocalMinute(date, endMinute, zone).toEpochMilli()
    return TimeRange(start, minOf(end, now))
}

fun loggedDuration(range: TimeRange, entries: List<TimeEntry>) = entries.sumOf {
    (minOf(range.end, it.endMillis) - maxOf(range.start, it.startMillis)).coerceAtLeast(0)
}

fun durationText(millis: Long): String {
    val minutes = millis.coerceAtLeast(0) / 60_000
    return when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

fun localDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
