# Comprehensive Android upgrade

Request: [REQUEST.md](REQUEST.md). Baseline: `ece52c2` (protected by local branch `checkpoint/comprehensive-upgrade-2026-10-16`). Existing Kotlin/Compose MVVM application, five-page Foundation pager, Room database, encrypted preferences and receipt storage were extended in place. Package `my.fuelledger`, installed version 1.4.0/code 6 and signing identity are retained.

## Implemented

1. **Home:** Overview/month/settings/eye control; planned available amount from expected income minus scheduled fixed commitments; real budget usage; Expense, Income, Scan and Calendar actions; unpaid upcoming commitments. Reference previews use RM1,870 available, RM3,000 expected, RM1,130 fixed, RM1,680 budget, RM1,260 spent and RM120 Wi-Fi. These figures are sample-only. Shared main-screen margins are 16dp.
2. **Expenses:** locally stored favourites with one-tap posting, editing/removal; merchant category/payment/description memory; last-used category and frequent-category ordering; retained cents keypad/payment icons/custom category IDs. Transaction actions support edit, duplicate, refund and confirmed deletion; configurable row swipes consume their own gestures. Advanced search covers merchant, description, notes, amount, category, payment and local ISO date, alongside date/category/payment/type filters and sorting. Notes stay collapsed. Existing private receipt viewing/replacement/deletion is retained.
3. **Refunds and Undo:** signed integer-sen refunds reduce net expenditure without entering income. Refunds can be unlinked or linked to an original purchase; linked amounts cannot exceed it. Historical references survive original deletion. Persistent transactional Undo restores expense edits/deletes and both sides of linked refuel changes, rejects intervening changes and retains required old receipt images. Immediate snackbar Undo and a persistent Expenses action are available. Explicit image cleanup removes image references from Undo as well. Ordinary receipt deletion retains the existing scan fingerprint safeguard.
4. **Spending Calendar:** Home and Expenses entry points, month grid, local-date totals/indicators, selected-day history, editing and date-seeded Add Expense; monthly total, highest positive spending day and daily average. No scheduled bills are counted as actual spending. Average uses elapsed days in the current month and full days in other months.
5. **Fuel:** primary averages/trend use complete full-tank intervals with intermediate partial refuels included once. Per-fill estimates remain in history. Recent change compares the last two valid cycles with at least two earlier cycles. Volume-weighted price history, separate price/consumption contributions to cost/km change, and measured fuel-only/recorded operating costs include coverage and category labels. Recorded transport costs are included only within measured intervals; linked pump payments are counted once. These figures do not claim complete ownership costs.
6. **Journey:** existing local routes gain optional names, recent local locations, a compact reverse action, independently stored round-trip distances/tolls, petrol-price scenarios, reusable presets using current fuel settings, explicit existing-expense attribution and estimated/actual comparisons. Missing/deleted actuals are unavailable. One expense cannot belong to two journeys. Text and PNG summaries are generated locally and shared through temporary FileProvider grants. No transactions are created by linking or estimating. Current default vehicle is retained.
7. **Analysis and reports:** actual income/net expenditure, paid fixed vs variable spending (including linked refunds), budget performance, daily/category/monthly/fuel trends, current/previous/3/6/12-month/custom ranges, labelled expected-income/commitment forecasts, planned savings reserves, deterministic local insights and vehicle analysis. CSV and paginated PDF reports use Android's document picker.
8. **Settings and usability:** one Monthly Money Planner entry with existing income/fixed/allocation sliders; Light/Dark/System; dashboard order, optional budget/upcoming visibility and reset; main swipe, row actions, haptics and reduced-animation preferences; amount masking for displayed text and chart accessibility; actual installed version in About; private storage usage, confirmed receipt/Undo/date-range cleanup. New preference fields remain in encrypted DataStore. Keyboard-aware editor layouts, local custom icons/colours and existing app lock remain available.
9. **Widgets and reminders:** standard RemoteViews monthly expense, quick-add and actual cash-flow widgets, device light/dark resources, resizable layouts and hidden values by default. Budget usage supports an overall budget or the sum of configured category budgets, without counting unbudgeted categories against that sum. Shortcut requests wait for an open editor to close and respect app lock. Financial writes, date/time broadcasts and the existing local periodic worker refresh widgets; aggregate SQL avoids loading an entire backup for refresh. Existing fixed-payment reminders gain a private budget-threshold alert. No network constraints or push service are required.

## Database and preservation

Room schema **4 → 5** adds:

- `expenses.merchant`, `expenses.kind` (default `Expense`) and nullable `expenses.refundOf`; existing sen amounts and payment strings are unchanged.
- `receipt_merchants.payment` and `.description`, default empty for old mappings.
- `expense_favourites` for templates and `transaction_undo` for reversible prior versions.

Migrations 1→2→3→4→5 are registered; there is no destructive fallback. New Journey metadata uses optional fields in the existing JSON payload. Backup format 5 reads formats 1–4 and includes favourites, refunds, merchant preferences, Journey metadata and settings; authenticated portable backups retain receipt photos. Undo history is local recovery state, not included in exported backups. Restore remains an explicit confirmed replacement, with validation before mutation. Old APKs, reports and user records are retained.

## Privacy

No dependency version was changed or new runtime dependency added. SQLCipher, Keystore-managed wrapping, Tink-based encrypted local data, authenticated password backups, BiometricPrompt, FLAG_SECURE and disabled automatic backup remain. Final merged manifest and APK audits are recorded in VALIDATION.md. Export destinations and receiving share applications are chosen by the user. Temporary shared Journey images are private, use URI grants and are pruned after 24 hours on startup/local work. Removing a receipt keeps its transaction; clearing Undo or receipt history requires confirmation.

## Verification and delivery

See [VALIDATION.md](../../VALIDATION.md), [PROGRESS.md](../../PROGRESS.md), [CHECKLIST.md](CHECKLIST.md) and the screenshots in this directory. Final host run: **283 tests, 0 failures/errors/skipped**. Debug, Android test and optimized unsigned release APKs build. Final lint: **0 errors, 10 preserved advisory warnings and 1 hint**. Twenty real-app matched renders cover all five screens in four configurations, with two additional Home reference renders.

Native API26 checks pass in airplane mode using an isolated test user: the nine-test offline financial/OCR/encryption/migration suite ran before the last UI-only preference/widget polish; four additional exact delivered-APK native OCR/permission/encryption checks pass on a fresh test user (28.034s). Full provenance and limitations are recorded in VALIDATION.md. No physical-device pass is claimed.

Download: [`Fuel-Expense-Tracker-v1.4.0-comprehensive-upgrade-debug.apk`](../../downloads/Fuel-Expense-Tracker-v1.4.0-comprehensive-upgrade-debug.apk), 76,809,823 bytes, SHA256 `4b500a32be9046723c24bd66345fdc90a1276804759e3fe78b2e6fbea989b660`. Original signing certificate and 16KiB ZIP alignment verify. All previous APK checksums pass.

Every created/modified path relative to baseline `ece52c2` is listed in [MODIFIED_FILES.txt](MODIFIED_FILES.txt).

## Practical limits

- Single vehicle and MYR; distances, tolls and actual expense attribution are manual/local. Vehicle costs cover only the labelled recorded categories and measured intervals.
- No split categories, maintenance tracker, savings-goal subsystem, passenger sharing, other Journey fees, safe-to-spend recommendations or online processing were added.
- Optional OCR tax/item extraction and optional Calendar category filtering are not added; deterministic financial parsing and single-category transactions remain.
- Physical camera/gallery flows, external share/document providers, launcher/OEM widget placement/resizing, TalkBack, large real thermal receipts and sustained performance still need device testing. API26 native and API26/35 host coverage are distinguished in the final validation record. Android15 native edge-back verification remains limited by the previously documented software-emulator system ANRs.
- Privacy masking changes visible monetary strings and chart descriptions, not exported data or editable source fields. Widget privacy also applies when the in-app privacy mode is active.
- Undo refuses to overwrite changed records, restore an invalid odometer sequence or violate refund limits; it can require manual review after such changes. Explicit image/history cleanup is confirmed. Deleting history does not delete income or planning preferences.

## Resume

```sh
cd /workspace/ChatgptFuelConsumption
git status --short --branch
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
```

Run release and lint sequentially; avoid source edits during lint. MainActivity/AppSmoke/MainPager tests exercise the real app, and the comprehensive UI tests cover the additions. Native instrumentation uses a separate Android test user to avoid resetting existing app data. Never clear/uninstall the owner's ledger to obtain a fresh-install test.
