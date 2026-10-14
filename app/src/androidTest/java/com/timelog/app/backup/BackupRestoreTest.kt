package com.timelog.app.backup

import androidx.test.platform.app.InstrumentationRegistry
import com.timelog.app.data.*
import com.timelog.app.reminders.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BackupRestoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "synthetic-restore-${UUID.randomUUID()}.db"
    private val clock = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: TimeLogDatabase
    private lateinit var store: TimeLogStore
    private lateinit var controller: ReminderController
    private lateinit var backup: BackupSnapshot
    private val output = Output()

    @Before fun setup() = runBlocking {
        db = TimeLogDatabase.open(context, name); store = TimeLogStore(db, clock); store.initialize()
        val category = store.createCategory("\u5b66\u4e60 \uD83D\uDCDA")
        store.save(EntryInput(category.id, clock.millis() - 60 * MINUTE_MILLIS, clock.millis(), "Note\n\"quoted\" \u0928\u092e\u0938\u094d\u0924\u0947"))
        store.setCategoryArchived(category.id, true)
        store.saveSettings(AppSettings(onboardingComplete = true, trackingStartMinute = 540, trackingEndMinute = 1210, intervalMinutes = 120, remindersEnabled = true))
        backup = store.backupSnapshot(clock.millis())
        controller = ReminderController(store, output, clock) { ZoneOffset.UTC }
        controller.reconcile()
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    private fun bytes(value: BackupSnapshot = backup) = ByteArrayOutputStream().also { writeBackup(value, it) }.toByteArray()
    private fun parse(value: ByteArray) = readBackup(ByteArrayInputStream(value), clock.millis())
    private fun json() = JSONObject(String(bytes(), Charsets.UTF_8))
    private fun rejected(value: ByteArray) {
        try { parse(value); fail("Invalid backup accepted") } catch (_: BackupValidationException) { }
    }
    private suspend fun unchanged(before: BackupSnapshot, device: DeviceReminderState?, calls: Int) {
        assertEquals(before, store.backupSnapshot(clock.millis()))
        assertEquals(device, db.dao().deviceState()); assertEquals(calls, output.calls)
    }

    @Test fun roundTripIntoFreshDatabasePreservesAllFieldsExceptReminderEnablement() = runBlocking {
        val parsed = parse(bytes())
        assertEquals(backup.categories.toSet(), parsed.categories.toSet()); assertEquals(backup.entries, parsed.entries)
        assertEquals(backup.settings, parsed.settings)
        val freshName = "synthetic-fresh-${UUID.randomUUID()}.db"
        val fresh = TimeLogDatabase.open(context, freshName)
        try {
            val target = TimeLogStore(fresh, clock); target.initialize()
            assertTrue(ReminderController(target, Output(), clock) { ZoneOffset.UTC }.restore(parsed))
            assertEquals(backup.copy(settings = backup.settings.copy(remindersEnabled = false)), target.backupSnapshot(clock.millis()))
            assertNull(fresh.dao().deviceState()!!.activeEventKey)
        } finally { fresh.close(); context.deleteDatabase(freshName) }
    }

    @Test fun malformedTypesVersionsNamesIdsReferencesAndTimesLeaveDataAndScheduleUntouched() = runBlocking {
        val device = db.dao().deviceState(); val calls = output.calls
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("format", "unrelated") }, { it.put("version", 2) }, { it.put("version", "1") },
            { it.remove("settings") }, { it.put("unknown", 1) }, { it.put("exportedAtMillis", -1) },
            { it.getJSONObject("settings").put("remindersEnabled", "true") },
            { it.getJSONObject("settings").put("intervalMinutes", 30) },
            { it.getJSONObject("settings").put("trackingEndMinute", 540) },
            { it.getJSONObject("settings").put("id", 2) },
            { it.getJSONObject("settings").put("trackingStartMinute", 9.5) },
            { it.getJSONObject("settings").put("trackingStartMinute", Long.MAX_VALUE) },
            { it.getJSONArray("categories").getJSONObject(0).put("colorToken", 8) },
            { it.getJSONArray("categories").getJSONObject(0).put("id", "invalid") },
            { it.getJSONArray("categories").getJSONObject(0).put("name", " ") },
            { it.getJSONArray("categories").getJSONObject(0).put("normalizedName", "wrong") },
            { it.getJSONArray("categories").put(it.getJSONArray("categories").getJSONObject(0)) },
            { val duplicate = JSONObject(it.getJSONArray("categories").getJSONObject(0).toString()); duplicate.put("id", UUID.randomUUID().toString()); it.getJSONArray("categories").put(duplicate) },
            { it.getJSONArray("entries").getJSONObject(0).put("categoryId", UUID.randomUUID().toString()) },
            { it.getJSONArray("entries").getJSONObject(0).put("id", "bad") },
            { it.getJSONArray("entries").put(it.getJSONArray("entries").getJSONObject(0)) },
            { val overlap = JSONObject(it.getJSONArray("entries").getJSONObject(0).toString()); overlap.put("id", UUID.randomUUID().toString()); it.getJSONArray("entries").put(overlap) },
            { it.getJSONArray("entries").getJSONObject(0).put("endMillis", clock.millis() + MINUTE_MILLIS) },
            { it.getJSONArray("entries").getJSONObject(0).put("startMillis", clock.millis()) },
            { it.getJSONArray("entries").getJSONObject(0).put("startMillis", clock.millis() - 1) },
            { it.getJSONArray("entries").getJSONObject(0).put("startMillis", clock.millis() - 1441 * MINUTE_MILLIS) },
            { it.getJSONArray("entries").getJSONObject(0).put("startMillis", Long.MIN_VALUE) },
            { it.getJSONArray("entries").getJSONObject(0).put("note", "x".repeat(501)) },
            { it.getJSONArray("entries").getJSONObject(0).put("note", false) },
            { it.getJSONArray("entries").getJSONObject(0).put("updatedMillis", 0) },
        )
        mutations.forEach { mutate -> val value = json(); mutate(value); rejected(value.toString().toByteArray()); unchanged(backup, device, calls) }
        val raw = String(bytes(), Charsets.UTF_8)
        listOf(raw.dropLast(8), raw + "{}", raw.replace("\"version\":1", "\"version\":1,\"version\":1"), "{", "[]", "null").forEach { rejected(it.toByteArray()) }
        rejected(byteArrayOf(0xC3.toByte(), 0x28))
        unchanged(backup, device, calls)
    }

    @Test fun sizeAndEntryLimitsAreEnforcedBeforeMutation() = runBlocking {
        val oversized = ByteArray(MAX_BACKUP_BYTES + 1) { 32 }
        try { parse(oversized); fail("Size limit ignored") }
        catch (error: BackupValidationException) { assertTrue(error.message!!.contains("20 MiB")) }
        val item = "{\"id\":\"\",\"categoryId\":\"\",\"startMillis\":0,\"endMillis\":60000,\"note\":null,\"createdMillis\":0,\"updatedMillis\":0}"
        val tooMany = "{\"entries\":[" + List(MAX_BACKUP_ENTRIES + 1) { item }.joinToString(",") + "]}"
        assertTrue(tooMany.length < MAX_BACKUP_BYTES)
        try { parse(tooMany.toByteArray()); fail("Entry parser limit ignored") }
        catch (error: BackupValidationException) { assertTrue(error.message!!.contains("100,000")) }
        assertEquals(backup, store.backupSnapshot(clock.millis()))
    }

    @Test fun cancelAndValidationFailureKeepExistingScheduleAndData() = runBlocking {
        val device = db.dao().deviceState(); val calls = output.calls
        assertNull(BackupRestore(context, clock).read(null))
        try { controller.restore(backup.copy(settings = backup.settings.copy(intervalMinutes = 5))); fail("Invalid restore") }
        catch (_: BackupValidationException) { }
        unchanged(backup, device, calls)
    }

    @Test fun transactionFailureRollsBackDeletesAndKeepsReminderOutputUnchanged() = runBlocking {
        val device = db.dao().deviceState(); val calls = output.calls
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER synthetic_restore_failure BEFORE INSERT ON entries BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        try {
            try { controller.restore(parse(bytes())); fail("Trigger did not abort") } catch (_: android.database.sqlite.SQLiteException) { }
            unchanged(backup, device, calls)
        } finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER synthetic_restore_failure") }
    }

    @Test fun repeatedRestoreRestartAndStaleEventLeaveRemindersOff() = runBlocking {
        val oldEvent = output.scheduled!!
        val oldGeneration = db.dao().deviceState()!!.generation
        repeat(2) { assertTrue(controller.restore(parse(bytes()))) }
        assertNull(output.scheduled); assertEquals(0, output.shown)
        assertEquals(oldGeneration + 2, db.dao().deviceState()!!.generation)
        db.close(); db = TimeLogDatabase.open(context, name); store = TimeLogStore(db, clock)
        controller = ReminderController(store, output, clock) { ZoneOffset.UTC }
        controller.reconcile(); controller.deliver(oldEvent)
        assertEquals(backup.copy(settings = backup.settings.copy(remindersEnabled = false)), store.backupSnapshot(clock.millis()))
        assertNull(output.scheduled); assertEquals(0, output.shown)
        assertNull(db.dao().deviceState()!!.snoozeAtMillis); assertNull(db.dao().deviceState()!!.lastHandledEventKey)
    }

    @Test fun platformFailureAfterCommitIsReportedAsCommittedAndRecoversOnReconcile() = runBlocking {
        output.failCancel = true
        assertFalse(controller.restore(backup))
        assertFalse(db.dao().settings()!!.remindersEnabled)
        output.failCancel = false; controller.reconcile(); assertNull(output.scheduled)
    }

    @Test fun emptyAndAdjacentRecordsWithNullNotesAreAccepted() {
        val first = backup.entries.first()
        val adjacent = first.copy(id = UUID.randomUUID().toString(), startMillis = first.startMillis - MINUTE_MILLIS, endMillis = first.startMillis, note = null)
        assertEquals(2, parse(bytes(backup.copy(entries = listOf(first, adjacent)))).entries.size)
        assertTrue(parse(bytes(backup.copy(categories = emptyList(), entries = emptyList()))).entries.isEmpty())
    }

    private class Output : ReminderOutput {
        var calls = 0; var shown = 0; var scheduled: ScheduledEvent? = null; var failCancel = false
        override fun schedule(event: ScheduledEvent) { calls++; scheduled = event }
        override fun cancel(snooze: Boolean) { calls++; if (failCancel) error("Synthetic platform failure"); if (!snooze) scheduled = null }
        override fun show(event: ScheduledEvent, alert: Boolean) { calls++; shown++ }
        override fun dismiss() { calls++ }
    }
}
