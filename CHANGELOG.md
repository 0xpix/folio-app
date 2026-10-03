# Changelog

All notable Folio changes are documented here. Release tags use the same convention as the rest of the PIX projects:

- Beta: `vMAJOR.MINOR.PATCH.beta`
- Stable: `vMAJOR.MINOR.PATCH`
- **Feature release:** increment MINOR and reset PATCH to 0, e.g. `0.8.5.beta → 0.9.0.beta`
- **Bug-fix/refinement release:** increment PATCH only, e.g. `0.9.0.beta → 0.9.1.beta`

The GitHub release workflow publishes the matching section of this file as the release notes, and the Android beta updater shows the same **Added / Changed / Fixed** notes before installation.

## [0.16.1.beta] - 2026-10-03

### Changed
- Rolled Folio back to the complete **0.15.0.beta feature set** after removing the Sparkasse/FinTS experiment.

### Fixed
- Removed all Sparkasse connection, snapshot, FinTS helper, bank-transaction categorization, and bank-balance override code introduced in 0.16.0.
- Restored the pre-Sparkasse spending categories, Money behavior, Home cash behavior, widget behavior, backup/security contract, and release validation rules.

## [0.16.0.beta] - 2026-10-03

### Changed
- Superseded by 0.16.1.beta. This release briefly introduced the Sparkasse/FinTS experiment that has now been fully removed.

## [0.15.0.beta] - 2026-10-03

### Added
- Added an interactive broker Portfolio performance graph to the Scalable-connected view, derived only from Scalable's broker absolute-return timeframes and current broker total.
- Added a dedicated Folio mark inside the home-screen widget.

### Changed
- Replaced the old switchable/resizable widget setup with one fixed **2×2 Folio overview widget** showing the Folio mark, Net worth, Savings, Cash, and Investments without resizing.
- Tightened the Home header spacing so the Folio mark/settings row sits closer to the top system inset.
- Simplified cash presentation by removing the separate **Left to plan** figure from Home and Money; **Available cash** is now the single visible cash balance.

### Fixed
- Fixed the Scalable Portfolio view having timeframe controls without a visible graph.
- Fixed the 2×2 overview widget needing extra resizing to expose all metrics.

## [0.14.3.beta] - 2026-10-03

### Fixed
- Fixed Scalable broker cash being counted twice in the connected Portfolio total.
- Scalable's `overview.valuation.total` is now stored and displayed as the authoritative broker total; `cash-breakdown.cash_balance` remains a separate informational component and is no longer added on top.
- Updated snapshot validation so broker total, non-cash portfolio value, cash, grouped positions, and displayed Portfolio value stay internally consistent.

## [0.14.2.beta] - 2026-10-03

### Changed
- Simplified the connected Portfolio screen to a Scalable-style layout: portfolio value, broker absolute return/timeframe, broker cash, and broker positions only.
- Removed reconciliation/debug cards, snapshot metadata, ISIN/unit noise, local-history explanations, and Folio-derived portfolio metrics from the connected view.
- The Scalable snapshot helper now reads portfolio groups in addition to overview, holdings, and cash breakdown so grouped positions omitted from the normal holdings response can be recovered from Scalable's own group valuation.

### Fixed
- Fixed a fresh reset + Scalable import showing €0 when the imported broker overview had a valid value but the holdings array was empty.
- Fixed the connected headline depending on the summed holdings list instead of the imported broker total.
- Fixed single grouped broker positions missing from the normal holdings response being absent from the sanitized snapshot when Scalable exposes a group valuation for them.
- A Scalable snapshot with no broker value now fails import instead of silently becoming a €0 portfolio.

## [0.14.1.beta] - 2026-10-03

### Changed
- Scalable-connected Portfolio is now a dedicated broker-only screen. Folio local holdings, local cost basis, purchase history, reconstructed charts, and Yahoo/OpenFIGI valuation do not run while a Scalable snapshot is connected.
- The connected Portfolio headline, Home net worth, and both widgets now use only Scalable-returned holding valuations plus Scalable broker cash.
- The snapshot helper now reads `sc broker cash-breakdown --json` in addition to overview and holdings, and prints a local holdings/cash/total reconciliation after snapshot creation.

### Fixed
- Fixed Scalable-connected Portfolio still inheriting Folio calculations even though the user wanted the broker to be the sole source of truth.
- Fixed positions present in Scalable holdings, such as an additional broker position outside the previous overview aggregate, being omitted from the connected headline when the holdings sum differed from overview valuation.
- Fixed broker cash being unavailable to the connected Portfolio snapshot.

## [0.14.0.beta] - 2026-10-03

### Added
- A redesigned Portfolio hero that makes the active valuation source explicit and groups broker total, invested capital, holdings value, and broker cash/credit in one readable summary.
- A separate **2×2 Folio overview widget** showing Net worth, Investments, Cash, and Savings, while keeping the existing compact switchable widget.
- Broker reconciliation details showing the imported holdings sum, overview securities/crypto value, broker cash/credit residual, Scalable CLI version, holding count, and snapshot timestamp.

### Changed
- Scalable-connected Portfolio mode now keeps broker truth and Folio history visually separate instead of blending them into one performance view.
- When an all-time Scalable absolute return is available, Folio derives invested capital from the same broker snapshot: broker total minus broker absolute return.
- Historical Return and Contributions charts are explicitly labelled as Folio history while a Scalable snapshot is active.
- Both home-screen widgets refresh together after finance changes or a Scalable snapshot import.

### Fixed
- Fixed Scalable mode still mixing Folio's manual cost basis into broker-mode return/invested calculations.
- Fixed a successful Scalable import not proving that the encrypted snapshot could be loaded back; Folio now reloads the persisted encrypted snapshot before switching the UI source.
- Fixed the Portfolio page making a valid broker import look ineffective by hiding source reconciliation behind local-history calculations.

## [0.13.1.beta] - 2026-10-03

### Changed
- Scalable-connected Portfolio, Home net worth, and the Investments widget now use Scalable's exact broker total as the authoritative connected value.
- The Scalable Portfolio card now keeps securities + crypto visible separately and shows the broker cash/credit reconciliation needed to reach the exact broker total.
- Settings now shows the imported holding count, broker total, and import time so a successful Scalable import is immediately visible.

### Fixed
- Fixed Scalable imports appearing to do nothing when the broker total differed from securities + crypto because Folio previously kept using only the investment-only subtotal for the Portfolio headline.
- Fixed the Scalable helper for current CLI machine JSON shaped as `{ ok, command, data }`, including the nested broker `result` envelope.
- Fixed import failures being communicated only through an easy-to-miss transient Toast; Settings now keeps the latest import success or error visible.

## [0.13.0.beta] - 2026-10-03

### Added
- Secure **Scalable Capital read-only snapshots** using Scalable's official CLI as the broker data source.
- Settings → Connections → Scalable Capital import/replace/disconnect controls with no username, password, 2FA, OAuth-token, account-ID, or portfolio-ID fields in Folio.
- A local helper, `tools/scalable_snapshot.py`, that executes only `sc broker overview --json` and `sc broker holdings --json`, strips account/portfolio identifiers, and writes a minimal owner-only snapshot.
- AES-256-GCM encrypted Scalable snapshot storage backed by a non-exportable Android Keystore key.
- Scalable broker-reported portfolio/holding values in Portfolio and the investment widget when a snapshot is active.

### Changed
- While a Scalable snapshot is active, Scalable's **securities + crypto valuation** is authoritative for Folio Investments; Scalable broker cash/credit stays separate so Folio does not double-count cash.
- Home net worth and the widget use the same Scalable investment value as Portfolio.
- Scalable mode removes the misleading Yahoo **Refresh** action; a newer exact broker snapshot is imported from Settings instead.
- Android cleartext network traffic is disabled.
- Full local-data reset also deletes the encrypted Scalable snapshot and its Keystore key.
- README and SECURITY.md now document the read-only trust boundary and the temporary plaintext-snapshot deletion step.

### Security
- The Scalable helper uses `subprocess.run([...], shell=False)` and exposes no generic broker-command interface.
- The helper never runs login or write commands and CI fails if additional broker commands are added.
- The imported snapshot is allowlisted/sanitized before encryption and is intentionally excluded from Folio portable backups.
- Scalable snapshot filename patterns are ignored by Git to reduce accidental publication risk.
- CI uses only fabricated fixtures and verifies that account/portfolio identifiers cannot enter the sanitized snapshot.

## [0.12.0.beta] - 2026-10-02

### Added
- Scalable-style portfolio performance metrics: **Portfolio value**, **Invested**, **Total return (€)**, and **Time-weighted return (%)**.
- A time-weighted return calculation that separates market performance from later contributions, so adding money does not create a fake gain or loss.
- The RETURN chart now represents absolute investment return in euros rather than a deposit-distorted percentage curve.

### Changed
- Portfolio performance now follows one shared valuation snapshot across the headline, VALUE chart endpoint, RETURN chart, and performance card.
- The Portfolio headline shows absolute total return alongside the time-weighted percentage return.
- Portfolio intelligence is now **Portfolio performance** and uses the same total-return source as the main headline.
- Contributions remain visible separately as cumulative invested capital instead of being mixed into performance.

### Fixed
- Fixed a current-day valuation bug where Folio could use an Oct 2 quote together with an Oct 1 valuation date when daily history had not caught up yet.
- Fixed purchases made on the current day being counted in invested capital but temporarily valued as zero, which could make a positive portfolio appear negative.
- Fixed today’s Portfolio VALUE endpoint omitting a same-day purchase when the stored quote was newer than the latest historical close.
- Added regression coverage for deposits not creating performance, time-weighted compounding across contribution periods, absolute-return history, and the current-day quote/date mismatch.

## [0.11.3.beta] - 2026-10-02

### Changed
- Exact ETF/stock valuation now uses only a **current broker-unit total explicitly confirmed by the user**.
- Legacy/inferred transaction units remain available for purchase-history estimates but are no longer allowed to silently replace the broker total or label the holding as exact.
- New purchases invalidate the confirmed broker-unit snapshot so stale quantities cannot keep driving current valuation.
- Fund-like Yahoo market resolution now tries **ISIN matches before cached ticker symbols**, preventing a previously mis-resolved ETF ticker from staying sticky forever.
- When tracked market history and a cached quote share the same date, Folio now prefers the freshly resolved tracked history instead of the stale cached quote.

### Fixed
- Fixed holdings such as multi-month MSCI ETF purchases showing a partial/inferred unit total as **Exact from owned units**.
- Fixed transaction-derived units overriding a user-confirmed broker quantity.
- Fixed recurring/manual contribution updates mutating the broker-unit snapshot behind the user’s back.
- Fixed the widget using ambiguous legacy owned-unit data as an exact investment value.
- Existing pre-0.11.3 owned-unit values are intentionally treated as unconfirmed; re-enter the current broker total once to restore exact broker-style valuation.

## [0.11.2.beta] - 2026-10-02

### Changed
- Portfolio **Contributions** now shows a running cumulative total by month, while each activity card still shows that month’s own total.
- Investment activity month headers now show both **MONTH TOTAL** and the cumulative contributed amount through that month.
- Portfolio value history is reconstructed from individual purchase lots instead of treating the full holding cost basis as if it existed from the first purchase date.

### Fixed
- Fixed September + October purchases collapsing into one incorrect portfolio curve.
- Fixed later-month contributions being backdated into the first invested month on the VALUE graph.
- Fixed estimated portfolio value applying the first purchase’s price ratio to the entire multi-month cost basis.
- Fixed stale holding-level owned units overriding the sum of complete per-purchase units.
- Fixed per-lot value history using transaction units with non-EUR market history.
- Added regression coverage for cumulative monthly contributions, summed purchase units, and per-lot valuation.

## [0.11.1.beta] - 2026-10-02

### Changed
- Portfolio **CONTRIBUTIONS** now plots each month’s own invested total instead of cumulative contributions sampled from market-history dates.
- Investment activity month headers now label the calculated value explicitly as **MONTH TOTAL**.

### Fixed
- Fixed later ETF purchases being backdated into the first invested month in the Portfolio value history.
- Fixed multi-month portfolio history treating the full current holding cost basis as if it existed from the earliest purchase date.
- September and October purchases now contribute to the portfolio curve only from their own purchase dates, while each month keeps its own independent contribution sum.

## [0.11.0.beta] - 2026-10-02

### Added
- A month-grouped **Investment activity** history on Portfolio showing every saved purchase and each month’s contributed total.
- Every investment purchase can now be edited individually — **amount, units, purchase date, and exact HH:mm time** — including purchases created by recurring investment rules.
- Investment transactions now store their own purchase time instead of relying only on holding-level metadata.

### Changed
- The holding-level “First purchase” editor now edits the earliest real purchase transaction instead of separate metadata.
- Adding the same ETF again keeps the earliest transaction as the holding’s first-purchase timestamp while preserving the later purchase as its own transaction.
- Monthly investment totals and remaining-budget accounting now use all investment transactions in their actual purchase month.
- Editing a recorded recurring purchase changes only that historical purchase; the recurring amount/day for future months stays unchanged.
- Beta version advanced to `v0.11.0.beta` because per-purchase history editing is a new user-facing capability.

### Fixed
- Fixed investments entered with a September purchase date being recorded in October simply because they were added to Folio in October.
- Existing initial purchases migrate from the holding purchase date/time already saved in Folio so historical monthly totals can recover automatically.
- Fixed manual and initial purchases being omitted from monthly invested accounting paths that previously considered recurring purchases only.
- Fixed adding a later purchase of an existing ETF overwriting the holding’s original first-purchase timestamp.

## [0.10.0.beta] - 2026-10-02

### Added
- The home-screen widget now follows Android light/dark appearance with dedicated day/night background, foreground, and muted-text colors.

### Changed
- Widget colors now come from Android configuration-aware resources instead of being snapshotted from the process theme at render time.
- Beta version advanced to `v0.10.0.beta` because widget theme support is a new user-facing capability.

### Fixed
- Fixed recurring ETF purchases leaving an existing exact broker-owned unit override stale after the contribution was applied.
- Fixed recurring ETF purchases calculating units from non-EUR quotes as though the quote were denominated in euros.
- Undoing a recurring ETF purchase now reverses the exact-unit adjustment when Folio had applied one.

## [0.9.0.beta] - 2026-10-02

### Added
- The home-screen widget can switch between **Net worth**, **Investments**, and **Cash left**.
- Compact up/down controls cycle the widget metric without opening Folio.

### Changed
- Simplified the widget to one focused value at a time instead of a combined balance card with chart and monthly-change text.
- Widget investment valuation prefers broker-owned units with the latest EUR market price when available.
- Beta version advanced to `v0.9.0.beta`.

### Fixed
- Fixed widget net worth using the legacy total-balance path that could make investments appear counted twice.
- Fixed the widget showing a different investment/net-worth calculation from the main app.
- Removed the legacy widget sparkline/history path that could disagree with current finance state.

## [0.8.5.beta] - 2026-10-02

### Fixed
- Corrected the widget net-worth double-counting bug found after `v0.8.4.beta`.
- This patch line is superseded by `v0.9.0.beta`, where the new switchable widget interaction is versioned as a feature release.

## [0.8.4.beta] - 2026-10-02

### Added
- A clear **Add contribution now** action inside each investment holding.
- Optional **Units bought** input when recording a contribution so ETF/stock owned units can stay synchronized with the brokerage quantity.

### Changed
- Home **INVESTED** now means money actually contributed (portfolio cost basis), while the Portfolio headline continues to show live/estimated market value.
- The holding screen explicitly separates a contribution made now from a recurring monthly investment schedule.
- When exact units are already tracked, Folio asks for the newly bought units before recording a new contribution so the displayed ETF market value does not silently stay stale.
- Beta version advanced to `v0.8.4.beta`.

### Fixed
- Fixed adding money to an ETF without increasing the Home **INVESTED** amount.
- Fixed direct contributions not updating an existing exact owned-unit quantity.
- Fixed investment contributions being able to exceed Available cash and silently create an inconsistent balance.
- Clarified that creating a recurring investment rule does not itself record an immediate investment contribution.

## [0.8.3.beta] - 2026-10-02

### Added
- Clear labeled **Home / Money / Portfolio** bottom navigation while keeping horizontal swipe gestures.
- Inline recurring status on the Money page with simple states such as due now, upcoming, done this month, or waiting for cash.
- Recurring investment controls directly inside each holding's focused details sheet.

### Changed
- Money is now one primary screen instead of an overview that opens a second full money manager inside a bottom sheet.
- Portfolio no longer opens a second full portfolio manager inside another sheet.
- The main money language is simplified to **Available cash** and **Left to plan**.
- Salary, bills, savings rules, recurring investments, spending budgets, and expenses are managed directly from Money.
- Recurring bills, savings, and investments now use actual available cash instead of being silently blocked by an internal assigned-income envelope.
- Settings and recurring forms use shorter, clearer language.
- Market-price refresh status and recurring-money status are now separate so unrelated messages do not appear in the wrong section.
- Beta version advanced to `v0.8.3.beta`.

### Fixed
- Fixed recurring bills, savings, and investments appearing broken when enough real cash existed but the monthly planning envelope was short.
- Fixed recurring processing being skipped when a best-effort market-price refresh failed.
- Fixed **Run now** doing nothing when automatic recurring was disabled.
- Removed nested manager-on-manager popup flows that made navigation and editing difficult to understand.
- Updated release validation to protect labeled primary navigation and the simplified money semantics instead of enforcing the previous hidden-navigation wording.

## [0.8.2.beta] - 2026-09-11

### Added
- Adaptive semantic finance colors: positive performance/change is green, negative performance/change is red, with separate light- and dark-mode tones defined centrally in the theme.
- Portfolio and net-worth charts now use a subtle semantic area tint to make direction easier to read without turning Folio into a dense trading dashboard.
- Portfolio **RETURN** mode now shows a real zero baseline and signed inspected values, so positive and negative performance are visually distinguishable.
- Spending-category rows now include compact proportional bars to show which categories dominate the month at a glance.
- Individual holdings now expose their tracked percentage return alongside allocation/status metadata and tint the current value by gain/loss state.

### Changed
- Portfolio headline gain/loss, market growth, best/lowest return, holdings, and trend charts now use the same centralized semantic color rules.
- Month-over-month spending change is red when spending increased and green when spending decreased; ordinary expense values remain neutral.
- Green/red are intentionally reserved for financial change and performance. Balances, contributions, savings, and normal spending values remain neutral to keep the visual language meaningful.
- Swipe navigation remains **Money ← Home → Portfolio**, but no pager dots or bottom navigation chrome are rendered at all.
- Beta version advanced to `v0.8.2.beta`.

### Fixed
- Fixed the three-dot pager indicator briefly appearing while vertically scrolling because a tiny horizontal gesture component could mark the pager as scrolling.
- Removed the pager-indicator implementation entirely instead of trying to hide it with timing or gesture thresholds.
- Kept all new gain/loss colors theme-owned instead of hardcoding per-screen color literals.

## [0.8.1.beta] - 2026-09-11

### Added
- Exact fractional **units owned** can now be entered for new investments and corrected later for existing holdings.
- Unit-based current valuation uses the complete owned quantity multiplied by the freshest available EUR market quote, with a visible **Exact from owned units** / **Estimated** status per holding.
- A persisted root-navigation state remembers whether the user was on Money, Home, or Portfolio across app locking and process recreation.
- Regression tests protect EUR unit valuation, non-EUR fallback behavior, and incomplete-unit fallback behavior.

### Changed
- The app theme now establishes its foreground color at the root Material surface, so default text and icons inherit the active light/dark color scheme instead of relying on per-screen color overrides.
- The three-dot swipe indicator is transient and only appears while the pager is moving; it no longer sits permanently over page content.
- Portfolio totals, Home net worth, portfolio intelligence, the primary portfolio screen, and the detailed portfolio manager all use the same v0.8.1 valuation path.
- Folio refreshes current market quotes when the app opens, while still keeping manual Refresh controls.
- Market-symbol resolution is generic: exchange-aware stored tickers plus Yahoo search by ISIN/name replace application-code mappings for individual securities.
- The market-data User-Agent now uses the actual app version from `BuildConfig` instead of a stale release string.
- Beta version advanced to `v0.8.1.beta`.

### Fixed
- Fixed dark mode rendering major text and icons in black on a near-black background.
- Fixed returning from the background, unlocking Folio, and being sent back to Home instead of the root page that was open before locking.
- Fixed the pager indicator overlapping content such as the “Where did my money go?” section.
- Fixed portfolio values continuing to use purchase-amount growth estimates even when a complete owned-unit quantity is available.
- Fixed the clean investment flow dropping the unit quantity that the underlying finance model already supports.
- Removed the security-specific MSCI/SCWX market-symbol hardcoding and replaced it with reusable metadata/search-based resolution.
- Kept non-EUR and incomplete-unit holdings explicitly estimated instead of silently treating a foreign-currency or partial-unit calculation as an exact euro value.

## [0.8.0.beta] - 2026-09-10

### Added
- A new **Spendable now** money value for the liquid amount Folio treats as usable today, separated visually and conceptually from monthly budgeting.
- A new **Unassigned this month** planning value: expected income minus planned bills, investments, spending budgets, and savings. It deliberately remains different from real spendable money when appropriate.
- A simplified Money overview with a direct explanation of both values, month-plan arithmetic, savings totals, and access to the full detailed money manager without removing existing controls.
- Interactive net-worth history with `1M`, `3M`, `1Y`, and `ALL` ranges plus touch/drag point inspection.
- A **Where did my money go?** monthly category breakdown with category shares and comparison against the previous month.
- Portfolio graph modes for **VALUE**, **RETURN**, and **CONTRIBUTIONS**.
- Portfolio intelligence for contributed amount, market growth, largest-position concentration, and best/lowest tracked return.
- Per-holding allocation percentages.
- Exact local investment purchase **date and time** metadata (`YYYY-MM-DD` + `HH:mm`) with backward-compatible reading of older date-only records.
- A v0.8 investment creation sheet that accepts the exact purchase time while preserving ISIN lookup, ticker/exchange metadata, CS2 asset support, and market tracking.
- A purchase timestamp editor for existing holdings. Daily historical market graphs remain daily-close based and do not pretend the stored clock time is an execution-grade intraday quote.
- Portable local **Export Folio / Restore Folio** backup files with format/version validation and a user-visible last-export timestamp.
- An AndroidX Room safety-mirror database (`folio_v08.db`) that automatically stores a complete transactional local snapshot after finance changes while SharedPreferences remains the beta source of truth during migration.
- Finance unit tests for February/day-31 clamping, end-of-month salary attribution, monthly planning semantics, and savings commitment behavior.

### Changed
- Folio now uses horizontal swipe navigation as the primary shell: **Money ← Home → Portfolio**. The persistent bottom navigation bar is removed and replaced by a subtle three-dot page indicator.
- Home is more focused: oversized net worth, interactive history, Spendable/Savings/Invested totals, one Unassigned-this-month block, spending breakdown, and recent activity.
- Portfolio creation now asks for purchase time instead of recording date alone.
- Existing detailed Money and Portfolio managers remain available behind explicit management actions so advanced recurring, ISIN, notes, tags, savings, and editing features are not lost in the cleaner primary screens.
- Automatic Room safety snapshots are intentionally separate from user-exported backups; they never change the **Last exported backup** label.
- CI now runs beta and Play unit tests before building both debug APK variants.
- Beta version advanced to `v0.8.0.beta` with the standardized PIX beta naming convention.

### Fixed
- Fixed the confusing use of “cash” / “available cash” for two fundamentally different concepts. Real liquid money is now **Spendable now** and monthly budget capacity is **Unassigned this month**.
- Fixed Home implying that Folio's internal liquid balance was necessarily a separate bank-account balance.
- Fixed investment history lacking an exact purchase clock time.
- Fixed newly created investments only capturing the purchase date by adding an explicit `HH:mm` field in the v0.8 creation flow.
- Fixed the stale README beta source and old beta-tag example.
- Added release validation to catch missing v0.8 screens, backup/Room infrastructure, purchase timestamps, graph modes, money semantics, and finance tests before release.

## [0.7.4.beta] - 2026-09-09

### Added
- A true salary-funded monthly envelope that calculates **Available this month** from income assigned to the budget month minus recurring bills, investments, savings, and reserved spending budgets.
- Recurring monthly savings rules with editable destination, amount, and day; for example €500 into savings on the 1st.
- Editable monthly spending budgets that reserve money without pretending it has already been spent; for example a €300 Food budget.
- Edit and delete actions for expenses, recurring salary, recurring bills, recurring investments, spending budgets, and monthly savings rules.
- A dedicated widget-refresh helper that re-renders the Glance widget after local finance mutations and background recurring processing.

### Changed
- Available Cash is no longer the raw account cash balance; the large Money value is the unallocated amount inside the selected salary-funded monthly envelope.
- A salary received near the end of one month can fund the following month while keeping its real receive date; for example a September 28–30 salary can fund October.
- Automatic bills, recurring investments, and recurring savings only run against income actually assigned to that budget month, so old carry-over cash cannot silently fund the next month.
- Money automatically opens the next funded budget month when the setup month has no assigned income but the following month does.
- Folio month navigation, Home history, recent activity, and the widget sparkline now start at September 2026.
- The salary editor defaults to day 30, which is clamped to the last valid day of shorter months and can still be changed manually.
- Beta version advanced to `v0.7.4.beta`.

### Fixed
- Fixed Available Cash showing €0 even when a future budget month had a €2,404 salary and planned allocations.
- Fixed monthly food or other category budgets not reducing the truly available amount until after the money had already been spent.
- Fixed previous-month account cash being mistaken for money available in the next budget month.
- Fixed recurring outflows being able to post before the new month had actually been funded by its assigned salary.
- Fixed the widget not reliably refreshing after items were added, edited, deleted, or processed by background automation.
- Fixed expenses and recurring money rules being add-only by giving existing rows clear edit and delete paths.
- Removed pre-September 2026 months from active Money navigation and visible Home/widget history.

## [0.7.3.beta] - 2026-09-09

### Added
- A combined Portfolio graph that adds every investment together from its own purchase date, so the curve shows total invested wealth rather than only one holding at a time.
- Robust Yahoo symbol resolution for exchange-traded holdings using known ISIN listings, exchange-aware ticker suffixes, and Yahoo search fallback.
- A direct mapping for the Scalable MSCI AC World Xtrackers UCITS ETF (ISIN `LU2903252349`) to its Xetra/Yahoo symbol `SCWX.DE`.
- The resolved market symbol is shown beside the data source inside each investment detail sheet for easier debugging.

### Changed
- Bottom navigation is now a minimal three-dot pager indicator with no text or page icons; Home remains the center dot.
- The locked screen uses a larger title and a high-contrast, full-width Unlock Folio button.
- Update notes are always displayed automatically when a beta update is available instead of being hidden behind a secondary toggle.
- The updater preserves GitHub release-note Markdown so Added / Changed / Fixed can be rendered correctly in-app.
- Beta version advanced to `v0.7.3.beta`.

### Fixed
- Fixed the Scalable MSCI AC World investment failing to build a graph when an ambiguous or incomplete ticker was stored.
- Fixed European ETF tracking being able to resolve the wrong security when a short ticker was also used by a US-listed company.
- Fixed the Portfolio screen having no combined value-history graph across NVIDIA, the Scalable MSCI AC World ETF, and other holdings.
- Fixed the navigation bar being visually heavier than the requested minimalist pager-dot design.
- Fixed the app-lock Unlock action being too subtle to see clearly.
- Fixed What's new appearing empty because the beta updater removed Markdown headings before the release-note renderer could parse them.

## [0.7.2.beta] - 2026-09-09

### Added
- A permanent Monthly Salary section in Money, with a large salary amount, payday, budget-month behavior, and a clear Add Monthly Salary action when none exists.
- An Existing Saved Balance action for the Emergency Fund so savings from before Folio can seed the milestone without reducing current cash or counting as this month's new savings.
- Purchase-date metadata for investments, stored independently from the legacy transaction model for backward compatibility.
- Key-free historical market tracking for stocks, ETFs, funds, bonds, and indexes through Yahoo Finance's public chart surface.
- Per-investment value history from the purchase date, with current value, gain/loss, percentage change, source/delay label, and a value graph.
- Purchase-date editing for existing holdings so old portfolio entries can be upgraded without recreating them.

### Changed
- Home now uses purchase-date-tracked portfolio values in the displayed portfolio total and net worth whenever market history is available.
- Portfolio entry is simplified to what was paid plus purchase date; the old share price is no longer required for the value graph.
- Portfolio tracking clearly labels market data as indicative and reports exchange delay when Yahoo provides it instead of claiming execution-grade realtime data.
- Financial typography is larger again: 64sp net worth on Home, 58sp available cash in Money, 58sp portfolio total, 48sp investment values, and larger section/list text throughout.
- Money was reorganized so Monthly Salary appears immediately after Available Cash, before Savings and the month plan.
- Beta version advanced to `v0.7.2.beta`.

### Fixed
- Fixed Monthly Salary being technically present only deep in recurring income and therefore appearing to have disappeared.
- Fixed the inability to enter Emergency Fund money that was already saved before the current month.
- Fixed portfolio totals remaining near cost basis when a holding had no manually entered units by deriving the tracked value from purchase-date price movement.
- Fixed investment history requiring manual price entries by loading historical daily closes automatically from the purchase date.

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
