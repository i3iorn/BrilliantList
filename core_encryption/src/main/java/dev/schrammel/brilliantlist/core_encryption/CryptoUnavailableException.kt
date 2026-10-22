package dev.schrammel.brilliantlist.core_encryption

/**
 * Thrown when the Android Keystore cannot be used, e.g. because the device is
 * locked or has not been unlocked since boot.
 *
 * Callers must fail closed: never fall back to an unencrypted fallback.
 */
class CryptoUnavailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
