# Comprehensive Android App Upgrade — Finance, Fuel, Journey and Analytics

## Your Role

Act as a senior Android software engineer, UI/UX designer, security engineer and quality assurance specialist.

Upgrade my EXISTING Android financial tracking application using Kotlin, Jetpack Compose, Material 3 and Room.

This is an ongoing project. Do not rebuild the application from scratch.

The app must remain 100% offline, and existing user data must be preserved.

The objective is to deliver a polished, commercial-quality Android application with a consistent, minimalist UI, excellent usability, reliable calculations and strong privacy protections.

---

# PHASE 0 — Inspect and Protect the Existing Project

Before making changes:

1. Inspect the full existing project structure.
2. Identify the database schema and migrations.
3. Identify the current navigation system.
4. Inspect the existing Home, Fuel, Expenses, Journey and Analysis screens.
5. Inspect recurring transactions, income tracking, budgets, receipts and settings.
6. Identify reusable UI components and calculation utilities.
7. Establish a safe Git checkpoint before modifications if Git is available.

Preserve all existing features unless a removal is explicitly requested below.

Do not:
- Reset or replace the existing database.
- Use destructive migrations.
- Rebuild unrelated screens unnecessarily.
- Introduce internet connectivity.
- Replace working calculations with placeholder values.
- Delete user records or preferences.

Create a development checklist and implement improvements in logical phases.

---

# PHASE 1 — HOME DASHBOARD REDESIGN

## 1.1 Visual Target

Redesign Home to closely match the dashboard reference described below.

The dashboard should use a premium, minimalist fintech design.

Design specifications:

Dark theme:
- Main background: #0B1220
- Main financial card: #14283D
- Card surfaces: #18263B
- Emerald accent: #2ABB91
- Primary text: #F3F6FA
- Secondary text: #ADBED0
- Subtle dividers: #35495B

Use equivalent accessible colours for Light theme.

UI styling:
- Rounded cards, approximately 16–24dp.
- Consistent screen margins of approximately 16dp.
- Clean Material 3 typography.
- Large financial figures.
- Compact secondary information.
- Minimal unnecessary borders.
- Consistent vertical spacing.
- No oversized components.
- No long explanatory paragraphs.

## 1.2 Required Home Layout

Follow this exact visual order:

### Section A — Header

Display:
- Current month and year in small secondary text.
- "Overview" as the main screen title.
- Settings icon aligned to the right.

Do not display "Fuel & Expense Tracker v1.4.0" in the header.

### Section B — Main Financial Card

Create a large dark navy rounded card.

Display:

"Available After Fixed Commitments"

Below that, display the calculated monthly amount in large bold MYR text.

Below the amount, show a compact status label:

"Monthly Money Planner · Planned"

Add a subtle divider.

Below the divider, display two columns:

Left:
"Expected Income"
Amount in emerald accent.

Right:
"Fixed Commitments"
Amount in white or theme-appropriate text.

Calculation:

Available After Fixed Commitments =
Expected Monthly Income − Scheduled Fixed Monthly Commitments.

This is a planned amount, not an actual bank balance.

Do not implement Smart Safe-to-Spend.

Do not show daily spending recommendations or automatically calculated safe-to-spend amounts.

Do not rebrand Smart Safe-to-Spend under another name.

### Section C — Monthly Budget

Display:
- "Monthly Budget" heading.
- Percentage used on the right.
- Thin rounded horizontal progress bar.
- Amount spent underneath on the left.
- Total budget underneath on the right.

Use green/emerald for normal usage and suitable warning colours near or above the limit.

Use actual expense records.

### Section D — Quick Actions

Provide four equal-sized icon action buttons in one compact row:

1. Expense
2. Income
3. Scan
4. Calendar

Each must have:
- A simple icon.
- Short label.
- Compact rounded touch target.
- Consistent spacing.

Actions:
- Expense opens Add Expense.
- Income opens Add Income.
- Scan opens the existing receipt scanner.
- Calendar opens the new Spending Calendar.

### Section E — Upcoming Payments

Display:
- "Upcoming Payments" heading.
- Chevron for opening the recurring payment list.
- Compact upcoming payment entries.

Each entry should show:
- Category or merchant icon.
- Payment name.
- Due date.
- Amount.

Do not show unnecessary descriptions.

## 1.3 Visual Verification

Create a Compose preview using illustrative data matching this reference:

- Planned available: RM 1,870.00
- Expected income: RM 3,000.00
- Fixed commitments: RM 1,130.00
- Monthly budget: RM 1,680.00
- Spent: RM 1,260.00
- Budget usage: 75%
- Upcoming Wi-Fi bill: RM 120.00

These values must exist only in preview/sample data, never as production dashboard defaults.

Capture or render the completed Home screen where tooling permits.

Compare the implementation against the specified reference and correct obvious visual differences.

The final Home screen should resemble this reference in layout, colour hierarchy, proportions and compactness.

---

# PHASE 2 — EXPENSES

Implement all improvements in this section.

## 2.1 Favourite Transactions

Allow users to save frequently repeated transactions as favourites.

Example:
- Lunch, RM15, Food, Cash.
- Parking, RM5, Transport, E-Wallet.

Users can add these transactions quickly with one tap.

Provide editing and deletion of favourites.

## 2.2 Merchant Memory

Remember merchants locally.

When a merchant is recognized:
- Suggest its previously used category.
- Suggest its previously used payment method.
- Optionally remember its common description.

Allow users to override any suggestion.

Do not use cloud-based merchant recognition.

## 2.3 Smart Category Memory

Remember the user's last selected category.

When appropriate, suggest frequently used categories based on transaction history.

Do not automatically overwrite explicit user selections.

## 2.4 Quick Transaction Editing

Allow tapping a transaction to open its details.

Implement configurable swipe shortcuts such as:
- Edit.
- Duplicate.
- Delete.

Destructive actions must allow Undo or confirmation.

Ensure transaction gestures do not conflict with main-screen swipe navigation.

## 2.5 Instant Undo

Provide Undo after deleting or modifying a transaction.

Use a reliable reversible or deferred deletion strategy.

Do not allow accidental permanent data loss.

## 2.6 Duplicate Detection

Detect potentially duplicated transactions using:
- Amount.
- Merchant.
- Date/time proximity.
- Existing receipt association where available.

Show a warning.

Allow the user to proceed when the duplicate is intentional.

Never silently delete valid records.

## 2.7 Transaction Notes

Provide an optional Notes field.

Keep it collapsed by default to maintain a compact expense entry screen.

## 2.8 Refunds

Support recording refunds.

Link refunds to original transactions where possible.

Clearly distinguish:
- Expense.
- Income.
- Refund.

Refunds should correctly adjust net expenditure without being incorrectly classified as ordinary earned income.

## 2.9 Advanced Search and Filtering

Allow searching transactions by:
- Merchant.
- Description.
- Amount.
- Category.
- Date.
- Payment method.

Provide intuitive filters and sorting.

## 2.10 Receipt Attachment

Allow linking an existing scanned receipt image to a transaction.

Users should be able to:
- View the receipt.
- Replace it.
- Delete the attachment.
- Retain the transaction when deleting its image.

Keep all files locally.

## 2.11 Explicit Exclusions

Do NOT implement:
- Split-category expenses.
- Multiple-category allocation of a single transaction.
- Item-level financial category splitting.

Preserve the existing one-tap category icons, cents-first numeric keypad and payment method icon selection.

---

# PHASE 3 — SPENDING CALENDAR

Create a new Spending Calendar screen accessible from Home and Expenses.

## 3.1 Calendar Design

Display:
- Current calendar month.
- Previous/next month controls.
- Daily expenditure total inside or beneath each date.
- Selected-date highlight.
- Indicators for dates with transactions.

Use a clean month-grid calendar design.

## 3.2 Calendar Interaction

When the user taps a date:
- Show total expenses for that day.
- Display the transactions recorded on that date.
- Allow opening or editing transactions.
- Allow adding a transaction dated to that day.

Support monthly navigation.

## 3.3 Calendar Statistics

Display:
- Total monthly spending.
- Highest spending day.
- Average daily expenditure.
- Optional category filters.

Do not include future planned bills in actual daily expenditure totals.

Use actual transaction dates and the device's local time zone.

Do not fabricate activity for empty dates.

---

# PHASE 4 — FUEL TRACKER

## 4.1 Historical Fuel Consumption

Use valid full-tank-to-full-tank intervals to calculate consumption.

Across valid intervals:

Average consumption (L/100 km) =
Total fuel consumed (L) / Total distance travelled (km) × 100.

Include partial refuels within each valid full-tank interval.

Never double-count fuel when combining intervals.

Display:
- Average L/100 km.
- Average km/L.
- Recent consumption.
- Historical trend.

Show insufficient-data messaging where appropriate.

## 4.2 Fuel Consumption Change Detection

Compare recent consumption against valid historical performance.

Examples:
- Recent consumption increased by 8%.
- Recent efficiency improved compared with the historical average.

Use a minimum sample-size requirement.

Clearly communicate when insufficient data exists.

Do not present consumption changes as proof of mechanical faults.

## 4.3 Fuel Price History

Preserve the actual petrol price per litre for each refuel.

Display:
- Historical price changes.
- Average price paid.
- Fuel spending changes.

Distinguish changes caused by fuel prices from changes caused by fuel efficiency where data supports the comparison.

## 4.4 Vehicle Cost Per Kilometre

Provide:
- Fuel-only cost per kilometre.
- Recorded operating cost per kilometre.

Use existing vehicle-related expenses where available.

Ensure costs are included only once.

Display included cost categories and coverage dates.

Do not claim this represents complete vehicle ownership cost if depreciation, insurance or other ownership costs are absent.

## 4.5 Explicit Exclusion

Do NOT implement a Vehicle Maintenance Tracker.

Do not create new servicing modules, engine oil schedules, tyre replacement trackers or maintenance reminder pages.

---

# PHASE 5 — JOURNEY PLANNER

Preserve the existing standalone Journey Planner.

## 5.1 Saved Routes

Allow users to:
- Save route names.
- Save origin and destination.
- Store manually entered distances.
- Store toll information.
- Reuse a saved route.
- Edit and delete routes.

## 5.2 Recent Destinations

Show recently used origins and destinations.

Allow one-tap selection.

All suggestions must be generated locally.

## 5.3 Reverse Journey

Add a compact reverse-direction button between Origin and Destination.

Tapping it swaps the locations.

Preserve the distinction between outward and return toll costs.

## 5.4 Round-Trip Customization

Allow:
- One Way.
- Round Trip.

Use the existing sliding segmented control.

For round trips, support separate:
- Outbound distance.
- Return distance.
- Outbound toll fees.
- Return toll fees.

Do not assume toll amounts are equal in both directions.

## 5.5 Fuel Price Scenarios

Allow the user to compare journey costs under different petrol prices.

Use existing historical average fuel consumption.

Keep this as a local calculation without modifying fuel history.

## 5.6 Estimated vs Actual Journey Cost

Allow users to compare:
- Planned fuel cost.
- Actual fuel expenditure attributed to the journey.
- Planned toll fees.
- Actual toll fees.
- Planned parking fees.
- Actual parking fees.

Only link actual expenses using explicit user confirmation or existing reliable associations.

Do not create duplicate expenses.

If actual expenses have not been recorded, display them as unavailable rather than zero.

## 5.7 Export Journey

Allow exporting a journey summary as:
- Plain text.
- Image.

Include:
- Origin and destination.
- Distance.
- Fuel cost.
- Toll fees.
- Parking.
- Total estimated cost.

Use locally generated files and Android's standard share functionality.

Do not require internet access.

## 5.8 Route Presets

Allow users to save frequently used journey templates.

Templates may store origin, destination, distance and toll values.

Recalculate fuel estimates using the current historical fuel consumption and selected petrol price.

## 5.9 Vehicle Selection

If the existing project already supports multiple vehicle profiles, allow selecting a vehicle for journey calculations.

Otherwise, use the current default vehicle.

Do not introduce a complete new multi-vehicle management subsystem solely for this feature.

## 5.10 Existing Exclusions

Do not reintroduce:
- Passenger cost sharing.
- Other Journey Fees.

Keep the Journey screen layout consistent with all other main screens.

---

# PHASE 6 — ANALYSIS AND REPORTING

Improve the existing Analysis screen.

## 6.1 Financial Analysis

Provide:
- Monthly income vs expenses.
- Spending by category.
- Budget vs actual spending.
- Daily spending trends.
- Fixed vs variable expenses.
- Monthly comparisons.
- Income and expense trends.
- Financial forecast based on expected income and scheduled commitments.

Financial forecasts must clearly distinguish planned values from actual transactions.

Do not implement Smart Safe-to-Spend or daily safe-spending recommendations.

## 6.2 Vehicle Analysis

Provide:
- Fuel spending trends.
- Fuel efficiency changes.
- Fuel price history.
- Fuel-only cost per kilometre.
- Recorded vehicle operating costs.

## 6.3 Monthly Comparisons

Support:
- Current month.
- Previous month.
- 3 months.
- 6 months.
- 12 months.
- Custom date range, if practical.

Use correct calendar date boundaries.

## 6.4 Offline Financial Insights

Build deterministic local insight rules.

Example insights:
- "Food spending increased 18% compared with last month."
- "Entertainment budget usage reached 90%."
- "Fuel consumption increased over recent valid intervals."
- "Upcoming fixed commitments total RM420."

Never invent insights without sufficient underlying data.

Do not use online AI, remote APIs or cloud processing.

## 6.5 Savings Information

If the existing Monthly Money Planner records savings allocations, show planned savings allocation information where relevant.

Do not falsely treat a planned savings allocation as actual saved money.

Do not introduce a separate Savings Goals feature.

## 6.6 Chart Design

Use:
- Clean bar charts.
- Line charts for trends.
- Donut charts for category distribution.
- Clear RM and percentage labels.

Avoid overly complicated charts, misleading scales and excessive decorations.

Ensure charts are readable in both Light and Dark themes.

---

# PHASE 7 — NAVIGATION AND GESTURES

Maintain the five main destinations in this exact order:

1. Home
2. Fuel
3. Expenses
4. Journey
5. Analysis

Implement horizontal swipe navigation using Compose HorizontalPager or an equivalent supported official Compose component.

## Required Behaviour

- Swipe left to move forward.
- Swipe right to move backward.
- Preserve the bottom navigation bar.
- Synchronize the selected bottom navigation item with the active page.
- Animate transitions smoothly.
- Do not wrap around from first to last screen.
- Preserve screen state and scroll position where practical.

## Gesture Protection

Prevent conflicts with:
- Monthly allocation sliders.
- Journey type selector.
- Expense category scrolling.
- Charts.
- Transaction swipe actions.
- Other horizontal controls.

Do not enable top-level swipe navigation inside detailed forms, Settings or the receipt scanner.

Keep Android system back navigation functioning properly.

---

# PHASE 8 — SETTINGS AND PERSONALIZATION

Organize Settings using a clean information architecture.

## 8.1 Financial Planning

Maintain one unified "Monthly Money Planner" settings entry.

Inside it:
- Expected Income.
- Fixed Monthly Expenses.
- Monthly Allocation.

Do not create duplicate top-level financial planning entries.

Use interactive sliders for allocation percentages.

Maintain 100% total allocation.

Do not require users to type percentages manually.

## 8.2 Appearance

Support:
- Light.
- Dark.
- System Default.

Save the selection locally.

System Default must follow the Android device's appearance setting.

Use consistent typography, colours, cards and chart styles.

## 8.3 Dashboard Customization

Allow users to:
- Reorder eligible Home dashboard cards.
- Show or hide optional cards.
- Restore the default dashboard arrangement.

Preserve the proposed dashboard as the default configuration.

Avoid allowing accidental deletion of underlying financial records.

## 8.4 Gesture Preferences

Add settings for:
- Enable/disable main-screen swipe navigation.
- Transaction swipe actions.
- Haptic feedback.
- Reduced animation.

Use sensible defaults.

## 8.5 UI Cleanliness

Remove:
- Unnecessary descriptions.
- Repeated section headings.
- Excessive grey overlays.
- Inconsistent card sizes.
- Oversized input controls.
- Redundant settings.

Keep helpful explanations behind optional information icons.

Do not remove important error messages or financial calculation labels.

---

# PHASE 9 — PRIVACY AND SECURITY

This application must remain completely offline.

## 9.1 Network Restrictions

Do not add:
- INTERNET permission.
- Cloud synchronization.
- Firebase.
- Online analytics.
- Remote logging.
- Remote AI processing.
- Advertising SDKs.

Inspect the final merged Android manifest and project dependencies.

## 9.2 Secure Local Storage

Keep financial data in application-private storage.

Review and implement suitable encryption at rest for sensitive databases and receipt files using maintained solutions and appropriate key management.

Use Android Keystore where appropriate.

Avoid custom cryptography.

Preserve existing records during migration.

## 9.3 Optional App Lock

Add:
- Fingerprint or device-credential authentication.
- Configurable lock timeout.
- Authentication on returning to the app when configured.

Use AndroidX BiometricPrompt.

Allow the user to turn this feature off.

## 9.4 Privacy Mode

Add an eye icon to hide or reveal sensitive monetary amounts.

When hidden:
- Replace visible amounts with masked characters.
- Apply consistently to relevant financial cards and lists.
- Respect the preference when returning to the app.

## 9.5 Android Screen Protection

Offer protection against sensitive information appearing in recent-app previews.

Use appropriate Android platform capabilities.

Avoid breaking legitimate app functionality.

## 9.6 Encrypted Manual Backup

Allow users to:
- Create an encrypted local backup.
- Restore from a compatible backup.
- Verify backup integrity.
- Choose a local export destination.

Use authenticated encryption and safe password-based key derivation for portable backups.

Do not store backup passwords or encryption keys in source code.

Do not automatically upload files.

## 9.7 Local Data Management

Allow users to:
- Delete stored receipt images.
- Export transaction records.
- Inspect local storage usage.
- Delete selected historical data with confirmation.

Avoid accidental irreversible deletion.

Keep automatic cloud backup disabled as far as supported by Android.

---

# PHASE 10 — DAILY USABILITY IMPROVEMENTS

Implement the following selected improvements.

## 10.1 Remember Last Payment Method

When adding a new expense, automatically preselect the last-used payment method.

The available payment options remain:
- Cash.
- Card.
- E-Wallet.

Do not reintroduce "Not Specified" into the new-expense interface.

Preserve compatibility with existing historical records.

## 10.2 Custom Category Icons

Allow users to assign icons and colours to custom categories.

Package icon assets locally.

Ensure icons are readable in Light and Dark themes.

## 10.3 Instant Undo

Provide Undo for relevant actions such as transaction deletion.

Make the restoration reliable.

## 10.4 Keyboard-Aware Layouts

Ensure:
- Inputs remain visible above the keyboard.
- Save actions are accessible.
- Screens resize or scroll correctly.
- No fields are covered by the keyboard.

Preserve the cents-first keypad for expenses.

## 10.5 Offline Notifications

Support local reminders for:
- Upcoming fixed expenses.
- Recurring bills.
- Important budget thresholds.
- Other existing scheduled financial commitments.

Use Android notification APIs and local scheduling.

Do not use cloud push notifications.

Respect notification permission requirements.

## 10.6 Swipe Action Customization

Allow users to configure transaction swipe actions.

Possible actions:
- Edit.
- Duplicate.
- Delete.
- None.

Provide safe defaults.

Avoid interfering with main-screen navigation.

## 10.7 Haptic Feedback

Implement subtle tactile feedback for:
- Numeric keypad presses.
- Category selection.
- Segmented controls.
- Important confirmation actions.

Provide an option to disable it.

Respect system accessibility and haptic preferences.

## 10.8 Adaptive Layouts

Support:
- Small phones.
- Standard smartphones.
- Large-screen phones.
- Landscape orientation where practical.
- System font scaling.

Ensure all five main screens maintain consistent typography and content width.

Do not repeat the earlier Journey screen scaling issue.

## 10.9 App Version Details

Show the app's actual installed version in:

Settings → About.

Do not place the app version in the main header.

Read version information from the application build configuration.

## 10.10 Financial Report Export

Allow exporting financial reports locally as:
- CSV.
- PDF.

Reports may include:
- Date range.
- Income.
- Expenses.
- Category breakdown.
- Budget performance.
- Fuel expenditure.
- Monthly summary.

Use local document generation.

Do not require online services.

---

# PHASE 11 — ANDROID HOME SCREEN WIDGETS

Implement useful, lightweight Android home-screen widgets.

## Widget A — Monthly Expense Summary

Display:
- Current month.
- Total expenses.
- Monthly budget usage.
- Remaining category/overall budget when configured.

Tapping opens the application.

## Widget B — Quick Add

Provide shortcuts:
- Add Expense.
- Add Income.
- Scan Receipt.

Open the appropriate existing app screen.

## Widget C — Financial Overview

Display:
- Actual monthly income.
- Actual monthly expenses.
- Actual net cash flow.

Avoid implying that net cash flow equals the user's true bank balance.

## Widget Technical Requirements

Use Jetpack Glance or another appropriate supported Android widget API.

Keep widgets fully offline.

Update widgets when relevant financial data changes, and use appropriate scheduling for calendar changes.

Avoid excessive battery consumption.

Provide suitable layouts for supported widget sizes.

Respect Light and Dark themes where supported.

Consider privacy:
- Provide an option to hide monetary values in widgets.
- Avoid exposing sensitive data unnecessarily on shared devices.

Do not require network connectivity.

---

# PHASE 12 — TESTING AND QUALITY ASSURANCE

Test the following:

## Financial Accuracy
- Income aggregation.
- Expense totals.
- Fixed monthly commitments.
- Monthly allocation calculations.
- Budget progress.
- Refund calculations.
- Recurring transaction handling.
- No duplicated expenses.
- Forecast vs actual separation.

## Expenses
- Favourite transactions.
- Merchant/category suggestions.
- Duplicate detection.
- Receipt attachments.
- Undo functionality.
- Search and filtering.
- Spending Calendar date calculations.

## Fuel
- Full-tank historical calculations.
- Partial refuels.
- Weighted fuel consumption.
- Fuel price history.
- Cost per kilometre.
- Missing data.

## Journey
- Saved routes.
- Reversed routes.
- Round-trip calculations.
- Toll calculations.
- Fuel price scenarios.
- Estimated vs actual spending.
- Exported summaries.

## Navigation and UI
- Five-screen swipe navigation.
- Bottom navigation synchronization.
- Slider gesture handling.
- Transaction swipe actions.
- Adaptive layouts.
- Theme switching.
- Keyboard behaviour.
- Widget shortcuts and updates.

## Security
- Airplane-mode functionality.
- Final manifest permissions.
- Receipt OCR fully offline.
- Encrypted backup and restore.
- App lock.
- Privacy mode.
- No network-dependent operations.

## Data Integrity
- Database migration preservation.
- Data persistence after restart.
- No silent data deletion.
- Stable handling of empty databases.
- Backward compatibility with existing records.

---

# IMPLEMENTATION STRATEGY

Do not attempt a complete rewrite.

Implement the project incrementally:

Milestone 1:
Home dashboard redesign and UI consistency.

Milestone 2:
Expense improvements and Spending Calendar.

Milestone 3:
Fuel and Journey improvements.

Milestone 4:
Analysis upgrades.

Milestone 5:
Settings, navigation and interaction improvements.

Milestone 6:
Security, offline notifications and Home Screen Widgets.

Milestone 7:
Final integration testing, migration checks and APK build.

After each milestone:
1. Check compilation where tools are available.
2. Run relevant automated tests.
3. Verify existing functionality has not regressed.
4. Report completed changes.
5. Create a Git checkpoint if possible.

Do not claim that a feature is implemented or tested unless verified.

## Final Deliverables

Provide:
- Updated Android project source code.
- List of implemented features.
- List of modified files.
- Database migration summary.
- Automated test results.
- Build results.
- Updated APK location if successfully generated.
- Known limitations.
- Any incomplete functionality.

Keep the app completely offline, privacy-focused, responsive and visually consistent.

Prioritize a high-quality finished result over adding unnecessary features.

Begin by inspecting the existing project and preparing the phased implementation plan, then proceed with the upgrades.