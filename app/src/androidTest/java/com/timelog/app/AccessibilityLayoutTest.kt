package com.timelog.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.unit.dp
import com.timelog.app.data.AppSettings
import com.timelog.app.data.EntryInput
import com.timelog.app.data.MINUTE_MILLIS
import com.timelog.app.ui.TimeLogViewModel
import com.timelog.app.reminders.TEST_REQUEST
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class AccessibilityLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun dateNavigationAndEntryActionsRemainReachableWithLargeText() {
        val app = compose.activity.application as TimeLogApplication
        lateinit var vm: TimeLogViewModel
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity, TimeLogViewModel.Factory)[TimeLogViewModel::class.java] }
        compose.waitUntil(15_000) { vm.ready }
        runBlocking { app.store.saveSettings(AppSettings(onboardingComplete = true)) }
        compose.waitUntil(10_000) { vm.settings?.onboardingComplete == true }
        compose.onNodeWithTag("previousDay").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("selectedDate").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("nextDay").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("previousDay").performClick()
        compose.onNodeWithTag("nextDay").assertIsEnabled().performClick()
        compose.onNodeWithTag("logTime").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Start date").assertIsDisplayed()
        compose.onNodeWithContentDescription("Start time").assertIsDisplayed()
        compose.onNodeWithContentDescription("End date").assertIsDisplayed()
        compose.onNodeWithContentDescription("End time").assertIsDisplayed()
        compose.onNodeWithTag("saveEntry").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("reminderSwitch").assertContentDescriptionEquals("Reminders")
    }

    @Test fun measureOrdinaryReminderToSingleCategorySave() {
        val app = compose.activity.application as TimeLogApplication
        lateinit var vm: TimeLogViewModel
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity, TimeLogViewModel.Factory)[TimeLogViewModel::class.java] }
        compose.waitUntil(15_000) { vm.ready }
        runBlocking { app.store.saveSettings(AppSettings(onboardingComplete = true)) }
        compose.waitUntil(10_000) { vm.settings?.onboardingComplete == true }
        val category = vm.categories.first { it.name == "Chores and travel" }
        val history = runBlocking {
            app.store.database.dao().deleteAllEntries()
            app.store.save(EntryInput(category.id, vm.now - 120 * MINUTE_MILLIS, vm.now - 60 * MINUTE_MILLIS))
        }
        compose.waitUntil(10_000) { vm.entries.any { it.id == history.id } }
        val started = android.os.SystemClock.elapsedRealtime()
        compose.runOnUiThread { vm.openRequest(TEST_REQUEST) }
        compose.onNodeWithTag("first-category-${category.id}").performClick()
        val saving = android.os.SystemClock.elapsedRealtime()
        compose.onNodeWithTag("saveEntry").performClick()
        compose.waitUntil(10_000) { vm.draft == null && !vm.busy }
        val finished = android.os.SystemClock.elapsedRealtime()
        android.util.Log.i("TimeLogAccessibility", "Synthetic reminder-to-save=${finished - started} ms; save-click-to-completion=${finished - saving} ms; fontScale=${compose.activity.resources.configuration.fontScale}")
    }
}
