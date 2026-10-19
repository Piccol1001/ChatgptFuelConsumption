# CLARITY UI refinement

19 October 2026, Malaysia. Existing Kotlin/Compose app extended in place from audit checkpoint `f9ab990`. [Request](docs/clarity-2026-10-18/REQUEST.md). Financial-integrity fixes and cleanup are separately documented in [CLEANUP_REPORT.md](CLEANUP_REPORT.md).

## Design implementation

| Area | Result |
| --- | --- |
| Home | Compact day/month and Overview header with Settings; navy financial card with actual Today's Expenses, monthly net expenses and actual received income; trailing budget value and clamped visual usage; exactly Add Expense/Add Fuel; shared calendar and compact upcoming payments. |
| Expenses | Existing cents-first entry, icon categories, Others-last/Fuel exclusion, E-wallet/Cash/Card memory, Calendar, history filters/search/favourites/Undo/refunds/receipts and swipes retained. Shared cards and heading alignment refined. |
| Fuel | Estimated weighted consecutive-ODO consumption remains primary. Monthly cost/latest price and Add Refuel precede history; charts follow history. Methodology uses an info dialog. Historical metadata and supplementary interval/full-cycle details remain accessible without dominating compact history cards. |
| Journey | Existing route form and result structure retained. Only shared typography, card padding and heading styling change. No formula, toll/parking, route, field serialization or saved-record change. |
| Analysis | Daily chart selection/centring/Today and child-gesture ownership retained. Income/expense comparison, category and monthly/budget/fuel analytics remain; negative net totals use a visible zero baseline. Monthly Plan vs Actual remains absent. |
| Settings | Financial Planning, Preferences, Privacy and Data, General groups. Allocation stays in Monthly Money Planner; actual income remains separate from expected planning. Independent settings writes remain intact. About reads installed package metadata. No gesture/dashboard arrangement pages reintroduced. |

## Shared design system and interaction

- Existing exact dark palette retained: background `#0B1220`, surface `#18263B`, accent `#2ABB91`, primary text `#F3F6FA`, muted text `#ADBED0`, divider `#35495B`.
- Coordinated warm light background `#F5F6F3`, white cards and existing accessible restrained emerald controls. System theme preference still follows Android and persists locally.
- `DesignTokens.kt` centralizes 16dp screen/section/card spacing, 20dp regular and24dp hero corners, 48dp touch dimension and bounded content width. Navy dashboard colors live in the same token layer and remain readable in both themes.
- Primary amounts32sp, main headings24sp, existing14–16sp body and12sp secondary labels. User font scale is retained. Shared cards have no decorative outline/shadow; selected controls retain accent indication.
- Shared header text now aligns at the same16dp gutter as Home. Removing duplicated decorative header icons also removes centre-alignment offsets caused by different subtitle heights. No whole-screen graphicsLayer/density scaling is used.
- Existing reusable press adapters now scale to.98 with90/130ms response/release. Existing Material semantics, touch bounds, haptic preference, keyboard focus and system/app reduced motion remain. No grey overlay is introduced.
- Phone bottom navigation retains Home/Expenses/Fuel/Journey/Analysis and the existing pager/saveable state. At enlarged font scales only the selected text label is painted, with more room, while every tab keeps its full accessible name and48dp+ target. Home actions share equal height when their labels wrap. Widths600dp and above use a rail; page content is bounded at840dp. Detailed forms remain outside paging. Editor width is bounded at560dp with its existing IME padding and scrollable content.
- Shared calendar always shows the selected date's exact total. Compact dates preserve48dp minimum height; narrow seven-column cell widths follow the available phone width. Expanded actual transaction details remain available.

## Visual evidence and verification

Home was rendered and visually inspected in Light/Dark at393×852dp before refining other screens. No design reference image was supplied, so the written specification was used; an exact screenshot match is not claimed.

The final host release gate passes:316 app test executions across39 suites,0 failures/errors/skips;4 build-policy tests and6 Python workflow tests also pass. [Per-suite results](docs/clarity-2026-10-18/TEST_RESULTS.md). Existing matched render tests cover all five screens at standard/narrow/accessibility sizes; new real-app tests cover480dp phone,852×393dp landscape and800dp tablet with populated local fixtures, rail/tab/swipe behavior and bounded expense entry. Contrast tests check normal text on primary/card/background/selected-control/dashboard surfaces in both themes. [45 retained host renders and measured geometry](docs/clarity-2026-10-18/SCREENS.md).

The complete ten-case native suite passes on the immutable1.5.3/code10 APK (`OK (10 tests)`,463.989s). The subsequent window-metrics correction is separately checkpointed at `a093f63`. Final1.5.4/code11 completes the release gate and passes four exact-APK native follow-ups (`OK (4 tests)`,154.001s): first-use offline OCR, permissions, actual production readiness/recreation and encrypted persistence. Installed SHA matches the download. Actual ready Home was also captured and inspected after standalone launch; the isolated fixture was removed and owner0/airplane mode1 restored. The optimized unsigned build also passes; final lint has0 errors,4 warnings and1 hint. [Native results and binary provenance](docs/clarity-2026-10-18/NATIVE_RESULTS.md).

## Actual layout causes and modified components

The shared `ScreenHeading` previously placed text beside a decorative leading icon and vertically centred it as a group. Different subtitle heights changed the heading position and added a different leading gutter from Home. The shared heading now uses one top-aligned text column at the same16dp content margin. Journey uses this same component and the existing shared cards/fields. Matching tests compare actual text, field and navigation bounds, rather than shrinking the entire screen. At393dp, Journey fields match the Expenses reference width/height; headings and bottom navigation align across all five screens. The earlier Journey-specific investigation remains in `docs/journey-layout/`.

Adaptive navigation initially read `Configuration.screenWidthDp`, whose inset behavior varies with target Android version. `LedgerApp` now reads `LocalWindowInfo.current.containerSize` converted through the current density; the600dp breakpoint and UI proportions are unchanged. This avoids a separate inconsistent screen-size source in resized windows.

| Files/components | Purpose |
| --- | --- |
| `DesignTokens.kt`, `FintechTheme.kt`, `Components.kt` | Shared dimensions/colors/type, card/header alignment and signed comparison bars. |
| `OverviewDashboard.kt`, `ExpenseToolsUi.kt` | Home financial hierarchy, equal action sizing and shared selected-calendar summary. |
| `Screens.kt`, `FinancialAnalysisUi.kt`, `JourneyScreen.kt` | Compact Fuel/history, existing Expenses, income/expense comparison and shared Journey heading. |
| `LedgerApp.kt`, `Editors.kt`, `PressComponents.kt`, `PressFeedback.kt` | Adaptive navigation/window size, bounded editors and reusable press/rail feedback. |
| `SettingsScreen.kt` | Grouped existing settings and installed-version About. |
| `ClarityHomeTest.kt`, `ClarityAdaptiveTest.kt`, `ClarityContrastTest.kt`, `ClarityChartTest.kt` | New UI/geometry/contrast/signed-chart regression coverage. Existing smoke/calendar/press expectations follow intentional label and semantics changes. |

The full audit-to-delivery file inventory is [MODIFIED_FILES.txt](docs/clarity-2026-10-18/MODIFIED_FILES.txt). No Room entity, migration, schema, backup format, fuel/Journey formula or application identifier changed in this UI phase.

Physical keyboard/IME behavior, camera/gallery providers, TalkBack, OEM launcher/widgets, native Android15 system back and sustained physical-device performance remain manual verification limits. Automated fixture tests do not certify unavailable phone contents.

## Branding status and differences

The user selected `Fuel_Expense_Logo_Option_4.png` and `Fuel_Expense_Android_Adaptive_Foreground.png`, to be supplied under `design-assets/`, and explicitly authorized skipping replacement if unavailable. Local files and fetched GitHub main contain neither asset. Existing launcher, adaptive/monochrome icons and splash branding are retained; no logo was invented or substituted. [Exact deferred status](docs/clarity-2026-10-18/LOGO_STATUS.md).

Optional existing receipt/station/fuel-type controls remain collapsed in Add Refuel to preserve earlier functionality; the core entry fields remain date, ODO, litres and amount. Hidden historical full-tank/location/note values are preserved. Home's eye remains the single in-app amount-mask control; widget visibility remains a separate preference. Other financial behavior, offline/security architecture, Room schema/migrations, package and signing identity remain intact.

## Delivered build

[FuelExpenseTracker_v1.5.4.apk](downloads/FuelExpenseTracker_v1.5.4.apk), version1.5.4/code11,76,275,157 bytes, signed debug with the original certificate. SHA256 `92ec7a5887651e60cf78dd6601c522fc14ffef574880e248ca5a002b6e50d1ca`. [Build/privacy verification](docs/clarity-2026-10-18/BUILD_RESULTS.md). Earlier artifacts1.5.2/code9 and1.5.3/code10 remain immutable, with their own actual metadata and verification results.

[GitHub publication and public APK checksum verification](docs/clarity-2026-10-18/PUBLICATION.md).
