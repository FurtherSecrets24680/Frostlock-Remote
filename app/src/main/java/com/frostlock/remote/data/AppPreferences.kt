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

enum class TaskUnits(val label: String) {
    AUTO("Auto"),
    MB("MB"),
    GB("GB"),
    ;

    companion object {
        fun from(raw: String?): TaskUnits =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO
    }
}

enum class TaskRefresh(val label: String, val seconds: Int) {
    TWO("2 seconds", 2),
    FOUR("4 seconds", 4),
    EIGHT("8 seconds", 8),
    FIFTEEN("15 seconds", 15),
    PAUSED("Paused", 0),
    ;

    companion object {
        fun from(raw: String?): TaskRefresh =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FOUR
    }
}

data class AppPreferences(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val haptics: Boolean = true,
    val requireDeviceAuthentication: Boolean = false,
    val taskUnits: TaskUnits = TaskUnits.AUTO,
    val taskGrouping: Boolean = true,
    val taskRefresh: TaskRefresh = TaskRefresh.FOUR,
)

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class PreferencesStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val hapticsKey = booleanPreferencesKey("haptics")
    private val deviceAuthenticationKey = booleanPreferencesKey("require_device_authentication")
    private val taskUnitsKey = stringPreferencesKey("task_units")
    private val taskGroupingKey = booleanPreferencesKey("task_grouping")
    private val taskRefreshKey = stringPreferencesKey("task_refresh")

    val flow: Flow<AppPreferences> = context.settingsDataStore.data.map { prefs ->
        AppPreferences(
            theme = ThemeMode.from(prefs[themeKey]),
            haptics = prefs[hapticsKey] ?: true,
            requireDeviceAuthentication = prefs[deviceAuthenticationKey] ?: false,
            taskUnits = TaskUnits.from(prefs[taskUnitsKey]),
            taskGrouping = prefs[taskGroupingKey] ?: true,
            taskRefresh = TaskRefresh.from(prefs[taskRefreshKey]),
        )
    }

    suspend fun save(preferences: AppPreferences) {
        context.settingsDataStore.edit { prefs ->
            prefs[themeKey] = preferences.theme.name
            prefs[hapticsKey] = preferences.haptics
            prefs[deviceAuthenticationKey] = preferences.requireDeviceAuthentication
            prefs[taskUnitsKey] = preferences.taskUnits.name
            prefs[taskGroupingKey] = preferences.taskGrouping
            prefs[taskRefreshKey] = preferences.taskRefresh.name
        }
    }
}
