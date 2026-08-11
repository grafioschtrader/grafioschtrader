-- CryptoCompare's free subscription is replaced by CoinMarketCap for cryptocurrency rates.
-- CoinMarketCap identifies both sides of a currency pair by numeric IDs. Keep the supported
-- cryptocurrency list aligned with GlobalConstants.CRYPTO_CURRENCY_SUPPORTED.

DROP TEMPORARY TABLE IF EXISTS tmp_coinmarketcap_currency;
CREATE TEMPORARY TABLE tmp_coinmarketcap_currency (
  symbol CHAR(3) NOT NULL,
  coinmarketcap_id INT UNSIGNED NOT NULL,
  is_cryptocurrency TINYINT(1) NOT NULL,
  PRIMARY KEY (symbol)
);

INSERT INTO tmp_coinmarketcap_currency (symbol, coinmarketcap_id, is_cryptocurrency) VALUES
  ('BTC', 1, 1),
  ('BNB', 1839, 1),
  ('ETH', 1027, 1),
  ('ETC', 1321, 1),
  ('LTC', 2, 1),
  ('XRP', 52, 1),
  ('USD', 2781, 0),
  ('CHF', 2785, 0),
  ('DKK', 2789, 0),
  ('EUR', 2790, 0),
  ('COP', 2820, 0);

DROP TEMPORARY TABLE IF EXISTS tmp_coinmarketcap_pair;
CREATE TEMPORARY TABLE tmp_coinmarketcap_pair (
  id_securitycurrency INT NOT NULL,
  url_extend VARCHAR(254) NOT NULL,
  PRIMARY KEY (id_securitycurrency)
);

-- CoinMarketCap's chart endpoint requires a cryptocurrency as its primary instrument. For
-- inverse pairs such as CHF/BTC, store BTC first; the connector performs the inversion.
INSERT INTO tmp_coinmarketcap_pair (id_securitycurrency, url_extend)
SELECT cp.id_securitycurrency,
       CASE
         WHEN from_map.is_cryptocurrency = 1 THEN
           CONCAT(from_map.symbol, '=', from_map.coinmarketcap_id, ',',
                  to_map.symbol, '=', to_map.coinmarketcap_id)
         ELSE
           CONCAT(to_map.symbol, '=', to_map.coinmarketcap_id, ',',
                  from_map.symbol, '=', from_map.coinmarketcap_id)
       END
FROM currencypair cp
JOIN tmp_coinmarketcap_currency from_map ON from_map.symbol = cp.from_currency
JOIN tmp_coinmarketcap_currency to_map ON to_map.symbol = cp.to_currency
WHERE from_map.is_cryptocurrency = 1 OR to_map.is_cryptocurrency = 1;

UPDATE securitycurrency sc
JOIN tmp_coinmarketcap_pair cmc_pair
  ON cmc_pair.id_securitycurrency = sc.id_securitycurrency
SET sc.id_connector_history = 'gt.datafeed.coinmarketcap',
    sc.url_history_extend = cmc_pair.url_extend,
    sc.retry_history_load = 0
WHERE sc.id_connector_history = 'gt.datafeed.cryptocompare'
  AND EXISTS (
    SELECT 1
    FROM connector_apikey ca
    WHERE ca.id_provider = 'cryptocompare'
      AND ca.subscription_type = 51
  );

UPDATE securitycurrency sc
JOIN tmp_coinmarketcap_pair cmc_pair
  ON cmc_pair.id_securitycurrency = sc.id_securitycurrency
SET sc.id_connector_intra = 'gt.datafeed.coinmarketcap',
    sc.url_intra_extend = cmc_pair.url_extend,
    sc.retry_intra_load = 0
WHERE sc.id_connector_intra = 'gt.datafeed.cryptocompare'
  AND EXISTS (
    SELECT 1
    FROM connector_apikey ca
    WHERE ca.id_provider = 'cryptocompare'
      AND ca.subscription_type = 51
  );

UPDATE globalparameters
SET property_string = 'gt.datafeed.coinmarketcap'
WHERE property_name IN (
    'gt.cryptocurrency.history.connector',
    'gt.cryptocurrency.intra.connector'
  )
  AND property_string = 'gt.datafeed.cryptocompare'
  AND EXISTS (
    SELECT 1
    FROM connector_apikey ca
    WHERE ca.id_provider = 'cryptocompare'
      AND ca.subscription_type = 51
  );

DROP TEMPORARY TABLE IF EXISTS tmp_coinmarketcap_pair;
DROP TEMPORARY TABLE IF EXISTS tmp_coinmarketcap_currency;
