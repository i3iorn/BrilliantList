package dev.schrammel.brilliantlist

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.schrammel.brilliantlist.core_data.repository.EntryRepository
import dev.schrammel.brilliantlist.core_data.repository.SettingsRepository

/**
 * Entry point of the app's composition root.
 *
 * The app is where all modules are wired together, so this is the place where
 * the singletons of the data layer can be reached from the outside - for
 * example from the instrumented tests, which need a known database state.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun entryRepository(): EntryRepository

    fun settingsRepository(): SettingsRepository
}
