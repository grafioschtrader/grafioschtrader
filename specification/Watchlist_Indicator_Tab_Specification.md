# Fundamental Data for Watchlist Indicators

**Code baseline:** backend 0.38.3, highest versioned Flyway script `V0_38_4__watchlist_indicator.sql`.

**Scope:** extend the existing indicator tab and `AlertExpressionSupport` with fundamental base values of direct
equities. Expressions compute ratios with the current price; base values are fetched weekly and stored per security.
The existing tab, persisted column definitions and daily-series functions are the integration points.

Analyst estimates, forward P/E, price targets, revisions and fundamentals of ETFs, funds, bonds and currency pairs
are outside the scope. Fundamental values are not distributed through GTNet.

## 1. Production performance validation

- [ ] On the production host, measure ten warm requests to `GET /api/watchlist/{id}/indicator` for the largest
      watchlist in the production database, with the default ten columns plus one `RS(126)` column and the intraday
      update not due. The median of the complete REST request must be below 500 ms.

If this bound requires a series cache, include `asOfDate` in its key and evict it on every `historyquote`,
`securitysplit` or `dividend` write for the instrument. The newest row's date alone cannot detect corrections of
older prices, splits or dividends.

## 2. Stage 2 — fundamental data

Stage 2 makes base values of a company's accounts available as expression variables. GT stores the **base values**
— earnings per share, shares outstanding, book value per share, revenue per share, net debt — and computes every
ratio with the live price in the column itself: `PRICE / EPS` is the P/E, `PRICE * SHARES` the market capitalisation.
A ratio is therefore as current as the price, while the base values change only with a company's report and are
loaded once a week. Stage 2 covers active direct equities only (`AssetclassType.EQUITIES`,
`SpecialInvestmentInstruments.DIRECT_INVESTMENT`).

### 2.1 Measured sources

`FundamentalSourceCoverageProbeTest` (`grafioschtrader-server/src/test/java/grafioschtrader/connector/fundamental/`,
opt-in, read-only JDBC, no Spring context) measured the free or already licensed sources on 2026-10-15 against the
248 active direct equities of the production database that are in a watchlist or have a transaction. Rerun it before
the implementation starts; the numbers below are as of that date.

| Source | Found | SIX | Xetra | NASDAQ + NYSE | Basis of the per-share values |
|---|---|---|---|---|---|
| Yahoo `v10/finance/quoteSummary` (unofficial, no key) | 230 | 20 / 22 | 39 / 42 | 116 / 120 | trailing twelve months; newest quarter mostly June 2026 |
| Eulerpool `api/1/equity/metrics/{ISIN}` (free key) | 237 | 20 / 22 | 41 / 42 | 115 / 120 | last fiscal year: 190 FY 2025, 34 FY 2026, 11 FY 2024, 2 older |
| Finnhub `stock/metric` (free key) | 117 | 0 | 0 | 117 / 120 | both: `epsTTM` and `epsAnnual`; every non-US symbol answers 403 on the free plan |
| EODHD `fundamentals` (plan "All World") | 0 | — | — | — | the plan does not include fundamentals (403) |
| SimFin `api/v3/companies/statements/compact`, `DERIVED`, `ttm=true` (free key) | 97, of them 76 with EPS | 0 | 0 / 41 | 97 / 120 | trailing twelve months; same quarter as Yahoo for 40 of 76, one quarter behind for 25, more for 11 |

Yahoo or Eulerpool together cover 245 of the 248 stocks.

SimFin's free plan is US-only in practice: its bulk market `de` holds 25 DAX companies last reported for 2021, `ca`
two companies, `cn` none, and the API finds no Xetra ticker. Where SimFin and Yahoo report the same quarter, their
earnings per share agree within 5 % for 26 of 40; the rest include gross errors on SimFin's side (McDonald's
12 286 713.29 USD, Hub Group 1 743.25 USD per share). Its licence requires deleting the data when the subscription
ends and reserves commercial use to an enterprise contract.

The values disagree more than the coverage suggests. Converted into one currency with the GT currency pairs,
Eulerpool's and Yahoo's earnings per share agree within 5 % for only 49 of 216 stocks that both deliver. A comparison
with Finnhub on US stocks explains most of it, and shows the errors that remain:

- **Different basis, not an error.** Finnhub's `epsAnnual` matches Eulerpool (Coca-Cola 3.039 / 3.039, Merck 7.28 /
  7.34) and Finnhub's `epsTTM` matches Yahoo (Apple 8.72 / 8.76, Merck 1.25 / 1.26). A fiscal-year value and a
  trailing value can legitimately differ in size and even in sign.
- **Yahoo's currency.** `trailingEps` is stated in the quote currency of the symbol (`summaryDetail.currency`), not in
  `financialData.financialCurrency`, which names the reporting currency. Novo Nordisk listed in EUR has 3.52 EUR, not
  DKK; the Alibaba ADR has 4.14 USD per ADR, not CNY. Read with the quote currency, the GT price divided by Yahoo's
  `trailingEps` reproduces Yahoo's `trailingPE` within 20 % for 173 of the 177 stocks with positive earnings and a
  P/E; the four others are Hitachi (0.18 against 31.6), Vonage, Canadian Natural Resources and AT&S. Single outliers
  against Finnhub also occur (Beyond Meat −22.0 against −0.65; DuPont 3.72 against 0.42). The measurement covers
  `trailingEps` only; the currency of Yahoo's other fields is not established by it.
- **Eulerpool.** Isolated wrong values (Sony −55.2 JPY for a profitable year), stale years (13 stocks before FY 2025),
  and mixed units within one answer: `other.shares` and `valuation.marketCap` in millions, `leverage.netDebt` in full
  units. `financials-ltm` mixes units inside one response (net income in millions next to full units) and its `asOf`
  is the generation date, not a period end; stage 2 does not use it.
- **Different share bases.** For a few stocks both sources name the same period end and still differ by an order of
  magnitude (Trinity Biotech −0.10 against −59.24, Freight Technologies, Concord Medical). The pattern fits a
  reverse split or a depositary ratio applied by one source and not by the other; the cause per stock is not
  established.

The rules in §2.4 follow from this: every value carries its source, basis, period and the outcome of its checks;
values are compared only when they describe the same thing; a contradicted or failed value is not shown; and a value
that could be neither confirmed nor checked is shown with that grade visible.

### 2.2 Variables

New expression variables, bound only for securities with a selected value (§2.4). A variable without a selected value
stays unbound, which makes the cell empty with the `IndicatorCellStatus` `NO_FUNDAMENTAL`, added to that enum.

| Variable | Bound from | Unit |
|---|---|---|
| `EPS` | selected `EPS_TTM` | security currency per share of the GT listing |
| `EPS_FY` | selected `EPS_FY` | security currency per share of the GT listing |
| `BVPS` | selected `BVPS` | security currency per share of the GT listing |
| `SPS` | selected `SPS_TTM`, else selected `SPS_FY` | security currency per share of the GT listing |
| `SHARES` | selected `SHARES` | count of shares of the GT listing's class (ADRs: ADR equivalents) |
| `NET_DEBT` | selected `NET_DEBT` | security currency, full units |

A value stored in another currency than the security's is converted on read with the latest close of the GT currency
pair, using the pair-direction lookup of `Dividend.getDividendsForTotalReturn`; without a pair the variable stays unbound. The stored base value changes only with a
report, but the bound variable moves with the exchange rate every day; for `PRICE / EPS` in the security currency
that is what is wanted, and the help text says so. (Dividends, by contrast, are converted at their ex-date.)

Examples for the help text of the column dialog: P/E `PRICE / EPS`, earnings yield `EPS / PRICE * 100`, market
capitalisation in billions `PRICE * SHARES / 1E9`, price-to-book `PRICE / BVPS`, price-to-sales `PRICE / SPS`,
EV/sales `(PRICE * SHARES + NET_DEBT) / (SPS * SHARES)`. A negative `EPS` gives a negative P/E; the help text says
so and suggests a boolean column `EPS > 0` or a filter on the P/E column to separate loss makers.

`IndicatorData` gains `FUNDAMENTALS`; `requiredData` reports it when the expression reads one of the six
variables. The column validation passes `FUNDAMENTALS`, the alert paths do not, so an expression alert refuses these
variables with `ALERT_EXPRESSION_DATA_NOT_AVAILABLE`.

### 2.3 Storage

Table `security_fundamental`, entity `SecurityFundamental` in `grafioschtrader-common/.../entities/`, one row per
security, metric and source:

```sql
CREATE TABLE IF NOT EXISTS security_fundamental (
  id_securitycurrency INT NOT NULL,
  metric              TINYINT NOT NULL,
  source              TINYINT NOT NULL,
  value               DOUBLE NOT NULL,
  currency            CHAR(3) NULL,
  period_end          DATE NULL,
  fetched_time        DATETIME NOT NULL,
  source_check        TINYINT NOT NULL,
  state               TINYINT NOT NULL,
  PRIMARY KEY (id_securitycurrency, metric, source),
  CONSTRAINT FK_SecurityFundamental_Security FOREIGN KEY (id_securitycurrency)
    REFERENCES security (id_securitycurrency) ON DELETE CASCADE
);
```

| Enum | Constants (byte value) |
|---|---|
| `FundamentalMetric` | `EPS_TTM(1)`, `EPS_FY(2)`, `BVPS(3)`, `SPS_TTM(4)`, `SPS_FY(5)`, `SHARES(6)`, `NET_DEBT(7)`, `PE_CHECK(8)`, `MCAP_CHECK(9)` |
| `FundamentalSource` | `YAHOO(1)`, `FINNHUB(2)`, `EULERPOOL(3)` |
| `FundamentalSourceCheck` | `NOT_CHECKED(0)`, `PASSED(1)`, `FAILED(2)` |
| `FundamentalState` | `CANDIDATE(0)`, `CONFIRMED(1)`, `CHECKED(2)`, `SINGLE(3)`, `SUSPECT(4)` |

- `value` is always in full units — Eulerpool's millions are multiplied out before any check and before the row is
  written. Non-finite numbers are never written.
- `currency` is the currency of the value per the field contract of §2.5, `NULL` for `SHARES`.
- `PE_CHECK` (Yahoo `trailingPE`) and `MCAP_CHECK` (Yahoo `marketCap`, in its quote currency) are evidence for the
  checks of §2.4 only; they stay `CANDIDATE`, are never selected and never bound.
- `source_check` is the outcome of the checks that need input outside the stored rows (price, the other fields of the
  same answer); it is written by the load task (§2.6), never by the selector.
- `state` is written by the selector (§2.4). Per security and bound metric at most one row is `CONFIRMED`, `CHECKED`
  or `SINGLE`; that row binds the variable.

The entity's getters return the enums (`backend/CLAUDE.md`, *Enum-Backed Entity Fields*).

### 2.4 Checks and selection

**Source checks**, run by the load task when a source's answer for a security is written, with the security's
`sLast` at that moment and the GT currency pairs; each sets `source_check` of the rows it covers. A check whose input
is missing — no `PE_CHECK`, a non-positive EPS or P/E, no pair, a net income of 0 — leaves `NOT_CHECKED`; "could not
check" is never "passed".

1. **Yahoo `EPS_TTM` against the price.** With a positive `EPS_TTM`, a positive `PE_CHECK` and `sLast`, all in the
   quote currency of the Yahoo symbol: `abs(sLast / EPS_TTM − PE_CHECK) <= 0.20 · PE_CHECK`. It proves that the EPS
   fits the price of the GT listing — the same share and the same currency — which is what `PRICE / EPS` needs. The
   tolerance absorbs the price move between Yahoo's computation and the load; measured, 173 of 177 pass (§2.1).
2. **`SHARES` against the market capitalisation.** For every `SHARES` row, with Yahoo's `MCAP_CHECK` and `sLast`
   converted into the same currency: `abs(sLast · SHARES / MCAP_CHECK − 1) <= 0.20`. It catches a share count of
   another class or of the ordinary shares behind an ADR, which would make `PRICE * SHARES` and the EV formula wrong
   by the depositary ratio.
3. **Eulerpool units.** `abs(EPS_FY · SHARES / netIncome − 1) <= 0.25` with the net income of the `historical` entry
   whose `period` equals `period_end`, read from the same answer and not stored. Failing it sets the Eulerpool
   `SHARES` row to `FAILED`; it catches a slip of the millions factor of `other.shares`.

**Selection.** `FundamentalSelector` (`grafioschtrader-server/.../fundamental/`) is a pure function
`select(List<SecurityFundamental> rows, LocalDate asOfDate)` over the stored rows of one security; it reads no
database and no price and is unit-tested on recorded values. Per bound metric, in this order:

1. **Exclude** rows with `source_check = FAILED`, and stale rows: a dated row counts when `period_end >= asOfDate − 18
   months`; an undated row when `fetched_time >= asOfDate − 21 days` (the weekly run may fail twice).
2. **Group comparable candidates.** Two candidates are compared only when their period ends are at most 45 days
   apart, or when one of them is undated; candidates of different periods are not a contradiction.
3. **Agreement.** Within a group, two values agree when, for `EPS_TTM` and `EPS_FY`,
   `abs(a − b) <= max(0.05 · max(abs(a), abs(b)), 0.02)`; for `SPS_TTM` within 10 %; for `SHARES` within 5 %. `BVPS`,
   `NET_DEBT` and `SPS_FY` are not compared — the sources report them at different dates.
4. **Choose**, walking the sources in order of preference:

   | Metric | Sources, in order of preference |
   |---|---|
   | `EPS_TTM` | Finnhub, Yahoo |
   | `EPS_FY` | Finnhub, Eulerpool |
   | `SPS_TTM` | Yahoo, Finnhub |
   | `SPS_FY` | Eulerpool |
   | `SHARES` | Yahoo, Eulerpool |
   | `BVPS` | Yahoo, Finnhub, Eulerpool |
   | `NET_DEBT` | Yahoo, Eulerpool |

   - the most preferred candidate that agrees with another one of its group becomes `CONFIRMED`;
   - when a group of two or more has no agreeing pair, all its rows become `SUSPECT`;
   - otherwise the most preferred remaining candidate of the newest period becomes `CHECKED` when its `source_check` is
     `PASSED`, else `SINGLE`. An undated candidate alone never becomes `CHECKED` or `SINGLE`; it can only confirm.
   - When the chosen row is made `SUSPECT` by step 5, the next candidate in order of preference that is not `SUSPECT`
     is chosen instead.
5. **Fiscal year against trailing twelve months.** When the chosen `EPS_TTM` and the chosen `EPS_FY` satisfy
   `period_end(EPS_TTM) >= period_end(EPS_FY)` and are at most 92 days apart, three of their four quarters are the
   same, and `abs(EPS_FY − EPS_TTM) <= 0.5 · max(abs(EPS_FY), abs(EPS_TTM))` must hold. If it does not, the `EPS_FY`
   row becomes `SUSPECT` unless it is `CONFIRMED`, and so does the `EPS_TTM` row unless it is `CONFIRMED` or
   `CHECKED`. Measured, the rule fires for 6 stocks: Sony's −55.2 JPY against a trailing 186.28 JPY one quarter later,
   AT&S, Aurora Cannabis and the three share-basis cases of §2.1. A quarter with an extreme swing can also fail it
   while both values are correct; the rule then hides a single-source value, the cost of keeping a wrong P/E out.
   Merck (FY December 2025, trailing June 2026, 182 days) is not compared.
6. All other rows of the metric stay `CANDIDATE`.

**Reading** (`SecurityFundamental.findSelectedByIds`, §2.7) re-applies the freshness of step 1 with the request's
`asOfDate`, so values expire on schedule even when the load job is disabled or keeps failing, and skips every row of a
security with a `securitysplit` whose `split_date` lies after the row's `fetched_time`: a split between two weekly
runs would otherwise combine the new price with the old per-share values. The next run replaces them.

**Invalidation on change.** When a security's currency, ISIN, its Yahoo or Finnhub connector extension, or the Yahoo
symbol UDF changes, its `security_fundamental` rows are deleted in the save path (`SecurityJpaRepositoryImpl`); a
security that no longer qualifies for §2 (inactive, other asset class) loses its rows at the next run.

### 2.5 Sources

`IFundamentalFeedConnector` in `grafioschtrader-server/.../connector/fundamental/`:

```java
public interface IFundamentalFeedConnector {
  FundamentalSource getFundamentalSource();
  boolean isFundamentalAvailable();             // key present, connector activated
  boolean supportsFundamentals(Security security);
  FundamentalAnswer loadFundamentals(Security security) throws Exception;
  Duration getFundamentalPause();
}
```

`FundamentalAnswer` is either `usable(List<SecurityFundamental> rows, Map<String, Double> evidence)` — HTTP 200 with
a parseable body containing the expected root object, `evidence` carrying what the checks of §2.4 read from the same
answer, such as Eulerpool's net income — or `failed(reason)`.

Finnhub and Eulerpool implement it in their existing connector classes (`FinnhubConnector`,
`EulerpoolFeedConnector`), because only they can read their key (`BaseFeedApiKeyConnector.getApiKey()` is protected).
Yahoo gets its own `@Component YahooFundamentalConnector` in `connector/yahoo/`, using `CrumbManager` for cookie and
crumb. All three answers are read with `tools.jackson.databind.JsonNode`; a missing or non-numeric field writes no
row.

**Field contract.** Every stored field has a stated unit, currency, period and share basis:

| Source | Field → metric | Unit | Currency | Period end | Share basis |
|---|---|---|---|---|---|
| Yahoo | `defaultKeyStatistics.trailingEps.raw` → `EPS_TTM` | per share | `summaryDetail.currency` (measured) | `defaultKeyStatistics.mostRecentQuarter` | listing (ADR: per ADR, measured) |
| Yahoo | `defaultKeyStatistics.bookValue.raw` → `BVPS` | per share | to verify: quote or `financialData.financialCurrency` | `mostRecentQuarter` | to verify |
| Yahoo | `financialData.totalRevenue.raw / defaultKeyStatistics.sharesOutstanding.raw` → `SPS_TTM` | per share | to verify: `financialData.financialCurrency` expected | `mostRecentQuarter` | to verify |
| Yahoo | `defaultKeyStatistics.sharesOutstanding.raw` → `SHARES` | count | — | none | to verify; check 2 |
| Yahoo | `financialData.totalDebt.raw − financialData.totalCash.raw` → `NET_DEBT` | full units | to verify: `financialData.financialCurrency` expected | `mostRecentQuarter` | — |
| Yahoo | `summaryDetail.trailingPE.raw` → `PE_CHECK` | ratio | — | none | listing |
| Yahoo | `summaryDetail.marketCap.raw` → `MCAP_CHECK` | full units | `summaryDetail.currency` | none | listing |
| Finnhub | `metric.epsTTM` → `EPS_TTM`, `metric.epsAnnual` → `EPS_FY`, `metric.bookValuePerShareQuarterly` → `BVPS`, `metric.revenuePerShareTTM` → `SPS_TTM` | per share | the security currency for US listings on the free plan (the only ones supported) | none from `metric`; `series.annual.eps[0].period` for `EPS_FY` when the probe shows it | listing |
| Eulerpool | `perShare.eps` → `EPS_FY`, `perShare.bps` → `BVPS`, `perShare.sps` → `SPS_FY` | per share | `currency` | newest `historical[].period` | ordinary share; check 2 against the listing |
| Eulerpool | `other.shares · 1 000 000` → `SHARES` | count | — | newest `historical[].period` | ordinary shares; checks 2 and 3 |
| Eulerpool | `leverage.netDebt` → `NET_DEBT` | full units | `currency` | newest `historical[].period` | — |

A field marked "to verify" is stored but not bound until the probe rerun has recorded answers for three reference
listings — a home listing (Nestlé on SIX), a foreign listing in another currency than the reporting one (Novo Nordisk
in EUR) and an ADR (Alibaba) — and the table has been settled from them. The probe is extended to log the currency
fields next to each value for that purpose.

Symbols:

- **Yahoo:** the extension of the Yahoo intraday connector when the security uses it, then the system-wide Yahoo
  symbol UDF (`UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE`), then ticker plus the exchange suffix of the MIC (`.SW` for XSWX,
  `.DE` for XETR, none for US exchanges; the table is the one of the probe's `FundamentalProbeSupport.YAHOO_SUFFIX`).
- **Finnhub:** its own connector extension when used, otherwise the ticker. `supportsFundamentals` is true for US MICs
  only, whatever the subscription: the free plan refuses every other symbol, and the symbol scheme of a paid plan is
  not measured.
- **Eulerpool:** the ISIN; securities without ISIN are not supported.

The recorded sample answers the mapping tests read are copied from `target/fundamental-probe/samples/`, where the
probe writes them with masked keys, to `grafioschtrader-server/src/test/resources/fundamental/samples/`, each with its
source, symbol and fetch date in the file name, without keys or account data.

### 2.6 Loading

- New task type `FUNDAMENTAL_DATA_LOAD((byte) 56)` in `TaskTypeExtended` — 56 is the free value between
  `HOLD_DAILY_TOTAL_UPDATE(55)` and `UDF_USER_SUGGEST_VALUES(57)` —, the same constant in the frontend mirror
  `task.type.extended.ts`, and its NLS key in both languages (`backend/CLAUDE.md`, *Enums Mirrored in the Frontend*).
- Cron `gt.fundamental.load.cron` in `application.properties`, default `0 23 4 * * SAT` (UTC), off the full hour and
  outside the daily chain; `util/shellscripts/gtcronrandom.sh` gains it as a separate key, outside the chain, moved to
  a random minute between 03:00 and 05:00 on Saturday while it is still at its default, so that installations do not
  all ask Yahoo in the same minute. `-` disables the schedule; the manual task stays possible.
- Property `gt.fundamental.sources`, default `yahoo,finnhub,eulerpool`, selects the sources; empty disables every
  fundamental request, scheduled or manual.
- Scope: the query of `FundamentalSourceCoverageProbeTest.loadSecurities` — active direct equities
  (`active_to_date >= CURDATE()`) in a watchlist of any tenant or in a transaction — so that the measured coverage
  stays comparable. With `idEntity` set the task loads that one security, so an administrator can refresh it from the
  task monitor.
- The task does not start while another `FUNDAMENTAL_DATA_LOAD` is in `PROG_RUNNING`; it ends at once, so two runs
  never overwrite each other's results.
- Each source runs on its own thread with its own pause — Yahoo 600 ms, Finnhub 1 100 ms (60 requests per minute on
  the free plan), Eulerpool 300 ms — and a request timeout of 20 s. HTTP 429 waits for `Retry-After` (at most 60 s)
  once and then counts as failed; 401 and 403 on a supported symbol stop that source for the rest of the run, since
  the key or plan is the cause; any other error fails that security for that source. The probe completed the 248
  stocks in about five minutes this way.
- After all sources, per security in one transaction: the rows of every source that gave a **usable** answer are
  replaced by that answer — so a metric the source no longer delivers disappears; the rows of a source that failed
  stay untouched; then the source checks (§2.4) run with the current `sLast`, then `FundamentalSelector` over the
  combined rows.
- The task log records per source the number of usable, failed and unsupported securities and the time of the last
  usable answer, so the task monitor shows when a source stopped working.

### 2.7 Evaluation in the tab

`WatchlistIndicatorReport.calculate` loads, when a column needs `FUNDAMENTALS`, the selected rows of the
watchlist's securities in one query (named query `SecurityFundamental.findSelectedByIds`: state `CONFIRMED`, `CHECKED`
or `SINGLE`, the six bound metrics, the freshness and split rule of §2.4, parameters the ids and `asOfDate`) and binds
the variables per instrument after the currency conversion of §2.2. Currency pairs and non-equity securities have no
rows, so their cells stay empty with `NO_FUNDAMENTAL`.

The cell tooltip of a column that reads fundamentals names, per variable used, the source, the basis (TTM or fiscal
year), the period end, the grade (`CONFIRMED`, `CHECKED`, `SINGLE`), the fetch date, and the exchange rate when the
value was converted. These travel in a map `fundamentalInfo` (`idSecuritycurrency` → variable → source, basis, period
end, state, fetch date, rate) on `SecuritycurrencyIndicatorGroup`.

### 2.8 Export, deletion, limits, licence

- `security_fundamental` is derived data rebuilt by the load task: no `ExportDefinition`, recorded in the Javadoc of
  `MyDataExportDeleteDefinition`. It is written only by the system and needs no limit key.
- The values are shown inside the instance only and never passed on through GTNet. The gt-user-manual names the
  sources, states that Yahoo's interface is unofficial and may stop working, and that the free plans of Finnhub and
  Eulerpool are subject to their providers' terms.

### 2.9 Acceptance of stage 2

- [ ] The probe is rerun on the production database before the implementation; the source table of §2.1 holds within
      a few stocks, otherwise the preferences of §2.4 are revisited. The rerun records the three reference listings
      of §2.5 and settles every "to verify" entry of the field contract, and shows whether Finnhub's `series` block
      carries a period for `epsAnnual`.
- [ ] Unit tests of `FundamentalSelector` on recorded values with a fixed `asOfDate` of 2026-10-15, without mocks:
      Apple (Finnhub and Yahoo `EPS_TTM` agree → `CONFIRMED`), Novo Nordisk (Yahoo 3.52 EUR with check 1 `PASSED` →
      `CHECKED`), Hitachi (check 1 `FAILED` → excluded), Trinity Biotech (rule 5 with negative earnings → both
      `SUSPECT`), Merck (FY 7.34 and TTM 1.26 both selected, 182 days apart), Sony (Yahoo `EPS_TTM` 186.28 `CHECKED`,
      Eulerpool `EPS_FY` −55.2 → `SUSPECT` by rule 5), a stock whose Eulerpool `period_end` is 2024-12-31 (excluded,
      before 2025-04-15), two `EPS_TTM` candidates one quarter apart (not compared, the newer one chosen), an undated
      Finnhub value alone (not chosen), and a preferred candidate made `SUSPECT` with the next one chosen instead.
- [ ] Unit tests of the source checks and of the three mappings of §2.5 on the recorded sample answers in
      `src/test/resources`, including the millions factor of Eulerpool's `other.shares`, an ADR whose share count
      fails check 2, a net income of 0 (`NOT_CHECKED`), and a failed source whose rows survive the transaction.
- [ ] `PRICE / EPS` computed with `sLast` at load time equals the stored `PE_CHECK` within 20 % for every `CHECKED`
      Yahoo `EPS_TTM` row of the largest watchlist.
- [ ] A row older than the freshness window disappears from the tab with the load job disabled; a split after
      `fetched_time` empties the fundamental cells of that security until the next run.
- [ ] An expression alert using `EPS` is refused on save with a translated message.
- [ ] With `gt.fundamental.load.cron=-` the task never starts on its own; with `gt.fundamental.sources` empty no
      fundamental request leaves the instance, also not from a manual task.
- [ ] gt-user-manual: the variables, the sources and their limits, the grades `CONFIRMED` / `CHECKED` / `SINGLE`,
      that converted values move with the exchange rate, and why a cell can be empty although a source has a value.

## 3. Decisions

| Decision | Choice | Reason |
|---|---|---|
| Fundamentals: what is stored | base values per share and in total; every ratio computed with the live price | a ratio stays current daily while the source is asked once a week |
| Fundamentals: sources | Yahoo, Finnhub (US only), Eulerpool (free key) | measured: Yahoo or Eulerpool together cover 245 of 248 stocks; EODHD's plan has no fundamentals |
| Fundamentals: grades | `CONFIRMED`, `CHECKED`, `SINGLE` shown with the grade; `SUSPECT` and failed checks never | a wrong P/E is worse than none, but an unverifiable value is still useful when it is labelled |
| Fundamentals: checks needing outside input | run at load time, stored as `source_check` | the selector stays a pure function over stored rows and a date |
| Fundamentals: expiry | at read time | a disabled or failing job must not leave old values visible forever |
| Fundamentals: unverified field contracts | stored, not bound | the currency of Yahoo's fields beyond `trailingEps` is not measured |
| Fundamentals: weekly scope | as the probe — active, in a watchlist or any transaction | keeps the measured coverage and timings valid |
| Fundamentals: switches | cron `-` stops the schedule, empty source list stops every request | an administrator can still refresh one security while the schedule is off |
| Fundamentals: `financials-ltm` of Eulerpool | not used | mixed units within one answer, no period end |
| Fundamentals: SEC EDGAR | not a source | covers only SEC filers; the US stocks are covered by Finnhub and Yahoo, which confirm each other |
| Fundamentals: SimFin | not a source | measured: US only, EPS for 76 of 120 US stocks, a quarter behind for a third of them, gross per-share errors; licence requires deletion at the end of the subscription |
| Fundamentals in alerts | no | the alert evaluation loads no fundamentals; keeps alerts independent of external sources |
