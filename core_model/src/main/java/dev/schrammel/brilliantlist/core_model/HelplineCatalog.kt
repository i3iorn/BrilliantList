package dev.schrammel.brilliantlist.core_model

/**
 * The crisis catalog of the app.
 *
 * GENERATED FILE - do not edit. It is written by
 * scripts/import_helplines.py from the open-helplines registry
 * (https://github.com/Kouki-odaka/open-helplines), whose data is CC0:
 * public domain, no attribution needed.
 *
 * Every record carries the name in the language of the country, the
 * languages the line answers in, the opening hours and the date the
 * number was last checked.
 */
val HELPLINE_CATALOG: Map<String, List<Helpline>> = mapOf(
    "BD" to listOf(
        Helpline(
            id = "bd-kaan-pete-roi",
            country = "BD",
            name = "Kaan Pete Roi",
            localName = "\u0995\u09be\u09a8 \u09aa\u09c7\u09a4\u09c7 \u09b0\u0987",
            description = "Bangladesh's first and only emotional support and suicide prevention helpline, staffed by trained volunteers. Available daily 15:00\u201303:00 BST. Standard call rates apply.",
            website = "https://kaanpeteroi.org/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+8809612119911",
                    hours = "Daily 15:00\u201303:00 BST",
                    languages = listOf(
                        "bn",
                    ),
                    anonymous = true,
                ),
            ),
        ),
    ),
    "BR" to listOf(
        Helpline(
            id = "br-cvv-188",
            country = "BR",
            name = "CVV \u2014 Centro de Valoriza\u00e7\u00e3o da Vida",
            localName = "CVV \u2014 Ligue 188",
            description = "Brazil's national free emotional support and suicide prevention service. Dial 188 from any phone, free of charge. Operated by CVV (Centre for Life Valuing) in partnership with the Ministry of Health since 2015.",
            website = "https://cvv.org.br/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+55188",
                    hours = "24/7",
                    languages = listOf(
                        "pt",
                    ),
                    free = true,
                    anonymous = true,
                ),
            ),
        ),
    ),
    "CA" to listOf(
        Helpline(
            id = "ca-988-suicide-crisis-helpline",
            country = "CA",
            name = "9-8-8: Suicide Crisis Helpline",
            description = "Canada's national three-digit suicide crisis helpline. Call or text 9-8-8 for 24/7 bilingual (English/French) trauma-informed support. Coordinated by CAMH, launched November 2023. Federal investment of \$120M confirmed for 2026.",
            website = "https://talksuicide.ca/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+1988",
                    hours = "24/7",
                    languages = listOf(
                        "en",
                        "fr-CA",
                    ),
                    free = true,
                    anonymous = true,
                ),
            ),
        ),
    ),
    "CN" to listOf(
        Helpline(
            id = "cn-beijing-crisis-hotline",
            country = "CN",
            name = "Beijing Suicide Research and Prevention Center Hotline",
            localName = "\u5317\u4eac\u5fc3\u7406\u5371\u673a\u7814\u7a76\u4e0e\u5e72\u9884\u4e2d\u5fc3\u70ed\u7ebf",
            description = "National 24/7 crisis intervention hotline operated by the Beijing Suicide Research and Prevention Center. 400 number charged at local rate nationwide. Also reachable at 010-82951332 (mobile callers in Beijing).",
            website = "https://www.crisis.org.cn/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+864001619995",
                    hours = "24/7",
                    languages = listOf(
                        "zh",
                    ),
                    anonymous = true,
                ),
            ),
        ),
    ),
    "DE" to listOf(
        Helpline(
            id = "de-telefonseelsorge",
            country = "DE",
            name = "Telefonseelsorge",
            description = "Germany's national pastoral telephone counselling service, operated jointly by the Protestant and Catholic churches. Two freephone numbers available 24/7. Suicide prevention is a core service.",
            website = "https://www.telefonseelsorge.de/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+498001110111",
                    hours = "24/7",
                    languages = listOf(
                        "de",
                    ),
                    free = true,
                    anonymous = true,
                ),
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+498001110222",
                    hours = "24/7",
                    languages = listOf(
                        "de",
                    ),
                    free = true,
                    anonymous = true,
                ),
            ),
        ),
    ),
    "FR" to listOf(
        Helpline(
            id = "fr-3114",
            country = "FR",
            name = "3114 \u2014 Num\u00e9ro national de pr\u00e9vention du suicide",
            localName = "3114",
            description = "France's national suicide prevention line, managed by the Ministry of Health. Staffed by specially trained healthcare professionals (nurses and psychologists) 24/7. Also supports professionals and bereaved.",
            website = "https://3114.fr/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+33333114",
                    hours = "24/7",
                    languages = listOf(
                        "fr",
                    ),
                    free = true,
                    anonymous = true,
                ),
            ),
        ),
    ),
    "GB" to listOf(
        Helpline(
            id = "gb-samaritans",
            country = "GB",
            name = "Samaritans",
            description = "Free 24/7 listening service for anyone struggling to cope or at risk of suicide. Calls do not appear on phone bills. Available from all UK and Irish landlines and mobiles.",
            website = "https://www.samaritans.org/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+44116123",
                    hours = "24/7",
                    languages = listOf(
                        "en",
                    ),
                    free = true,
                    anonymous = true,
                ),
            ),
        ),
        Helpline(
            id = "gb-shout-crisis-text-line",
            country = "GB",
            name = "Shout Crisis Text Line",
            description = "UK's 24/7 free crisis text service. Text SHOUT to 85258 for confidential support from trained volunteers. Run by Shout 85258, powered by Crisis Text Line.",
            website = "https://giveusashout.org/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.TEXT,
                    number = "+4485258",
                    hours = "24/7",
                    languages = listOf(
                        "en",
                    ),
                    free = true,
                    anonymous = true,
                    messageBody = "SHOUT",
                    instruction = "Text SHOUT to 85258.",
                ),
            ),
        ),
    ),
    "JP" to listOf(
        Helpline(
            id = "jp-yorisoi-hotline",
            country = "JP",
            name = "Yorisoi Hotline",
            localName = "\u3088\u308a\u305d\u3044\u30db\u30c3\u30c8\u30e9\u30a4\u30f3",
            description = "24/7 free multilingual crisis support line for anyone in distress. Available in Japanese and 10 other languages. Operated by the NPO Counseling Center.",
            website = "https://www.since2011.net/yorisoi/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+81120279338",
                    hours = "24/7",
                    languages = listOf(
                        "ja",
                        "en",
                        "zh",
                        "ko",
                        "es",
                        "pt",
                        "fil",
                        "th",
                        "vi",
                        "ne",
                        "id",
                    ),
                    free = true,
                    anonymous = true,
                ),
            ),
        ),
        Helpline(
            id = "jp-inochi-no-denwa",
            country = "JP",
            name = "Inochi no Denwa",
            localName = "\u3044\u306e\u3061\u306e\u96fb\u8a71",
            description = "Free telephone crisis line staffed by trained volunteers. Available daily 16:00\u201321:00 JST, with a 24-hour session on the 10th of each month. Established in 1971.",
            website = "https://www.inochinodenwa.org/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+81120783556",
                    hours = "Daily 16:00\u201321:00 JST; 08:00 JST on 10th to 08:00 JST on 11th each month",
                    languages = listOf(
                        "ja",
                    ),
                    free = true,
                    anonymous = true,
                ),
            ),
        ),
        Helpline(
            id = "jp-tell-lifeline",
            country = "JP",
            name = "TELL Lifeline",
            description = "Toll-free English-language crisis and emotional support line in Japan. Operated since 1973 by TELL (Tokyo English Life Line), Japan's longest-running English-language support service.",
            website = "https://www.telljp.com/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+818003008355",
                    hours = "24/7",
                    languages = listOf(
                        "en",
                    ),
                    free = true,
                    anonymous = true,
                ),
            ),
        ),
    ),
    "KR" to listOf(
        Helpline(
            id = "kr-109-suicide-crisis",
            country = "KR",
            name = "109 Suicide Prevention Crisis Line",
            localName = "\uc790\uc0b4\uc608\ubc29 \uc0c1\ub2f4\uc804\ud654 109",
            description = "South Korea's unified national suicide prevention crisis line, operated by the Ministry of Health and Welfare. Consolidated from nine separate helplines including the former 1393 in January 2024.",
            website = "https://www.mohw.go.kr/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+82109",
                    hours = "24/7",
                    languages = listOf(
                        "ko",
                    ),
                    free = true,
                    anonymous = true,
                ),
            ),
        ),
    ),
    "MX" to listOf(
        Helpline(
            id = "mx-saptel",
            country = "MX",
            name = "SAPTEL \u2014 Suicide & Crisis Intervention",
            localName = "SAPTEL \u2014 Sistema de Atenci\u00f3n por Tel\u00e9fono",
            description = "Mexico's primary 24/7 crisis and suicide prevention telephone service, operated by the Mexican Psychiatric Association for over 30 years. Standard call rates apply. Staffed by psychologists.",
            website = "https://www.saptel.org.mx/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+525552598121",
                    hours = "24/7",
                    languages = listOf(
                        "es",
                    ),
                    anonymous = true,
                ),
            ),
        ),
    ),
    "PH" to listOf(
        Helpline(
            id = "ph-ncmh-crisis-hotline",
            country = "PH",
            name = "NCMH Crisis Hotline",
            localName = "National Center for Mental Health Crisis Hotline",
            description = "24/7 psychological first aid and crisis support operated by the National Center for Mental Health (NCMH), Department of Health. Reach via landline 1553 or mobile 0917-899-8727 (USAP).",
            website = "https://ncmh.gov.ph/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+6321553",
                    hours = "24/7",
                    languages = listOf(
                        "fil",
                        "en",
                    ),
                    anonymous = true,
                ),
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+639178998727",
                    hours = "24/7",
                    languages = listOf(
                        "fil",
                        "en",
                    ),
                    anonymous = true,
                ),
            ),
        ),
    ),
    "US" to listOf(
        Helpline(
            id = "us-988-lifeline",
            country = "US",
            name = "988 Suicide & Crisis Lifeline",
            description = "US national crisis network providing 24/7 free and confidential support for people in suicidal crisis or mental health distress. Dial or text 988. Funded by SAMHSA and administered by Vibrant Emotional Health.",
            website = "https://988lifeline.org/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.PHONE,
                    number = "+18002738255",
                    hours = "24/7",
                    languages = listOf(
                        "en",
                        "es",
                    ),
                    free = true,
                    anonymous = true,
                ),
            ),
        ),
        Helpline(
            id = "us-crisis-text-line",
            country = "US",
            name = "Crisis Text Line",
            description = "24/7 free crisis support via text message. Text HOME to 741741 from anywhere in the USA to reach a trained Crisis Counselor. Particularly accessible for those who cannot speak aloud.",
            website = "https://www.crisistextline.org/",
            verifiedAt = "2026-06-02",
            contacts = listOf(
                HelplineContact(
                    method = HelplineMethod.TEXT,
                    number = "+1741741",
                    hours = "24/7",
                    languages = listOf(
                        "en",
                    ),
                    free = true,
                    anonymous = true,
                    messageBody = "HOME",
                    instruction = "Text HOME to 741741.",
                ),
            ),
        ),
    ),
)
