# 9. Exact money and the cents-first keypad

Money is one of the most important type decisions in this app. RM 12.50 is stored as **1250 sen**, a `Long`. Formatting turns that integer into text for display. Parsing turns validated text into an exact integer.

Binary floating point cannot represent many decimal fractions exactly. Using `Double` for money and repeatedly adding rounded values can introduce disagreements. Storing integer minor units avoids that representation problem. It does not prevent overflow, incorrect signs or mistakes about which records to count.

The project centralizes money behavior in `Money` in `domain/Calculations.kt`. Manual input is parsed through `BigDecimal` and converted with an exact operation. Invalid fractional sen are rejected. Estimated values use a specified rounding rule, `HALF_UP`, at the appropriate monetary boundary.

```text
Stored:       125050 sen
Decimal:      1250.50 ringgit
Display:      RM 1,250.50
```

Never parse the formatted display string by simply calling `toDouble()`. It contains a currency prefix and grouping characters. Keep the numeric value separate from presentation.

## Deriving the keypad

Let `c` be the current amount in sen and `d` the next digit. Appending a digit is:

```text
new amount = c × 10 + d
```

Starting at zero:

| Key | Stored sen | Display |
|---|---:|---|
| 1 | 1 | RM 0.01 |
| 2 | 12 | RM 0.12 |
| 3 | 123 | RM 1.23 |
| 4 | 1234 | RM 12.34 |
| 5 | 12345 | RM 123.45 |
| Backspace | 1234 | RM 12.34 |
| Clear | 0 | RM 0.00 |

Integer division by ten removes the last digit. Leading zeroes remain zero until a nonzero digit is entered. No decimal-point button is needed.

The production `appendSenDigit` checks the bound *before multiplying*:

```kotlin
if (cents > (MAX_CENTS - digit) / 10) return null
return cents * 10 + digit
```

Checking only after multiplication is unsafe because overflow may already have occurred. `MAX_CENTS` is 100,000,000,000 sen in this snapshot, equivalent to RM 1,000,000,000. A permitted per-record amount does not automatically make every aggregate sum safe; future changes must consider total bounds as well.

Editing an expense initializes the keypad directly from its saved sen. A stored 1250 must display RM 12.50. Multiplying it by 100 again would show RM 1,250.00 and corrupt the user's intention.

## Rounding policy

A unit fuel price can have more precision than a final payable amount. Keep the price as decimal text/`BigDecimal`, calculate with sufficient precision, then round the final cost to sen. Avoid constructing `BigDecimal` directly from an imprecise binary value such as `0.1`; use a decimal string or the deliberately chosen conversion API.

For an allocation, independently rounding every percentage can make the total differ by a sen. Chapter 13 explains how the project distributes the remainder.

**Common mistakes:** storing ringgit in an integer column described as sen; rounding twice; treating zero as an acceptable expense; replacing an invalid amount with zero; forgetting that refunds are negative; allowing an unchecked long sum to wrap.

**Practice:** enter 0, 0, 7, 5, backspace, 0. The final stored amount is 70 sen and the display is RM 0.70. Write this expectation before running the test.

# 10. Expenses, categories, payment history, refunds and Undo

An expense is a durable financial event, not merely a line of UI text. Its ID remains stable during an edit. Category and payment labels are presentation choices layered over stored meaning.

## Category selection

The entry screen uses an icon row, including custom categories. Others is ordered last. The main Expenses screen has its own filter row; filtering a list must not mutate the entry screen's selected category or rewrite records.

The current manual-entry category helper excludes the special fuel-transport category so the fuel workflow can maintain its linked records. Existing fuel entries still appear in reporting and relevant filters. Always distinguish “categories stored in the app” from “categories available in this particular action.”

A category rename should preserve its ID. A custom-category deletion uses an explicit reassignment strategy. If a saved favorite or merchant mapping points to a deleted category, that relationship also needs repair.

## Payment methods and compatibility

The visible choices are E-wallet, Cash and Card in the current implementation. The display group Card includes historical strings `Card`, `Credit Card` and `Debit Card`.

Grouping does not require rewriting history. `selectExpensePayment` preserves an original historical string when the user keeps its display group selected. If the user explicitly changes to a different group, the new selection is saved. Historical “Not specified” records remain records; editing requires an explicit valid choice instead of silently assuming Cash.

The last valid payment selection is saved locally. Treat this preference as convenience, not evidence of how a historical purchase was paid.

## Refunds

A refund is represented as a signed financial adjustment linked to a purchase. Net spending is purchases plus signed refunds. A RM 100 purchase and a RM 30 refund produce RM 70 net spending.

The repository must enforce that the total refunded amount does not exceed the eligible purchase amount. Checking each refund independently is insufficient: two separate RM 70 refunds could each seem valid against a RM 100 purchase but total RM 140.

When editing a purchase, the new amount must still cover refunds already attached. Refunds use the original purchase category so category totals remain coherent. Dates matter: a refund in a later month reduces spending in that later month; it does not automatically rewrite last month's actual events.

## Undo is a new operation with old information

The project records an Undo journal containing relevant before/after state. Undo verifies that the current record still matches the expected post-operation state. Otherwise it could overwrite a subsequent edit.

Consider a real regression caught during the audit:

1. A purchase was RM 10.
2. It was edited to RM 20.
3. A RM 15 refund was recorded.
4. Undo attempted to restore the purchase to RM 10.

Restoring RM 10 while keeping RM 15 refunded breaks the financial invariant. The fix checks all current linked refunds inside the Undo transaction. It rejects this restoration and preserves the Undo entry. Once the conflict is resolved, Undo can succeed.

This teaches a general principle: reversing an old change must respect the present state of related records.

## Linking existing transactions

The monthly planner can mark a commitment paid by linking an existing expense. Another audit test found that the repository once allowed a refund ID to be linked even though the UI picker hid refunds. The repository now requires an ordinary Expense and validates the updated record before committing.

UI filtering is helpful, but repository validation protects all entry points.

**Practice:** for a purchase of RM 80 with RM 20 already refunded, decide whether a new refund of RM 60, RM 60.01 and RM 0 is acceptable. The exact remaining amount can be valid; an excess and a zero refund must be rejected by the relevant validation.

# 11. Fuel consumption: mathematics, assumptions and linked spending

The fuel tracker stores odometer readings, litres purchased, cost and supporting metadata. It uses successive valid entries to estimate consumption. Understanding the physical assumptions is essential.

For two entries:

```text
distance = current odometer − previous odometer
estimated km/L = distance / litres bought at current entry
estimated L/100 km = litres bought at current entry × 100 / distance
```

For a valid interval, km/L and L/100 km are reciprocally related:

```text
L/100 km = 100 / km/L
```

Higher km/L means better efficiency. Lower L/100 km means better efficiency. Mixing these directions in an “improved” label reverses the meaning.

## Worked history

| Entry | Odometer | Litres purchased | Role |
|---|---:|---:|---|
| A | 10,000 km | 20 L | Establishes first baseline |
| B | 10,400 km | 30 L | First measured distance interval |
| C | 10,600 km | 20 L | Second interval |

For A→B, distance is 400 km. The estimate is 400/30 = 13.333 km/L or 7.5 L/100 km. For B→C, it is 200/20 = 10 km/L or 10 L/100 km.

The combined estimate is **600/50 = 12 km/L**, or **50/600 × 100 = 8.333 L/100 km**. Averaging 13.333 and 10 directly gives 11.667 km/L, which is wrong for the combined distance and fuel. Aggregate the underlying quantities before dividing.

Entry A's purchased fuel is not included in those intervals because there is no earlier distance observation associated with it.

## Purchased fuel is not always consumed fuel

The conservation equation is:

```text
fuel consumed = fuel purchased + starting tank fuel − ending tank fuel
```

If tank fuel levels after successive fills are equal, purchased litres can estimate consumption over the interval. With arbitrary partial fills and no tank-level measurement, odometer readings and receipts alone cannot establish exact consumption.

The current every-fill calculation is therefore an **estimate**. It should not be taught as a physically exact measurement merely because the arithmetic is precise. A partial fill can make a short interval appear unusually efficient or inefficient. Longer histories may reduce some variation, but do not prove the unknown tank-level difference is zero.

The code also retains older full-to-full cycle calculations and historical full-tank flags. A full-to-full cycle starts at a full baseline, accumulates subsequent purchased litres including partial fills, and closes at another full fill. Preserving this logic protects existing data and gives a useful contrasting method.

## Sequence validation

Valid fuel points require finite, positive litres and valid money. Odometer and timestamp order must increase across the sequence. `NaN` and infinity must be rejected explicitly; ordinary comparisons can behave unexpectedly with them.

Editing a middle entry affects both neighboring intervals. Validate the entire proposed chronological sequence, not only “new odometer is greater than the latest row.” Equal timestamps and non-increasing odometers need clear handling. An actual odometer replacement would require an explicit product model rather than accepting a negative distance silently.

## Avoid double counting cost

Saving a refuel also maintains one linked expense in the fuel category. The write is atomic. Expense summaries count the expense ledger. They must not sum all expenses and then add all refuel costs again.

Editing the refuel updates the linked expense while preserving relevant identity and payment information. Deleting it removes the linked financial record according to the relationship. Petrol receipt recognition routes through review because an existing refuel may already represent the purchase.

**Practice:** calculate 350 km on 25 L. The result is 14 km/L and approximately 7.143 L/100 km. Then explain why the same numbers do not prove the engine consumed exactly 25 L during that distance.

# 12. Journey planning and comparison with actual spending

A journey plan is an estimate. It does not create an expense merely because the user saves it. Origin and destination are useful labels; the offline app does not secretly obtain live road distances from an online map.

The current journey model includes outbound distance, optional return distance, consumption, unit fuel price, tolls and parking. Consumption can come from valid history, an enabled fallback or an explicit override. The source should remain visible so the user knows the basis of the estimate.

## Cost calculation

```text
distance = outbound km + return km, if round trip
litres = distance × consumption L/100 km / 100
fuel cost = litres × price per litre
total = fuel cost + applicable tolls + parking
```

Example: outbound 120 km, return 100 km, consumption 7.5 L/100 km, fuel RM 2.05/L, outbound toll RM 8.00, return toll RM 6.00 and parking RM 5.00.

Distance is 220 km. Estimated fuel is 16.5 L. Fuel cost is RM 33.825, rounded to RM 33.83. Total is **RM 52.83**.

Do not automatically double outbound tolls: the return route may differ. Do not double parking unless the user actually enters two charges through a supported model. A “round trip” toggle should establish which fields apply, not invent road information.

## Versioned saved plans

Older saved journeys include sharing and other-fee fields. The model retains them with a cost version so loading a historical plan does not silently change its meaning. New editor plans use cost version 2, which follows the current fuel+tolls+parking model.

This is **data-model versioning**, separate from the app version and Room schema version. JSON stored inside a database row can need its own compatibility logic even when the table columns do not change.

## Estimated versus actual

A journey can link recorded expenses to compare actual cost with the estimate. Linking should not duplicate those expenses in the ledger. The repository prevents the same actual expense from being attributed to two journeys under the current rules.

Refunds associated with linked expenses affect actual spending. If a linked record is missing, the project returns an unavailable actual result rather than displaying zero. Zero means known zero; missing means insufficient information.

Reversing a journey should consistently exchange origin/destination and the corresponding distances and toll lists. It should not accidentally retain an outbound toll under the return label.

**Common mistakes:** using km/L in a formula expecting L/100 km; converting cents twice; ignoring return-distance edits; including hidden legacy fees in new plans; posting estimates as transactions; displaying missing linked data as free travel.

**Practice:** change only the unit price to RM 2.10/L in the worked example. Fuel becomes RM 34.65 and total RM 53.65. Tolls and parking stay the same.

# 13. Monthly commitments, allocations and recurring work

The money planner separates planning from actual transactions. This distinction prevents a visually convincing dashboard from making false claims.

```text
Available for allocation = expected income − fixed commitments
Remaining allowance = available for allocation − variable spending
Actual net cash flow = actual income − actual expenses
```

Suppose expected income is RM 5,000 and fixed commitments are RM 2,000. Available for allocation is RM 3,000. If variable spending is RM 900, remaining allowance is RM 2,100.

If RM 1,200 of fixed commitments has already been paid, do not deduct that RM 1,200 again from the RM 3,000 allowance. It was already reserved within the RM 2,000. Actual cash flow still includes the paid expense once.

Received income may differ from expected income. None of these values is a verified bank balance because opening balances and unrecorded transactions may be unknown.

## Allocation representation and rounding

Percentages use **basis points**: 10,000 basis points equals 100%, and one basis point is 0.01%. The default plan in this snapshot is 20% savings, 30% food/groceries, 15% lifestyle and 35% general spending.

For RM 3,000, this yields RM 600, RM 900, RM 450 and RM 1,050. Savings is a planned reserve, not an expense or bank transfer. Spending categories can show allocated, spent and remaining values; overspending can legitimately make remaining negative.

Validation checks the total, category identities, valid ranges, duplicate spending-category mappings and savings restrictions. Two allocations must not both claim the same spending category and thereby count its expense twice.

For awkward amounts, `allocationAmounts` uses a largest-remainder method with `BigInteger` multiplication:

1. Multiply available sen by each basis-point percentage.
2. Divide by 10,000 and retain quotient and remainder.
3. Assign each quotient.
4. Distribute leftover sen to the largest fractional remainders.
5. Use input order as a stable tie-breaker.

For 100 sen divided 33.33%, 33.33%, 33.34%, floor amounts are 33, 33, 33. One leftover sen goes to the last category, yielding 33, 33, 34. The exact sum remains 100.

When available money is zero or negative, allocations use zero rather than assigning negative savings. The deficit still needs to be visible in planning. Clamping allocation amounts must not conceal the original negative availability.

## Defaults, overrides and historical months

An allocation has a scope: `default` or a specific `YYYY-MM`. A month override changes that month without rewriting the default. Before changing defaults, the repository freezes applicable saved past-month allocations that lack explicit overrides. Saved past commitments also retain their intended history.

Do not recalculate every old month from today's recurring settings. A rent increase in November should not casually rewrite a September plan. Conversely, an existing historical snapshot is not proof the same bill was actually paid.

## Recurrence and idempotency

Recurring rules have anchors, frequencies, enablement and next dates. A monthly rule anchored on January 31 should move to February 28 or 29 and then March 31. If you repeatedly add one month to the clipped February date, the schedule can drift to the 28th.

An occurrence is identified by its rule and scheduled date. A unique index plus transactional insert/cursor update prevents repeated launches or workers from creating duplicates. This property is **idempotency**: repeating the reconciliation has no additional effect for already handled occurrences.

WorkManager runs periodic work approximately according to system scheduling. The current worker requests a 12-hour interval; it is not an exact alarm and must tolerate delay. Manual commitments and expected income do not become actual cash transactions merely because their due date arrives.

**Practice:** expected income RM 4,000, commitments RM 2,500, variable spending RM 700. Allocation base is RM 1,500 and remaining allowance RM 800, regardless of how much of the reserved commitments has already been paid in the correctly linked same-month flow.

# 14. Dates, analytics, charts and exports

A timestamp is an instant; a day or month is a calendar interpretation in a time zone. The app commonly converts epoch milliseconds through `Instant`, a `ZoneId` and `LocalDate`. This matters even when the primary audience is Malaysian.

For a month, use the start of its first day and the start of the next month as an exclusive end. Adding a fixed number of milliseconds for every local day can fail in zones with daylight-saving transitions. Using “31 days” as a month fails almost everywhere.

Inject or pass dates and zones in tests where practical. A test that depends on the real current date may pass today and fail next month.

## One authoritative set of records

Reports filter actual expenses and income for the selected date range. Category totals group by stable IDs; display names are looked up separately. Refunds contribute signed values. Fixed and variable reporting follows explicit linkage rules rather than guessing from a description containing “rent.”

The financial report labels net cash flow as net cash flow and explicitly says it is not a bank balance. A budget is a constraint or plan, not another expense row. A report that includes full-month budget data alongside a partial date range must label the budget's scope.

## Charts communicate data semantics

Use line charts for trends and bars for comparable totals. A donut can show proportions of a meaningful nonnegative total; a negative refund-dominated category cannot be drawn as a negative wedge. Preserve signed net totals in labels or a suitable breakdown even when a chart needs a restricted representation.

Keep a sensible zero baseline for signed bars. Do not turn missing data into zero solely to make a smooth line. An empty chart should explain that there is insufficient history.

The daily spending chart has an interactive date selection. Centering “Today” means centering its actual plotted position, not centering a label within a container while ignoring chart padding. When debugging, compare plot coordinates and viewport coordinates explicitly.

Accessible charts need textual values, meaningful labels and contrast in both themes. Color alone should not carry essential meaning. A category named “Food” remains understandable when a user cannot distinguish two neighboring hues.

## CSV and PDF exports

CSV looks simple until fields contain commas, quotes, line breaks or spreadsheet formulas. The current `csvCell` quotes every field, doubles embedded quotes and prefixes dangerous formula-leading content. A merchant named `=...` should not become an executable spreadsheet formula when someone opens an export.

The same protection can cause a negative numeric text value to be treated as text by a spreadsheet. Preserve its signed value and explain import expectations rather than silently making refunds positive.

Exports use explicit user-selected document destinations. The app's lack of internet permission does not control what an external document provider or sharing application does. Manual export is a deliberate privacy boundary.

**Practice:** determine which local day contains `2026-10-19T17:00:00Z` in Malaysia. It is 20 October at 01:00. A report using UTC calendar dates would put it on the wrong local day.
