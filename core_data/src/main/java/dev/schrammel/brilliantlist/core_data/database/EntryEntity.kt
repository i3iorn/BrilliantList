package dev.schrammel.brilliantlist.core_data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity persisted in the SQLCipher-encrypted database.
 * Mirrors dev.schrammel.brilliantlist.core_model.Entry, which must stay a pure Kotlin model.
 */
@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey val id: java.util.UUID,
    val type: String,
    val textContent: String? = null,
    val mediaUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val position: Int,
    val isDeleted: Boolean = false,
    val isFavorite: Boolean = false
)
