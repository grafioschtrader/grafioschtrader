"""
Dry-run audit of BaseFeedConnector.supports(mic, country, AssetclassType, SpecialInvestmentInstruments)
against the production grafioschtrader database, BEFORE the validation is wired into the save path.

For every (security, connector-slot) and (currencypair, connector-slot) pairing currently in the DB,
simulate the same logic that BaseFeedConnector.supports() and AssetclassCategory.matches() would
apply, and report which combinations would FAIL the future validator.

READ-ONLY. No INSERT/UPDATE/DELETE.
"""
import subprocess
import sys
from collections import defaultdict, Counter

MYSQL = r"C:\xampp\mysql\bin\mysql.exe"
DB_USER = "grafioschtrader"
DB_PASS = "adergraf"
DB_NAME = "grafioschtrader"

# AssetclassType byte -> name (from AssetclassType.java)
AC_TYPE = {
    0: "EQUITIES", 1: "FIXED_INCOME", 2: "MONEY_MARKET", 3: "COMMODITIES",
    4: "REAL_ESTATE", 5: "MULTI_ASSET", 6: "CONVERTIBLE_BOND",
    7: "CREDIT_DERIVATIVE", 8: "CURRENCY_PAIR",
}
# SpecialInvestmentInstruments byte -> name (from SpecialInvestmentInstruments.java)
SII = {
    0: "DIRECT_INVESTMENT", 1: "ETF", 2: "MUTUAL_FUND", 3: "PENSION_FUNDS",
    4: "CFD", 5: "FOREX", 6: "ISSUER_RISK_PRODUCT", 10: "NON_INVESTABLE_INDICES",
}

# Connector metadata: id_prefix -> (categories, geo_restrictions, geo_exclusions)
# geo_restrictions: set of MIC/country codes (empty = global)
# geo_exclusions: dict geo -> set of excluded categories
STATIC_CONNECTORS = {
    "gt.datafeed.yahoo": (
        {"CURRENCY_PAIR", "CRYPTOCURRENCY", "NON_INVESTABLE_INDICES", "EQUITIES", "ETF",
         "ISSUER_RISK_PRODUCT", "CFD_DERIVATIVE"}, set(), {}),
    "gt.datafeed.six": (
        {"NON_INVESTABLE_INDICES", "EQUITIES", "FIXED_INCOME", "ETF", "MUTUAL_FUND",
         "REAL_ESTATE_FUND", "ISSUER_RISK_PRODUCT", "CFD_DERIVATIVE"}, {"XSWX"}, {}),
    "gt.datafeed.eodhistoricaldata": (
        {"CURRENCY_PAIR", "CRYPTOCURRENCY", "EQUITIES", "ETF", "ISSUER_RISK_PRODUCT",
         "CFD_DERIVATIVE"}, set(), {}),
    "gt.datafeed.divvydiary": ({"EQUITIES", "ETF"}, set(), {}),
    "gt.datafeed.xetra": (
        {"EQUITIES", "FIXED_INCOME", "ETF", "MUTUAL_FUND", "REAL_ESTATE_FUND",
         "ISSUER_RISK_PRODUCT", "CFD_DERIVATIVE"}, {"XETR", "XFRA"}, {}),
    "gt.datafeed.fxubc": ({"CURRENCY_PAIR"}, set(), {}),
    "gt.datafeed.finanzench": (
        {"CURRENCY_PAIR", "NON_INVESTABLE_INDICES", "EQUITIES", "FIXED_INCOME",
         "ETF", "MUTUAL_FUND", "ISSUER_RISK_PRODUCT", "PENSION_FUND"}, set(), {}),
    "gt.datafeed.comdirect": (
        {"CURRENCY_PAIR", "EQUITIES", "FIXED_INCOME", "ETF", "MUTUAL_FUND",
         "REAL_ESTATE_FUND", "ISSUER_RISK_PRODUCT"}, set(), {}),
    "gt.datafeed.boursorama": (
        {"CURRENCY_PAIR", "NON_INVESTABLE_INDICES", "EQUITIES", "FIXED_INCOME",
         "ETF", "MUTUAL_FUND"}, set(), {}),
    "gt.datafeed.consorsbank": ({"EQUITIES", "FIXED_INCOME", "ETF"}, set(), {}),
    "gt.datafeed.cryptocompare": ({"CRYPTOCURRENCY"}, set(), {}),
    "gt.datafeed.onvista": (
        {"CURRENCY_PAIR", "NON_INVESTABLE_INDICES", "EQUITIES", "FIXED_INCOME", "ETF",
         "ISSUER_RISK_PRODUCT"}, set(), {}),
    "gt.datafeed.finanzennet": (
        {"CURRENCY_PAIR", "NON_INVESTABLE_INDICES", "EQUITIES", "FIXED_INCOME",
         "ETF", "MUTUAL_FUND", "ISSUER_RISK_PRODUCT"}, set(), {}),
    "gt.datafeed.vienna": (
        {"NON_INVESTABLE_INDICES", "EQUITIES", "FIXED_INCOME", "ETF", "MUTUAL_FUND",
         "ISSUER_RISK_PRODUCT"}, {"XVIE"}, {}),
    "gt.datafeed.stockdata": (
        {"CURRENCY_PAIR", "CRYPTOCURRENCY", "EQUITIES", "ETF", "ISSUER_RISK_PRODUCT"}, set(), {}),
    "gt.datafeed.euronext": (
        {"NON_INVESTABLE_INDICES", "EQUITIES", "FIXED_INCOME", "ETF", "MUTUAL_FUND",
         "REAL_ESTATE_FUND", "ISSUER_RISK_PRODUCT"}, set(), {}),
    "gt.datafeed.investing": (
        {"CURRENCY_PAIR", "NON_INVESTABLE_INDICES", "EQUITIES", "FIXED_INCOME", "ETF",
         "ISSUER_RISK_PRODUCT", "CFD_DERIVATIVE"}, set(), {}),
    "gt.datafeed.fred": ({"NON_INVESTABLE_INDICES"}, set(), {}),
    "gt.datafeed.warsawgpw": ({"NON_INVESTABLE_INDICES", "EQUITIES", "ETF"}, {"XWAR"}, {}),
    "gt.datafeed.finnhub": ({"EQUITIES", "ETF"}, {"US"}, {}),
    "gt.datafeed.twelvedata": (
        {"CURRENCY_PAIR", "CRYPTOCURRENCY", "NON_INVESTABLE_INDICES", "EQUITIES", "ETF"}, set(), {}),
    "gt.datafeed.ecb": ({"CURRENCY_PAIR"}, set(), {}),
    "gt.datafeed.swissfunddata": (
        {"ETF", "MUTUAL_FUND", "REAL_ESTATE_FUND", "ISSUER_RISK_PRODUCT", "PENSION_FUND"}, set(), {}),
    "gt.datafeed.alphavantage": ({"EQUITIES", "ETF"}, set(), {}),
}

# AssetclassCategory.matches(AssetclassType, SpecialInvestmentInstruments)
def category_matches(cat, act, sii):
    if cat == "CURRENCY_PAIR": return act == "CURRENCY_PAIR" and sii == "FOREX"
    if cat == "CRYPTOCURRENCY": return act == "CURRENCY_PAIR" and sii == "CFD"
    if cat == "NON_INVESTABLE_INDICES": return sii == "NON_INVESTABLE_INDICES"
    if cat == "EQUITIES": return act == "EQUITIES" and sii == "DIRECT_INVESTMENT"
    if cat == "FIXED_INCOME": return act == "FIXED_INCOME" and sii == "DIRECT_INVESTMENT"
    if cat == "ETF": return sii == "ETF"
    if cat == "MUTUAL_FUND": return sii == "MUTUAL_FUND"
    if cat == "REAL_ESTATE_FUND": return act == "REAL_ESTATE" and sii == "MUTUAL_FUND"
    if cat == "ISSUER_RISK_PRODUCT": return sii == "ISSUER_RISK_PRODUCT"
    if cat == "CFD_DERIVATIVE": return sii == "CFD" and act != "CURRENCY_PAIR"
    if cat == "PENSION_FUND": return sii == "PENSION_FUNDS"
    return False

# BaseFeedConnector.supports(mic, countryCode, act, sii)
def supports(meta, mic, country, act, sii):
    categories, geo_restrictions, geo_exclusions = meta
    if not categories:
        return True
    if geo_restrictions:
        if not ((mic and mic in geo_restrictions) or (country and country in geo_restrictions)):
            return False
    for cat in categories:
        if category_matches(cat, act, sii):
            if geo_exclusions:
                if mic and mic in geo_exclusions and cat in geo_exclusions[mic]:
                    return False
                if country and country in geo_exclusions[country] and cat in geo_exclusions[country]:
                    return False
            return True
    return False

def mysql_query(sql):
    result = subprocess.run(
        [MYSQL, f"--user={DB_USER}", f"--password={DB_PASS}", f"--database={DB_NAME}",
         "--skip-column-names", "--batch", "--default-character-set=utf8mb4", "--execute", sql],
        capture_output=True, text=True, encoding="utf-8")
    if result.returncode != 0:
        sys.stderr.write(result.stderr)
        sys.exit(1)
    return [line.split("\t") for line in result.stdout.strip().split("\n") if line]

# Load generic connector definitions from DB
GENERIC = {}
for row in mysql_query(
    "SELECT short_id, IFNULL(supported_categories,''), IFNULL(geo_restrictions,'') FROM generic_connector_def"):
    # pad missing trailing empty fields (mysql --batch can omit trailing empty cols inconsistently)
    while len(row) < 3:
        row.append("")
    short_id, cats, geos = row[0], row[1], row[2]
    cat_set = set(c.strip() for c in cats.split(",") if c.strip()) if cats else set()
    geo_set = set()
    geo_excl = {}
    if geos:
        for tok in geos.split():
            if ":-" in tok:
                g, cat = tok.split(":-", 1)
                geo_set.add(g)
                geo_excl.setdefault(g, set()).add(cat)
            else:
                geo_set.add(tok)
    GENERIC[f"gt.datafeed.{short_id}"] = (cat_set, geo_set, geo_excl)

ALL_CONNECTORS = {**STATIC_CONNECTORS, **GENERIC}

# Security rows: id, name, mic, country, act_byte, sii_byte, slot, conn_id
security_rows = mysql_query("""
SELECT s.id_securitycurrency, s.name, IFNULL(se.mic,''), IFNULL(se.country_code,''),
       ac.category_type, ac.spec_invest_instrument,
       sc.id_connector_history, sc.id_connector_intra,
       s.id_connector_dividend, s.id_connector_split
FROM security s
JOIN securitycurrency sc ON s.id_securitycurrency = sc.id_securitycurrency
JOIN stockexchange se ON s.id_stockexchange = se.id_stockexchange
JOIN assetclass ac ON s.id_asset_class = ac.id_asset_class
WHERE s.active_to_date > CURDATE()
""")

# Crypto currencies (matches GlobalConstants.CRYPTO_CURRENCY_SUPPORTED)
CRYPTO = {"BTC", "BNB", "ETH", "ETC", "LTC", "XRP"}

# Currencypair rows: id, from, to, conn_history, conn_intra
cp_rows = mysql_query("""
SELECT sc.id_securitycurrency, cp.from_currency, cp.to_currency,
       sc.id_connector_history, sc.id_connector_intra
FROM currencypair cp
JOIN securitycurrency sc ON cp.id_securitycurrency = sc.id_securitycurrency
""")

# Evaluate
failures = []          # (kind, id, name/pair, slot, conn_id, reason)
orphan_connectors = Counter()  # connector_id -> count of rows referencing it that don't exist
pass_counts = Counter()
fail_counts = Counter()

def evaluate(kind, entity_id, name, slot, conn_id, mic, country, act, sii):
    if not conn_id:
        return
    meta = ALL_CONNECTORS.get(conn_id)
    if meta is None:
        orphan_connectors[conn_id] += 1
        failures.append((kind, entity_id, name, slot, conn_id,
                         f"ORPHAN connector (not registered as bean)"))
        fail_counts[conn_id] += 1
        return
    if supports(meta, mic, country, act, sii):
        pass_counts[conn_id] += 1
    else:
        cats = sorted(meta[0])
        geo = sorted(meta[1])
        reason_parts = []
        # Re-run the steps to explain
        if meta[0] and meta[1]:
            geo_ok = (mic in meta[1]) or (country in meta[1])
            if not geo_ok:
                reason_parts.append(f"geo {mic}/{country} not in {sorted(meta[1])}")
        if meta[0]:
            any_cat = any(category_matches(c, act, sii) for c in meta[0])
            if not any_cat:
                reason_parts.append(
                    f"no category matches ({act}, {sii}); supports {sorted(meta[0])}")
        reason = "; ".join(reason_parts) or "rejected"
        failures.append((kind, entity_id, name, slot, conn_id, reason))
        fail_counts[conn_id] += 1

for row in security_rows:
    sid, name, mic, country, act_b, sii_b, ch, ci, cd, csp = row
    act_b = int(act_b) if act_b else None
    sii_b = int(sii_b) if sii_b else None
    act = AC_TYPE.get(act_b)
    sii = SII.get(sii_b)
    for slot, conn in [("HISTORY", ch), ("INTRA", ci), ("DIVIDEND", cd), ("SPLIT", csp)]:
        if conn and conn != "NULL":
            evaluate("SECURITY", sid, name, slot, conn, mic, country, act, sii)

for row in cp_rows:
    sid, fc, tc, ch, ci = row
    is_crypto = (fc in CRYPTO) or (tc in CRYPTO)
    sii = "CFD" if is_crypto else "FOREX"
    pair = f"{fc}/{tc}"
    for slot, conn in [("HISTORY", ch), ("INTRA", ci)]:
        if conn and conn != "NULL":
            evaluate("CURRENCYPAIR", sid, pair, slot, conn, None, None, "CURRENCY_PAIR", sii)

# Report
print(f"\n{'='*78}")
print(f"DRY-RUN AUDIT: BaseFeedConnector.supports(...) against grafioschtrader DB")
print(f"{'='*78}")
print(f"\nTotal connector-slot evaluations: {sum(pass_counts.values()) + sum(fail_counts.values())}")
print(f"  Would PASS: {sum(pass_counts.values())}")
print(f"  Would FAIL: {sum(fail_counts.values())}")

print(f"\n--- Pass/fail per connector ---")
all_conns = sorted(set(pass_counts) | set(fail_counts))
print(f"{'Connector':<35} {'PASS':>6} {'FAIL':>6} {'%FAIL':>7}")
for c in all_conns:
    p = pass_counts[c]
    f = fail_counts[c]
    total = p + f
    pct = (f * 100 // total) if total else 0
    flag = " <-- ALL FAIL" if p == 0 and f > 0 else ""
    print(f"{c:<35} {p:>6} {f:>6} {pct:>6}%{flag}")

if orphan_connectors:
    print(f"\n--- Orphan connector IDs (no matching bean / generic_connector_def) ---")
    for c, cnt in orphan_connectors.most_common():
        print(f"  {c}: {cnt} references")

print(f"\n--- Failure reason breakdown ---")
reason_counter = Counter()
for f in failures:
    reason_counter[(f[4], f[5])] += 1
for (conn, reason), cnt in sorted(reason_counter.items(), key=lambda x: -x[1])[:50]:
    print(f"  [{cnt:>4}x] {conn} :: {reason}")

print(f"\n--- Sample failures (first 30) ---")
for f in failures[:30]:
    kind, eid, name, slot, conn, reason = f
    print(f"  {kind:<13} #{eid:<6} {name[:40]:<40} {slot:<8} {conn:<35} {reason}")
