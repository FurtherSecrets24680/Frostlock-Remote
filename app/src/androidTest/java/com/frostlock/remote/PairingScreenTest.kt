package com.frostlock.remote

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real Pair screen UI: manual entry -> Request pairing -> approved home screen.
 * Pass the encrypted code with `-e invite <FL1.code>`.
 */
@RunWith(AndroidJUnit4::class)
class PairingScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun manualEntryPairsAndReturnsHome() {
        val args = InstrumentationRegistry.getArguments()
        val code = requireNotNull(args.getString("invite"))

        compose.onAllNodesWithText("Pair a PC", useUnmergedTree = true)[0].performClick()
        compose.onNodeWithText("Enter code manually").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(code)
        compose.onNodeWithText("Request pairing").performClick()
        compose.onNodeWithText("Waiting for approval on the PC…", useUnmergedTree = true).assertExists()

        compose.waitUntil(timeoutMillis = 120_000) {
            compose.onAllNodesWithText("Pairing approved by", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Could not reach the PC", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Pairing approved by", substring = true, useUnmergedTree = true).assertExists()
    }
}
