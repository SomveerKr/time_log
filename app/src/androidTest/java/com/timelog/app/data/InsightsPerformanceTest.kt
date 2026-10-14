package com.timelog.app.data

import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.timelog.app.ui.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InsightsPerformanceTest {
    @Test fun queryAndAggregateTenThousandSyntheticEntries() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "synthetic-insights-${UUID.randomUUID()}.db"
        val db = TimeLogDatabase.open(context, name)
        try {
            val store = TimeLogStore(db)
            store.initialize()
            val categories = db.dao().categories()
            val date = LocalDate.of(2026, 10, 5)
            val zone = ZoneId.of("UTC")
            val range = weekRange(date, zone)
            db.withTransaction {
                repeat(10_000) { index ->
                    val start = range.start + index * MINUTE_MILLIS
                    db.dao().insertEntry(TimeEntry("synthetic-$index", categories[index % categories.size].id,
                        start, start + MINUTE_MILLIS, null, start, start))
                }
            }
            val beforeQuery = SystemClock.elapsedRealtimeNanos()
            val records = store.entriesBetween(range.start - 120 * MINUTE_MILLIS, range.end).first()
            val beforeReport = SystemClock.elapsedRealtimeNanos()
            val report = insightsReport(date, zone, true, records, categories)
            val finished = SystemClock.elapsedRealtimeNanos()
            val queryMillis = (beforeReport - beforeQuery) / 1_000_000.0
            val reportMillis = (finished - beforeReport) / 1_000_000.0
            Log.i("TimeLogPerf", "T06 10000 entries: query=${queryMillis}ms, aggregation=${reportMillis}ms")
            assertEquals(10_000, records.size)
            assertEquals(10_000 * MINUTE_MILLIS, report.totalMillis)
            assertEquals(report.totalMillis, report.days.sumOf { it.millis })
            assertEquals(report.totalMillis, report.categories.sumOf { it.millis })
            assertTrue("Report calculation took over one second: ${reportMillis}ms", reportMillis < 1_000)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
