# Folio Android

A minimal personal-finance companion for Android. Folio is not a brokerage and does not place trades. It gives you the same kind of overview as the Folio website: your balance, monthly expenses, investments, and recurring payments in one quiet interface.

**Current beta:** `0.1.0.beta`

## v0.1

- Minimal Home overview with total balance and monthly change
- Monthly income, expenses, investments, and payments summary
- Expense categories with monochrome **Noto Emoji** glyphs
- Add-expense bottom sheet
- Read-only investment total, trend, allocation, and portfolio list
- Upcoming / completed monthly payments
- Add-payment bottom sheet and tap-to-mark-paid flow
- Local persistence for expenses and payments
- System light / dark theme
- Nothing-inspired segmented Folio `F.` adaptive icon with themed monochrome support
- Minimal **3×1 balance widget**
- GitHub beta and Play build flavors
- Settings / More → beta updater
- GitHub prerelease discovery, APK checksum validation, and Android install handoff

The bundled values are demo data for the first beta. They are intentionally easy to replace once Folio website sync/import is wired in.

## Visual direction

- warm off-white / near-black surfaces
- large numbers, very little decoration
- no brokerage-style Buy / Sell controls
- no dense dashboards
- no colored emoji icons
- Noto Emoji is requested as a Google downloadable font and rendered into monochrome bitmaps
- restrained color only for positive/negative financial state
- native Android edge-to-edge behavior

## Stack

- Kotlin
- Jetpack Compose
- Material 3 foundations with custom Folio components
- Glance app widgets
- SharedPreferences JSON for the first beta data layer
- GitHub Actions for CI and signed beta releases

## Repository

The in-app beta updater is already configured for:

```text
https://github.com/0xpix/folio-android
```

The repository needs to be **public** for anonymous GitHub release checks from the beta APK. Do not embed a GitHub token in the app.

### Create and push the repo

With GitHub CLI authenticated:

```bash
./scripts/bootstrap_repo.sh
```

Or:

```bash
git init
git add .
git commit -m "feat: bootstrap Folio Android beta"
git branch -M main
gh repo create 0xpix/folio-android --public --source=. --remote=origin --push
```

## Beta signing

Create a dedicated beta key:

```bash
keytool -genkeypair \
  -v \
  -keystore folio-beta.jks \
  -alias folio-beta \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Add these GitHub Actions secrets:

```text
FOLIO_BETA_KEYSTORE_BASE64
FOLIO_BETA_KEYSTORE_PASSWORD
FOLIO_BETA_KEY_ALIAS
FOLIO_BETA_KEY_PASSWORD
```

Linux/macOS:

```bash
base64 -w 0 folio-beta.jks > folio-beta.jks.base64
```

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("folio-beta.jks")) | Set-Content -NoNewline folio-beta.jks.base64
```

## Publish a beta APK

```bash
git tag v0.1.0.beta.1
git push origin v0.1.0.beta.1
```

The workflow validates the project, builds and signs the beta APK, verifies its signature, writes a SHA-256 checksum, and publishes both in a GitHub prerelease.

The beta app reads the full Releases list—including prereleases—so **More → Check for beta** can see `v0.1.0.beta.2`, `v0.1.0.beta.3`, and later builds.

## Build locally

Use JDK 17 and Android SDK/API 37:

```bash
gradle :app:assembleBetaDebug
```

APK:

```text
app/build/outputs/apk/beta/debug/app-beta-debug.apk
```

## Widget

The first widget is deliberately simple:

```text
Folio                       ╭──── trend ────╮
€12,480                     ╰───────────────╯
+ €320 this month
```

It follows light/dark mode, uses the same warm surfaces as the app, opens Folio on tap, and refreshes when local finance data changes.

## Next

- sync/import the existing Folio website data
- edit income, cash balance, and investment holdings from the app
- add backup/export
- add a compact 2×2 expense widget only if it stays visually quiet
- Play Store release after the beta data model is stable
