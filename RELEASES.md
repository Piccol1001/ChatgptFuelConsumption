# Known APK releases

Actual version metadata is read from each APK, independently of filenames. Historical files are unchanged. A first Git addition is repository evidence, not proof of the original release date. Unknown release dates remain unverified.

| Artifact | Actual versionName | Actual versionCode | Release date | Evidence |
| --- | --- | ---: | --- | --- |
| [Fuel-Expense-Tracker-debug.apk](downloads/Fuel-Expense-Tracker-debug.apk) | 1.0.0 | 1 | Unverified | First Git addition; original release date unverified; first Git addition 2026-10-15T04:42:57Z |
| [Fuel-Expense-Tracker-v1.2.0-debug.apk](downloads/Fuel-Expense-Tracker-v1.2.0-debug.apk) | 1.2.0 | 4 | Unverified | First Git addition; original release date unverified; first Git addition 2026-10-15T12:26:50Z |
| [Fuel-Expense-Tracker-v1.3.0-debug.apk](downloads/Fuel-Expense-Tracker-v1.3.0-debug.apk) | 1.3.0 | 5 | Unverified | First Git addition; original release date unverified; first Git addition 2026-10-15T13:28:56Z |
| [Fuel-Expense-Tracker-v1.4.0-comprehensive-upgrade-debug.apk](downloads/Fuel-Expense-Tracker-v1.4.0-comprehensive-upgrade-debug.apk) | 1.4.0 | 6 | Unverified | First Git addition; original release date unverified; first Git addition 2026-10-16T20:40:05Z |
| [Fuel-Expense-Tracker-v1.4.0-debug.apk](downloads/Fuel-Expense-Tracker-v1.4.0-debug.apk) | 1.4.0 | 6 | Unverified | First Git addition; original release date unverified; first Git addition 2026-10-15T15:59:05Z |
| [Fuel-Expense-Tracker-v1.4.0-expense-entry-debug.apk](downloads/Fuel-Expense-Tracker-v1.4.0-expense-entry-debug.apk) | 1.4.0 | 6 | Unverified | First Git addition; original release date unverified; first Git addition 2026-10-15T17:24:18Z |
| [Fuel-Expense-Tracker-v1.4.0-journey-layout-debug.apk](downloads/Fuel-Expense-Tracker-v1.4.0-journey-layout-debug.apk) | 1.4.0 | 6 | Unverified | First Git addition; original release date unverified; first Git addition 2026-10-16T13:21:30Z |
| [Fuel-Expense-Tracker-v1.4.0-minimal-header-debug.apk](downloads/Fuel-Expense-Tracker-v1.4.0-minimal-header-debug.apk) | 1.4.0 | 6 | Unverified | First Git addition; original release date unverified; first Git addition 2026-10-15T16:08:10Z |
| [Fuel-Expense-Tracker-v1.4.0-press-feedback-debug.apk](downloads/Fuel-Expense-Tracker-v1.4.0-press-feedback-debug.apk) | 1.4.0 | 6 | Unverified | First Git addition; original release date unverified; first Git addition 2026-10-16T01:00:08Z |
| [Fuel-Expense-Tracker-v1.4.0-swipe-navigation-debug.apk](downloads/Fuel-Expense-Tracker-v1.4.0-swipe-navigation-debug.apk) | 1.4.0 | 6 | Unverified | First Git addition; original release date unverified; first Git addition 2026-10-16T15:01:25Z |
| [FuelExpenseTracker_v1.5.0.apk](downloads/FuelExpenseTracker_v1.5.0.apk) | 1.5.0 | 7 | 2026-10-17 | Built and verified by scripts/release.py |
| [FuelExpenseTracker_v1.5.1.apk](downloads/FuelExpenseTracker_v1.5.1.apk) | 1.5.1 | 8 | 2026-10-17 | Built and verified by scripts/release.py |
| [FuelExpenseTracker_v1.5.2.apk](downloads/FuelExpenseTracker_v1.5.2.apk) | 1.5.2 | 9 | 2026-10-18 | Built and verified by scripts/release.py |
| [FuelExpenseTracker_v1.5.3.apk](downloads/FuelExpenseTracker_v1.5.3.apk) | 1.5.3 | 10 | 2026-10-18 | Built and verified by scripts/release.py |
| [FuelExpenseTracker_v1.5.4.apk](downloads/FuelExpenseTracker_v1.5.4.apk) | 1.5.4 | 11 | 2026-10-19 | Built and verified by scripts/release.py |

All recorded APKs use `my.fuelledger` and the original certificate SHA256 `4cb7dbc7bfd1eafb5b37339ddcc875174bf6f35139c0e6d87ba4b0f120c37ae8`. Reused historical version 1.4.0/code 6 is recorded honestly. No APK was relabelled or rebuilt to invent a different history.

The immutable per-artifact hashes and metadata are in [releases/history.json](releases/history.json). Future new artifacts must use [the release workflow](RELEASING.md); ordinary Gradle builds reproduce the current version without incrementing it. Compatibility requires the same package/certificate and a higher versionCode. The distributed builds are signed debug builds unless explicitly recorded otherwise.
