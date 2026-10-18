# Android project audit and cleanup

18 October 2026, Malaysia. Baseline: `233d43d8c831514740a6c4f983202cc2afa3549e`. Protected checkpoint: `checkpoint/pre-audit-2026-10-18`. The working tree was clean before inspection. [Original request](docs/audit-2026-10-18/REQUEST.md).

## Scope and files reviewed

The audit covered the whole tracked repository, rather than only the latest diff: **291 files**. [Complete inventory with baseline sizes and SHA256](docs/audit-2026-10-18/REVIEW_INVENTORY.tsv). Categories: 62 production Kotlin files; 36 host-test/helper files; 2 native-test files; 5 Room schemas; 37 manifest/resource/license files; 3 build-policy files; 14 build/release tooling files; 118 documentation/reference-render files; 14 release artifacts/metadata files.

Repository-wide declaration/reference, resource/manifest, suspicious-file, logging/network, coroutine/lifecycle and build-configuration scans were combined with code review of the entry point, ViewModels, repositories, transactions, migrations, money/fuel/planning utilities, scanner and backup/security boundaries. Existing test assertions were reviewed against these paths. Static references are evidence, not proof of every possible runtime behavior; reflection, framework entry points, serialization and generated Room code were considered before removal. This is a code/automated QA audit, not a guarantee that every device/provider interaction is fault-free.

## Deleted file and removed content

| Item | Evidence and reason |
| --- | --- |
| `app/proguard-rules.pro` | Its entire content was one explanatory comment. No rules were defined. Remove its build reference; retain Android's default optimized configuration and dependency consumer rules. |
| Generated `app_name` and `app_version` string entries in `app/build.gradle.kts` | No Kotlin/XML/runtime lookup references. Installed launcher naming uses retained `app_display_name`; About reads PackageManager. Neither application ID nor installed version naming changes. |
| 19 explicit imports across 10 Kotlin files | No usage after excluding import declarations; compilation verifies removal. Delegated `getValue`/`setValue` operators were retained. [Exact inventory](docs/audit-2026-10-18/REMOVED_IMPORTS.json). |

No Kotlin source file, icon, image, migration, entity, test, license or intentionally retained APK was deleted. No accidental tracked caches/logs/backup copies were found. `buildSrc/.../build` is an active Java package, not a disposable build directory. Local build caches are already ignored and were retained for reproducibility/verification.

## Dependencies removed

- `androidx.room:room-ktx:2.8.5`: empty compatibility AAR; coroutine APIs are in retained `room-runtime:2.8.5`.
- `androidx.work:work-runtime-ktx:2.12.0`: empty compatibility AAR; use retained/referenced `work-runtime:2.12.0` directly.

Both inspected `classes.jar` archives contained only their `META-INF/...version` marker, no classes. There is no promised APK-size saving from removing empty artifacts. No dependency version was upgraded. Compose/compiler/KSP/serialization plugins are active; SQLCipher, Tink, bundled ML Kit, Fragment/Biometric, DataStore, EXIF and Material icons have actual runtime/build uses and remain.

## Duplicate logic and performance changes

- Reuse the same private numeric fuel-point validator in sequence validation, provisional intervals and supplementary full-cycle history. Formulas, accepted bounds and failure messages stay unchanged.
- Read allocation category IDs once per save, instead of querying inside each category membership check.
- Read saved journeys once per transactional edit, instead of twice for link uniqueness and prior links.
- Precompute fixed-expense IDs for monthly allowance/refund filtering and financial reports. Membership sets replace repeated linear searches without changing cross-period refund semantics.
- Preserve the existing feature packages, shared screen margins/theme/cards, press-feedback adapters, remembered computations, lazy containers and stable pager keys. No package reshuffle, screen resizing, global scale, new architecture or animation change was justified.
- Ignore disposable `.DS_Store` and heap dumps in addition to existing build, Python, IDE and signing exclusions.

## Verified defects and fixes

1. **Undo could over-refund a purchase.** A RM10 purchase edited to RM20 could receive a RM15 refund; Undo then restored RM10 while retaining RM15 refunded. A new API26/35 repository regression failed before the fix. Undo now checks all current linked refunds against the proposed restored amount inside its transaction and rejects conflicting restoration without consuming the Undo entry. After resolving the refund, the same Undo succeeds.
2. **A refund could pay a fixed commitment through the repository.** The visible picker already limits choices, but the repository accepted a refund ID and stored invalid recurring metadata. A new planner regression failed before the fix. The repository now requires an ordinary Expense and validates the copied linked record before writing. Rejection preserves the refund and leaves the commitment unpaid.
3. **Native launch verification was incomplete.** The old native suite tested encrypted storage without launching the production Activity. Added `ProductionStartupTest`, restricted to an isolated Android user, checks airplane mode, actual readiness/categories, screenshot flags and ViewModel identity through Activity recreation. The initial ActivityScenario harness timed out waiting for an idle event queue; direct lifecycle observation avoids that harness assumption while retaining actual readiness assertions. On baseline 1.5.1 the replacement test passed in 129.298s. A later standalone launch also visibly reached Overview with the calendar and navigation; [actual baseline capture](docs/audit-2026-10-18/baseline-native-home-api26.png). That duration is a software-emulator concern, not a claimed startup optimization or a proven explanation of every previous loading observation.

The three new financial regression executions failed before the guards and pass after them. No failing assertion was disabled.

## Feature and data safety review

| Existing feature | Review and regression coverage |
| --- | --- |
| Home, Expenses, income, Calendar | Local date/zone rollovers, actual spending, refund/category history, exact sen and historical payment strings; existing host UI/repository suites. |
| Fuel and Journey | Consecutive valid ODO intervals, later-fill litres, weighted volume/distance, provisional assumptions; old fields/full-cycle display retained; Journey formulas/legacy JSON and route form preserved. |
| Analysis, monthly planner and recurring bills | Fixed versus variable spending, planned reserves, allocation rounding/100% validation, manual links, recurrence cursor/unique occurrence index and reports; new rejection tests. |
| Scanner and attachments | Bundled Latin ML Kit model, local parsing/category mappings, ambiguity/duplicate checks, petrol review, private images and Undo retained. |
| Widgets and navigation | Three manifest receivers, serialized updates, stable five-page pager/ViewModels, child gesture ownership and detail/form navigation retained. |
| Appearance, app lock and screenshots | Persistent System/Light/Dark preference, independent Settings writes, reduced motion/haptics, lock/timeout behavior and screenshot allowance retained. |
| Encrypted backup/export | SQLCipher/Keystore, Tink authenticated streaming through EOF, bounded/validated staging, CSV injection protection, PDF and legacy backup import retained. |

Room remains **schema5**. Schemas1–5, migration1→2→3→4→5, serialized field names, IDs, hidden historical fields and backup versions are unchanged. No destructive fallback or cleanup migration was added. All manual device checks use an isolated test user; no owner database, app data or original gallery images are cleared. Tests demonstrate fixture safety; unavailable physical-phone records cannot be directly certified.

## Offline/security findings

Source has no production financial logging, HTTP client, cloud sync, Firebase Analytics/Crashlytics or Internet permission. Manifest actively removes network permissions; automatic backup and device-transfer rules exclude local data. The external camera/document/photo-picker integrations and three widget receivers are active, not unused files. Screenshot permission intentionally permits capture by prior request; app-lock and encryption remain.

Bundled OCR's reviewed telemetry sinks remain build-time no-ops; transport runtime/CCT artifacts are excluded, and the policy verifies pinned sink shapes. Removing that policy would weaken offline/privacy guarantees. Default backups are password-encrypted; no key/password is included in Git or this report. Captured test images use empty/sample profiles.

## Intentionally retained and uncertain items

- Every historical APK and dated release/render/progress/validation record: expressly retained history. They dominate repository size; deletion would violate the request.
- Compose previews and private sample fixtures: Android Studio tooling entry points; samples never enter production storage. They are not abandoned runtime screens.
- Manifest receivers, worker, provider, Room declarations/schema JSON, serialization fields and licenses: framework/generated/reflection uses.
- Shared interaction wrappers with Material-like names: deliberate existing application-wide ripple/semantics policy. Existing regression tests guard it.
- Legacy payment values, full-tank flags, hidden Journey/refuel fields, old backup readers and dormant dashboard preferences: historical compatibility. Absence from a visible control does not establish safe deletion.
- Large screen/ViewModel files and broad wildcard imports: potential gradual maintainability work, not evidence of dead code. No cosmetic package rewrite performed.
- SQLite/KTX suggestions and deprecated test APIs require separate dependency/API migration review. Version upgrades are outside this safe cleanup.

## Build, QA and release results

Pre-release compilation, debug APK and instrumentation APK builds pass. All **309 host tests** across 35 suites pass with zero failures/errors/skips; **6 release-workflow tests** pass. Optimized release build and lint pass: **0 errors, 4 warnings, 1 hint**, down from 10 warnings. Binary package/version/signature, original-certificate match, 16KiB alignment and no-network policy pass. On this exact1.5.2 APK, all nine native OCR/financial/backup/encryption/migration cases passed. The additional startup case timed out at its first-state30s budget in the combined run (10 cases, one failure;442.941s). With the first-state budget aligned to readiness at120s, a separate startup/readiness/recreation follow-up passed (`OK (1 test)`,125.352s) on the same APK. The failure and retest are recorded separately; no all-ten-in-one-pass claim is made for1.5.2. This does not establish physical-device startup performance. Four build-policy Java tests were freshly rerun and pass. No version is advanced for a failed check. The two verified financial fixes justify a PATCH release through the existing same-certificate workflow if all release gates pass.

Audit artifact: `downloads/FuelExpenseTracker_v1.5.2.apk`, version **1.5.2/code9**, 76,258,773 bytes, SHA256 `23c98bd572940d241fc730760e201ca05095f8223c57e981913d977978ff6c3d`. Signed debug with the original certificate; package `my.fuelledger`. No previous artifact was overwritten. Source checkpoint `48e3c51`. A subsequent CLARITY UI refinement is a separate phase, not part of these audit changes.
