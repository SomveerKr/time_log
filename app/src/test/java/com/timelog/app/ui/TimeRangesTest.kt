package com.timelog.app.ui

import com.timelog.app.data.TimeEntry
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeRangesTest {
    private fun entry(start: Long, end: Long) = TimeEntry("id", "category", start, end, null, 0, 0)

    @Test fun existingPartialAndOutsideIntervalsSubtractTheirUnion() {
        val records = listOf(entry(-20, 20), entry(10, 40), entry(40, 50), entry(70, 110), entry(120, 150))
        assertEquals(listOf(TimeRange(50, 70)), unloggedRanges(TimeRange(0, 100), records))
        assertEquals(listOf(TimeRange(0, 100)), unloggedRanges(TimeRange(0, 100), emptyList()))
        assertEquals(emptyList<TimeRange>(), unloggedRanges(TimeRange(0, 100), listOf(entry(-10, 120))))
    }

    @Test fun futureTimeIsNotMissingAndOutsideTimeStillCountsInDailyTotal() {
        val zone = ZoneId.of("UTC")
        val date = LocalDate.of(2026, 10, 12)
        val base = dayRange(date, zone).start
        val minute = 60_000L
        val records = listOf(entry(base + 8 * 60 * minute, base + 9 * 60 * minute),
            entry(base + 9 * 60 * minute, base + 10 * 60 * minute))
        val elapsed = elapsedWindow(date, zone, 540, 720, base + 11 * 60 * minute)
        assertEquals(60 * minute, unloggedRanges(elapsed, records).sumOf { it.duration })
        assertEquals(120 * minute, loggedDuration(dayRange(date, zone), records))
        assertEquals(emptyList<TimeRange>(), unloggedRanges(elapsedWindow(date, zone, 540, 720, base), records))
    }

    @Test fun overnightRecordsAreClippedToEachLocalDay() {
        val zone = ZoneId.of("Asia/Kolkata")
        val date = LocalDate.of(2026, 10, 12)
        val boundary = dayRange(date, zone).end
        val records = listOf(entry(boundary - 30 * 60_000, boundary + 30 * 60_000))
        assertEquals(30 * 60_000L, loggedDuration(dayRange(date, zone), records))
        assertEquals(30 * 60_000L, loggedDuration(dayRange(date.plusDays(1), zone), records))
    }

    @Test fun calendarDaysUseRealDstDurations() {
        val zone = ZoneId.of("America/New_York")
        assertEquals(23 * 60 * 60_000L, dayRange(LocalDate.of(2026, 3, 8), zone).duration)
        assertEquals(25 * 60 * 60_000L, dayRange(LocalDate.of(2026, 11, 1), zone).duration)
    }
}
