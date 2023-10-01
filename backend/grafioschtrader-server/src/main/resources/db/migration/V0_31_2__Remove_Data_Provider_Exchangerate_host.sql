# Forgot to delete `gt_network_exchange` and `gt_network` when naming convention was changed
DROP TABLE IF EXISTS `gt_network_exchange`;
DROP TABLE IF EXISTS `gt_network`;

# exchangerate.host is no longer free, It is deleted from GT.
# -----------------------------------------------------------
# If data provider "EOD Historical Data" is active, the previous "exchangerate.host" will be distributed to "fxubc", "yahoo" and "EOD Historical Data".
UPDATE securitycurrency set retry_history_load = 0, id_connector_history = CONCAT('gt.datafeed.', CASE WHEN RAND() < 0.5 THEN 'yahoo' ELSE 'fxubc' END) WHERE id_connector_history = 'gt.datafeed.exchangeratehosts' AND NOT EXISTS (SELECT * FROM connector_apikey WHERE id_provider = 'eodhistoricaldata');
# Otherwise, they will be distributed only to "fxubc", and "yahoo".
UPDATE securitycurrency set retry_history_load = 0, id_connector_history = CONCAT('gt.datafeed.', CASE WHEN RAND() < 0.25 THEN 'yahoo' WHEN RAND() < 0.5 THEN 'fxubc' ELSE 'eodhistoricaldata' END) WHERE id_connector_history = 'gt.datafeed.exchangeratehosts' AND EXISTS (SELECT * FROM connector_apikey WHERE id_provider = 'eodhistoricaldata');

# Default connector for currency pair is reset. If "EOD Historical Data" is active, it will be this.
UPDATE globalparameters SET property_string = 'gt.datafeed.eodhistoricaldata' WHERE property_name = 'gt.currency.history.connector' AND property_string = 'gt.datafeed.exchangeratehosts' AND EXISTS (SELECT * FROM connector_apikey WHERE id_provider = 'eodhistoricaldata');
# Otherwise Yahoo.
UPDATE globalparameters SET property_string = 'gt.datafeed.yahoo' WHERE property_name = 'gt.currency.history.connector' AND property_string = 'gt.datafeed.exchangeratehosts' AND NOT EXISTS (SELECT * FROM connector_apikey WHERE id_provider = 'eodhistoricaldata');

# Create job for tracking historical rate data, this will track the currency pairs.
INSERT INTO `task_data_change` (`id_task_data_change`, `id_task`, `execution_priority`, `entity`, `id_entity`, `earliest_start_time`, `creation_time`, `exec_start_time`, `exec_end_time`, `old_value_varchar`, `old_value_number`, `progress_state`, `failed_message_code`) VALUES (NULL, '0', '20', NULL, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), NULL, NULL, NULL, NULL, '0', NULL);  