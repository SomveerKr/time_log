package com.timelog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timelog.app.R
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

@Composable
fun InsightsScreen(vm: TimeLogViewModel) {
    val weekly = vm.weeklyInsights
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val today = localDate(vm.now, vm.zone)
    val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    val report = remember(vm.date, vm.zone, weekly, vm.entries, vm.categories) {
        insightsReport(vm.date, vm.zone, weekly, vm.entries, vm.categories)
    }
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 } }
    LazyColumn(Modifier.fillMaxSize().testTag("insightsList"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(!weekly, { vm.selectInsightsPeriod(false) }, label = { Text("Daily") },
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = !weekly, borderColor = MaterialTheme.colorScheme.outline),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("insightsDaily"))
                FilterChip(weekly, { vm.selectInsightsPeriod(true) }, label = { Text("Weekly") },
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = weekly, borderColor = MaterialTheme.colorScheme.outline),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("insightsWeekly"))
            }
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = { vm.selectDate(vm.date.minusDays(if (weekly) 7 else 1)) }, modifier = Modifier.size(48.dp).testTag("insightsPrevious")) {
                    Icon(painterResource(R.drawable.ic_back), if (weekly) "Previous week" else "Previous day")
                }
                TextButton(onClick = { pickDate(context, vm.date, vm.now) { vm.selectDate(it) } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("insightsDate")) {
                    Text(if (weekly) "${weekStart(vm.date).format(DateTimeFormatter.ofPattern("d MMM", locale))} - ${weekStart(vm.date).plusDays(6).format(dateFormat)}"
                        else vm.date.format(dateFormat), textAlign = TextAlign.Center)
                }
                IconButton(onClick = { vm.selectDate(minOf(today, vm.date.plusDays(if (weekly) 7 else 1))) },
                    enabled = if (weekly) weekStart(vm.date) < weekStart(today) else vm.date < today, modifier = Modifier.size(48.dp).testTag("insightsNext")) {
                    Icon(painterResource(R.drawable.ic_back), if (weekly) "Next week" else "Next day", Modifier.rotate(180f))
                }
            }
        }
        when {
            vm.dataError != null -> item {
                Text(vm.dataError!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = vm::reload) { Text("Retry") }
            }
            vm.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else -> {
                item {
                    Text(if (weekly) "Logged this week" else "Logged this day", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(durationText(report.totalMillis), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("insightsTotal"))
                }
                if (report.totalMillis == 0L) item {
                    EmptyState(R.drawable.ic_insights, R.string.insights_empty_title, R.string.insights_empty_body)
                }
                if (weekly) {
                    item { Text("By day", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }
                    items(report.days, key = { it.date.toString() }) { day ->
                        Column(Modifier.semantics(mergeDescendants = true) {}.testTag("insights-day-${day.date}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(day.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale)) + if (day.date == today) " - Today" else "",
                                style = MaterialTheme.typography.titleMedium)
                            Text(durationText(day.millis), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ReportBar(if (report.days.maxOf { it.millis } == 0L) 0f else day.millis.toFloat() / report.days.maxOf { it.millis })
                        }
                    }
                }
                if (report.categories.isNotEmpty()) item {
                    Text("By category", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
                items(report.categories, key = { it.category.id }) { row ->
                    Column(Modifier.semantics(mergeDescendants = true) {}.testTag("insights-category-${row.category.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.padding(top = 8.dp).size(8.dp).background(Color(categoryColors[row.category.colorToken]), CircleShape))
                            Text(row.category.name + if (row.category.archived) " (archived)" else "", style = MaterialTheme.typography.titleMedium)
                        }
                        Text("${durationText(row.millis)} - ${percent.format(row.millis.toDouble() / report.totalMillis)} of logged time",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ReportBar(row.millis.toFloat() / report.totalMillis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant).clearAndSetSemantics {}) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}
