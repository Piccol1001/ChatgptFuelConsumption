# APK release workflow

The package is always `my.fuelledger`. `version.properties` records the last successfully verified distributable version; Gradle and the launcher label read it. Settings → About → App Version reads PackageManager's installed version rather than a handwritten label. Main-screen headers contain no application version.

Choose Semantic Versioning deliberately: **MAJOR** for significant architectural changes, **MINOR** for new functionality and **PATCH** for fixes/UI refinements. Version 1.5.0 includes the embedded calendar and interactive centred daily chart alongside corrective UI/fuel work. Historical binaries are recorded honestly in [RELEASES.md](RELEASES.md), including reused version codes.

## Create a new distributable build

1. Finish and review the source changes, then save a source checkpoint. Preserve the existing database, offline restrictions and certificate.
2. Write reviewed release notes with `### New features`, `### Bug fixes` and `### UI improvements`. Use “None” where appropriate.
3. Load the installed JDK/SDK environment, then invoke:

```sh
source /workspace/.tooling/fuel-ledger-env.sh
python -m unittest discover -s scripts/tests -v
python scripts/release.py --bump patch --notes path/to/reviewed-notes.md --variant debug
```

Use `--bump minor` or `--bump major` when the actual scope warrants it. The date defaults to the current Malaysia date; `--date YYYY-MM-DD` can specify a verified release date. This is a development-time command; it adds no runtime networking or app dependency.

The script first checks the existing Gradle debug signing identity without building or creating a key, then audits actual metadata from existing distribution APKs, chooses a code greater than all known/discovered releases, and builds using temporary Gradle version overrides. It runs the full host suite, builds debug/instrumentation and optimized unsigned release APKs, then runs lint sequentially. The distributable APK must pass embedded package/version checks, signature verification against the original certificate, 16KiB ZIP alignment and the no-network permission policy. Only after success does it create `downloads/FuelExpenseTracker_vMAJOR.MINOR.PATCH.apk` and update `version.properties`, `CHANGELOG.md`, `RELEASES.md`, `releases/history.json` and `downloads/SHA256SUMS`. Existing APKs are never overwritten.

A failed build or failed binary validation leaves the recorded version unchanged. Fix the issue and run the same command again. Ordinary Gradle builds reproduce the current version without incrementing anything. Do not manually publish another changed APK with a reused version/code; use the workflow for each new distribution. After a published release, subsequent source changes require a new semantic bump and a new artifact.

Review the generated diff, update PROGRESS.md/VALIDATION.md and the README download link, commit the APK and metadata, push the authorized GitHub checkpoint, and verify the public APK's SHA256. Keep old artifacts and dated reports. Machine caches, local databases and signing credentials stay outside Git.

## Signing and updates

All recorded historical builds share certificate SHA256:

`4cb7dbc7bfd1eafb5b37339ddcc875174bf6f35139c0e6d87ba4b0f120c37ae8`

The current distribution workflow uses `--variant debug`: it preserves that existing development signing identity and explicitly records “Signed debug build.” The optimized release build is also compiled, but remains unsigned and is not the downloadable distribution.

For an optimized signed distribution, keep the **same private signing key**, build/sign the proposed next version with that key outside Git, then pass `--variant release --signed-apk /private/path/same-key-signed.apk`. The script still builds/tests and verifies the submitted APK's package/version/certificate. It never creates a new signing key or asks for a password in source code. A missing original key blocks compatible distribution; do not substitute a new key or advise uninstalling to bypass a mismatch. New compatible APKs install with `adb install -r` or the system package installer without clearing financial data. Safe Room migrations remain registered.

No signing key/password is stored in the release ledger. If a public development key is unsuitable for a future production distribution, explain migration and backup implications before changing identity; never silently change it.
