

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Datenbank: `grafioschtrader`
--

-- --------------------------------------------------------

DELETE FROM globalparameters WHERE property_name = 'gt.currency.precision';
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.currency.precision', NULL, 'BTC=8,ETH=7,JPY=0,KWD=3', NULL, NULL, '0');


ALTER TABLE mail_send_recv DROP COLUMN IF EXISTS id_entity;
ALTER TABLE mail_send_recv DROP COLUMN IF EXISTS message_com_type;
ALTER TABLE mail_send_recv CHANGE message message VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL; 



--
-- Tabellenstruktur für Tabelle `mail_entity`
--

DROP TABLE IF EXISTS `mail_entity`;
CREATE TABLE `mail_entity` (
  `id_mail_entity` int(11) NOT NULL,
  `id_mail_send_recv` int(11) DEFAULT NULL,
  `message_com_type` tinyint(4) NOT NULL,
  `id_entity` int(11) NOT NULL,
  `mark_date` date NOT NULL,
  `creation_date` date NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `mail_setting_forward`
--

DROP TABLE IF EXISTS `mail_setting_forward`;
CREATE TABLE `mail_setting_forward` (
  `id_mail_setting_forward` int(11) NOT NULL,
  `id_user` int(11) NOT NULL,
  `message_com_type` tinyint(4) NOT NULL,
  `message_target_type` tinyint(4) NOT NULL DEFAULT 0,
  `id_user_redirect` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Indizes der exportierten Tabellen
--

--
-- Indizes für die Tabelle `mail_entity`
--
ALTER TABLE `mail_entity`
  ADD PRIMARY KEY (`id_mail_entity`);

--
-- Indizes für die Tabelle `mail_setting_forward`
--
ALTER TABLE `mail_setting_forward`
  ADD PRIMARY KEY (`id_mail_setting_forward`),
  ADD KEY `FK_MailSettingForward_User` (`id_user`),
  ADD KEY `FK_MailSettingForwardRedirect_User` (`id_user_redirect`);

--
-- AUTO_INCREMENT für exportierte Tabellen
--

--
-- AUTO_INCREMENT für Tabelle `mail_entity`
--
ALTER TABLE `mail_entity`
  MODIFY `id_mail_entity` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT für Tabelle `mail_setting_forward`
--
ALTER TABLE `mail_setting_forward`
  MODIFY `id_mail_setting_forward` int(11) NOT NULL AUTO_INCREMENT;

--
-- Constraints der exportierten Tabellen
--

--
-- Constraints der Tabelle `mail_setting_forward`
--
ALTER TABLE `mail_setting_forward`
  ADD CONSTRAINT `FK_MailSettingForwardRedirect_User` FOREIGN KEY (`id_user_redirect`) REFERENCES `user` (`id_user`),
  ADD CONSTRAINT `FK_MailSettingForward_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;