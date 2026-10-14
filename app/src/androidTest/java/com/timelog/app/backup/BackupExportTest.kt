package com.timelog.app.backup

import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.timelog.app.data.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BackupExportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "synthetic-export-${UUID.randomUUID()}.db"
    private val clock = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: TimeLogDatabase
    private lateinit var store: TimeLogStore
    private lateinit var export: BackupExport
    @Before fun setup() = runBlocking {
        db = TimeLogDatabase.open(context, name); store = TimeLogStore(db, clock)
        store.initialize()
        context.getSharedPreferences("backup_metadata", 0).edit().clear().commit()
        export = BackupExport(context, store, clock)
    }
    @After fun cleanup() {
        db.close(); context.deleteDatabase(name)
        context.getSharedPreferences("backup_metadata", 0).edit().clear().commit()
    }

    @Test fun allPortableFieldsUnicodeAndArchivedReferencesAreWrittenWithoutDeviceState() = runBlocking {
        val category = store.createCategory("\u5b66\u4e60 \uD83D\uDCDA")
        val record = store.save(EntryInput(category.id, clock.millis() - 60 * MINUTE_MILLIS, clock.millis(), "Quote: \"hello\"\n\u0928\u092e\u0938\u094d\u0924\u0947"))
        store.setCategoryArchived(category.id, true)
        val settings = AppSettings(onboardingComplete = true, trackingStartMinute = 600, trackingEndMinute = 1210, intervalMinutes = 120, remindersEnabled = true)
        store.saveSettings(settings)
        val device = db.dao().deviceState()!!
        db.dao().saveDeviceState(device.copy(lastHandledEventKey = "private-device-marker", snoozeAtMillis = clock.millis()))
        val output = ByteArrayOutputStream()
        assertEquals(clock.millis(), export.exportTo { output })
        val raw = output.toString("UTF-8")
        val json = JSONObject(raw)
        assertEquals(setOf("format", "version", "exportedAtMillis", "categories", "entries", "settings"), json.keys().asSequence().toSet())
        assertEquals(BACKUP_FORMAT, json.getString("format")); assertEquals(1, json.getInt("version"))
        assertEquals(clock.millis(), json.getLong("exportedAtMillis"))
        val categories = json.getJSONArray("categories")
        val saved = (0 until categories.length()).map { categories.getJSONObject(it) }.single { it.getString("id") == category.id }
        assertEquals(category.name, saved.getString("name")); assertEquals(category.normalizedName, saved.getString("normalizedName"))
        assertEquals(category.colorToken, saved.getInt("colorToken")); assertTrue(saved.getBoolean("archived"))
        val entry = json.getJSONArray("entries").getJSONObject(0)
        assertEquals(record.id, entry.getString("id")); assertEquals(category.id, entry.getString("categoryId"))
        assertEquals(record.startMillis, entry.getLong("startMillis")); assertEquals(record.endMillis, entry.getLong("endMillis"))
        assertEquals(record.note, entry.getString("note")); assertEquals(record.createdMillis, entry.getLong("createdMillis"))
        assertEquals(record.updatedMillis, entry.getLong("updatedMillis"))
        val portable = json.getJSONObject("settings")
        assertEquals(1, portable.getInt("id")); assertTrue(portable.getBoolean("onboardingComplete"))
        assertEquals(600, portable.getInt("trackingStartMinute")); assertEquals(1210, portable.getInt("trackingEndMinute"))
        assertEquals(120, portable.getInt("intervalMinutes")); assertTrue(portable.getBoolean("remindersEnabled"))
        assertFalse(raw.contains("private-device-marker")); assertFalse(raw.contains("lastExportMillis"))
        assertEquals(clock.millis(), BackupExport(context, store, clock).lastSuccess())
    }

    @Test fun emptyHistoryAndNullNoteRemainValidJson() = runBlocking {
        val empty = ByteArrayOutputStream(); export.exportTo { empty }
        assertEquals(0, JSONObject(empty.toString("UTF-8")).getJSONArray("entries").length())
        assertEquals(8, JSONObject(empty.toString("UTF-8")).getJSONArray("categories").length())
        store.save(EntryInput(db.dao().categories().first().id, clock.millis() - MINUTE_MILLIS, clock.millis()))
        val output = ByteArrayOutputStream(); export.exportTo { output }
        val entry = JSONObject(output.toString("UTF-8")).getJSONArray("entries").getJSONObject(0)
        assertTrue(entry.has("note")); assertTrue(entry.isNull("note"))
    }

    @Test fun cancelOpenPartialWriteAndCloseFailuresDoNotClaimSuccessOrChangeHistory() = runBlocking {
        val category = db.dao().categories().first()
        store.save(EntryInput(category.id, clock.millis() - MINUTE_MILLIS, clock.millis()))
        val before = store.backupSnapshot(clock.millis())
        val device = db.dao().deviceState()
        assertNull(export.export(null)); assertNull(export.lastSuccess())
        val existing = 1234L
        context.getSharedPreferences("backup_metadata", 0).edit().putLong("lastExportMillis", existing).commit()
        val outputs = listOf<() -> OutputStream?>(
            { null },
            { object : OutputStream() {
                var bytes = 0
                override fun write(value: Int) { if (++bytes > 50) throw IOException("Synthetic no space") }
            } },
            { object : ByteArrayOutputStream() { override fun close() { super.close(); throw IOException("Synthetic provider close failure") } } },
        )
        outputs.forEach { open ->
            var failed = false
            try { export.exportTo(open) } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(existing, export.lastSuccess())
            assertEquals(before, store.backupSnapshot(clock.millis()))
            assertEquals(device, db.dao().deviceState())
        }
    }

    @Test fun snapshotCannotMixAnAtomicRenameAndSettingsChange() = runBlocking {
        val category = db.dao().categories().first()
        store.renameCategory(category.id, "Snapshot 60")
        coroutineScope {
            val writer = async {
                repeat(20) { index ->
                    val interval = if (index % 2 == 0) 120 else 60
                    db.withTransaction {
                        store.renameCategory(category.id, "Snapshot $interval")
                        store.saveSettings(AppSettings(intervalMinutes = interval))
                    }
                }
            }
            val reader = async {
                repeat(20) {
                    val snapshot = store.backupSnapshot(clock.millis())
                    assertEquals("Snapshot ${snapshot.settings.intervalMinutes}", snapshot.categories.single { it.id == category.id }.name)
                }
            }
            writer.await(); reader.await()
        }
    }
}
