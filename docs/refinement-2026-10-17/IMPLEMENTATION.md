# Corrective UI, fuel and release-management update

Baseline: `4ef21fc` (protected by `checkpoint/refinement-2026-10-17`). [Original refinement request](REQUEST.md). The existing Kotlin/Compose screens, ViewModels, Room 5 database, encrypted preferences and navigation were modified in place. No table/entity/migration change or runtime dependency was needed.

## Changes

- **Home:** actual local-day net expenditure from the existing expense stream, including each linked fuel expense once, excluding income. Date refresh runs at midnight, foregrounding and Android date/time/zone broadcasts. Budget progress clamps to 0–100%, while the label identifies over-budget usage. Its amount label uses a weighted trailing-aligned Text. Only two equal Expense/Fuel actions remain. The shared embedded calendar supports month navigation, today/selection highlights, activity/compact amounts and exact selected-day records. Upcoming payments remain, with overdue labels when needed. Monthly planning remains in Settings.
- **Expenses:** the same calendar component/source is embedded above history. The heading still precedes the horizontal category filter and Others remains last. Only the dedicated Fuel category is omitted from manual icon choices; custom Transport/Parking/Tolls remain. History and its Fuel filter remain accessible. Existing fuel-category edits keep their IDs/payment strings; old fuel favourites route to Add Refuel instead of posting a duplicate generic expense. Linked refuel rows omit duplicate/favourite actions. Payment icons are E-Wallet, Cash, Card, with the prior last-used preference and raw historical credit/debit strings retained. Scan receipt remains accessible from Expenses; income remains available through the planner/received-income flow and existing widget shortcut.
- **Navigation:** one official pager and existing selected-page state now order Home, Expenses, Fuel, Journey, Analysis. Stable keys, activity ViewModels, saveable drafts/scroll and local child-gesture ownership are retained. No duplicate routes/back-stack entries were added.
- **Fuel:** two valid increasing-odometer/date records enable a provisional estimate. Every interval uses the later purchase's litres; the baseline purchase is excluded. Historical L/100 km is `100 × sum(included litres) / sum(included distance)` and km/L is its reciprocal `100 / L/100 km`. Invalid intervals produce no estimate. Full-tank flags are irrelevant to this primary calculation. Fuel, Journey, trend/change analysis and recorded cost estimates use this same interval source. Labels explicitly say estimated/provisional and the optional help explains the equal-post-refuel-level assumption and partial-fill uncertainty. Existing verified full-cycle details remain as historical supplementary information only.
- **Refuel editor:** date/time, ODO, litres, exact cents-first payment and actual calculated RM/L; RON95 for new entries. Receipt/scanner, optional fuel type and station stay under a compact details control. Removed location, full-tank and notes inputs. Existing hidden values, old fuel types, sen amounts, unchanged timestamps and attached images survive edits. No old record is relabelled RON95 merely because its controls are hidden.
- **Journey:** route inputs, layout, fees, formulas, saved routes and one-way/round-trip controls are retained. Only the shared fuel source/help and common animation policy change.
- **Analysis:** removed the Monthly plan vs actual card, retaining actual income/net expense, budgets, other category/monthly/fuel analysis and exports. Daily history uses a lazy horizontal list with fixed bar pitch, symmetric edge space and an initially centred Today. Today is highlighted; selection shows exact date/amount; missing dates are zero. The window includes the selected range plus context around today. Scroll/selection are saveable, Today recentres, negative refund days draw below the baseline, and child drags do not navigate the pager.
- **Settings:** one planner entry, recurring transactions, appearance, scanner, privacy/security, category management, backup/export and About. Journey defaults, merchant rules, categories and local storage are collapsed rather than removed. Removed gesture/feedback and dashboard arrangement entries; no renamed equivalents were added. Existing gesture/haptic/reduced-motion preferences remain stored/honoured where applicable; Home now consistently shows the required default sections. The duplicate amount-mask control is removed from Settings because Home's eye owns it; widget visibility retains its necessary separate control.
- **Animations/capture:** common press/selection/navigation/expansion/calendar animations respect system and app reduced motion. Existing rounded 0.97 press behavior/independent sources remain. MainActivity clears FLAG_SECURE and app dialogs explicitly use SecureOff; the app-lock window helper also clears the flag while continuing to hide/block locked dialogs. Screenshots are allowed in normal screens and app dialogs. This reduces protection against device screenshots, thumbnails and authorized recording; optional authentication, encrypted storage/backups and offline OCR remain.

## Settings blinking: reproduced cause and fix

Before changes, `LedgerViewModel.saveSettings` called `perform`. That synchronously set the global `_busy` flag true. `SettingsScreen` consumed that flag as `enabled = !busy` on unrelated buttons/switches, and `LedgerApp` displayed its page-wide loading indicator. A preference tap therefore changed many controls' enabled colours and the loading state before restoring them. The regression failed on both API26 and API35 before the fix (`/tmp/refine-before.log`).

Each adapter already used its own remembered MutableInteractionSource. Haptics were not the cause: ordinary Settings switches did not call the manual haptic path, and independent interaction sources were preserved.

Preference writes now have an independent coroutine path that does not set the global transaction busy flag or show a success snackbar/spinner. `LedgerRepository.updateSettings` applies transforms under its existing writes mutex, merging each change with the latest persisted settings so rapid unrelated updates cannot overwrite each other. Failures still reach the existing error UI. Actual data mutations/restore retain their busy guards. Stable Settings item keys avoid composition shifts.

Verification includes keeping a real preference write blocked at the repository lock, toggling the receipt switch and comparing an unrelated button's pixels/enabled state before/after. Concurrent preference transforms and encrypted-store round trips are also checked. Theme changes intentionally recolour the app; this is distinct from the reproduced enabled/disabled flashing.

## Widget refresh reliability

The first release gate caught concurrent RemoteViews reapplication while receiver refreshes and explicit refreshes overlapped. Updates now serialize snapshot/render batches under one mutex, including the safe-unavailable fallback. This also prevents an older widget privacy snapshot from finishing over a newer refresh. Receiver completion tolerates direct provider callbacks without an asynchronous PendingResult. A concurrent refresh regression retains actual RemoteViews inflation and privacy checks.

## Version policy and history

Ten retained APKs were inspected with Android build-tools, not inferred from filenames: version 1.0.0/code1; 1.2.0/code4; 1.3.0/code5; seven distinct 1.4.0/code6 artifacts. All verify against the original certificate SHA256 `4cb7dbc7bfd1eafb5b37339ddcc875174bf6f35139c0e6d87ba4b0f120c37ae8`. No attached APK was found in the upload directory; current debug/unsigned-release outputs were also inspected. Historical dates distinguish first Git addition from an unverified original release date.

`version.properties` feeds Gradle and the launcher label. Settings → About → App Version reads the installed PackageManager metadata; main headers show no version. `scripts/release.py` selects a semantic bump and a strictly greater code using known/actual artifact metadata, checks the existing external signing identity before building, runs tests/builds/lint, validates the binary package/version/certificate/alignment/offline permissions, then writes the new APK and release metadata. A failed build or validation consumes no version. Old files/ledger rows remain intact. [RELEASING.md](../../RELEASING.md), [RELEASES.md](../../RELEASES.md), [CHANGELOG.md](../../CHANGELOG.md) and repository AGENTS.md define the future workflow.

## Visual alignment patch

Screenshot review of the first successful 1.5.0/code7 build found that MaterialCard’s ColumnScope let nested content wrap at the leading edge. Calendar date text and chart plots could be offset despite centred card bounds. Both inner Columns now fill their width, and regressions measure the actual day text and plot bounds. The first artifact remains unchanged; the corrected build uses semantic patch **1.5.1/code8**. This preserves actual build history rather than replacing an old file under the same version.

## Verification and delivery

FINAL_RESULTS_PENDING

## Limitations

- Fuel remaining in a tank is unknown. Estimates from arbitrary partial fills can be inaccurate; this is disclosed, never presented as exact measurement or mechanical diagnosis.
- A seven-column calendar uses compact amounts with exact values in accessibility/selected-day details. Very large values can abbreviate; stored sen never change.
- Daily history deliberately includes today/context even when another report range is selected; the other range-based analytics stay filtered.
- No physical camera/gallery, external share/document provider, OEM launcher/widget, TalkBack or sustained performance pass is claimed. Native system edge-gesture verification still requires a reliable physical/accelerated device.
- Android/system authentication and external picker windows retain their own system screenshot rules; only this app's windows were changed.
