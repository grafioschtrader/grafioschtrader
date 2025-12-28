-- phpMyAdmin SQL Dump
-- version 5.2.0
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Erstellungszeit: 27. Dez 2025 um 22:35
-- Server-Version: 10.4.27-MariaDB
-- PHP-Version: 8.1.12

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

--
-- Tabellenstruktur für Tabelle `gt_net`
--
DROP TABLE IF EXISTS  gt_net_config_entity;
DROP TABLE IF EXISTS  gt_net_entity;
DROP TABLE IF EXISTS  gt_net_config;
DROP TABLE IF EXISTS `gt_net_lastprice_currencypair`;
DROP TABLE IF EXISTS `gt_net_lastprice_security`;
DROP TABLE IF EXISTS `gt_net_lastprice_detail_log`;
DROP TABLE IF EXISTS `gt_net_message_param`;
DROP TABLE IF EXISTS `gt_net_lastprice`;
DROP TABLE IF EXISTS `gt_net_message_answer`;
DROP TABLE IF EXISTS `gt_net_message_attempt`;
DROP TABLE IF EXISTS `gt_net_message`;
DROP TABLE IF EXISTS `gt_net_supplier_detail`;
DROP TABLE IF EXISTS `gt_net_supplier`;
DROP TABLE IF EXISTS `gt_net_exchange`;
DROP TABLE IF EXISTS `gt_net_lastprice_log`;
-- Hugo
ALTER TABLE mail_send_recv DROP FOREIGN KEY FK_MailSendRecv_GtNet;
ALTER TABLE mail_send_recv DROP INDEX FK_MailInOut_GtNet;
DROP TABLE IF EXISTS  gt_net;



CREATE TABLE `gt_net` (
  `id_gt_net` int(11) NOT NULL,
  `domain_remote_name` varchar(128) NOT NULL,
  `time_zone` varchar(50) NOT NULL,
  `spread_capability` tinyint(1) NOT NULL DEFAULT 0,
  `daily_req_limit` int(11) DEFAULT NULL,
  `daily_req_limit_remote` int(11) DEFAULT NULL,
  `allow_server_creation` tinyint(1) NOT NULL DEFAULT 0,
  `server_busy` tinyint(1) NOT NULL DEFAULT 0,
  `server_online` tinyint(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_config`
--

CREATE TABLE `gt_net_config` (
  `id_gt_net` int(11) NOT NULL,
  `token_this` varchar(32) DEFAULT NULL,
  `token_remote` varchar(32) NOT NULL,
  `daily_req_limit_count` int(11) DEFAULT NULL,
  `daily_req_limit_remote_count` int(11) DEFAULT NULL,
  `supplier_last_update` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_config_entity`
--

CREATE TABLE `gt_net_config_entity` (
  `id_gt_net_entity` int(11) NOT NULL,
  `exchange` tinyint(1) NOT NULL DEFAULT 0,
  `use_detail_log` tinyint(1) NOT NULL,
  `consumer_usage` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_entity`
--

CREATE TABLE `gt_net_entity` (
  `id_gt_net_entity` int(11) NOT NULL,
  `id_gt_net` int(11) NOT NULL,
  `entity_kind` tinyint(1) NOT NULL,
  `server_state` tinyint(1) NOT NULL,
  `accept_request` tinyint(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_exchange`
--

CREATE TABLE `gt_net_exchange` (
  `id_gt_net_exchange` int(11) NOT NULL,
  `id_securitycurrency` int(11) NOT NULL,
  `lastprice_recv` tinyint(1) NOT NULL DEFAULT 0,
  `historical_recv` tinyint(1) NOT NULL DEFAULT 0,
  `lastprice_send` tinyint(1) NOT NULL DEFAULT 0,
  `historical_send` tinyint(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_lastprice`
--

CREATE TABLE `gt_net_lastprice` (
  `id_gt_net_lastprice` int(11) NOT NULL,
  `id_gt_net` int(11) NOT NULL,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `open` double(22,8) DEFAULT NULL,
  `low` double(22,8) DEFAULT NULL,
  `high` double(22,8) DEFAULT NULL,
  `last` double(22,8) NOT NULL,
  `volume` bigint(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_lastprice_currencypair`
--

CREATE TABLE `gt_net_lastprice_currencypair` (
  `id_gt_net_lastprice` int(11) NOT NULL,
  `dtype` varchar(1) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `from_currency` char(3) NOT NULL,
  `to_currency` char(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_lastprice_detail_log`
--

CREATE TABLE `gt_net_lastprice_detail_log` (
  `id_gt_net_lastprice_detail_log` int(11) NOT NULL,
  `id_gt_net_lastprice_log` int(11) NOT NULL,
  `id_gt_net_lastprice` int(11) NOT NULL,
  `last` double(22,8) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_lastprice_security`
--

CREATE TABLE `gt_net_lastprice_security` (
  `id_gt_net_lastprice` int(11) NOT NULL,
  `dtype` varchar(1) DEFAULT NULL,
  `isin` varchar(12) NOT NULL,
  `currency` char(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_message`
--

CREATE TABLE `gt_net_message` (
  `id_gt_net_message` int(11) NOT NULL,
  `id_gt_net` int(11) NOT NULL,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `send_recv` tinyint(1) NOT NULL,
  `id_source_gt_net_message` int(11) DEFAULT NULL,
  `reply_to` int(11) DEFAULT NULL,
  `message_code` tinyint(3) DEFAULT NULL,
  `message` varchar(1000) DEFAULT NULL,
  `error_msg_code` varchar(50) DEFAULT NULL,
  `has_been_read` tinyint(1) NOT NULL DEFAULT 0,
  `wait_days_apply` smallint(4) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_message_answer`
--

CREATE TABLE `gt_net_message_answer` (
  `id_gt_net_message_answer` int(11) NOT NULL,
  `request_msg_code` tinyint(3) NOT NULL,
  `response_msg_code` tinyint(3) NOT NULL,
  `priority` tinyint(1) NOT NULL,
  `response_msg_conditional` varchar(256) DEFAULT NULL,
  `response_msg_message` varchar(1000) DEFAULT NULL,
  `wait_days_apply` tinyint(4) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_message_attempt`
--

CREATE TABLE `gt_net_message_attempt` (
  `id_gt_net_message_attempt` int(11) NOT NULL,
  `id_gt_net_message` int(11) NOT NULL,
  `attempt_number` tinyint(4) NOT NULL,
  `attempt_timestamp` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `success` tinyint(1) NOT NULL DEFAULT 0,
  `http_status` smallint(6) DEFAULT NULL,
  `error_message` varchar(512) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_message_param`
--

CREATE TABLE `gt_net_message_param` (
  `id_gt_net_message` int(11) NOT NULL,
  `param_name` varchar(32) NOT NULL,
  `param_value` varchar(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `gt_net_supplier_detail`
--

CREATE TABLE `gt_net_supplier_detail` (
  `id_gt_net_supplier_detail` int(11) NOT NULL,
  `id_gt_net` int(11) NOT NULL,
  `id_securitycurrency` int(11) NOT NULL,
  `price_type` tinyint(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Indizes der exportierten Tabellen
--

--
-- Indizes für die Tabelle `gt_net`
--
ALTER TABLE `gt_net`
  ADD PRIMARY KEY (`id_gt_net`),
  ADD UNIQUE KEY `domainRemoteName` (`domain_remote_name`);

--
-- Indizes für die Tabelle `gt_net_config`
--
ALTER TABLE `gt_net_config`
  ADD PRIMARY KEY (`id_gt_net`);

--
-- Indizes für die Tabelle `gt_net_config_entity`
--
ALTER TABLE `gt_net_config_entity`
  ADD PRIMARY KEY (`id_gt_net_entity`);

--
-- Indizes für die Tabelle `gt_net_entity`
--
ALTER TABLE `gt_net_entity`
  ADD PRIMARY KEY (`id_gt_net_entity`),
  ADD KEY `FK_GtNetEntity_GtNet` (`id_gt_net`);

--
-- Indizes für die Tabelle `gt_net_exchange`
--
ALTER TABLE `gt_net_exchange`
  ADD PRIMARY KEY (`id_gt_net_exchange`),
  ADD KEY `FK_GTNetExchange_Securitycurrency` (`id_securitycurrency`);

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
-- Indizes für die Tabelle `gt_net_message_answer`
--
ALTER TABLE `gt_net_message_answer`
  ADD PRIMARY KEY (`id_gt_net_message_answer`),
  ADD UNIQUE KEY `Unique_GtNetMessageAnswer` (`response_msg_code`,`priority`,`request_msg_code`);

--
-- Indizes für die Tabelle `gt_net_message_attempt`
--
ALTER TABLE `gt_net_message_attempt`
  ADD PRIMARY KEY (`id_gt_net_message_attempt`),
  ADD KEY `idx_attempt_message` (`id_gt_net_message`),
  ADD KEY `idx_attempt_pending` (`success`,`attempt_timestamp`);

--
-- Indizes für die Tabelle `gt_net_message_param`
--
ALTER TABLE `gt_net_message_param`
  ADD PRIMARY KEY (`id_gt_net_message`,`param_name`);

--
-- Indizes für die Tabelle `gt_net_supplier_detail`
--
ALTER TABLE `gt_net_supplier_detail`
  ADD PRIMARY KEY (`id_gt_net_supplier_detail`),
  ADD KEY `FK_GtNetSupplierDetail_SecurityCurrency` (`id_securitycurrency`),
  ADD KEY `FK_GtNetSupplierDetail` (`id_gt_net`);

--
-- AUTO_INCREMENT für exportierte Tabellen
--

--
-- AUTO_INCREMENT für Tabelle `gt_net`
--
ALTER TABLE `gt_net`
  MODIFY `id_gt_net` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT für Tabelle `gt_net_entity`
--
ALTER TABLE `gt_net_entity`
  MODIFY `id_gt_net_entity` int(11) NOT NULL AUTO_INCREMENT;

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
-- AUTO_INCREMENT für Tabelle `gt_net_message_answer`
--
ALTER TABLE `gt_net_message_answer`
  MODIFY `id_gt_net_message_answer` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT für Tabelle `gt_net_message_attempt`
--
ALTER TABLE `gt_net_message_attempt`
  MODIFY `id_gt_net_message_attempt` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT für Tabelle `gt_net_supplier_detail`
--
ALTER TABLE `gt_net_supplier_detail`
  MODIFY `id_gt_net_supplier_detail` int(11) NOT NULL AUTO_INCREMENT;

--
-- Constraints der exportierten Tabellen
--

--
-- Constraints der Tabelle `gt_net_config`
--
ALTER TABLE `gt_net_config`
  ADD CONSTRAINT `FK_GTNetConfig_GTNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE;

--
-- Constraints der Tabelle `gt_net_config_entity`
--
ALTER TABLE `gt_net_config_entity`
  ADD CONSTRAINT `FK_GtNetConfigEntity_GtNetEntity` FOREIGN KEY (`id_gt_net_entity`) REFERENCES `gt_net_entity` (`id_gt_net_entity`) ON UPDATE CASCADE;

--
-- Constraints der Tabelle `gt_net_entity`
--
ALTER TABLE `gt_net_entity`
  ADD CONSTRAINT `FK_GtNetEntity_GtNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE;

--
-- Constraints der Tabelle `gt_net_exchange`
--
ALTER TABLE `gt_net_exchange`
  ADD CONSTRAINT `FK_GTNetExchange_Securitycurrency` FOREIGN KEY (`id_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`) ON DELETE CASCADE;

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

--
-- Constraints der Tabelle `gt_net_message_attempt`
--
ALTER TABLE `gt_net_message_attempt`
  ADD CONSTRAINT `fk_attempt_message` FOREIGN KEY (`id_gt_net_message`) REFERENCES `gt_net_message` (`id_gt_net_message`) ON DELETE CASCADE;

--
-- Constraints der Tabelle `gt_net_message_param`
--
ALTER TABLE `gt_net_message_param`
  ADD CONSTRAINT `FK_GTNetMessageParam_GTNetMessage` FOREIGN KEY (`id_gt_net_message`) REFERENCES `gt_net_message` (`id_gt_net_message`);

--
-- Constraints der Tabelle `gt_net_supplier_detail`
--
ALTER TABLE `gt_net_supplier_detail`
  ADD CONSTRAINT `FK_GtNetSupplierDetail_GTNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_GtNetSupplierDetail_SecurityCurrency` FOREIGN KEY (`id_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`) ON DELETE CASCADE;
COMMIT;

-- Hugo
ALTER TABLE mail_send_recv ADD INDEX FK_MailInOut_GtNet (id_gt_net);
ALTER TABLE mail_send_recv ADD CONSTRAINT FK_MailSendRecv_GtNet FOREIGN KEY (id_gt_net) REFERENCES gt_net (id_gt_net);

DELETE FROM globalparameters WHERE property_name = "gt.gtnet.my.entry.id";
INSERT INTO globalparameters (property_name, changed_by_system) VALUES ('gt.gtnet.my.entry.id', 1);

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
