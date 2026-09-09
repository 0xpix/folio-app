# Changelog

All notable Folio changes are documented here. Release tags use the same convention as the rest of the PIX projects:

- Beta: `vMAJOR.MINOR.PATCH.beta`
- Stable: `vMAJOR.MINOR.PATCH`

The GitHub release workflow publishes the matching section of this file as the release notes, and the Android beta updater shows the same **Added / Changed / Fixed** notes before installation.

## [0.6.0.beta] - 2026-09-09

### Added
- New Insights workspace linked directly from Home.
- Month-over-month comparison for income, spending, invested amount, and money left.
- Editable emergency-fund balance with coverage measured in months of recent outflow.
- Investment contribution streak indicator.
- Automatic net-worth milestones with progress toward the next target.
- Full transaction timeline combining income, expenses, recurring payments, and investment contributions by month.
- Annual Review with yearly income, spending, investment contributions, money left, net-worth change, active months, and contribution count.
- Projected Growth scenario calculator with editable monthly contribution, annual return, time horizon, chart, and checkpoint values.
- Investment Lab for market value, cost basis, unrealized gain/loss, units, latest recorded price, price-history chart, purchase history, tags, and notes.
- Manual dated market-price entries for tracked holdings.
- Optional units/shares and purchase price when adding investment contributions from Investment Lab.
- One-step Undo access from Insights for the latest local financial change.

### Changed
- Home now surfaces a compact Insights summary with month-over-month money-left change, emergency-fund coverage, and investment streak.
- Portfolio rows use calculated market value when units and a recorded market price are available, falling back to contributed value otherwise.
- Folio now exposes the v0.5 finance-model capabilities in the Android interface instead of keeping them as storage/model-only features.
- Version advanced to `v0.6.0.beta` using the same beta naming convention as the other PIX projects.

### Fixed
- Closed the gap between the v0.5.1 data model and the visible Android product by making the stored emergency-fund, price-history, tags, notes, milestones, review, projection, and timeline features usable in-app.
- Kept the new v0.6 actions compatible with older holdings that do not yet have units, prices, tags, or notes.

## [0.5.1.beta] - 2026-09-08

### Added
- Expanded finance models for annual reviews, projected-growth scenarios, net-worth milestones, transaction timelines, monthly comparisons, emergency-fund coverage, and investment contribution streaks.
- Investment tags for Long term, Speculative, Retirement, CS2 case, and High risk positions.
- Per-investment notes and first-class Bond, Index, and CS2 investment kinds.
- Optional investment units/shares and unit purchase price in the transaction model.
- Investment price-history records with symbol, currency, source metadata, and last-refresh timestamps.
- Local persistence for emergency-fund allocation, investment tags and notes, units, purchase prices, and market-price history.
- One-step local undo snapshots for financial data mutations, including Clear All recovery.
- Structured `CHANGELOG.md` release notes and Folio repository branding assets.

### Changed
- Folio Android is the primary Folio product; the release work no longer depends on Folio Web.
- Initial holdings are recorded as initial purchases while later additions remain manual contributions.
- Portfolio and net-worth snapshots use units × latest recorded price when position data is available, with contributed value as the safe fallback.
- Investment storage remains backward-compatible when older records do not contain the new v0.5 fields.
- Beta versioning is standardized to `v0.5.1.beta`, without an additional beta build suffix.
- Release notes are sourced from this changelog so GitHub releases and the in-app beta updater can present the same Added / Changed / Fixed history.

### Fixed
- Fixed the v0.5 model/store integration that prevented beta and Play debug variants from compiling after the finance-model expansion.
- Preserved newly introduced investment metadata instead of dropping it when holdings are re-saved.
- Preserved undo state across Clear All so the previous local financial state can still be restored.
- Removed the unfinished `v0.5.0.beta` release marker; this completed continuation ships as `v0.5.1.beta`.

## [0.3.0.beta] - 2026-09-07

### Added
- Minimal budgets and Monthly Overview.
- ISIN lookup through OpenFIGI.
- Recurring investments.
- Net-worth history and Upcoming on Home.
- Optional biometric/device-credential app lock.

### Changed
- Fresh installs start without dummy financial data.
- Recurring income and payments became month-aware.

### Fixed
- Compose app-lock JVM setter signature collision.
- GitHub beta updater repository target.

## [0.2.0.beta] - 2026-09-07

### Added
- Editable cash balance.
- Recurring income and payment tracking.
- Manual ETF, stock, fund, and other investment entries.
- Local balance and investment history.

### Changed
- Bottom-navigation geometry stays fixed when selecting tabs.
- Expense totals are scoped to the current month.

### Fixed
- Removed seeded demo balances, holdings, expenses, and fake chart curves.

## [0.1.0.beta] - 2026-09-07

### Added
- Initial Folio Android shell.
- Home, Expenses, Investments, Payments, and More.
- Local SharedPreferences-backed storage.
- 3×1 balance widget.
- GitHub beta release updater and signed beta release workflow.
