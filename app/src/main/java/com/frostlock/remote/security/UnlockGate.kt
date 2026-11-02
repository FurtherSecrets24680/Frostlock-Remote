package com.frostlock.remote.security

/**
 * How much proof FrostLock asks for before releasing a freeze remotely.
 *
 * Unlocking is the one direction that weakens the PC's protection, so it is always
 * confirmed on the phone when the phone can prove who is holding it. This decision
 * is kept free of Android types so the whole table can be unit tested.
 */
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

/**
 * Picks the strongest confirmation the phone can actually show.
 *
 * [biometric] is whether a weak-or-better biometric is enrolled and [credential] is
 * whether the phone has a PIN, pattern or password. Device credential cannot be
 * requested on its own before Android 11: the biometric library only accepts it
 * alongside a weak biometric and falls back to the credential when none is enrolled,
 * which is what [UnlockAuth.BIOMETRIC_OR_CREDENTIAL] becomes there.
 */
internal fun chooseAuthenticatorsFor(sdk: Int, biometric: Boolean, credential: Boolean): UnlockAuth = when {
    biometric && credential -> UnlockAuth.BIOMETRIC_OR_CREDENTIAL
    biometric -> UnlockAuth.BIOMETRIC
    credential && sdk >= 30 -> UnlockAuth.DEVICE_CREDENTIAL
    credential -> UnlockAuth.BIOMETRIC_OR_CREDENTIAL
    else -> UnlockAuth.SKIP
}
