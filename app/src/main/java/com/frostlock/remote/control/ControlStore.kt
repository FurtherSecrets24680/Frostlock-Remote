package com.frostlock.remote.control

import android.content.Context
import com.frostlock.remote.data.PcProfile

/**
 * Remembers which PC the Quick Settings tile and notifications act on, and the last
 * known freeze state per PC so those surfaces can draw without waiting for the network.
 */
internal class ControlStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("frostlock_control", Context.MODE_PRIVATE)

    /** The PC the user opened most recently; the intended target when there is a choice. */
    fun defaultProfileId(): String? = prefs.getString("default_profile", null)

    fun setDefaultProfileId(profileId: String) {
        prefs.edit().putString("default_profile", profileId).apply()
    }

    /** The PC the tile last resolved and is currently showing. */
    fun lastProfileId(): String? = prefs.getString("last_profile", null)

    fun setLastProfileId(profileId: String) {
        prefs.edit().putString("last_profile", profileId).apply()
    }

    fun clearLastProfile() {
        prefs.edit().remove("last_profile").apply()
    }

    fun active(profileId: String): Boolean = prefs.getBoolean("active_$profileId", false)

    fun setActive(profileId: String, active: Boolean) {
        prefs.edit().putBoolean("active_$profileId", active).apply()
    }

    /** The PC to act on when the user has not picked one in this flow. */
    fun resolve(profiles: List<PcProfile>): PcProfile? = chooseProfile(defaultProfileId(), profiles)
}

/**
 * The remembered PC when it is still paired, otherwise the most recently used one.
 * Pure so the fallback behaviour can be unit tested.
 */
internal fun chooseProfile(stored: String?, profiles: List<PcProfile>): PcProfile? {
    if (profiles.isEmpty()) return null
    stored?.let { id -> profiles.firstOrNull { it.id == id }?.let { return it } }
    return profiles.maxByOrNull { it.lastSeenAt ?: it.pairedAt }
}
