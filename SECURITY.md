# Security

Folio does not require a Folio account or backend. App data is stored locally on the device, and the beta updater reads public GitHub release metadata without embedding a GitHub token in the APK.

## Secrets and signing keys

Never commit signing keystores, encoded keystore contents, passwords, API tokens, credentials, `.env` files, or secret-bearing configuration files.

Beta signing material belongs only in GitHub Actions secrets:

- `FOLIO_BETA_KEYSTORE_BASE64`
- `FOLIO_BETA_KEYSTORE_PASSWORD`
- `FOLIO_BETA_KEY_ALIAS`
- `FOLIO_BETA_KEY_PASSWORD`

If a signing key or credential is ever committed or otherwise exposed, treat it as compromised and rotate it rather than relying only on deleting the file.

## Reporting a vulnerability

Please use a private GitHub Security Advisory for vulnerabilities or accidental secret exposure. Do not post private financial data, signing material, credentials, backup files, or other sensitive information in public issues.
