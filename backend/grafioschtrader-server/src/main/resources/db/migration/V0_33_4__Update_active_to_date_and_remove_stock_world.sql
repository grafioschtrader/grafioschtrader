# It is possible that the "active until" date is set too early for some securities.
UPDATE security s JOIN assetclass a ON s.id_asset_class = a.id_asset_class SET s.active_to_date= "2036-01-01" WHERE (s.active_to_date = "2025-01-01" OR s.active_to_date = "2025-02-01") AND NOT (a.category_type IN (1,6) AND a.spec_invest_instrument = 0);

# Stock-World has not worked since 2024-12-28. Therefore remove connector.
UPDATE securitycurrency SET id_connector_history = NULL, url_history_extend = NULL WHERE id_connector_history = "gt.datafeed.stockworld";  