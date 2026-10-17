# Android Application Refinement — UI, Navigation, Fuel Calculations and Performance

Act as a senior Android developer, UI/UX designer and QA engineer.

Refine my EXISTING Android financial and fuel tracking application.

This is a corrective update, not a project rebuild.

The app uses Kotlin, Jetpack Compose, Material 3 and Room. Preserve all existing financial records, refuelling data, preferences and features unless explicitly instructed to change them.

The application must remain 100% offline.

## 1. HOME — Dashboard Improvements

### 1.1 Main Financial Card

Replace the current "Available After Fixed Commitments" main dashboard figure with:

**Today's Expenses**

Display the total amount spent on the current local calendar day prominently.

Requirements:
- Display the amount in MYR.
- Automatically update when expenses are added, edited or deleted.
- Automatically roll over when the local date changes.
- Use actual expenditure records.
- Include linked fuel expenses exactly once in the relevant totals.
- Do not accidentally include income or transfers as expenditure.
- Keep the premium dashboard styling and compact layout.
- Preserve the Monthly Money Planner elsewhere in the application.

Do not display Smart Safe-to-Spend.

### 1.2 Monthly Budget Progress

Keep the existing monthly budget progress bar.

The horizontal progress bar should remain a clear 0%–100% representation of budget usage, with an appropriate over-budget indicator when spending exceeds the limit.

Fix the alignment problem:

The text showing "RM XXXX budget" must be aligned to the RIGHT EDGE of the budget section.

Do not center it.

Expected layout:

Left: RM XXXX spent
Right: RM XXXX budget

Use a Compose Row with appropriate alignment or weight to ensure the right label is positioned at the trailing edge.

Test with different monetary values and screen widths.

### 1.3 Quick Action Buttons

Currently there are four buttons:
- Expense
- Income
- Scan
- Calendar

Replace them with only TWO buttons:

1. Expense
2. Fuel

Use two equally sized buttons in one row.

Expense:
Open the existing Add Expense screen.

Fuel:
Open the existing Add Refuel screen.

Do not delete the ability to record income or scan receipts. Those features should remain accessible from their appropriate existing screens.

### 1.4 Spending Calendar

Embed the Spending Calendar directly on the Home screen.

Requirements:
- Display a compact monthly calendar.
- Highlight today's date.
- Show dates with spending activity.
- Show daily expenditure totals in a readable, compact way.
- Allow previous/next month navigation.
- Tapping a date opens or displays transactions recorded on that date.
- Keep the calendar visually consistent with the dashboard.

Do not require the user to open a separate calendar page for basic calendar functionality.

Reuse the existing calendar implementation instead of creating a duplicate data source.

### 1.5 Upcoming Payments

Keep the Upcoming Payments section.

Display:
- Payment name.
- Due date.
- Amount.
- Status where necessary.

Keep it compact.

Do not add long descriptions.

---

## 2. FUEL — Simplified Refuelling and Efficiency

### 2.1 Fuel Efficiency Calculation

Currently, the app requires two full-tank records before displaying Average Fuel Efficiency.

Remove the requirement for full-tank records.

Allow the app to generate a provisional consumption estimate once TWO valid refuelling records are available.

Requirements:
- At least two records with valid increasing odometer values.
- Valid positive fuel quantity.
- Valid date/order.
- Calculate the distance between consecutive refuelling records.
- Use the fuel quantity from the later refuel to generate an interval consumption estimate when appropriate.
- Clearly label the result "Estimated Fuel Consumption" when comparable fuel levels are not verified.
- Do not claim it is an exact measured value.
- Explain the estimate's equal-post-refuel-fuel-level assumption using an optional information icon.

Formula for a provisional interval estimate:

Distance = Current Odometer − Previous Odometer

Estimated L/100 km = Current Refuel Litres / Distance × 100

Estimated km/L = Distance / Current Refuel Litres

For historical estimates spanning multiple refuelling intervals, use total included fuel volume divided by total included distance rather than taking an unweighted average of displayed interval efficiencies.

Clearly indicate that arbitrary partial-refuel amounts may make this estimate inaccurate because the fuel remaining in the tank is unknown.

Do not fabricate consumption when insufficient data exists.

Apply the same historical estimate consistently wherever fuel consumption is required:
- Fuel dashboard.
- Journey Planner.
- Fuel analytics.
- Related cost estimations.

Do not keep hidden validation rules that still require the removed Full Tank checkbox.

### 2.2 Add Refuel Form

Simplify Add Refuel.

Assume RON95 by default.

Keep:
- Refuelling date.
- Odometer reading.
- Fuel quantity in litres.
- Total amount paid.
- Automatically calculated price per litre.

Remove:
- Station Location.
- Full Tank switch/button.
- Notes.

Do not require the user to fill unnecessary fields.

Store RON95 as the default fuel type, but retain compatibility with historical records using other fuel types.

Allow the actual price per litre to be calculated from payment and quantity, rather than assuming an unchanging official price.

Keep the cents-first monetary keypad where applicable.

### 2.3 Historical Data Compatibility

Preserve existing refuelling records.

Do not destructively remove old station, note or full-tank fields from saved records solely because their input controls are hidden.

Ensure the updated consumption calculation works with both old and new records.

---

## 3. EXPENSES — Navigation, Payment Method and Calendar

### 3.1 Navigation Order

Move Expenses immediately after Home.

The new main navigation order must be:

1. Home
2. Expenses
3. Fuel
4. Journey
5. Analysis

Update:
- Bottom navigation order.
- Horizontal swipe navigation order.
- Selected navigation states.
- Page indices and relevant navigation routes.

Preserve existing screen state during navigation.

### 3.2 Remove Fuel Category From Expenses

Remove Fuel as a manually selectable expense category in Add Expense.

The dedicated Fuel screen should handle refuelling records.

Also remove any other duplicated manual entry options that merely replicate dedicated Fuel functionality, but do not indiscriminately remove legitimate categories such as Transport, Parking or Tolls.

Important:
- Preserve historical fuel transactions.
- Preserve linked fuel expenditure in overall financial totals.
- Do not delete fuel-related database records.
- Do not double-count refuelling expenditure.
- Do not make existing historical transactions inaccessible.
- Keep a clear distinction between manual general transport expenses and dedicated refuelling records.

### 3.3 Payment Method Order

Change the payment method icon arrangement to:

1. E-Wallet
2. Cash
3. Card

Card combines credit and debit card as a visible selection.

Keep the payment method interface icon-based with one-tap selection.

Do not use dropdown menus.

Do not reintroduce "Not Specified".

Preserve the last-used payment method preference.

### 3.4 Spending Calendar

Embed the Spending Calendar directly on the Expenses screen.

Place it in a logical position without disrupting the expense summary and Transaction History.

Requirements:
- Calendar month navigation.
- Daily spending indicators.
- Highlight selected date.
- Tap a date to filter or display that day's transactions.
- Reuse the same calendar component and transaction source as Home.

Do not create separate inconsistent calendar calculations.

### 3.5 Transaction History

Preserve the existing Transaction History.

Keep the category filter row directly BELOW the Transaction History heading.

Keep Others as the last category.

Maintain fast one-tap category filtering.

---

## 4. JOURNEY

The current Journey Planner is satisfactory.

Do not redesign it.

Preserve:
- Existing origin and destination inputs.
- Distance calculation.
- Fuel cost estimates.
- Toll fees.
- Parking fees.
- One-way/round-trip control.
- Saved journeys.
- Existing calculations and visual layout.

Only update integrations required by the new fuel efficiency calculation and navigation order.

---

## 5. ANALYSIS

### 5.1 Remove Monthly Plan vs Actual

Remove the "Monthly Plan vs Actual" section from the Analysis screen.

Remove related unnecessary UI controls and empty spacing.

Do not delete actual historical income, expense or budget records.

Preserve all other analytics.

### 5.2 Daily Expenditure Chart

Redesign the Daily Expenditure chart as a horizontally scrollable chart.

Very important:

**Today's date must be positioned at the centre of the visible chart when the chart first opens.**

Requirements:
- Today's bar is centred horizontally by default.
- Show previous dates on the left.
- Show following dates on the right where appropriate.
- Allow users to scroll horizontally to inspect historical and other dates.
- Highlight today's bar.
- Display the selected date and amount clearly.
- Use actual spending records.
- Show zero for dates with no transactions.
- Do not fabricate future expenditure.
- Provide sufficient horizontal edge padding to allow the first and last available dates to be selected appropriately.
- Keep bars evenly spaced and consistently sized.
- Preserve horizontal scroll position when appropriate, but provide a clear "Today" action to recenter.
- Use the device's local date and time zone.

The chart must support smooth horizontal interaction without accidentally switching the entire application to another screen.

### 5.3 Preserve Other Analysis

Keep:
- Income vs Expenses.
- Monthly spending trends.
- Category breakdown.
- Budget usage.
- Fuel spending trends.
- Fuel efficiency insights.
- Other useful existing analytics.

Do not remove unrelated analysis features.

---

## 6. SETTINGS — Remove Clutter and Fix Switch Behaviour

### 6.1 Remove Settings Entries

Remove:
- Gesture & Feedback.
- Dashboard Arrangement.

Do not replace these with separate equivalent top-level settings under new names.

Keep appropriate built-in gestures and dashboard defaults functioning.

### 6.2 Audit Duplicate Settings

Inspect the entire Settings screen.

Identify options that:
- Duplicate controls already available directly on main screens.
- Repeat functionality.
- Are unnecessary for ordinary users.
- Have excessively long descriptions.
- Can be grouped under existing settings.

Simplify the screen.

Suggested compact structure:
- Monthly Money Planner.
- Recurring Transactions.
- Appearance.
- Receipt Scanner.
- Privacy & Security.
- Backup & Export.
- About.

Retain genuinely necessary controls, even if their exact grouping differs.

Avoid excessive scrolling.

Keep Monthly Allocation inside Monthly Money Planner rather than as a separate top-level settings entry.

### 6.3 Fix Blinking Buttons and Switches

There is a current UI bug:

When I press an ON/OFF switch, multiple buttons or controls on the Settings page visibly blink or flash.

Investigate the actual cause.

Possible areas to inspect:
- Compose recomposition.
- Shared MutableInteractionSource.
- Ripple/indication configuration.
- Animated state updates.
- Unstable component keys.
- State hoisting and theme updates.
- Haptic feedback implementation.
- Rebuilding interactive elements unnecessarily.

Do not assume that haptic feedback is the root cause without verification.

Requirements:
- Only the interacted switch should visibly respond.
- Other buttons must remain visually stable.
- Remove unwanted semi-transparent grey flashing overlays.
- Keep switch transitions smooth.
- Preserve correct state updates and persistence.
- Avoid unnecessary whole-screen animations.
- Do not disable legitimate accessibility feedback simply to hide the issue.

Fix the root cause instead of hiding the symptom.

---

## 7. GLOBAL UI AND ANIMATION IMPROVEMENTS

### 7.1 Smoother Animations

Improve animation consistency throughout the application.

Requirements:
- Smooth page transitions.
- Smooth button interactions.
- Smooth segmented controls.
- Smooth calendar month navigation.
- Smooth expandable sections.
- Smooth chart scrolling.
- Smooth loading and data updates.

Prefer appropriate Compose animation APIs.

Avoid:
- Excessive bouncing.
- Long transition delays.
- Unnecessary flashing.
- Aggressive scale changes.
- Animating entire screens for minor state changes.
- Recomposition-driven visual flicker.

Use consistent animation durations and easing.

Respect system accessibility and reduced-motion preferences.

### 7.2 Screenshot Support

I want to be able to take normal Android screenshots of my application.

Inspect the Activity, windows, dialogs and related components for:

WindowManager.LayoutParams.FLAG_SECURE

Remove or disable screenshot-blocking behaviour in the app.

Requirements:
- Allow normal Android screenshots.
- Allow screenshots in Home, Expenses, Fuel, Journey, Analysis and Settings.
- Ensure dialogs and financial screens do not unexpectedly block screenshot capture.
- Do not introduce screenshot restrictions through App Lock or Privacy Mode.
- Preserve the existing optional app authentication and data security features.
- Explain that enabling screenshots reduces protection against screen capture by the device or other authorized screen-recording mechanisms.

Do not change Android system screenshot behaviour outside the application.

### 7.3 Global Layout Consistency

Ensure every screen uses the same:
- Typography scale.
- Horizontal margins.
- Card proportions.
- Component spacing.
- Corner radius conventions.
- Navigation sizing.
- Theme colours.

Keep the app minimalist.

Do not reintroduce oversized Journey components or redundant descriptions.

---

## 8. TECHNICAL AND SECURITY REQUIREMENTS

- Kotlin.
- Jetpack Compose.
- Material 3.
- Room database.
- Safe database migrations.
- No destructive migration.
- No cloud services.
- No internet permission.
- No remote analytics or telemetry.
- No external API dependencies at runtime.
- Preserve local receipt scanning.
- Preserve encrypted local backup and restore where already implemented.
- Preserve Light, Dark and System themes.
- Preserve current financial calculations unless explicitly changed above.

---

## 9. TESTING

Add or update tests for:

Home:
- Today's spending.
- Budget amount alignment.
- Calendar date filtering.
- Quick-action navigation.

Fuel:
- Two valid refuelling records.
- Invalid or equal odometer values.
- Arbitrary partial refuels.
- Provisional consumption calculation.
- Historical aggregate calculation.
- Compatibility with old refuelling records.
- RON95 default.

Expenses:
- Payment method ordering.
- Fuel category removal from manual entry.
- Historical fuel record compatibility.
- Calendar integration.
- No double-counted fuel expenses.

Navigation:
- Home → Expenses → Fuel → Journey → Analysis.
- Horizontal swipe gestures.
- Bottom navigation synchronization.
- Page state preservation.

Analysis:
- Today's bar initially centred.
- Horizontal scrolling.
- Recenter-to-today behaviour.
- Empty dates.
- Monthly Plan vs Actual section removed.

Settings:
- Switch state persistence.
- No multi-button blinking.
- No redundant settings.
- Theme support.

Global:
- Smooth animations.
- No accidental gesture conflicts.
- Screenshot capability.
- Database preservation.
- Completely offline functionality.

---

## 10. Implementation Instructions

1. Inspect the current project and find the actual implementations of each affected screen.
2. Create a Git checkpoint if available.
3. Fix Home.
4. Fix Expenses and navigation order.
5. Update Fuel.
6. Preserve Journey and update necessary integrations.
7. Fix Analysis.
8. Simplify Settings and diagnose the blinking issue.
9. Improve animations and screenshot support.
10. Run tests.
11. Build the updated APK if supported.

Do not claim completion without verification.

At the end, provide:
- Exact changes made.
- Root cause of the Settings blinking issue.
- Explanation of the updated fuel calculation.
- Files modified.
- Tests completed.
- Build results.
- Updated APK output location if available.
- Any remaining issues.

**Important:** This update is focused on quality, simplicity, consistency and accurate calculations. Do not add unrelated features.

Begin implementing the changes in the existing project.
