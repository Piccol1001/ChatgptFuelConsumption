package my.fuelledger.domain

import org.junit.Assert.*
import org.junit.Test

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
