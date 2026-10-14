package com.timelog.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class ShellNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigationAndSettingsSurviveRecreation() {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Set up later").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() ||
                compose.onAllNodes(hasTestTag("logTime") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        if (compose.onAllNodesWithText("Set up later").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Set up later").performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodes(hasTestTag("logTime") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithText("Log time").assertIsEnabled()
        compose.onNodeWithText("Insights").performClick()
        compose.onNodeWithTag("insightsDaily").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Your time stays yours.").performScrollTo().assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Your time stays yours.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("insightsDaily").assertIsDisplayed()
        compose.onAllNodesWithText("Today")[0].performClick()
        compose.onNodeWithText("Log time").assertIsEnabled()
    }
}
