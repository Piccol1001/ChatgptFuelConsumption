# Final host test results

17 October 2026 (Malaysia time). Generated from the final Gradle XML results. All 283 executions passed; no failures, errors or skipped tests. Tests include API26/35 Robolectric executions where configured; these are distinct from native instrumentation.

| Suite | Executions | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| my.fuelledger.data.ExpenseEntryTest | 2 | 0 | 0 | 0 |
| my.fuelledger.data.ExpenseToolsTest | 18 | 0 | 0 | 0 |
| my.fuelledger.data.FinanceRepositoryTest | 12 | 0 | 0 | 0 |
| my.fuelledger.data.PlannerRepositoryTest | 13 | 0 | 0 | 0 |
| my.fuelledger.data.PlanningCalculationsTest | 18 | 0 | 0 | 0 |
| my.fuelledger.data.ReceiptBackupArchiveTest | 24 | 0 | 0 | 0 |
| my.fuelledger.data.RefinementTest | 12 | 0 | 0 | 0 |
| my.fuelledger.data.RepositoryTest | 25 | 0 | 0 | 0 |
| my.fuelledger.data.SettingsStoreTest | 14 | 0 | 0 | 0 |
| my.fuelledger.domain.CalculationsTest | 17 | 0 | 0 | 0 |
| my.fuelledger.domain.CentsKeypadTest | 3 | 0 | 0 | 0 |
| my.fuelledger.domain.FinancialReportTest | 2 | 0 | 0 | 0 |
| my.fuelledger.domain.VehicleJourneyUpgradeTest | 4 | 0 | 0 | 0 |
| my.fuelledger.receipts.ReceiptParserTest | 23 | 0 | 0 | 0 |
| my.fuelledger.receipts.ReceiptReviewTest | 2 | 0 | 0 | 0 |
| my.fuelledger.receipts.ReceiptStoreTest | 10 | 0 | 0 | 0 |
| my.fuelledger.security.AppLockSessionTest | 7 | 0 | 0 | 0 |
| my.fuelledger.security.AppLockStoreTest | 10 | 0 | 0 | 0 |
| my.fuelledger.security.LocalVaultTest | 3 | 0 | 0 | 0 |
| my.fuelledger.ui.AppLockUiTest | 4 | 0 | 0 | 0 |
| my.fuelledger.ui.AppSmokeTest | 16 | 0 | 0 | 0 |
| my.fuelledger.ui.ComprehensiveUpgradeUiTest | 10 | 0 | 0 | 0 |
| my.fuelledger.ui.ExpenseEntryControlsTest | 3 | 0 | 0 | 0 |
| my.fuelledger.ui.JourneyLayoutTest | 4 | 0 | 0 | 0 |
| my.fuelledger.ui.MainPagerTest | 10 | 0 | 0 | 0 |
| my.fuelledger.ui.OverviewDashboardTest | 2 | 0 | 0 | 0 |
| my.fuelledger.ui.PressFeedbackTest | 8 | 0 | 0 | 0 |
| my.fuelledger.ui.PrivacyUiTest | 2 | 0 | 0 | 0 |
| my.fuelledger.ui.RefinedControlsTest | 1 | 0 | 0 | 0 |
| my.fuelledger.widgets.LedgerWidgetsTest | 4 | 0 | 0 | 0 |

Reproduce with `source /workspace/.tooling/fuel-ledger-env.sh` then `./gradlew :app:testDebugUnitTest`. Native provenance, build, lint, security, screenshot coverage and device limitations are in [VALIDATION.md](../../VALIDATION.md).
