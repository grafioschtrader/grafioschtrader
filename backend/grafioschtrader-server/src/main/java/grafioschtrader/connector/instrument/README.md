## Intraday price data
### Possible candidates
### To observe
### Does not work

### Should be implemented 

## Historical price data

### Possible candidates

### To observe
Before it can be implemented, these points still need to be clarified.
### Nordic Nasdaq (Nasdaq: NDAQ)

```
<post>
<param+name="Exchange"+value="NMF"/>
<param+name="SubSystem"+value="History"/>
<param+name="Action"+value="GetDataSeries"/>
<param+name="AppendIntraDay"+value="no"/>
<param+name="FromDate"+value="2000-01-03"/>
<param+name="ToDate"+value="2023-01-16"/>
<param+name="Instrument"+value="SSE160271"/>
<param+name="hi__a"+value="0,5,6,3,1,2,4,21,8,10,12,9,11"/>
<param+name="OmitNoTrade"+value="true"/>
<param+name="ext_xslt_lang"+value="en"/>
<param+name="ext_xslt"+value="/nordicV3/hi_csv.xsl"/>
<param+name="ext_xslt_options"+value=",adjusted,"/>
<param+name="ext_contenttype"+value="application/ms-excel"/>
<param+name="ext_contenttypefilename"+value="NDA-SE-2000-01-03-2023-01-16.csv"/>
<param+name="ext_xslt_hiddenattrs"+value=",iv,ip,"/>
<param+name="ext_xslt_tableId"+value="historicalTable"/>
<param+name="DefaultDecimals"+value="false"/>
<param+name="app"+value="/shares/historicalprices"/>
</post>
`` 

### Does not work
Listed here are those data sources that were considered possible candidates but ultimately did not seem feasible for implementation.

#### Stooq (stooq.com)

Checked 2026-08-14 against production `grafioschtrader` (Apple / Yahoo and BTC/USD / CoinMarketCap). Third-party profile of the public CSV interface: https://github.com/api-evangelist/stooq

- Machine download is `GET https://stooq.com/q/d/l/?s={symbol}&d1=YYYYMMDD&d2=YYYYMMDD&i=d&apikey=...`. Without a valid `apikey` the body is `Access denied` (HTTP 200). The key has been required since about 2026-04-01; it is issued after a visual captcha on `https://stooq.com/q/d/?s=aapl.us&get_apikey`. A dummy key is rejected the same way.
- Tickers: `aapl.us`, crypto `btc.v`, FX often `eurusd` or `gbp.v`. Interval `i=d` is one bar per day. Quota exceeded is also HTTP 200 with body `Exceeded the daily hits limit`.
- HTML table scraping is not a fallback: about 40 rows per page, extra crypto pages empty without a session. Do not scrape.
- Recent Apple daily bars match Yahoo/GT to the cent. 2008 Apple closes from the HTML table were systematically about 16% below GT's Yahoo series (likely dividend adjustment vs split-adjusted close). Recent BTC/USD was within about 0.9–4.3% of CoinMarketCap. Full-CSV quality was not re-checked because no key was available.

A connector would be a `BaseFeedApiKeyConnector`. Do not add it until an administrator has a key and the 2008 Apple gap has been re-measured on the CSV series.

### Should be implemented 
