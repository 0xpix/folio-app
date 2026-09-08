#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
changelog = root / "CHANGELOG.md"
if len(sys.argv) != 2:
    raise SystemExit("usage: release_notes.py <tag-or-version>")

version = sys.argv[1].strip().removeprefix("v")
text = changelog.read_text(encoding="utf-8")
pattern = re.compile(
    rf"^## \[{re.escape(version)}\](?:\s+-\s+[^\n]*)?\n(?P<body>.*?)(?=^## \[|\Z)",
    re.MULTILINE | re.DOTALL,
)
match = pattern.search(text)
if not match:
    raise SystemExit(f"CHANGELOG.md has no section for {version}")
body = match.group("body").strip()
if not body:
    raise SystemExit(f"CHANGELOG.md section for {version} is empty")
print(body)
