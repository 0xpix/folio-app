package com.pix.folio.data

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/**
 * Applies due recurring income, fixed payments, investment contributions and monthly savings.
 *
 * Salary keeps its real receive date while FolioStore attributes it to the configured budget
 * month. Outflows are only allowed against income that is actually assigned to that month, so
 * leftover cash from a previous month can never silently fund the next month's plan.
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

    fun process(context: Context, today: LocalDate = LocalDate.now()): Result {
        val appContext = context.applicationContext
        val store = FolioStore(appContext)
        if (!store.autoRecurringEnabled()) return Result(0, 0, 0, 0)

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

        fun hasBudgetMoney(amount: Double): Boolean {
            val summary = store.summary()
            return summary.cashBalance + 0.005 >= amount &&
                planStore.budgetCashRemaining(summary, month) + 0.005 >= amount
        }

        // Fixed bills are posted only after this budget month has received enough assigned income.
        store.summary().payments
            .filter { payment ->
                payment.enabled &&
                    payment.lastPaidMonth != month &&
                    !payment.dueDateFor(month).isAfter(today)
            }
            .forEach { payment ->
                if (hasBudgetMoney(payment.amount)) {
                    store.togglePayment(payment.id)
                    paymentsApplied += 1
                }
            }

        // Recurring investments follow the same envelope guard; old carry-over cash is excluded.
        store.summary().recurringInvestments
            .filter { investment ->
                investment.enabled &&
                    investment.lastAppliedMonth != month &&
                    !investment.dueDateFor(month).isAfter(today)
            }
            .forEach { investment ->
                if (hasBudgetMoney(investment.amount)) {
                    store.toggleRecurringInvestment(investment.id)
                    investmentsApplied += 1
                }
            }

        // Monthly savings is a first-class recurring allocation but remains real cash in a bucket.
        val savingsApplied = planStore.applyDueSavings(store, today)

        return Result(incomesApplied, paymentsApplied, investmentsApplied, savingsApplied)
    }
}
