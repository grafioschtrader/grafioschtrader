# Sector Classification as a Second Dimension — Concept

`Assetclass.subCategoryNLS` is a single free-text NLS string. Its Javadoc recommends a geographic
reading ("probably best suited for a geographical breakdown"), and the user manual follows that
recommendation. But the field can only carry one meaning at a time. An asset class labelled
*Global Stocks* answers the geographic question and loses the sector question; an asset class
labelled *Technology* does the opposite. In practice a *Global Stocks* holding may be a broad
world-equity ETF or an information-technology ETF, and today GT cannot tell those apart in any
evaluation.

The codebase already concedes this. `grafioschtrader-common/.../gtnet/model/SubCategoryDetector.java`
and `SubCategoryScheme.java` (`REGIONAL | SECTOR | UNKNOWN`) exist for one reason: when GTNet imports
an instrument from a foreign GT instance, it has to *guess by keyword matching* whether that
instance used its sub-categories geographically or sectorally. A dedicated dimension removes the guess.

This document proposes that dimension: where it is stored, where the data comes from, how it is kept
current without manual work, and how it reaches the evaluations.

**Status: concept for review. No code, no schema, no migration has been written.**
The coverage numbers marked `‹probe›` are filled in from `scripts/probe_sector_coverage.py`
(§7) before this document is treated as decided.

---

## 1. Decisions

| # | Decision | Choice |
|---|----------|--------|
| 1 | Storage location | **Not on `security`.** A side table set (`security_sector_weight` + `security_sector_profile`). Sector exposure is a distribution, not a scalar, and the `security` row is globally shared. |
| 2 | Cardinality | **Weighted, many-to-many.** A single stock is one row at 100 %. An ETF is *n* rows summing to 100 %. |
| 3 | Taxonomy | A dedicated `sector` table, **3 levels** (sector → industry group → industry), names via the existing `MultilanguageString`, exactly as `Assetclass.subCategoryNLS` does. |
| 4 | Provider strings | Never mapped in code. A `sector_provider_map` table maps each provider's spelling to a taxonomy node; unmapped values surface in an admin list instead of being dropped. |
| 5 | Primary data source | **Two sources, split by instrument type**: FinanceDatabase (offline, MIT) for single stocks; Yahoo `quoteSummary` for ETFs/funds. See §3. |
| 6 | Feed integration | A **standalone `ISectorFeedConnector` interface**, priority-ordered, in the style of `ISplitCalendarFeedConnector`. **Not** a new `IFeedConnector.FeedSupport` constant. See §4. |
| 7 | Manual entries | A `locked` flag on the profile row. A locked profile is never overwritten by any automatic feed. |
| 8 | Reporting | **Two views**: dominant sector inside the existing group-by table, and a separate weighted look-through allocation report. See §6. |
| 9 | Tenancy | **Global**, like `security` and `assetclass`. Edits go through the existing propose-change / audit flow. |
| 10 | `subCategoryNLS` | **Unchanged.** It keeps its geographic meaning. No migration of existing sub-categories is part of this work. |

---

## 2. Why not the obvious alternatives

**Why not a column on `security`?**
Three reasons, in order of weight. (a) An ETF has no single sector — the whole motivating example is a
*Global Stocks* ETF that is really an IT fund, and a scalar column cannot express that. (b) `security`
is a globally shared row set (no `id_tenant`; only `id_tenant_private` for private instruments), so any
column added there is subject to the propose-change workflow for every edit, including automated ones.
(c) The row would need `id_connector_sector` / `url_sector_extend` / `retry_sector_load` alongside it to
follow the existing dividend/split pattern — four columns and a `BaseFeedConnector` switch branch for
data that is not a price feed.

**Why not reuse the UDF mechanism?**
`udf_data` is keyed `(entity, id_entity, id_user)` — the values are **per user**, stored as a JSON blob,
and not indexed. That is fine for a display column (which is exactly what `YahooStaticticsLink` and
`YahooEarningNextDate` use it for) and wrong for a grouping key that must aggregate across a tenant and
be queryable.

**Why not extend `subCategoryNLS` with a convention?**
Because `SubCategoryDetector` already demonstrates where that leads: a heuristic that has to guess the
meaning of free text, with a Jaro-Winkler threshold of 0.85 and hand-maintained EN/DE keyword lists.

**Why not `AlgoAssetclass.name`?**
It already exists as a freeform second axis ("Optional custom category name … e.g. 'Gambling Games'"),
but it is scoped to algorithmic-trading strategies, is mutually exclusive with the asset class, and is
manually maintained — the opposite of what is wanted here.

---

## 3. Data sources

### 3.1 The requirement, split in two

The problem has two halves and no single free source covers both:

| | Single stocks | ETFs / funds |
|---|---|---|
| What is needed | one sector | a **distribution** over sectors |
| Why it is hard | nothing — most providers have it | almost no free provider publishes the weights |

### 3.2 FinanceDatabase (github.com/JerBouma/FinanceDatabase)

Verified against the actual files, not the README.

**Equities** — `database/equities/<EXCHANGE>.csv`, one file per exchange:

```
symbol,name,summary,currency,sector,industry_group,industry,exchange,mic,market,
country,state,city,zipcode,website,market_cap,isin,cusip,figi,composite_figi,shareclass_figi,delisted
```

A row from `EBS.csv` (SIX Swiss Exchange, 806 rows):

```
ABBN.SW,ABB Ltd,"…",CHF,Industrials,Capital Goods,Electrical Equipment,EBS,XSWX,
BX Worldcaps,Switzerland,,Zurich,8050,http://www.global.abb/group/,Large Cap,CH0012221716,…,False
```

What makes it a good fit:

- **It carries `mic`** (`XSWX`). GT's `Stockexchange` already stores the MIC, so `mic + symbol` is a
  direct join key and ISIN is not required.
- A real **3-level hierarchy** — `sector → industry_group → industry`, GICS-style naming
  (`Information Technology / Software & Services / IT Services`), 11 sectors and 68 industries.
  This is a better taxonomy seed than Yahoo's flat 11-sector list.
- **MIT licensed**, so the taxonomy and the mapping rows may be redistributed and seeded into a
  Flyway migration.
- Offline: one file set, **zero API calls, no key, no rate limit, no cookie/crumb fragility**.
- `delisted` flag, so securities GT still holds but that no longer trade are still matchable.
- Freshness is better than the README's "US weekly, rest community-driven" implies — as of
  2026-08-02: `equities/EBS.csv` last changed 2026-07-12, `equities/GER.csv` and `equities/NMS.csv`
  2026-07-26.

Three caveats that must not be glossed over. The first two are measured, not estimated — the probe
script's parser was run over `EBS.csv` (SIX, the exchange with by far the most GT securities):

| `database/…/EBS.csv` | records | with a sector | with an ISIN |
|---|---|---|---|
| `equities/EBS.csv` | 805 | 608 (75.5 %) | 607 (75.4 %) |
| `etfs/EBS.csv` | 1 461 | — (no sector data) | 680 (46.5 %) |

- **A quarter of the SIX equity rows carry no sector at all.** FinanceDatabase is a baseline, not a
  complete answer; Yahoo has to cover the remainder. This is the single strongest argument for the
  two-source design in §3.5 rather than picking one.
- **`isin` is sparse** — 75 % on equities, 47 % on ETFs. ISIN is therefore a *secondary* key and
  `mic + symbol` must be the primary one. Good news for GT, whose `Stockexchange` already stores the MIC.
- **The `summary` column is corrupt in places** — double-encoded mojibake (`Z√É¬ºrich`,
  `M√É¬©ni√É¬®re's`). Only `symbol / mic / sector / industry_group / industry / isin` are read; nothing
  ingests `summary`. Given the strict UTF-8 guards on GT's NLS bundles (`NlsBundleGuardTest`), this is
  worth stating explicitly rather than discovering later. It also means the files must be parsed with a
  real CSV reader: `summary` contains embedded newlines, so `etfs/EBS.csv` is 1 832 physical lines but
  only 1 461 records.

**ETFs** — `database/etfs/<EXCHANGE>.csv`:

```
symbol,name,currency,summary,category_group,category,family,exchange,mic,isin
```

**There are no sector weights, and `category_group`/`category` must not be used as a sector
dimension.** From the same SIX file:

| Instrument | `category_group` | `category` |
|---|---|---|
| Amundi MSCI EM Asia | `Financials` | `Emerging Markets` |
| 21Shares Bitcoin ETP | `Currencies` | *(empty)* |
| UBS ETF FTSE 100 | *(empty)* | `Large Cap` |

That is asset type, geography and size collapsed into one field — the exact overloading this concept
exists to fix. Using it would reproduce the bug in a new table.

### 3.3 Yahoo `quoteSummary`

`GET https://query1.finance.yahoo.com/v10/finance/quoteSummary/{symbol}?modules=assetProfile,topHoldings`

- `assetProfile` → `sector`, `sectorKey`, `sectorDisp`, `industry`, `industryKey`, `industryDisp`
- `topHoldings` → `sectorWeightings[]` — **the only free source for the ETF/fund distribution**,
  plus `stockPosition` / `bondPosition` for sanity checks

GT already owns the difficult parts: cookie/crumb handling in `connector/yahoo/CrumbManager.java`,
ISIN→symbol resolution in `connector/yahoo/YahooSymbolSearch.java`, and bucket4j throttling
(`BaseFeedConnector.waitForTokenOrGo`, used at 2 req / 2 s by `YahooUDFConnect`). It is an unofficial
API, which is why it is the *second* source rather than the only one.

### 3.4 Everything else considered

| Source | Equity sector | ETF sector weights | Key | Cost | GT status |
|---|---|---|---|---|---|
| **FinanceDatabase** | 3 levels | — | `mic`+`symbol`, ISIN sparse | free, MIT | not integrated |
| **Yahoo `quoteSummary`** | `sector`, `sectorKey`, `industry` | `sectorWeightings[]` | symbol | free, no key | crumb + symbol search present |
| EODHD `/fundamentals` | `Sector`, `Industry`, `GicSector`…`GicSubIndustry` | `ETF_Data.Sector_Weights` | ISIN or symbol | ~USD 20/mo (All-World) | connector + `connector_apikey` slot exist |
| Finnhub `/stock/profile2` | `finnhubIndustry` (own taxonomy) | — | symbol | free tier, weak non-US | connector exists, endpoint unused |
| Twelve Data `/profile` | sector, industry | — | symbol | intl from USD 29/mo | connector exists |
| Alpha Vantage `OVERVIEW` | Sector, Industry | — | symbol | ~25 req/day free | connector exists |
| OpenFIGI | `marketSector` = *asset class* (Equity/Corp/Govt), **not** industry | — | ISIN accepted as input | free | not integrated |
| GLEIF LEI | — (legal entity data only) | — | LEI / ISIN | free | not integrated |
| ESMA FIRDS | — (CFI codes, not industry) | — | ISIN | free non-commercial | not integrated |

### 3.5 Recommendation

**Use FinanceDatabase and Yahoo together**, each for the half it covers:

- FinanceDatabase is the bulk offline baseline for single stocks. It resolves the large majority of
  GT's directly-held equities with no network traffic at all.
- Yahoo covers ETFs and funds — where the weights are the whole point — and acts as the fallback for
  equities FinanceDatabase misses, and as the refresh path.

This is strictly better than a Yahoo-only design: it removes most of the request volume, most of the
exposure to Yahoo's cookie/crumb fragility, and gives a licence-clean, versionable seed for the
taxonomy. EODHD remains the paid escape hatch if `‹probe›` shows free ETF coverage is inadequate; the
connector interface in §4 makes swapping it in a one-class change.

**Licensing note.** GICS codes and the GICS hierarchy are licensed by MSCI/S&P. GT must not present
itself as publishing GICS data. FinanceDatabase is MIT-licensed and its sector/industry *names* are
used descriptively; the `sector` table stores GT's own `code` values with NLS names and is not a GICS
redistribution. If this is ever in doubt, the taxonomy can be renamed without touching anything else,
because every provider string reaches it through `sector_provider_map` (§5.2).

---

## 4. Feed integration — a separate connector kind

### 4.1 Why not `FeedSupport.FS_SECTOR`

Adding a fifth constant to `IFeedConnector.FeedSupport` would require:

1. new `FeedIdentifier` constants and new interface methods on `IFeedConnector`;
2. default `UnsupportedOperationException` implementations in `BaseFeedConnector` plus a new `case`
   in `checkAndClearSecuritycurrencyUrlExtend`;
3. `id_connector_sector` / `url_sector_extend` / `retry_sector_load` columns on `security` and a
   migration, mirroring the dividend/split pattern at `Security.java:180–219`;
4. frontend connector-select changes and NLS keys.

All of that for data that is not a price series and needs no per-security URL.

### 4.2 The pattern to follow instead

Split and dividend calendars were added as **standalone interfaces** injected as a priority-ordered
list (`ISplitCalendarFeedConnector`, `IDividendCalendarFeedConnector`, consumed by
`SplitCalendarAppender` via `@Autowired(required = false) List<…>`). Zero `security` schema churn.
Do the same:

```java
// backend/grafioschtrader-server/src/main/java/grafioschtrader/connector/sector/

public interface ISectorFeedConnector {
  String getShortId();
  int getPriority();
  boolean supports(Security security);
  Optional<SectorProfileRaw> getSectorProfile(Security security) throws Exception;
}

public record SectorProfileRaw(String provider, List<RawWeight> weights) { }
public record RawWeight(String providerKey, double weightPercent) { }
```

The resolver walks the list in priority order and takes the first non-empty result.

### 4.3 `FinanceDatabaseSectorConnector`

- Higher priority; `supports()` returns true for direct equities.
- Reads a CSV snapshot into an in-memory map keyed `mic + symbol`, ISIN as the secondary key.
  Loaded on `ApplicationReadyEvent` — the lifecycle `GenericFeedConnectorFactory` already uses.
- Only `symbol / mic / sector / industry_group / industry / isin / delisted` are parsed.
  `summary` is never read (§3.2).
- Result: one `RawWeight` at 100 % on the *industry* node; the sector and industry-group levels are
  reachable through the taxonomy parent chain, so nothing is lost by storing the leaf.
- **Snapshot refresh** is an open question (§9, Q1): either a checked-in subset limited to the MICs GT
  uses, or a download step in the task. The `‹probe›` file sizes decide it.

### 4.4 `YahooSectorConnector`

- Lower priority; `supports()` returns true for everything, so it is also the equity fallback.
- Reuses `CrumbManager.getCookie()` / `getCrumb()` and `FeedConnectorHelper.getHttpAgentAsString(true)`
  directly. It must **not** extend `AbstractYahooFinanceConnector` — that class's template method is
  bound to the POST `/v1/finance/visualization` endpoint and does not fit a GET.
- Symbol resolution: prefer the Yahoo symbol GT already stores in
  `securitycurrency.url_history_extend` when `id_connector_history = 'gt.datafeed.yahoo'`; otherwise
  `YahooSymbolSearch.getSymbolByISINOrSymbolOrName`.
- Throttled with bucket4j at 2 requests / 2 s, as `YahooUDFConnect` does.
- Equity → one `RawWeight` at 100 %. Fund/ETF → `sectorWeightings` normalised to 100 %.

---

## 5. Schema

Four new tables. Nothing on `security`.

### 5.1 `sector` — the taxonomy

```sql
sector
  id_sector         SMALLINT     PRIMARY KEY
  id_sector_parent  SMALLINT     NULL REFERENCES sector(id_sector)
  hier_level        TINYINT      NOT NULL   -- 1 = sector, 2 = industry group, 3 = industry
  code              VARCHAR(64)  NOT NULL UNIQUE   -- stable, e.g. 'information-technology'
  name_nls          INT          NOT NULL REFERENCES multilinguestring(id)
```

`name_nls` reuses `grafiosch.entities.MultilanguageString` — the same mechanism
`Assetclass.subCategoryNLS` uses, so the EN/DE editing UI, the `@ValidMultilanguage` validator and the
`MapJoin`-based search in `SecuritySearchBuilder` all apply unchanged.

`code` is the stable identity. Renaming a display name is an NLS edit; it never breaks a mapping.

### 5.2 `sector_provider_map` — provider spelling → taxonomy node

```sql
sector_provider_map
  provider      VARCHAR(20)  -- 'FINDB' | 'YAHOO' | 'EODHD'
  provider_key  VARCHAR(64)
  id_sector     SMALLINT     NOT NULL REFERENCES sector(id_sector)
  PRIMARY KEY (provider, provider_key)
```

This table is the answer to "won't this need constant manual maintenance?". The recurring manual work
is **mapping a bounded set of strings once**, not classifying securities. Three spellings of the same
concept already exist across the two chosen sources:

| Concept | FinanceDatabase | Yahoo `assetProfile.sectorKey` | Yahoo `sectorWeightings` key |
|---|---|---|---|
| Financials | `Financials` | `financial-services` | `financial_services` |
| Information technology | `Information Technology` | `technology` | `technology` |
| Materials | `Materials` | `basic-materials` | `basic_materials` |

Hard-coding that mapping in a `switch` would mean a code change and a release every time a provider
adds a value. A row means an admin edit. An **unmapped** value is recorded and surfaced in the admin
list (§8) rather than silently dropped — so the failure mode is visible, not invisible.

### 5.3 `security_sector_profile` — provenance, one row per security

```sql
security_sector_profile
  id_securitycurrency  INT       PRIMARY KEY REFERENCES security(id_securitycurrency)
  source               TINYINT   NOT NULL   -- MANUAL | FINDB | YAHOO | EODHD | GTNET
  last_update          DATETIME  NOT NULL
  retry_count          TINYINT   NOT NULL DEFAULT 0
  locked               TINYINT   NOT NULL DEFAULT 0
```

`locked = 1` means a human decided; no automatic feed ever overwrites it. `retry_count` follows the
existing convention on `securitycurrency` (`retry_history_load`, `retry_dividend_load`, …) so the same
"reset retry counters" admin task shape applies.

### 5.4 `security_sector_weight` — the distribution

```sql
security_sector_weight
  id_securitycurrency  INT           NOT NULL REFERENCES security(id_securitycurrency)
  id_sector            SMALLINT      NOT NULL REFERENCES sector(id_sector)
  weight               DECIMAL(5,2)  NOT NULL   -- percent
  PRIMARY KEY (id_securitycurrency, id_sector)
```

A single stock is one row at `100.00`. An ETF is *n* rows summing to `100.00` (± rounding; the writer
normalises).

> **Note on `TINYINT(1)`** — `source` and `locked` are declared `TINYINT` without a display width on
> purpose. A `TINYINT(1)` column read through a Spring Data interface projection is returned as a
> `Boolean` by the MariaDB driver, which breaks the projection.

### 5.5 Migration

All DDL follows the idempotency rules in the root `CLAUDE.md`: `CREATE TABLE IF NOT EXISTS`,
drop-before-add for indexes and foreign keys, `INSERT IGNORE` (or delete-then-insert) for seed rows.

The version number is derived at implementation time from the active `backend/pom.xml` version
(currently `0.36.4`) and the highest existing migration. `V0_36_4__…` is released, so the new file is
`V0_36_5__sector_classification.sql` — unless a newer, still-unreleased migration exists on the branch,
in which case the SQL is folded into that one and its filename broadened.

Seed content: the `sector` rows (11 / ~24 / 68 nodes with EN+DE names), and the
`sector_provider_map` rows for `FINDB` and `YAHOO` derived from the `‹probe›` distinct-string output.

---

## 6. Reporting

Two views, because one shape cannot serve both honestly.

### 6.1 View A — dominant sector, inside the existing group-by table

The existing machinery generalises cleanly. `SecurityGroupByBaseReport<T>` derives its group key
reflectively (`PropertyUtils.getNestedProperty`), and
`SecurityGroupByAssetclassSubCategoryReport` already shows how to override `getGroupValue()` when the
value is language-dependent rather than a bean path.

- **Backend**: `SecurityGroupBySectorReport extends SecurityGroupByBaseReport<String>`, overriding
  `getGroupValue(Security)`. Language from `SecurityContextHolder`, as the sub-category report does.
  It preloads one `Map<idSecuritycurrency, String>` per report run — a per-security lookup inside the
  position loop would be an N+1.
- **REST**: three endpoints through the existing `getDynamicGroupReport` seam in
  `rest/SecurityaccountResource.java`, URL segment `sector`, at tenant / portfolio / security-account
  scope — matching the five groupings already exposed there.
- **Frontend**: `SecurityaccountSectorGroup extends SecurityaccountGroupBaseDynamic<string>`, a new
  `SecurityAccountGroup.GROUP_BY_SECTOR` constant, and one entry in the `groupMapping` map in
  `securityaccountTable.ts`. Plus the `GROUP_BY_SECTOR` NLS key.

> **Design detail worth recording.** The client-side group strategies read the group value straight off
> the `Security` object — `SecurityaccountAssetclassSubCategoryGroup.getGroupValue()` returns
> `security.assetClass.subCategoryNLS.map[this.language]`. Sector is *not* on `Security`. So the
> backend report must set a transient `dominantSector` on the serialized position for the strategy to
> read. This is the one place where the new dimension does not fit the existing pattern for free.

"Dominant" = highest weight; ties broken by `sector.code` for determinism.

### 6.2 View B — weighted look-through, its own report

For an ETF, the dominant sector is a lossy summary. The honest answer splits the position:

```
VWRL  CHF 10'000  ->  Technology        CHF 2'640
                      Financials        CHF 1'580
                      Consumer Cyclical CHF 1'080
                      …
```

This cannot be expressed in `SecurityPositionDynamicGroupSummary`: that type sums whole positions into
a group, and per-group columns such as units become meaningless once one holding appears in five
groups. So View B is a separate, smaller report that returns values only:

- **Backend**: `SectorAllocationReport` → `List<SectorAllocation{ sectorName, value, percentage }>`.
  Each position's main-currency value is split by weight. The grand total is unchanged, so the two
  views reconcile.
- **REST**: `GET /securityaccount/tenantsectorallocation` (+ portfolio scope), same
  `includeClosedPosition` / `untilDate` parameters as the existing summaries.
- **Frontend**: per `frontend/CLAUDE.md`, a parent component with `dynamic-form {nonModal: true}` for
  the inputs and a child table extending `TableConfigBase` — never one combined component. All values
  through `ColumnConfig` / `DataType` / `getValueByPath`; no raw Angular pipes. A Plotly pie is
  delivered through the existing `ChartDataService.sentToChart` →
  `chart.general.purpose.component.ts` in the `mainbottom` outlet, as
  `SecurityaccountGroupBaseDynamic.getChartDefinition()` already does.

### 6.3 Unclassified

Both views place securities with no profile in an explicit **Unclassified** bucket. Silently dropping
them would make the percentages lie, and hiding the gap would hide exactly the information an admin
needs to decide whether the feed is working.

---

## 7. Task and refresh

- `TaskTypeExtended.SECURITY_SECTOR_UPDATE = (byte) 55` — the next free value after
  `HOLD_TABLE_CONSISTENCY_CHECK (54)`, inside the application band 30–79.
  Per `backend/CLAUDE.md` this needs three matching changes in one commit: the Java constant, the
  same name and value in `frontend/src/app/shared/types/task.type.extended.ts`, and the
  `SECURITY_SECTOR_UPDATE` NLS key in EN **and** DE in
  `grafioschtrader-common/src/main/resources/message/messages{,_de}.properties`.
  `frontend/src/enum.mirror.spec.ts` and `NlsBundleGuardTest` fail the build otherwise.
- `task/exec/SecuritySectorUpdateTask.java` — `@Service implements ITask`,
  `@Scheduled(cron = "${gt.sector.update.data}")`, `TaskDataExecPriority.PRIO_VERY_LOW`,
  `removeAllOtherPendingJobsOfSameTask() = true`,
  `getAllowedEntities() = ["", "Security"]` so an admin can refresh one security from the task UI
  (the pattern `ResetConnectorRetryCountersTask` uses for connector-scoped tasks).
  `BackgroundWorker` discovers it automatically through `List<ITask>`.
- **Staleness gate** — a security is refreshed only when `locked = 0`, `retry_count < 3`, and the
  profile is missing or `last_update` is older than `gt.sector.refresh.days`
  (a `globalparameters` row with `input_rule = 'min:30,max:3650'`, default 180).

Because FinanceDatabase is offline, the equity half of a full refresh costs no requests at all. Only
ETFs, funds and FinanceDatabase misses reach the network.

---

## 8. Administration UI

Two small screens, both modelled on existing components:

1. **Sector taxonomy** — CRUD over `sector`, modelled on
   `assetclass/component/assetclass.table.component.ts` (`TableCrudSupportMenuSecurity`, with the EN
   and DE columns of the `MultilanguageString` shown side by side, as the asset-class table already
   does for `subCategoryNLS`).
2. **Unmapped provider values** — every `(provider, provider_key)` a feed returned that has no
   `sector_provider_map` row, with a count of affected securities and an inline assign action. This
   turns the residual manual work into a short, visible, finite list.

---

## 9. Open questions

| # | Question | Notes |
|---|----------|-------|
| Q1 | How is the FinanceDatabase snapshot shipped and refreshed? | Options: (a) a checked-in CSV subset limited to the MICs GT actually uses; (b) a download inside `SecuritySectorUpdateTask`; (c) seed rows in the Flyway migration. `‹probe›` file sizes and the per-MIC hit rate decide. (a) keeps the build offline; (b) stays current without a release. |
| Q2 | Store the leaf industry only, or all three levels per security? | The concept stores the leaf and walks `id_sector_parent` for aggregation. Storing all three would denormalise for query speed at the cost of consistency. |
| Q3 | Should View A group at sector or industry level? | Proposal: sector (level 1) by default, with the level selectable later. 68 industries is too many groups for a portfolio table. |
| Q4 | Bonds, CFDs and money-market instruments | Neither source classifies them meaningfully. Proposal: they stay Unclassified rather than being force-fitted. |
| Q5 | Should GTNet exchange sector profiles between instances? | Out of scope here. `source = GTNET` is reserved in the enum so it does not need a migration later. |
| Q6 | Seeding sectors for installations that used `subCategoryNLS` as a sector | Out of scope. Noted because `SubCategoryDetector` could drive it later. |

---

## 10. Verification before implementation

`scripts/probe_sector_coverage.py` measures the two sources against the **live `grafioschtrader`
database, read-only**, and produces the numbers this document is currently missing. Nothing in §5–§8
is built until it has run.

Already verified without the database: the mysql-client resolution, the exact read-only SQL, the
FinanceDatabase file inventory (85 equity files, 52 ETF files), the CSV parser, the `mic + symbol`
and ISIN lookups, and the `sectorWeightings` extraction. The numbers in §3.2 come from that run.
What is still open is the *GT-side* match rate — how many of this installation's securities those
indexes actually resolve.

- **Input** — `SELECT` only: `id_securitycurrency`, `isin`, `ticker_symbol`, `currency`, `name`,
  `active_to_date`, the stockexchange `mic`, `assetclass.category_type` / `spec_invest_instrument`,
  and `securitycurrency.id_connector_history` / `url_history_extend`.
- **Pass 1 — FinanceDatabase, offline**: download `equities/*.csv` and `etfs/*.csv` for the MICs GT
  actually uses; match on `mic + symbol`, then ISIN. Report the hit rate **and the ISIN fill rate in
  the source files** — §3.2 suggests it is well below 100 %, and that number decides whether ISIN is
  usable as a key at all.
- **Pass 2 — Yahoo, network**: everything Pass 1 missed, plus every ETF and fund.
  `quoteSummary?modules=assetProfile,topHoldings`, cookie + crumb, throttled to ~1 req/s, responses
  cached on disk so re-runs are cheap.
- **Output**:
  - combined and per-pass resolution rate, **per MIC** — SIX (~351), NASDAQ (~90), NYSE (~87) and
    XETR (~83) are the only exchanges in GT with enough securities for a rate to mean anything;
  - resolution rate per `SpecialInvestmentInstruments` (direct equity / ETF / fund / bond / CFD);
  - for ETFs and funds: how many return usable `sectorWeightings`, and whether the weights sum to ~100;
  - **the full distinct set of provider sector strings from both sources** — this is the seed data for
    `sector` and `sector_provider_map`, and the concrete measure of how much manual mapping is really
    required;
  - a CSV of every security neither pass resolves.

**Gate.** §3.5 is final only after this report. If FinanceDatabase's `mic + symbol` match rate on SIX
is poor, its role shrinks to seeding the taxonomy and Yahoo becomes primary. If Yahoo's ETF coverage is
poor, EODHD enters the recommendation as the paid option. **The schema in §5 and the
`ISectorFeedConnector` design in §4 are unaffected either way** — which is the point of making the
source a pluggable, priority-ordered list rather than a hard-wired dependency.
