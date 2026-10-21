# 15. Compose layout and a consistent design system

Compose describes the interface as functions of state. A `Column` arranges children vertically, a `Row` horizontally and a `Box` in layers. `LazyColumn` composes a scrolling list efficiently. Modifiers supply size, padding, drawing, interaction and semantics.

Modifier order matters. Padding outside a clickable element produces a different hit area from padding inside it. Clipping before a drawing operation can produce different results from clipping only a child. When a component looks wrong, inspect constraints and modifier order before changing font sizes.

## Constraints flow down; sizes flow up

A parent gives a child permitted minimum and maximum dimensions. The child measures within those constraints and reports its size. The parent places it. `fillMaxWidth()` fills the width offered by the parent; it does not mean “the physical phone width under every circumstance.”

Nested containers can accumulate padding. A Scaffold may already supply space for system bars and navigation. Applying equivalent insets again in a child can create an apparently enlarged or misaligned screen.

In this app, shared navigation and the primary Scaffold own the outer layout. Screens reuse shared components and spacing. The current `LedgerSpacing` specifies 16dp screen/card/section spacing, 20dp card radius, 24dp hero radius, a 48dp touch target and an 840dp content maximum.

These are current design tokens. The earlier Journey investigation used earlier values; do not copy its historical 20dp margin into today's design simply because the report measured it accurately.

## dp, sp and physical pixels

`dp` expresses density-independent layout size. `sp` expresses scalable text size. Pixels are actual image/device units. A 48dp control can occupy different pixel counts on different densities. Text height also depends on font metrics, line height and user font scaling.

Use semantic typography roles such as body, label, title and headline. Two equally important card titles should not use different sizes just because they were written in different files.

Large accessibility fonts can make a label wrap and increase a field's measured height. `singleLine` for text entry does not guarantee the floating label itself cannot wrap.

## Theme roles

`FintechTheme.kt` defines coherent light and dark palettes. Dark uses navy surfaces, readable light text and an emerald accent. The current dashboard deliberately retains its navy hero surface in both themes.

Avoid hardcoding white text into every reusable component; it can become unreadable on a light surface. Use theme roles such as `onSurface` for text on a surface, and verify contrast after choosing custom colors.

The local setting is System, Light or Dark. System observes the device's preference. A new installation defaults to System. Legacy migration preserves historical preferences, including the old default when reading older settings. Changing the default for new installs must not reset existing users.

## Previews and sample data

Compose previews provide quick visual feedback. Use deterministic synthetic records and the same device size, font scale, density and theme when comparing screens. Preview data should represent empty, typical and extreme states: long merchant names, negative remaining amounts and large text.

A preview may not execute camera providers, Android Keystore or native OCR correctly. Use it for what it can show, then test platform-dependent behavior on Android.

**Practice:** predict the effect of adding 16dp padding to both a parent and its card. Is the desired inner spacing 16dp or 32dp? Inspect the actual bounds rather than relying on visual intuition.

# 16. Navigation, swipes and accessible interaction

The current `MainPager.kt` uses the official Foundation `HorizontalPager`. At this snapshot its destination list is **Home, Expenses, Fuel, Journey, Analysis**. This differs from an earlier request in the conversation; the book documents the checked-in implementation rather than silently substituting an older order.

Bottom navigation and the pager use one destination mapping. Swiping updates the selected destination; tapping a navigation item animates to the corresponding page. Boundary pages do not wrap.

Settings, transaction forms and detailed configuration remain separate from the five-page swipe scope. An Add Expense keypad should not change to another main screen because the user drags near a control.

## Gesture ownership

Several children also handle horizontal movement: category rows, charts and sliders. A global horizontal gesture detector would compete with all of them.

The implementation uses Compose's scrolling and nested-scroll mechanisms. Child controls consume their own gestures. A boundary connection consumes leftover horizontal movement after a child has handled its scroll, preventing that remainder from unexpectedly turning into a page change. Vertical movement remains available to the vertical list.

This needs real testing at the ends of a child row, not only in its middle. A slider may behave correctly until it reaches its maximum and leaves horizontal motion unconsumed.

Android's system back gesture owns screen edges. Do not add a full-screen detector that captures those gestures indiscriminately. Host Compose tests can establish local gesture behavior, but physical/device-system behavior requires native verification.

## State preservation

Stable page keys help Compose identify pages. Page scroll state and practical form drafts should survive leaving and returning. Database and ViewModel objects are shared at the correct lifetime rather than rebuilt per swipe.

Adjacent pages may be drawn during dragging, but inactive pages should not expose duplicate focusable controls to accessibility. The project disables focus and clears semantics on inactive pages while preserving the visual pager behavior.

## Reusable pressed feedback

The app replaces unwanted grey ripple rectangles with shared interaction adapters. The current draw-only scale is **0.98**, with short press/release durations. Earlier requests mentioned approximately 0.97; the checked-in refined value is 0.98.

Scaling drawing is different from shrinking layout. Hit regions and semantic bounds stay intact. Selected categories use accent styling. Keyboard focus gets a visible rounded outline; removing ripple should not remove every indication of focus.

The implementation observes system animation settings, Compose motion scaling and the app's reduced-motion preference. Haptics respect the user's preference. Cancelled presses and multiple press interactions must restore the normal state.

## Practical accessibility checks

Try a large font, narrow screen, landscape orientation, a keyboard and a screen reader. Verify logical focus order, descriptions for icon-only actions, selected states, sufficiently large targets and useful error messages.

Do not add a content description to every decorative icon if adjacent text already supplies the same meaning; redundant announcements can make navigation exhausting. Conversely, a trash icon without a label needs a meaningful action description.

**Practice:** drag a category row to its boundary and continue dragging. The intended result is category-row interaction without an accidental page change. Then test a normal horizontal drag on noninteractive screen content.

# 17. Root-cause debugging: the Journey layout case

“Journey looks larger” is a symptom, not a diagnosis. The repository contains a dated investigation with matched screenshots and measured bounds. It is a strong example of replacing visual guesswork with evidence.

At the earlier checkpoint investigated on 16 October, four differences accumulated:

1. Journey used 20dp padding on every side while reference screens used 20dp horizontal, 8dp top and 28dp bottom. Its heading started 12dp lower.
2. Location suggestions emitted empty rows. In a spaced column, an empty child still occupied a spacing slot. The Origin-to-Destination gap measured 28dp instead of 14dp.
3. “Journey Planner” and a long distance label wrapped on narrow phones, increasing measured heights.
4. Some labels used a larger typography role than equivalent form or hero labels elsewhere.

The root was accumulated layout height and wrapping, not global zoom or a different screen density. Shrinking the whole screen with `graphicsLayer` would have concealed the symptom while damaging consistency and accessibility.

## The investigation method

Use the same viewport, density, font scale, theme and data on each reference screen. Measure heading position, card bounds, field width and height, spacing and navigation bounds.

At 393×851dp, the report measured Journey's heading top moving from 27px to 15px, matching reference screens. Field width was already correct at 313px. The Origin-to-Destination gap changed from 28px to 14px. Navigation bounds remained the same.

These numbers tell you what to fix and what to leave alone. Correct width is evidence against a global scale problem.

## Conditional layout, not empty placeholders

An important pattern is:

```kotlin
// Teaching example.
if (suggestions.isNotEmpty()) {
    Row { /* Render actual suggestions. */ }
}
```

Compare that with always adding a `Row` and leaving its contents empty. The latter can still affect a parent's spaced arrangement.

## Regression protection

The fix retained formulas, records and navigation. Matched reference screenshots were unchanged in the original comparison. Tests captured narrow, normal and large-font cases. The report also documented limitations, including shared large-font navigation behavior at that historical point.

Later UI refinements changed shared tokens and navigation behavior. Keep the report as evidence of a particular bug and method; do not present its old geometry as the current universal specification.

**Practice:** if a card is too tall only with one merchant name, inspect wrapping and content first. If every screen has excess top space, inspect shared insets first. If only Journey has excess top space, compare its root modifiers with the references.
