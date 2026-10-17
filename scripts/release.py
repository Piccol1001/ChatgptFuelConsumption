#!/usr/bin/env python3
"""Build, verify and record a new APK without advancing versions on failed attempts."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
SEMVER = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)\Z")
APP_ID = "my.fuelledger"


def semver(value):
    match = SEMVER.fullmatch(value)
    if not match:
        raise ValueError("Use MAJOR.MINOR.PATCH without leading zeroes.")
    return tuple(map(int, match.groups()))


def bump_version(value, bump):
    major, minor, patch = semver(value)
    return {"major": f"{major + 1}.0.0", "minor": f"{major}.{minor + 1}.0", "patch": f"{major}.{minor}.{patch + 1}"}[bump]


def read_version(root):
    props = dict(line.split("=", 1) for line in (root / "version.properties").read_text().splitlines() if "=" in line and not line.lstrip().startswith("#"))
    semver(props["versionName"])
    return props["versionName"], int(props["versionCode"])


def next_version(current, history, discovered, bump):
    versions = [current[0]] + [r["versionName"] for r in history + discovered]
    base = max(versions, key=semver)
    code = max([current[1]] + [r["versionCode"] for r in history + discovered]) + 1
    if code > 2_100_000_000:
        raise ValueError("Android versionCode limit reached.")
    return bump_version(base, bump), code


def tools_dir():
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    folders = list((sdk / "build-tools").glob("*"))
    if not folders:
        raise ValueError("Set ANDROID_HOME to an installed Android SDK.")
    return max(folders, key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.name)))


def inspect_apk(path, sdk_tools, require_signature=True):
    info = subprocess.check_output([str(sdk_tools / "aapt"), "dump", "badging", str(path)], text=True)
    package = next(s for s in info.splitlines() if s.startswith("package:"))
    values = dict(re.findall(r"(\w+)='([^']*)'", package))
    result = {"applicationId": values["name"], "versionName": values["versionName"], "versionCode": int(values["versionCode"]), "debuggable": "application-debuggable" in info}
    if require_signature:
        certificate = subprocess.check_output([str(sdk_tools / "apksigner"), "verify", "--print-certs", str(path)], text=True)
        fingerprints = re.findall(r"certificate SHA-256 digest: (\w+)", certificate)
        if len(fingerprints) != 1:
            raise ValueError("Expected the single existing application signing certificate.")
        result["certificateSha256"] = fingerprints[0]
    return result


def verify_debug_signing(report, expected):
    section = re.search(r"Variant: debug\s*\n(.*?)(?=\nVariant:|\Z)", report, re.S)
    fingerprint = re.search(r"SHA-256: ([A-Fa-f0-9:]+)", section.group(1)) if section else None
    if fingerprint is None or fingerprint.group(1).replace(":", "").lower() != expected:
        raise ValueError("Original debug signing identity unavailable. Restore the existing external keystore; a new key cannot update installed copies. No build/version change was made.")


def render_releases(history):
    lines = ["# Known APK releases", "", "Actual version metadata is read from each APK, independently of filenames. Historical files are unchanged. A first Git addition is repository evidence, not proof of the original release date. Unknown release dates remain unverified.", "", "| Artifact | Actual versionName | Actual versionCode | Release date | Evidence |", "| --- | --- | ---: | --- | --- |"]
    for r in history:
        date = r.get("releaseDate") or "Unverified"
        evidence = r.get("dateEvidence", "")
        if r.get("firstRecorded"):
            evidence += f"; first Git addition {r['firstRecorded']}"
        lines.append(f"| [{Path(r['path']).name}]({r['path']}) | {r['versionName']} | {r['versionCode']} | {date} | {evidence} |")
    lines += ["", "All recorded APKs use `my.fuelledger` and the original certificate SHA256 `" + history[0]["certificateSha256"] + "`. Reused historical version 1.4.0/code 6 is recorded honestly. No APK was relabelled or rebuilt to invent a different history.", "", "The immutable per-artifact hashes and metadata are in [releases/history.json](releases/history.json). Future new artifacts must use [the release workflow](RELEASING.md); ordinary Gradle builds reproduce the current version without incrementing it. Compatibility requires the same package/certificate and a higher versionCode. The distributed builds are signed debug builds unless explicitly recorded otherwise.", ""]
    return "\n".join(lines)


def atomic_text(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode="w", dir=path.parent, delete=False) as f:
        temp = Path(f.name)
        f.write(text)
    try:
        os.replace(temp, path)
    finally:
        temp.unlink(missing_ok=True)


def perform_release(root, bump, notes, release_date, variant, build, inspect, discovered=(), signed_apk=None):
    history_path = root / "releases/history.json"
    history = json.loads(history_path.read_text())
    old_changelog = (root / "CHANGELOG.md").read_text()
    if not old_changelog.startswith("# Changelog\n"):
        raise ValueError("CHANGELOG.md must retain its title and historical entries.")
    version, code = next_version(read_version(root), history, list(discovered), bump)
    destination = root / "downloads" / f"FuelExpenseTracker_v{version}.apk"
    if destination.exists():
        raise ValueError("Artifact exists; historical APKs must never be overwritten.")
    # No tracked metadata is touched until all Gradle tasks and binary checks succeed.
    properties = [f"-PledgerVersionName={version}", f"-PledgerVersionCode={code}"]
    build(properties, variant)
    candidate = Path(signed_apk) if signed_apk else root / f"app/build/outputs/apk/{variant}/app-{variant}.apk"
    metadata = inspect(candidate)
    if (metadata["applicationId"], metadata["versionName"], metadata["versionCode"]) != (APP_ID, version, code):
        raise ValueError("Built APK package/version does not match the proposed release.")
    if metadata.get("debuggable", variant == "debug") != (variant == "debug"):
        raise ValueError("APK debuggable flag does not match the declared signing/build status.")
    original = {r["certificateSha256"] for r in history}
    if len(original) != 1 or metadata["certificateSha256"] not in original:
        raise ValueError("Signing certificate differs from existing APKs; refusing incompatible update.")
    payload = candidate.read_bytes()
    digest = hashlib.sha256(payload).hexdigest()
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation protects old artifacts even if another process created this name.
    with destination.open("xb") as output:
        output.write(payload)
    record = dict(path=str(destination.relative_to(root)), **metadata, sha256=digest, bytes=len(payload), releaseDate=release_date,
                  dateEvidence="Built and verified by scripts/release.py", signing="Signed debug build" if variant == "debug" else "Signed optimized release; original certificate", sourceCheckpoint=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip() if (root / ".git").exists() else "test-fixture")
    history.append(record)
    entry = f"\n## {version} — {release_date}\n\n{notes.strip()}\n"
    atomic_text(history_path, json.dumps(history, indent=2) + "\n")
    atomic_text(root / "RELEASES.md", render_releases(history))
    atomic_text(root / "CHANGELOG.md", "# Changelog\n" + entry + old_changelog[len("# Changelog\n"):])
    sums = "\n".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}" for p in sorted((root / "downloads").glob("*.apk"))) + "\n"
    atomic_text(root / "downloads/SHA256SUMS", sums)
    atomic_text(root / "version.properties", f"# Last successfully verified distributable APK. Never increment on a failed build.\nversionName={version}\nversionCode={code}\n")
    print(json.dumps(record, indent=2))
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bump", choices=["major", "minor", "patch"], required=True)
    parser.add_argument("--notes", type=Path, required=True, help="Reviewed Markdown with New features, Bug fixes and UI improvements headings")
    parser.add_argument("--date", default=dt.datetime.now(ZoneInfo("Asia/Kuala_Lumpur")).date().isoformat())
    parser.add_argument("--variant", choices=["debug", "release"], default="debug")
    parser.add_argument("--signed-apk", type=Path, help="For release: same-key externally signed APK matching the proposed version")
    args = parser.parse_args()
    dt.date.fromisoformat(args.date)
    notes = args.notes.read_text()
    for heading in ("### New features", "### Bug fixes", "### UI improvements"):
        if heading not in notes:
            parser.error(f"Release notes need {heading}")
    if args.variant == "release" and not args.signed_apk:
        parser.error("Optimized release APKs need --signed-apk signed with the original key; no new key is generated.")
    sdk_tools = tools_dir()
    expected = {r["certificateSha256"] for r in json.loads((ROOT / "releases/history.json").read_text())}
    if len(expected) != 1:
        raise ValueError("Historical signing identities require manual review.")
    signing = subprocess.check_output([str(ROOT / "gradlew"), ":app:signingReport"], cwd=ROOT, text=True)
    verify_debug_signing(signing, next(iter(expected)))
    known = []
    # Include all existing distributable APKs, even one not yet listed in the historical ledger.
    for path in (ROOT / "downloads").glob("*.apk"):
        value = inspect_apk(path, sdk_tools)
        if value["applicationId"] == APP_ID:
            known.append(value)
    def build(properties, variant):
        def gradle(*tasks):
            subprocess.run([str(ROOT / "gradlew"), *properties, *tasks], cwd=ROOT, check=True)
        gradle(":app:testDebugUnitTest", ":app:assembleDebug", ":app:assembleDebugAndroidTest")
        gradle(":app:assembleRelease")
        gradle(":app:lintDebug")
    def inspect(path):
        metadata = inspect_apk(path, sdk_tools)
        subprocess.run([str(sdk_tools / "zipalign"), "-c", "-P", "16", "4", str(path)], check=True)
        permissions = subprocess.check_output([str(sdk_tools / "aapt"), "dump", "permissions", str(path)], text=True)
        if "android.permission.INTERNET" in permissions or "android.permission.ACCESS_NETWORK_STATE" in permissions:
            raise ValueError("Network permission violates the offline release policy.")
        return metadata
    # The lock file lives outside tracked release metadata and prevents concurrent version assignment.
    import fcntl
    with (ROOT / ".git/release-workflow.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        perform_release(ROOT, args.bump, notes, args.date, args.variant, build, inspect, known, args.signed_apk)


if __name__ == "__main__":
    main()
