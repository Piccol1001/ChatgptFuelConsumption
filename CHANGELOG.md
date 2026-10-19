# Changelog

## 1.5.4 — 2026-10-19

### New features

- None; this patch completes the CLARITY UI verification.

### Bug fixes

- Choose adaptive bottom navigation or navigation rail from the current Compose window width rather than Android configuration screen width, including resized and multi-window layouts.

### UI improvements

- Preserve the same 600dp navigation breakpoint, typography, spacing and screen state while using the current window-metrics API.
- Retain the supplied-logo replacement as deferred until the actual Option 4 PNG files are available.

## 1.5.3 — 2026-10-18

### New features

- None outside the requested refinement of existing UI and navigation.

### Bug fixes

- Align all main-screen heading text at the same content gutter; subtitle height no longer shifts headings beside decorative icons.
- Bound detailed editor widths on wide screens and preserve IME padding/scroll behavior.
- Keep Home actions equally tall at large font sizes and show the selected navigation label without breaking words.
- Plot negative net expenses on the negative side of comparison-chart zero baselines.
- Verify native production readiness/recreation with a bounded software-emulator budget for observed encrypted queries; retain earlier timeout evidence.

### UI improvements

- CLARITY Home: navy card with actual today's/monthly expenses and received income, trailing monthly budget, two Add actions, embedded calendar and upcoming payments.
- Shared16dp card/screen spacing,24sp main headings,32sp primary figures, warm Light and navy/emerald Dark/System themes; normal-text contrast checks.
- Compact Fuel dashboard, info-only methodology, history before charts and retained historical details.
- Grouped Settings, consistent Expenses/Journey/Analysis styling, income/expense comparison and always-readable selected calendar total.
- Adaptive navigation rail on wide screens; phone bottom navigation and existing pager/gesture ownership remain.
- Reusable.98 press feedback retains accessibility/reduced-motion behavior without grey rectangular overlays.
- Selected Option4 launcher replacement is deferred: supplied PNGs are not yet available, per explicit user instruction. Existing branding is unchanged.

## 1.5.2 — 2026-10-18

### New features

- None. This release audits and maintains the existing offline application.

### Bug fixes

- Reject Undo when later refunds would exceed the restored purchase amount; keep the current record and Undo entry intact.
- Reject linking a refund as payment of a fixed commitment, and validate the resulting expense before storing it.
- Add native production-Activity readiness and recreation acceptance coverage alongside the encrypted-storage tests.

### UI improvements

- Preserve existing screen layout, typography, pager gestures, theme behavior and independent Settings switch updates.
- Remove unused imports, two unused generated name/version strings and the empty custom ProGuard file.
- Remove empty Room/WorkManager KTX compatibility artifacts; retain their active runtime libraries and versions.
- Share fuel-point validation, reuse transaction snapshots and replace repeated refund scans with ID lookups without changing formulas or data formats.

## 1.5.1 — 2026-10-17

### New features

None. The verified 1.5.0 feature build is retained unchanged.

### Bug fixes

- Centre calendar-day content and the actual chart plot within shared Material cards. MaterialCard's ColumnScope allowed the inner Column to wrap at its leading edge; filling its width restores the intended alignment.
- Strengthen centring regressions to measure the actual day text and plotted area, including chart endpoints and the Today action, rather than only card bounds.

### UI improvements

- Consistent inner alignment for calendar highlights and the daily expenditure chart, without changing financial data, navigation, Journey layout, typography or touch targets.

## 1.5.0 — 2026-10-17

### New features

- Embed the same Spending Calendar on Home and Expenses; select a day to inspect its records.
- Add a virtualized daily expenditure chart centred on today with selection and a Today action.
- Add verified semantic-version release management, immutable APK metadata history and failure-safe version advancement.

### Bug fixes

- Stop preference saves from disabling unrelated Settings controls and flashing a global loading bar; merge rapid preference updates atomically.
- Serialize overlapping widget snapshot/render batches and safely complete receiver callbacks.
- Use one weighted provisional fuel estimate after two valid odometer records, without requiring full-tank flags, throughout Fuel, Journey and analytics.
- Right-align the budget amount, retain hidden refuel metadata when editing, and preserve the original signing certificate for in-place updates.

### UI improvements

- Show Today's Expenses on Home with two Expense/Fuel actions.
- Order main pages Home, Expenses, Fuel, Journey, Analysis; order payments E-Wallet, Cash, Card.
- Simplify refuel entry with RON95 default and exact cents keypad; omit dedicated Fuel from manual category icons without deleting historical records.
- Remove Analysis' Monthly plan vs actual card and compact Settings while keeping planning, scanner, income, backups and category management accessible.
- Allow screenshots in app and dialog windows while preserving optional authentication and encrypted storage. Screenshot capture reduces protection against authorized recording mechanisms.
- Respect reduced motion for interactions, navigation and expandable sections.

## 1.4.0 — original release date unverified

### New features

The retained APKs at this version include successive offline receipt scanning, financial planning/allocation, app lock and encryption, main-screen pager and comprehensive expense/calendar/Journey/report/widget improvements. These artifacts reused versionCode 6; see RELEASES.md for each binary's actual metadata and first Git record. No separate version is retroactively assigned to them.

### Bug fixes

Historical validation and progress reports retain the fixes and test evidence for each artifact.

### UI improvements

Successive fintech UI, cents-first expense entry, press feedback and Journey layout work are preserved in the historical reports.

## 1.3.0, 1.2.0, 1.0.0 — original release dates unverified

Historical APK version metadata is verified in RELEASES.md. Complete contemporaneous release notes are unavailable; no release dates or feature assignments are invented.
