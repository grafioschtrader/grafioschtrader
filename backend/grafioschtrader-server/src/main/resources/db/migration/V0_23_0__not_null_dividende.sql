DELETE d FROM dividend d JOIN security s ON d.id_securitycurrency = s.id_securitycurrency WHERE s.id_connector_dividend = "gt.datafeed.yahoo";
ALTER TABLE `dividend` CHANGE `amount` `amount` DOUBLE(16,7) NOT NULL; 
ALTER TABLE `dividend` CHANGE `amount_adjusted` `amount_adjusted` DOUBLE(16,7) NOT NULL; 
