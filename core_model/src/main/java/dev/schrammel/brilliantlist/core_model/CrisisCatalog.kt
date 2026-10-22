package dev.schrammel.brilliantlist.core_model

/**
 * Regions with a bundled, offline crisis resource catalog.
 *
 * [code] is the canonical value persisted in settings. It is matched
 * case-insensitively and common aliases are normalized, see
 * [CrisisCatalog.normalize].
 */
enum class CrisisRegion(val code: String, val displayName: String) {
    US("US", "United States"),
    GB("GB", "United Kingdom"),
    CA("CA", "Canada"),
    AU("AU", "Australia"),
    DE("DE", "Germany");

    companion object {
        val DEFAULT: CrisisRegion = US
    }
}

/**
 * Offline crisis resource catalog.
 *
 * This lives in core_model (pure Kotlin, no Android dependencies) so the
 * region -> resources mapping can be unit tested on the JVM. Numbers are
 * widely published helplines; verify locally before shipping.
 */
object CrisisCatalog {

    /** Free-form input that users type or paste, mapped onto a supported region. */
    private val ALIASES: Map<String, CrisisRegion> = mapOf(
        "UK" to CrisisRegion.GB,
        "UNITED KINGDOM" to CrisisRegion.GB,
        "ENGLAND" to CrisisRegion.GB,
        "SCOTLAND" to CrisisRegion.GB,
        "WALES" to CrisisRegion.GB,
        "USA" to CrisisRegion.US,
        "UNITED STATES" to CrisisRegion.US,
        "UNITED STATES OF AMERICA" to CrisisRegion.US,
        "AMERICA" to CrisisRegion.US
    )

    /**
     * Shown for any region without a bundled catalog. Never present these as
     * if they were region specific.
     */
    val fallbackResources: List<CrisisResource> = listOf(
        CrisisResource(
            "Local Emergency Services",
            "Call your local emergency number for immediate help.",
            "112"
        ),
        CrisisResource(
            "findahelpline.com",
            "When you are online, findahelpline.com lists verified helplines by country.",
            "",
            contact = CrisisContact.INFO,
            instruction = "findahelpline.com"
        )
    )

    /**
     * Normalizes free-form user input to a supported region.
     * Returns null when the input does not match any supported region, so the
     * UI can tell the user instead of silently showing the generic fallback.
     */
    fun normalize(raw: String): CrisisRegion? {
        val key = raw.trim().uppercase()
        if (key.isEmpty()) return null
        return ALIASES[key] ?: CrisisRegion.entries.firstOrNull { it.code == key }
    }

    /** Resources for a supported region. */
    fun resourcesFor(region: CrisisRegion): List<CrisisResource> = when (region) {
        CrisisRegion.US -> listOf(
            CrisisResource(
                "988 Suicide & Crisis Lifeline",
                "24/7 free, confidential support for mental health, suicidal thoughts, or emotional distress.",
                "988"
            ),
            CrisisResource(
                "Crisis Text Line",
                "Text-based support, available 24/7.",
                "741741",
                contact = CrisisContact.TEXT,
                instruction = "Text HOME to 741741",
                messageBody = "HOME"
            ),
            CrisisResource(
                "Emergency Services",
                "Immediate life-threatening emergencies.",
                "911"
            )
        )

        CrisisRegion.GB -> listOf(
            CrisisResource(
                "Samaritans",
                "24/7 listening support, free from any phone.",
                "116123"
            ),
            CrisisResource(
                "SHOUT Crisis Text Line",
                "Text-based support, 24/7.",
                "85258",
                contact = CrisisContact.TEXT,
                instruction = "Text SHOUT to 85258",
                messageBody = "SHOUT"
            ),
            CrisisResource(
                "Emergency Services",
                "Immediate life-threatening emergencies.",
                "999"
            )
        )

        CrisisRegion.CA -> listOf(
            CrisisResource(
                "988 Suicide Crisis Helpline",
                "24/7 bilingual support across Canada.",
                "988"
            ),
            CrisisResource(
                "Emergency Services",
                "Immediate life-threatening emergencies.",
                "911"
            )
        )

        CrisisRegion.AU -> listOf(
            CrisisResource(
                "Lifeline Australia",
                "24/7 crisis support and suicide prevention.",
                "131114"
            ),
            CrisisResource(
                "Emergency Services",
                "Immediate life-threatening emergencies.",
                "000"
            )
        )

        CrisisRegion.DE -> listOf(
            CrisisResource(
                "Telefonseelsorge",
                "24/7 free and anonymous support (Deutsch/English).",
                "08001110111"
            ),
            CrisisResource(
                "Emergency Services",
                "Immediate life-threatening emergencies.",
                "112"
            )
        )
    }

    /**
     * Resources for a raw region string. Unknown input yields
     * [fallbackResources]; use [normalize] first when the caller needs to know
     * whether the region was actually recognized.
     */
    fun resourcesFor(raw: String): List<CrisisResource> =
        normalize(raw)?.let { resourcesFor(it) } ?: fallbackResources
}
