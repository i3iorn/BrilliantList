package dev.schrammel.brilliantlist.core_model

import java.util.UUID

/**
 * Represents a user entry in the app (text, image or audio).
 */
data class Entry(
    val id: UUID,
    val type: EntryType,
    val textContent: String? = null,
    /** Location of the encrypted media file, relative to filesDir (e.g. "media/<uuid>.enc"). */
    val mediaUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val position: Int,
    val isDeleted: Boolean = false,
    val isFavorite: Boolean = false
)

enum class EntryType {
    TEXT, IMAGE, AUDIO
}

/**
 * The format of the audio media of an entry.
 *
 * The first versions recorded with MediaRecorder, which produced an M4A
 * container. The recorder now writes raw PCM samples straight into the
 * encrypted file, so there is no plaintext recording on the disk. Both
 * formats can be played, the format is derived from the media path.
 */
enum class AudioFormat {
    /** A compressed container (M4A). */
    ENCODED,

    /** Raw PCM samples; a WAV header is added when it is played. */
    PCM
}

/**
 * App settings.
 *
 * Encryption is unconditional in this app (see SECURITY.md), so there is
 * deliberately no "encryption enabled" switch. Notifications are not
 * implemented, so there is no notifications switch either.
 */
data class Settings(
    val rotationMode: RotationMode,
    val rotationIntervalMinutes: Int,
    val widgetAutoUpdate: Boolean,
    /** Canonical region code, see [CrisisRegion.code]. */
    val crisisRegion: String,
    /** Order of the entry list. */
    val sortMode: SortMode,
    /** Whether the home screen widget renders the entry text. */
    val widgetShowContent: Boolean,
    /**
     * Whether the app asks for the device credential / a biometric before it
     * shows any journal content.
     *
     * The lock is only effective on devices that can authenticate at all; the
     * settings screen says so when it cannot be enabled.
     */
    val appLockEnabled: Boolean = true
)

enum class RotationMode {
    RANDOM, SEQUENTIAL
}

/**
 * Order of the entry list.
 *
 * MANUAL keeps the order the user dragged the entries into.
 * NEWEST_FIRST sorts by creation date, newest at the top.
 */
enum class SortMode {
    MANUAL, NEWEST_FIRST
}

/**
 * How a crisis resource can be reached.
 *
 * Not every helpline can be called: text services only accept a message, and
 * some entries are pure information. Offering the wrong action in a crisis is
 * worse than offering none, so the type decides what the card does.
 */
enum class CrisisContact {
    /** A phone call; the platform dialer is opened. */
    CALL,

    /** A text message; the platform messaging app is opened. */
    TEXT,

    /** Information only, no action. */
    INFO
}

data class CrisisResource(
    val name: String,
    val description: String,
    /** The number or short code. Empty for [CrisisContact.INFO]. */
    val phone: String,
    val contact: CrisisContact = CrisisContact.CALL,
    /**
     * Extra instruction for text services, e.g. "Text HOME to 741741".
     * The text is pre-filled in the message, so the user only has to send it.
     */
    val instruction: String? = null,
    /** The body that is pre-filled for a [CrisisContact.TEXT] service. */
    val messageBody: String? = null
)
