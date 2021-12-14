# All securities with divvydiary need to be read. The field amount_adjusted must be set
UPDATE security SET div_earliest_next_check=current_date() WHERE id_connector_dividend = "gt.datafeed.divvydiary";
DELETE d FROM dividend d JOIN security s ON d.id_securitycurrency = s.id_securitycurrency WHERE s.id_connector_dividend = "gt.datafeed.divvydiary";

ALTER TABLE `correlation_instrument` DROP FOREIGN KEY `FK_CorrInstrument_CorrSet`; 
ALTER TABLE `correlation_instrument` ADD CONSTRAINT `FK_CorrInstrument_CorrSet` FOREIGN KEY (`id_correlation_set`) REFERENCES `correlation_set`(`id_correlation_set`) ON DELETE CASCADE ON UPDATE RESTRICT; 

DROP TABLE IF EXISTS `mail_setting_forward`;

CREATE TABLE `mail_setting_forward` (
  `id_mail_setting_forward` int(11) NOT NULL,
  `id_user` int(11) NOT NULL,
  `message_com_type` tinyint(4) NOT NULL,
  PRIMARY KEY (`id_mail_setting_forward`),
  CONSTRAINT `FK_MailSettingForward_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
