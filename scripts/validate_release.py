from pathlib import Path
import ast
import importlib.util
import json
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
required = [
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/res/values/colors.xml",
    "app/src/main/res/values-night/colors.xml",
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
    "app/src/main/java/com/pix/folio/ui/V14PortfolioOverview.kt",
    "app/src/main/java/com/pix/folio/ui/V141ScalablePortfolioScreen.kt",
    "app/src/main/java/com/pix/folio/ui/theme/FolioTheme.kt",
    "app/src/main/java/com/pix/folio/data/FolioStore.kt",
    "app/src/main/java/com/pix/folio/data/MonthlyPlanStore.kt",
    "app/src/main/java/com/pix/folio/data/InvestmentTrackingStore.kt",
    "app/src/main/java/com/pix/folio/data/ScalableSnapshot.kt",
    "app/src/main/java/com/pix/folio/data/SecureScalableStore.kt",
    "app/src/main/java/com/pix/folio/data/FolioNavigationStore.kt",
    "app/src/main/java/com/pix/folio/data/FolioBackup.kt",
    "app/src/main/java/com/pix/folio/data/FolioRoomMirror.kt",
    "app/src/main/java/com/pix/folio/data/OpenFigiService.kt",
    "app/src/main/java/com/pix/folio/data/MarketPriceService.kt",
    "app/src/main/java/com/pix/folio/data/RecurringMoneyProcessor.kt",
    "app/src/main/java/com/pix/folio/widget/FolioWidget.kt",
    "app/src/main/java/com/pix/folio/widget/FolioWidgetUpdater.kt",
    "app/src/main/res/xml/folio_balance_widget_info.xml",
    "app/src/main/res/xml/folio_overview_widget_info.xml",
    "app/src/beta/java/com/pix/folio/updates/BetaUpdater.kt",
    "app/src/test/java/com/pix/folio/V08FinanceModelTest.kt",
    ".github/workflows/build-apk.yml",
    "tools/scalable_snapshot.py",
    "SECURITY.md",
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
manifest_text = manifest.read_text()
if "android.permission.INTERNET" not in manifest_text:
    raise SystemExit("Base app manifest must include INTERNET")
if 'android:allowBackup="false"' not in manifest_text:
    raise SystemExit("Android backup must stay disabled")
if 'android:usesCleartextTraffic="false"' not in manifest_text:
    raise SystemExit("Cleartext Android traffic must stay disabled")

for widget_token in [
    ".widget.FolioBalanceWidgetReceiver",
    ".widget.FolioOverviewWidgetReceiver",
    "@xml/folio_balance_widget_info",
    "@xml/folio_overview_widget_info",
]:
    if widget_token not in manifest_text:
        raise SystemExit(f"Widget registration is incomplete: {widget_token}")

overview_widget_info = (root / "app/src/main/res/xml/folio_overview_widget_info.xml").read_text()
for token in ['android:targetCellWidth="2"', 'android:targetCellHeight="2"']:
    if token not in overview_widget_info:
        raise SystemExit(f"2x2 widget geometry is incomplete: {token}")

light_colors = (root / "app/src/main/res/values/colors.xml").read_text()
dark_colors = (root / "app/src/main/res/values-night/colors.xml").read_text()
for color_name in ["folio_widget_background", "folio_widget_foreground", "folio_widget_muted"]:
    if color_name not in light_colors or color_name not in dark_colors:
        raise SystemExit(f"Widget light/dark palette is incomplete: {color_name}")

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
    "mirrorToRoomV08",
    "FolioNavigationStore",
    "lastRootPage",
    "setLastRootPage",
    "refreshMarketPrices",
    "NavigationBar",
    "NavigationBarItem",
]:
    if token not in app:
        raise SystemExit(f"Missing app-shell token: {token}")
for forbidden in ["V07BottomBar", "V08PageIndicator", "pagerState.isScrollInProgress"]:
    if forbidden in app:
        raise SystemExit(f"Folio must not restore transient pager navigation chrome: {forbidden}")

# The app's root content color must come from the active Material color scheme. This protects every
# unstyled Text/Icon from becoming black-on-black in system dark mode. Finance change colors must be
# defined centrally through adaptive Material semantic roles rather than per-screen literals.
theme = (root / "app/src/main/java/com/pix/folio/ui/theme/FolioTheme.kt").read_text()
for token in [
    "Surface(",
    "color = scheme.background",
    "contentColor = scheme.onBackground",
    "tertiary = FolioPalette.LightGain",
    "tertiary = FolioPalette.DarkGain",
    "error = FolioPalette.LightLoss",
    "error = FolioPalette.DarkLoss",
]:
    if token not in theme:
        raise SystemExit(f"Theme-aware visual semantics are incomplete: {token}")

source = "\n".join(p.read_text() for p in root.glob("app/src/main/java/**/*.kt"))
feature_groups = {
    "money semantics": ["AVAILABLE CASH", "LEFT TO PLAN", "Expected income"],
    "spending breakdown": ["Where did my money go?"],
    "interactive net-worth ranges": ["ONE_MONTH", "THREE_MONTHS", "ONE_YEAR", "ALL"],
    "touch chart inspection": ["awaitEachGesture", "selectedIndex"],
    "semantic performance color": ["folioChangeColor", "MaterialTheme.colorScheme.tertiary", "MaterialTheme.colorScheme.error"],
    "semantic chart visuals": ["semanticTrend", "showZeroLine", "signedValues", "lineColor.copy(alpha = 0.08f)"],
    "spending share bars": ["V07Progress(share / 100.0)"],
    "portfolio modes": ["VALUE", "RETURN", "CONTRIBUTIONS"],
    "portfolio performance": ["Portfolio performance", "Portfolio value", "Invested", "Total return", "Time-weighted return", "v081AbsoluteReturn", "v081AbsoluteReturnSeries", "v081TimeWeightedReturnPct"],
    "purchase timestamp": ["KEY_PURCHASE_DATETIME", "purchaseDateTimeFor", "Purchase time · HH:mm"],
    "unit valuation": ["v081CurrentValue", "brokerOwnedUnits", "KEY_BROKER_OWNED_UNITS", "Current broker units", "Exact from broker units"],
    "direct investment contributions": ["Add contribution now", "Units bought", "portfolioCostBasis"],
    "investment purchase history": ["Investment activity", "Edit purchase", "Purchase time · HH:mm", "purchasedAt"],
    "monthly investment totals": ["MONTH TOTAL", "Cumulative", "v081CumulativeMonthlyContributions", "v081PurchaseLotValueAt", "v081ResolvedOwnedUnits"],
    "widget metrics": ["NET WORTH", "INVESTMENTS", "CASH LEFT", "actionRunCallback", "widgetValues"],
    "2x2 overview widget": ["FolioOverviewWidget", "FolioOverviewWidgetReceiver", "SAVINGS", "scalableConnected"],
    "widget theme": ["folio_widget_background", "folio_widget_foreground", "folio_widget_muted", "WidgetBackground", "WidgetForeground", "WidgetMuted"],
    "Scalable read-only source": ["ScalableSnapshot", "SecureScalableStore", "SCALABLE CAPITAL", "brokerAccountValue", "cashBalance", "V141ScalablePortfolioScreen"],
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
        raise SystemExit(f"Missing Folio feature {label}: {', '.join(missing_tokens)}")

market = (root / "app/src/main/java/com/pix/folio/data/MarketPriceService.kt").read_text()
for forbidden in ["knownYahooSymbols", "LU2903252349", "SCWX.DE", 'Folio/0.7.3 Android']:
    if forbidden in market:
        raise SystemExit(f"Security/version-specific market hardcoding returned: {forbidden}")
if "BuildConfig.VERSION_NAME" not in market or "searchYahooSymbols(isin, holding)" not in market:
    raise SystemExit("Generic market resolution/version metadata is incomplete")
isin_first = market.find("if (isin.isNotBlank() && isFundLike)")
cached_symbols = market.find("listOf(holding.priceSymbol, holding.symbol)")
if isin_first < 0 or cached_symbols < 0 or isin_first > cached_symbols:
    raise SystemExit("Fund-like Yahoo resolution must try ISIN candidates before cached symbols")

# Scalable integration security contract.
scalable_codec = (root / "app/src/main/java/com/pix/folio/data/ScalableSnapshot.kt").read_text()
scalable_store = (root / "app/src/main/java/com/pix/folio/data/SecureScalableStore.kt").read_text()
scalable_helper_path = root / "tools/scalable_snapshot.py"
scalable_helper = scalable_helper_path.read_text()
security_doc = (root / "SECURITY.md").read_text()
backup_source = (root / "app/src/main/java/com/pix/folio/data/FolioBackup.kt").read_text()
gitignore = (root / ".gitignore").read_text()

for token in ["AndroidKeyStore", "AES/GCM/NoPadding", "KeyGenParameterSpec", "setRandomizedEncryptionRequired(true)", "setUnlockedDeviceRequired(true)"]:
    if token not in scalable_store:
        raise SystemExit(f"Scalable encrypted storage safeguard is missing: {token}")

for forbidden in ["HttpURLConnection", "URL(", "Socket(", "access_token", "refresh_token", "client_secret"]:
    if forbidden in scalable_codec or forbidden in scalable_store:
        raise SystemExit(f"Scalable snapshot path must not own network/auth material: {forbidden}")

if "scalable_snapshot_v1.enc" in backup_source or "folio_scalable" in backup_source:
    raise SystemExit("Portable Folio backup must not include the encrypted Scalable snapshot")

for pattern in ["folio-scalable*.json", "*.folio-scalable.json", "scalable-snapshot*.json"]:
    if pattern not in gitignore:
        raise SystemExit(f"Missing Scalable privacy gitignore pattern: {pattern}")

try:
    ast.parse(scalable_helper)
except SyntaxError as error:
    raise SystemExit(f"Scalable helper syntax error: {error}") from error

for token in [
    'run_sc(sc, "broker", "overview")',
    'run_sc(sc, "broker", "holdings")',
    'run_sc(sc, "broker", "cash-breakdown")',
    "shell=False",
    "0o600",
]:
    if token not in scalable_helper:
        raise SystemExit(f"Scalable helper read-only safeguard is missing: {token}")
if scalable_helper.count('run_sc(sc, "broker",') != 3:
    raise SystemExit("Scalable helper may execute only overview, holdings, and cash-breakdown reads")
for forbidden in ['run_sc(sc, "broker", "trade"', 'run_sc(sc, "broker", "savings-plans"', "shell=True"]:
    if forbidden in scalable_helper:
        raise SystemExit(f"Unsafe Scalable helper behavior detected: {forbidden}")

spec = importlib.util.spec_from_file_location("folio_scalable_snapshot_helper", scalable_helper_path)
if spec is None or spec.loader is None:
    raise SystemExit("Could not load Scalable snapshot helper")
helper_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper_module)
synthetic = helper_module.build_snapshot(
    {
        "ok": True,
        "command": "broker.overview",
        "data": {
            "account_id": "outer-must-not-leak",
            "portfolio_id": "outer-must-not-leak",
            "resolution": {"account": "auto_resolve", "portfolio": "auto_resolve"},
            "result": {
                "account_id": "inner-must-not-leak",
                "portfolio_id": "inner-must-not-leak",
                "valuation": {"total": "1234.56", "securities": "1234.56", "crypto": "0"},
                "timestamps": {"valuation_timestamp_utc": "2026-10-03T07:00:00Z"},
                "performance": [{"timeframe": "MAX", "simpleAbsoluteReturn": "12.34"}],
            },
        },
    },
    {
        "ok": True,
        "command": "broker.holdings",
        "data": {
            "account_id": "outer-must-not-leak",
            "portfolio_id": "outer-must-not-leak",
            "resolution": {"account": "auto_resolve", "portfolio": "auto_resolve"},
            "result": {
                "account_id": "inner-must-not-leak",
                "portfolio_id": "inner-must-not-leak",
                "items": [{
                    "isin": "IE00B4L5Y983",
                    "name": "Synthetic ETF",
                    "security_type": "ETF",
                    "quantity": "10",
                    "valuation": "1234.56",
                    "valuation_currency": "EUR",
                    "quote_mid_price": "123.45",
                    "quote_currency": "EUR",
                    "quote_timestamp_utc": "2026-10-03T07:00:00Z",
                    "quote_is_outdated": False,
                }],
            },
        },
    },
    {
        "ok": True,
        "command": "broker.cash-breakdown",
        "data": {
            "account_id": "outer-must-not-leak",
            "portfolio_id": "outer-must-not-leak",
            "resolution": {"account": "auto_resolve", "portfolio": "auto_resolve"},
            "result": {
                "account_id": "inner-must-not-leak",
                "portfolio_id": "inner-must-not-leak",
                "cash_balance": "37.44",
                "buying_power": "37.44",
                "buying_power_without_credit": "37.44",
                "available_credit_line": "0",
                "loaned": "0",
            },
        },
    },
    "sc 1.1.0",
)
synthetic_json = json.dumps(synthetic)
if (
    "account_id" in synthetic_json
    or "portfolio_id" in synthetic_json
    or "outer-must-not-leak" in synthetic_json
    or "inner-must-not-leak" in synthetic_json
):
    raise SystemExit("Scalable snapshot helper leaked account/portfolio identifiers")
if (
    synthetic["valuation"]["portfolio"] != 1234.56
    or synthetic["valuation"]["cash"] != 37.44
    or synthetic["holdings"][0]["valuation"] != 1234.56
):
    raise SystemExit("Scalable snapshot helper changed broker-reported valuation/cash")
if "sc login --local-read-only" not in security_doc or "AES-256-GCM" not in security_doc:
    raise SystemExit("SECURITY.md is missing the Scalable trust boundary")

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
    "recurringInvestmentUsesEurQuoteForUnits",
    "recurringInvestmentDoesNotTreatUsdAsEur",
    "investmentPurchasesStayInTheirRealMonths",
    "investmentPurchaseKeepsExactDateAndTime",
    "cumulativeContributionsAddEachMonthOnTop",
    "confirmedBrokerUnitsBeatTransactionUnitSum",
    "transactionUnitsRemainEstimatedWithoutBrokerConfirmation",
    "purchaseLotUsesItsOwnUnitsAtMarketPrice",
    "laterPurchaseDoesNotExistBeforeItsPurchaseDate",
    "purchaseLotStartsAtItsOwnCostBasis",
    "todaysStoredQuoteKeepsTodaysPurchaseInCurrentValue",
    "depositAloneDoesNotCreatePerformance",
    "timeWeightedReturnCompoundsAcrossContributionPeriods",
    "absoluteReturnSubtractsInvestedCapitalNotDepositsFromPerformance",
    "brokerStyleAbsoluteReturnUsesCurrentValueMinusInvestedCapital",
    "scalableInvestmentValueExcludesBrokerCash",
    "scalableAccountTotalAddsBrokerCash",
    "scalablePrimaryReturnPrefersAllTimeStyleFrame",
]:
    if name not in tests:
        raise SystemExit(f"Missing finance regression test: {name}")

readme = (root / "README.md").read_text()
changelog = (root / "CHANGELOG.md").read_text()

version_name_match = re.search(r'versionName = ciVersionName \?: "(\d+)\.(\d+)\.(\d+)"', build)
version_code_match = re.search(r'versionCode = ciVersionCode \?: (\d+)', build)
if not version_name_match or not version_code_match:
    raise SystemExit("Unable to resolve the default Folio version from app/build.gradle.kts")

app_version = tuple(int(part) for part in version_name_match.groups())
app_version_text = ".".join(str(part) for part in app_version)
version_code = int(version_code_match.group(1))
expected_code = app_version[0] * 10_000 + app_version[1] * 100 + app_version[2]
if version_code != expected_code:
    raise SystemExit(
        f"Version code {version_code} does not match {app_version_text}; expected {expected_code}"
    )

heading_pattern = re.compile(
    r"^## \[(\d+)\.(\d+)\.(\d+)\.beta\] - \d{4}-\d{2}-\d{2}$",
    re.MULTILINE,
)
headings = list(heading_pattern.finditer(changelog))
if len(headings) < 2:
    raise SystemExit("CHANGELOG must contain at least two beta release sections")

current = tuple(int(part) for part in headings[0].groups())
previous = tuple(int(part) for part in headings[1].groups())
if current != app_version:
    raise SystemExit(
        f"App version {app_version_text} does not match latest CHANGELOG beta "
        f"{current[0]}.{current[1]}.{current[2]}"
    )

current_body = changelog[headings[0].end():headings[1].start()]
is_patch = current[0] == previous[0] and current[1] == previous[1]
is_minor = current[0] == previous[0] and current[1] == previous[1] + 1
is_major = current[0] == previous[0] + 1

if is_patch:
    if current[2] != previous[2] + 1:
        raise SystemExit("Patch beta versions must increment PATCH by exactly one")
    if re.search(r"^### Added\s*$", current_body, re.MULTILINE):
        raise SystemExit(
            "Patch beta releases are fixes/maintenance only; an Added section requires a MINOR bump"
        )
elif is_minor:
    if current[2] != 0:
        raise SystemExit("MINOR beta releases must reset PATCH to 0")
elif is_major:
    if current[1] != 0 or current[2] != 0:
        raise SystemExit("MAJOR beta releases must reset MINOR and PATCH to 0")
else:
    raise SystemExit(
        f"Invalid beta version transition: {previous} -> {current}. "
        "Use sequential PATCH, MINOR, or MAJOR increments."
    )

if f"`{app_version_text}.beta`" not in readme:
    raise SystemExit(f"README current beta source is not {app_version_text}.beta")
if "Net worth / Investments / Cash left" not in readme or "no transient pager dots" not in readme:
    raise SystemExit("README product documentation is incomplete")
if "Patch releases must not introduce a new user-facing capability" not in readme:
    raise SystemExit("README versioning policy is missing")

print(f"Folio v{app_version_text} static validation passed.")
