package com.timelog.app.reminders

import androidx.room.withTransaction
import com.timelog.app.data.*
import com.timelog.app.ui.TimeRange
import com.timelog.app.ui.unloggedRanges
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ReminderOutput {
    fun schedule(event: ScheduledEvent)
    fun cancel(snooze: Boolean)
    fun show(event: ScheduledEvent, alert: Boolean)
    fun dismiss()
}

fun parseEvent(key: String?): ScheduledEvent? = runCatching {
    require(key != null && key.length <= 256)
    val fields = key.split('|')
    require(fields.size == 6 && fields[5] in listOf("true", "false"))
    ZoneId.of(fields[1])
    val start = fields[2].toLong()
    val end = fields[3].toLong()
    val at = fields[4].toLong()
    val snooze = fields[5].toBoolean()
    require(fields[0].toLong() >= 0 && start % MINUTE_MILLIS == 0L && end % MINUTE_MILLIS == 0L)
    require(Math.subtractExact(end, start) in MINUTE_MILLIS..(1440 * MINUTE_MILLIS))
    require(at >= end && Math.subtractExact(at, end) <= 1440 * MINUTE_MILLIS && (snooze || at == end))
    ScheduledEvent(fields[0].toLong(), fields[1], ReminderBlock(start, end), at, snooze)
}.getOrNull()

class ReminderController(
    private val store: TimeLogStore,
    private val output: ReminderOutput,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val mutex = Mutex()
    private val db get() = store.database
    private val dao get() = db.dao()

    suspend fun reconcile(invalidate: Boolean = false) = mutex.withLock {
        store.initialize()
        if (invalidate || dao.deviceState()!!.zoneId != zone().id) store.invalidateSchedule(zone().id)
        reconcileLocked()
    }

    private suspend fun reconcileLocked() {
        val plan = db.withTransaction {
            val settings = dao.settings()!!
            var state = dao.deviceState()!!
            val regular = if (settings.remindersEnabled) ScheduledEvent(state.generation, zone().id,
                nextBlock(settings, clock, zone())) else null
            val active = parseEvent(state.activeEventKey)?.takeIf { validTarget(it, state, settings) && hasGap(it.block) }
            val snooze = state.snoozeAtMillis?.let { at ->
                val start = state.snoozeBlockStartMillis ?: return@let null
                val end = state.snoozeBlockEndMillis ?: return@let null
                ScheduledEvent(state.generation, state.zoneId, ReminderBlock(start, end), at, true)
            }?.takeIf { settings.remindersEnabled && it.atMillis > clock.millis() && it.atMillis < regular!!.atMillis &&
                validTarget(it, state, settings) && hasGap(it.block) }
            state = state.copy(activeEventKey = active?.key, snoozeAtMillis = snooze?.atMillis,
                snoozeBlockStartMillis = snooze?.block?.startMillis, snoozeBlockEndMillis = snooze?.block?.endMillis)
            dao.saveDeviceState(state)
            Plan(regular, snooze, active)
        }
        output.cancel(false); output.cancel(true)
        plan.regular?.let(output::schedule)
        plan.snooze?.let(output::schedule)
        if (plan.active == null) output.dismiss() else output.show(plan.active, alert = false)
    }

    suspend fun deliver(event: ScheduledEvent) = mutex.withLock {
        store.initialize()
        val show = db.withTransaction {
            val settings = dao.settings()!!
            val state = dao.deviceState()!!
            if (event.snooze && (state.snoozeAtMillis != event.atMillis ||
                state.snoozeBlockStartMillis != event.block.startMillis || state.snoozeBlockEndMillis != event.block.endMillis)) return@withTransaction null
            val block = deliveryBlock(event, state.generation, settings, clock, zone()) ?: return@withTransaction null
            val delivered = if (event.snooze) event else ScheduledEvent(state.generation, zone().id, block)
            if (state.lastHandledEventKey == delivered.key) return@withTransaction null
            val active = delivered.takeIf { hasGap(block) }
            dao.saveDeviceState(state.copy(lastHandledEventKey = delivered.key, activeEventKey = active?.key,
                snoozeAtMillis = null, snoozeBlockStartMillis = null, snoozeBlockEndMillis = null))
            active
        }
        show?.let { output.show(it, alert = true) }
        reconcileLocked()
    }

    suspend fun snooze(event: ScheduledEvent): Boolean = mutex.withLock {
        val accepted = db.withTransaction {
            val settings = dao.settings()!!
            val state = dao.deviceState()!!
            if (state.activeEventKey != event.key || !hasGap(event.block)) return@withTransaction false
            val snoozed = snoozeEvent(event, state.generation, settings, clock, zone()) ?: return@withTransaction false
            dao.saveDeviceState(state.copy(activeEventKey = null, snoozeAtMillis = snoozed.atMillis,
                snoozeBlockStartMillis = snoozed.block.startMillis, snoozeBlockEndMillis = snoozed.block.endMillis))
            true
        }
        reconcileLocked()
        accepted
    }

    suspend fun dismiss(event: ScheduledEvent) = mutex.withLock {
        db.withTransaction {
            val state = dao.deviceState() ?: return@withTransaction
            if (state.activeEventKey == event.key) dao.saveDeviceState(state.copy(activeEventKey = null))
        }
    }

    suspend fun refreshCoverage() = mutex.withLock {
        db.withTransaction {
            val state = dao.deviceState() ?: return@withTransaction
            val active = parseEvent(state.activeEventKey)
            val clearActive = active != null && !hasGap(active.block)
            val clearSnooze = state.snoozeBlockStartMillis?.let { start ->
                state.snoozeBlockEndMillis?.let { end -> !hasGap(ReminderBlock(start, end)) }
            } == true
            if (clearActive || clearSnooze) {
                dao.saveDeviceState(state.copy(activeEventKey = if (clearActive) null else state.activeEventKey,
                    snoozeAtMillis = if (clearSnooze) null else state.snoozeAtMillis,
                    snoozeBlockStartMillis = if (clearSnooze) null else state.snoozeBlockStartMillis,
                    snoozeBlockEndMillis = if (clearSnooze) null else state.snoozeBlockEndMillis))
                if (clearActive) output.dismiss()
                if (clearSnooze) output.cancel(true)
            }
        }
    }

    suspend fun prepareTest() = mutex.withLock {
        db.withTransaction {
            val state = dao.deviceState() ?: return@withTransaction
            dao.saveDeviceState(state.copy(activeEventKey = null))
        }
        output.dismiss()
    }

    suspend fun remainingGaps(event: ScheduledEvent): List<TimeRange> = mutex.withLock {
        db.withTransaction {
            val state = dao.deviceState()!!
            val settings = dao.settings()!!
            if ((event.key != state.activeEventKey && event.key != state.lastHandledEventKey) ||
                !validTarget(event, state, settings)) return@withTransaction emptyList()
            val gaps = gaps(event.block)
            if (state.activeEventKey == event.key) dao.saveDeviceState(state.copy(activeEventKey = null))
            output.dismiss()
            gaps
        }
    }

    private fun validTarget(event: ScheduledEvent, state: DeviceReminderState, settings: AppSettings): Boolean {
        if (!settings.remindersEnabled || event.generation != state.generation || event.zoneId != zone().id) return false
        val date = clock.instant().atZone(zone()).toLocalDate()
        return event.block in dailyBlocks(settings, date, zone()) && event.block.endMillis <= clock.millis() &&
            clock.instant() <= resolveLocalMinute(date, settings.trackingEndMinute, zone())
    }

    private suspend fun hasGap(block: ReminderBlock) = gaps(block).isNotEmpty()
    private suspend fun gaps(block: ReminderBlock): List<TimeRange> = unloggedRanges(TimeRange(block.startMillis, block.endMillis),
        dao.entriesInRange(block.startMillis, block.endMillis))
    private data class Plan(val regular: ScheduledEvent?, val snooze: ScheduledEvent?, val active: ScheduledEvent?)
}
