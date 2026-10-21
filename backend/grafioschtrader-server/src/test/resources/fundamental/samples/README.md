# Yahoo field contract

The `yahoo-*-2026-10-20.json` files are excerpts of public `quoteSummary` responses fetched on 2026-10-20 from
`https://query2.finance.yahoo.com/v10/finance/quoteSummary/{symbol}` with the modules `defaultKeyStatistics`,
`financialData`, `summaryDetail` and `price`. Only fields used by the mapping or the observations below are retained.
Values and their raw/fmt wrappers are unchanged. Cookies, crumbs and request headers are not included.

| Listing | Quote currency | Reporting currency | Book value per listed share | Company shares in listing units |
|---|---|---|---|---|
| Nestle `NESN.SW` | CHF | CHF | 11.36 CHF | 2,572,220,000 |
| Novo Nordisk `NOV.DE` | EUR | DKK | 6.6848507 EUR | 4,413,110,124 |
| Alibaba ADR `BABA` | USD | CNY | 67.17209 USD per ADR | 2,487,744,939 ADR equivalents |
| AstraZeneca `AZN.L` | GBp (pence) | USD | 24.511501 GBP | 1,550,905,742 |

## Mapping

- `bookValue` is in the **major quote currency**, on the listing's share basis. For Novo Nordisk,
  34.905 / 6.6848507 reproduces `priceToBook` 5.2215075; treating the book value as DKK would convert it twice.
  Alibaba's 108.5887 / 67.17209 similarly reproduces 1.6165748, establishing USD per ADR rather than CNY per
  ordinary share. AstraZeneca's book value is in pounds; Yahoo's own `priceToBook` erroneously uses the price in
  pence (12604 / 24.511501 = 514.2076). GT's price in pounds yields approximately 5.1421.
- `totalRevenue`, `totalDebt` and `totalCash` use **financialCurrency**, in full units. Their currency is not
  replaced by the quote currency. `NET_DEBT = totalDebt - totalCash`; Alibaba therefore has negative net debt of
  119,166,992,384 CNY, not USD. Currency conversion happens when GT reads the selected values.
- Prefer `impliedSharesOutstanding` to `sharesOutstanding`, falling back when the implied count is absent or
  non-positive. Novo's `sharesOutstanding` is 3,338,238,124 B shares, whereas its market cap covers the implied
  4,413,110,124 total. The latter times 34.905 EUR reproduces the 154,039,615,488 EUR market cap. This is a
  company-wide count expressed in units of the listing; for Alibaba it is ADR equivalents.
- `SPS_TTM = totalRevenue /` that same share count, in financialCurrency per listing-equivalent share.
  It is deliberately not `revenuePerShare`, which can use a different denominator. Nor is Yahoo's
  `priceToSalesTrailing12Months` evidence of a currency conversion: on these foreign listings it divides a
  quote-currency market cap by a reporting-currency revenue without conversion.
- A Yahoo share count and the revenue divided by it are selectable only after `GT price * shares` reproduces
  Yahoo market capitalisation within 20%, converting the GT price to Yahoo's major quote currency if needed.
  A failed or unavailable check does not establish the ADR/class basis. This check validates the denominator,
  not the accuracy of the reported revenue.
- Shares have no reporting period. A passed check allows a fresh undated count to be `CHECKED`; the 21-day
  fetch-time expiry still applies. An undated earnings value alone remains ineligible.

The tests use these recorded prices and independent expected mapped amounts. Modified copies cover a wrong ADR
ratio, an incomplete share class, missing market capitalisation, missing currency and invalid values.

# Finnhub and Eulerpool field contract

The `finnhub-*-2026-10-21.json` and `eulerpool-*-2026-10-21.json` files were recorded on 2026-10-21 by
`FundamentalSourceCoverageProbeTest` (sources `finnhub,eulerpool`, six reference securities). API keys are not part
of an answer and were masked in the probe output in any case.

- **Finnhub** (`stock/metric?metric=all`): the `metric` object is kept whole, the `series` block is cut to the first
  three annual and four quarterly `eps` entries. The first annual entry carries the period of `epsAnnual` (Apple
  7.465 on 2025-09-27, Merck 7.2812 on 2025-12-31), so the fiscal year earnings are dated from it. The trailing,
  book value and sales values stay undated. Only the two US listings answered; the free plan refuses Nestle,
  Novo Nordisk in EUR and Deutsche Telekom with 403.
- **Eulerpool** (`equity/metrics/{ISIN}`): the answer is kept whole; the companion `financials-ltm` answer of the
  probe is dropped because the mapping does not use it. All amounts are in `currency`, the reporting currency
  (Novo Nordisk DKK, Alibaba CNY), for the newest `historical[].period`. `other.shares` is in millions, `leverage
  .netDebt` and `historical[].netIncome` in full units. Alibaba's `perShare.eps` 42.89 CNY is per ADS (eight
  ordinary shares) while `other.shares` 19 318 million counts ordinary shares, so the share count fails the market
  capitalisation check against the ADR. `netIncome` includes non-controlling interests (Deutsche Telekom 14.93
  billion EUR against EPS times shares of 9.67 billion), which is why it is not used for a unit check.
