from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
required = [
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/pix/folio/MainActivity.kt",
    "app/src/main/java/com/pix/folio/ui/FolioApp.kt",
    "app/src/main/java/com/pix/folio/ui/V07ViewModel.kt",
    "app/src/main/java/com/pix/folio/ui/V07HomeScreen.kt",
    "app/src/main/java/com/pix/folio/ui/V07MoneyScreen.kt",
    "app/src/main/java/com/pix/folio/ui/V07MoneySheets.kt",
    "app/src/main/java/com/pix/folio/ui/V07PortfolioScreen.kt",
    "app/src/main/java/com/pix/folio/ui/V07SettingsSheet.kt",
    "app/src/main/java/com/pix/folio/ui/V071UpdateNotes.kt",
    "app/src/main/java/com/pix/folio/ui/V07Components.kt",
    "app/src/main/java/com/pix/folio/ui/theme/FolioTheme.kt",
    "app/src/main/java/com/pix/folio/model/FolioModels.kt",
    "app/src/main/java/com/pix/folio/data/FolioStore.kt",
    "app/src/main/java/com/pix/folio/data/InvestmentTrackingStore.kt",
    "app/src/main/java/com/pix/folio/data/OpenFigiService.kt",
    "app/src/main/java/com/pix/folio/data/MarketPriceService.kt",
    "app/src/main/java/com/pix/folio/data/RecurringMoneyProcessor.kt",
    "app/src/main/java/com/pix/folio/work/FolioRecurringWorker.kt",
    "app/src/main/java/com/pix/folio/work/RecurringWorkScheduler.kt",
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
    "v0.7.3 version": 'versionName = ciVersionName ?: "0.7.3"',
    "beta flavor": 'create("beta")',
    "play flavor": 'create("play")',
    "GitHub beta updates": 'GITHUB_BETA_UPDATES',
    "biometric dependency": 'androidx.biometric:biometric:1.1.0',
    "Google Fonts dependency": 'androidx.compose.ui:ui-text-google-fonts',
    "WorkManager dependency": 'androidx.work:work-runtime-ktx',
}
failed = [name for name, token in build_checks.items() if token not in build]
if failed:
    print("Validation failed:", ", ".join(failed))
    sys.exit(1)

manifest = root / "app/src/main/AndroidManifest.xml"
ET.parse(manifest)
manifest_text = manifest.read_text()
if "android.permission.INTERNET" not in manifest_text:
    print("Base app manifest must include INTERNET for ISIN and price lookup")
    sys.exit(1)

updater = (root / "app/src/beta/java/com/pix/folio/updates/BetaUpdater.kt").read_text()
if "0xpix/folio-app/releases" not in updater:
    print("Updater is not pointed at 0xpix/folio-app releases")
    sys.exit(1)
if ".removePrefix(\"### \"" in updater or ".removePrefix(\"- \"" in updater:
    print("Updater must preserve Markdown headings and bullets for What's new")
    sys.exit(1)

app = (root / "app/src/main/java/com/pix/folio/ui/FolioApp.kt").read_text()
for forbidden in ["RootTab { HOME, EXPENSES", "INSIGHTS", "UPCOMING", "Icons.Outlined.Payments", "Icons.Outlined.TrendingUp"]:
    if forbidden in app:
        print(f"Old navigation token still present in FolioApp.kt: {forbidden}")
        sys.exit(1)

checks = {
    "three-tab shell": ("HOME", "MONEY", "PORTFOLIO"),
    "swipe navigation": ("HorizontalPager", "rememberPagerState"),
    "dot-only navigation": ("CircleShape", "size(if (active) 9.dp else 6.dp)"),
    "strong unlock action": ("UNLOCK FOLIO", "ButtonDefaults.buttonColors"),
    "safe top inset": ("statusBarsPadding",),
    "next-month budget attribution": ("budgetMonthOffset", "budgetMonth"),
    "visible monthly salary": ("Monthly salary", "Save monthly salary"),
    "savings buckets": ("CRASH_RESERVE", "GENERAL", "emergencyFundTarget"),
    "existing emergency savings": ("Already saved before Folio?", "setExistingEmergencyFundBalance"),
    "manual savings": ("V07SavingsTransferSheet", "Add from cash", "Withdraw to cash"),
    "formatted update notes": ("V071UpdateNotes", "What's new"),
    "purchase-date tracking": ("InvestmentTrackingStore", "setInvestmentPurchaseDate", "Purchase date"),
    "historical market data": ("fetchHistory", "HistoryPoint", "exchangeDataDelayedBy"),
    "Scalable ETF resolver": ("LU2903252349", "SCWX.DE", "searchYahooSymbols"),
    "tracked value graph": ("trackedValueHistory", "since purchase"),
    "combined portfolio graph": ("trackedPortfolioHistory", "Combined portfolio"),
    "large typography": ("64.sp", "58.sp", "48.sp"),
    "CS2 assets": ("Cs2AssetType", "marketHashName"),
    "ISIN lookup": ("OpenFigiService.lookupIsin",),
    "automatic prices": ("MarketPriceService", "refreshMarketPrices"),
    "automatic recurring money": ("RecurringMoneyProcessor", "autoRecurringEnabled"),
    "Pixelify typography": ("Pixelify Sans", "AppFontChoice.PIXELIFY"),
}
search_text = "\n".join(path.read_text() for path in root.glob("app/src/main/java/**/*.kt"))
missing_features = [name for name, tokens in checks.items() if any(token not in search_text for token in tokens)]
if missing_features:
    print("Missing v0.7.3 features:", ", ".join(missing_features))
    sys.exit(1)

print("Folio v0.7.3 static validation passed.")
