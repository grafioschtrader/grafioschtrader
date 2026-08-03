#!/usr/bin/env python3
"""Read-only coverage probe for the sector-classification concept.

Measures how much of a live GT installation's security master data can be classified by sector
*automatically*, using the two candidate free sources, before any schema is designed:

  Pass 1  FinanceDatabase (github.com/JerBouma/FinanceDatabase, MIT) - offline CSV snapshot,
          matched on ``mic + symbol`` with ISIN as a secondary key. Covers single stocks only.
  Pass 2  Yahoo ``quoteSummary?modules=assetProfile,topHoldings`` - covers ETFs and funds (the
          only free source for a sector *distribution*) and acts as the equity fallback.

Nothing is ever written to the database: the single statement issued is a SELECT, and the script
refuses to run any statement that does not start with ``SELECT``. Downloaded files and reports go
to a cache/output directory outside the repository.

Usage
    python scripts/probe_sector_coverage.py --user=grafioschtrader --password=... \
        [--database=grafioschtrader] [--host=localhost] \
        [--out-dir=DIR] [--cache-dir=DIR] [--skip-yahoo] [--limit=N] [--dry-run]

    --dry-run   resolves the mysql client, prints the SQL and the FinanceDatabase file list that
                would be fetched, and exits without touching the network or the database.

Environment overrides
    GT_MYSQL_BIN   Path to the mariadb/mysql CLI (default: `mariadb`/`mysql` on PATH, then
                   C:\\xampp\\mysql\\bin\\mysql.exe on Windows) - same convention as
                   scripts/e2e-test.mjs and scripts/check-hold-tables.mjs.

Standard library only - no pip install required. The database is read through the mysql CLI
rather than a driver so the script runs on a machine with no Python MySQL package installed.

Exit code 0 = report produced, >0 = the script itself failed.
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path

IS_WIN = os.name == "nt"

FINDB_API = "https://api.github.com/repos/JerBouma/FinanceDatabase/contents/database/{kind}"
FINDB_RAW = "https://raw.githubusercontent.com/JerBouma/FinanceDatabase/main/database/{kind}/{name}"

YAHOO_COOKIE_URL = "https://fc.yahoo.com/"
YAHOO_CRUMB_URL = "https://query2.finance.yahoo.com/v1/test/getcrumb"
YAHOO_SUMMARY_URL = ("https://query1.finance.yahoo.com/v10/finance/quoteSummary/{symbol}"
                     "?modules=assetProfile,topHoldings")
YAHOO_SEARCH_URL = "https://query2.finance.yahoo.com/v1/finance/search?q={q}&quotesCount=6&newsCount=0"

USER_AGENT = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")

# grafioschtrader.types.SpecialInvestmentInstruments
SPEC_INSTRUMENT = {
    0: "DIRECT_INVESTMENT", 1: "ETF", 2: "MUTUAL_FUND", 3: "PENSION_FUNDS",
    4: "CFD", 5: "FOREX", 6: "ISSUER_RISK_PRODUCT", 10: "NON_INVESTABLE_INDICES",
}
# grafioschtrader.types.AssetclassType
ASSETCLASS_TYPE = {
    0: "EQUITIES", 1: "FIXED_INCOME", 2: "MONEY_MARKET", 3: "COMMODITIES", 4: "REAL_ESTATE",
    5: "MULTI_ASSET", 6: "CONVERTIBLE_BOND", 7: "CREDIT_DERIVATIVE", 8: "CURRENCY_PAIR",
    11: "CURRENCY_CASH", 12: "CURRENCY_FOREIGN",
}
# Instruments whose sector exposure is a distribution rather than a single value.
FUND_LIKE = {"ETF", "MUTUAL_FUND", "PENSION_FUNDS"}

# Yahoo ticker suffix per MIC, used only when the security carries no Yahoo symbol already.
MIC_YAHOO_SUFFIX = {
    "XSWX": ".SW", "XSWM": ".SW", "XVTX": ".SW", "XETR": ".DE", "XFRA": ".F", "XSTU": ".SG",
    "XNAS": "", "XNYS": "", "ARCX": "", "BATS": "", "XASE": "",
    "XLON": ".L", "XPAR": ".PA", "XAMS": ".AS", "XBRU": ".BR", "XMIL": ".MI", "XMAD": ".MC",
    "XLIS": ".LS", "XSTO": ".ST", "XCSE": ".CO", "XHEL": ".HE", "XOSL": ".OL", "XICE": ".IC",
    "XWBO": ".VI", "XPRA": ".PR", "XWAR": ".WA", "XBUD": ".BD", "XATH": ".AT",
    "XTSE": ".TO", "XTSX": ".V", "XASX": ".AX", "XNZE": ".NZ", "XTKS": ".T", "XHKG": ".HK",
    "XSES": ".SI", "XKRX": ".KS", "XTAI": ".TW", "XBOM": ".BO", "XNSE": ".NS",
    "XJSE": ".JO", "BVMF": ".SA", "XMEX": ".MX", "XSGO": ".SN", "XBUE": ".BA", "XTAE": ".TA",
}

SQL = """
SELECT s.id_securitycurrency, s.name, IFNULL(s.isin,''), IFNULL(s.ticker_symbol,''),
       s.currency, s.active_to_date, IFNULL(e.mic,''), a.category_type, a.spec_invest_instrument,
       IFNULL(sc.id_connector_history,''), IFNULL(sc.url_history_extend,''),
       IFNULL(s.id_tenant_private, 0)
FROM security s
JOIN securitycurrency sc ON sc.id_securitycurrency = s.id_securitycurrency
JOIN stockexchange e ON e.id_stockexchange = s.id_stockexchange
JOIN assetclass a ON a.id_asset_class = s.id_asset_class
ORDER BY s.id_securitycurrency
"""


# --------------------------------------------------------------------------------------------
# database access (read-only)
# --------------------------------------------------------------------------------------------

def resolve_mysql_bin() -> str:
    """Locate the mariadb/mysql CLI, following the same convention as the .mjs scripts."""
    tried = []
    env = os.environ.get("GT_MYSQL_BIN")
    if env:
        if Path(env).is_file():
            return env
        tried.append(f"GT_MYSQL_BIN={env}")
    for name in ("mariadb", "mysql"):
        found = shutil.which(name)
        if found:
            return found
        tried.append(name)
    xampp = r"C:\xampp\mysql\bin\mysql.exe"
    if IS_WIN and Path(xampp).is_file():
        return xampp
    tried.append(xampp)
    raise SystemExit("No mariadb/mysql client found (tried: " + ", ".join(tried)
                     + "). Set GT_MYSQL_BIN to the full path of the executable.")


def query_securities(args) -> list[dict]:
    """Run the single SELECT and return one dict per security. Read-only by construction."""
    statement = SQL.strip()
    if not statement.upper().startswith("SELECT"):
        raise SystemExit("refusing to run a non-SELECT statement")

    cmd = [resolve_mysql_bin(), f"--host={args.host}", f"--user={args.user}",
           "--batch", "--raw", "--skip-column-names", "--default-character-set=utf8mb4",
           args.database, "-e", statement]
    env = dict(os.environ)
    if args.password:
        env["MYSQL_PWD"] = args.password  # keeps the password off the process command line
    proc = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", env=env)
    if proc.returncode != 0:
        raise SystemExit(f"mysql client failed ({proc.returncode}):\n{proc.stderr.strip()}")

    rows = []
    for line in proc.stdout.splitlines():
        if not line.strip():
            continue
        f = line.split("\t")
        if len(f) < 12:
            continue
        rows.append({
            "id": int(f[0]), "name": f[1], "isin": f[2].strip().upper(),
            "ticker": f[3].strip().upper(), "currency": f[4], "active_to": f[5],
            "mic": f[6].strip().upper(),
            "assetclass": ASSETCLASS_TYPE.get(int(f[7]), f"?{f[7]}"),
            "instrument": SPEC_INSTRUMENT.get(int(f[8]), f"?{f[8]}"),
            "conn_history": f[9], "url_history": f[10],
            "private": f[11] not in ("0", ""),
        })
    return rows


# --------------------------------------------------------------------------------------------
# Pass 1 - FinanceDatabase
# --------------------------------------------------------------------------------------------

def http_get(url: str, headers: dict | None = None, timeout: int = 60) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, **(headers or {})})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


def findb_file_list(kind: str) -> list[str]:
    """Names of the per-exchange CSV files in database/<kind>/."""
    data = json.loads(http_get(FINDB_API.format(kind=kind)).decode("utf-8"))
    return sorted(e["name"] for e in data if e["type"] == "file" and e["name"].endswith(".csv"))


def findb_download(kind: str, names: list[str], cache: Path) -> list[Path]:
    """Download the CSVs into the cache, skipping any already present."""
    target_dir = cache / kind
    target_dir.mkdir(parents=True, exist_ok=True)
    paths = []
    for i, name in enumerate(names, 1):
        path = target_dir / name
        if not path.exists():
            print(f"  [{i:>3}/{len(names)}] {kind}/{name}", flush=True)
            try:
                path.write_bytes(http_get(FINDB_RAW.format(kind=kind, name=name)))
            except urllib.error.HTTPError as exc:
                print(f"      skipped ({exc.code})")
                continue
        paths.append(path)
    return paths


def base_symbol(symbol: str) -> str:
    """`ABBN.SW` -> `ABBN`. GT stores at most 6 characters and no exchange suffix."""
    return symbol.split(".", 1)[0].strip().upper()


def load_findb(paths: list[Path], kind: str, stats: dict) -> tuple[dict, dict]:
    """Index FinanceDatabase rows by ``(mic, base symbol)`` and by ISIN.

    The `summary` column is deliberately never read - it contains double-encoded mojibake in the
    upstream files (see the concept document, section 3.2).
    """
    by_mic_symbol, by_isin = {}, {}
    for path in paths:
        with path.open("r", encoding="utf-8", errors="replace", newline="") as fh:
            for row in csv.DictReader(fh):
                symbol = (row.get("symbol") or "").strip()
                mic = (row.get("mic") or "").strip().upper()
                isin = (row.get("isin") or "").strip().upper()
                if not symbol:
                    continue
                stats["rows"] += 1
                if isin:
                    stats["rows_with_isin"] += 1
                if kind == "equities":
                    entry = {
                        "sector": (row.get("sector") or "").strip(),
                        "industry_group": (row.get("industry_group") or "").strip(),
                        "industry": (row.get("industry") or "").strip(),
                        "delisted": (row.get("delisted") or "").strip(),
                    }
                    if not entry["sector"]:
                        continue
                else:  # etfs - recorded for the report only; category is NOT a sector dimension
                    entry = {
                        "category_group": (row.get("category_group") or "").strip(),
                        "category": (row.get("category") or "").strip(),
                    }
                stats["classified"] += 1
                if mic:
                    by_mic_symbol.setdefault((mic, base_symbol(symbol)), entry)
                if isin:
                    by_isin.setdefault(isin, entry)
    return by_mic_symbol, by_isin


# --------------------------------------------------------------------------------------------
# Pass 2 - Yahoo
# --------------------------------------------------------------------------------------------

class Yahoo:
    """Minimal quoteSummary client: cookie + crumb, disk-cached, throttled."""

    def __init__(self, cache: Path, delay: float = 1.0):
        self.cache = cache
        self.cache.mkdir(parents=True, exist_ok=True)
        self.delay = delay
        self.cookie = None
        self.crumb = None
        self.failures = Counter()

    def _authenticate(self) -> bool:
        if self.crumb:
            return True
        try:
            req = urllib.request.Request(YAHOO_COOKIE_URL, headers={"User-Agent": USER_AGENT})
            try:
                resp = urllib.request.urlopen(req, timeout=30)
                raw = resp.headers.get_all("Set-Cookie") or []
            except urllib.error.HTTPError as exc:          # 404 still carries the cookie
                raw = exc.headers.get_all("Set-Cookie") or []
            self.cookie = "; ".join(c.split(";", 1)[0] for c in raw)
            if not self.cookie:
                self.failures["no-cookie"] += 1
                return False
            self.crumb = http_get(YAHOO_CRUMB_URL, {"Cookie": self.cookie}).decode("utf-8").strip()
            return bool(self.crumb) and "<" not in self.crumb
        except Exception as exc:                            # noqa: BLE001 - probe, report and go on
            self.failures[f"auth:{type(exc).__name__}"] += 1
            return False

    def _fetch(self, url: str, cache_key: str) -> dict | None:
        path = self.cache / f"{cache_key}.json"
        if path.exists():
            try:
                return json.loads(path.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                path.unlink()
        if not self._authenticate():
            return None
        time.sleep(self.delay)
        try:
            body = http_get(f"{url}&crumb={urllib.parse.quote(self.crumb)}",
                            {"Cookie": self.cookie, "Accept": "application/json"})
        except urllib.error.HTTPError as exc:
            self.failures[f"http-{exc.code}"] += 1
            if exc.code in (401, 403):                      # crumb expired - force re-auth once
                self.crumb = None
            return None
        except Exception as exc:                            # noqa: BLE001
            self.failures[type(exc).__name__] += 1
            return None
        path.write_bytes(body)
        try:
            return json.loads(body.decode("utf-8"))
        except json.JSONDecodeError:
            self.failures["bad-json"] += 1
            return None

    def search_symbol(self, isin: str) -> str | None:
        data = self._fetch(YAHOO_SEARCH_URL.format(q=urllib.parse.quote(isin)), f"search_{isin}")
        for quote in (data or {}).get("quotes", []):
            if quote.get("symbol"):
                return quote["symbol"]
        return None

    def profile(self, symbol: str) -> dict | None:
        safe = urllib.parse.quote(symbol, safe="")
        data = self._fetch(YAHOO_SUMMARY_URL.format(symbol=safe), f"summary_{safe}")
        result = ((data or {}).get("quoteSummary") or {}).get("result") or []
        return result[0] if result else None


def yahoo_symbol_for(sec: dict) -> str | None:
    """Prefer the Yahoo symbol GT already stores; otherwise derive one from ticker + MIC."""
    if sec["conn_history"] == "gt.datafeed.yahoo" and sec["url_history"].strip():
        return sec["url_history"].strip()
    if sec["ticker"] and sec["mic"] in MIC_YAHOO_SUFFIX:
        return sec["ticker"] + MIC_YAHOO_SUFFIX[sec["mic"]]
    return None


def extract_weights(profile: dict) -> tuple[list[tuple[str, float]], str]:
    """Return ``([(provider key, percent)], kind)`` from a quoteSummary result."""
    top = profile.get("topHoldings") or {}
    weightings = top.get("sectorWeightings") or []
    weights = []
    for item in weightings:
        for key, value in item.items():
            raw = value.get("raw") if isinstance(value, dict) else value
            if isinstance(raw, (int, float)) and raw > 0:
                weights.append((key, round(raw * 100.0, 2)))
    if weights:
        return weights, "weights"
    asset = profile.get("assetProfile") or {}
    sector = asset.get("sector") or asset.get("sectorKey")
    if sector:
        return [(sector, 100.0)], "single"
    return [], "none"


# --------------------------------------------------------------------------------------------
# reporting
# --------------------------------------------------------------------------------------------

def rate(hit: int, total: int) -> str:
    return f"{hit:>5}/{total:<5} {(100.0 * hit / total if total else 0.0):5.1f}%"


def grouped_table(title: str, rows: list[dict], key: str, min_count: int = 1) -> None:
    buckets = defaultdict(lambda: [0, 0, 0])  # total, pass1, resolved
    for r in rows:
        b = buckets[r[key] or "(none)"]
        b[0] += 1
        b[1] += 1 if r["findb"] else 0
        b[2] += 1 if r["resolved"] else 0
    print(f"\n{title}")
    print(f"  {'':<22} {'total':>6} {'FinDB':>16} {'combined':>16}")
    for name, (total, p1, res) in sorted(buckets.items(), key=lambda kv: -kv[1][0]):
        if total < min_count:
            continue
        print(f"  {name:<22} {total:>6} {rate(p1, total):>16} {rate(res, total):>16}")


def write_csv(path: Path, header: list[str], rows) -> None:
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(header)
        writer.writerows(rows)
    print(f"  wrote {path}")


# --------------------------------------------------------------------------------------------

def main() -> int:
    default_tmp = Path(os.environ.get("TEMP", "/tmp")) / "gt-sector-probe"
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--user", default="grafioschtrader")
    parser.add_argument("--password", default=os.environ.get("GT_DB_PASSWORD", ""))
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--database", default="grafioschtrader")
    parser.add_argument("--cache-dir", default=str(default_tmp / "cache"))
    parser.add_argument("--out-dir", default=str(default_tmp / "report"))
    parser.add_argument("--skip-yahoo", action="store_true", help="run pass 1 only")
    parser.add_argument("--limit", type=int, default=0, help="cap the number of Yahoo lookups")
    parser.add_argument("--delay", type=float, default=1.0, help="seconds between Yahoo requests")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    cache, out = Path(args.cache_dir), Path(args.out_dir)

    if args.dry_run:
        print(f"mysql client : {resolve_mysql_bin()}")
        print(f"database     : {args.database} @ {args.host} as {args.user}")
        print(f"cache / out  : {cache}\n               {out}")
        print("\nThe only statement that would be issued (read-only):\n")
        print(SQL.strip())
        print("\nFinanceDatabase files that would be fetched:")
        for kind in ("equities", "etfs"):
            names = findb_file_list(kind)
            print(f"  {kind}: {len(names)} files, e.g. {', '.join(names[:8])} ...")
        print("\nNo Yahoo request was made. Re-run without --dry-run to produce the report.")
        return 0

    if not args.password:
        print("error: --password (or GT_DB_PASSWORD) is required", file=sys.stderr)
        return 2

    out.mkdir(parents=True, exist_ok=True)

    print("== reading securities (read-only) ==")
    securities = [s for s in query_securities(args)
                  if s["assetclass"] not in ("CURRENCY_PAIR", "CURRENCY_CASH", "CURRENCY_FOREIGN")]
    print(f"  {len(securities)} securities, {len(set(s['mic'] for s in securities))} MICs")

    print("\n== pass 1: FinanceDatabase ==")
    eq_stats = {"rows": 0, "rows_with_isin": 0, "classified": 0}
    etf_stats = {"rows": 0, "rows_with_isin": 0, "classified": 0}
    eq_sym, eq_isin = load_findb(findb_download("equities", findb_file_list("equities"), cache),
                                 "equities", eq_stats)
    # The ETF files are parsed for their ISIN fill rate only. `category_group` / `category` are
    # deliberately NOT used as a sector dimension - they mix asset type, geography and size.
    load_findb(findb_download("etfs", findb_file_list("etfs"), cache), "etfs", etf_stats)
    print(f"  equities: {eq_stats['rows']} rows, {eq_stats['classified']} with a sector, "
          f"ISIN present on {rate(eq_stats['rows_with_isin'], eq_stats['rows'])}")
    print(f"  etfs    : {etf_stats['rows']} rows, ISIN present on "
          f"{rate(etf_stats['rows_with_isin'], etf_stats['rows'])} - no sector weights in this source")

    findb_strings, yahoo_strings = Counter(), Counter()
    results = []
    for sec in securities:
        hit = None
        if sec["ticker"] and sec["mic"]:
            hit = eq_sym.get((sec["mic"], sec["ticker"]))
        if hit is None and sec["isin"]:
            hit = eq_isin.get(sec["isin"])
        if hit:
            findb_strings[hit["sector"]] += 1
        results.append({**sec, "findb": bool(hit),
                        "findb_sector": hit["sector"] if hit else "",
                        "findb_industry": hit["industry"] if hit else "",
                        "yahoo_kind": "", "yahoo_sectors": "", "weight_sum": "",
                        "resolved": bool(hit)})

    if not args.skip_yahoo:
        pending = [r for r in results if not r["findb"] or r["instrument"] in FUND_LIKE]
        if args.limit:
            pending = pending[:args.limit]
        print(f"\n== pass 2: Yahoo quoteSummary ({len(pending)} lookups, ~{args.delay}s apart) ==")
        yahoo = Yahoo(cache / "yahoo", args.delay)
        for i, r in enumerate(pending, 1):
            symbol = yahoo_symbol_for(r)
            if not symbol and r["isin"]:
                symbol = yahoo.search_symbol(r["isin"])
            if not symbol:
                continue
            profile = yahoo.profile(symbol)
            if not profile:
                continue
            weights, kind = extract_weights(profile)
            if not weights:
                continue
            for key, _ in weights:
                yahoo_strings[key] += 1
            r["yahoo_kind"] = kind
            r["yahoo_sectors"] = "|".join(f"{k}:{v}" for k, v in weights)
            r["weight_sum"] = round(sum(v for _, v in weights), 1)
            r["resolved"] = True
            if i % 25 == 0:
                print(f"  {i}/{len(pending)}", flush=True)
        if yahoo.failures:
            print(f"  yahoo failures: {dict(yahoo.failures)}")

    # ---- report -----------------------------------------------------------------------------
    total = len(results)
    p1 = sum(1 for r in results if r["findb"])
    res = sum(1 for r in results if r["resolved"])
    print("\n" + "=" * 78)
    print(f"OVERALL   FinanceDatabase {rate(p1, total)}   combined {rate(res, total)}")
    print("=" * 78)

    grouped_table("By MIC (>= 10 securities)", results, "mic", min_count=10)
    grouped_table("By instrument type", results, "instrument")
    grouped_table("By asset class", results, "assetclass")

    funds = [r for r in results if r["instrument"] in FUND_LIKE]
    with_weights = [r for r in funds if r["yahoo_kind"] == "weights"]
    bad_sum = [r for r in with_weights if not (95.0 <= float(r["weight_sum"]) <= 105.0)]
    print(f"\nFunds / ETFs: {len(funds)} total, {len(with_weights)} with a usable sector "
          f"distribution, {len(bad_sum)} whose weights do not sum to 100 +/- 5")

    print(f"\nDistinct provider sector strings - the seed for sector_provider_map")
    print(f"  FinanceDatabase ({len(findb_strings)}): "
          + ", ".join(f"{k} ({v})" for k, v in findb_strings.most_common()))
    print(f"  Yahoo ({len(yahoo_strings)}): "
          + ", ".join(f"{k} ({v})" for k, v in yahoo_strings.most_common()))

    write_csv(out / "resolved.csv",
              ["id", "name", "isin", "ticker", "mic", "assetclass", "instrument",
               "findb_sector", "findb_industry", "yahoo_kind", "yahoo_sectors", "weight_sum"],
              [[r["id"], r["name"], r["isin"], r["ticker"], r["mic"], r["assetclass"],
                r["instrument"], r["findb_sector"], r["findb_industry"], r["yahoo_kind"],
                r["yahoo_sectors"], r["weight_sum"]] for r in results if r["resolved"]])
    write_csv(out / "unresolved.csv",
              ["id", "name", "isin", "ticker", "mic", "assetclass", "instrument", "currency"],
              [[r["id"], r["name"], r["isin"], r["ticker"], r["mic"], r["assetclass"],
                r["instrument"], r["currency"]] for r in results if not r["resolved"]])
    write_csv(out / "provider_strings.csv", ["provider", "provider_key", "securities"],
              [["FINDB", k, v] for k, v in findb_strings.most_common()]
              + [["YAHOO", k, v] for k, v in yahoo_strings.most_common()])
    return 0


if __name__ == "__main__":
    sys.exit(main())
