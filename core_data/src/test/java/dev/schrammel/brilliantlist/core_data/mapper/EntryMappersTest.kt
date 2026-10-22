package dev.schrammel.brilliantlist.core_data.mapper

import dev.schrammel.brilliantlist.core_data.database.EntryEntity
import dev.schrammel.brilliantlist.core_model.EntryType
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryMappersTest {

    private fun entity(type: String) = EntryEntity(
        id = UUID.randomUUID(),
        type = type,
        textContent = "content",
        mediaUri = null,
        createdAt = 0L,
        updatedAt = 0L,
        position = 0,
        isDeleted = false,
        isFavorite = false
    )

    @Test
    fun `known types map to their enum`() {
        assertEquals(EntryType.TEXT, entity("TEXT").toDomain().type)
        assertEquals(EntryType.IMAGE, entity("IMAGE").toDomain().type)
        assertEquals(EntryType.AUDIO, entity("AUDIO").toDomain().type)
        assertEquals(EntryType.LINK, entity("LINK").toDomain().type)
    }

    @Test
    fun `an unknown type is shown as text instead of crashing`() {
        // A type that a newer version wrote. An older build must not crash
        // on it. The database keeps the original word; only the in-memory
        // model is downgraded.
        assertEquals(EntryType.TEXT, entity("VIDEO").toDomain().type)
        assertEquals(EntryType.TEXT, entity("SOMETHING_NEW").toDomain().type)
    }
}
