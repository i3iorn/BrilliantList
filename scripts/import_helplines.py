#!/usr/bin/env python3
"""Builds the crisis catalog of the app from the open-helplines registry.

The registry (https://github.com/Kouki-odaka/open-helplines) holds verified
helplines for many countries. Its data is CC0: public domain, no attribution
needed. Every record carries the name in the language of the country, the
languages the line answers in, the opening hours and the date the number was
last checked.

The app has no internet permission, so the catalog is bundled. This script
downloads the registry and writes a Kotlin file with the records. The file is
committed, so the build never needs the network - and the crisis page, the
most safety critical screen of the app, stays unit testable.

Usage:
    python3 scripts/import_helplines.py
    python3 scripts/import_helplines.py --check    # only report the age
"""

import argparse
import json
import sys
import urllib.request
from datetime import date, datetime
from pathlib import Path

REGISTRY = "https://raw.githubusercontent.com/Kouki-odaka/open-helplines/main/data"
OUTPUT = Path("core_model/src/main/java/dev/schrammel/brilliantlist/core_model/HelplineCatalog.kt")

# The countries of the registry. Its index.json is generated and empty in the
# repository, so the list is written down here.
COUNTRIES = [
    "AU", "BD", "BR", "CA", "CN", "DE", "EG", "FR", "GB", "ID", "IN", "JP",
    "KR", "MX", "NG", "NZ", "PH", "PK", "RU", "TR", "UA", "US", "VN", "ZA",
]

# The categories that belong on the crisis page of a journal.
CATEGORIES = ("suicide_prevention", "general_crisis")

# The methods the app can offer as an action.
METHODS = ("phone", "text")

# The emergency number of every country the app knows. These are not in the
# registry, and they are the first number a person in a crisis needs - so
# they are written down here, where a human can check them.
#
# The number is the one that reaches the emergency services of the country
# (the police number, or the single national number where there is one).
EMERGENCY_NUMBERS = {
    "AU": "000",
    "BD": "999",
    "BR": "190",
    "CA": "911",
    "CN": "110",
    "DE": "112",
    "EG": "112",
    "FR": "112",
    "GB": "999",
    "ID": "112",
    "IN": "112",
    "JP": "110",
    "KR": "112",
    "MX": "911",
    "NG": "112",
    "NZ": "111",
    "PH": "911",
    "PK": "15",
    "RU": "112",
    "TR": "112",
    "UA": "112",
    "US": "911",
    "VN": "113",
    "ZA": "112",
}

# The name of the country in the language of the country.
COUNTRY_NAMES = {
    "AU": "Australia",
    "BD": "Bangladesh",
    "BR": "Brasil",
    "CA": "Canada",
    "CN": "中国",
    "DE": "Deutschland",
    "EG": "مصر",
    "FR": "France",
    "GB": "United Kingdom",
    "ID": "Indonesia",
    "IN": "भारत",
    "JP": "日本",
    "KR": "대한민국",
    "MX": "México",
    "NG": "Nigeria",
    "NZ": "Aotearoa New Zealand",
    "PH": "Pilipinas",
    "PK": "پاکستان",
    "RU": "Россия",
    "TR": "Türkiye",
    "UA": "Україна",
    "US": "United States",
    "VN": "Việt Nam",
    "ZA": "South Africa",
}

# The languages the lines answer in, in the name of the language.
LANGUAGE_NAMES = {
    "en": "English",
    "sv": "Svenska",
    "de": "Deutsch",
    "fr": "Français",
    "es": "Español",
    "pt": "Português",
    "ar": "العربية",
    "zh": "中文",
    "ja": "日本語",
    "ko": "한국어",
    "ru": "Русский",
    "uk": "Українська",
    "hi": "हिन्दी",
    "bn": "বাংলা",
    "tr": "Türkçe",
    "vi": "Tiếng Việt",
    "id": "Bahasa Indonesia",
    "tl": "Tagalog",
    "it": "Italiano",
    "nl": "Nederlands",
    "pl": "Polski",
    "th": "ไทย",
    "fa": "فارسی",
    "ur": "اردو",
    "sw": "Kiswahili",
    "fil": "Filipino",
    "ne": "नेपाली",
    "so": "Soomaali",
    "he": "עברית",
}


def fetch(url: str) -> dict:
    request = urllib.request.Request(url, headers={"User-Agent": "brilliantlist-import"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def kotlin_string(value: str) -> str:
    """A string as a Kotlin literal.

    Everything outside of ASCII is written as a \\u escape, so the generated
    file is pure ASCII and builds the same way everywhere - the names of the
    lines are in Japanese, Bengali, Arabic and Chinese.
    """
    out = []
    for char in value:
        if char == "\\":
            out.append("\\\\")
        elif char == '"':
            out.append('\\"')
        elif char == "$":
            out.append("\\$")
        elif ord(char) < 0x80:
            out.append(char)
        else:
            out.append("\\u%04x" % ord(char))
    return '"%s"' % "".join(out)


def collect(countries):
    """Downloads the records of every country and returns them per country."""
    catalog = {}
    for code in countries:
        try:
            data = fetch("%s/countries/%s/helplines.json" % (REGISTRY, code.lower()))
        except urllib.error.HTTPError as error:
            if error.code == 404:
                print("  %s: no file in the registry" % code)
                continue
            raise
        records = []
        for record in data.get("records", []):
            if record.get("category") not in CATEGORIES:
                continue
            contacts = []
            for contact in record.get("contacts", []):
                if contact.get("method") not in METHODS:
                    continue
                contacts.append(
                    {
                        "method": contact["method"],
                        "number": contact.get("number", ""),
                        "hours": contact.get("hours", ""),
                        "languages": contact.get("languages", []),
                        "free": bool(contact.get("free")),
                        "anonymous": bool(contact.get("anonymous")),
                    }
                )
            if not contacts:
                continue
            records.append(
                {
                    "id": record.get("id", ""),
                    "name": record.get("name", ""),
                    "local_name": record.get("local_name", ""),
                    "description": record.get("description", ""),
                    "website": record.get("website", ""),
                    "verified_at": record.get("verified_at", ""),
                    "contacts": contacts,
                }
            )
        if records:
            catalog[code] = records
    return catalog


def render(catalog) -> str:
    """Renders the catalog as a Kotlin file."""
    lines = [
        "package dev.schrammel.brilliantlist.core_model",
        "",
        "/**",
        " * The crisis catalog of the app.",
        " *",
        " * GENERATED FILE - do not edit. It is written by",
        " * scripts/import_helplines.py from the open-helplines registry",
        " * (https://github.com/Kouki-odaka/open-helplines), whose data is CC0:",
        " * public domain, no attribution needed.",
        " *",
        " * Every record carries the name in the language of the country, the",
        " * languages the line answers in, the opening hours and the date the",
        " * number was last checked.",
        " */",
        "val HELPLINE_CATALOG: Map<String, List<Helpline>> = mapOf(",
    ]
    for code in sorted(catalog):
        lines.append('    "%s" to listOf(' % code)
        for record in catalog[code]:
            lines.append("        Helpline(")
            lines.append("            id = %s," % kotlin_string(record["id"]))
            lines.append("            country = %s," % kotlin_string(code))
            lines.append("            name = %s," % kotlin_string(record["name"]))
            if record["local_name"]:
                lines.append("            localName = %s," % kotlin_string(record["local_name"]))
            if record["description"]:
                lines.append("            description = %s," % kotlin_string(record["description"]))
            if record["website"]:
                lines.append("            website = %s," % kotlin_string(record["website"]))
            if record["verified_at"]:
                lines.append("            verifiedAt = %s," % kotlin_string(record["verified_at"]))
            if record["contacts"]:
                lines.append("            contacts = listOf(")
                for contact in record["contacts"]:
                    lines.append("                HelplineContact(")
                    lines.append("                    method = HelplineMethod.%s," % contact["method"].upper())
                    lines.append("                    number = %s," % kotlin_string(contact["number"]))
                    if contact["hours"]:
                        lines.append("                    hours = %s," % kotlin_string(contact["hours"]))
                    if contact["languages"]:
                        lines.append("                    languages = listOf(")
                        for language in contact["languages"]:
                            lines.append("                        %s," % kotlin_string(language))
                        lines.append("                    ),")
                    if contact["free"]:
                        lines.append("                    free = true,")
                    if contact["anonymous"]:
                        lines.append("                    anonymous = true,")
                    lines.append("                ),")
                lines.append("            ),")
            lines.append("        ),")
        lines.append("    ),")
    lines.append(")")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="only report how old the bundled data is",
    )
    arguments = parser.parse_args()

    if arguments.check:
        if not OUTPUT.exists():
            print("There is no catalog yet.")
            return 1
        text = OUTPUT.read_text(encoding="utf-8")
        dates = sorted(set(part.split('"')[0] for part in text.split("verifiedAt = \"")[1:]))
        if not dates:
            print("The catalog has no date.")
            return 1
        newest = max(dates)
        age = (date.today() - datetime.strptime(newest, "%Y-%m-%d").date()).days
        print("The newest number was checked on %s (%d days ago)." % (newest, age))
        return 0

    index = fetch("%s/index.json" % REGISTRY)
    generated = index.get("generated_at", "")
    print("The registry was built on %s." % generated)

    catalog = collect(COUNTRIES)
    print("The catalog has %d countries with %d records." % (
        len(catalog), sum(len(records) for records in catalog.values())))

    missing = sorted(set(EMERGENCY_NUMBERS) - set(catalog))
    if missing:
        print("These countries have an emergency number but no record: %s" % ", ".join(missing))
    extra = sorted(set(catalog) - set(EMERGENCY_NUMBERS))
    if extra:
        print("These countries have records but no emergency number: %s" % ", ".join(extra))

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(render(catalog), encoding="utf-8", newline="\n")
    print("Wrote %s (%d bytes)." % (OUTPUT, OUTPUT.stat().st_size))
    return 0


if __name__ == "__main__":
    sys.exit(main())
