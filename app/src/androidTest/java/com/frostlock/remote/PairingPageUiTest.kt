package com.frostlock.remote

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingPageUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeAndPairingScreensShowTheNewHierarchy() {
        compose.onNodeWithText("Nothing paired yet").assertExists()
        compose.onNodeWithText("No cloud account or relay.").assertDoesNotExist()
        compose.onAllNodesWithText("Pair a PC")[0].performClick()
        compose.onNodeWithText("Connect to FrostLock").assertExists()
        compose.onNodeWithText("Private, short-lived pairing").assertExists()
        compose.onNodeWithText("Enter code manually").performClick()
        compose.onNodeWithText("Encrypted pairing code").assertExists()
    }
}
