package com.timelog.app.reminders

import org.junit.Assert.*
import org.junit.Test

class EventParsingTest {
    @Test fun eventRoundTripsAndMalformedInputsAreRejected() {
        val event = ScheduledEvent(1, "UTC", ReminderBlock(60_000, 120_000))
        assertEquals(event, parseEvent(event.key))
        assertEquals(event.copy(atMillis = 180_000, snooze = true), parseEvent("1|UTC|60000|120000|180000|true"))
        listOf(null, "", "x".repeat(257), "1|UTC|60000|120000|120000|maybe", "1|bad zone|60000|120000|120000|false",
            "-1|UTC|60000|120000|120000|false", "1|UTC|60001|120000|120000|false",
            "1|UTC|60000|120000|60000|false", "1|UTC|120000|60000|120000|false",
            "1|UTC|-9223372036854720000|9223372036854720000|9223372036854720000|false").forEach { assertNull(it, parseEvent(it)) }
    }
}
