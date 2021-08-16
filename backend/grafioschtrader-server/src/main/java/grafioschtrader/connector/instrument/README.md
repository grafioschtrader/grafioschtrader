## Intraday price data
### Possible candidates
### To observe
### Does not work

### Should be implemented 

## Historical price data

### Possible candidates

### To observe
Before it can be implemented, these points still need to be clarified.

#### [Wiener Boerse](//www.wienerborse.at/) - Free
- It works with an **ID_NOTATION** and uses an array for request c48840[DATETIME_TZ_END_RANGE]. Does 
this array change over time?

### Does not work
Listed here are those data sources that were considered possible candidates but ultimately did not seem feasible for implementation.

### Should be implemented 
#### [Euronext](//www.euronext.com/en) - Free
- Over 2 years take rates from price chart. It only contains the date and close price.
- For the last two years or less take the historical rates from historical prices.
#### [EOD Historical Data](//eodhistoricaldata.com/) - Paid
This data source provides data from over 60 stock exchanges and at a very "reasonable" price.
