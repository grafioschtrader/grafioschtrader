# EODHD real-time quotes: unreliable `previousClose` after the US close

**Code baseline:** backend 0.40.2, highest Flyway script `V0_40_3__performance_benchmark.sql`.

## 1. Summary

The EODHD real-time endpoint (`/api/real-time/{ticker}?s=...`) returns correct `previousClose`, `change` and
`change_p` values for US stocks while the US market is open. After the US close, about one third of the US tickers
answer with a `previousClose` that already belongs to the trading day that just ended. In most of these cases it is
identical to `close`, so `change_p` is `0`. In the rest it is a few cents away from `close`, so `change_p` is near
zero. GT copies both fields unchanged into `securitycurrency.s_prev_close` and `securitycurrency.s_change_percentage`.
As a result, between the US close (22:00 CET) and the next US open (15:30 CET) the watchlists show a daily change of
0 % or near 0 % for those instruments, even though the true change is sometimes larger than 10 %.

Exchanges outside the US (XETRA, LSE, SIX, Euronext, TSX, Copenhagen, Mexico, Korea, ASX) were correct in every
snapshot.

How long EODHD has behaved like this cannot be determined. GT keeps only the latest intraday snapshot per
instrument in `securitycurrency`, so no history of `s_prev_close` / `s_change_percentage` exists.

## 2. Observations

All figures come from active securities whose intraday connector is `gt.datafeed.eodhistoricaldata`. The reference
close is `historyquote.close` of the previous trading day. For every ticker shown with figures in this section,
that close was loaded from EODHD's own end-of-day endpoint (`id_connector_history = gt.datafeed.eodhistoricaldata`).

### 2.1 During the US session — correct

Direct request on 2026-11-25. The quote timestamps are 15:07–15:08 UTC, about 1.5 hours after the US open. The
reference close is from 2026-11-24.

| Ticker | close | previousClose | change_p | Close 2026-11-24 |
|---|---|---|---|---|
| IBM.US | 238.82 | 235.62 | +1.3581 | 235.62 |
| JNJ.US | 275.2757 | 276.46 | −0.4284 | 276.46 |
| COST.US | 1095.102213 | 1091.71 | +0.3107 | 1091.71 |
| MSTR.US | 72.96 | 77.55 | −5.9188 | 77.55 |
| ORCL.US | 101.47 | 109.50 | −7.3333 | 109.50 |
| AI.US | 9.55 | 9.84 | −2.9472 | 9.84 |
| CIFR.US | 5.235 | 5.52 | −5.163 | 5.52 |

Every `previousClose` matches the close of the previous trading day.

### 2.2 After the US close — wrong for about one third

Direct request on 2026-11-26 05:40 UTC with all 73 US tickers in one call. The quote timestamps are 2026-11-25
21:00 UTC (the close of the 25th). The correct `previousClose` would be the close of 2026-11-24.

| Classification | Count | Examples |
|---|---|---|
| `previousClose` = close of 2026-11-24 (correct) | 48 | MU.US, GOOG.US, COKE.US, SBUX.US, COST.US |
| `previousClose` = `close` of 2026-11-25, `change_p = 0` | 12 | IBM.US, NVDA.US, PYPL.US, GAP.US, WY.US, PTON.US, CRWD.US, TER.US, GEHC.US, CVNA.US, BBAI.US, GEVO.US |
| `previousClose` a few cents away from `close`, `change_p` ≈ 0 | 6 | ORCL.US, JNJ.US, LLY.US, PG.US, PINS.US, ABNB.US |
| `previousClose` another price of 2026-11-25 | 6 | BYND.US, MSTR.US, MRVL.US, HUBG.US, ANT.US, OPEN.US |

LIPO.US is not counted. It did not trade on 2026-11-25, so its quote is still the 2026-11-24 close with
`change_p = 0`, which is correct.

Examples of the wrong answers:

| Ticker | close | previousClose | change_p (EODHD) | Close 2026-11-24 | Correct change |
|---|---|---|---|---|---|
| IBM.US | 243.47 | 243.47 | 0 | 235.62 | +3.33 % |
| ORCL.US | 103.88 | 103.87 | ≈ 0 | 109.50 | −5.13 % |
| JNJ.US | 273.94 | 274.22 | ≈ 0 | 276.46 | −0.91 % |
| MSTR.US | 79.96 | 80.1984 | −0.2973 | 77.55 | +3.11 % |
| BYND.US | 4.45 | 4.43 | +0.4515 | 3.70 | +20.27 % |

### 2.3 The affected tickers change from night to night

GT's own snapshots of 2026-11-25 05:50 CET, taken after the close of 2026-11-24, had the same pattern but a different
set of tickers. That night IBM, JNJ, COST, SBUX, COKE, WY, PTON, AI and CIFR had `previousClose == close`. One night
later COST, SBUX, COKE, AI and CIFR were correct again, while NVDA, PYPL and GAP had turned wrong. The roll-over
of `previousClose` to the new trading day therefore happens at a different time for each ticker, and some tickers
roll over before the quote itself does.

GT's intraday snapshots taken during the European session, before the US open (2026-11-25 10:48 and 11:01 CET),
carried the same wrong US values as the 05:50 snapshot. For US tickers, the error persists until the US market opens.

## 3. Cause

The error is in the data EODHD delivers, not in GT. `EodHistoricalDataConnector.Quote.setValues` copies `close`,
`previousClose` and `change_p` without any transformation. The batch path (`updateLastPrices`, `applyQuotes`)
matches the answers by `code`, not by position, so values cannot be swapped between tickers.

The answer of the real-time endpoint contains a `timestamp` (seconds since epoch, UTC) that identifies the trading
day the quote belongs to. `Quote` currently ignores this field.

## 4. Change in GT

GT stops trusting EODHD's `previousClose` and `change_p` and derives both from its own end-of-day history.

1. **Quote trading day.** `EodHistoricalDataConnector.Quote` reads `timestamp` and `gmtoffset`. The connector
   converts `timestamp` to the instrument's local date. For a security, the time zone is
   `security.getStockexchange().getTimeZone()`. This date is the quote trading day `dQ`.
2. **Previous close.** `s_prev_close` becomes the close of the newest `historyquote` of the instrument whose date
   is strictly before `dQ`. Both `create_type` 0 (connector) and filled rows count; the existing repository method
   `HistoryquoteJpaRepository.findFirstByIdSecuritycurrencyAndDateLessThanEqualOrderByDateDesc` with `dQ − 1 day`
   delivers it. Because the comparison is strictly before `dQ`, the result is correct both before and after the
   end-of-day load (`gt.eod.cron.quotation`) has written the bar of `dQ`.
3. **Change.** `s_change_percentage = (s_last − s_prev_close) / s_prev_close × 100`, using `s_last` after the
   minor-unit divider has been applied.
4. **Fallback.** If no such `historyquote` row exists (newly added instrument, history not yet loaded), keep the
   values from EODHD.
5. **Landing point.** The connector has no access to `historyquote`. The correction runs right after the
   connector call, in `SecurityJpaRepositoryImpl.updateIntraSecurityCurrency` and
   `SecurityJpaRepositoryImpl.updateIntraSecurityCurrencies`, for instruments whose intraday connector declares
   that its previous close needs to be derived. This needs a new default method on `IFeedConnector`, for example
   `boolean isIntradayPrevCloseFromHistory()`, which returns `false` by default and `true` in
   `EodHistoricalDataConnector`. The connector passes `dQ` to the repository layer. The simplest carrier is a
   transient field on `Securitycurrency`. Securities in a batch are corrected with one query for all ids rather
   than one query per instrument.
6. **Scope.** Securities only. Currency pairs on EODHD (`*.FOREX`) were not observed to be affected, so
   `CurrencypairJpaRepositoryImpl` stays unchanged.

### Acceptance

- A unit test of the correction with a recorded after-close answer (the IBM.US element of §2.2 together with a
  `historyquote` close of 235.62 on 2026-11-24) yields `s_prev_close = 235.62` and `s_change_percentage ≈ 3.3316`.
- The same test with a recorded in-session answer (§2.1) leaves the values unchanged, because EODHD's
  `previousClose` already equals the history close.
- An instrument without a `historyquote` row before `dQ` keeps EODHD's values.

## 5. Report to EODHD (ready to send)

> **Subject:** Real-time API: `previousClose` already rolled to the current day after US close for some tickers
>
> Hello EODHD support,
>
> We use the real-time endpoint `https://eodhistoricaldata.com/api/real-time/{ticker}?s=...&fmt=json` for US
> stocks. While the US market is open, `previousClose`, `change` and `change_p` are correct. After the US close,
> roughly one third of the tickers in our list return a `previousClose` that already belongs to the trading day
> that has just ended. In most of these cases it is equal to `close`, so `change` and `change_p` are `0`.
>
> **Request at 2026-11-26 05:40 UTC** (73 US tickers in one call), quotes with `timestamp` 1795640400
> (2026-11-25 21:00 UTC):
>
> ```
> {"code":"IBM.US","timestamp":1795640400,"gmtoffset":0,"open":233.51,"high":246.24,"low":232.17,
>  "close":243.47,"volume":5033319,"previousClose":243.47,"change":0,"change_p":0}
> ```
>
> The close of IBM.US on 2026-11-24 in your own end-of-day data is 235.62, so `previousClose` should be 235.62 and
> `change_p` +3.33. The same request during the session on 2026-11-25 (timestamp 1795619160, 15:06 UTC) correctly
> returned `"previousClose":235.62`.
>
> Further examples from the same after-close request:
>
> | Ticker | close | previousClose returned | Close 2026-11-24 (your EOD data) |
> |---|---|---|---|
> | IBM.US | 243.47 | 243.47 | 235.62 |
> | ORCL.US | 103.88 | 103.87 | 109.50 |
> | JNJ.US | 273.94 | 274.22 | 276.46 |
> | MSTR.US | 79.96 | 80.1984 | 77.55 |
> | BYND.US | 4.45 | 4.43 | 3.70 |
>
> Other tickers in the same request were correct, for example MU.US, GOOG.US, SBUX.US and COST.US. The set of
> affected tickers changes from night to night. After the close of 2026-11-24, for example, COST.US, SBUX.US and
> CIFR.US were affected, while one night later they were correct and NVDA.US, PYPL.US and GAP.US were affected
> instead. The wrong values persist until the next US open.
>
> Could you please check why `previousClose` is rolled over to the current trading day before the next session
> starts, and only for some tickers? Our expectation is that, until the next session opens, `previousClose` stays
> the close of the trading day before the one given by `timestamp`.
>
> Thank you and best regards
