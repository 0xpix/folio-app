# Folio v0.3.0 beta

## Added

- ISIN-first investment lookup with OpenFIGI v3
- listing selection with ticker / exchange / FIGI metadata
- minimal monthly category budgets
- full Monthly Overview screen with previous-month navigation
- recurring investments linked to existing holdings
- per-holding investment contribution history
- dedicated net-worth history screen with 1W / 1M / 3M / 1Y / ALL ranges
- upcoming salary, bills, and recurring investments on Home
- biometric or device-credential app lock
- local recurring ledger so historical months remain meaningful

## Changed

- Fresh data stays empty; no demo values or fake charts
- Investment rows open a detail/history sheet instead of exposing Remove directly
- Recurring is now split into Payments / Income / Invest
- Base manifest has Internet permission because ISIN lookup is available in beta and Play flavors
- Default local source version is 0.3.0

## ISIN provider

OpenFIGI is used only for instrument identity / listing metadata. Folio stores the ISIN typed by the user because the free OpenFIGI response does not redistribute third-party proprietary identifiers back in results.
