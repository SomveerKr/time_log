package com.timelog.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReminderSettingsStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun settingsChangesInvalidateSnoozeAtomicallyAndSurviveReopen() = runBlocking {
        val name = "synthetic-settings-${UUID.randomUUID()}.db"
        var db = TimeLogDatabase.open(context, name)
        try {
            var store = TimeLogStore(db)
            store.initialize()
            val pending = DeviceReminderState(generation = 3, zoneId = "UTC", lastHandledEventKey = "synthetic",
                snoozeAtMillis = 1, snoozeBlockStartMillis = 0, snoozeBlockEndMillis = 1)
            db.dao().saveDeviceState(pending)
            store.saveSettings(AppSettings(onboardingComplete = true))
            assertEquals(pending, db.dao().deviceState())
            val value = AppSettings(onboardingComplete = true, trackingStartMinute = 510, trackingEndMinute = 1290,
                intervalMinutes = 120, remindersEnabled = true)
            store.saveSettings(value)
            assertEquals(DeviceReminderState(generation = 4, zoneId = "UTC"), db.dao().deviceState())
            store.saveSettings(value)
            assertEquals(4L, db.dao().deviceState()!!.generation)
            try { store.saveSettings(value.copy(trackingEndMinute = 500)); fail("Invalid window saved") }
            catch (e: DataValidationException) { assertEquals(ValidationIssue.INVALID_SETTINGS, e.issue) }
            assertEquals(value, db.dao().settings())
            assertEquals(4L, db.dao().deviceState()!!.generation)
            db.close(); db = TimeLogDatabase.open(context, name); store = TimeLogStore(db)
            store.initialize()
            assertEquals(value, db.dao().settings())
            assertEquals(DeviceReminderState(generation = 4, zoneId = "UTC"), db.dao().deviceState())
            store.invalidateSchedule("America/New_York")
            assertEquals(DeviceReminderState(generation = 5, zoneId = "America/New_York"), db.dao().deviceState())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun migrationOneToTwoPreservesExistingCategoriesEntriesAndSettings() = runBlocking {
        val name = "synthetic-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.timelog.app.data.TimeLogDatabase/1.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { raw ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (i in 0 until indices.length()) raw.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            raw.execSQL("INSERT INTO categories VALUES ('00000000-0000-0000-0000-000000000001', 'Synthetic', 'synthetic', 0, 0)")
            raw.execSQL("INSERT INTO entries VALUES ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 60000, 120000, 'Synthetic migration', 120000, 120000)")
            raw.execSQL("INSERT INTO settings VALUES (1, 1, 510, 1290, 120, 1)")
            raw.version = 1
        }
        val db = TimeLogDatabase.open(context, name)
        try {
            assertEquals("Synthetic", db.dao().categories().single().name)
            assertEquals("Synthetic migration", db.dao().entries().single().note)
            assertEquals(AppSettings(onboardingComplete = true, trackingStartMinute = 510, trackingEndMinute = 1290,
                intervalMinutes = 120, remindersEnabled = true), db.dao().settings())
            assertEquals(DeviceReminderState(), db.dao().deviceState())
            assertEquals(2, db.openHelper.writableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
