**Which part and what Version?**
Version: 0.37.0
[x] Backend
[ ] Frontend

## Problem

Share-price sources have different limitations in historical coverage, request quotas and endpoint stability. Eulerpool provides another API-backed source that accepts ISINs. A programmed connector is needed to handle its exchange-specific quotes and provide historical prices, intraday prices, dividends and splits together.

## Solution

Implement Eulerpool as `EulerpoolFeedConnector`, a Java connector extending `BaseFeedApiKeyConnector`, with provider ID `eulerpool` and connector ID `gt.datafeed.eulerpool`. An administrator enables access by storing a personal API key for Eulerpool. This implementation does not require a generic-connector database definition or activation of a generic connector.

The connector supports shares through four endpoints under `https://api.eulerpool.com/api/1/`:

| Feed | Endpoint | Behaviour |
| --- | --- | --- |
| Historical prices | `charting/ohlcv/{identifier}?resolution=D&from=...&to=...` | Reads parallel OHLCV arrays, keeps the requested date interval, excludes the current day and deduplicates dates. Prices are split-adjusted. |
| Intraday prices | `market/quotes/exchanges/{identifier}` | Prefers a quote in the instrument's currency, favouring the provider's primary listing when its currency matches. If no currency matches, falls back to the primary listing or the first available listing. Uses a positive bid, otherwise the ask, and records the quote timestamp. |
| Splits | `equity/splits/{identifier}` | Returns split dates and factors within the requested date interval. |
| Dividends | `equity/dividends/{identifier}` | Returns ex-dates, available payment dates and split-adjusted dividend amounts from the requested starting date. |

Requests use the API key as the `token` query parameter. Error messages mask that parameter. The connector limits requests to five per second and retries an HTTP 500 response once. GT reports intraday prices as delayed by 15 minutes.

Use the share's ISIN for the URL extensions, for example `US0378331005` for Apple. Tickers, WKN, CUSIP and SEDOL identifiers are also accepted. ISINs are preferred because an ISIN and ticker for the same company can return different price series. Use the same identifier for all four feeds.

## Restrictions and data quality

- This GT connector supports shares only. ETFs, funds, indices and currency pairs remain outside its scope, regardless of separate endpoints offered by Eulerpool.
- Quote selection does not convert currencies or guarantee the instrument's configured exchange. Its fallback can select a quote in another currency, so users must verify the listing and currency.
- Historical prices on a share's home market normally use that market's currency. Observed London listings could not reliably be interpreted as either pence or pounds; users should compare them with another source.
- The provider returns isolated Saturday quotes: Cisco on 2016-02-27 and Coca-Cola on 2020-11-14. Nestlé and Geberit each contain zero closing prices on 2021-01-04 and 2021-01-12. The connector currently preserves these anomalies, and the live tests assert their exact dates rather than accepting arbitrary weekend or zero-price rows.
- Dividend amounts, especially older Swiss series, should be checked against another source.
- As of 2026-09-20, Eulerpool's [free plan](https://eulerpool.com/financial-data-api/pricing) includes 100,000 requests per month for non-commercial use and delayed intraday quotes. Its [licensing terms](https://eulerpool.com/financial-data-api/licensing) require visible "Data by Eulerpool" attribution linking to Eulerpool wherever data or charts are displayed publicly. Redistribution of raw data, including through GTNet, requires appropriate rights; a personal API key alone does not grant them.

## Validation and documentation

`EulerpoolFeedConnectorTest` runs against the live API with the `prod` profile, which disables Flyway and reads the developer database for its API key. All 21 cases passed on 2026-09-20 without skips: six historical-price cases, six last-price cases, five split cases, three dividend cases and one API-key masking case for an unsupported index. The cases also check date ordering and uniqueness, quote timestamps, split factors and split-adjusted dividend amounts and currencies. The shared dividend fixture now passes the MIC and currency to the correct constructor parameters.

The German and English user-manual pages under `content/watchlistinstrument/externaldata/` list Eulerpool in the connector table and explain setup, supported feeds, currency limitations, provider anomalies and licensing restrictions.
