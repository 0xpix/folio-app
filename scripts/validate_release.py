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
    "app/src/main/java/com/pix/folio/ui/V07MoneyScreen.kt",
    "app/src/main/java/com/pix/folio/ui/V07PortfolioScreen.kt",
    "app/src/main/java/com/pix/folio/ui/V07Components.kt",
    "app/src/main/java/com/pix/folio/ui/V08HomeScreen.kt",
    "app/src/main/java/com/pix/folio/ui/V08MoneyScreen.kt",
    "app/src/main/java/com/pix/folio/ui/V08PortfolioScreen.kt",
    "app/src/main/java/com/pix/folio/ui/V08AddInvestmentSheet.kt",
    "app/src/main/java/com/pix/folio/ui/V08SettingsSheet.kt",
    "app/src/main/java/com/pix/folio/ui/V08DashboardComponents.kt",
    "app/src/main/java/com/pix/folio/ui/V08ViewModelExtensions.kt",
    "app/src/main/java/com/pix/folio/ui/V081Valuation.kt",
    "app/src/main/java/com/pix/folio/ui/theme/FolioTheme.kt",
    "app/src/main/java/com/pix/folio/data/FolioStore.kt",
    "app/src/main/java/com/pix/folio/data/MonthlyPlanStore.kt",
    "app/src/main/java/com/pix/folio/data/InvestmentTrackingStore.kt",
    "app/src/main/java/com/pix/folio/data/FolioNavigationStore.kt",
    "app/src/main/java/com/pix/folio/data/FolioBackup.kt",
    "app/src/main/java/com/pix/folio/data/FolioRoomMirror.kt",
    "app/src/main/java/com/pix/folio/data/OpenFigiService.kt",
    "app/src/main/java/com/pix/folio/data/MarketPriceService.kt",
    "app/src/main/java/com/pix/folio/data/RecurringMoneyProcessor.kt",
    "app/src/main/java/com/pix/folio/widget/FolioWidgetUpdater.kt",
    "app/src/beta/java/com/pix/folio/updates/BetaUpdater.kt",
    "app/src/test/java/com/pix/folio/V08FinanceModelTest.kt",
    ".github/workflows/build-apk.yml",
    "CHANGELOG.md",
    "README.md",
]
missing = [p for p in required if not (root / p).is_file()]
if missing:
    raise SystemExit("Missing required files:\n" + "\n".join(f" - {p}" for p in missing))

build = (root / "app/build.gradle.kts").read_text()
root_build = (root / "build.gradle.kts").read_text()
for label, token in {
    "application id": 'applicationId = "com.pix.folio"',
    "v0.8.1 version": 'versionName = ciVersionName ?: "0.8.1"',
    "v0.8.1 version code": 'versionCode = ciVersionCode ?: 801',
    "Room runtime": 'androidx.room:room-runtime',
    "Room compiler": 'androidx.room:room-compiler',
    "JUnit": 'junit:junit:4.13.2',
    "beta flavor": 'create("beta")',
    "play flavor": 'create("play")',
}.items():
    if token not in build:
        raise SystemExit(f"Build validation failed: {label}")
if "com.android.legacy-kapt" not in root_build or "com.android.legacy-kapt" not in build:
    raise SystemExit("Build validation failed: AGP-compatible Room legacy-kapt plugin")

manifest = root / "app/src/main/AndroidManifest.xml"
ET.parse(manifest)
if "android.permission.INTERNET" not in manifest.read_text():
    raise SystemExit("Base app manifest must include INTERNET")

updater = (root / "app/src/beta/java/com/pix/folio/updates/BetaUpdater.kt").read_text()
if "0xpix/folio-app/releases" not in updater:
    raise SystemExit("Updater is not pointed at 0xpix/folio-app releases")

app = (root / "app/src/main/java/com/pix/folio/ui/FolioApp.kt").read_text()
for token in [
    "HorizontalPager",
    "rememberPagerState",
    "V08MoneyScreen",
    "V08HomeScreen",
    "V08PortfolioScreen",
    "V08SettingsSheet",
    "V08PageIndicator",
    "mirrorToRoomV08",
    "FolioNavigationStore",
    "lastRootPage",
    "setLastRootPage",
    "pagerState.isScrollInProgress",
    "refreshMarketPrices",
]:
    if token not in app:
        raise SystemExit(f"Missing v0.8.1 app-shell token: {token}")
if "bottomBar =" in app or "V07BottomBar" in app:
    raise SystemExit("v0.8.1 must not restore a persistent bottom navigation bar")

# The app's root content color must come from the active Material color scheme. This protects every
# unstyled Text/Icon from becoming black-on-black in system dark mode.
theme = (root / "app/src/main/java/com/pix/folio/ui/theme/FolioTheme.kt").read_text()
for token in ["Surface(", "color = scheme.background", "contentColor = scheme.onBackground"]:
    if token not in theme:
        raise SystemExit(f"Theme-aware root content color is incomplete: {token}")

source = "\n".join(p.read_text() for p in root.glob("app/src/main/java/**/*.kt"))
feature_groups = {
    "money semantics": ["SPENDABLE NOW", "UNASSIGNED THIS MONTH", "Planning only"],
    "spending breakdown": ["Where did my money go?"],
    "interactive net-worth ranges": ["ONE_MONTH", "THREE_MONTHS", "ONE_YEAR", "ALL"],
    "touch chart inspection": ["awaitEachGesture", "selectedIndex"],
    "portfolio modes": ["VALUE", "RETURN", "CONTRIBUTIONS"],
    "portfolio intelligence": ["Portfolio intelligence", "Largest position", "Market growth"],
    "purchase timestamp": ["KEY_PURCHASE_DATETIME", "purchaseDateTimeFor", "Purchase time · HH:mm"],
    "unit valuation": ["v081CurrentValue", "ownedUnits", "Units owned (recommended)", "Current units owned"],
    "last-page restore": ["folio_navigation_v1", "root_page"],
    "portable backup": ["folio-backup", "Export Folio", "Restore Folio"],
    "backup validation": ["Unsupported Folio backup version", "Backup data is incomplete"],
    "Room bridge": ["FolioRoomDatabase", "folio_v08.db", "FolioSnapshotEntity"],
    "salary attribution": ["budgetMonthOffset"],
    "September 2026 start": ["YearMonth.of(2026, 9)"],
    "savings": ["CRASH_RESERVE", "emergencyFundTarget"],
    "market tracking": ["MarketPriceService", "trackedPortfolioHistory"],
    "ISIN lookup": ["OpenFigiService.lookupIsin"],
    "CS2 support": ["Cs2AssetType", "marketHashName"],
}
for label, tokens in feature_groups.items():
    missing_tokens = [t for t in tokens if t not in source]
    if missing_tokens:
        raise SystemExit(f"Missing v0.8.1 feature {label}: {', '.join(missing_tokens)}")

market = (root / "app/src/main/java/com/pix/folio/data/MarketPriceService.kt").read_text()
for forbidden in ["knownYahooSymbols", "LU2903252349", "SCWX.DE", 'Folio/0.7.3 Android']:
    if forbidden in market:
        raise SystemExit(f"Security/version-specific market hardcoding returned: {forbidden}")
if "BuildConfig.VERSION_NAME" not in market or "searchYahooSymbols(isin, holding)" not in market:
    raise SystemExit("Generic market resolution/version metadata is incomplete")

workflow = (root / ".github/workflows/build-apk.yml").read_text()
for task in [":app:testBetaDebugUnitTest", ":app:testPlayDebugUnitTest", ":app:assembleBetaDebug", ":app:assemblePlayDebug"]:
    if task not in workflow:
        raise SystemExit(f"CI is missing required task: {task}")

tests = (root / "app/src/test/java/com/pix/folio/V08FinanceModelTest.kt").read_text()
for name in [
    "day31ClampsToFebruaryEnd",
    "endOfMonthSalaryCanFundNextBudgetMonth",
    "unassignedPlanIsNotTheSameAsSpendableCash",
    "exactEurUnitsUseLatestMarketPrice",
    "nonEurQuoteDoesNotPretendToBeEuroExact",
    "incompleteUnitsStayEstimated",
]:
    if name not in tests:
        raise SystemExit(f"Missing finance regression test: {name}")

readme = (root / "README.md").read_text()
changelog = (root / "CHANGELOG.md").read_text()
if "`0.8.1.beta`" not in readme or "owned units × freshest available EUR market quote" not in readme:
    raise SystemExit("README v0.8.1 documentation is incomplete")
if "## [0.8.1.beta] - 2026-09-11" not in changelog:
    raise SystemExit("CHANGELOG is missing v0.8.1.beta")

print("Folio v0.8.1 static validation passed.")
