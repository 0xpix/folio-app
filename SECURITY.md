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

Scalable documents `--local-read-only` as a **local CLI write guard**; it does not reduce the
backend token's permissions. Folio therefore does not rely on that flag alone: the included helper
has no generic command interface and can execute only the four fixed read commands listed below.

The helper in `tools/scalable_snapshot.py` executes only these fixed read commands:

```text
sc broker overview --json
sc broker holdings --json
sc broker cash-breakdown --json
sc broker portfolio-groups --json
```

It uses `subprocess.run([...], shell=False)`. It never executes `sc login`, trade commands, savings-plan mutations, watchlist mutations, or arbitrary user-supplied broker commands.

The helper removes account/portfolio identifiers and writes only the minimum fields Folio needs: broker valuation, broker cash balance, read-only performance values, portfolio-group valuation needed to recover omitted grouped positions, ISIN/name, quantity, holding valuation and quote metadata.

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

## Sparkasse read-only integration

Folio treats Sparkasse/FinTS as a separate **read-only trust boundary**. The Android app never asks
for or stores the user's online-banking login ID, PIN, TAN, IBAN, FinTS endpoint credentials, FinTS
dialog state, or product-registration secret.

The helper in `tools/sparkasse_snapshot.py` runs on the user's own computer and uses
`python-fints`. FinTS clients require a registered product ID, and some banks can require TAN
confirmation even for read operations. The helper therefore prompts locally for PIN/TAN and never
writes either value to disk.

The helper deliberately calls only these read operations:

```text
get_sepa_accounts()
get_balance(account)
get_transactions(account, ...)
```

It does not expose or call transfer, debit, scheduled-payment, standing-order, or other mutation
methods. The generated snapshot contains only the selected account's EUR balance and sanitized booked
transaction fields needed by Folio: booking date, signed amount, merchant/counterparty display text,
and purpose text. Account numbers, IBANs, BICs, login IDs, PINs, TANs, and FinTS session state are not
written to the snapshot.

### Sparkasse snapshot handling

The intermediate `folio-sparkasse.snapshot.json` is plaintext so it can be moved to the phone. It is
excluded by `.gitignore` and should be deleted after import.

After import:

- Folio parses only an allowlist of balance/transaction fields.
- The snapshot is encrypted with AES-256-GCM using a separate non-exportable Android Keystore key.
- The encrypted file stays in Folio's private app directory.
- Android backup remains disabled and Folio's portable backup does not include the Sparkasse snapshot.
- Disconnecting Sparkasse or clearing all Folio data deletes the encrypted snapshot and its Keystore key.
- Sparkasse balance becomes the displayed Available cash / net-worth cash component while connected.
- Booked bank debits are categorized locally on-device; transaction text is not sent to a Folio server.

## Network and telemetry

- Android cleartext network traffic is disabled.
- Folio has no analytics SDK and no crash-reporting SDK.
- The Scalable and Sparkasse snapshot import paths make no network requests.
- The official Scalable CLI and the local FinTS helper, not the Android app, talk to the financial providers.
- Folio does not upload imported Scalable or Sparkasse data to GitHub or any Folio server.

Folio still uses network access for existing non-Scalable features such as market-price refreshes and beta updates. Those paths do not receive the encrypted Scalable snapshot.

## Scalable source of truth

When a valid Scalable snapshot is active, the Portfolio screen is Scalable-only: it uses only broker-returned holdings, broker cash, broker return and broker timestamps. Folio's local holdings, Yahoo/OpenFIGI estimates, local cost basis and purchase history do not participate in the connected Portfolio value.

## Public repository safety

Never commit a generated Scalable or Sparkasse snapshot. The following patterns are ignored:

```text
folio-scalable*.json
*.folio-scalable.json
scalable-snapshot*.json
private-scalable/
folio-sparkasse*.json
*.folio-sparkasse.json
sparkasse-snapshot*.json
private-sparkasse/
```

Do not add real account responses, bank transactions, IBANs, account IDs, portfolio IDs, credentials,
PINs, TANs, tokens, screenshots with personal account identifiers, or copied CLI/FinTS session
material to tests, fixtures, issues, or commits.

## Reporting a vulnerability

Please use a private GitHub Security Advisory for vulnerabilities or accidental secret exposure. Do not post private financial data, signing material, credentials, backup files, Scalable/Sparkasse snapshots, bank transactions, IBANs, account IDs, portfolio IDs, or other sensitive information in public issues.
