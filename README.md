# Fuel & Expense Tracker

A native Android app for personal spending and car fuel records. Kotlin, Jetpack Compose / Material 3, MVVM, Room and DataStore. Designed for offline use with no account, server, advertising or Internet permission. Structured records, settings and retained receipt photos are encrypted locally. Receipt selection uses the system picker without broad gallery permissions. See [SECURITY.md](SECURITY.md) and [VALIDATION.md](VALIDATION.md) for the architecture, dependency review and precise verification limits.

## Download the APK

[**Download Fuel & Expense Tracker for Android**](https://github.com/Piccol1001/ChatgptFuelConsumption/raw/refs/heads/main/downloads/Fuel-Expense-Tracker-v1.2.0-debug.apk)

Version 1.2.0, signed debug build, Android 8.0 or later. Open this link on your phone, download the `.apk` file, then open it to install. If Android asks, allow installation for the browser or file manager you used. The APK is also available in this repository's `downloads` folder. Its SHA-256 checksum is in `downloads/SHA256SUMS`; build and test evidence is in [VALIDATION.md](VALIDATION.md).

The launcher icon now combines a fuel pump and wallet in ivory and gold on teal. The installed app is named **Fuel & Expense Tracker v1.2.0**; the header and Settings show the same version. Android adaptive icons follow the launcher’s circle, squircle or rounded-square shape, and Android 13+ themed icons use a monochrome companion. Install this APK over version 1.0.0 to keep your records: the application ID and signing key are unchanged, and version code increases from 1 to 4.

## Install on your phone

Requires **Android 8.0 or later**. The default units are Malaysian Ringgit (MYR/RM), kilometres and litres. Dates are displayed in your phone's local time zone.

1. Build or download `app/build/outputs/apk/debug/app-debug.apk`.
2. Transfer the APK to your phone using USB, Bluetooth or a private file transfer service.
3. Open the APK with your phone's file manager.
4. When Android asks, allow **Install unknown apps** for that file manager or browser. Install, then turn that permission off again if desired.
5. Open **Fuel & Expense Tracker v1.2.0**. No Internet connection is needed for installation or use once the APK is on the phone.

A debug APK is already signed with a development key and is suitable for personal testing. A release APK is optimized and should be signed with **your own private key**. Updates must have the same application ID, the same signing key and a higher version code to retain data. Keep your signing key safe. If Android reports a signing mismatch, stop before uninstalling. **Create and test a complete encrypted backup first**; uninstalling removes local records and their Keystore keys. Keep the existing signing key for an in-place update.

## Build in Android Studio (beginner workflow)

1. Install a current stable Android Studio supporting Android Gradle Plugin 9.4.1.
2. Open this directory as a project. Use a complete JDK 21 for Gradle (Settings → Build Tools → Gradle → Gradle JDK).
3. In SDK Manager, install Android API **37.0**, Android SDK Build Tools and Platform Tools. Accept the SDK licenses.
4. Let Gradle sync. Dependency downloads need Internet access on this first setup; the app itself does not.
5. Select the `app` run configuration and a phone or emulator, then press Run.
6. To create the debug APK, use Build → Generate App Bundles or APKs → Generate APKs, or run the command below.

The project includes a Gradle wrapper, with the distribution checksum pinned. Do not install a separate system Gradle or change library versions just to build it.

```sh
# macOS/Linux, from this directory (use gradlew.bat on Windows)
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Configure `ANDROID_HOME` or let Android Studio create the ignored `local.properties` with `sdk.dir=/path/to/Android/Sdk`. Do not commit machine-specific paths. SDKs, downloaded dependencies and signing keys are outside source control.

## Using the app

- **Home:** a navy-and-emerald dashboard with a prominent monthly expenditure card, today/monthly-fuel cards, large quick actions and recent transactions. No fictional account balance is shown.
- **Fuel:** ODO-based per-fill efficiency estimates, consumption trend, monthly fuel costs, a separate trip calculator and compact refuelling history. Tap a refuel to view, edit, scan its receipt or delete it. Full-tank measured consumption remains available as an additional reference.
- **Expenses:** add/edit/delete transactions; choose a date range and category, search descriptions, and sort by newest, oldest or highest amount. Daily subtotals and the filtered range's daily average are shown. Highest sorts globally by amount; a date header can repeat when its transactions occur at different places in that ordering.
- **Analytics:** choose a month or a custom inclusive date range; see a category donut with labeled breakdown, daily bars, monthly trends, fuel history, month comparison and highest category. Long-range charts clearly cap the displayed daily detail to the latest 31 days and monthly detail to the latest 12 months. Values remain readable without relying on chart colours.
- **Settings** (gear button): set default petrol price and estimated consumption, choose System/Light/Dark mode, manage categories, income, budgets and recurring payments, configure local app lock, export CSV, and create/restore encrypted backups.

Each refuel automatically creates one expense in **Fuel & Transport**. Editing or deleting it updates or deletes that expense in the same SQLite transaction. Tap a linked expense to open its refuel editor. A manually added Fuel & Transport expense is independent; avoid entering a refuel's payment manually as well.

Enter the **ODO reading and litres purchased at every fill-up**, including partial fills. The first record establishes a baseline. Each later record shows distance since the preceding ODO reading, estimated km/L, L/100 km and cost/km using the current purchase's litres and payment. For example, 10,000 km followed by 10,500 km with 40 L gives a **12.50 km/L per-fill estimate**. Tank levels can vary, so this is explicitly an estimate rather than measured consumption. New entries default to partial fill; fuel type (RON95/RON97/Diesel/Other), station and station location are optional.

For the additional measured-consumption view, first enter a **full tank** as the baseline. Enter any partial refuels, then the next full tank. Measured fuel is the litres purchased **after** the baseline, including the final full tank. The first tank alone cannot measure consumption. Dates and odometers must increase strictly; existing records can be edited but cannot reverse that order. This app tracks **one vehicle**; mixing vehicles would invalidate measurements.

The estimator uses distance × consumption ÷ 100 and petrol price. The example 150 km, 7 L/100 km, RM2.05/L produces **10.50 L, RM21.53, RM0.1435/km and 14.29 km/L**. Estimated money uses decimal HALF_UP rounding; stored payments use exact integer sen. Overall measured efficiency is total valid-cycle km divided by total valid-cycle litres.

Week totals use Monday–Sunday. Calendar totals and date filters use the phone's local time zone, including daylight saving changes; timestamps are stored as epoch milliseconds. Changing the device time zone can change the calendar day a transaction appears under. Daily averages include all calendar days in the selected range, even days without transactions.

## Receipt photos and offline scanning

In Add/Edit Expense or Refuel, select **Choose receipt photo**. The app reads the original without changing it and retains a normalized, orientation-correct JPEG copy encrypted in private storage. You can view, replace or remove the attachment. No public receipt album is created.

**Scan & review receipt** runs the bundled Latin OCR model locally. Edit suggested merchant, total, date/time, category and applicable fuel details before applying selected fields to the form. ODO is suggested only when explicitly printed. Unit prices, tax and loyalty values are not treated as totals. Blurry or ambiguous receipts need manual correction; scanning never saves a transaction automatically. Choose the delete-after-extraction option if you do not want to retain the imported copy. Removing an existing saved photo takes effect when the transaction edit is saved; cancelling preserves the original record.

## Income, budgets and recurring payments

Open Settings → **Income, budgets & recurring expenses** to track income and set overall/category monthly budgets using local MYR records. Budget usage comes from actual expenses, including linked refuels. Income is kept separately from expenditure; no account balance is invented.

Recurring expenses support daily, weekly and monthly schedules. Occurrences and their next-date cursor commit together to prevent duplicates. Monthly schedules keep the original day through shorter months (for example, 31 January → 28/29 February → 31 March). Android can defer background work; local reconciliation catches up when the app next runs. Disabling/deleting a rule preserves existing transactions.

## Export and encrypted backup

Settings uses Android's document picker. Choose a private local destination. CSV is an **unencrypted** quoted UTF-8 spreadsheet export with exact MYR amounts and local timestamps; spreadsheet formula-like text is escaped. CSV cannot be restored.

A complete `.fuelbackup` export includes categories, expenses, refuels, income, budgets, recurring rules, preferences and retained receipt photos. Enter and confirm a password of 12–1024 characters. Tink streaming authenticated encryption protects the archive, with PBKDF2-HMAC-SHA256 (600,000 iterations) and a fresh salt. The password is never stored. **Losing it can make the backup unrecoverable.**

Restore requires the password and verifies the entire encrypted file, records and images before showing a preview. Confirming replaces the current dataset; export a backup first if needed. Cancel leaves current records unchanged. Older version-1 JSON and version-2 ZIP files have a separate, explicit unencrypted import action. Backups enforce bounded sizes (256 MB expanded, 16 MB metadata, 8 MB per photo) and validate filenames, relationships, odometer order and linked amounts. Staged photos/metadata stay encrypted privately; confirmed photos receive fresh names.

Automatic cloud backup and device transfer are excluded through Android backup rules. Saving an export to a cloud-synced document provider or sharing it with another app is outside this app's private-storage protection. Keep the backup password separately and test restore before relying on it. Deleting a custom category moves transactions/recurring rules to Others and combines matching category budgets after confirmation. Interrupted settings restoration is retried from a Room-committed intent.

## Local app lock

Settings offers optional fingerprint or device-credential authentication, configurable inactivity timeout, and immediate relocking after backgrounding. Sensitive windows/recent-app previews are protected. App lock is independent of encryption and needs no login or connection. Returning from a file picker may require unlocking again; forms remain behind the lock. Actual authentication support depends on the device and enrolled screen lock.

## Project structure

```text
app/
  build.gradle.kts                  Android module and dependencies
  schemas/                          Exported Room database schema
  src/main/
    AndroidManifest.xml             Launcher, offline permission and backup policy
    java/my/fuelledger/
      MainActivity.kt               Compose entry point
      LedgerApplication.kt          Application-scoped repository
      LedgerViewModel.kt            Observable UI state and file operations
      domain/Calculations.kt        Money, trip, full-tank and spending logic
      data/Database.kt              Room entities, relationships and DAO
      data/LedgerRepository.kt      Transactions and record synchronization
      data/SettingsStore.kt         Encrypted DataStore settings/migration
      data/Backup.kt                Backup validation and CSV escaping
      data/ReceiptBackupArchive.kt  Encrypted complete archives and legacy imports
      receipts/                    Encrypted private photos, bundled OCR and parsing
      security/                    Keystore vault, SQLCipher migration and app lock
      finance/                     Local recurring-payment worker
      ui/                           Screens, editors, charts, theme and receipt review
      ui/ScreenPreviews.kt         Ten dark/light/large-type/empty previews
    res/                            Native launcher icon and base theme
  src/test/                         Host calculation, Room, crypto and Compose tests
  src/androidTest/                  Native encryption/migration and offline OCR tests
buildSrc/                          Reviewed offline OCR telemetry build policy
gradle/wrapper/                     Verified Gradle wrapper
scripts/setup-cloud.sh              Repeatable cloud toolchain setup
build.gradle.kts                   Pinned build plugins
settings.gradle.kts                Standard Google and Maven repositories
```

The shared fintech design uses navy `#0B1220`, cards `#18263B`, emerald `#4DE0B1`, text `#F3F6FA` and muted text `#94A3B8`, with a complete light palette. Cards/actions stack on narrow phones and at larger font sizes. `FintechTheme.kt` centralizes colours, typography and shapes; `Components.kt` provides reusable cards/charts. Open `ScreenPreviews.kt` in Android Studio for previews without a database or ViewModel.

The UI reads state through `LedgerViewModel`. Writes go through `LedgerRepository`; Room exposes flows, so adding, editing and deleting records updates dashboards automatically. Heavy database and document operations run off the main thread. Calculation logic is separate from Android and is tested with JUnit. Room integration tests use Robolectric with an actual SQLite database.

Room is at schema version 3 and exports all three schemas. Additive migrations retain existing records and expense links; the encryption upgrade verifies a replacement before replacing a legacy plaintext database. Future database changes must increment the version and implement/test a migration. **Do not add destructive migration fallback**, which would erase records. Preferences belong in DataStore. Dark mode is the default for new preferences; saved Light/System/Dark choices are retained. Keep the stable application ID `my.fuelledger` for updates.

In this prepared cloud workspace, use `source /workspace/.tooling/fuel-ledger-env.sh` before running Gradle. To reproduce the full cloud setup, run `bash scripts/setup-cloud.sh` from this project. It downloads checksum-verified build tools outside the checkout, reuses the platform's proxy and certificate trust, accepts SDK licenses and runs the tests, debug build and lint. This script is specifically for the cloud `/workspace` layout; use the Android Studio instructions on your own computer.

## Signed release APK

The easiest path is Android Studio → Build → Generate Signed App Bundle / APK → APK. Create or choose a keystore **outside this repository**, choose `release`, and keep the key and passwords private. Studio can sign the APK without adding passwords to project files.

For command-line signing:

```sh
./gradlew :app:assembleRelease
# Create a key outside the repository; keytool prompts for passwords.
keytool -genkeypair -v -keystore /private/path/fuel-ledger.jks \
  -alias fuel-ledger -keyalg RSA -keysize 3072 -validity 10000

# Use the binaries in your SDK's build-tools directory.
zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk /private/path/fuel-ledger-aligned.apk
apksigner sign --ks /private/path/fuel-ledger.jks --ks-key-alias fuel-ledger \
  --out /private/path/fuel-ledger-release.apk /private/path/fuel-ledger-aligned.apk
apksigner verify --verbose /private/path/fuel-ledger-release.apk
```

Never commit keystores, passwords or `signing.properties`. Increase `versionCode` and update `versionName` in `app/build.gradle.kts` for each installed update. Back up your data before updating.

## Validation and limitations

See [VALIDATION.md](VALIDATION.md) for the actual commands and outcomes from this build. Automated checks exercise the specified trip example, exact currency, daily/monthly/category aggregation, timezone and year boundaries, partial fills, edits/deletions, linked expenses, invalid input, CSV and backup restoration.

This is a personal, single-vehicle, MYR app. It does not include multi-vehicle tracking or CSV import. Release signing requires your own key. Before relying on it for irreplaceable data, verify the app and backup/restore on your phone and keep regular backups. A compiled APK is not a claim of physical-device testing.
