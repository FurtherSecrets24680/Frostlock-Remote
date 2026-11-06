package com.frostlock.remote.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray

private val Context.profileDataStore by preferencesDataStore(name = "paired_pcs")

class ProfileStore(private val context: Context) {
    private val profilesKey = stringPreferencesKey("profiles")

    suspend fun all(): List<PcProfile> {
        val raw = context.profileDataStore.data.first()[profilesKey] ?: "[]"
        return allFrom(raw)
    }

    suspend fun save(profile: PcProfile) {
        context.profileDataStore.edit { prefs ->
            val all = allFrom(prefs[profilesKey])
            prefs[profilesKey] = JSONArray((all.filterNot { it.id == profile.id } + profile).map(PcProfile::toJson)).toString()
        }
    }

    suspend fun remove(id: String): PcProfile? {
        var removed: PcProfile? = null
        context.profileDataStore.edit { prefs ->
            val all = allFrom(prefs[profilesKey])
            removed = all.find { it.id == id }
            prefs[profilesKey] = JSONArray(all.filterNot { it.id == id }.map(PcProfile::toJson)).toString()
        }
        return removed
    }

    private fun allFrom(raw: String?): List<PcProfile> {
        val array = JSONArray(raw ?: "[]")
        return (0 until array.length()).map { array.getJSONObject(it).toProfile() }
            .filter { it.endpoint.startsWith("https://") }
    }
}
