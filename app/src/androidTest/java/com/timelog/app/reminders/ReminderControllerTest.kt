package com.timelog.app.reminders

import androidx.test.platform.app.InstrumentationRegistry
import com.timelog.app.data.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReminderControllerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "synthetic-reminders-${UUID.randomUUID()}.db"
    private val clock = MovingClock(Instant.parse("2026-10-13T10:00:00Z"))
    private lateinit var db: TimeLogDatabase
    private lateinit var store: TimeLogStore
    private lateinit var controller: ReminderController
    private val output = RecordedOutput()
    private val zone = ZoneId.of("UTC")
    private lateinit var event: ScheduledEvent
    private lateinit var categoryId: String

    @Before fun setup() = runBlocking {
        db = TimeLogDatabase.open(context, name); store = TimeLogStore(db, clock)
        store.initialize()
        store.saveSettings(AppSettings(onboardingComplete = true, remindersEnabled = true))
        controller = ReminderController(store, output, clock) { zone }
        controller.reconcile()
        categoryId = db.dao().categories().first().id
        val settings = db.dao().settings()!!
        event = ScheduledEvent(db.dao().deviceState()!!.generation, zone.id, dailyBlocks(settings, clock.instant().atZone(zone).toLocalDate(), zone).first())
    }

    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    private fun input(start: Long = event.block.startMillis, end: Long = event.block.endMillis) = EntryInput(categoryId, start, end)

    @Test fun concurrentDuplicateDeliveryClaimsOneEventAndOneVisibleNotification() = runBlocking {
        coroutineScope { List(8) { async { controller.deliver(event) } }.forEach { it.await() } }
        assertEquals(1, output.alerted.size)
        assertEquals(event.key, output.visible?.key)
        assertEquals(event.key, db.dao().deviceState()!!.lastHandledEventKey)
        assertEquals(Instant.parse("2026-10-13T11:00:00Z").toEpochMilli(), output.scheduled[false]!!.atMillis)
        controller.dismiss(event); output.visible = null; controller.reconcile()
        assertNull(output.visible)
        controller.deliver(event)
        assertEquals(1, output.alerted.size)
    }

    @Test fun partialCoverageOpensRemainingGapsAndFullCoverageSuppresses() = runBlocking {
        val start = event.block.startMillis
        store.save(input(start + 15 * MINUTE_MILLIS, start + 45 * MINUTE_MILLIS))
        controller.deliver(event)
        val gaps = controller.remainingGaps(event)
        assertEquals(2, gaps.size)
        assertEquals(listOf(15 * MINUTE_MILLIS, 15 * MINUTE_MILLIS), gaps.map { it.duration })
        assertNull(output.visible)
        store.save(input(start, start + 15 * MINUTE_MILLIS))
        store.save(input(start + 45 * MINUTE_MILLIS, event.block.endMillis))
        assertTrue(controller.remainingGaps(event).isEmpty())
        controller.deliver(event)
        assertEquals(1, output.alerted.size)
    }

    @Test fun alreadyLoggedDeliveryIsHandledWithoutNotification() = runBlocking {
        store.save(input()); controller.deliver(event)
        assertTrue(output.alerted.isEmpty())
        assertNull(output.visible)
        assertEquals(event.key, db.dao().deviceState()!!.lastHandledEventKey)
    }

    @Test fun unrelatedSaveKeepsActiveBlockAndCompletingItDismisses() = runBlocking {
        controller.deliver(event)
        store.save(input(event.block.startMillis - 60 * MINUTE_MILLIS, event.block.startMillis))
        controller.refreshCoverage()
        assertEquals(event.key, output.visible!!.key)
        store.save(input()); controller.refreshCoverage()
        assertNull(output.visible)
        assertNull(db.dao().deviceState()!!.activeEventKey)
    }

    @Test fun snoozeRetainsTargetSurvivesReconcileAndIsIdempotent() = runBlocking {
        controller.deliver(event)
        assertTrue(controller.snooze(event))
        assertFalse(controller.snooze(event))
        assertNull(output.visible)
        val snoozed = output.scheduled[true]!!
        assertEquals(event.block, snoozed.block)
        assertEquals(event.block.endMillis + 15 * MINUTE_MILLIS, snoozed.atMillis)
        controller.reconcile()
        assertEquals(snoozed, output.scheduled[true])
        clock.now = Instant.ofEpochMilli(snoozed.atMillis)
        controller.deliver(snoozed); controller.deliver(snoozed)
        assertEquals(2, output.alerted.size)
        assertEquals(snoozed.key, output.visible!!.key)
        assertNull(db.dao().deviceState()!!.snoozeAtMillis)
    }

    @Test fun scheduleChangeCancelsOldEventSnoozeAndNotification() = runBlocking {
        controller.deliver(event); controller.snooze(event)
        val oldSnooze = output.scheduled[true]!!
        store.saveSettings(db.dao().settings()!!.copy(intervalMinutes = 120))
        controller.reconcile(); controller.deliver(event)
        clock.now = Instant.ofEpochMilli(oldSnooze.atMillis); controller.deliver(oldSnooze)
        assertNull(output.visible)
        assertNull(output.scheduled[true])
        assertEquals(1, output.alerted.size)
        assertEquals(Instant.parse("2026-10-13T11:00:00Z").toEpochMilli(), output.scheduled[false]!!.atMillis)
        assertTrue(controller.remainingGaps(event).isEmpty())
    }

    @Test fun reopenOnlySchedulesFutureAndDoesNotBurstPastBlocks() = runBlocking {
        clock.now = Instant.parse("2026-10-13T15:30:00Z")
        controller.reconcile(invalidate = true)
        assertTrue(output.alerted.isEmpty())
        assertEquals(Instant.parse("2026-10-13T16:00:00Z").toEpochMilli(), output.scheduled[false]!!.atMillis)
        store.saveSettings(db.dao().settings()!!.copy(remindersEnabled = false))
        controller.reconcile()
        assertTrue(output.scheduled.isEmpty())
        assertNull(output.visible)
    }

    @Test fun delayedDeliveryAndQuietEndRejectObsoleteTaps() = runBlocking {
        clock.now = Instant.parse("2026-10-13T11:20:00Z"); controller.deliver(event)
        assertEquals(Instant.parse("2026-10-13T11:00:00Z").toEpochMilli(), output.visible!!.block.endMillis)
        val latest = output.visible!!
        clock.now = Instant.parse("2026-10-13T22:00:00.001Z")
        assertTrue(controller.remainingGaps(latest).isEmpty())
        controller.reconcile()
        assertNull(output.visible)
        assertEquals(Instant.parse("2026-10-14T10:00:00Z").toEpochMilli(), output.scheduled[false]!!.atMillis)
    }

    @Test fun explicitTestReplacesVisibleReminderWithoutChangingItsSchedule() = runBlocking {
        controller.deliver(event)
        val planned = output.scheduled.toMap()
        val generation = db.dao().deviceState()!!.generation
        controller.prepareTest()
        assertNull(output.visible)
        assertNull(db.dao().deviceState()!!.activeEventKey)
        assertEquals(event.key, db.dao().deviceState()!!.lastHandledEventKey)
        assertEquals(generation, db.dao().deviceState()!!.generation)
        assertEquals(planned, output.scheduled)
    }

    private class MovingClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }

    private class RecordedOutput : ReminderOutput {
        val scheduled = mutableMapOf<Boolean, ScheduledEvent>()
        val alerted = mutableListOf<ScheduledEvent>()
        var visible: ScheduledEvent? = null
        override fun schedule(event: ScheduledEvent) { scheduled[event.snooze] = event }
        override fun cancel(snooze: Boolean) { scheduled.remove(snooze) }
        override fun show(event: ScheduledEvent, alert: Boolean) { visible = event; if (alert) alerted += event }
        override fun dismiss() { visible = null }
    }
}
