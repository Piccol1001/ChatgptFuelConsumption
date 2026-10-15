# Offline storage and security

This document describes the 1.2.0 implementation. Actual build/device results belong in `VALIDATION.md`; source inspection alone is not evidence of working security on every phone.

## Local data

- Room preserves the existing categories, expenses, refuels, and settings intent. Schema 1 → 2 adds optional refuel metadata; schema 2 → 3 adds receipt/recurrence references and local income, budgets, and recurring-payment tables. Schema 3 → 4 adds planning snapshots, allocations, journeys, merchant rules, receipt fingerprints, a unique recurring-occurrence index and additive recurrence metadata. No destructive migration is used.
- Production Room opens with maintained SQLCipher. A random database password is wrapped by an AES-256-GCM key generated in Android Keystore. Wrapped keys live under `noBackupFilesDir`; the master key is not exported.
- An existing plaintext database is checkpointed and exported directly to an encrypted temporary database. Integrity, version, tables, and record counts are checked before a same-directory atomic replacement. An encryption failure must preserve the existing records and report an error.
- Settings, lock preferences, and retained receipt copies use the platform AES-GCM primitive with fresh nonces and authenticated file purposes. Settings migration removes old preferences only after committing encrypted replacements.
- Receipt originals remain in their original gallery location. Imported copies are normalized and encrypted in private storage. OCR, previews and image adjustments decrypt into memory. Camera capture delegates to the device camera app using a non-exported, grant-only FileProvider limited to `cache/receipt-capture`; this temporary image is plaintext in private cache until imported and removed. Abandoned captures are cleaned after 24 hours at startup. Persistent imported/retained copies are encrypted. Removing an existing attachment takes effect when its edited transaction is saved. Deleting extracted/new copies must not delete a still-saved reference.
- Production never silently falls back to a plaintext database or resets an unreadable store. JVM tests use an explicitly separate test application with a test key and plaintext Room; that class is not packaged in the application.

## Offline OCR and dependencies

The app uses **bundled** `com.google.mlkit:text-recognition:16.0.1`. Its Latin model is packaged in the APK. The application's OCR path never requests a remote model. Google's bundled artifact transitively includes shared `play-services-mlkit-*` code; that name alone does not mean the selected model is downloaded.

An audit found three Firelog transport sinks and one Play Services telemetry sink in the pinned ML Kit shared code. `buildSrc/.../OfflineMlKitTelemetry.java` removes their transport initialization and dispatch bodies at build time for both debug and release. The `transport-runtime` and `transport-backend-cct` modules are excluded entirely. The small transport **API** interfaces and ML Kit's component/encoder utilities remain for binary compatibility; they are not a Firebase account, database, analytics service, or network backend.

`verifyOfflineOcrPolicy` verifies that all four reviewed classes/signatures exist in the resolved SDK artifacts and fails if their shapes change. Any ML Kit upgrade requires reviewing this policy and rerunning fresh-install offline recognition. The application manifest also explicitly removes Internet and network-state permissions introduced by dependencies. Final packaged permissions and services must be inspected after each dependency change.

| Direct runtime dependency | Purpose |
| --- | --- |
| AndroidX Compose / Material 3 / Activity / Lifecycle | Local interface, navigation state, lifecycle, accessibility |
| Room 2.8.5 / SQLite 2.6.2 | Local structured records and migrations |
| SQLCipher Android 4.19.1 | Encrypted SQLite storage |
| DataStore 1.2.1 | Local settings with encrypted serializer |
| ExifInterface 1.4.2 | Correct imported image orientation |
| ML Kit Text Recognition 16.0.1 | Bundled on-device Latin receipt recognition |
| Tink Android 1.23.0 | Maintained streaming authenticated manual-backup encryption |
| Biometric 1.1.0 / Fragment 1.9.1 | Device authentication and credential fallback |
| WorkManager 2.12.0 | Local recurring-payment scheduling; no network constraint |
| Coroutines 1.11.0 / Kotlin serialization 1.11.0 | Local asynchronous work and validated backup data |

There is no app cloud database, account, advertising SDK, external API client, remote crash reporter, remote AI integration, or app-written network request. Development-time Gradle downloads are separate from installed-app operation.

## Manual exports and restore

The complete `.fuelbackup` export uses Tink AES-256-GCM-HKDF streaming authenticated encryption. A fresh 32-byte salt and PBKDF2-HMAC-SHA256 with 600,000 iterations derive a key from the user's password. The fixed versioned header is authenticated; untrusted files cannot request arbitrary password-derivation cost. All authenticated segments, including the final segment, must be verified before displaying a restore preview.

Passwords are requested for each operation, not persisted, and character arrays are cleared after use. Lost passwords cannot be recovered. Restore previews use encrypted private staging with bounded metadata/image/expanded-size limits. Records, references, and images are validated before confirmation. Restored photos receive fresh names, preserving existing files if a database restore fails.

CSV exports and explicitly imported older JSON/ZIP backups are plaintext formats. Choose a local destination deliberately. Saving any export to a cloud-synced document provider, sharing it, or retaining the original gallery photo is outside the app's private-storage protection. The app does not integrate a cloud storage provider.

## Lock, scheduling, and platform backup

Optional app lock uses AndroidX BiometricPrompt or the device credential on older supported Android versions. It must relock after configured inactivity and backgrounding while retaining entry state behind protected UI. `FLAG_SECURE` protects app windows/recent-app previews. Locking and data encryption are separate controls; local scheduled payments still work without authenticating in a foreground session.

Recurring payments use local dates/time zones, deterministic occurrence IDs, and transactional record/cursor updates. Reconciliation runs at launch and through local WorkManager work. Android may defer background work; reconciliation catches up when the app runs again.

`allowBackup=false` and explicit pre-Android-12 backup / Android-12+ cloud-and-device-transfer exclusions cover root, files, databases, preferences, external, and device-protected domains. Android Keystore keys are not included in manual backups; portable backups are instead protected by the separate password. Manufacturer transfer behavior remains a platform limitation requiring device-specific verification.

## Practical limits

- This is not protection against an already-compromised/rooted operating system, an unlocked authenticated session, malicious keyboards/accessibility services, or someone who knows the backup password.
- Plaintext legacy data can be removed only after a verified replacement. Flash storage does not provide guaranteed secure erasure of historical blocks.
- Sensitive values and password text can exist transiently in process memory. Clearing mutable buffers reduces retention; it cannot guarantee erasure of every framework/string copy.
- Uninstalling/clearing app data or losing the device Keystore can make local encrypted records unrecoverable. Keep a tested password-encrypted backup and retain its password separately.
- Debug builds are debuggable. The repository's downloadable debug APK is for installation/testing; a distribution release needs an appropriate private signing key kept outside Git.
- Airplane-mode OCR, migration, restart persistence, credential authentication, hardware-backed key storage, recent-app rendering, and device-transfer behavior must only be called verified where an actual test was performed. See `VALIDATION.md` for the precise evidence and remaining limits.

## Planning and receipt automation in 1.3.0

Allocation percentages, month snapshots and saved journeys are planning records in encrypted Room; none posts actual transactions. Expected income is kept separate from actual income. Confirmed fixed payments link an existing expense or create one occurrence atomically, with a unique `(recurringRuleId, recurringDate)` constraint. Existing automatic recurrence remains supported and consults those same links.

Receipt auto-save defaults off. A local contrast/sharpness gate and deterministic parsing/validation require review for weak, ambiguous, unsupported-currency, petrol or duplicate candidates. Auto-save additionally verifies the currently stored mode, recognized merchant/amount/date and merchant override inside the save operation. Exact normalized OCR fingerprints are unique; potential semantic duplicates require review. Expense insertion, fingerprint and optional merchant mapping commit in one Room transaction. Undo checks the unchanged transaction before deleting it and its fingerprint. Fingerprints are hashes of normalized OCR text; raw OCR text is not retained in the database. OCR confidence rules cannot guarantee a perfect result.

The only newly requested runtime permission is `POST_NOTIFICATIONS`, offered on Android 13+ after optional local planning reminders are enabled. There is no camera permission, storage permission, Internet permission, geocoding, toll API or remote categorization. Camera content is shared only with the user-selected device camera app. Gallery/document providers and camera apps are controlled by Android and may have their own privacy behavior outside this app.
