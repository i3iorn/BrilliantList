package dev.schrammel.brilliantlist.core_model

import java.util.UUID

/**
 * Represents a user entry in the app (text, image, audio, or public).
 */
data class Entry(
    val id: UUID,
    val type: EntryType,
    val textContent: String? = null,
    val mediaUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val position: Int,
    val isDeleted: Boolean = false,
    val isFavorite: Boolean = false
)

enum class EntryType {
    TEXT, IMAGE, AUDIO, PUBLIC
}

/**
 * App settings.
 */
data class Settings(
    val rotationMode: RotationMode,
    val rotationIntervalMinutes: Int,
    val widgetAutoUpdate: Boolean,
    val notificationsEnabled: Boolean,
    val crisisRegion: String,
    val encryptionEnabled: Boolean
)

enum class RotationMode {
    RANDOM, SEQUENTIAL
}

data class PublicEntry(
    val id: UUID,
    val textContent: String,
    val category: String
)

/**
 * Region-specific crisis support resource, fully offline.
 */
data class CrisisResource(
    val name: String,
    val description: String,
    val phone: String
)

