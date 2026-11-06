package com.frostlock.remote.security

internal enum class UnlockAuth {
    /** Fingerprint or face, with the device PIN, pattern or password as a fallback. */
    BIOMETRIC_OR_CREDENTIAL,

    /** A biometric only; the phone has no separate device credential to fall back to. */
    BIOMETRIC,

    /** No biometric is enrolled, but the phone is secured with a credential. */
    DEVICE_CREDENTIAL,

    /** The phone has no screen lock, so there is nothing to ask for. */
    SKIP,
}

internal fun chooseAuthenticatorsFor(sdk: Int, biometric: Boolean, credential: Boolean): UnlockAuth = when {
    biometric && credential -> UnlockAuth.BIOMETRIC_OR_CREDENTIAL
    biometric -> UnlockAuth.BIOMETRIC
    credential && sdk >= 30 -> UnlockAuth.DEVICE_CREDENTIAL
    credential -> UnlockAuth.BIOMETRIC_OR_CREDENTIAL
    else -> UnlockAuth.SKIP
}
