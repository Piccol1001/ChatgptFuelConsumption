# Comprehensive upgrade checklist

Baseline: ece52c2; protected local branch `checkpoint/comprehensive-upgrade-2026-10-16`.
Existing Room version 4, SQLCipher, encrypted DataStore and receipt storage; migrations 1→2→3→4 preserve all records. Existing five-page Foundation pager and activity-scoped ViewModels are retained.

- [x] Inspect schema, navigation, five screens, financial planning, scanner and security.
- [x] Protect baseline and retain request.
- [x] 1. Home: Overview, planned card, budget, four actions, upcoming payments, reference rendering.
- [x] 2. Expenses: favourites, memory, refunds, Undo, duplicates, search, swipe actions, calendar.
- [x] 3. Fuel/Journey: historical comparisons, recorded costs, reverse/scenarios/actual links/export.
- [x] 4. Analysis: ranges, actual/planned comparisons, local insights, CSV/PDF reports.
- [x] 5. Settings: dashboard order/visibility, gestures, haptics, reduced motion, privacy, About.
- [x] 6. Security review, notifications, local management, three offline widgets.
- [ ] 7. Full integration and migration tests, offline manifest/dependency audit, APK delivery.

No maintenance tracker, savings goals, split categories, passenger sharing, other journey fees, safe-to-spend recommendations, network dependencies or database reset.

Build: `source /workspace/.tooling/fuel-ledger-env.sh`; run `./gradlew :app:testDebugUnitTest :app:assembleDebug`, then sequential release and lint checks. Do not alter sources while lint is running. Preserve old APKs.

Milestone 1: compileDebugKotlin and two OverviewDashboardTest cases passed. Light/dark reference renders inspected and retained in this directory. Shared horizontal margins are 16dp. Calendar action is wired during milestone 2.

Milestones 2–3: combined AppSmokeTest, ExpenseToolsTest and VehicleJourneyUpgradeTest run passed (32 executions). Room 4→5 adds expense merchant/refund metadata, merchant preferences, favourites and encrypted transaction Undo journal. Old monetary/payment values are unchanged. CSV/backup format 5 preserves new fields and reads formats 1–4. Undo retains old referenced receipt images until explicitly cleared. Journey summary exports use temporary app-private files granted only through Android sharing. Physical sharing-app verification remains pending.

Milestone 4: FinancialReportTest and OverviewDashboardTest passed (4 executions). Analysis supports actual income/expense trends, paid fixed/variable spending, budgets, planned forecasts/reserves, deterministic insights and local CSV/PDF document generation. Export picker cancellation/process recreation remains a device check.

Milestone 5: encrypted SettingsStore round-trip tests now include all interaction, dashboard and privacy preferences; press, reference render and accessibility masking tests passed. Amount masking changes display/semantics only. Widgets remain hidden by default; app screen protection stays enabled.

Milestone 6: standard RemoteViews expense, quick-add and cash-flow widgets compile and inflate/update in API 26/35 tests. Existing local worker handles periodic updates; date/time broadcasts refresh calendar boundaries. Financial writes request widget refresh. Amounts hidden by default. Generic budget-threshold reminders added without financial lock-screen text. SQLCipher, Keystore, encrypted receipts/backups, BiometricPrompt and FLAG_SECURE retained. Repository/widget regression group passed.
