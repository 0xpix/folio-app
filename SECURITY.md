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

## Scalable Capital integration

Folio treats Scalable Capital as a separate **read-only trust boundary**. Folio does **not** implement Scalable login and does not store or request:

- Scalable username or password
- two-factor authentication codes
- OAuth access or refresh tokens
- Scalable CLI session files
- account IDs or portfolio IDs

Authentication is completed by the user with Scalable Capital's official CLI outside Folio:

```bash
sc login --local-read-only
```

The helper in `tools/scalable_snapshot.py` executes only these fixed read commands:

```text
sc broker overview --json
sc broker holdings --json
```

It uses `subprocess.run([...], shell=False)`. It never executes `sc login`, trade commands, savings-plan mutations, watchlist mutations, or arbitrary user-supplied broker commands.

The helper removes account/portfolio identifiers and writes only the minimum fields Folio needs: broker valuation, read-only performance values, ISIN/name, quantity, holding valuation and quote metadata.

## Scalable snapshot handling

The intermediate `folio-scalable.snapshot.json` is plaintext because it must be transferable from the computer running the official CLI to the phone. It is deliberately excluded by `.gitignore`. Delete that file after importing it into Folio.

After import:

- Folio parses only an allowlist of fields and discards unknown metadata.
- The sanitized snapshot is encrypted with AES-256-GCM.
- The AES key is generated in Android Keystore and is non-exportable.
- The encrypted file is stored in Folio's private app directory.
- Android backup is disabled for Folio.
- Folio's portable backup intentionally does **not** include the Scalable snapshot.
- Disconnecting Scalable deletes the encrypted snapshot and its Keystore key.

## Network and telemetry

- Android cleartext network traffic is disabled.
- Folio has no analytics SDK and no crash-reporting SDK.
- The Scalable snapshot import path makes no network requests.
- The official Scalable CLI, not Folio, talks to Scalable Capital.
- Folio does not upload imported Scalable data to GitHub or any Folio server.

Folio still uses network access for existing non-Scalable features such as market-price refreshes and beta updates. Those paths do not receive the encrypted Scalable snapshot.

## Scalable source of truth

When a valid Scalable snapshot is active, Scalable's broker-reported portfolio and holding valuations are authoritative in the Portfolio screen and investment widget. Folio's Yahoo/OpenFIGI estimates are used only when no Scalable snapshot is active.

## Public repository safety

Never commit a generated Scalable snapshot. The following patterns are ignored:

```text
folio-scalable*.json
*.folio-scalable.json
scalable-snapshot*.json
private-scalable/
```

Do not add real account responses, account IDs, portfolio IDs, credentials, tokens, screenshots with personal account identifiers, or copied CLI session material to tests, fixtures, issues, or commits.

## Reporting a vulnerability

Please use a private GitHub Security Advisory for vulnerabilities or accidental secret exposure. Do not post private financial data, signing material, credentials, backup files, Scalable snapshots, account IDs, portfolio IDs, or other sensitive information in public issues.
