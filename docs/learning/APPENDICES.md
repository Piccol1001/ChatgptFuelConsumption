# Appendix A. Read real code, one decision at a time

These excerpts are extracted directly from the pinned Git checkpoint. They are not newly invented replacement implementations. Read the explanation, predict a result, then compare with the function and its tests.

## A1. Exact money

The regex limits manual input to decimal money; moving the decimal point converts RM to sen. longValueExact rejects non-integral or overflowing conversion. The range check rejects zero and excess. Estimates deliberately use rounding; formatting never changes the stored integer.

Exact source: [app/src/main/java/my/fuelledger/domain/Calculations.kt, line 13](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/domain/Calculations.kt#L13). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
object Money {
    fun parse(text: String): Long {
        require(Regex("\\d{1,10}(\\.\\d{1,2})?").matches(text.trim())) {
            "Enter an amount with at most two decimal places."
        }
        val cents = text.trim().toBigDecimal().movePointRight(2).longValueExact()
        require(cents in 1..MAX_CENTS) { "Amount must be positive and at most RM1,000,000,000." }
        return cents
    }
    fun fromEstimate(value: BigDecimal): Long = value.setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2).longValueExact()
    fun decimal(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
    fun format(cents: Long): String = "RM " + String.format(Malaysia, "%,.2f", BigDecimal.valueOf(cents, 2))
}
```

## A2. The entire keypad algorithm

require validates the function's input contract. The rearranged inequality checks the maximum before multiplication. A null result means reject the proposed digit. Integer division drops the final digit.

Exact source: [app/src/main/java/my/fuelledger/domain/CentsKeypad.kt, line 4](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/domain/CentsKeypad.kt#L4). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
fun appendSenDigit(cents: Long, digit: Int): Long? {
    require(cents in 0..MAX_CENTS && digit in 0..9)
    if (cents > (MAX_CENTS - digit) / 10) return null
    return cents * 10 + digit
}

fun backspaceSen(cents: Long): Long {
    require(cents in 0..MAX_CENTS)
    return cents / 10
}
```

## A3. A test that protects the money boundary

The tests cover the user's exact keystroke sequence, editing compatibility and inclusive maximum. Expected formatted values are written explicitly, rather than recomputed with the same formula.

Exact source: [app/src/test/java/my/fuelledger/domain/CentsKeypadTest.kt, line 6](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/domain/CentsKeypadTest.kt#L6). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
class CentsKeypadTest {
    @Test fun digitsShiftIntegerSenAndFormatExactRinggit() {
        var cents = 0L
        val amounts = listOf("RM 0.01", "RM 0.12", "RM 1.23", "RM 12.34", "RM 123.45")
        "12345".forEachIndexed { index, digit ->
            cents = appendSenDigit(cents, digit.digitToInt())!!
            assertEquals(amounts[index], Money.format(cents))
        }
        assertEquals(12345L, cents)
        assertEquals(cents, Money.parse(Money.decimal(cents)))
    }
    @Test fun leadingZerosBackspaceAndExistingAmountsNeverRescaleSavedSen() {
        var cents = 0L
        "00012".forEach { cents = appendSenDigit(cents, it.digitToInt())!! }
        assertEquals(12L, cents)
        assertEquals(1L, backspaceSen(cents)); assertEquals(0L, backspaceSen(1)); assertEquals(0L, backspaceSen(0))
        assertEquals(1234L, backspaceSen(12345)); assertEquals(123456L, appendSenDigit(12345, 6))
        assertEquals("RM 123.45", Money.format(12345))
    }
    @Test fun maximumIsInclusiveAndExcessDigitsCannotOverflow() {
        assertEquals(MAX_CENTS, appendSenDigit(MAX_CENTS / 10, 0))
        assertNull(appendSenDigit(MAX_CENTS / 10, 1))
        assertNull(appendSenDigit(MAX_CENTS, 9))
        assertEquals(MAX_CENTS / 10, backspaceSen(MAX_CENTS))
        assertTrue(runCatching { appendSenDigit(Long.MAX_VALUE, 9) }.isFailure)
        assertTrue(runCatching { appendSenDigit(-1, 0) }.isFailure)
        assertTrue(runCatching { appendSenDigit(0, 10) }.isFailure)
    }
}
```

## A4. Fuel intervals

sortBy establishes chronological order. zipWithNext creates adjacent pairs. mapNotNull discards invalid intervals. The later entry supplies purchased litres and cost; the odometer difference supplies distance. This implements the estimate and its assumptions discussed in Chapter 11.

Exact source: [app/src/main/java/my/fuelledger/domain/Calculations.kt, line 65](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/domain/Calculations.kt#L65). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
fun fillEstimates(points: List<FuelPoint>): List<FillEstimate> = points.sortedBy { it.timestamp }
    .zipWithNext().mapNotNull { (previous, current) ->
        if (!validFuelPoint(previous) || !validFuelPoint(current) ||
            current.timestamp <= previous.timestamp || current.odometer <= previous.odometer) null
        else FillEstimate(previous.id, current.id, current.odometer - previous.odometer,
            current.litres, current.cents)
    }
```

## A5. A refuel and one linked expense

The repository lock coordinates its writes. Validation precedes changes. Inside the Room transaction, the edited point is checked against the entire sequence. Existing linked identity/payment are preserved, and both rows commit together. The edit's before/after records support Undo.

Exact source: [app/src/main/java/my/fuelledger/data/LedgerRepository.kt, line 178](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/LedgerRepository.kt#L178). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
    suspend fun saveRefuel(refuel: Refuel) = writes.withLock {
        validateRefuel(refuel)
        database.withTransaction {
            val remaining = dao.allRefuels().filter { it.id != refuel.id }
            validateSequence((remaining + refuel).map { it.point() })
            val old = dao.linkedExpense(refuel.id)
            val oldRefuel = dao.allRefuels().find { it.id == refuel.id }
            dao.putRefuel(refuel)
            val updated = Expense(id = old?.id ?: java.util.UUID.randomUUID().toString(), cents = refuel.cents,
                categoryId = FUEL_CATEGORY, timestamp = refuel.timestamp, description = fuelDescription(refuel),
                notes = refuel.notes, payment = old?.payment ?: "Cash", fuelId = refuel.id, receiptFile = refuel.receiptFile)
            dao.putExpense(updated)
            if (old != null && oldRefuel != null && (old != updated || oldRefuel != refuel)) database.toolsDao().putUndo(TransactionUndo(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), backupJson.encodeToString(TransactionChange(old, updated, oldRefuel, refuel))))
        }
    }
```

## A6. Historical payment preservation

The visible Card group accepts several old stored strings. If the selected group is unchanged, the original string is returned. Grouping a display is not the same as migrating historical data.

Exact source: [app/src/main/java/my/fuelledger/data/ExpenseEntry.kt, line 3](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/ExpenseEntry.kt#L3). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
val expensePaymentOptions = listOf("E-wallet", "Cash", "Card")

/** Presentation grouping only. Never migrate or rewrite historical payment strings. */
fun expensePaymentGroup(payment: String): String? = when (payment) {
    "Cash" -> "Cash"
    "Card", "Credit Card", "Debit Card" -> "Card"
    "E-wallet" -> "E-wallet"
    else -> null
}

fun selectExpensePayment(current: String, selection: String): String {
    require(selection in expensePaymentOptions)
    return if (expensePaymentGroup(current) == selection) current else selection
}
```

## A7. Fixed commitments are excluded once

The reserved set identifies rule/date occurrences, while fixedIds identifies their actual payments. The filter selects the actual month and excludes those reserved payments and their refunds. An unreserved payment has different allowance treatment.

Exact source: [app/src/main/java/my/fuelledger/data/MonthlyPlanner.kt, line 73](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/MonthlyPlanner.kt#L73). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
fun variableExpensesForMonth(month: YearMonth, occurrences: List<PlannedOccurrence>, expenses: List<Expense>, zone: ZoneId): List<Expense> {
    val reserved = occurrences.filter { it.kind == "Expense" }.map { it.ruleId to it.date }.toSet()
    val fixedIds = expenses.filter { (it.recurringRuleId to it.recurringDate) in reserved }.map { it.id }.toSet()
    return expenses.filter { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) == month &&
        (it.recurringRuleId == null || (it.recurringRuleId to it.recurringDate) !in reserved) &&
        !(it.kind == "Refund" && it.refundOf in fixedIds) }
}
```

## A8. Largest-remainder allocation

BigInteger avoids overflowing the intermediate multiplication. Quotients give floor amounts. Sorted remainders determine who gets each leftover sen. Savings has no spending-category expense deduction.

Exact source: [app/src/main/java/my/fuelledger/data/MonthlyPlanner.kt, line 97](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/MonthlyPlanner.kt#L97). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
fun allocationAmounts(available: Long, categories: List<AllocationCategory>, expenses: List<Expense>,
    month: YearMonth, zone: ZoneId = ZoneId.systemDefault(), reserved: List<PlannedOccurrence> = emptyList()): List<AllocationAmount> {
    validateAllocationPlan(allocationPlan("default", categories))
    val positive = maxOf(0, available)
    val parts = categories.map { java.math.BigInteger.valueOf(positive).multiply(java.math.BigInteger.valueOf(it.basisPoints.toLong())).divideAndRemainder(java.math.BigInteger.valueOf(10000)) }
    val amounts = parts.map { it[0].longValueExact() }.toMutableList()
    val leftover = (positive - amounts.sum()).toInt()
    parts.indices.sortedWith(compareByDescending<Int> { parts[it][1] }.thenBy { it }).take(leftover).forEach { amounts[it]++ }
    val variable = variableExpensesForMonth(month, reserved, expenses, zone)
    return categories.mapIndexed { index, category -> AllocationAmount(category, amounts[index],
        if (category.savings) 0 else variable.filter { it.categoryId in category.categoryIds }.sumOf { it.cents }) }
}
```

## A9. Flexible percentage movement

Only the selected and flexible categories change. The target is clamped to their combined percentage. Subtracting the same delta from the flexible category preserves the exact total.

Exact source: [app/src/main/java/my/fuelledger/data/Refinement.kt, line 27](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/Refinement.kt#L27). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
fun moveAllocation(categories: List<AllocationCategory>, id: String, requested: Int): List<AllocationCategory> {
    require(categories.isNotEmpty() && categories.sumOf { it.basisPoints } == 10000 && categories.all { it.basisPoints in 0..10000 })
    val flexible = flexibleAllocationId(categories)
    if (id == flexible) return categories
    val selected = requireNotNull(categories.find { it.id == id })
    val remainder = categories.first { it.id == flexible }
    val target = requested.coerceIn(0, selected.basisPoints + remainder.basisPoints)
    val delta = target - selected.basisPoints
    return categories.map { when (it.id) { id -> it.copy(basisPoints = target); flexible -> it.copy(basisPoints = it.basisPoints - delta); else -> it } }
}
```

## A10. Conservative auto-save validation

The function collects transparent reasons for review. It does not use an unexplained percentage confidence score. Ambiguity, weak images, foreign currency and possible fuel duplication are explicit gates.

Exact source: [app/src/main/java/my/fuelledger/data/ReceiptAutomation.kt, line 43](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/ReceiptAutomation.kt#L43). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
fun receiptAutoSaveIssues(s: ReceiptSuggestions, categoryId: String?, duplicate: Boolean,
    today: LocalDate = LocalDate.now()): List<String> = buildList {
    s.imageQualityIssue?.let { add(it) }
    if (s.station.isNullOrBlank() || s.station.length > 200) add("Merchant is missing or unclear.")
    if (s.amount == null || runCatching { Money.parse(s.amount) }.isFailure || !s.strictTotal || s.ambiguousTotals) add("The final total needs review.")
    val date = s.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (date == null || date > today || date.year < 1970 || s.ambiguousDates) add("The transaction date needs review.")
    if (s.currency != "MYR") add("Only MYR receipts can be accepted; convert other currencies manually in Add Expense.")
    if (categoryId.isNullOrBlank()) add("Choose an expense category.")
    if (categoryId == FUEL_CATEGORY || s.fuelType != null) add("Petrol receipts need review against existing refuelling records.")
    if (duplicate) add("A matching receipt or transaction may already exist.")
}
```

## A11. Encrypted settings persistence

DataStoreFactory uses a custom serializer and migration. The public save method validates before updateData. The repository additionally serializes transformations so separate preferences do not overwrite a stale snapshot.

Exact source: [app/src/main/java/my/fuelledger/data/SettingsStore.kt, line 57](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/SettingsStore.kt#L57). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
class SettingsStore(context: Context, vault: LocalVault = LocalVault(context),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) : PreferencesStore {
    private val store = DataStoreFactory.create(serializer = EncryptedSettingsSerializer(vault),
        migrations = listOf(LegacySettingsMigration(context.applicationContext)), scope = scope,
        produceFile = { File(context.noBackupFilesDir, "settings/settings.v1.enc") })
    override val settings = store.data
    override suspend fun save(settings: AppSettings) {
        settings.validate()
        store.updateData { settings }
    }
}
```

## A12. Real pager and child gesture boundary

One list maps page keys to labels. HorizontalPager owns page dragging and snapping. Only unused horizontal child movement is consumed at the boundary. Inactive pages are visually available during a drag but do not expose duplicate focus/semantics.

Exact source: [app/src/main/java/my/fuelledger/ui/MainPager.kt, line 21](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/MainPager.kt#L21). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
internal data class MainDestination(val key: String, val label: String, val symbol: String)
internal val mainDestinations = listOf(
    MainDestination("Home", "Home", "home"), MainDestination("Expenses", "Expenses", "expenses"),
    MainDestination("Fuel", "Fuel", "fuel"), MainDestination("Journey", "Journey", "route"),
    MainDestination("Analytics", "Analysis", "analytics"),
)

// Nested child scrollers own their entire gesture, including overflow/flings at either edge.
// Consume only unused horizontal deltas here, before they reach Pager's scrolling ancestor.
// Direct page swipes still use Foundation's native touch slop, drag and snap handling.
private object ChildScrollBoundary : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = Offset(available.x, 0f)
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(available.x, 0f)
}

@Composable
internal fun MainPager(state: PagerState, enabled: Boolean, content: @Composable (Int) -> Unit) {
    HorizontalPager(
        state = state, modifier = Modifier.fillMaxSize().testTag("main-pager"),
        key = { mainDestinations[it].key }, userScrollEnabled = enabled,
        // Keep physical left/right behavior consistent with the requested order in RTL, too.
        reverseLayout = LocalLayoutDirection.current == LayoutDirection.Rtl,
        pageNestedScrollConnection = ChildScrollBoundary,
    ) { page ->
        val active = page == state.currentPage
        // Neighbors are visible while dragging, but only the current page exposes controls.
        Box(Modifier.fillMaxSize().testTag("main-page-${mainDestinations[page].key}")
            .focusProperties { canFocus = active }
            .then(if (active) Modifier else Modifier.clearAndSetSemantics {})) { content(page) }
    }
}
```

## A13. A preserving schema addition

ALTER TABLE adds columns with compatible defaults or nullability. Existing rows survive. A new installation and an upgraded installation must converge on the expected exported schema.

Exact source: [app/src/main/java/my/fuelledger/data/Database.kt, line 102](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/Database.kt#L102). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE refuels ADD COLUMN fuelType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE refuels ADD COLUMN stationLocation TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE refuels ADD COLUMN receiptFile TEXT")
            }
        }
```

## A14. Spreadsheet-safe CSV cells

Formula-leading content is neutralized, quotes are doubled and every field is quoted. The UTF-8 BOM aids common spreadsheet import behavior. A CSV writer needs more than joining strings with commas.

Exact source: [app/src/main/java/my/fuelledger/data/Backup.kt, line 110](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/Backup.kt#L110). Imports or surrounding declarations may be outside this excerpt; use the companion for the complete file.

```kotlin
fun csvCell(value: String): String {
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
    return "\"${safe.replace("\"", "\"\"")}\""
}
fun csv(rows: List<List<String>>) = "\uFEFF" + rows.joinToString("\r\n") { it.joinToString(",", transform = ::csvCell) } + "\r\n"
```

# Appendix B. Source-file atlas

All production Kotlin files in this snapshot are listed below. Links point to the immutable checkpoint, so they will not silently change when main advances. The offline source browser and source ZIP contain the full files, including resources, tests and build configuration.

## entry-points

- [FinanceViewModel.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/FinanceViewModel.kt): Income, recurring rules, budgets and monthly planning state.
- [JourneyViewModel.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/JourneyViewModel.kt): Saved journey state and repository operations.
- [LedgerApplication.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/LedgerApplication.kt): Application-owned vault, database and repository composition.
- [LedgerViewModel.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/LedgerViewModel.kt): Ledger state, receipt operations, preferences, backup and UI events.
- [MainActivity.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/MainActivity.kt): Android entry, lifecycle, authentication gate and Compose root.

## data

- [data/Backup.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/Backup.kt): Backup model validation, relationships and safe CSV encoding.
- [data/BackupCrypto.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/BackupCrypto.kt): Password-derived authenticated streaming backup encryption.
- [data/Database.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/Database.kt): Room entities, DAOs, constraints, schema and migrations 1–5.
- [data/ExpenseEntry.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/ExpenseEntry.kt): Payment display grouping and manual category availability.
- [data/ExpenseTools.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/ExpenseTools.kt): Favorites, Undo models and expense-tool validation.
- [data/FinanceData.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/FinanceData.kt): Income, budgets, recurring models, validation and anchored dates.
- [data/FinanceRepository.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/FinanceRepository.kt): Recurring reconciliation, commitment payment and planning persistence.
- [data/JourneyData.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/JourneyData.kt): Versioned journey payloads, estimates, actual links and storage.
- [data/LedgerRepository.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/LedgerRepository.kt): Atomic ledger writes, duplicate safeguards, Undo and restore.
- [data/MonthlyPlanner.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/MonthlyPlanner.kt): Planned occurrences, allowance and exact allocation calculations.
- [data/ReceiptAutomation.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/ReceiptAutomation.kt): Merchant memory, fingerprints and conservative auto-save gates.
- [data/ReceiptBackupArchive.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/ReceiptBackupArchive.kt): Bounded receipt archive staging, validation and restoration.
- [data/Refinement.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/Refinement.kt): Stable category order, fuel month series and flexible allocation movement.
- [data/SettingsStore.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/data/SettingsStore.kt): Typed encrypted DataStore and legacy settings migration.

## domain

- [domain/Calculations.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/domain/Calculations.kt): Exact money, measurements, trip estimates, fuel intervals and spending.
- [domain/CentsKeypad.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/domain/CentsKeypad.kt): Bounded integer digit entry and backspace.
- [domain/FinancialReport.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/domain/FinancialReport.kt): Actual income/expense reports and local financial insights.
- [domain/VehicleAnalysis.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/domain/VehicleAnalysis.kt): Coverage-aware vehicle cost, weighted price and consumption changes.

## finance

- [finance/PlannerReminders.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/finance/PlannerReminders.kt): Local planner notification generation.
- [finance/RecurringWorker.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/finance/RecurringWorker.kt): Unique periodic reconciliation via WorkManager.

## receipts

- [receipts/ReceiptParser.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/receipts/ReceiptParser.kt): Pure text candidate extraction for Malaysian receipt formats.
- [receipts/ReceiptScanner.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/receipts/ReceiptScanner.kt): Bundled ML Kit recognition, image quality and resource lifetime.
- [receipts/ReceiptStore.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/receipts/ReceiptStore.kt): Private encrypted photos, normalization, crop, rotate and limits.

## security

- [security/AppLock.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/security/AppLock.kt): Device authentication and session/lifecycle lock policy.
- [security/EncryptedDatabaseFiles.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/security/EncryptedDatabaseFiles.kt): Protected database passphrases and preserving conversion.
- [security/LocalVault.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/security/LocalVault.kt): Keystore AES-GCM encryption with purpose binding.
- [security/PreservingOpenHelperFactory.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/security/PreservingOpenHelperFactory.kt): SQLCipher integration that preserves files on open failure.

## ui

- [ui/AppLockSettings.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/AppLockSettings.kt): Authentication gate and lock preference interface.
- [ui/Components.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/Components.kt): Reusable cards, headings, fields, icons and shared UI helpers.
- [ui/DailySpendingChart.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/DailySpendingChart.kt): Scrollable daily amounts, date selection and Today positioning.
- [ui/DesignTokens.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/DesignTokens.kt): Shared spacing, shape, target and dashboard color constants.
- [ui/Editors.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/Editors.kt): Existing expense/refuel and supporting editing flows.
- [ui/ExpenseEntryControls.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/ExpenseEntryControls.kt): Category icons, payment buttons and cents keypad UI.
- [ui/ExpenseToolsUi.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/ExpenseToolsUi.kt): Favorites, refund/Undo and transaction-tool controls.
- [ui/FinanceScreen.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/FinanceScreen.kt): Income, budgets and recurring transaction controls.
- [ui/FinancialAnalysisUi.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/FinancialAnalysisUi.kt): Financial planning/actual summaries and report presentation.
- [ui/FintechTheme.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/FintechTheme.kt): Material palettes, typography and theme selection.
- [ui/JourneyScreen.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/JourneyScreen.kt): Journey form, estimates and saved journey presentation.
- [ui/JourneyToolsUi.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/JourneyToolsUi.kt): Route tools, actual-link selection and sharing.
- [ui/LedgerApp.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/LedgerApp.kt): Primary Scaffold, navigation and screen/form orchestration.
- [ui/LocalDataSettings.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/LocalDataSettings.kt): Local retention/cleanup controls.
- [ui/MainPager.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/MainPager.kt): Main destinations, HorizontalPager and gesture boundaries.
- [ui/MonthlyPlannerScreen.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/MonthlyPlannerScreen.kt): Monthly planning and allocation editing interface.
- [ui/OverviewDashboard.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/OverviewDashboard.kt): Home overview, budget, quick actions and upcoming items.
- [ui/PersonalizationSettings.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/PersonalizationSettings.kt): Preference UI and shared UI preference context.
- [ui/PressComponents.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/PressComponents.kt): Material component adapters for consistent interaction feedback.
- [ui/PressFeedback.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/PressFeedback.kt): Draw-only press scaling, rounded focus and motion settings.
- [ui/PrivacyText.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/PrivacyText.kt): Privacy-aware value presentation.
- [ui/ReceiptAttachment.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/ReceiptAttachment.kt): Receipt attachment, preview and image transformation UI.
- [ui/ReceiptCameraButton.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/ReceiptCameraButton.kt): Camera intent/FileProvider capture integration.
- [ui/RefinedControls.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/RefinedControls.kt): Shared calendar and refined supporting controls.
- [ui/ReportActions.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/ReportActions.kt): Explicit local CSV/PDF document exports.
- [ui/ScreenPreviews.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/ScreenPreviews.kt): Compose preview entry points and fixture states.
- [ui/Screens.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/Screens.kt): Main fuel, expense and analysis screen implementations.
- [ui/SettingsScreen.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/SettingsScreen.kt): Settings categories, scanner mode, About and local workflows.
- [ui/SmartReceiptScreen.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/SmartReceiptScreen.kt): Extracted receipt review and acceptance UI.
- [ui/VehicleAnalysisUi.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/ui/VehicleAnalysisUi.kt): Coverage and vehicle comparison presentation.

## widgets

- [widgets/LedgerWidgets.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/java/my/fuelledger/widgets/LedgerWidgets.kt): Android widgets, privacy-aware values and quick actions.

## Tests and evidence map

Start with the test closest to the rule you are studying. Test names describe coverage areas; read their assertions before treating them as proof of a broader claim.

### Host and fixture tests

- [data/ExpenseEntryTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/data/ExpenseEntryTest.kt)
- [data/ExpenseToolsTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/data/ExpenseToolsTest.kt)
- [data/FinanceRepositoryTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/data/FinanceRepositoryTest.kt)
- [data/PlannerRepositoryTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/data/PlannerRepositoryTest.kt)
- [data/PlanningCalculationsTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/data/PlanningCalculationsTest.kt)
- [data/ReceiptBackupArchiveTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/data/ReceiptBackupArchiveTest.kt)
- [data/RefinementTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/data/RefinementTest.kt)
- [data/RepositoryTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/data/RepositoryTest.kt)
- [data/SettingsStoreTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/data/SettingsStoreTest.kt)
- [domain/CalculationsTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/domain/CalculationsTest.kt)
- [domain/CentsKeypadTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/domain/CentsKeypadTest.kt)
- [domain/FinancialReportTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/domain/FinancialReportTest.kt)
- [domain/ProvisionalFuelTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/domain/ProvisionalFuelTest.kt)
- [domain/VehicleJourneyUpgradeTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/domain/VehicleJourneyUpgradeTest.kt)
- [receipts/ReceiptParserTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/receipts/ReceiptParserTest.kt)
- [receipts/ReceiptReviewTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/receipts/ReceiptReviewTest.kt)
- [receipts/ReceiptStoreTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/receipts/ReceiptStoreTest.kt)
- [security/AppLockSessionTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/security/AppLockSessionTest.kt)
- [security/AppLockStoreTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/security/AppLockStoreTest.kt)
- [security/LocalVaultTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/security/LocalVaultTest.kt)
- [testing/TestLedgerApplication.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/testing/TestLedgerApplication.kt)
- [ui/AppLockUiTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/AppLockUiTest.kt)
- [ui/AppSmokeTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/AppSmokeTest.kt)
- [ui/ClarityAdaptiveTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/ClarityAdaptiveTest.kt)
- [ui/ClarityChartTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/ClarityChartTest.kt)
- [ui/ClarityContrastTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/ClarityContrastTest.kt)
- [ui/ClarityHomeTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/ClarityHomeTest.kt)
- [ui/ComprehensiveUpgradeUiTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/ComprehensiveUpgradeUiTest.kt)
- [ui/ExpenseEntryControlsTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/ExpenseEntryControlsTest.kt)
- [ui/JourneyLayoutTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/JourneyLayoutTest.kt)
- [ui/MainPagerTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/MainPagerTest.kt)
- [ui/OverviewDashboardTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/OverviewDashboardTest.kt)
- [ui/PreferenceUpdateTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/PreferenceUpdateTest.kt)
- [ui/PressFeedbackTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/PressFeedbackTest.kt)
- [ui/PrivacyUiTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/PrivacyUiTest.kt)
- [ui/RefinedControlsTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/RefinedControlsTest.kt)
- [ui/RefinementChartTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/RefinementChartTest.kt)
- [ui/RefinementHomeTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/RefinementHomeTest.kt)
- [ui/RefinementIntegrationTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/ui/RefinementIntegrationTest.kt)
- [widgets/LedgerWidgetsTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/test/java/my/fuelledger/widgets/LedgerWidgetsTest.kt)

### Native Android tests

- [OfflinePrivacyTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/androidTest/java/my/fuelledger/OfflinePrivacyTest.kt)
- [ProductionStartupTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/androidTest/java/my/fuelledger/ProductionStartupTest.kt)
- [security/EncryptedStorageTest.kt](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/androidTest/java/my/fuelledger/security/EncryptedStorageTest.kt)

### Build policy tests

- [build/OfflineMlKitTelemetryTest.java](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/buildSrc/src/test/java/my/fuelledger/build/OfflineMlKitTelemetryTest.java)

### Release workflow tests

- [test_release.py](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/scripts/tests/test_release.py)

## Build, resources and schemas

- [app/build.gradle.kts](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/build.gradle.kts)
- [build.gradle.kts](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/build.gradle.kts)
- [buildSrc/build.gradle.kts](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/buildSrc/build.gradle.kts)
- [settings.gradle.kts](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/settings.gradle.kts)
- [gradle.properties](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/gradle.properties)
- [gradle/wrapper/gradle-wrapper.properties](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/gradle/wrapper/gradle-wrapper.properties)
- [app/src/main/AndroidManifest.xml](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/app/src/main/AndroidManifest.xml)
- [version.properties](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/version.properties)
- [scripts/release.py](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/scripts/release.py)
- [scripts/setup-cloud.sh](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/scripts/setup-cloud.sh)
- [RELEASING.md](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/RELEASING.md)
- [SECURITY.md](https://github.com/Piccol1001/ChatgptFuelConsumption/blob/1f13482bfc550da77e5c950e48075f62de12620f/SECURITY.md)

`app/src/main/res` contains packaged icons, themes, widget layouts and backup/provider rules. `app/schemas` contains all five exported Room schemas. `buildSrc/src/main` contains the dependency policy. The source ZIP preserves their directory structure.

# Appendix C. Matched screens for visual study

These existing host-rendered test screenshots use the same 393×851dp, mdpi, 100% font, Dark configuration. They are fixture captures, not personal financial records. They demonstrate alignment and component density; they do not independently prove calculation correctness or every physical-device behavior. See the dated CLARITY reports for provenance and the additional light/large-font/native captures.

## Home

![Home: matched synthetic test capture, Dark, 393×851dp.](assets/home.png)

## Expenses

![Expenses: matched synthetic test capture, Dark, 393×851dp.](assets/expenses.png)

## Fuel

![Fuel: matched synthetic test capture, Dark, 393×851dp.](assets/fuel.png)

## Journey

![Journey: matched synthetic test capture, Dark, 393×851dp.](assets/journey.png)

## Analysis

![Analysis: matched synthetic test capture, Dark, 393×851dp.](assets/analysis.png)

# Appendix D. Using the learning package

Read the PDF for fixed pagination and its clickable contents. Open the HTML file in a browser for an offline reading edition with chapter navigation. The DOCX is editable in compatible word processors; its automatic contents field may need updating after edits. Markdown is the editable text source. The complete ZIP groups these editions and the source companion.

Open `Source_Browser.html` after extracting the package to search file names and read code without Android Studio. Each source block retains line anchors. Extract `Source_Companion.zip` to inspect the actual directory structure. A manifest records every included source file's SHA-256 and the source checkpoint. No APK, signing key, personal database or machine cache is included. First builds can require SDK/dependency downloads and a local development signing configuration; do not substitute a generated key for the original distribution key.

The core book and source excerpts describe 1.5.4. App code and version were not changed to produce this material. Existing app test reports are clearly historical. Documentation validation, page count and artifact checksums are recorded with this learning edition.
