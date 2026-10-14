package com.timelog.app.backup

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import androidx.room.withTransaction
import com.timelog.app.data.*
import com.timelog.app.reminders.validSchedule
import java.io.FilterInputStream
import java.io.InputStream
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
const val MAX_BACKUP_ENTRIES = 100_000
class BackupValidationException(message: String) : IllegalArgumentException(message)
private fun reject(message: String): Nothing = throw BackupValidationException(message)

class BackupRestore(private val context: Context, private val clock: Clock = Clock.systemUTC()) {
    suspend fun read(uri: Uri?): BackupSnapshot? = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext null
        val input = context.contentResolver.openInputStream(uri) ?: reject("Cannot open this backup.")
        readBackup(input, clock.millis())
    }
}

// Count bytes actually read: document providers need not report a reliable file size.
private class BoundedBackupInput(input: InputStream) : FilterInputStream(input) {
    private var count = 0L
    private fun add(size: Int) { if (size > 0) count += size; if (count > MAX_BACKUP_BYTES) reject("Backup exceeds the 20 MiB limit.") }
    override fun read(): Int = super.read().also { if (it != -1) add(1) }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = `in`.read(buffer, offset, minOf(length, (MAX_BACKUP_BYTES - count + 1).toInt())).also(::add)
}

fun readBackup(input: InputStream, now: Long): BackupSnapshot {
    try {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return JsonReader(java.io.InputStreamReader(BoundedBackupInput(input), decoder)).use { json ->
            var format: String? = null
            var version: Long? = null
            var exported: Long? = null
            var categories: List<Category>? = null
            var entries: List<TimeEntry>? = null
            var settings: AppSettings? = null
            json.fields(setOf("format", "version", "exportedAtMillis", "categories", "entries", "settings")) { field ->
                when (field) {
                    "format" -> format = json.primitive() as? String ?: reject("Invalid format identifier.")
                    "version" -> version = json.primitive() as? Long ?: reject("Invalid backup version.")
                    "exportedAtMillis" -> exported = json.primitive() as? Long ?: reject("Invalid backup date.")
                    "categories" -> categories = json.records(Int.MAX_VALUE) {
                        val item = json.record(setOf("id", "name", "normalizedName", "colorToken", "archived"))
                        Category(item.string("id"), item.string("name"), item.string("normalizedName"), item.int("colorToken"), item.bool("archived"))
                    }
                    "entries" -> entries = json.records(MAX_BACKUP_ENTRIES) {
                        val item = json.record(setOf("id", "categoryId", "startMillis", "endMillis", "note", "createdMillis", "updatedMillis"))
                        val note = item["note"]
                        if (note != null && note !is String) reject("Invalid entry note.")
                        TimeEntry(item.string("id"), item.string("categoryId"), item.long("startMillis"), item.long("endMillis"), note as String?, item.long("createdMillis"), item.long("updatedMillis"))
                    }
                    "settings" -> {
                        val item = json.record(setOf("id", "onboardingComplete", "trackingStartMinute", "trackingEndMinute", "intervalMinutes", "remindersEnabled"))
                        settings = AppSettings(item.int("id"), item.bool("onboardingComplete"), item.int("trackingStartMinute"), item.int("trackingEndMinute"), item.int("intervalMinutes"), item.bool("remindersEnabled"))
                    }
                }
            }
            if (json.peek() != JsonToken.END_DOCUMENT) reject("Unexpected content after backup.")
            if (format != BACKUP_FORMAT) reject("This is not a Time Log backup.")
            if (version != BACKUP_VERSION.toLong()) reject("Unsupported backup version. This app supports version 1.")
            BackupSnapshot(exported!!, categories!!, entries!!, settings!!).also { validateBackup(it, now) }
        }
    } catch (error: BackupValidationException) { throw error }
    catch (_: Exception) { reject("Cannot read this backup. It may be incomplete or contain invalid JSON.") }
}

private fun JsonReader.fields(expected: Set<String>, read: (String) -> Unit) {
    beginObject()
    val seen = mutableSetOf<String>()
    while (hasNext()) {
        val name = nextName()
        if (name !in expected || !seen.add(name)) reject("Unexpected or duplicate backup field.")
        read(name)
    }
    endObject()
    if (seen != expected) reject("Required backup fields are missing.")
}
private fun JsonReader.primitive(): Any? = when (peek()) {
    JsonToken.STRING -> nextString()
    JsonToken.BOOLEAN -> nextBoolean()
    JsonToken.NUMBER -> nextString().toLongOrNull() ?: reject("Backup numbers must be whole numbers within range.")
    JsonToken.NULL -> { nextNull(); null }
    else -> reject("Invalid backup field type.")
}
private fun JsonReader.record(fields: Set<String>): Map<String, Any?> = mutableMapOf<String, Any?>().also { result -> fields(fields) { result[it] = primitive() } }
private fun <T> JsonReader.records(limit: Int, read: () -> T): List<T> {
    beginArray()
    val result = mutableListOf<T>()
    while (hasNext()) {
        if (result.size >= limit) reject("Backup exceeds the 100,000 entry limit.")
        result.add(read())
    }
    endArray()
    return result
}
private fun Map<String, Any?>.string(key: String) = this[key] as? String ?: reject("Invalid text field: $key.")
private fun Map<String, Any?>.long(key: String) = this[key] as? Long ?: reject("Invalid number field: $key.")
private fun Map<String, Any?>.int(key: String): Int = long(key).let { if (it !in Int.MIN_VALUE..Int.MAX_VALUE) reject("Number outside range: $key."); it.toInt() }
private fun Map<String, Any?>.bool(key: String) = this[key] as? Boolean ?: reject("Invalid boolean field: $key.")
private fun validId(value: String) = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

fun validateBackup(snapshot: BackupSnapshot, now: Long) {
    if (snapshot.exportedAtMillis < 0) reject("Invalid backup date.")
    if (!validSchedule(snapshot.settings)) reject("Invalid reminder settings.")
    if (snapshot.entries.size > MAX_BACKUP_ENTRIES) reject("Backup exceeds the 100,000 entry limit.")
    val categoryIds = mutableSetOf<String>()
    val names = mutableSetOf<String>()
    snapshot.categories.forEach { category ->
        if (!validId(category.id) || !categoryIds.add(category.id)) reject("Invalid or duplicate category ID.")
        if (category.name != category.name.trim() || category.name.codePointCount(0, category.name.length) !in 1..40 ||
            category.normalizedName.isEmpty() || category.normalizedName != normalizedCategoryName(category.name) || !names.add(category.normalizedName)) reject("Invalid or duplicate category name.")
        if (category.colorToken !in 0 until CATEGORY_COLOR_COUNT) reject("Invalid category color.")
    }
    val ids = mutableSetOf<String>()
    var previousEnd: Long? = null
    snapshot.entries.sortedBy { it.startMillis }.forEach { entry ->
        if (!validId(entry.id) || !ids.add(entry.id)) reject("Invalid or duplicate entry ID.")
        if (entry.categoryId !in categoryIds) reject("An entry refers to a missing category.")
        val duration = runCatching { Math.subtractExact(entry.endMillis, entry.startMillis) }.getOrNull()
        if (entry.startMillis % MINUTE_MILLIS != 0L || entry.endMillis % MINUTE_MILLIS != 0L ||
            duration == null || duration !in MINUTE_MILLIS..(1440 * MINUTE_MILLIS) || entry.endMillis > now) reject("Invalid or future entry time.")
        if (entry.createdMillis < 0 || entry.updatedMillis < entry.createdMillis) reject("Invalid entry creation or update time.")
        if (entry.note != null && entry.note.codePointCount(0, entry.note.length) > 500) reject("An entry note exceeds 500 characters.")
        if (previousEnd != null && entry.startMillis < previousEnd!!) reject("Backup entries overlap.")
        previousEnd = entry.endMillis
    }
}

internal suspend fun TimeLogStore.replaceBackup(snapshot: BackupSnapshot, now: Long, zoneId: String) = withContext(Dispatchers.IO) {
    validateBackup(snapshot, now)
    database.withTransaction {
        val dao = database.dao()
        val generation = Math.addExact(dao.deviceState()?.generation ?: 0, 1)
        dao.deleteAllEntries()
        dao.deleteAllCategories()
        snapshot.categories.forEach { dao.insertCategory(it) }
        snapshot.entries.forEach { dao.insertEntry(it) }
        dao.saveSettings(snapshot.settings.copy(remindersEnabled = false))
        dao.saveDeviceState(DeviceReminderState(generation = generation, zoneId = zoneId))
    }
}
