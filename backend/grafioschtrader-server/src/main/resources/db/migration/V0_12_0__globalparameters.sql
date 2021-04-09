ALTER TABLE `securitycurrency` DROP COLUMN IF EXISTS `last_history_try`;
ALTER TABLE `securitycurrency` ADD `last_history_try` TIMESTAMP NULL AFTER `retry_history_load`; 
ALTER TABLE `securitycurrency` DROP COLUMN IF EXISTS `next_history_planned`;
ALTER TABLE `securitycurrency` ADD `next_history_planned` TIMESTAMP NULL AFTER `retry_history_load`; 

ALTER TABLE `globalparameters` DROP COLUMN IF EXISTS `changed_by_system`;
ALTER TABLE `globalparameters` ADD `changed_by_system` TINYINT(1) NULL DEFAULT '0' AFTER `property_blob`; 

UPDATE globalparameters SET changed_by_system = 1 WHERE property_name = "gt.historyquote.quality.update.date" OR property_name="gt.securitysplit.append.date";

UPDATE securitycurrency SET id_connector_history = 'gt.datafeed.fxubc' WHERE id_connector_history = 'gt.datafeed.exchangeratesapi';
UPDATE globalparameters SET property_string = 'gt.datafeed.fxubc' WHERE property_name ='gt.currency.history.connector';