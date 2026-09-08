from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
required = [
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/pix/folio/MainActivity.kt",
    "app/src/main/java/com/pix/folio/ui/FolioApp.kt",
    "app/src/beta/java/com/pix/folio/updates/BetaUpdater.kt",
    ".github/workflows/build-apk.yml",
]
missing = [p for p in required if not (root / p).is_file()]
if missing:
    print("Missing required files:")
    print("\n".join(f" - {p}" for p in missing))
    sys.exit(1)

text = (root / "app/build.gradle.kts").read_text()
checks = {
    "application id": 'applicationId = "com.pix.folio"',
    "beta flavor": 'create("beta")',
    "play flavor": 'create("play")',
    "GitHub beta updates": 'GITHUB_BETA_UPDATES',
}
failed = [name for name, token in checks.items() if token not in text]
if failed:
    print("Validation failed:", ", ".join(failed))
    sys.exit(1)

updater = (root / "app/src/beta/java/com/pix/folio/updates/BetaUpdater.kt").read_text()
if "0xpix/folio-app/releases" not in updater:
    print("Updater is not pointed at 0xpix/folio-app releases")
    sys.exit(1)

print("Folio static validation passed.")
