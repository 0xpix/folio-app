# Changelog

All notable Folio changes are documented here. Release tags use the same convention as the rest of the PIX projects:

- Beta: `vMAJOR.MINOR.PATCH.beta`
- Stable: `vMAJOR.MINOR.PATCH`

The GitHub release workflow publishes the matching section of this file as the release notes, and the Android beta updater shows the same **Added / Changed / Fixed** notes before installation.

## [0.5.0.beta] - 2026-09-08

### Added
- Annual Review with yearly income, spending, investments, net-worth change, active months, contribution count, and month-by-month activity.
- Projected Growth scenarios with editable monthly contribution, annual-return assumption, time horizon, chart, and checkpoint values.
- Automatic net-worth milestones from €1,000 through €1,000,000.
- Investment tags: Long term, Speculative, Retirement, CS2 case, and High risk.
- Per-investment notes for a thesis, reminder, or personal context.
- CS2 as a first-class manually tracked investment type for cases and other Counter-Strike assets.
- Undo for the latest local money/data change, including expenses, budgets, recurring entries, investments, balances, tags, and notes.
- Optional units/shares and purchase price on investment purchases and contributions.
- Current market value, cost basis, unrealized gain/loss, and percentage performance when units and market prices are available.
- Provider/source and last-updated timestamps on investment market-price history.
- Transaction timeline combining income, expenses, bills, and investments.
- Monthly comparison against the previous month.
- Emergency-fund coverage in months based on recent outflow.
- Investment contribution streaks.
- Dated investment purchase history and price-history graphs with purchase markers.
- ISIN-first instrument lookup through OpenFIGI for ETFs, stocks, funds, bonds, and indexes.
- Recurring salary, recurring bills, and recurring investments.
- Minimal category budgets, Monthly Overview, Net-worth History, Upcoming on Home, and biometric/device-credential app lock.

### Changed
- Folio Android is now the primary and self-contained Folio product; Folio Web is no longer required for the product roadmap.
- Investment totals now distinguish contributed cost basis from current market value when enough position data is available.
- Portfolio allocation uses current market value when Folio can calculate it, otherwise it safely falls back to contributed value.
- Refreshing market prices now records a fresh portfolio/net-worth snapshot.
- Investment entry supports manual assets without requiring an ISIN.
- The beta updater preserves structured release notes instead of flattening them into plain text.
- Versioning is standardized to `v0.5.0.beta` style with no extra beta build suffix.
- README and repository structure are product-oriented rather than scaffold/debug oriented.
- `CHANGELOG.md` is the single source of truth for release notes.

### Fixed
- Prevented CS2/manual holdings from attempting unsupported automatic market-price refreshes.
- Preserved legacy investment records when new units, tags, notes, and provider metadata are absent.
- Undo data now survives a Clear All action long enough to restore the previous local state.
- Added missing undo capture for newly created recurring investments.
- Net-worth and investment snapshots now reflect refreshed market values when units are known.
- Removed stale compile-fix artifacts and old per-version update notes from the product repo.
- Removed committed beta signing-key material from the repository and blocked signing-key/base64 files via `.gitignore`.

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
