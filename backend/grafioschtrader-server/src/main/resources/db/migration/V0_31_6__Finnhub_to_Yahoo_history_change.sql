# Historical price data has not been included in Finnhub's free plan since 2023-11-30.
# Solution change from Finnhub to Yahoo
UPDATE securitycurrency SET id_connector_history = "gt.datafeed.yahoo", retry_history_load = 0  WHERE id_connector_history = "gt.datafeed.finnhub" AND EXISTS (SELECT * FROM connector_apikey WHERE id_provider = 'finnhub' AND subscription_type = 31);

# The ISIN is transferred directly to the Warsaw Stock Exchange connector. URL extension input no longer possible.
UPDATE securitycurrency SET url_history_extend = NULL WHERE id_connector_history = 'gt.datafeed.warsawgpw';
UPDATE securitycurrency SET url_intra_extend = NULL WHERE id_connector_intra = 'gt.datafeed.warsawgpw';