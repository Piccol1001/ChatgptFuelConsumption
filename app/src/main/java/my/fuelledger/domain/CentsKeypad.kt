package my.fuelledger.domain

/** Append a digit directly to integer sen, rejecting values outside the existing money limit. */
fun appendSenDigit(cents: Long, digit: Int): Long? {
    require(cents in 0..MAX_CENTS && digit in 0..9)
    if (cents > (MAX_CENTS - digit) / 10) return null
    return cents * 10 + digit
}

fun backspaceSen(cents: Long): Long {
    require(cents in 0..MAX_CENTS)
    return cents / 10
}
