# Folio Android

A minimal, local-first personal-finance companion for Android. Folio is not a brokerage and does not place trades. It tracks spendable money, monthly plans, spending, savings, investments, recurring money, and net worth in one quiet interface.

**Current beta source:** `0.11.4.beta`

## What Folio tracks

- **Available cash** — liquid money Folio treats as usable today
- **Invested** — money actually contributed to holdings; current market value is shown separately in Portfolio
- **Left to plan** — expected monthly income that has not yet been assigned to bills, investments, spending budgets, or savings
- Net worth across spendable money, savings, and investments
- Expenses grouped by month and category, including a “Where did my money go?” breakdown
- Monthly category budgets
- Recurring salary / income, bills, savings, and investments
- Salary attribution to the same or following budget month
- Emergency fund, crash reserve, and general savings
- Investments added manually or resolved from an **ISIN** with OpenFIGI v3
- Exact **date and HH:mm time on every investment purchase transaction**, including recurring purchases
- Confirmed current **broker units** for exact unit-based valuation when a EUR market quote is available; inferred transaction units stay estimated
- Month-grouped investment purchase history with editable amount, units, date, and time for every entry, plus explicit monthly and cumulative contribution totals
- Direct per-holding **Add contribution now** flow with optional units bought
- Portfolio **Value / Return / Contributions** graph modes, with Contributions showing cumulative invested totals across months
- Portfolio concentration, best/lowest return, contribution, and market-growth summaries
- Interactive net-worth history with **1M / 3M / 1Y / ALL** ranges
- Transaction timeline, monthly comparison, milestones, annual review, and growth projections
- Automatic market tracking for supported exchange-traded assets plus Steam Community Market support for CS2 assets
- Optional biometric / device-credential app lock that restores the last root page after unlocking
- Minimal 3×1 widget with switchable Net worth / Investments / Cash left metrics that follows Android light/dark appearance
- Portable local backup and restore

Fresh installs start empty. Folio does not seed demo money or fake chart data.

> **0.11.3 migration:** older builds mixed user-entered owned units with automatic transaction updates. After upgrading, open each holding that needs exact broker-style valuation and confirm its current **broker units** once. Inferred/legacy units remain estimated until then.

## Versioning

Folio uses feature-aware beta versioning:

- **New user-facing feature or capability:** bump the minor version and reset patch to zero, for example `0.8.5.beta → 0.9.0.beta`.
- **Bug fix, polish, or refinement only:** keep the same minor line and bump the patch, for example `0.9.0.beta → 0.9.1.beta`.
- The next feature after the `0.9.x.beta` line becomes `0.10.0.beta`.

Patch releases must not introduce a new user-facing capability. CI validates this rule against the current changelog entry.


## Money model

Folio keeps current money and planning separate:

```text
Available cash
= liquid money available today

Left to plan
= expected income for the selected budget month
  - planned bills
  - planned investments
  - spending budgets
  - planned savings
```

They are allowed to be different. **Available cash** reflects recorded real money movements. **Left to plan** is a planning number, not a second cash balance.

Savings are not spendable, but they still count toward net worth. Investments are tracked separately and also count toward net worth at their tracked market value when available.

## Investment identity, units, and valuation

The Android app can resolve an ISIN through OpenFIGI v3:

```text
ISIN → OpenFIGI mapping → matching listing → tracked holding
```

Folio stores the ISIN together with the selected FIGI, ticker, exchange code, name, and inferred holding type. An OpenFIGI API key is not bundled or required for normal use; anonymous public requests use OpenFIGI's lower rate limit.

Market-symbol resolution is generic. Folio uses stored/exchange-aware ticker metadata and Yahoo search by ISIN/name instead of maintaining security-specific mappings in application code.

For exchange-traded assets, Folio stores a local purchase date and `HH:mm` time on every transaction. The Portfolio activity section groups those transactions by their real month, and each purchase — initial, manual, or recurring — can be corrected individually without changing the future recurring rule. Historical value graphs still use daily market closes rather than pretending that daily data represents an exact execution price at the stored clock time.

For current value, Folio prefers:

```text
owned units × freshest available EUR market quote
```

when the complete owned-unit quantity is known. Existing holdings can be updated with the exact fractional quantity shown by the brokerage. When Folio does not know complete units, or the resolved quote is not EUR, the value is explicitly labelled **Estimated** instead of silently mixing currencies or pretending an estimate is exact. A public market quote can still differ slightly from a brokerage quote because of source timing, exchange delay, spread, or the brokerage's own display rules.

## Navigation and visual direction

- visible labeled bottom navigation: **Home / Money / Portfolio**
- horizontal swipe navigation still works between the three primary pages
- no transient pager dots
- last root page is persisted and restored after app lock / process recreation
- theme-owned foreground/background colors in both system light and dark mode
- adaptive semantic finance colors: green for positive performance/change, red for negative performance/change, neutral for balances, spending, and contributions
- net-worth and portfolio charts use a subtle semantic area tint; Return mode includes a visible zero baseline and signed values
- spending-category rows include compact proportional bars while keeping the palette monochrome
- warm off-white / near-black surfaces
- large numbers and generous whitespace
- no brokerage-style Buy / Sell controls
- no dense dashboard-card grid
- monochrome Noto Emoji category glyphs
- Nothing-inspired Folio `F.` adaptive icon

## Data and backups

Folio remains local-first. User finance data is not tied to an account.

The v0.8 line adds two protection layers:

1. **Portable backup / restore** — export a local Folio JSON backup and import it later.
2. **Room migration safety mirror** — after finance changes, Folio mirrors a complete local snapshot into a Room database while the existing SharedPreferences model remains the beta source of truth.

This staged migration avoids replacing the existing data store in one risky step. The automatic Room mirror is not shown as a user-exported backup; “Last exported backup” only changes when the user explicitly exports a file.

## Stack

- Kotlin
- Jetpack Compose
- Material 3 foundations with custom Folio components
- OpenFIGI v3 via a small `HttpURLConnection` client
- AndroidX Biometric + device credential fallback
- AndroidX Room migration/safety snapshot layer
- AndroidX WorkManager recurring processing
- Glance app widgets
- SharedPreferences JSON source of truth during the v0.8 beta migration
- GitHub Actions for unit tests, CI builds, signed beta releases, APK signature verification, and checksums

## Repository

The in-app beta updater points to the public repository:

```text
https://github.com/0xpix/folio-app
```

No GitHub token is embedded in the APK.

## Beta signing

Create and keep one dedicated beta key for all future Folio beta updates:

```bash
keytool -genkeypair \
  -v \
  -keystore folio-beta.jks \
  -alias folio-beta \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

GitHub Actions secrets:

```text
FOLIO_BETA_KEYSTORE_BASE64
FOLIO_BETA_KEYSTORE_PASSWORD
FOLIO_BETA_KEY_ALIAS
FOLIO_BETA_KEY_PASSWORD
```

## Publish a beta

The beta version in `app/build.gradle.kts`, README, and CHANGELOG must agree. Pull requests run static validation, unit tests, and both Android debug builds. After a validated beta is merged into `main`, the release workflow detects the newest missing beta in the changelog, builds and verifies the signed APK, creates its SHA-256 checksum, and publishes the matching GitHub prerelease. Existing releases are skipped safely.

Tag-triggered release runs remain supported for recovery/manual workflows.

## Build locally

Use JDK 17 and Android SDK/API 37:

```bash
gradle :app:testBetaDebugUnitTest :app:testPlayDebugUnitTest \
  :app:assembleBetaDebug :app:assemblePlayDebug
```

Beta debug APK:

```text
app/build/outputs/apk/beta/debug/app-beta-debug.apk
```

## Data notes

Recurring actions are month-aware. Marking or automatically processing salary, a bill, or a recurring investment writes a local ledger/transaction entry so previous months are preserved instead of only remembering the latest toggle state.

Investment contributions are stored separately from the holding total. Purchase timestamps and optional broker-reported current units are separate tracking metadata so older Folio data stays readable during the v0.8 migration. Both are included in Folio's portable backup because they live in the investment-tracking preference store.
