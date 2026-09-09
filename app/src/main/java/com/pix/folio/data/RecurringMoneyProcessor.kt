package com.pix.folio.data

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/**
 * Applies due recurring income, fixed payments and investment contributions.
 *
 * Salary keeps its real receive date while FolioStore attributes it to the configured
 * budget month (for example a September 29 salary can fund October).
 */
object RecurringMoneyProcessor {
    data class Result(
        val incomes: Int,
        val payments: Int,
        val investments: Int,
    ) {
        val totalApplied: Int get() = incomes + payments + investments
    }

    fun process(context: Context, today: LocalDate = LocalDate.now()): Result {
        val store = FolioStore(context.applicationContext)
        if (!store.autoRecurringEnabled()) return Result(0, 0, 0)

        val month = YearMonth.from(today)
        var incomesApplied = 0
        var paymentsApplied = 0
        var investmentsApplied = 0

        // Re-read after every action because each toggle persists a fresh snapshot.
        store.summary().incomes
            .filter { income ->
                income.enabled &&
                    income.lastReceivedMonth != month &&
                    !income.dueDateFor(month).isAfter(today)
            }
            .forEach { income ->
                store.toggleIncomeReceived(income.id)
                incomesApplied += 1
            }

        store.summary().payments
            .filter { payment ->
                payment.enabled &&
                    payment.lastPaidMonth != month &&
                    !payment.dueDateFor(month).isAfter(today)
            }
            .forEach { payment ->
                store.togglePayment(payment.id)
                paymentsApplied += 1
            }

        store.summary().recurringInvestments
            .filter { investment ->
                investment.enabled &&
                    investment.lastAppliedMonth != month &&
                    !investment.dueDateFor(month).isAfter(today)
            }
            .forEach { investment ->
                store.toggleRecurringInvestment(investment.id)
                investmentsApplied += 1
            }

        return Result(incomesApplied, paymentsApplied, investmentsApplied)
    }
}
