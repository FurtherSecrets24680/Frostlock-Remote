package com.frostlock.remote.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val haptics: Boolean = true,
    val requireDeviceAuthentication: Boolean = false,
)

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class PreferencesStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val hapticsKey = booleanPreferencesKey("haptics")
    private val deviceAuthenticationKey = booleanPreferencesKey("require_device_authentication")

    val flow: Flow<AppPreferences> = context.settingsDataStore.data.map { prefs ->
        AppPreferences(
            theme = ThemeMode.from(prefs[themeKey]),
            haptics = prefs[hapticsKey] ?: true,
            requireDeviceAuthentication = prefs[deviceAuthenticationKey] ?: false,
        )
    }

    suspend fun save(preferences: AppPreferences) {
        context.settingsDataStore.edit { prefs ->
            prefs[themeKey] = preferences.theme.name
            prefs[hapticsKey] = preferences.haptics
            prefs[deviceAuthenticationKey] = preferences.requireDeviceAuthentication
        }
    }
}
