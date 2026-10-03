package com.pix.folio

import com.pix.folio.data.BudgetEnvelope
import com.pix.folio.data.ScalableHoldingSnapshot
import com.pix.folio.data.ScalablePerformanceSnapshot
import com.pix.folio.data.ScalableSnapshot
import com.pix.folio.data.recurringInvestmentUnits
import com.pix.folio.model.FolioSummary
import com.pix.folio.model.InvestmentEntrySource
import com.pix.folio.model.InvestmentPricePoint
import com.pix.folio.model.InvestmentTransaction
import com.pix.folio.model.RecurringIncome
import com.pix.folio.ui.v081AbsoluteReturn
import com.pix.folio.ui.v081AbsoluteReturnSeries
import com.pix.folio.ui.v081CumulativeMonthlyContributions
import com.pix.folio.ui.v081CurrentValue
import com.pix.folio.ui.v081PurchaseLotValueAt
import com.pix.folio.ui.v081ResolvedOwnedUnits
import com.pix.folio.ui.v081SelectedMarketDate
import com.pix.folio.ui.v081TimeWeightedReturnPct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
    @Test
    fun recurringInvestmentUsesEurQuoteForUnits() {
        val price = InvestmentPricePoint(
            holdingId = "etf",
            date = LocalDate.of(2026, 10, 2),
            close = 100.0,
            currency = "EUR",
        )
        assertEquals(5.0, recurringInvestmentUnits(500.0, price), 0.001)
    }

    @Test
    fun recurringInvestmentDoesNotTreatUsdAsEur() {
        val price = InvestmentPricePoint(
            holdingId = "etf",
            date = LocalDate.of(2026, 10, 2),
            close = 100.0,
            currency = "USD",
        )
        assertEquals(0.0, recurringInvestmentUnits(500.0, price), 0.001)
    }

    @Test
    fun investmentPurchasesStayInTheirRealMonths() {
        val september = InvestmentTransaction(
            id = "sep",
            holdingId = "etf",
            amount = 400.0,
            date = LocalDate.of(2026, 9, 18),
            source = InvestmentEntrySource.INITIAL,
            time = LocalTime.of(9, 35),
        )
        val october = InvestmentTransaction(
            id = "oct",
            holdingId = "etf",
            amount = 500.0,
            date = LocalDate.of(2026, 10, 2),
            source = InvestmentEntrySource.RECURRING,
            time = LocalTime.of(16, 42),
        )
        val summary = FolioSummary(
            cashBalance = 0.0,
            emergencyFundBalance = 0.0,
            investments = emptyList(),
            investmentTransactions = listOf(september, october),
            investmentPrices = emptyList(),
            recurringInvestments = emptyList(),
            expenses = emptyList(),
            budgets = emptyList(),
            payments = emptyList(),
            incomes = emptyList(),
            ledger = emptyList(),
            balanceHistory = emptyList(),
            investmentHistory = emptyList(),
        )

        assertEquals(400.0, summary.monthOverview(YearMonth.of(2026, 9)).invested, 0.001)
        assertEquals(500.0, summary.monthOverview(YearMonth.of(2026, 10)).invested, 0.001)
    }

    @Test
    fun investmentPurchaseKeepsExactDateAndTime() {
        val transaction = InvestmentTransaction(
            id = "purchase",
            holdingId = "etf",
            amount = 500.0,
            date = LocalDate.of(2026, 9, 18),
            time = LocalTime.of(9, 35),
        )
        assertEquals(
            LocalDateTime.of(2026, 9, 18, 9, 35),
            transaction.purchasedAt,
        )
    }

    @Test
    fun laterPurchaseDoesNotExistBeforeItsPurchaseDate() {
        assertEquals(
            0.0,
            v081PurchaseLotValueAt(
                amount = 500.0,
                units = 0.0,
                purchaseDate = LocalDate.of(2026, 10, 2),
                date = LocalDate.of(2026, 9, 30),
                purchaseClose = 100.0,
                close = 98.0,
            ),
            0.001,
        )
    }

    @Test
    fun purchaseLotStartsAtItsOwnCostBasis() {
        assertEquals(
            500.0,
            v081PurchaseLotValueAt(
                amount = 500.0,
                units = 0.0,
                purchaseDate = LocalDate.of(2026, 10, 2),
                date = LocalDate.of(2026, 10, 2),
                purchaseClose = 100.0,
                close = 100.0,
            ),
            0.001,
        )
    }

    @Test
    fun cumulativeContributionsAddEachMonthOnTop() {
        val september = InvestmentTransaction(
            id = "sep",
            holdingId = "etf",
            amount = 1_000.0,
            date = LocalDate.of(2026, 9, 18),
            units = 8.0,
        )
        val october = InvestmentTransaction(
            id = "oct",
            holdingId = "etf",
            amount = 1_200.0,
            date = LocalDate.of(2026, 10, 1),
            units = 10.0,
        )

        val series = v081CumulativeMonthlyContributions(listOf(september, october))

        assertEquals(2, series.size)
        assertEquals(1_000.0, series[0].second, 0.001)
        assertEquals(2_200.0, series[1].second, 0.001)
    }

    @Test
    fun confirmedBrokerUnitsBeatTransactionUnitSum() {
        val september = InvestmentTransaction(
            id = "sep",
            holdingId = "etf",
            amount = 1_000.0,
            date = LocalDate.of(2026, 9, 18),
            units = 8.0,
        )
        val october = InvestmentTransaction(
            id = "oct",
            holdingId = "etf",
            amount = 1_200.0,
            date = LocalDate.of(2026, 10, 1),
            units = 10.0,
        )

        assertEquals(
            10.0,
            v081ResolvedOwnedUnits(10.0, listOf(september, october)) ?: 0.0,
            0.001,
        )
    }

    @Test
    fun transactionUnitsRemainEstimatedWithoutBrokerConfirmation() {
        val transaction = InvestmentTransaction(
            id = "auto",
            holdingId = "etf",
            amount = 1_000.0,
            date = LocalDate.of(2026, 10, 1),
            units = 8.25,
        )
        val inferredUnits = v081ResolvedOwnedUnits(null, listOf(transaction))
        val decision = v081CurrentValue(
            fallbackValue = 990.0,
            units = inferredUnits,
            latestPrice = 12.0,
            quoteCurrency = "EUR",
            unitsAreComplete = false,
        )

        assertFalse(decision.exact)
        assertEquals(990.0, decision.value, 0.001)
    }

    @Test
    fun purchaseLotUsesItsOwnUnitsAtMarketPrice() {
        assertEquals(
            1_172.8,
            v081PurchaseLotValueAt(
                amount = 1_100.0,
                units = 10.0,
                purchaseDate = LocalDate.of(2026, 9, 18),
                date = LocalDate.of(2026, 10, 2),
                purchaseClose = 110.0,
                close = 117.28,
            ),
            0.001,
        )
    }

    @Test
    fun todaysStoredQuoteKeepsTodaysPurchaseInCurrentValue() {
        val valuationDate = v081SelectedMarketDate(
            useStoredPoint = true,
            historyDate = LocalDate.of(2026, 10, 1),
            storedDate = LocalDate.of(2026, 10, 2),
            today = LocalDate.of(2026, 10, 2),
        )

        assertEquals(LocalDate.of(2026, 10, 2), valuationDate)
        assertEquals(
            100.0,
            v081PurchaseLotValueAt(
                amount = 100.0,
                units = 0.0,
                purchaseDate = LocalDate.of(2026, 10, 2),
                date = valuationDate,
                purchaseClose = 12.26,
                close = 12.26,
            ),
            0.001,
        )
    }

    @Test
    fun depositAloneDoesNotCreatePerformance() {
        val first = InvestmentTransaction(
            id = "first",
            holdingId = "etf",
            amount = 1_000.0,
            date = LocalDate.of(2026, 9, 1),
        )
        val second = InvestmentTransaction(
            id = "second",
            holdingId = "etf",
            amount = 1_000.0,
            date = LocalDate.of(2026, 10, 1),
        )
        val history = listOf(
            LocalDate.of(2026, 9, 1) to 1_000.0,
            LocalDate.of(2026, 10, 1) to 2_000.0,
        )

        assertEquals(
            0.0,
            v081TimeWeightedReturnPct(history, listOf(first, second)),
            0.001,
        )
    }

    @Test
    fun timeWeightedReturnCompoundsAcrossContributionPeriods() {
        val first = InvestmentTransaction(
            id = "first",
            holdingId = "etf",
            amount = 1_000.0,
            date = LocalDate.of(2026, 9, 1),
        )
        val second = InvestmentTransaction(
            id = "second",
            holdingId = "etf",
            amount = 1_000.0,
            date = LocalDate.of(2026, 10, 1),
        )
        val history = listOf(
            LocalDate.of(2026, 9, 1) to 1_000.0,
            LocalDate.of(2026, 9, 30) to 1_100.0,
            LocalDate.of(2026, 10, 1) to 2_100.0,
            LocalDate.of(2026, 10, 31) to 2_205.0,
        )

        assertEquals(
            15.5,
            v081TimeWeightedReturnPct(history, listOf(first, second)),
            0.001,
        )
    }

    @Test
    fun absoluteReturnSubtractsInvestedCapitalNotDepositsFromPerformance() {
        val first = InvestmentTransaction(
            id = "first",
            holdingId = "etf",
            amount = 1_000.0,
            date = LocalDate.of(2026, 9, 1),
        )
        val second = InvestmentTransaction(
            id = "second",
            holdingId = "etf",
            amount = 1_000.0,
            date = LocalDate.of(2026, 10, 1),
        )
        val history = listOf(
            LocalDate.of(2026, 9, 1) to 1_000.0,
            LocalDate.of(2026, 9, 30) to 1_100.0,
            LocalDate.of(2026, 10, 1) to 2_100.0,
            LocalDate.of(2026, 10, 31) to 2_205.0,
        )
        val returns = v081AbsoluteReturnSeries(history, listOf(first, second))

        assertEquals(100.0, returns[1].second, 0.001)
        assertEquals(100.0, returns[2].second, 0.001)
        assertEquals(205.0, returns.last().second, 0.001)
    }

    @Test
    fun brokerStyleAbsoluteReturnUsesCurrentValueMinusInvestedCapital() {
        assertEquals(
            50.0,
            v081AbsoluteReturn(currentValue = 1_250.0, investedCapital = 1_200.0),
            0.001,
        )
    }

    @Test
    fun scalableInvestmentValueExcludesBrokerCash() {
        val snapshot = ScalableSnapshot(
            createdAtUtc = Instant.parse("2026-01-01T00:00:00Z"),
            valuationTimestampUtc = null,
            cliVersion = "synthetic",
            currency = "EUR",
            totalValue = 1_500.0,
            securitiesValue = 1_200.0,
            cryptoValue = 50.0,
            performance = emptyList(),
            holdings = emptyList(),
        )

        assertEquals(1_250.0, snapshot.investmentValue, 0.001)
    }

    @Test
    fun scalableBrokerTotalReconcilesCashOrCredit() {
        val snapshot = ScalableSnapshot(
            createdAtUtc = Instant.parse("2026-01-01T00:00:00Z"),
            valuationTimestampUtc = null,
            cliVersion = "synthetic",
            currency = "EUR",
            totalValue = 1_500.0,
            securitiesValue = 1_200.0,
            cryptoValue = 50.0,
            performance = emptyList(),
            holdings = emptyList(),
        )

        assertEquals(250.0, snapshot.brokerCashOrCreditValue, 0.001)
        assertEquals(
            snapshot.totalValue,
            snapshot.investmentValue + snapshot.brokerCashOrCreditValue,
            0.001,
        )
    }

    @Test
    fun scalablePrimaryReturnPrefersAllTimeStyleFrame() {
        val snapshot = ScalableSnapshot(
            createdAtUtc = Instant.parse("2026-01-01T00:00:00Z"),
            valuationTimestampUtc = null,
            cliVersion = "synthetic",
            currency = "EUR",
            totalValue = 1_250.0,
            securitiesValue = 1_250.0,
            cryptoValue = 0.0,
            performance = listOf(
                ScalablePerformanceSnapshot("ONE_DAY", 1.0),
                ScalablePerformanceSnapshot("MAX", 42.0),
            ),
            holdings = listOf(
                ScalableHoldingSnapshot(
                    isin = "IE00B4L5Y983",
                    name = "Synthetic ETF",
                    securityType = "ETF",
                    quantity = 5.0,
                    valuation = 1_250.0,
                    valuationCurrency = "EUR",
                    quoteMidPrice = 250.0,
                    quoteCurrency = "EUR",
                    quoteTimestampUtc = null,
                    quoteOutdated = false,
                )
            ),
        )

        assertEquals("MAX", snapshot.primaryAbsoluteReturn?.timeframe)
        assertEquals(42.0, snapshot.primaryAbsoluteReturn?.absoluteReturn ?: 0.0, 0.001)
    }

}
