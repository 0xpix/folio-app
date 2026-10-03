package com.pix.folio.data

import com.pix.folio.model.ExpenseCategory
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

data class SparkasseTransactionSnapshot(
    val bookingDate: LocalDate,
    val amount: Double,
    val currency: String,
    val merchant: String,
    val purpose: String,
) {
    val isExpense: Boolean get() = amount < 0.0
    val expenseAmount: Double get() = -amount

    val category: ExpenseCategory
        get() = SparkasseTransactionCategorizer.categoryFor(merchant, purpose)
}

data class SparkasseSnapshot(
    val createdAtUtc: Instant,
    val balance: Double,
    val availableBalance: Double,
    val currency: String,
    val transactions: List<SparkasseTransactionSnapshot>,
) {
    fun expensesForMonth(month: java.time.YearMonth): List<SparkasseTransactionSnapshot> =
        transactions.filter { it.isExpense && java.time.YearMonth.from(it.bookingDate) == month }
}

object SparkasseTransactionCategorizer {
    fun categoryFor(merchant: String, purpose: String): ExpenseCategory {
        val text = (merchant + " " + purpose).uppercase()

        return when {
            hasAny(
                text,
                "NETFLIX", "SPOTIFY", "DISNEY", "AMAZON PRIME", "PRIME VIDEO",
                "YOUTUBE PREMIUM", "APPLE.COM/BILL", "GOOGLE ONE", "MICROSOFT 365",
                "ADOBE", "OPENAI", "CHATGPT", "GITHUB", "DROPBOX", "ICLOUD",
            ) -> ExpenseCategory.SUBSCRIPTIONS

            hasAny(
                text,
                "REWE", "EDEKA", "ALDI", "LIDL", "KAUFLAND", "PENNY", "NETTO",
                "NORMA", "GLOBUS", "HIT MARKT", "NAHKAUF", "BIO COMPANY",
            ) -> ExpenseCategory.GROCERIES

            hasAny(
                text,
                "RESTAURANT", "CAFE", "CAFÉ", "BÄCK", "BAECK", "MCDONALD",
                "BURGER KING", "KFC", "SUBWAY", "LIEFERANDO", "WOLT", "UBER EATS",
                "PIZZA", "DÖNER", "DOENER",
            ) -> ExpenseCategory.DINING

            hasAny(
                text,
                "MIETE", "RENT", "HAUSVERWALTUNG", "WOHNUNG", "NEBENKOSTEN",
            ) -> ExpenseCategory.HOME

            hasAny(
                text,
                "STADTWERKE", "ENBW", "E.ON", "VATTENFALL", "TELEKOM", "VODAFONE",
                "O2 ", "1&1", "INTERNET", "STROM", "GAS", "WASSER",
            ) -> ExpenseCategory.UTILITIES

            hasAny(
                text,
                "DEUTSCHE BAHN", "DB VERTRIEB", "RNV", "VRN", "DEUTSCHLANDTICKET",
                "D-TICKET", "FLIXBUS", "UBER", "BOLT", "ARAL", "SHELL", "ESSO",
                "TOTALENERGIES", "TANKSTELLE",
            ) -> ExpenseCategory.TRANSPORT

            hasAny(
                text,
                "APOTHEKE", "PHARMACY", "ARZT", "PRAXIS", "ZAHNARZT", "FITNESS",
                "GYM", "MCFIT", "FITX",
            ) -> ExpenseCategory.HEALTH

            hasAny(
                text,
                "STEAM", "PLAYSTATION", "XBOX", "NINTENDO", "KINO", "CINEMA",
                "EVENTIM", "TICKETMASTER",
            ) -> ExpenseCategory.ENTERTAINMENT

            hasAny(
                text,
                "BOOKING.COM", "AIRBNB", "HOTEL", "RYANAIR", "EUROWINGS",
                "LUFTHANSA", "EASYJET", "HOSTEL",
            ) -> ExpenseCategory.TRAVEL

            hasAny(
                text,
                "KONTOFÜHR", "KONTOFUEHR", "BANKGEBÜHR", "BANKGEBUEHR",
                "KARTENGEBÜHR", "KARTENGEBUEHR", "ENTGELT",
            ) -> ExpenseCategory.FEES

            hasAny(
                text,
                "AMAZON", "ZALANDO", "H&M", "IKEA", "DM ", "ROSSMANN",
                "MEDIA MARKT", "SATURN", "DECATHLON",
            ) -> ExpenseCategory.SHOPPING

            else -> ExpenseCategory.OTHER
        }
    }

    private fun hasAny(text: String, vararg needles: String): Boolean =
        needles.any(text::contains)
}

object SparkasseSnapshotCodec {
    const val FORMAT = "folio-sparkasse-snapshot"
    const val VERSION = 1

    fun parse(raw: String): SparkasseSnapshot {
        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) { "Not a Folio Sparkasse snapshot" }
        require(root.optInt("version", 0) == VERSION) { "Unsupported Sparkasse snapshot version" }
        require(root.optString("source") == "fints") { "Sparkasse snapshot must come from FinTS" }

        val currency = root.optString("currency").trim().uppercase()
        require(currency == "EUR") { "Only EUR Sparkasse snapshots are supported" }

        val balance = root.requireFiniteNumber("balance")
        val available = root.optFiniteNumber("available_balance") ?: balance
        val createdAt = Instant.parse(root.getString("created_at_utc"))

        val rows = root.optJSONArray("transactions") ?: JSONArray()
        require(rows.length() <= 5_000) { "Sparkasse snapshot has too many transactions" }

        val transactions = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val rowCurrency = row.optString("currency").trim().uppercase()
                require(rowCurrency == currency) { "Sparkasse transaction currency must be EUR" }
                val amount = row.requireFiniteNumber("amount")
                val date = LocalDate.parse(row.getString("booking_date"))
                add(
                    SparkasseTransactionSnapshot(
                        bookingDate = date,
                        amount = amount,
                        currency = rowCurrency,
                        merchant = row.optString("merchant").trim().take(180),
                        purpose = row.optString("purpose").trim().take(360),
                    )
                )
            }
        }

        return SparkasseSnapshot(
            createdAtUtc = createdAt,
            balance = balance,
            availableBalance = available,
            currency = currency,
            transactions = transactions.sortedByDescending { it.bookingDate },
        )
    }

    fun encode(snapshot: SparkasseSnapshot): String {
        val transactions = JSONArray().apply {
            snapshot.transactions.forEach { row ->
                put(
                    JSONObject()
                        .put("booking_date", row.bookingDate.toString())
                        .put("amount", row.amount)
                        .put("currency", row.currency)
                        .put("merchant", row.merchant)
                        .put("purpose", row.purpose)
                )
            }
        }

        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("source", "fints")
            .put("created_at_utc", snapshot.createdAtUtc.toString())
            .put("currency", snapshot.currency)
            .put("balance", snapshot.balance)
            .put("available_balance", snapshot.availableBalance)
            .put("transactions", transactions)
            .toString()
    }

    private fun JSONObject.requireFiniteNumber(key: String): Double =
        optFiniteNumber(key) ?: error("Missing or invalid Sparkasse value: $key")

    private fun JSONObject.optFiniteNumber(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = when (val raw = get(key)) {
            is Number -> raw.toDouble()
            is String -> raw.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return value?.takeIf(Double::isFinite)
    }
}
