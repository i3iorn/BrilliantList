package dev.schrammel.brilliantlist.core_data.mapper

import dev.schrammel.brilliantlist.core_data.database.EntryEntity
import dev.schrammel.brilliantlist.core_model.Entry
import dev.schrammel.brilliantlist.core_model.EntryType

fun EntryEntity.toDomain(): Entry = Entry(
    id = id,
    type = when (type) {
        "TEXT" -> EntryType.TEXT
        "IMAGE" -> EntryType.IMAGE
        "AUDIO" -> EntryType.AUDIO
        "LINK" -> EntryType.LINK
        // A type that a newer version wrote. An older build must not crash
        // on it, so it falls back to a plain text entry. The database keeps
        // the original word; only the in-memory model is downgraded.
        else -> EntryType.TEXT
    },
    textContent = textContent,
    mediaUri = mediaUri,
    createdAt = createdAt,
    updatedAt = updatedAt,
    position = position,
    isDeleted = isDeleted,
    isFavorite = isFavorite
)

fun Entry.toEntity(): EntryEntity = EntryEntity(
    id = id,
    type = type.name,
    textContent = textContent,
    mediaUri = mediaUri,
    createdAt = createdAt,
    updatedAt = updatedAt,
    position = position,
    isDeleted = isDeleted,
    isFavorite = isFavorite
)
