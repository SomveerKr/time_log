package com.timelog.app.data

import androidx.room.withTransaction
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class TimeLogStore(internal val database: TimeLogDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val dao = database.dao()
    val categories: Flow<List<Category>> = dao.observeCategories()
    val settings: Flow<AppSettings?> = dao.observeSettings()

    fun entriesBetween(start: Long, end: Long): Flow<List<TimeEntry>> {
        require(end > start)
        return dao.observeEntries(start, end)
    }

    suspend fun initialize() = database.withTransaction {
        if (dao.settings() == null) {
            starterCategories.forEach { dao.insertCategory(it) }
            dao.saveSettings(AppSettings())
        }
    }

    suspend fun createCategory(name: String): Category = database.withTransaction {
        val (display, key) = checkedName(name)
        checkDuplicateName(key)
        val category = Category(UUID.randomUUID().toString(), display, key, dao.categories().size % CATEGORY_COLOR_COUNT)
        dao.insertCategory(category)
        category
    }

    suspend fun renameCategory(id: String, name: String): Category = database.withTransaction {
        val previous = dao.category(id) ?: invalid(ValidationIssue.CATEGORY_MISSING)
        val (display, key) = checkedName(name)
        checkDuplicateName(key, id)
        val renamed = previous.copy(name = display, normalizedName = key)
        dao.updateCategory(renamed)
        renamed
    }

    suspend fun setCategoryArchived(id: String, archived: Boolean) = database.withTransaction {
        val category = dao.category(id) ?: invalid(ValidationIssue.CATEGORY_MISSING)
        dao.updateCategory(category.copy(archived = archived))
    }

    suspend fun saveSettings(settings: AppSettings) = database.withTransaction {
        if (settings.id != 1 || settings.trackingStartMinute !in 0..1439 ||
            settings.trackingEndMinute !in 0..1439 || settings.trackingEndMinute <= settings.trackingStartMinute ||
            settings.intervalMinutes !in listOf(60, 120)) invalid(ValidationIssue.INVALID_SETTINGS)
        dao.saveSettings(settings)
    }

    suspend fun save(input: EntryInput, editing: Boolean = false): TimeEntry = database.withTransaction {
        val previous = dao.entry(input.id)
        if (editing && previous == null) invalid(ValidationIssue.ENTRY_MISSING)
        if (!editing && previous != null) {
            if (previous.matches(input)) return@withTransaction previous
            invalid(ValidationIssue.DUPLICATE_ENTRY)
        }
        validateEntry(input, if (editing) previous else null)
        val entry = toEntry(input, previous)
        if (editing) dao.updateEntry(entry) else dao.insertEntry(entry)
        entry
    }

    suspend fun delete(id: String) = database.withTransaction {
        if (dao.entry(id) == null) invalid(ValidationIssue.ENTRY_MISSING)
        dao.deleteEntry(id)
    }

    suspend fun split(
        startMillis: Long,
        endMillis: Long,
        pieces: List<EntryInput>,
        originalId: String? = null,
    ): List<TimeEntry> = database.withTransaction {
        validateRange(startMillis, endMillis)
        if (pieces.size < 2 || pieces.map { it.id }.distinct().size != pieces.size ||
            pieces.first().startMillis != startMillis || pieces.last().endMillis != endMillis ||
            pieces.zipWithNext().any { (first, next) -> first.endMillis != next.startMillis }) {
            invalid(ValidationIssue.INVALID_SPLIT)
        }
        val previous = originalId?.let { dao.entry(it) ?: invalid(ValidationIssue.ENTRY_MISSING) }
        if (previous != null && pieces.first().id != previous.id) invalid(ValidationIssue.INVALID_SPLIT)
        if (previous == null) {
            val existing = pieces.map { dao.entry(it.id) }
            if (existing.all { it != null } && existing.zip(pieces).all { (entry, input) -> entry!!.matches(input) }) {
                return@withTransaction existing.filterNotNull()
            }
            if (existing.any { it != null }) invalid(ValidationIssue.DUPLICATE_ENTRY)
        } else if (pieces.drop(1).any { dao.entry(it.id) != null }) invalid(ValidationIssue.DUPLICATE_ENTRY)
        pieces.forEach { validateEntry(it, previous) }
        val result = pieces.mapIndexed { index, input -> toEntry(input, if (index == 0) previous else null) }
        if (previous != null) dao.deleteEntry(previous.id)
        result.forEach { dao.insertEntry(it) }
        result
    }

    private suspend fun validateEntry(input: EntryInput, previous: TimeEntry?) {
        if (runCatching { UUID.fromString(input.id).toString() }.getOrNull() != input.id) {
            invalid(ValidationIssue.INVALID_ID)
        }
        validateRange(input.startMillis, input.endMillis)
        if (input.note != null && input.note.codePointCount(0, input.note.length) > 500) {
            invalid(ValidationIssue.NOTE_TOO_LONG)
        }
        val category = dao.category(input.categoryId) ?: invalid(ValidationIssue.CATEGORY_MISSING)
        if (category.archived && previous?.categoryId != category.id) invalid(ValidationIssue.CATEGORY_ARCHIVED)
        dao.conflict(input.startMillis, input.endMillis, previous?.id)?.let {
            throw DataValidationException(ValidationIssue.OVERLAP, conflictingEntry = it)
        }
    }

    private fun validateRange(start: Long, end: Long) {
        val duration = runCatching { Math.subtractExact(end, start) }.getOrNull()
        if (start % MINUTE_MILLIS != 0L || end % MINUTE_MILLIS != 0L ||
            duration == null || duration !in MINUTE_MILLIS..(1440 * MINUTE_MILLIS)) {
            invalid(ValidationIssue.INVALID_RANGE)
        }
        if (end > clock.instant().truncatedTo(ChronoUnit.MINUTES).toEpochMilli()) invalid(ValidationIssue.FUTURE_END)
    }

    private fun toEntry(input: EntryInput, previous: TimeEntry?): TimeEntry {
        val now = clock.millis()
        return TimeEntry(input.id, input.categoryId, input.startMillis, input.endMillis, input.note,
            previous?.createdMillis ?: now, maxOf(now, previous?.updatedMillis ?: now))
    }

    private fun checkedName(name: String): Pair<String, String> {
        val display = name.trim()
        val key = normalizedCategoryName(name)
        if (display.codePointCount(0, display.length) !in 1..40 || key.isEmpty()) invalid(ValidationIssue.INVALID_NAME)
        return display to key
    }

    private suspend fun checkDuplicateName(key: String, excludingId: String? = null) {
        dao.categoryWithName(key)?.takeIf { it.id != excludingId }?.let {
            throw DataValidationException(ValidationIssue.DUPLICATE_CATEGORY, matchingCategory = it)
        }
    }

    private fun TimeEntry.matches(input: EntryInput) = categoryId == input.categoryId &&
        startMillis == input.startMillis && endMillis == input.endMillis && note == input.note

    private fun invalid(issue: ValidationIssue): Nothing = throw DataValidationException(issue)
}
