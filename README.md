# Folio Android

A minimal personal-finance companion for Android. Folio is not a brokerage and does not place trades. It tracks your cash, monthly spending, budgets, investments, recurring money, and net worth in one quiet interface.

**Current beta source:** `0.3.0.beta`

## What Folio tracks

- Cash balance and net worth
- Expenses grouped by month and category
- Minimal monthly category budgets
- Recurring salary / income
- Recurring bills
- Recurring investments linked to a holding
- Investments added manually or resolved from an **ISIN** with OpenFIGI v3
- Investment contribution history
- Net-worth and portfolio history
- Monthly overview: income, expenses, payments, invested, left
- Upcoming salary, bills, and investment contributions on Home
- Optional biometric / device-credential app lock
- Minimal 3×1 balance widget

Fresh installs start empty. Folio does not seed demo money or fake chart data.

## Investment identity

The Android app can resolve an ISIN through OpenFIGI v3:

```text
ISIN → OpenFIGI mapping → matching listings → user selects listing
```

Folio stores the ISIN entered by the user together with the selected FIGI, ticker, exchange code, name, and inferred holding type. An OpenFIGI API key is not bundled or required for normal use; anonymous public requests use OpenFIGI's lower rate limit.

## Visual direction

- warm off-white / near-black surfaces
- large numbers and generous whitespace
- no brokerage-style Buy / Sell controls
- no dense dashboards
- monochrome Noto Emoji category glyphs
- restrained color only for positive/negative financial state
- Nothing-inspired Folio `F.` adaptive icon
- fixed bottom-navigation geometry; selected tabs do not jump

## Stack

- Kotlin
- Jetpack Compose
- Material 3 foundations with custom Folio components
- OpenFIGI v3 via a tiny `HttpURLConnection` client
- AndroidX Biometric + device credential fallback
- Glance app widgets
- SharedPreferences JSON data layer for beta
- GitHub Actions for CI and signed beta releases

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

## Publish the next beta

After pushing the source:

```bash
git tag v0.3.0.beta.1
git push origin v0.3.0.beta.1
```

The tag run builds and signs the beta APK, verifies its signature, creates a SHA-256 checksum, and publishes both to a GitHub prerelease.

## Build locally

Use JDK 17 and Android SDK/API 37:

```bash
gradle :app:assembleBetaDebug :app:assemblePlayDebug
```

Beta debug APK:

```text
app/build/outputs/apk/beta/debug/app-beta-debug.apk
```

## Data notes

Recurring actions are month-aware. Marking salary received, a bill paid, or a recurring investment executed writes a small local ledger entry. That ledger drives Monthly Overview and preserves older months instead of only remembering the latest toggle state.

Investment contributions are also stored separately from the holding total, which allows each holding to have its own contribution history.
