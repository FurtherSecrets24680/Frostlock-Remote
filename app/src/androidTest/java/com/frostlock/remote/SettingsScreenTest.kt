package com.frostlock.remote

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun themeSwitchAppliesImmediatelyAndSettingsScroll() {
        compose.onNodeWithContentDescription("Settings").performClick()

        compose.onNodeWithText("APPEARANCE").assertExists()
        compose.onNodeWithText("FREEZE CONTROLS").assertDoesNotExist()
        compose.onNodeWithText("Until unlock").assertDoesNotExist()
        capture("settings-dark-or-system.png")

        compose.onNodeWithContentDescription("Theme selection").performClick()
        compose.onNodeWithContentDescription("Theme option: System").performClick()
        compose.onNodeWithContentDescription("Theme selection").assertTextEquals("System")

        compose.onNodeWithContentDescription("Theme selection").performClick()
        compose.onNodeWithContentDescription("Theme option: Light").performClick()
        compose.onNodeWithContentDescription("Theme selection").assertTextEquals("Light")
        compose.waitForIdle()
        capture("settings-light.png")

        compose.onNodeWithContentDescription("Theme selection").performClick()
        compose.onNodeWithContentDescription("Theme option: Dark").performClick()
        compose.onNodeWithContentDescription("Theme selection").assertTextEquals("Dark")
        compose.waitForIdle()
        capture("settings-dark.png")

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Reset settings"))
        compose.onNodeWithText("Reset settings").assertExists()
        capture("settings-bottom.png")
    }
}
