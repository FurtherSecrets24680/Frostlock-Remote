package com.frostlock.remote.widget

import android.content.Context
import org.json.JSONObject

/** Last known state of the PC a widget points at, cached so the widget can draw instantly. */
internal data class StoredState(
    val known: Boolean = false,
    val active: Boolean = false,
    val busy: Boolean = false,
    val mode: String? = null,
    val remainingSeconds: Long = 0L,
    val remoteReleaseAllowed: Boolean = true,
    val error: String? = null,
) {
    fun toJson(): String = JSONObject()
        .put("known", known)
        .put("active", active)
        .put("busy", busy)
        .put("mode", mode.orEmpty())
        .put("remaining", remainingSeconds)
        .put("release", remoteReleaseAllowed)
        .put("error", error.orEmpty())
        .toString()

    /** Folds the cached row into the display model, taking "configured" from the widget binding. */
    fun toWidgetState(configured: Boolean) = WidgetState(
        configured = configured,
        checking = configured && !known,
        active = active,
        busy = busy,
        mode = mode,
        remainingSeconds = remainingSeconds,
        remoteReleaseAllowed = remoteReleaseAllowed,
        error = error,
    )

    companion object {
        fun fromJson(raw: String?): StoredState {
            if (raw.isNullOrBlank()) return StoredState()
            return runCatching {
                val json = JSONObject(raw)
                StoredState(
                    known = json.optBoolean("known"),
                    active = json.optBoolean("active"),
                    busy = json.optBoolean("busy"),
                    mode = json.optString("mode").takeIf(String::isNotBlank),
                    remainingSeconds = json.optLong("remaining"),
                    remoteReleaseAllowed = json.optBoolean("release", true),
                    error = json.optString("error").takeIf(String::isNotBlank),
                )
            }.getOrDefault(StoredState())
        }
    }
}

/** Which PC each widget instance controls, and the cached state for it. */
internal class WidgetStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("frostlock_widgets", Context.MODE_PRIVATE)

    fun profileId(widgetId: Int): String? = prefs.getString(profileKey(widgetId), null)

    fun bind(widgetId: Int, profileId: String) {
        prefs.edit().putString(profileKey(widgetId), profileId).remove(stateKey(widgetId)).apply()
    }

    fun unbind(widgetId: Int) {
        prefs.edit().remove(profileKey(widgetId)).remove(stateKey(widgetId)).apply()
    }

    fun state(widgetId: Int): StoredState = StoredState.fromJson(prefs.getString(stateKey(widgetId), null))

    fun saveState(widgetId: Int, state: StoredState) {
        prefs.edit().putString(stateKey(widgetId), state.toJson()).apply()
    }

    private fun profileKey(widgetId: Int) = "profile_$widgetId"
    private fun stateKey(widgetId: Int) = "state_$widgetId"
}
