ALTER TABLE `user` CHANGE `email` `email` VARCHAR(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL; 

ALTER TABLE `task_data_change` DROP COLUMN IF EXISTS `failed_stack_trace`;
ALTER TABLE `task_data_change` ADD `failed_stack_trace` VARCHAR(4096) NULL AFTER `failed_message_code`; 

ALTER TABLE `user` DROP COLUMN IF EXISTS `ui_show_my_property`;
ALTER TABLE `user` ADD `ui_show_my_property` TINYINT(1) NOT NULL DEFAULT '1' AFTER `enabled`; 

DELETE FROM globalparameters WHERE property_name = 'gt.currency.precision';
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.currency.precision', NULL, 'BTC=8,ETH=7,JPY=0', NULL, NULL, '0');
UPDATE `globalparameters` SET `property_string` = 'gt.datafeed.exchangeratehosts' WHERE `globalparameters`.`property_name` = 'gt.currency.history.connector'; 