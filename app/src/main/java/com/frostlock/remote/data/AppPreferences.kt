package com.frostlock.remote.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    ;

    companion object {
        fun from(raw: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

data class AppPreferences(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val freezeDurationSeconds: Int = 15 * 60,
    val untilMinuteOfDay: Int? = null,
    val haptics: Boolean = true,
) {
    fun normalized(): AppPreferences = copy(
        freezeDurationSeconds = if (freezeDurationSeconds == 0) 0
        else freezeDurationSeconds.coerceIn(MIN_FREEZE_SECONDS, MAX_FREEZE_SECONDS),
        untilMinuteOfDay = untilMinuteOfDay?.takeIf { it in 0 until MINUTES_PER_DAY },
    )

    companion object {
        const val MIN_FREEZE_SECONDS = 5 * 60
        const val MAX_FREEZE_SECONDS = 60 * 60
        const val MINUTES_PER_DAY = 24 * 60
    }
}

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class PreferencesStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val durationSecondsKey = intPreferencesKey("freeze_duration_seconds")
    private val untilMinuteKey = intPreferencesKey("freeze_until_minute")
    private val legacyDefaultMinutesKey = intPreferencesKey("default_freeze_minutes")
    private val hapticsKey = booleanPreferencesKey("haptics")

    val flow: Flow<AppPreferences> = context.settingsDataStore.data.map { prefs ->
        AppPreferences(
            theme = ThemeMode.from(prefs[themeKey]),
            freezeDurationSeconds = prefs[durationSecondsKey]
                ?: prefs[legacyDefaultMinutesKey]?.times(60)
                ?: AppPreferences().freezeDurationSeconds,
            untilMinuteOfDay = prefs[untilMinuteKey],
            haptics = prefs[hapticsKey] ?: true,
        ).normalized()
    }

    suspend fun save(preferences: AppPreferences) {
        val normalized = preferences.normalized()
        context.settingsDataStore.edit { prefs ->
            prefs[themeKey] = normalized.theme.name
            prefs[durationSecondsKey] = normalized.freezeDurationSeconds
            normalized.untilMinuteOfDay?.let { prefs[untilMinuteKey] = it } ?: prefs.remove(untilMinuteKey)
            prefs[hapticsKey] = normalized.haptics
        }
    }
}
