package my.fuelledger.receipts

import org.junit.Assert.*
import org.junit.Test

class ReceiptParserTest {
    @Test fun petronasReceiptUsesActualVolumeAndTotalWithoutInferringOdometer() {
        val text = """
            PETRONAS DAGANGAN BERHAD
            Jalan Pudu
            55100 Kuala Lumpur
            DATE: 14/10/2026 TIME: 18:42:19
            PRIMAX 95
            VOLUME (L): 24.390
            UNIT PRICE RM/L: 2.05
            SUBTOTAL RM 50.00
            TAX RM 0.00
            TOTAL RM50.00
            CASH RM100.00
            CHANGE RM50.00
            POINTS 12345
        """.trimIndent()
        val result = parseReceipt(text)
        assertEquals("24.39", result.litres)
        assertEquals("50.00", result.amount)
        assertEquals("RON95", result.fuelType)
        assertEquals("Petronas", result.station)
        assertEquals("Jalan Pudu, 55100 Kuala Lumpur", result.stationLocation)
        assertEquals("2026-10-14", result.date)
        assertEquals("18:42", result.time)
        assertNull(result.odometer)
        assertEquals(text, result.rawText)
    }

    @Test fun shellReceiptReadsLitreUnitAndExplicitOdometer() {
        val result = parseReceipt("""
            SHELL
            Address: Jalan Ampang, Kuala Lumpur
            15-10-26 7:09 PM
            RON 97
            15.250 L @ RM 3.19
            AMOUNT PAID: MYR48.65
            ODO: 56,123.5 km
        """.trimIndent())
        assertEquals("15.25", result.litres)
        assertEquals("48.65", result.amount)
        assertEquals("56123.5", result.odometer)
        assertEquals("RON97", result.fuelType)
        assertEquals("Shell", result.station)
        assertEquals("Jalan Ampang, Kuala Lumpur", result.stationLocation)
        assertEquals("2026-10-15", result.date)
        assertEquals("19:09", result.time)
    }

    @Test fun dieselAndMalayLabelsAreSupported() {
        val result = parseReceipt("""
            BHPetrol
            TARIKH 2026-10-15 MASA 00:05
            DIESEL EURO5
            KUANTITI (LTR) 32.500
            HARGA SELITER 3.00
            JUMLAH BAYARAN RM 97.50
        """.trimIndent())
        assertEquals("BHPetrol", result.station)
        assertEquals("Diesel", result.fuelType)
        assertEquals("32.5", result.litres)
        assertEquals("97.50", result.amount)
        assertEquals("2026-10-15", result.date)
        assertEquals("00:05", result.time)
    }

    @Test fun fuelTableUsesLitresColumnAfterTheFuelName() {
        val result = parseReceipt("""
            CALTEX
            Product Litres Unit Price Amount
            RON95 20.000 2.05 41.00
            TOTAL 41.00
        """.trimIndent())
        assertEquals("20", result.litres)
        assertEquals("41.00", result.amount)
        assertEquals("Caltex", result.station)
    }

    @Test fun splitLabelsCanUseTheFollowingNumericLine() {
        val result = parseReceipt("VOLUME (L)\n18.250\nTOTAL MYR\n37.41")
        assertEquals("18.25", result.litres)
        assertEquals("37.41", result.amount)
    }

    @Test fun differentEquallyStrongTotalsAndVolumesAreLeftForManualEntry() {
        val result = parseReceipt("TOTAL RM 20.00\nTOTAL RM 30.00\nVOLUME 10.0\nVOLUME 15.0")
        assertNull(result.amount)
        assertNull(result.litres)
    }

    @Test fun duplicateSameTotalAndVolumeAreNotAmbiguous() {
        val result = parseReceipt("TOTAL RM 20.00\nTOTAL MYR20\nVOLUME 10.00\nVOLUME 10.0")
        assertEquals("20.00", result.amount)
        assertEquals("10", result.litres)
    }

    @Test fun subtotalTaxAndRewardsNeverBecomeThePaidAmount() {
        val result = parseReceipt("SUB TOTAL 39.00\nTOTAL TAX 1.00\nTOTAL SAVINGS 4.00\nPOINTS 500.00\nTOTAL AMOUNT RM40.00")
        assertEquals("40.00", result.amount)
        assertNull(parseReceipt("SUBTOTAL 39.00\nTAX 1.00\nCASH 50.00\nCHANGE 10.00").amount)
    }

    @Test fun taxInclusiveTotalRemainsUsable() {
        assertEquals("41.00", parseReceipt("TOTAL (INCL SST) RM 41.00").amount)
    }

    @Test fun unitPriceAndGenericQuantityCannotBecomeLitres() {
        listOf("UNIT PRICE 2.05 L", "PRICE/LITRE 2.05", "RM/L 2.05", "QTY 1").forEach {
            assertNull(it, parseReceipt(it).litres)
        }
    }

    @Test fun noAmountOrOdometerIsInventedFromNumbersAlone() {
        val result = parseReceipt("Receipt 981231\nPump 4\nLoyalty 123456789\n56,123 km\nRM 50.00\n2.05\n12.34")
        assertNull(result.amount)
        assertNull(result.odometer)
        assertNull(result.litres)
        assertNull(result.time)
    }

    @Test fun invalidDatesTimesAndMoneyAreIgnoredWithoutThrowing() {
        val result = parseReceipt("DATE: 31/02/2026\nTIME: 29:10\nTOTAL RM12.345")
        assertNull(result.date)
        assertNull(result.time)
        assertNull(result.amount)
        assertNull(parseReceipt("TIME: 12:30:99").time)
    }

    @Test fun expiryDatesDoNotOverrideTransactionDate() {
        val result = parseReceipt("DATE 15 OCT 2026\nTIME 12:05 AM\nVALID UNTIL 20/11/2026\nEXPIRY 25/12/2027")
        assertEquals("2026-10-15", result.date)
        assertEquals("00:05", result.time)
    }

    @Test fun conflictingFuelTypesAndDatesRemainUnfilled() {
        val result = parseReceipt("RON95\nRON97\n15/10/2026\n16/10/2026")
        assertNull(result.fuelType)
        assertNull(result.date)
    }

    @Test fun currencyThousandsAndWhitespaceAreNormalized() {
        val result = parseReceipt("PETRON\nTOTAL\u00a0MYR1,234.50\nLITRES : 500.000")
        assertEquals("1234.50", result.amount)
        assertEquals("500", result.litres)
        assertEquals("Petron", result.station)
    }

    @Test fun aLabelledLineWithTwoMoneyValuesIsAmbiguous() {
        assertNull(parseReceipt("TOTAL RM 50.00 RM 60.00").amount)
    }

    @Test fun totalVolumeAndNegativeValuesDoNotBecomePaidAmounts() {
        val volume = parseReceipt("TOTAL LITRES 20.00")
        assertEquals("20", volume.litres)
        assertNull(volume.amount)
        listOf("TOTAL RM -20.00", "TOTAL -RM20.00", "REFUND TOTAL RM20.00").forEach {
            assertNull(it, parseReceipt(it).amount)
        }
        assertNull(parseReceipt("VOLUME -10.00 L").litres)
        assertNull(parseReceipt("ODO -100").odometer)
    }

    @Test fun emptyAndUnrelatedTextHaveNoSuggestions() {
        listOf("", "Thanks for shopping!", "a receipt with no readable fields").forEach { text ->
            assertEquals(ReceiptSuggestions(rawText = text), parseReceipt(text))
        }
    }
}
