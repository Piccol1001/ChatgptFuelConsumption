# Changelog

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
