package dev.schrammel.brilliantlist.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.schrammel.brilliantlist.core_data.repository.SettingsRepository
import dev.schrammel.brilliantlist.core_model.AppLockDelay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The state of the app lock.
 *
 * The journal must not be shown before the setting is known, otherwise the
 * content would be visible for a moment before the lock appears.
 */
enum class AppLockState {
    /** The setting has not been read yet. */
    UNKNOWN,

    /** The user has to prove who they are. */
    LOCKED,

    /** The journal can be shown. */
    UNLOCKED
}

/**
 * The app lock.
 *
 * The journal is encrypted with a key that the device only hands out while
 * the device is unlocked. That protects the data on the disk, but it does not
 * stop the person holding the phone from reading the screen.
 *
 * The lock asks for the screen lock (or a fingerprint) before the journal is
 * shown. It is only active when the device can authenticate at all.
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    settingsRepository: SettingsRepository
) : ViewModel() {

    /** True when the user wants the lock. */
    val enabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.appLockEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * How long the journal stays unlocked after the app was in the
     * background.
     */
    val delay: StateFlow<AppLockDelay> = settingsRepository.settings
        .map { it.appLockDelay }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLockDelay.THIRTY_SECONDS)

    /**
     * True when the device can ask the user for their screen lock.
     *
     * It is checked again when the app comes back to the foreground: the
     * user can set up a screen lock while the app is closed.
     */
    private val _available = MutableStateFlow(canAuthenticate(context))
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _state = MutableStateFlow(AppLockState.UNKNOWN)

    /** The state of the lock. */
    val state: StateFlow<AppLockState> = _state

    private var backgroundedAt = 0L

    init {
        // The setting is read once: from then on the state follows it.
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _available.value = canAuthenticate(context)
                _state.value = when {
                    !settings.appLockEnabled || !_available.value -> AppLockState.UNLOCKED
                    _state.value == AppLockState.UNLOCKED -> AppLockState.UNLOCKED
                    else -> AppLockState.LOCKED
                }
            }
        }
    }

    /** The app went to the background. */
    fun onBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
    }

    /**
     * The app came back to the foreground.
     *
     * The lock only kicks in after a moment in the background, so switching
     * to another app for a second does not ask again. How long that moment
     * is, the user decides.
     */
    fun onResumed() {
        // The user can set up a screen lock while the app was closed.
        _available.value = canAuthenticate(context)
        val seconds = delay.value.seconds
        if (seconds < 0) {
            // The journal stays unlocked until the app is closed.
            backgroundedAt = 0
            return
        }
        if (backgroundedAt > 0 &&
            System.currentTimeMillis() - backgroundedAt > seconds * 1000L
        ) {
            lock()
        }
        backgroundedAt = 0
    }

    /** The user proved who they are. */
    fun unlockSucceeded() {
        _state.value = AppLockState.UNLOCKED
    }

    /** The journal has to be locked again. */
    fun lock() {
        if (enabled.value && _available.value) {
            _state.value = AppLockState.LOCKED
        }
    }

    private companion object {
        /** The lock is asked for again after this many seconds by default. */
        const val LOCK_GRACE_MILLIS = 30_000L
    }
}

/** True when the device can show a screen lock or a biometric prompt. */
internal fun canAuthenticate(context: Context): Boolean {
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    return BiometricManager.from(context).canAuthenticate(authenticators) ==
        BiometricManager.BIOMETRIC_SUCCESS
}
