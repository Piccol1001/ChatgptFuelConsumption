# Fuel & Expense Tracker

A native Android app for personal spending and car fuel records. Kotlin, Jetpack Compose / Material 3, MVVM, Room and DataStore. Designed for offline use with no account, server, advertising or Internet permission. Structured records, settings and retained receipt photos are encrypted locally. Receipt selection uses the system picker without broad gallery permissions. See [SECURITY.md](SECURITY.md) and [VALIDATION.md](VALIDATION.md) for the architecture, dependency review and precise verification limits.

## Download the APK

[**Download Fuel & Expense Tracker for Android**](https://github.com/Piccol1001/ChatgptFuelConsumption/raw/refs/heads/main/downloads/Fuel-Expense-Tracker-v1.4.0-comprehensive-upgrade-debug.apk)

Version 1.4.0, signed debug build, Android 8.0 or later. Open this link on your phone, download the `.apk` file, then open it to install. If Android asks, allow installation for the browser or file manager you used. The APK is also available in this repository's `downloads` folder. Its SHA-256 checksum is in `downloads/SHA256SUMS`; build and test evidence is in [VALIDATION.md](VALIDATION.md).

The launcher icon now combines a fuel pump and wallet in ivory and gold on teal. The installed app is named **Fuel & Expense Tracker v1.4.0**; main screens begin with their screen title and an inline Settings button, without an app-name/version header or empty toolbar row. Project metadata remains version 1.4.0 / code 6. Android adaptive icons follow the launcher’s circle, squircle or rounded-square shape, and Android 13+ themed icons use a monochrome companion. Install this APK over the existing app to keep your records: the application ID and signing key are unchanged, and version code increases to 6 (from 5 in version 1.3.0).

## Install on your phone

Requires **Android 8.0 or later**. The default units are Malaysian Ringgit (MYR/RM), kilometres and litres. Dates are displayed in your phone's local time zone.

1. Build or download `app/build/outputs/apk/debug/app-debug.apk`.
2. Transfer the APK to your phone using USB, Bluetooth or a private file transfer service.
3. Open the APK with your phone's file manager.
4. When Android asks, allow **Install unknown apps** for that file manager or browser. Install, then turn that permission off again if desired.
5. Open **Fuel & Expense Tracker v1.4.0**. No Internet connection is needed for installation or use once the APK is on the phone.

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

Swipe left/right between **Home → Fuel → Expenses → Journey → Analysis**, or tap the matching bottom tab. Pages stop at either end and retain their drafts and scroll positions. Category rows, the Journey toggle and chart regions keep their own gestures; forms, Settings, scanner and planner configuration retain their existing Back behavior. Main-screen swipes can be disabled in Settings. See [current implementation and verification](docs/comprehensive-upgrade/IMPLEMENTATION.md).

Journey now shares the reference screens' content margins, omits empty suggestion spacing, and uses concise labels that fit small phones. See the [measured layout investigation and matching screenshots](docs/journey-layout/README.md).

- **Home:** month/Overview header, planned available after fixed commitments, real monthly budget progress, Expense/Income/Scan/Calendar shortcuts, upcoming payments and an amount-privacy eye control. No bank balance or safe-to-spend recommendation is shown.
- **Fuel:** weighted full-tank consumption and trends, retained per-fill history, recent change detection, actual price history and measured fuel/recorded transport costs per kilometre with coverage labels.
- **Expenses:** favourites, merchant/category/payment memory, refunds, duplicate warnings, persistent Undo, configurable row swipes and advanced date/category/payment/type/search filters. Others stays last; category filters remain below Transaction history. Spending Calendar shows actual daily records and supports date-seeded entry.
- **Analysis:** actual income and net expenses, category/daily/monthly trends, budget performance, paid fixed/variable spending, planned forecasts/reserves, local insights, vehicle statistics and local CSV/PDF reports. Select current/previous/3/6/12-month or custom ranges.
- **Journey:** named local routes and presets, reverse direction, independent outbound/return distances and tolls, price scenarios, explicit existing-expense links, estimated/actual comparisons and text/PNG sharing. Planning/linking creates no transactions; missing actuals remain unavailable.
- **Settings:** one Monthly Money Planner, existing allocation sliders, themes, dashboard order/visibility, swipe actions, haptics, reduced animation, privacy, app lock, local data cleanup, exports and encrypted backups. About shows the installed version.
- **Home-screen widgets:** monthly expense/budget summary, quick-add shortcuts and actual income/expenses/net cash flow. Amounts are hidden by default; widgets use local updates and the device's light/dark appearance.

[Full implementation, migrations, screenshots and limitations](docs/comprehensive-upgrade/IMPLEMENTATION.md).

Each refuel automatically creates one expense in **Fuel & Transport**. Editing or deleting it updates or deletes that expense in the same SQLite transaction. Tap a linked expense to open its refuel editor. A manually added Fuel & Transport expense is independent; avoid entering a refuel's payment manually as well.

Enter the **ODO reading and litres purchased at every fill-up**, including partial fills. The first record establishes a baseline. Each later record shows distance since the preceding ODO reading, estimated km/L, L/100 km and cost/km using the current purchase's litres and payment. For example, 10,000 km followed by 10,500 km with 40 L gives a **12.50 km/L per-fill estimate**. Tank levels can vary, so this is explicitly an estimate rather than measured consumption. New entries default to partial fill; fuel type (RON95/RON97/Diesel/Other), station and station location are optional.

For the primary historical measured-consumption value, first enter a **full tank** as the baseline. Enter any partial refuels, then the next full tank. Measured fuel is the litres purchased **after** the baseline, including the final full tank. The first tank alone cannot measure consumption. Dates and odometers must increase strictly; existing records can be edited but cannot reverse that order. This app tracks **one vehicle**; mixing vehicles would invalidate measurements.

The shared historical value is 100 × total valid-cycle litres ÷ total valid-cycle distance, rather than an average of interval ratios. It updates on refuel edits/deletions and excludes incomplete/invalid cycles. Settings and Journey show “Not enough fuel data” until a valid cycle exists; fallback consumption is used only after explicit entry/enablement. The estimator uses distance × consumption ÷ 100 and petrol price. The example 150 km, 7 L/100 km, RM2.05/L produces **10.50 L, RM21.53, RM0.1435/km and 14.29 km/L**. Estimated money uses decimal HALF_UP rounding; stored payments use exact integer sen. Overall measured efficiency is total valid-cycle km divided by total valid-cycle litres.

Week totals use Monday–Sunday. Calendar totals and date filters use the phone's local time zone, including daylight saving changes; timestamps are stored as epoch milliseconds. Changing the device time zone can change the calendar day a transaction appears under. Analysis daily averages include every calendar day in the selected range, including empty days. Spending Calendar uses elapsed days for the current month and full days for other months.

### Adding and editing expenses

Tap **Expense** on Home or **Add Expense** in Expenses, choose an icon in the horizontal category row (custom categories are retained, Others stays last), and use the cents-first keypad. Starting at RM 0.00, digits 1 → 2 → 3 → 4 → 5 produce RM 0.01 → RM 0.12 → RM 1.23 → RM 12.34 → RM 123.45. Backspace removes the last digit; Clear resets to zero. There is no decimal key or software keyboard for the amount. Save requires a positive amount and a selected payment method; values are exact integer sen and limited to the existing RM1,000,000,000 maximum.

Choose **Cash**, **Card** or **E-Wallet**. The last selected method is saved in encrypted local DataStore, including when cancelling the draft, and highlighted for the next new expense (initially Cash). Existing Credit Card/Debit Card payments display as Card but retain their original stored strings when edited within that group. Historical Not specified records remain intact and prompt for an explicit method before saving changes. Existing amounts initialize directly from their stored sen; untouched transaction timestamps retain their original precision.

Open **Details & receipt** for date, time, merchant, description, collapsed notes, Photo Picker/camera and offline OCR review. Choose Refund there for an unlinked refund, or use a transaction’s Actions → Record refund to link it to the purchase. Favourites can be created from Actions or the editor details and managed in Expenses. Collapsing the section retains the attachment. The expense list's category filters now sit directly below **Transaction history**; their filtering, scrolling and ordering are unchanged. The app name/version metadata and prior header cleanup remain unchanged (1.4.0 / code 6).

### Touch feedback

App controls use a subtle 0.97× press animation instead of grey ripple overlays. The drawing shrinks for roughly 90ms and returns smoothly over 130ms; touch targets, layout and accessibility semantics keep their original bounds. Selected categories, chips, navigation and the journey toggle use accent colours, and keyboard focus has a rounded accent outline. Android's Remove animations / animator duration scale is respected: scaling is disabled at zero, and the app only reads that system preference. The shared policy covers the app, dialogs, app-lock screen and previews in both themes. Standard date/time input internals retain their native selection/editing behavior with the grey ripple suppressed.

## Receipt photos and offline scanning

In Add/Edit Expense or Refuel, select **Choose receipt photo**. The app reads the original without changing it and retains a normalized, orientation-correct JPEG copy encrypted in private storage. You can view, replace or remove the attachment. No public receipt album is created.

**Scan & review receipt** runs the bundled Latin OCR model locally. Edit suggested merchant, total, date/time, category and applicable fuel details before applying selected fields to the form. ODO is suggested only when explicitly printed. Unit prices, tax and loyalty values are not treated as totals. Blurry or ambiguous receipts need manual correction; scanning inside an existing form always waits for you to save. The separate dashboard scanner supports the optional Smart Auto-Save mode described below. Choose the delete-after-extraction option if you do not want to retain the imported copy. Removing an existing saved photo takes effect when the transaction edit is saved; cancelling preserves the original record.

## Monthly Money Planner and allocation

Open Settings → Financial Planning → **Monthly Money Planner**. Choose **Expected Income → Sources** to set multiple Salary, Allowance, Stock / Investment Income, Passive Income, Part-Time Income or Other Income sources. Optional irregular estimates can be blank; zero estimates never become received income. Use **Expected Income → Received** for actual received-income records. Choose **Fixed Monthly Expenses** to manage rent, subscriptions and other commitments. Both reuse the existing recurrence rules; daily, weekly and monthly schedules support start/end dates and pause/resume. For a monthly bill on the 29th–31st, shorter months use their last day and the following month returns to the original day.

The dashboard distinguishes:

- **Planned available:** expected income − fixed commitments.
- **Actual monthly net cash flow:** income received − all actual recorded expenses.
- **Remaining spending allowance:** planned available − variable/unreserved spending. A payment linked to a fixed occurrence reserved in the selected month is excluded from variable spending, while remaining in expenditure and budget reports. An early/late payment for another month's commitment counts as unreserved spending in its actual payment month.

New fixed expenses wait for confirmation. In **Overview & payments**, record a payment once or link an existing expense; linked refuelling transactions cannot be reused as fixed payments. Existing automatic recurring expenses retain their behavior and use the same rule IDs. To convert one, edit that existing rule and enable **Confirm payments manually**. Editing the actual transaction updates payment history; deleting a manual payment reopens its occurrence. Expected income never becomes actual received income automatically.

Use month arrows to compare saved past plans and preview future months. Reconciliation catches missed months when the app opens; it does not require continuous background operation. Captured past plans stay unchanged when rules change; uncaptured historical months are reconstructed from the rules available when first opened. Current plans reflect rule edits. Pausing excludes a rule from new projections; old saved months remain available.

Within the same **Monthly Money Planner → Monthly Allocation** screen, choose defaults or an override for the selected month. Drag category sliders and inspect the live donut and RM amounts. Only the selected category and the named flexible remainder exchange percentages; totals always stay at 100% without negative values. To adjust the remainder directly, first choose another flexible category. Edit names and spending-category mappings, add/remove custom categories, then save. Removing a category returns its percentage to the remainder. Each spending category may map to one allocation. Allocation rounding distributes every sen; zero/negative available amounts allocate RM 0.00 and keep the shortfall visible. Savings is a planned reserve, not an expense or transfer. Month overrides do not change defaults; changing defaults preserves captured past allocation plans. Allocations never create transactions or deduct fixed commitments a second time.

Optional **Local planning reminders** are checked when the app opens and roughly every 12 hours, subject to Android scheduling. They cover upcoming/unpaid bills and income shortfalls, use generic private notification text, and request notification permission only when enabled on Android 13+. Android settings may need to re-enable previously denied notifications.

## Journey Planner

Tap **Journey** in bottom navigation. Enter origin and destination names and a manual one-way driving distance; saved locations appear as offline suggestions. Place names never generate distances. Tap or drag the One Way / Round Trip pill and edit the return distance. The live historical fuel average is selected automatically. Enable **Override this journey** only if needed. Without history, enter a labelled fallback and optionally save it for new journeys. Petrol price remains editable. Return distance initially copies outbound distance; return tolls are never doubled automatically.

Add one total toll entry or several named toll segments for each direction, plus total parking. New estimates equal **fuel + tolls + parking**, using the existing fuel estimator and integer-sen fees. Other-fee and passenger-sharing controls are removed. Existing saved journeys retain their original fields and totals through a versioned JSON compatibility path; explicitly saving an edit uses the new formula. Legacy journeys reopen with their saved consumption; new journeys remember whether consumption came from history, a fallback or an explicit override. Reopening a history-based journey uses the latest average; saved history lists retain the original estimate until an edit is saved. Save, edit, delete or duplicate a journey using the latest default petrol price. Saved plans and locations remain local and are included in encrypted backups. No journey estimate is posted to Expenses or Fuel Tracker.

## Automatic offline receipt scanner

Tap **Scan Receipt** on the dashboard. Use **Take receipt photo** or Android Photo Picker. Camera capture uses the device's camera app through a narrowly scoped private FileProvider; the app needs no camera or gallery permission. Camera failure/denial leaves gallery selection available. The temporary private camera file is removed after import; abandoned camera files are cleaned after 24 hours on the next app startup. Gallery originals are unchanged. Imported photos, crop/rotate results and retained attachments are encrypted privately. Long portrait receipts keep up to 4096 pixels on their long side, subject to pixel/file limits.

Recognition uses the bundled ML Kit Latin model, including on first offline launch. Crop top/bottom borders or rotate 90° and rescan if needed. Parser rules rank final totals and reject subtotal, tax, tendered cash and change. Missing or conflicting values remain for review. Low contrast, likely blur, small images, ambiguous totals/dates, foreign currencies, unknown categories, petrol receipts and possible duplicates prevent automatic saving.

Settings → **Receipt Scanner → Save Mode** offers **Review Before Saving** (default) and **Smart Auto-Save**. Review mode fills editable merchant, amount, date and category fields. Smart mode requires clear, validated values and no duplicate indicators. Successful saving updates the existing expense, budget, allowance and analytics flows and shows **Undo saved receipt expense**. Undo removes only an unchanged saved receipt transaction and its duplicate marker; an edited transaction must be reviewed in Expenses before deletion. Normal expense deletion retains a receipt fingerprint to prevent accidental rescanning as a new charge.

Exact normalized OCR fingerprints are unique in Room. Same-day/same-amount transactions trigger conservative duplicate review. Match a petrol receipt to its existing linked refuel expense using **Use existing transaction** to avoid a second charge; an explicitly confirmed distinct purchase can be recorded after review. Retaining the receipt is optional. Delete a retained photo later through its expense/refuel editor without deleting the transaction.

Correct a category and enable **Remember this merchant's category**, or add/edit/remove exact merchant mappings in Settings. Matching ignores letter case and punctuation; built-in Malaysian merchant rules cover McDonald's, Lotus's, Petronas, TGV and Watsons. Non-MYR receipts require manual conversion outside the scanner. Tax/item-level extraction is not included in this version. Very damaged, skewed or unreadable thermal receipts may require a better photograph or manual entry; confidence checks are conservative heuristics, not a guarantee of OCR accuracy.

## Appearance

Settings → **Appearance → Theme** applies Light, Dark or System Default immediately, including dialogs and the app-lock screen. New installations follow the device theme; updates keep the existing choice. Preferences persist in encrypted DataStore without restarting the app.

## Income, budgets and recurring payments

Open Settings → **Financial Planning → Monthly Money Planner** to track income and set overall/category monthly budgets using local MYR records. Budget usage comes from actual expenses, including linked refuels. Income is kept separately from expenditure; no account balance is invented.

Recurring expenses support daily, weekly and monthly schedules. Occurrences and their next-date cursor commit together to prevent duplicates. Monthly schedules keep the original day through shorter months (for example, 31 January → 28/29 February → 31 March). Android can defer background work; local reconciliation catches up when the app next runs. Disabling/deleting a rule preserves existing transactions.

## Export and encrypted backup

Settings uses Android's document picker. Choose a private local destination. CSV is an **unencrypted** quoted UTF-8 spreadsheet export with exact MYR amounts and local timestamps; spreadsheet formula-like text is escaped. CSV cannot be restored.

A complete `.fuelbackup` export includes categories, expenses, refuels, income, budgets, recurring rules, monthly plan snapshots, allocation defaults/overrides, saved journeys, merchant mappings, duplicate fingerprints, preferences and retained receipt photos. Enter and confirm a password of 12–1024 characters. Tink streaming authenticated encryption protects the archive, with PBKDF2-HMAC-SHA256 (600,000 iterations) and a fresh salt. The password is never stored. **Losing it can make the backup unrecoverable.**

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

Room is at schema version 4 and exports all four schemas. Additive migrations retain existing records and expense links; the encryption upgrade verifies a replacement before replacing a legacy plaintext database. Future database changes must increment the version and implement/test a migration. **Do not add destructive migration fallback**, which would erase records. Appearance and scanner preferences use encrypted DataStore; allocation plans and month overrides use encrypted Room. System Default is the theme for new installations; saved Light/System/Dark choices are retained. Keep the stable application ID `my.fuelledger` for updates.

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

The comprehensive upgrade passes 283 host tests, debug/release builds and lint (0 errors). Native offline/security/migration checks and their build provenance are recorded in [VALIDATION.md](VALIDATION.md). Automated checks exercise the specified trip example, exact currency, daily/monthly/category aggregation, timezone and year boundaries, partial fills, edits/deletions, linked expenses, invalid input, CSV and backup restoration.

This is a personal, single-vehicle, MYR app. It does not include multi-vehicle tracking or CSV import. Release signing requires your own key. Before relying on it for irreplaceable data, verify the app and backup/restore on your phone and keep regular backups. A compiled APK is not a claim of physical-device testing.
