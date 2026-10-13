package com.timelog.app.ui

import android.os.Bundle
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
) : ViewModel() {
    var now by mutableLongStateOf(currentMinute()); private set
    var zone by mutableStateOf(ZoneId.systemDefault()); private set
    var date by mutableStateOf(saved.get<String>("date")?.let(LocalDate::parse) ?: localDate(now, zone)); private set
    var categories by mutableStateOf(emptyList<Category>()); private set
    var settings by mutableStateOf<AppSettings?>(null); private set
    var entries by mutableStateOf(emptyList<TimeEntry>()); private set
    var loading by mutableStateOf(true); private set
    var dataError by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var archivedMatch by mutableStateOf<Category?>(null); private set
    var draft by mutableStateOf(saved.get<Bundle>("draft")?.let(EntryDraft::from)); private set
    private val query = MutableStateFlow(queryRange())
    private var loadJob: Job? = null
    val ready get() = !loading && settings != null && dataError == null
    val day get() = dayRange(date, zone)
    val today get() = date == localDate(now, zone)
    val visibleEntries get() = entries.filter { it.endMillis > day.start && it.startMillis < day.end }
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
                    launch { store.categories.collect { categories = it } }
                    launch { store.settings.collect { settings = it } }
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

    fun refresh() {
        val wasToday = today
        now = currentMinute()
        zone = ZoneId.systemDefault()
        if (wasToday) selectDate(localDate(now, zone)) else updateQuery()
    }

    fun selectDate(value: LocalDate) {
        if (value > localDate(now, zone)) return
        date = value
        saved["date"] = value.toString()
        updateQuery()
    }

    private fun updateQuery() {
        val next = queryRange()
        if (query.value != next) { loading = true; entries = emptyList(); query.value = next }
    }

    private fun queryRange() = dayRange(date, zone).let { it.copy(start = it.start - 120 * MINUTE_MILLIS) }
    private fun currentMinute() = clock.millis() / MINUTE_MILLIS * MINUTE_MILLIS

    fun openNew(fillGap: Boolean = false) {
        if (!ready || busy) return
        refresh()
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
    fun closeDraft() { if (!busy) { persistDraft(null); error = null; archivedMatch = null } }
    fun consumeMessage() { message = null }

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
            initializer { TimeLogViewModel((this[APPLICATION_KEY] as TimeLogApplication).store, createSavedStateHandle()) }
        }
    }
}
