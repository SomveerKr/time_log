package com.timelog.app.backup

import android.content.Context
import android.net.Uri
import android.util.JsonWriter
import androidx.room.withTransaction
import com.timelog.app.data.*
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val BACKUP_FORMAT = "time-log-backup"
const val BACKUP_VERSION = 1

data class BackupSnapshot(val exportedAtMillis: Long, val categories: List<Category>, val entries: List<TimeEntry>, val settings: AppSettings)

suspend fun TimeLogStore.backupSnapshot(exportedAtMillis: Long): BackupSnapshot = database.withTransaction {
    BackupSnapshot(exportedAtMillis, database.dao().categories(), database.dao().entries(),
        database.dao().settings() ?: throw IOException("Local settings are unavailable."))
}

fun writeBackup(snapshot: BackupSnapshot, output: OutputStream) {
    JsonWriter(output.writer(Charsets.UTF_8)).use { json ->
        json.beginObject()
        json.name("format").value(BACKUP_FORMAT)
        json.name("version").value(BACKUP_VERSION.toLong())
        json.name("exportedAtMillis").value(snapshot.exportedAtMillis)
        json.name("categories").beginArray()
        snapshot.categories.forEach { category ->
            json.beginObject()
            json.name("id").value(category.id)
            json.name("name").value(category.name)
            json.name("normalizedName").value(category.normalizedName)
            json.name("colorToken").value(category.colorToken.toLong())
            json.name("archived").value(category.archived)
            json.endObject()
        }
        json.endArray()
        json.name("entries").beginArray()
        snapshot.entries.forEach { entry ->
            json.beginObject()
            json.name("id").value(entry.id)
            json.name("categoryId").value(entry.categoryId)
            json.name("startMillis").value(entry.startMillis)
            json.name("endMillis").value(entry.endMillis)
            json.name("note").value(entry.note)
            json.name("createdMillis").value(entry.createdMillis)
            json.name("updatedMillis").value(entry.updatedMillis)
            json.endObject()
        }
        json.endArray()
        json.name("settings").beginObject()
        json.name("id").value(snapshot.settings.id.toLong())
        json.name("onboardingComplete").value(snapshot.settings.onboardingComplete)
        json.name("trackingStartMinute").value(snapshot.settings.trackingStartMinute.toLong())
        json.name("trackingEndMinute").value(snapshot.settings.trackingEndMinute.toLong())
        json.name("intervalMinutes").value(snapshot.settings.intervalMinutes.toLong())
        json.name("remindersEnabled").value(snapshot.settings.remindersEnabled)
        json.endObject()
        json.endObject()
    }
}

class BackupExport(private val context: Context, private val store: TimeLogStore, private val clock: Clock = Clock.systemUTC()) {
    private val metadata get() = context.getSharedPreferences("backup_metadata", Context.MODE_PRIVATE)
    suspend fun lastSuccess(): Long? = withContext(Dispatchers.IO) { metadata.getLong("lastExportMillis", 0).takeIf { it > 0 } }

    suspend fun export(uri: Uri?): Long? {
        if (uri == null) return null
        return exportTo { context.contentResolver.openOutputStream(uri, "wt") }
    }

    internal suspend fun exportTo(openDestination: () -> OutputStream?): Long = withContext(Dispatchers.IO) {
        val snapshot = store.backupSnapshot(clock.millis())
        val output = openDestination() ?: throw IOException("Cannot open the selected file.")
        writeBackup(snapshot, output)
        // A provider may fail on close; only a fully closed file counts as a successful export.
        val completed = clock.millis()
        if (!metadata.edit().putLong("lastExportMillis", completed).commit()) throw IOException("Cannot record backup completion.")
        completed
    }
}
