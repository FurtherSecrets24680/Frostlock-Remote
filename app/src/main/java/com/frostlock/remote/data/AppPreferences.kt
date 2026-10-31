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
    val defaultFreezeMinutes: Int = 15,
    val confirmFreeze: Boolean = true,
    val confirmUnlock: Boolean = true,
    val haptics: Boolean = true,
) {
    fun normalized(): AppPreferences = copy(
        defaultFreezeMinutes = defaultFreezeMinutes.coerceIn(MIN_FREEZE_MINUTES, MAX_FREEZE_MINUTES),
    )

    companion object {
        const val MIN_FREEZE_MINUTES = 5
        const val MAX_FREEZE_MINUTES = 60
        const val FREEZE_STEP_MINUTES = 5
        val FREEZE_DURATIONS = listOf(5, 15, 30, 60)
    }
}

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class PreferencesStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val defaultMinutesKey = intPreferencesKey("default_freeze_minutes")
    private val confirmFreezeKey = booleanPreferencesKey("confirm_freeze")
    private val confirmUnlockKey = booleanPreferencesKey("confirm_unlock")
    private val hapticsKey = booleanPreferencesKey("haptics")

    val flow: Flow<AppPreferences> = context.settingsDataStore.data.map { prefs ->
        AppPreferences(
            theme = ThemeMode.from(prefs[themeKey]),
            defaultFreezeMinutes = prefs[defaultMinutesKey] ?: AppPreferences().defaultFreezeMinutes,
            confirmFreeze = prefs[confirmFreezeKey] ?: true,
            confirmUnlock = prefs[confirmUnlockKey] ?: true,
            haptics = prefs[hapticsKey] ?: true,
        ).normalized()
    }

    suspend fun save(preferences: AppPreferences) {
        val normalized = preferences.normalized()
        context.settingsDataStore.edit { prefs ->
            prefs[themeKey] = normalized.theme.name
            prefs[defaultMinutesKey] = normalized.defaultFreezeMinutes
            prefs[confirmFreezeKey] = normalized.confirmFreeze
            prefs[confirmUnlockKey] = normalized.confirmUnlock
            prefs[hapticsKey] = normalized.haptics
        }
    }
}
