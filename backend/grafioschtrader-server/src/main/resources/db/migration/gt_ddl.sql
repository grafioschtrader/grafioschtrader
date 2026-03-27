-- MariaDB dump 10.19  Distrib 10.4.27-MariaDB, for Win64 (AMD64)
--
-- Host: localhost    Database: grafioschtrader
-- ------------------------------------------------------
-- Server version	10.4.27-MariaDB

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `algo_assetclass`
--

DROP TABLE IF EXISTS `algo_assetclass`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_assetclass` (
  `id_algo_assetclass_security` int(11) NOT NULL,
  `id_algo_assetclass_parent` int(11) DEFAULT NULL,
  `id_asset_class` int(11) DEFAULT NULL,
  `category_type` smallint(6) DEFAULT NULL,
  `spec_invest_instrument` smallint(6) DEFAULT NULL,
  `name` varchar(40) DEFAULT NULL,
  PRIMARY KEY (`id_algo_assetclass_security`),
  KEY `FK_AlgoAssetClass_Assetclass` (`id_asset_class`),
  KEY `FK_AlgoAssetClass_AlgoTop` (`id_algo_assetclass_parent`),
  CONSTRAINT `FK_AlgoAssetClass_AlgoAssetClassSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_assetclass_security` (`id_algo_assetclass_security`) ON DELETE CASCADE,
  CONSTRAINT `FK_AlgoAssetClass_AlgoTop` FOREIGN KEY (`id_algo_assetclass_parent`) REFERENCES `algo_top` (`id_algo_assetclass_security`) ON DELETE CASCADE,
  CONSTRAINT `FK_AlgoAssetClass_Assetclass` FOREIGN KEY (`id_asset_class`) REFERENCES `assetclass` (`id_asset_class`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_assetclass_security`
--

DROP TABLE IF EXISTS `algo_assetclass_security`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_assetclass_security` (
  `id_algo_assetclass_security` int(11) NOT NULL,
  `id_securitycash_account_1` int(11) DEFAULT NULL,
  `id_securitycash_account_2` int(11) DEFAULT NULL,
  PRIMARY KEY (`id_algo_assetclass_security`),
  KEY `FK_AlgoAssetClassSecurity_SecurityAccount_1` (`id_securitycash_account_1`),
  KEY `FK_AlgoAssetClassSecurity_SecurityAccount_2` (`id_securitycash_account_2`),
  CONSTRAINT `FK_AlgoAssetClassSecurity_AlgoTopAssetSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_top_asset_security` (`id_algo_assetclass_security`) ON DELETE CASCADE,
  CONSTRAINT `FK_AlgoAssetClassSecurity_SecurityAccount_1` FOREIGN KEY (`id_securitycash_account_1`) REFERENCES `securityaccount` (`id_securitycash_account`),
  CONSTRAINT `FK_AlgoAssetClassSecurity_SecurityAccount_2` FOREIGN KEY (`id_securitycash_account_2`) REFERENCES `securityaccount` (`id_securitycash_account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_event_log`
--

DROP TABLE IF EXISTS `algo_event_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_event_log` (
  `id_algo_event` int(11) NOT NULL AUTO_INCREMENT,
  `id_algo_top` int(11) NOT NULL,
  `id_securitycurrency` int(11) DEFAULT NULL,
  `id_tenant` int(11) NOT NULL,
  `event_type` varchar(50) NOT NULL,
  `event_date` date NOT NULL,
  `details` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`details`)),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id_algo_event`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_execution_state`
--

DROP TABLE IF EXISTS `algo_execution_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_execution_state` (
  `id_algo_execution_state` int(11) NOT NULL AUTO_INCREMENT,
  `id_algo_top` int(11) NOT NULL,
  `id_securitycurrency` int(11) NOT NULL,
  `id_tenant` int(11) NOT NULL,
  `position_qty` double DEFAULT 0,
  `position_direction` tinyint(4) DEFAULT 1,
  `avg_cost` double DEFAULT 0,
  `initial_entry_price` double DEFAULT NULL,
  `initial_entry_qty` double DEFAULT NULL,
  `adds_done` int(11) DEFAULT 0,
  `last_buy_date` date DEFAULT NULL,
  `last_sell_date` date DEFAULT NULL,
  `tranche_state` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`tranche_state`)),
  `state_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`state_data`)),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id_algo_execution_state`),
  UNIQUE KEY `UK_ExecState` (`id_algo_top`,`id_securitycurrency`,`id_tenant`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_message_alert`
--

DROP TABLE IF EXISTS `algo_message_alert`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_message_alert` (
  `id_algo_message_alert` int(11) NOT NULL AUTO_INCREMENT,
  `id_security_currency` int(11) DEFAULT NULL,
  `id_tenant` int(11) NOT NULL,
  `id_algo_strategy` int(11) NOT NULL,
  `alert_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `alarm_type` tinyint(4) DEFAULT NULL,
  `alarm_details` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`alarm_details`)),
  `id_algo_recommendation` int(11) DEFAULT NULL,
  PRIMARY KEY (`id_algo_message_alert`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_recommendation`
--

DROP TABLE IF EXISTS `algo_recommendation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_recommendation` (
  `id_algo_recommendation` int(11) NOT NULL AUTO_INCREMENT,
  `id_tenant` int(11) NOT NULL,
  `id_algo_assetclass_security` int(11) NOT NULL,
  `id_securitycurrency` int(11) NOT NULL,
  `recommendation_type` tinyint(4) NOT NULL,
  `recommended_units` double DEFAULT NULL,
  `recommended_price` double DEFAULT NULL,
  `rationale` varchar(500) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `executed_at` timestamp NULL DEFAULT NULL,
  `id_transaction` int(11) DEFAULT NULL,
  PRIMARY KEY (`id_algo_recommendation`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_rule_strategy`
--

DROP TABLE IF EXISTS `algo_rule_strategy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_rule_strategy` (
  `id_algo_rule_strategy` int(11) NOT NULL AUTO_INCREMENT,
  `id_algo_assetclass_security` int(11) NOT NULL,
  `dtype` varchar(1) NOT NULL,
  `id_tenant` int(11) NOT NULL,
  PRIMARY KEY (`id_algo_rule_strategy`) USING BTREE,
  KEY `FK_AlgoRuleStrategy_AlgoTopAssetSecurity` (`id_algo_assetclass_security`),
  CONSTRAINT `FK_AlgoRuleStrategy_AlgoTopAssetSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_top_asset_security` (`id_algo_assetclass_security`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_rule_strategy_param`
--

DROP TABLE IF EXISTS `algo_rule_strategy_param`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_rule_strategy_param` (
  `id_algo_rule_strategy` int(11) NOT NULL,
  `param_name` varchar(32) NOT NULL,
  `param_value` varchar(24) NOT NULL,
  PRIMARY KEY (`id_algo_rule_strategy`,`param_name`) USING BTREE,
  CONSTRAINT `FK_AlgoRuleStrategyParam_AlgoRuleStrategy` FOREIGN KEY (`id_algo_rule_strategy`) REFERENCES `algo_rule_strategy` (`id_algo_rule_strategy`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_security`
--

DROP TABLE IF EXISTS `algo_security`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_security` (
  `id_algo_assetclass_security` int(11) NOT NULL,
  `id_algo_security_parent` int(11) DEFAULT NULL,
  `id_securitycurrency` int(11) NOT NULL,
  `activatable` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id_algo_assetclass_security`),
  KEY `FK_AlgoSecurity_Security` (`id_securitycurrency`),
  KEY `FK_AlgoSecurity_AlgoAssetClass` (`id_algo_security_parent`),
  CONSTRAINT `FK_AlgoSecurity_AlgoAssetClass` FOREIGN KEY (`id_algo_security_parent`) REFERENCES `algo_assetclass` (`id_algo_assetclass_security`) ON DELETE CASCADE,
  CONSTRAINT `FK_AlgoSecurity_AlgoAssetClassSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_assetclass_security` (`id_algo_assetclass_security`) ON DELETE CASCADE,
  CONSTRAINT `FK_AlgoSecurity_Security` FOREIGN KEY (`id_securitycurrency`) REFERENCES `security` (`id_securitycurrency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_simulation_result`
--

DROP TABLE IF EXISTS `algo_simulation_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_simulation_result` (
  `id_simulation_result` int(11) NOT NULL AUTO_INCREMENT,
  `id_tenant` int(11) NOT NULL,
  `id_algo_top` int(11) NOT NULL,
  `total_return` double DEFAULT NULL,
  `annualized_return` double DEFAULT NULL,
  `max_drawdown` double DEFAULT NULL,
  `sharpe_ratio` double DEFAULT NULL,
  `total_trades` int(11) DEFAULT NULL,
  `winning_trades` int(11) DEFAULT NULL,
  `losing_trades` int(11) DEFAULT NULL,
  `calculated_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id_simulation_result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_strategy`
--

DROP TABLE IF EXISTS `algo_strategy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_strategy` (
  `id_algo_rule_strategy` int(11) NOT NULL,
  `algo_strategy_impl` tinyint(4) NOT NULL,
  `strategy_config` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`strategy_config`)),
  `activatable` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id_algo_rule_strategy`),
  CONSTRAINT `FK_AlgoStrategy_AlgoRuleStrategy` FOREIGN KEY (`id_algo_rule_strategy`) REFERENCES `algo_rule_strategy` (`id_algo_rule_strategy`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_top`
--

DROP TABLE IF EXISTS `algo_top`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_top` (
  `id_algo_assetclass_security` int(11) NOT NULL,
  `name` varchar(32) NOT NULL,
  `activatable` tinyint(1) NOT NULL,
  `id_watchlist` int(11) NOT NULL,
  `simulation_start_date` date DEFAULT NULL,
  `simulation_end_date` date DEFAULT NULL,
  `reference_date` date DEFAULT NULL,
  PRIMARY KEY (`id_algo_assetclass_security`),
  KEY `FK_AlgoTop_Watchlist` (`id_watchlist`),
  CONSTRAINT `FK_AlgoTop_AlgoTopAssetSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_top_asset_security` (`id_algo_assetclass_security`) ON DELETE CASCADE,
  CONSTRAINT `FK_AlgoTop_Watchlist` FOREIGN KEY (`id_watchlist`) REFERENCES `watchlist` (`id_watchlist`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_top_asset_security`
--

DROP TABLE IF EXISTS `algo_top_asset_security`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_top_asset_security` (
  `id_algo_assetclass_security` int(11) NOT NULL AUTO_INCREMENT,
  `dtype` varchar(1) NOT NULL,
  `id_tenant` int(11) NOT NULL,
  `percentage` float DEFAULT NULL,
  PRIMARY KEY (`id_algo_assetclass_security`),
  KEY `FK_AlgoTopAssetSecurity_Tenant` (`id_tenant`),
  CONSTRAINT `FK_AlgoTopAssetSecurity_Tenant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`)
) ENGINE=InnoDB AUTO_INCREMENT=218 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `assetclass`
--

DROP TABLE IF EXISTS `assetclass`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `assetclass` (
  `id_asset_class` int(11) NOT NULL AUTO_INCREMENT,
  `category_type` smallint(6) NOT NULL,
  `spec_invest_instrument` smallint(6) NOT NULL,
  `sub_category_nls` int(11) NOT NULL,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id_asset_class`),
  UNIQUE KEY `category_type` (`category_type`,`sub_category_nls`,`spec_invest_instrument`) USING BTREE,
  KEY `FK_Assetklass_Multilinguestring` (`sub_category_nls`),
  CONSTRAINT `FK_Assetclass_Multilinguestring` FOREIGN KEY (`sub_category_nls`) REFERENCES `multilinguestring` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=568 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cashaccount`
--

DROP TABLE IF EXISTS `cashaccount`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cashaccount` (
  `id_securitycash_account` int(11) NOT NULL AUTO_INCREMENT,
  `currency` char(3) NOT NULL,
  `connect_id_securityaccount` int(11) DEFAULT NULL,
  `borrowing_rate` double DEFAULT NULL,
  PRIMARY KEY (`id_securitycash_account`),
  CONSTRAINT `FK_CashAccount_SecurityCashAccount` FOREIGN KEY (`id_securitycash_account`) REFERENCES `securitycashaccount` (`id_securitycash_account`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=75606 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `connector_apikey`
--

DROP TABLE IF EXISTS `connector_apikey`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `connector_apikey` (
  `id_provider` varchar(32) NOT NULL,
  `api_key` varchar(255) NOT NULL,
  `subscription_type` smallint(6) DEFAULT NULL,
  PRIMARY KEY (`id_provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `correlation_instrument`
--

DROP TABLE IF EXISTS `correlation_instrument`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `correlation_instrument` (
  `id_correlation_set` int(11) NOT NULL,
  `id_securitycurrency` int(11) NOT NULL,
  PRIMARY KEY (`id_correlation_set`,`id_securitycurrency`),
  KEY `FK_CorrInstrument_SecurityCurrency` (`id_securitycurrency`),
  CONSTRAINT `FK_CorrInstrument_CorrSet` FOREIGN KEY (`id_correlation_set`) REFERENCES `correlation_set` (`id_correlation_set`) ON DELETE CASCADE,
  CONSTRAINT `FK_CorrInstrument_SecurityCurrency` FOREIGN KEY (`id_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `correlation_set`
--

DROP TABLE IF EXISTS `correlation_set`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `correlation_set` (
  `id_correlation_set` int(11) NOT NULL AUTO_INCREMENT,
  `id_tenant` int(11) NOT NULL,
  `name` varchar(25) NOT NULL,
  `note` varchar(1000) DEFAULT NULL,
  `date_from` date DEFAULT NULL,
  `date_to` date DEFAULT NULL,
  `sampling_period` tinyint(1) NOT NULL DEFAULT 1,
  `rolling` tinyint(4) DEFAULT 12,
  `adjust_currency` tinyint(4) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_correlation_set`),
  UNIQUE KEY `Unique_idTenant_name` (`id_tenant`,`name`)
) ENGINE=InnoDB AUTO_INCREMENT=7447 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `currencypair`
--

DROP TABLE IF EXISTS `currencypair`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `currencypair` (
  `id_securitycurrency` int(11) NOT NULL,
  `from_currency` char(3) NOT NULL,
  `to_currency` char(3) NOT NULL,
  PRIMARY KEY (`id_securitycurrency`),
  UNIQUE KEY `from_to_currency` (`from_currency`,`to_currency`),
  CONSTRAINT `FK_Currencypair_SecurityCurrency` FOREIGN KEY (`id_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dividend`
--

DROP TABLE IF EXISTS `dividend`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `dividend` (
  `id_dividend` int(11) NOT NULL AUTO_INCREMENT,
  `id_securitycurrency` int(11) NOT NULL,
  `ex_date` date NOT NULL,
  `pay_date` date DEFAULT NULL,
  `amount` double(22,8) NOT NULL,
  `amount_adjusted` double(22,10) NOT NULL,
  `currency` char(3) NOT NULL,
  `create_type` tinyint(1) NOT NULL,
  `create_modify_time` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id_dividend`),
  KEY `FK_Dividend_Security` (`id_securitycurrency`),
  CONSTRAINT `FK_Dividend_Security` FOREIGN KEY (`id_securitycurrency`) REFERENCES `security` (`id_securitycurrency`)
) ENGINE=InnoDB AUTO_INCREMENT=1069713 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ecb_exchange_rates`
--

DROP TABLE IF EXISTS `ecb_exchange_rates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ecb_exchange_rates` (
  `date` date NOT NULL,
  `currency` char(3) NOT NULL,
  `rate` double(22,8) NOT NULL,
  PRIMARY KEY (`date`,`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int(11) NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int(11) DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT current_timestamp(),
  `execution_time` int(11) NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `generic_connector_def`
--

DROP TABLE IF EXISTS `generic_connector_def`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `generic_connector_def` (
  `id_generic_connector` int(11) NOT NULL AUTO_INCREMENT,
  `short_id` varchar(32) NOT NULL,
  `readable_name` varchar(100) NOT NULL,
  `domain_url` varchar(255) NOT NULL,
  `needs_api_key` tinyint(1) NOT NULL DEFAULT 0,
  `rate_limit_type` tinyint(3) NOT NULL DEFAULT 0,
  `rate_limit_requests` smallint(6) DEFAULT NULL,
  `rate_limit_period_sec` smallint(6) DEFAULT NULL,
  `rate_limit_concurrent` smallint(6) DEFAULT NULL,
  `intraday_delay_seconds` int(11) NOT NULL DEFAULT 900,
  `regex_url_pattern` varchar(255) DEFAULT NULL,
  `supports_security` tinyint(1) NOT NULL DEFAULT 1,
  `supports_currency` tinyint(1) NOT NULL DEFAULT 0,
  `need_history_gap_filler` tinyint(1) NOT NULL DEFAULT 0,
  `gbx_divider_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `description_nls` int(11) DEFAULT NULL,
  `activated` tinyint(1) NOT NULL DEFAULT 0,
  `created_by` int(11) DEFAULT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `last_modified_by` int(11) DEFAULT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) DEFAULT 0,
  `token_config_yaml` text DEFAULT NULL,
  `supported_categories` varchar(255) DEFAULT NULL,
  `geo_restrictions` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id_generic_connector`),
  UNIQUE KEY `uk_generic_connector_short_id` (`short_id`),
  KEY `FK_GenericConnector_DescNLS` (`description_nls`),
  CONSTRAINT `FK_GenericConnector_DescNLS` FOREIGN KEY (`description_nls`) REFERENCES `multilinguestring` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `generic_connector_endpoint`
--

DROP TABLE IF EXISTS `generic_connector_endpoint`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `generic_connector_endpoint` (
  `id_endpoint` int(11) NOT NULL AUTO_INCREMENT,
  `id_generic_connector` int(11) NOT NULL,
  `feed_support` varchar(10) NOT NULL,
  `instrument_type` varchar(10) NOT NULL,
  `url_template` varchar(1000) NOT NULL,
  `http_method` varchar(6) NOT NULL DEFAULT 'GET',
  `response_format` tinyint(3) NOT NULL,
  `number_format` tinyint(3) NOT NULL DEFAULT 4,
  `date_format_type` tinyint(3) NOT NULL DEFAULT 4,
  `date_format_pattern` varchar(64) DEFAULT NULL,
  `json_data_structure` tinyint(3) DEFAULT NULL,
  `json_data_path` varchar(255) DEFAULT NULL,
  `json_status_path` varchar(128) DEFAULT NULL,
  `json_status_ok_value` varchar(64) DEFAULT NULL,
  `csv_delimiter` varchar(4) DEFAULT NULL,
  `csv_skip_header_lines` tinyint(3) DEFAULT 1,
  `html_css_selector` varchar(255) DEFAULT NULL,
  `html_extract_mode` tinyint(3) DEFAULT NULL,
  `html_text_cleanup` varchar(255) DEFAULT NULL,
  `html_extract_regex` varchar(512) DEFAULT NULL,
  `html_split_delimiter` varchar(16) DEFAULT NULL,
  `ticker_build_strategy` tinyint(3) NOT NULL DEFAULT 1,
  `currency_pair_separator` varchar(4) DEFAULT NULL,
  `currency_pair_suffix` varchar(20) DEFAULT NULL,
  `ticker_uppercase` tinyint(1) NOT NULL DEFAULT 1,
  `max_data_points` int(11) DEFAULT NULL,
  `pagination_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `endpoint_options` bigint(20) DEFAULT NULL,
  `ever_used_successfully` tinyint(1) NOT NULL DEFAULT 0,
  `json_column_names_path` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id_endpoint`),
  UNIQUE KEY `uk_endpoint_feed` (`id_generic_connector`,`feed_support`,`instrument_type`),
  CONSTRAINT `FK_Endpoint_GenericConnector` FOREIGN KEY (`id_generic_connector`) REFERENCES `generic_connector_def` (`id_generic_connector`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `generic_connector_field_mapping`
--

DROP TABLE IF EXISTS `generic_connector_field_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `generic_connector_field_mapping` (
  `id_field_mapping` int(11) NOT NULL AUTO_INCREMENT,
  `id_endpoint` int(11) NOT NULL,
  `target_field` varchar(20) NOT NULL,
  `source_expression` varchar(255) NOT NULL,
  `csv_column_index` smallint(6) DEFAULT NULL,
  `divider_expression` varchar(64) DEFAULT NULL,
  `is_required` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_field_mapping`),
  KEY `FK_FieldMapping_Endpoint` (`id_endpoint`),
  CONSTRAINT `FK_FieldMapping_Endpoint` FOREIGN KEY (`id_endpoint`) REFERENCES `generic_connector_endpoint` (`id_endpoint`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `generic_connector_http_header`
--

DROP TABLE IF EXISTS `generic_connector_http_header`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `generic_connector_http_header` (
  `id_http_header` int(11) NOT NULL AUTO_INCREMENT,
  `id_generic_connector` int(11) NOT NULL,
  `header_name` varchar(64) NOT NULL,
  `header_value` varchar(512) NOT NULL,
  PRIMARY KEY (`id_http_header`),
  KEY `FK_HttpHeader_GenericConnector` (`id_generic_connector`),
  CONSTRAINT `FK_HttpHeader_GenericConnector` FOREIGN KEY (`id_generic_connector`) REFERENCES `generic_connector_def` (`id_generic_connector`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `globalparameters`
--

DROP TABLE IF EXISTS `globalparameters`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `globalparameters` (
  `property_name` varchar(45) NOT NULL,
  `property_int` int(11) DEFAULT NULL,
  `property_string` varchar(30) DEFAULT NULL,
  `property_date` date DEFAULT NULL,
  `property_date_time` timestamp NULL DEFAULT NULL,
  `property_blob` blob DEFAULT NULL,
  `changed_by_system` tinyint(1) DEFAULT 0,
  `input_rule` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`property_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net`
--

DROP TABLE IF EXISTS `gt_net`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net` (
  `id_gt_net` int(11) NOT NULL AUTO_INCREMENT,
  `domain_remote_name` varchar(128) NOT NULL,
  `time_zone` varchar(50) NOT NULL,
  `spread_capability` tinyint(1) NOT NULL DEFAULT 0,
  `daily_req_limit` int(11) DEFAULT NULL,
  `allow_server_creation` tinyint(1) NOT NULL DEFAULT 0,
  `server_busy` tinyint(1) NOT NULL DEFAULT 0,
  `server_online` tinyint(1) NOT NULL DEFAULT 0,
  `last_modified_time` timestamp NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id_gt_net`),
  UNIQUE KEY `domainRemoteName` (`domain_remote_name`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_config`
--

DROP TABLE IF EXISTS `gt_net_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_config` (
  `id_gt_net` int(11) NOT NULL,
  `token_this` varchar(32) DEFAULT NULL,
  `token_remote` varchar(32) NOT NULL,
  `daily_req_limit_count` int(11) DEFAULT NULL,
  `daily_req_limit_remote_count` int(11) DEFAULT NULL,
  `supplier_last_update` timestamp NULL DEFAULT NULL,
  `serverlist_access_granted` tinyint(1) NOT NULL DEFAULT 0,
  `request_violation_count` tinyint(2) NOT NULL DEFAULT 0,
  `handshake_timestamp` timestamp NULL DEFAULT NULL COMMENT 'UTC timestamp when first successful handshake completed',
  `connection_timeout` tinyint(4) DEFAULT NULL,
  PRIMARY KEY (`id_gt_net`),
  CONSTRAINT `FK_GTNetConfig_GTNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_config_entity`
--

DROP TABLE IF EXISTS `gt_net_config_entity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_config_entity` (
  `id_gt_net_entity` int(11) NOT NULL,
  `exchange` tinyint(1) NOT NULL DEFAULT 0,
  `consumer_usage` int(11) NOT NULL DEFAULT 0,
  `supplier_log` tinyint(4) NOT NULL DEFAULT 1,
  `consumer_log` tinyint(4) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id_gt_net_entity`),
  CONSTRAINT `FK_GtNetConfigEntity_GtNetEntity` FOREIGN KEY (`id_gt_net_entity`) REFERENCES `gt_net_entity` (`id_gt_net_entity`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_entity`
--

DROP TABLE IF EXISTS `gt_net_entity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_entity` (
  `id_gt_net_entity` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net` int(11) NOT NULL,
  `entity_kind` tinyint(1) NOT NULL,
  `server_state` tinyint(1) NOT NULL,
  `accept_request` tinyint(1) NOT NULL,
  `max_limit` smallint(5) NOT NULL,
  PRIMARY KEY (`id_gt_net_entity`),
  KEY `FK_GtNetEntity_GtNet` (`id_gt_net`),
  CONSTRAINT `FK_GtNetEntity_GtNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_exchange_log`
--

DROP TABLE IF EXISTS `gt_net_exchange_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_exchange_log` (
  `id_gt_net_exchange_log` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net` int(11) NOT NULL,
  `entity_kind` tinyint(4) NOT NULL,
  `log_as_supplier` tinyint(1) NOT NULL,
  `period_type` tinyint(4) NOT NULL,
  `period_start` date NOT NULL,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp(),
  `entities_sent` int(11) NOT NULL DEFAULT 0,
  `entities_updated` int(11) NOT NULL DEFAULT 0,
  `entities_in_response` int(11) NOT NULL DEFAULT 0,
  `request_count` int(11) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id_gt_net_exchange_log`),
  KEY `FK_GtNetExchangeLog_GtNet` (`id_gt_net`),
  CONSTRAINT `FK_GtNetExchangeLog_GtNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4769 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_historyquote`
--

DROP TABLE IF EXISTS `gt_net_historyquote`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_historyquote` (
  `id_gt_net_historyquote` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net_instrument` int(11) NOT NULL,
  `date` date NOT NULL,
  `open` double(22,8) DEFAULT NULL,
  `high` double(22,8) DEFAULT NULL,
  `low` double(22,8) DEFAULT NULL,
  `close` double(22,8) NOT NULL,
  `volume` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id_gt_net_historyquote`),
  UNIQUE KEY `UK_GtNetHistoryquote_InstrumentDate` (`id_gt_net_instrument`,`date`),
  CONSTRAINT `FK_GtNetHistoryquote_Instrument` FOREIGN KEY (`id_gt_net_instrument`) REFERENCES `gt_net_instrument` (`id_gt_net_instrument`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_instrument`
--

DROP TABLE IF EXISTS `gt_net_instrument`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_instrument` (
  `id_gt_net_instrument` int(11) NOT NULL AUTO_INCREMENT,
  `dtype` varchar(1) NOT NULL,
  PRIMARY KEY (`id_gt_net_instrument`)
) ENGINE=InnoDB AUTO_INCREMENT=289 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_instrument_currencypair`
--

DROP TABLE IF EXISTS `gt_net_instrument_currencypair`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_instrument_currencypair` (
  `id_gt_net_instrument` int(11) NOT NULL,
  `from_currency` char(3) NOT NULL,
  `to_currency` char(3) NOT NULL,
  PRIMARY KEY (`id_gt_net_instrument`),
  UNIQUE KEY `UK_GtNetInstrumentCp_FromTo` (`from_currency`,`to_currency`),
  CONSTRAINT `FK_GtNetInstrumentCp_Instrument` FOREIGN KEY (`id_gt_net_instrument`) REFERENCES `gt_net_instrument` (`id_gt_net_instrument`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_instrument_security`
--

DROP TABLE IF EXISTS `gt_net_instrument_security`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_instrument_security` (
  `id_gt_net_instrument` int(11) NOT NULL,
  `isin` varchar(12) NOT NULL,
  `currency` char(3) NOT NULL,
  PRIMARY KEY (`id_gt_net_instrument`),
  UNIQUE KEY `UK_GtNetInstrumentSec_IsinCurrency` (`isin`,`currency`),
  CONSTRAINT `FK_GtNetInstrumentSec_Instrument` FOREIGN KEY (`id_gt_net_instrument`) REFERENCES `gt_net_instrument` (`id_gt_net_instrument`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_lastprice`
--

DROP TABLE IF EXISTS `gt_net_lastprice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_lastprice` (
  `id_gt_net_lastprice` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net_instrument` int(11) NOT NULL,
  `timestamp` timestamp NULL DEFAULT NULL,
  `open` double(22,8) DEFAULT NULL,
  `high` double(22,8) DEFAULT NULL,
  `low` double(22,8) DEFAULT NULL,
  `last` double(22,8) DEFAULT NULL,
  `volume` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id_gt_net_lastprice`),
  UNIQUE KEY `UK_GtNetLastprice_Instrument` (`id_gt_net_instrument`),
  CONSTRAINT `FK_GtNetLastprice_Instrument` FOREIGN KEY (`id_gt_net_instrument`) REFERENCES `gt_net_instrument` (`id_gt_net_instrument`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_message`
--

DROP TABLE IF EXISTS `gt_net_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_message` (
  `id_gt_net_message` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net` int(11) NOT NULL,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp(),
  `send_recv` tinyint(1) NOT NULL,
  `id_source_gt_net_message` int(11) DEFAULT NULL,
  `reply_to` int(11) DEFAULT NULL,
  `id_original_message` int(11) DEFAULT NULL COMMENT 'Reference to original announcement for cancellation messages',
  `message_code` tinyint(3) DEFAULT NULL,
  `message` varchar(1000) DEFAULT NULL,
  `visibility` tinyint(1) NOT NULL DEFAULT 0,
  `error_msg_code` varchar(50) DEFAULT NULL,
  `has_been_read` tinyint(1) NOT NULL DEFAULT 0,
  `wait_days_apply` smallint(4) NOT NULL DEFAULT 0,
  `delivery_status` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_gt_net_message`),
  KEY `FK_GtNetMessage_GtNet` (`id_gt_net`),
  KEY `FK_GtNetMessage_GtNetMessage` (`reply_to`),
  CONSTRAINT `FK_GtNetMessage_GtNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE,
  CONSTRAINT `FK_GtNetMessage_GtNetMessage` FOREIGN KEY (`reply_to`) REFERENCES `gt_net_message` (`id_gt_net_message`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=3833 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_message_answer`
--

DROP TABLE IF EXISTS `gt_net_message_answer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_message_answer` (
  `id_gt_net_message_answer` int(11) NOT NULL AUTO_INCREMENT,
  `request_msg_code` tinyint(3) NOT NULL,
  `response_msg_code` tinyint(3) NOT NULL,
  `priority` tinyint(1) NOT NULL,
  `response_msg_conditional` varchar(256) DEFAULT NULL,
  `response_msg_message` varchar(1000) DEFAULT NULL,
  `wait_days_apply` tinyint(4) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_gt_net_message_answer`),
  UNIQUE KEY `Unique_GtNetMessageAnswer` (`response_msg_code`,`priority`,`request_msg_code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_message_attempt`
--

DROP TABLE IF EXISTS `gt_net_message_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_message_attempt` (
  `id_gt_net_message_attempt` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net` int(11) NOT NULL COMMENT 'Target GTNet instance to receive the message',
  `id_gt_net_message` int(11) NOT NULL COMMENT 'The broadcast message to deliver',
  `has_send` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Whether delivery succeeded',
  `send_timestamp` timestamp NULL DEFAULT NULL COMMENT 'When successfully delivered (UTC)',
  PRIMARY KEY (`id_gt_net_message_attempt`),
  UNIQUE KEY `uk_message_gtnet` (`id_gt_net_message`,`id_gt_net`),
  KEY `idx_attempt_message` (`id_gt_net_message`),
  KEY `idx_attempt_gtnet` (`id_gt_net`),
  KEY `idx_attempt_pending` (`has_send`,`id_gt_net_message`),
  CONSTRAINT `fk_attempt_gtnet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE,
  CONSTRAINT `fk_attempt_message` FOREIGN KEY (`id_gt_net_message`) REFERENCES `gt_net_message` (`id_gt_net_message`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Tracks per-target delivery status for future-oriented GTNet broadcast messages';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_message_param`
--

DROP TABLE IF EXISTS `gt_net_message_param`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_message_param` (
  `id_gt_net_message` int(11) NOT NULL,
  `param_name` varchar(32) NOT NULL,
  `param_value` varchar(255) NOT NULL,
  PRIMARY KEY (`id_gt_net_message`,`param_name`),
  CONSTRAINT `FK_GTNetMessageParam_GTNetMessage` FOREIGN KEY (`id_gt_net_message`) REFERENCES `gt_net_message` (`id_gt_net_message`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_security_imp_gap`
--

DROP TABLE IF EXISTS `gt_net_security_imp_gap`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_security_imp_gap` (
  `id_gt_net_security_imp_gap` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net_security_imp_pos` int(11) NOT NULL,
  `id_gt_net` int(11) NOT NULL,
  `gap_code` tinyint(2) NOT NULL,
  `gap_message` varchar(1000) NOT NULL,
  PRIMARY KEY (`id_gt_net_security_imp_gap`),
  KEY `FK_GTNetSecurityImpGap_GTNetSecurityImpPos` (`id_gt_net_security_imp_pos`),
  KEY `FK_GTNetSecurityImpGap_GTNet` (`id_gt_net`),
  CONSTRAINT `FK_GTNetSecurityImpGap_GTNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE,
  CONSTRAINT `FK_GTNetSecurityImpGap_GTNetSecurityImpPos` FOREIGN KEY (`id_gt_net_security_imp_pos`) REFERENCES `gt_net_security_imp_pos` (`id_gt_net_security_imp_pos`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_security_imp_head`
--

DROP TABLE IF EXISTS `gt_net_security_imp_head`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_security_imp_head` (
  `id_gt_net_security_imp_head` int(11) NOT NULL AUTO_INCREMENT,
  `id_tenant` int(11) NOT NULL,
  `name` varchar(40) NOT NULL,
  `note` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id_gt_net_security_imp_head`),
  KEY `fk_gt_net_sec_imp_head_tenant` (`id_tenant`),
  CONSTRAINT `fk_gt_net_sec_imp_head_tenant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_security_imp_pos`
--

DROP TABLE IF EXISTS `gt_net_security_imp_pos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_security_imp_pos` (
  `id_gt_net_security_imp_pos` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net_security_imp_head` int(11) NOT NULL,
  `isin` varchar(12) DEFAULT NULL,
  `ticker_symbol` varchar(6) DEFAULT NULL,
  `currency` varchar(3) NOT NULL,
  `id_securitycurrency` int(11) DEFAULT NULL,
  PRIMARY KEY (`id_gt_net_security_imp_pos`),
  KEY `fk_gt_net_sec_imp_pos_head` (`id_gt_net_security_imp_head`),
  KEY `fk_gt_net_sec_imp_pos_security` (`id_securitycurrency`),
  CONSTRAINT `fk_gt_net_sec_imp_pos_head` FOREIGN KEY (`id_gt_net_security_imp_head`) REFERENCES `gt_net_security_imp_head` (`id_gt_net_security_imp_head`) ON DELETE CASCADE,
  CONSTRAINT `fk_gt_net_sec_imp_pos_security` FOREIGN KEY (`id_securitycurrency`) REFERENCES `security` (`id_securitycurrency`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_supplier_detail`
--

DROP TABLE IF EXISTS `gt_net_supplier_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_supplier_detail` (
  `id_gt_net_supplier_detail` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_net` int(11) NOT NULL,
  `id_entity` int(11) DEFAULT NULL,
  `entity_kind` tinyint(1) NOT NULL,
  PRIMARY KEY (`id_gt_net_supplier_detail`),
  KEY `FK_GtNetSupplierDetail_SecurityCurrency` (`id_entity`),
  KEY `FK_GtNetSupplierDetail` (`id_gt_net`),
  CONSTRAINT `FK_GtNetSupplierDetail_GTNet` FOREIGN KEY (`id_gt_net`) REFERENCES `gt_net` (`id_gt_net`) ON DELETE CASCADE,
  CONSTRAINT `FK_GtNetSupplierDetail_SecurityCurrency` FOREIGN KEY (`id_entity`) REFERENCES `securitycurrency` (`id_securitycurrency`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=45665 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_supplier_detail_hist`
--

DROP TABLE IF EXISTS `gt_net_supplier_detail_hist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_supplier_detail_hist` (
  `id_gt_net_supplier_detail` int(11) NOT NULL,
  `retry_history_load` smallint(6) NOT NULL DEFAULT 0,
  `history_min_date` date DEFAULT NULL,
  `history_max_date` date DEFAULT NULL,
  `ohl_percentage` double DEFAULT NULL,
  PRIMARY KEY (`id_gt_net_supplier_detail`),
  CONSTRAINT `fk_supplier_detail_hist` FOREIGN KEY (`id_gt_net_supplier_detail`) REFERENCES `gt_net_supplier_detail` (`id_gt_net_supplier_detail`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gt_net_supplier_detail_last`
--

DROP TABLE IF EXISTS `gt_net_supplier_detail_last`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_net_supplier_detail_last` (
  `id_gt_net_supplier_detail` int(11) NOT NULL,
  `retry_intra_load` smallint(6) NOT NULL DEFAULT 0,
  `s_timestamp` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id_gt_net_supplier_detail`),
  CONSTRAINT `fk_supplier_detail_last` FOREIGN KEY (`id_gt_net_supplier_detail`) REFERENCES `gt_net_supplier_detail` (`id_gt_net_supplier_detail`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `historyquote`
--

DROP TABLE IF EXISTS `historyquote`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `historyquote` (
  `id_history_quote` int(11) NOT NULL AUTO_INCREMENT,
  `id_securitycurrency` int(11) NOT NULL,
  `date` date NOT NULL,
  `close` double(22,8) NOT NULL,
  `volume` bigint(11) DEFAULT NULL,
  `open` double(22,8) DEFAULT NULL,
  `high` double(22,8) DEFAULT NULL,
  `low` double(22,8) DEFAULT NULL,
  `create_type` tinyint(1) DEFAULT NULL,
  `create_modify_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id_history_quote`),
  UNIQUE KEY `IHistoryQuote_id_Date` (`id_securitycurrency`,`date`),
  KEY `FK_HistoryQuote_SecurityCurrency` (`id_securitycurrency`) USING BTREE,
  CONSTRAINT `FK_HistoryQuote_SecurityCurrency` FOREIGN KEY (`id_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=9276567 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `historyquote_period`
--

DROP TABLE IF EXISTS `historyquote_period`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `historyquote_period` (
  `id_historyquote_period` int(11) NOT NULL AUTO_INCREMENT,
  `id_securitycurrency` int(11) NOT NULL,
  `from_date` date NOT NULL,
  `to_date` date NOT NULL,
  `price` double NOT NULL,
  `create_type` tinyint(1) NOT NULL,
  PRIMARY KEY (`id_historyquote_period`),
  KEY `FK_HistoryquotePeriod_Security` (`id_securitycurrency`),
  CONSTRAINT `FK_HistoryquotePeriod_Security` FOREIGN KEY (`id_securitycurrency`) REFERENCES `security` (`id_securitycurrency`)
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT COMMENT='Certain securities do not have public and daily prices. For example, time deposits, but a daily price is still required for the holding period.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `historyquote_quality`
--

DROP TABLE IF EXISTS `historyquote_quality`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `historyquote_quality` (
  `idSecurity` int(11) NOT NULL,
  `minDate` date DEFAULT NULL,
  `connectorCreated` int(11) DEFAULT NULL,
  `filledNoTradeDay` int(11) DEFAULT NULL,
  `manualImported` int(11) DEFAULT NULL,
  `filledLinear` int(11) DEFAULT NULL,
  `missingStart` int(11) DEFAULT NULL,
  `maxDate` date DEFAULT NULL,
  `missingEnd` int(11) DEFAULT NULL,
  `totalMissing` int(11) DEFAULT NULL,
  `expectedTotal` int(11) NOT NULL,
  `qualityPercentage` double DEFAULT NULL,
  `toManyAsCalendar` int(11) NOT NULL,
  `quoteSaturday` int(11) NOT NULL,
  `quoteSunday` int(11) NOT NULL,
  `ohlPercentage` double DEFAULT NULL,
  PRIMARY KEY (`idSecurity`),
  CONSTRAINT `FK_HistoryquoteQuality_Security` FOREIGN KEY (`idSecurity`) REFERENCES `security` (`id_securitycurrency`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `historyquote_update_log`
--

DROP TABLE IF EXISTS `historyquote_update_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `historyquote_update_log` (
  `id_hq_update_log` int(11) NOT NULL AUTO_INCREMENT,
  `id_stockexchange` int(11) NOT NULL,
  `update_timestamp` timestamp NOT NULL DEFAULT current_timestamp(),
  `minutes_since_close` int(11) NOT NULL,
  `securities_count` int(11) NOT NULL,
  `securities_updated` int(11) NOT NULL DEFAULT 0,
  `update_status` tinyint(1) NOT NULL DEFAULT 0,
  `error_message` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id_hq_update_log`),
  KEY `idx_hq_update_stockexchange` (`id_stockexchange`),
  KEY `idx_hq_update_timestamp` (`update_timestamp`),
  CONSTRAINT `fk_hq_update_stockexchange` FOREIGN KEY (`id_stockexchange`) REFERENCES `stockexchange` (`id_stockexchange`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `hold_cashaccount_balance`
--

DROP TABLE IF EXISTS `hold_cashaccount_balance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `hold_cashaccount_balance` (
  `id_securitycash_account` int(11) NOT NULL,
  `from_hold_date` date NOT NULL,
  `to_hold_date` date DEFAULT NULL,
  `withdrawl_deposit` double DEFAULT NULL,
  `interest_cashaccount` double DEFAULT NULL,
  `fee` double DEFAULT NULL,
  `accumulate_reduce` double DEFAULT NULL,
  `dividend` double DEFAULT NULL,
  `balance` double NOT NULL,
  `id_currency_pair_tenant` int(11) DEFAULT NULL,
  `id_currency_pair_portfolio` int(11) DEFAULT NULL,
  `id_tenant` int(11) NOT NULL,
  `id_portfolio` int(11) NOT NULL,
  `valid_timestamp` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id_securitycash_account`,`from_hold_date`),
  KEY `idTenantIdPortfolioHCS` (`id_tenant`,`id_portfolio`),
  KEY `idTenant_validtimestamp` (`id_tenant`,`valid_timestamp`),
  CONSTRAINT `FK_HoldCashaccountBalance_Cashaccount` FOREIGN KEY (`id_securitycash_account`) REFERENCES `cashaccount` (`id_securitycash_account`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `hold_cashaccount_deposit`
--

DROP TABLE IF EXISTS `hold_cashaccount_deposit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `hold_cashaccount_deposit` (
  `id_securitycash_account` int(11) NOT NULL,
  `from_hold_date` date NOT NULL,
  `to_hold_date` date DEFAULT NULL,
  `deposit` double NOT NULL,
  `deposit_portfolio_currency` double NOT NULL,
  `deposit_tenant_currency` double NOT NULL,
  `id_tenant` int(11) NOT NULL,
  `id_portfolio` int(11) NOT NULL,
  `valid_timestamp` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id_securitycash_account`,`from_hold_date`),
  KEY `fromHoldToHoldDate` (`from_hold_date`,`to_hold_date`),
  KEY `idTenantIdPortfolioHCD` (`id_tenant`,`id_portfolio`),
  CONSTRAINT `FK_HoldCashaccountDeposit_Cashaccount` FOREIGN KEY (`id_securitycash_account`) REFERENCES `cashaccount` (`id_securitycash_account`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `hold_securityaccount_security`
--

DROP TABLE IF EXISTS `hold_securityaccount_security`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `hold_securityaccount_security` (
  `id_securitycash_account` int(11) NOT NULL,
  `id_securitycurrency` int(11) NOT NULL,
  `from_hold_date` date NOT NULL,
  `to_hold_date` date DEFAULT NULL,
  `holdings` double NOT NULL,
  `margin_real_holdings` double DEFAULT NULL,
  `margin_average_price` double DEFAULT NULL,
  `split_price_factor` double NOT NULL,
  `id_currency_pair_tenant` int(11) DEFAULT NULL,
  `id_currency_pair_portfolio` int(11) DEFAULT NULL,
  `id_tenant` int(11) NOT NULL,
  `id_portfolio` int(11) NOT NULL,
  `valid_timestamp` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id_securitycash_account`,`id_securitycurrency`,`from_hold_date`),
  UNIQUE KEY `idTenantidSecurtyFromHoldDate` (`id_tenant`,`id_securitycash_account`,`id_securitycurrency`,`from_hold_date`) USING BTREE,
  KEY `idTenantIdPortfolioHSS` (`id_tenant`,`id_portfolio`),
  CONSTRAINT `FK_HoldSecurityaccountSecurity_SecurityAccount` FOREIGN KEY (`id_securitycash_account`) REFERENCES `securityaccount` (`id_securitycash_account`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ictax_payment`
--

DROP TABLE IF EXISTS `ictax_payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ictax_payment` (
  `id_ictax_payment` int(11) NOT NULL AUTO_INCREMENT,
  `id_ictax_data` int(11) NOT NULL,
  `payment_date` date DEFAULT NULL,
  `ex_date` date DEFAULT NULL,
  `currency` varchar(3) DEFAULT NULL,
  `payment_value` double DEFAULT NULL,
  `exchange_rate` double DEFAULT NULL,
  `payment_value_chf` double DEFAULT NULL,
  `capital_gain` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`id_ictax_payment`),
  KEY `fk_ictax_payment_data` (`id_ictax_data`),
  CONSTRAINT `fk_ictax_payment_data` FOREIGN KEY (`id_ictax_data`) REFERENCES `ictax_security_tax_data` (`id_ictax_data`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=8730 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ictax_security_tax_data`
--

DROP TABLE IF EXISTS `ictax_security_tax_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ictax_security_tax_data` (
  `id_ictax_data` int(11) NOT NULL AUTO_INCREMENT,
  `id_tax_upload` int(11) NOT NULL,
  `isin` varchar(12) NOT NULL,
  `valor_number` int(11) DEFAULT NULL,
  `tax_value_chf` double DEFAULT NULL,
  `quotation_type` varchar(10) DEFAULT NULL,
  `security_group` varchar(20) DEFAULT NULL,
  `institution_name` varchar(200) DEFAULT NULL,
  `country` varchar(5) DEFAULT NULL,
  `currency` varchar(3) DEFAULT NULL,
  PRIMARY KEY (`id_ictax_data`),
  KEY `fk_ictax_data_upload` (`id_tax_upload`),
  KEY `idx_ictax_data_isin` (`isin`),
  CONSTRAINT `fk_ictax_data_upload` FOREIGN KEY (`id_tax_upload`) REFERENCES `tax_upload` (`id_tax_upload`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3735 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `imp_trans_head`
--

DROP TABLE IF EXISTS `imp_trans_head`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `imp_trans_head` (
  `id_trans_head` int(11) NOT NULL AUTO_INCREMENT,
  `id_tenant` int(11) NOT NULL,
  `id_securitycash_account` int(11) NOT NULL,
  `name` varchar(40) NOT NULL,
  `note` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id_trans_head`),
  UNIQUE KEY `UNIQUE_ImpTransHead` (`id_tenant`,`id_securitycash_account`,`name`) USING BTREE,
  KEY `FK_ImpTransHead_Securityaccount` (`id_securitycash_account`),
  CONSTRAINT `FK_ImpTransHead_Securityaccount` FOREIGN KEY (`id_securitycash_account`) REFERENCES `securityaccount` (`id_securitycash_account`),
  CONSTRAINT `FK_ImpTransHead_Tenant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`)
) ENGINE=InnoDB AUTO_INCREMENT=972 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `imp_trans_platform`
--

DROP TABLE IF EXISTS `imp_trans_platform`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `imp_trans_platform` (
  `id_trans_imp_platform` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL,
  `id_csv_imp_impl` varchar(32) DEFAULT NULL,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id_trans_imp_platform`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `imp_trans_pos`
--

DROP TABLE IF EXISTS `imp_trans_pos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `imp_trans_pos` (
  `id_trans_pos` int(11) NOT NULL AUTO_INCREMENT,
  `id_trans_head` int(11) NOT NULL,
  `transaction_time` timestamp NULL DEFAULT NULL,
  `ex_date` date DEFAULT NULL,
  `transaction_type` smallint(6) DEFAULT NULL,
  `transaction_type_imp` varchar(20) DEFAULT NULL,
  `id_cash_account` int(11) DEFAULT NULL,
  `id_tenant` int(11) NOT NULL,
  `currency_account` char(3) DEFAULT NULL,
  `cash_account_imp` varchar(32) DEFAULT NULL,
  `currency_security` char(3) DEFAULT NULL,
  `isin` varchar(12) DEFAULT NULL,
  `symbol_imp` varchar(20) DEFAULT NULL,
  `security_name_imp` varchar(80) DEFAULT NULL,
  `id_securitycurrency` int(11) DEFAULT NULL,
  `currency_ex_rate` double(22,10) DEFAULT NULL,
  `units` double DEFAULT NULL,
  `quotation` double(22,8) DEFAULT NULL,
  `tax_cost` double(22,8) DEFAULT NULL,
  `transaction_cost` double(22,8) DEFAULT NULL,
  `currency_cost` char(3) DEFAULT NULL,
  `cashaccount_amount` double(22,8) DEFAULT NULL,
  `accepted_total_diff` double DEFAULT NULL,
  `accrued_interest` double(22,8) DEFAULT NULL,
  `field1_string_imp` varchar(20) DEFAULT NULL,
  `ready_for_transaction` tinyint(1) NOT NULL DEFAULT 0,
  `id_transaction` int(11) DEFAULT NULL,
  `id_transaction_maybe` int(11) DEFAULT NULL,
  `id_trans_imp_template` int(11) DEFAULT NULL,
  `id_file_part` int(11) DEFAULT NULL,
  `file_name_original` varchar(255) DEFAULT NULL,
  `con_id_trans_pos` int(11) DEFAULT NULL,
  `known_other_flags` int(11) DEFAULT NULL,
  `transaction_error` varchar(1024) DEFAULT NULL,
  PRIMARY KEY (`id_trans_pos`),
  UNIQUE KEY `Unique_idTransaction` (`id_transaction`),
  KEY `FK_ImpTransPos_ImpTransHead` (`id_trans_head`),
  KEY `FK_ImpTransPos_Cashaccount` (`id_cash_account`) USING BTREE,
  CONSTRAINT `FK_ImpTransPos_Cashaccount1` FOREIGN KEY (`id_cash_account`) REFERENCES `cashaccount` (`id_securitycash_account`),
  CONSTRAINT `FK_ImpTransPos_ImpTransHead` FOREIGN KEY (`id_trans_head`) REFERENCES `imp_trans_head` (`id_trans_head`),
  CONSTRAINT `transacton_maybe` CHECK (`id_transaction_maybe` is null or `id_transaction_maybe` is not null and `id_transaction` is null)
) ENGINE=InnoDB AUTO_INCREMENT=37299 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `imp_trans_pos_failed`
--

DROP TABLE IF EXISTS `imp_trans_pos_failed`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `imp_trans_pos_failed` (
  `id_imp_trans_pos_failed` int(11) NOT NULL AUTO_INCREMENT,
  `id_trans_imp_template` int(11) NOT NULL,
  `last_matching_property` varchar(12) DEFAULT NULL,
  `id_trans_pos` int(11) NOT NULL,
  `error_message` varchar(120) DEFAULT NULL,
  PRIMARY KEY (`id_imp_trans_pos_failed`),
  KEY `Fk_ImpTransPosFailed_ImpTransPos` (`id_trans_pos`),
  CONSTRAINT `Fk_ImpTransPosFailed_ImpTransPos` FOREIGN KEY (`id_trans_pos`) REFERENCES `imp_trans_pos` (`id_trans_pos`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=22003 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `imp_trans_template`
--

DROP TABLE IF EXISTS `imp_trans_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `imp_trans_template` (
  `id_trans_imp_template` int(11) NOT NULL AUTO_INCREMENT,
  `id_trans_imp_platform` int(11) NOT NULL,
  `template_format_type` tinyint(2) NOT NULL,
  `template_purpose` varchar(50) NOT NULL,
  `template_category` tinyint(2) NOT NULL DEFAULT 0,
  `template_as_txt` varchar(4096) NOT NULL,
  `valid_since` date NOT NULL,
  `template_language` varchar(5) NOT NULL,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id_trans_imp_template`),
  UNIQUE KEY `UNIQUE_imp_template` (`id_trans_imp_platform`,`template_format_type`,`template_category`,`template_language`,`valid_since`),
  CONSTRAINT `FK_TradingImpTemplate_TradingImpPlatform` FOREIGN KEY (`id_trans_imp_platform`) REFERENCES `imp_trans_platform` (`id_trans_imp_platform`)
) ENGINE=InnoDB AUTO_INCREMENT=94 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mail_entity`
--

DROP TABLE IF EXISTS `mail_entity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mail_entity` (
  `id_mail_entity` int(11) NOT NULL AUTO_INCREMENT,
  `id_mail_send_recv` int(11) DEFAULT NULL,
  `message_com_type` tinyint(4) NOT NULL,
  `id_entity` int(11) NOT NULL,
  `mark_date` date NOT NULL,
  `creation_date` date NOT NULL,
  PRIMARY KEY (`id_mail_entity`)
) ENGINE=InnoDB AUTO_INCREMENT=577 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mail_send_recv`
--

DROP TABLE IF EXISTS `mail_send_recv`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mail_send_recv` (
  `id_mail_send_recv` int(11) NOT NULL AUTO_INCREMENT,
  `send_recv` char(1) NOT NULL,
  `id_user_from` int(11) NOT NULL,
  `id_user_to` int(11) DEFAULT NULL,
  `id_role_to` int(11) DEFAULT NULL,
  `id_reply_to_remote` int(11) DEFAULT NULL,
  `id_reply_to_local` int(11) DEFAULT NULL,
  `reply_to_role_private` tinyint(1) NOT NULL DEFAULT 0,
  `subject` varchar(96) NOT NULL,
  `message` varchar(4096) NOT NULL,
  `send_recv_time` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id_mail_send_recv`),
  KEY `FK_MailInOut_Role` (`id_role_to`),
  KEY `id_reply_to_local` (`id_reply_to_local`),
  CONSTRAINT `FK_MailSendRecv_Role` FOREIGN KEY (`id_role_to`) REFERENCES `role` (`id_role`)
) ENGINE=InnoDB AUTO_INCREMENT=929 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mail_send_recv_read_del`
--

DROP TABLE IF EXISTS `mail_send_recv_read_del`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mail_send_recv_read_del` (
  `id_mail_send_recv` int(11) NOT NULL,
  `id_user` int(11) NOT NULL,
  `has_been_read` tinyint(1) NOT NULL DEFAULT 0,
  `mark_hide_del` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_mail_send_recv`,`id_user`),
  KEY `FK_MailInBoxRead_User` (`id_user`),
  CONSTRAINT `FK_MailSendRecvReadDel_MailSendRecv` FOREIGN KEY (`id_mail_send_recv`) REFERENCES `mail_send_recv` (`id_mail_send_recv`) ON DELETE CASCADE,
  CONSTRAINT `FK_MailSendRecvReadDel_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mail_setting_forward`
--

DROP TABLE IF EXISTS `mail_setting_forward`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mail_setting_forward` (
  `id_mail_setting_forward` int(11) NOT NULL AUTO_INCREMENT,
  `id_user` int(11) NOT NULL,
  `message_com_type` tinyint(4) NOT NULL,
  `message_target_type` tinyint(4) NOT NULL DEFAULT 0,
  `id_user_redirect` int(11) DEFAULT NULL,
  PRIMARY KEY (`id_mail_setting_forward`),
  KEY `FK_MailSettingForward_User` (`id_user`),
  KEY `FK_MailSettingForwardRedirect_User` (`id_user_redirect`),
  CONSTRAINT `FK_MailSettingForwardRedirect_User` FOREIGN KEY (`id_user_redirect`) REFERENCES `user` (`id_user`),
  CONSTRAINT `FK_MailSettingForward_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mic_provider_map`
--

DROP TABLE IF EXISTS `mic_provider_map`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mic_provider_map` (
  `id_provider` varchar(15) NOT NULL,
  `mic` char(4) NOT NULL,
  `code_provider` varchar(5) NOT NULL,
  `symbol_suffix` char(4) DEFAULT NULL,
  PRIMARY KEY (`id_provider`,`mic`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `multilinguestring`
--

DROP TABLE IF EXISTS `multilinguestring`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `multilinguestring` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=826 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `multilinguestrings`
--

DROP TABLE IF EXISTS `multilinguestrings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `multilinguestrings` (
  `id_string` int(11) NOT NULL,
  `text` varchar(2000) NOT NULL,
  `language` varchar(2) NOT NULL,
  PRIMARY KEY (`id_string`,`language`),
  CONSTRAINT `FK_Multilinguestrings_Multilinguestring` FOREIGN KEY (`id_string`) REFERENCES `multilinguestring` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `portfolio`
--

DROP TABLE IF EXISTS `portfolio`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `portfolio` (
  `id_portfolio` int(11) NOT NULL AUTO_INCREMENT,
  `id_tenant` int(11) NOT NULL,
  `name` varchar(25) NOT NULL,
  `currency` char(3) NOT NULL,
  `closed_until` date DEFAULT NULL,
  PRIMARY KEY (`id_portfolio`),
  UNIQUE KEY `idtenant_name` (`id_tenant`,`name`) USING BTREE,
  KEY `FK_Portfolio_Tentant` (`id_tenant`),
  CONSTRAINT `FK_Portfolio_Tentant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`)
) ENGINE=InnoDB AUTO_INCREMENT=18942 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `propose_change_entity`
--

DROP TABLE IF EXISTS `propose_change_entity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `propose_change_entity` (
  `id_propose_request` int(11) NOT NULL,
  `id_entity` int(11) NOT NULL,
  `id_owner_entity` int(11) NOT NULL,
  PRIMARY KEY (`id_propose_request`),
  CONSTRAINT `FK_ProposeChangeEntity_ProposeRequest` FOREIGN KEY (`id_propose_request`) REFERENCES `propose_request` (`id_propose_request`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `propose_change_field`
--

DROP TABLE IF EXISTS `propose_change_field`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `propose_change_field` (
  `id_propose_field` int(11) NOT NULL AUTO_INCREMENT,
  `id_propose_request` int(11) NOT NULL,
  `field` varchar(40) NOT NULL,
  `value` blob NOT NULL,
  PRIMARY KEY (`id_propose_field`),
  KEY `FK_ProposeChangeField_ProposeRequest` (`id_propose_request`),
  CONSTRAINT `FK_ProposeChangeField_ProposeRequest` FOREIGN KEY (`id_propose_request`) REFERENCES `propose_request` (`id_propose_request`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=212 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `propose_request`
--

DROP TABLE IF EXISTS `propose_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `propose_request` (
  `id_propose_request` int(11) NOT NULL AUTO_INCREMENT,
  `dtype` varchar(1) NOT NULL,
  `entity` varchar(40) NOT NULL,
  `data_change_state` smallint(6) NOT NULL,
  `note_request` varchar(1000) DEFAULT NULL,
  `note_accept_reject` varchar(1000) DEFAULT NULL,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id_propose_request`)
) ENGINE=InnoDB AUTO_INCREMENT=124 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `propose_user_task`
--

DROP TABLE IF EXISTS `propose_user_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `propose_user_task` (
  `id_propose_request` int(11) NOT NULL,
  `id_target_user` int(11) NOT NULL,
  `id_role_to` int(11) NOT NULL,
  `user_task_type` smallint(6) NOT NULL,
  PRIMARY KEY (`id_propose_request`),
  KEY `FK_ProposeUserTask_User` (`id_target_user`),
  CONSTRAINT `FK_ProposeUserTask_ProposeRequest` FOREIGN KEY (`id_propose_request`) REFERENCES `propose_request` (`id_propose_request`) ON DELETE CASCADE,
  CONSTRAINT `FK_ProposeUserTask_User` FOREIGN KEY (`id_target_user`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `release_note`
--

DROP TABLE IF EXISTS `release_note`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `release_note` (
  `id_release_note` int(11) NOT NULL AUTO_INCREMENT,
  `version` varchar(12) NOT NULL,
  `language` char(2) NOT NULL,
  `note` varchar(1024) NOT NULL,
  PRIMARY KEY (`id_release_note`),
  UNIQUE KEY `uk_version_language` (`version`,`language`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `role`
--

DROP TABLE IF EXISTS `role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `role` (
  `id_role` int(11) NOT NULL AUTO_INCREMENT,
  `rolename` varchar(50) NOT NULL,
  PRIMARY KEY (`id_role`),
  UNIQUE KEY `rolename` (`id_role`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `security`
--

DROP TABLE IF EXISTS `security`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `security` (
  `id_securitycurrency` int(11) NOT NULL,
  `name` varchar(80) NOT NULL,
  `isin` varchar(12) DEFAULT NULL,
  `ticker_symbol` varchar(6) DEFAULT NULL,
  `currency` char(3) NOT NULL,
  `active_from_date` date DEFAULT NULL,
  `active_to_date` date NOT NULL,
  `dist_frequency` tinyint(4) NOT NULL,
  `denomination` int(11) DEFAULT NULL,
  `leverage_factor` float(2,1) NOT NULL DEFAULT 1.0,
  `id_stockexchange` int(11) NOT NULL,
  `id_asset_class` int(11) NOT NULL,
  `product_link` varchar(254) DEFAULT NULL,
  `s_volume` bigint(20) DEFAULT NULL,
  `id_tenant_private` int(11) DEFAULT NULL,
  `id_link_securitycurrency` int(11) DEFAULT NULL,
  `formula_prices` varchar(255) DEFAULT NULL,
  `id_connector_dividend` varchar(35) DEFAULT NULL,
  `url_dividend_extend` varchar(254) DEFAULT NULL,
  `dividend_currency` char(3) DEFAULT NULL,
  `div_earliest_next_check` timestamp NULL DEFAULT NULL,
  `retry_dividend_load` smallint(6) NOT NULL DEFAULT 0,
  `id_connector_split` varchar(35) DEFAULT NULL,
  `url_split_extend` varchar(254) DEFAULT NULL,
  `retry_split_load` smallint(6) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_securitycurrency`),
  UNIQUE KEY `Unique_ISIN_currency` (`isin`,`currency`),
  KEY `FK_Security_StockExchange` (`id_stockexchange`),
  KEY `FK_Security_AssetClass` (`id_asset_class`),
  KEY `I_Security_Active_until_date` (`active_to_date`),
  KEY `FK_Security_Link_Securitycurrency` (`id_link_securitycurrency`),
  CONSTRAINT `FK_Security_AssetClass` FOREIGN KEY (`id_asset_class`) REFERENCES `assetclass` (`id_asset_class`),
  CONSTRAINT `FK_Security_Link_Securitycurrency` FOREIGN KEY (`id_link_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`),
  CONSTRAINT `FK_Security_SecurityCurrency` FOREIGN KEY (`id_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`),
  CONSTRAINT `FK_Security_StockExchange` FOREIGN KEY (`id_stockexchange`) REFERENCES `stockexchange` (`id_stockexchange`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_AUTO_VALUE_ON_ZERO' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `security_ins` BEFORE INSERT ON `security`
 FOR EACH ROW SET NEW.ticker_symbol = UPPER(NEW.ticker_symbol) */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_AUTO_VALUE_ON_ZERO' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `security_upd` BEFORE UPDATE ON `security`
 FOR EACH ROW SET NEW.ticker_symbol = UPPER(NEW.ticker_symbol) */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `security_action`
--

DROP TABLE IF EXISTS `security_action`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `security_action` (
  `id_security_action` int(11) NOT NULL AUTO_INCREMENT,
  `id_security_old` int(11) NOT NULL,
  `id_security_new` int(11) DEFAULT NULL,
  `isin_old` varchar(12) NOT NULL,
  `isin_new` varchar(12) NOT NULL,
  `action_date` date NOT NULL,
  `note` varchar(1024) DEFAULT NULL,
  `affected_count` int(11) NOT NULL DEFAULT 0,
  `applied_count` int(11) NOT NULL DEFAULT 0,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `from_factor` int(11) DEFAULT NULL,
  `to_factor` int(11) DEFAULT NULL,
  PRIMARY KEY (`id_security_action`),
  KEY `fk_sa_security_old` (`id_security_old`),
  KEY `fk_sa_security_new` (`id_security_new`),
  KEY `fk_sa_created_by` (`created_by`),
  CONSTRAINT `fk_sa_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id_user`),
  CONSTRAINT `fk_sa_security_new` FOREIGN KEY (`id_security_new`) REFERENCES `securitycurrency` (`id_securitycurrency`),
  CONSTRAINT `fk_sa_security_old` FOREIGN KEY (`id_security_old`) REFERENCES `securitycurrency` (`id_securitycurrency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `security_action_application`
--

DROP TABLE IF EXISTS `security_action_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `security_action_application` (
  `id_security_action_app` int(11) NOT NULL AUTO_INCREMENT,
  `id_security_action` int(11) NOT NULL,
  `id_tenant` int(11) NOT NULL,
  `id_transaction_sell` int(11) DEFAULT NULL,
  `id_transaction_buy` int(11) DEFAULT NULL,
  `applied_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `is_reversed` tinyint(4) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_security_action_app`),
  UNIQUE KEY `uq_saa_action_tenant` (`id_security_action`,`id_tenant`),
  KEY `fk_saa_tenant` (`id_tenant`),
  KEY `fk_saa_transaction_sell` (`id_transaction_sell`),
  KEY `fk_saa_transaction_buy` (`id_transaction_buy`),
  CONSTRAINT `fk_saa_security_action` FOREIGN KEY (`id_security_action`) REFERENCES `security_action` (`id_security_action`),
  CONSTRAINT `fk_saa_tenant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`),
  CONSTRAINT `fk_saa_transaction_buy` FOREIGN KEY (`id_transaction_buy`) REFERENCES `transaction` (`id_transaction`),
  CONSTRAINT `fk_saa_transaction_sell` FOREIGN KEY (`id_transaction_sell`) REFERENCES `transaction` (`id_transaction`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `security_derived_link`
--

DROP TABLE IF EXISTS `security_derived_link`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `security_derived_link` (
  `id_securitycurrency` int(11) NOT NULL,
  `var_name` char(1) NOT NULL,
  `id_link_securitycurrency` int(11) NOT NULL,
  PRIMARY KEY (`id_securitycurrency`,`var_name`) USING BTREE,
  UNIQUE KEY `idSecurityToLink` (`id_securitycurrency`,`id_link_securitycurrency`),
  KEY `IN_id_securitycurrency_link` (`id_link_securitycurrency`),
  CONSTRAINT `FK_SecurityDerivedLink_Security` FOREIGN KEY (`id_securitycurrency`) REFERENCES `security` (`id_securitycurrency`) ON DELETE CASCADE,
  CONSTRAINT `FK_SecurityDerivedLink_Securitycurrency` FOREIGN KEY (`id_link_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `security_transfer`
--

DROP TABLE IF EXISTS `security_transfer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `security_transfer` (
  `id_security_transfer` int(11) NOT NULL AUTO_INCREMENT,
  `id_tenant` int(11) NOT NULL,
  `id_security` int(11) NOT NULL,
  `id_securityaccount_source` int(11) NOT NULL,
  `id_securityaccount_target` int(11) NOT NULL,
  `transfer_date` date NOT NULL,
  `units` double NOT NULL,
  `quotation` double NOT NULL,
  `id_transaction_sell` int(11) DEFAULT NULL,
  `id_transaction_buy` int(11) DEFAULT NULL,
  `note` varchar(1024) DEFAULT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id_security_transfer`),
  KEY `fk_st_tenant` (`id_tenant`),
  KEY `fk_st_security` (`id_security`),
  KEY `fk_st_source` (`id_securityaccount_source`),
  KEY `fk_st_target` (`id_securityaccount_target`),
  KEY `fk_st_transaction_sell` (`id_transaction_sell`),
  KEY `fk_st_transaction_buy` (`id_transaction_buy`),
  CONSTRAINT `fk_st_security` FOREIGN KEY (`id_security`) REFERENCES `securitycurrency` (`id_securitycurrency`),
  CONSTRAINT `fk_st_source` FOREIGN KEY (`id_securityaccount_source`) REFERENCES `securitycashaccount` (`id_securitycash_account`),
  CONSTRAINT `fk_st_target` FOREIGN KEY (`id_securityaccount_target`) REFERENCES `securitycashaccount` (`id_securitycash_account`),
  CONSTRAINT `fk_st_tenant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`),
  CONSTRAINT `fk_st_transaction_buy` FOREIGN KEY (`id_transaction_buy`) REFERENCES `transaction` (`id_transaction`),
  CONSTRAINT `fk_st_transaction_sell` FOREIGN KEY (`id_transaction_sell`) REFERENCES `transaction` (`id_transaction`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `securityaccount`
--

DROP TABLE IF EXISTS `securityaccount`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `securityaccount` (
  `id_securitycash_account` int(11) NOT NULL,
  `id_trading_platform_plan` int(11) NOT NULL,
  `weka_model` longblob DEFAULT NULL,
  `lowest_transaction_cost` float(6,2) NOT NULL,
  `fee_model_yaml` text DEFAULT NULL,
  PRIMARY KEY (`id_securitycash_account`),
  KEY `FK_Securityaccount_Tradingplatformplan` (`id_trading_platform_plan`),
  CONSTRAINT `FK_SecurityAccount_SecurityCashAccount` FOREIGN KEY (`id_securitycash_account`) REFERENCES `securitycashaccount` (`id_securitycash_account`) ON DELETE CASCADE,
  CONSTRAINT `FK_Securityaccount_Tradingplatformplan` FOREIGN KEY (`id_trading_platform_plan`) REFERENCES `trading_platform_plan` (`id_trading_platform_plan`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `securityaccount_trading_period`
--

DROP TABLE IF EXISTS `securityaccount_trading_period`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `securityaccount_trading_period` (
  `id_secaccount_trading_period` int(11) NOT NULL AUTO_INCREMENT,
  `id_securitycash_account` int(11) NOT NULL,
  `category_type` tinyint(4) DEFAULT NULL,
  `spec_invest_instrument` tinyint(4) NOT NULL,
  `date_from` date NOT NULL DEFAULT '2000-01-01',
  `date_to` date DEFAULT NULL,
  PRIMARY KEY (`id_secaccount_trading_period`),
  KEY `idx_stp_secaccount` (`id_securitycash_account`),
  CONSTRAINT `fk_stp_securitycash_account` FOREIGN KEY (`id_securitycash_account`) REFERENCES `securitycashaccount` (`id_securitycash_account`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1201 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `securitycashaccount`
--

DROP TABLE IF EXISTS `securitycashaccount`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `securitycashaccount` (
  `id_securitycash_account` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(25) NOT NULL,
  `id_portfolio` int(11) NOT NULL,
  `note` varchar(1000) DEFAULT NULL,
  `dtype` varchar(1) NOT NULL,
  `id_tenant` int(11) NOT NULL,
  `active_to_date` date DEFAULT NULL,
  PRIMARY KEY (`id_securitycash_account`),
  UNIQUE KEY `idPortfolio_dType_name` (`id_portfolio`,`dtype`,`name`) USING BTREE,
  KEY `FK_SecurityAccount_Portfolio` (`id_portfolio`),
  CONSTRAINT `FK_SecurityAccount_Portfolio` FOREIGN KEY (`id_portfolio`) REFERENCES `portfolio` (`id_portfolio`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=75606 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `securitycurrency`
--

DROP TABLE IF EXISTS `securitycurrency`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `securitycurrency` (
  `id_securitycurrency` int(11) NOT NULL AUTO_INCREMENT,
  `dtype` varchar(1) NOT NULL,
  `full_load_timestamp` timestamp NULL DEFAULT NULL,
  `id_connector_history` varchar(35) DEFAULT NULL,
  `url_history_extend` varchar(254) DEFAULT NULL,
  `retry_history_load` smallint(6) NOT NULL DEFAULT 0,
  `id_connector_intra` varchar(35) DEFAULT NULL,
  `url_intra_extend` varchar(254) DEFAULT NULL,
  `retry_intra_load` smallint(6) NOT NULL DEFAULT 0,
  `stockexchange_link` varchar(254) DEFAULT NULL,
  `note` varchar(1000) DEFAULT NULL,
  `s_timestamp` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `s_prev_close` double(22,8) DEFAULT NULL,
  `s_change_percentage` double(22,8) DEFAULT NULL,
  `s_open` double(18,8) DEFAULT NULL,
  `s_last` double(22,8) DEFAULT NULL,
  `s_low` double(22,8) DEFAULT NULL,
  `s_high` double(22,8) DEFAULT NULL,
  `gt_net_lastprice_recv` tinyint(1) NOT NULL DEFAULT 0,
  `gt_net_historical_recv` tinyint(1) NOT NULL DEFAULT 0,
  `gt_net_lastprice_send` tinyint(1) NOT NULL,
  `gt_net_historical_send` tinyint(1) NOT NULL,
  `gt_net_last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id_securitycurrency`)
) ENGINE=InnoDB AUTO_INCREMENT=4276 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `securitysplit`
--

DROP TABLE IF EXISTS `securitysplit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `securitysplit` (
  `id_securitysplit` int(11) NOT NULL AUTO_INCREMENT,
  `id_securitycurrency` int(11) NOT NULL,
  `split_date` date NOT NULL,
  `from_factor` int(11) NOT NULL,
  `to_factor` int(11) NOT NULL,
  `create_type` tinyint(1) NOT NULL,
  `create_modify_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id_securitysplit`),
  KEY `FK_Securitysplit_Security` (`id_securitycurrency`),
  CONSTRAINT `FK_Securitysplit_Security` FOREIGN KEY (`id_securitycurrency`) REFERENCES `security` (`id_securitycurrency`)
) ENGINE=InnoDB AUTO_INCREMENT=907 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `standing_order`
--

DROP TABLE IF EXISTS `standing_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `standing_order` (
  `id_standing_order` int(11) NOT NULL AUTO_INCREMENT,
  `dtype` varchar(1) NOT NULL,
  `id_tenant` int(11) NOT NULL,
  `transaction_type` tinyint(4) NOT NULL,
  `id_cash_account` int(11) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `repeat_unit` tinyint(4) NOT NULL,
  `repeat_interval` smallint(6) NOT NULL DEFAULT 1,
  `day_of_execution` tinyint(4) DEFAULT NULL,
  `month_of_execution` tinyint(4) DEFAULT NULL,
  `period_day_position` tinyint(4) NOT NULL DEFAULT 0,
  `weekend_adjust` tinyint(4) NOT NULL DEFAULT 0,
  `valid_from` date NOT NULL,
  `valid_to` date NOT NULL,
  `last_execution_date` date DEFAULT NULL,
  `next_execution_date` date DEFAULT NULL,
  `transaction_cost` double DEFAULT NULL,
  PRIMARY KEY (`id_standing_order`),
  KEY `fk_so_tenant` (`id_tenant`),
  KEY `fk_so_cashaccount` (`id_cash_account`),
  CONSTRAINT `fk_so_cashaccount` FOREIGN KEY (`id_cash_account`) REFERENCES `securitycashaccount` (`id_securitycash_account`),
  CONSTRAINT `fk_so_tenant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `standing_order_cashaccount`
--

DROP TABLE IF EXISTS `standing_order_cashaccount`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `standing_order_cashaccount` (
  `id_standing_order` int(11) NOT NULL,
  `cashaccount_amount` double NOT NULL,
  PRIMARY KEY (`id_standing_order`),
  CONSTRAINT `fk_soc_parent` FOREIGN KEY (`id_standing_order`) REFERENCES `standing_order` (`id_standing_order`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `standing_order_failure`
--

DROP TABLE IF EXISTS `standing_order_failure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `standing_order_failure` (
  `id_standing_order_failure` int(11) NOT NULL AUTO_INCREMENT,
  `id_standing_order` int(11) NOT NULL,
  `execution_date` date NOT NULL,
  `business_error` varchar(2000) DEFAULT NULL,
  `unexpected_error` varchar(4096) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id_standing_order_failure`),
  KEY `idx_sof_standing_order` (`id_standing_order`),
  CONSTRAINT `fk_sof_standing_order` FOREIGN KEY (`id_standing_order`) REFERENCES `standing_order` (`id_standing_order`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `standing_order_security`
--

DROP TABLE IF EXISTS `standing_order_security`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `standing_order_security` (
  `id_standing_order` int(11) NOT NULL,
  `id_securitycurrency` int(11) NOT NULL,
  `id_security_account` int(11) NOT NULL,
  `id_currency_pair` int(11) DEFAULT NULL,
  `units` double DEFAULT NULL,
  `invest_amount` double DEFAULT NULL,
  `amount_includes_costs` tinyint(4) NOT NULL DEFAULT 0,
  `fractional_units` tinyint(4) NOT NULL DEFAULT 1,
  `tax_cost_formula` varchar(200) DEFAULT NULL,
  `transaction_cost_formula` varchar(200) DEFAULT NULL,
  `tax_cost` double DEFAULT NULL,
  PRIMARY KEY (`id_standing_order`),
  KEY `fk_sos_security` (`id_securitycurrency`),
  KEY `fk_sos_securityaccount` (`id_security_account`),
  KEY `fk_sos_currencypair` (`id_currency_pair`),
  CONSTRAINT `fk_sos_currencypair` FOREIGN KEY (`id_currency_pair`) REFERENCES `currencypair` (`id_securitycurrency`),
  CONSTRAINT `fk_sos_parent` FOREIGN KEY (`id_standing_order`) REFERENCES `standing_order` (`id_standing_order`) ON DELETE CASCADE,
  CONSTRAINT `fk_sos_security` FOREIGN KEY (`id_securitycurrency`) REFERENCES `security` (`id_securitycurrency`),
  CONSTRAINT `fk_sos_securityaccount` FOREIGN KEY (`id_security_account`) REFERENCES `securitycashaccount` (`id_securitycash_account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stockexchange`
--

DROP TABLE IF EXISTS `stockexchange`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `stockexchange` (
  `id_stockexchange` int(11) NOT NULL AUTO_INCREMENT,
  `mic` char(4) DEFAULT NULL,
  `name` varchar(32) NOT NULL,
  `country_code` char(2) DEFAULT NULL,
  `secondary_market` tinyint(1) NOT NULL,
  `no_market_value` tinyint(1) NOT NULL,
  `time_open` time NOT NULL DEFAULT '09:00:00',
  `time_close` time NOT NULL,
  `time_zone` varchar(50) NOT NULL,
  `id_index_upd_calendar` int(11) DEFAULT NULL,
  `max_calendar_upd_date` date DEFAULT NULL,
  `last_direct_price_update` timestamp NOT NULL DEFAULT (current_timestamp() + interval -72 hour),
  `website` varchar(128) DEFAULT NULL,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id_stockexchange`),
  UNIQUE KEY `name` (`name`),
  UNIQUE KEY `stockexchange_mic` (`mic`)
) ENGINE=InnoDB AUTO_INCREMENT=280 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stockexchange_mic`
--

DROP TABLE IF EXISTS `stockexchange_mic`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `stockexchange_mic` (
  `mic` char(4) NOT NULL,
  `name` varchar(128) NOT NULL,
  `country_code` char(2) NOT NULL,
  `city` varchar(30) DEFAULT NULL,
  `website` varchar(128) DEFAULT NULL,
  `time_zone` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`mic`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `task_data_change`
--

DROP TABLE IF EXISTS `task_data_change`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `task_data_change` (
  `id_task_data_change` int(11) NOT NULL AUTO_INCREMENT,
  `id_task` tinyint(2) NOT NULL,
  `execution_priority` tinyint(3) NOT NULL,
  `entity` varchar(40) DEFAULT NULL,
  `id_entity` int(11) DEFAULT NULL,
  `earliest_start_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `exec_start_time` timestamp NULL DEFAULT NULL,
  `exec_end_time` timestamp NULL DEFAULT NULL,
  `old_value_varchar` varchar(30) DEFAULT NULL,
  `old_value_number` double DEFAULT NULL,
  `progress_state` tinyint(1) NOT NULL,
  `failed_message_code` varchar(40) DEFAULT NULL,
  `failed_stack_trace` varchar(4096) DEFAULT NULL,
  PRIMARY KEY (`id_task_data_change`)
) ENGINE=InnoDB AUTO_INCREMENT=19034 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tax_country`
--

DROP TABLE IF EXISTS `tax_country`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tax_country` (
  `id_tax_country` int(11) NOT NULL AUTO_INCREMENT,
  `country_code` varchar(2) NOT NULL,
  PRIMARY KEY (`id_tax_country`),
  UNIQUE KEY `uq_tax_country_code` (`country_code`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tax_security_year_config`
--

DROP TABLE IF EXISTS `tax_security_year_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tax_security_year_config` (
  `id_tenant` int(11) NOT NULL,
  `tax_year` smallint(6) NOT NULL,
  `id_securitycurrency` int(11) NOT NULL,
  PRIMARY KEY (`id_tenant`,`tax_year`,`id_securitycurrency`),
  KEY `fk_tsyc_security` (`id_securitycurrency`),
  CONSTRAINT `fk_tsyc_security` FOREIGN KEY (`id_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`) ON DELETE CASCADE,
  CONSTRAINT `fk_tsyc_tenant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tax_upload`
--

DROP TABLE IF EXISTS `tax_upload`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tax_upload` (
  `id_tax_upload` int(11) NOT NULL AUTO_INCREMENT,
  `id_tax_year` int(11) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_path` varchar(500) NOT NULL,
  `upload_date` timestamp NOT NULL DEFAULT current_timestamp(),
  `record_count` int(11) DEFAULT 0,
  PRIMARY KEY (`id_tax_upload`),
  KEY `fk_tax_upload_year` (`id_tax_year`),
  CONSTRAINT `fk_tax_upload_year` FOREIGN KEY (`id_tax_year`) REFERENCES `tax_year` (`id_tax_year`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tax_year`
--

DROP TABLE IF EXISTS `tax_year`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tax_year` (
  `id_tax_year` int(11) NOT NULL AUTO_INCREMENT,
  `id_tax_country` int(11) NOT NULL,
  `tax_year` smallint(6) NOT NULL,
  PRIMARY KEY (`id_tax_year`),
  UNIQUE KEY `uq_tax_country_year` (`id_tax_country`,`tax_year`),
  CONSTRAINT `fk_tax_year_country` FOREIGN KEY (`id_tax_country`) REFERENCES `tax_country` (`id_tax_country`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tenant`
--

DROP TABLE IF EXISTS `tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tenant` (
  `id_tenant` int(11) NOT NULL AUTO_INCREMENT,
  `tenant_name` varchar(25) NOT NULL,
  `create_id_user` int(11) NOT NULL,
  `tenant_kind_type` tinyint(4) NOT NULL,
  `currency` char(3) NOT NULL,
  `exclude_div_tax` tinyint(1) NOT NULL DEFAULT 0,
  `id_watchlist_performance` int(11) DEFAULT NULL,
  `closed_until` date DEFAULT NULL,
  `id_parent_tenant` int(11) DEFAULT NULL,
  `id_algo_top` int(11) DEFAULT NULL,
  `country` varchar(2) DEFAULT NULL,
  `tax_export_settings` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`tax_export_settings`)),
  PRIMARY KEY (`id_tenant`)
) ENGINE=InnoDB AUTO_INCREMENT=54 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `trading_days_minus`
--

DROP TABLE IF EXISTS `trading_days_minus`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `trading_days_minus` (
  `id_stockexchange` int(11) NOT NULL,
  `trading_date_minus` date NOT NULL,
  `create_type` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`trading_date_minus`,`id_stockexchange`),
  KEY `FK_TradingDayMinus_Stockexchange` (`id_stockexchange`),
  CONSTRAINT `FK_TradingDayMinus_Stockexchange` FOREIGN KEY (`id_stockexchange`) REFERENCES `stockexchange` (`id_stockexchange`) ON DELETE CASCADE,
  CONSTRAINT `FK_TradingDaysMinus_TradingDaysPlus` FOREIGN KEY (`trading_date_minus`) REFERENCES `trading_days_plus` (`trading_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `trading_days_plus`
--

DROP TABLE IF EXISTS `trading_days_plus`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `trading_days_plus` (
  `trading_date` date NOT NULL,
  PRIMARY KEY (`trading_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `trading_platform_plan`
--

DROP TABLE IF EXISTS `trading_platform_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `trading_platform_plan` (
  `id_trading_platform_plan` int(11) NOT NULL AUTO_INCREMENT,
  `platform_plan_name_nls` int(11) NOT NULL,
  `transaction_fee_plan` smallint(6) NOT NULL,
  `id_trans_imp_platform` int(11) DEFAULT NULL,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  `fee_model_yaml` text DEFAULT NULL,
  PRIMARY KEY (`id_trading_platform_plan`),
  UNIQUE KEY `platform_plan_name` (`platform_plan_name_nls`),
  KEY `FK_Tradingplatformplan_Multilinguestring` (`platform_plan_name_nls`),
  KEY `FK_Tradingplatform_ImpTransPlatform` (`id_trans_imp_platform`),
  CONSTRAINT `FK_Tradingplatform_ImpTransPlatform` FOREIGN KEY (`id_trans_imp_platform`) REFERENCES `imp_trans_platform` (`id_trans_imp_platform`),
  CONSTRAINT `FK_Tradingplatformplan_Multilinguestring` FOREIGN KEY (`platform_plan_name_nls`) REFERENCES `multilinguestring` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `transaction`
--

DROP TABLE IF EXISTS `transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `transaction` (
  `id_transaction` int(11) NOT NULL AUTO_INCREMENT,
  `id_cash_account` int(11) NOT NULL,
  `id_security_account` int(11) DEFAULT NULL,
  `id_securitycurrency` int(11) DEFAULT NULL,
  `id_tenant` int(11) NOT NULL,
  `con_id_transaction` int(11) DEFAULT NULL,
  `units` double DEFAULT NULL,
  `transaction_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `tt_date` date NOT NULL,
  `ex_date` date DEFAULT NULL,
  `quotation` double(22,8) DEFAULT NULL,
  `transaction_type` smallint(6) NOT NULL,
  `tax_cost` double(22,8) DEFAULT NULL,
  `taxable_interest` tinyint(1) DEFAULT NULL,
  `transaction_cost` double(22,8) DEFAULT NULL,
  `currency_ex_rate` double(20,10) DEFAULT NULL,
  `note` varchar(1000) DEFAULT NULL,
  `cashaccount_amount` double(22,8) NOT NULL COMMENT 'Amount which is added to the cash account',
  `id_currency_pair` int(11) DEFAULT NULL,
  `asset_investment_value_1` double(22,8) DEFAULT NULL COMMENT 'Used for accrued interest with Bonds and daily holding costs with CFD',
  `asset_investment_value_2` double(22,8) DEFAULT NULL COMMENT 'CFD holds the value per point',
  `id_standing_order` int(11) DEFAULT NULL,
  `id_security_action_app` int(11) DEFAULT NULL,
  `id_security_transfer` int(11) DEFAULT NULL,
  PRIMARY KEY (`id_transaction`),
  KEY `FK_Transaction_SecurityCurrency` (`id_securitycurrency`),
  KEY `FK_Transaction_securityAccount` (`id_security_account`),
  KEY `FK_Transaction_cashAccount` (`id_cash_account`),
  KEY `tenant_securitycurrency` (`id_tenant`,`id_securitycurrency`),
  KEY `tt_date` (`tt_date`),
  KEY `tenant_tt_date` (`id_tenant`,`tt_date`),
  KEY `idx_txn_standing_order` (`id_standing_order`),
  KEY `fk_trans_security_action_app` (`id_security_action_app`),
  KEY `fk_trans_security_transfer` (`id_security_transfer`),
  CONSTRAINT `FK_Transaction_CashAccount` FOREIGN KEY (`id_cash_account`) REFERENCES `cashaccount` (`id_securitycash_account`),
  CONSTRAINT `FK_Transaction_SecurityAccount` FOREIGN KEY (`id_security_account`) REFERENCES `securityaccount` (`id_securitycash_account`),
  CONSTRAINT `FK_Transaction_SecurityCurrency` FOREIGN KEY (`id_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`),
  CONSTRAINT `fk_trans_security_action_app` FOREIGN KEY (`id_security_action_app`) REFERENCES `security_action_application` (`id_security_action_app`),
  CONSTRAINT `fk_trans_security_transfer` FOREIGN KEY (`id_security_transfer`) REFERENCES `security_transfer` (`id_security_transfer`),
  CONSTRAINT `fk_txn_standing_order` FOREIGN KEY (`id_standing_order`) REFERENCES `standing_order` (`id_standing_order`),
  CONSTRAINT `c_currency_ex_rate` CHECK (`currency_ex_rate` is not null and `currency_ex_rate` > 0 and `id_currency_pair` is not null or `currency_ex_rate` is null and `id_currency_pair` is null),
  CONSTRAINT `s_units` CHECK (`units` is not null and `units` <> 0 and `id_securitycurrency` is not null or `id_securitycurrency` is null and `units` is null),
  CONSTRAINT `s_quotation` CHECK (`quotation` is not null and (`quotation` > 0 or `quotation` <> 0 and `transaction_type` between 6 and 7) and `id_securitycurrency` is not null or `quotation` is null and `id_securitycurrency` is null)
) ENGINE=InnoDB AUTO_INCREMENT=1163726 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `udf_data`
--

DROP TABLE IF EXISTS `udf_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `udf_data` (
  `id_user` int(11) NOT NULL,
  `entity` varchar(20) NOT NULL,
  `id_entity` int(11) NOT NULL,
  `json_values` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`json_values`)),
  PRIMARY KEY (`entity`,`id_entity`,`id_user`) USING BTREE,
  KEY `FK_udfData_user` (`id_user`),
  CONSTRAINT `FK_udfData_user` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `udf_metadata`
--

DROP TABLE IF EXISTS `udf_metadata`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `udf_metadata` (
  `id_udf_metadata` int(11) NOT NULL AUTO_INCREMENT,
  `id_user` int(11) DEFAULT NULL,
  `udf_special_type` tinyint(3) DEFAULT NULL,
  `description` varchar(24) NOT NULL,
  `description_help` varchar(80) DEFAULT NULL,
  `udf_data_type` tinyint(2) NOT NULL,
  `field_size` varchar(20) DEFAULT NULL,
  `ui_order` smallint(2) NOT NULL,
  PRIMARY KEY (`id_udf_metadata`),
  KEY `I_UDF_Metadata_IdUser` (`id_user`),
  CONSTRAINT `FK_udfMetadata_user` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB AUTO_INCREMENT=4499 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `udf_metadata_general`
--

DROP TABLE IF EXISTS `udf_metadata_general`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `udf_metadata_general` (
  `id_udf_metadata` int(11) NOT NULL,
  `entity` varchar(20) NOT NULL,
  PRIMARY KEY (`id_udf_metadata`),
  CONSTRAINT `FK_MetadataGeneral_Metadata` FOREIGN KEY (`id_udf_metadata`) REFERENCES `udf_metadata` (`id_udf_metadata`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `udf_metadata_security`
--

DROP TABLE IF EXISTS `udf_metadata_security`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `udf_metadata_security` (
  `id_udf_metadata` int(11) NOT NULL,
  `category_types` bigint(20) NOT NULL,
  `spec_invest_instruments` bigint(20) NOT NULL,
  PRIMARY KEY (`id_udf_metadata`),
  CONSTRAINT `FK_MetadataSecurity_Metadata` FOREIGN KEY (`id_udf_metadata`) REFERENCES `udf_metadata` (`id_udf_metadata`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `udf_special_type_disable_user`
--

DROP TABLE IF EXISTS `udf_special_type_disable_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `udf_special_type_disable_user` (
  `id_user` int(11) NOT NULL,
  `udf_special_type` tinyint(3) NOT NULL,
  PRIMARY KEY (`id_user`,`udf_special_type`),
  CONSTRAINT `FK_UdfSpecialTypeDisableUser_user` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user` (
  `id_user` int(11) NOT NULL AUTO_INCREMENT,
  `id_tenant` int(11) DEFAULT NULL,
  `nickname` varchar(30) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `locale` varchar(5) NOT NULL,
  `timezone_offset` int(11) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  `ui_show_my_property` tinyint(1) NOT NULL DEFAULT 1,
  `security_breach_count` smallint(6) NOT NULL DEFAULT 0,
  `limit_request_exceed_count` smallint(6) NOT NULL DEFAULT 0,
  `last_role_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id_user`),
  UNIQUE KEY `nickname` (`nickname`),
  UNIQUE KEY `email` (`email`) USING BTREE,
  KEY `FK_User_Tenant` (`id_tenant`),
  CONSTRAINT `FK_User_Tenant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`)
) ENGINE=InnoDB AUTO_INCREMENT=67 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_chart_shape`
--

DROP TABLE IF EXISTS `user_chart_shape`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_chart_shape` (
  `id_user` int(11) NOT NULL,
  `id_securitycurrency` int(11) NOT NULL,
  `shape_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`shape_data`)),
  PRIMARY KEY (`id_user`,`id_securitycurrency`),
  CONSTRAINT `fk_ucs_user` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_entity_change_count`
--

DROP TABLE IF EXISTS `user_entity_change_count`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_entity_change_count` (
  `id_user` int(11) NOT NULL,
  `date` date NOT NULL,
  `entity_name` varchar(25) NOT NULL,
  `count_insert` int(11) NOT NULL,
  `count_update` int(11) NOT NULL,
  `count_delete` int(11) NOT NULL,
  PRIMARY KEY (`id_user`,`date`,`entity_name`) USING BTREE,
  CONSTRAINT `FK_UserEntityChangeCount_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_entity_change_limit`
--

DROP TABLE IF EXISTS `user_entity_change_limit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_entity_change_limit` (
  `id_user_entity_change_limit` int(11) NOT NULL AUTO_INCREMENT,
  `id_user` int(11) NOT NULL,
  `entity_name` varchar(25) NOT NULL,
  `day_limit` int(11) NOT NULL,
  `until_date` date NOT NULL,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id_user_entity_change_limit`),
  UNIQUE KEY `Uecl_unique` (`id_user`,`entity_name`) USING BTREE,
  CONSTRAINT `FK_UserEntityChangeLimit_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_role`
--

DROP TABLE IF EXISTS `user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_role` (
  `id_user` int(11) NOT NULL,
  `id_role` int(11) NOT NULL,
  PRIMARY KEY (`id_user`,`id_role`),
  KEY `role_id` (`id_role`),
  CONSTRAINT `FK_User_Roles_Roles` FOREIGN KEY (`id_role`) REFERENCES `role` (`id_role`),
  CONSTRAINT `FK_User_Roles_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `verificationtoken`
--

DROP TABLE IF EXISTS `verificationtoken`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `verificationtoken` (
  `id_verificationtoken` int(11) NOT NULL AUTO_INCREMENT,
  `expiry_date` datetime DEFAULT NULL,
  `token` varchar(255) DEFAULT NULL,
  `id_user` int(11) NOT NULL,
  PRIMARY KEY (`id_verificationtoken`),
  KEY `FK_Verify_User` (`id_user`),
  CONSTRAINT `FK_VerificationToken_User` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `watchlist`
--

DROP TABLE IF EXISTS `watchlist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `watchlist` (
  `id_watchlist` int(11) NOT NULL AUTO_INCREMENT,
  `id_tenant` int(11) NOT NULL,
  `name` varchar(25) NOT NULL,
  `last_timestamp` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id_watchlist`),
  UNIQUE KEY `idtenant_name` (`id_tenant`,`name`) USING BTREE,
  KEY `FK_Watchlist_Tentant` (`id_tenant`),
  CONSTRAINT `FK_Watchlist_Tentant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`)
) ENGINE=InnoDB AUTO_INCREMENT=54206 DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `watchlist_sec_cur`
--

DROP TABLE IF EXISTS `watchlist_sec_cur`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `watchlist_sec_cur` (
  `id_watchlist` int(11) NOT NULL,
  `id_securitycurrency` int(11) NOT NULL,
  PRIMARY KEY (`id_watchlist`,`id_securitycurrency`),
  KEY `id_securitycurrency` (`id_securitycurrency`),
  CONSTRAINT `watchlist_sec_cur_ibfk_1` FOREIGN KEY (`id_watchlist`) REFERENCES `watchlist` (`id_watchlist`) ON DELETE CASCADE,
  CONSTRAINT `watchlist_sec_cur_ibfk_2` FOREIGN KEY (`id_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'grafioschtrader'
--
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_AUTO_VALUE_ON_ZERO' */ ;
/*!50003 DROP PROCEDURE IF EXISTS `copyTradingMinusToOtherStockexchange` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
DELIMITER ;;
CREATE DEFINER=`grafioschtrader`@`localhost` PROCEDURE `copyTradingMinusToOtherStockexchange`(IN `sourceIdStockexchange` INT, IN `targetIdStockexchange` INT, IN `dateFrom` DATE, IN `dateTo` DATE)
    MODIFIES SQL DATA
BEGIN

DELETE FROM trading_days_minus WHERE id_stockexchange = targetIdStockexchange AND trading_date_minus >= dateFrom AND trading_date_minus <= dateTo;

INSERT INTO trading_days_minus (id_stockexchange, trading_date_minus, create_type) SELECT targetIdStockexchange, trading_date_minus, create_type FROM trading_days_minus WHERE id_stockexchange = sourceIdStockexchange AND trading_date_minus >= dateFrom AND trading_date_minus <= dateTo;

END ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_AUTO_VALUE_ON_ZERO' */ ;
/*!50003 DROP PROCEDURE IF EXISTS `deleteUpdateHistoryQuality` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
DELIMITER ;;
CREATE DEFINER=`grafioschtrader`@`localhost` PROCEDURE `deleteUpdateHistoryQuality`()
    MODIFIES SQL DATA
BEGIN

SET @globalparam_update = "gt.historyquote.quality.update.date";

DROP TEMPORARY TABLE IF EXISTS minmax;

CREATE TEMPORARY TABLE minmax AS

(SELECT s.id_securitycurrency, MIN(hq.date) AS minDate, MAX(hq.date) AS maxDate,

SUM(IF(hq.create_type = 0, 1, 0)) AS connectorCreated,

SUM(IF(hq.create_type = 1, 1, 0)) AS filledNoTradeDay,

SUM(IF(hq.create_type = 2, 1, 0)) AS manualImported,

SUM(IF(hq.create_type = 3, 1, 0)) AS filledLinear

FROM security s JOIN historyquote hq ON s.id_securitycurrency = hq.id_securitycurrency

GROUP BY s.id_securitycurrency);

DELETE FROM historyquote_quality;

INSERT INTO historyquote_quality (idSecurity, minDate, connectorCreated, filledNoTradeDay, manualImported, filledLinear,

missingStart, maxDate, missingEnd, totalMissing, expectedTotal, qualityPercentage, toManyAsCalendar, quoteSaturday, quoteSunday)

SELECT s.id_securitycurrency AS idSecurity,

MIN(hq.date) AS minDate, mm.connectorCreated, mm.filledNoTradeDay, mm.manualImported, mm.filledLinear,

SUM(IF(tdp.trading_date < mm.minDate AND hq.date IS NULL, 1, 0) ) AS missingStart,

MAX(hq.date) maxDate,

SUM(IF(tdp.trading_date > mm.maxDate AND hq.date IS NULL, 1, 0)) AS missingEnd,

SUM(IF(hq.date IS NULL, 1, 0)) AS totalMissing,

count(*) AS expectedTotal,

ROUND((1 - SUM(IF(hq.date IS NULL, 1, 0)) / count(*)) * 100, 2) AS qualityPercentage,

0 AS toManyAsCalendar,

0 AS quoteSaturday,

0 AS quoteSunday

FROM minmax mm JOIN security s ON s.id_securitycurrency = mm.id_securitycurrency

JOIN trading_days_plus tdp LEFT JOIN trading_days_minus tdm ON tdp.trading_date = tdm.trading_date_minus AND tdm.id_stockexchange = s.id_stockexchange

LEFT JOIN historyquote hq ON s.id_securitycurrency = hq.id_securitycurrency AND tdp.trading_date = hq.date

WHERE tdm.trading_date_minus IS NULL

AND s.active_from_date <= tdp.trading_date AND s.id_tenant_private IS NULL AND tdp.trading_date <= LEAST(s.active_to_date, NOW() - INTERVAL 1 DAY)

GROUP BY s.id_securitycurrency;

UPDATE historyquote_quality hq JOIN

(SELECT s.id_securitycurrency, count(*) AS toManyAsCalendar, SUM(IF(DAYOFWEEK(hq.date) = 7, 1, 0)) AS quoteSaturday, SUM(IF(DAYOFWEEK(hq.date) = 1, 1, 0))

AS quoteSunday FROM security s JOIN historyquote hq ON s.id_securitycurrency = hq.id_securitycurrency LEFT JOIN trading_days_plus tdp

ON tdp.trading_date = hq.date LEFT JOIN trading_days_minus tdm ON tdp.trading_date = tdm.trading_date_minus AND tdm.id_stockexchange = s.id_stockexchange

WHERE (tdp.trading_date IS NULL OR tdm.trading_date_minus IS NOT NULL) AND s.active_from_date <= hq.date

AND hq.date <= LEAST(s.active_to_date, NOW() - INTERVAL 1 DAY)  GROUP BY s.id_securitycurrency) AS x ON x.id_securitycurrency = hq.idSecurity

SET hq.toManyAsCalendar = x.toManyAsCalendar, hq.quoteSaturday = x.quoteSaturday, hq.quoteSunday = x.quoteSunday;

UPDATE historyquote_quality hqq JOIN

(SELECT hq.id_securitycurrency,

  ROUND(SUM(IF(

    (hq.open IS NOT NULL AND hq.open <> 0)

    AND (hq.high IS NOT NULL AND hq.high <> 0)

    AND (hq.low IS NOT NULL AND hq.low <> 0), 1, 0)) / COUNT(*) * 100, 2) AS ohlPercentage

  FROM historyquote hq

  GROUP BY hq.id_securitycurrency) AS ohl ON ohl.id_securitycurrency = hqq.idSecurity

SET hqq.ohlPercentage = ohl.ohlPercentage;

IF (SELECT count(*) FROM globalparameters WHERE property_name = @globalparam_update) = 1

THEN

   UPDATE globalparameters SET property_date = CURDATE() WHERE property_name = @globalparam_update;

ELSE

    INSERT INTO globalparameters (property_name, property_date) VALUES(@globalparam_update,  CURDATE());

END IF;

END ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_AUTO_VALUE_ON_ZERO' */ ;
/*!50003 DROP PROCEDURE IF EXISTS `holdSecuritySplitMarginTransaction` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
DELIMITER ;;
CREATE DEFINER=`grafioschtrader`@`localhost` PROCEDURE `holdSecuritySplitMarginTransaction`(IN `idSecurity` INT)
    NO SQL
BEGIN

DROP TEMPORARY TABLE IF EXISTS holdSecurityMarginSplit;

CREATE TEMPORARY TABLE holdSecurityMarginSplit (tsDate TIMESTAMP NOT NULL)

SELECT t.id_tenant as idTenant, p.id_portfolio AS idPortfolio, t.id_security_account AS idSecurityaccount, t.transaction_time AS tsDate, IF(t.transaction_type = 4, 1, -1) * t.units *

t.asset_investment_value_2 as factorUnits, te.currency AS tenantCurrency, p.currency AS porfolioCurrency,

t.id_transaction AS idTransactionMargin

FROM transaction t JOIN security s ON s.id_securitycurrency = t.id_securitycurrency JOIN tenant te ON te.id_tenant = t.id_tenant

JOIN securitycashaccount sca ON sca.id_securitycash_account = t.id_security_account  JOIN portfolio p ON p.id_portfolio = sca.id_portfolio

WHERE t.transaction_type >= 4 AND t.transaction_type <= 5 

AND  t.id_securitycurrency = idSecurity

UNION 

SELECT t.id_tenant as idTenant, null AS idPortfolio, t.id_security_account AS idSecurityaccount, ss.split_date AS tsDate, ss.to_factor / ss.from_factor as factorUnits, null AS tenantCurrency, null AS porfolioCurrency, null AS idTransactionMargin

FROM security s JOIN securitysplit ss ON s.id_securitycurrency = ss.id_securitycurrency JOIN transaction t ON t.id_securitycurrency = s.id_securitycurrency 

WHERE t.transaction_type >= 4 AND t.transaction_type <= 5 

AND t.id_securitycurrency = idSecurity

GROUP BY t.id_tenant, t.id_security_account, ss.split_date;



SELECT t1.* FROM holdSecurityMarginSplit t1 JOIN (

SELECT t2.idTenant, t2.idSecurityaccount, MAX(IF(t2.idPortfolio IS NULL, t2.tsDate, NULL)) AS maxSplitDate, MIN(IF(t2.idPortfolio IS NULL, NULL, t2.tsDate)) AS minTransDate FROM holdSecurityMarginSplit t2 

GROUP BY t2.idTenant, t2.idSecurityaccount) AS t3 ON t1.idTenant = t3.idTenant

AND t1.idSecurityaccount = t3.idSecurityaccount AND t3.maxSplitDate >= t3.minTransDate AND (t1.tsDate >= t3.minTransDate AND t1.idPortfolio IS NULL || t1.idPortfolio IS NOT NULL)

ORDER BY t1.idTenant, t1.idSecurityaccount, t1.tsDate;

END ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_AUTO_VALUE_ON_ZERO' */ ;
/*!50003 DROP PROCEDURE IF EXISTS `holdSecuritySplitTransaction` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
DELIMITER ;;
CREATE DEFINER=`grafioschtrader`@`localhost` PROCEDURE `holdSecuritySplitTransaction`(IN `idSecurity` INT)
    READS SQL DATA
BEGIN

DROP TEMPORARY TABLE IF EXISTS holdSecuritySplit;

CREATE TEMPORARY TABLE holdSecuritySplit (tsDate TIMESTAMP NOT NULL)

SELECT t.id_tenant as idTenant, p.id_portfolio AS idPortfolio, t.id_security_account AS idSecurityaccount, t.transaction_time AS tsDate, SUM(IF(t.transaction_type = 4, 1, -1) * t.units) as factorUnits, te.currency AS tenantCurrency, p.currency AS porfolioCurrency, CAST(NULL AS int) AS idTransactionMargin

FROM transaction t JOIN security s ON s.id_securitycurrency = t.id_securitycurrency JOIN tenant te ON te.id_tenant = t.id_tenant

JOIN securitycashaccount sca ON sca.id_securitycash_account = t.id_security_account  JOIN portfolio p ON p.id_portfolio = sca.id_portfolio

WHERE t.transaction_type >= 4 AND t.transaction_type <= 5 

AND  t.id_securitycurrency = idSecurity

GROUP BY idTenant, idSecurityaccount, tsDate

UNION 

SELECT t.id_tenant as idTenant, null AS idPortfolio, t.id_security_account AS idSecurityaccount, ss.split_date AS tsDate, ss.to_factor / ss.from_factor as factorUnits, null AS tenantCurrency, null AS porfolioCurrency, CAST(NULL AS int) AS idTransactionMargin

FROM security s JOIN securitysplit ss ON s.id_securitycurrency = ss.id_securitycurrency JOIN transaction t ON t.id_securitycurrency = s.id_securitycurrency 

WHERE t.transaction_type >= 4 AND t.transaction_type <= 5 

AND t.id_securitycurrency = idSecurity

GROUP BY t.id_tenant, t.id_security_account, ss.split_date;



SELECT t1.* FROM holdSecuritySplit t1 JOIN (

SELECT t2.idTenant, t2.idSecurityaccount, MAX(IF(t2.idPortfolio IS NULL, t2.tsDate, NULL)) AS maxSplitDate, MIN(IF(t2.idPortfolio IS NULL, NULL, t2.tsDate)) AS minTransDate FROM holdSecuritySplit t2 

GROUP BY t2.idTenant, t2.idSecurityaccount) AS t3 ON t1.idTenant = t3.idTenant

AND t1.idSecurityaccount = t3.idSecurityaccount AND t3.maxSplitDate >= t3.minTransDate AND (t1.tsDate >= t3.minTransDate AND t1.idPortfolio IS NULL || t1.idPortfolio IS NOT NULL)

ORDER BY t1.idTenant, t1.idSecurityaccount, t1.tsDate;

END ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_AUTO_VALUE_ON_ZERO' */ ;
/*!50003 DROP PROCEDURE IF EXISTS `moveCreatedByUserToOtherUser` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
DELIMITER ;;
CREATE DEFINER=`grafioschtrader`@`localhost` PROCEDURE `moveCreatedByUserToOtherUser`(IN `fromIdUser` INT, IN `toIdUser` INT, IN `schemaName` VARCHAR(32))
    MODIFIES SQL DATA
BEGIN

  DECLARE done BOOLEAN DEFAULT FALSE;

  DECLARE rowChanged INTEGER DEFAULT 0;

  DECLARE rowsChanged INTEGER DEFAULT 0;

  DECLARE sqls VARCHAR(2000);



  DECLARE csr CURSOR FOR

  SELECT CONCAT('UPDATE ',c.table_schema,'.',c.table_name,' SET created_by = ', toIdUser, ' WHERE created_by = ', fromIdUser) 

    FROM information_schema.columns c

    WHERE c.column_name = 'created_by' AND c.table_name NOT LIKE 'user%' AND c.table_schema = schemaName;

  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done := TRUE;





  OPEN csr;

  do_foo: LOOP

     FETCH csr INTO sqls;

     IF done THEN

        LEAVE do_foo;

     END IF;

     PREPARE stmt FROM sqls;

     EXECUTE stmt;

	 SELECT ROW_COUNT()INTO rowChanged;

	 SET rowsChanged = rowsChanged + rowChanged;

     DEALLOCATE PREPARE stmt;

  END LOOP do_foo;

  CLOSE csr;

  SELECT rowsChanged;

END ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_AUTO_VALUE_ON_ZERO' */ ;
/*!50003 DROP PROCEDURE IF EXISTS `updCalendarStockexchangeByIndex` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
DELIMITER ;;
CREATE DEFINER=`grafioschtrader`@`localhost` PROCEDURE `updCalendarStockexchangeByIndex`()
    NO SQL
BEGIN

  DECLARE done BOOLEAN DEFAULT FALSE;

  DECLARE _idStockexchange INT UNSIGNED;

  DECLARE _idSecurity INT UNSIGNED;

  DECLARE cur CURSOR FOR SELECT id_stockexchange, id_index_upd_calendar FROM stockexchange  

     WHERE (SELECT COUNT(*) FROM historyquote WHERE id_securitycurrency = id_index_upd_calendar) > 4000;

  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done := TRUE;

  OPEN cur;

  testLoop: LOOP

    FETCH cur INTO _idStockexchange, _idSecurity;

    IF done THEN

      LEAVE testLoop;

    END IF;

    DELETE FROM trading_days_minus WHERE id_stockexchange = _idStockexchange AND trading_date_minus > 

      (SELECT IFNULL(MAX(trading_date_minus), "1999-12-31") AS fromDate FROM trading_days_minus WHERE id_stockexchange = _idStockexchange AND create_type = 5);

    INSERT INTO trading_days_minus (id_stockexchange, trading_date_minus)

     SELECT _idStockexchange, tsp.trading_date AS trandingDate FROM trading_days_plus tsp LEFT JOIN (SELECT DISTINCT hq.date AS date FROM historyquote hq  WHERE hq.id_securitycurrency = _idSecurity) AS a ON tsp.trading_date = a.date WHERE a.date IS NULL AND  

     tsp.trading_date > (SELECT IFNULL(MAX(trading_date_minus), "1999-12-31") AS fromDate FROM trading_days_minus WHERE id_stockexchange = _idStockexchange AND create_type = 5)

     AND tsp.trading_date < CURDATE();

     UPDATE stockexchange SET max_calendar_upd_date = (SELECT MAX(date) FROM historyquote WHERE id_securitycurrency = _idSecurity);

  END LOOP testLoop;

  CLOSE cur;



END ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-03-27 10:20:57
