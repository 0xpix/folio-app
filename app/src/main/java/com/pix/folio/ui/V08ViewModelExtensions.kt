package com.pix.folio.ui

import android.app.Application
import com.pix.folio.data.FolioBackup
import com.pix.folio.data.FolioRoomMirror
import com.pix.folio.data.InvestmentTrackingStore
import com.pix.folio.model.Cs2AssetType
import com.pix.folio.model.InvestmentKind
import java.time.LocalDateTime

internal fun V07ViewModel.purchaseDateTimeFor(id: String): LocalDateTime? =
    InvestmentTrackingStore(getApplication<Application>()).purchaseDateTime(id)

internal fun V07ViewModel.setInvestmentPurchaseDateTime(id: String, dateTime: LocalDateTime) {
    val safe = dateTime.coerceAtMost(LocalDateTime.now())
    InvestmentTrackingStore(getApplication<Application>()).setPurchaseDateTime(id, safe)
    // Re-use the existing history refresh path after the timestamp is persisted.
    setInvestmentPurchaseDate(id, safe.toLocalDate())
}

internal fun V07ViewModel.ownedUnitsFor(id: String): Double? =
    InvestmentTrackingStore(getApplication<Application>()).ownedUnits(id)
        ?: summary.unitsFor(id).takeIf { it > 0.0 }

internal fun V07ViewModel.setInvestmentOwnedUnits(id: String, units: Double?) {
    InvestmentTrackingStore(getApplication<Application>()).setOwnedUnits(id, units)
    refreshTrackedInvestment(id)
}

internal fun V07ViewModel.addInvestmentV08(
    kind: InvestmentKind,
    name: String,
    symbol: String,
    amount: Double,
    purchaseDateTime: LocalDateTime,
    isin: String = "",
    figi: String = "",
    exchange: String = "",
    units: Double = 0.0,
    marketHashName: String = "",
    cs2AssetType: Cs2AssetType = Cs2AssetType.OTHER,
) {
    addInvestment(
        kind = kind,
        name = name,
        symbol = symbol,
        amount = amount,
        isin = isin,
        figi = figi,
        exchange = exchange,
        units = units,
        unitPrice = if (units > 0.0) amount / units else 0.0,
        marketHashName = marketHashName,
        cs2AssetType = cs2AssetType,
        purchaseDate = purchaseDateTime.toLocalDate(),
    )
    val normalizedIsin = isin.trim().uppercase()
    val normalizedSymbol = symbol.trim().uppercase()
    summary.investments.firstOrNull {
        (normalizedIsin.isNotBlank() && it.isin.equals(normalizedIsin, true)) ||
            (normalizedSymbol.isNotBlank() && it.symbol.equals(normalizedSymbol, true)) ||
            it.name.equals(name.trim(), true)
    }?.let { setInvestmentPurchaseDateTime(it.id, purchaseDateTime) }
}

internal fun V07ViewModel.exportBackupV08(): String = FolioBackup.export(getApplication<Application>())

internal fun V07ViewModel.importBackupV08(raw: String): Result<Unit> =
    FolioBackup.import(getApplication<Application>(), raw).onSuccess {
        refresh()
        refreshTrackedInvestments()
    }

internal suspend fun V07ViewModel.mirrorToRoomV08() {
    FolioRoomMirror.save(getApplication<Application>())
}

private fun LocalDateTime.coerceAtMost(maximum: LocalDateTime): LocalDateTime =
    if (isAfter(maximum)) maximum else this
