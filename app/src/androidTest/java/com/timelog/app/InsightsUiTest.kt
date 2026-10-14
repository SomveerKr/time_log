package com.timelog.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import com.timelog.app.data.*
import com.timelog.app.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InsightsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val store get() = (compose.activity.application as TimeLogApplication).store
    private lateinit var vm: TimeLogViewModel

    @Before fun prepareSyntheticRecords() {
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity, TimeLogViewModel.Factory)[TimeLogViewModel::class.java] }
        compose.waitUntil(15_000) { vm.ready }
        runBlocking {
            store.database.withTransaction {
                store.database.openHelper.writableDatabase.execSQL("DELETE FROM entries")
                store.database.openHelper.writableDatabase.execSQL("DELETE FROM categories")
                store.database.openHelper.writableDatabase.execSQL("DELETE FROM settings")
            }
            store.initialize()
            store.saveSettings(AppSettings(onboardingComplete = true))
        }
        compose.runOnUiThread { vm.selectDate(weekStart(localDate(vm.now, vm.zone)).minusWeeks(1)); vm.selectInsightsPeriod(false) }
        compose.waitUntil(15_000) { vm.ready && vm.categories.size == 8 && vm.entries.isEmpty() }
        val start = vm.day.start + 9 * 60 * MINUTE_MILLIS
        runBlocking {
            store.save(EntryInput(vm.categories.first { it.name == "Work" }.id, start, start + 60 * MINUTE_MILLIS))
            store.save(EntryInput(vm.categories.first { it.name == "Health" }.id, start + 60 * MINUTE_MILLIS, start + 90 * MINUTE_MILLIS))
        }
        compose.waitUntil(10_000) { vm.entries.size == 2 }
        compose.onNodeWithText("Insights").performClick()
    }

    @Test fun dailyReportUpdatesAfterEditRenameAndArchive() {
        compose.onNodeWithTag("insightsTotal").assertTextEquals("1 h 30 min")
        val work = vm.categories.first { it.name == "Work" }
        compose.onNodeWithTag("insights-category-${work.id}").assertTextContains("66.7%", substring = true)
        val health = vm.entries.first { it.categoryId == vm.categories.first { category -> category.name == "Health" }.id }
        runBlocking {
            store.save(EntryInput(health.categoryId, health.startMillis, health.endMillis + 30 * MINUTE_MILLIS, id = health.id), editing = true)
            store.renameCategory(work.id, "Project Work")
            store.setCategoryArchived(work.id, true)
        }
        compose.waitUntil(10_000) { vm.entries.any { it.id == health.id && it.endMillis == health.endMillis + 30 * MINUTE_MILLIS } && vm.categories.any { it.id == work.id && it.archived } }
        compose.onNodeWithTag("insightsTotal").assertTextEquals("2 h")
        compose.onNodeWithTag("insights-category-${work.id}").performScrollTo().assertTextContains("Project Work (archived)", substring = true)
    }

    @Test fun weeklyNavigationZerosAndSelectionSurviveRecreation() {
        val monday = vm.date
        compose.onNodeWithTag("insightsWeekly").performClick()
        compose.onNodeWithTag("insightsTotal").assertTextEquals("1 h 30 min")
        compose.onNodeWithTag("insightsList").performScrollToNode(hasTestTag("insights-day-${monday.plusDays(6)}"))
        compose.onNodeWithTag("insights-day-${monday.plusDays(6)}").assertTextContains("0 min", substring = true)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("insightsList").performScrollToNode(hasTestTag("insightsWeekly"))
        compose.onNodeWithTag("insightsWeekly").assertIsSelected()
        compose.onNodeWithTag("insightsPrevious").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("insightsTotal").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("insightsTotal").assertTextEquals("0 min")
        compose.onNodeWithTag("insightsNext").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("insightsTotal").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("insightsTotal").assertTextEquals("1 h 30 min")
    }
}
