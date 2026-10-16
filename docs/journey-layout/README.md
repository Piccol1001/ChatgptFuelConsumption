# Journey layout investigation — 16 October 2026

Baseline: commit `313d66e`. This is a UI-only correction of the existing app. Home, Fuel and Expenses are the reference; their rendered screenshots remain byte-for-byte unchanged in the three configurations captured before and after this fix.

## Actual causes

1. `JourneyScreen` used `PaddingValues(20.dp)`, while the reference screens used 20dp horizontal, 8dp top and 28dp bottom padding. Journey's heading therefore started **12dp too low**.
2. `LocationSuggestions` always emitted a `Row`, even with no matching locations. `LedgerCard` uses a Column with 14dp spacing; each empty row still added another spaced slot. The measured Origin-to-Destination gap was **28dp instead of 14dp**. Two empty rows added 28dp to the form.
3. The long heading **Journey Planner** wrapped at 320dp into two lines (64px measured text bounds versus 30px for the reference headings). The redundant **One-way / outbound distance (km)** label also wrapped, increasing that input's height. Input `singleLine` governs entered text, not the independently laid-out label.
4. The form-card title and estimated-total label both used `titleLarge` (22sp/30sp line height), unlike existing form-card headings (`titleMedium`, 16sp/24sp) and hero labels (`labelLarge`, 14sp/20sp). Their semantic roles now use those existing styles. The prominent cost amount remains 36sp/44sp, matching the dashboard hero amount.

These were accumulated layout height and text-wrapping differences, not a global zoom, altered display density or different theme.

## Implementation comparison

| Property | Home / Fuel / Expenses | Journey before → after |
| --- | --- | --- |
| Root / width | LazyColumn under the app's shared Box/Crossfade; constrained by the same viewport | Same; unchanged |
| Horizontal margins | 20dp each side | Already 20dp; unchanged |
| Top / bottom content padding | 8dp / 28dp | 20dp / 20dp → same shared 8dp / 28dp |
| Heading | Shared ScreenHeading, 26sp/34sp; 46dp icon, 24dp glyph, 48dp Settings touch target | Same component and sizes; shorter visible title “Journey” prevents unnecessary wrapping |
| Card width / shape | Available width minus 40dp; LedgerCard 22dp radius | Already identical; unchanged |
| Card padding / child spacing | 20dp / 14dp in LedgerCard | Unchanged; empty suggestion rows no longer add extra gaps |
| Between list items | Home/Fuel 16dp; Expenses 14dp | 16dp, matching Home/Fuel; unchanged |
| Input controls | Shared TextField/NumericField wrapping Material OutlinedTextField; bodyLarge 16sp/24sp, 56dp minimum surface | Same controls; shorter labels preserve units/context and avoid wrapping at normal font sizes |
| Buttons | Shared Material adapters; LedgerAction and Journey toggle use 56dp minimum, standard Material controls retain accessible minimum touch targets | Existing shared buttons and 56dp toggle unchanged |
| Scrolling | One vertical LazyColumn; nested horizontal rows where needed | Same; explicit list state retained for Edit/load scrolling; suggestions remain horizontal |
| Scaffold / bars / navigation | One LedgerApp Scaffold owns status/system insets and bottom navigation | Same instance and destination order; no nested Scaffold or inset modifiers added |
| Theme / density / scale | LedgerTheme / LedgerTypography; inherited Android density/font scale | Identical; no local density override or whole-screen scaling |
| Press feedback | Shared draw-only 0.97× feedback while pressed | Unchanged; cannot explain the resting layout difference |

Shortened field labels are **Outbound distance (km)**, **Fallback (L/100 km)**, **Override (L/100 km)** and **Parking (RM)**. Input validation, state, saved values and callbacks are unchanged. Suggestions retain the exact filtering, limit and click behavior; only the empty layout container is omitted.

## Measured result

At 393×851dp, mdpi, font scale 1.0:

| Measurement | Before Journey | After Journey | Reference |
| --- | ---: | ---: | ---: |
| Heading text left / top | 80 / 27px | 80 / 15px | 80 / 15px |
| Heading text height | 30px | 30px | 30px |
| Field left / width | 40 / 313px | 40 / 313px | Expenses search: 40 / 313px |
| Origin field height | 65px | 65px | Expenses search: 65px |
| Origin → Destination gap | 28px | 14px | LedgerCard spacing: 14dp |
| Bottom navigation viewport | y=771–851px | y=771–851px | y=771–851px |

At 320×640dp, Journey now has one heading line and a 65px outbound field, matching its Origin field and the Expenses search field. Its heading starts at y=15px, matching Home/Expenses (Fuel is y=14px due to its naturally wrapping subtitle). No text size, device density or touch target is artificially scaled down.

The 65px text-field semantic height includes the Material label allowance above the 56dp outlined surface; it is not a custom enlarged control.

## Matched screenshots

All images come from the actual MainActivity and its real navigation, rendered with Robolectric native graphics on API35. Each row uses exactly the same viewport, density, font scale and theme for all four screens. Screenshots are captured after navigation/theme transitions settle. Empty local test data is used consistently.

| Configuration | Home | Fuel | Expenses | Journey |
| --- | --- | --- | --- | --- |
| 393×851dp, mdpi, 100%, Dark | [Home](after/393-Dark-Home.png) | [Fuel](after/393-Dark-Fuel.png) | [Expenses](after/393-Dark-Expenses.png) | [Journey](after/393-Dark-Journey.png) |
| 393×851dp, mdpi, 100%, Light | [Home](after/393-Light-Home.png) | [Fuel](after/393-Light-Fuel.png) | [Expenses](after/393-Light-Expenses.png) | [Journey](after/393-Light-Journey.png) |
| 320×640dp, mdpi, 100%, Dark | [Home](after/320-Dark-Home.png) | [Fuel](after/320-Dark-Fuel.png) | [Expenses](after/320-Dark-Expenses.png) | [Journey](after/320-Dark-Journey.png) |
| 393×851dp, mdpi, 160%, Light | [Home](after/393-font160-Light-Home.png) | [Fuel](after/393-font160-Light-Fuel.png) | [Expenses](after/393-font160-Light-Expenses.png) | [Journey](after/393-font160-Light-Journey.png) |

Baseline images and geometry are retained in [before/](before/); updated geometry is in [after/](after/). Screenshots are test artifacts, not receipts or real financial records.

## Verification and limits

Signed debug and optimized unsigned release APKs build successfully. Release vital lint and debug/release 16KiB ZIP alignment checks pass. Debug lint has 0 errors, the same 10 advisory warnings and 1 hint as the baseline: six preserved unused app-name/version entries, one SQLite update suggestion, one state-backed offset suggestion, two KTX suggestions and one primitive-state hint. Native API26 in-place installation succeeds without clearing data; launch reports `Status: ok`, and MainActivity is confirmed as the resumed foreground activity. This native check covers startup, not a rerun of the historical financial/OCR instrumentation suite.

The large-font run revealed an existing shared-navigation behavior: long Expenses/Analytics labels wrap at 160% font scale on every screen. This is not Journey-specific; the shared navigation is intentionally unchanged. Large accessibility text can naturally increase component heights and scrolling. Reference screen item spacing remains 14dp for Expenses versus 16dp for Home/Fuel/Journey. These existing differences are documented rather than altering the reference screens.

Host screenshots do not validate every OEM/font/density combination, physical keyboard, TalkBack or on-device system-bar rendering. No physical-device screenshot is claimed. There is no known Journey-specific clipping/overlap in the inspected configurations; content below the viewport remains available by scrolling.

## Modified implementation files

- `ui/JourneyScreen.kt`: shared padding, no empty suggestion rows, concise heading/labels and appropriate existing card text roles.
- `ui/Components.kt`: moved the exact existing screen-padding value into an internal shared token.
- `ui/Screens.kt`: reference screens use the relocated token with identical values and rendering; no layout redesign.
- `ui/AppSmokeTest.kt`: selectors updated for the concise labels; existing calculation/save/load assertions retained.
- `ui/JourneyLayoutTest.kt`: matched real-app captures and geometry regression tests, including small phones and Android 160% font scaling.
- `README.md`, `PROGRESS.md`, `VALIDATION.md`, this report and screenshot evidence, plus the new APK and checksum.

No changes to JourneyViewModel, journey formulas/data classes, toll computation, Room entities/migrations, saved journeys, permissions, security, dependencies, app name/version or navigation order.
