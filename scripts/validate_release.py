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
    "app/src/main/java/com/pix/folio/ui/V07Components.kt",
    "app/src/main/java/com/pix/folio/ui/V071UpdateNotes.kt",
    "app/src/main/java/com/pix/folio/ui/V08HomeScreen.kt",
    "app/src/main/java/com/pix/folio/ui/V08MoneyScreen.kt",
    "app/src/main/java/com/pix/folio/ui/V08PortfolioScreen.kt",
    "app/src/main/java/com/pix/folio/ui/V08AddInvestmentSheet.kt",
    "app/src/main/java/com/pix/folio/ui/V08SettingsSheet.kt",
    "app/src/main/java/com/pix/folio/ui/V08DashboardComponents.kt",
    "app/src/main/java/com/pix/folio/ui/V08ViewModelExtensions.kt",
    "app/src/main/java/com/pix/folio/ui/theme/FolioTheme.kt",
    "app/src/main/java/com/pix/folio/model/FolioModels.kt",
    "app/src/main/java/com/pix/folio/data/FolioStore.kt",
    "app/src/main/java/com/pix/folio/data/FinanceEditor.kt",
    "app/src/main/java/com/pix/folio/data/MonthlyPlanStore.kt",
    "app/src/main/java/com/pix/folio/data/InvestmentTrackingStore.kt",
    "app/src/main/java/com/pix/folio/data/FolioBackup.kt",
    "app/src/main/java/com/pix/folio/data/FolioRoomMirror.kt",
    "app/src/main/java/com/pix/folio/data/OpenFigiService.kt",
    "app/src/main/java/com/pix/folio/data/MarketPriceService.kt",
    "app/src/main/java/com/pix/folio/data/RecurringMoneyProcessor.kt",
    "app/src/main/java/com/pix/folio/widget/FolioWidgetUpdater.kt",
    "app/src/main/java/com/pix/folio/work/FolioRecurringWorker.kt",
    "app/src/main/java/com/pix/folio/work/RecurringWorkScheduler.kt",
    "app/src/beta/java/com/pix/folio/updates/BetaUpdater.kt",
    "app/src/test/java/com/pix/folio/V08FinanceModelTest.kt",
    ".github/workflows/build-apk.yml",
    "CHANGELOG.md",
    "README.md",
]
missing = [path for path in required if not (root / path).is_file()]
if missing:
    print("Missing required files:")
    print("\n".join(f" - {path}" for path in missing))
    sys.exit(1)

build = (root / "app/build.gradle.kts").read_text()
root_build = (root / "build.gradle.kts").read_text()
build_checks = {
    "application id": 'applicationId = "com.pix.folio"',
    "v0.8.0 version": 'versionName = ciVersionName ?: "0.8.0"',
    "v0.8.0 version code": 'versionCode = ciVersionCode ?: 800',
    "beta flavor": 'create("beta")',
    "play flavor": 'create("play")',
    "GitHub beta updates": 'GITHUB_BETA_UPDATES',
    "biometric dependency": 'androidx.biometric:biometric:1.1.0',
    "WorkManager dependency": 'androidx.work:work-runtime-ktx',
    "Room runtime": 'androidx.room:room-runtime',
    "Room compiler": 'androidx.room:room-compiler',
    "JUnit": 'junit:junit:4.13.2',
}
failed = [name for name, token in build_checks.items() if token not in build]
if 'org.jetbrains.kotlin.kapt' not in root_build or 'org.jetbrains.kotlin.kapt' not in build:
    failed.append("Room kapt plugin")
if failed:
    print("Build validation failed:", ", ".join(failed))
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
if '.removePrefix("### "' in updater or '.removePrefix("- "' in updater:
    print("Updater must preserve Markdown headings and bullets for What's new")
    sys.exit(1)

app = (root / "app/src/main/java/com/pix/folio/ui/FolioApp.kt").read_text()
app_checks = {
    "swipe navigation": ("HorizontalPager", "rememberPagerState"),
    "v0.8 money": ("V08MoneyScreen",),
    "v0.8 home": ("V08HomeScreen",),
    "v0.8 portfolio": ("V08PortfolioScreen",),
    "v0.8 settings": ("V08SettingsSheet",),
    "subtle pager indicator": ("V08PageIndicator", "navigationBarsPadding"),
    "Room safety mirroring": ("mirrorToRoomV08", "Dispatchers.IO"),
}
missing_app = [name for name, tokens in app_checks.items() if any(token not in app for token in tokens)]
if missing_app:
    print("App shell validation failed:", ", ".join(missing_app))
    sys.exit(1)
if "bottomBar =" in app or "V07BottomBar" in app:
    print("v0.8 must not restore the persistent bottom navigation bar")
    sys.exit(1)

source_text = "\n".join(path.read_text() for path in root.glob("app/src/main/java/**/*.kt"))
feature_checks = {
    "clear spendable money": ("SPENDABLE NOW", "Spendable now"),
    "clear monthly planning money": ("UNASSIGNED THIS MONTH", "Planning only"),
    "spending breakdown": ("Where did my money go?", "spentFor"),
    "interactive net-worth ranges": ("ONE_MONTH", "THREE_MONTHS", "ONE_YEAR", "ALL"),
    "interactive chart inspection": ("awaitEachGesture", "selectedIndex"),
    "portfolio graph modes": ("VALUE", "RETURN", "CONTRIBUTIONS"),
    "portfolio intelligence": ("Portfolio intelligence", "Largest position", "Market growth"),
    "allocation percentages": ("allocation", "% ·"),
    "purchase timestamps": ("purchase_datetime_", "purchaseDateTimeFor", "HH:mm"),
    "timestamp-aware investment creation": ("Purchase time · HH:mm", "addInvestmentV08"),
    "portable backup": ("folio-backup", "Export Folio", "Restore Folio"),
    "backup validation": ("Unsupported Folio backup version", "Backup data is incomplete"),
    "Room migration bridge": ("FolioRoomDatabase", "folio_v08.db", "FolioSnapshotEntity"),
    "salary month attribution": ("budgetMonthOffset", "budgetMonth"),
    "September 2026 start": ("FolioStartMonth", "YearMonth.of(2026, 9)"),
    "savings buckets": ("CRASH_RESERVE", "GENERAL", "emergencyFundTarget"),
    "automatic recurring money": ("RecurringMoneyProcessor", "autoRecurringEnabled"),
    "market history": ("fetchHistory", "trackedPortfolioHistory"),
    "ISIN lookup": ("OpenFigiService.lookupIsin",),
    "CS2 assets": ("Cs2AssetType", "marketHashName"),
    "undo": ("undoLastChange", "canUndo"),
}
missing_features = [name for name, tokens in feature_checks.items() if any(token not in source_text for token in tokens)]
if missing_features:
    print("Missing v0.8.0 features:", ", ".join(missing_features))
    sys.exit(1)

workflow = (root / ".github/workflows/build-apk.yml").read_text()
for task in [":app:testBetaDebugUnitTest", ":app:testPlayDebugUnitTest", ":app:assembleBetaDebug", ":app:assemblePlayDebug"]:
    if task not in workflow:
        print(f"CI is missing required task: {task}")
        sys.exit(1)

unit_tests = (root / "app/src/test/java/com/pix/folio/V08FinanceModelTest.kt").read_text()
for test_token in ["day31ClampsToFebruaryEnd", "endOfMonthSalaryCanFundNextBudgetMonth", "unassignedPlanIsNotTheSameAsSpendableCash"]:
    if test_token not in unit_tests:
        print(f"Missing finance edge-case test: {test_token}")
        sys.exit(1)

readme = (root / "README.md").read_text()
changelog = (root / "CHANGELOG.md").read_text()
if "Current beta source: `0.8.0.beta`" not in readme:
    print("README current beta source is stale")
    sys.exit(1)
if "## [0.8.0.beta] - 2026-09-10" not in changelog:
    print("CHANGELOG is missing v0.8.0.beta")
    sys.exit(1)

print("Folio v0.8.0 static validation passed.")
