package my.fuelledger.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.*

class CalculationsTest {
    @Test fun tripMatchesSpecifiedExampleWithHalfUpMoneyRounding() {
        val result = estimateTrip(150.0, 7.0, BigDecimal("2.05"))
        assertEquals(10.5, result.litres, 1e-9)
        assertEquals(2153L, result.costCents)
        assertEquals(.1435, result.costPerKm, 1e-9)
        assertEquals(14.285714, result.kmPerLitre, 1e-5)
    }
    @Test fun decimalMoneyIsExact() {
        assertEquals(10L, Money.parse("0.10"))
        assertEquals(30L, Money.parse("0.10") + Money.parse("0.20"))
        assertEquals("0.30", Money.decimal(30))
        assertEquals("RM 21.53", Money.format(2153))
    }
    @Test fun rejectNegativeZeroFractionalSenAndNonfiniteMoney() {
        listOf("-1", "0", "0.00", "1.005", "NaN", "Infinity", "1e5", "", "1,000", "1000000001").forEach {
            assertThrows(IllegalArgumentException::class.java) { Money.parse(it) }
        }
    }
    @Test fun invalidPhysicalInputsAreRejected() {
        listOf("-1", "0", "NaN", "Infinity", "abc", "", "1e300").forEach {
            assertThrows(IllegalArgumentException::class.java) { measurement(it, "Distance") }
        }
        assertEquals(0.0, measurement("0", "Odometer", true), 0.0)
    }
    @Test fun estimatorNeverDividesByZeroOrAcceptsInvalidPrice() {
        assertThrows(IllegalArgumentException::class.java) { estimateTrip(0.0, 7.0, BigDecimal.ONE) }
        assertThrows(IllegalArgumentException::class.java) { estimateTrip(10.0, 0.0, BigDecimal.ONE) }
        assertThrows(IllegalArgumentException::class.java) { estimateTrip(10.0, Double.NaN, BigDecimal.ONE) }
        assertThrows(IllegalArgumentException::class.java) { estimateTrip(10.0, 7.0, BigDecimal.ZERO) }
        assertThrows(IllegalArgumentException::class.java) { estimateTrip(10.0, 7.0, BigDecimal("-2")) }
    }
    private fun fuel(id: String, time: Long, odo: Double, litres: Double = 40.0, cents: Long = 8200, full: Boolean = true) =
        FuelPoint(id, time, odo, litres, cents, full)
    @Test fun emptyOneFullAndOnlyPartialHaveNoMeasuredEfficiency() {
        assertTrue(efficiencies(emptyList()).isEmpty())
        assertTrue(efficiencies(listOf(fuel("a", 1, 1000.0))).isEmpty())
        assertTrue(efficiencies(listOf(fuel("a", 1, 1000.0, full = false), fuel("b", 2, 1200.0, full = false))).isEmpty())
    }
    @Test fun partialFillsAreIncludedAndBaselineFuelExcluded() {
        val points = listOf(fuel("before", 0, 800.0, 20.0, 4100, false),
            fuel("a", 1, 1000.0, 50.0, 10250), fuel("partial", 2, 1300.0, 20.0, 4100, false), fuel("b", 3, 1600.0, 30.0, 6150))
        val cycle = efficiencies(points).single()
        assertEquals(600.0, cycle.distance, 0.0)
        assertEquals(50.0, cycle.litres, 0.0)
        assertEquals(10250L, cycle.cents)
        assertEquals(12.0, cycle.kmPerLitre, 0.0)
        assertEquals(8.333333, cycle.litresPer100Km, 1e-5)
        assertEquals(.17083333, cycle.costPerKm, 1e-7)
        assertEquals("a", cycle.startId)
    }
    @Test fun multipleCyclesDoNotReuseLitresAndUnfinishedCycleIsIgnored() {
        val points = listOf(fuel("a", 1, 1000.0), fuel("b", 2, 1500.0), fuel("c", 3, 1900.0, 20.0), fuel("p", 4, 2000.0, full = false))
        val result = efficiencies(points.reversed())
        assertEquals(2, result.size)
        assertEquals(12.5, result[0].kmPerLitre, 0.0)
        assertEquals(20.0, result[1].kmPerLitre, 0.0)
    }
    @Test fun editingAndDeletingPartialFillsRecalculatesTheCycle() {
        val a = fuel("a", 1, 1000.0)
        val p = fuel("p", 2, 1200.0, 20.0, 4100, false)
        val b = fuel("b", 3, 1600.0, 30.0, 6150)
        assertEquals(12.0, efficiencies(listOf(a, p, b)).single().kmPerLitre, 0.0)
        assertEquals(15.0, efficiencies(listOf(a, p.copy(litres = 10.0), b)).single().kmPerLitre, 0.0)
        assertEquals(20.0, efficiencies(listOf(a, b)).single().kmPerLitre, 0.0)
        assertTrue(efficiencies(listOf(p, b)).isEmpty())
    }
    @Test fun invalidOdometerSequenceCannotGenerateValidConsumption() {
        val points = listOf(fuel("a", 1, 1000.0), fuel("b", 2, 900.0))
        assertThrows(IllegalArgumentException::class.java) { validateSequence(points) }
        assertTrue(efficiencies(points).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { validateSequence(listOf(points[0], points[0].copy(id = "b"))) }
    }
    @Test fun everyPartialFillUsesCurrentPurchaseWithConsecutiveOdometerDifference() {
        val points = listOf(fuel("a", 1, 1000.0, 90.0, 19000, false),
            fuel("b", 2, 1200.0, 20.0, 4100, false), fuel("c", 3, 1500.0, 25.0, 5500, false))
        val result = fillEstimates(points.reversed())
        assertEquals(2, result.size)
        assertEquals("a", result[0].startId)
        assertEquals("b", result[0].endId)
        assertEquals(200.0, result[0].distance, 0.0)
        assertEquals(20.0, result[0].litres, 0.0)
        assertEquals(4100L, result[0].cents)
        assertEquals(10.0, result[0].kmPerLitre, 0.0)
        assertEquals(10.0, result[0].litresPer100Km, 0.0)
        assertEquals(0.205, result[0].costPerKm, 1e-9)
        assertEquals(12.0, result[1].kmPerLitre, 0.0)
        assertEquals(25.0, result[1].litres, 0.0)
        assertTrue(efficiencies(points).isEmpty())
        assertEquals(result, fillEstimates(points.map { it.copy(full = true) }))
    }
    @Test fun firstFillAndInvalidIntervalsHaveNoFillEstimate() {
        val a = fuel("a", 1, 1000.0, full = false)
        val b = fuel("b", 2, 1200.0, full = false)
        assertTrue(fillEstimates(emptyList()).isEmpty())
        assertTrue(fillEstimates(listOf(a)).isEmpty())
        listOf(b.copy(timestamp = 1), b.copy(odometer = 1000.0), b.copy(odometer = 900.0),
            b.copy(litres = 0.0), b.copy(litres = Double.NaN), b.copy(cents = 0),
            b.copy(odometer = Double.POSITIVE_INFINITY)).forEach { invalid ->
            assertTrue(fillEstimates(listOf(a, invalid)).isEmpty())
        }
        val bad = b.copy(litres = -1.0)
        assertTrue(fillEstimates(listOf(a, bad, fuel("c", 3, 1400.0))).isEmpty())
    }
    @Test fun editingAndDeletingFillsRecalculatesConsecutiveEstimates() {
        val a = fuel("a", 1, 1000.0, full = false)
        val b = fuel("b", 2, 1200.0, 20.0, 4100, false)
        val c = fuel("c", 3, 1600.0, 40.0, 8200, false)
        val edited = fillEstimates(listOf(a, b.copy(odometer = 1300.0, litres = 10.0), c))
        assertEquals(30.0, edited[0].kmPerLitre, 0.0)
        assertEquals(7.5, edited[1].kmPerLitre, 0.0)
        val deleted = fillEstimates(listOf(a, c)).single()
        assertEquals("a", deleted.startId)
        assertEquals("c", deleted.endId)
        assertEquals(600.0, deleted.distance, 0.0)
        assertEquals(40.0, deleted.litres, 0.0)
        assertEquals(15.0, deleted.kmPerLitre, 0.0)
        assertTrue(fillEstimates(listOf(c)).isEmpty())
    }
    private val zone = ZoneId.of("Asia/Kuala_Lumpur")
    private fun point(date: String, cents: Long, category: String = "food") = SpendingPoint(
        LocalDateTime.parse(date).atZone(zone).toInstant().toEpochMilli(), cents, category)
    @Test fun dailyMonthlyCategoryAndYearTotalsCrossLocalBoundaries() {
        val stats = Spending(listOf(point("2025-12-31T23:59:00", 100), point("2026-01-01T00:01:00", 25000),
            point("2026-01-15T12:00:00", 18000, "groceries"), point("2026-01-31T23:59:00", 7000, "entertainment"),
            point("2026-02-01T00:00:00", 500)), zone)
        val jan = YearMonth.of(2026, 1)
        assertEquals(50000L, stats.month(jan))
        assertEquals(25000L, stats.today(jan.atDay(1)))
        assertEquals(50500L, stats.year(jan.atDay(1)))
        assertEquals(mapOf("food" to 25000L, "groceries" to 18000L, "entertainment" to 7000L), stats.categories(jan.atDay(1), jan.atEndOfMonth()))
        assertEquals(500.0 / 31, stats.average(jan.atDay(1), jan.atEndOfMonth()), 1e-8)
    }
    @Test fun weekUsesMondayAcrossYearBoundary() {
        val stats = Spending(listOf(point("2025-12-28T12:00:00", 100), point("2025-12-29T12:00:00", 200), point("2026-01-04T12:00:00", 300), point("2026-01-05T00:00:00", 400)), zone)
        assertEquals(500L, stats.week(LocalDate.of(2026, 1, 1)))
    }
    @Test fun noDataIsZeroAndReversedRangesFail() {
        val stats = Spending(emptyList(), zone)
        val day = LocalDate.of(2026, 1, 1)
        assertEquals(0L, stats.today(day))
        assertEquals(0.0, stats.average(day, day), 0.0)
        assertTrue(stats.categories(day, day).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { stats.total(day.plusDays(1), day) }
    }
    @Test fun daylightSavingsUsesCalendarDaysRatherThan24HourArithmetic() {
        val ny = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 3, 8)
        val stats = Spending(listOf(SpendingPoint(day.atTime(23, 30).atZone(ny).toInstant().toEpochMilli(), 2300, "food")), ny)
        assertEquals(2300L, stats.today(day))
        assertEquals(23.0, stats.average(day, day), 0.0)
    }
}
