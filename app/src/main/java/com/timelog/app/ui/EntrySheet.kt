package com.timelog.app.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.timelog.app.data.Category
import com.timelog.app.data.MINUTE_MILLIS
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(vm: TimeLogViewModel, draft: EntryDraft) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !vm.busy })
    var noteOpen by rememberSaveable(draft.id) { mutableStateOf(draft.note.isNotEmpty()) }
    var deleteOpen by rememberSaveable(draft.id) { mutableStateOf(false) }
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() - 80.dp }
    ModalBottomSheet(onDismissRequest = vm::closeDraft, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight.coerceAtLeast(160.dp)).imePadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(if (draft.editing) "Edit time" else "Log time", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = vm::closeDraft, enabled = !vm.busy) { Text("Close") }
                }
                TimeField("Start", "start", draft.start, vm.zone, !vm.busy) {
                    vm.changeDraft(draft.copy(start = it, chooseRange = false))
                }
                TimeField("End", "end", draft.end, vm.zone, !vm.busy) {
                    vm.changeDraft(draft.copy(end = it, chooseRange = false))
                }
                Text(durationText(draft.end - draft.start), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("draftDuration"))
                if (draft.chooseRange) Text("This interval is fully logged. Choose another time range.", color = MaterialTheme.colorScheme.error)
                Text(if (draft.split) "First category" else "Category", style = MaterialTheme.typography.titleMedium)
                CategoryChoices(vm, draft.categoryId, draft.originalCategoryId, second = false) {
                    vm.changeDraft(draft.copy(categoryId = it))
                }
                if (draft.split) {
                    OutlinedTextField(draft.firstMinutes, onValueChange = { value ->
                        if (value.length <= 4 && value.all(Char::isDigit)) vm.changeDraft(draft.copy(firstMinutes = value))
                    }, label = { Text("First piece (minutes)") }, enabled = !vm.busy, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("firstMinutes"))
                    val remaining = (draft.end - draft.start) / MINUTE_MILLIS - (draft.firstMinutes.toLongOrNull() ?: 0)
                    Text("Second piece: ${remaining.coerceAtLeast(0)} min")
                    CategoryChoices(vm, draft.secondCategoryId, draft.originalCategoryId, second = true) {
                        vm.changeDraft(draft.copy(secondCategoryId = it))
                    }
                    TextButton(onClick = { vm.changeDraft(draft.copy(split = false)) }, enabled = !vm.busy) { Text("Remove split") }
                } else TextButton(onClick = {
                    vm.changeDraft(draft.copy(split = true, firstMinutes = ((draft.end - draft.start) / MINUTE_MILLIS / 2).toString()))
                }, enabled = !vm.busy) { Text("Split this block") }
                if (noteOpen) OutlinedTextField(draft.note, onValueChange = {
                    if (it.codePointCount(0, it.length) <= 500) vm.changeDraft(draft.copy(note = it))
                }, label = { Text("Note (optional)") }, supportingText = { Text("${draft.note.codePointCount(0, draft.note.length)}/500") },
                    enabled = !vm.busy, modifier = Modifier.fillMaxWidth().testTag("note"), minLines = 2, maxLines = 4)
                else TextButton(onClick = { noteOpen = true }, enabled = !vm.busy) { Text("Add a note") }
                vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("entryError")) }
                if (draft.editing) TextButton(onClick = { deleteOpen = true }, enabled = !vm.busy) {
                    Text("Delete entry", color = MaterialTheme.colorScheme.error)
                }
            }
            Button(onClick = vm::save, enabled = !vm.busy && draft.categoryId != null && (!draft.split || draft.secondCategoryId != null),
                modifier = Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 56.dp).testTag("saveEntry")) {
                Text(if (vm.busy) "Saving..." else "Save")
            }
        }
    }
    if (deleteOpen) AlertDialog(onDismissRequest = { if (!vm.busy) deleteOpen = false }, title = { Text("Delete this entry?") },
        text = { Text("This removes the saved time block.") }, confirmButton = {
            TextButton(onClick = { deleteOpen = false; vm.delete() }, enabled = !vm.busy, modifier = Modifier.testTag("confirmDelete")) { Text("Delete") }
        }, dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancel") } })
}

@Composable
private fun TimeField(label: String, tag: String, millis: Long, zone: ZoneId, enabled: Boolean, onChanged: (Long) -> Unit) {
    val context = LocalContext.current
    val value = Instant.ofEpochMilli(millis).atZone(zone)
    val use24 = DateFormat.is24HourFormat(context)
    val locale = LocalLocale.current.platformLocale
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(0.6f).padding(top = 14.dp))
        TextButton(onClick = { pickDate(context, value.toLocalDate()) {
            onChanged(it.atTime(value.toLocalTime()).atZone(zone).toInstant().toEpochMilli())
        } }, enabled = enabled, modifier = Modifier.weight(1.3f).testTag("${tag}Date")) {
            Text(value.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)))
        }
        TextButton(onClick = { TimePickerDialog(context, { _, hour, minute ->
            onChanged(value.toLocalDate().atTime(hour, minute).atZone(zone).toInstant().toEpochMilli())
        }, value.hour, value.minute, use24).show() }, enabled = enabled, modifier = Modifier.weight(1f).testTag("${tag}Time")) {
            Text(value.format(DateTimeFormatter.ofPattern(if (use24) "HH:mm" else "h:mm a", locale)))
        }
    }
}

private val categoryColors = listOf(0xFF477A59, 0xFF577AA5, 0xFF8E6649, 0xFF8B669B, 0xFF627D83, 0xFF967B40, 0xFF9E6262, 0xFF6D7785)

@Composable
private fun CategoryChoices(vm: TimeLogViewModel, selected: String?, retained: String?, second: Boolean, onSelected: (String) -> Unit) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.categories.filter { !it.archived || it.id == retained }.forEach { category ->
            FilterChip(selected = selected == category.id, onClick = { onSelected(category.id) }, enabled = !vm.busy,
                label = { Text(category.name + if (category.archived) " (archived)" else "") },
                leadingIcon = { Box(Modifier.size(8.dp).background(Color(categoryColors[category.colorToken]), CircleShape)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("${if (second) "second" else "first"}-category-${category.id}"))
        }
        TextButton(onClick = { creating = !creating }, enabled = !vm.busy) { Text("+ New category") }
    }
    if (creating) {
        OutlinedTextField(name, onValueChange = { if (it.codePointCount(0, it.length) <= 40) name = it }, label = { Text("Category name") },
            singleLine = true, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().testTag(if (second) "secondNewCategory" else "newCategory"))
        Row {
            TextButton(onClick = { vm.createCategory(name, second) { creating = false; name = "" } }, enabled = !vm.busy && name.isNotBlank()) { Text("Create category") }
            TextButton(onClick = { creating = false }, enabled = !vm.busy) { Text("Cancel") }
        }
        if (vm.archivedMatch != null) TextButton(onClick = { vm.reactivateMatch(second) { creating = false; name = "" } }, enabled = !vm.busy) {
            Text("Reactivate ${vm.archivedMatch!!.name}")
        }
    }
}

@Composable
fun CategoryManager(vm: TimeLogViewModel, onClose: () -> Unit) {
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!vm.busy) onClose() }, title = { Text("Categories") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.categories.forEach { category ->
                Text(category.name + if (category.archived) " (archived)" else "", style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = { renameId = category.id; name = category.name }, enabled = !vm.busy) { Text("Rename") }
                    TextButton(onClick = { vm.archiveCategory(category.id, !category.archived) }, enabled = !vm.busy) {
                        Text(if (category.archived) "Reactivate" else "Archive")
                    }
                }
                HorizontalDivider()
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = onClose, enabled = !vm.busy) { Text("Done") } })
    renameId?.let { id ->
        AlertDialog(onDismissRequest = { if (!vm.busy) renameId = null }, title = { Text("Rename category") }, text = {
            Column {
                OutlinedTextField(name, onValueChange = { if (it.codePointCount(0, it.length) <= 40) name = it }, label = { Text("Name") }, singleLine = true)
                vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = { vm.renameCategory(id, name) { renameId = null } }, enabled = !vm.busy) { Text("Save name") } },
            dismissButton = { TextButton(onClick = { renameId = null }, enabled = !vm.busy) { Text("Cancel") } })
    }
}
