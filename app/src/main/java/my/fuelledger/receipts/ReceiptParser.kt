package my.fuelledger.receipts

import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

/** OCR produces suggestions only. A person must review them before saving a refuel. */
data class ReceiptSuggestions(
    val litres: String? = null,
    val amount: String? = null,
    val fuelType: String? = null,
    val station: String? = null,
    val stationLocation: String? = null,
    val odometer: String? = null,
    val date: String? = null,
    val time: String? = null,
    val rawText: String = "",
    val suggestedCategoryId: String? = null
)

/** Conservative Malaysian receipt parsing, independent of Android and the OCR engine. */
fun parseReceipt(text: String): ReceiptSuggestions {
    val lines = text.lineSequence().map { it.replace('\u00a0', ' ').trim().replace(Regex("[\\t ]+"), " ") }
        .filter(String::isNotBlank).toList()
    val litres = mutableListOf<ReceiptCandidate>()
    val amounts = mutableListOf<ReceiptCandidate>()
    val odometers = mutableListOf<ReceiptCandidate>()
    val dates = mutableListOf<ReceiptCandidate>()
    val times = mutableListOf<ReceiptCandidate>()

    lines.forEachIndexed { index, line ->
        val next = lines.getOrNull(index + 1)
        // A unit price is never a volume, even when OCR drops the slash before L.
        if (!unitPrice.containsMatchIn(line)) {
            val volumeLabel = volumeLabel.find(line)
            if (volumeLabel != null) {
                val suffix = line.substring(volumeLabel.range.last + 1)
                firstNumber(suffix)?.let { decimal(it, 10_000)?.let { n -> litres += ReceiptCandidate(3, n) } }
                    ?: next?.takeIf { numericOnly.matches(it) }?.let(::firstNumber)
                        ?.let { decimal(it, 10_000)?.let { n -> litres += ReceiptCandidate(3, n) } }
            }
            // Requiring an actual litre unit avoids mistaking an item count for fuel volume.
            litreQuantity.findAll(line).forEach { match ->
                val currencyBefore = Regex("(?i)(?:RM|MYR)\\s*$").containsMatchIn(line.substring(0, match.range.first))
                if (!currencyBefore) decimal(match.groupValues[1], 10_000)?.let { litres += ReceiptCandidate(2, it) }
            }
            val quantityLabel = quantityLabel.find(line)
            if (quantityLabel != null && litreUnit.containsMatchIn(line)) {
                firstNumber(line.substring(quantityLabel.range.last + 1))?.let {
                    decimal(it, 10_000)?.let { n -> litres += ReceiptCandidate(3, n) }
                }
            }
        }

        // Common pump table: "Product Litres Unit Price Amount" followed by a fuel row.
        if (Regex("(?i)\\b(?:LITRES?|LITERS?|VOLUME)\\b").containsMatchIn(line) &&
            priceOrAmount.containsMatchIn(line) && next != null) {
            fuelPattern.find(next)?.let { fuel ->
                firstNumber(next.substring(fuel.range.last + 1))?.let {
                    decimal(it, 10_000)?.let { n -> litres += ReceiptCandidate(1, n) }
                }
            }
        }

        val amountLine = line.replace(Regex("(?i)\\b(?:INCL(?:USIVE)?\\.?|INCLUDING)\\s+(?:OF\\s+)?(?:SST|GST|TAX)\\b"), "")
        if (!excludedAmount.containsMatchIn(amountLine) && !unitPrice.containsMatchIn(amountLine)) {
            val total = totalLabel.find(amountLine)
            if (total != null) {
                val priority = if (Regex("(?i)grand\\s+total|jumlah\\s+(?:besar|keseluruhan)").containsMatchIn(total.value)) 4 else 3
                val suffix = amountLine.substring(total.range.last + 1)
                moneyIn(suffix)?.let { amounts += ReceiptCandidate(priority, it) }
                    ?: next?.takeIf { moneyOnly.matches(it) }?.let(::moneyIn)
                        ?.let { amounts += ReceiptCandidate(priority, it) }
            } else {
                val amount = amountLabel.find(amountLine)
                if (amount != null) {
                    moneyIn(amountLine.substring(amount.range.last + 1))?.let { amounts += ReceiptCandidate(2, it) }
                        ?: next?.takeIf { moneyOnly.matches(it) }?.let(::moneyIn)
                            ?.let { amounts += ReceiptCandidate(2, it) }
                }
            }
        }

        odometerLabel.find(line)?.let { label ->
            firstNumber(line.substring(label.range.last + 1))?.let {
                decimal(it, 10_000_000)?.let { n -> odometers += ReceiptCandidate(1, n) }
            }
        }

        if (!nonTransactionDate.containsMatchIn(line)) {
            val priority = if (dateLabel.containsMatchIn(line)) 2 else 1
            isoDate.findAll(line).forEach { match ->
                validDate(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                    ?.let { dates += ReceiptCandidate(priority, it) }
            }
            localDate.findAll(line).forEach { match ->
                val year = match.groupValues[3].let { if (it.length == 2) "20$it" else it }
                validDate(year, match.groupValues[2], match.groupValues[1])
                    ?.let { dates += ReceiptCandidate(priority, it) }
            }
            namedDate.findAll(line).forEach { match ->
                val month = monthNames[match.groupValues[2].take(3).lowercase(Locale.ROOT)]
                if (month != null) {
                    validDate(match.groupValues[3], month.toString(), match.groupValues[1])
                        ?.let { dates += ReceiptCandidate(priority, it) }
                }
            }
            receiptTime.findAll(line).forEach { match ->
                var hour = match.groupValues[1].toInt()
                val minute = match.groupValues[2].toInt()
                val meridiem = match.groupValues[3].uppercase(Locale.ROOT)
                if (meridiem.isNotEmpty()) {
                    if (hour !in 1..12) return@forEach
                    hour = hour % 12 + if (meridiem == "PM") 12 else 0
                }
                if (hour in 0..23) times += ReceiptCandidate(
                    if (timeLabel.containsMatchIn(line)) 2 else 1,
                    String.format(Locale.ROOT, "%02d:%02d", hour, minute)
                )
            }
        }
    }

    val fuels = lines.flatMap { line -> fuelPattern.findAll(line).map { normalizeFuel(it.value) }.toList() }.distinct()
    val stations = lines.take(10).flatMap { line -> stationPattern.findAll(line).map { normalizeStation(it.value) }.toList() }.distinct()
    return ReceiptSuggestions(
        litres = choose(litres), amount = choose(amounts), fuelType = fuels.singleOrNull(),
        station = stations.singleOrNull() ?: genericMerchant(lines), stationLocation = receiptLocation(lines),
        odometer = choose(odometers), date = choose(dates), time = choose(times), rawText = text,
        suggestedCategoryId = suggestCategory(lines, fuels.isNotEmpty() || stations.isNotEmpty())
    )
}

private data class ReceiptCandidate(val priority: Int, val value: String)
private fun choose(values: List<ReceiptCandidate>): String? {
    val priority = values.maxOfOrNull { it.priority } ?: return null
    return values.filter { it.priority == priority }.map { it.value }.distinct().singleOrNull()
}

private const val NUMBER = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?"
private val number = Regex("(?i)(?:(?<=RM)|(?<=MYR)|(?<![\\w.]))-?$NUMBER(?![\\w.])")
private val numericOnly = Regex("(?i)[:= -]*(?:$NUMBER)\\s*(?:L|LTR|LTRS|LITRES?|LITERS?)?")
private val moneyOnly = Regex("(?i)[:= -]*(?:(?:RM|MYR)\\s*)?$NUMBER\\s*(?:RM|MYR)?")
private val litreUnit = Regex("(?i)\\b(?:L|LTR|LTRS|LITRES?|LITERS?)\\b")
private val litreQuantity = Regex("(?i)(?<![\\w./-])(-?$NUMBER)\\s*(?:L|LTR|LTRS|LITRES?|LITERS?)\\b")
private val volumeLabel = Regex("(?i)\\b(?:VOLUME|VOL\\.?|LITRES?|LITERS?|ISI\\s*PADU)\\b")
private val quantityLabel = Regex("(?i)\\b(?:QUANTITY|QTY|KUANTITI)\\b")
private val unitPrice = Regex("(?i)\\b(?:UNIT\\s*PRICE|PRICE\\s*(?:PER|/|\\()?\\s*(?:L|LTR|LITRE|LITER)|HARGA\\s*(?:SELITER|SEUNIT|/L))\\b|(?:RM|MYR)\\s*/\\s*(?:L|LTR|LITRE|LITER)\\b")
private val priceOrAmount = Regex("(?i)\\b(?:PRICE|AMOUNT|HARGA|JUMLAH)\\b")
private val totalLabel = Regex("(?i)\\b(?:GRAND\\s+TOTAL|TOTAL(?:\\s+(?:AMOUNT|SALES?|DUE|PAYABLE))?|JUMLAH(?:\\s+(?:BESAR|KESELURUHAN|BAYARAN))?)\\b")
private val amountLabel = Regex("(?i)\\b(?:AMOUNT(?:\\s+(?:DUE|PAYABLE|PAID))?|BAYARAN)\\b")
private val excludedAmount = Regex("(?i)\\b(?:SUB\\s*TOTAL|SUBTOTAL|TAX|SST|GST|CHANGE|BALANCE|DISCOUNT|SAVINGS?|POINTS?|LOYALTY|REWARD|REFUND|UNIT\\s*PRICE|VOLUME|LITRES?|LITERS?|QUANTITY|QTY|KUANTITI)\\b|(?:PRICE|HARGA)\\s*/\\s*L")
private val odometerLabel = Regex("(?i)\\b(?:ODOMETER|ODO|MILEAGE|BACAAN\\s+(?:ODO|METER))\\b")
private val dateLabel = Regex("(?i)\\b(?:DATE|TARIKH)\\b")
private val timeLabel = Regex("(?i)\\b(?:TIME|MASA)\\b")
private val nonTransactionDate = Regex("(?i)\\b(?:EXPIRY|EXPIRES?|VALID\\s+(?:UNTIL|THRU|TO)|MEMBER\\s+SINCE|TARIKH\\s+LUPUT)\\b")
private val isoDate = Regex("(?<![0-9])([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})(?![0-9])")
private val localDate = Regex("(?<![0-9])([0-9]{1,2})[/.\\-]([0-9]{1,2})[/.\\-]([0-9]{4}|[0-9]{2})(?![0-9])")
private val namedDate = Regex("(?i)\\b([0-9]{1,2})[ \\-]([A-Z]{3,9})[ \\-]([0-9]{4})\\b")
private val monthNames = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    .mapIndexed { index, month -> month to index + 1 }.toMap()
private val receiptTime = Regex("(?i)(?<![0-9:])([0-9]{1,2}):([0-5][0-9])(?::[0-5][0-9])?\\s*(AM|PM)?\\b(?![0-9:])")
private val fuelPattern = Regex("(?i)\\b(?:RON\\s*[- ]?\\s*9[57]|PRIMAX\\s*9[57]|V[- ]?POWER\\s*(?:9[57])?|DIESEL(?:\\s*(?:EURO\\s*5|B(?:7|10|20)))?)\\b")
private val stationPattern = Regex("(?i)\\b(?:PETRONAS|SHELL|PETRON|BHP(?:ETROL)?|CALTEX)\\b")

private fun firstNumber(text: String): String? = number.find(text)?.value
private fun decimal(value: String, max: Int): String? {
    val parsed = value.replace(",", "").toBigDecimalOrNull() ?: return null
    if (parsed <= BigDecimal.ZERO || parsed > max.toBigDecimal()) return null
    return parsed.stripTrailingZeros().toPlainString()
}

private fun moneyIn(text: String): String? {
    if (Regex("(?i)-\\s*(?:RM|MYR)").containsMatchIn(text)) return null
    // More than one money value on a labelled line is ambiguous; do not pick the last number.
    val values = number.findAll(text).mapNotNull { match ->
        val token = match.value
        val hasCurrency = Regex("(?i)(?:RM|MYR)\\s*$").containsMatchIn(text.substring(0, match.range.first)) ||
            Regex("(?i)^\\s*(?:RM|MYR)\\b").containsMatchIn(text.substring(match.range.last + 1))
        if (!hasCurrency && !Regex(".*\\.[0-9]{2}$").matches(token)) null
        else decimal(token, 10_000_000)?.let { value ->
            runCatching { BigDecimal(value).setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString() }.getOrNull()
        }
    }.toList()
    return values.distinct().singleOrNull()
}

private fun validDate(year: String, month: String, day: String): String? = try {
    LocalDate.of(year.toInt(), month.toInt(), day.toInt()).takeIf { it.year in 1900..2199 }?.toString()
} catch (_: DateTimeException) { null } catch (_: NumberFormatException) { null }

private fun normalizeFuel(value: String): String = when {
    value.contains("DIESEL", ignoreCase = true) -> "Diesel"
    value.contains("95") -> "RON95"
    value.contains("97") -> "RON97"
    // V-Power Racing without an octane number does not establish RON97.
    else -> "V-Power"
}

private fun normalizeStation(value: String): String = when (value.uppercase(Locale.ROOT)) {
    "PETRONAS" -> "Petronas"
    "SHELL" -> "Shell"
    "PETRON" -> "Petron"
    "BHP", "BHPETROL" -> "BHPetrol"
    else -> "Caltex"
}

private fun receiptLocation(lines: List<String>): String? {
    val label = Regex("(?i)^(?:STATION\\s+ADDRESS|ADDRESS|ALAMAT|LOCATION|LOKASI)\\s*[:=-]\\s*(.*)$")
    lines.take(12).forEachIndexed { index, line ->
        label.find(line)?.let { match ->
            val value = match.groupValues[1].ifBlank { lines.getOrNull(index + 1).orEmpty() }
            return value.takeIf { it.isNotBlank() && !Regex("(?i)\\b(?:TOTAL|DATE|TIME|AMOUNT|VOLUME)\\b").containsMatchIn(it) }?.take(300)
        }
    }
    val address = Regex("(?i)\\b(?:JALAN|JLN\\.?|LORONG|PERSIARAN|LEBUH(?:RAYA)?|TAMAN|KAMPUNG)\\b")
    val postcode = Regex("\\b[0-9]{5}\\b")
    val start = lines.take(10).indexOfFirst { address.containsMatchIn(it) }
    if (start < 0) return null
    val parts = mutableListOf(lines[start])
    lines.getOrNull(start + 1)?.takeIf { postcode.containsMatchIn(it) && !Regex("(?i)\\b(?:TEL|PHONE|FAX|ODO|DATE|TIME)\\b").containsMatchIn(it) }?.let(parts::add)
    return parts.joinToString(", ").take(300)
}

private val categoryRules = listOf(
    "groceries" to Regex("(?i)\\b(?:MYDIN|TESCO|LOTUS(?:['’]S)?|AEON|GIANT|JAYA\\s+GROCER|VILLAGE\\s+GROCER|99\\s*SPEED\\s*MART|99\\s*SPEEDMART|ECONSAVE|NSK|GROCER(?:Y|IES)?|SUPERMARKET|PASAR\\s*RAYA|7[- ]?ELEVEN)\\b"),
    "food" to Regex("(?i)\\b(?:RESTORAN|RESTAURANT|CAFE|CAFÉ|COFFEE|BAKERY|WARUNG|MAMAK|STARBUCKS|MCDONALD['’]?S?|KFC|TEALIVE|SECRET\\s+RECIPE|PIZZA|BURGER|FOOD\\s+COURT|KOPI)\\b"),
    "bills" to Regex("(?i)\\b(?:TENAGA\\s+NASIONAL|TNB|AIR\\s+SELANGOR|UNIFI|MAXIS|CELCOM|DIGI|U\\s+MOBILE|ASTRO|ELECTRICITY|UTILIT(?:Y|IES)|INTERNET\\s+BILL|WATER\\s+BILL|BIL\\s+(?:AIR|ELEKTRIK))\\b"),
    "shopping" to Regex("(?i)\\b(?:MR\\.?\\s*D\\.?I\\.?Y|WATSONS|GUARDIAN|UNIQLO|PADINI|IKEA|DECATHLON|SHOPPING|FASHION|CLOTHING|ELECTRONICS|DEPARTMENT\\s+STORE)\\b")
)

private fun suggestCategory(lines: List<String>, fuel: Boolean): String? {
    if (fuel) return "fuel-transport"
    // Merchant headers take precedence over incidental products or advertisements farther down.
    val meaningful = lines.filterNot { receiptFooter.containsMatchIn(it) }
    val headers = meaningful.take(5).joinToString("\n")
    val headerMatches = categoryRules.filter { it.second.containsMatchIn(headers) }.map { it.first }
    if (headerMatches.size == 1) return headerMatches.single()
    if (headerMatches.size > 1) return null
    return categoryRules.filter { it.second.containsMatchIn(meaningful.joinToString("\n")) }.map { it.first }.singleOrNull()
}

private fun genericMerchant(lines: List<String>): String? {
    val explicit = Regex("(?i)^(?:MERCHANT|KEDAI|PENIAGA)\\s*[:=-]\\s*(.+)$")
    lines.take(8).forEach { line -> explicit.find(line)?.let { return it.groupValues[1].take(150) } }
    return lines.take(5).firstOrNull { line -> !receiptFooter.containsMatchIn(line) && categoryRules.any { it.second.containsMatchIn(line) } }?.take(150)
}

private val receiptFooter = Regex("(?i)\\b(?:THANK(?:S|\\s+YOU)|TERIMA\\s+KASIH|COME\\s+AGAIN|PLEASE\\s+(?:COME|VISIT))\\b")
