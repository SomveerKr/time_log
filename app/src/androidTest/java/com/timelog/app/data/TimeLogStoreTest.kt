package com.timelog.app.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TimeLogStoreTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: TimeLogDatabase
    private lateinit var store: TimeLogStore
    private val clock = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC)
    private val dayStart = Instant.parse("2026-10-13T00:00:00Z").toEpochMilli()
    private val workId get() = starterCategories.first { it.name == "Work" }.id
    private val healthId get() = starterCategories.first { it.name == "Health" }.id

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "time-log-test-${UUID.randomUUID()}.db"
        reopen()
        store.initialize()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun reopen() {
        database = TimeLogDatabase.open(context, databaseName)
        store = TimeLogStore(database, clock)
    }

    private fun input(startMinute: Int = 540, endMinute: Int = 600, category: String = workId) =
        EntryInput(category, dayStart + startMinute * MINUTE_MILLIS, dayStart + endMinute * MINUTE_MILLIS)

    private suspend inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }

    @Test
    fun entriesCategoriesAndSettingsPersistAfterReopening() = runBlocking {
        val category = store.createCategory("  Studio  ")
        val saved = store.save(input(category = category.id).copy(note = "Synthetic note \u6D4B\u8BD5"))
        val settings = AppSettings(onboardingComplete = true, trackingEndMinute = 1200, intervalMinutes = 120)
        store.saveSettings(settings)
        database.close()
        reopen()
        store.initialize()
        assertEquals(9, store.categories.first().size)
        assertEquals(category, database.dao().category(category.id))
        assertEquals(saved, database.dao().entry(saved.id))
        assertEquals(settings, store.settings.first())
    }

    @Test
    fun categoryNamesNormalizeAndArchivedDuplicatesRemainReserved() = runBlocking {
        assertEquals(8, store.categories.first().size)
        val duplicate = expectFailure<DataValidationException> {
            store.createCategory(" \uFF37\uFF2F\uFF32\uFF2B ")
        }
        assertEquals(ValidationIssue.DUPLICATE_CATEGORY, duplicate.issue)
        assertEquals(workId, duplicate.matchingCategory!!.id)
        store.setCategoryArchived(workId, true)
        val archived = expectFailure<DataValidationException> { store.createCategory("WORK") }
        assertTrue(archived.matchingCategory!!.archived)
        store.setCategoryArchived(workId, false)
        assertFalse(database.dao().category(workId)!!.archived)
        for (invalid in listOf(" ", "a".repeat(41))) {
            assertEquals(ValidationIssue.INVALID_NAME,
                expectFailure<DataValidationException> { store.createCategory(invalid) }.issue)
        }
    }

    @Test
    fun renameRetainsReferencesAndColorAndRejectsDuplicates() = runBlocking {
        val saved = store.save(input())
        val before = database.dao().category(workId)!!
        val renamed = store.renameCategory(workId, "Focused work")
        assertEquals(before.id, renamed.id)
        assertEquals(before.colorToken, renamed.colorToken)
        assertEquals(workId, database.dao().entry(saved.id)!!.categoryId)
        assertEquals(ValidationIssue.DUPLICATE_CATEGORY,
            expectFailure<DataValidationException> { store.renameCategory(workId, "Health") }.issue)
        assertEquals(renamed, database.dao().category(workId))
    }

    @Test
    fun archivedCategoryCanBeRetainedByEditButNotUsedForNewRecords() = runBlocking {
        val saved = store.save(input())
        store.setCategoryArchived(workId, true)
        val edited = store.save(input().copy(id = saved.id, note = "Updated synthetic note"), editing = true)
        assertEquals(saved.id, edited.id)
        assertEquals(saved.createdMillis, edited.createdMillis)
        assertEquals(ValidationIssue.CATEGORY_ARCHIVED,
            expectFailure<DataValidationException> { store.save(input(600, 660)) }.issue)
        assertEquals(edited, database.dao().entry(saved.id))
    }

    @Test
    fun invalidRangesFutureTimeMissingCategoryAndLongNotePreserveData() = runBlocking {
        val invalidInputs = listOf(
            input(600, 600), input(601, 600), input(-1000, 600),
            input().copy(startMillis = dayStart + 1),
            input().copy(startMillis = Long.MIN_VALUE, endMillis = Long.MAX_VALUE),
            input(720, 721), input().copy(categoryId = UUID.randomUUID().toString()),
            input().copy(note = "a".repeat(501)), input().copy(id = "bad-id"),
        )
        for (draft in invalidInputs) expectFailure<DataValidationException> { store.save(draft) }
        assertTrue(database.dao().entries().isEmpty())
        val oneMinute = store.save(input(719, 720))
        assertEquals(MINUTE_MILLIS, oneMinute.endMillis - oneMinute.startMillis)
        val fullDay = store.save(input(-1440, 0))
        assertEquals(1440 * MINUTE_MILLIS, fullDay.endMillis - fullDay.startMillis)
    }

    @Test
    fun adjacentIntervalsAreAllowedAndOverlapReportsTheExistingRecord() = runBlocking {
        val first = store.save(input())
        store.save(input(600, 630, healthId))
        for (draft in listOf(input(539, 541), input(599, 601), input(540, 600), input(500, 640))) {
            val failure = expectFailure<DataValidationException> { store.save(draft) }
            assertEquals(ValidationIssue.OVERLAP, failure.issue)
            assertNotNull(failure.conflictingEntry)
        }
        assertEquals(first, database.dao().entry(first.id))
        assertEquals(2, database.dao().entries().size)
    }

    @Test
    fun editExcludesItsOwnIdAndDoesNotOverwriteAnotherRecord() = runBlocking {
        val first = store.save(input())
        val second = store.save(input(600, 660, healthId))
        store.save(input(550, 600).copy(id = first.id), editing = true)
        val collision = expectFailure<DataValidationException> {
            store.save(input(550, 601).copy(id = first.id), editing = true)
        }
        assertEquals(second.id, collision.conflictingEntry!!.id)
        assertEquals(dayStart + 600 * MINUTE_MILLIS, database.dao().entry(first.id)!!.endMillis)
        store.delete(first.id)
        assertNull(database.dao().entry(first.id))
        assertEquals(listOf(second), database.dao().entries())
    }

    @Test
    fun overnightEntryAppearsInBothIntersectingDateQueries() = runBlocking {
        val overnight = store.save(input(-30, 30))
        assertEquals(listOf(overnight), store.entriesBetween(dayStart - 1440 * MINUTE_MILLIS, dayStart).first())
        assertEquals(listOf(overnight), store.entriesBetween(dayStart, dayStart + 1440 * MINUTE_MILLIS).first())
    }

    @Test
    fun splitValidationAndDatabaseFailureRollBackTheOriginalRecord() = runBlocking {
        val original = store.save(input())
        val first = input(540, 570).copy(id = original.id)
        val second = input(570, 600, healthId)
        expectFailure<DataValidationException> {
            store.split(original.startMillis, original.endMillis, listOf(first, second.copy(startMillis = second.startMillis + MINUTE_MILLIS)), original.id)
        }
        assertEquals(listOf(original), database.dao().entries())
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER synthetic_split_failure BEFORE INSERT ON entries WHEN NEW.categoryId = '$healthId' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END",
        )
        expectFailure<SQLiteConstraintException> {
            store.split(original.startMillis, original.endMillis, listOf(first, second), original.id)
        }
        assertEquals(listOf(original), database.dao().entries())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER synthetic_split_failure")
        val split = store.split(original.startMillis, original.endMillis, listOf(first, second), original.id)
        assertEquals(2, split.size)
        assertEquals(original.id, split.first().id)
        assertEquals(original.createdMillis, split.first().createdMillis)
        assertEquals(split.first().endMillis, split.last().startMillis)
        assertEquals(60 * MINUTE_MILLIS, split.sumOf { it.endMillis - it.startMillis })
    }

    @Test
    fun newSplitAndRepeatedSaveAreIdempotent() = runBlocking {
        val draft = input()
        val saved = store.save(draft)
        assertEquals(saved, store.save(draft))
        assertEquals(1, database.dao().entries().size)
        store.delete(saved.id)
        val pieces = listOf(input(540, 570), input(570, 600, healthId))
        val split = store.split(draft.startMillis, draft.endMillis, pieces)
        assertEquals(split, store.split(draft.startMillis, draft.endMillis, pieces))
        assertEquals(2, database.dao().entries().size)
    }

    @Test
    fun foreignKeysRejectMissingCategoryAndDeletingReferencedCategory() = runBlocking {
        val saved = store.save(input())
        expectFailure<SQLiteConstraintException> {
            database.dao().insertEntry(saved.copy(id = UUID.randomUUID().toString(), categoryId = UUID.randomUUID().toString()))
        }
        expectFailure<SQLiteConstraintException> {
            database.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE id = ?", arrayOf(workId))
        }
        assertEquals(listOf(saved), database.dao().entries())
        assertNotNull(database.dao().category(workId))
    }

    @Test
    fun conflictingSavesAcrossConnectionsAreSerialized() = runBlocking {
        val secondDatabase = TimeLogDatabase.open(context, databaseName)
        try {
            val secondStore = TimeLogStore(secondDatabase, clock)
            val gate = CompletableDeferred<Unit>()
            val results = coroutineScope {
                val writes = listOf(store, secondStore).map { current ->
                    async(Dispatchers.Default) { gate.await(); runCatching { current.save(input()) } }
                }
                gate.complete(Unit)
                writes.map { it.await() }
            }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(ValidationIssue.OVERLAP, (results.single { it.isFailure }.exceptionOrNull() as DataValidationException).issue)
            assertEquals(1, database.dao().entries().size)
        } finally { secondDatabase.close() }
    }

    @Test
    fun invalidSettingsAreRejectedAndUnknownSchemaCannotEraseData() = runBlocking {
        for (settings in listOf(AppSettings(id = 2), AppSettings(trackingStartMinute = 1320, trackingEndMinute = 540),
            AppSettings(trackingEndMinute = 540), AppSettings(intervalMinutes = 15))) {
            expectFailure<DataValidationException> { store.saveSettings(settings) }
        }
        assertEquals(AppSettings(), database.dao().settings())
        val saved = store.save(input())
        database.openHelper.writableDatabase.version = 2
        database.close()
        reopen()
        expectFailure<IllegalStateException> { database.dao().entries() }
        SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw.rawQuery("SELECT id FROM entries", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(saved.id, cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
        }
    }
}
