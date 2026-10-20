# Recorded indicator inputs

Recorded read-only from `grafioschtrader_t` on 2026-10-19; evaluation date 2026-10-15.
`spi.csv`: iShares Core SPI (CH) ETF, CHF. `benchmark.csv`: iShares J.P. Morgan EM Local Govt Bond UCITS ETF, CHF.
Each contains the newest 60 daily observations through the evaluation date, excluding create type 1.
`dividends.csv` contains the SPI instrument's split-adjusted dividend history in CHF through that date.
These frozen test-database snapshots are calculation fixtures, not current market-data assertions.
`WatchlistIndicatorFunctionsTest.recordedInputs` spells out the independent reference formulas.
