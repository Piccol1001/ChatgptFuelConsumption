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
