# Version 1.2.0 validation

Verified on **15 October 2026 (Asia/Kuala_Lumpur)**. Application ID `my.fuelledger`, version **1.2.0 / code 4**, Android API 26 minimum / 37 target. The original baseline report is preserved below as historical evidence.

## Final results

- **144 host tests passed**, zero failed/errors/skipped. Includes real Compose workflows on Robolectric APIs 26 and 35; these are host Android simulations.
- **9 native Android tests passed** on an actual booted AOSP Android 8.0 / API 26 x86_64 software emulator, with airplane mode enabled before the first app installation, Wi-Fi/mobile data off, and no active default network.
- The production `MainActivity` cold-launched to the real Dashboard on the same offline API26 emulator; app-local logs showed no fatal exception. Software-emulator startup timing is not a physical-phone performance benchmark.
- **4 build-policy tests passed** for the pinned OCR telemetry removal and fail-on-SDK-shape-change policy.
- Debug APK, Android test APK, and optimized unsigned release APK built successfully. Debug lint: **0 errors, 1 advisory warning** that SQLite 2.7.0 is available; the tested version is pinned at 2.6.2. Release vital lint passed.
- Final merged manifests and packaged debug/release permissions contain **no Internet, network-state or public-storage permission**.
- The signed debug APK verifies with APK Signature Scheme v2 and the **same signing certificate as 1.0.0**. ZIP alignment passes, including 16 KiB native alignment. Inspected arm64 SQLCipher/OCR ELF segments are aligned to `0x4000`.

| Host suite | Executions |
| --- | ---: |
| Calculations | 17 |
| Repository, migration and transactional backup validation | 23 |
| Income, budgets and recurrence | 12 |
| Encrypted settings/migration (APIs 26/35) | 8 |
| Receipt parsing/category rules | 23 |
| Encrypted receipt storage/native graphics | 8 |
| Editable receipt review | 2 |
| Encrypted complete archives, tampering and bounds | 23 |
| Local vault/purpose binding | 3 |
| Lock session | 7 |
| Encrypted lock storage (APIs 26/35) | 10 |
| Lock/window state preservation (APIs 26/35) | 4 |
| Real app Compose entry/edit/navigation (APIs 26/35) | 4 |
| **Total** | **144** |

Native cases cover:

1. Bundled OCR recognizes merchant, MYR total, fuel type and litres while offline; the model was available on the first installation with airplane mode already enabled.
2. Installed permissions/services and disabled Android automatic backup.
3. Local expense/refuel/income/budget/recurring transactions, no duplicate recurring occurrence, per-fill estimate, Keystore-encrypted photo backup/restore and persistence after closing/reopening the native encrypted database.
4. Real plaintext schemas 1 and 2 become encrypted schema 3 without losing records, categories, linked expenses, pending preferences or new finance writes.
5. Fresh encrypted storage persists and contains no plaintext test marker.
6. Wrong wrapping key, missing wrapped key and wrong database password preserve original files and permit recovery with the original key.
7. Corrupted plaintext source is preserved on failure.
8. Missing/empty original plus an interrupted candidate fails safely without replacing the recovery file.
9. Actual Android Keystore key is non-exportable and AES-GCM purpose authentication is enforced across vault instances.

## Commands and evidence

```sh
cd /workspace/ChatgptFuelConsumption
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
# Final host run: BUILD SUCCESSFUL, 144/144 tests.

# After the native migration correction, rebuild the affected APKs and validate release/lint sequentially:
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
./gradlew -p buildSrc test
```

Sequential release/lint validation avoids a generated-KSP-file race observed when both variants are analyzed while release outputs are being regenerated. No assertions/checks were disabled. Kotlin Gradle Plugin 2.3.20 and Android Gradle Plugin 9.4.1 are explicitly pinned in `buildSrc` so Compose mapping uses the correct compiler version.

The current cloud machine uses `/workspace/.tooling/fuel-adb.sh` to map only ADB's `.android` configuration through a verified, user-local Debian `proot`; `$HOME` was not changed. On a normal workstation use the SDK's `adb` directly.

```sh
# emulator-5556 was a fresh API26 software device; airplane mode was enabled before install.
/workspace/.tooling/fuel-adb.sh -s emulator-5556 install app/build/outputs/apk/debug/app-debug.apk
/workspace/.tooling/fuel-adb.sh -s emulator-5556 install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
/workspace/.tooling/fuel-adb.sh -s emulator-5556 shell am instrument -w -r \
  my.fuelledger.test/androidx.test.runner.AndroidJUnitRunner
# Final runner result: OK (9 tests). Inspect runner output, not just adb's exit code.
```

The native run caught and led to a fix for `ATTACH` inheriting SQLite's no-create flag. The encrypted candidate is now created as an empty private file before attaching it, while the original is always opened without CREATE. A numeric OCR assertion was corrected to accept equivalent normalized litres (`40` and `40.00`). The complete nine-case suite was rerun after these corrections.

Other resolved issues included ARGB-to-Compose color conversion, a previously visible locked dialog (dynamic composition-local tracking), conservative grocery/footer receipt parsing, obsolete public-gallery code, and a transient snackbar intercepting a smoke-test tap. Existing calculations and stored entities were retained and migrated additively.

Current-instance logs are under `/tmp/fuel-*.log`; XML/HTML reports are in `app/build/test-results/testDebugUnitTest/`, `app/build/reports/tests/`, `app/build/reports/lint-results-debug.*`, and `buildSrc/build/test-results/test/`. These generated/local reports are excluded from Git. Sample UI captures are in `app/build/reports/ui-screenshots/`; main Compose previews cover dark/light, narrow large-type, and empty states.

## Offline/dependency inspection

Both packaged variants request only:

```text
android.permission.USE_BIOMETRIC
android.permission.USE_FINGERPRINT
android.permission.WAKE_LOCK
android.permission.RECEIVE_BOOT_COMPLETED
android.permission.FOREGROUND_SERVICE
my.fuelledger.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION  (internal signature permission)
```

Biometric permissions support optional local authentication; wake/boot/foreground declarations come from local WorkManager support. There are no camera, location, gallery/storage, Internet or network-state permissions.

The debug APK includes **21 bundled OCR model assets**. DEX class-definition inspection found no DataTransport runtime/CCT backend, Firebase Analytics or Crashlytics classes. All three Firelog dispatch sinks and the Play Services telemetry sink contain only no-op returns (plus required monitor enter/exit for the synchronized method). `verifyOfflineOcrPolicy` validates all four pinned upstream class signatures before build. `SECURITY.md` lists direct dependencies and explains the remaining ML Kit component/encoder/API utilities.

Automatic backup is disabled and both legacy backup rules and Android 12+ cloud/device-transfer exclusions cover all relevant storage domains. Runtime app code contains no app-written network request or financial/password logging.

## Installable artifact

[Download Fuel & Expense Tracker v1.2.0](https://github.com/Piccol1001/ChatgptFuelConsumption/raw/refs/heads/main/downloads/Fuel-Expense-Tracker-v1.2.0-debug.apk)

```text
File: downloads/Fuel-Expense-Tracker-v1.2.0-debug.apk
Bytes: 75609451
SHA-256: 47da1aa93b52acd78e50d569352e1f3c86af133289131317a0d6ac4addf5fec0
Signing certificate SHA-256: 4cb7dbc7bfd1eafb5b37339ddcc875174bf6f35139c0e6d87ba4b0f120c37ae8
```

The original 1.0.0 APK remains untouched at its original download path. The new versioned file avoids overwriting that artifact. `downloads/SHA256SUMS` includes both. The optimized release at `app/build/outputs/apk/release/app-release-unsigned.apk` is not installable until signed with a private distribution key, which is deliberately not committed.

## Verification limits

Actual fingerprint/PIN interaction and hardware-backed key storage on a physical phone, OEM device transfer, and a physical-phone reboot were not tested. Native tests close/reopen encrypted databases; host UI tests exercise application flows. API35 hardware-emulator boot did not complete under software emulation; API35 UI coverage is Robolectric. Emulator OS watchdog timing was increased for slow software boot; application authentication/crypto checks and OCR timeout were not weakened.

Encryption cannot guarantee erasure of historical plaintext flash blocks or protection against a compromised/unlocked OS. Losing the device Keystore or backup password can prevent recovery. CSV and legacy backup files are plaintext; exporting to another app/cloud provider is outside private app storage. The provided APK is a debuggable personal-testing build. See `SECURITY.md` for these limits and the signed-release workflow in `README.md`.

---

# Historical version 1.0.0 report

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
