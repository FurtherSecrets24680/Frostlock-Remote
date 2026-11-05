package com.frostlock.remote

import com.frostlock.remote.widget.WidgetState
import com.frostlock.remote.widget.useCompactWidgetLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {

    @Test
    fun unconfiguredWidgetAsksForSetup() {
        val state = WidgetState()
        assertEquals("Set up", state.buttonLabel)
        assertEquals("Tap to choose a PC", state.statusLine)
    }

    @Test
    fun activeFreezeShowsUnlock() {
        val state = WidgetState(configured = true, active = true, mode = "Strict", remainingSeconds = 600)
        assertEquals("Unfreeze", state.buttonLabel)
        assertEquals("Strict mode, 10 min left", state.statusLine)
    }

    @Test
    fun busyWinsOverEverything() {
        val state = WidgetState(configured = true, active = true, busy = true)
        assertEquals("Working", state.buttonLabel)
        assertEquals("Talking to your PC…", state.statusLine)
    }

    @Test
    fun errorReplacesTheStatusLine() {
        val state = WidgetState(configured = true, error = "PC unreachable")
        assertEquals("Freeze", state.buttonLabel)
        assertEquals("PC unreachable", state.statusLine)
    }

    @Test
    fun longFreezesReadInHours() {
        assertEquals(
            "Gentle mode, 1 hr left",
            WidgetState(configured = true, active = true, mode = "Gentle", remainingSeconds = 3600).statusLine,
        )
        assertEquals(
            "Gentle mode, 1 hr 5 min left",
            WidgetState(configured = true, active = true, mode = "Gentle", remainingSeconds = 3900).statusLine,
        )
    }

    @Test
    fun roundedMinuteNeverShowsZero() {
        val state = WidgetState(configured = true, active = true, remainingSeconds = 20)
        assertEquals("Freeze mode, 1 min left", state.statusLine)
    }

    @Test
    fun blockedReleaseIsCalledOut() {
        val state = WidgetState(configured = true, active = true, remoteReleaseAllowed = false)
        assertEquals("Release is blocked by this PC", state.statusLine)
    }

    @Test
    fun compactLayoutIsUsedBelowThreeCells() {
        assertTrue(useCompactWidgetLayout(minWidthDp = 110, minHeightDp = 110))
        assertTrue(useCompactWidgetLayout(minWidthDp = 110, minHeightDp = 180))
        assertFalse(useCompactWidgetLayout(minWidthDp = 180, minHeightDp = 180))
        assertFalse(useCompactWidgetLayout(minWidthDp = 0, minHeightDp = 0))
    }

    @Test
    fun checkingStateIsNotMistakenForReady() {
        val state = WidgetState(configured = true, checking = true)
        assertEquals("Freeze", state.buttonLabel)
        assertEquals("Checking your PC…", state.statusLine)
    }
}
