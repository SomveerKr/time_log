package com.timelog.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import com.timelog.app.data.EntryInput
import com.timelog.app.data.MINUTE_MILLIS
import com.timelog.app.ui.TimeLogViewModel
import com.timelog.app.ui.localDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EntryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val store get() = (compose.activity.application as TimeLogApplication).store
    private lateinit var vm: TimeLogViewModel

    @Before fun resetSyntheticDatabase() {
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity, TimeLogViewModel.Factory)[TimeLogViewModel::class.java] }
        compose.waitUntil(15_000) { vm.ready }
        runBlocking {
            store.database.withTransaction {
                store.database.openHelper.writableDatabase.execSQL("DELETE FROM entries")
                store.database.openHelper.writableDatabase.execSQL("DELETE FROM categories")
                store.database.openHelper.writableDatabase.execSQL("DELETE FROM settings")
            }
            store.initialize()
        }
        compose.runOnUiThread { vm.closeDraft(); vm.selectDate(localDate(vm.now, vm.zone)) }
        compose.waitUntil(15_000) { vm.ready && vm.categories.size == 8 && vm.entries.isEmpty() }
    }

    private fun openHistorical() {
        compose.onNodeWithTag("previousDay").performClick()
        compose.waitUntil(15_000) { vm.ready }
        compose.onNodeWithTag("logTime").performClick()
        compose.waitUntil(5_000) { vm.draft != null }
    }

    private fun choose(name: String, second: Boolean = false) {
        val id = vm.categories.first { it.name == name }.id
        compose.onNodeWithTag("${if (second) "second" else "first"}-category-$id").performScrollTo().performClick()
    }

    private fun save() {
        compose.onNodeWithTag("saveEntry").performClick()
        compose.waitUntil(10_000) { vm.draft == null && !vm.busy }
    }

    private fun records() = runBlocking { store.database.dao().entries() }

    @Test fun categorySaveUsesSelectedDayAndDoubleTapKeepsOneRecord() {
        openHistorical()
        val draft = vm.draft!!
        assertEquals(vm.date, localDate(draft.start, vm.zone))
        assertEquals(vm.date, localDate(draft.end - 1, vm.zone))
        compose.onNodeWithTag("saveEntry").assertIsNotEnabled()
        choose("Work")
        compose.onNodeWithTag("saveEntry").performTouchInput { doubleClick(delayMillis = 50) }
        compose.waitUntil(10_000) { vm.draft == null && !vm.busy }
        val records = records()
        assertEquals(1, records.size)
        assertEquals(draft.id, records.single().id)
        assertEquals(draft.start, records.single().startMillis)
        assertEquals(draft.end, records.single().endMillis)
        compose.waitUntil(10_000) { vm.visibleEntries.size == 1 }
        compose.onNodeWithTag("entry-${draft.id}").performScrollTo().assertIsDisplayed()
    }

    @Test fun inlineCategoryIsSelectedAndAvailableOnNextVisit() {
        openHistorical()
        compose.onNodeWithText("+ New category").performScrollTo().performClick()
        compose.onNodeWithTag("newCategory").performScrollTo().performTextInput("Synthetic project")
        compose.onNodeWithText("Create category").performScrollTo().performClick()
        compose.waitUntil(10_000) { vm.draft?.categoryId != null && !vm.busy }
        val id = vm.draft!!.categoryId!!
        compose.onNodeWithTag("first-category-$id").assertIsSelected()
        save()
        compose.onNodeWithTag("logTime").performScrollTo().performClick()
        compose.onNodeWithTag("first-category-$id").performScrollTo().assertIsDisplayed().assertIsNotSelected()
        assertNull(vm.draft!!.categoryId)
    }

    @Test fun futureAndOverlapValidationKeepTheDraftRetryable() {
        openHistorical()
        choose("Work")
        val id = vm.draft!!.id
        compose.runOnUiThread { vm.changeDraft(vm.draft!!.copy(start = vm.now, end = vm.now + MINUTE_MILLIS, note = "Retain draft")) }
        compose.onNodeWithTag("saveEntry").performClick()
        compose.waitUntil(10_000) { !vm.busy && vm.error != null }
        compose.onNodeWithTag("entryError").performScrollTo().assertTextContains("future", substring = true)
        assertEquals(id, vm.draft!!.id)
        assertEquals("Retain draft", vm.draft!!.note)
        val start = vm.day.start + 9 * 60 * MINUTE_MILLIS
        runBlocking { store.save(EntryInput(vm.categories.first { it.name == "Work" }.id, start, start + 60 * MINUTE_MILLIS)) }
        compose.runOnUiThread { vm.changeDraft(vm.draft!!.copy(start = start, end = start + 60 * MINUTE_MILLIS, chooseRange = false)) }
        compose.onNodeWithTag("saveEntry").performClick()
        compose.waitUntil(10_000) { !vm.busy && vm.error != null }
        compose.onNodeWithTag("entryError").performScrollTo().assertTextContains("Overlaps Work", substring = true)
        assertEquals(1, records().size)
    }

    @Test fun editSplitAndConfirmedDeletionPreserveIdentity() {
        openHistorical(); choose("Work"); save()
        compose.waitUntil(10_000) { vm.visibleEntries.size == 1 }
        val original = records().single()
        compose.onNodeWithTag("entry-${original.id}").performScrollTo().performClick()
        choose("Health"); save()
        assertEquals(original.id, records().single().id)
        assertEquals(vm.categories.first { it.name == "Health" }.id, records().single().categoryId)
        compose.onNodeWithTag("entry-${original.id}").performScrollTo().performClick()
        compose.onNodeWithText("Split this block").performScrollTo().performClick()
        choose("Learning", second = true); save()
        compose.waitUntil(10_000) { vm.visibleEntries.size == 2 }
        val pieces = records().sortedBy { it.startMillis }
        assertEquals(2, pieces.size)
        assertEquals(original.id, pieces.first().id)
        assertEquals(original.startMillis, pieces.first().startMillis)
        assertEquals(pieces.first().endMillis, pieces.last().startMillis)
        assertEquals(original.endMillis, pieces.last().endMillis)
        compose.onNodeWithTag("entry-${original.id}").performScrollTo().performClick()
        compose.onNodeWithText("Delete entry").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(2, records().size)
        compose.onNodeWithText("Delete entry").performClick()
        compose.onNodeWithTag("confirmDelete").performClick()
        compose.waitUntil(10_000) { vm.draft == null && !vm.busy }
        assertEquals(1, records().size)
    }

    @Test fun recreationKeepsUnsavedRangeCategoryAndNote() {
        openHistorical(); choose("Work")
        compose.onNodeWithText("Add a note").performScrollTo().performClick()
        compose.onNodeWithTag("note").performScrollTo().performTextInput("Synthetic retained note")
        val before = vm.draft!!
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("note").performScrollTo().assertTextContains(before.note)
        assertEquals(before, vm.draft)
        compose.onNodeWithTag("first-category-${before.categoryId}").performScrollTo().assertIsSelected()
        save()
        assertEquals(before.note, records().single().note)
    }

    @Test fun sqliteFailureKeepsDraftAndRetrySavesOnce() {
        openHistorical(); choose("Work")
        val before = vm.draft!!
        store.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_save BEFORE INSERT ON entries BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        compose.onNodeWithTag("saveEntry").performClick()
        compose.waitUntil(10_000) { !vm.busy && vm.error != null }
        compose.onNodeWithTag("entryError").performScrollTo().assertTextContains("Your draft is kept", substring = true)
        assertEquals(before, vm.draft)
        assertTrue(records().isEmpty())
        store.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_save")
        save()
        assertEquals(before.id, records().single().id)
    }
}
