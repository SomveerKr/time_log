package com.timelog.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ShellNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigationAndSettingsSurviveRecreation() {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodes(hasTestTag("logTime") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithText("Log time").assertIsEnabled()
        compose.onNodeWithText("Insights").performClick()
        compose.onNodeWithText("A little time, a clearer picture.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Your time stays yours.").assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Your time stays yours.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("A little time, a clearer picture.").assertIsDisplayed()
        compose.onAllNodesWithText("Today")[0].performClick()
        compose.onNodeWithText("Log time").assertIsEnabled()
    }
}
