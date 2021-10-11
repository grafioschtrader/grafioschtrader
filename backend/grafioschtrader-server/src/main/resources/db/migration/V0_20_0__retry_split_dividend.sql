# This index has the wrong currency
UPDATE security SET currency = "DKK" WHERE ticker_symbol = "OMXC20";

ALTER TABLE `security` CHANGE `retry_dividend_load` `retry_dividend_load` SMALLINT(6) NOT NULL DEFAULT '0'; 
ALTER TABLE `security` CHANGE `retry_split_load` `retry_split_load` SMALLINT(6) NOT NULL DEFAULT '0';

DELETE FROM globalparameters WHERE property_name = 'gt.dividend.retry';
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.dividend.retry', '2', NULL, NULL, NULL, '0');

DELETE FROM globalparameters WHERE property_name = 'gt.split.retry';
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.split.retry', '2', NULL, NULL, NULL, '0');