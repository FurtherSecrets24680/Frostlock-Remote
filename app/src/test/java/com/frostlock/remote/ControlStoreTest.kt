package com.frostlock.remote

import com.frostlock.remote.control.chooseProfile
import com.frostlock.remote.data.PcProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlStoreTest {

    private fun profile(id: String, pairedAt: Long, lastSeenAt: Long? = null) = PcProfile(
        id = id,
        name = "PC $id",
        endpoint = "https://192.168.0.10:7443",
        certificateSha256 = "a".repeat(64),
        keyAlias = "alias-$id",
        pairedAt = pairedAt,
        lastSeenAt = lastSeenAt,
    )

    @Test
    fun noProfilesHasNoTarget() {
        assertNull(chooseProfile("anything", emptyList()))
    }

    @Test
    fun rememberedProfileWins() {
        val a = profile("a", pairedAt = 100, lastSeenAt = 500)
        val b = profile("b", pairedAt = 200, lastSeenAt = 900)
        assertEquals("a", chooseProfile("a", listOf(a, b))?.id)
    }

    @Test
    fun missingRememberedProfileFallsBackToMostRecent() {
        val a = profile("a", pairedAt = 100, lastSeenAt = 500)
        val b = profile("b", pairedAt = 200, lastSeenAt = 900)
        assertEquals("b", chooseProfile("gone", listOf(a, b))?.id)
    }

    @Test
    fun neverSeenProfileFallsBackToPairingTime() {
        val older = profile("older", pairedAt = 100)
        val newer = profile("newer", pairedAt = 700)
        assertEquals("newer", chooseProfile(null, listOf(older, newer))?.id)
    }

    @Test
    fun noRememberedProfileUsesMostRecent() {
        val a = profile("a", pairedAt = 100, lastSeenAt = 500)
        val b = profile("b", pairedAt = 200, lastSeenAt = 900)
        assertEquals("b", chooseProfile(null, listOf(a, b))?.id)
    }
}
