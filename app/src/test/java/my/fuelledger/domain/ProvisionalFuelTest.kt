package my.fuelledger.domain

import my.fuelledger.data.Refuel
import org.junit.Assert.*
import org.junit.Test

class ProvisionalFuelTest {
    private fun p(id: String, time: Long, odo: Double, litres: Double, full: Boolean = false) = FuelPoint(id, time, odo, litres, 2050, full)
    @Test fun twoPartialRecordsUseLaterPurchaseAndDoNotRequireFullFlags() {
        val a = p("a", 1, 10000.0, 50.0)
        val b = p("b", 2, 10500.0, 40.0)
        val interval = fillEstimates(listOf(a, b)).single()
        assertEquals(500.0, interval.distance, 0.0)
        assertEquals(8.0, historicalConsumption(listOf(a, b))!!, 0.0)
        assertEquals(12.5, interval.kmPerLitre, 0.0)
        assertEquals(historicalConsumption(listOf(a, b)), historicalConsumption(listOf(a.copy(full = true), b.copy(full = true))))
        assertEquals(8.0, historicalConsumption(listOf(a.copy(litres = 1.0), b))!!, 0.0)
    }
    @Test fun weightedHistoricalEstimateIncludesArbitraryPartialsOnceAndHasNoUnweightedAverage() {
        val points = listOf(p("a", 1, 1000.0, 90.0), p("b", 2, 1100.0, 5.0), p("c", 3, 1500.0, 40.0))
        assertEquals(9.0, historicalConsumption(points.reversed())!!, .00001)
        assertNotEquals(7.5, historicalConsumption(points)!!, .001)
        assertEquals(45.0, fillEstimates(points).sumOf { it.litres }, 0.0)
        assertEquals(500.0, fillEstimates(points).sumOf { it.distance }, 0.0)
    }
    @Test fun invalidOrEqualReadingsTimesAndQuantitiesDoNotInventEstimates() {
        val a = p("a", 1, 1000.0, 20.0)
        val b = p("b", 2, 1200.0, 10.0)
        for (invalid in listOf(b.copy(odometer = 1000.0), b.copy(odometer = 900.0), b.copy(timestamp = 1),
            b.copy(litres = 0.0), b.copy(litres = -1.0), b.copy(litres = Double.NaN), b.copy(odometer = Double.POSITIVE_INFINITY))) {
            assertNull(historicalConsumption(listOf(a, invalid)))
        }
        assertNull(historicalConsumption(listOf(a)))
    }
    @Test fun historicalFuelMetadataIsUnchangedByConsumptionAndJourneyUsesTheSameEstimate() {
        val old = Refuel("old", 1, 1000.0, 30.0, 9000, true, "Shell", "old note", "RON97", "old location")
        val next = old.copy(id = "new", timestamp = 2, odometer = 1200.0, litres = 10.0, fullTank = false)
        assertEquals(5.0, historicalConsumption(listOf(old.point(), next.point()))!!, 0.0)
        val estimate = estimateTrip(150.0, historicalConsumption(listOf(old.point(), next.point()))!!, "2.05".toBigDecimal())
        assertEquals(1538, estimate.costCents)
        assertEquals("RON97", old.fuelType); assertTrue(old.fullTank); assertEquals("old note", old.notes)
    }
}
