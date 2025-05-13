# Create background tasks for each security if the historical price data is read by the Warsaw Stock Exchange connector. 
INSERT INTO task_data_change (id_task, execution_priority, entity, id_entity, earliest_start_time, creation_time, exec_start_time, exec_end_time, old_value_varchar, old_value_number, progress_state, failed_message_code) 
SELECT 5, 20, "Security", s.id_securitycurrency, UTC_TIMESTAMP(), UTC_TIMESTAMP(), NULL, NULL, NULL, NULL, 0, NULL FROM historyquote h JOIN security s ON h.id_securitycurrency = s.id_securitycurrency JOIN securitycurrency sc ON s.id_securitycurrency = sc.id_securitycurrency JOIN assetclass a ON s.id_asset_class = a.id_asset_class WHERE WEEKDAY(h.date) >= 6 AND s.id_link_securitycurrency IS NULL AND a.spec_invest_instrument != 5 AND sc.id_connector_history = "gt.datafeed.warsawgpw" GROUP BY h.id_securitycurrency;

# Create background task. Create new trading calendar due to adjusted price data of the Warsaw Stock Exchange.
INSERT INTO task_data_change (id_task, execution_priority, entity, id_entity, earliest_start_time, creation_time, exec_start_time, exec_end_time, old_value_varchar, old_value_number, progress_state, failed_message_code) 
VALUES (12, 40, NULL, NULL, UTC_TIMESTAMP() + INTERVAL 3 MINUTE, UTC_TIMESTAMP(), NULL, NULL, NULL, NULL, 0, NULL);

# Some connectors have a repeat counter for dividends that is higher than 0, even though no dividend connector is set.
UPDATE security SET retry_dividend_load = 0 WHERE id_connector_dividend IS NULL AND retry_dividend_load > 0; 