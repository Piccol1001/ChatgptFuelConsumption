# Current progress — Journey layout root-cause fix

Updated 16 October 2026 (Malaysia time), continuing from `313d66e`. Implementation and visual verification are complete. Earlier work is preserved below.

## Completed

- Compared the actual Home, Fuel, Expenses, Journey, shared Scaffold, theme, cards, inputs, navigation and press-feedback implementations.
- Fixed Journey's 20dp top padding to reuse the existing 8dp reference value (horizontal margins remain 20dp; bottom now shares 28dp).
- Removed empty location-suggestion Row nodes that doubled field spacing from 14dp to 28dp. Matching suggestions retain their original behavior.
- Shortened the screen heading and redundant input labels to avoid extra heading/input lines on 320dp phones. Form-card heading and total-label typography now use the existing semantic roles; shared font scale, hero amount, cards, buttons and controls remain intact.
- Captured and inspected all four real app screens under matching API35 native-rendered configurations: 393×851dp Light/Dark, 320×640dp Dark, and 393×851dp Light at Android font scale 1.6, all mdpi. All nine reference-screen images with before/after pairs are byte-for-byte identical.
- No whole-screen scaling, density override, new Scaffold or system-inset handling. No financial, journey-state, database, permission, version or navigation-order changes.

## Verification / current work

All **230 host tests pass**, zero failures/errors/skipped, including four new layout comparison cases and the existing journey calculation/save/load/history tests. Debug APK builds. Signed debug and optimized unsigned release APKs build successfully. Release vital lint and debug/release 16KiB ZIP alignment checks pass. Debug lint has 0 errors, the same 10 advisory warnings and 1 hint as the baseline: six preserved unused app-name/version entries, one SQLite update suggestion, one state-backed offset suggestion, two KTX suggestions and one primitive-state hint. Native API26 in-place installation succeeds without clearing data; launch reports `Status: ok`, and MainActivity is confirmed as the resumed foreground activity. This native check covers startup, not a rerun of the historical financial/OCR instrumentation suite.

Detailed causes, resolved measurements, screenshot links, modified components and verification limitations: [Journey layout report](docs/journey-layout/README.md).

## APK

- `downloads/Fuel-Expense-Tracker-v1.4.0-journey-layout-debug.apk` — 76,098,095 bytes; Android 8.0+, unchanged version 1.4.0 / code 6.
- SHA256 `efcaff409f5ae2694d161764af0637053e72e61678ebc684e30c94f7edf2551d`.
- Original package/signing certificate retained for in-place updates; all older APKs remain available. Private release signing keys are not included.

## Files created or modified

- `app/src/main/java/my/fuelledger/ui/JourneyScreen.kt`
- `app/src/main/java/my/fuelledger/ui/Components.kt` (shared existing padding token)
- `app/src/main/java/my/fuelledger/ui/Screens.kt` (same values via relocated token)
- `app/src/test/java/my/fuelledger/ui/JourneyLayoutTest.kt`
- `app/src/test/java/my/fuelledger/ui/AppSmokeTest.kt` (label selectors only)
- `docs/journey-layout/README.md`, `docs/journey-layout/before/`, `docs/journey-layout/after/` (test screenshots and measured geometry)
- `README.md`, `PROGRESS.md`, `VALIDATION.md`
- `downloads/Fuel-Expense-Tracker-v1.4.0-journey-layout-debug.apk`, `downloads/SHA256SUMS`

## Remaining tasks / known limits

No known compilation errors or Journey-specific layout defect remains in the verified configurations. Shared Expenses/Analytics navigation labels wrap at 160% font size on every screen; this existing behavior is unchanged. Physical-device/OEM, actual TalkBack and system-bar visual checks remain manual. Screens retain natural scrolling and accessibility-driven height growth. Home/Fuel/Journey list spacing remains 16dp; Expenses retains its original 14dp.

## Exact resumption instructions

```sh
cd /workspace/ChatgptFuelConsumption
git status --short --branch
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
```

For layout-only verification: `./gradlew :app:testDebugUnitTest --tests my.fuelledger.ui.JourneyLayoutTest`. Captures are regenerated under `app/build/reports/journey-layout`. Compare the same navigation item across screens when measuring semantics: long labels can give different items different heights at large fonts. Keep all screen configuration parameters matched. Retain `ledgerScreenPadding`, shared card/input controls, native font scaling, package/version/signing identity and Room schema 4. Finish source edits before running lint; assemble release and lint sequentially. Do not uninstall/clear app data or reapply the old WIP stash. Keep previous APKs and baseline visual evidence. Keys, passwords, databases and build caches stay outside Git.

<details>
<summary>Preserved progress before Journey layout correction</summary>

# Current progress — Press feedback refinement

Updated 16 October 2026 (Malaysia time), continuing from `b2d6706`. Implementation is complete; prior reports are preserved below.

## Completed features and source of the old overlay

- Material3's explicit ripple/state layers, plus the un-clipped rectangular selectable regions inside JourneyTypeToggle, caused the unwanted grey press effect. Replacing LocalIndication alone would miss Material3 controls.
- A shared interaction provider disables Material3 ripples and supplies scale-only feedback for Foundation clickable/selectable controls. Reusable Material adapters share each native interaction source and scale the entire rounded surface, not only its label.
- Buttons, text/outlined/tonal/icon buttons, chips, navigation items, clickable cards, dropdown items, switches, checkboxes and allocation sliders share the policy. Existing click, drag, disabled state, sizing, roles and selected semantics remain native. The journey rail explicitly clips children to its rounded outline. Selected categories/toggles/chips/navigation retain clean accent backgrounds/borders.
- Draw-only scaling reaches about 0.97× while pressed (90ms) and returns over 130ms. Layout and hit regions do not shrink. Rounded accent keyboard-focus outlines replace the focus indication lost by disabling ripples. Stable indication nodes retain focus across theme changes.
- Android animator duration scale is observed and re-read on resume. Scale0 disables even instant shrinking; Compose also respects nonzero duration scaling. App code never changes system motion settings. Previews avoid registering Android setting observers.
- Root app, dialogs, app-lock UI and screen previews use the same LedgerTheme policy. Standard picker/input internals keep their native editing and selection behavior; ripple overlays are suppressed globally.
- No business logic, Room entities/migrations, financial calculations, records, preference values, dependencies or manifest permissions changed. App name/version remain 1.4.0 / code6; offline architecture and previous work are preserved.

## Verification / current work

**226 host tests pass** with zero failures/errors/skipped, including 8 new API26/35 interaction executions. Native pixel assertions check light/dark scale ratio, unchanged accent colour, rounded corners, stable touch/semantics bounds, release/cancel, disabled actions, selected chip/navigation behavior and zero-motion click behavior. Existing entry, planning, receipt, lock and persistence regressions pass. Signed debug and optimized unsigned release APKs build successfully; release vital lint and debug/release 16KiB ZIP alignment checks pass. Debug lint has 0 errors, the same 10 advisory warnings and 1 hint as the preceding build (details in VALIDATION.md). Native API26 in-place installation succeeds, launch reports `Status: ok`, and MainActivity is the resumed foreground activity. This native check covers startup; prior financial/OCR instrumentation results are preserved below.

## APK

- `downloads/Fuel-Expense-Tracker-v1.4.0-press-feedback-debug.apk` — 76,098,095 bytes, signed debug build, Android 8.0+.
- SHA256 `4d1afc37dfdd04e0c48daebd5cd83af9ba14adac3661596e9964fd77020122d2`. Original package/signing certificate and app metadata retained for in-place updates. Older APKs/checksums are preserved.
- Optimized unsigned release output requires private signing. Do not commit private signing keys.

## Remaining tasks / known limits

No known compilation errors or implementation work remain for this request. Physical-phone haptics, TalkBack/keyboard behavior across OEMs and all device motion-setting variants remain manual checks. Standard Material picker internals expose no shared press source; they retain native input/selection feedback rather than a custom per-day/dial animation. System-owned picker/authentication windows retain platform interaction behavior. Lint/Gradle advisories are recorded in VALIDATION.md.

## Files created or modified

- [`PROGRESS.md`](PROGRESS.md)
- [`README.md`](README.md)
- [`VALIDATION.md`](VALIDATION.md)
- [`app/src/main/java/my/fuelledger/ui/AppLockSettings.kt`](app/src/main/java/my/fuelledger/ui/AppLockSettings.kt)
- [`app/src/main/java/my/fuelledger/ui/FintechTheme.kt`](app/src/main/java/my/fuelledger/ui/FintechTheme.kt)
- [`app/src/main/java/my/fuelledger/ui/LedgerApp.kt`](app/src/main/java/my/fuelledger/ui/LedgerApp.kt)
- [`app/src/main/java/my/fuelledger/ui/PressComponents.kt`](app/src/main/java/my/fuelledger/ui/PressComponents.kt)
- [`app/src/main/java/my/fuelledger/ui/PressFeedback.kt`](app/src/main/java/my/fuelledger/ui/PressFeedback.kt)
- [`app/src/main/java/my/fuelledger/ui/RefinedControls.kt`](app/src/main/java/my/fuelledger/ui/RefinedControls.kt)
- [`app/src/main/java/my/fuelledger/ui/ScreenPreviews.kt`](app/src/main/java/my/fuelledger/ui/ScreenPreviews.kt)
- [`app/src/test/java/my/fuelledger/ui/PressFeedbackTest.kt`](app/src/test/java/my/fuelledger/ui/PressFeedbackTest.kt)
- [`downloads/Fuel-Expense-Tracker-v1.4.0-press-feedback-debug.apk`](downloads/Fuel-Expense-Tracker-v1.4.0-press-feedback-debug.apk)
- [`downloads/SHA256SUMS`](downloads/SHA256SUMS)

## Exact resumption instructions

```sh
cd /workspace/ChatgptFuelConsumption
git status --short --branch
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
```

Use the existing checkout and pinned toolchain; preserve package, version, signing identity and Room schema 4. Finish source edits before running lint, and run release assembly and debug lint sequentially. Targeted interaction tests: `./gradlew :app:testDebugUnitTest --tests my.fuelledger.ui.PressFeedbackTest` (8 executions on APIs26/35). Do not clear/uninstall app data. Reuse `LedgerTheme`/`PremiumInteractions` for new theme roots and the shared Material adapters for new app controls. Foundation indications can only scale content drawn inside their modifier; put custom foreground backgrounds inside that scope or use outer `Modifier.pressFeedback` with the shared interaction source. Keep prior APKs and append basename-only checksums. Keys, databases, passwords and build caches stay outside Git.

<details>
<summary>Preserved progress before press-feedback refinement</summary>

# Current progress — Expense entry and filter layout refresh

Updated 16 October 2026 (Malaysia time). Both user requests are delivered separately: commit `69f0aa3` moves one existing heading above the unchanged Expenses filter row; the Add Expense redesign follows in its own checkpoint. Previous work and reports remain below.

## Completed

- Expenses order is Transaction history → horizontal category filters → existing search/date controls and records. One-tap filtering, custom categories, Others-last ordering, spacing and calculations are unchanged. This isolated move does not alter Add Expense controls.
- Add/Edit Expense has one horizontal category icon row with short labels, accessible full names, animated selection and automatic visibility of the selected category. Existing category IDs are preserved.
- Cents-first 0–9 keypad, Backspace, Clear and large formatted MYR amount, no decimal key or decimal input field. Exact integer-sen entry starts at zero, handles leading zeros, rejects values above the existing limit without overflow, disables Save at zero and loads existing amounts without rescaling.
- Cash/Card/E-Wallet icon buttons, subtle selection haptics and animated highlights. Last selection is remembered locally in encrypted DataStore, without making the editor busy or overwriting unrelated preferences. New entries start with that visible selection (Cash on old/new installations until changed).
- Historical Credit Card and Debit Card strings remain untouched by display grouping and same-group editing. Unknown historical payments are preserved until the user explicitly selects a method to save. The validator additionally accepts new Card records without removing legacy values.
- Compact secure expense dialog; date/time, description, notes and all receipt capture/import/scan/review tools remain under Details & receipt. Attachment ownership remains composed while collapsed, so an unsaved photo is retained. Unchanged edit dates/times preserve original timestamp precision.
- Existing Room schema/entities, category associations, financial/fuel calculations and offline security architecture are unchanged. No permission/dependency/network service added. App name/version remain 1.4.0 / code 6 as requested; previous header cleanup remains.

## Verification / currently implementing

All implementation is complete. **218 host tests pass**, 0 failures/errors/skipped, including 16 real-app Compose executions on APIs26/35 and three control-layout cases covering both themes, small/wide phones and 1.6 font scale. Debug/test APKs build. Debug and Android test APKs and the optimized unsigned release build successfully. Debug lint has 0 errors, 10 advisory warnings and 1 hint: six unused app-name/version resource entries left by the prior header cleanup, an SQLite update suggestion, a state-backed offset suggestion, two optional KTX suggestions and a primitive-state hint. The original name/version resources are intentionally preserved. Release vital lint and debug/release 16KiB ZIP alignment checks pass. All 3 native API26 integration tests pass in airplane mode (222.973 seconds), including bundled OCR, permissions, encrypted Card records, remembered payment preferences, complete receipt/finance backup/restore and database reopen. Instrumentation result: OK (3 tests).

## APK

- `downloads/Fuel-Expense-Tracker-v1.4.0-expense-entry-debug.apk` — 76,098,095 bytes, signed debug build, Android 8.0+.
- SHA256 `ce011e110952c4674834ba520b2be19e6cea70359ad61d75b3acbd4928c4cb47`; same package and original signing certificate. Previous APK files/checksums are preserved.
- Release output requires private signing; do not distribute the unsigned release as an installable APK.

## Remaining tasks / known issues

No known compilation errors or implementation work remain for these requests. Physical-phone haptics, camera/picker/OEM behavior, real TalkBack operation and the full range of device/font combinations still need hands-on testing. Basic entry is compact; smaller screens, long amounts and expanded details scroll while Save remains separate. Automatic OCR quality limitations from the prior implementation remain documented. Existing Gradle/compiler/lint advisories are recorded in VALIDATION.md; unrelated dependency upgrades are outside this UI task.

## Files created or modified across the two changes

- [`PROGRESS.md`](PROGRESS.md)
- [`README.md`](README.md)
- [`VALIDATION.md`](VALIDATION.md)
- [`app/src/androidTest/java/my/fuelledger/OfflinePrivacyTest.kt`](app/src/androidTest/java/my/fuelledger/OfflinePrivacyTest.kt)
- [`app/src/main/java/my/fuelledger/LedgerViewModel.kt`](app/src/main/java/my/fuelledger/LedgerViewModel.kt)
- [`app/src/main/java/my/fuelledger/data/Database.kt`](app/src/main/java/my/fuelledger/data/Database.kt)
- [`app/src/main/java/my/fuelledger/data/ExpenseEntry.kt`](app/src/main/java/my/fuelledger/data/ExpenseEntry.kt)
- [`app/src/main/java/my/fuelledger/data/LedgerRepository.kt`](app/src/main/java/my/fuelledger/data/LedgerRepository.kt)
- [`app/src/main/java/my/fuelledger/data/SettingsStore.kt`](app/src/main/java/my/fuelledger/data/SettingsStore.kt)
- [`app/src/main/java/my/fuelledger/domain/CentsKeypad.kt`](app/src/main/java/my/fuelledger/domain/CentsKeypad.kt)
- [`app/src/main/java/my/fuelledger/ui/Components.kt`](app/src/main/java/my/fuelledger/ui/Components.kt)
- [`app/src/main/java/my/fuelledger/ui/Editors.kt`](app/src/main/java/my/fuelledger/ui/Editors.kt)
- [`app/src/main/java/my/fuelledger/ui/ExpenseEntryControls.kt`](app/src/main/java/my/fuelledger/ui/ExpenseEntryControls.kt)
- [`app/src/main/java/my/fuelledger/ui/ReceiptAttachment.kt`](app/src/main/java/my/fuelledger/ui/ReceiptAttachment.kt)
- [`app/src/main/java/my/fuelledger/ui/Screens.kt`](app/src/main/java/my/fuelledger/ui/Screens.kt)
- [`app/src/test/java/my/fuelledger/data/ExpenseEntryTest.kt`](app/src/test/java/my/fuelledger/data/ExpenseEntryTest.kt)
- [`app/src/test/java/my/fuelledger/data/RepositoryTest.kt`](app/src/test/java/my/fuelledger/data/RepositoryTest.kt)
- [`app/src/test/java/my/fuelledger/data/SettingsStoreTest.kt`](app/src/test/java/my/fuelledger/data/SettingsStoreTest.kt)
- [`app/src/test/java/my/fuelledger/domain/CentsKeypadTest.kt`](app/src/test/java/my/fuelledger/domain/CentsKeypadTest.kt)
- [`app/src/test/java/my/fuelledger/ui/AppSmokeTest.kt`](app/src/test/java/my/fuelledger/ui/AppSmokeTest.kt)
- [`app/src/test/java/my/fuelledger/ui/ExpenseEntryControlsTest.kt`](app/src/test/java/my/fuelledger/ui/ExpenseEntryControlsTest.kt)
- [`downloads/Fuel-Expense-Tracker-v1.4.0-expense-entry-debug.apk`](downloads/Fuel-Expense-Tracker-v1.4.0-expense-entry-debug.apk)
- [`downloads/SHA256SUMS`](downloads/SHA256SUMS)

## Exact resumption instructions

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

<details>
<summary>Preserved reports before expense-entry redesign</summary>

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

# Current progress — Fuel & Expense Tracker v1.4.0

Updated 15 October 2026 (Malaysia time). Continues the existing application from commit `22fabb5`. Earlier progress reports are retained below.

## Completed features

- Settings and Journey use one weighted historical fuel value: 100 × complete-cycle litres / complete-cycle distance. Partial fills within full/full cycles count; incomplete, invalid and zero-distance cycles do not. No valid history displays “Not enough fuel data”. Optional fallback requires explicit entry/enablement; the old stored numeric preference remains preserved.
- Settings → Financial Planning now has one Monthly Money Planner entry, containing Expected Income, Fixed Monthly Expenses and Monthly Allocation. Overview/payments and existing budgets remain accessible. The home card shows planned Available After Fixed Commitments, Expected Income and Fixed Expenses, with details/help on demand.
- Expected Income supports the six requested source types, multiple active/inactive sources, schedules and optional zero/blank irregular estimates. The Received subview retains actual income CRUD and totals. Forecasts never become received transactions.
- Allocation sliders and a live accessible donut show percentages/RM values. Only the selected category and named flexible remainder change; total stays exactly 100%, with no negative allocation. Custom categories, mappings, reserves, defaults and per-month overrides remain supported.
- Home/Fuel/Expenses/Journey/Analytics navigation; Others always last with stable custom-category sorting. Fuel charts offer 3/6/12 rolling months including the current month, using recorded spending, local-zone dates, zero gaps and labels such as Aug 26 / Sep 26.
- Journey has an animated accessible tap/drag binary pill. Live historical consumption updates new and reopened history-based plans. Explicit overrides, saved fallback and legacy recorded values retain their distinct source. New totals are fuel + tolls + parking. Other-fee and passenger-sharing UI is removed; legacy fields/totals remain in JSON until an explicit edit/save uses the new calculation version.
- Redundant descriptions/headings moved into expandable help, preserving essential warnings and distinctions. Existing light/dark/system appearance, scanner, encrypted storage, backups, reminders and transaction functionality remain intact.
- Version 1.4.0 / code 6. No new dependency, permission, Room entity/table/schema change, migration, destructive reset or encryption-format change. Optional new serialized fields decode old JSON with compatible defaults.

## Current implementation and verification

Implementation is complete. 201 host tests pass (0 failures/errors/skipped), including real Compose navigation and entry on Robolectric API26/35. All 9 native Android tests pass in airplane mode; the final APK also passes all 3 offline integration rechecks. Debug/test/optimized unsigned release APKs build; lint has 0 errors, 4 advisory warnings and 1 hint. Details are recorded in VALIDATION.md. No known compilation errors remain.

## Updated APK

- `downloads/Fuel-Expense-Tracker-v1.4.0-debug.apk` — 76,096,099 bytes, Android 8.0+, signed debug build.
- SHA256: `180d4f2f7cc92c9c099774cec03e171da62fa312d4ea4494c225ec1cbf4a90ce`.
- Original package and signing certificate preserved for in-place updates. Older APK files remain unchanged. Optimized release output requires private signing and is not the installable download.

## Remaining tasks and known issues

- Physical-phone camera/picker handoff, permission denial, biometric/PIN interaction, OEM behavior, large accessibility font sizes and RTL gestures need hands-on testing. Host API35 tests are Robolectric; native tests use an API26 software emulator.
- OCR cannot guarantee every thermal receipt parses correctly; review ambiguous receipts. Optional tax/item extraction remains unimplemented from the earlier scanner scope.
- Old journeys retain their historical total until explicitly updated; saving an edit applies fuel + tolls + parking. Their hidden legacy metadata is retained for backup compatibility. New saved lists show the recorded estimate; reopening a history-based route uses the latest average.
- New zero-estimate income sources do not contribute planned occurrences. Existing captured past monthly snapshots remain unchanged, so historical forecasts reflect the settings captured then.
- Lint advisory details are in VALIDATION.md. Gradle reports existing deprecation/configuration-cache advice; no version bump of unrelated dependencies is needed for this refinement.

## Exact instructions for resuming development

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

## Files created or modified in this refinement

- [`PROGRESS.md`](PROGRESS.md)
- [`README.md`](README.md)
- [`VALIDATION.md`](VALIDATION.md)
- [`app/build.gradle.kts`](app/build.gradle.kts)
- [`app/src/main/java/my/fuelledger/data/FinanceData.kt`](app/src/main/java/my/fuelledger/data/FinanceData.kt)
- [`app/src/main/java/my/fuelledger/data/JourneyData.kt`](app/src/main/java/my/fuelledger/data/JourneyData.kt)
- [`app/src/main/java/my/fuelledger/data/MonthlyPlanner.kt`](app/src/main/java/my/fuelledger/data/MonthlyPlanner.kt)
- [`app/src/main/java/my/fuelledger/data/Refinement.kt`](app/src/main/java/my/fuelledger/data/Refinement.kt)
- [`app/src/main/java/my/fuelledger/data/SettingsStore.kt`](app/src/main/java/my/fuelledger/data/SettingsStore.kt)
- [`app/src/main/java/my/fuelledger/domain/Calculations.kt`](app/src/main/java/my/fuelledger/domain/Calculations.kt)
- [`app/src/main/java/my/fuelledger/ui/FinanceScreen.kt`](app/src/main/java/my/fuelledger/ui/FinanceScreen.kt)
- [`app/src/main/java/my/fuelledger/ui/JourneyScreen.kt`](app/src/main/java/my/fuelledger/ui/JourneyScreen.kt)
- [`app/src/main/java/my/fuelledger/ui/LedgerApp.kt`](app/src/main/java/my/fuelledger/ui/LedgerApp.kt)
- [`app/src/main/java/my/fuelledger/ui/MonthlyPlannerScreen.kt`](app/src/main/java/my/fuelledger/ui/MonthlyPlannerScreen.kt)
- [`app/src/main/java/my/fuelledger/ui/RefinedControls.kt`](app/src/main/java/my/fuelledger/ui/RefinedControls.kt)
- [`app/src/main/java/my/fuelledger/ui/Screens.kt`](app/src/main/java/my/fuelledger/ui/Screens.kt)
- [`app/src/main/java/my/fuelledger/ui/SettingsScreen.kt`](app/src/main/java/my/fuelledger/ui/SettingsScreen.kt)
- [`app/src/test/java/my/fuelledger/data/RefinementTest.kt`](app/src/test/java/my/fuelledger/data/RefinementTest.kt)
- [`app/src/test/java/my/fuelledger/ui/AppSmokeTest.kt`](app/src/test/java/my/fuelledger/ui/AppSmokeTest.kt)
- [`app/src/test/java/my/fuelledger/ui/RefinedControlsTest.kt`](app/src/test/java/my/fuelledger/ui/RefinedControlsTest.kt)
- [`downloads/Fuel-Expense-Tracker-v1.4.0-debug.apk`](downloads/Fuel-Expense-Tracker-v1.4.0-debug.apk)
- [`downloads/SHA256SUMS`](downloads/SHA256SUMS)

<details>
<summary>Preserved progress reports through version 1.3.0</summary>

# Current progress — Fuel & Expense Tracker v1.3.0

Updated 15 October 2026. This update extends the existing application from commit `2725b51`; the complete prior progress report is preserved below.

## Completed features

- Monthly Money Planner with recurring expected income, fixed commitments, planned available, actual net cash flow and remaining allowance. Expected income remains separate from received income. Negative plans stay visible.
- Fixed Expenses and Expected Income management reuse the existing recurrence model, with manual payment confirmation, optional end dates, pause/resume and original-day monthly anchoring. Existing automatic recurring rules retain their behavior. Paid occurrences link one actual expense; a unique occurrence index and transactional checks prevent duplicate charges, including after converting a legacy automatic rule to manual confirmation.
- Saved monthly snapshots preserve captured past plans. Startup/screen reconciliation backfills missed months without continuous execution. Future months are forecasts. Uncaptured old months are reconstructed from the rules available when first viewed.
- Monthly Allocation settings: editable categories and exact two-decimal percentages, 100% validation, independent month overrides, integer-sen largest-remainder rounding, spending mappings and savings reserves. Defaults and overrides use the existing encrypted Room store. Allocations never post transactions or deduct fixed commitments twice.
- Appearance settings: System Default for new installations, immediate Light/Dark/System selection, encrypted DataStore persistence, preserved previous choices and matching app-lock appearance.
- Standalone Journey Planner in bottom navigation. Manual origin/destination/distance with offline saved-location suggestions; outbound/return distances, toll segments, parking, custom fees, selectable passenger sharing, local save/edit/delete/duplicate history. Existing fuel-estimation logic is reused; plans never create expenses. Fuel Tracker retains actual statistics and history.
- Automatic offline Receipt Scanner via the dashboard: delegated camera capture and Photo Picker, optional crop/rotate, larger portrait receipts with bounded decoding, bundled ML Kit OCR, conservative total/date/merchant parsing and image-quality checks, editable local merchant mappings, review/default and optional Smart Auto-Save modes, duplicate fingerprints and same-day/amount review, existing-refuel matching, encrypted retained photos, transactional save and guarded Undo.
- Optional on-device upcoming/overdue/shortfall reminders with Android 13+ permission requested only when enabled. No exact scheduling or Internet connection required.
- Additive Room schema 3 → 4, updated encrypted complete backups including all new planning/journey/scanner records, preserved schema 1 → 2 → 3 migrations, and database-free Compose previews for the new summary components.

## Current implementation and verification

Feature implementation is complete. **184 host tests pass**, with no failures, errors or skipped cases. Debug and Android test APKs build. Optimized unsigned release builds. All 9 native API26 tests pass in airplane mode, including offline OCR, complete encrypted finance/planning/scanner backups, additive schema 1/2/3 → 4 migrations and wrong/missing-key preservation. All 4 build-policy tests pass. Final debug/release lint has 0 errors and 3 advisory warnings. See VALIDATION.md for the final record.

New tests cover the RM3,000 − RM1,130 = RM1,870 example, fixed-payment deduplication, missed schedules, month-end dates, negative/zero allocation, rounding and invalid percentages, month overrides/history, time zones, journey fee/sharing validation, theme/scanner persistence, merchant categorization, duplicate/auto-save/review/Undo safeguards, additive migration from installed schema 3, backup round trips and real Compose navigation/data entry.

## Updated APK

- `downloads/Fuel-Expense-Tracker-v1.3.0-debug.apk` — 76,639,470 bytes, Android 8.0+, signed debug build.
- SHA-256: `c85624e8ee2e9ce299718ad409150284577edb9413bfdb2d1c57fbfe722ef059`.
- Version 1.3.0 / code 5; package and signing certificate match previous builds for in-place updates. Older APKs are preserved.
- Optimized unsigned release remains at `app/build/outputs/apk/release/app-release-unsigned.apk` and requires private signing. No signing keys, databases, passwords or caches belong in Git.

## Remaining tasks and known limits

- Physical-phone camera/Photo Picker handoff, notification permission denial, thermal-paper image quality, biometric/PIN interaction and OEM reboot/transfer behavior need hands-on verification. Host API35 coverage is Robolectric; native device tests use an API26 software emulator.
- OCR heuristics cannot guarantee every receipt is correct. Poor, very long or severely tilted receipts may need a better photo or manual entry. Non-MYR receipts require manual conversion. Tax/item-level extraction is optional and not implemented.
- Normal deletion retains a receipt fingerprint to guard against accidental rescanning; Undo of an unchanged accepted receipt also removes its fingerprint. An edited transaction cannot be silently removed by Undo.
- Captured past plans are immutable when rules/defaults change; historical months not captured earlier are reconstructions, not evidence of past settings. Explicit monthly allocation overrides remain editable.
- Local reminders are approximate and may be delayed/disabled by Android. Reminder summaries currently cover the current and next month; inspect older unpaid occurrences by selecting a past month in Planner. No remote messaging, geocoding or toll pricing is used.
- No known compilation errors remain. Lint has 0 errors and 3 advisory warnings (SQLite update and two optional KTX suggestions). Release APK is unsigned; the installable debug APK is for personal testing. Keep private release signing keys outside Git.

## Exact instructions for resuming development

```sh
cd /workspace/ChatgptFuelConsumption
git status --short --branch
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
./gradlew -p buildSrc test
```

Keep the existing checkout, package `my.fuelledger` and signing identity. Never clear installed user data or add destructive migrations. Schema 4 is the new upgrade target. Preserve older APKs. Run release assembly and debug lint sequentially. For native tests use the helper in the previous report and require `OK (9 tests)` in instrumentation output; adb may return status 0 even when tests fail. Do not uninstall/clear user data to run tests. Review README.md, SECURITY.md and VALIDATION.md before making further architecture changes.

## Files created or modified in this upgrade

- [`PROGRESS.md`](PROGRESS.md)
- [`README.md`](README.md)
- [`SECURITY.md`](SECURITY.md)
- [`VALIDATION.md`](VALIDATION.md)
- [`app/build.gradle.kts`](app/build.gradle.kts)
- [`app/schemas/my.fuelledger.data.LedgerDatabase/4.json`](app/schemas/my.fuelledger.data.LedgerDatabase/4.json)
- [`app/src/androidTest/java/my/fuelledger/OfflinePrivacyTest.kt`](app/src/androidTest/java/my/fuelledger/OfflinePrivacyTest.kt)
- [`app/src/androidTest/java/my/fuelledger/security/EncryptedStorageTest.kt`](app/src/androidTest/java/my/fuelledger/security/EncryptedStorageTest.kt)
- [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml)
- [`app/src/main/java/my/fuelledger/FinanceViewModel.kt`](app/src/main/java/my/fuelledger/FinanceViewModel.kt)
- [`app/src/main/java/my/fuelledger/JourneyViewModel.kt`](app/src/main/java/my/fuelledger/JourneyViewModel.kt)
- [`app/src/main/java/my/fuelledger/LedgerViewModel.kt`](app/src/main/java/my/fuelledger/LedgerViewModel.kt)
- [`app/src/main/java/my/fuelledger/MainActivity.kt`](app/src/main/java/my/fuelledger/MainActivity.kt)
- [`app/src/main/java/my/fuelledger/data/Backup.kt`](app/src/main/java/my/fuelledger/data/Backup.kt)
- [`app/src/main/java/my/fuelledger/data/Database.kt`](app/src/main/java/my/fuelledger/data/Database.kt)
- [`app/src/main/java/my/fuelledger/data/FinanceData.kt`](app/src/main/java/my/fuelledger/data/FinanceData.kt)
- [`app/src/main/java/my/fuelledger/data/FinanceRepository.kt`](app/src/main/java/my/fuelledger/data/FinanceRepository.kt)
- [`app/src/main/java/my/fuelledger/data/JourneyData.kt`](app/src/main/java/my/fuelledger/data/JourneyData.kt)
- [`app/src/main/java/my/fuelledger/data/LedgerRepository.kt`](app/src/main/java/my/fuelledger/data/LedgerRepository.kt)
- [`app/src/main/java/my/fuelledger/data/MonthlyPlanner.kt`](app/src/main/java/my/fuelledger/data/MonthlyPlanner.kt)
- [`app/src/main/java/my/fuelledger/data/ReceiptAutomation.kt`](app/src/main/java/my/fuelledger/data/ReceiptAutomation.kt)
- [`app/src/main/java/my/fuelledger/data/ReceiptBackupArchive.kt`](app/src/main/java/my/fuelledger/data/ReceiptBackupArchive.kt)
- [`app/src/main/java/my/fuelledger/data/SettingsStore.kt`](app/src/main/java/my/fuelledger/data/SettingsStore.kt)
- [`app/src/main/java/my/fuelledger/finance/PlannerReminders.kt`](app/src/main/java/my/fuelledger/finance/PlannerReminders.kt)
- [`app/src/main/java/my/fuelledger/finance/RecurringWorker.kt`](app/src/main/java/my/fuelledger/finance/RecurringWorker.kt)
- [`app/src/main/java/my/fuelledger/receipts/ReceiptParser.kt`](app/src/main/java/my/fuelledger/receipts/ReceiptParser.kt)
- [`app/src/main/java/my/fuelledger/receipts/ReceiptScanner.kt`](app/src/main/java/my/fuelledger/receipts/ReceiptScanner.kt)
- [`app/src/main/java/my/fuelledger/receipts/ReceiptStore.kt`](app/src/main/java/my/fuelledger/receipts/ReceiptStore.kt)
- [`app/src/main/java/my/fuelledger/ui/AppLockSettings.kt`](app/src/main/java/my/fuelledger/ui/AppLockSettings.kt)
- [`app/src/main/java/my/fuelledger/ui/FinanceScreen.kt`](app/src/main/java/my/fuelledger/ui/FinanceScreen.kt)
- [`app/src/main/java/my/fuelledger/ui/JourneyScreen.kt`](app/src/main/java/my/fuelledger/ui/JourneyScreen.kt)
- [`app/src/main/java/my/fuelledger/ui/LedgerApp.kt`](app/src/main/java/my/fuelledger/ui/LedgerApp.kt)
- [`app/src/main/java/my/fuelledger/ui/MonthlyPlannerScreen.kt`](app/src/main/java/my/fuelledger/ui/MonthlyPlannerScreen.kt)
- [`app/src/main/java/my/fuelledger/ui/ReceiptAttachment.kt`](app/src/main/java/my/fuelledger/ui/ReceiptAttachment.kt)
- [`app/src/main/java/my/fuelledger/ui/ReceiptCameraButton.kt`](app/src/main/java/my/fuelledger/ui/ReceiptCameraButton.kt)
- [`app/src/main/java/my/fuelledger/ui/ScreenPreviews.kt`](app/src/main/java/my/fuelledger/ui/ScreenPreviews.kt)
- [`app/src/main/java/my/fuelledger/ui/Screens.kt`](app/src/main/java/my/fuelledger/ui/Screens.kt)
- [`app/src/main/java/my/fuelledger/ui/SettingsScreen.kt`](app/src/main/java/my/fuelledger/ui/SettingsScreen.kt)
- [`app/src/main/java/my/fuelledger/ui/SmartReceiptScreen.kt`](app/src/main/java/my/fuelledger/ui/SmartReceiptScreen.kt)
- [`app/src/main/res/xml/receipt_camera_paths.xml`](app/src/main/res/xml/receipt_camera_paths.xml)
- [`app/src/test/java/my/fuelledger/data/PlannerRepositoryTest.kt`](app/src/test/java/my/fuelledger/data/PlannerRepositoryTest.kt)
- [`app/src/test/java/my/fuelledger/data/PlanningCalculationsTest.kt`](app/src/test/java/my/fuelledger/data/PlanningCalculationsTest.kt)
- [`app/src/test/java/my/fuelledger/data/RepositoryTest.kt`](app/src/test/java/my/fuelledger/data/RepositoryTest.kt)
- [`app/src/test/java/my/fuelledger/data/SettingsStoreTest.kt`](app/src/test/java/my/fuelledger/data/SettingsStoreTest.kt)
- [`app/src/test/java/my/fuelledger/receipts/ReceiptStoreTest.kt`](app/src/test/java/my/fuelledger/receipts/ReceiptStoreTest.kt)
- [`app/src/test/java/my/fuelledger/testing/TestLedgerApplication.kt`](app/src/test/java/my/fuelledger/testing/TestLedgerApplication.kt)
- [`app/src/test/java/my/fuelledger/ui/AppSmokeTest.kt`](app/src/test/java/my/fuelledger/ui/AppSmokeTest.kt)
- [`downloads/Fuel-Expense-Tracker-v1.3.0-debug.apk`](downloads/Fuel-Expense-Tracker-v1.3.0-debug.apk)
- [`downloads/SHA256SUMS`](downloads/SHA256SUMS)

<details>
<summary>Preserved v1.2.0 progress and earlier checkpoint history</summary>

# Current progress — Fuel & Expense Tracker v1.2.0

Updated **15 October 2026 (Asia/Kuala_Lumpur)** after development resumed from checkpoint `6be584c`. The paused-checkpoint document is preserved below; this current section supersedes its unfinished-work/build-error status.

## Completed features

- Preserved and redesigned the existing Kotlin/Compose Home, Fuel, Expenses, and Analytics screens with the requested navy/emerald design, light theme, Material Symbols, responsive components and sample previews. Existing calculations, navigation and stored records were retained.
- New adaptive logo and consistent app/header/settings version **1.2.0 (code 4)**.
- ODO-based estimates after every fill, including partial fills, while retaining measured full-to-full calculations. Optional fuel type, station/location, litres, and receipts.
- Gallery selection, encrypted private receipt storage and in-memory previews, bundled offline OCR, editable extraction/category suggestions, optional deletion after extraction, and generic expense attachments. No public-gallery export/storage permission.
- Room schema **1 → 2 → 3**, maintained SQLCipher storage, Android Keystore-wrapped database password, encrypted settings, verified plaintext-to-encrypted migration, explicit no-delete corruption handling and safe failure/recovery paths.
- Income, monthly/category budgets and local recurring payments with transactional deduplication, original monthly-day anchoring, launch reconciliation and local scheduling.
- Optional local biometric/device credential lock with inactivity/background relocking, protected recent-app/dialog windows and preserved entry state.
- Password-encrypted authenticated complete backups, verified before confirmation, encrypted staging, finance/photo round-trips and explicit legacy imports. No persisted backup password or automatic cloud/device-transfer backup.
- Removed network permissions and OCR telemetry transport paths; pinned SDK policy validates reviewed bytecode signatures. No cloud account/database, remote AI, ads or app analytics integration.

## Current implementation and validation status

Implementation is complete. Final verification passed:

- **144 host tests**, **9 native Android/API26 tests in airplane mode**, and **4 build-policy tests**.
- Debug and Android test APKs built; optimized unsigned release built. Lint: zero errors and one dependency-update advisory (tested SQLite 2.6.2; 2.7.0 is available).
- Final debug/release manifests and packaged permissions contain no Internet, network-state or public-storage permission. Bundled model assets and no-op telemetry sinks were inspected in the APK.
- Native migration, integrity, wrong/missing key preservation, local finance/recurrence, encrypted photo backup and offline OCR tested on a booted software emulator. Earlier migration and locked-dialog defects were corrected and retested.
- APK signature and native alignment verified. The debug signing certificate matches the original APK, preserving the in-place-update signing identity.

Complete evidence and commands are in [VALIDATION.md](VALIDATION.md); dependency/security architecture and limits are in [SECURITY.md](SECURITY.md).

## Artifacts

- New installable file: `downloads/Fuel-Expense-Tracker-v1.2.0-debug.apk` (75,609,451 bytes).
- SHA-256: `47da1aa93b52acd78e50d569352e1f3c86af133289131317a0d6ac4addf5fec0`.
- The original `downloads/Fuel-Expense-Tracker-debug.apk` remains intact as version 1.0.0. Both checksums are listed in `downloads/SHA256SUMS`.
- Optimized unsigned release: `app/build/outputs/apk/release/app-release-unsigned.apk`; requires private release signing before installation/distribution.
- UI screenshots/reports remain in ignored `app/build/reports/`; no private keys, credentials, user data or tool caches belong in Git.

## Remaining tasks and known limits

- Test actual fingerprint/PIN interaction, OEM backup/transfer behavior, hardware-backed key storage and reboot persistence on the user's physical phone. API35 UI tests ran under Robolectric; the actual offline native device was API26.
- Release distribution requires the owner's private signing key, kept outside this repository. The supplied installable APK is a signed, debuggable personal-testing build.
- Missing Keystore material/damaged storage fails closed; no automatic salvage/reset is provided. Old plaintext flash blocks cannot be guaranteed securely erased. Backup-password loss can make backups unrecoverable.
- CSV/old JSON/ZIP files are plaintext; user-chosen cloud/shared destinations are outside private app storage.
- No known compilation errors or failing final tests remain. Toolchain deprecation notices and the SQLite update advisory do not block the build. Run release assembly and debug lint sequentially if a generated-KSP-path race recurs during simultaneous variant processing.

## Exact instructions for resuming development

```sh
cd /workspace/ChatgptFuelConsumption
git status --short --branch
git log -2 --oneline
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
./gradlew -p buildSrc test
```

Use the existing checkout; preserve new work and the stable application ID/signing identity. Do not use destructive migrations, clear installed user data, or introduce a plaintext production fallback. Future Room changes require a new additive migration/schema and preservation tests. Any ML Kit dependency update requires auditing the four pinned telemetry sinks and rerunning bundled-model offline tests.

The prepared cloud toolchain lives under `/workspace/.tooling`; use `scripts/setup-cloud.sh` if a new instance needs setup. Emulator packages/proot are optional device-test tools, not app runtime dependencies. Standard workstations can use normal SDK `adb`. This current cloud machine uses `/workspace/.tooling/fuel-adb.sh` with `emulator-5556`; it maps ADB's writable configuration without changing `$HOME`.

```sh
# Confirm airplane mode, Wi-Fi/data off, and no active network before a fresh-install OCR test.
/workspace/.tooling/fuel-adb.sh -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
/workspace/.tooling/fuel-adb.sh -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
/workspace/.tooling/fuel-adb.sh -s emulator-5556 shell am instrument -w -r \
  my.fuelledger.test/androidx.test.runner.AndroidJUnitRunner
# Require OK (9 tests), not merely adb exit status 0.
```

Before publishing another APK, verify version, both manifests, permissions, signing-certificate continuity, ZIP/native alignment and SHA-256. Create a new versioned filename, retain older artifacts, update checksums and documentation, and never commit credentials/signing keys. GitHub HTTPS Git/raw downloads work; the releases API was unavailable in this environment.

## Complete file inventory since the published 1.0.0 baseline

A = added, M = modified, D = an earlier resource replaced by the current launcher. Original content remains in Git history. Build caches, credentials, signing material and generated reports are excluded.

| Status | File |
| --- | --- |
| M | `.gitignore` |
| A | `PROGRESS.md` |
| M | `README.md` |
| A | `SECURITY.md` |
| M | `VALIDATION.md` |
| M | `app/build.gradle.kts` |
| A | `app/schemas/my.fuelledger.data.LedgerDatabase/2.json` |
| A | `app/schemas/my.fuelledger.data.LedgerDatabase/3.json` |
| A | `app/src/androidTest/java/my/fuelledger/OfflinePrivacyTest.kt` |
| A | `app/src/androidTest/java/my/fuelledger/security/EncryptedStorageTest.kt` |
| M | `app/src/main/AndroidManifest.xml` |
| A | `app/src/main/assets/licenses/material-symbols-LICENSE.txt` |
| A | `app/src/main/assets/licenses/material-symbols-NOTICE.txt` |
| A | `app/src/main/java/my/fuelledger/FinanceViewModel.kt` |
| M | `app/src/main/java/my/fuelledger/LedgerApplication.kt` |
| M | `app/src/main/java/my/fuelledger/LedgerViewModel.kt` |
| M | `app/src/main/java/my/fuelledger/MainActivity.kt` |
| M | `app/src/main/java/my/fuelledger/data/Backup.kt` |
| A | `app/src/main/java/my/fuelledger/data/BackupCrypto.kt` |
| M | `app/src/main/java/my/fuelledger/data/Database.kt` |
| A | `app/src/main/java/my/fuelledger/data/FinanceData.kt` |
| A | `app/src/main/java/my/fuelledger/data/FinanceRepository.kt` |
| M | `app/src/main/java/my/fuelledger/data/LedgerRepository.kt` |
| A | `app/src/main/java/my/fuelledger/data/ReceiptBackupArchive.kt` |
| M | `app/src/main/java/my/fuelledger/data/SettingsStore.kt` |
| M | `app/src/main/java/my/fuelledger/domain/Calculations.kt` |
| A | `app/src/main/java/my/fuelledger/finance/RecurringWorker.kt` |
| A | `app/src/main/java/my/fuelledger/receipts/ReceiptParser.kt` |
| A | `app/src/main/java/my/fuelledger/receipts/ReceiptScanner.kt` |
| A | `app/src/main/java/my/fuelledger/receipts/ReceiptStore.kt` |
| A | `app/src/main/java/my/fuelledger/security/AppLock.kt` |
| A | `app/src/main/java/my/fuelledger/security/EncryptedDatabaseFiles.kt` |
| A | `app/src/main/java/my/fuelledger/security/LocalVault.kt` |
| A | `app/src/main/java/my/fuelledger/security/PreservingOpenHelperFactory.kt` |
| A | `app/src/main/java/my/fuelledger/ui/AppLockSettings.kt` |
| M | `app/src/main/java/my/fuelledger/ui/Components.kt` |
| M | `app/src/main/java/my/fuelledger/ui/Editors.kt` |
| A | `app/src/main/java/my/fuelledger/ui/FinanceScreen.kt` |
| A | `app/src/main/java/my/fuelledger/ui/FintechTheme.kt` |
| M | `app/src/main/java/my/fuelledger/ui/LedgerApp.kt` |
| A | `app/src/main/java/my/fuelledger/ui/ReceiptAttachment.kt` |
| A | `app/src/main/java/my/fuelledger/ui/ScreenPreviews.kt` |
| M | `app/src/main/java/my/fuelledger/ui/Screens.kt` |
| M | `app/src/main/java/my/fuelledger/ui/SettingsScreen.kt` |
| A | `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` |
| D | `app/src/main/res/drawable/ic_launcher.xml` |
| A | `app/src/main/res/drawable/ic_launcher_background.xml` |
| A | `app/src/main/res/drawable/ic_launcher_monochrome.xml` |
| A | `app/src/main/res/drawable/symbol_account_balance_wallet.xml` |
| A | `app/src/main/res/drawable/symbol_add.xml` |
| A | `app/src/main/res/drawable/symbol_favorite.xml` |
| A | `app/src/main/res/drawable/symbol_home.xml` |
| A | `app/src/main/res/drawable/symbol_local_gas_station.xml` |
| A | `app/src/main/res/drawable/symbol_monitoring.xml` |
| A | `app/src/main/res/drawable/symbol_more_horiz.xml` |
| A | `app/src/main/res/drawable/symbol_movie.xml` |
| A | `app/src/main/res/drawable/symbol_receipt_long.xml` |
| A | `app/src/main/res/drawable/symbol_restaurant.xml` |
| A | `app/src/main/res/drawable/symbol_route.xml` |
| A | `app/src/main/res/drawable/symbol_settings.xml` |
| A | `app/src/main/res/drawable/symbol_shopping_bag.xml` |
| A | `app/src/main/res/drawable/symbol_shopping_cart.xml` |
| A | `app/src/main/res/drawable/symbol_speed.xml` |
| A | `app/src/main/res/drawable/symbol_tune.xml` |
| A | `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml` |
| A | `app/src/main/res/mipmap-anydpi/ic_launcher.xml` |
| A | `app/src/test/java/my/fuelledger/data/FinanceRepositoryTest.kt` |
| A | `app/src/test/java/my/fuelledger/data/ReceiptBackupArchiveTest.kt` |
| M | `app/src/test/java/my/fuelledger/data/RepositoryTest.kt` |
| A | `app/src/test/java/my/fuelledger/data/SettingsStoreTest.kt` |
| M | `app/src/test/java/my/fuelledger/domain/CalculationsTest.kt` |
| A | `app/src/test/java/my/fuelledger/receipts/ReceiptParserTest.kt` |
| A | `app/src/test/java/my/fuelledger/receipts/ReceiptReviewTest.kt` |
| A | `app/src/test/java/my/fuelledger/receipts/ReceiptStoreTest.kt` |
| A | `app/src/test/java/my/fuelledger/security/AppLockSessionTest.kt` |
| A | `app/src/test/java/my/fuelledger/security/AppLockStoreTest.kt` |
| A | `app/src/test/java/my/fuelledger/security/LocalVaultTest.kt` |
| A | `app/src/test/java/my/fuelledger/testing/TestLedgerApplication.kt` |
| A | `app/src/test/java/my/fuelledger/ui/AppLockUiTest.kt` |
| M | `app/src/test/java/my/fuelledger/ui/AppSmokeTest.kt` |
| M | `build.gradle.kts` |
| A | `buildSrc/build.gradle.kts` |
| A | `buildSrc/src/main/java/my/fuelledger/build/OfflineMlKitTelemetry.java` |
| A | `buildSrc/src/test/java/my/fuelledger/build/OfflineMlKitTelemetryTest.java` |
| A | `downloads/Fuel-Expense-Tracker-v1.2.0-debug.apk` |
| M | `downloads/SHA256SUMS` |

Total: **86 files** relative to `d5053a0`.

---

<details>
<summary>Original paused checkpoint (6be584c), retained unchanged for history</summary>

# Development checkpoint

Saved on **15 October 2026**. Development is paused at the user's request. This document and the accompanying local Git commit preserve the current work; they do not mark the upgrade as ready to release.

- Repository: `Piccol1001/ChatgptFuelConsumption`.
- Checkout: `/workspace/ChatgptFuelConsumption`, local branch `work`.
- Parent / last published commit: `d5053a080ddff6c5a55d3c2e7ba601878f284a96`.
- Last verified APK: **1.0.0 (code 1)**, already in `downloads/Fuel-Expense-Tracker-debug.apk`.
- Current source version: **1.2.0 (code 4)**. This upgrade is incomplete and has not produced a verified APK.
- All development agents have stopped. No new implementation, builds, or publication should happen until development is resumed.

## 1. Features already completed

### Previously validated and published baseline

The existing app was implemented in Kotlin and Jetpack Compose with Material 3, Room, DataStore, and an MVVM structure. Its baseline includes:

- Home, Fuel, Expenses, and Analytics screens with bottom navigation.
- Expense/refuelling add, edit, delete, payment methods, and custom categories.
- Exact monetary values stored as integer sen; MYR formatting.
- Full-to-full fuel consumption and a trip-cost calculator.
- Transactional linking of refuels to fuel expenses, preventing duplicate spending.
- Local persistence, CSV export, JSON backup/restore, settings, and light/dark themes.
- Disabled automatic cloud/device-transfer backup in the baseline configuration.

The original baseline passed **30 tests** (14 domain, 14 repository, 2 Compose smoke cases), debug lint with zero errors/warnings, debug assembly, and unsigned release assembly. The Compose tests used Robolectric APIs 26 and 35, not a real phone or booted hardware emulator. `VALIDATION.md` records those baseline results; it does **not** validate this checkpoint.

The existing published APK is unchanged:

```text
downloads/Fuel-Expense-Tracker-debug.apk
SHA-256: 4b9768f3d4d2cf1182550f001f4d8e7ec04e3c9121005a2fa05253a82acacdb0
```

Download: https://github.com/Piccol1001/ChatgptFuelConsumption/raw/refs/heads/main/downloads/Fuel-Expense-Tracker-debug.apk

### Additional implementation saved in this checkpoint

These changes have been written to source. They still require the integration and validation below.

- A new teal/ivory/gold fuel-and-wallet launcher logo, adaptive launcher resources, monochrome icon, and generated app name/version labels.
- Navy/emerald dark design and complementary light theme, shared typography, rounded cards, responsive components, accessible chart descriptions, and Material Symbols with license/source notices.
- Redesigned Home, Fuel, Expenses, and Analytics Compose screens, including expense totals, quick actions, history, fuel trends, category donut/breakdown, and date-range selection. Ten sample-data previews cover main screens, light/dark themes, narrow/large-font layout, and empty state.
- Adjacent-fill ODO estimates independent of the full-tank flag, while retaining the original measured full-to-full calculation. Per-fill estimates must remain labelled as estimates because remaining tank fuel is unknown.
- Refuel fuel type, station location, and receipt reference fields, with additive Room migration 1 to 2 and exported schema 2. Existing category/station/notes fields remain intact.
- Refuel form inputs, per-fill preview, receipt attachment UI, bundled offline OCR, conservative receipt parsing, and category suggestion rules.
- Receipt archive and legacy backup handling, CSV metadata additions, and additional calculation/repository/parser/archive/UI tests.

In the last executed pre-security test run, all 17 calculation, 19 repository, 18 parser, and 14 archive tests passed. That evidence predates the latest encryption, finance, and lock edits and must not be treated as validation of the current checkout.

## 2. Features currently being implemented

### Mandatory offline/privacy upgrade

The user's latest privacy requirements take precedence over the earlier request for a public receipt album. Retain gallery selection through the system picker, but keep imported receipt copies encrypted in app-private storage. Do not create public gallery copies. Required behavior includes offline first launch and OCR, encrypted local records/settings/photos, optional receipt deletion after extraction, editable local category suggestions, income/budgets/recurring payments, optional device authentication lock, and password-encrypted manual backups. No Internet permission, cloud service, analytics, remote logging, automatic cloud backup, or device-transfer backup is permitted in the final application.

Saved but unfinished work:

| Area | Saved implementation | Integration still required |
| --- | --- | --- |
| Vault/settings | `LocalVault` uses Android Keystore AES-256-GCM with fresh nonces and authenticated purposes. Encrypted DataStore serializer/migration is drafted. | Compile, test key injection, validate existing-setting migration and failure recovery. |
| Database encryption | SQLCipher dependency is declared. | Room still opens a **plaintext version-2 database**. Wrapped key, SQLCipher factory, and verified non-destructive plaintext migration are absent. |
| Receipt privacy | Encrypted private JPEG storage, in-memory normalization/decryption, legacy-private-photo migration, byte-array OCR, local category suggestions. | ViewModel, preview, picker, forms, retention controls, and tests still use older interfaces in places. |
| Manual backup | Tink streaming AEAD, PBKDF2-SHA256 (600,000 iterations), authenticated versioned header, encrypted staging, password-array cleanup, and draft security tests. | Password UI and encrypted ViewModel calls; Expense receipt fields; finance backup version 3. Current UI still calls legacy unencrypted archive APIs. |
| Income/budgets/recurrence | Entities, DAO, repository, ViewModel, Compose CRUD screen, local WorkManager worker, deduplication and monthly day anchoring. | Room registration/schema/migration, backup integration, category deletion handling, navigation/startup wiring, and tests. |
| App lock | FragmentActivity, secure window flag, encrypted lock settings, timeout controller, biometric/device-credential paths, settings/gate UI, seven test cases. | Preserve forms while locked, protect modal windows/callbacks, settings routing, API-26 stream compatibility, real authentication testing. |

Security dependencies are already declared in `app/build.gradle.kts`: SQLCipher 4.19.1, Tink Android 1.23.0, biometric 1.1.0, fragment 1.9.1, and WorkManager 2.12.0. Their presence alone does not establish working security.

## 3. Files created or modified

The complete file inventory is recorded at the end of this document, relative to the repository root. It includes the source, tests, schema, resources, and this checkpoint document.

An earlier in-progress icon replacement removed `app/src/main/res/drawable/ic_launcher.xml` in favor of the new adaptive resources. This checkpoint preserves that already-existing change; the original remains in Git history. No files were reset, cleaned, or deleted to create the checkpoint. Existing APKs, local toolchain, caches, and user inputs were retained.

## 4. Remaining tasks

1. Finish the database/schema and encryption integration before wiring further features. Preserve existing records and migration paths; do not use destructive migration or reset installed data.
2. Finish receipt privacy: decrypt only in memory, update scanner/preview calls, remove obsolete public-gallery controls and storage permission, support generic expense receipts and editable extraction/category review, and implement delete-after-extraction.
3. Finish password-protected manual backups and finance data round-trips. Keep legacy imports explicit; do not present legacy unencrypted export as the new secure backup. Confirm restoration only after complete authentication/validation and show a review first.
4. Wire Income/Budgets/Recurring through Settings, initialize and reconcile locally on launch, schedule local work, and preserve historical expenses if a recurring rule is removed.
5. Complete app lock without losing entry forms or exposing dialogs. Add settings, inactivity/background handling, and authenticate on supported OS versions.
6. Complete the UI review on different phones/font sizes; use Material Symbols consistently in root navigation/settings. Inspect actual screenshots; previews have not been visually verified in this checkpoint.
7. Run compilation, tests, lint, APK assembly, persistence/migration checks, final merged-manifest/dependency review, and fresh-install airplane-mode tests. Validate hardware authentication on a real device where possible.
8. Rewrite stale README claims after the implementation is verified. The current README still describes intermediate version 1.1.0 / Room 2 / ZIP and gallery features. Preserve `VALIDATION.md` as historical evidence or clearly distinguish new results.
9. Only after validation, produce the updated downloadable APK, record its version/hash/signing continuity, update download documentation, and publish through Git as authorized. The checkpoint itself is local and has not been pushed.

## 5. Known errors and compilation issues

### Last executed test/build command

```sh
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease
```

The command stopped at unit-test failure: **78 executed, 73 passed, 5 failed, 0 skipped**. It did not establish successful debug/release/lint completion for the upgrade.

- Three Compose smoke failures: `ArrayIndexOutOfBoundsException` caused by treating persisted ARGB longs as packed Compose color values. `Components.kt` was subsequently changed to `Color(it.color.toInt())`; that fix has **not** been rerun.
- Two `ReceiptStoreTest` assertion failures: `invalidAndOversizeInputsLeaveNoPrivateReceiptOrPartialFiles` and `importCreatesBoundedPrivateJpegAndPreservesOriginal`. Investigate Robolectric graphics/MIME behavior and use native graphics configuration where appropriate; retain meaningful assertions.
- Log: `/tmp/fuel-update-build.log`. Historical XML: `app/build/test-results/testDebugUnitTest/TEST-*.xml`. Those reports belong to the pre-security source state, not all current files.

### Current source blockers identified by inspection

No build or test has run since the security/finance drafts were saved. Expect the current source to fail compilation until these mismatches are resolved:

- `FinanceRepository` calls missing `LedgerDatabase.financeDao()`. Finance entities are not registered with Room.
- `Expense` is missing `receiptFile`, `recurringRuleId`, and `recurringDate`; archive/finance code already refers to those fields.
- `LedgerViewModel` passes a `File` to `ReceiptScanner.scan`, whose new signature accepts `ByteArray`, and still calls removed `ReceiptStore.saveToGallery`.
- `ReceiptAttachment` decodes a ciphertext file as a JPEG; update it to use decrypted in-memory bytes.
- Receipt storage tests still reference the former plaintext/gallery contract and lack injected vault keys.
- Backup validation only accepts versions 1–2 and does not include finance records.
- `AppLockStore` uses `InputStream.readNBytes`, requiring a portable bounded implementation for minimum API 26.
- Production Keystore/SQLCipher cannot simply run as Android native code under JVM Robolectric. An explicit test application/database/key seam is started but test wiring is incomplete. Never introduce an automatic plaintext production fallback to make host tests pass.

Other unresolved issues: the current lock gate disposes the ledger UI when locked; overlays must also protect Compose dialogs and deferred callbacks. `WRITE_EXTERNAL_STORAGE` (max API 28) and public-gallery wording remain in the source manifest/UI and must be removed to meet the latest privacy request. The merged manifest has not been checked after dependency changes.

No physical phone or booted emulator has tested the upgrade. `/dev/kvm` is absent; an emulator installation attempt did not complete or establish device readiness. No current-version screenshots exist. No new APK has been built or published.

## 6. Exact instructions for resuming development

### Inspect and activate the saved environment

```sh
cd /workspace/ChatgptFuelConsumption
git status --short --branch
git log -1 --oneline
cat PROGRESS.md
source /workspace/.tooling/fuel-ledger-env.sh
java -version
./gradlew --version
```

Use this existing checkout; do not reset or clean it. If more changes appear after the checkpoint, inspect and preserve them before editing. The checkpoint can be located with `git log -1 -- PROGRESS.md`.

The activation helper sets JDK 21.0.12.1+1, Android SDK `/workspace/.tooling/android-sdk`, Android user directory `/workspace/.tooling/android-user`, and Gradle user home `/workspace/.tooling/gradle-home`. Pinned tools include Gradle 9.6.0, AGP 9.4.1, Kotlin/Compose compiler 2.3.20, KSP 2.3.12, platform 37.0, and build-tools 36.0.0. Application minimum API is 26 and target is 37.

If the helper/toolchain is absent in a new environment, inspect `scripts/setup-cloud.sh` and the existing saved environment install/start draft. The setup script also runs tests/build/lint, so an incomplete-checkpoint failure is an application integration issue, not necessarily a toolchain failure. Retain checksum/signature/TLS verification. Saved environment draft configuration does not mean it was published or tested in a new task.

The original privacy request is also retained in the current workspace at `/workspace/attachments/8dfb5bbe-75f5-4f44-8afd-0f7f96d1d1d3/Pasted text.txt`. That attachment and other workspace-only files may not survive a new checkout; the requirements above must remain sufficient to guide the work.

### Integrate in this order

1. **Room/schema:** append nullable Expense receipt/recurrence fields; register `FinanceIncome`, `FinanceBudget`, `FinanceRecurring` and `financeDao()`; add and export schema 3 with additive migration 2 to 3. Keep migration 1 to 2. Do not enforce a relationship that invalidates expenses after deleting a recurring rule.
2. **Encrypted database:** use maintained `net.zetetic.database.sqlcipher.SupportOpenHelperFactory` with a random database key wrapped by `LocalVault`; initialize the native SQLCipher library. For an existing plaintext database, checkpoint its WAL, export into an encrypted temporary database in the same directory, verify schema/counts/integrity, sync it, and replace atomically only after success. Preserve the original until verification succeeds; handle interruption safely and never use destructive fallback. Secure erasure of old flash blocks cannot be guaranteed.
3. **Settings/tests:** finish encrypted DataStore migration, retaining price/consumption/theme and deleting legacy preferences only after a verified commit. `LedgerApplication` already exposes protected `createVault()` / `createDatabase()` seams; add explicit host-test vault/database implementations. Test actual native cipher behavior separately on Android.
4. **Backup model/repository:** add version-3 finance lists with backward-compatible empty defaults, validation, transactional restore, receipt references, and category handling. In category deletion, reassign finance references before deleting a custom category. Preserve pending-settings crash recovery and old backup imports.
5. **Receipt API:** `ReceiptStore(context, vault)` stores encrypted `receipts/<UUID.jpg>.enc`; `file(name)` is ciphertext existence/deletion only; `readPhoto(name)` returns decrypted bytes and must run on IO. Use `ReceiptScanner.scan(bytes)` and in-memory bitmap decoding. Invoke `migrateLegacyPhotos()` for old private JPEGs. Do not modify original gallery photos. Finish retention/review and update tests.
6. **Encrypted backup UI:** call `writeEncrypted(output, backup, passwordChars)` and `readEncrypted(input, passwordChars)` rather than legacy `write/read`. Current minimum export password length is 12 characters. Ask for confirmation on creation, never persist/log a password, pass fresh character arrays, clear UI state, and close `PreparedRestore` on cancel/success. `materialize(prepared)` restores photos with fresh references. Add explicit warnings/confirmation around replacing current records after authenticated review.
7. **Finance navigation/startup:** route `FinanceScreen` / `FinanceViewModel` from Settings; after ledger initialization reconcile local recurring records and call `scheduleRecurring()`. WorkManager is local and must not require network. Add meaningful money/budget/scheduling/idempotence/migration tests.
8. **Lock:** integrate `AppLockSettings`, replace incompatible bounded stream read, preserve forms/picker state behind a secure gate, hide inaccessible semantics/focus, and protect dialogs and callbacks opened while locked. Test API 26 credential flow and API 30+ biometric/device-credential flow. Preserve `FLAG_SECURE` requirements.
9. **UI/polish:** retain existing navigation/calculations/data, finish Material Symbols navigation, validate per-fill estimate labels and all four screens, inspect previews and screenshots, and update name/version documentation consistently to the actually built version.

### Compile and validate after integration

```sh
./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease
```

Capture fresh logs without losing the command's exit status. Inspect the new test XML counts and failed/skipped cases; do not reuse earlier results as evidence. The currently saved archive security suite has 20 cases (only its earlier 14-case version ran), lock suite has seven unrun cases, and finance tests have not yet been written.

On an available emulator/phone, add/run meaningful instrumentation coverage and use `./gradlew :app:connectedDebugAndroidTest` only after a device and actual test cases exist. Verify existing-data upgrades, failed/interrupted encryption/restore, editing/deleting entries, linked fuel totals, full/partial-fill math, category changes, recurrence deduplication, lock transitions, accessibility fonts, and persistent state across restart. Test a fresh airplane-mode first launch and OCR without previously downloaded models. The bundled dependency is `com.google.mlkit:text-recognition:16.0.1`, not the Play Services downloadable-model artifact.

Inspect the final merged manifest and packaged permissions, including third-party components. Confirm no Internet/network-state/public-storage permissions and no automatic cloud/device-transfer paths. Review release shrinking/native packaging and authenticated backup rejection of wrong passwords, tampering, truncation, and invalid references.

```sh
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump permissions app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

Compare signing certificates with the existing downloaded debug APK before claiming an in-place update preserves installed data. Keep signing material outside Git; do not uninstall or clear data to bypass update/migration issues. The unsigned release APK is not installable until properly signed.

After successful validation, update documentation with accurate limits and replace the downloadable APK deliberately. Git HTTPS/raw downloads worked previously; GitHub release API requests were blocked by an HTTP CONNECT 403, so a repository-hosted download was used. Do not force-push or publish the unfinished checkpoint by accident.

### Checkpoint scope and exclusions

The checkpoint includes all current source/resource/test/documentation changes. Build/cache directories, `local.properties`, signing stores/properties, credentials, private app data, and workspace toolchain files are excluded. Synthetic test inputs are not real credentials. The existing APK and generated artwork outside the checkout were left intact. No reset, clean, uninstall, or data deletion was performed for this save.

## Complete checkpoint file inventory

Statuses: **A** new, **M** modified, **D** previously removed/replaced launcher resource. Paths are relative to the repository root.

| Status | File |
| --- | --- |
| A | `PROGRESS.md` |
| M | `README.md` |
| M | `app/build.gradle.kts` |
| A | `app/schemas/my.fuelledger.data.LedgerDatabase/2.json` |
| M | `app/src/main/AndroidManifest.xml` |
| A | `app/src/main/assets/licenses/material-symbols-LICENSE.txt` |
| A | `app/src/main/assets/licenses/material-symbols-NOTICE.txt` |
| A | `app/src/main/java/my/fuelledger/FinanceViewModel.kt` |
| M | `app/src/main/java/my/fuelledger/LedgerApplication.kt` |
| M | `app/src/main/java/my/fuelledger/LedgerViewModel.kt` |
| M | `app/src/main/java/my/fuelledger/MainActivity.kt` |
| M | `app/src/main/java/my/fuelledger/data/Backup.kt` |
| A | `app/src/main/java/my/fuelledger/data/BackupCrypto.kt` |
| M | `app/src/main/java/my/fuelledger/data/Database.kt` |
| A | `app/src/main/java/my/fuelledger/data/FinanceData.kt` |
| A | `app/src/main/java/my/fuelledger/data/FinanceRepository.kt` |
| M | `app/src/main/java/my/fuelledger/data/LedgerRepository.kt` |
| A | `app/src/main/java/my/fuelledger/data/ReceiptBackupArchive.kt` |
| M | `app/src/main/java/my/fuelledger/data/SettingsStore.kt` |
| M | `app/src/main/java/my/fuelledger/domain/Calculations.kt` |
| A | `app/src/main/java/my/fuelledger/finance/RecurringWorker.kt` |
| A | `app/src/main/java/my/fuelledger/receipts/ReceiptParser.kt` |
| A | `app/src/main/java/my/fuelledger/receipts/ReceiptScanner.kt` |
| A | `app/src/main/java/my/fuelledger/receipts/ReceiptStore.kt` |
| A | `app/src/main/java/my/fuelledger/security/AppLock.kt` |
| A | `app/src/main/java/my/fuelledger/security/LocalVault.kt` |
| A | `app/src/main/java/my/fuelledger/ui/AppLockSettings.kt` |
| M | `app/src/main/java/my/fuelledger/ui/Components.kt` |
| M | `app/src/main/java/my/fuelledger/ui/Editors.kt` |
| A | `app/src/main/java/my/fuelledger/ui/FinanceScreen.kt` |
| A | `app/src/main/java/my/fuelledger/ui/FintechTheme.kt` |
| M | `app/src/main/java/my/fuelledger/ui/LedgerApp.kt` |
| A | `app/src/main/java/my/fuelledger/ui/ReceiptAttachment.kt` |
| A | `app/src/main/java/my/fuelledger/ui/ScreenPreviews.kt` |
| M | `app/src/main/java/my/fuelledger/ui/Screens.kt` |
| M | `app/src/main/java/my/fuelledger/ui/SettingsScreen.kt` |
| A | `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` |
| D | `app/src/main/res/drawable/ic_launcher.xml` |
| A | `app/src/main/res/drawable/ic_launcher_background.xml` |
| A | `app/src/main/res/drawable/ic_launcher_monochrome.xml` |
| A | `app/src/main/res/drawable/symbol_account_balance_wallet.xml` |
| A | `app/src/main/res/drawable/symbol_add.xml` |
| A | `app/src/main/res/drawable/symbol_favorite.xml` |
| A | `app/src/main/res/drawable/symbol_home.xml` |
| A | `app/src/main/res/drawable/symbol_local_gas_station.xml` |
| A | `app/src/main/res/drawable/symbol_monitoring.xml` |
| A | `app/src/main/res/drawable/symbol_more_horiz.xml` |
| A | `app/src/main/res/drawable/symbol_movie.xml` |
| A | `app/src/main/res/drawable/symbol_receipt_long.xml` |
| A | `app/src/main/res/drawable/symbol_restaurant.xml` |
| A | `app/src/main/res/drawable/symbol_route.xml` |
| A | `app/src/main/res/drawable/symbol_settings.xml` |
| A | `app/src/main/res/drawable/symbol_shopping_bag.xml` |
| A | `app/src/main/res/drawable/symbol_shopping_cart.xml` |
| A | `app/src/main/res/drawable/symbol_speed.xml` |
| A | `app/src/main/res/drawable/symbol_tune.xml` |
| A | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` |
| A | `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml` |
| A | `app/src/test/java/my/fuelledger/data/ReceiptBackupArchiveTest.kt` |
| M | `app/src/test/java/my/fuelledger/data/RepositoryTest.kt` |
| M | `app/src/test/java/my/fuelledger/domain/CalculationsTest.kt` |
| A | `app/src/test/java/my/fuelledger/receipts/ReceiptParserTest.kt` |
| A | `app/src/test/java/my/fuelledger/receipts/ReceiptStoreTest.kt` |
| A | `app/src/test/java/my/fuelledger/security/AppLockSessionTest.kt` |
| M | `app/src/test/java/my/fuelledger/ui/AppSmokeTest.kt` |

Total: **65 changed files**, including this document.

</details>

</details>

</details>

</details>

</details>

</details>
