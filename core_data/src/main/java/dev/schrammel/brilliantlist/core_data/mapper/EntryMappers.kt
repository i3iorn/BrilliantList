package dev.schrammel.brilliantlist.core_data.mapper

import dev.schrammel.brilliantlist.core_data.database.EntryEntity
import dev.schrammel.brilliantlist.core_model.Entry
import dev.schrammel.brilliantlist.core_model.EntryType

fun EntryEntity.toDomain(): Entry = Entry(
    id = id,
    type = EntryType.valueOf(type),
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
