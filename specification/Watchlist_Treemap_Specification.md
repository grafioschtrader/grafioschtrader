# Watchlist Treemap — Implementation Specification

**Code baseline:** backend 0.39.3, highest Flyway script `V0_39_3__watchlist_indicator_multi_watchlist.sql`.

## 1. Purpose

A new tab of the watchlist shows its stocks as a market map:

- the **area** of a tile is the market capitalisation of the company, normalised to the main currency of the tenant;
- the **colour** of a tile is the current percentage price change, red for a loss and green for a gain;
- the tiles are **grouped** by `Assetclass.subCategoryNLS`, which for stocks names the economic country or region
  ("Aktien USA", "Aktien Schweiz").

No table, column or Flyway migration is added. The tab is read only, so it creates no `ExportDefinition` and no
`entity_limit` key.

## 2. Decisions

| Topic | Decision |
|---|---|
| Instruments | Active direct equities only: `AssetclassType.EQUITIES` and `SpecialInvestmentInstruments.DIRECT_INVESTMENT` |
| Market capitalisation | `SHARES × sLast`; when no selected `SHARES` row exists, Yahoo's stored `MCAP_CHECK` |
| Colour value | `Securitycurrency.sChangePercentage` in the currency of the listing, without exchange rate effects |
| Colour scale | Fixed from −3 % over 0 % to +3 %; larger changes take the end colour |
| Group | `Assetclass.subCategoryNLS` in the language of the user; node key is `id_asset_class` |
| Group colour | Change weighted by market capitalisation over the tiles of the group |
| Normalisation | Main currency of the tenant at the newest closing rate |
| Duplicates | One tile per ISIN: the listing with the newest `sTimestamp` |
| Freshness | A tile is dropped when its `sTimestamp` is more than one hour older than its reference time (§4.3) |
| Calculation | Entirely in the backend; the client maps nodes to a Plotly trace and formats |

## 3. Data sources

| Value | Source | Notes |
|---|---|---|
| Shares | `security_fundamental`, metric `SHARES`, through `SecurityFundamentalJpaRepository.findSelectedByIds` | Applies the grade, the freshness window and the split guard. ADRs count in ADR equivalents, so the product with the ADR price is consistent. |
| Market capitalisation fallback | `security_fundamental`, metric `MCAP_CHECK`, source `YAHOO` | Stated in `currency` of the row, which can differ from the currency of the security. |
| Price, change, time | `Securitycurrency.sLast`, `sChangePercentage`, `sTimestamp` | `sTimestamp` is UTC for every connector; some connectors store the time of the fetch, others the time of the last trade. |
| Group | `Security.assetClass.subCategoryNLS` | One `MultilanguageString` per asset class. |
| Exchange hours | `Stockexchange.timeOpen`, `timeClose`, `timeZone` | |
| Sessions | `PriceFreshnessService.loadSessions` | Newest first, includes holidays of the exchange through its calendar. |
| Exchange rates | `currencypair` joined to `historyquote` | Newest close, direct or inverse pair. |

## 4. Backend

### 4.1 REST

`WatchlistResource`:

```java
@GetMapping(value = "/{idWatchlist}/treemap", produces = APPLICATION_JSON_VALUE)
public WatchlistTreemap treemap(@PathVariable Integer idWatchlist) {
  return watchlistReport.getWatchlistTreemap(idWatchlist);
}
```

`WatchlistReport.getWatchlistTreemap(Integer idWatchlist)` is `@Transactional` and follows
`getWatchlistWithIndicators`: `updateWatchlistPrices` (which also checks that the tenant owns the watchlist), joining
`prices()` and `closes()`, `entityManager.clear()`, `createSecuritycurrencyPositionList` and
`replaceLastPriceByHistoryquote`. It then calls `WatchlistTreemapReport.calculate(securities, tenant, today)` with
`today = ClientClock.today()`, resolved on the request thread.

`replaceLastPriceByHistoryquote` clears `sChangePercentage` of an instrument whose historical close is newer than its
intraday price. Such an instrument is reported with `TREEMAP_CHANGE_MISSING`.

### 4.2 Loading — `grafioschtrader.reports.WatchlistTreemapReport`

A Spring service that loads and hands plain input to the pure builder of §4.5:

1. Filter the positions to the instruments of §2 whose `activeToDate` is not before `today`. The number of removed
   positions becomes `notApplicableCount`.
2. `findSelectedByIds(ids, today)`, keeping only metric `SHARES`.
3. New repository method `SecurityFundamentalJpaRepository.findMarketCapChecks(ids, asOfDate)`, named query
   `SecurityFundamental.findMarketCapChecks`. It returns `id_securitycurrency`, `value` and `currency` of metric 9 and
   source `YAHOO`, where `fetched_time >= DATE_SUB(?2, INTERVAL 21 DAY)` and no `securitysplit` row of the security has
   `split_date >= DATE(fetched_time)`. Document the query in the repository interface.
4. New repository method `CurrencypairJpaRepository.findNewestRatesToCurrency(currencies, targetCurrency, asOfDate)`,
   named query `Currencypair.findNewestRatesToCurrency`. It returns per currency the newest `historyquote.close` of the
   pair `currency → target` with `date <= asOfDate`, otherwise `1 / close` of the pair `target → currency`. A currency
   without either pair yields no row. It is independent of the tenant's accounts, because a watchlist can hold
   currencies in which the tenant has no account.
5. `PriceFreshnessService.loadSessions(idsStockexchange, today.plusDays(1))`. The extra day covers exchanges whose local
   date is ahead of the date of the client.

The currencies passed in step 4 are the union of the security currencies and the currencies of the `MCAP_CHECK` rows.

### 4.3 Reference time and freshness

`now` is `LocalDateTime.now(ZoneOffset.UTC)`. Let `newest` be the largest non-null `sTimestamp` of the candidates that
is not after `now`.

New method of `PriceFreshnessService`:

```java
/**
 * Closing instant of the newest session of the exchange that has already opened: today's close during and after a
 * session, the close of the previous session before the opening bell, on a weekend and on a holiday.
 *
 * @return that instant as a UTC date and time; null when the exchange has no time zone, opening or closing time, or
 *         when the window contains no opened session
 */
public LocalDateTime closeOfNewestOpenedSession(List<LocalDate> sessionsDesc, Stockexchange stockexchange,
    LocalDateTime nowUtc)
```

For every candidate:

```text
close     = closeOfNewestOpenedSession(sessions of its exchange, its exchange, now)
reference = min(newest, close), or newest when close is null
fresh     = sTimestamp != null AND sTimestamp >= reference − 60 minutes
```

| Situation | `close` | Effect |
|---|---|---|
| Exchange in session | today's close, later than `now` | `reference = newest`: one hour behind the newest title of the watchlist |
| After the close, connector stores the last trade | today's close | A title traded until the close stays in |
| After the close, connector stores the fetch time | today's close | Stays in |
| Before the opening, weekend, holiday | close of the previous session | The last session's change stays visible |
| Illiquid title, last trade more than one hour before the close | today's close | Dropped |
| Connector failed for several sessions | any | Dropped |

### 4.4 Market capitalisation

Per candidate, in the main currency `MC` of the tenant:

```text
rate(c)  = 1 when c = MC, otherwise the rate of step 4 in §4.2
capMC    = SHARES × sLast × rate(security.currency)      when SHARES is present and sLast > 0
         = MCAP_CHECK.value × rate(MCAP_CHECK.currency)  otherwise
```

Exchange rates only scale the area, so the newest close is sufficient and no intraday rate is requested.

### 4.5 Builder — `grafioschtrader.reports.WatchlistTreemapBuilder`

A `final` class with a static `build` method and no Spring dependency, like `HoldingsTreemapBuilder`. Order of the rules:

1. **One listing per ISIN.** Among the candidates with the same ISIN keep the one with the newest `sTimestamp`; on equal
   times the lowest `idSecuritycurrency`. A candidate without ISIN stands alone. The removed listings are not reported.
2. **Exclusions**, first matching reason wins, each producing a `WatchlistTreemapExcluded`:

   | Condition | `reasonKey` |
   |---|---|
   | No `sChangePercentage` | `TREEMAP_CHANGE_MISSING` |
   | Not fresh (§4.3) | `TREEMAP_CHANGE_STALE` |
   | Neither `SHARES` with a positive price nor `MCAP_CHECK` | `TREEMAP_MARKET_CAP_MISSING` |
   | A required exchange rate is missing | `TREEMAP_PRICE_MISSING` |

3. **Nodes.** A flat list, root first, groups by descending total, tiles of a group by descending area:

   | `nodeType` | `id` | `valueMC` | `totalValueMC` | `changePercentage` |
   |---|---|---|---|---|
   | `ROOT` | `root` | 0 | sum of all tiles | null |
   | `GROUP` | `grp/<idAssetClass>` | 0 | sum of its tiles | `Σ(capMC × change) / Σ capMC` |
   | `SECURITY` | `sec/<idSecuritycurrency>` | `capMC` | `capMC` | `sChangePercentage` |

Round percentages with `DataBusinessHelper.roundPercentage`. All amounts are `double`.

### 4.6 DTOs — `grafioschtrader.reportviews.securitycurrency`

Records with `@Schema` descriptions:

```java
public record WatchlistTreemap(String mainCurrency, int precisionMC, int notApplicableCount,
    List<WatchlistTreemapNode> nodes, List<WatchlistTreemapExcluded> excluded) {}

public record WatchlistTreemapNode(String id, String parentId, WatchlistTreemapNodeType nodeType, String label,
    double valueMC, double totalValueMC, Double changePercentage, Integer idSecuritycurrency,
    @JsonFormat(pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT) LocalDateTime timestamp) {}

public record WatchlistTreemapExcluded(String label, String reasonKey) {}
```

`WatchlistTreemapNodeType` has the constants `ROOT`, `GROUP` and `SECURITY`. The label of a `GROUP` node is resolved in
the backend with the language of the user; a missing text falls back to the name of the other language. `timestamp` is
set on `SECURITY` nodes only; the literal `Z` of the format is correct because `sTimestamp` is UTC.

## 5. Frontend

| Change | Location |
|---|---|
| `WATCHLIST_TREEMAP_KEY = 'watchlisttreemap'` | `frontend/src/app/shared/app.settings.ts` |
| Route with `loadComponent` after the indicator route | `frontend/src/app/app.routes.ts` |
| Tab `{ label: 'WATCHLIST_TREEMAP', route: AppSettings.WATCHLIST_TREEMAP_KEY, icon: '' }` | `WatchlistTabMenuComponent.initializeTabs` |
| Interfaces of §4.6 with type-only imports | `frontend/src/app/watchlist/model/watchlist-treemap.ts` |
| `getWatchlistTreemapRequest(id)` | `WatchlistService` |
| `WatchlistTreemapComponent` | `frontend/src/app/watchlist/component/watchlist-treemap.component.ts` |
| `WatchlistTreemapChart` | `frontend/src/app/watchlist/component/watchlist.treemap.chart.ts` |

`WatchlistTreemapComponent` receives `id` as a signal input. It reads through `httpResource`, calls
`reportResourceErrors`, offers a refresh button that calls `reload()`, and obtains Plotly from `PlotlyLoaderService`,
keeping the instance for `resize` and `purge`. An empty node list shows `WATCHLIST_TREEMAP_NO_EQUITIES` instead of a chart.

`WatchlistTreemapChart` follows `HoldingsTreemapChart`, including `ColumnConfig` based formatting:

```ts
{
  type: 'treemap',
  ids, parents, labels,
  values: nodes.map((n) => n.valueMC),
  branchvalues: 'remainder',
  sort: false,
  marker: {
    colors: nodes.map((n) => n.changePercentage),
    colorscale: [[0, LOSS_COLOR], [0.5, NEUTRAL_COLOR], [1, GAIN_COLOR]],
    cmin: -3, cmid: 0, cmax: 3,
    showscale: true
  },
  texttemplate: '%{label}<br>%{customdata[1]}',
  hovertemplate: '<b>%{label}</b><br>%{customdata[0]}<br>%{customdata[1]}<br>%{customdata[2]}<extra></extra>',
  pathbar: { visible: true }
}
```

`customdata` holds the formatted market capitalisation with `mainCurrency`, the formatted change and the formatted
time of the price. The change is printed on every tile, so the colour is never the only carrier of the value.

The exclusions can be numerous, so they are shown as a list below the chart, grouped by `reasonKey`, and not as a Plotly
annotation. `notApplicableCount` greater than zero adds the line `WATCHLIST_TREEMAP_NOT_APPLICABLE`.

## 6. Texts

Application texts in `grafioschtrader-common/src/main/resources/message/messages{,_de}.properties`:

| Key | English | German |
|---|---|---|
| `WATCHLIST_TREEMAP` | Market map | Marktkarte |
| `WATCHLIST_TREEMAP_NO_EQUITIES` | This watchlist contains no stocks that can be shown. | Diese Watchlist enthält keine darstellbaren Aktien. |
| `WATCHLIST_TREEMAP_NOT_APPLICABLE` | {{count}} instruments are not direct stocks and have no market capitalisation. | {{count}} Instrumente sind keine Direktanlagen in Aktien und haben keine Marktkapitalisierung. |
| `TREEMAP_CHANGE_MISSING` | no current price change | keine aktuelle Kursänderung |
| `TREEMAP_CHANGE_STALE` | price change is outdated | Kursänderung ist veraltet |
| `TREEMAP_MARKET_CAP_MISSING` | market capitalisation missing | Marktkapitalisierung fehlt |

`TREEMAP_PRICE_MISSING` and `TREEMAP_NOT_SHOWN` exist.

## 7. Tests

Following the testing rules in `CLAUDE.md`, with independently calculated results and real domain objects:

| Test | Asserts |
|---|---|
| `WatchlistTreemapBuilderTest` | Area from `SHARES × price × rate` and from the `MCAP_CHECK` fallback in a currency other than the security's; weighted group change; one tile per ISIN keeps the newest listing; order and reason of the exclusions |
| `PriceFreshnessServiceTest`, extended | `closeOfNewestOpenedSession` during a session, after the close, before the opening, on a holiday, with a time zone ahead of UTC, and without exchange hours |
| `WatchlistTreemapBuilderTest`, freshness | The situations of the table in §4.3 |

The two named queries are checked with the test database when a suitable integration phase exists; browser behaviour
belongs in a Playwright spec of the watchlist tabs.

## 8. Limitations

These belong in the gt-user-manual when the feature ships:

- ETFs, funds, bonds and currency pairs have no market capitalisation and are not shown.
- `MCAP_CHECK` comes from Yahoo alone and is confirmed by no second source. It is as old as the last weekly load.
- A security newly added to a watchlist has no value until the next run of `FUNDAMENTAL_DATA_LOAD`.
- A company whose share classes have different ISINs appears once per class, and each tile may carry the capitalisation
  of the whole company.
- The grouping depends on the maintained asset classes. "Aktien Welt" appears as a group of its own.
- Outside trading hours the colour shows the change of the last session of the respective exchange.
