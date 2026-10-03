#!/usr/bin/env python3
"""
Create a minimal Folio snapshot from the official Scalable Capital CLI.

Security properties:
- never logs in and never reads Scalable CLI session files
- never accepts a password, token, account id, or portfolio id
- executes only two fixed read commands with shell=False
- strips account_id / portfolio_id and other nonessential metadata
- writes the output with owner-only permissions (0600)

Authenticate yourself first with:
    sc login --local-read-only

Then run:
    python3 tools/scalable_snapshot.py --output folio-scalable.snapshot.json
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from typing import Any


def run_sc(sc: str, *args: str) -> Any:
    completed = subprocess.run(
        [sc, *args, "--json"],
        shell=False,
        check=False,
        capture_output=True,
        text=True,
        timeout=60,
    )
    if completed.returncode != 0:
        message = completed.stderr.strip() or completed.stdout.strip() or "Scalable CLI command failed"
        raise RuntimeError(message)
    try:
        return json.loads(completed.stdout)
    except json.JSONDecodeError as error:
        raise RuntimeError("Scalable CLI did not return valid JSON") from error


def finite_number(value: Any, label: str, *, optional: bool = False) -> float | None:
    if value is None and optional:
        return None
    try:
        number = float(value)
    except (TypeError, ValueError) as error:
        if optional:
            return None
        raise RuntimeError(f"Missing or invalid Scalable value: {label}") from error
    if number != number or number in (float("inf"), float("-inf")):
        raise RuntimeError(f"Invalid Scalable value: {label}")
    return number


def clean_text(value: Any) -> str:
    return str(value).strip() if value is not None else ""


def unwrap_broker_result(payload: dict[str, Any], label: str) -> dict[str, Any]:
    """Accept legacy JSON plus Scalable CLI machine/data and broker/result envelopes."""
    current = payload

    # Current Scalable CLI --json output is a machine envelope:
    # {"ok": true, "command": "broker.overview", "data": {...}}
    if {"ok", "command", "data"}.issubset(current.keys()):
        if current.get("ok") is not True:
            raise RuntimeError(f"Scalable {label} command returned an unsuccessful JSON envelope")
        data = current.get("data")
        if not isinstance(data, dict):
            raise RuntimeError(f"Scalable {label} response has an invalid data envelope")
        current = data

    # Broker query output itself may also be wrapped:
    # {"account_id": "...", "portfolio_id": "...", "resolution": {...}, "result": {...}}
    # Only the result object is passed onward, so wrapper identifiers are never persisted.
    if "result" in current:
        result = current.get("result")
        if not isinstance(result, dict):
            raise RuntimeError(f"Scalable {label} response has an invalid result envelope")
        current = result

    return current


def build_snapshot(overview: dict[str, Any], holdings: dict[str, Any], cli_version: str) -> dict[str, Any]:
    overview = unwrap_broker_result(overview, "overview")
    holdings = unwrap_broker_result(holdings, "holdings")

    valuation = overview.get("valuation")
    if not isinstance(valuation, dict):
        raise RuntimeError("Scalable overview is missing valuation")

    items = holdings.get("items")
    if not isinstance(items, list):
        raise RuntimeError("Scalable holdings response is missing items")

    sanitized_holdings: list[dict[str, Any]] = []
    currencies: set[str] = set()

    for item in items:
        if not isinstance(item, dict):
            continue
        isin = clean_text(item.get("isin")).upper()
        if not isin:
            raise RuntimeError("Scalable holding is missing ISIN")
        valuation_currency = clean_text(item.get("valuation_currency")).upper()
        if valuation_currency:
            currencies.add(valuation_currency)

        sanitized_holdings.append(
            {
                "isin": isin,
                "name": clean_text(item.get("name")) or isin,
                "security_type": clean_text(item.get("security_type")),
                "quantity": finite_number(item.get("quantity"), f"{isin}.quantity", optional=True),
                "valuation": finite_number(item.get("valuation"), f"{isin}.valuation"),
                "valuation_currency": valuation_currency,
                "quote_mid_price": finite_number(
                    item.get("quote_mid_price"),
                    f"{isin}.quote_mid_price",
                    optional=True,
                ),
                "quote_currency": clean_text(item.get("quote_currency")).upper() or None,
                "quote_timestamp_utc": clean_text(item.get("quote_timestamp_utc")) or None,
                "quote_is_outdated": bool(item.get("quote_is_outdated", False)),
            }
        )

    if not currencies:
        # Scalable Broker Germany is EUR-denominated, but do not silently accept mixed/missing
        # currency data when there are actual holdings.
        if sanitized_holdings:
            raise RuntimeError("Scalable holdings are missing valuation currency")
        currency = "EUR"
    elif len(currencies) == 1:
        currency = next(iter(currencies))
    else:
        raise RuntimeError(f"Mixed Scalable valuation currencies are not supported: {sorted(currencies)}")

    if currency != "EUR":
        raise RuntimeError(f"Only EUR Scalable broker snapshots are supported, got {currency}")

    performance_rows: list[dict[str, Any]] = []
    performance = overview.get("performance")
    if isinstance(performance, list):
        for row in performance:
            if not isinstance(row, dict):
                continue
            timeframe = clean_text(row.get("timeframe"))
            absolute = finite_number(
                row.get("simpleAbsoluteReturn"),
                f"performance.{timeframe}",
                optional=True,
            )
            if timeframe and absolute is not None:
                performance_rows.append(
                    {
                        "timeframe": timeframe,
                        "simple_absolute_return": absolute,
                    }
                )

    timestamps = overview.get("timestamps") if isinstance(overview.get("timestamps"), dict) else {}

    return {
        "format": "folio-scalable-snapshot",
        "version": 1,
        "source": "scalable-cli",
        "created_at_utc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "valuation_timestamp_utc": clean_text(timestamps.get("valuation_timestamp_utc")) or None,
        "cli_version": cli_version or None,
        "currency": currency,
        "valuation": {
            "total": finite_number(valuation.get("total"), "valuation.total"),
            "securities": finite_number(valuation.get("securities"), "valuation.securities"),
            "crypto": finite_number(valuation.get("crypto"), "valuation.crypto", optional=True) or 0.0,
        },
        "performance": performance_rows,
        "holdings": sorted(sanitized_holdings, key=lambda row: row["isin"]),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Create a sanitized Folio snapshot from Scalable CLI")
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("folio-scalable.snapshot.json"),
        help="Output path (default: ./folio-scalable.snapshot.json)",
    )
    parser.add_argument("--force", action="store_true", help="Overwrite an existing output file")
    args = parser.parse_args()

    output = args.output.expanduser().resolve()
    if output.exists() and not args.force:
        raise RuntimeError(f"Refusing to overwrite existing file: {output}")

    sc = shutil.which("sc")
    if not sc:
        raise RuntimeError("Official Scalable CLI 'sc' was not found on PATH")

    version = subprocess.run(
        [sc, "--version"],
        shell=False,
        check=False,
        capture_output=True,
        text=True,
        timeout=15,
    )
    cli_version = version.stdout.strip() if version.returncode == 0 else ""

    # These are deliberately the only broker commands this helper can execute.
    overview = run_sc(sc, "broker", "overview")
    holdings = run_sc(sc, "broker", "holdings")
    if not isinstance(overview, dict) or not isinstance(holdings, dict):
        raise RuntimeError("Unexpected Scalable CLI response shape")

    snapshot = build_snapshot(overview, holdings, cli_version)

    output.parent.mkdir(parents=True, exist_ok=True)
    flags = os.O_WRONLY | os.O_CREAT | os.O_TRUNC
    fd = os.open(output, flags, 0o600)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            json.dump(snapshot, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
    except Exception:
        output.unlink(missing_ok=True)
        raise

    try:
        output.chmod(0o600)
    except OSError:
        pass

    print(f"Created sanitized snapshot: {output}")
    print("Import it into Folio, then delete the plaintext snapshot file when you no longer need it.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"Error: {error}", file=sys.stderr)
        raise SystemExit(1)
