package dev.schrammel.brilliantlist

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dev.schrammel.brilliantlist.core_data.database.DatabaseKeyManager
import dev.schrammel.brilliantlist.core_ui.theme.BrilliantListTheme
import dev.schrammel.brilliantlist.ui.AppLockScreen
import dev.schrammel.brilliantlist.ui.AppLockState
import dev.schrammel.brilliantlist.ui.AppLockViewModel
import dev.schrammel.brilliantlist.ui.AppNavigation
import dev.schrammel.brilliantlist.ui.DeviceLockedScreen
import dev.schrammel.brilliantlist.ui.KeyLostScreen
import dev.schrammel.brilliantlist.ui.startOver
import dev.schrammel.brilliantlist.ui.rememberDeviceUnlocked
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var keyManager: DatabaseKeyManager

    private val lockViewModel: AppLockViewModel by viewModels()

    private lateinit var biometricPrompt: BiometricPrompt

    /** The unlock in progress, when it needs the crypto ceremony. */
    private var pendingSession: DatabaseKeyManager.UnlockSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Journal content must not end up in screenshots, screen recordings or
        // the thumbnail of the recent apps list.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    val session = pendingSession
                    pendingSession = null
                    lockViewModel.unlockSucceeded(session)
                }
            }
        )

        setContent {
            BrilliantListTheme {
                val unlockedState = rememberDeviceUnlocked(keyManager)
                val unlocked by unlockedState
                val lockState by lockViewModel.state.collectAsState()
                val lockAvailable by lockViewModel.available.collectAsState()
                // The key can be gone - the Keystore was reset, for example.
                // The entries cannot be decrypted any more, so the app says
                // so instead of crashing when the database is opened.
                val keyLost = remember { keyManager.isKeyLost() }

                when {
                    // The key cannot be used while the device is locked.
                    !unlocked -> DeviceLockedScreen(
                        onRetry = { unlockedState.value = keyManager.isAvailable() }
                    )

                    // The encryption key is gone: the entries are lost.
                    keyLost -> KeyLostScreen(
                        onStartOver = { startOver(this, keyManager) }
                    )

                    // The app lock: the user has to prove who they are.
                    lockState == AppLockState.LOCKED && lockAvailable -> AppLockScreen(
                        onUnlock = { authenticate() }
                    )

                    // The setting has not been read yet: nothing is shown, so
                    // the journal cannot be seen before the lock is known.
                    lockState == AppLockState.UNKNOWN -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )

                    else -> {
                        val appViewModel: AppViewModel = viewModel()
                        AppNavigation(appViewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lockViewModel.onResumed()
    }

    override fun onStop() {
        super.onStop()
        lockViewModel.onBackgrounded()
    }

    /**
     * Asks the user for their screen lock or their fingerprint.
     *
     * The device credential is allowed as well: a fingerprint is not always
     * available, and a journal that cannot be opened is worse than useless.
     */
    private fun authenticate() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_lock_prompt_title))
            .setSubtitle(getString(R.string.app_lock_prompt_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()
        // A passphrase wrapped by an auth key can only be opened through a
        // cipher the prompt authenticated: the session's cipher goes into the
        // CryptoObject, the prompt returns it on success.
        val session = lockViewModel.prepareUnlock()
        pendingSession = session
        if (session != null) {
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(session.cipher))
        } else {
            biometricPrompt.authenticate(promptInfo)
        }
    }
}
