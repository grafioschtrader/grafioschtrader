# Delete some zombies in task_data_change?
DELETE FROM task_data_change WHERE progress_state = 4;

# Since holding tabel was fixed, a rebuild is need for all tenants
INSERT INTO `task_data_change` (`id_task_data_change`, `id_task`, `execution_priority`, `entity`, `id_entity`, `earliest_start_time`, `creation_time`, `exec_start_time`, `exec_end_time`, `old_value_varchar`, `old_value_number`, `progress_state`, `failed_message_code`) VALUES (NULL, '9', '20', NULL, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), NULL, NULL, NULL, NULL, '0', NULL);

ALTER TABLE stockexchange DROP IF EXISTS last_direct_price_update;
ALTER TABLE `stockexchange` ADD `last_direct_price_update` TIMESTAMP NOT NULL DEFAULT TIMESTAMPADD(HOUR, -72, NOW()) AFTER `id_index_upd_calendar`;

ALTER TABLE securitycurrency DROP IF EXISTS next_history_planned;
ALTER TABLE securitycurrency DROP IF EXISTS last_history_try; 

ALTER TABLE gt_network DROP IF EXISTS password;
ALTER TABLE `gt_network` ADD `password` VARCHAR(20) NULL AFTER `domain_remote_name`; 

ALTER TABLE gt_network_exchange DROP IF EXISTS indirection_count;
ALTER TABLE `gt_network_exchange` ADD `indirection_count` SMALLINT NOT NULL DEFAULT '1' AFTER `id_entity_remote`;  

DELETE FROM globalparameters WHERE property_name = 'gt.update.price.by.exchange';
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.update.price.by.exchange', 0, NULL, NULL, NULL, '0');
