# Main-screen swipe navigation — 16 October 2026

Implemented in the existing Kotlin/Compose app, from checkpoint `a76e227`. No database, financial or journey-calculation implementation, manifest, dependency or app-version change.

## Navigation and state

- Official Foundation `HorizontalPager` and `rememberPagerState`, using the project's existing Compose dependencies (BOM 2026.09.00). The old main-screen Crossfade is replaced, not nested inside another navigation system.
- Order: **Home → Fuel → Expenses → Journey → Analysis**, without wrapping. The final page reuses `AnalyticsScreen` and its stable `Analytics` key; the bottom label is Analysis. Existing screen content/title remains intact.
- Pager `currentPage` is the only selected-page source. Bottom taps call `animateScrollToPage`; a new tap cancels the preceding navigation job. Native Foundation touch slop, drag rendering, fling and snap behavior remain. Physical left/right order is maintained in RTL as well.
- Stable page keys and Foundation's page saveable state are enclosed in a retained `SaveableStateHolder`, so leaving the pager for Settings/scanning/planning also preserves page drafts, filters and scroll state. The pager's own selected page is saveable.
- Ledger, Journey and Finance ViewModels remain scoped to the same activity and use the same existing application repository/database. Finance state has one shared collection. No per-swipe ViewModel or database creation. Models are obtained only after the existing ledger-ready/category gate, preserving IO initialization sequencing.
- Neighboring pages render during a drag; inactive page controls are excluded from accessibility/focus. This prevents offscreen controls from competing with the current screen. Accessible bottom tabs retain native selected/click semantics.
- The existing single Scaffold, system insets and bottom bar remain. No extra page indicator, whole-screen scale, font, card or spacing change. Main content pixels in all 16 existing matched screenshots are identical to the prior Journey-layout build when the bottom navigation label is excluded.

## Gesture ownership and detail scope

- A pager-level nested-scroll connection consumes **unused horizontal child-scroll deltas and flings** before they reach the pager's scrolling ancestor. This includes horizontal category/date-range/chart/location-suggestion rows at both boundaries. It returns zero vertical consumption. Direct page drags remain native Foundation pager gestures.
- Journey's existing native horizontal drag recognizer and Material slider recognizers keep their pointer ownership; no global gesture detector is installed.
- Static chart cards reserve horizontal gestures only within their own bounds. Vertical scrolling still works through these regions. This is a local chart gesture guard, not a screen-wide interception layer.
- Settings, Monthly Money Planner configuration and Receipt Scanner replace the pager in the existing detail branch. Expense/refuel editors remain modal windows and explicitly disable underlying pager gestures; other native dialogs also own their input window. Existing app-lock and backup/restore safeguards remain.
- Scanner retains its existing bottom bar: a tab tap closes it and opens the requested main page. Existing Back handlers for details/dialogs remain in place.
- No system-gesture exclusion, edge interceptor or main-screen Back handler is added. Android continues to arbitrate edge-back gestures; the pager does not implement back-stack entries.

## Test results

**240 host executions pass**, zero failures/errors/skipped. Ten new executions are five `MainPagerTest` cases on APIs26 and35. Full existing finance, fuel, receipt, data, lock, planning, layout and UI suites also pass.

| Test area | Verification |
| --- | --- |
| Main page swiping | Actual touch drags through all five pages in both directions; Home/Analysis boundary drags do not wrap |
| Partial drag | Home and Fuel are both visibly placed while the finger remains down |
| Small movement | A sub-touch-slop movement leaves Home selected |
| Bottom navigation | All five taps, exactly one selected tab, repeated final-request convergence |
| Horizontal categories | Actual horizontal row drags in both directions and repeated drags at either edge preserve Expenses; selection/filter state survives page changes |
| Journey toggle | Actual drag changes One Way to Round Trip without changing page |
| Charts and vertical lists | Chart horizontal drags do not page; vertical Fuel list drag changes scroll position without changing page |
| Draft and scroll retention | Journey origin/destination/distance/consumption and saved-list scroll position survive distant pages and Settings |
| Activity recreation | Selected Journey page, unsaved origin, scroll position and Journey ViewModel identity survive recreation |
| Editors and scanner | Expense keypad value survives horizontal dialog drag; dialog Back dismisses through its own dispatcher; scanner has no pager, Back returns, and bottom taps reopen the requested page |
| Planning slider | Actual Savings slider drag changes its value, with no pager in planning configuration |
| Android system Back | Activity/detail and dialog Back dispatchers pass host tests; native edge gestures remain unverified due to emulator-wide ANRs |
| Themes / visuals | Light and Dark interaction cases, plus existing Light/Dark/small-phone/160% font layout captures; all 16 main-content image regions match the baseline pixel-for-pixel |

Test development caught offscreen accessibility exposure, which was fixed in the app. Test selectors distinguish native Tab roles from Fuel category controls. Robolectric activity access runs on the test/UI thread; dialog Back is dispatched to the dialog rather than the activity. These test-harness corrections preserve all behavioral assertions.

Signed debug and optimized unsigned release builds pass; release vital lint and debug/release 16KiB ZIP alignment pass. Final debug lint has 0 errors, the same 10 advisory warnings and 1 hint as the baseline (six preserved unused name/version entries, one SQLite update suggestion, one offset suggestion, two optional KTX suggestions and one primitive-state hint). Native API26 in-place installation succeeds and UIAutomator confirms the ready Dashboard after waking/unlocking the headless emulator. Native API35 edge-back testing was attempted in gestural/airplane mode but did not complete: the software emulator showed app startup ANRs plus system-wide failures, including a system_server main-handler block and com.android.phone startup failures. No native edge-back pass is claimed. Verify system edge Back and cold startup on a physical phone or hardware-accelerated Android15 emulator. Test navigation/airplane settings were restored.

## Artifacts and files

- Updated signed debug APK: `downloads/Fuel-Expense-Tracker-v1.4.0-swipe-navigation-debug.apk`, Android8.0+, 76,098,095 bytes.
- SHA256: `873fa8048054e902dde025e1801df502f8eaaaa1ae84ec46e92f45696e63a2c0`. Original package, version1.4.0/code6 and signing certificate are retained; all older APKs remain.
- Production UI changes: `ui/MainPager.kt` (new reusable pager/gesture policy), `ui/LedgerApp.kt` (pager/state/navigation integration), `ui/Components.kt` and `ui/Screens.kt` (local chart guards).
- Tests: `ui/MainPagerTest.kt` (new), `ui/AppSmokeTest.kt` (Analysis navigation-label selector).
- Reports/artifacts: `README.md`, `PROGRESS.md`, `VALIDATION.md`, this report, new APK and `downloads/SHA256SUMS`.

## Resume / reproduce

```sh
cd /workspace/ChatgptFuelConsumption
source /workspace/.tooling/fuel-ledger-env.sh
./gradlew :app:testDebugUnitTest --tests my.fuelledger.ui.MainPagerTest
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
```

Run release assembly and debug lint sequentially, after finishing source edits. Host logs are `/tmp/fuel-pager-{compile,targeted,validation,release,lint}.log`; native logs are `/tmp/fuel-pager-final-native26.log`, `/tmp/fuel-pager-final-native35.log`, `/tmp/fuel-pager-native35-gestures.log` and `/tmp/fuel-pager-native35-{system,latest}-anr.txt`. API26 ready-screen evidence is `/tmp/fuel-pager-native26-window-awake.xml`. Native startup launch commands can time out under software emulation, so a resumed Activity alone is not counted as a ready UI. Use the original signing identity for updates and never uninstall/clear app data to apply this UI update. Private signing keys, credentials and databases stay outside Git.

For the remaining native check, use a physical phone or accelerated Android15 emulator with gesture navigation. From Fuel, compare a center rightward swipe (Home) with an edge rightward swipe (Android Back). Reopen the app, enter Settings from Fuel and verify edge Back restores Fuel. Repeat from the opposite system edge and check first cold launch. The app adds no gesture-exclusion regions or main-screen Back handler.
