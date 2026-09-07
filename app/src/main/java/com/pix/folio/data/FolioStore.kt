package com.pix.folio.data

import android.content.Context
import com.pix.folio.model.Expense
import com.pix.folio.model.ExpenseCategory
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.InvestmentHolding
import com.pix.folio.model.MonthlyPayment
import com.pix.folio.model.PaymentCategory
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class FolioStore(context: Context) {
    private val prefs = context.getSharedPreferences("folio_store_v2", Context.MODE_PRIVATE)
    private val month = YearMonth.now()

    fun summary(): FolioSummary = FolioSummary(
        cashBalance = prefs.getString("cash_balance", null)?.toDoubleOrNull() ?: 1_270.0,
        monthlyIncome = prefs.getString("monthly_income", null)?.toDoubleOrNull() ?: 2_450.0,
        monthlyInvestmentContribution = prefs.getString("monthly_investment", null)?.toDoubleOrNull() ?: 500.0,
        investments = investments(),
        expenses = expenses(),
        payments = payments(),
    )

    fun expenses(): List<Expense> {
        val raw = prefs.getString("expenses", null) ?: return seededExpenses()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val json = array.getJSONObject(i)
                    add(
                        Expense(
                            id = json.getString("id"),
                            category = ExpenseCategory.valueOf(json.getString("category")),
                            amount = json.getDouble("amount"),
                            date = LocalDate.parse(json.getString("date")),
                            note = json.optString("note"),
                        )
                    )
                }
            }
        }.getOrElse { seededExpenses() }
    }

    fun payments(): List<MonthlyPayment> {
        val raw = prefs.getString("payments", null) ?: return seededPayments()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val json = array.getJSONObject(i)
                    add(
                        MonthlyPayment(
                            id = json.getString("id"),
                            category = PaymentCategory.valueOf(json.getString("category")),
                            name = json.getString("name"),
                            amount = json.getDouble("amount"),
                            dueDate = LocalDate.parse(json.getString("dueDate")),
                            paid = json.getBoolean("paid"),
                        )
                    )
                }
            }
        }.getOrElse { seededPayments() }
    }

    fun investments(): List<InvestmentHolding> {
        val raw = prefs.getString("investments", null) ?: return seededInvestments()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val json = array.getJSONObject(i)
                    add(
                        InvestmentHolding(
                            id = json.getString("id"),
                            name = json.getString("name"),
                            symbol = json.getString("symbol"),
                            group = json.getString("group"),
                            amount = json.getDouble("amount"),
                        )
                    )
                }
            }
        }.getOrElse { seededInvestments() }
    }

    fun addExpense(category: ExpenseCategory, amount: Double, note: String) {
        if (amount <= 0.0) return
        val next = expenses() + Expense(
            id = UUID.randomUUID().toString(),
            category = category,
            amount = amount,
            date = LocalDate.now(),
            note = note.trim(),
        )
        writeExpenses(next)
        setCashBalance((summary().cashBalance - amount).coerceAtLeast(0.0))
    }

    fun addPayment(category: PaymentCategory, name: String, amount: Double, dueDate: LocalDate) {
        if (amount <= 0.0 || name.isBlank()) return
        val next = payments() + MonthlyPayment(
            id = UUID.randomUUID().toString(),
            category = category,
            name = name.trim(),
            amount = amount,
            dueDate = dueDate,
            paid = false,
        )
        writePayments(next)
    }

    fun togglePayment(id: String) {
        val current = payments()
        val row = current.firstOrNull { it.id == id } ?: return
        val nextPaid = !row.paid
        writePayments(current.map { if (it.id == id) it.copy(paid = nextPaid) else it })
        val cash = summary().cashBalance
        setCashBalance(if (nextPaid) (cash - row.amount).coerceAtLeast(0.0) else cash + row.amount)
    }

    fun setMonthlyIncome(value: Double) {
        prefs.edit().putString("monthly_income", value.coerceAtLeast(0.0).toString()).apply()
    }

    fun setCashBalance(value: Double) {
        prefs.edit().putString("cash_balance", value.coerceAtLeast(0.0).toString()).apply()
    }

    fun resetDemo() {
        prefs.edit().clear().apply()
    }

    private fun writeExpenses(rows: List<Expense>) {
        val array = JSONArray()
        rows.forEach { row ->
            array.put(
                JSONObject()
                    .put("id", row.id)
                    .put("category", row.category.name)
                    .put("amount", row.amount)
                    .put("date", row.date.toString())
                    .put("note", row.note)
            )
        }
        prefs.edit().putString("expenses", array.toString()).apply()
    }

    private fun writePayments(rows: List<MonthlyPayment>) {
        val array = JSONArray()
        rows.forEach { row ->
            array.put(
                JSONObject()
                    .put("id", row.id)
                    .put("category", row.category.name)
                    .put("name", row.name)
                    .put("amount", row.amount)
                    .put("dueDate", row.dueDate.toString())
                    .put("paid", row.paid)
            )
        }
        prefs.edit().putString("payments", array.toString()).apply()
    }

    private fun seededExpenses(): List<Expense> {
        val d = month.atDay(1)
        return listOf(
            Expense("seed-food", ExpenseCategory.FOOD, 320.0, d.plusDays(1)),
            Expense("seed-home", ExpenseCategory.HOME, 280.0, d.plusDays(1)),
            Expense("seed-transport", ExpenseCategory.TRANSPORT, 120.0, d.plusDays(2)),
            Expense("seed-shopping", ExpenseCategory.SHOPPING, 110.0, d.plusDays(3)),
            Expense("seed-health", ExpenseCategory.HEALTH, 90.0, d.plusDays(3)),
            Expense("seed-leisure", ExpenseCategory.LEISURE, 70.0, d.plusDays(4)),
            Expense("seed-other", ExpenseCategory.OTHER, 190.0, d.plusDays(5)),
        )
    }

    private fun seededPayments(): List<MonthlyPayment> {
        fun day(value: Int) = month.atDay(value.coerceAtMost(month.lengthOfMonth()))
        return listOf(
            MonthlyPayment("seed-rent", PaymentCategory.HOME, "Rent", 450.0, day(1), true),
            MonthlyPayment("seed-phone", PaymentCategory.PHONE, "Phone", 10.0, day(8), false),
            MonthlyPayment("seed-gym", PaymentCategory.GYM, "Gym", 30.0, day(10), false),
            MonthlyPayment("seed-streaming", PaymentCategory.STREAMING, "Netflix", 13.0, day(12), false),
            MonthlyPayment("seed-electricity", PaymentCategory.ELECTRICITY, "Electricity", 60.0, day(15), false),
            MonthlyPayment("seed-internet", PaymentCategory.INTERNET, "Internet", 30.0, day(18), false),
        )
    }

    private fun seededInvestments(): List<InvestmentHolding> = listOf(
        InvestmentHolding("ac-world", "AC World", "ETF", "ETFs", 7_650.0),
        InvestmentHolding("world-small", "World Small Cap", "ETF", "ETFs", 421.2),
        InvestmentHolding("nvidia", "NVIDIA", "NVDA", "Stocks", 1_280.0),
        InvestmentHolding("biontech", "BioNTech", "BNTX", "Stocks", 737.8),
        InvestmentHolding("other", "Other", "—", "Others", 1_121.0),
    )
}
