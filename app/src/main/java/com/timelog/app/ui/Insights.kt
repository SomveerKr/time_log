package com.timelog.app.ui

import com.timelog.app.data.Category
import com.timelog.app.data.TimeEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
fun weekRange(date: LocalDate, zone: ZoneId) = TimeRange(
    dayRange(weekStart(date), zone).start,
    dayRange(weekStart(date).plusDays(6), zone).end,
)

data class CategoryTotal(val category: Category, val millis: Long)
data class DayTotal(val date: LocalDate, val millis: Long)
data class InsightsReport(val totalMillis: Long, val categories: List<CategoryTotal>, val days: List<DayTotal>)

fun insightsReport(date: LocalDate, zone: ZoneId, weekly: Boolean, entries: List<TimeEntry>, categories: List<Category>): InsightsReport {
    val range = if (weekly) weekRange(date, zone) else dayRange(date, zone)
    val totals = mutableMapOf<String, Long>()
    entries.forEach { entry ->
        val clipped = (minOf(range.end, entry.endMillis) - maxOf(range.start, entry.startMillis)).coerceAtLeast(0)
        if (clipped > 0) totals[entry.categoryId] = (totals[entry.categoryId] ?: 0) + clipped
    }
    val rows = categories.mapNotNull { category -> totals[category.id]?.let { CategoryTotal(category, it) } }
        .sortedWith(compareByDescending<CategoryTotal> { it.millis }.thenBy { it.category.normalizedName }.thenBy { it.category.id })
    val start = if (weekly) weekStart(date) else date
    val days = List(if (weekly) 7 else 1) { index ->
        val day = start.plusDays(index.toLong())
        DayTotal(day, loggedDuration(dayRange(day, zone), entries))
    }
    return InsightsReport(totals.values.sum(), rows, days)
}
