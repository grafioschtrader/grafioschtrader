# Perhaps the user should be redirected to an appropriate page after login. Save this user-selectable setting in the user table.
# ? 

UPDATE securitycurrency SET url_history_extend = SUBSTRING_INDEX(SUBSTRING_INDEX(url_history_extend, ',', 2), ',', -1) 
WHERE id_connector_history = "gt.datafeed.investing" AND url_history_extend LIKE '%,%,%' ; 


SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Tabellenstruktur für Tabelle `gt_net_message`
--
DROP TABLE IF EXISTS `gt_net_lastprice_detail_log`;
DROP TABLE IF EXISTS `gt_net_lastprice_security`;
DROP TABLE IF EXISTS `gt_net_lastprice_currencypair`;
DROP TABLE IF EXISTS `gt_net_lastprice_log`;
DROP TABLE IF EXISTS `gt_net_lastprice`;
DROP TABLE IF EXISTS `gt_net_message`;
DROP TABLE IF EXISTS `gt_net_exchange`;
DROP TABLE IF EXISTS `gt_net`;

CREATE TABLE `gt_net_message` (
  `id_gt_net_message` int(11) NOT NULL,
  `id_gt_net` int(11) NOT NULL,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `send_recv` tinyint(1) NOT NULL,
  `reply_to` int(11) DEFAULT NULL,
  `message_code` smallint(6) DEFAULT NULL,
  `message` varchar(1024) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--
--
-- Tabellenstruktur für Tabelle `gt_net`
--


CREATE TABLE `gt_net` (
  `id_gt_net` int(11) NOT NULL,
  `domain_remote_name` varchar(128) NOT NULL,
  `token_this` varchar(32) DEFAULT NULL,
  `token_remote` varchar(32) DEFAULT NULL,
  `spread_capability` tinyint(1) NOT NULL DEFAULT 0,
  `allow_give_away` tinyint(1) NOT NULL DEFAULT 0,
  `accept_request` tinyint(1) NOT NULL DEFAULT 0,
  `daily_req_limit` int(11) DEFAULT NULL,
  `daily_req_limit_count` int(11) DEFAULT NULL,
  `daily_req_limit_remote` int(11) DEFAULT NULL,
  `daily_req_limit_remote_count` int(11) DEFAULT NULL,
  `lastprice_supplier_capability` tinyint(1) NOT NULL DEFAULT 0,
  `lastprice_consumer_usage` tinyint(1) NOT NULL DEFAULT 0,
  `lastprice_use_detail_log` tinyint(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_exchange`
--


CREATE TABLE `gt_net_exchange` (
  `id_gt_net_exchange` int(11) NOT NULL,
  `id_gt_net` int(11) NOT NULL,
  `in_out` tinyint(1) NOT NULL,
  `entity` varchar(40) NOT NULL,
  `id_entity` int(11) NOT NULL,
  `id_entity_remote` int(11) NOT NULL,
  `indirection_count` smallint(6) NOT NULL DEFAULT 1,
  `send_msg_code` tinyint(3) DEFAULT NULL,
  `send_msg_timestamp` timestamp NULL DEFAULT NULL,
  `recv_msg_code` tinyint(3) DEFAULT NULL,
  `recv_msg_timestamp` timestamp NULL DEFAULT NULL,
  `request_entity_timestamp` timestamp NULL DEFAULT NULL,
  `give_entity_timestamp` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_lastprice`
--

CREATE TABLE `gt_net_lastprice` (
  `id_gt_net_lastprice` int(11) NOT NULL,
  `id_gt_net` int(11) NOT NULL,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `open` double(16,8) DEFAULT NULL,
  `low` double(16,8) DEFAULT NULL,
  `high` double(16,8) DEFAULT NULL,
  `last` double(16,8) NOT NULL,
  `volume` bigint(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_lastprice_currencypair`
--

CREATE TABLE `gt_net_lastprice_currencypair` (
  `id_gt_net_lastprice` int(11) NOT NULL,
  `dtype` varchar(1) CHARACTER SET utf8 NOT NULL,
  `from_currency` char(3) NOT NULL,
  `to_currency` char(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_lastprice_detail_log`
--

CREATE TABLE `gt_net_lastprice_detail_log` (
  `id_gt_net_lastprice_detail_log` int(11) NOT NULL,
  `id_gt_net_lastprice_log` int(11) NOT NULL,
  `id_gt_net_lastprice` int(11) NOT NULL,
  `last` double(16,8) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_lastprice_log`
--

CREATE TABLE `gt_net_lastprice_log` (
  `id_gt_net_lastprice_log` int(11) NOT NULL,
  `id_gt_net` int(11) NOT NULL,
  `log_as_supplier` tinyint(1) NOT NULL,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `lastprice_payload` int(11) NOT NULL DEFAULT 0,
  `read_count` int(11) NOT NULL DEFAULT 0,
  `write_count` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_lastprice_security`
--

CREATE TABLE `gt_net_lastprice_security` (
  `id_gt_net_lastprice` int(11) NOT NULL,
  `dtype` varchar(1) DEFAULT NULL,
  `isin` varchar(12) NOT NULL,
  `currency` char(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------


-- Indizes der exportierten Tabellen
--

--
-- Indizes für die Tabelle `gt_net`
--
ALTER TABLE `gt_net`
  ADD PRIMARY KEY (`id_gt_net`),
  ADD UNIQUE KEY `domainRemoteName` (`domain_remote_name`);

--
-- Indizes für die Tabelle `gt_net_exchange`
--
ALTER TABLE `gt_net_exchange`
  ADD PRIMARY KEY (`id_gt_net_exchange`),
  ADD KEY `FK_GTNet_GTNetExchange` (`id_gt_net`);

--
-- Indizes für die Tabelle `gt_net_lastprice`
--
ALTER TABLE `gt_net_lastprice`
  ADD PRIMARY KEY (`id_gt_net_lastprice`),
  ADD KEY `FK_GtNetLastprice_GtNet` (`id_gt_net`);

--
-- Indizes für die Tabelle `gt_net_lastprice_currencypair`
--
ALTER TABLE `gt_net_lastprice_currencypair`
  ADD PRIMARY KEY (`id_gt_net_lastprice`);

--
-- Indizes für die Tabelle `gt_net_lastprice_detail_log`
--
ALTER TABLE `gt_net_lastprice_detail_log`
  ADD PRIMARY KEY (`id_gt_net_lastprice_detail_log`),
  ADD KEY `FK_GtNetLastpriceLog_GtNetLastpriceDetailLog` (`id_gt_net_lastprice_log`),
  ADD KEY `FK_GtNetLastpriceDetailLog_GtNetLastprice` (`id_gt_net_lastprice`);

--
-- Indizes für die Tabelle `gt_net_lastprice_log`
--
ALTER TABLE `gt_net_lastprice_log`
  ADD PRIMARY KEY (`id_gt_net_lastprice_log`),
  ADD KEY `FK_GtNetLastpriceLog_GtNet` (`id_gt_net`);

--
-- Indizes für die Tabelle `gt_net_lastprice_security`
--
ALTER TABLE `gt_net_lastprice_security`
  ADD PRIMARY KEY (`id_gt_net_lastprice`);

--
-- Indizes für die Tabelle `gt_net_message`
--
ALTER TABLE `gt_net_message`
  ADD PRIMARY KEY (`id_gt_net_message`),
  ADD KEY `FK_GtNetMessage_GtNet` (`id_gt_net`),
  ADD KEY `FK_GtNetMessage_GtNetMessage` (`reply_to`);

--
-- AUTO_INCREMENT für exportierte Tabellen
--

--
-- AUTO_INCREMENT für Tabelle `gt_net`
--
ALTER TABLE `gt_net`
  MODIFY `id_gt_net` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT für Tabelle `gt_net_exchange`
--
ALTER TABLE `gt_net_exchange`
  MODIFY `id_gt_net_exchange` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT für Tabelle `gt_net_lastprice`
--
ALTER TABLE `gt_net_lastprice`
  MODIFY `id_gt_net_lastprice` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT für Tabelle `gt_net_lastprice_detail_log`
--
ALTER TABLE `gt_net_lastprice_detail_log`
  MODIFY `id_gt_net_lastprice_detail_log` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT für Tabelle `gt_net_lastprice_log`
--
ALTER TABLE `gt_net_lastprice_log`
  MODIFY `id_gt_net_lastprice_log` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT für Tabelle `gt_net_message`
--
ALTER TABLE `gt_net_message`
  MODIFY `id_gt_net_message` int(11) NOT NULL AUTO_INCREMENT;

--
-- Constraints der exportierten Tabellen
--

--
-- Constraints der Tabelle `gt_net_exchange`
--
ALTER TABLE `gt_net_exchange`
  ADD CONSTRAINT `FK_GTNet_GTNetExchange` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`);

--
-- Constraints der Tabelle `gt_net_lastprice`
--
ALTER TABLE `gt_net_lastprice`
  ADD CONSTRAINT `FK_GtNetLastprice_GtNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`);

--
-- Constraints der Tabelle `gt_net_lastprice_currencypair`
--
ALTER TABLE `gt_net_lastprice_currencypair`
  ADD CONSTRAINT `FK_GtNetLastpriceCurrency_GtNetLasprice` FOREIGN KEY (`id_gt_net_lastprice`) REFERENCES `gt_net_lastprice` (`id_gt_net_lastprice`);

--
-- Constraints der Tabelle `gt_net_lastprice_detail_log`
--
ALTER TABLE `gt_net_lastprice_detail_log`
  ADD CONSTRAINT `FK_GtNetLastpriceDetailLog_GtNetLastprice` FOREIGN KEY (`id_gt_net_lastprice`) REFERENCES `gt_net_lastprice` (`id_gt_net_lastprice`),
  ADD CONSTRAINT `FK_GtNetLastpriceLog_GtNetLastpriceDetailLog` FOREIGN KEY (`id_gt_net_lastprice_log`) REFERENCES `gt_net_lastprice_log` (`id_gt_net_lastprice_log`);

--
-- Constraints der Tabelle `gt_net_lastprice_log`
--
ALTER TABLE `gt_net_lastprice_log`
  ADD CONSTRAINT `FK_GtNetLastpriceLog_GtNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`);

--
-- Constraints der Tabelle `gt_net_lastprice_security`
--
ALTER TABLE `gt_net_lastprice_security`
  ADD CONSTRAINT `FK_GtNetLastpriceSecurity_GtNetLastprice` FOREIGN KEY (`id_gt_net_lastprice`) REFERENCES `gt_net_lastprice` (`id_gt_net_lastprice`);

--
-- Constraints der Tabelle `gt_net_message`
--
ALTER TABLE `gt_net_message`
  ADD CONSTRAINT `FK_GtNetMessage_GtNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`),
  ADD CONSTRAINT `FK_GtNetMessage_GtNetMessage` FOREIGN KEY (`reply_to`) REFERENCES `gt_net_message` (`id_gt_net_message`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;