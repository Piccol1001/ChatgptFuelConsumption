package my.fuelledger.domain

import my.fuelledger.data.*
import org.junit.Assert.*
import org.junit.Test

class VehicleJourneyUpgradeTest {
    private fun fills() = listOf(
        Refuel("0", 1000, 1000.0, 40.0, 8000, true),
        Refuel("p", 2000, 1200.0, 15.0, 3000, false),
        Refuel("1", 3000, 1500.0, 20.0, 4000, true),
        Refuel("2", 4000, 2000.0, 35.0, 7000, true),
        Refuel("3", 5000, 2500.0, 40.0, 8000, true),
        Refuel("4", 6000, 3000.0, 40.0, 8000, true))
    @Test fun nonOverlappingFuelChangesAndPriceWeighting() {
        val points = fills().map { it.point() }
        assertNull(consumptionChange(points.take(4)))
        val change = consumptionChange(points)!!
        assertEquals(7.0, change.historical, .001); assertEquals(8.0, change.recent, .001)
        assertEquals(14.285714, change.percent, .00001)
        assertEquals(2.0, averageFuelPrice(fills())!!, .001)
        assertEquals(7.5, historicalConsumption(points)!!, .001)
    }
    @Test fun operatingCoverageCountsLinkedFuelOnceAndExcludesBaselineAndUncoveredCosts() {
        val fuels = fills()
        val linked = fuels.map { Expense("expense-${it.id}", it.cents, FUEL_CATEGORY, it.timestamp, fuelId = it.id) }
        val extras = listOf(Expense("toll", 500, FUEL_CATEGORY, 3500), Expense("old", 9000, FUEL_CATEGORY, 500), Expense("food", 1000, "food", 3500))
        val c = vehicleCoverage(fuels, linked + extras, defaultCategories)!!
        assertEquals(30000, c.fuelCents); assertEquals(30500, c.operatingCents); assertEquals(2000.0, c.distance, .001)
    }
    @Test fun reversingRoundTripSwapsDirectionalDistancesAndTollsWithoutChangingTotal() {
        val p = JourneyPlan("A", "B", 120.0, true, 130.0, outboundTolls = listOf(JourneyFee("Out", 500)), returnTolls = listOf(JourneyFee("Back", 900)), costVersion = 2)
        val r = reverseJourney(p)
        assertEquals("B", r.origin); assertEquals(130.0, r.outboundKm, .001); assertEquals(900L, r.outboundTolls.single().cents)
        assertEquals(journeyCost(p).totalCents, journeyCost(r).totalCents)
        assertEquals(p, reverseJourney(r))
    }
    @Test fun missingActualsStayUnavailableAndLinkedRefundsReduceActuals() {
        val p = JourneyPlan("A", "B", 100.0, actualLinks = listOf(JourneyActualLink("fuel", "Fuel")))
        assertNull(journeyActual(p, emptyList()).fuel)
        val expenses = listOf(Expense("fuel", 3000, FUEL_CATEGORY, 1000), Expense("refund", -500, FUEL_CATEGORY, 2000, kind = "Refund", refundOf = "fuel"))
        val actual = journeyActual(p, expenses)
        assertEquals(2500L, actual.fuel); assertNull(actual.tolls); assertNull(actual.parking)
        assertTrue(journeyCost(p.copy(petrolPrice = "3.00")).fuelCents > journeyCost(p).fuelCents)
    }
}
