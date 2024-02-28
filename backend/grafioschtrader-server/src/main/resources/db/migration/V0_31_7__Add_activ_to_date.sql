ALTER TABLE securitycashaccount DROP COLUMN IF EXISTS active_to_date;
ALTER TABLE securitycashaccount ADD active_to_date DATE NULL AFTER id_tenant;

# Reset retry history load of fxubc connector
UPDATE securitycurrency set retry_history_load = 0 WHERE id_connector_history = 'gt.datafeed.fxubc';


# Create job for tracking historical rate data, this will track the currency pairs.
INSERT INTO `task_data_change` (`id_task_data_change`, `id_task`, `execution_priority`, `entity`, `id_entity`, `earliest_start_time`, `creation_time`, `exec_start_time`, `exec_end_time`, `old_value_varchar`, `old_value_number`, `progress_state`, `failed_message_code`) VALUES (NULL, '0', '20', NULL, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), NULL, NULL, NULL, NULL, '0', NULL);  