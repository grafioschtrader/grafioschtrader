# IFeedConnector Search API Capabilities

Research document covering search API availability for all 24 IFeedConnector implementations.
**Reference implementation**: `YahooSymbolSearch.java` — searches via `https://query2.finance.yahoo.com/v1/finance/search`

---

## Summary Table

| # | Connector ID | Provider | Search API | ISIN | API Key | Backend Callable |
|----|---|---|---|---|---|---|
| 1 | `yahoo` | Yahoo Finance | YES | NO | No | YES |
| 2 | `alphavantage` | Alpha Vantage | YES | NO | Yes (free tier) | YES |
| 3 | `finnhub` | Finnhub | YES | YES | Yes (free tier) | YES |
| 4 | `twelvedata` | Twelve Data | YES | YES (paid) | Yes (free tier) | YES |
| 5 | `eodhistoricaldata` | EODHD | YES | YES | Yes (free tier) | YES |
| 6 | `stockdata` | StockData | YES | NO | Yes | YES |
| 7 | `onvista` | Onvista | YES | YES | No | YES (non-public) |
| 8 | `euronext` | Euronext | PARTIAL | Likely | No | Requires cookies |
| 9 | `xetra` | Xetra / Börse Frankfurt | PARTIAL | YES | No | Non-public API |
| 10 | `boursorama` | Boursorama | PARTIAL | Unknown | No | Cloudflare risk |
| 11 | `six` | SIX Swiss Exchange | PARTIAL | YES | Yes (commercial) | Commercial only |
| 12 | `vienna` | Wiener Börse | PARTIAL | YES (web) | N/A | HTML scraping only |
| 13 | `warsawgpw` | Warsaw GPW | NO | YES (web) | N/A | HTML scraping only |
| 14 | `cryptocompare` | CryptoCompare | PARTIAL | N/A | Optional | YES (coin list dump) |
| 15 | `investing` | Investing.com | YES (non-public) | YES | No | Cloudflare blocked |
| 16 | `comdirect` | Comdirect | NO | N/A | No | HTML scraping |
| 17 | `consorsbank` | Consorsbank | NO | N/A | No | Proprietary API |
| 18 | `finanzench` | Finanzen.ch | NO | N/A | No | HTML scraping |
| 19 | `finanzennet` | Finanzen.net | NO | N/A | No | Cloudflare blocked |
| 20 | `divvydiary` | DivvyDiary | NO | N/A | No | ISIN lookup only |
| 21 | `ecb` | ECB Cross Rate | NO | N/A | No | Repository-based |
| 22 | `fxubc` | FX UBC (Sauder) | NO | N/A | No | Currency pairs only |
| 23 | `swissfunddata` | Swiss Fund Data | PARTIAL | YES (web) | No | SPA; needs AJAX discovery |
| 24 | `generic` | Generic (dynamic) | NO | N/A | Configurable | Configurable |

---

## Detailed Connector Analysis

### 1. Yahoo Finance (`yahoo`)
- **Search API**: YES — already implemented in `YahooSymbolSearch.java`
- **Endpoint**: `GET https://query2.finance.yahoo.com/v1/finance/search?q={query}&lang=en-US&region=US&quotesCount=6&enableFuzzyQuery=false&enableEnhancedTrivialQuery=true`
- **ISIN support**: NO — searches by ticker/name; ISIN not recognized
- **Response fields**: `exchange`, `symbol`, `shortname`, `quoteType`, `exchDisp`, `typeDisp`
- **API key**: Not required
- **Backend callable**: YES — currently working via Java HttpClient
- **Notes**: Matching logic filters by Yahoo exchange code, symbol, or symbol with suffix (e.g., `.SW`). Uses `MicProviderMapRepository` for exchange mapping.

### 2. Alpha Vantage (`alphavantage`)
- **Search API**: YES
- **Endpoint**: `GET https://www.alphavantage.co/query?function=SYMBOL_SEARCH&keywords={query}&apikey={key}`
- **ISIN support**: NO — ticker/company name search only
- **Response fields**: `bestMatches[]` → `symbol`, `name`, `type`, `region`, `marketOpen`, `marketClose`, `timezone`, `currency`, `matchScore`
- **API key**: Required (free tier: 5 calls/min, 500/day; premium: 75 calls/min)
- **Backend callable**: YES — standard REST JSON endpoint, no blocking
- **Notes**: Already has API key infrastructure in GT (`BaseFeedApiKeyConnector`). Good candidate but no ISIN support limits usefulness.

### 3. Finnhub (`finnhub`)
- **Search API**: YES
- **Endpoint**: `GET https://finnhub.io/api/v1/search?q={query}&token={key}`
- **ISIN support**: YES — query can be symbol, name, ISIN, or CUSIP
- **Response fields**: `count`, `result[]` → `description`, `displaySymbol`, `symbol`, `type`
- **API key**: Required (free tier: 60 calls/min)
- **Backend callable**: YES — clean REST JSON API, no blocking
- **Notes**: Best candidate for ISIN-based search. Already has API key infra in GT. Free tier generous.

### 4. Twelve Data (`twelvedata`)
- **Search API**: YES
- **Endpoint**: `GET https://api.twelvedata.com/symbol_search?symbol={query}&outputsize={n}`
- **ISIN support**: YES — but requires paid "Data add-ons" activation
- **Response fields**: `symbol`, `instrument_name`, `exchange`, `type`, `country` (ordered by relevance)
- **API key**: Required (free tier for basic search; ISIN add-on is paid)
- **Backend callable**: YES — standard REST JSON endpoint
- **Notes**: ISIN search behind paywall limits usefulness for free-tier users. Rate limited (4 calls/min in GT).

### 5. EOD Historical Data (`eodhistoricaldata`)
- **Search API**: YES
- **Endpoint**: `GET https://eodhd.com/api/search/{query}?api_token={key}&fmt=json&limit={n}`
- **ISIN support**: YES — auto-detects if query is ticker, name, or ISIN
- **Response fields**: `Code`, `Name`, `Exchange`, `Country`, `ISIN`, `Currency`, `Type`
- **API key**: Required (each search call = 1 API credit)
- **Backend callable**: YES — standard REST JSON endpoint
- **Notes**: Results ranked by market cap/volume. Also has `/api/id-mapping` for CUSIP/ISIN/FIGI/LEI conversion. Strong candidate.

### 6. StockData (`stockdata`)
- **Search API**: YES
- **Endpoint**: `GET https://api.stockdata.org/v1/entity/search?search={query}&api_token={key}`
- **ISIN support**: NO — ticker/name search only
- **Response fields**: `symbol`, `name`, `type`, `industry`, `exchange`, `exchange_long`, `mic_code`, `country`
- **API key**: Required
- **Backend callable**: YES — standard REST JSON endpoint
- **Notes**: Max 50 results per request. No ISIN support.

### 7. Onvista (`onvista`)
- **Search API**: YES (non-public/undocumented)
- **Endpoint**: `GET https://api.onvista.de/api/v1/instruments/search/facet?searchValue={query}&perType=10`
- **ISIN support**: YES — supports ISIN as search value
- **Response fields**: Instrument type, name, ISIN, notation IDs, URLs, exchange info
- **API key**: Not required
- **Backend callable**: YES — no authentication, but non-public API; may change without notice
- **Notes**: GT already uses `api.onvista.de` for price data. Adding search would be natural extension. Risk: undocumented API may break.

### 8. Euronext (`euronext`)
- **Search API**: PARTIAL (internal AJAX endpoint)
- **Endpoint**: `POST https://live.euronext.com/en/search_instruments/{query}` (requires cookies/session)
- **ISIN support**: Likely YES (website search accepts ISIN)
- **Response fields**: Instrument name, ISIN, MIC, symbol, instrument type
- **API key**: Not required (but needs session cookies from page visit)
- **Backend callable**: Difficult — requires cookies from prior page load; Cloudflare protection possible
- **Notes**: Official Euronext APIs (CONNEXOR, Stream API) are commercial. Internal endpoint fragile.

### 9. Xetra / Börse Frankfurt (`xetra`)
- **Search API**: PARTIAL (undocumented internal)
- **Endpoint**: `https://api.boerse-frankfurt.de/v1/search/...` (undocumented; website uses this internally)
- **ISIN support**: YES — API accepts ISIN parameters (e.g., `?isin=DE0007236101`)
- **Response fields**: ISIN, bidLimit, askLimit, lastPrice, tradingStatus
- **API key**: Not required for undocumented API; official Deutsche Börse API Platform is commercial
- **Backend callable**: Possible but fragile — undocumented API may break without notice
- **Notes**: GT already uses `api.boerse-frankfurt.de` for historical TradingView data. Search extension possible but risky.

### 10. Boursorama (`boursorama`)
- **Search API**: PARTIAL (internal/undocumented)
- **Endpoint**: Website uses internal search/autocomplete at `www.boursorama.com/recherche/` — exact AJAX URL undocumented
- **ISIN support**: Unknown — likely via website search
- **Response fields**: Market, name, price, symbol (from third-party scrapers)
- **API key**: Not required
- **Backend callable**: Risky — Cloudflare protection; no official API for market data (Boursorama API is DSP2 banking only)
- **Notes**: Not recommended for search integration.

### 11. SIX Swiss Exchange (`six`)
- **Search API**: PARTIAL (commercial only)
- **Endpoint**: Public website has product search at `https://www.six-group.com/en/market-data/news-tools/product-search.html` (web-based, not REST). Commercial CONNEXOR API supports ISIN-based REST lookups.
- **ISIN support**: YES (via commercial CONNEXOR API)
- **Response fields**: Reference data per ISIN including terms, corporate actions, lifecycle events (JSON/CSV/XML)
- **API key**: Required (commercial license, pay-per-ISIN)
- **Backend callable**: Only with commercial license
- **Notes**: Not viable for open-source project without paid license.

### 12. Vienna Stock Exchange (`vienna`)
- **Search API**: PARTIAL (web search only)
- **Endpoint**: Website search at `https://www.wienerborse.at/en/market-data/` — no documented REST API
- **ISIN support**: YES — website search accepts ISIN (minimum 3 characters)
- **Response fields**: N/A (HTML response)
- **API key**: N/A
- **Backend callable**: Only via HTML scraping (Jsoup) — fragile
- **Notes**: GT already scrapes this site for price data. Adding search scraping possible but maintenance-heavy.

### 13. Warsaw GPW (`warsawgpw`)
- **Search API**: NO
- **Endpoint**: No public REST API. Website provides HTML pages only. Commercial Market Data Gateway for licensed distributors.
- **ISIN support**: YES via URL pattern (`gpw.pl/company-factsheet?isin=...`) but not a search API
- **Response fields**: N/A
- **API key**: N/A (commercial gateway requires license)
- **Backend callable**: Only via HTML scraping
- **Notes**: No search API available.

### 14. CryptoCompare (`cryptocompare`)
- **Search API**: PARTIAL — coin list dump only, no search query
- **Endpoint**: `GET https://min-api.cryptocompare.com/data/all/coinlist`
- **ISIN support**: N/A (cryptocurrency — no ISIN concept)
- **Response fields**: Per coin: `Id`, `Name`, `Symbol`, `CoinName`, `FullName`, `Algorithm`, `ProofType`, `TotalCoinSupply`, `ImageUrl`
- **API key**: Optional (rate limits apply without key)
- **Backend callable**: YES — but returns entire coin list; client-side filtering needed
- **Notes**: Could cache coin list and filter locally. Not a true search API.

### 15. Investing.com (`investing`)
- **Search API**: YES (two known endpoints, both non-public/undocumented)
- **Website search URL**: `https://www.investing.com/search/?q={query}` (e.g., `?q=IE00B66F4759` for ISIN search)
- **Endpoint 1 — SearchInnerPage** (used by investpy library):
  - `POST https://www.investing.com/search/service/SearchInnerPage`
  - Parameters: `search_text` (query string), `tab` ("quotes"), `isFilter` (true), `limit` (270), `offset` (0)
  - Response: JSON with search results, each containing: `id_` (internal pair ID), `name`, `symbol`, `tag` (URL path), `country`, `pair_type` (asset class), `exchange`
  - Headers: Requires randomized User-Agent to avoid bot detection
  - **Status**: Cloudflare V2 protected since ~2022; returns 403 Forbidden from server-side code
- **Endpoint 2 — TVC search** (TradingView charting infrastructure):
  - GT already uses `tvc4.investing.com` for historical data (see `InvestingConnector.java` line 56)
  - URL pattern: `https://tvc6.investing.com/{guid}/{timestamp}/56/56/23/history?symbol={id}&resolution=D&from={epoch}&to={epoch}`
  - The TVC servers expose `symbol_info` and `history` endpoints but no documented `search` or `symbol_search` endpoint
  - `tvc4` was banned; `tvc6` works but is also intermittently Cloudflare protected
- **ISIN support**: YES — the website search (`/search/?q=IE00B66F4759`) supports ISIN input and returns matching instruments
- **API key**: Not required (no official API exists)
- **Backend callable**: NO — all known endpoints are Cloudflare protected; direct HTTP requests return 403
- **Current GT usage**: Intraday only via Jsoup HTML scraping of instrument pages (e.g., `currencies/eur-usd`). Historical data via `tvc4` is code-present but commented as broken since 2022-10-18.
- **Third-party libraries**: `investpy` (Python, deprecated/broken), `investiny` (Python, uses tvc6, intermittent 403s), `investing-com-api-v2` (Node.js, no longer supported)
- **URL extension mapping problem**:
  - GT's `urlIntraExtend` requires Investing.com's internal URL slug in `category/slug` format (regex: `^(?!equities)[A-Za-z\-]+\/[A-Za-z0-9_\(\)\-\.]+$`)
  - Example: For ticker "IWN" (ISIN US4642876308), the required value is `etfs/ishares-russell-2000-index-etf`
  - This slug **cannot be derived** from ticker or ISIN alone — it's Investing.com's internal naming convention
  - The `SearchInnerPage` response `tag` field returns exactly this slug (e.g., `etfs/ishares-russell-2000-index-etf`), which maps directly to `urlIntraExtend`
  - For historical data, the `id_` field from the search response provides the numeric pair ID needed for `urlHistoryExtend` (regex: `^\d+$`)
  - So the search API would solve both URL extension lookups — **if it weren't Cloudflare blocked**
- **Implementation notes**:
  - Investing.com explicitly states they do not offer a public API ([support article](https://www.investing-support.com/hc/en-us/articles/115005473825))
  - The `SearchInnerPage` endpoint is the only known way to programmatically resolve ticker/ISIN → `urlIntraExtend` (tag) and `urlHistoryExtend` (id_)
  - However, Cloudflare protection makes server-side calls unreliable — would need browser-based scraping or a proxy approach
  - Not recommended for automated search integration due to instability and TOS concerns

### 16. Comdirect (`comdirect`)
- **Search API**: NO
- **Endpoint**: HTML scraping only for intraday data
- **Backend callable**: HTML scraping with Jsoup — no search API known
- **Notes**: No search functionality available.

### 17. Consorsbank (`consorsbank`)
- **Search API**: NO
- **Endpoint**: Proprietary API for intraday only (`web-financialinfo-service/api/marketdata/stocks`)
- **Backend callable**: Limited proprietary API
- **Notes**: No search functionality available.

### 18. Finanzen.ch (`finanzench`)
- **Search API**: NO
- **Endpoint**: HTML scraping for price data
- **Backend callable**: HTML scraping only
- **Notes**: No search API available.

### 19. Finanzen.net (`finanzennet`)
- **Search API**: NO
- **Endpoint**: Website has search but protected by Cloudflare
- **Backend callable**: NO — Cloudflare blocks non-browser requests
- **Notes**: Not viable.

### 20. DivvyDiary (`divvydiary`)
- **Search API**: NO — ISIN-based direct lookup only
- **Endpoint**: `GET https://api.divvydiary.com/symbols/{ISIN}` — requires known ISIN, not a search
- **Backend callable**: YES — but for dividend data only, not search
- **Notes**: Could verify ISIN existence but not search.

### 21. ECB Cross Rate (`ecb`)
- **Search API**: NO — repository-based currency pair lookups only
- **Notes**: Fixed set of currency pairs from ECB. No external search needed.

### 22. FX UBC (`fxubc`)
- **Search API**: NO — fixed currency pair endpoint only
- **Notes**: University of British Columbia FX data. No search API.

### 23. Swiss Fund Data (`swissfunddata`)
- **Search API**: PARTIAL (web-based search, no documented public REST API)
- **Website search**: `https://www.swissfunddata.ch/sfdpub/fund-search` (Quick Search) and `https://www.swissfunddata.ch/sfdpub/detailed-fund-search` (Detailed Search)
- **Search fields available on website**:
  - Fund name (free text)
  - ISIN
  - Valor number (Swiss security number)
  - Asset class
  - Fund provider
  - Country
  - Performance
  - Parameters can be combined
- **ISIN support**: YES — website search accepts ISIN as search input
- **Valor support**: YES — Swiss security number (Valor) is a primary search field
- **Fund detail page URL**: `https://www.swissfunddata.ch/sfdpub/en/funds/show/{fundId}` (e.g., `/funds/show/22083` for "Win Fund Equity-Index Switzerland A")
- **CSV export URL** (already used by GT): `https://www.swissfunddata.ch/sfdpub/de/funds/excelData/{fundId}` — downloads entire price history as CSV
- **API key**: Not required for website access
- **Backend callable**:
  - Website search: JavaScript-rendered (Angular/SPA), not directly callable via simple HTTP GET — requires browser rendering or discovery of underlying AJAX endpoints
  - CSV export: YES — already working in GT (direct URL download)
  - The site blocks direct HTTP requests from non-browser clients (ECONNREFUSED observed)
- **Official API**: No public REST API documented. Swiss Fund Data offers commercial data delivery services with "efficient interfaces and structured formats" for partners and print media, but these require a business relationship
- **OpenFIGI integration**: Swiss Fund Data is listed as a third-party facilitator on OpenFIGI, suggesting they participate in standardized financial identifier mapping
- **Supported asset classes in GT**: ETF, Mutual Fund, Real Estate Fund, Issuer Risk Product
- **Current GT usage**: URL extension is a numeric fund ID (regex `^\d+$`); the connector downloads the full CSV history file on each call
- **Implementation notes**:
  - The website search UI is JavaScript-rendered (SPA), so the underlying AJAX search endpoints are not directly visible without browser DevTools inspection
  - A potential approach would be to inspect network traffic in a browser to discover the internal search API endpoints (likely POST requests to `/sfdpub/...` paths)
  - The Quick Search page (`/sfdpub/fund-search`) is the best candidate for reverse-engineering since it's the simplest search form
  - Alternative: Use the fund detail page URL pattern `/funds/show/{id}` — if a mapping from ISIN/Valor to fund ID can be discovered, the existing CSV connector could be auto-configured
  - The site appears to use Azure AD B2C for authentication (`/sfdpub/oauth2/authorization/B2C_1A_signup_signin`), but basic search appears to work without login

### 24. Generic Connector (`generic`)
- **Search API**: NO — configurable data feed only
- **Notes**: Dynamic connector driven by `GenericConnectorDef` database configuration. Search would need per-definition configuration.

---

## Implementation Priority

### Tier 1 — Best candidates (official API, ISIN support, free tier)
| Connector | ISIN | Free | Notes |
|---|---|---|---|
| **Finnhub** | YES | 60/min | Best overall: ISIN+name+symbol search, generous free tier |
| **EODHD** | YES | Limited | Auto-detects ISIN/ticker/name; 1 credit per search |

### Tier 2 — Good candidates (official API, no ISIN)
| Connector | ISIN | Free | Notes |
|---|---|---|---|
| **Alpha Vantage** | NO | 5/min | Ticker/name only; `matchScore` ranking useful |
| **StockData** | NO | Limited | Ticker/name only; max 50 results |
| **Twelve Data** | Paid add-on | 4/min | ISIN requires paid plan |

### Tier 3 — Possible but fragile (undocumented APIs)
| Connector | ISIN | Free | Notes |
|---|---|---|---|
| **Onvista** | YES | Yes | Non-public API; may break without notice |
| **Xetra** | YES | Yes | Undocumented internal API |
| **Euronext** | Likely | Yes | Needs session cookies; fragile |

### Tier 4 — Requires browser inspection (SPA with hidden AJAX)
| Connector | ISIN | Free | Notes |
|---|---|---|---|
| **Swiss Fund Data** | YES (web) | Yes | SPA search UI; AJAX endpoints need DevTools discovery; ISIN+Valor search |

### Not viable for search
Boursorama, SIX (commercial), Vienna (web only), Warsaw (no API), CryptoCompare (coin list dump), Investing (Cloudflare), Comdirect, Consorsbank, Finanzen.ch, Finanzen.net, DivvyDiary, ECB, FxUBC, Generic.
