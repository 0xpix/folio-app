package com.pix.folio.data

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/**
 * Applies due recurring income, fixed payments, investment contributions and monthly savings.
 *
 * Salary keeps its real receive date while FolioStore attributes it to the configured budget
 * month. Budget attribution is planning/reporting metadata; due recurring transactions use the
 * actual available cash balance so a hidden planning envelope cannot block them.
 */
object RecurringMoneyProcessor {
    data class Result(
        val incomes: Int,
        val payments: Int,
        val investments: Int,
        val savings: Int,
    ) {
        val totalApplied: Int get() = incomes + payments + investments + savings
    }

    fun process(
        context: Context,
        today: LocalDate = LocalDate.now(),
        requireAutomationEnabled: Boolean = true,
    ): Result {
        val appContext = context.applicationContext
        val store = FolioStore(appContext)
        if (requireAutomationEnabled && !store.autoRecurringEnabled()) return Result(0, 0, 0, 0)

        val planStore = MonthlyPlanStore(appContext)
        val month = YearMonth.from(today)
        var incomesApplied = 0
        var paymentsApplied = 0
        var investmentsApplied = 0

        // Salary/income arrives first. A late-month salary can be attributed to the next month.
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

        fun hasCash(amount: Double): Boolean =
            store.summary().cashBalance + 0.005 >= amount

        // A due recurring item is a real transaction. If the account has the cash, post it.
        // Budget envelopes remain planning information and never silently block automation.
        store.summary().payments
            .filter { payment ->
                payment.enabled &&
                    payment.lastPaidMonth != month &&
                    !payment.dueDateFor(month).isAfter(today)
            }
            .forEach { payment ->
                if (hasCash(payment.amount)) {
                    store.togglePayment(payment.id)
                    paymentsApplied += 1
                }
            }

        // Recurring investments use the same real-cash rule as bills.
        store.summary().recurringInvestments
            .filter { investment ->
                investment.enabled &&
                    investment.lastAppliedMonth != month &&
                    !investment.dueDateFor(month).isAfter(today)
            }
            .forEach { investment ->
                if (hasCash(investment.amount)) {
                    store.toggleRecurringInvestment(investment.id)
                    investmentsApplied += 1
                }
            }

        // Monthly savings is a first-class recurring allocation but remains real cash in a bucket.
        val savingsApplied = planStore.applyDueSavings(store, today)

        return Result(incomesApplied, paymentsApplied, investmentsApplied, savingsApplied)
    }
}
