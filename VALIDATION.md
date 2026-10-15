# Verified build results

Final validation was performed on 14 October 2026 in the cloud workspace at `/workspace/ChatgptFuelConsumption`. These results describe the current instance; a new cloud task and a physical Android phone were not tested.

## Toolchain

- Eclipse Temurin JDK 21.0.12.1+1, archive verified against the publisher's SHA-256.
- Gradle wrapper 9.6.0, distribution SHA-256 pinned in wrapper properties.
- Android Gradle Plugin 9.4.1, Kotlin / Compose compiler plugin 2.3.20, KSP 2.3.12.
- Android platform 37.0, build tools 36.0.0, minimum app API 26, target API 37.
- Compose BOM 2026.09.00, Room 2.8.5, DataStore 1.2.1, Activity 1.13.0, Lifecycle 2.11.0.
- Coroutines 1.11.0, Kotlin serialization 1.11.0, Robolectric 4.17, JUnit 4.13.2.

Build downloads used the existing network proxy with TLS verification and the managed Java CA trust store. No additional application credentials or network settings were required.

## Commands and outcomes

```sh
bash scripts/setup-cloud.sh
# Includes: ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# BUILD SUCCESSFUL (57 seconds on the final run)

source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:assembleRelease
# BUILD SUCCESSFUL (1 minute 8 seconds on the final run)

"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
# Verifies: APK Signature Scheme v2; one signer
```

All **30 tests passed**: 0 failures, 0 errors, 0 skipped.

| Suite | Executed | Evidence |
| --- | ---: | --- |
| CalculationsTest | 14 | Exact sen; specified trip example; invalid inputs; no data; baseline-only and partial fills; edited/deleted refuels; calendar, category, week/year and daylight-saving boundaries |
| RepositoryTest | 14 | Actual Room/SQLite transactions; idempotent writes; fuel links; cascading deletes; protected categories; expense edits; backup round-trip; malformed backups; interrupted settings restore recovery; CSV quoting |
| AppSmokeTest | 2 | Real Compose app launch, add/edit expense, trip example and screen navigation on emulated APIs 26 and 35 using Robolectric |

Android lint completed with **0 errors and 0 warnings**. Release vital lint also passed. Gradle emits a harmless cloud metrics-cache warning because the default home directory is read-only; it does not affect build, tests or the APK. No checks or assertions were disabled.

## Artifacts

Installable debug APK:

```text
/workspace/ChatgptFuelConsumption/app/build/outputs/apk/debug/app-debug.apk
```

Debug APK SHA-256:

```text
4b9768f3d4d2cf1182550f001f4d8e7ec04e3c9121005a2fa05253a82acacdb0
```

The APK manifest confirms application ID `my.fuelledger`, version 1.0.0 (code 1), minimum API 26 and target API 37. It has no Internet, advertising, analytics, account or storage permission. AndroidX declares an internal signature permission for non-exported broadcast receivers; it does not ask the user for device access.

Optimized unsigned release APK:

```text
/workspace/ChatgptFuelConsumption/app/build/outputs/apk/release/app-release-unsigned.apk
```

This release output is **not installable until signed** with a private key. See README.md for signing and update instructions. No private release key or password was created or stored in the project.

Reports:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/test-results/testDebugUnitTest/TEST-*.xml`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/lint-results-debug.xml`
- `app/schemas/my.fuelledger.data.LedgerDatabase/1.json`

The complete setup script was exercised, including wrapper activation, SDK setup, dependencies, tests, debug compilation and lint. `install_script` and `start_skill` were successfully saved to the cloud environment draft. Saving does not publish the environment; review and save the changes in environment settings, then publish to retain the prepared snapshot.

## Remaining verification

No physical phone or hardware emulator was attached (`/dev/kvm` is absent). Robolectric tests are automated Android simulations, not physical-device tests. On your phone, verify the document picker, CSV export, backup/restore, keyboard behavior, accessibility/font scaling and updates with the same signing key before depending on it for irreplaceable records. The app supports one vehicle and MYR; backups replace existing data after confirmation and are not encrypted. CSV import is not provided.
