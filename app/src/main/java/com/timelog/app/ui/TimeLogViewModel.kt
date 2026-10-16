package com.timelog.app.ui

import android.os.Bundle
import android.net.Uri
import com.timelog.app.backup.BackupExport
import com.timelog.app.backup.BackupRestore
import com.timelog.app.backup.BackupSnapshot
import com.timelog.app.backup.BackupValidationException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.timelog.app.TimeLogApplication
import com.timelog.app.data.*
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.timelog.app.reminders.AndroidReminderOutput
import com.timelog.app.reminders.ReminderController
import com.timelog.app.reminders.TEST_REQUEST
import com.timelog.app.reminders.parseEvent

data class EntryDraft(
    val id: String = UUID.randomUUID().toString(),
    val start: Long,
    val end: Long,
    val categoryId: String? = null,
    val note: String = "",
    val editing: Boolean = false,
    val originalCategoryId: String? = null,
    val split: Boolean = false,
    val firstMinutes: String = "",
    val secondCategoryId: String? = null,
    val secondId: String = UUID.randomUUID().toString(),
    val chooseRange: Boolean = false,
) {
    fun bundle() = Bundle().apply {
        putString("id", id); putLong("start", start); putLong("end", end)
        putString("category", categoryId); putString("note", note); putBoolean("editing", editing)
        putString("originalCategory", originalCategoryId); putBoolean("split", split)
        putString("firstMinutes", firstMinutes); putString("secondCategory", secondCategoryId)
        putString("secondId", secondId); putBoolean("chooseRange", chooseRange)
    }

    companion object {
        fun from(bundle: Bundle) = EntryDraft(
            id = bundle.getString("id")!!, start = bundle.getLong("start"), end = bundle.getLong("end"),
            categoryId = bundle.getString("category"), note = bundle.getString("note").orEmpty(),
            editing = bundle.getBoolean("editing"), originalCategoryId = bundle.getString("originalCategory"),
            split = bundle.getBoolean("split"), firstMinutes = bundle.getString("firstMinutes").orEmpty(),
            secondCategoryId = bundle.getString("secondCategory"), secondId = bundle.getString("secondId")!!,
            chooseRange = bundle.getBoolean("chooseRange"),
        )
    }
}

class TimeLogViewModel(
    private val store: TimeLogStore,
    private val saved: SavedStateHandle,
    private val clock: Clock = Clock.systemUTC(),
    private val reminders: ReminderController? = null,
    val reminderOutput: AndroidReminderOutput? = null,
    private val backups: BackupExport? = null,
    private val backupRestore: BackupRestore? = null,
) : ViewModel() {
    var now by mutableLongStateOf(currentMinute()); private set
    var zone by mutableStateOf(ZoneId.systemDefault()); private set
    var date by mutableStateOf(saved.get<String>("date")?.let(LocalDate::parse) ?: localDate(now, zone)); private set
    var weeklyInsights by mutableStateOf(saved.get<Boolean>("weeklyInsights") ?: false); private set
    var lastExportMillis by mutableStateOf<Long?>(null); private set
    var backupError by mutableStateOf<String?>(null); private set
    var restorePreview by mutableStateOf<BackupSnapshot?>(null); private set
    var categories by mutableStateOf(emptyList<Category>()); private set
    var settings by mutableStateOf<AppSettings?>(null); private set
    var entries by mutableStateOf(emptyList<TimeEntry>()); private set
    var loading by mutableStateOf(true); private set
    var dataError by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var archivedMatch by mutableStateOf<Category?>(null); private set
    var reminderStatus by mutableStateOf("Reminders off"); private set
    var reminderGaps by mutableStateOf(emptyList<TimeRange>()); private set
    var draft by mutableStateOf(saved.get<Bundle>("draft")?.let(EntryDraft::from)); private set
    private val query = MutableStateFlow(queryRange())
    private var loadJob: Job? = null
    val ready get() = !loading && settings != null && dataError == null
    val day get() = dayRange(date, zone)
    val today get() = date == localDate(now, zone)
    val visibleEntries get(): List<TimeEntry> {
        val range = day
        return entries.filter { it.endMillis > range.start && it.startMillis < range.end }
    }
    val gaps get() = settings?.let { unloggedRanges(elapsedWindow(date, zone,
        it.trackingStartMinute, it.trackingEndMinute, now), entries) }.orEmpty()

    init { reload() }

    fun reload() {
        loadJob?.cancel()
        loading = true
        dataError = null
        loadJob = viewModelScope.launch {
            try {
                store.initialize()
                coroutineScope {
                    launch { lastExportMillis = backups?.lastSuccess() }
                    launch { store.categories.collect { categories = it } }
                    launch { store.settings.collect { settings = it; updateReminderStatus() } }
                    launch {
                        query.collectLatest { range ->
                            store.entriesBetween(range.start, range.end).collect {
                                entries = it
                                loading = false
                            }
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { dataError = "Cannot load local data. Please retry."; loading = false }
        }
    }

    fun refresh(reconcileReminders: Boolean = true) {
        val wasToday = today
        now = currentMinute()
        zone = ZoneId.systemDefault()
        if (wasToday) selectDate(localDate(now, zone)) else updateQuery()
        updateReminderStatus()
        if (reconcileReminders) viewModelScope.launch { reconcileDelivery() }
    }

    private fun updateReminderStatus() { reminderStatus = reminderOutput?.status(settings?.remindersEnabled == true) ?: "Reminders off" }
    private suspend fun reconcileDelivery() {
        try { reminders?.reconcile(); updateReminderStatus() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { reminderOutput?.markFailure(); updateReminderStatus() }
    }

    fun openRequest(key: String) {
        if (draft != null || busy) { message = "Your unsaved draft is still open. Save or close it first."; return }
        selectDate(localDate(now, zone))
        if (key == TEST_REQUEST) { openNew(); return }
        val event = parseEvent(key) ?: return
        operation {
            val remaining = reminders?.remainingGaps(event).orEmpty()
            if (remaining.isEmpty()) { message = "This check-in is no longer active. You can fill gaps in Today."; return@operation }
            reminderGaps = remaining
            val range = remaining.last()
            persistDraft(EntryDraft(start = range.start, end = range.end))
        }
    }

    fun chooseReminderGap(range: TimeRange) {
        draft?.let { changeDraft(it.copy(start = range.start, end = range.end, chooseRange = false)) }
    }

    fun selectDate(value: LocalDate) {
        if (value > localDate(now, zone)) return
        date = value
        saved["date"] = value.toString()
        updateQuery()
    }

    fun selectInsightsPeriod(weekly: Boolean) { weeklyInsights = weekly; saved["weeklyInsights"] = weekly }

    private fun updateQuery() {
        val next = queryRange()
        if (query.value != next) { loading = true; entries = emptyList(); query.value = next }
    }

    private fun queryRange() = weekRange(date, zone).let { it.copy(start = it.start - 120 * MINUTE_MILLIS) }
    private fun currentMinute() = clock.millis() / MINUTE_MILLIS * MINUTE_MILLIS

    fun openNew(fillGap: Boolean = false) {
        if (!ready || busy) return
        reminderGaps = emptyList()
        refresh(reconcileReminders = false)
        val interval = settings!!.intervalMinutes * MINUTE_MILLIS
        val range = if (today) TimeRange(now - interval, now) else {
            elapsedWindow(date, zone, settings!!.trackingStartMinute, settings!!.trackingEndMinute, now)
        }
        val available = if (fillGap && today) gaps else unloggedRanges(range, entries)
        val last = available.lastOrNull()
        val chosen = last?.let { it.copy(start = maxOf(it.start, it.end - interval)) }
            ?: range.copy(start = maxOf(range.start, range.end - interval))
        persistDraft(EntryDraft(start = chosen.start, end = chosen.end, chooseRange = last == null))
        error = null
    }

    fun edit(entry: TimeEntry) {
        if (busy) return
        reminderGaps = emptyList()
        persistDraft(EntryDraft(id = entry.id, start = entry.startMillis, end = entry.endMillis,
            categoryId = entry.categoryId, note = entry.note.orEmpty(), editing = true,
            originalCategoryId = entry.categoryId))
        error = null
    }

    fun changeDraft(value: EntryDraft) {
        if (busy) return
        persistDraft(value)
        error = null
    }

    private fun persistDraft(value: EntryDraft?) { draft = value; saved["draft"] = value?.bundle() }
    fun closeDraft() { if (!busy) { persistDraft(null); error = null; archivedMatch = null; reminderGaps = emptyList() } }
    fun consumeMessage() { message = null }
    fun showMessage(value: String) { message = value }
    fun exportBackup(uri: Uri?) {
        if (uri == null || busy) return
        busy = true
        backupError = null
        viewModelScope.launch {
            try {
                lastExportMillis = backups?.export(uri) ?: throw IllegalStateException("Backup export is unavailable")
                message = "Backup exported"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { backupError = "Export could not finish. Local data is unchanged. The selected file may be incomplete; try a new file." }
            finally { busy = false }
        }
    }
    fun previewBackup(uri: Uri?) {
        if (uri == null || busy) return
        busy = true; backupError = null; restorePreview = null
        viewModelScope.launch {
            try { restorePreview = backupRestore?.read(uri) ?: throw IllegalStateException("Restore unavailable") }
            catch (error: CancellationException) { throw error }
            catch (error: BackupValidationException) { backupError = "${error.message} Local data and reminders are unchanged." }
            catch (_: Exception) { backupError = "Cannot open this backup. Local data and reminders are unchanged." }
            finally { busy = false }
        }
    }
    fun cancelRestore() { if (!busy) { restorePreview = null; backupError = null } }
    fun confirmRestore() {
        val snapshot = restorePreview ?: return
        if (busy) return
        busy = true; backupError = null
        viewModelScope.launch {
            try {
                val reconciled = reminders?.restore(snapshot) ?: throw IllegalStateException("Restore unavailable")
                restorePreview = null; persistDraft(null); error = null; archivedMatch = null; reminderGaps = emptyList()
                message = if (reconciled) "Backup restored. Reminders are off."
                    else "Backup restored. Reminders are off; reopen the app to clear any old notification."
                updateReminderStatus()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { backupError = "Restore could not finish. Local data and reminders are unchanged. Try again." }
            finally { busy = false }
        }
    }
    fun sendTestReminder() {
        viewModelScope.launch {
            try { reminders?.prepareTest(); reminderOutput?.sendTest() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "Test reminder could not be sent. Please retry." }
        }
    }

    fun save() {
        val d = draft ?: return
        if (busy) return
        if (d.chooseRange) { error = "This interval is fully logged. Choose another time range."; return }
        val category = d.categoryId ?: run { error = "Choose a category."; return }
        operation {
            val first = EntryInput(category, d.start, d.end, d.note.takeIf(String::isNotEmpty), d.id)
            if (d.split) {
                val minutes = d.firstMinutes.toLongOrNull()
                val total = (d.end - d.start) / MINUTE_MILLIS
                if (minutes == null || minutes !in 1 until total) throw DataValidationException(ValidationIssue.INVALID_SPLIT)
                val second = d.secondCategoryId ?: throw DataValidationException(ValidationIssue.CATEGORY_MISSING)
                val boundary = d.start + minutes * MINUTE_MILLIS
                store.split(d.start, d.end, listOf(first.copy(endMillis = boundary),
                    EntryInput(second, boundary, d.end, first.note, d.secondId)), d.id.takeIf { d.editing })
            } else store.save(first, d.editing)
            persistDraft(null)
            message = "Time saved"
        }
    }

    fun delete() {
        val d = draft ?: return
        if (!d.editing) return
        operation { store.delete(d.id); persistDraft(null); message = "Entry deleted" }
    }

    fun createCategory(name: String, second: Boolean, onCreated: () -> Unit) = operation {
        val category = store.createCategory(name)
        draft?.let { persistDraft(if (second) it.copy(secondCategoryId = category.id) else it.copy(categoryId = category.id)) }
        archivedMatch = null
        onCreated()
    }

    fun reactivateMatch(second: Boolean, onCreated: () -> Unit) {
        val match = archivedMatch ?: return
        operation {
            store.setCategoryArchived(match.id, false)
            draft?.let { persistDraft(if (second) it.copy(secondCategoryId = match.id) else it.copy(categoryId = match.id)) }
            archivedMatch = null
            onCreated()
        }
    }

    fun renameCategory(id: String, name: String, onRenamed: () -> Unit) = operation {
        store.renameCategory(id, name); onRenamed()
    }
    fun archiveCategory(id: String, archived: Boolean) = operation { store.setCategoryArchived(id, archived) }
    fun saveReminderSettings(value: AppSettings, onSaved: () -> Unit = {}) = operation {
        store.saveSettings(value); reconcileDelivery(); onSaved()
    }

    private fun operation(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        archivedMatch = null
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: DataValidationException) {
                archivedMatch = e.matchingCategory?.takeIf { it.archived }
                error = when (e.issue) {
                    ValidationIssue.INVALID_NAME -> "Use a name from 1 to 40 characters."
                    ValidationIssue.DUPLICATE_CATEGORY -> "A category with this name already exists."
                    ValidationIssue.INVALID_RANGE -> "Choose an end after start, from 1 minute to 24 hours."
                    ValidationIssue.FUTURE_END -> "The end cannot be in the future."
                    ValidationIssue.NOTE_TOO_LONG -> "Keep the note within 500 characters."
                    ValidationIssue.CATEGORY_MISSING -> "Choose a category for each piece."
                    ValidationIssue.CATEGORY_ARCHIVED -> "This category is archived. Choose an active category."
                    ValidationIssue.INVALID_SPLIT -> "Use positive pieces that add up to the whole block."
                    ValidationIssue.INVALID_SETTINGS -> "Choose a same-day end after start and a 60 or 120 minute interval."
                    ValidationIssue.OVERLAP -> e.conflictingEntry?.let {
                        "Overlaps ${categories.find { c -> c.id == it.categoryId }?.name.orEmpty()} " +
                            "(${rangeText(it.startMillis, it.endMillis, zone)}). Adjust the start or end."
                    } ?: "This range overlaps a saved entry. Adjust the start or end."
                    else -> "The record changed. Reopen it and try again."
                }
            } catch (_: Exception) { error = "Could not save local data. Your draft is kept. Please retry." }
            finally { busy = false }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TimeLogApplication
                TimeLogViewModel(app.store, createSavedStateHandle(), reminders = app.reminders, reminderOutput = app.reminderOutput, backups = app.backups, backupRestore = app.backupRestore)
            }
        }
    }
}
