package com.hfremovalsadelaide.invoices.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: rotating the phone switches between a bottom bar (portrait) and a navigation rail
 * (landscape). The content - which hosts the WebView - must NOT be torn down and rebuilt by that
 * switch, otherwise the user's page and typed input are lost on every rotation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdaptiveShellFrameTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentSurvivesSwitchingBetweenBottomBarAndRail() {
        var useRail by mutableStateOf(false)
        var created = 0
        var disposed = 0
        var restoredCounter = -1

        composeRule.setContent {
            AdaptiveShellFrame(
                useRail = useRail,
                rail = { Text("RAIL") },
                bottomBar = { Text("BOTTOM") },
                snackbarHost = {}
            ) {
                val state = remember { mutableStateOf(41) }
                restoredCounter = state.value
                DisposableEffect(Unit) {
                    created++
                    onDispose { disposed++ }
                }
                Text("CONTENT")
            }
        }

        composeRule.onNodeWithText("BOTTOM").assertIsDisplayed()
        composeRule.onNodeWithText("CONTENT").assertIsDisplayed()

        composeRule.runOnIdle { useRail = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("RAIL").assertIsDisplayed()
        composeRule.onNodeWithText("CONTENT").assertIsDisplayed()

        composeRule.runOnIdle { useRail = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("BOTTOM").assertIsDisplayed()

        assertEquals("Content must be created exactly once", 1, created)
        assertEquals("Content must never be disposed by a layout switch", 0, disposed)
        assertEquals(41, restoredCounter)
    }

    @Test
    fun onlyTheActiveNavigationSurfaceIsShown() {
        var useRail by mutableStateOf(true)
        composeRule.setContent {
            AdaptiveShellFrame(
                useRail = useRail,
                rail = { Text("RAIL") },
                bottomBar = { Text("BOTTOM") },
                snackbarHost = {}
            ) { Text("CONTENT") }
        }

        composeRule.onNodeWithText("RAIL").assertIsDisplayed()
        composeRule.onNodeWithText("BOTTOM").assertDoesNotExist()
        composeRule.runOnIdle { useRail = false }
        composeRule.onNodeWithText("BOTTOM").assertIsDisplayed()
        composeRule.onNodeWithText("RAIL").assertDoesNotExist()
    }
}
