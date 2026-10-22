package dev.schrammel.brilliantlist.core_data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Small key/value table for app state that must not be readable in plaintext
 * (it lives in the SQLCipher encrypted database).
 *
 * Used for example for the id of the entry that was surfaced last, so the
 * sequential rotation can advance.
 */
@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val key: String,
    val value: String?
)
