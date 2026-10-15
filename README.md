# Fuel & Expense Tracker

A native Android app for personal spending and car fuel records. Kotlin, Jetpack Compose / Material 3, MVVM, Room and DataStore. All app functions work offline. There is no account, server, advertising, tracking, Internet permission or storage permission.

## Download the APK

[**Download Fuel & Expense Tracker for Android**](https://github.com/Piccol1001/ChatgptFuelConsumption/raw/refs/heads/main/downloads/Fuel-Expense-Tracker-debug.apk)

Version 1.0.0, signed debug build, Android 8.0 or later. Open this link on your phone, download the `.apk` file, then open it to install. If Android asks, allow installation for the browser or file manager you used. The APK is also available in this repository's `downloads` folder. Its SHA-256 checksum is in `downloads/SHA256SUMS`; build and test evidence is in [VALIDATION.md](VALIDATION.md).

## Install on your phone

Requires **Android 8.0 or later**. The default units are Malaysian Ringgit (MYR/RM), kilometres and litres. Dates are displayed in your phone's local time zone.

1. Build or download `app/build/outputs/apk/debug/app-debug.apk`.
2. Transfer the APK to your phone using USB, Bluetooth or a private file transfer service.
3. Open the APK with your phone's file manager.
4. When Android asks, allow **Install unknown apps** for that file manager or browser. Install, then turn that permission off again if desired.
5. Open **Fuel & Expense Tracker**. No Internet connection is needed for installation or use once the APK is on the phone.

A debug APK is already signed with a development key and is suitable for personal testing. A release APK is optimized and should be signed with **your own private key**. Updates must have the same application ID, the same signing key and a higher version code to retain data. Keep your signing key safe. A different signing key requires uninstalling the old app: **export a complete backup first**, then restore it after installing. Uninstalling removes local data.

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

- **Home:** today's and monthly expenditure, monthly fuel spending, quick add buttons and recent transactions.
- **Fuel:** immediately updated trip estimate, measured full-tank efficiency, monthly fuel chart and refuelling history. Tap a refuel to view, edit or delete it.
- **Expenses:** add/edit/delete transactions; choose a date range and category, search descriptions, and sort by newest, oldest or highest amount. Daily subtotals and the filtered range's daily average are shown. Highest sorts globally by amount; a date header can repeat when its transactions occur at different places in that ordering.
- **Analytics:** choose a month with the arrows; see category shares, daily bars, monthly trends, comparison with the previous month and highest category. Scroll the daily chart horizontally on small phones. Amounts are labeled for accessibility.
- **Settings** (gear button): set default petrol price and estimated consumption, choose System/Light/Dark mode, manage custom categories, export CSV, back up and restore.

Each refuel automatically creates one expense in **Fuel & Transport**. Editing or deleting it updates or deletes that expense in the same SQLite transaction. Tap a linked expense to open its refuel editor. A manually added Fuel & Transport expense is independent; avoid entering a refuel's payment manually as well.

For actual consumption, first enter a **full tank** as the baseline. Enter any partial refuels, then the next full tank. Measured fuel is the litres purchased **after** the baseline, including the final full tank. The first tank alone cannot measure consumption. Dates and odometers must increase strictly; existing records can be edited but cannot reverse that order. This app tracks **one vehicle**; mixing vehicles would invalidate measurements.

The estimator uses distance × consumption ÷ 100 and petrol price. The example 150 km, 7 L/100 km, RM2.05/L produces **10.50 L, RM21.53, RM0.1435/km and 14.29 km/L**. Estimated money uses decimal HALF_UP rounding; stored payments use exact integer sen. Overall measured efficiency is total valid-cycle km divided by total valid-cycle litres.

Week totals use Monday–Sunday. Calendar totals and date filters use the phone's local time zone, including daylight saving changes; timestamps are stored as epoch milliseconds. Changing the device time zone can change the calendar day a transaction appears under. Daily averages include all calendar days in the selected range, even days without transactions.

## Export and backup

Use Settings and Android's system document picker. Select a local folder for fully offline operation. CSV contains quoted UTF-8 fields, exact MYR amounts, local timestamps with offsets and link IDs. Text that could become a spreadsheet formula is escaped. CSV is an export format, not a restore format.

A complete JSON backup includes categories, expenses, refuels and preferences. Restore validates the format version, amounts, IDs, foreign keys, odometer order and fuel links before offering confirmation. Confirmation **replaces** the current dataset; merging is intentionally not offered. Cancel leaves current data unchanged. Export your current backup before replacement. No data is silently deleted when removing a custom category: its expenses move to Others after confirmation.

Backups are not encrypted: keep them in a private folder. Automatic Android cloud backup is disabled. Backups are capped at 16 MB, 100,000 expenses, 100,000 refuels and 500 categories to prevent unbounded imports. Built-in category definitions are protected. If restoring DataStore settings is interrupted, a settings intent committed in Room is replayed on the next launch.

## Project structure

```text
app/
  build.gradle.kts                  Android module and dependencies
  schemas/                          Exported Room database schema
  src/main/
    AndroidManifest.xml             Launcher; no app permissions
    java/my/fuelledger/
      MainActivity.kt               Compose entry point
      LedgerApplication.kt          Application-scoped repository
      LedgerViewModel.kt            Observable UI state and file operations
      domain/Calculations.kt        Money, trip, full-tank and spending logic
      data/Database.kt              Room entities, relationships and DAO
      data/LedgerRepository.kt      Transactions and record synchronization
      data/SettingsStore.kt         DataStore preferences
      data/Backup.kt                Backup validation and CSV escaping
      ui/                           Screens, editors, charts and theme
    res/                            Native launcher icon and base theme
  src/test/                         Calculation and Room integration tests
gradle/wrapper/                     Verified Gradle wrapper
scripts/setup-cloud.sh              Repeatable cloud toolchain setup
build.gradle.kts                   Pinned build plugins
settings.gradle.kts                Standard Google and Maven repositories
```

The UI reads state through `LedgerViewModel`. Writes go through `LedgerRepository`; Room exposes flows, so adding, editing and deleting records updates dashboards automatically. Heavy database and document operations run off the main thread. Calculation logic is separate from Android and is tested with JUnit. Room integration tests use Robolectric with an actual SQLite database.

Room is at schema version 1 and exports its schema. Future database changes must increment the version and implement/test a migration. **Do not add destructive migration fallback**, which would erase records. Preferences belong in DataStore. Keep the stable application ID `my.fuelledger` for updates.

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

This is a personal, single-vehicle, MYR app. It does not include multi-vehicle tracking, budget targets or CSV import. Release signing requires your own key. Before relying on it for irreplaceable data, verify the app and backup/restore on your phone and keep regular backups. A compiled APK is not a claim of physical-device testing.
