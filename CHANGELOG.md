# Changelog

All notable Folio changes are documented here. Release tags use the same convention as the rest of the PIX projects:

- Beta: `vMAJOR.MINOR.PATCH.beta`
- Stable: `vMAJOR.MINOR.PATCH`

The GitHub release workflow publishes the matching section of this file as the release notes, and the Android beta updater shows the same **Added / Changed / Fixed** notes before installation.

## [0.7.1.beta] - 2026-09-09

### Added
- Horizontal swipe navigation between Money, Home, and Portfolio.
- A dedicated manual savings transfer sheet for Emergency Fund, Crash Reserve, and General Savings, with explicit Add from cash and Withdraw to cash actions.
- A clean in-app release-note renderer that formats Added, Changed, and Fixed sections instead of showing raw changelog text.

### Changed
- Home is now the center item in bottom navigation: Money · Home · Portfolio.
- Settings is exposed from Home only and stays out of Money and Portfolio.
- Home was simplified to a clearer hierarchy: net worth, compact cash/savings/portfolio totals, monthly plan, savings goals, and recent activity.
- Money was reorganized into clear sections for available cash, quick actions, manual savings, month plan, recurring flows, and expenses.
- Main financial typography is intentionally larger, with oversized net-worth and cash values plus larger page and section headings inspired by the new reference design.
- Savings copy now explicitly states that Emergency Fund and other savings transfers are manual; recurring automation only applies to income, bills, and recurring investments.
- Beta version advanced to `v0.7.1.beta`.

### Fixed
- Fixed the top safe-area overlap that could place the Folio logo and controls underneath the phone status bar.
- Fixed the settings affordance effectively disappearing under the top system bar on edge-to-edge devices.
- Fixed the confusing Emergency Fund flow by making the savings bucket directly tappable and manually editable.
- Fixed the beta updater changelog layout so long release notes wrap cleanly and can be expanded or hidden.

## [0.7.0.beta] - 2026-09-09

### Added
- Budget-month attribution for recurring income, so salary received near the end of one month can fund the following month while keeping the real transaction date.
- Forward-looking monthly Money Plan with expected income, fixed payments, recurring investments, expenses, savings allocations, committed amount, and projected money left.
- Savings buckets for Emergency Fund, Crash Reserve, and General Savings, including transfers between spendable cash and savings.
- Editable emergency-fund and crash-reserve targets with progress tracking; the emergency target defaults to €3,500.
- Emergency-fund runway based on recent monthly outflow.
- End-of-month leftover allocation into Emergency Fund, Crash Reserve, General Savings, investments, or retained cash.
- Automatic recurring processing for due income, fixed payments, and recurring investment contributions.
- Background WorkManager automation plus a startup pass so recurring items can be applied even when they were not manually opened on their due date.
- Automatic market-price refresh for supported exchange-traded holdings and Steam Community Market pricing for CS2 assets.
- Dedicated CS2 investment metadata for cases, stickers, skins, and other assets, including Steam market hash names.
- Compact Portfolio screen with ISIN lookup, investment creation, market value, gain/loss, contribution history inputs, recurring purchases, manual prices, automatic tracking fields, and allocation summary.
- Selectable app typography with Pixify/Pixelify Sans as the default, plus System, Mono, and Serif options.
- A redesigned Home that combines full net worth, history, monthly plan, emergency/crash goals, savings, cash, and portfolio status.
- A consolidated Money screen containing the monthly plan, expenses, recurring flows, savings goals, and leftover allocation in one place.

### Changed
- Folio now has only three permanent destinations: Home, Money, and Portfolio. Settings opens as a sheet instead of another root page.
- Net worth now includes spendable cash, Emergency Fund, Crash Reserve, General Savings, ETFs/stocks/funds, and CS2 assets.
- Net-worth snapshots use the complete net-worth calculation rather than cash plus investments only.
- Recurring income can be marked as funding the following budget month; for example, salary received on September 29 can be attributed to October.
- Recurring investments can be processed automatically and can record units from the latest known market price when available.
- The visual system is more restrained: fewer persistent navigation choices, larger financial hierarchy, lighter controls, compact metrics, and sheet-based editing.
- Beta version advanced to `v0.7.0.beta` with no extra beta build suffix.

### Fixed
- Emergency savings are no longer excluded from the net-worth total.
- Removed the requirement to manually confirm every recurring salary, bill, or recurring investment when automatic recurring processing is enabled.
- Removed the old five-root-page navigation from the active app shell.
- Closed the missing crash-reserve and generic-savings gaps in the previous finance model.
- Closed the missing CS2 case/sticker/skin classification and price-tracking gap.
- Replaced manual-only investment price tracking with optional automatic refresh while preserving manual price entry as a fallback.

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
