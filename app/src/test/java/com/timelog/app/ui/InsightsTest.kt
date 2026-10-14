package com.timelog.app.ui

import com.timelog.app.data.Category
import com.timelog.app.data.MINUTE_MILLIS
import com.timelog.app.data.TimeEntry
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class InsightsTest {
    private val zone = ZoneId.of("UTC")
    private val monday = LocalDate.of(2026, 10, 12)
    private val work = Category("work", "Work", "work", 0)
    private val health = Category("health", "Health", "health", 1)
    private fun entry(category: Category, start: Long, end: Long) = TimeEntry("${category.id}-$start", category.id, start, end, null, 0, 0)
    private fun report(date: LocalDate = monday, weekly: Boolean = false, records: List<TimeEntry>, categories: List<Category> = listOf(work, health)) =
        insightsReport(date, zone, weekly, records, categories)

    @Test fun referenceFixtureHasNinetyMinutesAndCorrectShares() {
        val start = dayRange(monday, zone).start + 9 * 60 * MINUTE_MILLIS
        val result = report(records = listOf(entry(work, start, start + 60 * MINUTE_MILLIS), entry(health, start + 60 * MINUTE_MILLIS, start + 90 * MINUTE_MILLIS)))
        assertEquals(90 * MINUTE_MILLIS, result.totalMillis)
        assertEquals(listOf("work", "health"), result.categories.map { it.category.id })
        assertEquals(2.0 / 3, result.categories[0].millis.toDouble() / result.totalMillis, 0.00001)
        assertEquals(1.0 / 3, result.categories[1].millis.toDouble() / result.totalMillis, 0.00001)
    }

    @Test fun midnightAndMondaySplitAcrossDaysAndWeeks() {
        val boundary = dayRange(monday, zone).start
        val records = listOf(entry(work, boundary - 30 * MINUTE_MILLIS, boundary + 30 * MINUTE_MILLIS))
        assertEquals(30 * MINUTE_MILLIS, report(records = records).totalMillis)
        assertEquals(30 * MINUTE_MILLIS, report(monday.minusDays(1), records = records).totalMillis)
        assertEquals(30 * MINUTE_MILLIS, report(weekly = true, records = records).totalMillis)
        assertEquals(30 * MINUTE_MILLIS, report(monday.minusDays(1), true, records).totalMillis)
        assertEquals(monday, weekStart(monday.plusDays(6)))
    }

    @Test fun weeklyTotalsMatchDaysAndIncludeFiveZeroDays() {
        val start = dayRange(monday, zone).start
        val records = listOf(entry(work, start, start + 60 * MINUTE_MILLIS), entry(health, start + 1440 * MINUTE_MILLIS, start + 1470 * MINUTE_MILLIS))
        val result = report(monday.plusDays(3), true, records)
        assertEquals(7, result.days.size)
        assertEquals(5, result.days.count { it.millis == 0L })
        assertEquals(result.totalMillis, result.days.sumOf { it.millis })
        assertEquals(result.totalMillis, result.categories.sumOf { it.millis })
        result.days.forEach { assertEquals(it.millis, report(it.date, records = records).totalMillis) }
    }

    @Test fun dstAndTimezoneUseElapsedInstantsWithinLocalDates() {
        val newYork = ZoneId.of("America/New_York")
        listOf(LocalDate.of(2026, 3, 8) to 3, LocalDate.of(2026, 11, 1) to 5).forEach { (date, hours) ->
            val start = date.atStartOfDay(newYork).toInstant().toEpochMilli()
            val end = date.atTime(4, 0).atZone(newYork).toInstant().toEpochMilli()
            val result = insightsReport(date, newYork, true, listOf(entry(work, start, end)), listOf(work))
            assertEquals(hours * 60 * MINUTE_MILLIS, result.totalMillis)
            assertEquals(result.totalMillis, result.days.sumOf { it.millis })
        }
        val midnight = dayRange(monday, zone).start
        val records = listOf(entry(work, midnight, midnight + 30 * MINUTE_MILLIS))
        assertEquals(0L, insightsReport(monday, newYork, false, records, listOf(work)).totalMillis)
        assertEquals(30 * MINUTE_MILLIS, insightsReport(monday.minusDays(1), newYork, false, records, listOf(work)).totalMillis)
    }

    @Test fun archiveAndRenameRetainHistoryAndSortTiesStably() {
        val start = dayRange(monday, zone).start
        val records = listOf(entry(work, start, start + MINUTE_MILLIS), entry(health, start + MINUTE_MILLIS, start + 2 * MINUTE_MILLIS))
        val result = report(records = records)
        assertEquals(listOf("health", "work"), result.categories.map { it.category.id })
        assertEquals(result, report(records = records.reversed(), categories = listOf(health, work)))
        val renamed = work.copy(name = "A project", normalizedName = "a project", archived = true)
        val updated = report(records = records, categories = listOf(renamed, health))
        assertEquals(renamed, updated.categories.first().category)
        assertEquals(result.totalMillis, updated.totalMillis)
    }

    @Test fun emptyReportsHaveNoCategorySharesAndSevenZeroDays() {
        val result = report(weekly = true, records = emptyList())
        assertEquals(0L, result.totalMillis)
        assertTrue(result.categories.isEmpty())
        assertEquals(7, result.days.size)
        assertTrue(result.days.all { it.millis == 0L })
    }
}
