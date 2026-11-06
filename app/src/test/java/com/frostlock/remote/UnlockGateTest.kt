package com.frostlock.remote

import com.frostlock.remote.security.UnlockAuth
import com.frostlock.remote.security.chooseAuthenticatorsFor
import org.junit.Assert.assertEquals
import org.junit.Test

class UnlockGateTest {

    @Test
    fun biometricWithCredentialAsksForBoth() {
        assertEquals(
            UnlockAuth.BIOMETRIC_OR_CREDENTIAL,
            chooseAuthenticatorsFor(sdk = 34, biometric = true, credential = true),
        )
        assertEquals(
            UnlockAuth.BIOMETRIC_OR_CREDENTIAL,
            chooseAuthenticatorsFor(sdk = 26, biometric = true, credential = true),
        )
    }

    @Test
    fun biometricOnlyDoesNotAskForCredential() {
        assertEquals(
            UnlockAuth.BIOMETRIC,
            chooseAuthenticatorsFor(sdk = 34, biometric = true, credential = false),
        )
        assertEquals(
            UnlockAuth.BIOMETRIC,
            chooseAuthenticatorsFor(sdk = 26, biometric = true, credential = false),
        )
    }

    @Test
    fun credentialOnlyOnModernAndroidUsesDeviceCredential() {
        assertEquals(
            UnlockAuth.DEVICE_CREDENTIAL,
            chooseAuthenticatorsFor(sdk = 30, biometric = false, credential = true),
        )
    }

    @Test
    fun credentialOnlyOnOldAndroidUsesTheTogetherMask() {
        assertEquals(
            UnlockAuth.BIOMETRIC_OR_CREDENTIAL,
            chooseAuthenticatorsFor(sdk = 29, biometric = false, credential = true),
        )
        assertEquals(
            UnlockAuth.BIOMETRIC_OR_CREDENTIAL,
            chooseAuthenticatorsFor(sdk = 26, biometric = false, credential = true),
        )
    }

    @Test
    fun unprotectedPhoneSkipsTheGate() {
        assertEquals(
            UnlockAuth.SKIP,
            chooseAuthenticatorsFor(sdk = 34, biometric = false, credential = false),
        )
        assertEquals(
            UnlockAuth.SKIP,
            chooseAuthenticatorsFor(sdk = 26, biometric = false, credential = false),
        )
    }
}
