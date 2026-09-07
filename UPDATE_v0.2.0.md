# Folio Android update

Implemented in this source package:

- Fixed bottom navigation icon movement by reserving a fixed label slot for every tab.
- Fixed GitHub beta updater repository URL: `0xpix/folio-app`.
- Removed all seeded/demo expenses, payments, holdings, balances, income, and fake chart curves.
- Added one-time migration that strips legacy `seed-*` records from existing installs.
- Added investment entry for ETF, Stock, Fund, and Other holdings.
- Repeated additions to the same ETF/holding accumulate into the tracked amount.
- Added selectable 1W / 1M / 3M / 1Y / ALL chart ranges.
- Charts now use locally recorded balance/investment snapshots only.
- Added recurring monthly income (Salary by default) with received-this-month tracking.
- Recurring payments now roll to each new month and remember paid state per month.
- Expenses totals are scoped to the current month.
- Added editable starting cash balance.
- Added clear-all-data confirmation.
- Widget uses real local balance history instead of a fake sparkline.

Validation performed:

- `python3 scripts/validate_release.py` passes.
- All Android XML resources parse successfully.
- GitHub Actions YAML parses successfully.
- No `import androidx.compose.foundation.layout.weight` remains.
- No old `0xpix/folio-android/releases` updater URL remains.
- No old hard-coded demo labels/values remain under `app/src`.

A full Android/Compose compile still needs GitHub Actions or an Android SDK environment.
