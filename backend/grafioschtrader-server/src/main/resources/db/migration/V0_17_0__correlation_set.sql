DELETE FROM globalparameters WHERE property_name = 'gt.max.transaction';
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.max.transaction', '5000', NULL, NULL, NULL, '0');

DELETE FROM globalparameters WHERE property_name = 'gt.max.instrument.splits';
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.max.instrument.splits', '20', NULL, NULL, NULL, '0');

DELETE FROM globalparameters WHERE property_name = 'gt.max.instrument.historyquote.periods';
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.max.instrument.historyquote.periods', '20', NULL, NULL, NULL, '0');

DELETE FROM globalparameters WHERE property_name = 'gt.max.correlation.set';
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.max.correlation.set', '10', NULL, NULL, NULL, '0');

DELETE FROM globalparameters WHERE property_name = 'gt.max.correlation.instruments';
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.max.correlation.instruments', '20', NULL, NULL, NULL, '0');


ALTER TABLE `dividend` CHANGE `amount` `amount` DOUBLE(16,7) NULL; 
ALTER TABLE `dividend` DROP COLUMN IF EXISTS `amount_adjusted`;
ALTER TABLE `dividend` ADD `amount_adjusted` DOUBLE(16,7) NULL AFTER `amount`; 
UPDATE dividend d JOIN security s ON d.id_securitycurrency = s.id_securitycurrency SET d.amount_adjusted = d.amount WHERE s.id_connector_dividend = "gt.datafeed.yahoo";


ALTER TABLE `security` DROP COLUMN IF EXISTS `div_earliest_next_check`;
ALTER TABLE `security` ADD `div_earliest_next_check` TIMESTAMP NULL AFTER `dividend_currency`; 
UPDATE security s JOIN securitycurrency sc ON s.id_securitycurrency = sc.id_securitycurrency SET s.div_earliest_next_check = GREATEST(sc.creation_time, (SELECT MAX(d.ex_date) FROM dividend d WHERE s.id_securitycurrency = d.id_securitycurrency)) WHERE id_connector_dividend IS NOT NULL; 


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

DROP TABLE IF EXISTS `correlation_instrument`;
DROP TABLE IF EXISTS `correlation_set`;

CREATE TABLE `correlation_set` (
  `id_correlation_set` int(11) NOT NULL,
  `id_tenant` int(11) NOT NULL,
  `name` varchar(25) NOT NULL,
  `note` varchar(1000) DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `sampling_period` tinyint(1) DEFAULT 0,
  `rolling` tinyint(4) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `correlation_set`
  ADD PRIMARY KEY (`id_correlation_set`),
  ADD UNIQUE KEY `Unique_idTenant_name` (`id_tenant`,`name`);

ALTER TABLE `correlation_set`
  MODIFY `id_correlation_set` int(11) NOT NULL AUTO_INCREMENT;

CREATE TABLE `correlation_instrument` (
  `id_correlation_set` int(11) NOT NULL,
  `id_securitycurrency` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `correlation_instrument`
  ADD PRIMARY KEY (`id_correlation_set`,`id_securitycurrency`),
  ADD KEY `FK_CorrInstrument_SecurityCurrency` (`id_securitycurrency`);

ALTER TABLE `correlation_instrument`
  ADD CONSTRAINT `FK_CorrInstrument_CorrSet` FOREIGN KEY (`id_correlation_set`) REFERENCES `correlation_set` (`id_correlation_set`),
  ADD CONSTRAINT `FK_CorrInstrument_SecurityCurrency` FOREIGN KEY (`id_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`);

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;