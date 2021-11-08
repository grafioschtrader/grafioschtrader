# BUG https://jira.mariadb.org/browse/MDEV-25672 - FLUSH TABLES cannot be used because of rights
# Wating for mariadb version 10.3.30 
ALTER TABLE transaction DROP CONSTRAINT `s_quotation`;
ALTER TABLE transaction ADD CONSTRAINT `s_quotation` CHECK (`quotation` is not null and (`quotation` > 0 or `quotation` <> 0 and `transaction_type` BETWEEN 6 AND 7 ) and `id_securitycurrency` is not null or `quotation` is null and `id_securitycurrency` is null);

UPDATE correlation_set SET sampling_period = 1;
UPDATE correlation_set SET rolling = 12;
ALTER TABLE `correlation_set` CHANGE IF EXISTS `start_date` `date_from` DATE NULL DEFAULT NULL; 
ALTER TABLE `correlation_set` DROP COLUMN IF EXISTS `date_to`;
ALTER TABLE `correlation_set` ADD `date_to` DATE NULL DEFAULT NULL AFTER `date_from`; 
ALTER TABLE `correlation_set` CHANGE `sampling_period` `sampling_period` TINYINT(1) NOT NULL DEFAULT '1'; 
ALTER TABLE `correlation_set` CHANGE `rolling` `rolling` TINYINT(4) NULL DEFAULT '12';  
ALTER TABLE `correlation_set` DROP COLUMN IF EXISTS `adjust_currency`;
ALTER TABLE `correlation_set` ADD `adjust_currency` TINYINT NOT NULL DEFAULT '0' AFTER `rolling`; 