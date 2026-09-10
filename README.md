# Folio Android

A minimal, local-first personal-finance companion for Android. Folio is not a brokerage and does not place trades. It tracks spendable money, monthly plans, spending, savings, investments, recurring money, and net worth in one quiet interface.

**Current beta source:** `0.8.0.beta`

## What Folio tracks

- **Spendable now** — liquid money Folio treats as usable today
- **Unassigned this month** — expected monthly income that has not yet been assigned to bills, investments, spending budgets, or savings
- Net worth across spendable money, savings, and investments
- Expenses grouped by month and category, including a “Where did my money go?” breakdown
- Monthly category budgets
- Recurring salary / income, bills, savings, and investments
- Salary attribution to the same or following budget month
- Emergency fund, crash reserve, and general savings
- Investments added manually or resolved from an **ISIN** with OpenFIGI v3
- Exact local purchase **date and time** metadata for investments
- Investment contribution history and allocation percentages
- Portfolio **Value / Return / Contributions** graph modes
- Portfolio concentration, best/lowest return, contribution, and market-growth summaries
- Interactive net-worth history with **1M / 3M / 1Y / ALL** ranges
- Transaction timeline, monthly comparison, milestones, annual review, and growth projections
- Automatic market tracking for supported exchange-traded assets plus Steam Community Market support for CS2 assets
- Optional biometric / device-credential app lock
- Minimal 3×1 balance widget
- Portable local backup and restore

Fresh installs start empty. Folio does not seed demo money or fake chart data.

## Money model

Folio deliberately separates two different numbers that older betas called “cash” or “available cash” too loosely:

```text
Spendable now
= liquid money available today

Unassigned this month
= expected income for the selected budget month
  - planned bills
  - planned investments
  - spending budgets
  - planned savings
```

They are allowed to be different. **Spendable now** reflects recorded real money movements. **Unassigned this month** is a planning number and intentionally does not treat old leftover money as new monthly income.

Savings are not spendable, but they still count toward net worth. Investments are tracked separately and also count toward net worth at their tracked market value when available.

## Investment identity and history

The Android app can resolve an ISIN through OpenFIGI v3:

```text
ISIN → OpenFIGI mapping → matching listing → tracked holding
```

Folio stores the ISIN together with the selected FIGI, ticker, exchange code, name, and inferred holding type. An OpenFIGI API key is not bundled or required for normal use; anonymous public requests use OpenFIGI's lower rate limit.

For exchange-traded assets, Folio also stores the user's local purchase date and `HH:mm` time. Historical value graphs currently use daily market closes rather than pretending that daily data represents an exact execution price at the stored clock time.

## Navigation and visual direction

- horizontal swipe navigation: **Money ← Home → Portfolio**
- no permanent bottom navigation bar
- subtle three-dot page indicator
- warm off-white / near-black surfaces
- large numbers and generous whitespace
- no brokerage-style Buy / Sell controls
- no dense dashboard-card grid
- monochrome Noto Emoji category glyphs
- restrained color only for meaningful financial state
- Nothing-inspired Folio `F.` adaptive icon

## Data and backups

Folio remains local-first. User finance data is not tied to an account.

`v0.8.0.beta` adds two protection layers:

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

## Publish the beta

After the v0.8 branch is validated and merged:

```bash
git tag v0.8.0.beta
git push origin v0.8.0.beta
```

The tag run tests and builds the app, signs the beta APK, verifies its signature and version, creates a SHA-256 checksum, and publishes the APK and matching changelog section to a GitHub prerelease.

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

Investment contributions are stored separately from the holding total. Purchase timestamps are separate tracking metadata so older date-only Folio data stays readable during the v0.8 migration.
