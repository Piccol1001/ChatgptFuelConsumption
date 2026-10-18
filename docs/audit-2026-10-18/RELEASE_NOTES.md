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
