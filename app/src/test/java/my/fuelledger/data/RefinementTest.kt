package my.fuelledger.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import my.fuelledger.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RefinementTest {
    private fun point(id: String, time: Long, odo: Double, litres: Double, full: Boolean) = FuelPoint(id, time, odo, litres, 1000, full)
    @Test fun weightedAverageUsesTotalConsumedFuelAndDistanceAcrossUnequalCycles() {
        val points = listOf(point("a", 1, 1000.0, 99.0, true), point("partial1", 2, 1050.0, 3.0, false),
            point("partial2", 3, 1075.0, 2.0, false), point("b", 4, 1100.0, 5.0, true), point("c", 5, 1500.0, 20.0, true),
            point("unfinished", 6, 1700.0, 30.0, false))
        assertEquals(6.0, historicalConsumption(points)!!, 0.00001)
        assertEquals(6.0, historicalConsumption(points.reversed())!!, 0.00001)
        assertNotEquals(7.5, historicalConsumption(points)!!, 0.01)
    }
    @Test fun missingAndIncompleteHistoryHasNoInventedConsumption() {
        assertNull(historicalConsumption(emptyList()))
        val full = point("one", 1, 1000.0, 20.0, true)
        assertNull(historicalConsumption(listOf(full)))
        assertNull(historicalConsumption(listOf(full.copy(full = false), point("p", 2, 1200.0, 10.0, false))))
        assertNull(historicalConsumption(listOf(full, point("same", 2, 1000.0, 10.0, true))))
        assertNull(historicalConsumption(listOf(full, point("bad", 2, 1100.0, Double.NaN, false), point("last", 3, 1200.0, 10.0, true))))
    }
    @Test fun editingOrDeletingFuelChangesAverageAndIncompleteTrailingPurchasesStayExcluded() {
        val first = point("a", 1, 1000.0, 30.0, true)
        val second = point("b", 2, 1200.0, 20.0, true)
        assertEquals(10.0, historicalConsumption(listOf(first, second))!!, 0.0)
        assertEquals(5.0, historicalConsumption(listOf(first, second.copy(odometer = 1400.0)))!!, 0.0)
        assertNull(historicalConsumption(listOf(first)))
        assertNull(historicalConsumption(listOf(first, second.copy(full = false))))
    }
    @Test fun invalidCycleDoesNotPolluteSubsequentValidCycle() {
        val points = listOf(point("a", 1, 1000.0, 30.0, true), point("bad", 2, 950.0, 5.0, true), point("c", 3, 1150.0, 10.0, true))
        assertEquals(5.0, historicalConsumption(points)!!, 0.0)
    }
    @Test fun rollingChartsIncludeCurrentMonthAndZeroMonthsAcrossYearBoundary() {
        val records = listOf(Refuel("old", Instant.parse("2025-12-20T12:00:00Z").toEpochMilli(), 1000.0, 10.0, 2050, true))
        val values = fuelSpendingMonths(records, LocalDate.of(2026, 1, 10), 3, ZoneOffset.UTC)
        assertEquals(listOf("Nov 25", "Dec 25", "Jan 26"), values.map { it.first })
        assertEquals(listOf(0L, 2050L, 0L), values.map { it.second })
        assertEquals(listOf("Aug 26", "Sep 26", "Oct 26"), fuelSpendingMonths(emptyList(), LocalDate.of(2026, 10, 15), 3).map { it.first })
        for (period in listOf(3, 6, 12)) assertEquals(period, fuelSpendingMonths(emptyList(), LocalDate.of(2026, 10, 15), period).size)
        assertTrue(runCatching { fuelSpendingMonths(emptyList(), LocalDate.now(), 4) }.isFailure)
    }
    @Test fun fuelSpendingMonthUsesDeviceLocalTimeZone() {
        val refuel = Refuel("boundary", Instant.parse("2026-01-31T18:00:00Z").toEpochMilli(), 1000.0, 10.0, 2000, true)
        val malaysia = fuelSpendingMonths(listOf(refuel), LocalDate.of(2026, 2, 1), 3, ZoneId.of("Asia/Kuala_Lumpur"))
        val utc = fuelSpendingMonths(listOf(refuel), LocalDate.of(2026, 2, 1), 3, ZoneOffset.UTC)
        assertEquals(2000L, malaysia.last().second); assertEquals(0L, utc.last().second); assertEquals(2000L, utc[1].second)
    }
    @Test fun categoryOrderingIsDeterministicAndOthersAlwaysLastIncludingCustomCategories() {
        val custom = listOf(Category("z", "Zebra", "other", categoryColors.first()), Category("a", "Alpha", "other", categoryColors.first()))
        val expected = listOf("food", "groceries", "entertainment", "shopping", FUEL_CATEGORY, "bills", "a", "z", OTHER_CATEGORY)
        for (seed in 1..10) assertEquals(expected, displayCategories((defaultCategories + custom).shuffled(kotlin.random.Random(seed))).map { it.id })
    }
    @Test fun allIncomeSourceTypesAreDistinctForecastsAndInactiveOrUnestimatedSourcesDoNotAggregate() {
        val rules = incomeSourceTypes.mapIndexed { index, type -> FinanceRecurring("income-$index", type, (index + 1) * 1000L,
            "income:$type", "Monthly", "2026-01-31", "2026-01-31", kind = "Income", manualPayment = true) }
        rules.forEach(::validateFinanceRecurring)
        val optional = rules.first().copy(id = "optional", cents = 0)
        validateFinanceRecurring(optional)
        val occurrences = plannedOccurrences(rules + optional + rules.first().copy(id = "inactive", enabled = false), YearMonth.of(2026, 2))
        assertEquals(21000L, occurrences.sumOf { it.cents }); assertEquals(6, occurrences.size)
        assertTrue(occurrences.all { it.date == "2026-02-28" })
        assertEquals(incomeSourceTypes, rules.map(::incomeSourceType))
        assertEquals("Other Income", incomeSourceType(rules.first().copy(categoryId = "bills")))
        assertTrue(runCatching { validateFinanceRecurring(optional.copy(cents = -1)) }.isFailure)
        assertTrue(runCatching { validateFinanceRecurring(optional.copy(kind = "Expense", categoryId = "bills")) }.isFailure)
    }
    @Test fun sliderExchangesOnlyWithRemainderAndAlwaysTotalsOneHundredPercent() {
        val seed = defaultAllocations.map { it.copy(remainder = it.id == "general") }
        for (request in listOf(-500, 0, 1000, 2000, 3333, 8000, 10000, Int.MAX_VALUE)) {
            val result = moveAllocation(seed, "food", request)
            assertEquals(10000, result.sumOf { it.basisPoints }); assertTrue(result.all { it.basisPoints in 0..10000 })
            assertEquals(seed.first { it.id == "savings" }, result.first { it.id == "savings" })
            assertEquals(seed.first { it.id == "lifestyle" }, result.first { it.id == "lifestyle" })
            allocationPlan("default", result)
        }
        assertEquals(seed, moveAllocation(seed, "general", 0))
    }
    @Test fun allocationCustomAddRemovePreservesPreferencesAndExactTotal() {
        val seed = defaultAllocations.map { it.copy(remainder = it.id == "general") } + AllocationCategory("custom", "Travel", 0)
        val updated = moveAllocation(seed, "custom", 1234)
        assertEquals(1234, updated.last().basisPoints)
        assertEquals("general", flexibleAllocationId(updated))
        val removed = removeAllocation(updated, "custom")
        assertEquals(defaultAllocations.map { it.basisPoints }, removed.map { it.basisPoints })
        assertTrue(runCatching { removeAllocation(updated, "general") }.isFailure)
        val legacy = backupJson.decodeFromString<AllocationPlan>(backupJson.encodeToString(allocationPlan("default", defaultAllocations)))
        assertEquals(defaultAllocations, legacy.categories())
    }
    @Test fun newJourneyTotalsOnlyFuelTollsParkingWhileLegacyFieldsRemainIntact() {
        val legacy = JourneyPlan("KL", "Penang", 150.0, outboundTolls = listOf(JourneyFee("Toll", 1000)), parkingCents = 500,
            otherFees = listOf(JourneyFee("Legacy ferry", 700)), people = 4)
        assertEquals(4353L, journeyCost(legacy).totalCents)
        val refined = legacy.copy(costVersion = 2)
        assertEquals(3653L, journeyCost(refined).totalCents); assertEquals(0L, journeyCost(refined).otherCents)
        val saved = savedJourney("old", legacy)
        val roundTrip = backupJson.decodeFromString<SavedJourney>(backupJson.encodeToString(saved))
        assertEquals(legacy, roundTrip.plan()); assertEquals(4353L, journeyCost(roundTrip.plan()).totalCents)
        assertEquals(legacy.otherFees, refined.otherFees); assertEquals(legacy.people, refined.people)
    }
    @Test fun oldJourneyJsonAndOldSettingsDecodeWithoutChangingRecordedValues() {
        val old = """{"origin":"KL","destination":"Ipoh","outboundKm":100.0,"otherFees":[{"name":"Legacy","cents":500}],"people":3}"""
        val plan = backupJson.decodeFromString<JourneyPlan>(old)
        assertEquals(1, plan.costVersion); assertEquals(3, plan.people); assertEquals(500L, plan.otherFees.single().cents)
        assertEquals("Legacy", plan.consumptionSource)
        assertEquals(1935L, journeyCost(plan).totalCents)
        val settings = backupJson.decodeFromString<AppSettings>("""{"consumption":8.5,"theme":"Dark"}""")
        assertEquals(8.5, settings.consumption, 0.0); assertEquals("Dark", settings.theme)
        assertFalse(settings.consumptionFallbackEnabled)
    }
}
