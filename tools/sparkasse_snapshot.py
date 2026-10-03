#!/usr/bin/env python3
"""Create a minimal read-only Sparkasse/FinTS snapshot for Folio.

Requires:
    python3 -m pip install fints

A FinTS product ID is required by the German banking industry for client software.
Folio never stores the online-banking PIN or TAN. They are entered interactively
and exist only in this helper process.
"""

from __future__ import annotations

import argparse
import datetime as dt
import getpass
import json
import os
from pathlib import Path
from typing import Any


def clean_text(value: Any, limit: int) -> str:
    if value is None:
        return ""
    text = " ".join(str(value).split())
    return text[:limit]


def finite_number(value: Any, label: str) -> float:
    if hasattr(value, "amount"):
        return finite_number(getattr(value, "amount"), label)
    if value is None:
        raise RuntimeError(f"Missing {label}")
    text = str(value).strip().replace(",", ".")
    # Some money types stringify as "12.34 EUR".
    token = text.split()[0]
    try:
        number = float(token)
    except ValueError as exc:
        raise RuntimeError(f"Invalid {label}") from exc
    if number != number or number in (float("inf"), float("-inf")):
        raise RuntimeError(f"Invalid {label}")
    return number


def currency_of(value: Any, fallback: str = "EUR") -> str:
    currency = getattr(value, "currency", None)
    if currency is None and hasattr(value, "amount"):
        currency = getattr(getattr(value, "amount"), "currency", None)
    return clean_text(currency or fallback, 3).upper()


def transaction_data(transaction: Any) -> dict[str, Any]:
    raw = getattr(transaction, "data", None)
    return raw if isinstance(raw, dict) else {}


def first_text(data: dict[str, Any], *keys: str, limit: int) -> str:
    for key in keys:
        value = data.get(key)
        text = clean_text(value, limit)
        if text:
            return text
    return ""


def booking_date(data: dict[str, Any]) -> str:
    value = data.get("date") or data.get("entry_date") or data.get("booking_date")
    if isinstance(value, dt.datetime):
        return value.date().isoformat()
    if isinstance(value, dt.date):
        return value.isoformat()
    text = clean_text(value, 10)
    if text:
        return dt.date.fromisoformat(text).isoformat()
    raise RuntimeError("Transaction is missing booking date")


def sanitize_transaction(transaction: Any) -> dict[str, Any]:
    data = transaction_data(transaction)
    raw_amount = data.get("amount")
    amount = finite_number(raw_amount, "transaction amount")
    currency = currency_of(raw_amount, clean_text(data.get("currency"), 3) or "EUR")
    if currency != "EUR":
        raise RuntimeError(f"Only EUR Sparkasse transactions are supported, got {currency}")

    merchant = first_text(
        data,
        "applicant_name",
        "payee",
        "recipient_name",
        "sender_name",
        "counterparty_name",
        limit=180,
    )
    purpose_parts = [
        first_text(data, "purpose", limit=220),
        first_text(data, "additional_purpose", limit=120),
        first_text(data, "posting_text", "transaction_details", limit=120),
    ]
    purpose = clean_text(" · ".join(part for part in purpose_parts if part), 360)

    return {
        "booking_date": booking_date(data),
        "amount": amount,
        "currency": currency,
        "merchant": merchant,
        "purpose": purpose,
    }


def build_snapshot(balance: Any, transactions: list[Any]) -> dict[str, Any]:
    raw_amount = getattr(balance, "amount", balance)
    current = finite_number(raw_amount, "balance")
    currency = currency_of(raw_amount, currency_of(balance))
    if currency != "EUR":
        raise RuntimeError(f"Only EUR Sparkasse accounts are supported, got {currency}")

    sanitized = [sanitize_transaction(row) for row in transactions]
    return {
        "format": "folio-sparkasse-snapshot",
        "version": 1,
        "source": "fints",
        "created_at_utc": dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z"),
        "currency": currency,
        "balance": current,
        "available_balance": current,
        "transactions": sanitized,
    }


def resolve(value: str | None, env_name: str, prompt: str, *, secret: bool = False) -> str:
    resolved = (value or os.environ.get(env_name, "")).strip()
    if resolved:
        return resolved
    if secret:
        return getpass.getpass(prompt).strip()
    return input(prompt).strip()


def mask_iban(value: str) -> str:
    compact = "".join(str(value).split())
    if len(compact) <= 8:
        return "••••"
    return compact[:4] + "••••••" + compact[-4:]


def ask_for_tan(client: Any, response: Any) -> Any:
    print()
    print("Sparkasse requires confirmation:")
    challenge = clean_text(getattr(response, "challenge", ""), 500)
    if challenge:
        print(challenge)

    if getattr(response, "decoupled", False):
        input("Confirm in your pushTAN app, then press Enter here: ")
        tan = ""
    else:
        tan = getpass.getpass("TAN: ").strip()
    return client.send_tan(response, tan)


def choose_account(accounts: list[Any], requested_index: int | None) -> Any:
    if not accounts:
        raise RuntimeError("Sparkasse/FinTS returned no SEPA accounts")

    if requested_index is not None:
        if requested_index < 0 or requested_index >= len(accounts):
            raise RuntimeError(f"--account-index must be between 0 and {len(accounts) - 1}")
        return accounts[requested_index]

    if len(accounts) == 1:
        return accounts[0]

    print("Available Sparkasse accounts:")
    for index, account in enumerate(accounts):
        print(f"  [{index}] {mask_iban(getattr(account, 'iban', ''))}")
    while True:
        raw = input("Account index: ").strip()
        if raw.isdigit() and int(raw) < len(accounts):
            return accounts[int(raw)]
        print("Choose one of the listed account indexes.")


def write_snapshot(snapshot: dict[str, Any], output: Path, force: bool) -> None:
    if output.exists() and not force:
        raise RuntimeError(f"{output} already exists; use --force to replace it")
    output.parent.mkdir(parents=True, exist_ok=True)
    temp = output.with_suffix(output.suffix + ".tmp")
    with temp.open("w", encoding="utf-8") as handle:
        json.dump(snapshot, handle, ensure_ascii=False, separators=(",", ":"))
        handle.write("\n")
    os.chmod(temp, 0o600)
    temp.replace(output)
    os.chmod(output, 0o600)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Create a sanitized read-only Sparkasse/FinTS snapshot for Folio."
    )
    parser.add_argument("--blz", help="Sparkasse bank code (or FOLIO_SPARKASSE_BLZ)")
    parser.add_argument("--user-id", help="Online-banking login ID (or FOLIO_SPARKASSE_USER)")
    parser.add_argument("--endpoint", help="FinTS/HBCI endpoint (or FOLIO_SPARKASSE_ENDPOINT)")
    parser.add_argument("--product-id", help="Registered FinTS product ID (or FOLIO_FINTS_PRODUCT_ID)")
    parser.add_argument("--days", type=int, default=90, help="Booked transaction history to fetch (default: 90)")
    parser.add_argument("--account-index", type=int, default=None)
    parser.add_argument("--output", default="folio-sparkasse.snapshot.json")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    if not 1 <= args.days <= 730:
        parser.error("--days must be between 1 and 730")

    try:
        from fints.client import FinTS3PinTanClient, NeedTANResponse
        from fints.utils import minimal_interactive_cli_bootstrap
    except ImportError as exc:
        raise RuntimeError(
            "python-fints is required. Install it with: python3 -m pip install fints"
        ) from exc

    blz = resolve(args.blz, "FOLIO_SPARKASSE_BLZ", "Sparkasse BLZ: ")
    user_id = resolve(args.user_id, "FOLIO_SPARKASSE_USER", "Online-banking login ID: ")
    endpoint = resolve(args.endpoint, "FOLIO_SPARKASSE_ENDPOINT", "FinTS endpoint URL: ")
    product_id = resolve(args.product_id, "FOLIO_FINTS_PRODUCT_ID", "Registered FinTS product ID: ")
    pin = resolve(None, "FOLIO_SPARKASSE_PIN", "Online-banking PIN: ", secret=True)

    if not all((blz, user_id, endpoint, product_id, pin)):
        raise RuntimeError("BLZ, login ID, FinTS endpoint, product ID and PIN are required")

    client = FinTS3PinTanClient(
        blz,
        user_id,
        pin,
        endpoint,
        product_id=product_id,
    )

    minimal_interactive_cli_bootstrap(client)

    today = dt.date.today()
    start = today - dt.timedelta(days=args.days)

    with client:
        while isinstance(client.init_tan_response, NeedTANResponse):
            client.init_tan_response = ask_for_tan(client, client.init_tan_response)

        account = choose_account(list(client.get_sepa_accounts()), args.account_index)

        balance = client.get_balance(account)
        while isinstance(balance, NeedTANResponse):
            balance = ask_for_tan(client, balance)

        transactions = client.get_transactions(
            account,
            start_date=start,
            end_date=today,
            include_pending=False,
        )
        while isinstance(transactions, NeedTANResponse):
            transactions = ask_for_tan(client, transactions)

    snapshot = build_snapshot(balance, list(transactions))
    output = Path(args.output).expanduser().resolve()
    write_snapshot(snapshot, output, args.force)

    spent = sum(-row["amount"] for row in snapshot["transactions"] if row["amount"] < 0)
    print(f"Created sanitized Sparkasse snapshot: {output}")
    print(
        "Sparkasse snapshot: "
        f"balance EUR {snapshot['balance']:.2f} · "
        f"{len(snapshot['transactions'])} booked transactions · "
        f"debits EUR {spent:.2f}"
    )
    print("Import it into Folio, then delete the plaintext snapshot file.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("\nCancelled.")
        raise SystemExit(130)
    except Exception as exc:
        print(f"Error: {exc}")
        raise SystemExit(1)
