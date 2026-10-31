package com.frostlock.remote

import com.frostlock.remote.data.AppPreferences
import com.frostlock.remote.data.PcLockSettings
import com.frostlock.remote.data.ThemeMode
import com.frostlock.remote.ui.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun durationKeepsPresetsUntilUnlockAndClampsCustomValues() {
        assertEquals(300, AppPreferences(freezeDurationSeconds = 30).normalized().freezeDurationSeconds)
        assertEquals(300, AppPreferences(freezeDurationSeconds = 120).normalized().freezeDurationSeconds)
        assertEquals(60 * 60, AppPreferences(freezeDurationSeconds = 500_000).normalized().freezeDurationSeconds)
        assertEquals(0, AppPreferences(freezeDurationSeconds = 0).normalized().freezeDurationSeconds)
        assertEquals(60, AppPreferences(freezeDurationSeconds = 60).normalized().freezeDurationSeconds)
    }

    @Test
    fun themeModeParsesKnownValuesAndFallsBack() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from(null))
        assertEquals(ThemeMode.DARK, ThemeMode.from("dark"))
        assertEquals(ThemeMode.DARK, ThemeMode.from("DARK"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.from("Light"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("sepia"))
    }

    @Test
    fun defaultsAreSafe() {
        val defaults = AppPreferences()
        assertEquals(ThemeMode.SYSTEM, defaults.theme)
        assertEquals(15 * 60, defaults.freezeDurationSeconds)
        assertEquals(null, defaults.untilMinuteOfDay)
        assertEquals(true, defaults.haptics)
    }

    @Test
    fun relativeTimeUsesCompactLabels() {
        val now = 1_000_000_000_000L
        assertEquals("just now", relativeTime(now - 20_000, now))
        assertEquals("5 min ago", relativeTime(now - 5 * 60_000, now))
        assertEquals("3 h ago", relativeTime(now - 3 * 60 * 60_000, now))
        assertEquals("2 d ago", relativeTime(now - 2 * 24 * 60 * 60_000, now))
        assertEquals("just now", relativeTime(now + 60_000, now))
    }

    @Test
    fun pcLockSettingsRoundTripPreservesIndefiniteAndPolicyFields() {
        val original = PcLockSettings(
            mode = "Pin",
            durationSeconds = 0,
            armSeconds = 8,
            safetyMaxSeconds = 3600,
            blockAllKeys = true,
            blockMouse = false,
            blockSystemKeys = true,
            lockScreenStyle = "Dim",
            lockScreenOnAllDisplays = false,
            showCountdownOnOverlay = false,
            showClockOnOverlay = true,
            lockScreenMessage = "Focus time",
            keepDisplayAwake = true,
            playSound = false,
            lockWindowsOnRelease = true,
            hasPin = true,
            mobilePinReleaseAllowed = false,
        )

        val parsed = PcLockSettings.fromJson(original.toJson())
        assertEquals(original, parsed.copy(hasPin = true, mobilePinReleaseAllowed = false))
        assertEquals(0, parsed.durationSeconds)
    }
}
