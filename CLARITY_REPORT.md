# CLARITY UI refinement

18 October 2026, Malaysia. Existing Kotlin/Compose app extended in place from audit checkpoint `f9ab990`. [Request](docs/clarity-2026-10-18/REQUEST.md). Financial-integrity fixes and cleanup are separately documented in [CLEANUP_REPORT.md](CLEANUP_REPORT.md).

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

Pre-release checks pass:316 app test executions across39 suites,0 failures/errors/skips;4 build-policy tests and6 Python workflow tests pass. Debug and instrumentation APKs build. The final versioned release gate and native run are pending. Existing matched render tests cover all five screens at standard/narrow/accessibility sizes; new real-app tests cover480dp phone,852×393dp landscape and800dp tablet with populated local fixtures, rail/tab/swipe behavior and bounded expense entry. Contrast tests check normal text on primary/card/background/selected-control/dashboard surfaces in both themes.

Physical keyboard/IME behavior, camera/gallery providers, TalkBack, OEM launcher/widgets, native Android15 system back and sustained physical-device performance remain manual verification limits. Automated fixture tests do not certify unavailable phone contents.

## Branding status and differences

The user selected `Fuel_Expense_Logo_Option_4.png` and `Fuel_Expense_Android_Adaptive_Foreground.png`, to be supplied under `design-assets/`, and explicitly authorized skipping replacement if unavailable. Local files and fetched GitHub main contain neither asset. Existing launcher, adaptive/monochrome icons and splash branding are retained; no logo was invented or substituted. [Exact deferred status](docs/clarity-2026-10-18/LOGO_STATUS.md).

Optional existing receipt/station/fuel-type controls remain collapsed in Add Refuel to preserve earlier functionality; the core entry fields remain date, ODO, litres and amount. Hidden historical full-tank/location/note values are preserved. Home's eye remains the single in-app amount-mask control; widget visibility remains a separate preference. Other financial behavior, offline/security architecture, Room schema/migrations, package and signing identity remain intact.
