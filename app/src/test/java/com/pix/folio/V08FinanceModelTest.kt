package com.pix.folio

import com.pix.folio.data.BudgetEnvelope
import com.pix.folio.model.RecurringIncome
import com.pix.folio.ui.v081CurrentValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class V08FinanceModelTest {
    @Test
    fun day31ClampsToFebruaryEnd() {
        val income = RecurringIncome(
            id = "salary",
            name = "Salary",
            amount = 2_404.0,
            dayOfMonth = 31,
        )
        assertEquals("2028-02-29", income.dueDateFor(YearMonth.of(2028, 2)).toString())
        assertEquals("2027-02-28", income.dueDateFor(YearMonth.of(2027, 2)).toString())
    }

    @Test
    fun endOfMonthSalaryCanFundNextBudgetMonth() {
        val income = RecurringIncome(
            id = "salary",
            name = "Salary",
            amount = 2_404.0,
            dayOfMonth = 30,
            budgetMonthOffset = 1,
        )
        assertEquals(YearMonth.of(2026, 10), income.budgetMonthFor(YearMonth.of(2026, 9)))
    }

    @Test
    fun unassignedPlanIsNotTheSameAsSpendableCash() {
        val envelope = BudgetEnvelope(
            month = YearMonth.of(2026, 10),
            expectedIncome = 2_404.0,
            receivedIncome = 2_404.0,
            fixedPayments = 500.0,
            recurringInvestments = 1_000.0,
            manualInvestments = 0.0,
            plannedVariableSpending = 300.0,
            actualExpenses = 100.0,
            plannedSavings = 500.0,
            actualSavings = 0.0,
            actualPayments = 0.0,
            actualInvested = 0.0,
        )
        assertEquals(104.0, envelope.availableCash, 0.001)
        assertEquals(2_304.0, envelope.actualRemainder, 0.001)
    }

    @Test
    fun actualSavingsDoesNotDoubleCountAgainstPlannedSavings() {
        val envelope = BudgetEnvelope(
            month = YearMonth.of(2026, 10),
            expectedIncome = 2_404.0,
            receivedIncome = 2_404.0,
            fixedPayments = 400.0,
            recurringInvestments = 1_000.0,
            manualInvestments = 0.0,
            plannedVariableSpending = 300.0,
            actualExpenses = 0.0,
            plannedSavings = 500.0,
            actualSavings = 500.0,
            actualPayments = 0.0,
            actualInvested = 0.0,
        )
        assertEquals(500.0, envelope.savingsCommitment, 0.001)
        assertEquals(204.0, envelope.availableCash, 0.001)
    }

    @Test
    fun exactEurUnitsUseLatestMarketPrice() {
        val decision = v081CurrentValue(
            fallbackValue = 1_041.0,
            units = 10.0,
            latestPrice = 103.8,
            quoteCurrency = "EUR",
            unitsAreComplete = true,
        )
        assertTrue(decision.exact)
        assertEquals(1_038.0, decision.value, 0.001)
    }

    @Test
    fun nonEurQuoteDoesNotPretendToBeEuroExact() {
        val decision = v081CurrentValue(
            fallbackValue = 1_041.0,
            units = 10.0,
            latestPrice = 103.8,
            quoteCurrency = "USD",
            unitsAreComplete = true,
        )
        assertFalse(decision.exact)
        assertEquals(1_041.0, decision.value, 0.001)
    }

    @Test
    fun incompleteUnitsStayEstimated() {
        val decision = v081CurrentValue(
            fallbackValue = 1_041.0,
            units = 10.0,
            latestPrice = 103.8,
            quoteCurrency = "EUR",
            unitsAreComplete = false,
        )
        assertFalse(decision.exact)
        assertEquals(1_041.0, decision.value, 0.001)
    }
}
