# PROJECT: CLARITY — Complete Android UI/UX Refinement

## Role

Act as an expert Android UI/UX designer, senior Jetpack Compose developer and mobile design-system architect.

I want to improve the visual design, interaction quality and usability of my EXISTING Fuel & Expense Tracker application.

The objective is a premium, minimalist Android application similar in quality to a professionally designed financial management product.

**Do not rebuild the project. Do not add unrelated features. Focus on design quality and implementation accuracy.**

Use the provided design reference screenshot, if attached, as the visual source of truth for the Home screen.

Preserve:
- All existing financial data.
- Current database and migrations.
- Working financial calculations.
- Existing feature functionality.
- Fully offline operation.
- App signing compatibility.
- Light, Dark and System themes.

---

# 1. DESIGN PHILOSOPHY

Design concept: CLARITY.

Core principles:

1. Minimalist, not empty.
2. Premium, not decorative.
3. Functional, not overloaded.
4. Consistent across all screens.
5. Fast for repetitive daily usage.
6. One clear primary action per context.
7. Display numbers more prominently than descriptions.
8. No duplicated controls or settings.
9. No unnecessary borders or visual effects.
10. Use actual application data, not hardcoded demonstration values.

Avoid generic Android tutorial styling.

Avoid overly large cards, unnecessary gradients, excessive shadows, large blank spaces and repetitive explanatory text.

The interface should look polished and intentional.

---

# 2. GLOBAL DESIGN SYSTEM

Implement a reusable Compose design system.

## Dark Theme

Background: #0B1220
Card surface: #18263B
Primary dashboard card: #14283D
Accent: #2ABB91
Primary text: #F3F6FA
Secondary text: #ADBED0
Dividers: #35495B

## Light Theme

Create a coordinated light theme with:
- Warm or neutral off-white background.
- White or lightly tinted cards.
- Dark readable primary text.
- Restrained emerald accent.
- Subtle borders and dividers.

Maintain accessible contrast.

## System Theme

Follow the Android device theme automatically.

All colours must come from centralized theme tokens.

Avoid hardcoding dark-theme text colours in reusable components without theme handling.

## Dimensions

Use consistent design tokens:

- Screen horizontal padding: 16dp.
- Typical section spacing: 16–20dp.
- Card corner radius: 18–24dp.
- Card internal padding: 16dp.
- Screen heading: approximately 22–24sp.
- Primary amounts: approximately 28–34sp.
- Regular text: 14–16sp.
- Secondary labels: 12–13sp.
- Minimum interactive touch target: 48dp.

Respect user font-scale preferences.

Do not create oversized components on the Journey screen.

---

# 3. HOME SCREEN — HIGH-PRIORITY REDESIGN

The Home screen must closely match the supplied design reference.

Follow this order exactly:

## A. Compact Header

Left:
- Current day and month.
- "Overview" heading.

Right:
- Settings icon.

Do not display the application version in the header.

## B. Main Financial Card

Use a dark navy rounded card.

Display:

Today's Expenses

Below:
Large MYR figure representing the actual amount spent today.

After a subtle divider:

Two compact columns:
- This Month: actual monthly expenses.
- Income Received: actual income received this month.

Use correct financial calculations.

Do not display:
- Smart Safe-to-Spend.
- Daily safe-spending recommendation.
- Available After Fixed Commitments as the main Home figure.

Keep Monthly Money Planner accessible in Settings.

## C. Monthly Budget

Place directly underneath the main financial card.

Display:
- "Monthly Budget".
- Budget usage percentage.
- Thin horizontal progress bar.
- Amount spent aligned LEFT.
- Total budget aligned RIGHT.

Ensure the "RM XXXX budget" label is positioned at the rightmost edge, not centred.

Clamp the visual progress bar to its supported display range while still showing over-budget values accurately in text.

## D. Two Quick Actions

Display exactly two equal-width buttons:

- Add Expense.
- Add Fuel.

Use a two-column layout.

Each button should have:
- Recognizable icon.
- Short label.
- Rounded container.
- Subtle pressed-state animation.

Do not add Income, Scan or Calendar as additional quick-action buttons.

Keep income entry and receipt scanning accessible from their existing relevant screens.

## E. Embedded Spending Calendar

Place a compact monthly spending calendar directly on Home.

Show:
- Month/year.
- Previous/next month navigation.
- Today's date.
- Dates with expenses.
- Selected date.
- Selected day's total expenditure.

Tapping a date should expose the corresponding transactions through the existing transaction UI or a compact detail sheet.

Use a shared calendar component and the existing Room data source.

The calendar must not display fabricated expenses.

## F. Upcoming Payments

Keep Upcoming Payments below the calendar.

Use compact entries showing:
- Icon.
- Payment name.
- Due date.
- Amount.

Do not add long descriptions.

### Home Acceptance Criteria

The final Home layout should visually match the reference in:
- Section order.
- Spacing.
- Card proportions.
- Colours.
- Typography hierarchy.
- Button arrangement.
- Calendar density.

Do not merely use the reference as loose inspiration.

Generate a Home Compose preview at a representative phone size, approximately 393 × 852dp, and compare it visually with the reference.

Use synthetic data in previews and real persisted data in the running application.

---

# 4. EXPENSES SCREEN

Make Expenses the second main navigation destination.

Keep the screen fast and simple.

## Expense Entry

Preserve the cents-first numeric keypad.

Input sequence example:
1 → RM0.01
12 → RM0.12
123 → RM1.23
1234 → RM12.34

Use precise monetary representation.

## Category Selection

Use horizontally scrollable category icons.

Requirements:
- One tap to select.
- Icon above short text label.
- Selected state clearly highlighted.
- No dropdown selection column.
- Others placed last.
- Exclude Fuel from ordinary manual expense categories.
- Keep legitimate Transport, Parking and Toll entries where already supported.
- Preserve historical fuel-linked transactions.

## Payment Method

Use three icon choices in this order:

1. E-Wallet.
2. Cash.
3. Card.

Card represents credit and debit for new selections.

Preserve compatibility with historical credit/debit records.

Remember the last payment method.

## Spending Calendar

Embed the existing Spending Calendar directly in Expenses.

Reuse the same data and reusable calendar component as Home.

## Transaction History

Keep the category filter row BELOW the Transaction History heading.

Use compact transaction rows:
- Category icon.
- Merchant or description.
- Date.
- Amount aligned right.

Support existing:
- Transaction search.
- Filters.
- Favourites.
- Merchant memory.
- Receipt attachment.
- Undo.
- Refunds.
- Swipe actions.

Do not implement split-category expenses.

---

# 5. FUEL SCREEN

Use a clean, data-focused automotive dashboard.

## Layout

Recommended order:

1. Screen heading.
2. Estimated Fuel Consumption.
3. Compact efficiency and fuel-cost summary.
4. Add Refuel action.
5. Refuelling history.
6. Fuel spending and efficiency charts.

Avoid unnecessary explanatory text.

## Fuel Estimate

Use historical refuelling records.

Do not require full-tank checkboxes.

After two valid refuelling records, show a provisional estimate based on odometer distance and fuel purchased at the later refuelling.

Clearly label this as estimated consumption because unequal remaining fuel levels can make the result inaccurate.

If there are more records, use a suitable distance-weighted historical aggregate.

Provide detailed methodology only behind an information icon.

Do not misrepresent provisional estimates as precisely measured efficiency.

## Add Refuel

Keep only:
- Date.
- Odometer.
- Litres purchased.
- Amount paid.

Default fuel type: RON95.

Automatically calculate price per litre.

Remove:
- Station location.
- Full Tank control.
- Notes.

Retain compatibility with historical data.

## Visual Style

Use large, clearly readable efficiency figures and compact charts.

Use the same card dimensions and margins as Home and Expenses.

---

# 6. JOURNEY SCREEN

Preserve the existing Journey Planner functionality and layout structure, since its major layout issues have already been resolved.

Improve only visual consistency.

Maintain:
- Origin and destination.
- Direction swap.
- One-way/Round-trip toggle.
- Distance.
- Historical fuel consumption estimate.
- Fuel price.
- Toll charges.
- Parking charges.
- Total journey cost.
- Saved routes and existing journey tools.

Keep the cost result visually prominent.

Do not reintroduce:
- Other Journey Fees.
- Passenger Cost Sharing.

Use the same fonts, cards, padding and spacing as the other screens.

Do not scale the entire Journey screen using graphicsLayer or similar layout hacks.

---

# 7. ANALYSIS SCREEN

Use compact, readable charts.

Preserve existing useful analytics.

## Daily Expenditure

Create a horizontally scrollable daily expenditure bar chart.

Requirements:
- Today's bar initially appears at the horizontal centre.
- Past dates appear to the left.
- Future dates may appear to the right with zero recorded expenditure.
- Users can scroll horizontally.
- Selected date and amount remain readable.
- Include a small "Today" action for recentering.

Prevent chart dragging from accidentally activating main-screen navigation.

## Other Charts

Keep:
- Income vs Expenses.
- Spending by Category.
- Monthly Spending Trend.
- Budget Performance.
- Fuel Efficiency Trend.
- Fuel Spending History.

Remove:
- Monthly Plan vs Actual.

Use restrained colours and legible scales.

Do not overcrowd charts with labels.

---

# 8. MAIN NAVIGATION

Use this exact order:

1. Home.
2. Expenses.
3. Fuel.
4. Journey.
5. Analysis.

Implement a consistent Material 3 bottom navigation bar.

Support horizontal swiping between these five primary screens.

Requirements:
- Smooth transitions.
- Selected tab stays synchronized.
- Preserve screen state where practical.
- Maintain vertical scroll behaviour.
- Avoid conflicts with category rows, sliders, charts and transaction gestures.
- Preserve Android system Back navigation.

Keep detailed forms outside the main horizontal pager.

For larger screen configurations, use adaptive navigation where appropriate rather than stretching a phone layout excessively.

---

# 9. SETTINGS REDESIGN

Keep Settings short and organized.

Preferred groups:

Financial Planning:
- Monthly Money Planner.
- Recurring Transactions.

Preferences:
- Appearance.
- Receipt Scanner.

Privacy and Data:
- App Lock.
- Privacy Mode.
- Backup and Export.

General:
- About.

Keep Monthly Allocation inside Monthly Money Planner.

Use sliders for allocation percentages.

Do not reintroduce:
- Gesture & Feedback settings.
- Dashboard Arrangement settings.
- Duplicate Income/Expected Income configuration pages.

Retain the ability to record actual income independently from expected income planning.

Remove redundant explanations and unnecessary nested menus.

Place the actual application version inside Settings → About.

---

# 10. BUTTONS AND INTERACTIONS

Eliminate the unattractive grey translucent rectangular pressed-state overlay.

Use consistent button interaction feedback.

Preferred effect:
- Subtle scale-down while pressed, approximately 0.98x.
- Quick return to normal.
- Optional gentle accent-colour change.
- Respect rounded corners.

Avoid:
- Whole-screen blinking.
- Shared button flashing.
- Aggressive ripples.
- Excessive bouncing.
- Unnecessary elevation changes.

Investigate and fix the existing Settings switch-blinking issue if still present.

Do not simply hide the issue by disabling unrelated functionality.

Use stable Compose state and interaction handling.

---

# 11. ANIMATION QUALITY

Use consistent, restrained motion.

Typical interaction duration: approximately 150–250ms.

Adjust duration as appropriate for the interaction.

Priorities:
- Smooth navigation.
- Smooth state transitions.
- No animation-induced layout jumps.
- No flashing during recomposition.
- Responsive keyboard behaviour.
- Smooth horizontal calendar and chart scrolling.

Prefer stable and reusable animation patterns.

Respect reduced-motion and accessibility preferences.

Do not animate large containers unnecessarily when a single value changes.

---

# 12. SCREENSHOT AND PRIVACY REQUIREMENTS

Allow normal Android screenshots.

Do not set FLAG_SECURE on ordinary application screens.

Preserve:
- Fully offline operation.
- No INTERNET permission.
- Existing app lock where implemented.
- Existing private local storage.
- Encrypted manual backups where implemented.

Screenshots are intentionally allowed at the user's request.

Do not upload financial data or screenshots to external services.

---

# 13. RESPONSIVE DESIGN

Test on representative configurations:

- Small phone.
- Standard smartphone.
- Large smartphone.
- Landscape where supported.
- Increased font scale.
- Light theme.
- Dark theme.

Requirements:
- No clipped text.
- No overlapping controls.
- No excessive horizontal margins.
- No cards extending beyond the viewport.
- All important buttons accessible with the keyboard visible.
- Consistent bottom navigation height.
- Scrollable content does not extend behind system navigation without appropriate insets.

Use dp and sp correctly.

Avoid hardcoded screen-wide scaling.

---

# 14. IMPLEMENTATION PROCESS

## Step 1 — Audit UI

Inspect the existing Compose implementation of all five screens.

Identify:
- Duplicated components.
- Inconsistent design tokens.
- Oversized elements.
- Unnecessary visual effects.
- Repeated settings.
- Layout inconsistency.

## Step 2 — Create a Shared Design System

Introduce or improve reusable:
- Colour tokens.
- Typography.
- Spacing tokens.
- Cards.
- Buttons.
- Selection controls.
- Transaction rows.
- Section headers.
- Calendar components.

Do not perform a disruptive architecture rewrite.

## Step 3 — Implement Home First

Complete the Home dashboard to match the design reference.

Generate preview or screenshot evidence.

Review its visual quality before applying shared styling to the remaining screens.

## Step 4 — Refine Other Screens

Proceed in this order:
1. Expenses.
2. Fuel.
3. Journey.
4. Analysis.
5. Settings.

## Step 5 — Visual Regression Review

Generate representative preview screenshots for all five screens using identical device dimensions and theme.

Compare:
- Title alignment.
- Card widths.
- Horizontal padding.
- Typography.
- Bottom navigation.
- Component density.
- Scroll behaviour.

Use the provided reference screenshot as a comparison target where available.

Do not claim an exact visual match unless it was actually inspected.

## Step 6 — Functional Regression Tests

Verify:
- Expense entry.
- Income entry.
- Fuel calculations.
- Refuelling records.
- Journey calculations.
- Spending calendars.
- Charts.
- Receipt scanning.
- Recurring payments.
- Theme persistence.
- Navigation.
- Widget shortcuts where implemented.
- Backup and restore compatibility.

Do not break working features as part of the UI redesign.

## Step 7 — Build and Version

Build the updated APK if tools are available.

After a successful release-ready build:
- Update versionName appropriately.
- Increment versionCode.
- Update CHANGELOG.md.
- Preserve applicationId.
- Preserve signing certificate compatibility.

Ensure the new APK can update the existing application without erasing financial records.

## Step 8 — Final Report

Provide:
1. Screens redesigned.
2. Design-system changes.
3. Files modified.
4. Screenshots or preview locations if generated.
5. Build and test results.
6. APK location if built.
7. Known issues.
8. Differences between the requested design and actual implementation.

---

# FINAL ACCEPTANCE CRITERIA

The redesign is complete only when:

- Home closely matches the supplied visual reference.
- Today's Expenses is the main financial figure.
- Monthly budget values are correctly aligned.
- Home contains exactly two quick-action buttons.
- The Spending Calendar appears directly on Home and Expenses.
- All five screens share consistent typography and spacing.
- Main navigation order is correct.
- Buttons and switches no longer blink unexpectedly.
- Journey does not appear larger than other screens.
- Light, Dark and System themes work.
- No existing user data is lost.
- The application remains fully offline.
- The updated APK builds successfully where the environment supports it.

Prioritize visual consistency, responsiveness, simplicity and reliability over adding features.

**Begin by inspecting the project, establishing a safe checkpoint and implementing the Home screen to match the visual reference.**