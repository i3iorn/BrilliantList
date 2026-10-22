#!/usr/bin/env python3
"""Creates the release key of the app.

The key has to exist for as long as the app is distributed: Android only
installs an update over an existing app when the signature is the same. A
lost key means the app can never be updated again - every user has to
uninstall it first, and their data is gone with it.

So the key is the most precious file of the project. It is not in the
repository: it is created here, kept in the keystore/ directory (which is
ignored by git) and stored in the repository secrets, so the CI can sign
with it.

Usage:
    python3 scripts/create_release_key.py
    python3 scripts/create_release_key.py --password "..." --output my.jks

The script prints the values that the CI needs. The base64 of the keystore
goes into the secret RELEASE_KEYSTORE_BASE64, the rest into
RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD.
"""

import argparse
import base64
import os
import secrets
import shutil
import subprocess
import sys
from pathlib import Path

KEY_ALIAS = "brilliantlist"
KEYSTORE_NAME = "brilliantlist-release.jks"
VALIDITY_DAYS = 10950  # 30 years, so the key outlives every device
DNAME = "CN=BrilliantList, OU=BrilliantList, O=BrilliantList, C=DE"


def find_keytool() -> str:
    """Finds the keytool of a JDK."""
    for name in ("keytool", "keytool.exe"):
        found = shutil.which(name)
        if found:
            return found

    # The JDKs that Gradle downloads, and the usual install locations.
    candidates = []
    gradle_jdks = Path.home() / ".gradle" / "jdks"
    if gradle_jdks.is_dir():
        candidates += sorted(gradle_jdks.glob("*/bin/keytool*"))
    for variable in ("JAVA_HOME", "ANDROID_STUDIO_JDK"):
        home = os.environ.get(variable)
        if home:
            candidates += sorted(Path(home).glob("bin/keytool*"))
    android_studio = Path("/Applications/Android Studio.app/Contents/jbr/bin")
    if android_studio.parent.exists():
        candidates += sorted(android_studio.glob("keytool*"))

    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    raise SystemExit(
        "No keytool found. A JDK is needed - the one of Android Studio or the "
        "one Gradle downloaded works."
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default=None, help="where the keystore goes")
    parser.add_argument("--password", default=None, help="the password of the key")
    parser.add_argument("--alias", default=KEY_ALIAS, help="the alias of the key")
    arguments = parser.parse_args()

    if arguments.password:
        password = arguments.password
    else:
        password = secrets.token_urlsafe(24)

    output = Path(arguments.output) if arguments.output else Path("keystore") / KEYSTORE_NAME
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        print("%s exists already - it was not replaced." % output)
        print("A new key means that the app cannot be updated over an installed one.")
        return 1

    keytool = find_keytool()
    command = [
        keytool,
        "-genkeypair",
        "-v",
        "-keystore", str(output),
        "-storetype", "PKCS12",
        "-alias", arguments.alias,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-sigalg", "SHA256withRSA",
        "-validity", str(VALIDITY_DAYS),
        "-storepass", password,
        "-keypass", password,
        "-dname", DNAME,
    ]
    print("Creating the release key ...")
    subprocess.run(command, check=True)

    encoded = base64.b64encode(output.read_bytes()).decode("ascii")
    credentials = output.with_suffix(".credentials.txt")
    credentials.write_text(
        "\n".join(
            [
                "The release key of BrilliantList. Keep it safe: Android only",
                "installs an update over an installed app when the signature is",
                "the same.",
                "",
                "RELEASE_KEYSTORE_BASE64=%s" % encoded,
                "RELEASE_KEYSTORE_PASSWORD=%s" % password,
                "RELEASE_KEY_ALIAS=%s" % arguments.alias,
                "RELEASE_KEY_PASSWORD=%s" % password,
                "",
            ]
        ),
        encoding="utf-8",
        # The line endings are always the same, so the file can be read the
        # same way on every system.
        newline="\n",
    )

    print()
    print("The key is in %s" % output)
    print("The credentials are in %s - keep that file, it is not in git." % credentials)
    print()
    print("Put them into the repository secrets:")
    print("  gh secret set RELEASE_KEYSTORE_BASE64 < %s" % credentials)
    print("  gh secret set RELEASE_KEYSTORE_PASSWORD --body '<the password>'")
    print("  gh secret set RELEASE_KEY_ALIAS --body '%s'" % arguments.alias)
    print("  gh secret set RELEASE_KEY_PASSWORD --body '<the password>'")
    return 0


if __name__ == "__main__":
    sys.exit(main())
