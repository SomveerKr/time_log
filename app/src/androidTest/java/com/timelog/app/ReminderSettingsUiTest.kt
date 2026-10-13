package com.timelog.app

import android.text.format.DateFormat
import android.view.View
import android.widget.TimePicker
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.action.ViewActions.click
import com.timelog.app.data.AppSettings
import com.timelog.app.ui.TimeLogViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ReminderSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: TimeLogViewModel
    private val store get() = (compose.activity.application as TimeLogApplication).store

    @Before fun showSetupWithSyntheticSettings() {
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity, TimeLogViewModel.Factory)[TimeLogViewModel::class.java] }
        compose.waitUntil(15_000) { vm.ready }
        runBlocking { store.saveSettings(AppSettings()) }
        compose.waitUntil(15_000) { vm.settings?.onboardingComplete == false }
        compose.onNodeWithText("Set up later").assertIsDisplayed()
    }

    private fun time(minute: Int) = LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(compose.activity)) "HH:mm" else "h:mm a", Locale.getDefault()))

    private fun selectTime(tag: String, hour: Int, minute: Int) {
        compose.onNodeWithTag(tag).performClick()
        onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).perform(object : ViewAction {
            override fun getDescription() = "Select synthetic reminder window time"
            override fun getConstraints(): Matcher<View> = isAssignableFrom(TimePicker::class.java)
            override fun perform(controller: UiController, view: View) {
                (view as TimePicker).hour = hour; view.minute = minute; controller.loopMainThreadUntilIdle()
            }
        })
        onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click())
    }

    @Test fun setupPreviewMatchesScheduleAndSettingsSaveImmediately() {
        compose.onNodeWithTag("schedulePreview").assertTextEquals("First check-in ${time(600)}, last check-in ${time(1320)}")
        compose.onNodeWithTag("interval120").performClick()
        compose.onNodeWithTag("schedulePreview").assertTextEquals("First check-in ${time(660)}, last check-in ${time(1320)}")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("interval120").assertIsSelected()
        compose.onNodeWithTag("enableReminders").performClick()
        compose.waitUntil(10_000) { !vm.busy && vm.settings?.onboardingComplete == true }
        assertTrue(runBlocking { store.database.dao().settings()!!.remindersEnabled })
        assertEquals(120, runBlocking { store.database.dao().settings()!!.intervalMinutes })
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("reminderSwitch").assertIsOn()
        compose.onNodeWithTag("interval60").performClick()
        compose.waitUntil(10_000) { !vm.busy && vm.settings?.intervalMinutes == 60 }
        compose.onNodeWithTag("schedulePreview").assertTextEquals("First check-in ${time(600)}, last check-in ${time(1320)}")
        assertEquals(60, runBlocking { store.database.dao().settings()!!.intervalMinutes })
    }

    @Test fun invalidSetupWindowCannotEnableAndSkipKeepsRemindersOff() {
        selectTime("trackingEnd", 8, 0)
        compose.onNodeWithTag("schedulePreview").assertTextContains("end after start", substring = true)
        compose.onNodeWithTag("enableReminders").assertIsNotEnabled()
        compose.onNodeWithText("Set up later").performClick()
        compose.waitUntil(10_000) { !vm.busy && vm.settings?.onboardingComplete == true }
        assertFalse(runBlocking { store.database.dao().settings()!!.remindersEnabled })
        compose.onNodeWithTag("logTime").assertIsEnabled()
    }
}
