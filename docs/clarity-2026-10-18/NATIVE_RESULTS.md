# Native Android acceptance and binary provenance

All checks use API26 `emulator-5556` with airplane mode1 before first use, in isolated temporary Android users. Owner0 data is never cleared or uninstalled. Recorded installed APK SHA256 values are compared against the actual immutable download files; a filename alone is not version evidence.

| APK | Native outcome | Meaning |
| --- | --- | --- |
|1.5.2/code9|Combined10 cases:9 passed,1 startup first-state timeout;442.941s|The initial30s first-state budget was insufficient. No all-ten pass is claimed for this artifact.|
|1.5.2/code9|Separate startup/readiness/recreation follow-up passed;125.352s|First-state budget aligned with readiness at120s. Earlier failure retained.|
|1.5.3/code10|**OK (10 tests)**;463.989s|Complete offline/financial/backup/security/migration/production-startup suite on this exact APK.|
|1.5.4/code11|**OK (4 tests)**;154.001s|Exact-APK offline OCR, permissions, production startup/recreation and encrypted persistence. Full316-test host gate also reran.|

The ten passed1.5.3 cases are:

- `my.fuelledger.OfflinePrivacyTest#bundledReceiptModelRecognizesTextWithoutAnyNetwork`
- `my.fuelledger.OfflinePrivacyTest#packagedApplicationHasNoNetworkOrPublicStoragePermission`
- `my.fuelledger.OfflinePrivacyTest#financialRecordsRecurringPaymentsAndEncryptedPhotoBackupWorkOffline`
- `my.fuelledger.ProductionStartupTest#freshOfflineActivityReachesLedgerAndSurvivesRecreation`
- `my.fuelledger.security.EncryptedStorageTest#keystoreMasterIsNonExportableAndPurposeBoundAcrossVaultInstances`
- `my.fuelledger.security.EncryptedStorageTest#damagedPlaintextSourceFailsWithoutDeletingOrReplacingOriginal`
- `my.fuelledger.security.EncryptedStorageTest#plaintextVersionsOneThroughFourBecomeEncryptedVersionFiveWithoutLosingRecords`
- `my.fuelledger.security.EncryptedStorageTest#freshDatabasePersistsEncryptedRecordsWithKeystoreWrappedKey`
- `my.fuelledger.security.EncryptedStorageTest#incompleteMigrationCandidateIsPreservedIfOriginalIsMissingOrEmpty`
- `my.fuelledger.security.EncryptedStorageTest#missingWrongWrappingKeyAndWrongDatabaseKeyNeverReplaceExistingRecords`

[1.5.3 complete instrumentation output](native/v1.5.3-instrumentation.txt) and [installed metadata/hash](native/v1.5.3-installed-metadata.json). Historical1.5.2 failure and follow-up output are retained in [audit native evidence](../audit-2026-10-18/native/).

The final four-case follow-up exercises first-use bundled OCR, actual packaged permissions, real production Activity/ViewModel readiness/recreation, and SQLCipher/Keystore persistence. It is not presented as a rerun of all ten cases. No financial/storage code changed after the ten-case1.5.3 pass.

The startup test launches the production Application/Activity and waits for actual readiness and default categories, checks FLAG_SECURE is absent, recreates the Activity and verifies identical retained ViewModel identity. It uses explicit bounded waits rather than requiring an animated loading screen to become idle. Software emulation remains slow: successful readiness is not a startup-performance improvement, physical-device benchmark or a proven diagnosis of every earlier loading/ANR observation.

Physical camera/gallery, receipt lighting/skew/thermal-paper variability, external share/document providers, OEM widgets, TalkBack, physical IME, sustained performance and native Android15 edge gestures on a reliable device remain manual verification limits. Host UI/gesture/widget tests and native model/storage checks are distinct.

Final evidence: [1.5.4 instrumentation output](native/v1.5.4-instrumentation.txt), [installed version/code/hash](native/v1.5.4-installed-metadata.json). The [actual ready Home screenshot](native/v1.5.4-home-api26.png) was captured after standalone launch and visually inspected; it contains an empty fixture ledger, not owner data. [Capture configuration](native/v1.5.4-home-api26.json). Fixture15 was then removed; active owner0 and airplane mode1 were confirmed, with only owner0 remaining. [Cleanup evidence](native/device-cleanup.json). The earlier1.5.3 fixture14 was also removed before this final run.
