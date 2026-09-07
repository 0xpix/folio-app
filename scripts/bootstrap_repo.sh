#!/usr/bin/env bash
set -euo pipefail

git init
git add .
git commit -m "feat: bootstrap Folio Android beta"
git branch -M main
gh repo create 0xpix/folio-android --public --source=. --remote=origin --push
