# Comprehensive upgrade checklist

Baseline: ece52c2; protected local branch `checkpoint/comprehensive-upgrade-2026-10-16`.
Existing Room version 4, SQLCipher, encrypted DataStore and receipt storage; migrations 1→2→3→4 preserve all records. Existing five-page Foundation pager and activity-scoped ViewModels are retained.

- [x] Inspect schema, navigation, five screens, financial planning, scanner and security.
- [x] Protect baseline and retain request.
- [x] 1. Home: Overview, planned card, budget, four actions, upcoming payments, reference rendering.
- [x] 2. Expenses: favourites, memory, refunds, Undo, duplicates, search, swipe actions, calendar.
- [x] 3. Fuel/Journey: historical comparisons, recorded costs, reverse/scenarios/actual links/export.
- [ ] 4. Analysis: ranges, actual/planned comparisons, local insights, CSV/PDF reports.
- [ ] 5. Settings: dashboard order/visibility, gestures, haptics, reduced motion, privacy, About.
- [ ] 6. Security review, notifications, local management, three offline widgets.
- [ ] 7. Full integration and migration tests, offline manifest/dependency audit, APK delivery.

No maintenance tracker, savings goals, split categories, passenger sharing, other journey fees, safe-to-spend recommendations, network dependencies or database reset.

Build: `source /workspace/.tooling/fuel-ledger-env.sh`; run `./gradlew :app:testDebugUnitTest :app:assembleDebug`, then sequential release and lint checks. Do not alter sources while lint is running. Preserve old APKs.

Milestone 1: compileDebugKotlin and two OverviewDashboardTest cases passed. Light/dark reference renders inspected and retained in this directory. Shared horizontal margins are 16dp. Calendar action is wired during milestone 2.

Milestones 2–3: combined AppSmokeTest, ExpenseToolsTest and VehicleJourneyUpgradeTest run passed (32 executions). Room 4→5 adds expense merchant/refund metadata, merchant preferences, favourites and encrypted transaction Undo journal. Old monetary/payment values are unchanged. CSV/backup format 5 preserves new fields and reads formats 1–4. Undo retains old referenced receipt images until explicitly cleared. Journey summary exports use temporary app-private files granted only through Android sharing. Physical sharing-app verification remains pending.
