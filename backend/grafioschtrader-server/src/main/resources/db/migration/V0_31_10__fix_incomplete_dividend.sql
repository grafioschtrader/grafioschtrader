# Forcing the import of dividends through connectors
UPDATE security SET div_earliest_next_check = DATE(NOW()-INTERVAL 400 DAY) WHERE div_earliest_next_check IS NOT NULL AND id_connector_dividend IS NOT NULL;
DELETE FROM dividend WHERE ex_date >= DATE(NOW()-INTERVAL 400 DAY);

# Creating the job for reading the dividends through the connectors
INSERT INTO `task_data_change` (`id_task_data_change`, `id_task`, `execution_priority`, `entity`, `id_entity`, `earliest_start_time`, `creation_time`, `exec_start_time`, `exec_end_time`, `old_value_varchar`, `old_value_number`, `progress_state`, `failed_message_code`) VALUES (NULL, '13', '5', NULL, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), NULL, NULL, NULL, NULL, '0', NULL);

DELETE FROM globalparameters WHERE property_name = 'gt.securitydividend.append.date';
INSERT INTO globalparameters (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.securitydividend.append.date', NULL, NULL, CURDATE() - INTERVAL 1 DAY, NULL, '1');
 