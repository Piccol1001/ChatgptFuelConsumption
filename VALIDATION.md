# Comprehensive upgrade validation

Verified **17 October 2026 (Malaysia time)** against the existing app extended from `ece52c2`. Final feature source checkpoint: `312cc7a`. Package `my.fuelledger`, version 1.4.0/code 6 and original signing identity are retained. Room is now schema 5 through additive migration 4→5; all older migrations remain registered and destructive fallback is absent. Previous validation reports are preserved below and apply to their recorded builds.

## Automated host coverage

**283 executions, 0 failures, 0 errors, 0 skipped** across 30 suites. [Per-suite results](docs/comprehensive-upgrade/TEST_RESULTS.md).

The full suite includes exact monetary calculations, expected versus actual income, fixed commitments, allocations, recurring transactions, partial/full fuel cycles, weighted history, refunds, duplicates, backups, encryption helpers and prior features. New coverage exercises favourite/merchant persistence, search/date/calendar boundaries, restart-safe expense and linked-refuel Undo, intervening changes, image deletion, refund limits, Journey reversal/scenarios/actual attribution, fuel-price decomposition, reports and empty-data fallbacks. A new authenticated format-5 receipt-archive round trip guards the native-discovered ZIP-reader compatibility fix.

API26/35 host UI tests exercise Calendar/date-seeded entry, favourites/refunds/delete/Undo, row-gesture ownership and disabled main swipes, queued widget actions, existing detail navigation, drafts/scroll, settings/theme persistence, amount masking and accessibility semantics. Widgets actually inflate/update RemoteViews on both API levels, including budget fallbacks and default privacy. AppSmokeTest, MainPagerTest, app-lock, receipt, keypad and allocation regressions also pass. No failed tests were disabled to obtain this result.

## Native Android checks and provenance

API26 emulator `emulator-5556`, airplane mode enabled before fresh test-user installation. The owner's app data was never cleared or uninstalled.

- **9 native tests pass** (`OK (9 tests)`, 401.787s): first-use bundled offline OCR; manifest permission restrictions; financial records, recurring/fixed payments, allocation/Journey/receipt workflows and authenticated photo-backup round trip; real SQLCipher/Keystore persistence and failure-closed handling; plaintext schemas 1–4 migrating into encrypted schema 5 without record loss; damaged or incomplete migration source preservation. This run followed the format-5 ZIP-reader fix, before the final UI-only preference/frequency-ranking/widget-button polish. It is not represented as a rerun of the complete nine-test suite on the final binary.
- **3 follow-up native tests pass** (`OK (3 tests)`, 6.595s): permissions, encrypted fresh-database persistence and Keystore non-exportability/purpose binding.
- **4 exact delivered-APK native tests pass** (`OK (4 tests)`, 28.034s), using a new isolated user: first-use bundled OCR in airplane mode, no-network/public-storage permissions, fresh encrypted-database persistence and non-exportable purpose-bound Keystore keys. The emulator-installed APK SHA256 was checked as `4b500a32be9046723c24bd66345fdc90a1276804759e3fe78b2e6fbea989b660`, identical to the download. Temporary test users were removed afterward; original active user 0 and airplane mode 1 were preserved.

After the exact-APK tests, the final download was installed in place, launched for the original user with `am start -W` (`Status: ok`, `my.fuelledger/.MainActivity`), and confirmed as the resumed activity. The original data was preserved.

Native UIAutomator hierarchy retrieval timed out after 30s; the activity was resumed/focused during the earlier launch check. Native widget-launcher/shortcut gestures are not claimed from that failed hierarchy request. Their application routing is covered by host tests. Existing Android15 software-emulator system-wide ANRs leave native edge-back/cold-start checks pending on a reliable physical or accelerated device. No physical camera/gallery, external document/share provider, OEM launcher placement/resizing, TalkBack or sustained performance pass is claimed.

## Visual checks

Twenty real-app native-graphics host captures use matching configurations for Home, Fuel, Expenses, Journey and Analysis: 393×851dp Light/Dark, 320×640dp Dark, and 393×851dp Light at font scale 1.6, mdpi/API35. See [screens and resolved geometry](docs/comprehensive-upgrade/screens/). Two separate [dark](docs/comprehensive-upgrade/overview-dark.png)/[light](docs/comprehensive-upgrade/overview-light.png) Home reference captures use sample data only. Shared main content margins are 16dp; controls, navigation alignment and touch targets are checked in JourneyLayoutTest. The new Home month/Overview header and Journey reverse-action gap are deliberate layout elements. Screens were visually inspected; there is no whole-screen scaling or density override. Long navigation labels can still wrap at accessibility font sizes.

## Build, lint and packaged privacy

- `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`: successful, 1m28s final incremental run.
- `:app:assembleRelease`: successful optimized unsigned release, 2m16s; release vital lint passes. Distribution APK is the signed debug build.
- `:app:lintDebug`: successful, **0 errors, 10 preserved warnings, 1 hint**. Advisories: SQLite update, six preserved unused app-name/version resources, offset lambda, two KTX suggestions and primitive-state hint. Gradle deprecation advisories remain. A direct Compose StateFlow read identified during validation was replaced with lifecycle-aware state collection; final lint has no error.
- Debug and release ZIPs both pass 16KiB alignment. The final debug signature verifies; certificate SHA256 remains `4cb7dbc7bfd1eafb5b37339ddcc875174bf6f35139c0e6d87ba4b0f120c37ae8`.
- Final APK/merged manifest has no INTERNET, network-state, camera, media/gallery or public-storage permission. `allowBackup=false`; existing backup rules and app-lock/screenshot protections remain. Notification/biometric/background-work permissions are unchanged.
- Binary audit finds **21 bundled OCR model assets**, no DataTransport runtime/CCT or Firebase Analytics/Crashlytics class definitions, and four reviewed no-op telemetry sinks. No new runtime dependency or library version change was introduced.
- All ten APK checksums in `downloads/SHA256SUMS` pass, including every retained prior artifact.

## Artifact and reproduction

[`downloads/Fuel-Expense-Tracker-v1.4.0-comprehensive-upgrade-debug.apk`](downloads/Fuel-Expense-Tracker-v1.4.0-comprehensive-upgrade-debug.apk) — **76,809,823 bytes**, Android8.0+, original debug signing identity. SHA256: `4b500a32be9046723c24bd66345fdc90a1276804759e3fe78b2e6fbea989b660`. The copied download and final Gradle debug output are byte-identical.

```sh
cd /workspace/ChatgptFuelConsumption
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
(cd downloads && sha256sum -c SHA256SUMS)
```

Native tests: install the app and Android-test APKs with `adb install -r`; use a fresh isolated Android test user, enable airplane mode before first launch, then run `adb shell am instrument --user USER_ID -w -r my.fuelledger.test/androidx.test.runner.AndroidJUnitRunner`. Remove only the temporary test user afterward. Preserve the owner's data and device settings. Release and lint run sequentially after source changes end.

Session logs: `/tmp/upgrade-final-test.log`, `/tmp/upgrade-release.log`, `/tmp/upgrade-lint.log`, `/tmp/upgrade-native-final.log`, `/tmp/upgrade-native-delivery.log`, `/tmp/upgrade-native-exact-apk.log`, `/tmp/upgrade-apk-audit.log`, `/tmp/upgrade-signature.log` and the two `/tmp/upgrade-*-align.log` files. These machine-local logs are not permanent repository artifacts. The reports, per-suite summary, captures, schema and tests are committed. [Full implementation and limitations](docs/comprehensive-upgrade/IMPLEMENTATION.md); [current progress and resume instructions](PROGRESS.md).

<details>
<summary>Preserved validation before comprehensive upgrade</summary>

# Main-screen pager validation

Verified 16 October 2026 (Malaysia time), baseline `a76e227`. Original package/version1.4.0/code6, database schema4, signing identity and offline permissions are preserved.

- **240 host executions pass**, 0 failures/errors/skipped: all prior230 plus ten new MainPagerTest executions on APIs26/35. The [detailed test matrix](SWIPE_NAVIGATION.md) covers touch gestures, state, modal scope and theme behavior.
- In-progress drag tests verify that both adjacent pages are visible. Repeated nested category drags at both edges, Journey toggle and chart drags do not navigate. Real planning-slider input changes the allocation value outside pager scope. Small drags and outer boundaries do not switch pages.
- Draft text, selection and scroll survive distant pages, Settings and activity recreation; ViewModel identity remains stable. No saved financial transaction is created by paging or unsaved drafts.
- All16 existing API35 native-rendered main-content captures match the prior layout build pixel-for-pixel, comparing RGB regions above the bottom bar. Navigation styling/size remains; the requested fifth label is Analysis.
- Debug and Android test APKs build. Signed debug and optimized unsigned release builds pass; release vital lint and debug/release 16KiB ZIP alignment pass. Final debug lint has 0 errors, the same 10 advisory warnings and 1 hint as the baseline (six preserved unused name/version entries, one SQLite update suggestion, one offset suggestion, two optional KTX suggestions and one primitive-state hint). Native API26 in-place installation succeeds and UIAutomator confirms the ready Dashboard after waking/unlocking the headless emulator. Native API35 edge-back testing was attempted in gestural/airplane mode but did not complete: the software emulator showed app startup ANRs plus system-wide failures, including a system_server main-handler block and com.android.phone startup failures. No native edge-back pass is claimed. Verify system edge Back and cold startup on a physical phone or hardware-accelerated Android15 emulator. Test navigation/airplane settings were restored.
- Debug APK signature and 16KiB alignment verify. Certificate SHA256 remains `4cb7dbc7bfd1eafb5b37339ddcc875174bf6f35139c0e6d87ba4b0f120c37ae8`. Binary audit retains21 bundled OCR assets, no transport/CCT/Analytics/Crashlytics implementations, and four no-op telemetry sinks.
- Artifact: `downloads/Fuel-Expense-Tracker-v1.4.0-swipe-navigation-debug.apk`, 76,098,095 bytes, SHA256 `873fa8048054e902dde025e1801df502f8eaaaa1ae84ec46e92f45696e63a2c0`. All previous artifacts are retained.

No physical/OEM/TalkBack or full process-death test is claimed. Activity recreation is covered. Historical native financial/OCR/migration suites below belong to their recorded builds; this UI-only task does not claim to rerun them. Logs and exact reproduction commands are in SWIPE_NAVIGATION.md and PROGRESS.md.

<details>
<summary>Preserved validation before main-screen pager</summary>

# Journey layout validation

Verified 16 October 2026 (Malaysia time), from baseline `313d66e`. Version 1.4.0 / code 6, Room schema 4 and original signing identity are preserved.

- **230 host tests pass**, zero failures/errors/skipped. This includes four new real-app layout cases, all prior 226 executions, journey save/load, live historical consumption, explicit override, known RM21.53 estimation, receipt/finance/data/security and navigation regressions.
- Before/after geometry reproduces and fixes 12dp extra top padding and a 28dp field gap caused by empty suggestion containers. At 320dp, the shortened heading and distance label avoid extra lines; input geometry matches Expenses' shared search control. Details and all screenshots: [Journey layout investigation](docs/journey-layout/README.md).
- Captures use the actual MainActivity, identical per-row device settings and Robolectric native graphics: API35/mdpi, 393×851dp Light/Dark, 320×640dp Dark, and 393×851dp Light/160% font. All nine paired Home/Fuel/Expenses images are byte-identical to the baseline. The four-screen comparisons were visually inspected.
- The first large-font test compared different navigation items and failed because Expenses wraps while Home does not. The corrected assertion compares the same Home item across all screens. This does not alter the app or hide Journey-specific alignment differences; all screen-heading, field-gap, width, input-height and touch-target assertions remain.
- Signed debug and optimized unsigned release APKs build successfully. Release vital lint and debug/release 16KiB ZIP alignment checks pass. Debug lint has 0 errors, the same 10 advisory warnings and 1 hint as the baseline: six preserved unused app-name/version entries, one SQLite update suggestion, one state-backed offset suggestion, two KTX suggestions and one primitive-state hint. Native API26 in-place installation succeeds without clearing data; launch reports `Status: ok`, and MainActivity is confirmed as the resumed foreground activity. This native check covers startup, not a rerun of the historical financial/OCR instrumentation suite.
- Debug signature and 16KiB ZIP alignment pass. Original certificate SHA256: `4cb7dbc7bfd1eafb5b37339ddcc875174bf6f35139c0e6d87ba4b0f120c37ae8`.
- Final binary has unchanged permissions, no INTERNET permission, 21 bundled OCR model assets, no DataTransport runtime/CCT or Firebase Analytics/Crashlytics definitions, and the four reviewed no-op telemetry sinks.
- APK: `downloads/Fuel-Expense-Tracker-v1.4.0-journey-layout-debug.apk`, 76,098,095 bytes, SHA256 `efcaff409f5ae2694d161764af0637053e72e61678ebc684e30c94f7edf2551d`. Older artifacts are preserved.

The shared bottom navigation still wraps long labels at 160% font size on all screens. No physical-device/OEM/TalkBack verification is claimed. Historical native financial/OCR/migration instrumentation results below apply to their recorded builds, not this UI-only update.

Logs: `/tmp/fuel-journey-before.log`, `/tmp/fuel-journey-targeted.log`, `/tmp/fuel-journey-validation.log`, `/tmp/fuel-journey-release.log`, `/tmp/fuel-journey-lint.log`, `/tmp/fuel-journey-native.log`. Resumption commands are in PROGRESS.md; test captures and geometry are retained in `docs/journey-layout/`.

<details>
<summary>Preserved validation before Journey layout correction</summary>

# Press-feedback validation

Verified 16 October 2026 (Malaysia time). Package `my.fuelledger`, unchanged version1.4.0/code6 and schema4. No business/data/configuration changes.

- **226 host tests passed**, zero failures/errors/skipped. New PressFeedbackTest has 8 executions across APIs26/35; all prior218 executions also pass.
- New native-rendered pixel checks hold Material buttons and raw clickable foregrounds: visible width ratio is approximately0.97, accent colour stays unchanged, rounded-corner pixels retain the surrounding surface, and accessible hit bounds stay constant. Pointer release/cancel and disabled/selected semantics are verified. Android animator scale0 preserves stationary visuals and clicks.
- The test runner's manually paused Compose clock did not reliably redraw a dynamic theme switch before capture; final light/dark pixel cases use independent initial themes and explicit Android-frame synchronization. Assertions were preserved, not relaxed. Existing app theme-persistence tests remain in the full suite.
- Signed debug and optimized unsigned release APKs build successfully. Release vital lint and debug/release 16KiB ZIP alignment checks pass. Debug lint reports 0 errors, 10 existing advisory warnings and 1 hint: six unused app-name/version resource entries from the preserved header cleanup, one SQLite update suggestion, one state-backed offset suggestion, two optional KTX suggestions and one primitive-state hint. Gradle reports existing deprecation advisories. No new lint issue is introduced by this change.
- Native API26 in-place `install -r` succeeds without clearing data. Launch reports `Status: ok` and `my.fuelledger/.MainActivity`; ActivityManager confirms it is the resumed foreground activity. This is a startup smoke check, not a rerun of the historical financial/OCR instrumentation suite.
- Debug signing certificate SHA256 remains `4cb7dbc7bfd1eafb5b37339ddcc875174bf6f35139c0e6d87ba4b0f120c37ae8`; debug 16KiB ZIP alignment verifies. Packaged permissions are unchanged, with no Internet/network-state/camera/public-storage permission.
- Final APK retains21 bundled OCR model assets, no DataTransport runtime/CCT or Firebase Analytics/Crashlytics definitions and four reviewed no-op telemetry sinks.
- Versioned download: `Fuel-Expense-Tracker-v1.4.0-press-feedback-debug.apk`, 76,098,095 bytes, SHA256 `4d1afc37dfdd04e0c48daebd5cd83af9ba14adac3661596e9964fd77020122d2`. All previous APKs remain unchanged and `downloads/SHA256SUMS` verifies the artifacts.

## Commands / evidence

```sh
cd /workspace/ChatgptFuelConsumption
git status --short --branch
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
```

Use the existing checkout and pinned toolchain; preserve package, version, signing identity and Room schema 4. Finish source edits before running lint, and run release assembly and debug lint sequentially. Targeted interaction tests: `./gradlew :app:testDebugUnitTest --tests my.fuelledger.ui.PressFeedbackTest` (8 executions on APIs26/35). Do not clear/uninstall app data. Reuse `LedgerTheme`/`PremiumInteractions` for new theme roots and the shared Material adapters for new app controls. Foundation indications can only scale content drawn inside their modifier; put custom foreground backgrounds inside that scope or use outer `Modifier.pressFeedback` with the shared interaction source. Keep prior APKs and append basename-only checksums. Keys, databases, passwords and build caches stay outside Git.

Logs: `/tmp/fuel-press-validation.log`, `/tmp/fuel-press-release.log`, `/tmp/fuel-press-lint.log`, `/tmp/fuel-press-native.log`. XML and prior sample screenshots are ignored build outputs under `app/build/test-results` and `app/build/reports`. This task reran the full host suite and uses a native startup smoke check; prior native financial/OCR/migration instrumentation results belong to their recorded builds and are not claimed as rerun for this UI-only change. No physical device was available. Standard Material date/time internals retain native selection/input feedback with global ripple disabled.

<details>
<summary>Preserved validation before press-feedback refinement</summary>

# Expense-entry refresh validation

Verified 16 October 2026 (Malaysia time). Package `my.fuelledger`, unchanged version **1.4.0 / code 6**, schema **4**. Separate Expenses filter-only commit: `69f0aa3` (one heading moved, 10 existing Compose smoke executions passed, debug build passed before restoring the separate expense redesign work).

## Final results

- **218 host tests passed**, zero failures/errors/skipped. Full existing finance/fuel/receipt/security regression suites run alongside new entry/payment checks.
- Debug and Android test APKs and the optimized unsigned release build successfully. Debug lint has 0 errors, 10 advisory warnings and 1 hint: six unused app-name/version resource entries left by the prior header cleanup, an SQLite update suggestion, a state-backed offset suggestion, two optional KTX suggestions and a primitive-state hint. The original name/version resources are intentionally preserved. Release vital lint and debug/release 16KiB ZIP alignment checks pass. All 3 native API26 integration tests pass in airplane mode (222.973 seconds), including bundled OCR, permissions, encrypted Card records, remembered payment preferences, complete receipt/finance backup/restore and database reopen. Instrumentation result: OK (3 tests).
- Final debug APK signature verifies against original certificate SHA256 `4cb7dbc7bfd1eafb5b37339ddcc875174bf6f35139c0e6d87ba4b0f120c37ae8`. 16KiB debug ZIP alignment passes. Package/version/launcher metadata are preserved.
- APK has no INTERNET, network-state, camera or public-storage permission. Final binary retains 21 bundled OCR model assets and reviewed no-op telemetry sinks, with no DataTransport runtime/CCT or Firebase Analytics/Crashlytics definitions. No new dependency or manifest change.
- Published artifact: `Fuel-Expense-Tracker-v1.4.0-expense-entry-debug.apk`, 76,098,095 bytes, SHA256 `ce011e110952c4674834ba520b2be19e6cea70359ad61d75b3acbd4928c4cb47`. Previous APKs remain unchanged; `downloads/SHA256SUMS` verifies all artifacts.

## Coverage

- Digits 1/2/3/4/5 yield exactly 1/12/123/1234/12345 sen; ringgit formatting and parsing preserve sen. Leading zeros, zero/backspace, existing 12345-sen editing, inclusive maximum, rejected excessive digits and invalid numeric ranges.
- Initial zero/Clear disables Save, keypad interaction, category/merchant custom IDs, one-tap Card/E-Wallet selection and last-selection reuse in new expense drafts.
- Repository backup/restore retains Cash, Credit Card, Debit Card, Not specified and new Card values; local preference updates preserve theme/consumption. Encrypted DataStore restarts preserve all three default-payment selections and other settings. Old JSON defaults to Cash without changing stored amounts/theme.
- Real Compose edits retain original Credit Card/Debit Card values, exact amounts/notes/timestamps; Not specified remains unchanged in storage until an explicit method is chosen. No bulk record rewrite or Room migration.
- Receipt import into an unsaved draft, expanding/collapsing details, successful save and encrypted retained image readability. Attachment effects are not disposed by hiding their controls. OCR amount application still uses exact Money.parse conversion and existing suggestion handling.
- Small 320×568dp, wide 600×960dp and 393×851dp with composed 1.6 font scale, each in light/dark schemes. System theme uses the existing shared theme implementation. Screenshots are local generated test evidence, not physical-device claims.

Test diagnosis: the receipt test needed to drain Robolectric's paused main queue before asserting import completion. The repeated legacy-edit test needed to allow the transient saved snackbar to clear before tapping another underlying history row. Assertions and financial behavior are preserved; final full suite passes.

## Host suites

| Suite | Executions |
| --- | ---: |
| ExpenseEntryTest | 2 |
| FinanceRepositoryTest | 12 |
| PlannerRepositoryTest | 13 |
| PlanningCalculationsTest | 18 |
| ReceiptBackupArchiveTest | 23 |
| RefinementTest | 12 |
| RepositoryTest | 25 |
| SettingsStoreTest | 14 |
| CalculationsTest | 17 |
| CentsKeypadTest | 3 |
| ReceiptParserTest | 23 |
| ReceiptReviewTest | 2 |
| ReceiptStoreTest | 10 |
| AppLockSessionTest | 7 |
| AppLockStoreTest | 10 |
| LocalVaultTest | 3 |
| AppLockUiTest | 4 |
| AppSmokeTest | 16 |
| ExpenseEntryControlsTest | 3 |
| RefinedControlsTest | 1 |

## Commands / evidence

```sh
cd /workspace/ChatgptFuelConsumption
git status --short --branch
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
```

Finish source edits before lint runs. Run release assembly and debug lint sequentially. Keep package `my.fuelledger`, schema 4, current name/version and signing identity. Do not clear user data or uninstall for testing. To exercise native offline integration on this instance:

```sh
ADB=/workspace/.tooling/fuel-adb.sh
$ADB -s emulator-5556 shell settings put global airplane_mode_on 1
$ADB -s emulator-5556 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true
$ADB -s emulator-5556 shell svc wifi disable
$ADB -s emulator-5556 shell svc data disable
$ADB -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
$ADB -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ADB -s emulator-5556 shell am instrument -w -r -e class my.fuelledger.OfflinePrivacyTest my.fuelledger.test/androidx.test.runner.AndroidJUnitRunner
```

Require `OK (3 tests)` in the instrumentation output; adb status alone is insufficient. Full native migration/security suite can be run by omitting `-e class ...`. Existing old WIP stash is a retained checkpoint from before this completed redesign; do not blindly reapply it. Keep passwords, signing keys, databases and caches outside Git. Preserve previous APKs and append new checksums using basenames.

Local logs: `/tmp/fuel-expenses-filter-layout.log`, `/tmp/fuel-expense-validation.log`, `/tmp/fuel-expense-release.log`, `/tmp/fuel-expense-lint.log`, `/tmp/fuel-expense-native.log`. XML and screenshots are ignored generated files under `app/build/test-results` and `app/build/reports/ui-screenshots` (including actual add-expense dialog and small/wide/font-scale light/dark controls).

No physical device was available; native coverage is an API26 software emulator with an in-place `install -r` update, not a fresh installation. Earlier full native migration/security results apply to their recorded builds below. Current changes do not alter Room schema or migrations. Physical haptics, Android picker/camera handoff, OEM and complete accessibility behavior remain manual checks.

<details>
<summary>Preserved validation before expense-entry redesign</summary>

# Header cleanup — 16 October 2026 (Malaysia time)

Completed a UI-only refinement of existing v1.4.0 / code 6, continuing from `6e4d932`. Removed the shared app-name/version top bar on Home, Fuel, Expenses, Journey and Analytics. Settings now appears beside each existing screen heading, eliminating the empty toolbar row. Secondary Settings, Monthly Money Planner and Receipt Scanner titles/Back navigation remain. Scaffold system-bar insets, status-bar appearance and bottom navigation are preserved.

The application ID, launcher name, version name/code, build configuration, manifest, database and calculations are unchanged. No dependencies, permissions or migrations were added. Existing data and APKs are preserved.

Verification: all 10 existing AppSmokeTest executions pass on Robolectric API26/35 (0 failures/errors/skipped); signed debug APK builds. Existing test interactions now scroll to Settings in the screen heading. Visually inspected the API35 dashboard screenshot: no branding header or blank row, with aligned content and Settings icon. Signature matches the prior APK and 16KiB ZIP alignment passes. APK metadata still reports `my.fuelledger`, `1.4.0`, code `6`, launcher label `Fuel & Expense Tracker v1.4.0`. No native/full-suite/release/lint rerun was needed for this UI-only change; prior results below belong to their stated builds. Physical-device status-bar/large-font/OEM checks remain unperformed.

Updated APK: `downloads/Fuel-Expense-Tracker-v1.4.0-minimal-header-debug.apk` (76,096,099 bytes), SHA256 `ea6f3d9b937ea98f000e1f569a15e2b9862bd5ad415c71ac982d4825af022ec1`. All older APKs retain their original checksums. Install this variant over the existing app; the package, version and signing key are unchanged.

Files changed:

- `PROGRESS.md`
- `README.md`
- `VALIDATION.md`
- `app/src/main/java/my/fuelledger/ui/Components.kt`
- `app/src/main/java/my/fuelledger/ui/JourneyScreen.kt`
- `app/src/main/java/my/fuelledger/ui/LedgerApp.kt`
- `app/src/main/java/my/fuelledger/ui/Screens.kt`
- `app/src/test/java/my/fuelledger/ui/AppSmokeTest.kt`
- `downloads/Fuel-Expense-Tracker-v1.4.0-minimal-header-debug.apk`
- `downloads/SHA256SUMS`

Resume using the existing checkout/toolchain; do not change the app version/name for this cleanup, clear app data or overwrite old APKs:

```sh
cd /workspace/ChatgptFuelConsumption
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest --tests my.fuelledger.ui.AppSmokeTest :app:assembleDebug
```

Local verification log: `/tmp/fuel-header-cleanup.log`. Screenshots: `app/build/reports/ui-screenshots`. No known compilation errors or implementation tasks remain for this request.

---

# Version 1.4.0 validation

Verified 15 October 2026 (Malaysia time). Package `my.fuelledger`, version **1.4.0 / code 6**, min API26, target/compile API37. Existing schema remains **4**. Previous reports are preserved below.

## Results

- **201 host tests pass**, 0 failures/errors/skipped. Compose tests exercise API26 and API35 via Robolectric; the pill control test uses API35.
- **9 native API26 tests passed** in airplane mode (356.196 seconds), including offline OCR/permissions, encrypted complete backups, receipt deduplication/Undo, schema upgrades and wrong/missing-key preservation. This full run preceded the final saved-journey source metadata fix; **all three OfflinePrivacyTest cases passed again on the final APK** (222.755 seconds) after in-place installation, covering bundled OCR, permissions and encrypted finance/receipt backup/restore.
- Signed debug APK, Android test APK and optimized unsigned release all build successfully. Final debug lint: **0 errors, 4 advisory warnings, 1 hint** (SQLite version, state-backed offset overload, two optional KTX suggestions and primitive-state boxing). Release vital lint passes. Debug and unsigned release 16KiB ZIP alignment checks pass.
- **4 build-policy tests pass** (cached successful suite; preBuild policy runs also pass). No dependency or telemetry policy changed.
- Final APK audit: 21 bundled OCR model assets, no DataTransport runtime/CCT or Firebase Analytics/Crashlytics definitions, all four reviewed telemetry sinks no-ops. No INTERNET, network-state, camera or public-storage permission. Automatic cloud backup remains disabled.
- Signature verifies with original certificate SHA256 `4cb7dbc7bfd1eafb5b37339ddcc875174bf6f35139c0e6d87ba4b0f120c37ae8`. Final debug ZIP alignment passes with 16KiB native alignment.
- Versioned signed debug download: **76,096,099 bytes**, SHA256 **`180d4f2f7cc92c9c099774cec03e171da62fa312d4ea4494c225ec1cbf4a90ce`**. Older artifacts are preserved and all entries in `downloads/SHA256SUMS` verify.

## New and expanded coverage

- Unequal-distance full-tank cycles verify weighting rather than averaging ratios; partial fills, missing/invalid cycles, changes/deletions, recovery after invalid intervals and incomplete trailing purchases.
- 3/6/12-month chart ranges, current-month inclusion, zero gaps, year boundaries, Malaysian local-zone boundary and exact English short labels (en-MY can produce “Sept”; chart deliberately uses “Sep”).
- Deterministic category ordering independent of query order, with custom categories retained and Others last.
- Active income aggregation, optional zero estimates, excluded inactive sources, named source types and rejected negative amounts. Received-income Compose entry stays separate from forecasts.
- Slider exchanges affect only the target and remainder, totals remain 10,000 basis points, removal transfers the percentage, custom additions keep the remainder selection, and old allocation payloads decode unchanged.
- Old journey JSON retains calculation version 1 and recorded values, new version 2 uses only fuel/tolls/parking, and hidden old fields survive serialization. Compose tests save fallback/history sources and reopen a history-based route, then verify live history updates without automatically enabling override.
- Tap and drag both directions on the pill, selected semantics, actual planner navigation, salary forecast versus received income, live slider/save persistence, retained theme/scanner settings, existing Room/backup/crypto/receipt regressions.

## Fuel chart diagnosis

The previous production code already generated six rolling months from `YearMonth.now()` and grouped real refuels by local calendar month. May/June/July were not hardcoded sample labels. A six-month view in October begins in May; without the original device screenshot/state, the exact reason those three appeared cannot be established. The new default is a readable rolling three months with explicit 3/6/12 selection and year-bearing labels.

## Data compatibility

No Room schema diff or migration is needed. `consumptionFallbackEnabled` defaults false, preserving the old stored number without silently using it. Allocation `remainder` defaults false for old payloads; the existing last category becomes the draft remainder with unchanged percentages. Journey `costVersion` defaults 1 and `consumptionSource` defaults Legacy when absent. Existing expected-income records retain their rule IDs/amounts/schedules; the already-existing income-only category string stores a namespaced source type when saved. These are local encrypted payload/preferences changes. Existing backups, databases, transaction associations, actual received income, fixed-payment deduplication and past captured months remain intact.

## Host suites

| Suite | Executions |
| --- | ---: |
| FinanceRepositoryTest | 12 |
| PlannerRepositoryTest | 13 |
| PlanningCalculationsTest | 18 |
| ReceiptBackupArchiveTest | 23 |
| RefinementTest | 12 |
| RepositoryTest | 24 |
| SettingsStoreTest | 12 |
| CalculationsTest | 17 |
| ReceiptParserTest | 23 |
| ReceiptReviewTest | 2 |
| ReceiptStoreTest | 10 |
| AppLockSessionTest | 7 |
| AppLockStoreTest | 10 |
| LocalVaultTest | 3 |
| AppLockUiTest | 4 |
| AppSmokeTest | 10 |
| RefinedControlsTest | 1 |

## Commands and evidence

```sh
cd /workspace/ChatgptFuelConsumption
git status --short --branch
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
./gradlew -p buildSrc test
```

Keep the existing checkout, package `my.fuelledger`, schema 4 and signing identity. Do not clear installed data or uninstall to run checks. Run release assembly and debug lint sequentially and finish source edits before launching lint/analysis. Use `/workspace/.tooling/fuel-adb.sh -s emulator-5556` for this environment's API26 software emulator. Enable airplane mode and disable Wi-Fi/data, then install both generated APKs with `install -r`. Run `shell am instrument -w -r my.fuelledger.test/androidx.test.runner.AndroidJUnitRunner` and inspect the final `OK (9 tests)` result; adb's exit status alone does not establish test success. Hardware interaction and physical-phone checks remain separate.

For a future version, increment both version name/code, create a new versioned APK filename, append its basename-only SHA256 entry to `downloads/SHA256SUMS`, and preserve all prior APKs. Check checksum, signing certificate, permissions and bundled model/telemetry policy before publishing. Keep passwords, databases, signing keys and caches out of Git.

Local logs: `/tmp/fuel-refinement-final-host.log`, `/tmp/fuel-refinement-final-release.log`, `/tmp/fuel-refinement-final-lint.log`, `/tmp/fuel-refinement-policy.log`, `/tmp/fuel-refinement-native.log`. Final native log: `/tmp/fuel-refinement-final-native.log`. Generated host XML/reports are under `app/build/test-results/testDebugUnitTest` and `app/build/reports`; these are ignored build products.

## Limitations

No physical device was available. Emulator coverage does not establish camera/picker/OEM/biometric behavior, all TalkBack/RTL/large-font layouts or first-install behavior on every device. This run preserves the existing emulator install with `install -r`; it does not erase user data or claim a fresh installation. Earlier OCR/privacy limitations remain documented in README.md and SECURITY.md.

<details>
<summary>Preserved validation reports through version 1.3.0</summary>

# Version 1.3.0 validation

Verified 15 October 2026. Package `my.fuelledger`; version **1.3.0 / code 5**, min API26, target/compile API37. Previous reports are preserved below.

## Final results

- **184 host tests passed**, no failures/errors/skipped. Real Compose navigation/entry runs on Robolectric APIs26 and35, including saving a journey without financial side effects and immediately persisting theme/scanner mode changes.
- **9 native Android tests passed** in airplane mode on the booted AOSP API26 x86_64 software emulator. The suite includes bundled offline OCR; packaged permission and backup flags; encrypted local finance, monthly plans, allocations, journeys, receipt auto-save/deduplication/Undo, complete photo/finance backup/restore and reopen; schema 1/2/3 → encrypted schema4 preservation; and damaged/wrong/missing-key fail-closed behavior. Instrumentation result: `OK (9 tests)`, 303.018 seconds.
- **4 build-policy tests passed**. Bundled Latin OCR dependency and reviewed telemetry shape are unchanged; preBuild verifies them.
- Signed debug APK, Android test APK and optimized unsigned release APK build successfully. Debug lint: **0 errors, 3 advisory warnings** (tested SQLite2.6.2 vs available2.7.0, optional Bitmap.get KTX syntax, optional String.toUri KTX syntax). Release vital lint passes.
- The final debug APK contains **21 bundled OCR model assets**, no DataTransport runtime/CCT or Firebase Analytics/Crashlytics definitions, and all four reviewed telemetry sinks are no-ops. Manifest/APK permission inspection confirms **no Internet, network-state, camera or public-storage permission**. The optional new permission is `POST_NOTIFICATIONS`.
- Debug APK signature verifies with the original certificate SHA256 `4cb7dbc7bfd1eafb5b37339ddcc875174bf6f35139c0e6d87ba4b0f120c37ae8`. Debug and unsigned release ZIP alignment checks pass with 16KiB native alignment.
- Published versioned debug APK: **76,639,470 bytes**, SHA256 **`c85624e8ee2e9ce299718ad409150284577edb9413bfdb2d1c57fbfe722ef059`**. Original 1.0.0 and1.2.0 APK files remain unchanged; checksums are in `downloads/SHA256SUMS`.

## Host suites

| Suite | Executions |
| --- | ---: |
| FinanceRepositoryTest | 12 |
| PlannerRepositoryTest | 13 |
| PlanningCalculationsTest | 18 |
| ReceiptBackupArchiveTest | 23 |
| RepositoryTest | 24 |
| SettingsStoreTest | 12 |
| CalculationsTest | 17 |
| ReceiptParserTest | 23 |
| ReceiptReviewTest | 2 |
| ReceiptStoreTest | 10 |
| AppLockSessionTest | 7 |
| AppLockStoreTest | 10 |
| LocalVaultTest | 3 |
| AppLockUiTest | 4 |
| AppSmokeTest | 6 |

## Coverage added in this upgrade

Monthly plan tests cover the requested RM3,000 − RM1,130 = RM1,870 example, expected/actual separation, fixed-payment reservation and actual-cost updates, negative results, daily/weekly/month-end schedules, start/end/pause, missed-month backfill, timezone boundaries, historical snapshot preservation and transaction linking/deduplication across manual/automatic recurrence.

Allocation tests cover exact100% validation, invalid/negative/oversized percentages, duplicate category mappings, independent defaults/overrides, savings reserves, zero/negative available amounts, sen-preserving largest-remainder rounding and spending not already reserved in the selected month. A fixed payment recorded in another month counts as unreserved spending there.

Journey tests cover the reused150km fuel example (RM21.53), exact fee sums, distinct return distances/tolls, selected cost sharing, invalid values, local CRUD/duplication and no expense/refuel side effects. Distance starts blank in the entry screen and must be entered or loaded from a saved route.

Receipt tests cover Malaysian TOTAL/GRAND TOTAL/AMOUNT PAID/NET TOTAL/JUMLAH/JUMLAH BAYARAN layouts; subtotal/tax/change rejection; requested merchant examples; conflicting totals/dates, currency and low-quality fallbacks; mode/recognized-field checks; concurrent duplicate prevention; refuel linking; normal deletion/tombstones; unchanged-only Undo; encrypted image crop/rotation/long portrait limits; and complete metadata backup/restore. Theme/scanner settings are tested across encrypted DataStore restarts and older preference migration.

One host integration test exposed a cached application factory holding a different test application; ViewModels now explicitly use the current application. An expanded native test initially retained repository references after deliberately closing/reopening its test database; its fixture now uses the reopened database. The final suites above pass. No app/user data was cleared to run these checks.

## Commands and local evidence

```sh
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
./gradlew -p buildSrc test
/workspace/.tooling/fuel-adb.sh -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
/workspace/.tooling/fuel-adb.sh -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
/workspace/.tooling/fuel-adb.sh -s emulator-5556 shell am instrument -w -r my.fuelledger.test/androidx.test.runner.AndroidJUnitRunner
(cd downloads && sha256sum -c SHA256SUMS)
```

Native tests ran with airplane mode on, Wi-Fi and mobile data off. The emulator was first installed offline during the preceding1.2.0 validation; this version was installed as an update, without clearing app records. The model remains bundled and was exercised offline again. A separate first-install1.3.0 run on a physical phone remains unverified.

Final logs: `/tmp/fuel-delivery-host.log`, `/tmp/fuel-delivery-release.log`, `/tmp/fuel-delivery-lint.log`, `/tmp/fuel-delivery-policy.log`, `/tmp/fuel-native-upgrade-final.log`. Generated reports/screenshots remain ignored under `app/build/`. Run build/release/lint sequentially and do not edit source while an analysis task is reading it.

## Practical limits

Physical camera/Photo Picker handoff, Android notification-denial flows, biometric/PIN/OEM reboot behavior and a broad collection of real Malaysian thermal-paper photographs need hands-on device testing. OCR quality gates are conservative heuristics and cannot guarantee error-free extraction. Optional tax/item-level parsing is not implemented. Current/next-month notification summaries do not include older unpaid months; those remain visible through the planner's month selection. Captured past plans preserve recorded settings; uncaptured old months are reconstructed from the current available rules. Release distribution still requires the owner's private signing key.

<details>
<summary>Preserved v1.2.0 and earlier validation reports</summary>

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

</details>

</details>

</details>

</details>

</details>

</details>

</details>
