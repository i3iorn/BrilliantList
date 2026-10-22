package dev.schrammel.brilliantlist

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Turns the app lock off before the activity is started.
 *
 * The lock asks for the screen lock of the device, so on a device that has
 * one the tests would only see the lock screen. The rule has to run before
 * the compose rule, because that one starts the activity - so it is the
 * outer rule (a lower order number).
 */
class DisableAppLockRule : TestWatcher() {

    override fun starting(description: Description) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = EntryPointAccessors
            .fromApplication(context.applicationContext, AppEntryPoint::class.java)
            .settingsRepository()
        runBlocking { settings.setAppLockEnabled(false) }
    }
}
