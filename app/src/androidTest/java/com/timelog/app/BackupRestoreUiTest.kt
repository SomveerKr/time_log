package com.timelog.app

import android.Manifest
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.timelog.app.backup.*
import com.timelog.app.data.*
import com.timelog.app.ui.TimeLogViewModel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupRestoreUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun previewCancelRecreationAndExplicitReplacementPreservePermissionState() {
        val app = compose.activity.application as TimeLogApplication
        lateinit var vm: TimeLogViewModel
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity, TimeLogViewModel.Factory)[TimeLogViewModel::class.java] }
        compose.waitUntil(15_000) { vm.ready }
        val file = File(app.cacheDir, "synthetic-restore-ui.json")
        val permission = app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        val snapshot = runBlocking {
            app.store.saveSettings(AppSettings(onboardingComplete = true, remindersEnabled = true))
            app.store.backupSnapshot(System.currentTimeMillis()).also { writeBackup(it, file.outputStream()) }
        }
        runBlocking { app.store.createCategory("Keep until confirmed") }
        compose.waitUntil(10_000) { vm.settings?.onboardingComplete == true && vm.categories.size == snapshot.categories.size + 1 }
        compose.onNodeWithContentDescription("Settings").performClick()
        val before = runBlocking { app.store.backupSnapshot(snapshot.exportedAtMillis) }
        val deviceBefore = runBlocking { app.store.database.dao().deviceState() }
        try {
            compose.runOnUiThread { vm.previewBackup(Uri.fromFile(file)) }
            compose.waitUntil(10_000) { vm.restorePreview != null && !vm.busy }
            compose.onNodeWithText("Restore backup?").assertIsDisplayed()
            compose.onNodeWithText("Export current data").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(before, runBlocking { app.store.backupSnapshot(snapshot.exportedAtMillis) })
            assertEquals(deviceBefore, runBlocking { app.store.database.dao().deviceState() })

            compose.runOnUiThread { vm.previewBackup(Uri.fromFile(file)) }
            compose.waitUntil(10_000) { vm.restorePreview != null && !vm.busy }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Restore and replace").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { vm.restorePreview == null && !vm.busy && vm.settings?.remindersEnabled == false }
            assertEquals(snapshot.copy(settings = snapshot.settings.copy(remindersEnabled = false)), runBlocking { app.store.backupSnapshot(snapshot.exportedAtMillis) })
            assertEquals(permission, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            compose.onNodeWithText("Reminders off").performScrollTo().assertIsDisplayed()
        } finally { file.delete() }
    }
}
