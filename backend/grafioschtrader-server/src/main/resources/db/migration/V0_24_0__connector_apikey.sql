## Dividend need some more more decimal places
DELETE FROM dividend;
ALTER TABLE `dividend` CHANGE `amount` `amount` DOUBLE(16,8) NOT NULL; 
ALTER TABLE `dividend` CHANGE `amount_adjusted` `amount_adjusted` DOUBLE(16,10) NOT NULL; 

DROP TABLE IF EXISTS `connector_apikey`;

CREATE TABLE `connector_apikey` (
  `id_provider` varchar(32) NOT NULL,
  `api_key` varchar(255) NOT NULL,
  `subscription_type` smallint(4) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `connector_apikey`
  ADD PRIMARY KEY (`id_provider`);
