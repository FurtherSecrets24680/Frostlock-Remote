package com.frostlock.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.frostlock.remote.data.DeviceSigner
import com.frostlock.remote.data.FrostLockApi
import com.frostlock.remote.data.PairingInvite
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * End-to-end check of the real Android pairing path against a running FrostLock PC.
 *
 * Run with:
 *   adb shell am instrument -w \
 *     -e invite '<encrypted pairing code>' \
 *     -e class com.frostlock.remote.PairingFlowTest \
 *     com.frostlock.remote.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class PairingFlowTest {
    @Test
    fun pairThenQueryStatus() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val code = requireNotNull(args.getString("invite")) { "Pass -e invite <encrypted pairing code>" }
        val invite = PairingInvite.parse(code)
        val signer = DeviceSigner()
        val api = FrostLockApi(signer)
        val alias = "instrumented-${UUID.randomUUID()}"

        val profile = api.pair(invite, "Instrumented test phone", alias)
        assertEquals(invite.endpoint, profile.endpoint)
        assertNotNull(profile.id)

        val snapshot = api.status(profile)
        assertNotNull(snapshot.phase)
    }
}
