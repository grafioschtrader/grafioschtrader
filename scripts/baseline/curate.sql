-- ---------------------------------------------------------------------------------------------------------------------
-- Curates the source database of the Flyway baseline B0_38_0 (issue #268).
--
-- The source is a database built from empty by the Flyway chain V0_10_0 ... V0_37_3 and named `grafioschtrader`.
-- V0_10_0 dates from 2022: many of its securities name connectors and tickers that have changed since. This script
-- brings their master data up to date from a maintained installation, `grafioschtrader_prod`, which is only read.
-- Afterwards scripts/export-gt-baseline.mjs writes the baseline from the curated database.
--
--   mysql --user=... --password --default-character-set=utf8mb4 grafioschtrader < scripts/baseline/curate.sql
--
-- What it does, in this order:
--   1. Copies the master data of every seeded security from grafioschtrader_prod: name, ticker, active period,
--      distribution frequency, product link, price formula, bond terms, every connector with its URL extension,
--      stock exchange and asset class. Runtime state (last prices, timestamps, retry counters, GTNet flags, volume)
--      is not copied. The original seed shares its ids with grafioschtrader_prod; the risk-free rate series of
--      V0_35_5 got other auto-increment ids there and are paired by name and currency.
--   2. Adds the currency pairs whose securitycurrency rows V0_10_0 seeded without a currencypair row, with the
--      connectors of grafioschtrader_prod.
--   3. Replaces the connector EOD Historical Data by Yahoo on securities and currency pairs.
--   4. Deletes matured bonds and securities that have not been traded for years.
--   5. Corrects active periods that would stop a still working instrument from being updated.
-- ---------------------------------------------------------------------------------------------------------------------

START TRANSACTION;

-- 1. Master data of the seeded securities -----------------------------------------------------------------------------

CREATE TEMPORARY TABLE sec_map (id_new INT PRIMARY KEY, id_prod INT NOT NULL);
INSERT INTO sec_map
SELECT n.id_securitycurrency, p.id_securitycurrency
FROM security n
JOIN grafioschtrader_prod.security p ON p.id_securitycurrency = n.id_securitycurrency
  AND p.isin <=> n.isin AND p.currency = n.currency AND p.id_tenant_private IS NULL;
INSERT INTO sec_map
SELECT n.id_securitycurrency, p.id_securitycurrency
FROM security n
JOIN grafioschtrader_prod.security p ON p.name = n.name AND p.currency = n.currency AND p.id_tenant_private IS NULL
WHERE n.id_securitycurrency NOT IN (SELECT id_new FROM sec_map);

-- Stock exchange and asset class are only taken over when the id exists here as well; the asset classes a user
-- created in grafioschtrader_prod do not.
UPDATE security n
JOIN sec_map m ON m.id_new = n.id_securitycurrency
JOIN grafioschtrader_prod.security p ON p.id_securitycurrency = m.id_prod
SET n.name = p.name,
    n.ticker_symbol = p.ticker_symbol,
    n.active_from_date = p.active_from_date,
    n.active_to_date = p.active_to_date,
    n.dist_frequency = p.dist_frequency,
    n.product_link = p.product_link,
    n.formula_prices = p.formula_prices,
    n.simulation_metadata = p.simulation_metadata,
    n.id_connector_dividend = p.id_connector_dividend,
    n.url_dividend_extend = p.url_dividend_extend,
    n.id_connector_split = p.id_connector_split,
    n.url_split_extend = p.url_split_extend,
    n.id_stockexchange = IF(EXISTS (SELECT 1 FROM stockexchange e WHERE e.id_stockexchange = p.id_stockexchange),
                            p.id_stockexchange, n.id_stockexchange),
    n.id_asset_class = IF(EXISTS (SELECT 1 FROM assetclass a WHERE a.id_asset_class = p.id_asset_class),
                          p.id_asset_class, n.id_asset_class);

UPDATE securitycurrency n
JOIN sec_map m ON m.id_new = n.id_securitycurrency
JOIN grafioschtrader_prod.securitycurrency p ON p.id_securitycurrency = m.id_prod
SET n.id_connector_history = p.id_connector_history,
    n.url_history_extend = p.url_history_extend,
    n.id_connector_intra = p.id_connector_intra,
    n.url_intra_extend = p.url_intra_extend,
    n.stockexchange_link = p.stockexchange_link;

-- 2. Currency pairs seeded without their currencypair row -------------------------------------------------------------

INSERT IGNORE INTO currencypair (id_securitycurrency, from_currency, to_currency)
SELECT p.id_securitycurrency, p.from_currency, p.to_currency
FROM grafioschtrader_prod.currencypair p
JOIN securitycurrency sc ON sc.id_securitycurrency = p.id_securitycurrency AND sc.dtype = 'C';

UPDATE securitycurrency n
JOIN currencypair c ON c.id_securitycurrency = n.id_securitycurrency
JOIN grafioschtrader_prod.securitycurrency p ON p.id_securitycurrency = n.id_securitycurrency
SET n.id_connector_history = p.id_connector_history,
    n.url_history_extend = p.url_history_extend,
    n.id_connector_intra = p.id_connector_intra,
    n.url_intra_extend = p.url_intra_extend;

-- 3. EOD Historical Data -> Yahoo -------------------------------------------------------------------------------------
-- EOD writes SYMBOL.EXCHANGE. Yahoo drops the suffix of US listings and uses its own suffix elsewhere. A currency pair
-- carries no URL extension: Yahoo derives the symbol from the two currencies.

CREATE TEMPORARY TABLE eod_yahoo_suffix (eod VARCHAR(10) PRIMARY KEY, yahoo VARCHAR(10) NOT NULL);
INSERT INTO eod_yahoo_suffix VALUES ('US', ''), ('LSE', '.L'), ('XETRA', '.DE'), ('SW', '.SW'), ('PA', '.PA'),
  ('TO', '.TO'), ('AU', '.AX'), ('MI', '.MI'), ('AS', '.AS'), ('MC', '.MC'), ('VI', '.VI');

UPDATE securitycurrency sc
JOIN eod_yahoo_suffix x ON x.eod = SUBSTRING_INDEX(sc.url_history_extend, '.', -1)
SET sc.id_connector_history = 'gt.datafeed.yahoo',
    sc.url_history_extend = CONCAT(SUBSTRING_INDEX(sc.url_history_extend, '.', 1), x.yahoo)
WHERE sc.id_connector_history = 'gt.datafeed.eodhistoricaldata';
UPDATE securitycurrency sc
JOIN eod_yahoo_suffix x ON x.eod = SUBSTRING_INDEX(sc.url_intra_extend, '.', -1)
SET sc.id_connector_intra = 'gt.datafeed.yahoo',
    sc.url_intra_extend = CONCAT(SUBSTRING_INDEX(sc.url_intra_extend, '.', 1), x.yahoo)
WHERE sc.id_connector_intra = 'gt.datafeed.eodhistoricaldata';
UPDATE security s
JOIN eod_yahoo_suffix x ON x.eod = SUBSTRING_INDEX(s.url_dividend_extend, '.', -1)
SET s.id_connector_dividend = 'gt.datafeed.yahoo',
    s.url_dividend_extend = CONCAT(SUBSTRING_INDEX(s.url_dividend_extend, '.', 1), x.yahoo)
WHERE s.id_connector_dividend = 'gt.datafeed.eodhistoricaldata';
UPDATE security s
JOIN eod_yahoo_suffix x ON x.eod = SUBSTRING_INDEX(s.url_split_extend, '.', -1)
SET s.id_connector_split = 'gt.datafeed.yahoo',
    s.url_split_extend = CONCAT(SUBSTRING_INDEX(s.url_split_extend, '.', 1), x.yahoo)
WHERE s.id_connector_split = 'gt.datafeed.eodhistoricaldata';

UPDATE securitycurrency
SET id_connector_history = 'gt.datafeed.yahoo'
WHERE dtype = 'C' AND id_connector_history = 'gt.datafeed.eodhistoricaldata' AND url_history_extend IS NULL;
UPDATE securitycurrency
SET id_connector_intra = 'gt.datafeed.yahoo'
WHERE dtype = 'C' AND id_connector_intra = 'gt.datafeed.eodhistoricaldata' AND url_intra_extend IS NULL;

-- 4. Matured bonds and dead securities --------------------------------------------------------------------------------
-- historyquote and historyquote_quality follow by ON DELETE CASCADE. A derived security has to go before its base.

CREATE TEMPORARY TABLE sec_delete (id INT PRIMARY KEY);
-- Bonds (asset class category FIXED_INCOME) whose active period has ended.
INSERT INTO sec_delete
SELECT s.id_securitycurrency
FROM security s JOIN assetclass a ON a.id_asset_class = s.id_asset_class
WHERE a.category_type = 1 AND s.active_to_date < CURDATE();
-- ComStage ETF DJ STOXX 600 Telecommunications TR: not traded since 2020-09-24, and the derived security based on it.
INSERT IGNORE INTO sec_delete VALUES (1919), (3714);

DELETE FROM security_derived_link WHERE id_securitycurrency IN (SELECT id FROM sec_delete);
DELETE FROM security WHERE id_securitycurrency IN (SELECT id FROM sec_delete) AND id_link_securitycurrency IS NOT NULL;
DELETE FROM security WHERE id_securitycurrency IN (SELECT id FROM sec_delete);
DELETE FROM securitycurrency WHERE id_securitycurrency IN (SELECT id FROM sec_delete);

-- 5. Active periods ---------------------------------------------------------------------------------------------------
-- Calc 2 x Core Equities + Bond stays as an example of a derived security: all its bases are still updated.
UPDATE security SET active_to_date = '2036-01-01' WHERE id_securitycurrency = 3717;
-- An index does not mature. grafioschtrader_prod carries 2026-02-01, which stopped its updates.
UPDATE security SET active_to_date = '2099-12-31' WHERE id_securitycurrency = 3532;

COMMIT;

-- Checks: every expected value is 0.
SELECT
  (SELECT COUNT(*) FROM security WHERE active_to_date < CURDATE()) AS expired_securities,
  (SELECT COUNT(*) FROM securitycurrency sc WHERE sc.dtype = 'C'
     AND NOT EXISTS (SELECT 1 FROM currencypair c WHERE c.id_securitycurrency = sc.id_securitycurrency))
     AS orphan_currency_rows,
  (SELECT COUNT(*) FROM securitycurrency sc WHERE sc.dtype = 'S'
     AND NOT EXISTS (SELECT 1 FROM security s WHERE s.id_securitycurrency = sc.id_securitycurrency))
     AS orphan_security_rows,
  (SELECT COUNT(*) FROM securitycurrency
     WHERE id_connector_history LIKE '%eodhistoricaldata' OR id_connector_intra LIKE '%eodhistoricaldata')
   + (SELECT COUNT(*) FROM security
     WHERE id_connector_dividend LIKE '%eodhistoricaldata' OR id_connector_split LIKE '%eodhistoricaldata')
     AS eod_connectors;
