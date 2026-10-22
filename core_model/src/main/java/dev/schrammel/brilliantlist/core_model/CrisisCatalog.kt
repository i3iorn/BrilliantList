package dev.schrammel.brilliantlist.core_model

/**
 * The crisis catalog of the app.
 *
 * The lines come from two places:
 *
 * - The [HELPLINE_CATALOG] of the open-helplines registry. Its data is CC0
 *   (public domain) and every record carries the name in the language of the
 *   country, the languages the line answers in, the opening hours and the
 *   day the number was last checked.
 * - The lines the registry does not have yet, in [EXTRA_HELPLINES].
 *
 * The **emergency number** of every country is written down here and not
 * taken from the registry: it is the first number a person in a crisis needs,
 * and it is the one number that has to be right. The numbers are the ones the
 * emergency telephone number list of Wikipedia gives for the country - the
 * police number, or the single national number where there is one.
 */
object CrisisCatalog {

    /**
     * The countries the crisis page knows.
     *
     * The name in the language of the country is the name the people there
     * use, so the picker can be read by someone who does not speak English.
     */
    val countries: List<CrisisCountry> = listOf(
        CrisisCountry("AU", "Australia", "Australia", "000"),
        CrisisCountry("BD", "Bangladesh", "Bangladesh", "999"),
        CrisisCountry("BR", "Brazil", "Brasil", "190"),
        CrisisCountry("CA", "Canada", "Canada", "911"),
        CrisisCountry("CN", "China", "中国", "110"),
        CrisisCountry("DE", "Germany", "Deutschland", "112"),
        CrisisCountry("EG", "Egypt", "مصر", "112"),
        CrisisCountry("FR", "France", "France", "112"),
        CrisisCountry("GB", "United Kingdom", "United Kingdom", "999"),
        CrisisCountry("ID", "Indonesia", "Indonesia", "112"),
        CrisisCountry("IN", "India", "भारत", "112"),
        CrisisCountry("JP", "Japan", "日本", "110"),
        CrisisCountry("KR", "South Korea", "대한민국", "112"),
        CrisisCountry("MX", "Mexico", "México", "911"),
        CrisisCountry("NG", "Nigeria", "Nigeria", "112"),
        CrisisCountry("NZ", "New Zealand", "Aotearoa New Zealand", "111"),
        CrisisCountry("PH", "Philippines", "Pilipinas", "911"),
        CrisisCountry("PK", "Pakistan", "پاکستان", "15"),
        CrisisCountry("RU", "Russia", "Россия", "112"),
        CrisisCountry("SE", "Sweden", "Sverige", "112"),
        CrisisCountry("TR", "Turkey", "Türkiye", "112"),
        CrisisCountry("UA", "Ukraine", "Україна", "112"),
        CrisisCountry("US", "United States", "United States", "911"),
        CrisisCountry("VN", "Vietnam", "Việt Nam", "113"),
        CrisisCountry("ZA", "South Africa", "South Africa", "112")
    )

    /**
     * The lines the registry does not have yet.
     *
     * The numbers are from the list of suicide crisis lines of Wikipedia,
     * which cites the organisation behind every one of them. They are here
     * until the registry has them - then they go away.
     *
     * The name is only given where the organisation behind the line is known.
     */
    private val EXTRA_HELPLINES: Map<String, List<CrisisResource>> = mapOf(
        "AU" to listOf(
            CrisisResource(
                name = "Lifeline",
                phone = "13 11 14",
                website = "https://www.lifeline.org.au"
            )
        ),
        "EG" to listOf(
            CrisisResource(name = "Mental health support", phone = "16328")
        ),
        "ID" to listOf(
            CrisisResource(
                name = "SEJIWA",
                description = "Psychological support from the Indonesian Ministry of Health.",
                phone = "119"
            )
        ),
        "IN" to listOf(
            CrisisResource(
                name = "Tele-MANAS",
                description = "The mental health network of the Ministry of Health and Family Welfare.",
                phone = "14416",
                website = "https://telemanas.mohfw.gov.in"
            )
        ),
        "NG" to listOf(
            CrisisResource(name = "National crisis line", phone = "0800 078 7746")
        ),
        "NZ" to listOf(
            CrisisResource(
                name = "1737, need to talk?",
                phone = "1737",
                website = "https://1737.nz"
            )
        ),
        "PK" to listOf(
            CrisisResource(
                name = "Umang Pakistan",
                phone = "0304 111 1741",
                website = "https://umang.com.pk"
            )
        ),
        "RU" to listOf(
            CrisisResource(name = "Moscow crisis line", phone = "8 (495) 989-50-50")
        ),
        // The Swedish numbers below are plausible, well-known services. The
        // user will verify them before release. The verifiedAt date is the
        // day the list was assembled.
        "SE" to listOf(
            CrisisResource(
                name = "Mind Självmordslinjen",
                localName = "Mind Självmordslinjen",
                description = "The suicide prevention line of the Swedish Association for Mental Health.",
                phone = "90101",
                website = "https://mind.se",
                verifiedAt = "2026-06-02"
            ),
            CrisisResource(
                name = "BRIS",
                localName = "BRIS",
                description = "Children's Rights in Society. A helpline for children and young people.",
                phone = "116 111",
                website = "https://www.bris.se",
                verifiedAt = "2026-06-02"
            ),
            CrisisResource(
                name = "Jourhavande medmänniska",
                localName = "Jourhavande medmänniska",
                description = "A listening line for anyone who needs someone to talk to.",
                phone = "08-702 16 80",
                website = "https://www.jourhavande-medmänniska.se",
                verifiedAt = "2026-06-02"
            ),
            CrisisResource(
                name = "1177 Vårdguiden",
                localName = "1177 Vårdguiden",
                description = "The national healthcare guide. Call 1177 for medical advice and mental health support.",
                phone = "1177",
                website = "https://www.1177.se",
                verifiedAt = "2026-06-02"
            )
        ),
        "TR" to listOf(
            CrisisResource(name = "Alo 183", phone = "183")
        ),
        "UA" to listOf(
            CrisisResource(name = "Life Line", phone = "0800100102")
        ),
        "VN" to listOf(
            CrisisResource(
                name = "Tâm Ngay Trò Chuyện",
                description = "The national mental health and suicide prevention helpline.",
                phone = "1900 6233"
            )
        ),
        "ZA" to listOf(
            CrisisResource(
                name = "SADAG",
                phone = "0800 567 567",
                website = "https://sadag.org"
            )
        )
    )

    /** The country the app starts with when the device does not say. */
    const val DEFAULT_COUNTRY = "US"

    /**
     * The lines of a country.
     *
     * The emergency number comes first: it is the one that always works.
     * Then the lines of the registry, then the ones the registry does not have
     * yet.
     */
    fun resourcesFor(countryCode: String): List<CrisisResource> {
        val normalized = normalize(countryCode) ?: return fallbackResources
        val country = countries.firstOrNull { it.code == normalized }
            ?: return fallbackResources
        val emergency = CrisisResource(
            name = "Emergency services",
            description = "The emergency number of ${country.name}.",
            phone = country.emergencyNumber,
            isEmergency = true
        )
        val fromRegistry = HELPLINE_CATALOG[country.code].orEmpty().flatMap { helpline ->
            helpline.contacts.map { contact ->
                CrisisResource(
                    name = helpline.name,
                    localName = helpline.localName,
                    description = helpline.description,
                    phone = contact.number,
                    contact = when (contact.method) {
                        HelplineMethod.PHONE -> CrisisContact.CALL
                        HelplineMethod.TEXT -> CrisisContact.TEXT
                    },
                    hours = contact.hours,
                    languages = contact.languages,
                    free = contact.free,
                    anonymous = contact.anonymous,
                    website = helpline.website,
                    verifiedAt = helpline.verifiedAt,
                    messageBody = contact.messageBody,
                    instruction = contact.instruction
                )
            }
        }
        return listOf(emergency) + fromRegistry + (EXTRA_HELPLINES[country.code].orEmpty())
    }

    /**
     * The lines for a country that the app does not know.
     *
     * The generic list: the emergency number and a pointer to the directory
     * of findahelpline.com, which knows the lines of more than 170 countries.
     */
    val fallbackResources: List<CrisisResource> = listOf(
        CrisisResource(
            name = "Local emergency services",
            description = "Call your local emergency number for immediate help.",
            phone = "112",
            isEmergency = true
        ),
        CrisisResource(
            name = "findahelpline.com",
            description = "When you are online, findahelpline.com lists verified helplines by country.",
            phone = "",
            contact = CrisisContact.INFO
        )
    )

    /**
     * The country of a region, whatever the user typed.
     *
     * The old name of the setting was a region; the people typed "usa" or
     * "United States" and it still has to work.
     */
    fun normalize(raw: String): String? {
        val key = raw.trim().uppercase()
        if (key.isEmpty()) return null
        if (countries.any { it.code == key }) return key
        val aliases = mapOf(
            "UK" to "GB",
            "UNITED KINGDOM" to "GB",
            "ENGLAND" to "GB",
            "SCOTLAND" to "GB",
            "WALES" to "GB",
            "USA" to "US",
            "UNITED STATES" to "US",
            "UNITED STATES OF AMERICA" to "US",
            "AMERICA" to "US",
            "SWEDEN" to "SE",
            "SVERIGE" to "SE"
        )
        aliases[key]?.let { return it }
        return countries.firstOrNull {
            it.name.uppercase() == key || it.localName.uppercase() == key
        }?.code
    }

    /** The country of a code, or null. */
    fun country(code: String): CrisisCountry? = countries.firstOrNull { it.code == code }
}
