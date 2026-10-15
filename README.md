# Fuel & Expense Tracker

A native Android app for personal spending and car fuel records. Kotlin, Jetpack Compose / Material 3, MVVM, Room and DataStore. All app functions work offline. There is no account, server, advertising, tracking or Internet permission. Selecting a receipt photo needs no broad gallery access; Android 8–9 asks for storage permission only when you explicitly export a receipt copy to the gallery.

## Download the APK

[**Download Fuel & Expense Tracker for Android**](https://github.com/Piccol1001/ChatgptFuelConsumption/raw/refs/heads/main/downloads/Fuel-Expense-Tracker-debug.apk)

Version 1.1.0, signed debug build, Android 8.0 or later. Open this link on your phone, download the `.apk` file, then open it to install. If Android asks, allow installation for the browser or file manager you used. The APK is also available in this repository's `downloads` folder. Its SHA-256 checksum is in `downloads/SHA256SUMS`; build and test evidence is in [VALIDATION.md](VALIDATION.md).

The launcher icon now combines a fuel pump and wallet in ivory and gold on teal. The installed app is named **Fuel & Expense Tracker v1.1.0**; the header and Settings show the same version. Android adaptive icons follow the launcher’s circle, squircle or rounded-square shape, and Android 13+ themed icons use a monochrome companion. Install this APK over version 1.0.0 to keep your records: the application ID and signing key are unchanged, and version code increases from 1 to 3.

## Install on your phone

Requires **Android 8.0 or later**. The default units are Malaysian Ringgit (MYR/RM), kilometres and litres. Dates are displayed in your phone's local time zone.

1. Build or download `app/build/outputs/apk/debug/app-debug.apk`.
2. Transfer the APK to your phone using USB, Bluetooth or a private file transfer service.
3. Open the APK with your phone's file manager.
4. When Android asks, allow **Install unknown apps** for that file manager or browser. Install, then turn that permission off again if desired.
5. Open **Fuel & Expense Tracker v1.1.0**. No Internet connection is needed for installation or use once the APK is on the phone.

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

- **Home:** a navy-and-emerald dashboard with a prominent monthly expenditure card, today/monthly-fuel cards, large quick actions and recent transactions. No fictional account balance is shown.
- **Fuel:** ODO-based per-fill efficiency estimates, consumption trend, monthly fuel costs, a separate trip calculator and compact refuelling history. Tap a refuel to view, edit, scan its receipt or delete it. Full-tank measured consumption remains available as an additional reference.
- **Expenses:** add/edit/delete transactions; choose a date range and category, search descriptions, and sort by newest, oldest or highest amount. Daily subtotals and the filtered range's daily average are shown. Highest sorts globally by amount; a date header can repeat when its transactions occur at different places in that ordering.
- **Analytics:** choose a month or a custom inclusive date range; see a category donut with labeled breakdown, daily bars, monthly trends, fuel history, month comparison and highest category. Long-range charts clearly cap the displayed daily detail to the latest 31 days and monthly detail to the latest 12 months. Values remain readable without relying on chart colours.
- **Settings** (gear button): set default petrol price and estimated consumption, choose System/Light/Dark mode, manage custom categories, export CSV, back up and restore.

Each refuel automatically creates one expense in **Fuel & Transport**. Editing or deleting it updates or deletes that expense in the same SQLite transaction. Tap a linked expense to open its refuel editor. A manually added Fuel & Transport expense is independent; avoid entering a refuel's payment manually as well.

Enter the **ODO reading and litres purchased at every fill-up**, including partial fills. The first record establishes a baseline. Each later record shows distance since the preceding ODO reading, estimated km/L, L/100 km and cost/km using the current purchase's litres and payment. For example, 10,000 km followed by 10,500 km with 40 L gives a **12.50 km/L per-fill estimate**. Tank levels can vary, so this is explicitly an estimate rather than measured consumption. New entries default to partial fill; fuel type (RON95/RON97/Diesel/Other), station and station location are optional.

For the additional measured-consumption view, first enter a **full tank** as the baseline. Enter any partial refuels, then the next full tank. Measured fuel is the litres purchased **after** the baseline, including the final full tank. The first tank alone cannot measure consumption. Dates and odometers must increase strictly; existing records can be edited but cannot reverse that order. This app tracks **one vehicle**; mixing vehicles would invalidate measurements.

The estimator uses distance × consumption ÷ 100 and petrol price. The example 150 km, 7 L/100 km, RM2.05/L produces **10.50 L, RM21.53, RM0.1435/km and 14.29 km/L**. Estimated money uses decimal HALF_UP rounding; stored payments use exact integer sen. Overall measured efficiency is total valid-cycle km divided by total valid-cycle litres.

Week totals use Monday–Sunday. Calendar totals and date filters use the phone's local time zone, including daylight saving changes; timestamps are stored as epoch milliseconds. Changing the device time zone can change the calendar day a transaction appears under. Daily averages include all calendar days in the selected range, even days without transactions.

## Receipt photos and offline scanning

In Add/Edit Refuel, select **Choose receipt photo** to pick one image from your phone's gallery. The app stores a bounded, orientation-correct JPEG copy privately so the attachment survives the picker permission expiring. Originals are never modified. You can view, replace or detach the photo.

Tap **Scan & review receipt** to run the bundled Latin OCR model offline. The review shows suggested litres, total payment, fuel type, station/location, date/time and an odometer only when explicitly printed. Select the fields to apply, check them against the photo and save the form normally. The parser avoids confusing unit price, tax or loyalty values with the total, but real receipts can be blurry or ambiguous, so manual review is required. Receipt scanning never creates a transaction automatically. The final app manifest denies Internet access, including to bundled SDKs.

**Save copy to receipt album** creates a separate **Pictures/Fuel & Expense Tracker Receipts** folder visible in your gallery. Repeat saves avoid duplicate copies. Android 10+ uses scoped MediaStore without storage permission; Android 8–9 requests write permission only for this explicit export action. Detaching/deleting a record does not erase a photo from your gallery.

## Export and backup

Use Settings and Android's system document picker. Select a local folder for fully offline operation. CSV contains quoted UTF-8 fields, exact MYR amounts, local timestamps with offsets and link IDs. Text that could become a spreadsheet formula is escaped. CSV is an export format, not a restore format.

A complete version-2 ZIP backup includes categories, expenses, refuels, preferences and all attached receipt photos. Version-1 JSON backups remain importable with defaults for the new optional fields. Restore validates the format version, amounts, IDs, foreign keys, odometer order and fuel links before offering confirmation. Confirmation **replaces** the current dataset; merging is intentionally not offered. Cancel leaves current data unchanged. Export your current backup before replacement. No data is silently deleted when removing a custom category: its expenses move to Others after confirmation.

Backups are not encrypted: keep them in a private folder. Automatic Android cloud backup is disabled. ZIP backups are capped at 256 MB expanded, with 16 MB metadata, 8 MB per photo, 100,000 expenses, 100,000 refuels and 500 categories to prevent unbounded imports. Restore validates filenames, sizes, image data and relationships in cache before confirmation; confirmed restoration copies photos to fresh private names before replacing records. Legacy JSON is capped at 16 MB. Built-in category definitions are protected. If restoring DataStore settings is interrupted, a settings intent committed in Room is replayed on the next launch.

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
      data/ReceiptBackupArchive.kt  ZIP photos and legacy JSON restoration
      receipts/                    Private/gallery photos, bundled OCR and parsing
      ui/                           Screens, editors, charts, theme and receipt review
      ui/ScreenPreviews.kt         Ten dark/light/large-type/empty previews
    res/                            Native launcher icon and base theme
  src/test/                         Calculation and Room integration tests
gradle/wrapper/                     Verified Gradle wrapper
scripts/setup-cloud.sh              Repeatable cloud toolchain setup
build.gradle.kts                   Pinned build plugins
settings.gradle.kts                Standard Google and Maven repositories
```

The shared fintech design uses navy `#0B1220`, cards `#18263B`, emerald `#4DE0B1`, text `#F3F6FA` and muted text `#94A3B8`, with a complete light palette. Cards/actions stack on narrow phones and at larger font sizes. `FintechTheme.kt` centralizes colours, typography and shapes; `Components.kt` provides reusable cards/charts. Open `ScreenPreviews.kt` in Android Studio for previews without a database or ViewModel.

The UI reads state through `LedgerViewModel`. Writes go through `LedgerRepository`; Room exposes flows, so adding, editing and deleting records updates dashboards automatically. Heavy database and document operations run off the main thread. Calculation logic is separate from Android and is tested with JUnit. Room integration tests use Robolectric with an actual SQLite database.

Room is at schema version 2 and exports both schemas. The tested additive 1→2 migration adds only optional refuel metadata and retains all existing records and expense links. Future database changes must increment the version and implement/test a migration. **Do not add destructive migration fallback**, which would erase records. Preferences belong in DataStore. Dark mode is the default for new preferences; saved Light/System/Dark choices are retained. Keep the stable application ID `my.fuelledger` for updates.

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
