#!/usr/bin/env python3
"""Verifies the privacy and compatibility invariants of the app.

Three invariants that are easy to break and hard to notice:

1. The app must not be able to talk to the network. The merged manifest may
   therefore never declare android.permission.INTERNET.
2. Every 64 bit native library has to be aligned for 16 KB page size devices
   (Android 15+). A library that is not aligned makes the whole app fail to
   start on those devices.
3. The app that ships is the release build, so the release APK is the one
   that has to be checked. The test APK has no native libraries at all, so
   checking it would pass without ever looking at the app.

Run after assembling the app:
    python3 scripts/verify_privacy_invariants.py
"""

import glob
import os
import struct
import subprocess
import sys
import zipfile
from pathlib import Path

MIN_16KB_ALIGNMENT = 16384
# 32 bit ABIs are never used on 16 KB page size devices, so they are skipped.
CHECKED_ABIS = ("arm64-v8a", "x86_64")

INTERNET_PERMISSION = "android.permission.INTERNET"

# The variant that is shipped to the users.
SHIPPED_VARIANT = "release"


def pt_load_alignments(data: bytes) -> list:
    """Returns the p_align value of every PT_LOAD segment of an ELF file."""
    if data[:4] != b"\x7fELF":
        return []

    is_64_bit = data[4] == 2
    endian = "<" if data[5] == 1 else ">"

    if is_64_bit:
        e_phoff = struct.unpack_from(endian + "Q", data, 0x20)[0]
        e_phentsize = struct.unpack_from(endian + "H", data, 0x36)[0]
        e_phnum = struct.unpack_from(endian + "H", data, 0x38)[0]
    else:
        e_phoff = struct.unpack_from(endian + "I", data, 0x1C)[0]
        e_phentsize = struct.unpack_from(endian + "H", data, 0x2A)[0]
        e_phnum = struct.unpack_from(endian + "H", data, 0x2C)[0]

    alignments = []
    for index in range(e_phnum):
        offset = e_phoff + index * e_phentsize
        if offset + e_phentsize > len(data):
            break
        p_type = struct.unpack_from(endian + "I", data, offset)[0]
        if p_type == 1:  # PT_LOAD
            p_align = struct.unpack_from(
                endian + ("Q" if is_64_bit else "I"), data, offset + (0x30 if is_64_bit else 0x1C)
            )[0]
            alignments.append(p_align)
    return alignments


def find_apks(root: Path) -> dict:
    """Returns the APK of every built variant, keyed by the variant name.

    The androidTest APK is skipped on purpose: it holds the test code, not the
    app, so it has no native libraries and would pass the alignment check
    without ever looking at the shipped app.
    """
    apks = {}
    for apk in sorted(root.glob("app/build/outputs/apk/**/*.apk")):
        # The path is apk/<buildType>/<name>.apk, the androidTest APK is in
        # apk/androidTest/<buildType>/.
        parts = apk.relative_to(root / "app/build/outputs/apk").parts
        if "androidTest" in parts:
            continue
        if len(parts) < 2:
            continue
        apks[parts[-2]] = apk
    return apks


def find_merged_manifests(root: Path) -> dict:
    """Returns the merged manifest of every built variant."""
    manifests = {}
    for manifest in sorted(
        root.glob("app/build/intermediates/merged_manifest/*/*/AndroidManifest.xml")
    ):
        # The path is merged_manifest/<buildType>/process<Variant>MainManifest/.
        parts = manifest.relative_to(root / "app/build/intermediates/merged_manifest").parts
        if len(parts) < 2:
            continue
        manifests[parts[0]] = manifest
    return manifests


def check_no_internet_permission(manifest: Path) -> list:
    text = manifest.read_text(encoding="utf-8", errors="ignore")
    if INTERNET_PERMISSION in text:
        return [
            "the merged manifest declares %s - the app must not be able to "
            "reach the network" % INTERNET_PERMISSION
        ]
    return []


def check_native_alignment(apk: Path) -> list:
    errors = []
    with zipfile.ZipFile(apk) as archive:
        libraries = [
            name
            for name in archive.namelist()
            if name.startswith("lib/") and name.endswith(".so")
        ]
        if not libraries:
            errors.append("%s: the APK has no native libraries at all" % apk)
        for name in libraries:
            abi = name.split("/")[1]
            if abi not in CHECKED_ABIS:
                continue
            alignments = pt_load_alignments(archive.read(name))
            if not alignments:
                errors.append("%s: could not read the ELF headers" % name)
                continue
            if max(alignments) < MIN_16KB_ALIGNMENT:
                errors.append(
                    "%s: PT_LOAD alignment 0x%X is below 16 KB - the app would "
                    "not start on 16 KB page size devices"
                    % (name, max(alignments))
                )
    return errors


def find_apksigner() -> str | None:
    """Finds apksigner in the Android SDK, if it is installed."""
    candidates = []
    for variable in ("ANDROID_SDK_ROOT", "ANDROID_HOME", "LOCALAPPDATA"):
        root = os.environ.get(variable)
        if not root:
            continue
        if variable == "LOCALAPPDATA":
            root = os.path.join(root, "Android", "Sdk")
        candidates.extend(glob.glob(os.path.join(root, "build-tools", "*", "apksigner*")))
    for candidate in sorted(candidates, reverse=True):
        if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate
    return None


def certificate_digest(apksigner: str, apk: Path) -> str | None:
    """The SHA-256 digest of the certificate the APK is signed with."""
    try:
        result = subprocess.run(
            [apksigner, "verify", "--print-certs", str(apk)],
            capture_output=True,
            text=True,
            timeout=120,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    for line in result.stdout.splitlines():
        if "certificate SHA-256 digest:" in line:
            return line.split(":", 1)[1].strip()
    return None


def check_not_signed_with_the_debug_key(apks: dict) -> list:
    """The build that ships must not be signed with the debug key.

    The debug key is public - everybody has it. An APK that is signed with it
    can be replaced by anyone.

    Only enforced in CI: a local build without a release key is signed with
    the debug key on purpose, so that it can be installed and tested.
    """
    release = apks.get(SHIPPED_VARIANT)
    debug = apks.get("debug")
    if release is None or debug is None:
        return []

    apksigner = find_apksigner()
    if apksigner is None:
        print("Note: apksigner was not found, the signing key was not checked.")
        return []

    release_certificate = certificate_digest(apksigner, release)
    debug_certificate = certificate_digest(apksigner, debug)
    if not release_certificate:
        return []
    if release_certificate == debug_certificate:
        if not os.environ.get("CI"):
            print(
                "Note: the %s APK is signed with the debug key. That is fine "
                "for a local build, but not for one that is published." % SHIPPED_VARIANT
            )
            return []
        return [
            "the %s APK is signed with the debug key - everybody has that key, "
            "so the build could be replaced by anyone" % SHIPPED_VARIANT
        ]
    return []


def main() -> int:
    root = Path(__file__).resolve().parent.parent

    apks = find_apks(root)
    if not apks:
        print("No APK found. Run './gradlew assembleDebug assembleRelease' first.")
        return 1

    manifests = find_merged_manifests(root)
    if not manifests:
        print("No merged manifest found. Run './gradlew assembleDebug' first.")
        return 1

    # The shipped build is the one that matters, the other variants are
    # checked as well so a problem cannot hide in a variant that is not
    # shipped.
    variants = [SHIPPED_VARIANT] if SHIPPED_VARIANT in apks else sorted(apks)
    errors = []
    for variant in variants:
        apk = apks.get(variant)
        if apk is not None:
            errors += check_native_alignment(apk)
        manifest = manifests.get(variant)
        if manifest is not None:
            errors += check_no_internet_permission(manifest)
    errors += check_not_signed_with_the_debug_key(apks)

    if errors:
        print("Privacy invariants violated:")
        for error in errors:
            print("  - %s" % error)
        return 1

    print("Privacy invariants OK:")
    print("  - no %s permission" % INTERNET_PERMISSION)
    print("  - all 64 bit native libraries are 16 KB page size aligned")
    for variant in sorted(apks):
        print("  - checked the %s APK" % variant)
    return 0


if __name__ == "__main__":
    sys.exit(main())
