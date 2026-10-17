# Repository work instructions

Extend the existing Kotlin/Compose app. Preserve Room records, registered migrations, encrypted storage, scanner and the offline-only architecture. Do not add INTERNET permission, telemetry or destructive migrations. Read the current PROGRESS.md and VALIDATION.md before resuming historical work.

For every new distributable APK, follow RELEASING.md and use `python scripts/release.py --bump patch|minor|major --notes <reviewed-notes.md> --variant debug` (or the documented same-key optimized release path). Choose the semantic bump from the actual scope; never bump merely for a failed build. Do not hand-edit old APK names or replace their historical metadata. Keep version.properties, CHANGELOG.md, RELEASES.md, releases/history.json and APK checksums consistent with verified binary metadata. Preserve the original signing certificate and package. A plain Gradle build is reproducible and is not a new release.

Finish source changes before lint. Run appropriate host tests, build the app and test APK, then run optimized release and lint sequentially. Keep prior progress/validation records when updating their current section. Record native-versus-host coverage and device-verification limits honestly. Never clear or uninstall the owner's application to prepare a test; use an isolated temporary Android test user and clean up only that fixture.
