-- For all securities with the historical price data connector “yahoo” that have historical price data on a Saturday or Sunday, create the background task that re-inserts the historical price data. 
-- This may have been caused by a time zone problem.
INSERT INTO task_data_change (id_task, execution_priority, entity, id_entity, earliest_start_time, creation_time, exec_start_time, exec_end_time, 
old_value_varchar, old_value_number, progress_state, failed_message_code) 
SELECT 5, 20, "Security", s.id_securitycurrency, UTC_TIMESTAMP(), UTC_TIMESTAMP(), NULL, NULL, NULL, NULL, 0, NULL FROM historyquote h 
JOIN security s ON h.id_securitycurrency = s.id_securitycurrency JOIN securitycurrency sc ON s.id_securitycurrency = sc.id_securitycurrency 
JOIN assetclass a ON s.id_asset_class = a.id_asset_class WHERE WEEKDAY(h.date) >= 5 AND s.id_link_securitycurrency IS NULL 
AND a.spec_invest_instrument != 5 AND sc.id_connector_history = "gt.datafeed.yahoo" GROUP BY h.id_securitycurrency; 

-- The setting “URL extension intraday data” has been changed. The suffix “aktien/” “etf/” etc. is no longer necessary.
UPDATE securitycurrency SET url_intra_extend = REGEXP_REPLACE(url_intra_extend, '^(etf|aktien|fonds|obligationen|derivate|index)/', '') WHERE id_connector_intra = 'gt.datafeed.finanzench'; 