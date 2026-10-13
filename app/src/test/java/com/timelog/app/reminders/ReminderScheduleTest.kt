package com.timelog.app.reminders

import com.timelog.app.data.AppSettings
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ReminderScheduleTest {
    private val date = LocalDate.of(2026, 10, 13)
    private val utc = ZoneId.of("UTC")
    private val settings = AppSettings(remindersEnabled = true)
    private fun clock(value: String) = Clock.fixed(Instant.parse(value), ZoneOffset.UTC)
    private fun end(block: ReminderBlock) = Instant.ofEpochMilli(block.endMillis)
    private fun event(block: ReminderBlock, zone: ZoneId = utc, generation: Long = 7) = ScheduledEvent(generation, zone.id, block)

    @Test fun nextBoundaryIsStrictlyFutureBeforeAtAndAfterBoundaries() {
        for ((now, expected) in listOf("08:00:00Z" to "10:00:00Z", "09:00:00Z" to "10:00:00Z",
            "09:59:59Z" to "10:00:00Z", "10:00:00Z" to "11:00:00Z", "10:00:01Z" to "11:00:00Z")) {
            assertEquals(Instant.parse("2026-10-13T$expected"), end(nextBlock(settings, clock("2026-10-13T$now"), utc)))
        }
        assertEquals(Instant.parse("2026-10-14T10:00:00Z"), end(nextBlock(settings, clock("2026-10-13T22:00:00Z"), utc)))
        assertEquals(Instant.parse("2026-10-14T10:00:00Z"), end(nextBlock(settings, clock("2026-10-13T23:59:59Z"), utc)))
    }

    @Test fun finalPartialAndShortWindowsKeepTheExactWindowEnd() {
        val partial = dailyBlocks(settings.copy(trackingEndMinute = 750, intervalMinutes = 120), date, utc)
        assertEquals(listOf(Instant.parse("2026-10-13T11:00:00Z"), Instant.parse("2026-10-13T12:30:00Z")), partial.map(::end))
        assertEquals(listOf(120L, 90L), partial.map { (it.endMillis - it.startMillis) / 60_000 })
        val short = dailyBlocks(settings.copy(trackingEndMinute = 570), date, utc)
        assertEquals(1, short.size)
        assertEquals(Instant.parse("2026-10-13T09:30:00Z"), end(short.single()))
    }

    @Test fun invalidWindowsAndIntervalsAreRejected() {
        listOf(settings.copy(trackingEndMinute = 540), settings.copy(trackingEndMinute = 400),
            settings.copy(trackingStartMinute = -1), settings.copy(trackingEndMinute = 1440),
            settings.copy(intervalMinutes = 15), settings.copy(id = 2)).forEach {
            assertFalse(validSchedule(it))
            assertThrows(IllegalArgumentException::class.java) { dailyBlocks(it, date, utc) }
        }
    }

    @Test fun enableAndEditsNeverReturnPastReminderBursts() {
        val now = clock("2026-10-13T15:30:00Z")
        assertEquals(Instant.parse("2026-10-13T16:00:00Z"), end(nextBlock(settings, now, utc)))
        assertEquals(Instant.parse("2026-10-13T17:00:00Z"), end(nextBlock(settings.copy(intervalMinutes = 120), now, utc)))
        assertEquals(Instant.parse("2026-10-13T17:30:00Z"), end(nextBlock(settings.copy(trackingStartMinute = 570, intervalMinutes = 120), now, utc)))
    }

    @Test fun dstGapsMoveToNextValidInstantAndDeduplicateBoundaries() {
        val zone = ZoneId.of("America/New_York")
        val gapDate = LocalDate.of(2026, 3, 8)
        assertEquals(Instant.parse("2026-03-08T07:00:00Z"), resolveLocalMinute(gapDate, 150, zone))
        val blocks = dailyBlocks(settings.copy(trackingStartMinute = 60, trackingEndMinute = 240), gapDate, zone)
        assertEquals(2, blocks.size)
        assertEquals(listOf(Instant.parse("2026-03-08T07:00:00Z"), Instant.parse("2026-03-08T08:00:00Z")), blocks.map(::end))
        assertTrue(blocks.all { it.endMillis > it.startMillis })
    }

    @Test fun repeatedLocalBoundaryUsesFirstOccurrenceWithActualElapsedDuration() {
        val zone = ZoneId.of("America/New_York")
        val repeatedDate = LocalDate.of(2026, 11, 1)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), resolveLocalMinute(repeatedDate, 90, zone))
        val blocks = dailyBlocks(settings.copy(trackingStartMinute = 30, trackingEndMinute = 180), repeatedDate, zone)
        assertEquals(listOf(60L, 120L, 30L), blocks.map { (it.endMillis - it.startMillis) / 60_000 })
        assertEquals(210L, blocks.sumOf { (it.endMillis - it.startMillis) / 60_000 })
    }

    @Test fun collapsedGapWindowAndSkippedCalendarDateAdvanceSafely() {
        val zone = ZoneId.of("America/New_York")
        val value = settings.copy(trackingStartMinute = 120, trackingEndMinute = 150)
        assertTrue(dailyBlocks(value, LocalDate.of(2026, 3, 8), zone).isEmpty())
        assertEquals(Instant.parse("2026-03-09T06:30:00Z"), end(nextBlock(value, clock("2026-03-08T06:00:00Z"), zone)))
        assertTrue(dailyBlocks(settings, LocalDate.of(2011, 12, 30), ZoneId.of("Pacific/Apia")).isEmpty())
    }

    @Test fun timezoneChangeRecalculatesAndRejectsOldTimezoneEvent() {
        val now = clock("2026-10-13T09:30:00Z")
        assertEquals(Instant.parse("2026-10-13T10:00:00Z"), end(nextBlock(settings, now, utc)))
        val changed = ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2026-10-13T14:00:00Z"), end(nextBlock(settings, now, changed)))
        val old = event(dailyBlocks(settings, date, utc).first())
        assertNull(deliveryBlock(old, 7, settings, clock("2026-10-13T15:00:00Z"), changed))
    }

    @Test fun delayedEventPresentsOnlyLatestCompletedBlockInsideWindow() {
        val blocks = dailyBlocks(settings, date, utc)
        assertNull(latestCompletedBlock(settings, clock("2026-10-13T09:59:59Z"), utc))
        assertEquals(blocks[1], deliveryBlock(event(blocks.first()), 7, settings, clock("2026-10-13T11:20:00Z"), utc))
        assertNull(deliveryBlock(event(blocks.first()), 8, settings, clock("2026-10-13T11:20:00Z"), utc))
        assertNull(deliveryBlock(event(blocks.first()), 7, settings.copy(remindersEnabled = false), clock("2026-10-13T11:20:00Z"), utc))
        assertNull(deliveryBlock(event(blocks.first()), 7, settings, clock("2026-10-13T09:59:59Z"), utc))
        assertNull(deliveryBlock(event(blocks.first()), 7, settings, clock("2026-10-14T11:00:00Z"), utc))
    }

    @Test fun finalIsAtEndOnlyAndDelayedFinalIsDiscarded() {
        val final = dailyBlocks(settings, date, utc).last()
        assertEquals(final, deliveryBlock(event(final), 7, settings, clock("2026-10-13T22:00:00Z"), utc))
        assertNull(deliveryBlock(event(final), 7, settings, clock("2026-10-13T22:00:00.001Z"), utc))
    }

    @Test fun snoozeRetainsBlockAndDoesNotMoveRegularSchedule() {
        val first = dailyBlocks(settings, date, utc).first()
        val snoozed = snoozeEvent(event(first), 7, settings, clock("2026-10-13T10:00:00Z"), utc)!!
        assertEquals(first, snoozed.block)
        assertTrue(snoozed.snooze)
        assertEquals(Instant.parse("2026-10-13T10:15:00Z"), Instant.ofEpochMilli(snoozed.atMillis))
        assertEquals(Instant.parse("2026-10-13T11:00:00Z"), end(nextBlock(settings, clock("2026-10-13T10:00:00Z"), utc)))
        assertEquals(first, deliveryBlock(snoozed, 7, settings, clock("2026-10-13T10:15:00Z"), utc))
        assertNull(deliveryBlock(snoozed, 7, settings, clock("2026-10-13T10:14:59Z"), utc))
    }

    @Test fun regularReminderSupersedesSnoozeAndEndRejectsIt() {
        val blocks = dailyBlocks(settings, date, utc)
        val first = event(blocks.first())
        assertNull(snoozeEvent(first, 7, settings, clock("2026-10-13T10:45:00Z"), utc))
        assertNull(snoozeEvent(first, 7, settings, clock("2026-10-13T10:50:00Z"), utc))
        assertNull(snoozeEvent(event(blocks.last()), 7, settings, clock("2026-10-13T22:00:00Z"), utc))
        val snoozed = snoozeEvent(first, 7, settings, clock("2026-10-13T10:00:00Z"), utc)!!
        assertNull(deliveryBlock(snoozed, 7, settings, clock("2026-10-13T11:00:00Z"), utc))
        assertNull(deliveryBlock(snoozed, 8, settings, clock("2026-10-13T10:15:00Z"), utc))
        assertNull(snoozeEvent(first, 7, settings, clock("2026-10-14T10:00:00Z"), utc))
        assertNull(snoozeEvent(first, 8, settings, clock("2026-10-13T10:00:00Z"), utc))
    }
}
