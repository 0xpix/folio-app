from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
required = [
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/pix/folio/MainActivity.kt",
    "app/src/main/java/com/pix/folio/ui/FolioApp.kt",
    "app/src/main/java/com/pix/folio/ui/FolioViewModel.kt",
    "app/src/main/java/com/pix/folio/model/FolioModels.kt",
    "app/src/main/java/com/pix/folio/data/FolioStore.kt",
    "app/src/main/java/com/pix/folio/data/OpenFigiService.kt",
    "app/src/beta/java/com/pix/folio/updates/BetaUpdater.kt",
    ".github/workflows/build-apk.yml",
]
missing = [p for p in required if not (root / p).is_file()]
if missing:
    print("Missing required files:")
    print("\n".join(f" - {p}" for p in missing))
    sys.exit(1)

build = (root / "app/build.gradle.kts").read_text()
build_checks = {
    "application id": 'applicationId = "com.pix.folio"',
    "beta flavor": 'create("beta")',
    "play flavor": 'create("play")',
    "GitHub beta updates": 'GITHUB_BETA_UPDATES',
    "biometric dependency": 'androidx.biometric:biometric:1.1.0',
    "fragment activity dependency": 'androidx.fragment:fragment-ktx:1.9.0',
}
failed = [name for name, token in build_checks.items() if token not in build]
if failed:
    print("Validation failed:", ", ".join(failed))
    sys.exit(1)

manifest = root / "app/src/main/AndroidManifest.xml"
ET.parse(manifest)
manifest_text = manifest.read_text()
if "android.permission.INTERNET" not in manifest_text:
    print("Base app manifest must include INTERNET for ISIN lookup")
    sys.exit(1)

updater = (root / "app/src/beta/java/com/pix/folio/updates/BetaUpdater.kt").read_text()
if "0xpix/folio-app/releases" not in updater:
    print("Updater is not pointed at 0xpix/folio-app releases")
    sys.exit(1)

app = (root / "app/src/main/java/com/pix/folio/ui/FolioApp.kt").read_text()
for forbidden in ["import androidx.compose.foundation.layout.weight", "seed-"]:
    if forbidden in app:
        print(f"Forbidden stale token in FolioApp.kt: {forbidden}")
        sys.exit(1)

feature_tokens = [
    "OpenFigiService.lookupIsin",
    "BudgetSheet",
    "MonthlyOverviewScreen",
    "AddRecurringInvestmentSheet",
    "InvestmentHistorySheet",
    "NetWorthHistoryScreen",
    "UPCOMING",
    "AppLockGate",
]
missing_features = [token for token in feature_tokens if token not in app]
if missing_features:
    print("Missing v0.3 feature tokens:", ", ".join(missing_features))
    sys.exit(1)

print("Folio static validation passed.")
