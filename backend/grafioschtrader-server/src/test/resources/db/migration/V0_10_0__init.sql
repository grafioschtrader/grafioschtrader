-- MariaDB dump 10.17  Distrib 10.4.8-MariaDB, for Win64 (AMD64)
--
-- Host: localhost    Database: grafioschtrader
-- ------------------------------------------------------
-- Server version	10.4.8-MariaDB

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
  `id_algo_assetclass_security_p` int(11) NOT NULL,
  `id_asset_class` int(11) NOT NULL,
  PRIMARY KEY (`id_algo_assetclass_security`),
  KEY `FK_AlgoAssetClass_Assetclass` (`id_asset_class`),
  KEY `FK_AlgoAssetClass_AlgoTop` (`id_algo_assetclass_security_p`),
  CONSTRAINT `FK_AlgoAssetClass_AlgoAssetClassSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_assetclass_security` (`id_algo_assetclass_security`),
  CONSTRAINT `FK_AlgoAssetClass_AlgoTop` FOREIGN KEY (`id_algo_assetclass_security_p`) REFERENCES `algo_top` (`id_algo_assetclass_security`) ON DELETE CASCADE,
  CONSTRAINT `FK_AlgoAssetClass_Assetclass` FOREIGN KEY (`id_asset_class`) REFERENCES `assetclass` (`id_asset_class`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
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
  CONSTRAINT `FK_AlgoAssetClassSecurity_AlgoTopAssetSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_top_asset_security` (`id_algo_assetclass_security`),
  CONSTRAINT `FK_AlgoAssetClassSecurity_SecurityAccount_1` FOREIGN KEY (`id_securitycash_account_1`) REFERENCES `securityaccount` (`id_securitycash_account`),
  CONSTRAINT `FK_AlgoAssetClassSecurity_SecurityAccount_2` FOREIGN KEY (`id_securitycash_account_2`) REFERENCES `securityaccount` (`id_securitycash_account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
  `id_alog_strategy` int(11) NOT NULL,
  `alert_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id_algo_message_alert`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_rule`
--

DROP TABLE IF EXISTS `algo_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_rule` (
  `id_algo_rule_strategy` int(11) NOT NULL,
  `buy_sell` tinyint(1) NOT NULL,
  `and_or_not` tinyint(1) NOT NULL,
  `trading_rule` tinyint(4) NOT NULL,
  `rule_param1` tinyint(4) NOT NULL,
  `rule_param2` tinyint(4) NOT NULL,
  PRIMARY KEY (`id_algo_rule_strategy`),
  CONSTRAINT `FK_AlgoRule_AlgoRuleStrategy` FOREIGN KEY (`id_algo_rule_strategy`) REFERENCES `algo_rule_strategy` (`id_algo_rule_strategy`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_rule_param2`
--

DROP TABLE IF EXISTS `algo_rule_param2`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_rule_param2` (
  `id_algo_rule_strategy` int(11) NOT NULL,
  `param_name` varchar(32) NOT NULL,
  `param_value` varchar(24) NOT NULL,
  PRIMARY KEY (`id_algo_rule_strategy`,`param_name`) USING BTREE,
  CONSTRAINT `FK_AlgoRuleParam_AlgoRule` FOREIGN KEY (`id_algo_rule_strategy`) REFERENCES `algo_rule` (`id_algo_rule_strategy`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
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
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_security`
--

DROP TABLE IF EXISTS `algo_security`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_security` (
  `id_algo_assetclass_security` int(11) NOT NULL,
  `id_algo_assetclass_security_p` int(11) NOT NULL,
  `id_securitycurrency` int(11) NOT NULL,
  PRIMARY KEY (`id_algo_assetclass_security`),
  KEY `FK_AlgoSecurity_Security` (`id_securitycurrency`),
  KEY `FK_AlgoSecurity_AlgoAssetClass` (`id_algo_assetclass_security_p`),
  CONSTRAINT `FK_AlgoSecurity_AlgoAssetClass` FOREIGN KEY (`id_algo_assetclass_security_p`) REFERENCES `algo_assetclass` (`id_algo_assetclass_security`),
  CONSTRAINT `FK_AlgoSecurity_AlgoAssetClassSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_assetclass_security` (`id_algo_assetclass_security`),
  CONSTRAINT `FK_AlgoSecurity_Security` FOREIGN KEY (`id_securitycurrency`) REFERENCES `security` (`id_securitycurrency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
  PRIMARY KEY (`id_algo_rule_strategy`),
  CONSTRAINT `FK_AlgoStrategy_AlgoRuleStrategy` FOREIGN KEY (`id_algo_rule_strategy`) REFERENCES `algo_rule_strategy` (`id_algo_rule_strategy`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `algo_top`
--

DROP TABLE IF EXISTS `algo_top`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `algo_top` (
  `id_algo_assetclass_security` int(11) NOT NULL,
  `rule_or_strategy` tinyint(1) NOT NULL,
  `name` varchar(32) NOT NULL,
  `activatable` tinyint(1) NOT NULL,
  `id_watchlist` int(11) NOT NULL,
  PRIMARY KEY (`id_algo_assetclass_security`),
  KEY `FK_AlgoTop_Watchlist` (`id_watchlist`),
  CONSTRAINT `FK_AlgoTop_AlgoTopAssetSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_top_asset_security` (`id_algo_assetclass_security`) ON DELETE CASCADE,
  CONSTRAINT `FK_AlgoTop_Watchlist` FOREIGN KEY (`id_watchlist`) REFERENCES `watchlist` (`id_watchlist`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
  `percentage` float NOT NULL,
  PRIMARY KEY (`id_algo_assetclass_security`),
  KEY `FK_AlgoTopAssetSecurity_Tenant` (`id_tenant`),
  CONSTRAINT `FK_AlgoTopAssetSecurity_Tenant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB AUTO_INCREMENT=512 DEFAULT CHARSET=utf8;
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
  PRIMARY KEY (`id_securitycash_account`),
  CONSTRAINT `FK_CashAccount_SecurityCashAccount` FOREIGN KEY (`id_securitycash_account`) REFERENCES `securitycashaccount` (`id_securitycash_account`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=857 DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
  `amount` double(16,7) NOT NULL,
  `currency` char(3) NOT NULL,
  `create_type` tinyint(1) NOT NULL,
  `create_modify_time` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id_dividend`),
  KEY `FK_Dividend_Security` (`id_securitycurrency`),
  CONSTRAINT `FK_Dividend_Security` FOREIGN KEY (`id_securitycurrency`) REFERENCES `security` (`id_securitycurrency`)
) ENGINE=InnoDB AUTO_INCREMENT=4741 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ex_security_currencypair`
--

DROP TABLE IF EXISTS `ex_security_currencypair`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ex_security_currencypair` (
  `id_securitycurrency` int(11) NOT NULL,
  PRIMARY KEY (`id_securitycurrency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
  `property_blob` blob DEFAULT NULL,
  PRIMARY KEY (`property_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
  `close` double(16,8) NOT NULL,
  `volume` bigint(11) DEFAULT NULL,
  `open` double(16,8) DEFAULT NULL,
  `high` double(16,8) DEFAULT NULL,
  `low` double(16,8) DEFAULT NULL,
  `create_type` tinyint(1) DEFAULT NULL,
  `create_modify_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id_history_quote`),
  UNIQUE KEY `IHistoryQuote_id_Date` (`id_securitycurrency`,`date`,`create_type`) USING BTREE,
  KEY `FK_HistoryQuote_SecurityCurrency` (`id_securitycurrency`) USING BTREE,
  CONSTRAINT `FK_HistoryQuote_SecurityCurrency` FOREIGN KEY (`id_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5637166 DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT COMMENT='Certain securities do not have public and daily prices. For example, time deposits, but a daily price is still required for the holding period.';
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
  PRIMARY KEY (`idSecurity`),
  CONSTRAINT `FK_HistoryquoteQuality_Security` FOREIGN KEY (`idSecurity`) REFERENCES `security` (`id_securitycurrency`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
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
  KEY `FK_ImpTransHead_Tenant` (`id_tenant`),
  KEY `FK_ImpTransHead_Securityaccount` (`id_securitycash_account`),
  CONSTRAINT `FK_ImpTransHead_Securityaccount` FOREIGN KEY (`id_securitycash_account`) REFERENCES `securityaccount` (`id_securitycash_account`),
  CONSTRAINT `FK_ImpTransHead_Tenant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`)
) ENGINE=InnoDB AUTO_INCREMENT=326 DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
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
  `currency_ex_rate` double(16,10) DEFAULT NULL,
  `units` double DEFAULT NULL,
  `quotation` double(16,7) DEFAULT NULL,
  `tax_cost` double(16,7) DEFAULT NULL,
  `transaction_cost` double(16,7) DEFAULT NULL,
  `currency_cost` char(3) DEFAULT NULL,
  `cashaccount_amount` double(16,7) DEFAULT NULL,
  `accepted_total_diff` double DEFAULT NULL,
  `accrued_interest` double(16,7) DEFAULT NULL,
  `field1_string_imp` varchar(20) DEFAULT NULL,
  `ready_for_transaction` tinyint(1) NOT NULL DEFAULT 0,
  `id_transaction` int(11) DEFAULT NULL,
  `id_trans_imp_template` int(11) DEFAULT NULL,
  `id_file_part` int(11) DEFAULT NULL,
  `file_name_original` varchar(255) DEFAULT NULL,
  `con_id_trans_pos` int(11) DEFAULT NULL,
  `known_other_flags` int(11) DEFAULT NULL,
  PRIMARY KEY (`id_trans_pos`),
  KEY `FK_ImpTransPos_ImpTransHead` (`id_trans_head`),
  KEY `FK_ImpTransPos_Cashaccount` (`id_cash_account`) USING BTREE,
  CONSTRAINT `FK_ImpTransPos_Cashaccount1` FOREIGN KEY (`id_cash_account`) REFERENCES `cashaccount` (`id_securitycash_account`),
  CONSTRAINT `FK_ImpTransPos_ImpTransHead` FOREIGN KEY (`id_trans_head`) REFERENCES `imp_trans_head` (`id_trans_head`)
) ENGINE=InnoDB AUTO_INCREMENT=30310 DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
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
) ENGINE=InnoDB AUTO_INCREMENT=18793 DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
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
  `template_format_type` smallint(6) NOT NULL,
  `template_purpose` varchar(50) NOT NULL,
  `template_as_txt` varchar(4096) NOT NULL,
  `valid_since` date NOT NULL,
  `template_language` varchar(5) NOT NULL,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id_trans_imp_template`),
  UNIQUE KEY `UNIQUE_imptrastemplate_field` (`id_trans_imp_platform`,`template_purpose`,`valid_since`),
  CONSTRAINT `FK_TradingImpTemplate_TradingImpPlatform` FOREIGN KEY (`id_trans_imp_platform`) REFERENCES `imp_trans_platform` (`id_trans_imp_platform`)
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mail_in_out`
--

DROP TABLE IF EXISTS `mail_in_out`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mail_in_out` (
  `id_mail_inout` int(11) NOT NULL AUTO_INCREMENT,
  `dtype` varchar(1) NOT NULL,
  `id_user_from` int(11) NOT NULL,
  `id_user_to` int(11) DEFAULT NULL,
  `id_role_to` int(11) DEFAULT NULL,
  `subject` varchar(96) NOT NULL,
  `message` varchar(1024) NOT NULL,
  PRIMARY KEY (`id_mail_inout`),
  KEY `FK_MailInOut_Role` (`id_role_to`),
  CONSTRAINT `FK_MailInOut_Role` FOREIGN KEY (`id_role_to`) REFERENCES `role` (`id_role`),
  CONSTRAINT `CONSTRAINT_1` CHECK (`id_user_to` is null and `id_role_to` is not null or `id_user_to` is not null and `id_role_to` is null)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mail_inbox`
--

DROP TABLE IF EXISTS `mail_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mail_inbox` (
  `id_mail_inout` int(11) NOT NULL,
  `domain_from` varchar(64) DEFAULT NULL,
  `received_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `has_been_read` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_mail_inout`),
  CONSTRAINT `FK_MailInbox_MailInOut` FOREIGN KEY (`id_mail_inout`) REFERENCES `mail_in_out` (`id_mail_inout`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mail_sendbox`
--

DROP TABLE IF EXISTS `mail_sendbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `mail_sendbox` (
  `id_mail_inout` int(11) NOT NULL,
  `domain_to` varchar(64) DEFAULT NULL,
  `send_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  KEY `FK_Sendbox_MailInOut` (`id_mail_inout`),
  CONSTRAINT `FK_Sendbox_MailInOut` FOREIGN KEY (`id_mail_inout`) REFERENCES `mail_in_out` (`id_mail_inout`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB AUTO_INCREMENT=655 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `multilinguestrings`
--

DROP TABLE IF EXISTS `multilinguestrings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `multilinguestrings` (
  `id_string` int(11) NOT NULL,
  `text` varchar(64) NOT NULL,
  `language` varchar(2) NOT NULL,
  PRIMARY KEY (`id_string`,`language`),
  CONSTRAINT `FK_Multilinguestrings_Multilinguestring` FOREIGN KEY (`id_string`) REFERENCES `multilinguestring` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
  PRIMARY KEY (`id_portfolio`),
  UNIQUE KEY `idtenant_name` (`id_tenant`,`name`) USING BTREE,
  KEY `FK_Portfolio_Tentant` (`id_tenant`),
  CONSTRAINT `FK_Portfolio_Tentant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`)
) ENGINE=InnoDB AUTO_INCREMENT=220 DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
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
) ENGINE=InnoDB AUTO_INCREMENT=95 DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
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
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
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
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8;
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
  `short_security` tinyint(1) NOT NULL DEFAULT 0,
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
  `retry_dividend_load` int(6) NOT NULL DEFAULT 0,
  `id_connector_split` varchar(35) DEFAULT NULL,
  `url_split_extend` varchar(254) DEFAULT NULL,
  `retry_split_load` int(6) NOT NULL DEFAULT 0,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;
ALTER DATABASE `grafioschtrader` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`grafioschtrader`@`localhost`*/ /*!50003 TRIGGER security_ins BEFORE INSERT ON security
  FOR EACH ROW SET NEW.ticker_symbol = UPPER(NEW.ticker_symbol) */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
ALTER DATABASE `grafioschtrader` CHARACTER SET utf8 COLLATE utf8_general_ci ;
ALTER DATABASE `grafioschtrader` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`grafioschtrader`@`localhost`*/ /*!50003 TRIGGER security_upd BEFORE UPDATE ON security 
FOR EACH ROW SET NEW.ticker_symbol = UPPER(NEW.ticker_symbol) */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
ALTER DATABASE `grafioschtrader` CHARACTER SET utf8 COLLATE utf8_general_ci ;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
  `share_use_until` date DEFAULT NULL,
  `bond_use_until` date DEFAULT NULL,
  `etf_use_until` date DEFAULT NULL,
  `fond_use_until` date DEFAULT NULL,
  `forex_use_until` date DEFAULT NULL,
  `cfd_use_until` date DEFAULT NULL,
  `weka_model` longblob DEFAULT NULL,
  `lowest_transaction_cost` float(6,2) NOT NULL,
  PRIMARY KEY (`id_securitycash_account`),
  KEY `FK_Securityaccount_Tradingplatformplan` (`id_trading_platform_plan`),
  CONSTRAINT `FK_SecurityAccount_SecurityCashAccount` FOREIGN KEY (`id_securitycash_account`) REFERENCES `securitycashaccount` (`id_securitycash_account`) ON DELETE CASCADE,
  CONSTRAINT `FK_Securityaccount_Tradingplatformplan` FOREIGN KEY (`id_trading_platform_plan`) REFERENCES `trading_platform_plan` (`id_trading_platform_plan`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
  PRIMARY KEY (`id_securitycash_account`),
  UNIQUE KEY `idPortfolio_dType_name` (`id_portfolio`,`dtype`,`name`) USING BTREE,
  KEY `FK_SecurityAccount_Portfolio` (`id_portfolio`),
  CONSTRAINT `FK_SecurityAccount_Portfolio` FOREIGN KEY (`id_portfolio`) REFERENCES `portfolio` (`id_portfolio`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=857 DEFAULT CHARSET=utf8;
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
  `s_prev_close` double(16,8) DEFAULT NULL,
  `s_change_percentage` double(16,8) DEFAULT NULL,
  `s_open` double(18,8) DEFAULT NULL,
  `s_last` double(16,8) DEFAULT NULL,
  `s_low` double(16,8) DEFAULT NULL,
  `s_high` double(16,8) DEFAULT NULL,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id_securitycurrency`)
) ENGINE=InnoDB AUTO_INCREMENT=3922 DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB AUTO_INCREMENT=205 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stockexchange`
--

DROP TABLE IF EXISTS `stockexchange`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `stockexchange` (
  `id_stockexchange` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL,
  `country_code` char(2) DEFAULT NULL,
  `secondary_market` tinyint(1) NOT NULL,
  `no_market_value` tinyint(1) NOT NULL,
  `symbol` varchar(8) NOT NULL,
  `time_close` time NOT NULL,
  `time_zone` varchar(50) NOT NULL,
  `created_by` int(11) NOT NULL,
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `last_modified_by` int(11) NOT NULL,
  `last_modified_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id_stockexchange`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=244 DEFAULT CHARSET=utf8;
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
  `creation_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `exec_start_time` timestamp NULL DEFAULT NULL,
  `exec_end_time` timestamp NULL DEFAULT NULL,
  `old_value_varchar` varchar(30) DEFAULT NULL,
  `old_value_number` double DEFAULT NULL,
  `progress_state` tinyint(1) NOT NULL,
  `failed_message_code` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`id_task_data_change`)
) ENGINE=InnoDB AUTO_INCREMENT=876 DEFAULT CHARSET=utf8;
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
  PRIMARY KEY (`id_tenant`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8;
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
  PRIMARY KEY (`trading_date_minus`,`id_stockexchange`),
  KEY `FK_TradingDayMinus_Stockexchange` (`id_stockexchange`),
  CONSTRAINT `FK_TradingDayMinus_Stockexchange` FOREIGN KEY (`id_stockexchange`) REFERENCES `stockexchange` (`id_stockexchange`) ON DELETE CASCADE,
  CONSTRAINT `FK_TradingDaysMinus_TradingDaysPlus` FOREIGN KEY (`trading_date_minus`) REFERENCES `trading_days_plus` (`trading_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
  `last_modified_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `version` int(11) NOT NULL,
  PRIMARY KEY (`id_trading_platform_plan`),
  UNIQUE KEY `platform_plan_name` (`platform_plan_name_nls`),
  KEY `FK_Tradingplatformplan_Multilinguestring` (`platform_plan_name_nls`),
  KEY `FK_Tradingplatform_ImpTransPlatform` (`id_trans_imp_platform`),
  CONSTRAINT `FK_Tradingplatform_ImpTransPlatform` FOREIGN KEY (`id_trans_imp_platform`) REFERENCES `imp_trans_platform` (`id_trans_imp_platform`),
  CONSTRAINT `FK_Tradingplatformplan_Multilinguestring` FOREIGN KEY (`platform_plan_name_nls`) REFERENCES `multilinguestring` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
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
  `quotation` double(16,7) DEFAULT NULL,
  `transaction_type` smallint(6) NOT NULL,
  `tax_cost` double(16,7) DEFAULT NULL,
  `taxable_interest` tinyint(1) DEFAULT NULL,
  `transaction_cost` double(16,7) DEFAULT NULL,
  `currency_ex_rate` double(16,10) DEFAULT NULL,
  `note` varchar(1000) DEFAULT NULL,
  `cashaccount_amount` double(16,7) NOT NULL COMMENT 'Amount which is added to the cash account',
  `id_currency_pair` int(11) DEFAULT NULL,
  `asset_investment_value_1` double(16,7) DEFAULT NULL COMMENT 'Used for accrued interest with Bonds and daily holding costs with CFD',
  `asset_investment_value_2` double(16,7) DEFAULT NULL COMMENT 'CFD holds the value per point',
  PRIMARY KEY (`id_transaction`),
  KEY `FK_Transaction_SecurityCurrency` (`id_securitycurrency`),
  KEY `FK_Transaction_securityAccount` (`id_security_account`),
  KEY `FK_Transaction_cashAccount` (`id_cash_account`),
  KEY `tt_date` (`tt_date`) USING BTREE,
  KEY `tenant_securitycurrency` (`id_tenant`,`id_securitycurrency`),
  CONSTRAINT `FK_Transaction_CashAccount` FOREIGN KEY (`id_cash_account`) REFERENCES `cashaccount` (`id_securitycash_account`),
  CONSTRAINT `FK_Transaction_SecurityAccount` FOREIGN KEY (`id_security_account`) REFERENCES `securityaccount` (`id_securitycash_account`),
  CONSTRAINT `FK_Transaction_SecurityCurrency` FOREIGN KEY (`id_securitycurrency`) REFERENCES `securitycurrency` (`id_securitycurrency`),
  CONSTRAINT `c_currency_ex_rate` CHECK (`currency_ex_rate` is not null and `currency_ex_rate` > 0 and `id_currency_pair` is not null or `currency_ex_rate` is null and `id_currency_pair` is null),
  CONSTRAINT `s_units` CHECK (`units` is not null and `units` <> 0 and `id_securitycurrency` is not null or `id_securitycurrency` is null and `units` is null),
  CONSTRAINT `s_quotation` CHECK (`quotation` is not null and (`quotation` > 0 or `quotation` <> 0 and `transaction_type` = 7) and `id_securitycurrency` is not null or `quotation` is null and `id_securitycurrency` is null)
) ENGINE=InnoDB AUTO_INCREMENT=51664 DEFAULT CHARSET=utf8;
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
  `email` varchar(30) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `locale` varchar(5) NOT NULL,
  `timezone_offset` int(11) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  `security_breach_count` smallint(6) NOT NULL DEFAULT 0,
  `limit_request_exceed_count` smallint(6) NOT NULL DEFAULT 0,
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
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB AUTO_INCREMENT=345 DEFAULT CHARSET=utf8;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'grafioschtrader'
--
/*!50003 DROP PROCEDURE IF EXISTS `copyTradingMinusToOtherStockexchange` */;
ALTER DATABASE `grafioschtrader` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
CREATE DEFINER=`grafioschtrader`@`localhost` PROCEDURE `copyTradingMinusToOtherStockexchange`(IN `sourceIdStockexchange` INT, IN `targetIdStockexchange` INT, IN `dateFrom` DATE, IN `dateTo` DATE)
    MODIFIES SQL DATA
BEGIN
DELETE FROM trading_days_minus WHERE id_stockexchange = targetIdStockexchange AND trading_date_minus >= dateFrom AND trading_date_minus <= dateTo;
INSERT INTO trading_days_minus (id_stockexchange, 	trading_date_minus) SELECT targetIdStockexchange, trading_date_minus FROM trading_days_minus WHERE id_stockexchange = sourceIdStockexchange AND trading_date_minus >= dateFrom AND trading_date_minus <= dateTo;
END ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
ALTER DATABASE `grafioschtrader` CHARACTER SET utf8 COLLATE utf8_general_ci ;
/*!50003 DROP PROCEDURE IF EXISTS `deleteUpdateHistoryQuality` */;
ALTER DATABASE `grafioschtrader` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION' */ ;
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
INSERT INTO historyquote_quality SELECT s.id_securitycurrency AS idSecurity, 
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
ALTER DATABASE `grafioschtrader` CHARACTER SET utf8 COLLATE utf8_general_ci ;
/*!50003 DROP PROCEDURE IF EXISTS `holdSecuritySplitMarginTransaction` */;
ALTER DATABASE `grafioschtrader` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION' */ ;
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
ALTER DATABASE `grafioschtrader` CHARACTER SET utf8 COLLATE utf8_general_ci ;
/*!50003 DROP PROCEDURE IF EXISTS `holdSecuritySplitTransaction` */;
ALTER DATABASE `grafioschtrader` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION' */ ;
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
ALTER DATABASE `grafioschtrader` CHARACTER SET utf8 COLLATE utf8_general_ci ;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2021-03-27 16:23:57
-- MariaDB dump 10.17  Distrib 10.4.8-MariaDB, for Win64 (AMD64)
--
-- Host: localhost    Database: grafioschtrader
-- ------------------------------------------------------
-- Server version	10.4.8-MariaDB

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `multilinguestring`
--

LOCK TABLES `multilinguestring` WRITE;
/*!40000 ALTER TABLE `multilinguestring` DISABLE KEYS */;
INSERT INTO `multilinguestring` VALUES (423),(424),(428),(438),(440),(442),(443),(445),(446),(448),(449),(450),(452),(453),(455),(457),(458),(459),(460),(462),(463),(465),(466),(467),(468),(469),(471),(472),(474),(475),(476),(477),(478),(479),(480),(482),(485),(489),(490),(491),(492),(493),(495),(496),(497),(498),(499),(517),(521),(522),(548),(554),(555),(557),(560),(561),(563),(564),(565),(566),(567),(570),(571),(572),(573),(574),(575),(576),(577),(578),(582),(586),(587),(588),(595),(608),(609),(610),(611),(612),(613),(614),(618),(631),(632),(633),(634),(635),(636),(637),(638),(639),(640),(641),(642),(643),(644),(645),(646),(647),(653),(654);
/*!40000 ALTER TABLE `multilinguestring` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `multilinguestrings`
--

LOCK TABLES `multilinguestrings` WRITE;
/*!40000 ALTER TABLE `multilinguestrings` DISABLE KEYS */;
INSERT INTO `multilinguestrings` VALUES (423,'Aktien Schweiz','de'),(423,'Stocks Switzerland','en'),(424,'Aktien Welt','de'),(424,'Stocks World','en'),(428,'Mischfonds','de'),(428,'Mixed funds','en'),(438,'Edelmetalle','de'),(438,'Precious metals','en'),(440,'Aktien Schweiz','de'),(440,'Stocks Switzerland','en'),(442,'Immobilien','de'),(442,'Real estate','en'),(443,'Anleihen Schweiz','de'),(443,'Bond Switzerland','en'),(445,'Anleihen Schwellenländer','de'),(445,'Bond Emerging Markets','en'),(446,'Aktien Schwellenländer','de'),(446,'Stocks Emerging Markets','en'),(448,'Immobilien Schweiz','de'),(448,'Real estate Switzerland','en'),(449,'Metall und Öl','de'),(449,'Precious metals & Oiil','en'),(450,'Aktien Schweiz','de'),(450,'Stocks Switzerland','en'),(452,'Aktien Deutschland','de'),(452,'Stocks Germany','en'),(453,'Aktien USA','de'),(453,'Stocks USA','en'),(455,'Edelmetalle, Metall,  Energie, Vieh und Agrarrohstfoffe','de'),(455,'Precious, Base  & Energy & Agricultural & Livestock','en'),(457,'Aktien Immobilien Europa','de'),(457,'Stocks Real Estata Europe','en'),(458,'Aktien Immobilien Welt','de'),(458,'Stocks Real Estata World','en'),(459,'Aktien UK','de'),(459,'Stocks UK','en'),(460,'Aktien Europa','de'),(460,'Stocks Europe','en'),(462,'Anleihen Europa inflationsgeschützt','de'),(462,'Bond Europe inflation Linked','en'),(463,'Wandelanleihe Schweiz','de'),(463,'Convertible Bond Switzerland','en'),(465,'Kreditderivate Welt','de'),(465,'Credit derivative World','en'),(466,'Aktien Deutschland','de'),(466,'Stocks Germany','en'),(467,'Aktien Deutschland','de'),(467,'Stocks Germany','en'),(468,'Anleihen Schwellenländer','de'),(468,'Bond Emerging Markets','en'),(469,'Anleihen Europa','de'),(469,'Bond Europe','en'),(471,'Immobilien Schwellenländer','de'),(471,'Real estate Emerging Makerts','en'),(472,'Anleihen Welt','de'),(472,'Bond World','en'),(474,'Agrar Futures','de'),(474,'Agriculture Futures','en'),(475,'Anleihen Welt','de'),(475,'Bond World','en'),(476,'Geldmarkt Europa','de'),(476,'Time deposit Europe','en'),(477,'Aktien UK','de'),(477,'Stocks UK','en'),(478,'Aktien USA','de'),(478,'Stocks USA','en'),(479,'Aktien USA','de'),(479,'Stocks USA','en'),(480,'Anleihen USA','de'),(480,'Bond USA','en'),(482,'Aktien Spanien','de'),(482,'Stocks Spain ','en'),(485,'Anleihen USA','de'),(485,'Bond USA','en'),(489,'Aktien Schwellenländer','de'),(489,'Stocks Emerging Markets','en'),(490,'Aktien Japan','de'),(490,'Stocks Japan','en'),(491,'Aktien Frankreich','de'),(491,'Stocks France','en'),(492,'Aktien Frankreich','de'),(492,'Stocks France','en'),(493,'Aktien Italien','de'),(493,'Stocks Italy','en'),(495,'Aktien Österreich','de'),(495,'Stocks Austria','en'),(496,'Aktien Österreich','de'),(496,'Stocks Austria','en'),(497,'Aktien Italien','de'),(497,'Stocks Italy','en'),(498,'Aktien Spanien','de'),(498,'Stocks Spain','en'),(499,'Aktien Spanien','de'),(499,'Stocks Spain ','en'),(517,'Migros Bank Vorsorge','de'),(517,'Migros Bank Pensions ','en'),(521,'Swissquote Pauschal oder Transaktionsbetrag','de'),(521,'Swissquote Flat Fee or Transaction Value','en'),(522,'Raiffeisen Schweiz','de'),(522,'Raiffeisen Switzerland','en'),(548,'TradeDirect Schweiz','de'),(548,'TradeDirect Switzerland','en'),(554,'Öl Futures','de'),(554,'Oil Futures','en'),(555,'Aktien Japan','de'),(555,'Stocks Japan','en'),(557,'Aktien Japan','de'),(557,'Stocks Japan','en'),(560,'CornèTrader Transaktionsbetrag','de'),(560,'CornèTrader Transactions value','en'),(561,'Anleihen Europa','de'),(561,'Bond Europe','en'),(563,'Festgeld','de'),(563,'Time deposit','en'),(564,'Anleihen Welt inflationsgeschützt','de'),(564,'Bond World inflation Linked','en'),(565,'Aktien Europa-Euro','de'),(565,'Stocks Europe-Euro','en'),(566,'Aktien Südkorea','de'),(566,'Stocks South Korea','en'),(567,'Immobilien Schweiz','de'),(567,'Real estate Switzerland','en'),(570,'GENERAL - Pro Aktie','de'),(570,'GENERAL - Per share','en'),(571,'GENERAL - Pauschalgebühr','de'),(571,'GENERAL - Flat fee','en'),(572,'GENERAL - Gratis','de'),(572,'GENERAL - Free','en'),(573,'GENERAL - Pauschal oder % vom Transaktionsbetrag','de'),(573,'GENERAL - Flat fee or % of transactions value','en'),(574,'GENERAL - Pauschal oder Transaktionsbetrag mit Stufentarif','de'),(574,'GENERAL - Flat fee or graduated rates of transaction value','en'),(575,'GENERAL - Unterschiedliche Gebührenmodelle','de'),(575,'GENERAL - Mixed fee models','en'),(576,'GENERAL - % vom Transaktionsbetrag','de'),(576,'GENERAL - % of transaction value','en'),(577,'GENERAL - Transaktionsbetrag mit Stufentarif','de'),(577,'GENERAL - Graduated rates of transaction value','en'),(578,'Aktien Italien','de'),(578,'Stocks Italy','en'),(582,'Aktien Frankreich','de'),(582,'Stocks France','en'),(586,'Aktien Südkorea','de'),(586,'Stocks South Korea','en'),(587,'Aktien Dänemark','de'),(587,'Stocks Denmark','en'),(588,'Oil Indices','de'),(588,'Öl Index','en'),(595,'Test asset de','de'),(595,'Test asset en','en'),(608,'Aktien UK','de'),(608,'Stocks UK','en'),(609,'Aktien Norwegen','de'),(609,'Stocks Norway','en'),(610,'Aktien Schweden','de'),(610,'Stocks Sweden','en'),(611,'Anleihen Deutschland','de'),(611,'Bond Germany','en'),(612,'Anleihen USA','de'),(612,'Bond USA','en'),(613,'Aktien Schweiz','de'),(613,'Stocks Switzerland','en'),(614,'Forex','de'),(614,'Forex','en'),(618,'Aktien Niederlande','de'),(618,'Stocks  Netherlands','en'),(631,'xxxx','de'),(631,'xxxx','en'),(632,'Aktien Welt (entwickelten Länder)','de'),(632,'Stocks World (developed economies)','en'),(633,'Aktien Welt','de'),(633,'Stocks World','en'),(634,'Aktien Deutschland','de'),(634,'Stocks Germany','en'),(635,'Aktien USA','de'),(635,'Stocks USA','en'),(636,'Öl Futures','de'),(636,'Oil Futures','en'),(637,'Aktien Österreich','de'),(637,'Stocks Austria','en'),(638,'Aktien Kanada','de'),(638,'Stocks Canada','en'),(639,'Aktien Autstralien','de'),(639,'Stocks Australia','en'),(640,'Aktien Neuseeland','de'),(640,'Stock New Zealand','en'),(641,'Aktien Australien','de'),(641,'Stocks Australia','en'),(642,'Aktien Neuseeland','de'),(642,'Stock New Zealand','en'),(643,'E-Trading - PostFinance Standard','de'),(643,'E-Trading - PostFinance Standard','en'),(644,'Strateo','de'),(644,'Strateo','en'),(645,'Aktien Irland','de'),(645,'Stocks Ireland','en'),(646,'Aktien China','de'),(646,'Stocks China','en'),(647,'Aktien Israel','de'),(647,'Stocks Israel','en'),(653,'Migros Bank Standard','de'),(653,'Migros Bank Standard','en'),(654,'Degiro','de'),(654,'Degiro','en');
/*!40000 ALTER TABLE `multilinguestrings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `assetclass`
--

LOCK TABLES `assetclass` WRITE;
/*!40000 ALTER TABLE `assetclass` DISABLE KEYS */;
INSERT INTO `assetclass` VALUES (423,0,0,423,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(424,0,0,424,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(425,1,0,443,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(426,3,1,438,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(427,0,1,632,1,'2021-02-08 17:20:52',1,'2020-02-21 15:00:06',4),(428,5,3,428,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(429,4,2,442,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(430,1,1,472,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(431,0,10,440,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(432,1,1,445,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(433,0,1,446,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(434,3,1,554,1,'2021-02-08 17:20:52',1,'2018-06-25 05:02:45',2),(435,4,1,448,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(436,3,1,449,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(437,0,1,450,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(438,1,1,462,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(439,0,1,452,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(440,0,0,453,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(441,3,1,474,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(442,3,1,455,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(443,0,1,482,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(444,0,1,457,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(445,0,1,458,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(446,0,1,459,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(447,0,1,460,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(448,2,0,563,1,'2021-02-08 17:20:52',1,'2018-07-14 03:31:26',3),(449,6,0,463,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(451,7,1,465,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(452,0,10,466,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(453,0,0,467,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(454,1,0,468,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(455,1,0,469,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(456,4,2,471,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(457,1,0,475,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(458,2,1,476,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(459,0,0,477,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(460,0,10,478,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(461,0,1,479,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(462,1,1,480,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(463,0,0,490,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(464,0,0,491,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(465,1,0,485,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(466,0,10,492,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(467,0,10,489,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(468,0,10,493,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(469,0,10,557,1,'2021-02-08 17:20:52',1,'2018-07-02 10:06:27',3),(470,0,10,495,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(471,0,0,496,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(472,0,0,497,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(473,0,0,498,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(474,0,10,499,1,'2021-02-08 17:20:52',1,'2018-01-01 23:00:00',1),(476,0,1,555,1,'2021-02-08 17:20:52',1,'2018-06-30 03:13:38',0),(477,1,1,561,1,'2021-02-08 17:20:52',1,'2018-07-14 03:26:33',0),(478,1,1,564,1,'2021-02-08 17:20:52',1,'2018-07-22 09:41:20',0),(479,0,1,565,1,'2021-02-08 17:20:52',1,'2018-08-10 08:24:19',0),(480,0,1,566,1,'2021-02-08 17:20:52',1,'2018-08-10 08:32:35',0),(481,4,2,567,1,'2021-02-08 17:20:52',1,'2018-08-10 08:54:35',0),(482,0,1,578,1,'2021-02-08 17:20:52',1,'2018-09-12 13:46:24',0),(483,0,10,608,1,'2021-02-08 17:20:52',1,'2019-02-13 06:22:42',5),(485,0,1,582,1,'2021-02-08 17:20:52',1,'2018-09-15 12:17:32',0),(486,0,0,586,1,'2021-02-08 17:20:52',1,'2018-09-20 05:14:34',2),(487,0,10,587,1,'2021-02-08 17:20:52',1,'2018-10-14 07:52:02',0),(488,3,10,588,1,'2021-02-08 17:20:52',1,'2018-11-22 08:18:08',0),(489,0,10,609,1,'2021-02-08 17:20:52',1,'2019-04-13 18:52:38',0),(490,0,10,610,1,'2021-02-08 17:20:52',1,'2019-04-14 03:47:46',0),(491,1,0,611,1,'2021-02-08 17:20:52',1,'2019-04-24 07:30:12',0),(492,1,10,612,1,'2021-02-08 17:20:52',1,'2019-06-15 15:46:20',0),(493,0,4,613,1,'2021-02-08 17:20:52',1,'2019-06-27 05:58:03',0),(494,8,5,614,1,'2021-02-08 17:20:52',1,'2019-08-06 07:38:31',0),(495,0,10,618,1,'2021-02-08 17:20:52',1,'2019-10-15 11:47:52',1),(499,0,1,633,1,'2021-02-08 17:20:52',1,'2020-02-21 15:00:54',0),(500,0,4,634,1,'2021-02-08 17:20:52',1,'2020-03-23 11:25:26',0),(501,0,4,635,1,'2021-02-08 17:20:52',1,'2020-03-27 19:31:36',0),(502,3,4,636,1,'2021-02-08 17:20:52',1,'2020-03-30 06:11:21',0),(503,0,1,637,1,'2021-02-08 17:20:52',1,'2020-05-03 11:45:27',0),(504,0,0,638,1,'2021-02-08 17:20:52',1,'2020-12-07 08:56:25',0),(505,0,0,639,1,'2021-02-08 17:20:52',1,'2020-12-13 20:49:01',0),(506,0,0,640,1,'2021-02-08 17:20:52',1,'2020-12-13 20:50:31',0),(507,0,10,641,1,'2021-02-08 17:20:52',1,'2020-12-13 20:51:07',0),(508,0,10,642,1,'2021-02-08 17:20:52',1,'2020-12-13 20:51:33',0),(509,0,0,645,1,'2021-02-23 10:42:20',1,'2021-02-23 10:42:20',0),(510,0,0,646,1,'2021-02-23 16:51:16',1,'2021-02-23 16:51:16',0),(511,0,0,647,1,'2021-02-23 17:28:22',1,'2021-02-23 17:28:22',0);
/*!40000 ALTER TABLE `assetclass` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `stockexchange`
--

LOCK TABLES `stockexchange` WRITE;
/*!40000 ALTER TABLE `stockexchange` DISABLE KEYS */;
INSERT INTO `stockexchange` VALUES (217,'SIX Swiss Exchange','CH',1,0,'SIX','17:30:00','Europe/Zurich',1,'2021-02-08 17:48:20',1,'2020-12-05 12:04:45',4),(218,'NYSE Market (Amex)','US',1,0,'NYSE','16:00:00','America/New_York',1,'2021-02-08 17:48:20',1,'2020-12-12 12:08:24',4),(220,'Frankfurt','DE',1,0,'FSX','20:00:00','Europe/Berlin',1,'2021-02-08 17:48:20',1,'2020-12-05 12:08:08',3),(221,'Paris Euronext ','FR',1,0,'PAR','17:30:00','Europe/Paris',1,'2021-02-08 17:48:20',1,'2020-12-12 12:14:23',6),(222,'Bolsa de Madrid','ES',1,0,'MCE','17:30:00','Europe/Madrid',1,'2021-02-08 17:48:20',1,'2020-12-12 12:49:06',5),(223,'Milano Borsa Italiana','IT',1,0,'MIL','17:30:00','Europe/Rome',1,'2021-02-08 17:48:20',1,'2021-01-05 18:32:49',4),(224,'London Stock Exchange','GB',1,0,'LSE','16:30:00','Europe/London',1,'2021-02-08 17:48:20',1,'2020-12-05 12:06:50',3),(225,'Primary exchange Switzerland','CH',0,0,'---','18:00:00','Europe/Zurich',1,'2021-02-08 17:48:20',1,'2020-12-05 12:05:19',7),(226,'Vienna Stock Exchange','AT',1,0,'VIE','17:35:00','Europe/Vienna',1,'2021-02-08 17:48:20',1,'2021-01-05 14:48:32',5),(227,'Tokyo Stock Exchange','JP',1,0,'JPX','15:00:00','Asia/Tokyo',1,'2021-02-08 17:48:20',1,'2020-12-05 11:57:33',3),(228,'Hong Kong Stock Exchange','CN',1,0,'HKEX','16:00:00','Asia/Hong_Kong',1,'2021-02-08 17:48:20',1,'2020-12-05 12:07:21',3),(231,'Stockholm OMX','SE',1,0,'OMX','17:30:00','Europe/Stockholm',1,'2021-02-08 17:48:20',1,'2020-12-05 12:04:15',8),(232,'Oslo Stock Exchange','NO',1,0,'OSE','17:00:00','Europe/Oslo',1,'2021-02-08 17:48:20',1,'2020-12-05 12:05:38',3),(233,'NASDAQ Stock Exchange','US',1,0,'NAS','16:00:00','America/New_York',1,'2021-02-08 17:48:20',1,'2020-12-12 11:49:13',2),(234,'Copenhagen Stock Exchange','DK',1,0,'OMXC','18:00:00','Europe/Copenhagen',1,'2021-02-08 17:48:20',1,'2020-12-05 12:08:37',2),(235,'Private Papers','GB',0,1,'---','20:00:00','UCT',1,'2021-02-08 17:48:20',1,'2020-12-05 12:09:58',3),(236,'Berlin Stock Exchange','DE',1,0,'BER','18:00:00','Europe/Berlin',1,'2021-02-08 17:48:20',1,'2020-12-05 12:09:05',1),(237,'Zürcher Kantonalbank','CH',0,0,'ZKB','17:30:00','Europe/Zurich',1,'2021-02-08 17:48:20',1,'2019-10-25 05:53:50',1),(238,'Amsterdam Stock Exchange','NL',1,0,'XAMS','18:00:00','Europe/Amsterdam',1,'2021-02-08 17:48:20',1,'2020-12-05 12:09:19',1),(239,'BM&FBOVESPA (B3)','BR',1,0,'BMFBOVES','17:00:00','America/Sao_Paulo',1,'2021-02-08 17:48:20',1,'2020-12-05 12:08:55',2),(241,'Toronto Stock Exchange','CA',1,0,'TSX','18:00:00','America/Toronto',1,'2021-02-08 17:48:20',1,'2020-12-07 08:52:53',0),(242,'New Zealand Stock Exchange','NZ',1,0,'NZE','18:00:00','Pacific/Auckland',1,'2021-02-08 17:48:20',1,'2020-12-13 20:46:43',0),(243,'Australian Stock Exchange','AU',1,0,'ASX','18:00:00','Australia/Sydney',1,'2021-02-08 17:48:20',1,'2020-12-13 20:48:23',0);
/*!40000 ALTER TABLE `stockexchange` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `globalparameters`
--

LOCK TABLES `globalparameters` WRITE;
/*!40000 ALTER TABLE `globalparameters` DISABLE KEYS */;
INSERT INTO `globalparameters` VALUES ('gt.core.data.feed.start.date',NULL,NULL,'2000-01-01',NULL),('gt.cryptocurrency.history.connector',NULL,'gt.datafeed.cryptocompare',NULL,NULL),('gt.cryptocurrency.intra.connector',NULL,'gt.datafeed.cryptocompare',NULL,NULL),('gt.currency.history.connector',NULL,'gt.datafeed.exchangeratesapi',NULL,NULL),('gt.currency.intra.connector',NULL,'gt.datafeed.yahoo',NULL,NULL),('gt.history.max.filldays.currency',5,NULL,NULL,NULL),('gt.history.retry',4,NULL,NULL,NULL),('gt.historyquote.quality.update.date',NULL,NULL,'2021-03-27',NULL),('gt.intra.retry',4,NULL,NULL,NULL),('gt.limit.day.Assetclass',10,NULL,NULL,NULL),('gt.limit.day.MailSendbox',200,NULL,NULL,NULL),('gt.limit.day.Stockexchange',10,NULL,NULL,NULL),('gt.max.cash.account',25,NULL,NULL,NULL),('gt.max.limit.request.exceeded.count',20,NULL,NULL,NULL),('gt.max.portfolio',20,NULL,NULL,NULL),('gt.max.securities.currencies',1000,NULL,NULL,NULL),('gt.max.security.breach.count',5,NULL,NULL,NULL),('gt.max.watchlist',30,NULL,NULL,NULL),('gt.max.watchlist.length',200,NULL,NULL,NULL),('gt.sc.intra.update.timeout.seconds',300,NULL,NULL,NULL),('gt.securitysplit.append.date',NULL,NULL,'2021-03-27',NULL),('gt.source.demo.idtenant',22,NULL,NULL,NULL),('gt.w.intra.update.timeout.seconds',1200,NULL,NULL,NULL);
/*!40000 ALTER TABLE `globalparameters` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `imp_trans_platform`
--

LOCK TABLES `imp_trans_platform` WRITE;
/*!40000 ALTER TABLE `imp_trans_platform` DISABLE KEYS */;
INSERT INTO `imp_trans_platform` VALUES (1,'Swissquote','gt.platform.import.swissquote',7,'2017-12-31 23:00:00',7,'2018-05-17 12:16:50',3),(2,'Migros Bank','gt.platform.import.migros',7,'2017-12-31 23:00:00',1,'2021-02-25 15:23:50',4),(3,'E-Trading (Postfinance)','',7,'2017-12-31 23:00:00',7,'2018-07-02 13:38:06',2),(4,'CornèrTrader','gt.platform.import.cornertrader',7,'2017-12-31 23:00:00',7,'2018-01-01 23:00:00',1),(5,'Strateo',NULL,7,'2021-01-03 09:30:28',7,'2021-01-03 09:30:28',0),(6,'DEGIRO','gt.platform.import.degiro',1,'2021-02-23 08:48:33',1,'2021-03-01 11:13:29',1);
/*!40000 ALTER TABLE `imp_trans_platform` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `imp_trans_template`
--

LOCK TABLES `imp_trans_template` WRITE;
/*!40000 ALTER TABLE `imp_trans_template` DISABLE KEYS */;
INSERT INTO `imp_trans_template` VALUES (1,1,0,'Kauf und Verkauf Wertpapier (Wiederholung units)','Gland, {datetime|P|N}\n(?:Börsengeschäft:|Börsentransaktion:) {transType|P|N} Unsere Referenz: 49534746 \nGemäss Ihrem Kaufauftrag vom 28.01.2013 haben wir folgende Transaktionen vorgenommen:\nTitel Ort der Ausführung\nISHARES $ CORP BND ISIN: {isin|P} SIX Swiss Exchange\nNKN: 1613957\nAnzahl Preis Betrag\n{units|PL|R} {quotation} {cac} 8’000.00\nTotal USD 8’250.00\nKommission Swissquote Bank AG USD {tc1|SL|N|O}\n[Abgabe (Eidg. Stempelsteuer)|Eidgenössische Stempelsteuer] USD {tt1|SL|N}\nBörsengebühren USD {tc2|SL|N}\nZu Ihren Lasten USD {ta|SL|N}\n[END]\ntransType=ACCUMULATE|Kauf\ntransType=REDUCE|Verkauf\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n','2011-10-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:17:37',7),(2,1,0,'Kauf und Verkauf Wertpapier  (Wiederholung units)','Börsengeschäft                    REF : BO/28082356\nIhr {transType|P|N} vom {datetime|P|N}                                                      \nAbschlussort : SIX Swiss Exchange\nBEZEICHNUNG                                      VALOREN NR          \nISIN      \nDB X-TR MSEM                                     3\'614\'480\n{isin|P|NL}\nANZAHL                KURS                       BETRAG\n-{units|Pc|Nc|PL|R}-  {cac} {quotation}                  CHF          1.00\nAusländische Taxen USD {tt2|SL|N|O}\nCourtage SQB               CHF               {tc1|SL|N|O}\nEidg. Stempel              CHF               {tt1|SL|N}\nBörsengebühren             CHF               {tc2|SL|N|O}\n----------------------\nZU IHREN GUNSTEN CHF          {ta|SL|N}\n======================\nValuta      02.11.2010\n[END]\ntransType=ACCUMULATE|Kauf\ntransType=REDUCE|Verkauf\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n\n','2000-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:17:19',6),(3,2,0,'Kauf und Verkauf Wertpapier','IHR {transType|P|N} VOM {datetime|P|Nc}: SWX USD\n{units|P|PL}  UNITS -A USD- CAPITALISATION\nVALOR {sf1|P|N}\nZU {cin|P|PL} {quotation|SL|N|PL}\nBRUTTO                                        USD         24,750.00\nSTEUERN, MARKTGEBUEHREN, FREMDE SPESEN ETC.   USD  {tc2|SL|N|O}\nBRUTTO                                        USD         24,752.49\nKURS      {cex|P|O}                           CHF         31,151.00\nCOURTAGE                                      {cac|P|SL}  {tc1|SL|N}\nEIDG. UMSATZABGABE + SWX/EBK-GEBUEHR          CHF         {tt1|SL|N}\nB E R N                                         VALUTA 27.02.2007   CHF        {ta|SL|N}\n[END]\ntransType=ACCUMULATE|Kauf\ntransType=ACCUMULATE|KAUF\ntransType=REDUCE|Verkauf\ndateFormat=dd.MM.yyyy\n','2000-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:19:21',3),(4,2,0,'Kauf und Verkauf Wertpapier','Auftragsnummer AUF8909899\nAuftragsdatum 10.08.2017 Thun, {datetime|SL|N}\nBörsenabrechnung - {transType|SL|P}  - Teilausführung\nWir haben für Sie am 10.08.2017 verkauft.\nBörsenplatz: SIX Swiss Exchange\nAnteile -H CHF hedged-\nRaiffeisen ETF - Solid Gold Ounces\nValor: 13403486\nISIN: {isin|P|N}\n{units|PL|R} zu (?:...) {quotation} 15:14:45\nTotal Kurswert CHF 20\'216.00\nEidg. Umsatzabgabe CHF  {tt1|SL|N}\nCourtage CHF {tc1|SL|N}\nAusführungsgebühr Börse CHF {tc2|SL|N|O}\nNetto  {cac|SL|P} {ta|SL|N}\n[END]\ntransType=ACCUMULATE|Kauf\ntransType=REDUCE|Verkauf\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n\n','2015-08-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:19:35',6),(5,2,0,'Dividende','Am {datetime|P|N} wurde die {transType|P|N} zahlbar auf\n(?:.*Ex.Datum.*) {exdiv|P|N|O}\nProShares Short QQQ Zahlbar Datum: 02.07.2019\nValor: 25792502\nISIN: {isin|P|N}\nBestand: {units|P|N|SL} zu {cin|P|SL} {quotation|SL|N}\nBrutto (200 * USD 0.1838) USD 36.76\n(?:.*steuer.*)  {tt1|P|PL|N|O}\n(?:.*zu.*steuer.*)  {tt2|P|NL|N|O}\nNetto {cac|P|SL} {ta|SL|N}\nUnsere Gutschrift erfolgt auf Konto 00 .0.000.000.00 mit Valuta 28.11.2016.\n[END]\ntransType=DIVIDEND|Dividende\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n','2015-08-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:18:49',25),(6,3,0,'Ertrag Yellowtrade','{transType|P|N|NL}\n[SEITE|3002]\nWIR ERKENNEN SIE, EINGANG VORBEHALTEN, MIT FOLGENDEN TITELERTRAEGEN.\n{units|P|PL} ACT GBP SVN VALOREN-NR. 1.102.657\nGLAXOSMITHKLINE PLC ISIN {isin|P|N}\nIM DEPOT: DEUTSCHE BK FRANKFU.\nEX-DATUM : 10.05.2006\nVERFALLSDATUM : 06.07.2006\nCOUPON : 3\nEINHEITSPREIS TOTALBETRAEGE\nANGEKUENDIGT. ERTRAG  0,11 44,00\nSTEUERGUTHABEN 0,012225 {tt1|SL|N|O}\nBRUTTOERTRAG GBP {quotation|SL} GBP 1,00\n[STEUERGUTHABEN|STEUER] 00%  {tt1|SL|Nc|O}-\nNETTOERTRAG 44,00\nNETTOBETRAG {cin|P|NL} 44,00\nDIESEN BETRAG SCHREIBEN WIR DEM KONTO GUT\nR 0000.00.00 VALUTA : {datetime|PL} {cac|PL} {ta|PL|N}\nKURS : {cex|SL|N|O}\nSTEUERRUECKFORDERUNG\nSTEUER.GUTHABENVERR. GBP 4,89\nSTEUERVERRECHNUNG CHF 10,95\n[END]\ntransType=DIVIDEND|ERTRAGSABRECHNUNG\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n','2007-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:21:32',6),(7,3,0,'Kauf und Verkauf Wertpapier','Bern, {datetime|P|N}\nBörsentransaktion: {transType|P|N}\nUnsere Referenz: 142494149 \nGemäss Ihrem Kaufauftrag vom 08.03.2018 haben wir folgende Transaktionen vorgenommen:\nTitel Ort der Ausführung\n2.20 BALIFE 17-48 ISIN: {isin|P} SIX Swiss Exchange\nNKN: 37961100\nAnzahl Preis Betrag\n{units|PL|R} {quotation} {per|O} {cin} 28’450.00\nCHF\nMarchzinsen {ac|SL|N|O}\nTotal CHF 5\'068.60\nKommission CHF {tc1|SL|N|O}\nAbgabe (Eidg. Stempelsteuer) CHF {tt1|SL|N}\nBörsengebühren CHF {tc2|SL|N|O}\nZu Ihren Lasten {cac|SL} {ta|SL|N}\n[END]\ntransType=ACCUMULATE|Kauf\ntransType=REDUCE|Verkauf\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n','2016-05-16','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:22:58',5),(8,3,0,'Rückkaufangebot','Bern, 30.01.2018\n{transType|PL|P|N}\nUnsere Referenz: 140209352 \nWir haben folgende Transaktion auf Ihrem Konto vorgenommen:\nTitel\nOriginal Original Anzahl\nISIN: {isin|P|N}\n2.375 REPOWER 10-22 {units|PL|N}\nNKN: 10915272\nBewegung Ratio Anzahl\nhaben wir Ihrem Konto den folgenden Betrag gutgeschrieben:\nAusführungsdatum {datetime|P|N}\nAnzahl {units|P|N}\nPreis {quotation|P|SL} %\nTotal {cac|P|SL} {ta|SL|N}\n[END]\ntransType=REDUCE|Rückkaufangebot\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n\n','2016-05-16','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:23:53',4),(9,3,0,'Zins für Anleihen','Bern, {datetime|P|N}\n{transType|PL|P}\nUnsere Referenz: 139280138 \nIm Hinblick auf folgenden Titel:\nTitel\nBezeichnung Anzahl\nISIN: {isin|P|N}\n4 ALFA 13-18 20\'000\nAusführungsdatum 16.01.2018\nValutadatum\nNominal {units|P|N}\nZins {quotation|P|SL} {per|N|SL}\nBetrag {cin|P} 41.40\nWechselkurs {cex|P|N|O}\nVerrechnungssteuer 35% (CH) CHF {tt1|NL|N|O}\nTotal {cac|P|SL} {ta|SL|N}\n[END]\ntransType=DIVIDEND|Zins\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\notherFlagOptions=BOND_QUATION_CORRECTION\n','2016-05-16','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:32:31',23),(10,1,0,'Dividende alt (Wiederhlolung units/...)','KUNDE : 000\'000 HANS MUSTER Gland, {datetime|P|N}\n{transType|P|NL} REF : CN/90057162\nBEZEICHNUNG EX TAG VALOREN NR\nXmtch on (CH 29.03.2010 1\'985\'280\n{isin|NL|P|N}\nANZAHL DIVIDENDE BETRAG\n-{units|Pc|Nc|PL|R}-  {quotation} {cac}            \nVerrechnungssteuer 35.00 % CHF {tt1|SL|Nc|O}-\n----------------------\nZU IHREN GUNSTEN CHF {ta|SL|N}\n======================\nValuta 01.04.2010\n[END]\ntransType=DIVIDEND|Dividende\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n\n','2010-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 11:07:50',5),(11,1,0,'Dividende','Gland, {datetime|P|N}\n{transType|P|N} Unsere Referenz: 66622789 \nIm Hinblick auf folgenden Titel:\nTitel\nISHARES $ CORP BND ISIN: {isin|P} 490\nNKN: 1613957\nWir haben Ihrem Konto den folgenden Betrag gutgeschrieben: \nKontonummer 000000\nAusführungsdatum 21.05.2014\nValutadatum 11.06.2014\nAnzahl {units|P|N}\nDividende {quotation|P} USD\nBetrag {cac|P|PL} 416.11\nVerrechnungssteuer 35% (CH) CHF {tt1|SL|N|O}\nTotal USD {ta|SL|N}\n[END]\ntransType=DIVIDEND|Dividende\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n\n','2011-10-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 11:07:27',10),(12,2,0,'Dividende','Am {datetime|P|N} wurde die {transType|P|N} zahlbar auf \nNamen-Aktie Ex Datum: 29.03.2011 \nCisco Systems Inc Zahlbar Datum: 20.04.2011 \nValor: 918546 \nISIN: {isin|P|N} \nBestand: {units|P|N} zu {cin|P} {quotation|SL|N} \nBrutto (1 * USD 0.06) USD 0.06 \n15% Nicht rückforderbare Steuern (USD 1.00) USD {tt1|Nc|PL}-\n15% zusätzlicher Steuerrückbehalt USA (CHF 1.00) USD {tt2|Nc|NL}-\nNetto {cac|P} 105.00 \n[END]\ntransType=DIVIDEND|Dividende\ndateFormat=dd.MM.yyyy\n','2000-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:18:31',2),(13,1,0,'Sell (Wiederholung units)','Gland, {datetime|P|N}\nStock-Exchange Transaction: {transType|P|N} Our reference: 119126920 \nIn accordance with your sell order of 25.01.2017 we have carried out the following transactions:\nSecurity Place of execution\nDB X-TR MSCI EM ISIN: {isin|P} SIX Swiss Exchange\nNKN: 3067289\nQuantity Price Amount\n{units|PL|R} {quotation} {cac} 1\'000.00\nTotal USD 1\'000.00\nCommission Swissquote Bank Ltd USD {tc1|SL|N}\nTax (Federal stamp duty) USD {tt1|SL|N}\nStock exchange fee USD {tc2|SL|N}\nTo your credit USD {ta|SL|N}\n[END]\ntransType=ACCUMULATE|Buy\ntransType=REDUCE|Sell\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n','2011-10-01','en',1,'2021-02-23 09:27:22',1,'2021-02-28 15:17:53',5),(14,3,0,'Verkauf (ohne Wiederholung von units/quotation)','ABRECHNUNG {transType|P|N}\nSEITE 1\nDATUM DER TRANSAKTION : {datetime|Pc|N} BOERSE : XETRA\nIHR(E) KASSAVERKAUF 1.450\nVALOREN-NR. 000,485,864 ACT \"A\" EUR 2\nISIN {isin|P} ALCATEL\n{units|P|N} ZUM KURS VON {quotation|P|N}\nBRUTTOBETRAG EUR 14.848,00\nCOURTAGE SCHWEIZ EUR {tc1|SL|N}\nBOERSE GEBUEHR EUR {tc2|SL|N|O}\nEIDG. STEMPEL EUR {tt1|SL|N}\nBETRAG, DEN WIR DEM KONTO GUTSCHREIBEN {cac|SL|P} {ta|SL|N}\nEUR E 5116.67.03 GRAF HUGO\nVALUTA : 27.10.2006\n[END]\ntransType=REDUCE|BOERSENVERKAUF\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>','2000-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:24:49',4),(15,3,0,'Kapitalgewinn','Bern, 08.09.2017\n{transType|P|N|NL}\nUnsere Referenz: 130663278 \nIm Hinblick auf folgenden Titel:\nTitel\nBezeichnung Anzahl\nISIN: {isin|P|N}\nUBSETF MSCI Switzerland CHF 195\nNKN: 22627424\nhaben wir Ihrem Konto den folgenden Betrag gutgeschrieben:\nKontonummer 86961700\nAusführungsdatum 06.09.2017\nValutadatum {datetime|SL|N}\nAnzahl {units|P|N}\nKapitalgewinn {quotation|P} CHF\nBetrag CHF 11.70\nTotal {cac|SL} {ta|SL|N}\n[END]\ntransType=DIVIDEND|Kapitalgewinn\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>','2016-05-16','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:21:54',3),(16,3,0,'Kauf und Verkauf Yellowtrade Anleihe','IHR(E) {transType|P|N}\nCHF 30.000 1.625 % OBL CHF 2014 - 15.10.2024 (1) VALOREN-NR.: 025,359,276\nIMPLENIA AG, DIETLIKON ISIN : {isin|P|N|NL}\nDATUM DER TRANSAKTION : {datetime|P|N|SL}\nBOERSE : ELEKTRONISCHE BOERSE SCHWEIZ\n{cin|P|PL|R} {units|PL|N} ZUM KURS VON {quotation|P|PL} {per|PL} CHF 1.000,00\n+ ANGEFALLENE ZINSEN WAEHREND 12 TAGE CHF {ac|SL|N|O}\nD.H. VOM 15.10.2014 BIS AM 27.10.2014 360 / 360\nBRUTTOBETRAG CHF 29.401,25\nCOURTAGE SCHWEIZ CHF {tc1|SL|N}\nSEPARATE AUSFUEHRUNGSGEBUEHR CHF {tc2|SL|N|O}\nEIDG. STEMPEL CHF {tt1|SL|N|O}\nBETRAG, DEN WIR DEM KONTO BELASTEN {cac|SL} {ta|SL|N}\n[END]\ntransType=ACCUMULATE|KAUF,KASSAKAUF\ntransType=REDUCE|VERKAUF,KASSAVERKAUF\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>','2000-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:23:28',14),(17,3,0,'Zins für Anleihen','FOLGENDE {transType|P|N} WURDEN IHREM KONTO GUTGESCHRIEBEN, EINGANG\nVORBEHALTEN.\nCHF {units|PL} 2 % OBL CHF 2013 - 9.6.2022 (1) VALOREN-NR. 22.853.145\nHOLCIM LTD, RAPPERSWIL-JONA ISIN {isin|P|N}\nIM DEPOT: SIX SIS AG.\nVERFALLSDATUM : 09.06.2014 EX-DATUM : 04.06.2014 COUPON : 09.06.2014\nZINSSATZ : {quotation|P|SL} {per|N|SL}\nZINSSATZ / EINHEITSPREIS BETRAEGE\nBRUTTOERTRAG CHF 450,00\nSTEUER 35 % {tt1|SL|N|O}\nNETTOERTRAG 292,50\nNETTOBETRAG {cac|P|SL} 292,50\nDIESEN BETRAG SCHREIBEN WIR DEM KONTO GUT : R 5116.67.01 CHF {ta|SL|N}\nVALUTA : {datetime|P|N}\nSTEUERRUECKFORDERUNG\nSTEUERABZUG CHF 157,50\nDEPOTBANK BERN, DEN 10. JUNI 2014\n[END]\ntransType=DIVIDEND|WERTPAPIERERTRAEGE\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n','2007-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:25:19',3),(18,3,0,'Kassakauf Yellowtrade Aktie/ETF','DATUM DER TRANSAKTION : {datetime|P|N} BOERSE : XETRA\nIHR(E)  {transType|P|SL} 350\nVALOREN-NR. 001,647,069 ACT USD 0.01\nISIN {isin|P} DELL INC\n{units|P|N} ZUM KURS VON {quotation|P|N}\nBRUTTOBETRAG EUR 6.275,50\nCOURTAGE SCHWEIZ EUR {tc1|SL|N}\nBOERSE GEBUEHR CHF {tc2|SL|N|O}\nEIDG. STEMPEL EUR {tt1|SL|N}\nZUM KURS VON {cex|P|N|O}\nBETRAG, DEN WIR DEM KONTO BELASTEN {cac|P|SL} {ta|SL|N}\nEUR E 5116.67.03 GRAF HUGO\nVALUTA : 04.10.2006\n[END]\ntransType=ACCUMULATE|KASSAKAUF\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>','2000-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:22:29',6),(19,3,0,'Wertpapierertrag','FOLGENDE {transType|P|N} WURDEN IHREM KONTO GUTGESCHRIEBEN, EINGANG\nVORBEHALTEN.\n{units|P|PL} ACT GBP 0.25 VALOREN-NR. 1.102.657\nGLAXOSMITHKLINE PLC ISIN {isin|P|N}\nIM DEPOT: DEUTSCHE BK FRANKFU.\nVERFALLSDATUM : 04.01.2007 EX-DATUM : 01.11.2006 COUPON : 04.01.2007\nZINSSATZ / EINHEITSPREIS BETRAEGE\nANGEKUENDIGT. ERTRAG 0,12 48,00\nSTEUERGUTHABEN 0,013325 5,33\nBRUTTOERTRAG {cin|P} {quotation|SL} GBP 53,33\n[STEUER|STEUERGUTHABEN] {tt1|SL|N|O}\nNETTOERTRAG 48,00\nNETTOBETRAG GBP 48,00\nDIESEN BETRAG SCHREIBEN WIR DEM KONTO GUT : R 5116.67.01 {cac|SL} {ta||N|SL}\nVALUTA : {datetime|SL|N}\nKURS : {cex|P|N|O}\nSTEUERRUECKFORDERUNG\nSTEUER.GUTHABENVERR. GBP 5,33\nSTEUERVERRECHNUNG CHF 12,60\n[END]\ntransType=DIVIDEND|WERTPAPIERERTRAEGE\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>','2007-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:25:05',2),(20,3,0,'Kauf und Verkauf Yellowtrade Aktien/ETF','IHR(E) {transType|P|N}\n2 ACT GBP 0.25 VALOREN-NR.: 001,102,657\nGLAXOSMITHKLINE PLC ISIN : {isin|P|N|NL}\nDATUM DER TRANSAKTION : {datetime|P|N}\nBOERSE : XETRA\n{units|R|P|PL} ZUM KURS VON  {quotation}\nBRUTTOBETRAG {cin|P|SL} 44,80\nCOURTAGE SCHWEIZ EUR {tc1|SL|N}\nSEPARATE AUSFUEHRUNGSGEBUEHR CHF {tc2|SL|N|O}\nEIDG. STEMPEL EUR {tt1|SL|N}\nZU KURS VON {cex|P|N|O}\nBETRAG, DEN WIR DEM KONTO (?:GUTSCHREIBEN|BELASTEN) {cac|P|SL} {ta|SL|N}\nEUR E 5116.67.03 GRAF HUGO\nVALUTA : 19.02.2007\n[END]\ntransType=ACCUMULATE|KAUF,KASSAKAUF\ntransType=REDUCE|VERKAUF,KASSAVERKAUF\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>','2000-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:23:14',4),(21,3,0,'Rückzahlung Anleihe','Bern, 30.01.2018\n{transType|PL|P|N}\nUnsere Referenz: 140347410 \nIm Hinblick auf folgenden Titel:\nTitel\nBezeichnung Anzahl\nISIN: {isin|P|N}\nResidual Debt 10.05% 60\'627\nNKN: 32105978\nhaben wir Ihrem Konto den folgenden Betrag gutgeschrieben:\nKontonummer 86961700\nAusführungsdatum {datetime|P|N}\nValutadatum 29.01.2018\nAnzahl {units|P|N}\nPreis {quotation|P|SL} {per|SL|N}\nBetrag {cin|P} 41.40\nWechselkurs {cex|P|N|O}\nTotal {cac|P|SL} {ta|SL|N}\nBitte prüfen Sie dieses Dokument und benachrichtigen Sie uns bei Unstimmigkeiten innert Monatsfrist.\n[END]\ntransType=REDUCE|Rückzahlung\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>','2016-05-16','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:24:36',3),(22,3,0,'Rückzahlung Anleihe','WIR ERKENNEN SIE, EINGANG VORBEHALTEN, MIT FOLGENDEN {transType|P|N} TITELN.\n{cin|PL|P} {units|PL} 2 % NOTES CHF 2011 - 23.12.2015 (1) EMT VALOREN-NR 13.107.842\nENEL FINANCE INTERNATIONAL NV ISIN {isin|P|N}\nBEI: SIX SIS AG.\nVERFALLSDATUM 23.12.2015\nRUECKZAHLUNGSPREIS  {quotation|P|SL} {per|SL|N}\nRUECKZAHLUNG TOTALBETRAEGE\nKAPITALRUECKZAHLUNG CHF 50 000,00\nNETTOBETRAG CHF 50 000,00\nDIESE TITEL WERDEN IHREM DEPOT ENTNOMMEN\nDIESEN BETRAG SCHREIBEN WIR DEM KONTO GUT R 5116.67.01 {cac|SL} {ta|SL|N}\nVALUTA : {datetime|P|N}\n[END]\ntransType=REDUCE|RUECKZAHLBAREN\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>','2000-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:24:21',3),(23,3,0,'Dividende für Aktien/ETF','Bern, 25.04.2018\n{transType|PL|P|N}\nUnsere Referenz: 145360471 \nIm Hinblick auf folgenden Titel:\nTitel\nBezeichnung Anzahl\nISIN: {isin|P|N}\niShares Global HY Corp Bnd CHF 650\nNKN: 22134231\nhaben wir Ihrem Konto den folgenden Betrag gutgeschrieben:\nKontonummer 86961700\nAusführungsdatum {datetime|P|N}\nValutadatum 25.04.2018\nAnzahl {units|P|N}\nDividende {quotation|P|SL}  CHF\nBetrag CHF 1\'649.31\n[Quellensteuer|Verrechnungssteuer] 35% (CH) CHF {tt1|SL|N|O}\nTotal {cac|P|SL} {ta|SL|N}\n[END]\ntransType=DIVIDEND|Dividende\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>','2016-05-16','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:20:49',5),(24,3,0,'Kassakauf Yellowtrade Anleihe','DATUM DER TRANSAKTION : {datetime|P|N} BOERSE : ELEKTRONISCHE BOERSE SCHWEIZ\nIHR(E) {transType|P|SL} EUR 5.000\nVALOREN-NR. 000,715,243 4.125 % OBL EUR 1999 - 30.4.2009 (1)\nISIN  {isin|P} LANDWIRTSCHAFTLICHE RENTENBANK\n{cin|P|PL} {units|N|PL} ZUM KURS VON {quotation|P|PL} {per|PL} EUR 5.049,50\n+ ANGEFALLENE ZINSEN WAEHREND 219 TAGE EUR {ac|SL|N}\nD.H. VOM 30.04.2006 BIS AM 05.12.2006 366 / 366\nBRUTTOBETRAG EUR 5.173,25\nCOURTAGE SCHWEIZ EUR {tc1|SL|N}\nEIDG. STEMPEL EUR {tt1|SL|N}\nZUM KURS VON {cex|P|N|O}\nBETRAG, DEN WIR DEM KONTO BELASTEN {cac|P|SL} {ta|SL|N}\n[END]\ntransType=ACCUMULATE|KASSAKAUF\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>','2000-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:22:44',3),(25,3,0,'Dividends for Stock/ETF in english','Bern, 31.10.2017\n{transType|PL|P|N}\nOur reference: 133508877 \nRegarding the following security:\nSecurity\nDescription Quantity\nISIN: {isin|P|N}\niShares Global HY Corp Bnd CHF 650\nNKN: 22134231\nWe have credited the following amount to your account:\nAccount number 86961700\nExecution date {datetime|P|N}\nValue date 31.10.2017\nQuantity {units|P|N}\nDividend {quotation|P|SL} CHF\nAmount CHF 1\'083.03\nTotal {cac|P|SL} {ta|SL|N}\n[END]\ntransType=DIVIDEND|Dividend\ndateFormat=dd.MM.yyyy','2016-05-16','en',1,'2021-02-23 09:27:22',1,'2021-02-28 15:19:55',2),(26,3,0,'Rückkaufangebot Yellowtrade','GEMAESS DEN ERHALTENEN INSTRUKTIONEN NEHMEN WIR FOLGENDE BUCHUNG VOR :\nCHF {units|PL} 3.15 % NOTES CHF 2012 - 16.12.2016(1) VALOREN-NR 19,372,428\nLOAN PART ISIN {isin|P|N}\nVTB CAPITAL SA\nTITEL, DIE WIR IHREM DEPOT ENTNEHMEN IM DEPOT BEI : SIX SIS AG.\n{transType|PL|P} E.O./51068/AIM\nBRUTTOBETRAG CHF 30 588,00\nEIDG. STEMPEL AUSLAND CHF {tt1|SL|N}\nNETTOBETRAG CHF 30 542,10\nWELCHEN BETRAG WIR DEM KONTO GUTSCHREIBEN CHF R 5116.67.01 GRAF HUGO {cac|SL} {ta|SL|N}\nVALUTA : {datetime|P|N}\n[END]\ntransType=REDUCE|RUECKKAUFANGEBOT\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n','2000-01-01','de',1,'2021-02-08 17:22:16',1,'2021-02-28 15:24:07',6),(27,1,0,'Dividende Zeilenumbruch','Gland, {datetime|P|N}\n{transType|P|N} Unsere Referenz: 78811438 \nIm Hinblick auf folgenden Titel:\nTitel\nOriginal Original Anzahl\nISIN: {isin|P|N}\nISHARES $ CORP BND 490\nNKN: 1613957\nWir haben Ihrem Konto den folgenden Betrag gutgeschrieben: \nKontonummer 46595501\nAusführungsdatum 26.02.2015\nValutadatum 19.03.2015\nAnzahl\n{units|PL|P|N}\nDividende\n{quotation|PL|P} USD\nBetrag\nUSD\n430.17\n{cac|P|N|NL}\nTotal  {ta|SL|N}\n[END]\ntransType=DIVIDEND|Dividende\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n','2011-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 11:08:03',7),(28,4,0,'Kauf und Verkauf Wertpapier','Währung: {cac|P|N}\nKonto/Konten: 28500/702484CHF\nStock\nInstrument iShares J.P.Morgan $ EM Bond CHF Symbol {symbol|P|N}\nHedged UCITS ETF\nK/V {transType|P|SL} Open/Close ToOpen\nExchange Description SIX Swiss Exchange (ETFs) Börsenname oder OTC Exchange\nAnzahl {units|P|N} Preis {quotation|P|N}\nValutadatum 05-Dec-2016 Trade Value -18,640\nTradeTime {datetime|P|N} Trade-ID 1050435068\nOrder-ID 927439222 Spread Costs 0.00\nOrdertyp Not Available Total trading costs -50\nBuchungsbetrag-ID Handelsdatum Valutadatum Anzahl Umrechnungskurs Currency conversion cost Gebuchter Betrag\nCommission 4523005266 01-Dec-2016 05-Dec-2016 -22.37 1.0000 0.00 {tc1|SL|N}\nShare Amount 4523558817 01-Dec-2016 05-Dec-2016 -18,640.00 1.0000 0.00 -18,640.00\nSwiss Stamp Duty 4524956687 01-Dec-2016 05-Dec-2016 -27.96 1.0000 0.00 {tt1|SL|N}\nForeign\nAll bookings\nNettobetrag 0.00 {ta|SL|N}\nTrade Details Kunde:CT702484 2\nErstellt um:01-May-2018 5:59:26 AM (UTC) Konto/Konten:28500/702484CHF\n[END]\ntransType=ACCUMULATE|Bought\ntransType=REDUCE|Sold\ndateFormat=dd-MMM-yyyy hh:mm:ss a','2016-01-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:41:30',2),(29,1,1,'Transaction export (english)','datetime=Date\norder=Order #\ntransType=Transaction\nsymbol=Symbol\nsn=Name\nisin=ISIN\nunits=Quantity\nquotation=Unit price\ntt1=Costs\nac=Accrued interest\nta=Net Amount\ncac=Currency\n[END]\ntemplateId=1\ntransType=WITHDRAWAL|Paying out\ntransType=WITHDRAWAL|Fx debit comp.\ntransType=WITHDRAWAL|Forex debit\ntransType=DEPOSIT|Payment\ntransType=DEPOSIT|Forex credit\ntransType=DEPOSIT|Fx credit comp.\ntransType=INTEREST_CASHACCOUNT|Interests\ntransType=FEE|Custody Fees\ntransType=ACCUMULATE|Buy\ntransType=REDUCE|Sell\ntransType=DIVIDEND|Dividend\ndateFormat=dd.MM.yyyy HH:mm\ndelimiterField=;\nbond=quotation|%\noverRuleSeparators=All<’\'|.>\n','2010-01-01','en',1,'2021-02-08 17:22:16',1,'2021-03-01 16:37:30',18),(30,3,0,'Zins für Anleihen mit Quellensteuer','Bern, 16.01.2018\n{transType|PL|P}\nUnsere Referenz: 139280138 \nIm Hinblick auf folgenden Titel:\nTitel\nBezeichnung Anzahl\nISIN: {isin|P|N}\n4 ALFA 13-18 20\'000\nAusführungsdatum {datetime|P|N}\nValutadatum\nNominal {units|P|N}\nFür die Zahlung verwendeter Zinssatz {quotation|P|SL} {per|N|SL}\nBetrag {cin|P} 41.40\nQuellensteuer 15% (ZA) ZAR {tt1|SL|N|O}\nWechselkurs {cex|SL|N|O}\nTotal {cac|P|SL} {ta|SL|N}\n[END]\ntransType=DIVIDEND|Zins\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>','2016-05-16','de',1,'2021-02-23 09:27:22',1,'2021-03-02 15:54:33',4),(31,3,0,'Option Premium','Bern, 16.01.2018\n{transType|PL|P|N} Premium\nUnsere Referenz: 139280138 \nIm Hinblick auf folgenden Titel:\nTitel\nBezeichnung Anzahl\nISIN: {isin|P|N}\nHOCHDORF CV 3.5% 30.03.2020 15\'000\nAusführungsdatum {datetime|P|N}\nValutadatum\nAnzahl {units|P|N}\nPrämienanteil {quotation|P|SL} {per|N|SL}\nBetrag {cin|P} 41.40\nWechselkurs {cex|P|N|O}\nTotal {cac|P|SL} {ta|SL|N}\n[END]\ntransType=DIVIDEND|Option\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>','2016-05-16','de',1,'2021-02-23 09:27:22',1,'2021-02-28 15:23:41',1),(32,1,0,'Kapitalgewinn','Gland, {datetime|P|N}\n{transType|P|N} Unsere Referenz: 66622789 \nIm Hinblick auf folgenden Titel:\nTitel\nISHARES $ CORP BND ISIN: {isin|P} 490\nNKN: 1613957\nWir haben Ihrem Konto den folgenden Betrag gutgeschrieben: \nKontonummer 000000\nAusführungsdatum 21.05.2014\nValutadatum 11.06.2014\nAnzahl {units|P|N}\nKapitalgewinn {quotation|P} CHF\nBetrag {cac|SL|PL} 416.11\nTotal CHF {ta|SL|N}\n[END]\ntransType=DIVIDEND|Kapitalgewinn\ndateFormat=dd.MM.yyyy\noverRuleSeparators=All<’\'|.>\n\n\n','2011-10-01','de',1,'2021-02-23 09:27:22',1,'2021-02-28 11:08:23',7),(33,5,0,'Kauf und Verkauf','Belegnummer\n{transType|P|NL} {units|PL|NL} ISHARES ATX UCITS ETF ({isin|Pc|Nc|N}) um {quotation|NL|P} {cin|NL|N}\nInstrument: Trackers/ETF\nAuftragserstellung : 14/05/2020 09:56:14 CET\nAuftragstyp : Limit (22,50 EUR)\nGültigkeit : Day\nAusführungsdatum und -zeit : {datetime|SL|PL} 10:01:11 CET \nAusführungsort : XETRA\nBruttobetrag 13.500,00 EUR\nTransaktionskosten {tc1|P|SL} EUR\nStempelsteuer (Schweiz) {tt1|SL|PL|O} EUR\n{ta|PL|P} {cac|PL|N}\nLastschrift 13.555,25 EUR Valutadatum 18/05/2020\n[END]\ntransType=ACCUMULATE|Kauf\ntransType=REDUCE|Verkauf\ndateFormat=dd/MM/yyyy\noverRuleSeparators=All<.|,>\n\n','2015-01-01','de',1,'2021-02-23 09:27:22',1,'2021-03-19 07:28:57',13),(34,5,0,'Gutschrift','Typ: Foreign stocks\n{transType|P|N} Record date 17/12/2020\n{units|P|NL} Vanguard FTSE Japan ETF {quotation|NL} USD\nBruttobetrag 157,88 USD (139,71 CHF)\nVerwendete Wechselkurse 1 USD = 0,8849 CHF\nDiese Mitteilung dient ausschliesslich zu Informationszwecken. Sie ist und bleibt kein offizieller Beleg\nfür die Zuteilung oder Rückerstattung der erhobenen Steuer.\nNettoguthaben {ta|SL|P} {cac|SL|N} Datum  {datetime|SL|N}\nWertschriftenkonto:10/146992 Wertpapier:{isin|Pc|N} Belegnr.: CPN / 232207\n[END]\ntransType=DIVIDEND|GUTSCHRIFT\ndateFormat=dd/MM/yyyy\noverRuleSeparators=All<.|,>\n\n','2015-01-06','de',1,'2021-02-23 09:27:22',1,'2021-03-19 05:54:18',7),(36,6,1,'Kauf und Verkauf Aktien','date=Datum\ntime=Uhrzeit\nsn=Produkt\nisin=ISIN\nunits=Anzahl\nquotation=Kurs\ncin=cin\ncex=Wechselkurs\ntc1=Gebühr\ncct=cct\nta=Gesamt\ncac=cac\norder=Order-ID\n[END]\ntemplateId=1\ntransType=ACCUMULATE|Buy\ntransType=REDUCE|Sell\ndateFormat=dd.MM.yyyy\ntimeFormat=H:mm\ndelimiterField=;\n','2015-01-01','de',1,'2021-02-23 08:51:32',1,'2021-03-02 07:04:31',10),(37,1,1,'Transaktions Export','datetime=Date\norder=Order #\ntransType=Transaction\nsymbol=Symbol\nsn=Name\nisin=ISIN\nunits=Quantity\nquotation=Unit price\ntt1=Costs\nac=Accrued interest\nta=Net Amount\ncac=Currency\n[END]\ntemplateId=2\ntransType=WITHDRAWAL|Auszahlung\ntransType=WITHDRAWAL|Forex-Belastung\ntransType=WITHDRAWAL|Fx-Belastung Comp.\ntransType=DEPOSIT|Vergütung\ntransType=DEPOSIT|Forex-Gutschrift\ntransType=DEPOSIT|Fx-Gutschrift Comp.\ntransType=INTEREST_CASHACCOUNT|Zins\ntransType=FEE|Depotgebühren\ntransType=ACCUMULATE|Kauf\ntransType=REDUCE|Verkauf\ntransType=DIVIDEND|Dividende\ndateFormat=dd.MM.yyyy HH:mm\ndelimiterField=;\nbond=quotation|%\noverRuleSeparators=All<’\'|.>','2010-01-01','de',1,'2021-03-02 15:53:55',1,'2021-03-02 16:28:33',1);
/*!40000 ALTER TABLE `imp_trans_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `role`
--

LOCK TABLES `role` WRITE;
/*!40000 ALTER TABLE `role` DISABLE KEYS */;
INSERT INTO `role` VALUES (5,'ROLE_ADMIN'),(6,'ROLE_ALLEDIT'),(7,'ROLE_USER'),(8,'ROLE_LIMITEDIT');
/*!40000 ALTER TABLE `role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `trading_days_plus`
--

LOCK TABLES `trading_days_plus` WRITE;
/*!40000 ALTER TABLE `trading_days_plus` DISABLE KEYS */;
INSERT INTO `trading_days_plus` VALUES ('2000-01-03'),('2000-01-04'),('2000-01-05'),('2000-01-06'),('2000-01-07'),('2000-01-10'),('2000-01-11'),('2000-01-12'),('2000-01-13'),('2000-01-14'),('2000-01-17'),('2000-01-18'),('2000-01-19'),('2000-01-20'),('2000-01-21'),('2000-01-24'),('2000-01-25'),('2000-01-26'),('2000-01-27'),('2000-01-28'),('2000-01-31'),('2000-02-01'),('2000-02-02'),('2000-02-03'),('2000-02-04'),('2000-02-07'),('2000-02-08'),('2000-02-09'),('2000-02-10'),('2000-02-11'),('2000-02-14'),('2000-02-15'),('2000-02-16'),('2000-02-17'),('2000-02-18'),('2000-02-21'),('2000-02-22'),('2000-02-23'),('2000-02-24'),('2000-02-25'),('2000-02-28'),('2000-02-29'),('2000-03-01'),('2000-03-02'),('2000-03-03'),('2000-03-06'),('2000-03-07'),('2000-03-08'),('2000-03-09'),('2000-03-10'),('2000-03-13'),('2000-03-14'),('2000-03-15'),('2000-03-16'),('2000-03-17'),('2000-03-20'),('2000-03-21'),('2000-03-22'),('2000-03-23'),('2000-03-24'),('2000-03-27'),('2000-03-28'),('2000-03-29'),('2000-03-30'),('2000-03-31'),('2000-04-03'),('2000-04-04'),('2000-04-05'),('2000-04-06'),('2000-04-07'),('2000-04-10'),('2000-04-11'),('2000-04-12'),('2000-04-13'),('2000-04-14'),('2000-04-17'),('2000-04-18'),('2000-04-19'),('2000-04-20'),('2000-04-21'),('2000-04-24'),('2000-04-25'),('2000-04-26'),('2000-04-27'),('2000-04-28'),('2000-05-01'),('2000-05-02'),('2000-05-03'),('2000-05-04'),('2000-05-05'),('2000-05-08'),('2000-05-09'),('2000-05-10'),('2000-05-11'),('2000-05-12'),('2000-05-15'),('2000-05-16'),('2000-05-17'),('2000-05-18'),('2000-05-19'),('2000-05-22'),('2000-05-23'),('2000-05-24'),('2000-05-25'),('2000-05-26'),('2000-05-29'),('2000-05-30'),('2000-05-31'),('2000-06-01'),('2000-06-02'),('2000-06-05'),('2000-06-06'),('2000-06-07'),('2000-06-08'),('2000-06-09'),('2000-06-12'),('2000-06-13'),('2000-06-14'),('2000-06-15'),('2000-06-16'),('2000-06-19'),('2000-06-20'),('2000-06-21'),('2000-06-22'),('2000-06-23'),('2000-06-26'),('2000-06-27'),('2000-06-28'),('2000-06-29'),('2000-06-30'),('2000-07-03'),('2000-07-04'),('2000-07-05'),('2000-07-06'),('2000-07-07'),('2000-07-10'),('2000-07-11'),('2000-07-12'),('2000-07-13'),('2000-07-14'),('2000-07-17'),('2000-07-18'),('2000-07-19'),('2000-07-20'),('2000-07-21'),('2000-07-24'),('2000-07-25'),('2000-07-26'),('2000-07-27'),('2000-07-28'),('2000-07-31'),('2000-08-01'),('2000-08-02'),('2000-08-03'),('2000-08-04'),('2000-08-07'),('2000-08-08'),('2000-08-09'),('2000-08-10'),('2000-08-11'),('2000-08-14'),('2000-08-15'),('2000-08-16'),('2000-08-17'),('2000-08-18'),('2000-08-21'),('2000-08-22'),('2000-08-23'),('2000-08-24'),('2000-08-25'),('2000-08-28'),('2000-08-29'),('2000-08-30'),('2000-08-31'),('2000-09-01'),('2000-09-04'),('2000-09-05'),('2000-09-06'),('2000-09-07'),('2000-09-08'),('2000-09-11'),('2000-09-12'),('2000-09-13'),('2000-09-14'),('2000-09-15'),('2000-09-18'),('2000-09-19'),('2000-09-20'),('2000-09-21'),('2000-09-22'),('2000-09-25'),('2000-09-26'),('2000-09-27'),('2000-09-28'),('2000-09-29'),('2000-10-02'),('2000-10-03'),('2000-10-04'),('2000-10-05'),('2000-10-06'),('2000-10-09'),('2000-10-10'),('2000-10-11'),('2000-10-12'),('2000-10-13'),('2000-10-16'),('2000-10-17'),('2000-10-18'),('2000-10-19'),('2000-10-20'),('2000-10-23'),('2000-10-24'),('2000-10-25'),('2000-10-26'),('2000-10-27'),('2000-10-30'),('2000-10-31'),('2000-11-01'),('2000-11-02'),('2000-11-03'),('2000-11-06'),('2000-11-07'),('2000-11-08'),('2000-11-09'),('2000-11-10'),('2000-11-13'),('2000-11-14'),('2000-11-15'),('2000-11-16'),('2000-11-17'),('2000-11-20'),('2000-11-21'),('2000-11-22'),('2000-11-23'),('2000-11-24'),('2000-11-27'),('2000-11-28'),('2000-11-29'),('2000-11-30'),('2000-12-01'),('2000-12-04'),('2000-12-05'),('2000-12-06'),('2000-12-07'),('2000-12-08'),('2000-12-11'),('2000-12-12'),('2000-12-13'),('2000-12-14'),('2000-12-15'),('2000-12-18'),('2000-12-19'),('2000-12-20'),('2000-12-21'),('2000-12-22'),('2000-12-26'),('2000-12-27'),('2000-12-28'),('2000-12-29'),('2001-01-02'),('2001-01-03'),('2001-01-04'),('2001-01-05'),('2001-01-08'),('2001-01-09'),('2001-01-10'),('2001-01-11'),('2001-01-12'),('2001-01-15'),('2001-01-16'),('2001-01-17'),('2001-01-18'),('2001-01-19'),('2001-01-22'),('2001-01-23'),('2001-01-24'),('2001-01-25'),('2001-01-26'),('2001-01-29'),('2001-01-30'),('2001-01-31'),('2001-02-01'),('2001-02-02'),('2001-02-05'),('2001-02-06'),('2001-02-07'),('2001-02-08'),('2001-02-09'),('2001-02-12'),('2001-02-13'),('2001-02-14'),('2001-02-15'),('2001-02-16'),('2001-02-19'),('2001-02-20'),('2001-02-21'),('2001-02-22'),('2001-02-23'),('2001-02-26'),('2001-02-27'),('2001-02-28'),('2001-03-01'),('2001-03-02'),('2001-03-05'),('2001-03-06'),('2001-03-07'),('2001-03-08'),('2001-03-09'),('2001-03-12'),('2001-03-13'),('2001-03-14'),('2001-03-15'),('2001-03-16'),('2001-03-19'),('2001-03-20'),('2001-03-21'),('2001-03-22'),('2001-03-23'),('2001-03-26'),('2001-03-27'),('2001-03-28'),('2001-03-29'),('2001-03-30'),('2001-04-02'),('2001-04-03'),('2001-04-04'),('2001-04-05'),('2001-04-06'),('2001-04-09'),('2001-04-10'),('2001-04-11'),('2001-04-12'),('2001-04-13'),('2001-04-16'),('2001-04-17'),('2001-04-18'),('2001-04-19'),('2001-04-20'),('2001-04-23'),('2001-04-24'),('2001-04-25'),('2001-04-26'),('2001-04-27'),('2001-04-30'),('2001-05-01'),('2001-05-02'),('2001-05-03'),('2001-05-04'),('2001-05-07'),('2001-05-08'),('2001-05-09'),('2001-05-10'),('2001-05-11'),('2001-05-14'),('2001-05-15'),('2001-05-16'),('2001-05-17'),('2001-05-18'),('2001-05-21'),('2001-05-22'),('2001-05-23'),('2001-05-24'),('2001-05-25'),('2001-05-28'),('2001-05-29'),('2001-05-30'),('2001-05-31'),('2001-06-01'),('2001-06-04'),('2001-06-05'),('2001-06-06'),('2001-06-07'),('2001-06-08'),('2001-06-11'),('2001-06-12'),('2001-06-13'),('2001-06-14'),('2001-06-15'),('2001-06-18'),('2001-06-19'),('2001-06-20'),('2001-06-21'),('2001-06-22'),('2001-06-25'),('2001-06-26'),('2001-06-27'),('2001-06-28'),('2001-06-29'),('2001-07-02'),('2001-07-03'),('2001-07-04'),('2001-07-05'),('2001-07-06'),('2001-07-09'),('2001-07-10'),('2001-07-11'),('2001-07-12'),('2001-07-13'),('2001-07-16'),('2001-07-17'),('2001-07-18'),('2001-07-19'),('2001-07-20'),('2001-07-23'),('2001-07-24'),('2001-07-25'),('2001-07-26'),('2001-07-27'),('2001-07-30'),('2001-07-31'),('2001-08-01'),('2001-08-02'),('2001-08-03'),('2001-08-06'),('2001-08-07'),('2001-08-08'),('2001-08-09'),('2001-08-10'),('2001-08-13'),('2001-08-14'),('2001-08-15'),('2001-08-16'),('2001-08-17'),('2001-08-20'),('2001-08-21'),('2001-08-22'),('2001-08-23'),('2001-08-24'),('2001-08-27'),('2001-08-28'),('2001-08-29'),('2001-08-30'),('2001-08-31'),('2001-09-03'),('2001-09-04'),('2001-09-05'),('2001-09-06'),('2001-09-07'),('2001-09-10'),('2001-09-11'),('2001-09-12'),('2001-09-13'),('2001-09-14'),('2001-09-17'),('2001-09-18'),('2001-09-19'),('2001-09-20'),('2001-09-21'),('2001-09-24'),('2001-09-25'),('2001-09-26'),('2001-09-27'),('2001-09-28'),('2001-10-01'),('2001-10-02'),('2001-10-03'),('2001-10-04'),('2001-10-05'),('2001-10-08'),('2001-10-09'),('2001-10-10'),('2001-10-11'),('2001-10-12'),('2001-10-15'),('2001-10-16'),('2001-10-17'),('2001-10-18'),('2001-10-19'),('2001-10-22'),('2001-10-23'),('2001-10-24'),('2001-10-25'),('2001-10-26'),('2001-10-29'),('2001-10-30'),('2001-10-31'),('2001-11-01'),('2001-11-02'),('2001-11-05'),('2001-11-06'),('2001-11-07'),('2001-11-08'),('2001-11-09'),('2001-11-12'),('2001-11-13'),('2001-11-14'),('2001-11-15'),('2001-11-16'),('2001-11-19'),('2001-11-20'),('2001-11-21'),('2001-11-22'),('2001-11-23'),('2001-11-26'),('2001-11-27'),('2001-11-28'),('2001-11-29'),('2001-11-30'),('2001-12-03'),('2001-12-04'),('2001-12-05'),('2001-12-06'),('2001-12-07'),('2001-12-10'),('2001-12-11'),('2001-12-12'),('2001-12-13'),('2001-12-14'),('2001-12-17'),('2001-12-18'),('2001-12-19'),('2001-12-20'),('2001-12-21'),('2001-12-24'),('2001-12-26'),('2001-12-27'),('2001-12-28'),('2001-12-31'),('2002-01-02'),('2002-01-03'),('2002-01-04'),('2002-01-07'),('2002-01-08'),('2002-01-09'),('2002-01-10'),('2002-01-11'),('2002-01-14'),('2002-01-15'),('2002-01-16'),('2002-01-17'),('2002-01-18'),('2002-01-21'),('2002-01-22'),('2002-01-23'),('2002-01-24'),('2002-01-25'),('2002-01-28'),('2002-01-29'),('2002-01-30'),('2002-01-31'),('2002-02-01'),('2002-02-04'),('2002-02-05'),('2002-02-06'),('2002-02-07'),('2002-02-08'),('2002-02-11'),('2002-02-12'),('2002-02-13'),('2002-02-14'),('2002-02-15'),('2002-02-18'),('2002-02-19'),('2002-02-20'),('2002-02-21'),('2002-02-22'),('2002-02-25'),('2002-02-26'),('2002-02-27'),('2002-02-28'),('2002-03-01'),('2002-03-04'),('2002-03-05'),('2002-03-06'),('2002-03-07'),('2002-03-08'),('2002-03-11'),('2002-03-12'),('2002-03-13'),('2002-03-14'),('2002-03-15'),('2002-03-18'),('2002-03-19'),('2002-03-20'),('2002-03-21'),('2002-03-22'),('2002-03-25'),('2002-03-26'),('2002-03-27'),('2002-03-28'),('2002-03-29'),('2002-04-01'),('2002-04-02'),('2002-04-03'),('2002-04-04'),('2002-04-05'),('2002-04-08'),('2002-04-09'),('2002-04-10'),('2002-04-11'),('2002-04-12'),('2002-04-15'),('2002-04-16'),('2002-04-17'),('2002-04-18'),('2002-04-19'),('2002-04-22'),('2002-04-23'),('2002-04-24'),('2002-04-25'),('2002-04-26'),('2002-04-29'),('2002-04-30'),('2002-05-01'),('2002-05-02'),('2002-05-03'),('2002-05-06'),('2002-05-07'),('2002-05-08'),('2002-05-09'),('2002-05-10'),('2002-05-13'),('2002-05-14'),('2002-05-15'),('2002-05-16'),('2002-05-17'),('2002-05-20'),('2002-05-21'),('2002-05-22'),('2002-05-23'),('2002-05-24'),('2002-05-27'),('2002-05-28'),('2002-05-29'),('2002-05-30'),('2002-05-31'),('2002-06-03'),('2002-06-04'),('2002-06-05'),('2002-06-06'),('2002-06-07'),('2002-06-10'),('2002-06-11'),('2002-06-12'),('2002-06-13'),('2002-06-14'),('2002-06-17'),('2002-06-18'),('2002-06-19'),('2002-06-20'),('2002-06-21'),('2002-06-24'),('2002-06-25'),('2002-06-26'),('2002-06-27'),('2002-06-28'),('2002-07-01'),('2002-07-02'),('2002-07-03'),('2002-07-04'),('2002-07-05'),('2002-07-08'),('2002-07-09'),('2002-07-10'),('2002-07-11'),('2002-07-12'),('2002-07-15'),('2002-07-16'),('2002-07-17'),('2002-07-18'),('2002-07-19'),('2002-07-22'),('2002-07-23'),('2002-07-24'),('2002-07-25'),('2002-07-26'),('2002-07-29'),('2002-07-30'),('2002-07-31'),('2002-08-01'),('2002-08-02'),('2002-08-05'),('2002-08-06'),('2002-08-07'),('2002-08-08'),('2002-08-09'),('2002-08-12'),('2002-08-13'),('2002-08-14'),('2002-08-15'),('2002-08-16'),('2002-08-19'),('2002-08-20'),('2002-08-21'),('2002-08-22'),('2002-08-23'),('2002-08-26'),('2002-08-27'),('2002-08-28'),('2002-08-29'),('2002-08-30'),('2002-09-02'),('2002-09-03'),('2002-09-04'),('2002-09-05'),('2002-09-06'),('2002-09-09'),('2002-09-10'),('2002-09-11'),('2002-09-12'),('2002-09-13'),('2002-09-16'),('2002-09-17'),('2002-09-18'),('2002-09-19'),('2002-09-20'),('2002-09-23'),('2002-09-24'),('2002-09-25'),('2002-09-26'),('2002-09-27'),('2002-09-30'),('2002-10-01'),('2002-10-02'),('2002-10-03'),('2002-10-04'),('2002-10-07'),('2002-10-08'),('2002-10-09'),('2002-10-10'),('2002-10-11'),('2002-10-14'),('2002-10-15'),('2002-10-16'),('2002-10-17'),('2002-10-18'),('2002-10-21'),('2002-10-22'),('2002-10-23'),('2002-10-24'),('2002-10-25'),('2002-10-28'),('2002-10-29'),('2002-10-30'),('2002-10-31'),('2002-11-01'),('2002-11-04'),('2002-11-05'),('2002-11-06'),('2002-11-07'),('2002-11-08'),('2002-11-11'),('2002-11-12'),('2002-11-13'),('2002-11-14'),('2002-11-15'),('2002-11-18'),('2002-11-19'),('2002-11-20'),('2002-11-21'),('2002-11-22'),('2002-11-25'),('2002-11-26'),('2002-11-27'),('2002-11-28'),('2002-11-29'),('2002-12-02'),('2002-12-03'),('2002-12-04'),('2002-12-05'),('2002-12-06'),('2002-12-09'),('2002-12-10'),('2002-12-11'),('2002-12-12'),('2002-12-13'),('2002-12-16'),('2002-12-17'),('2002-12-18'),('2002-12-19'),('2002-12-20'),('2002-12-23'),('2002-12-24'),('2002-12-26'),('2002-12-27'),('2002-12-30'),('2002-12-31'),('2003-01-02'),('2003-01-03'),('2003-01-06'),('2003-01-07'),('2003-01-08'),('2003-01-09'),('2003-01-10'),('2003-01-13'),('2003-01-14'),('2003-01-15'),('2003-01-16'),('2003-01-17'),('2003-01-20'),('2003-01-21'),('2003-01-22'),('2003-01-23'),('2003-01-24'),('2003-01-27'),('2003-01-28'),('2003-01-29'),('2003-01-30'),('2003-01-31'),('2003-02-03'),('2003-02-04'),('2003-02-05'),('2003-02-06'),('2003-02-07'),('2003-02-10'),('2003-02-11'),('2003-02-12'),('2003-02-13'),('2003-02-14'),('2003-02-17'),('2003-02-18'),('2003-02-19'),('2003-02-20'),('2003-02-21'),('2003-02-24'),('2003-02-25'),('2003-02-26'),('2003-02-27'),('2003-02-28'),('2003-03-03'),('2003-03-04'),('2003-03-05'),('2003-03-06'),('2003-03-07'),('2003-03-10'),('2003-03-11'),('2003-03-12'),('2003-03-13'),('2003-03-14'),('2003-03-17'),('2003-03-18'),('2003-03-19'),('2003-03-20'),('2003-03-21'),('2003-03-24'),('2003-03-25'),('2003-03-26'),('2003-03-27'),('2003-03-28'),('2003-03-31'),('2003-04-01'),('2003-04-02'),('2003-04-03'),('2003-04-04'),('2003-04-07'),('2003-04-08'),('2003-04-09'),('2003-04-10'),('2003-04-11'),('2003-04-14'),('2003-04-15'),('2003-04-16'),('2003-04-17'),('2003-04-18'),('2003-04-21'),('2003-04-22'),('2003-04-23'),('2003-04-24'),('2003-04-25'),('2003-04-28'),('2003-04-29'),('2003-04-30'),('2003-05-01'),('2003-05-02'),('2003-05-05'),('2003-05-06'),('2003-05-07'),('2003-05-08'),('2003-05-09'),('2003-05-12'),('2003-05-13'),('2003-05-14'),('2003-05-15'),('2003-05-16'),('2003-05-19'),('2003-05-20'),('2003-05-21'),('2003-05-22'),('2003-05-23'),('2003-05-26'),('2003-05-27'),('2003-05-28'),('2003-05-29'),('2003-05-30'),('2003-06-02'),('2003-06-03'),('2003-06-04'),('2003-06-05'),('2003-06-06'),('2003-06-09'),('2003-06-10'),('2003-06-11'),('2003-06-12'),('2003-06-13'),('2003-06-16'),('2003-06-17'),('2003-06-18'),('2003-06-19'),('2003-06-20'),('2003-06-23'),('2003-06-24'),('2003-06-25'),('2003-06-26'),('2003-06-27'),('2003-06-30'),('2003-07-01'),('2003-07-02'),('2003-07-03'),('2003-07-04'),('2003-07-07'),('2003-07-08'),('2003-07-09'),('2003-07-10'),('2003-07-11'),('2003-07-14'),('2003-07-15'),('2003-07-16'),('2003-07-17'),('2003-07-18'),('2003-07-21'),('2003-07-22'),('2003-07-23'),('2003-07-24'),('2003-07-25'),('2003-07-28'),('2003-07-29'),('2003-07-30'),('2003-07-31'),('2003-08-01'),('2003-08-04'),('2003-08-05'),('2003-08-06'),('2003-08-07'),('2003-08-08'),('2003-08-11'),('2003-08-12'),('2003-08-13'),('2003-08-14'),('2003-08-15'),('2003-08-18'),('2003-08-19'),('2003-08-20'),('2003-08-21'),('2003-08-22'),('2003-08-25'),('2003-08-26'),('2003-08-27'),('2003-08-28'),('2003-08-29'),('2003-09-01'),('2003-09-02'),('2003-09-03'),('2003-09-04'),('2003-09-05'),('2003-09-08'),('2003-09-09'),('2003-09-10'),('2003-09-11'),('2003-09-12'),('2003-09-15'),('2003-09-16'),('2003-09-17'),('2003-09-18'),('2003-09-19'),('2003-09-22'),('2003-09-23'),('2003-09-24'),('2003-09-25'),('2003-09-26'),('2003-09-29'),('2003-09-30'),('2003-10-01'),('2003-10-02'),('2003-10-03'),('2003-10-06'),('2003-10-07'),('2003-10-08'),('2003-10-09'),('2003-10-10'),('2003-10-13'),('2003-10-14'),('2003-10-15'),('2003-10-16'),('2003-10-17'),('2003-10-20'),('2003-10-21'),('2003-10-22'),('2003-10-23'),('2003-10-24'),('2003-10-27'),('2003-10-28'),('2003-10-29'),('2003-10-30'),('2003-10-31'),('2003-11-03'),('2003-11-04'),('2003-11-05'),('2003-11-06'),('2003-11-07'),('2003-11-10'),('2003-11-11'),('2003-11-12'),('2003-11-13'),('2003-11-14'),('2003-11-17'),('2003-11-18'),('2003-11-19'),('2003-11-20'),('2003-11-21'),('2003-11-24'),('2003-11-25'),('2003-11-26'),('2003-11-27'),('2003-11-28'),('2003-12-01'),('2003-12-02'),('2003-12-03'),('2003-12-04'),('2003-12-05'),('2003-12-08'),('2003-12-09'),('2003-12-10'),('2003-12-11'),('2003-12-12'),('2003-12-15'),('2003-12-16'),('2003-12-17'),('2003-12-18'),('2003-12-19'),('2003-12-22'),('2003-12-23'),('2003-12-24'),('2003-12-26'),('2003-12-29'),('2003-12-30'),('2003-12-31'),('2004-01-02'),('2004-01-05'),('2004-01-06'),('2004-01-07'),('2004-01-08'),('2004-01-09'),('2004-01-12'),('2004-01-13'),('2004-01-14'),('2004-01-15'),('2004-01-16'),('2004-01-19'),('2004-01-20'),('2004-01-21'),('2004-01-22'),('2004-01-23'),('2004-01-26'),('2004-01-27'),('2004-01-28'),('2004-01-29'),('2004-01-30'),('2004-02-02'),('2004-02-03'),('2004-02-04'),('2004-02-05'),('2004-02-06'),('2004-02-09'),('2004-02-10'),('2004-02-11'),('2004-02-12'),('2004-02-13'),('2004-02-16'),('2004-02-17'),('2004-02-18'),('2004-02-19'),('2004-02-20'),('2004-02-23'),('2004-02-24'),('2004-02-25'),('2004-02-26'),('2004-02-27'),('2004-03-01'),('2004-03-02'),('2004-03-03'),('2004-03-04'),('2004-03-05'),('2004-03-08'),('2004-03-09'),('2004-03-10'),('2004-03-11'),('2004-03-12'),('2004-03-15'),('2004-03-16'),('2004-03-17'),('2004-03-18'),('2004-03-19'),('2004-03-22'),('2004-03-23'),('2004-03-24'),('2004-03-25'),('2004-03-26'),('2004-03-29'),('2004-03-30'),('2004-03-31'),('2004-04-01'),('2004-04-02'),('2004-04-05'),('2004-04-06'),('2004-04-07'),('2004-04-08'),('2004-04-09'),('2004-04-12'),('2004-04-13'),('2004-04-14'),('2004-04-15'),('2004-04-16'),('2004-04-19'),('2004-04-20'),('2004-04-21'),('2004-04-22'),('2004-04-23'),('2004-04-26'),('2004-04-27'),('2004-04-28'),('2004-04-29'),('2004-04-30'),('2004-05-03'),('2004-05-04'),('2004-05-05'),('2004-05-06'),('2004-05-07'),('2004-05-10'),('2004-05-11'),('2004-05-12'),('2004-05-13'),('2004-05-14'),('2004-05-17'),('2004-05-18'),('2004-05-19'),('2004-05-20'),('2004-05-21'),('2004-05-24'),('2004-05-25'),('2004-05-26'),('2004-05-27'),('2004-05-28'),('2004-05-31'),('2004-06-01'),('2004-06-02'),('2004-06-03'),('2004-06-04'),('2004-06-07'),('2004-06-08'),('2004-06-09'),('2004-06-10'),('2004-06-11'),('2004-06-14'),('2004-06-15'),('2004-06-16'),('2004-06-17'),('2004-06-18'),('2004-06-21'),('2004-06-22'),('2004-06-23'),('2004-06-24'),('2004-06-25'),('2004-06-28'),('2004-06-29'),('2004-06-30'),('2004-07-01'),('2004-07-02'),('2004-07-05'),('2004-07-06'),('2004-07-07'),('2004-07-08'),('2004-07-09'),('2004-07-12'),('2004-07-13'),('2004-07-14'),('2004-07-15'),('2004-07-16'),('2004-07-19'),('2004-07-20'),('2004-07-21'),('2004-07-22'),('2004-07-23'),('2004-07-26'),('2004-07-27'),('2004-07-28'),('2004-07-29'),('2004-07-30'),('2004-08-02'),('2004-08-03'),('2004-08-04'),('2004-08-05'),('2004-08-06'),('2004-08-09'),('2004-08-10'),('2004-08-11'),('2004-08-12'),('2004-08-13'),('2004-08-16'),('2004-08-17'),('2004-08-18'),('2004-08-19'),('2004-08-20'),('2004-08-23'),('2004-08-24'),('2004-08-25'),('2004-08-26'),('2004-08-27'),('2004-08-30'),('2004-08-31'),('2004-09-01'),('2004-09-02'),('2004-09-03'),('2004-09-06'),('2004-09-07'),('2004-09-08'),('2004-09-09'),('2004-09-10'),('2004-09-13'),('2004-09-14'),('2004-09-15'),('2004-09-16'),('2004-09-17'),('2004-09-20'),('2004-09-21'),('2004-09-22'),('2004-09-23'),('2004-09-24'),('2004-09-27'),('2004-09-28'),('2004-09-29'),('2004-09-30'),('2004-10-01'),('2004-10-04'),('2004-10-05'),('2004-10-06'),('2004-10-07'),('2004-10-08'),('2004-10-11'),('2004-10-12'),('2004-10-13'),('2004-10-14'),('2004-10-15'),('2004-10-18'),('2004-10-19'),('2004-10-20'),('2004-10-21'),('2004-10-22'),('2004-10-25'),('2004-10-26'),('2004-10-27'),('2004-10-28'),('2004-10-29'),('2004-11-01'),('2004-11-02'),('2004-11-03'),('2004-11-04'),('2004-11-05'),('2004-11-08'),('2004-11-09'),('2004-11-10'),('2004-11-11'),('2004-11-12'),('2004-11-15'),('2004-11-16'),('2004-11-17'),('2004-11-18'),('2004-11-19'),('2004-11-22'),('2004-11-23'),('2004-11-24'),('2004-11-25'),('2004-11-26'),('2004-11-29'),('2004-11-30'),('2004-12-01'),('2004-12-02'),('2004-12-03'),('2004-12-06'),('2004-12-07'),('2004-12-08'),('2004-12-09'),('2004-12-10'),('2004-12-13'),('2004-12-14'),('2004-12-15'),('2004-12-16'),('2004-12-17'),('2004-12-20'),('2004-12-21'),('2004-12-22'),('2004-12-23'),('2004-12-24'),('2004-12-27'),('2004-12-28'),('2004-12-29'),('2004-12-30'),('2004-12-31'),('2005-01-03'),('2005-01-04'),('2005-01-05'),('2005-01-06'),('2005-01-07'),('2005-01-10'),('2005-01-11'),('2005-01-12'),('2005-01-13'),('2005-01-14'),('2005-01-17'),('2005-01-18'),('2005-01-19'),('2005-01-20'),('2005-01-21'),('2005-01-24'),('2005-01-25'),('2005-01-26'),('2005-01-27'),('2005-01-28'),('2005-01-31'),('2005-02-01'),('2005-02-02'),('2005-02-03'),('2005-02-04'),('2005-02-07'),('2005-02-08'),('2005-02-09'),('2005-02-10'),('2005-02-11'),('2005-02-14'),('2005-02-15'),('2005-02-16'),('2005-02-17'),('2005-02-18'),('2005-02-21'),('2005-02-22'),('2005-02-23'),('2005-02-24'),('2005-02-25'),('2005-02-28'),('2005-03-01'),('2005-03-02'),('2005-03-03'),('2005-03-04'),('2005-03-07'),('2005-03-08'),('2005-03-09'),('2005-03-10'),('2005-03-11'),('2005-03-14'),('2005-03-15'),('2005-03-16'),('2005-03-17'),('2005-03-18'),('2005-03-21'),('2005-03-22'),('2005-03-23'),('2005-03-24'),('2005-03-25'),('2005-03-28'),('2005-03-29'),('2005-03-30'),('2005-03-31'),('2005-04-01'),('2005-04-04'),('2005-04-05'),('2005-04-06'),('2005-04-07'),('2005-04-08'),('2005-04-11'),('2005-04-12'),('2005-04-13'),('2005-04-14'),('2005-04-15'),('2005-04-18'),('2005-04-19'),('2005-04-20'),('2005-04-21'),('2005-04-22'),('2005-04-25'),('2005-04-26'),('2005-04-27'),('2005-04-28'),('2005-04-29'),('2005-05-02'),('2005-05-03'),('2005-05-04'),('2005-05-05'),('2005-05-06'),('2005-05-09'),('2005-05-10'),('2005-05-11'),('2005-05-12'),('2005-05-13'),('2005-05-16'),('2005-05-17'),('2005-05-18'),('2005-05-19'),('2005-05-20'),('2005-05-23'),('2005-05-24'),('2005-05-25'),('2005-05-26'),('2005-05-27'),('2005-05-30'),('2005-05-31'),('2005-06-01'),('2005-06-02'),('2005-06-03'),('2005-06-06'),('2005-06-07'),('2005-06-08'),('2005-06-09'),('2005-06-10'),('2005-06-13'),('2005-06-14'),('2005-06-15'),('2005-06-16'),('2005-06-17'),('2005-06-20'),('2005-06-21'),('2005-06-22'),('2005-06-23'),('2005-06-24'),('2005-06-27'),('2005-06-28'),('2005-06-29'),('2005-06-30'),('2005-07-01'),('2005-07-04'),('2005-07-05'),('2005-07-06'),('2005-07-07'),('2005-07-08'),('2005-07-11'),('2005-07-12'),('2005-07-13'),('2005-07-14'),('2005-07-15'),('2005-07-18'),('2005-07-19'),('2005-07-20'),('2005-07-21'),('2005-07-22'),('2005-07-25'),('2005-07-26'),('2005-07-27'),('2005-07-28'),('2005-07-29'),('2005-08-01'),('2005-08-02'),('2005-08-03'),('2005-08-04'),('2005-08-05'),('2005-08-08'),('2005-08-09'),('2005-08-10'),('2005-08-11'),('2005-08-12'),('2005-08-15'),('2005-08-16'),('2005-08-17'),('2005-08-18'),('2005-08-19'),('2005-08-22'),('2005-08-23'),('2005-08-24'),('2005-08-25'),('2005-08-26'),('2005-08-29'),('2005-08-30'),('2005-08-31'),('2005-09-01'),('2005-09-02'),('2005-09-05'),('2005-09-06'),('2005-09-07'),('2005-09-08'),('2005-09-09'),('2005-09-12'),('2005-09-13'),('2005-09-14'),('2005-09-15'),('2005-09-16'),('2005-09-19'),('2005-09-20'),('2005-09-21'),('2005-09-22'),('2005-09-23'),('2005-09-26'),('2005-09-27'),('2005-09-28'),('2005-09-29'),('2005-09-30'),('2005-10-03'),('2005-10-04'),('2005-10-05'),('2005-10-06'),('2005-10-07'),('2005-10-10'),('2005-10-11'),('2005-10-12'),('2005-10-13'),('2005-10-14'),('2005-10-17'),('2005-10-18'),('2005-10-19'),('2005-10-20'),('2005-10-21'),('2005-10-24'),('2005-10-25'),('2005-10-26'),('2005-10-27'),('2005-10-28'),('2005-10-31'),('2005-11-01'),('2005-11-02'),('2005-11-03'),('2005-11-04'),('2005-11-07'),('2005-11-08'),('2005-11-09'),('2005-11-10'),('2005-11-11'),('2005-11-14'),('2005-11-15'),('2005-11-16'),('2005-11-17'),('2005-11-18'),('2005-11-21'),('2005-11-22'),('2005-11-23'),('2005-11-24'),('2005-11-25'),('2005-11-28'),('2005-11-29'),('2005-11-30'),('2005-12-01'),('2005-12-02'),('2005-12-05'),('2005-12-06'),('2005-12-07'),('2005-12-08'),('2005-12-09'),('2005-12-12'),('2005-12-13'),('2005-12-14'),('2005-12-15'),('2005-12-16'),('2005-12-19'),('2005-12-20'),('2005-12-21'),('2005-12-22'),('2005-12-23'),('2005-12-26'),('2005-12-27'),('2005-12-28'),('2005-12-29'),('2005-12-30'),('2006-01-02'),('2006-01-03'),('2006-01-04'),('2006-01-05'),('2006-01-06'),('2006-01-09'),('2006-01-10'),('2006-01-11'),('2006-01-12'),('2006-01-13'),('2006-01-16'),('2006-01-17'),('2006-01-18'),('2006-01-19'),('2006-01-20'),('2006-01-23'),('2006-01-24'),('2006-01-25'),('2006-01-26'),('2006-01-27'),('2006-01-30'),('2006-01-31'),('2006-02-01'),('2006-02-02'),('2006-02-03'),('2006-02-06'),('2006-02-07'),('2006-02-08'),('2006-02-09'),('2006-02-10'),('2006-02-13'),('2006-02-14'),('2006-02-15'),('2006-02-16'),('2006-02-17'),('2006-02-20'),('2006-02-21'),('2006-02-22'),('2006-02-23'),('2006-02-24'),('2006-02-27'),('2006-02-28'),('2006-03-01'),('2006-03-02'),('2006-03-03'),('2006-03-06'),('2006-03-07'),('2006-03-08'),('2006-03-09'),('2006-03-10'),('2006-03-13'),('2006-03-14'),('2006-03-15'),('2006-03-16'),('2006-03-17'),('2006-03-20'),('2006-03-21'),('2006-03-22'),('2006-03-23'),('2006-03-24'),('2006-03-27'),('2006-03-28'),('2006-03-29'),('2006-03-30'),('2006-03-31'),('2006-04-03'),('2006-04-04'),('2006-04-05'),('2006-04-06'),('2006-04-07'),('2006-04-10'),('2006-04-11'),('2006-04-12'),('2006-04-13'),('2006-04-14'),('2006-04-17'),('2006-04-18'),('2006-04-19'),('2006-04-20'),('2006-04-21'),('2006-04-24'),('2006-04-25'),('2006-04-26'),('2006-04-27'),('2006-04-28'),('2006-05-01'),('2006-05-02'),('2006-05-03'),('2006-05-04'),('2006-05-05'),('2006-05-08'),('2006-05-09'),('2006-05-10'),('2006-05-11'),('2006-05-12'),('2006-05-15'),('2006-05-16'),('2006-05-17'),('2006-05-18'),('2006-05-19'),('2006-05-22'),('2006-05-23'),('2006-05-24'),('2006-05-25'),('2006-05-26'),('2006-05-29'),('2006-05-30'),('2006-05-31'),('2006-06-01'),('2006-06-02'),('2006-06-05'),('2006-06-06'),('2006-06-07'),('2006-06-08'),('2006-06-09'),('2006-06-12'),('2006-06-13'),('2006-06-14'),('2006-06-15'),('2006-06-16'),('2006-06-19'),('2006-06-20'),('2006-06-21'),('2006-06-22'),('2006-06-23'),('2006-06-26'),('2006-06-27'),('2006-06-28'),('2006-06-29'),('2006-06-30'),('2006-07-03'),('2006-07-04'),('2006-07-05'),('2006-07-06'),('2006-07-07'),('2006-07-10'),('2006-07-11'),('2006-07-12'),('2006-07-13'),('2006-07-14'),('2006-07-17'),('2006-07-18'),('2006-07-19'),('2006-07-20'),('2006-07-21'),('2006-07-24'),('2006-07-25'),('2006-07-26'),('2006-07-27'),('2006-07-28'),('2006-07-31'),('2006-08-01'),('2006-08-02'),('2006-08-03'),('2006-08-04'),('2006-08-07'),('2006-08-08'),('2006-08-09'),('2006-08-10'),('2006-08-11'),('2006-08-14'),('2006-08-15'),('2006-08-16'),('2006-08-17'),('2006-08-18'),('2006-08-21'),('2006-08-22'),('2006-08-23'),('2006-08-24'),('2006-08-25'),('2006-08-28'),('2006-08-29'),('2006-08-30'),('2006-08-31'),('2006-09-01'),('2006-09-04'),('2006-09-05'),('2006-09-06'),('2006-09-07'),('2006-09-08'),('2006-09-11'),('2006-09-12'),('2006-09-13'),('2006-09-14'),('2006-09-15'),('2006-09-18'),('2006-09-19'),('2006-09-20'),('2006-09-21'),('2006-09-22'),('2006-09-25'),('2006-09-26'),('2006-09-27'),('2006-09-28'),('2006-09-29'),('2006-10-02'),('2006-10-03'),('2006-10-04'),('2006-10-05'),('2006-10-06'),('2006-10-09'),('2006-10-10'),('2006-10-11'),('2006-10-12'),('2006-10-13'),('2006-10-16'),('2006-10-17'),('2006-10-18'),('2006-10-19'),('2006-10-20'),('2006-10-23'),('2006-10-24'),('2006-10-25'),('2006-10-26'),('2006-10-27'),('2006-10-30'),('2006-10-31'),('2006-11-01'),('2006-11-02'),('2006-11-03'),('2006-11-06'),('2006-11-07'),('2006-11-08'),('2006-11-09'),('2006-11-10'),('2006-11-13'),('2006-11-14'),('2006-11-15'),('2006-11-16'),('2006-11-17'),('2006-11-20'),('2006-11-21'),('2006-11-22'),('2006-11-23'),('2006-11-24'),('2006-11-27'),('2006-11-28'),('2006-11-29'),('2006-11-30'),('2006-12-01'),('2006-12-04'),('2006-12-05'),('2006-12-06'),('2006-12-07'),('2006-12-08'),('2006-12-11'),('2006-12-12'),('2006-12-13'),('2006-12-14'),('2006-12-15'),('2006-12-18'),('2006-12-19'),('2006-12-20'),('2006-12-21'),('2006-12-22'),('2006-12-26'),('2006-12-27'),('2006-12-28'),('2006-12-29'),('2007-01-02'),('2007-01-03'),('2007-01-04'),('2007-01-05'),('2007-01-08'),('2007-01-09'),('2007-01-10'),('2007-01-11'),('2007-01-12'),('2007-01-15'),('2007-01-16'),('2007-01-17'),('2007-01-18'),('2007-01-19'),('2007-01-22'),('2007-01-23'),('2007-01-24'),('2007-01-25'),('2007-01-26'),('2007-01-29'),('2007-01-30'),('2007-01-31'),('2007-02-01'),('2007-02-02'),('2007-02-05'),('2007-02-06'),('2007-02-07'),('2007-02-08'),('2007-02-09'),('2007-02-12'),('2007-02-13'),('2007-02-14'),('2007-02-15'),('2007-02-16'),('2007-02-19'),('2007-02-20'),('2007-02-21'),('2007-02-22'),('2007-02-23'),('2007-02-26'),('2007-02-27'),('2007-02-28'),('2007-03-01'),('2007-03-02'),('2007-03-05'),('2007-03-06'),('2007-03-07'),('2007-03-08'),('2007-03-09'),('2007-03-12'),('2007-03-13'),('2007-03-14'),('2007-03-15'),('2007-03-16'),('2007-03-19'),('2007-03-20'),('2007-03-21'),('2007-03-22'),('2007-03-23'),('2007-03-26'),('2007-03-27'),('2007-03-28'),('2007-03-29'),('2007-03-30'),('2007-04-02'),('2007-04-03'),('2007-04-04'),('2007-04-05'),('2007-04-06'),('2007-04-09'),('2007-04-10'),('2007-04-11'),('2007-04-12'),('2007-04-13'),('2007-04-16'),('2007-04-17'),('2007-04-18'),('2007-04-19'),('2007-04-20'),('2007-04-23'),('2007-04-24'),('2007-04-25'),('2007-04-26'),('2007-04-27'),('2007-04-30'),('2007-05-01'),('2007-05-02'),('2007-05-03'),('2007-05-04'),('2007-05-07'),('2007-05-08'),('2007-05-09'),('2007-05-10'),('2007-05-11'),('2007-05-14'),('2007-05-15'),('2007-05-16'),('2007-05-17'),('2007-05-18'),('2007-05-21'),('2007-05-22'),('2007-05-23'),('2007-05-24'),('2007-05-25'),('2007-05-28'),('2007-05-29'),('2007-05-30'),('2007-05-31'),('2007-06-01'),('2007-06-04'),('2007-06-05'),('2007-06-06'),('2007-06-07'),('2007-06-08'),('2007-06-11'),('2007-06-12'),('2007-06-13'),('2007-06-14'),('2007-06-15'),('2007-06-18'),('2007-06-19'),('2007-06-20'),('2007-06-21'),('2007-06-22'),('2007-06-25'),('2007-06-26'),('2007-06-27'),('2007-06-28'),('2007-06-29'),('2007-07-02'),('2007-07-03'),('2007-07-04'),('2007-07-05'),('2007-07-06'),('2007-07-09'),('2007-07-10'),('2007-07-11'),('2007-07-12'),('2007-07-13'),('2007-07-16'),('2007-07-17'),('2007-07-18'),('2007-07-19'),('2007-07-20'),('2007-07-23'),('2007-07-24'),('2007-07-25'),('2007-07-26'),('2007-07-27'),('2007-07-30'),('2007-07-31'),('2007-08-01'),('2007-08-02'),('2007-08-03'),('2007-08-06'),('2007-08-07'),('2007-08-08'),('2007-08-09'),('2007-08-10'),('2007-08-13'),('2007-08-14'),('2007-08-15'),('2007-08-16'),('2007-08-17'),('2007-08-20'),('2007-08-21'),('2007-08-22'),('2007-08-23'),('2007-08-24'),('2007-08-27'),('2007-08-28'),('2007-08-29'),('2007-08-30'),('2007-08-31'),('2007-09-03'),('2007-09-04'),('2007-09-05'),('2007-09-06'),('2007-09-07'),('2007-09-10'),('2007-09-11'),('2007-09-12'),('2007-09-13'),('2007-09-14'),('2007-09-17'),('2007-09-18'),('2007-09-19'),('2007-09-20'),('2007-09-21'),('2007-09-24'),('2007-09-25'),('2007-09-26'),('2007-09-27'),('2007-09-28'),('2007-10-01'),('2007-10-02'),('2007-10-03'),('2007-10-04'),('2007-10-05'),('2007-10-08'),('2007-10-09'),('2007-10-10'),('2007-10-11'),('2007-10-12'),('2007-10-15'),('2007-10-16'),('2007-10-17'),('2007-10-18'),('2007-10-19'),('2007-10-22'),('2007-10-23'),('2007-10-24'),('2007-10-25'),('2007-10-26'),('2007-10-29'),('2007-10-30'),('2007-10-31'),('2007-11-01'),('2007-11-02'),('2007-11-05'),('2007-11-06'),('2007-11-07'),('2007-11-08'),('2007-11-09'),('2007-11-12'),('2007-11-13'),('2007-11-14'),('2007-11-15'),('2007-11-16'),('2007-11-19'),('2007-11-20'),('2007-11-21'),('2007-11-22'),('2007-11-23'),('2007-11-26'),('2007-11-27'),('2007-11-28'),('2007-11-29'),('2007-11-30'),('2007-12-03'),('2007-12-04'),('2007-12-05'),('2007-12-06'),('2007-12-07'),('2007-12-10'),('2007-12-11'),('2007-12-12'),('2007-12-13'),('2007-12-14'),('2007-12-17'),('2007-12-18'),('2007-12-19'),('2007-12-20'),('2007-12-21'),('2007-12-24'),('2007-12-26'),('2007-12-27'),('2007-12-28'),('2007-12-31'),('2008-01-02'),('2008-01-03'),('2008-01-04'),('2008-01-07'),('2008-01-08'),('2008-01-09'),('2008-01-10'),('2008-01-11'),('2008-01-14'),('2008-01-15'),('2008-01-16'),('2008-01-17'),('2008-01-18'),('2008-01-21'),('2008-01-22'),('2008-01-23'),('2008-01-24'),('2008-01-25'),('2008-01-28'),('2008-01-29'),('2008-01-30'),('2008-01-31'),('2008-02-01'),('2008-02-04'),('2008-02-05'),('2008-02-06'),('2008-02-07'),('2008-02-08'),('2008-02-11'),('2008-02-12'),('2008-02-13'),('2008-02-14'),('2008-02-15'),('2008-02-18'),('2008-02-19'),('2008-02-20'),('2008-02-21'),('2008-02-22'),('2008-02-25'),('2008-02-26'),('2008-02-27'),('2008-02-28'),('2008-02-29'),('2008-03-03'),('2008-03-04'),('2008-03-05'),('2008-03-06'),('2008-03-07'),('2008-03-10'),('2008-03-11'),('2008-03-12'),('2008-03-13'),('2008-03-14'),('2008-03-17'),('2008-03-18'),('2008-03-19'),('2008-03-20'),('2008-03-21'),('2008-03-24'),('2008-03-25'),('2008-03-26'),('2008-03-27'),('2008-03-28'),('2008-03-31'),('2008-04-01'),('2008-04-02'),('2008-04-03'),('2008-04-04'),('2008-04-07'),('2008-04-08'),('2008-04-09'),('2008-04-10'),('2008-04-11'),('2008-04-14'),('2008-04-15'),('2008-04-16'),('2008-04-17'),('2008-04-18'),('2008-04-21'),('2008-04-22'),('2008-04-23'),('2008-04-24'),('2008-04-25'),('2008-04-28'),('2008-04-29'),('2008-04-30'),('2008-05-01'),('2008-05-02'),('2008-05-05'),('2008-05-06'),('2008-05-07'),('2008-05-08'),('2008-05-09'),('2008-05-12'),('2008-05-13'),('2008-05-14'),('2008-05-15'),('2008-05-16'),('2008-05-19'),('2008-05-20'),('2008-05-21'),('2008-05-22'),('2008-05-23'),('2008-05-26'),('2008-05-27'),('2008-05-28'),('2008-05-29'),('2008-05-30'),('2008-06-02'),('2008-06-03'),('2008-06-04'),('2008-06-05'),('2008-06-06'),('2008-06-09'),('2008-06-10'),('2008-06-11'),('2008-06-12'),('2008-06-13'),('2008-06-16'),('2008-06-17'),('2008-06-18'),('2008-06-19'),('2008-06-20'),('2008-06-23'),('2008-06-24'),('2008-06-25'),('2008-06-26'),('2008-06-27'),('2008-06-30'),('2008-07-01'),('2008-07-02'),('2008-07-03'),('2008-07-04'),('2008-07-07'),('2008-07-08'),('2008-07-09'),('2008-07-10'),('2008-07-11'),('2008-07-14'),('2008-07-15'),('2008-07-16'),('2008-07-17'),('2008-07-18'),('2008-07-21'),('2008-07-22'),('2008-07-23'),('2008-07-24'),('2008-07-25'),('2008-07-28'),('2008-07-29'),('2008-07-30'),('2008-07-31'),('2008-08-01'),('2008-08-04'),('2008-08-05'),('2008-08-06'),('2008-08-07'),('2008-08-08'),('2008-08-11'),('2008-08-12'),('2008-08-13'),('2008-08-14'),('2008-08-15'),('2008-08-18'),('2008-08-19'),('2008-08-20'),('2008-08-21'),('2008-08-22'),('2008-08-25'),('2008-08-26'),('2008-08-27'),('2008-08-28'),('2008-08-29'),('2008-09-01'),('2008-09-02'),('2008-09-03'),('2008-09-04'),('2008-09-05'),('2008-09-08'),('2008-09-09'),('2008-09-10'),('2008-09-11'),('2008-09-12'),('2008-09-15'),('2008-09-16'),('2008-09-17'),('2008-09-18'),('2008-09-19'),('2008-09-22'),('2008-09-23'),('2008-09-24'),('2008-09-25'),('2008-09-26'),('2008-09-29'),('2008-09-30'),('2008-10-01'),('2008-10-02'),('2008-10-03'),('2008-10-06'),('2008-10-07'),('2008-10-08'),('2008-10-09'),('2008-10-10'),('2008-10-13'),('2008-10-14'),('2008-10-15'),('2008-10-16'),('2008-10-17'),('2008-10-20'),('2008-10-21'),('2008-10-22'),('2008-10-23'),('2008-10-24'),('2008-10-27'),('2008-10-28'),('2008-10-29'),('2008-10-30'),('2008-10-31'),('2008-11-03'),('2008-11-04'),('2008-11-05'),('2008-11-06'),('2008-11-07'),('2008-11-10'),('2008-11-11'),('2008-11-12'),('2008-11-13'),('2008-11-14'),('2008-11-17'),('2008-11-18'),('2008-11-19'),('2008-11-20'),('2008-11-21'),('2008-11-24'),('2008-11-25'),('2008-11-26'),('2008-11-27'),('2008-11-28'),('2008-12-01'),('2008-12-02'),('2008-12-03'),('2008-12-04'),('2008-12-05'),('2008-12-08'),('2008-12-09'),('2008-12-10'),('2008-12-11'),('2008-12-12'),('2008-12-15'),('2008-12-16'),('2008-12-17'),('2008-12-18'),('2008-12-19'),('2008-12-22'),('2008-12-23'),('2008-12-24'),('2008-12-26'),('2008-12-29'),('2008-12-30'),('2008-12-31'),('2009-01-02'),('2009-01-05'),('2009-01-06'),('2009-01-07'),('2009-01-08'),('2009-01-09'),('2009-01-12'),('2009-01-13'),('2009-01-14'),('2009-01-15'),('2009-01-16'),('2009-01-19'),('2009-01-20'),('2009-01-21'),('2009-01-22'),('2009-01-23'),('2009-01-26'),('2009-01-27'),('2009-01-28'),('2009-01-29'),('2009-01-30'),('2009-02-02'),('2009-02-03'),('2009-02-04'),('2009-02-05'),('2009-02-06'),('2009-02-09'),('2009-02-10'),('2009-02-11'),('2009-02-12'),('2009-02-13'),('2009-02-16'),('2009-02-17'),('2009-02-18'),('2009-02-19'),('2009-02-20'),('2009-02-23'),('2009-02-24'),('2009-02-25'),('2009-02-26'),('2009-02-27'),('2009-03-02'),('2009-03-03'),('2009-03-04'),('2009-03-05'),('2009-03-06'),('2009-03-09'),('2009-03-10'),('2009-03-11'),('2009-03-12'),('2009-03-13'),('2009-03-16'),('2009-03-17'),('2009-03-18'),('2009-03-19'),('2009-03-20'),('2009-03-23'),('2009-03-24'),('2009-03-25'),('2009-03-26'),('2009-03-27'),('2009-03-30'),('2009-03-31'),('2009-04-01'),('2009-04-02'),('2009-04-03'),('2009-04-06'),('2009-04-07'),('2009-04-08'),('2009-04-09'),('2009-04-10'),('2009-04-13'),('2009-04-14'),('2009-04-15'),('2009-04-16'),('2009-04-17'),('2009-04-20'),('2009-04-21'),('2009-04-22'),('2009-04-23'),('2009-04-24'),('2009-04-27'),('2009-04-28'),('2009-04-29'),('2009-04-30'),('2009-05-01'),('2009-05-04'),('2009-05-05'),('2009-05-06'),('2009-05-07'),('2009-05-08'),('2009-05-11'),('2009-05-12'),('2009-05-13'),('2009-05-14'),('2009-05-15'),('2009-05-18'),('2009-05-19'),('2009-05-20'),('2009-05-21'),('2009-05-22'),('2009-05-25'),('2009-05-26'),('2009-05-27'),('2009-05-28'),('2009-05-29'),('2009-06-01'),('2009-06-02'),('2009-06-03'),('2009-06-04'),('2009-06-05'),('2009-06-08'),('2009-06-09'),('2009-06-10'),('2009-06-11'),('2009-06-12'),('2009-06-15'),('2009-06-16'),('2009-06-17'),('2009-06-18'),('2009-06-19'),('2009-06-22'),('2009-06-23'),('2009-06-24'),('2009-06-25'),('2009-06-26'),('2009-06-29'),('2009-06-30'),('2009-07-01'),('2009-07-02'),('2009-07-03'),('2009-07-06'),('2009-07-07'),('2009-07-08'),('2009-07-09'),('2009-07-10'),('2009-07-13'),('2009-07-14'),('2009-07-15'),('2009-07-16'),('2009-07-17'),('2009-07-20'),('2009-07-21'),('2009-07-22'),('2009-07-23'),('2009-07-24'),('2009-07-27'),('2009-07-28'),('2009-07-29'),('2009-07-30'),('2009-07-31'),('2009-08-03'),('2009-08-04'),('2009-08-05'),('2009-08-06'),('2009-08-07'),('2009-08-10'),('2009-08-11'),('2009-08-12'),('2009-08-13'),('2009-08-14'),('2009-08-17'),('2009-08-18'),('2009-08-19'),('2009-08-20'),('2009-08-21'),('2009-08-24'),('2009-08-25'),('2009-08-26'),('2009-08-27'),('2009-08-28'),('2009-08-31'),('2009-09-01'),('2009-09-02'),('2009-09-03'),('2009-09-04'),('2009-09-07'),('2009-09-08'),('2009-09-09'),('2009-09-10'),('2009-09-11'),('2009-09-14'),('2009-09-15'),('2009-09-16'),('2009-09-17'),('2009-09-18'),('2009-09-21'),('2009-09-22'),('2009-09-23'),('2009-09-24'),('2009-09-25'),('2009-09-28'),('2009-09-29'),('2009-09-30'),('2009-10-01'),('2009-10-02'),('2009-10-05'),('2009-10-06'),('2009-10-07'),('2009-10-08'),('2009-10-09'),('2009-10-12'),('2009-10-13'),('2009-10-14'),('2009-10-15'),('2009-10-16'),('2009-10-19'),('2009-10-20'),('2009-10-21'),('2009-10-22'),('2009-10-23'),('2009-10-26'),('2009-10-27'),('2009-10-28'),('2009-10-29'),('2009-10-30'),('2009-11-02'),('2009-11-03'),('2009-11-04'),('2009-11-05'),('2009-11-06'),('2009-11-09'),('2009-11-10'),('2009-11-11'),('2009-11-12'),('2009-11-13'),('2009-11-16'),('2009-11-17'),('2009-11-18'),('2009-11-19'),('2009-11-20'),('2009-11-23'),('2009-11-24'),('2009-11-25'),('2009-11-26'),('2009-11-27'),('2009-11-30'),('2009-12-01'),('2009-12-02'),('2009-12-03'),('2009-12-04'),('2009-12-07'),('2009-12-08'),('2009-12-09'),('2009-12-10'),('2009-12-11'),('2009-12-14'),('2009-12-15'),('2009-12-16'),('2009-12-17'),('2009-12-18'),('2009-12-21'),('2009-12-22'),('2009-12-23'),('2009-12-24'),('2009-12-28'),('2009-12-29'),('2009-12-30'),('2009-12-31'),('2010-01-04'),('2010-01-05'),('2010-01-06'),('2010-01-07'),('2010-01-08'),('2010-01-11'),('2010-01-12'),('2010-01-13'),('2010-01-14'),('2010-01-15'),('2010-01-18'),('2010-01-19'),('2010-01-20'),('2010-01-21'),('2010-01-22'),('2010-01-25'),('2010-01-26'),('2010-01-27'),('2010-01-28'),('2010-01-29'),('2010-02-01'),('2010-02-02'),('2010-02-03'),('2010-02-04'),('2010-02-05'),('2010-02-08'),('2010-02-09'),('2010-02-10'),('2010-02-11'),('2010-02-12'),('2010-02-15'),('2010-02-16'),('2010-02-17'),('2010-02-18'),('2010-02-19'),('2010-02-22'),('2010-02-23'),('2010-02-24'),('2010-02-25'),('2010-02-26'),('2010-03-01'),('2010-03-02'),('2010-03-03'),('2010-03-04'),('2010-03-05'),('2010-03-08'),('2010-03-09'),('2010-03-10'),('2010-03-11'),('2010-03-12'),('2010-03-15'),('2010-03-16'),('2010-03-17'),('2010-03-18'),('2010-03-19'),('2010-03-22'),('2010-03-23'),('2010-03-24'),('2010-03-25'),('2010-03-26'),('2010-03-29'),('2010-03-30'),('2010-03-31'),('2010-04-01'),('2010-04-02'),('2010-04-05'),('2010-04-06'),('2010-04-07'),('2010-04-08'),('2010-04-09'),('2010-04-12'),('2010-04-13'),('2010-04-14'),('2010-04-15'),('2010-04-16'),('2010-04-19'),('2010-04-20'),('2010-04-21'),('2010-04-22'),('2010-04-23'),('2010-04-26'),('2010-04-27'),('2010-04-28'),('2010-04-29'),('2010-04-30'),('2010-05-03'),('2010-05-04'),('2010-05-05'),('2010-05-06'),('2010-05-07'),('2010-05-10'),('2010-05-11'),('2010-05-12'),('2010-05-13'),('2010-05-14'),('2010-05-17'),('2010-05-18'),('2010-05-19'),('2010-05-20'),('2010-05-21'),('2010-05-24'),('2010-05-25'),('2010-05-26'),('2010-05-27'),('2010-05-28'),('2010-05-31'),('2010-06-01'),('2010-06-02'),('2010-06-03'),('2010-06-04'),('2010-06-07'),('2010-06-08'),('2010-06-09'),('2010-06-10'),('2010-06-11'),('2010-06-14'),('2010-06-15'),('2010-06-16'),('2010-06-17'),('2010-06-18'),('2010-06-21'),('2010-06-22'),('2010-06-23'),('2010-06-24'),('2010-06-25'),('2010-06-28'),('2010-06-29'),('2010-06-30'),('2010-07-01'),('2010-07-02'),('2010-07-05'),('2010-07-06'),('2010-07-07'),('2010-07-08'),('2010-07-09'),('2010-07-12'),('2010-07-13'),('2010-07-14'),('2010-07-15'),('2010-07-16'),('2010-07-19'),('2010-07-20'),('2010-07-21'),('2010-07-22'),('2010-07-23'),('2010-07-26'),('2010-07-27'),('2010-07-28'),('2010-07-29'),('2010-07-30'),('2010-08-02'),('2010-08-03'),('2010-08-04'),('2010-08-05'),('2010-08-06'),('2010-08-09'),('2010-08-10'),('2010-08-11'),('2010-08-12'),('2010-08-13'),('2010-08-16'),('2010-08-17'),('2010-08-18'),('2010-08-19'),('2010-08-20'),('2010-08-23'),('2010-08-24'),('2010-08-25'),('2010-08-26'),('2010-08-27'),('2010-08-30'),('2010-08-31'),('2010-09-01'),('2010-09-02'),('2010-09-03'),('2010-09-06'),('2010-09-07'),('2010-09-08'),('2010-09-09'),('2010-09-10'),('2010-09-13'),('2010-09-14'),('2010-09-15'),('2010-09-16'),('2010-09-17'),('2010-09-20'),('2010-09-21'),('2010-09-22'),('2010-09-23'),('2010-09-24'),('2010-09-27'),('2010-09-28'),('2010-09-29'),('2010-09-30'),('2010-10-01'),('2010-10-04'),('2010-10-05'),('2010-10-06'),('2010-10-07'),('2010-10-08'),('2010-10-11'),('2010-10-12'),('2010-10-13'),('2010-10-14'),('2010-10-15'),('2010-10-18'),('2010-10-19'),('2010-10-20'),('2010-10-21'),('2010-10-22'),('2010-10-25'),('2010-10-26'),('2010-10-27'),('2010-10-28'),('2010-10-29'),('2010-11-01'),('2010-11-02'),('2010-11-03'),('2010-11-04'),('2010-11-05'),('2010-11-08'),('2010-11-09'),('2010-11-10'),('2010-11-11'),('2010-11-12'),('2010-11-15'),('2010-11-16'),('2010-11-17'),('2010-11-18'),('2010-11-19'),('2010-11-22'),('2010-11-23'),('2010-11-24'),('2010-11-25'),('2010-11-26'),('2010-11-29'),('2010-11-30'),('2010-12-01'),('2010-12-02'),('2010-12-03'),('2010-12-06'),('2010-12-07'),('2010-12-08'),('2010-12-09'),('2010-12-10'),('2010-12-13'),('2010-12-14'),('2010-12-15'),('2010-12-16'),('2010-12-17'),('2010-12-20'),('2010-12-21'),('2010-12-22'),('2010-12-23'),('2010-12-24'),('2010-12-27'),('2010-12-28'),('2010-12-29'),('2010-12-30'),('2010-12-31'),('2011-01-03'),('2011-01-04'),('2011-01-05'),('2011-01-06'),('2011-01-07'),('2011-01-10'),('2011-01-11'),('2011-01-12'),('2011-01-13'),('2011-01-14'),('2011-01-17'),('2011-01-18'),('2011-01-19'),('2011-01-20'),('2011-01-21'),('2011-01-24'),('2011-01-25'),('2011-01-26'),('2011-01-27'),('2011-01-28'),('2011-01-31'),('2011-02-01'),('2011-02-02'),('2011-02-03'),('2011-02-04'),('2011-02-07'),('2011-02-08'),('2011-02-09'),('2011-02-10'),('2011-02-11'),('2011-02-14'),('2011-02-15'),('2011-02-16'),('2011-02-17'),('2011-02-18'),('2011-02-21'),('2011-02-22'),('2011-02-23'),('2011-02-24'),('2011-02-25'),('2011-02-28'),('2011-03-01'),('2011-03-02'),('2011-03-03'),('2011-03-04'),('2011-03-07'),('2011-03-08'),('2011-03-09'),('2011-03-10'),('2011-03-11'),('2011-03-14'),('2011-03-15'),('2011-03-16'),('2011-03-17'),('2011-03-18'),('2011-03-21'),('2011-03-22'),('2011-03-23'),('2011-03-24'),('2011-03-25'),('2011-03-28'),('2011-03-29'),('2011-03-30'),('2011-03-31'),('2011-04-01'),('2011-04-04'),('2011-04-05'),('2011-04-06'),('2011-04-07'),('2011-04-08'),('2011-04-11'),('2011-04-12'),('2011-04-13'),('2011-04-14'),('2011-04-15'),('2011-04-18'),('2011-04-19'),('2011-04-20'),('2011-04-21'),('2011-04-22'),('2011-04-25'),('2011-04-26'),('2011-04-27'),('2011-04-28'),('2011-04-29'),('2011-05-02'),('2011-05-03'),('2011-05-04'),('2011-05-05'),('2011-05-06'),('2011-05-09'),('2011-05-10'),('2011-05-11'),('2011-05-12'),('2011-05-13'),('2011-05-16'),('2011-05-17'),('2011-05-18'),('2011-05-19'),('2011-05-20'),('2011-05-23'),('2011-05-24'),('2011-05-25'),('2011-05-26'),('2011-05-27'),('2011-05-30'),('2011-05-31'),('2011-06-01'),('2011-06-02'),('2011-06-03'),('2011-06-06'),('2011-06-07'),('2011-06-08'),('2011-06-09'),('2011-06-10'),('2011-06-13'),('2011-06-14'),('2011-06-15'),('2011-06-16'),('2011-06-17'),('2011-06-20'),('2011-06-21'),('2011-06-22'),('2011-06-23'),('2011-06-24'),('2011-06-27'),('2011-06-28'),('2011-06-29'),('2011-06-30'),('2011-07-01'),('2011-07-04'),('2011-07-05'),('2011-07-06'),('2011-07-07'),('2011-07-08'),('2011-07-11'),('2011-07-12'),('2011-07-13'),('2011-07-14'),('2011-07-15'),('2011-07-18'),('2011-07-19'),('2011-07-20'),('2011-07-21'),('2011-07-22'),('2011-07-25'),('2011-07-26'),('2011-07-27'),('2011-07-28'),('2011-07-29'),('2011-08-01'),('2011-08-02'),('2011-08-03'),('2011-08-04'),('2011-08-05'),('2011-08-08'),('2011-08-09'),('2011-08-10'),('2011-08-11'),('2011-08-12'),('2011-08-15'),('2011-08-16'),('2011-08-17'),('2011-08-18'),('2011-08-19'),('2011-08-22'),('2011-08-23'),('2011-08-24'),('2011-08-25'),('2011-08-26'),('2011-08-29'),('2011-08-30'),('2011-08-31'),('2011-09-01'),('2011-09-02'),('2011-09-05'),('2011-09-06'),('2011-09-07'),('2011-09-08'),('2011-09-09'),('2011-09-12'),('2011-09-13'),('2011-09-14'),('2011-09-15'),('2011-09-16'),('2011-09-19'),('2011-09-20'),('2011-09-21'),('2011-09-22'),('2011-09-23'),('2011-09-26'),('2011-09-27'),('2011-09-28'),('2011-09-29'),('2011-09-30'),('2011-10-03'),('2011-10-04'),('2011-10-05'),('2011-10-06'),('2011-10-07'),('2011-10-10'),('2011-10-11'),('2011-10-12'),('2011-10-13'),('2011-10-14'),('2011-10-17'),('2011-10-18'),('2011-10-19'),('2011-10-20'),('2011-10-21'),('2011-10-24'),('2011-10-25'),('2011-10-26'),('2011-10-27'),('2011-10-28'),('2011-10-31'),('2011-11-01'),('2011-11-02'),('2011-11-03'),('2011-11-04'),('2011-11-07'),('2011-11-08'),('2011-11-09'),('2011-11-10'),('2011-11-11'),('2011-11-14'),('2011-11-15'),('2011-11-16'),('2011-11-17'),('2011-11-18'),('2011-11-21'),('2011-11-22'),('2011-11-23'),('2011-11-24'),('2011-11-25'),('2011-11-28'),('2011-11-29'),('2011-11-30'),('2011-12-01'),('2011-12-02'),('2011-12-05'),('2011-12-06'),('2011-12-07'),('2011-12-08'),('2011-12-09'),('2011-12-12'),('2011-12-13'),('2011-12-14'),('2011-12-15'),('2011-12-16'),('2011-12-19'),('2011-12-20'),('2011-12-21'),('2011-12-22'),('2011-12-23'),('2011-12-26'),('2011-12-27'),('2011-12-28'),('2011-12-29'),('2011-12-30'),('2012-01-02'),('2012-01-03'),('2012-01-04'),('2012-01-05'),('2012-01-06'),('2012-01-09'),('2012-01-10'),('2012-01-11'),('2012-01-12'),('2012-01-13'),('2012-01-16'),('2012-01-17'),('2012-01-18'),('2012-01-19'),('2012-01-20'),('2012-01-23'),('2012-01-24'),('2012-01-25'),('2012-01-26'),('2012-01-27'),('2012-01-30'),('2012-01-31'),('2012-02-01'),('2012-02-02'),('2012-02-03'),('2012-02-06'),('2012-02-07'),('2012-02-08'),('2012-02-09'),('2012-02-10'),('2012-02-13'),('2012-02-14'),('2012-02-15'),('2012-02-16'),('2012-02-17'),('2012-02-20'),('2012-02-21'),('2012-02-22'),('2012-02-23'),('2012-02-24'),('2012-02-27'),('2012-02-28'),('2012-02-29'),('2012-03-01'),('2012-03-02'),('2012-03-05'),('2012-03-06'),('2012-03-07'),('2012-03-08'),('2012-03-09'),('2012-03-12'),('2012-03-13'),('2012-03-14'),('2012-03-15'),('2012-03-16'),('2012-03-19'),('2012-03-20'),('2012-03-21'),('2012-03-22'),('2012-03-23'),('2012-03-26'),('2012-03-27'),('2012-03-28'),('2012-03-29'),('2012-03-30'),('2012-04-02'),('2012-04-03'),('2012-04-04'),('2012-04-05'),('2012-04-06'),('2012-04-09'),('2012-04-10'),('2012-04-11'),('2012-04-12'),('2012-04-13'),('2012-04-16'),('2012-04-17'),('2012-04-18'),('2012-04-19'),('2012-04-20'),('2012-04-23'),('2012-04-24'),('2012-04-25'),('2012-04-26'),('2012-04-27'),('2012-04-30'),('2012-05-01'),('2012-05-02'),('2012-05-03'),('2012-05-04'),('2012-05-07'),('2012-05-08'),('2012-05-09'),('2012-05-10'),('2012-05-11'),('2012-05-14'),('2012-05-15'),('2012-05-16'),('2012-05-17'),('2012-05-18'),('2012-05-21'),('2012-05-22'),('2012-05-23'),('2012-05-24'),('2012-05-25'),('2012-05-28'),('2012-05-29'),('2012-05-30'),('2012-05-31'),('2012-06-01'),('2012-06-04'),('2012-06-05'),('2012-06-06'),('2012-06-07'),('2012-06-08'),('2012-06-11'),('2012-06-12'),('2012-06-13'),('2012-06-14'),('2012-06-15'),('2012-06-18'),('2012-06-19'),('2012-06-20'),('2012-06-21'),('2012-06-22'),('2012-06-25'),('2012-06-26'),('2012-06-27'),('2012-06-28'),('2012-06-29'),('2012-07-02'),('2012-07-03'),('2012-07-04'),('2012-07-05'),('2012-07-06'),('2012-07-09'),('2012-07-10'),('2012-07-11'),('2012-07-12'),('2012-07-13'),('2012-07-16'),('2012-07-17'),('2012-07-18'),('2012-07-19'),('2012-07-20'),('2012-07-23'),('2012-07-24'),('2012-07-25'),('2012-07-26'),('2012-07-27'),('2012-07-30'),('2012-07-31'),('2012-08-01'),('2012-08-02'),('2012-08-03'),('2012-08-06'),('2012-08-07'),('2012-08-08'),('2012-08-09'),('2012-08-10'),('2012-08-13'),('2012-08-14'),('2012-08-15'),('2012-08-16'),('2012-08-17'),('2012-08-20'),('2012-08-21'),('2012-08-22'),('2012-08-23'),('2012-08-24'),('2012-08-27'),('2012-08-28'),('2012-08-29'),('2012-08-30'),('2012-08-31'),('2012-09-03'),('2012-09-04'),('2012-09-05'),('2012-09-06'),('2012-09-07'),('2012-09-10'),('2012-09-11'),('2012-09-12'),('2012-09-13'),('2012-09-14'),('2012-09-17'),('2012-09-18'),('2012-09-19'),('2012-09-20'),('2012-09-21'),('2012-09-24'),('2012-09-25'),('2012-09-26'),('2012-09-27'),('2012-09-28'),('2012-10-01'),('2012-10-02'),('2012-10-03'),('2012-10-04'),('2012-10-05'),('2012-10-08'),('2012-10-09'),('2012-10-10'),('2012-10-11'),('2012-10-12'),('2012-10-15'),('2012-10-16'),('2012-10-17'),('2012-10-18'),('2012-10-19'),('2012-10-22'),('2012-10-23'),('2012-10-24'),('2012-10-25'),('2012-10-26'),('2012-10-29'),('2012-10-30'),('2012-10-31'),('2012-11-01'),('2012-11-02'),('2012-11-05'),('2012-11-06'),('2012-11-07'),('2012-11-08'),('2012-11-09'),('2012-11-12'),('2012-11-13'),('2012-11-14'),('2012-11-15'),('2012-11-16'),('2012-11-19'),('2012-11-20'),('2012-11-21'),('2012-11-22'),('2012-11-23'),('2012-11-26'),('2012-11-27'),('2012-11-28'),('2012-11-29'),('2012-11-30'),('2012-12-03'),('2012-12-04'),('2012-12-05'),('2012-12-06'),('2012-12-07'),('2012-12-10'),('2012-12-11'),('2012-12-12'),('2012-12-13'),('2012-12-14'),('2012-12-17'),('2012-12-18'),('2012-12-19'),('2012-12-20'),('2012-12-21'),('2012-12-24'),('2012-12-26'),('2012-12-27'),('2012-12-28'),('2012-12-31'),('2013-01-02'),('2013-01-03'),('2013-01-04'),('2013-01-07'),('2013-01-08'),('2013-01-09'),('2013-01-10'),('2013-01-11'),('2013-01-14'),('2013-01-15'),('2013-01-16'),('2013-01-17'),('2013-01-18'),('2013-01-21'),('2013-01-22'),('2013-01-23'),('2013-01-24'),('2013-01-25'),('2013-01-28'),('2013-01-29'),('2013-01-30'),('2013-01-31'),('2013-02-01'),('2013-02-04'),('2013-02-05'),('2013-02-06'),('2013-02-07'),('2013-02-08'),('2013-02-11'),('2013-02-12'),('2013-02-13'),('2013-02-14'),('2013-02-15'),('2013-02-18'),('2013-02-19'),('2013-02-20'),('2013-02-21'),('2013-02-22'),('2013-02-25'),('2013-02-26'),('2013-02-27'),('2013-02-28'),('2013-03-01'),('2013-03-04'),('2013-03-05'),('2013-03-06'),('2013-03-07'),('2013-03-08'),('2013-03-11'),('2013-03-12'),('2013-03-13'),('2013-03-14'),('2013-03-15'),('2013-03-18'),('2013-03-19'),('2013-03-20'),('2013-03-21'),('2013-03-22'),('2013-03-25'),('2013-03-26'),('2013-03-27'),('2013-03-28'),('2013-03-29'),('2013-04-01'),('2013-04-02'),('2013-04-03'),('2013-04-04'),('2013-04-05'),('2013-04-08'),('2013-04-09'),('2013-04-10'),('2013-04-11'),('2013-04-12'),('2013-04-15'),('2013-04-16'),('2013-04-17'),('2013-04-18'),('2013-04-19'),('2013-04-22'),('2013-04-23'),('2013-04-24'),('2013-04-25'),('2013-04-26'),('2013-04-29'),('2013-04-30'),('2013-05-01'),('2013-05-02'),('2013-05-03'),('2013-05-06'),('2013-05-07'),('2013-05-08'),('2013-05-09'),('2013-05-10'),('2013-05-13'),('2013-05-14'),('2013-05-15'),('2013-05-16'),('2013-05-17'),('2013-05-20'),('2013-05-21'),('2013-05-22'),('2013-05-23'),('2013-05-24'),('2013-05-27'),('2013-05-28'),('2013-05-29'),('2013-05-30'),('2013-05-31'),('2013-06-03'),('2013-06-04'),('2013-06-05'),('2013-06-06'),('2013-06-07'),('2013-06-10'),('2013-06-11'),('2013-06-12'),('2013-06-13'),('2013-06-14'),('2013-06-17'),('2013-06-18'),('2013-06-19'),('2013-06-20'),('2013-06-21'),('2013-06-24'),('2013-06-25'),('2013-06-26'),('2013-06-27'),('2013-06-28'),('2013-07-01'),('2013-07-02'),('2013-07-03'),('2013-07-04'),('2013-07-05'),('2013-07-08'),('2013-07-09'),('2013-07-10'),('2013-07-11'),('2013-07-12'),('2013-07-15'),('2013-07-16'),('2013-07-17'),('2013-07-18'),('2013-07-19'),('2013-07-22'),('2013-07-23'),('2013-07-24'),('2013-07-25'),('2013-07-26'),('2013-07-29'),('2013-07-30'),('2013-07-31'),('2013-08-01'),('2013-08-02'),('2013-08-05'),('2013-08-06'),('2013-08-07'),('2013-08-08'),('2013-08-09'),('2013-08-12'),('2013-08-13'),('2013-08-14'),('2013-08-15'),('2013-08-16'),('2013-08-19'),('2013-08-20'),('2013-08-21'),('2013-08-22'),('2013-08-23'),('2013-08-26'),('2013-08-27'),('2013-08-28'),('2013-08-29'),('2013-08-30'),('2013-09-02'),('2013-09-03'),('2013-09-04'),('2013-09-05'),('2013-09-06'),('2013-09-09'),('2013-09-10'),('2013-09-11'),('2013-09-12'),('2013-09-13'),('2013-09-16'),('2013-09-17'),('2013-09-18'),('2013-09-19'),('2013-09-20'),('2013-09-23'),('2013-09-24'),('2013-09-25'),('2013-09-26'),('2013-09-27'),('2013-09-30'),('2013-10-01'),('2013-10-02'),('2013-10-03'),('2013-10-04'),('2013-10-07'),('2013-10-08'),('2013-10-09'),('2013-10-10'),('2013-10-11'),('2013-10-14'),('2013-10-15'),('2013-10-16'),('2013-10-17'),('2013-10-18'),('2013-10-21'),('2013-10-22'),('2013-10-23'),('2013-10-24'),('2013-10-25'),('2013-10-28'),('2013-10-29'),('2013-10-30'),('2013-10-31'),('2013-11-01'),('2013-11-04'),('2013-11-05'),('2013-11-06'),('2013-11-07'),('2013-11-08'),('2013-11-11'),('2013-11-12'),('2013-11-13'),('2013-11-14'),('2013-11-15'),('2013-11-18'),('2013-11-19'),('2013-11-20'),('2013-11-21'),('2013-11-22'),('2013-11-25'),('2013-11-26'),('2013-11-27'),('2013-11-28'),('2013-11-29'),('2013-12-02'),('2013-12-03'),('2013-12-04'),('2013-12-05'),('2013-12-06'),('2013-12-09'),('2013-12-10'),('2013-12-11'),('2013-12-12'),('2013-12-13'),('2013-12-16'),('2013-12-17'),('2013-12-18'),('2013-12-19'),('2013-12-20'),('2013-12-23'),('2013-12-24'),('2013-12-26'),('2013-12-27'),('2013-12-30'),('2013-12-31'),('2014-01-02'),('2014-01-03'),('2014-01-06'),('2014-01-07'),('2014-01-08'),('2014-01-09'),('2014-01-10'),('2014-01-13'),('2014-01-14'),('2014-01-15'),('2014-01-16'),('2014-01-17'),('2014-01-20'),('2014-01-21'),('2014-01-22'),('2014-01-23'),('2014-01-24'),('2014-01-27'),('2014-01-28'),('2014-01-29'),('2014-01-30'),('2014-01-31'),('2014-02-03'),('2014-02-04'),('2014-02-05'),('2014-02-06'),('2014-02-07'),('2014-02-10'),('2014-02-11'),('2014-02-12'),('2014-02-13'),('2014-02-14'),('2014-02-17'),('2014-02-18'),('2014-02-19'),('2014-02-20'),('2014-02-21'),('2014-02-24'),('2014-02-25'),('2014-02-26'),('2014-02-27'),('2014-02-28'),('2014-03-03'),('2014-03-04'),('2014-03-05'),('2014-03-06'),('2014-03-07'),('2014-03-10'),('2014-03-11'),('2014-03-12'),('2014-03-13'),('2014-03-14'),('2014-03-17'),('2014-03-18'),('2014-03-19'),('2014-03-20'),('2014-03-21'),('2014-03-24'),('2014-03-25'),('2014-03-26'),('2014-03-27'),('2014-03-28'),('2014-03-31'),('2014-04-01'),('2014-04-02'),('2014-04-03'),('2014-04-04'),('2014-04-07'),('2014-04-08'),('2014-04-09'),('2014-04-10'),('2014-04-11'),('2014-04-14'),('2014-04-15'),('2014-04-16'),('2014-04-17'),('2014-04-18'),('2014-04-21'),('2014-04-22'),('2014-04-23'),('2014-04-24'),('2014-04-25'),('2014-04-28'),('2014-04-29'),('2014-04-30'),('2014-05-01'),('2014-05-02'),('2014-05-05'),('2014-05-06'),('2014-05-07'),('2014-05-08'),('2014-05-09'),('2014-05-12'),('2014-05-13'),('2014-05-14'),('2014-05-15'),('2014-05-16'),('2014-05-19'),('2014-05-20'),('2014-05-21'),('2014-05-22'),('2014-05-23'),('2014-05-26'),('2014-05-27'),('2014-05-28'),('2014-05-29'),('2014-05-30'),('2014-06-02'),('2014-06-03'),('2014-06-04'),('2014-06-05'),('2014-06-06'),('2014-06-09'),('2014-06-10'),('2014-06-11'),('2014-06-12'),('2014-06-13'),('2014-06-16'),('2014-06-17'),('2014-06-18'),('2014-06-19'),('2014-06-20'),('2014-06-23'),('2014-06-24'),('2014-06-25'),('2014-06-26'),('2014-06-27'),('2014-06-30'),('2014-07-01'),('2014-07-02'),('2014-07-03'),('2014-07-04'),('2014-07-07'),('2014-07-08'),('2014-07-09'),('2014-07-10'),('2014-07-11'),('2014-07-14'),('2014-07-15'),('2014-07-16'),('2014-07-17'),('2014-07-18'),('2014-07-21'),('2014-07-22'),('2014-07-23'),('2014-07-24'),('2014-07-25'),('2014-07-28'),('2014-07-29'),('2014-07-30'),('2014-07-31'),('2014-08-01'),('2014-08-04'),('2014-08-05'),('2014-08-06'),('2014-08-07'),('2014-08-08'),('2014-08-11'),('2014-08-12'),('2014-08-13'),('2014-08-14'),('2014-08-15'),('2014-08-18'),('2014-08-19'),('2014-08-20'),('2014-08-21'),('2014-08-22'),('2014-08-25'),('2014-08-26'),('2014-08-27'),('2014-08-28'),('2014-08-29'),('2014-09-01'),('2014-09-02'),('2014-09-03'),('2014-09-04'),('2014-09-05'),('2014-09-08'),('2014-09-09'),('2014-09-10'),('2014-09-11'),('2014-09-12'),('2014-09-15'),('2014-09-16'),('2014-09-17'),('2014-09-18'),('2014-09-19'),('2014-09-22'),('2014-09-23'),('2014-09-24'),('2014-09-25'),('2014-09-26'),('2014-09-29'),('2014-09-30'),('2014-10-01'),('2014-10-02'),('2014-10-03'),('2014-10-06'),('2014-10-07'),('2014-10-08'),('2014-10-09'),('2014-10-10'),('2014-10-13'),('2014-10-14'),('2014-10-15'),('2014-10-16'),('2014-10-17'),('2014-10-20'),('2014-10-21'),('2014-10-22'),('2014-10-23'),('2014-10-24'),('2014-10-27'),('2014-10-28'),('2014-10-29'),('2014-10-30'),('2014-10-31'),('2014-11-03'),('2014-11-04'),('2014-11-05'),('2014-11-06'),('2014-11-07'),('2014-11-10'),('2014-11-11'),('2014-11-12'),('2014-11-13'),('2014-11-14'),('2014-11-17'),('2014-11-18'),('2014-11-19'),('2014-11-20'),('2014-11-21'),('2014-11-24'),('2014-11-25'),('2014-11-26'),('2014-11-27'),('2014-11-28'),('2014-12-01'),('2014-12-02'),('2014-12-03'),('2014-12-04'),('2014-12-05'),('2014-12-08'),('2014-12-09'),('2014-12-10'),('2014-12-11'),('2014-12-12'),('2014-12-15'),('2014-12-16'),('2014-12-17'),('2014-12-18'),('2014-12-19'),('2014-12-22'),('2014-12-23'),('2014-12-24'),('2014-12-26'),('2014-12-29'),('2014-12-30'),('2014-12-31'),('2015-01-02'),('2015-01-05'),('2015-01-06'),('2015-01-07'),('2015-01-08'),('2015-01-09'),('2015-01-12'),('2015-01-13'),('2015-01-14'),('2015-01-15'),('2015-01-16'),('2015-01-19'),('2015-01-20'),('2015-01-21'),('2015-01-22'),('2015-01-23'),('2015-01-26'),('2015-01-27'),('2015-01-28'),('2015-01-29'),('2015-01-30'),('2015-02-02'),('2015-02-03'),('2015-02-04'),('2015-02-05'),('2015-02-06'),('2015-02-09'),('2015-02-10'),('2015-02-11'),('2015-02-12'),('2015-02-13'),('2015-02-16'),('2015-02-17'),('2015-02-18'),('2015-02-19'),('2015-02-20'),('2015-02-23'),('2015-02-24'),('2015-02-25'),('2015-02-26'),('2015-02-27'),('2015-03-02'),('2015-03-03'),('2015-03-04'),('2015-03-05'),('2015-03-06'),('2015-03-09'),('2015-03-10'),('2015-03-11'),('2015-03-12'),('2015-03-13'),('2015-03-16'),('2015-03-17'),('2015-03-18'),('2015-03-19'),('2015-03-20'),('2015-03-23'),('2015-03-24'),('2015-03-25'),('2015-03-26'),('2015-03-27'),('2015-03-30'),('2015-03-31'),('2015-04-01'),('2015-04-02'),('2015-04-03'),('2015-04-06'),('2015-04-07'),('2015-04-08'),('2015-04-09'),('2015-04-10'),('2015-04-13'),('2015-04-14'),('2015-04-15'),('2015-04-16'),('2015-04-17'),('2015-04-20'),('2015-04-21'),('2015-04-22'),('2015-04-23'),('2015-04-24'),('2015-04-27'),('2015-04-28'),('2015-04-29'),('2015-04-30'),('2015-05-01'),('2015-05-04'),('2015-05-05'),('2015-05-06'),('2015-05-07'),('2015-05-08'),('2015-05-11'),('2015-05-12'),('2015-05-13'),('2015-05-14'),('2015-05-15'),('2015-05-18'),('2015-05-19'),('2015-05-20'),('2015-05-21'),('2015-05-22'),('2015-05-25'),('2015-05-26'),('2015-05-27'),('2015-05-28'),('2015-05-29'),('2015-06-01'),('2015-06-02'),('2015-06-03'),('2015-06-04'),('2015-06-05'),('2015-06-08'),('2015-06-09'),('2015-06-10'),('2015-06-11'),('2015-06-12'),('2015-06-15'),('2015-06-16'),('2015-06-17'),('2015-06-18'),('2015-06-19'),('2015-06-22'),('2015-06-23'),('2015-06-24'),('2015-06-25'),('2015-06-26'),('2015-06-29'),('2015-06-30'),('2015-07-01'),('2015-07-02'),('2015-07-03'),('2015-07-06'),('2015-07-07'),('2015-07-08'),('2015-07-09'),('2015-07-10'),('2015-07-13'),('2015-07-14'),('2015-07-15'),('2015-07-16'),('2015-07-17'),('2015-07-20'),('2015-07-21'),('2015-07-22'),('2015-07-23'),('2015-07-24'),('2015-07-27'),('2015-07-28'),('2015-07-29'),('2015-07-30'),('2015-07-31'),('2015-08-03'),('2015-08-04'),('2015-08-05'),('2015-08-06'),('2015-08-07'),('2015-08-10'),('2015-08-11'),('2015-08-12'),('2015-08-13'),('2015-08-14'),('2015-08-17'),('2015-08-18'),('2015-08-19'),('2015-08-20'),('2015-08-21'),('2015-08-24'),('2015-08-25'),('2015-08-26'),('2015-08-27'),('2015-08-28'),('2015-08-31'),('2015-09-01'),('2015-09-02'),('2015-09-03'),('2015-09-04'),('2015-09-07'),('2015-09-08'),('2015-09-09'),('2015-09-10'),('2015-09-11'),('2015-09-14'),('2015-09-15'),('2015-09-16'),('2015-09-17'),('2015-09-18'),('2015-09-21'),('2015-09-22'),('2015-09-23'),('2015-09-24'),('2015-09-25'),('2015-09-28'),('2015-09-29'),('2015-09-30'),('2015-10-01'),('2015-10-02'),('2015-10-05'),('2015-10-06'),('2015-10-07'),('2015-10-08'),('2015-10-09'),('2015-10-12'),('2015-10-13'),('2015-10-14'),('2015-10-15'),('2015-10-16'),('2015-10-19'),('2015-10-20'),('2015-10-21'),('2015-10-22'),('2015-10-23'),('2015-10-26'),('2015-10-27'),('2015-10-28'),('2015-10-29'),('2015-10-30'),('2015-11-02'),('2015-11-03'),('2015-11-04'),('2015-11-05'),('2015-11-06'),('2015-11-09'),('2015-11-10'),('2015-11-11'),('2015-11-12'),('2015-11-13'),('2015-11-16'),('2015-11-17'),('2015-11-18'),('2015-11-19'),('2015-11-20'),('2015-11-23'),('2015-11-24'),('2015-11-25'),('2015-11-26'),('2015-11-27'),('2015-11-30'),('2015-12-01'),('2015-12-02'),('2015-12-03'),('2015-12-04'),('2015-12-07'),('2015-12-08'),('2015-12-09'),('2015-12-10'),('2015-12-11'),('2015-12-14'),('2015-12-15'),('2015-12-16'),('2015-12-17'),('2015-12-18'),('2015-12-21'),('2015-12-22'),('2015-12-23'),('2015-12-24'),('2015-12-28'),('2015-12-29'),('2015-12-30'),('2015-12-31'),('2016-01-04'),('2016-01-05'),('2016-01-06'),('2016-01-07'),('2016-01-08'),('2016-01-11'),('2016-01-12'),('2016-01-13'),('2016-01-14'),('2016-01-15'),('2016-01-18'),('2016-01-19'),('2016-01-20'),('2016-01-21'),('2016-01-22'),('2016-01-25'),('2016-01-26'),('2016-01-27'),('2016-01-28'),('2016-01-29'),('2016-02-01'),('2016-02-02'),('2016-02-03'),('2016-02-04'),('2016-02-05'),('2016-02-08'),('2016-02-09'),('2016-02-10'),('2016-02-11'),('2016-02-12'),('2016-02-15'),('2016-02-16'),('2016-02-17'),('2016-02-18'),('2016-02-19'),('2016-02-22'),('2016-02-23'),('2016-02-24'),('2016-02-25'),('2016-02-26'),('2016-02-29'),('2016-03-01'),('2016-03-02'),('2016-03-03'),('2016-03-04'),('2016-03-07'),('2016-03-08'),('2016-03-09'),('2016-03-10'),('2016-03-11'),('2016-03-14'),('2016-03-15'),('2016-03-16'),('2016-03-17'),('2016-03-18'),('2016-03-21'),('2016-03-22'),('2016-03-23'),('2016-03-24'),('2016-03-25'),('2016-03-28'),('2016-03-29'),('2016-03-30'),('2016-03-31'),('2016-04-01'),('2016-04-04'),('2016-04-05'),('2016-04-06'),('2016-04-07'),('2016-04-08'),('2016-04-11'),('2016-04-12'),('2016-04-13'),('2016-04-14'),('2016-04-15'),('2016-04-18'),('2016-04-19'),('2016-04-20'),('2016-04-21'),('2016-04-22'),('2016-04-25'),('2016-04-26'),('2016-04-27'),('2016-04-28'),('2016-04-29'),('2016-05-02'),('2016-05-03'),('2016-05-04'),('2016-05-05'),('2016-05-06'),('2016-05-09'),('2016-05-10'),('2016-05-11'),('2016-05-12'),('2016-05-13'),('2016-05-16'),('2016-05-17'),('2016-05-18'),('2016-05-19'),('2016-05-20'),('2016-05-23'),('2016-05-24'),('2016-05-25'),('2016-05-26'),('2016-05-27'),('2016-05-30'),('2016-05-31'),('2016-06-01'),('2016-06-02'),('2016-06-03'),('2016-06-06'),('2016-06-07'),('2016-06-08'),('2016-06-09'),('2016-06-10'),('2016-06-13'),('2016-06-14'),('2016-06-15'),('2016-06-16'),('2016-06-17'),('2016-06-20'),('2016-06-21'),('2016-06-22'),('2016-06-23'),('2016-06-24'),('2016-06-27'),('2016-06-28'),('2016-06-29'),('2016-06-30'),('2016-07-01'),('2016-07-04'),('2016-07-05'),('2016-07-06'),('2016-07-07'),('2016-07-08'),('2016-07-11'),('2016-07-12'),('2016-07-13'),('2016-07-14'),('2016-07-15'),('2016-07-18'),('2016-07-19'),('2016-07-20'),('2016-07-21'),('2016-07-22'),('2016-07-25'),('2016-07-26'),('2016-07-27'),('2016-07-28'),('2016-07-29'),('2016-08-01'),('2016-08-02'),('2016-08-03'),('2016-08-04'),('2016-08-05'),('2016-08-08'),('2016-08-09'),('2016-08-10'),('2016-08-11'),('2016-08-12'),('2016-08-15'),('2016-08-16'),('2016-08-17'),('2016-08-18'),('2016-08-19'),('2016-08-22'),('2016-08-23'),('2016-08-24'),('2016-08-25'),('2016-08-26'),('2016-08-29'),('2016-08-30'),('2016-08-31'),('2016-09-01'),('2016-09-02'),('2016-09-05'),('2016-09-06'),('2016-09-07'),('2016-09-08'),('2016-09-09'),('2016-09-12'),('2016-09-13'),('2016-09-14'),('2016-09-15'),('2016-09-16'),('2016-09-19'),('2016-09-20'),('2016-09-21'),('2016-09-22'),('2016-09-23'),('2016-09-26'),('2016-09-27'),('2016-09-28'),('2016-09-29'),('2016-09-30'),('2016-10-03'),('2016-10-04'),('2016-10-05'),('2016-10-06'),('2016-10-07'),('2016-10-10'),('2016-10-11'),('2016-10-12'),('2016-10-13'),('2016-10-14'),('2016-10-17'),('2016-10-18'),('2016-10-19'),('2016-10-20'),('2016-10-21'),('2016-10-24'),('2016-10-25'),('2016-10-26'),('2016-10-27'),('2016-10-28'),('2016-10-31'),('2016-11-01'),('2016-11-02'),('2016-11-03'),('2016-11-04'),('2016-11-07'),('2016-11-08'),('2016-11-09'),('2016-11-10'),('2016-11-11'),('2016-11-14'),('2016-11-15'),('2016-11-16'),('2016-11-17'),('2016-11-18'),('2016-11-21'),('2016-11-22'),('2016-11-23'),('2016-11-24'),('2016-11-25'),('2016-11-28'),('2016-11-29'),('2016-11-30'),('2016-12-01'),('2016-12-02'),('2016-12-05'),('2016-12-06'),('2016-12-07'),('2016-12-08'),('2016-12-09'),('2016-12-12'),('2016-12-13'),('2016-12-14'),('2016-12-15'),('2016-12-16'),('2016-12-19'),('2016-12-20'),('2016-12-21'),('2016-12-22'),('2016-12-23'),('2016-12-26'),('2016-12-27'),('2016-12-28'),('2016-12-29'),('2016-12-30'),('2017-01-02'),('2017-01-03'),('2017-01-04'),('2017-01-05'),('2017-01-06'),('2017-01-09'),('2017-01-10'),('2017-01-11'),('2017-01-12'),('2017-01-13'),('2017-01-16'),('2017-01-17'),('2017-01-18'),('2017-01-19'),('2017-01-20'),('2017-01-23'),('2017-01-24'),('2017-01-25'),('2017-01-26'),('2017-01-27'),('2017-01-30'),('2017-01-31'),('2017-02-01'),('2017-02-02'),('2017-02-03'),('2017-02-06'),('2017-02-07'),('2017-02-08'),('2017-02-09'),('2017-02-10'),('2017-02-13'),('2017-02-14'),('2017-02-15'),('2017-02-16'),('2017-02-17'),('2017-02-20'),('2017-02-21'),('2017-02-22'),('2017-02-23'),('2017-02-24'),('2017-02-27'),('2017-02-28'),('2017-03-01'),('2017-03-02'),('2017-03-03'),('2017-03-06'),('2017-03-07'),('2017-03-08'),('2017-03-09'),('2017-03-10'),('2017-03-13'),('2017-03-14'),('2017-03-15'),('2017-03-16'),('2017-03-17'),('2017-03-20'),('2017-03-21'),('2017-03-22'),('2017-03-23'),('2017-03-24'),('2017-03-27'),('2017-03-28'),('2017-03-29'),('2017-03-30'),('2017-03-31'),('2017-04-03'),('2017-04-04'),('2017-04-05'),('2017-04-06'),('2017-04-07'),('2017-04-10'),('2017-04-11'),('2017-04-12'),('2017-04-13'),('2017-04-14'),('2017-04-17'),('2017-04-18'),('2017-04-19'),('2017-04-20'),('2017-04-21'),('2017-04-24'),('2017-04-25'),('2017-04-26'),('2017-04-27'),('2017-04-28'),('2017-05-01'),('2017-05-02'),('2017-05-03'),('2017-05-04'),('2017-05-05'),('2017-05-08'),('2017-05-09'),('2017-05-10'),('2017-05-11'),('2017-05-12'),('2017-05-15'),('2017-05-16'),('2017-05-17'),('2017-05-18'),('2017-05-19'),('2017-05-22'),('2017-05-23'),('2017-05-24'),('2017-05-25'),('2017-05-26'),('2017-05-29'),('2017-05-30'),('2017-05-31'),('2017-06-01'),('2017-06-02'),('2017-06-05'),('2017-06-06'),('2017-06-07'),('2017-06-08'),('2017-06-09'),('2017-06-12'),('2017-06-13'),('2017-06-14'),('2017-06-15'),('2017-06-16'),('2017-06-19'),('2017-06-20'),('2017-06-21'),('2017-06-22'),('2017-06-23'),('2017-06-26'),('2017-06-27'),('2017-06-28'),('2017-06-29'),('2017-06-30'),('2017-07-03'),('2017-07-04'),('2017-07-05'),('2017-07-06'),('2017-07-07'),('2017-07-10'),('2017-07-11'),('2017-07-12'),('2017-07-13'),('2017-07-14'),('2017-07-17'),('2017-07-18'),('2017-07-19'),('2017-07-20'),('2017-07-21'),('2017-07-24'),('2017-07-25'),('2017-07-26'),('2017-07-27'),('2017-07-28'),('2017-07-31'),('2017-08-01'),('2017-08-02'),('2017-08-03'),('2017-08-04'),('2017-08-07'),('2017-08-08'),('2017-08-09'),('2017-08-10'),('2017-08-11'),('2017-08-14'),('2017-08-15'),('2017-08-16'),('2017-08-17'),('2017-08-18'),('2017-08-21'),('2017-08-22'),('2017-08-23'),('2017-08-24'),('2017-08-25'),('2017-08-28'),('2017-08-29'),('2017-08-30'),('2017-08-31'),('2017-09-01'),('2017-09-04'),('2017-09-05'),('2017-09-06'),('2017-09-07'),('2017-09-08'),('2017-09-11'),('2017-09-12'),('2017-09-13'),('2017-09-14'),('2017-09-15'),('2017-09-18'),('2017-09-19'),('2017-09-20'),('2017-09-21'),('2017-09-22'),('2017-09-25'),('2017-09-26'),('2017-09-27'),('2017-09-28'),('2017-09-29'),('2017-10-02'),('2017-10-03'),('2017-10-04'),('2017-10-05'),('2017-10-06'),('2017-10-09'),('2017-10-10'),('2017-10-11'),('2017-10-12'),('2017-10-13'),('2017-10-16'),('2017-10-17'),('2017-10-18'),('2017-10-19'),('2017-10-20'),('2017-10-23'),('2017-10-24'),('2017-10-25'),('2017-10-26'),('2017-10-27'),('2017-10-30'),('2017-10-31'),('2017-11-01'),('2017-11-02'),('2017-11-03'),('2017-11-06'),('2017-11-07'),('2017-11-08'),('2017-11-09'),('2017-11-10'),('2017-11-13'),('2017-11-14'),('2017-11-15'),('2017-11-16'),('2017-11-17'),('2017-11-20'),('2017-11-21'),('2017-11-22'),('2017-11-23'),('2017-11-24'),('2017-11-27'),('2017-11-28'),('2017-11-29'),('2017-11-30'),('2017-12-01'),('2017-12-04'),('2017-12-05'),('2017-12-06'),('2017-12-07'),('2017-12-08'),('2017-12-11'),('2017-12-12'),('2017-12-13'),('2017-12-14'),('2017-12-15'),('2017-12-18'),('2017-12-19'),('2017-12-20'),('2017-12-21'),('2017-12-22'),('2017-12-26'),('2017-12-27'),('2017-12-28'),('2017-12-29'),('2018-01-02'),('2018-01-03'),('2018-01-04'),('2018-01-05'),('2018-01-08'),('2018-01-09'),('2018-01-10'),('2018-01-11'),('2018-01-12'),('2018-01-15'),('2018-01-16'),('2018-01-17'),('2018-01-18'),('2018-01-19'),('2018-01-22'),('2018-01-23'),('2018-01-24'),('2018-01-25'),('2018-01-26'),('2018-01-29'),('2018-01-30'),('2018-01-31'),('2018-02-01'),('2018-02-02'),('2018-02-05'),('2018-02-06'),('2018-02-07'),('2018-02-08'),('2018-02-09'),('2018-02-12'),('2018-02-13'),('2018-02-14'),('2018-02-15'),('2018-02-16'),('2018-02-19'),('2018-02-20'),('2018-02-21'),('2018-02-22'),('2018-02-23'),('2018-02-26'),('2018-02-27'),('2018-02-28'),('2018-03-01'),('2018-03-02'),('2018-03-05'),('2018-03-06'),('2018-03-07'),('2018-03-08'),('2018-03-09'),('2018-03-12'),('2018-03-13'),('2018-03-14'),('2018-03-15'),('2018-03-16'),('2018-03-19'),('2018-03-20'),('2018-03-21'),('2018-03-22'),('2018-03-23'),('2018-03-26'),('2018-03-27'),('2018-03-28'),('2018-03-29'),('2018-03-30'),('2018-04-02'),('2018-04-03'),('2018-04-04'),('2018-04-05'),('2018-04-06'),('2018-04-09'),('2018-04-10'),('2018-04-11'),('2018-04-12'),('2018-04-13'),('2018-04-16'),('2018-04-17'),('2018-04-18'),('2018-04-19'),('2018-04-20'),('2018-04-23'),('2018-04-24'),('2018-04-25'),('2018-04-26'),('2018-04-27'),('2018-04-30'),('2018-05-01'),('2018-05-02'),('2018-05-03'),('2018-05-04'),('2018-05-07'),('2018-05-08'),('2018-05-09'),('2018-05-10'),('2018-05-11'),('2018-05-14'),('2018-05-15'),('2018-05-16'),('2018-05-17'),('2018-05-18'),('2018-05-21'),('2018-05-22'),('2018-05-23'),('2018-05-24'),('2018-05-25'),('2018-05-28'),('2018-05-29'),('2018-05-30'),('2018-05-31'),('2018-06-01'),('2018-06-04'),('2018-06-05'),('2018-06-06'),('2018-06-07'),('2018-06-08'),('2018-06-11'),('2018-06-12'),('2018-06-13'),('2018-06-14'),('2018-06-15'),('2018-06-18'),('2018-06-19'),('2018-06-20'),('2018-06-21'),('2018-06-22'),('2018-06-25'),('2018-06-26'),('2018-06-27'),('2018-06-28'),('2018-06-29'),('2018-07-02'),('2018-07-03'),('2018-07-04'),('2018-07-05'),('2018-07-06'),('2018-07-09'),('2018-07-10'),('2018-07-11'),('2018-07-12'),('2018-07-13'),('2018-07-16'),('2018-07-17'),('2018-07-18'),('2018-07-19'),('2018-07-20'),('2018-07-23'),('2018-07-24'),('2018-07-25'),('2018-07-26'),('2018-07-27'),('2018-07-30'),('2018-07-31'),('2018-08-01'),('2018-08-02'),('2018-08-03'),('2018-08-06'),('2018-08-07'),('2018-08-08'),('2018-08-09'),('2018-08-10'),('2018-08-13'),('2018-08-14'),('2018-08-15'),('2018-08-16'),('2018-08-17'),('2018-08-20'),('2018-08-21'),('2018-08-22'),('2018-08-23'),('2018-08-24'),('2018-08-27'),('2018-08-28'),('2018-08-29'),('2018-08-30'),('2018-08-31'),('2018-09-03'),('2018-09-04'),('2018-09-05'),('2018-09-06'),('2018-09-07'),('2018-09-10'),('2018-09-11'),('2018-09-12'),('2018-09-13'),('2018-09-14'),('2018-09-17'),('2018-09-18'),('2018-09-19'),('2018-09-20'),('2018-09-21'),('2018-09-24'),('2018-09-25'),('2018-09-26'),('2018-09-27'),('2018-09-28'),('2018-10-01'),('2018-10-02'),('2018-10-03'),('2018-10-04'),('2018-10-05'),('2018-10-08'),('2018-10-09'),('2018-10-10'),('2018-10-11'),('2018-10-12'),('2018-10-15'),('2018-10-16'),('2018-10-17'),('2018-10-18'),('2018-10-19'),('2018-10-22'),('2018-10-23'),('2018-10-24'),('2018-10-25'),('2018-10-26'),('2018-10-29'),('2018-10-30'),('2018-10-31'),('2018-11-01'),('2018-11-02'),('2018-11-05'),('2018-11-06'),('2018-11-07'),('2018-11-08'),('2018-11-09'),('2018-11-12'),('2018-11-13'),('2018-11-14'),('2018-11-15'),('2018-11-16'),('2018-11-19'),('2018-11-20'),('2018-11-21'),('2018-11-22'),('2018-11-23'),('2018-11-26'),('2018-11-27'),('2018-11-28'),('2018-11-29'),('2018-11-30'),('2018-12-03'),('2018-12-04'),('2018-12-05'),('2018-12-06'),('2018-12-07'),('2018-12-10'),('2018-12-11'),('2018-12-12'),('2018-12-13'),('2018-12-14'),('2018-12-17'),('2018-12-18'),('2018-12-19'),('2018-12-20'),('2018-12-21'),('2018-12-24'),('2018-12-26'),('2018-12-27'),('2018-12-28'),('2018-12-31'),('2019-01-02'),('2019-01-03'),('2019-01-04'),('2019-01-07'),('2019-01-08'),('2019-01-09'),('2019-01-10'),('2019-01-11'),('2019-01-14'),('2019-01-15'),('2019-01-16'),('2019-01-17'),('2019-01-18'),('2019-01-21'),('2019-01-22'),('2019-01-23'),('2019-01-24'),('2019-01-25'),('2019-01-28'),('2019-01-29'),('2019-01-30'),('2019-01-31'),('2019-02-01'),('2019-02-04'),('2019-02-05'),('2019-02-06'),('2019-02-07'),('2019-02-08'),('2019-02-11'),('2019-02-12'),('2019-02-13'),('2019-02-14'),('2019-02-15'),('2019-02-18'),('2019-02-19'),('2019-02-20'),('2019-02-21'),('2019-02-22'),('2019-02-25'),('2019-02-26'),('2019-02-27'),('2019-02-28'),('2019-03-01'),('2019-03-04'),('2019-03-05'),('2019-03-06'),('2019-03-07'),('2019-03-08'),('2019-03-11'),('2019-03-12'),('2019-03-13'),('2019-03-14'),('2019-03-15'),('2019-03-18'),('2019-03-19'),('2019-03-20'),('2019-03-21'),('2019-03-22'),('2019-03-25'),('2019-03-26'),('2019-03-27'),('2019-03-28'),('2019-03-29'),('2019-04-01'),('2019-04-02'),('2019-04-03'),('2019-04-04'),('2019-04-05'),('2019-04-08'),('2019-04-09'),('2019-04-10'),('2019-04-11'),('2019-04-12'),('2019-04-15'),('2019-04-16'),('2019-04-17'),('2019-04-18'),('2019-04-19'),('2019-04-22'),('2019-04-23'),('2019-04-24'),('2019-04-25'),('2019-04-26'),('2019-04-29'),('2019-04-30'),('2019-05-01'),('2019-05-02'),('2019-05-03'),('2019-05-06'),('2019-05-07'),('2019-05-08'),('2019-05-09'),('2019-05-10'),('2019-05-13'),('2019-05-14'),('2019-05-15'),('2019-05-16'),('2019-05-17'),('2019-05-20'),('2019-05-21'),('2019-05-22'),('2019-05-23'),('2019-05-24'),('2019-05-27'),('2019-05-28'),('2019-05-29'),('2019-05-30'),('2019-05-31'),('2019-06-03'),('2019-06-04'),('2019-06-05'),('2019-06-06'),('2019-06-07'),('2019-06-10'),('2019-06-11'),('2019-06-12'),('2019-06-13'),('2019-06-14'),('2019-06-17'),('2019-06-18'),('2019-06-19'),('2019-06-20'),('2019-06-21'),('2019-06-24'),('2019-06-25'),('2019-06-26'),('2019-06-27'),('2019-06-28'),('2019-07-01'),('2019-07-02'),('2019-07-03'),('2019-07-04'),('2019-07-05'),('2019-07-08'),('2019-07-09'),('2019-07-10'),('2019-07-11'),('2019-07-12'),('2019-07-15'),('2019-07-16'),('2019-07-17'),('2019-07-18'),('2019-07-19'),('2019-07-22'),('2019-07-23'),('2019-07-24'),('2019-07-25'),('2019-07-26'),('2019-07-29'),('2019-07-30'),('2019-07-31'),('2019-08-01'),('2019-08-02'),('2019-08-05'),('2019-08-06'),('2019-08-07'),('2019-08-08'),('2019-08-09'),('2019-08-12'),('2019-08-13'),('2019-08-14'),('2019-08-15'),('2019-08-16'),('2019-08-19'),('2019-08-20'),('2019-08-21'),('2019-08-22'),('2019-08-23'),('2019-08-26'),('2019-08-27'),('2019-08-28'),('2019-08-29'),('2019-08-30'),('2019-09-02'),('2019-09-03'),('2019-09-04'),('2019-09-05'),('2019-09-06'),('2019-09-09'),('2019-09-10'),('2019-09-11'),('2019-09-12'),('2019-09-13'),('2019-09-16'),('2019-09-17'),('2019-09-18'),('2019-09-19'),('2019-09-20'),('2019-09-23'),('2019-09-24'),('2019-09-25'),('2019-09-26'),('2019-09-27'),('2019-09-30'),('2019-10-01'),('2019-10-02'),('2019-10-03'),('2019-10-04'),('2019-10-07'),('2019-10-08'),('2019-10-09'),('2019-10-10'),('2019-10-11'),('2019-10-14'),('2019-10-15'),('2019-10-16'),('2019-10-17'),('2019-10-18'),('2019-10-21'),('2019-10-22'),('2019-10-23'),('2019-10-24'),('2019-10-25'),('2019-10-28'),('2019-10-29'),('2019-10-30'),('2019-10-31'),('2019-11-01'),('2019-11-04'),('2019-11-05'),('2019-11-06'),('2019-11-07'),('2019-11-08'),('2019-11-11'),('2019-11-12'),('2019-11-13'),('2019-11-14'),('2019-11-15'),('2019-11-18'),('2019-11-19'),('2019-11-20'),('2019-11-21'),('2019-11-22'),('2019-11-25'),('2019-11-26'),('2019-11-27'),('2019-11-28'),('2019-11-29'),('2019-12-02'),('2019-12-03'),('2019-12-04'),('2019-12-05'),('2019-12-06'),('2019-12-09'),('2019-12-10'),('2019-12-11'),('2019-12-12'),('2019-12-13'),('2019-12-16'),('2019-12-17'),('2019-12-18'),('2019-12-19'),('2019-12-20'),('2019-12-23'),('2019-12-24'),('2019-12-26'),('2019-12-27'),('2019-12-30'),('2019-12-31'),('2020-01-02'),('2020-01-03'),('2020-01-06'),('2020-01-07'),('2020-01-08'),('2020-01-09'),('2020-01-10'),('2020-01-13'),('2020-01-14'),('2020-01-15'),('2020-01-16'),('2020-01-17'),('2020-01-20'),('2020-01-21'),('2020-01-22'),('2020-01-23'),('2020-01-24'),('2020-01-27'),('2020-01-28'),('2020-01-29'),('2020-01-30'),('2020-01-31'),('2020-02-03'),('2020-02-04'),('2020-02-05'),('2020-02-06'),('2020-02-07'),('2020-02-10'),('2020-02-11'),('2020-02-12'),('2020-02-13'),('2020-02-14'),('2020-02-17'),('2020-02-18'),('2020-02-19'),('2020-02-20'),('2020-02-21'),('2020-02-24'),('2020-02-25'),('2020-02-26'),('2020-02-27'),('2020-02-28'),('2020-03-02'),('2020-03-03'),('2020-03-04'),('2020-03-05'),('2020-03-06'),('2020-03-09'),('2020-03-10'),('2020-03-11'),('2020-03-12'),('2020-03-13'),('2020-03-16'),('2020-03-17'),('2020-03-18'),('2020-03-19'),('2020-03-20'),('2020-03-23'),('2020-03-24'),('2020-03-25'),('2020-03-26'),('2020-03-27'),('2020-03-30'),('2020-03-31'),('2020-04-01'),('2020-04-02'),('2020-04-03'),('2020-04-06'),('2020-04-07'),('2020-04-08'),('2020-04-09'),('2020-04-10'),('2020-04-13'),('2020-04-14'),('2020-04-15'),('2020-04-16'),('2020-04-17'),('2020-04-20'),('2020-04-21'),('2020-04-22'),('2020-04-23'),('2020-04-24'),('2020-04-27'),('2020-04-28'),('2020-04-29'),('2020-04-30'),('2020-05-01'),('2020-05-04'),('2020-05-05'),('2020-05-06'),('2020-05-07'),('2020-05-08'),('2020-05-11'),('2020-05-12'),('2020-05-13'),('2020-05-14'),('2020-05-15'),('2020-05-18'),('2020-05-19'),('2020-05-20'),('2020-05-21'),('2020-05-22'),('2020-05-25'),('2020-05-26'),('2020-05-27'),('2020-05-28'),('2020-05-29'),('2020-06-01'),('2020-06-02'),('2020-06-03'),('2020-06-04'),('2020-06-05'),('2020-06-08'),('2020-06-09'),('2020-06-10'),('2020-06-11'),('2020-06-12'),('2020-06-15'),('2020-06-16'),('2020-06-17'),('2020-06-18'),('2020-06-19'),('2020-06-22'),('2020-06-23'),('2020-06-24'),('2020-06-25'),('2020-06-26'),('2020-06-29'),('2020-06-30'),('2020-07-01'),('2020-07-02'),('2020-07-03'),('2020-07-06'),('2020-07-07'),('2020-07-08'),('2020-07-09'),('2020-07-10'),('2020-07-13'),('2020-07-14'),('2020-07-15'),('2020-07-16'),('2020-07-17'),('2020-07-20'),('2020-07-21'),('2020-07-22'),('2020-07-23'),('2020-07-24'),('2020-07-27'),('2020-07-28'),('2020-07-29'),('2020-07-30'),('2020-07-31'),('2020-08-03'),('2020-08-04'),('2020-08-05'),('2020-08-06'),('2020-08-07'),('2020-08-10'),('2020-08-11'),('2020-08-12'),('2020-08-13'),('2020-08-14'),('2020-08-17'),('2020-08-18'),('2020-08-19'),('2020-08-20'),('2020-08-21'),('2020-08-24'),('2020-08-25'),('2020-08-26'),('2020-08-27'),('2020-08-28'),('2020-08-31'),('2020-09-01'),('2020-09-02'),('2020-09-03'),('2020-09-04'),('2020-09-07'),('2020-09-08'),('2020-09-09'),('2020-09-10'),('2020-09-11'),('2020-09-14'),('2020-09-15'),('2020-09-16'),('2020-09-17'),('2020-09-18'),('2020-09-21'),('2020-09-22'),('2020-09-23'),('2020-09-24'),('2020-09-25'),('2020-09-28'),('2020-09-29'),('2020-09-30'),('2020-10-01'),('2020-10-02'),('2020-10-05'),('2020-10-06'),('2020-10-07'),('2020-10-08'),('2020-10-09'),('2020-10-12'),('2020-10-13'),('2020-10-14'),('2020-10-15'),('2020-10-16'),('2020-10-19'),('2020-10-20'),('2020-10-21'),('2020-10-22'),('2020-10-23'),('2020-10-26'),('2020-10-27'),('2020-10-28'),('2020-10-29'),('2020-10-30'),('2020-11-02'),('2020-11-03'),('2020-11-04'),('2020-11-05'),('2020-11-06'),('2020-11-09'),('2020-11-10'),('2020-11-11'),('2020-11-12'),('2020-11-13'),('2020-11-16'),('2020-11-17'),('2020-11-18'),('2020-11-19'),('2020-11-20'),('2020-11-23'),('2020-11-24'),('2020-11-25'),('2020-11-26'),('2020-11-27'),('2020-11-30'),('2020-12-01'),('2020-12-02'),('2020-12-03'),('2020-12-04'),('2020-12-07'),('2020-12-08'),('2020-12-09'),('2020-12-10'),('2020-12-11'),('2020-12-14'),('2020-12-15'),('2020-12-16'),('2020-12-17'),('2020-12-18'),('2020-12-21'),('2020-12-22'),('2020-12-23'),('2020-12-24'),('2020-12-28'),('2020-12-29'),('2020-12-30'),('2020-12-31'),('2021-01-04'),('2021-01-05'),('2021-01-06'),('2021-01-07'),('2021-01-08'),('2021-01-11'),('2021-01-12'),('2021-01-13'),('2021-01-14'),('2021-01-15'),('2021-01-18'),('2021-01-19'),('2021-01-20'),('2021-01-21'),('2021-01-22'),('2021-01-25'),('2021-01-26'),('2021-01-27'),('2021-01-28'),('2021-01-29'),('2021-02-01'),('2021-02-02'),('2021-02-03'),('2021-02-04'),('2021-02-05'),('2021-02-08'),('2021-02-09'),('2021-02-10'),('2021-02-11'),('2021-02-12'),('2021-02-15'),('2021-02-16'),('2021-02-17'),('2021-02-18'),('2021-02-19'),('2021-02-22'),('2021-02-23'),('2021-02-24'),('2021-02-25'),('2021-02-26'),('2021-03-01'),('2021-03-02'),('2021-03-03'),('2021-03-04'),('2021-03-05'),('2021-03-08'),('2021-03-09'),('2021-03-10'),('2021-03-11'),('2021-03-12'),('2021-03-15'),('2021-03-16'),('2021-03-17'),('2021-03-18'),('2021-03-19'),('2021-03-22'),('2021-03-23'),('2021-03-24'),('2021-03-25'),('2021-03-26'),('2021-03-29'),('2021-03-30'),('2021-03-31'),('2021-04-01'),('2021-04-02'),('2021-04-05'),('2021-04-06'),('2021-04-07'),('2021-04-08'),('2021-04-09'),('2021-04-12'),('2021-04-13'),('2021-04-14'),('2021-04-15'),('2021-04-16'),('2021-04-19'),('2021-04-20'),('2021-04-21'),('2021-04-22'),('2021-04-23'),('2021-04-26'),('2021-04-27'),('2021-04-28'),('2021-04-29'),('2021-04-30'),('2021-05-03'),('2021-05-04'),('2021-05-05'),('2021-05-06'),('2021-05-07'),('2021-05-10'),('2021-05-11'),('2021-05-12'),('2021-05-13'),('2021-05-14'),('2021-05-17'),('2021-05-18'),('2021-05-19'),('2021-05-20'),('2021-05-21'),('2021-05-24'),('2021-05-25'),('2021-05-26'),('2021-05-27'),('2021-05-28'),('2021-05-31'),('2021-06-01'),('2021-06-02'),('2021-06-03'),('2021-06-04'),('2021-06-07'),('2021-06-08'),('2021-06-09'),('2021-06-10'),('2021-06-11'),('2021-06-14'),('2021-06-15'),('2021-06-16'),('2021-06-17'),('2021-06-18'),('2021-06-21'),('2021-06-22'),('2021-06-23'),('2021-06-24'),('2021-06-25'),('2021-06-28'),('2021-06-29'),('2021-06-30'),('2021-07-01'),('2021-07-02'),('2021-07-05'),('2021-07-06'),('2021-07-07'),('2021-07-08'),('2021-07-09'),('2021-07-12'),('2021-07-13'),('2021-07-14'),('2021-07-15'),('2021-07-16'),('2021-07-19'),('2021-07-20'),('2021-07-21'),('2021-07-22'),('2021-07-23'),('2021-07-26'),('2021-07-27'),('2021-07-28'),('2021-07-29'),('2021-07-30'),('2021-08-02'),('2021-08-03'),('2021-08-04'),('2021-08-05'),('2021-08-06'),('2021-08-09'),('2021-08-10'),('2021-08-11'),('2021-08-12'),('2021-08-13'),('2021-08-16'),('2021-08-17'),('2021-08-18'),('2021-08-19'),('2021-08-20'),('2021-08-23'),('2021-08-24'),('2021-08-25'),('2021-08-26'),('2021-08-27'),('2021-08-30'),('2021-08-31'),('2021-09-01'),('2021-09-02'),('2021-09-03'),('2021-09-06'),('2021-09-07'),('2021-09-08'),('2021-09-09'),('2021-09-10'),('2021-09-13'),('2021-09-14'),('2021-09-15'),('2021-09-16'),('2021-09-17'),('2021-09-20'),('2021-09-21'),('2021-09-22'),('2021-09-23'),('2021-09-24'),('2021-09-27'),('2021-09-28'),('2021-09-29'),('2021-09-30'),('2021-10-01'),('2021-10-04'),('2021-10-05'),('2021-10-06'),('2021-10-07'),('2021-10-08'),('2021-10-11'),('2021-10-12'),('2021-10-13'),('2021-10-14'),('2021-10-15'),('2021-10-18'),('2021-10-19'),('2021-10-20'),('2021-10-21'),('2021-10-22'),('2021-10-25'),('2021-10-26'),('2021-10-27'),('2021-10-28'),('2021-10-29'),('2021-11-01'),('2021-11-02'),('2021-11-03'),('2021-11-04'),('2021-11-05'),('2021-11-08'),('2021-11-09'),('2021-11-10'),('2021-11-11'),('2021-11-12'),('2021-11-15'),('2021-11-16'),('2021-11-17'),('2021-11-18'),('2021-11-19'),('2021-11-22'),('2021-11-23'),('2021-11-24'),('2021-11-25'),('2021-11-26'),('2021-11-29'),('2021-11-30'),('2021-12-01'),('2021-12-02'),('2021-12-03'),('2021-12-06'),('2021-12-07'),('2021-12-08'),('2021-12-09'),('2021-12-10'),('2021-12-13'),('2021-12-14'),('2021-12-15'),('2021-12-16'),('2021-12-17'),('2021-12-20'),('2021-12-21'),('2021-12-22'),('2021-12-23'),('2021-12-24'),('2021-12-27'),('2021-12-28'),('2021-12-29'),('2021-12-30'),('2021-12-31'),('2022-01-03'),('2022-01-04'),('2022-01-05'),('2022-01-06'),('2022-01-07'),('2022-01-10'),('2022-01-11'),('2022-01-12'),('2022-01-13'),('2022-01-14'),('2022-01-17'),('2022-01-18'),('2022-01-19'),('2022-01-20'),('2022-01-21'),('2022-01-24'),('2022-01-25'),('2022-01-26'),('2022-01-27'),('2022-01-28'),('2022-01-31'),('2022-02-01'),('2022-02-02'),('2022-02-03'),('2022-02-04'),('2022-02-07'),('2022-02-08'),('2022-02-09'),('2022-02-10'),('2022-02-11'),('2022-02-14'),('2022-02-15'),('2022-02-16'),('2022-02-17'),('2022-02-18'),('2022-02-21'),('2022-02-22'),('2022-02-23'),('2022-02-24'),('2022-02-25'),('2022-02-28'),('2022-03-01'),('2022-03-02'),('2022-03-03'),('2022-03-04'),('2022-03-07'),('2022-03-08'),('2022-03-09'),('2022-03-10'),('2022-03-11'),('2022-03-14'),('2022-03-15'),('2022-03-16'),('2022-03-17'),('2022-03-18'),('2022-03-21'),('2022-03-22'),('2022-03-23'),('2022-03-24'),('2022-03-25'),('2022-03-28'),('2022-03-29'),('2022-03-30'),('2022-03-31'),('2022-04-01'),('2022-04-04'),('2022-04-05'),('2022-04-06'),('2022-04-07'),('2022-04-08'),('2022-04-11'),('2022-04-12'),('2022-04-13'),('2022-04-14'),('2022-04-15'),('2022-04-18'),('2022-04-19'),('2022-04-20'),('2022-04-21'),('2022-04-22'),('2022-04-25'),('2022-04-26'),('2022-04-27'),('2022-04-28'),('2022-04-29'),('2022-05-02'),('2022-05-03'),('2022-05-04'),('2022-05-05'),('2022-05-06'),('2022-05-09'),('2022-05-10'),('2022-05-11'),('2022-05-12'),('2022-05-13'),('2022-05-16'),('2022-05-17'),('2022-05-18'),('2022-05-19'),('2022-05-20'),('2022-05-23'),('2022-05-24'),('2022-05-25'),('2022-05-26'),('2022-05-27'),('2022-05-30'),('2022-05-31'),('2022-06-01'),('2022-06-02'),('2022-06-03'),('2022-06-06'),('2022-06-07'),('2022-06-08'),('2022-06-09'),('2022-06-10'),('2022-06-13'),('2022-06-14'),('2022-06-15'),('2022-06-16'),('2022-06-17'),('2022-06-20'),('2022-06-21'),('2022-06-22'),('2022-06-23'),('2022-06-24'),('2022-06-27'),('2022-06-28'),('2022-06-29'),('2022-06-30'),('2022-07-01'),('2022-07-04'),('2022-07-05'),('2022-07-06'),('2022-07-07'),('2022-07-08'),('2022-07-11'),('2022-07-12'),('2022-07-13'),('2022-07-14'),('2022-07-15'),('2022-07-18'),('2022-07-19'),('2022-07-20'),('2022-07-21'),('2022-07-22'),('2022-07-25'),('2022-07-26'),('2022-07-27'),('2022-07-28'),('2022-07-29'),('2022-08-01'),('2022-08-02'),('2022-08-03'),('2022-08-04'),('2022-08-05'),('2022-08-08'),('2022-08-09'),('2022-08-10'),('2022-08-11'),('2022-08-12'),('2022-08-15'),('2022-08-16'),('2022-08-17'),('2022-08-18'),('2022-08-19'),('2022-08-22'),('2022-08-23'),('2022-08-24'),('2022-08-25'),('2022-08-26'),('2022-08-29'),('2022-08-30'),('2022-08-31'),('2022-09-01'),('2022-09-02'),('2022-09-05'),('2022-09-06'),('2022-09-07'),('2022-09-08'),('2022-09-09'),('2022-09-12'),('2022-09-13'),('2022-09-14'),('2022-09-15'),('2022-09-16'),('2022-09-19'),('2022-09-20'),('2022-09-21'),('2022-09-22'),('2022-09-23'),('2022-09-26'),('2022-09-27'),('2022-09-28'),('2022-09-29'),('2022-09-30'),('2022-10-03'),('2022-10-04'),('2022-10-05'),('2022-10-06'),('2022-10-07'),('2022-10-10'),('2022-10-11'),('2022-10-12'),('2022-10-13'),('2022-10-14'),('2022-10-17'),('2022-10-18'),('2022-10-19'),('2022-10-20'),('2022-10-21'),('2022-10-24'),('2022-10-25'),('2022-10-26'),('2022-10-27'),('2022-10-28'),('2022-10-31'),('2022-11-01'),('2022-11-02'),('2022-11-03'),('2022-11-04'),('2022-11-07'),('2022-11-08'),('2022-11-09'),('2022-11-10'),('2022-11-11'),('2022-11-14'),('2022-11-15'),('2022-11-16'),('2022-11-17'),('2022-11-18'),('2022-11-21'),('2022-11-22'),('2022-11-23'),('2022-11-24'),('2022-11-25'),('2022-11-28'),('2022-11-29'),('2022-11-30'),('2022-12-01'),('2022-12-02'),('2022-12-05'),('2022-12-06'),('2022-12-07'),('2022-12-08'),('2022-12-09'),('2022-12-12'),('2022-12-13'),('2022-12-14'),('2022-12-15'),('2022-12-16'),('2022-12-19'),('2022-12-20'),('2022-12-21'),('2022-12-22'),('2022-12-23'),('2022-12-26'),('2022-12-27'),('2022-12-28'),('2022-12-29'),('2022-12-30'),('2023-01-02'),('2023-01-03'),('2023-01-04'),('2023-01-05'),('2023-01-06'),('2023-01-09'),('2023-01-10'),('2023-01-11'),('2023-01-12'),('2023-01-13'),('2023-01-16'),('2023-01-17'),('2023-01-18'),('2023-01-19'),('2023-01-20'),('2023-01-23'),('2023-01-24'),('2023-01-25'),('2023-01-26'),('2023-01-27'),('2023-01-30'),('2023-01-31'),('2023-02-01'),('2023-02-02'),('2023-02-03'),('2023-02-06'),('2023-02-07'),('2023-02-08'),('2023-02-09'),('2023-02-10'),('2023-02-13'),('2023-02-14'),('2023-02-15'),('2023-02-16'),('2023-02-17'),('2023-02-20'),('2023-02-21'),('2023-02-22'),('2023-02-23'),('2023-02-24'),('2023-02-27'),('2023-02-28'),('2023-03-01'),('2023-03-02'),('2023-03-03'),('2023-03-06'),('2023-03-07'),('2023-03-08'),('2023-03-09'),('2023-03-10'),('2023-03-13'),('2023-03-14'),('2023-03-15'),('2023-03-16'),('2023-03-17'),('2023-03-20'),('2023-03-21'),('2023-03-22'),('2023-03-23'),('2023-03-24'),('2023-03-27'),('2023-03-28'),('2023-03-29'),('2023-03-30'),('2023-03-31'),('2023-04-03'),('2023-04-04'),('2023-04-05'),('2023-04-06'),('2023-04-07'),('2023-04-10'),('2023-04-11'),('2023-04-12'),('2023-04-13'),('2023-04-14'),('2023-04-17'),('2023-04-18'),('2023-04-19'),('2023-04-20'),('2023-04-21'),('2023-04-24'),('2023-04-25'),('2023-04-26'),('2023-04-27'),('2023-04-28'),('2023-05-01'),('2023-05-02'),('2023-05-03'),('2023-05-04'),('2023-05-05'),('2023-05-08'),('2023-05-09'),('2023-05-10'),('2023-05-11'),('2023-05-12'),('2023-05-15'),('2023-05-16'),('2023-05-17'),('2023-05-18'),('2023-05-19'),('2023-05-22'),('2023-05-23'),('2023-05-24'),('2023-05-25'),('2023-05-26'),('2023-05-29'),('2023-05-30'),('2023-05-31'),('2023-06-01'),('2023-06-02'),('2023-06-05'),('2023-06-06'),('2023-06-07'),('2023-06-08'),('2023-06-09'),('2023-06-12'),('2023-06-13'),('2023-06-14'),('2023-06-15'),('2023-06-16'),('2023-06-19'),('2023-06-20'),('2023-06-21'),('2023-06-22'),('2023-06-23'),('2023-06-26'),('2023-06-27'),('2023-06-28'),('2023-06-29'),('2023-06-30'),('2023-07-03'),('2023-07-04'),('2023-07-05'),('2023-07-06'),('2023-07-07'),('2023-07-10'),('2023-07-11'),('2023-07-12'),('2023-07-13'),('2023-07-14'),('2023-07-17'),('2023-07-18'),('2023-07-19'),('2023-07-20'),('2023-07-21'),('2023-07-24'),('2023-07-25'),('2023-07-26'),('2023-07-27'),('2023-07-28'),('2023-07-31'),('2023-08-01'),('2023-08-02'),('2023-08-03'),('2023-08-04'),('2023-08-07'),('2023-08-08'),('2023-08-09'),('2023-08-10'),('2023-08-11'),('2023-08-14'),('2023-08-15'),('2023-08-16'),('2023-08-17'),('2023-08-18'),('2023-08-21'),('2023-08-22'),('2023-08-23'),('2023-08-24'),('2023-08-25'),('2023-08-28'),('2023-08-29'),('2023-08-30'),('2023-08-31'),('2023-09-01'),('2023-09-04'),('2023-09-05'),('2023-09-06'),('2023-09-07'),('2023-09-08'),('2023-09-11'),('2023-09-12'),('2023-09-13'),('2023-09-14'),('2023-09-15'),('2023-09-18'),('2023-09-19'),('2023-09-20'),('2023-09-21'),('2023-09-22'),('2023-09-25'),('2023-09-26'),('2023-09-27'),('2023-09-28'),('2023-09-29'),('2023-10-02'),('2023-10-03'),('2023-10-04'),('2023-10-05'),('2023-10-06'),('2023-10-09'),('2023-10-10'),('2023-10-11'),('2023-10-12'),('2023-10-13'),('2023-10-16'),('2023-10-17'),('2023-10-18'),('2023-10-19'),('2023-10-20'),('2023-10-23'),('2023-10-24'),('2023-10-25'),('2023-10-26'),('2023-10-27'),('2023-10-30'),('2023-10-31'),('2023-11-01'),('2023-11-02'),('2023-11-03'),('2023-11-06'),('2023-11-07'),('2023-11-08'),('2023-11-09'),('2023-11-10'),('2023-11-13'),('2023-11-14'),('2023-11-15'),('2023-11-16'),('2023-11-17'),('2023-11-20'),('2023-11-21'),('2023-11-22'),('2023-11-23'),('2023-11-24'),('2023-11-27'),('2023-11-28'),('2023-11-29'),('2023-11-30'),('2023-12-01'),('2023-12-04'),('2023-12-05'),('2023-12-06'),('2023-12-07'),('2023-12-08'),('2023-12-11'),('2023-12-12'),('2023-12-13'),('2023-12-14'),('2023-12-15'),('2023-12-18'),('2023-12-19'),('2023-12-20'),('2023-12-21'),('2023-12-22'),('2023-12-26'),('2023-12-27'),('2023-12-28'),('2023-12-29'),('2024-01-02'),('2024-01-03'),('2024-01-04'),('2024-01-05'),('2024-01-08'),('2024-01-09'),('2024-01-10'),('2024-01-11'),('2024-01-12'),('2024-01-15'),('2024-01-16'),('2024-01-17'),('2024-01-18'),('2024-01-19'),('2024-01-22'),('2024-01-23'),('2024-01-24'),('2024-01-25'),('2024-01-26'),('2024-01-29'),('2024-01-30'),('2024-01-31'),('2024-02-01'),('2024-02-02'),('2024-02-05'),('2024-02-06'),('2024-02-07'),('2024-02-08'),('2024-02-09'),('2024-02-12'),('2024-02-13'),('2024-02-14'),('2024-02-15'),('2024-02-16'),('2024-02-19'),('2024-02-20'),('2024-02-21'),('2024-02-22'),('2024-02-23'),('2024-02-26'),('2024-02-27'),('2024-02-28'),('2024-02-29'),('2024-03-01'),('2024-03-04'),('2024-03-05'),('2024-03-06'),('2024-03-07'),('2024-03-08'),('2024-03-11'),('2024-03-12'),('2024-03-13'),('2024-03-14'),('2024-03-15'),('2024-03-18'),('2024-03-19'),('2024-03-20'),('2024-03-21'),('2024-03-22'),('2024-03-25'),('2024-03-26'),('2024-03-27'),('2024-03-28'),('2024-03-29'),('2024-04-01'),('2024-04-02'),('2024-04-03'),('2024-04-04'),('2024-04-05'),('2024-04-08'),('2024-04-09'),('2024-04-10'),('2024-04-11'),('2024-04-12'),('2024-04-15'),('2024-04-16'),('2024-04-17'),('2024-04-18'),('2024-04-19'),('2024-04-22'),('2024-04-23'),('2024-04-24'),('2024-04-25'),('2024-04-26'),('2024-04-29'),('2024-04-30'),('2024-05-01'),('2024-05-02'),('2024-05-03'),('2024-05-06'),('2024-05-07'),('2024-05-08'),('2024-05-09'),('2024-05-10'),('2024-05-13'),('2024-05-14'),('2024-05-15'),('2024-05-16'),('2024-05-17'),('2024-05-20'),('2024-05-21'),('2024-05-22'),('2024-05-23'),('2024-05-24'),('2024-05-27'),('2024-05-28'),('2024-05-29'),('2024-05-30'),('2024-05-31'),('2024-06-03'),('2024-06-04'),('2024-06-05'),('2024-06-06'),('2024-06-07'),('2024-06-10'),('2024-06-11'),('2024-06-12'),('2024-06-13'),('2024-06-14'),('2024-06-17'),('2024-06-18'),('2024-06-19'),('2024-06-20'),('2024-06-21'),('2024-06-24'),('2024-06-25'),('2024-06-26'),('2024-06-27'),('2024-06-28'),('2024-07-01'),('2024-07-02'),('2024-07-03'),('2024-07-04'),('2024-07-05'),('2024-07-08'),('2024-07-09'),('2024-07-10'),('2024-07-11'),('2024-07-12'),('2024-07-15'),('2024-07-16'),('2024-07-17'),('2024-07-18'),('2024-07-19'),('2024-07-22'),('2024-07-23'),('2024-07-24'),('2024-07-25'),('2024-07-26'),('2024-07-29'),('2024-07-30'),('2024-07-31'),('2024-08-01'),('2024-08-02'),('2024-08-05'),('2024-08-06'),('2024-08-07'),('2024-08-08'),('2024-08-09'),('2024-08-12'),('2024-08-13'),('2024-08-14'),('2024-08-15'),('2024-08-16'),('2024-08-19'),('2024-08-20'),('2024-08-21'),('2024-08-22'),('2024-08-23'),('2024-08-26'),('2024-08-27'),('2024-08-28'),('2024-08-29'),('2024-08-30'),('2024-09-02'),('2024-09-03'),('2024-09-04'),('2024-09-05'),('2024-09-06'),('2024-09-09'),('2024-09-10'),('2024-09-11'),('2024-09-12'),('2024-09-13'),('2024-09-16'),('2024-09-17'),('2024-09-18'),('2024-09-19'),('2024-09-20'),('2024-09-23'),('2024-09-24'),('2024-09-25'),('2024-09-26'),('2024-09-27'),('2024-09-30'),('2024-10-01'),('2024-10-02'),('2024-10-03'),('2024-10-04'),('2024-10-07'),('2024-10-08'),('2024-10-09'),('2024-10-10'),('2024-10-11'),('2024-10-14'),('2024-10-15'),('2024-10-16'),('2024-10-17'),('2024-10-18'),('2024-10-21'),('2024-10-22'),('2024-10-23'),('2024-10-24'),('2024-10-25'),('2024-10-28'),('2024-10-29'),('2024-10-30'),('2024-10-31'),('2024-11-01'),('2024-11-04'),('2024-11-05'),('2024-11-06'),('2024-11-07'),('2024-11-08'),('2024-11-11'),('2024-11-12'),('2024-11-13'),('2024-11-14'),('2024-11-15'),('2024-11-18'),('2024-11-19'),('2024-11-20'),('2024-11-21'),('2024-11-22'),('2024-11-25'),('2024-11-26'),('2024-11-27'),('2024-11-28'),('2024-11-29'),('2024-12-02'),('2024-12-03'),('2024-12-04'),('2024-12-05'),('2024-12-06'),('2024-12-09'),('2024-12-10'),('2024-12-11'),('2024-12-12'),('2024-12-13'),('2024-12-16'),('2024-12-17'),('2024-12-18'),('2024-12-19'),('2024-12-20'),('2024-12-23'),('2024-12-24'),('2024-12-26'),('2024-12-27'),('2024-12-30'),('2024-12-31'),('2025-01-02'),('2025-01-03'),('2025-01-06'),('2025-01-07'),('2025-01-08'),('2025-01-09'),('2025-01-10'),('2025-01-13'),('2025-01-14'),('2025-01-15'),('2025-01-16'),('2025-01-17'),('2025-01-20'),('2025-01-21'),('2025-01-22'),('2025-01-23'),('2025-01-24'),('2025-01-27'),('2025-01-28'),('2025-01-29'),('2025-01-30'),('2025-01-31'),('2025-02-03'),('2025-02-04'),('2025-02-05'),('2025-02-06'),('2025-02-07'),('2025-02-10'),('2025-02-11'),('2025-02-12'),('2025-02-13'),('2025-02-14'),('2025-02-17'),('2025-02-18'),('2025-02-19'),('2025-02-20'),('2025-02-21'),('2025-02-24'),('2025-02-25'),('2025-02-26'),('2025-02-27'),('2025-02-28'),('2025-03-03'),('2025-03-04'),('2025-03-05'),('2025-03-06'),('2025-03-07'),('2025-03-10'),('2025-03-11'),('2025-03-12'),('2025-03-13'),('2025-03-14'),('2025-03-17'),('2025-03-18'),('2025-03-19'),('2025-03-20'),('2025-03-21'),('2025-03-24'),('2025-03-25'),('2025-03-26'),('2025-03-27'),('2025-03-28'),('2025-03-31'),('2025-04-01'),('2025-04-02'),('2025-04-03'),('2025-04-04'),('2025-04-07'),('2025-04-08'),('2025-04-09'),('2025-04-10'),('2025-04-11'),('2025-04-14'),('2025-04-15'),('2025-04-16'),('2025-04-17'),('2025-04-18'),('2025-04-21'),('2025-04-22'),('2025-04-23'),('2025-04-24'),('2025-04-25'),('2025-04-28'),('2025-04-29'),('2025-04-30'),('2025-05-01'),('2025-05-02'),('2025-05-05'),('2025-05-06'),('2025-05-07'),('2025-05-08'),('2025-05-09'),('2025-05-12'),('2025-05-13'),('2025-05-14'),('2025-05-15'),('2025-05-16'),('2025-05-19'),('2025-05-20'),('2025-05-21'),('2025-05-22'),('2025-05-23'),('2025-05-26'),('2025-05-27'),('2025-05-28'),('2025-05-29'),('2025-05-30'),('2025-06-02'),('2025-06-03'),('2025-06-04'),('2025-06-05'),('2025-06-06'),('2025-06-09'),('2025-06-10'),('2025-06-11'),('2025-06-12'),('2025-06-13'),('2025-06-16'),('2025-06-17'),('2025-06-18'),('2025-06-19'),('2025-06-20'),('2025-06-23'),('2025-06-24'),('2025-06-25'),('2025-06-26'),('2025-06-27'),('2025-06-30'),('2025-07-01'),('2025-07-02'),('2025-07-03'),('2025-07-04'),('2025-07-07'),('2025-07-08'),('2025-07-09'),('2025-07-10'),('2025-07-11'),('2025-07-14'),('2025-07-15'),('2025-07-16'),('2025-07-17'),('2025-07-18'),('2025-07-21'),('2025-07-22'),('2025-07-23'),('2025-07-24'),('2025-07-25'),('2025-07-28'),('2025-07-29'),('2025-07-30'),('2025-07-31'),('2025-08-01'),('2025-08-04'),('2025-08-05'),('2025-08-06'),('2025-08-07'),('2025-08-08'),('2025-08-11'),('2025-08-12'),('2025-08-13'),('2025-08-14'),('2025-08-15'),('2025-08-18'),('2025-08-19'),('2025-08-20'),('2025-08-21'),('2025-08-22'),('2025-08-25'),('2025-08-26'),('2025-08-27'),('2025-08-28'),('2025-08-29'),('2025-09-01'),('2025-09-02'),('2025-09-03'),('2025-09-04'),('2025-09-05'),('2025-09-08'),('2025-09-09'),('2025-09-10'),('2025-09-11'),('2025-09-12'),('2025-09-15'),('2025-09-16'),('2025-09-17'),('2025-09-18'),('2025-09-19'),('2025-09-22'),('2025-09-23'),('2025-09-24'),('2025-09-25'),('2025-09-26'),('2025-09-29'),('2025-09-30'),('2025-10-01'),('2025-10-02'),('2025-10-03'),('2025-10-06'),('2025-10-07'),('2025-10-08'),('2025-10-09'),('2025-10-10'),('2025-10-13'),('2025-10-14'),('2025-10-15'),('2025-10-16'),('2025-10-17'),('2025-10-20'),('2025-10-21'),('2025-10-22'),('2025-10-23'),('2025-10-24'),('2025-10-27'),('2025-10-28'),('2025-10-29'),('2025-10-30'),('2025-10-31'),('2025-11-03'),('2025-11-04'),('2025-11-05'),('2025-11-06'),('2025-11-07'),('2025-11-10'),('2025-11-11'),('2025-11-12'),('2025-11-13'),('2025-11-14'),('2025-11-17'),('2025-11-18'),('2025-11-19'),('2025-11-20'),('2025-11-21'),('2025-11-24'),('2025-11-25'),('2025-11-26'),('2025-11-27'),('2025-11-28'),('2025-12-01'),('2025-12-02'),('2025-12-03'),('2025-12-04'),('2025-12-05'),('2025-12-08'),('2025-12-09'),('2025-12-10'),('2025-12-11'),('2025-12-12'),('2025-12-15'),('2025-12-16'),('2025-12-17'),('2025-12-18'),('2025-12-19'),('2025-12-22'),('2025-12-23'),('2025-12-24'),('2025-12-26'),('2025-12-29'),('2025-12-30'),('2025-12-31');
/*!40000 ALTER TABLE `trading_days_plus` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `trading_days_minus`
--

LOCK TABLES `trading_days_minus` WRITE;
/*!40000 ALTER TABLE `trading_days_minus` DISABLE KEYS */;
INSERT INTO `trading_days_minus` VALUES (217,'2000-01-03'),(224,'2000-01-03'),(225,'2000-01-03'),(227,'2000-01-03'),(237,'2000-01-03'),(238,'2000-01-03'),(239,'2000-01-03'),(238,'2000-01-04'),(239,'2000-01-04'),(238,'2000-01-05'),(239,'2000-01-05'),(222,'2000-01-06'),(226,'2000-01-06'),(238,'2000-01-06'),(239,'2000-01-06'),(238,'2000-01-07'),(239,'2000-01-07'),(227,'2000-01-10'),(238,'2000-01-10'),(239,'2000-01-10'),(238,'2000-01-11'),(239,'2000-01-11'),(238,'2000-01-12'),(239,'2000-01-12'),(238,'2000-01-13'),(239,'2000-01-13'),(238,'2000-01-14'),(239,'2000-01-14'),(218,'2000-01-17'),(233,'2000-01-17'),(238,'2000-01-17'),(239,'2000-01-17'),(238,'2000-01-18'),(239,'2000-01-18'),(238,'2000-01-19'),(239,'2000-01-19'),(238,'2000-01-20'),(239,'2000-01-20'),(238,'2000-01-21'),(239,'2000-01-21'),(238,'2000-01-24'),(239,'2000-01-24'),(238,'2000-01-25'),(239,'2000-01-25'),(238,'2000-01-26'),(239,'2000-01-26'),(238,'2000-01-27'),(239,'2000-01-27'),(238,'2000-01-28'),(239,'2000-01-28'),(238,'2000-01-31'),(239,'2000-01-31'),(238,'2000-02-01'),(239,'2000-02-01'),(238,'2000-02-02'),(239,'2000-02-02'),(228,'2000-02-04'),(228,'2000-02-07'),(227,'2000-02-11'),(218,'2000-02-21'),(233,'2000-02-21'),(239,'2000-03-06'),(239,'2000-03-07'),(227,'2000-03-20'),(228,'2000-04-04'),(232,'2000-04-20'),(234,'2000-04-20'),(217,'2000-04-21'),(218,'2000-04-21'),(220,'2000-04-21'),(221,'2000-04-21'),(222,'2000-04-21'),(223,'2000-04-21'),(224,'2000-04-21'),(225,'2000-04-21'),(226,'2000-04-21'),(228,'2000-04-21'),(232,'2000-04-21'),(233,'2000-04-21'),(234,'2000-04-21'),(236,'2000-04-21'),(237,'2000-04-21'),(238,'2000-04-21'),(239,'2000-04-21'),(217,'2000-04-24'),(220,'2000-04-24'),(221,'2000-04-24'),(222,'2000-04-24'),(223,'2000-04-24'),(224,'2000-04-24'),(225,'2000-04-24'),(226,'2000-04-24'),(228,'2000-04-24'),(232,'2000-04-24'),(234,'2000-04-24'),(236,'2000-04-24'),(237,'2000-04-24'),(238,'2000-04-24'),(217,'2000-05-01'),(220,'2000-05-01'),(221,'2000-05-01'),(222,'2000-05-01'),(223,'2000-05-01'),(224,'2000-05-01'),(225,'2000-05-01'),(226,'2000-05-01'),(228,'2000-05-01'),(232,'2000-05-01'),(236,'2000-05-01'),(237,'2000-05-01'),(239,'2000-05-01'),(227,'2000-05-03'),(227,'2000-05-04'),(227,'2000-05-05'),(228,'2000-05-11'),(232,'2000-05-17'),(234,'2000-05-19'),(218,'2000-05-29'),(224,'2000-05-29'),(233,'2000-05-29'),(217,'2000-06-01'),(225,'2000-06-01'),(226,'2000-06-01'),(232,'2000-06-01'),(234,'2000-06-01'),(237,'2000-06-01'),(238,'2000-06-01'),(234,'2000-06-05'),(228,'2000-06-06'),(217,'2000-06-12'),(221,'2000-06-12'),(225,'2000-06-12'),(226,'2000-06-12'),(232,'2000-06-12'),(234,'2000-06-12'),(237,'2000-06-12'),(238,'2000-06-12'),(226,'2000-06-22'),(239,'2000-06-22'),(218,'2000-07-04'),(233,'2000-07-04'),(221,'2000-07-14'),(227,'2000-07-20'),(217,'2000-08-01'),(225,'2000-08-01'),(237,'2000-08-01'),(223,'2000-08-15'),(226,'2000-08-15'),(224,'2000-08-28'),(218,'2000-09-04'),(233,'2000-09-04'),(239,'2000-09-07'),(228,'2000-09-13'),(227,'2000-09-15'),(228,'2000-10-02'),(220,'2000-10-03'),(236,'2000-10-03'),(228,'2000-10-06'),(227,'2000-10-09'),(222,'2000-10-12'),(239,'2000-10-12'),(226,'2000-10-26'),(222,'2000-11-01'),(226,'2000-11-01'),(239,'2000-11-02'),(227,'2000-11-03'),(239,'2000-11-15'),(218,'2000-11-23'),(227,'2000-11-23'),(233,'2000-11-23'),(222,'2000-12-06'),(222,'2000-12-08'),(226,'2000-12-08'),(217,'2000-12-26'),(220,'2000-12-26'),(221,'2000-12-26'),(222,'2000-12-26'),(223,'2000-12-26'),(224,'2000-12-26'),(225,'2000-12-26'),(226,'2000-12-26'),(228,'2000-12-26'),(232,'2000-12-26'),(234,'2000-12-26'),(236,'2000-12-26'),(237,'2000-12-26'),(238,'2000-12-26'),(226,'2000-12-29'),(239,'2000-12-29'),(217,'2001-01-02'),(225,'2001-01-02'),(227,'2001-01-02'),(237,'2001-01-02'),(227,'2001-01-03'),(227,'2001-01-08'),(218,'2001-01-15'),(233,'2001-01-15'),(228,'2001-01-24'),(228,'2001-01-25'),(239,'2001-01-25'),(228,'2001-01-26'),(227,'2001-02-12'),(218,'2001-02-19'),(233,'2001-02-19'),(239,'2001-02-26'),(239,'2001-02-27'),(227,'2001-03-20'),(228,'2001-04-05'),(232,'2001-04-12'),(234,'2001-04-12'),(217,'2001-04-13'),(218,'2001-04-13'),(220,'2001-04-13'),(221,'2001-04-13'),(222,'2001-04-13'),(223,'2001-04-13'),(224,'2001-04-13'),(225,'2001-04-13'),(226,'2001-04-13'),(228,'2001-04-13'),(232,'2001-04-13'),(233,'2001-04-13'),(234,'2001-04-13'),(236,'2001-04-13'),(237,'2001-04-13'),(238,'2001-04-13'),(239,'2001-04-13'),(217,'2001-04-16'),(220,'2001-04-16'),(221,'2001-04-16'),(222,'2001-04-16'),(223,'2001-04-16'),(224,'2001-04-16'),(225,'2001-04-16'),(226,'2001-04-16'),(228,'2001-04-16'),(232,'2001-04-16'),(234,'2001-04-16'),(236,'2001-04-16'),(237,'2001-04-16'),(238,'2001-04-16'),(227,'2001-04-30'),(228,'2001-04-30'),(238,'2001-04-30'),(217,'2001-05-01'),(220,'2001-05-01'),(221,'2001-05-01'),(222,'2001-05-01'),(223,'2001-05-01'),(225,'2001-05-01'),(226,'2001-05-01'),(228,'2001-05-01'),(232,'2001-05-01'),(234,'2001-05-01'),(236,'2001-05-01'),(237,'2001-05-01'),(239,'2001-05-01'),(227,'2001-05-03'),(227,'2001-05-04'),(224,'2001-05-07'),(234,'2001-05-11'),(232,'2001-05-17'),(217,'2001-05-24'),(225,'2001-05-24'),(226,'2001-05-24'),(232,'2001-05-24'),(234,'2001-05-24'),(237,'2001-05-24'),(218,'2001-05-28'),(224,'2001-05-28'),(233,'2001-05-28'),(217,'2001-06-04'),(221,'2001-06-04'),(225,'2001-06-04'),(226,'2001-06-04'),(232,'2001-06-04'),(234,'2001-06-04'),(237,'2001-06-04'),(238,'2001-06-04'),(234,'2001-06-05'),(226,'2001-06-14'),(239,'2001-06-14'),(228,'2001-06-25'),(228,'2001-07-02'),(218,'2001-07-04'),(233,'2001-07-04'),(228,'2001-07-06'),(239,'2001-07-09'),(227,'2001-07-20'),(228,'2001-07-25'),(222,'2001-08-15'),(223,'2001-08-15'),(226,'2001-08-15'),(224,'2001-08-27'),(218,'2001-09-03'),(233,'2001-09-03'),(239,'2001-09-07'),(218,'2001-09-11'),(220,'2001-09-11'),(222,'2001-09-11'),(233,'2001-09-11'),(238,'2001-09-11'),(239,'2001-09-11'),(218,'2001-09-12'),(233,'2001-09-12'),(218,'2001-09-13'),(233,'2001-09-13'),(218,'2001-09-14'),(233,'2001-09-14'),(227,'2001-09-24'),(228,'2001-10-01'),(228,'2001-10-02'),(227,'2001-10-08'),(222,'2001-10-12'),(239,'2001-10-12'),(226,'2001-10-26'),(226,'2001-11-01'),(239,'2001-11-02'),(239,'2001-11-15'),(218,'2001-11-22'),(233,'2001-11-22'),(227,'2001-11-23'),(222,'2001-12-06'),(217,'2001-12-24'),(220,'2001-12-24'),(222,'2001-12-24'),(223,'2001-12-24'),(225,'2001-12-24'),(226,'2001-12-24'),(227,'2001-12-24'),(232,'2001-12-24'),(234,'2001-12-24'),(237,'2001-12-24'),(239,'2001-12-24'),(217,'2001-12-26'),(220,'2001-12-26'),(221,'2001-12-26'),(222,'2001-12-26'),(223,'2001-12-26'),(224,'2001-12-26'),(225,'2001-12-26'),(226,'2001-12-26'),(228,'2001-12-26'),(232,'2001-12-26'),(234,'2001-12-26'),(236,'2001-12-26'),(237,'2001-12-26'),(238,'2001-12-26'),(217,'2001-12-31'),(220,'2001-12-31'),(221,'2001-12-31'),(222,'2001-12-31'),(223,'2001-12-31'),(225,'2001-12-31'),(226,'2001-12-31'),(227,'2001-12-31'),(232,'2001-12-31'),(234,'2001-12-31'),(237,'2001-12-31'),(238,'2001-12-31'),(239,'2001-12-31'),(227,'2002-01-02'),(227,'2002-01-03'),(227,'2002-01-14'),(218,'2002-01-21'),(233,'2002-01-21'),(239,'2002-01-25'),(227,'2002-02-11'),(239,'2002-02-11'),(228,'2002-02-12'),(239,'2002-02-12'),(228,'2002-02-13'),(228,'2002-02-14'),(218,'2002-02-18'),(233,'2002-02-18'),(227,'2002-03-21'),(232,'2002-03-28'),(234,'2002-03-28'),(217,'2002-03-29'),(218,'2002-03-29'),(220,'2002-03-29'),(221,'2002-03-29'),(222,'2002-03-29'),(223,'2002-03-29'),(224,'2002-03-29'),(225,'2002-03-29'),(226,'2002-03-29'),(228,'2002-03-29'),(232,'2002-03-29'),(233,'2002-03-29'),(234,'2002-03-29'),(236,'2002-03-29'),(237,'2002-03-29'),(238,'2002-03-29'),(239,'2002-03-29'),(217,'2002-04-01'),(220,'2002-04-01'),(221,'2002-04-01'),(222,'2002-04-01'),(223,'2002-04-01'),(224,'2002-04-01'),(225,'2002-04-01'),(226,'2002-04-01'),(228,'2002-04-01'),(232,'2002-04-01'),(234,'2002-04-01'),(236,'2002-04-01'),(237,'2002-04-01'),(238,'2002-04-01'),(228,'2002-04-05'),(234,'2002-04-26'),(227,'2002-04-29'),(217,'2002-05-01'),(220,'2002-05-01'),(221,'2002-05-01'),(222,'2002-05-01'),(223,'2002-05-01'),(225,'2002-05-01'),(226,'2002-05-01'),(228,'2002-05-01'),(232,'2002-05-01'),(236,'2002-05-01'),(237,'2002-05-01'),(238,'2002-05-01'),(239,'2002-05-01'),(227,'2002-05-03'),(224,'2002-05-06'),(227,'2002-05-06'),(226,'2002-05-09'),(232,'2002-05-09'),(234,'2002-05-09'),(232,'2002-05-17'),(226,'2002-05-20'),(228,'2002-05-20'),(232,'2002-05-20'),(234,'2002-05-20'),(218,'2002-05-27'),(233,'2002-05-27'),(226,'2002-05-30'),(239,'2002-05-30'),(224,'2002-06-03'),(224,'2002-06-04'),(234,'2002-06-05'),(228,'2002-07-01'),(218,'2002-07-04'),(233,'2002-07-04'),(239,'2002-07-09'),(222,'2002-08-15'),(223,'2002-08-15'),(226,'2002-08-15'),(224,'2002-08-26'),(218,'2002-09-02'),(233,'2002-09-02'),(227,'2002-09-16'),(227,'2002-09-23'),(228,'2002-10-01'),(227,'2002-10-14'),(228,'2002-10-14'),(222,'2002-11-01'),(226,'2002-11-01'),(227,'2002-11-04'),(239,'2002-11-15'),(218,'2002-11-28'),(233,'2002-11-28'),(222,'2002-12-06'),(227,'2002-12-23'),(217,'2002-12-24'),(220,'2002-12-24'),(222,'2002-12-24'),(223,'2002-12-24'),(225,'2002-12-24'),(226,'2002-12-24'),(232,'2002-12-24'),(234,'2002-12-24'),(236,'2002-12-24'),(237,'2002-12-24'),(239,'2002-12-24'),(217,'2002-12-26'),(220,'2002-12-26'),(221,'2002-12-26'),(222,'2002-12-26'),(223,'2002-12-26'),(224,'2002-12-26'),(225,'2002-12-26'),(226,'2002-12-26'),(228,'2002-12-26'),(232,'2002-12-26'),(234,'2002-12-26'),(236,'2002-12-26'),(237,'2002-12-26'),(238,'2002-12-26'),(217,'2002-12-31'),(220,'2002-12-31'),(222,'2002-12-31'),(223,'2002-12-31'),(225,'2002-12-31'),(226,'2002-12-31'),(227,'2002-12-31'),(232,'2002-12-31'),(234,'2002-12-31'),(237,'2002-12-31'),(239,'2002-12-31'),(217,'2003-01-02'),(225,'2003-01-02'),(227,'2003-01-02'),(237,'2003-01-02'),(227,'2003-01-03'),(222,'2003-01-06'),(226,'2003-01-06'),(227,'2003-01-13'),(218,'2003-01-20'),(233,'2003-01-20'),(228,'2003-01-31'),(228,'2003-02-03'),(227,'2003-02-11'),(218,'2003-02-17'),(233,'2003-02-17'),(239,'2003-03-03'),(239,'2003-03-04'),(227,'2003-03-21'),(232,'2003-04-17'),(234,'2003-04-17'),(217,'2003-04-18'),(218,'2003-04-18'),(220,'2003-04-18'),(221,'2003-04-18'),(222,'2003-04-18'),(223,'2003-04-18'),(224,'2003-04-18'),(225,'2003-04-18'),(226,'2003-04-18'),(228,'2003-04-18'),(232,'2003-04-18'),(233,'2003-04-18'),(234,'2003-04-18'),(237,'2003-04-18'),(238,'2003-04-18'),(239,'2003-04-18'),(217,'2003-04-21'),(220,'2003-04-21'),(221,'2003-04-21'),(222,'2003-04-21'),(223,'2003-04-21'),(224,'2003-04-21'),(225,'2003-04-21'),(226,'2003-04-21'),(228,'2003-04-21'),(232,'2003-04-21'),(234,'2003-04-21'),(237,'2003-04-21'),(238,'2003-04-21'),(239,'2003-04-21'),(227,'2003-04-29'),(217,'2003-05-01'),(220,'2003-05-01'),(221,'2003-05-01'),(222,'2003-05-01'),(223,'2003-05-01'),(225,'2003-05-01'),(226,'2003-05-01'),(228,'2003-05-01'),(232,'2003-05-01'),(237,'2003-05-01'),(238,'2003-05-01'),(239,'2003-05-01'),(224,'2003-05-05'),(227,'2003-05-05'),(228,'2003-05-08'),(234,'2003-05-16'),(218,'2003-05-26'),(224,'2003-05-26'),(233,'2003-05-26'),(217,'2003-05-29'),(225,'2003-05-29'),(226,'2003-05-29'),(232,'2003-05-29'),(234,'2003-05-29'),(237,'2003-05-29'),(228,'2003-06-04'),(234,'2003-06-05'),(217,'2003-06-09'),(225,'2003-06-09'),(226,'2003-06-09'),(232,'2003-06-09'),(234,'2003-06-09'),(237,'2003-06-09'),(226,'2003-06-19'),(239,'2003-06-19'),(228,'2003-07-01'),(218,'2003-07-04'),(233,'2003-07-04'),(239,'2003-07-09'),(227,'2003-07-21'),(222,'2003-08-15'),(223,'2003-08-15'),(226,'2003-08-15'),(224,'2003-08-25'),(218,'2003-09-01'),(233,'2003-09-01'),(228,'2003-09-12'),(227,'2003-09-15'),(227,'2003-09-23'),(228,'2003-10-01'),(227,'2003-10-13'),(227,'2003-11-03'),(227,'2003-11-24'),(218,'2003-11-27'),(233,'2003-11-27'),(222,'2003-12-08'),(226,'2003-12-08'),(227,'2003-12-23'),(217,'2003-12-24'),(220,'2003-12-24'),(222,'2003-12-24'),(223,'2003-12-24'),(225,'2003-12-24'),(226,'2003-12-24'),(232,'2003-12-24'),(234,'2003-12-24'),(237,'2003-12-24'),(239,'2003-12-24'),(217,'2003-12-26'),(220,'2003-12-26'),(221,'2003-12-26'),(222,'2003-12-26'),(223,'2003-12-26'),(224,'2003-12-26'),(225,'2003-12-26'),(226,'2003-12-26'),(228,'2003-12-26'),(232,'2003-12-26'),(234,'2003-12-26'),(237,'2003-12-26'),(238,'2003-12-26'),(217,'2003-12-31'),(220,'2003-12-31'),(222,'2003-12-31'),(223,'2003-12-31'),(225,'2003-12-31'),(226,'2003-12-31'),(227,'2003-12-31'),(232,'2003-12-31'),(234,'2003-12-31'),(237,'2003-12-31'),(239,'2003-12-31'),(217,'2004-01-02'),(225,'2004-01-02'),(227,'2004-01-02'),(237,'2004-01-02'),(222,'2004-01-06'),(226,'2004-01-06'),(227,'2004-01-12'),(218,'2004-01-19'),(233,'2004-01-19'),(228,'2004-01-22'),(228,'2004-01-23'),(227,'2004-02-11'),(218,'2004-02-16'),(233,'2004-02-16'),(239,'2004-02-23'),(239,'2004-02-24'),(228,'2004-04-05'),(232,'2004-04-08'),(234,'2004-04-08'),(217,'2004-04-09'),(218,'2004-04-09'),(220,'2004-04-09'),(221,'2004-04-09'),(222,'2004-04-09'),(223,'2004-04-09'),(224,'2004-04-09'),(225,'2004-04-09'),(226,'2004-04-09'),(228,'2004-04-09'),(232,'2004-04-09'),(233,'2004-04-09'),(234,'2004-04-09'),(237,'2004-04-09'),(238,'2004-04-09'),(239,'2004-04-09'),(217,'2004-04-12'),(220,'2004-04-12'),(221,'2004-04-12'),(222,'2004-04-12'),(223,'2004-04-12'),(224,'2004-04-12'),(225,'2004-04-12'),(226,'2004-04-12'),(228,'2004-04-12'),(232,'2004-04-12'),(234,'2004-04-12'),(237,'2004-04-12'),(238,'2004-04-12'),(239,'2004-04-21'),(227,'2004-04-29'),(224,'2004-05-03'),(227,'2004-05-03'),(227,'2004-05-04'),(227,'2004-05-05'),(234,'2004-05-07'),(232,'2004-05-17'),(217,'2004-05-20'),(225,'2004-05-20'),(226,'2004-05-20'),(232,'2004-05-20'),(234,'2004-05-20'),(237,'2004-05-20'),(228,'2004-05-26'),(217,'2004-05-31'),(218,'2004-05-31'),(224,'2004-05-31'),(225,'2004-05-31'),(226,'2004-05-31'),(232,'2004-05-31'),(233,'2004-05-31'),(234,'2004-05-31'),(237,'2004-05-31'),(226,'2004-06-10'),(239,'2004-06-10'),(218,'2004-06-11'),(233,'2004-06-11'),(228,'2004-06-22'),(228,'2004-07-01'),(218,'2004-07-05'),(233,'2004-07-05'),(239,'2004-07-09'),(227,'2004-07-19'),(222,'2004-08-16'),(224,'2004-08-30'),(218,'2004-09-06'),(233,'2004-09-06'),(239,'2004-09-07'),(227,'2004-09-20'),(227,'2004-09-23'),(228,'2004-09-29'),(228,'2004-10-01'),(227,'2004-10-11'),(222,'2004-10-12'),(239,'2004-10-12'),(228,'2004-10-22'),(226,'2004-10-26'),(222,'2004-11-01'),(226,'2004-11-01'),(239,'2004-11-02'),(227,'2004-11-03'),(239,'2004-11-15'),(227,'2004-11-23'),(218,'2004-11-25'),(233,'2004-11-25'),(222,'2004-12-06'),(222,'2004-12-08'),(226,'2004-12-08'),(227,'2004-12-23'),(217,'2004-12-24'),(218,'2004-12-24'),(220,'2004-12-24'),(222,'2004-12-24'),(223,'2004-12-24'),(225,'2004-12-24'),(226,'2004-12-24'),(232,'2004-12-24'),(233,'2004-12-24'),(234,'2004-12-24'),(237,'2004-12-24'),(239,'2004-12-24'),(224,'2004-12-27'),(228,'2004-12-27'),(224,'2004-12-28'),(217,'2004-12-31'),(220,'2004-12-31'),(222,'2004-12-31'),(223,'2004-12-31'),(225,'2004-12-31'),(226,'2004-12-31'),(227,'2004-12-31'),(232,'2004-12-31'),(234,'2004-12-31'),(237,'2004-12-31'),(239,'2004-12-31'),(224,'2005-01-03'),(227,'2005-01-03'),(222,'2005-01-06'),(226,'2005-01-06'),(227,'2005-01-10'),(218,'2005-01-17'),(233,'2005-01-17'),(239,'2005-01-25'),(239,'2005-02-07'),(239,'2005-02-08'),(228,'2005-02-09'),(228,'2005-02-10'),(227,'2005-02-11'),(228,'2005-02-11'),(218,'2005-02-21'),(233,'2005-02-21'),(227,'2005-03-21'),(232,'2005-03-24'),(234,'2005-03-24'),(217,'2005-03-25'),(218,'2005-03-25'),(220,'2005-03-25'),(221,'2005-03-25'),(222,'2005-03-25'),(223,'2005-03-25'),(224,'2005-03-25'),(225,'2005-03-25'),(226,'2005-03-25'),(228,'2005-03-25'),(232,'2005-03-25'),(233,'2005-03-25'),(234,'2005-03-25'),(237,'2005-03-25'),(238,'2005-03-25'),(239,'2005-03-25'),(217,'2005-03-28'),(220,'2005-03-28'),(221,'2005-03-28'),(222,'2005-03-28'),(223,'2005-03-28'),(224,'2005-03-28'),(225,'2005-03-28'),(226,'2005-03-28'),(228,'2005-03-28'),(232,'2005-03-28'),(234,'2005-03-28'),(237,'2005-03-28'),(238,'2005-03-28'),(228,'2005-04-05'),(239,'2005-04-21'),(234,'2005-04-22'),(227,'2005-04-29'),(224,'2005-05-02'),(228,'2005-05-02'),(227,'2005-05-03'),(227,'2005-05-04'),(217,'2005-05-05'),(225,'2005-05-05'),(226,'2005-05-05'),(227,'2005-05-05'),(232,'2005-05-05'),(234,'2005-05-05'),(237,'2005-05-05'),(217,'2005-05-16'),(225,'2005-05-16'),(226,'2005-05-16'),(228,'2005-05-16'),(232,'2005-05-16'),(234,'2005-05-16'),(237,'2005-05-16'),(232,'2005-05-17'),(226,'2005-05-26'),(239,'2005-05-26'),(218,'2005-05-30'),(224,'2005-05-30'),(233,'2005-05-30'),(228,'2005-07-01'),(218,'2005-07-04'),(233,'2005-07-04'),(227,'2005-07-18'),(223,'2005-08-15'),(226,'2005-08-15'),(224,'2005-08-29'),(218,'2005-09-05'),(233,'2005-09-05'),(239,'2005-09-07'),(227,'2005-09-19'),(228,'2005-09-19'),(227,'2005-09-23'),(227,'2005-10-10'),(228,'2005-10-11'),(239,'2005-10-12'),(226,'2005-10-26'),(226,'2005-11-01'),(239,'2005-11-02'),(227,'2005-11-03'),(239,'2005-11-15'),(227,'2005-11-23'),(218,'2005-11-24'),(233,'2005-11-24'),(226,'2005-12-08'),(227,'2005-12-23'),(217,'2005-12-26'),(218,'2005-12-26'),(220,'2005-12-26'),(221,'2005-12-26'),(222,'2005-12-26'),(223,'2005-12-26'),(224,'2005-12-26'),(225,'2005-12-26'),(226,'2005-12-26'),(228,'2005-12-26'),(232,'2005-12-26'),(233,'2005-12-26'),(234,'2005-12-26'),(237,'2005-12-26'),(238,'2005-12-26'),(224,'2005-12-27'),(228,'2005-12-27'),(226,'2005-12-30'),(239,'2005-12-30'),(217,'2006-01-02'),(218,'2006-01-02'),(224,'2006-01-02'),(225,'2006-01-02'),(227,'2006-01-02'),(228,'2006-01-02'),(233,'2006-01-02'),(237,'2006-01-02'),(227,'2006-01-03'),(226,'2006-01-06'),(227,'2006-01-09'),(218,'2006-01-16'),(233,'2006-01-16'),(239,'2006-01-25'),(228,'2006-01-30'),(228,'2006-01-31'),(218,'2006-02-20'),(233,'2006-02-20'),(239,'2006-02-27'),(239,'2006-02-28'),(227,'2006-03-21'),(228,'2006-04-05'),(232,'2006-04-13'),(234,'2006-04-13'),(217,'2006-04-14'),(218,'2006-04-14'),(220,'2006-04-14'),(221,'2006-04-14'),(222,'2006-04-14'),(223,'2006-04-14'),(224,'2006-04-14'),(225,'2006-04-14'),(226,'2006-04-14'),(228,'2006-04-14'),(232,'2006-04-14'),(233,'2006-04-14'),(234,'2006-04-14'),(236,'2006-04-14'),(237,'2006-04-14'),(238,'2006-04-14'),(239,'2006-04-14'),(217,'2006-04-17'),(220,'2006-04-17'),(221,'2006-04-17'),(222,'2006-04-17'),(223,'2006-04-17'),(224,'2006-04-17'),(225,'2006-04-17'),(226,'2006-04-17'),(228,'2006-04-17'),(232,'2006-04-17'),(234,'2006-04-17'),(236,'2006-04-17'),(237,'2006-04-17'),(238,'2006-04-17'),(239,'2006-04-21'),(217,'2006-05-01'),(220,'2006-05-01'),(221,'2006-05-01'),(222,'2006-05-01'),(223,'2006-05-01'),(224,'2006-05-01'),(225,'2006-05-01'),(226,'2006-05-01'),(228,'2006-05-01'),(232,'2006-05-01'),(236,'2006-05-01'),(237,'2006-05-01'),(238,'2006-05-01'),(239,'2006-05-01'),(227,'2006-05-03'),(227,'2006-05-04'),(227,'2006-05-05'),(228,'2006-05-05'),(234,'2006-05-12'),(232,'2006-05-17'),(217,'2006-05-25'),(225,'2006-05-25'),(226,'2006-05-25'),(232,'2006-05-25'),(234,'2006-05-25'),(237,'2006-05-25'),(218,'2006-05-29'),(224,'2006-05-29'),(233,'2006-05-29'),(228,'2006-05-31'),(217,'2006-06-05'),(225,'2006-06-05'),(226,'2006-06-05'),(232,'2006-06-05'),(234,'2006-06-05'),(237,'2006-06-05'),(226,'2006-06-15'),(239,'2006-06-15'),(218,'2006-07-04'),(233,'2006-07-04'),(227,'2006-07-17'),(217,'2006-08-01'),(225,'2006-08-01'),(237,'2006-08-01'),(223,'2006-08-15'),(226,'2006-08-15'),(224,'2006-08-28'),(218,'2006-09-04'),(233,'2006-09-04'),(239,'2006-09-07'),(227,'2006-09-18'),(228,'2006-10-02'),(227,'2006-10-09'),(239,'2006-10-12'),(231,'2006-10-16'),(231,'2006-10-18'),(226,'2006-10-26'),(228,'2006-10-30'),(226,'2006-11-01'),(239,'2006-11-02'),(227,'2006-11-03'),(239,'2006-11-15'),(239,'2006-11-20'),(218,'2006-11-23'),(227,'2006-11-23'),(233,'2006-11-23'),(226,'2006-12-08'),(217,'2006-12-26'),(220,'2006-12-26'),(221,'2006-12-26'),(222,'2006-12-26'),(223,'2006-12-26'),(224,'2006-12-26'),(225,'2006-12-26'),(226,'2006-12-26'),(228,'2006-12-26'),(231,'2006-12-26'),(232,'2006-12-26'),(234,'2006-12-26'),(236,'2006-12-26'),(237,'2006-12-26'),(238,'2006-12-26'),(226,'2006-12-29'),(239,'2006-12-29'),(217,'2007-01-02'),(218,'2007-01-02'),(225,'2007-01-02'),(227,'2007-01-02'),(233,'2007-01-02'),(237,'2007-01-02'),(227,'2007-01-03'),(227,'2007-01-08'),(218,'2007-01-15'),(233,'2007-01-15'),(239,'2007-01-25'),(227,'2007-02-12'),(218,'2007-02-19'),(228,'2007-02-19'),(233,'2007-02-19'),(239,'2007-02-19'),(228,'2007-02-20'),(239,'2007-02-20'),(227,'2007-03-21'),(228,'2007-04-05'),(232,'2007-04-05'),(234,'2007-04-05'),(217,'2007-04-06'),(218,'2007-04-06'),(220,'2007-04-06'),(221,'2007-04-06'),(222,'2007-04-06'),(223,'2007-04-06'),(224,'2007-04-06'),(225,'2007-04-06'),(226,'2007-04-06'),(228,'2007-04-06'),(231,'2007-04-06'),(232,'2007-04-06'),(233,'2007-04-06'),(234,'2007-04-06'),(236,'2007-04-06'),(237,'2007-04-06'),(238,'2007-04-06'),(239,'2007-04-06'),(217,'2007-04-09'),(220,'2007-04-09'),(221,'2007-04-09'),(222,'2007-04-09'),(223,'2007-04-09'),(224,'2007-04-09'),(225,'2007-04-09'),(226,'2007-04-09'),(228,'2007-04-09'),(231,'2007-04-09'),(232,'2007-04-09'),(234,'2007-04-09'),(236,'2007-04-09'),(237,'2007-04-09'),(238,'2007-04-09'),(227,'2007-04-30'),(217,'2007-05-01'),(220,'2007-05-01'),(221,'2007-05-01'),(222,'2007-05-01'),(223,'2007-05-01'),(225,'2007-05-01'),(226,'2007-05-01'),(228,'2007-05-01'),(231,'2007-05-01'),(232,'2007-05-01'),(236,'2007-05-01'),(237,'2007-05-01'),(238,'2007-05-01'),(239,'2007-05-01'),(227,'2007-05-03'),(227,'2007-05-04'),(234,'2007-05-04'),(224,'2007-05-07'),(217,'2007-05-17'),(225,'2007-05-17'),(226,'2007-05-17'),(231,'2007-05-17'),(232,'2007-05-17'),(234,'2007-05-17'),(237,'2007-05-17'),(228,'2007-05-24'),(217,'2007-05-28'),(218,'2007-05-28'),(220,'2007-05-28'),(224,'2007-05-28'),(225,'2007-05-28'),(226,'2007-05-28'),(232,'2007-05-28'),(233,'2007-05-28'),(234,'2007-05-28'),(236,'2007-05-28'),(237,'2007-05-28'),(234,'2007-06-05'),(231,'2007-06-06'),(226,'2007-06-07'),(239,'2007-06-07'),(228,'2007-06-19'),(231,'2007-06-22'),(228,'2007-07-02'),(218,'2007-07-04'),(233,'2007-07-04'),(239,'2007-07-09'),(227,'2007-07-16'),(223,'2007-08-15'),(226,'2007-08-15'),(224,'2007-08-27'),(218,'2007-09-03'),(233,'2007-09-03'),(239,'2007-09-07'),(227,'2007-09-17'),(227,'2007-09-24'),(228,'2007-09-26'),(228,'2007-10-01'),(227,'2007-10-08'),(239,'2007-10-12'),(228,'2007-10-19'),(226,'2007-10-26'),(226,'2007-11-01'),(239,'2007-11-02'),(239,'2007-11-15'),(239,'2007-11-20'),(218,'2007-11-22'),(233,'2007-11-22'),(227,'2007-11-23'),(217,'2007-12-24'),(220,'2007-12-24'),(222,'2007-12-24'),(223,'2007-12-24'),(225,'2007-12-24'),(226,'2007-12-24'),(227,'2007-12-24'),(231,'2007-12-24'),(232,'2007-12-24'),(234,'2007-12-24'),(236,'2007-12-24'),(237,'2007-12-24'),(239,'2007-12-24'),(217,'2007-12-26'),(220,'2007-12-26'),(221,'2007-12-26'),(222,'2007-12-26'),(223,'2007-12-26'),(224,'2007-12-26'),(225,'2007-12-26'),(226,'2007-12-26'),(228,'2007-12-26'),(231,'2007-12-26'),(232,'2007-12-26'),(234,'2007-12-26'),(236,'2007-12-26'),(237,'2007-12-26'),(238,'2007-12-26'),(217,'2007-12-31'),(220,'2007-12-31'),(222,'2007-12-31'),(223,'2007-12-31'),(225,'2007-12-31'),(226,'2007-12-31'),(227,'2007-12-31'),(231,'2007-12-31'),(232,'2007-12-31'),(234,'2007-12-31'),(236,'2007-12-31'),(237,'2007-12-31'),(239,'2007-12-31'),(227,'2008-01-02'),(227,'2008-01-03'),(227,'2008-01-14'),(218,'2008-01-21'),(233,'2008-01-21'),(239,'2008-01-25'),(239,'2008-02-04'),(239,'2008-02-05'),(228,'2008-02-07'),(228,'2008-02-08'),(227,'2008-02-11'),(218,'2008-02-18'),(233,'2008-02-18'),(227,'2008-03-20'),(232,'2008-03-20'),(234,'2008-03-20'),(217,'2008-03-21'),(218,'2008-03-21'),(220,'2008-03-21'),(221,'2008-03-21'),(222,'2008-03-21'),(223,'2008-03-21'),(224,'2008-03-21'),(225,'2008-03-21'),(226,'2008-03-21'),(228,'2008-03-21'),(231,'2008-03-21'),(232,'2008-03-21'),(233,'2008-03-21'),(234,'2008-03-21'),(236,'2008-03-21'),(237,'2008-03-21'),(238,'2008-03-21'),(239,'2008-03-21'),(217,'2008-03-24'),(220,'2008-03-24'),(221,'2008-03-24'),(222,'2008-03-24'),(223,'2008-03-24'),(224,'2008-03-24'),(225,'2008-03-24'),(226,'2008-03-24'),(228,'2008-03-24'),(231,'2008-03-24'),(232,'2008-03-24'),(234,'2008-03-24'),(236,'2008-03-24'),(237,'2008-03-24'),(238,'2008-03-24'),(228,'2008-04-04'),(234,'2008-04-18'),(239,'2008-04-21'),(227,'2008-04-29'),(217,'2008-05-01'),(220,'2008-05-01'),(221,'2008-05-01'),(222,'2008-05-01'),(223,'2008-05-01'),(225,'2008-05-01'),(226,'2008-05-01'),(228,'2008-05-01'),(231,'2008-05-01'),(232,'2008-05-01'),(234,'2008-05-01'),(236,'2008-05-01'),(237,'2008-05-01'),(238,'2008-05-01'),(239,'2008-05-01'),(224,'2008-05-05'),(227,'2008-05-05'),(227,'2008-05-06'),(217,'2008-05-12'),(225,'2008-05-12'),(226,'2008-05-12'),(228,'2008-05-12'),(232,'2008-05-12'),(234,'2008-05-12'),(237,'2008-05-12'),(226,'2008-05-22'),(239,'2008-05-22'),(218,'2008-05-26'),(224,'2008-05-26'),(233,'2008-05-26'),(234,'2008-06-05'),(231,'2008-06-06'),(228,'2008-06-09'),(231,'2008-06-20'),(228,'2008-07-01'),(218,'2008-07-04'),(233,'2008-07-04'),(239,'2008-07-09'),(227,'2008-07-21'),(217,'2008-08-01'),(225,'2008-08-01'),(237,'2008-08-01'),(228,'2008-08-06'),(223,'2008-08-15'),(226,'2008-08-15'),(224,'2008-08-25'),(218,'2008-09-01'),(233,'2008-09-01'),(227,'2008-09-15'),(228,'2008-09-15'),(227,'2008-09-23'),(228,'2008-10-01'),(228,'2008-10-07'),(227,'2008-10-13'),(227,'2008-11-03'),(239,'2008-11-20'),(227,'2008-11-24'),(218,'2008-11-27'),(233,'2008-11-27'),(226,'2008-12-08'),(227,'2008-12-23'),(217,'2008-12-24'),(220,'2008-12-24'),(222,'2008-12-24'),(223,'2008-12-24'),(225,'2008-12-24'),(226,'2008-12-24'),(231,'2008-12-24'),(232,'2008-12-24'),(234,'2008-12-24'),(236,'2008-12-24'),(237,'2008-12-24'),(239,'2008-12-24'),(217,'2008-12-26'),(220,'2008-12-26'),(221,'2008-12-26'),(222,'2008-12-26'),(223,'2008-12-26'),(224,'2008-12-26'),(225,'2008-12-26'),(226,'2008-12-26'),(228,'2008-12-26'),(231,'2008-12-26'),(232,'2008-12-26'),(234,'2008-12-26'),(236,'2008-12-26'),(237,'2008-12-26'),(238,'2008-12-26'),(217,'2008-12-31'),(220,'2008-12-31'),(222,'2008-12-31'),(223,'2008-12-31'),(225,'2008-12-31'),(226,'2008-12-31'),(227,'2008-12-31'),(231,'2008-12-31'),(232,'2008-12-31'),(234,'2008-12-31'),(236,'2008-12-31'),(237,'2008-12-31'),(239,'2008-12-31'),(217,'2009-01-02'),(225,'2009-01-02'),(227,'2009-01-02'),(237,'2009-01-02'),(226,'2009-01-06'),(231,'2009-01-06'),(227,'2009-01-12'),(218,'2009-01-19'),(233,'2009-01-19'),(228,'2009-01-26'),(228,'2009-01-27'),(228,'2009-01-28'),(227,'2009-02-11'),(218,'2009-02-16'),(220,'2009-02-16'),(233,'2009-02-16'),(239,'2009-02-23'),(239,'2009-02-24'),(227,'2009-03-20'),(232,'2009-04-09'),(234,'2009-04-09'),(217,'2009-04-10'),(218,'2009-04-10'),(220,'2009-04-10'),(221,'2009-04-10'),(222,'2009-04-10'),(223,'2009-04-10'),(224,'2009-04-10'),(225,'2009-04-10'),(226,'2009-04-10'),(228,'2009-04-10'),(231,'2009-04-10'),(232,'2009-04-10'),(233,'2009-04-10'),(234,'2009-04-10'),(236,'2009-04-10'),(237,'2009-04-10'),(238,'2009-04-10'),(239,'2009-04-10'),(217,'2009-04-13'),(220,'2009-04-13'),(221,'2009-04-13'),(222,'2009-04-13'),(223,'2009-04-13'),(224,'2009-04-13'),(225,'2009-04-13'),(226,'2009-04-13'),(228,'2009-04-13'),(231,'2009-04-13'),(232,'2009-04-13'),(234,'2009-04-13'),(236,'2009-04-13'),(237,'2009-04-13'),(238,'2009-04-13'),(239,'2009-04-21'),(227,'2009-04-29'),(217,'2009-05-01'),(220,'2009-05-01'),(221,'2009-05-01'),(222,'2009-05-01'),(223,'2009-05-01'),(225,'2009-05-01'),(226,'2009-05-01'),(228,'2009-05-01'),(231,'2009-05-01'),(232,'2009-05-01'),(236,'2009-05-01'),(237,'2009-05-01'),(238,'2009-05-01'),(239,'2009-05-01'),(224,'2009-05-04'),(227,'2009-05-04'),(227,'2009-05-05'),(227,'2009-05-06'),(234,'2009-05-08'),(217,'2009-05-21'),(225,'2009-05-21'),(226,'2009-05-21'),(231,'2009-05-21'),(232,'2009-05-21'),(234,'2009-05-21'),(237,'2009-05-21'),(234,'2009-05-22'),(218,'2009-05-25'),(224,'2009-05-25'),(233,'2009-05-25'),(228,'2009-05-28'),(217,'2009-06-01'),(225,'2009-06-01'),(226,'2009-06-01'),(232,'2009-06-01'),(234,'2009-06-01'),(237,'2009-06-01'),(234,'2009-06-05'),(226,'2009-06-11'),(239,'2009-06-11'),(231,'2009-06-19'),(228,'2009-07-01'),(218,'2009-07-03'),(233,'2009-07-03'),(239,'2009-07-09'),(227,'2009-07-20'),(224,'2009-08-31'),(227,'2009-09-01'),(218,'2009-09-07'),(233,'2009-09-07'),(239,'2009-09-07'),(227,'2009-09-21'),(227,'2009-09-22'),(227,'2009-09-23'),(228,'2009-10-01'),(227,'2009-10-12'),(239,'2009-10-12'),(226,'2009-10-26'),(228,'2009-10-26'),(239,'2009-11-02'),(227,'2009-11-03'),(239,'2009-11-20'),(227,'2009-11-23'),(218,'2009-11-26'),(233,'2009-11-26'),(226,'2009-12-08'),(227,'2009-12-23'),(217,'2009-12-24'),(220,'2009-12-24'),(222,'2009-12-24'),(223,'2009-12-24'),(225,'2009-12-24'),(226,'2009-12-24'),(231,'2009-12-24'),(232,'2009-12-24'),(234,'2009-12-24'),(236,'2009-12-24'),(237,'2009-12-24'),(239,'2009-12-24'),(224,'2009-12-28'),(217,'2009-12-31'),(220,'2009-12-31'),(222,'2009-12-31'),(223,'2009-12-31'),(225,'2009-12-31'),(226,'2009-12-31'),(227,'2009-12-31'),(231,'2009-12-31'),(232,'2009-12-31'),(234,'2009-12-31'),(236,'2009-12-31'),(237,'2009-12-31'),(239,'2009-12-31'),(226,'2010-01-06'),(231,'2010-01-06'),(227,'2010-01-11'),(218,'2010-01-18'),(233,'2010-01-18'),(239,'2010-01-25'),(227,'2010-02-11'),(218,'2010-02-15'),(228,'2010-02-15'),(233,'2010-02-15'),(239,'2010-02-15'),(228,'2010-02-16'),(239,'2010-02-16'),(227,'2010-03-22'),(232,'2010-04-01'),(234,'2010-04-01'),(217,'2010-04-02'),(218,'2010-04-02'),(220,'2010-04-02'),(221,'2010-04-02'),(222,'2010-04-02'),(223,'2010-04-02'),(224,'2010-04-02'),(225,'2010-04-02'),(226,'2010-04-02'),(228,'2010-04-02'),(231,'2010-04-02'),(232,'2010-04-02'),(233,'2010-04-02'),(234,'2010-04-02'),(236,'2010-04-02'),(237,'2010-04-02'),(238,'2010-04-02'),(239,'2010-04-02'),(217,'2010-04-05'),(220,'2010-04-05'),(221,'2010-04-05'),(222,'2010-04-05'),(223,'2010-04-05'),(224,'2010-04-05'),(225,'2010-04-05'),(226,'2010-04-05'),(228,'2010-04-05'),(231,'2010-04-05'),(232,'2010-04-05'),(234,'2010-04-05'),(236,'2010-04-05'),(237,'2010-04-05'),(238,'2010-04-05'),(228,'2010-04-06'),(239,'2010-04-21'),(227,'2010-04-29'),(234,'2010-04-30'),(224,'2010-05-03'),(227,'2010-05-03'),(227,'2010-05-04'),(227,'2010-05-05'),(217,'2010-05-13'),(225,'2010-05-13'),(226,'2010-05-13'),(231,'2010-05-13'),(232,'2010-05-13'),(234,'2010-05-13'),(237,'2010-05-13'),(234,'2010-05-14'),(232,'2010-05-17'),(228,'2010-05-21'),(217,'2010-05-24'),(225,'2010-05-24'),(226,'2010-05-24'),(232,'2010-05-24'),(234,'2010-05-24'),(237,'2010-05-24'),(218,'2010-05-31'),(224,'2010-05-31'),(233,'2010-05-31'),(226,'2010-06-03'),(239,'2010-06-03'),(228,'2010-06-16'),(231,'2010-06-25'),(228,'2010-07-01'),(218,'2010-07-05'),(233,'2010-07-05'),(239,'2010-07-09'),(227,'2010-07-19'),(227,'2010-07-20'),(224,'2010-08-30'),(218,'2010-09-06'),(233,'2010-09-06'),(239,'2010-09-07'),(227,'2010-09-15'),(227,'2010-09-20'),(227,'2010-09-23'),(228,'2010-09-23'),(228,'2010-10-01'),(227,'2010-10-11'),(239,'2010-10-12'),(226,'2010-10-26'),(226,'2010-11-01'),(239,'2010-11-02'),(227,'2010-11-03'),(239,'2010-11-15'),(227,'2010-11-23'),(218,'2010-11-25'),(233,'2010-11-25'),(226,'2010-12-08'),(227,'2010-12-23'),(217,'2010-12-24'),(218,'2010-12-24'),(220,'2010-12-24'),(221,'2010-12-24'),(222,'2010-12-24'),(223,'2010-12-24'),(225,'2010-12-24'),(226,'2010-12-24'),(231,'2010-12-24'),(232,'2010-12-24'),(233,'2010-12-24'),(234,'2010-12-24'),(236,'2010-12-24'),(237,'2010-12-24'),(239,'2010-12-24'),(224,'2010-12-27'),(228,'2010-12-27'),(224,'2010-12-28'),(217,'2010-12-31'),(220,'2010-12-31'),(221,'2010-12-31'),(222,'2010-12-31'),(223,'2010-12-31'),(225,'2010-12-31'),(226,'2010-12-31'),(227,'2010-12-31'),(231,'2010-12-31'),(232,'2010-12-31'),(234,'2010-12-31'),(236,'2010-12-31'),(237,'2010-12-31'),(238,'2010-12-31'),(239,'2010-12-31'),(224,'2011-01-03'),(227,'2011-01-03'),(226,'2011-01-06'),(231,'2011-01-06'),(227,'2011-01-10'),(218,'2011-01-17'),(233,'2011-01-17'),(239,'2011-01-25'),(228,'2011-02-03'),(228,'2011-02-04'),(227,'2011-02-11'),(218,'2011-02-21'),(233,'2011-02-21'),(239,'2011-03-07'),(239,'2011-03-08'),(227,'2011-03-21'),(228,'2011-04-05'),(232,'2011-04-21'),(234,'2011-04-21'),(239,'2011-04-21'),(217,'2011-04-22'),(218,'2011-04-22'),(220,'2011-04-22'),(221,'2011-04-22'),(222,'2011-04-22'),(223,'2011-04-22'),(224,'2011-04-22'),(225,'2011-04-22'),(226,'2011-04-22'),(228,'2011-04-22'),(231,'2011-04-22'),(232,'2011-04-22'),(233,'2011-04-22'),(234,'2011-04-22'),(236,'2011-04-22'),(237,'2011-04-22'),(238,'2011-04-22'),(239,'2011-04-22'),(217,'2011-04-25'),(220,'2011-04-25'),(221,'2011-04-25'),(222,'2011-04-25'),(223,'2011-04-25'),(224,'2011-04-25'),(225,'2011-04-25'),(226,'2011-04-25'),(228,'2011-04-25'),(231,'2011-04-25'),(232,'2011-04-25'),(234,'2011-04-25'),(236,'2011-04-25'),(237,'2011-04-25'),(238,'2011-04-25'),(224,'2011-04-29'),(227,'2011-04-29'),(224,'2011-05-02'),(228,'2011-05-02'),(227,'2011-05-03'),(227,'2011-05-04'),(227,'2011-05-05'),(228,'2011-05-10'),(232,'2011-05-17'),(234,'2011-05-20'),(218,'2011-05-30'),(224,'2011-05-30'),(233,'2011-05-30'),(217,'2011-06-02'),(225,'2011-06-02'),(226,'2011-06-02'),(231,'2011-06-02'),(232,'2011-06-02'),(234,'2011-06-02'),(237,'2011-06-02'),(234,'2011-06-03'),(228,'2011-06-06'),(231,'2011-06-06'),(217,'2011-06-13'),(225,'2011-06-13'),(226,'2011-06-13'),(232,'2011-06-13'),(234,'2011-06-13'),(237,'2011-06-13'),(226,'2011-06-23'),(239,'2011-06-23'),(231,'2011-06-24'),(228,'2011-07-01'),(218,'2011-07-04'),(233,'2011-07-04'),(227,'2011-07-18'),(217,'2011-08-01'),(225,'2011-08-01'),(237,'2011-08-01'),(223,'2011-08-15'),(226,'2011-08-15'),(224,'2011-08-29'),(218,'2011-09-05'),(233,'2011-09-05'),(239,'2011-09-07'),(228,'2011-09-13'),(227,'2011-09-19'),(227,'2011-09-23'),(228,'2011-09-29'),(220,'2011-10-03'),(236,'2011-10-03'),(228,'2011-10-05'),(227,'2011-10-10'),(239,'2011-10-12'),(226,'2011-10-26'),(226,'2011-11-01'),(239,'2011-11-02'),(227,'2011-11-03'),(239,'2011-11-15'),(227,'2011-11-23'),(218,'2011-11-24'),(233,'2011-11-24'),(226,'2011-12-08'),(227,'2011-12-23'),(217,'2011-12-26'),(218,'2011-12-26'),(220,'2011-12-26'),(221,'2011-12-26'),(222,'2011-12-26'),(223,'2011-12-26'),(224,'2011-12-26'),(225,'2011-12-26'),(226,'2011-12-26'),(228,'2011-12-26'),(231,'2011-12-26'),(232,'2011-12-26'),(233,'2011-12-26'),(234,'2011-12-26'),(236,'2011-12-26'),(237,'2011-12-26'),(238,'2011-12-26'),(224,'2011-12-27'),(228,'2011-12-27'),(226,'2011-12-30'),(239,'2011-12-30'),(217,'2012-01-02'),(218,'2012-01-02'),(220,'2012-01-02'),(224,'2012-01-02'),(225,'2012-01-02'),(227,'2012-01-02'),(228,'2012-01-02'),(233,'2012-01-02'),(237,'2012-01-02'),(227,'2012-01-03'),(226,'2012-01-06'),(231,'2012-01-06'),(227,'2012-01-09'),(218,'2012-01-16'),(233,'2012-01-16'),(228,'2012-01-23'),(228,'2012-01-24'),(228,'2012-01-25'),(239,'2012-01-25'),(218,'2012-02-20'),(233,'2012-02-20'),(239,'2012-02-20'),(239,'2012-02-21'),(239,'2012-02-22'),(228,'2012-03-19'),(227,'2012-03-20'),(228,'2012-04-04'),(232,'2012-04-05'),(234,'2012-04-05'),(217,'2012-04-06'),(218,'2012-04-06'),(220,'2012-04-06'),(221,'2012-04-06'),(222,'2012-04-06'),(223,'2012-04-06'),(224,'2012-04-06'),(225,'2012-04-06'),(226,'2012-04-06'),(228,'2012-04-06'),(231,'2012-04-06'),(232,'2012-04-06'),(233,'2012-04-06'),(234,'2012-04-06'),(236,'2012-04-06'),(237,'2012-04-06'),(238,'2012-04-06'),(239,'2012-04-06'),(217,'2012-04-09'),(220,'2012-04-09'),(221,'2012-04-09'),(222,'2012-04-09'),(223,'2012-04-09'),(224,'2012-04-09'),(225,'2012-04-09'),(226,'2012-04-09'),(228,'2012-04-09'),(231,'2012-04-09'),(232,'2012-04-09'),(234,'2012-04-09'),(236,'2012-04-09'),(237,'2012-04-09'),(238,'2012-04-09'),(227,'2012-04-30'),(217,'2012-05-01'),(220,'2012-05-01'),(221,'2012-05-01'),(222,'2012-05-01'),(223,'2012-05-01'),(225,'2012-05-01'),(226,'2012-05-01'),(228,'2012-05-01'),(231,'2012-05-01'),(232,'2012-05-01'),(236,'2012-05-01'),(237,'2012-05-01'),(238,'2012-05-01'),(239,'2012-05-01'),(227,'2012-05-03'),(227,'2012-05-04'),(234,'2012-05-04'),(224,'2012-05-07'),(217,'2012-05-17'),(225,'2012-05-17'),(226,'2012-05-17'),(231,'2012-05-17'),(232,'2012-05-17'),(234,'2012-05-17'),(237,'2012-05-17'),(234,'2012-05-18'),(217,'2012-05-28'),(218,'2012-05-28'),(224,'2012-05-28'),(225,'2012-05-28'),(226,'2012-05-28'),(233,'2012-05-28'),(234,'2012-05-28'),(237,'2012-05-28'),(224,'2012-06-04'),(224,'2012-06-05'),(234,'2012-06-05'),(231,'2012-06-06'),(226,'2012-06-07'),(239,'2012-06-07'),(231,'2012-06-22'),(228,'2012-07-02'),(218,'2012-07-04'),(233,'2012-07-04'),(239,'2012-07-09'),(227,'2012-07-16'),(217,'2012-08-01'),(225,'2012-08-01'),(237,'2012-08-01'),(223,'2012-08-15'),(226,'2012-08-15'),(224,'2012-08-27'),(218,'2012-09-03'),(233,'2012-09-03'),(239,'2012-09-07'),(227,'2012-09-17'),(228,'2012-10-01'),(228,'2012-10-02'),(220,'2012-10-03'),(236,'2012-10-03'),(227,'2012-10-08'),(239,'2012-10-12'),(228,'2012-10-23'),(226,'2012-10-26'),(218,'2012-10-29'),(233,'2012-10-29'),(218,'2012-10-30'),(233,'2012-10-30'),(226,'2012-11-01'),(239,'2012-11-02'),(239,'2012-11-15'),(239,'2012-11-20'),(218,'2012-11-22'),(233,'2012-11-22'),(227,'2012-11-23'),(217,'2012-12-24'),(220,'2012-12-24'),(221,'2012-12-24'),(223,'2012-12-24'),(225,'2012-12-24'),(226,'2012-12-24'),(227,'2012-12-24'),(231,'2012-12-24'),(232,'2012-12-24'),(234,'2012-12-24'),(236,'2012-12-24'),(237,'2012-12-24'),(239,'2012-12-24'),(217,'2012-12-26'),(220,'2012-12-26'),(221,'2012-12-26'),(222,'2012-12-26'),(223,'2012-12-26'),(224,'2012-12-26'),(225,'2012-12-26'),(226,'2012-12-26'),(228,'2012-12-26'),(231,'2012-12-26'),(232,'2012-12-26'),(234,'2012-12-26'),(236,'2012-12-26'),(237,'2012-12-26'),(238,'2012-12-26'),(217,'2012-12-31'),(220,'2012-12-31'),(221,'2012-12-31'),(223,'2012-12-31'),(225,'2012-12-31'),(226,'2012-12-31'),(227,'2012-12-31'),(231,'2012-12-31'),(232,'2012-12-31'),(234,'2012-12-31'),(236,'2012-12-31'),(237,'2012-12-31'),(239,'2012-12-31'),(217,'2013-01-02'),(225,'2013-01-02'),(227,'2013-01-02'),(237,'2013-01-02'),(227,'2013-01-03'),(227,'2013-01-14'),(218,'2013-01-21'),(233,'2013-01-21'),(239,'2013-01-25'),(227,'2013-02-11'),(228,'2013-02-11'),(239,'2013-02-11'),(228,'2013-02-12'),(239,'2013-02-12'),(228,'2013-02-13'),(218,'2013-02-18'),(233,'2013-02-18'),(227,'2013-03-20'),(232,'2013-03-28'),(234,'2013-03-28'),(217,'2013-03-29'),(218,'2013-03-29'),(220,'2013-03-29'),(221,'2013-03-29'),(222,'2013-03-29'),(223,'2013-03-29'),(224,'2013-03-29'),(225,'2013-03-29'),(226,'2013-03-29'),(228,'2013-03-29'),(231,'2013-03-29'),(232,'2013-03-29'),(233,'2013-03-29'),(234,'2013-03-29'),(236,'2013-03-29'),(237,'2013-03-29'),(238,'2013-03-29'),(239,'2013-03-29'),(217,'2013-04-01'),(220,'2013-04-01'),(221,'2013-04-01'),(222,'2013-04-01'),(223,'2013-04-01'),(224,'2013-04-01'),(225,'2013-04-01'),(226,'2013-04-01'),(228,'2013-04-01'),(231,'2013-04-01'),(232,'2013-04-01'),(234,'2013-04-01'),(236,'2013-04-01'),(237,'2013-04-01'),(238,'2013-04-01'),(228,'2013-04-04'),(234,'2013-04-26'),(227,'2013-04-29'),(217,'2013-05-01'),(220,'2013-05-01'),(221,'2013-05-01'),(222,'2013-05-01'),(223,'2013-05-01'),(225,'2013-05-01'),(226,'2013-05-01'),(228,'2013-05-01'),(231,'2013-05-01'),(232,'2013-05-01'),(236,'2013-05-01'),(237,'2013-05-01'),(238,'2013-05-01'),(239,'2013-05-01'),(227,'2013-05-03'),(224,'2013-05-06'),(227,'2013-05-06'),(217,'2013-05-09'),(225,'2013-05-09'),(226,'2013-05-09'),(231,'2013-05-09'),(232,'2013-05-09'),(234,'2013-05-09'),(237,'2013-05-09'),(231,'2013-05-10'),(234,'2013-05-10'),(228,'2013-05-17'),(232,'2013-05-17'),(217,'2013-05-20'),(225,'2013-05-20'),(226,'2013-05-20'),(232,'2013-05-20'),(234,'2013-05-20'),(237,'2013-05-20'),(218,'2013-05-27'),(224,'2013-05-27'),(233,'2013-05-27'),(226,'2013-05-30'),(239,'2013-05-30'),(234,'2013-06-05'),(231,'2013-06-06'),(228,'2013-06-12'),(228,'2013-07-01'),(218,'2013-07-04'),(233,'2013-07-04'),(239,'2013-07-09'),(227,'2013-07-15'),(217,'2013-08-01'),(225,'2013-08-01'),(237,'2013-08-01'),(228,'2013-08-14'),(223,'2013-08-15'),(226,'2013-08-15'),(224,'2013-08-26'),(218,'2013-09-02'),(233,'2013-09-02'),(227,'2013-09-16'),(228,'2013-09-20'),(227,'2013-09-23'),(228,'2013-10-01'),(220,'2013-10-03'),(236,'2013-10-03'),(227,'2013-10-14'),(228,'2013-10-14'),(226,'2013-11-01'),(227,'2013-11-04'),(239,'2013-11-15'),(239,'2013-11-20'),(218,'2013-11-28'),(233,'2013-11-28'),(227,'2013-12-23'),(217,'2013-12-24'),(220,'2013-12-24'),(223,'2013-12-24'),(225,'2013-12-24'),(226,'2013-12-24'),(231,'2013-12-24'),(232,'2013-12-24'),(234,'2013-12-24'),(236,'2013-12-24'),(237,'2013-12-24'),(239,'2013-12-24'),(217,'2013-12-26'),(220,'2013-12-26'),(221,'2013-12-26'),(222,'2013-12-26'),(223,'2013-12-26'),(224,'2013-12-26'),(225,'2013-12-26'),(226,'2013-12-26'),(228,'2013-12-26'),(231,'2013-12-26'),(232,'2013-12-26'),(234,'2013-12-26'),(236,'2013-12-26'),(237,'2013-12-26'),(238,'2013-12-26'),(217,'2013-12-31'),(220,'2013-12-31'),(223,'2013-12-31'),(225,'2013-12-31'),(226,'2013-12-31'),(227,'2013-12-31'),(231,'2013-12-31'),(232,'2013-12-31'),(234,'2013-12-31'),(236,'2013-12-31'),(237,'2013-12-31'),(239,'2013-12-31'),(217,'2014-01-02'),(225,'2014-01-02'),(227,'2014-01-02'),(237,'2014-01-02'),(227,'2014-01-03'),(226,'2014-01-06'),(231,'2014-01-06'),(227,'2014-01-13'),(218,'2014-01-20'),(233,'2014-01-20'),(228,'2014-01-31'),(228,'2014-02-03'),(227,'2014-02-11'),(218,'2014-02-17'),(233,'2014-02-17'),(239,'2014-03-03'),(239,'2014-03-04'),(227,'2014-03-21'),(232,'2014-04-17'),(234,'2014-04-17'),(217,'2014-04-18'),(218,'2014-04-18'),(220,'2014-04-18'),(221,'2014-04-18'),(222,'2014-04-18'),(223,'2014-04-18'),(224,'2014-04-18'),(225,'2014-04-18'),(226,'2014-04-18'),(228,'2014-04-18'),(231,'2014-04-18'),(232,'2014-04-18'),(233,'2014-04-18'),(234,'2014-04-18'),(236,'2014-04-18'),(237,'2014-04-18'),(238,'2014-04-18'),(239,'2014-04-18'),(217,'2014-04-21'),(220,'2014-04-21'),(221,'2014-04-21'),(222,'2014-04-21'),(223,'2014-04-21'),(224,'2014-04-21'),(225,'2014-04-21'),(226,'2014-04-21'),(228,'2014-04-21'),(231,'2014-04-21'),(232,'2014-04-21'),(234,'2014-04-21'),(236,'2014-04-21'),(237,'2014-04-21'),(238,'2014-04-21'),(239,'2014-04-21'),(227,'2014-04-29'),(217,'2014-05-01'),(220,'2014-05-01'),(221,'2014-05-01'),(222,'2014-05-01'),(223,'2014-05-01'),(225,'2014-05-01'),(226,'2014-05-01'),(228,'2014-05-01'),(231,'2014-05-01'),(232,'2014-05-01'),(236,'2014-05-01'),(237,'2014-05-01'),(238,'2014-05-01'),(239,'2014-05-01'),(224,'2014-05-05'),(227,'2014-05-05'),(227,'2014-05-06'),(228,'2014-05-06'),(234,'2014-05-16'),(218,'2014-05-26'),(224,'2014-05-26'),(233,'2014-05-26'),(217,'2014-05-29'),(225,'2014-05-29'),(226,'2014-05-29'),(231,'2014-05-29'),(232,'2014-05-29'),(234,'2014-05-29'),(237,'2014-05-29'),(234,'2014-05-30'),(228,'2014-06-02'),(234,'2014-06-05'),(231,'2014-06-06'),(217,'2014-06-09'),(225,'2014-06-09'),(226,'2014-06-09'),(232,'2014-06-09'),(234,'2014-06-09'),(237,'2014-06-09'),(239,'2014-06-12'),(226,'2014-06-19'),(239,'2014-06-19'),(231,'2014-06-20'),(228,'2014-07-01'),(218,'2014-07-04'),(233,'2014-07-04'),(239,'2014-07-09'),(227,'2014-07-21'),(217,'2014-08-01'),(225,'2014-08-01'),(237,'2014-08-01'),(223,'2014-08-15'),(226,'2014-08-15'),(224,'2014-08-25'),(218,'2014-09-01'),(233,'2014-09-01'),(228,'2014-09-09'),(227,'2014-09-15'),(227,'2014-09-23'),(228,'2014-10-01'),(228,'2014-10-02'),(220,'2014-10-03'),(236,'2014-10-03'),(227,'2014-10-13'),(227,'2014-11-03'),(239,'2014-11-20'),(227,'2014-11-24'),(218,'2014-11-27'),(233,'2014-11-27'),(226,'2014-12-08'),(227,'2014-12-23'),(217,'2014-12-24'),(220,'2014-12-24'),(223,'2014-12-24'),(225,'2014-12-24'),(226,'2014-12-24'),(231,'2014-12-24'),(232,'2014-12-24'),(234,'2014-12-24'),(236,'2014-12-24'),(237,'2014-12-24'),(239,'2014-12-24'),(217,'2014-12-26'),(220,'2014-12-26'),(221,'2014-12-26'),(222,'2014-12-26'),(223,'2014-12-26'),(224,'2014-12-26'),(225,'2014-12-26'),(226,'2014-12-26'),(228,'2014-12-26'),(231,'2014-12-26'),(232,'2014-12-26'),(234,'2014-12-26'),(236,'2014-12-26'),(237,'2014-12-26'),(238,'2014-12-26'),(217,'2014-12-31'),(220,'2014-12-31'),(223,'2014-12-31'),(225,'2014-12-31'),(226,'2014-12-31'),(227,'2014-12-31'),(231,'2014-12-31'),(232,'2014-12-31'),(234,'2014-12-31'),(236,'2014-12-31'),(237,'2014-12-31'),(239,'2014-12-31'),(217,'2015-01-02'),(225,'2015-01-02'),(227,'2015-01-02'),(237,'2015-01-02'),(226,'2015-01-06'),(231,'2015-01-06'),(227,'2015-01-12'),(218,'2015-01-19'),(233,'2015-01-19'),(227,'2015-02-11'),(218,'2015-02-16'),(233,'2015-02-16'),(239,'2015-02-16'),(239,'2015-02-17'),(228,'2015-02-19'),(228,'2015-02-20'),(232,'2015-04-02'),(234,'2015-04-02'),(217,'2015-04-03'),(218,'2015-04-03'),(220,'2015-04-03'),(221,'2015-04-03'),(222,'2015-04-03'),(223,'2015-04-03'),(224,'2015-04-03'),(225,'2015-04-03'),(226,'2015-04-03'),(228,'2015-04-03'),(231,'2015-04-03'),(232,'2015-04-03'),(233,'2015-04-03'),(234,'2015-04-03'),(236,'2015-04-03'),(237,'2015-04-03'),(238,'2015-04-03'),(239,'2015-04-03'),(217,'2015-04-06'),(220,'2015-04-06'),(221,'2015-04-06'),(222,'2015-04-06'),(223,'2015-04-06'),(224,'2015-04-06'),(225,'2015-04-06'),(226,'2015-04-06'),(228,'2015-04-06'),(231,'2015-04-06'),(232,'2015-04-06'),(234,'2015-04-06'),(236,'2015-04-06'),(237,'2015-04-06'),(238,'2015-04-06'),(228,'2015-04-07'),(239,'2015-04-21'),(227,'2015-04-29'),(217,'2015-05-01'),(220,'2015-05-01'),(221,'2015-05-01'),(222,'2015-05-01'),(223,'2015-05-01'),(225,'2015-05-01'),(226,'2015-05-01'),(228,'2015-05-01'),(231,'2015-05-01'),(232,'2015-05-01'),(234,'2015-05-01'),(236,'2015-05-01'),(237,'2015-05-01'),(238,'2015-05-01'),(239,'2015-05-01'),(224,'2015-05-04'),(227,'2015-05-04'),(227,'2015-05-05'),(227,'2015-05-06'),(217,'2015-05-14'),(225,'2015-05-14'),(226,'2015-05-14'),(231,'2015-05-14'),(232,'2015-05-14'),(234,'2015-05-14'),(237,'2015-05-14'),(234,'2015-05-15'),(217,'2015-05-25'),(218,'2015-05-25'),(220,'2015-05-25'),(224,'2015-05-25'),(225,'2015-05-25'),(226,'2015-05-25'),(228,'2015-05-25'),(232,'2015-05-25'),(233,'2015-05-25'),(234,'2015-05-25'),(236,'2015-05-25'),(237,'2015-05-25'),(226,'2015-06-04'),(239,'2015-06-04'),(234,'2015-06-05'),(231,'2015-06-19'),(228,'2015-07-01'),(218,'2015-07-03'),(233,'2015-07-03'),(239,'2015-07-09'),(227,'2015-07-20'),(224,'2015-08-31'),(228,'2015-09-03'),(218,'2015-09-07'),(233,'2015-09-07'),(239,'2015-09-07'),(227,'2015-09-21'),(227,'2015-09-22'),(227,'2015-09-23'),(228,'2015-09-28'),(228,'2015-10-01'),(227,'2015-10-12'),(239,'2015-10-12'),(228,'2015-10-21'),(226,'2015-10-26'),(239,'2015-11-02'),(227,'2015-11-03'),(239,'2015-11-20'),(227,'2015-11-23'),(218,'2015-11-26'),(233,'2015-11-26'),(226,'2015-12-08'),(227,'2015-12-23'),(217,'2015-12-24'),(220,'2015-12-24'),(223,'2015-12-24'),(225,'2015-12-24'),(226,'2015-12-24'),(231,'2015-12-24'),(232,'2015-12-24'),(234,'2015-12-24'),(236,'2015-12-24'),(237,'2015-12-24'),(239,'2015-12-24'),(224,'2015-12-28'),(217,'2015-12-31'),(220,'2015-12-31'),(221,'2015-12-31'),(223,'2015-12-31'),(225,'2015-12-31'),(226,'2015-12-31'),(227,'2015-12-31'),(231,'2015-12-31'),(232,'2015-12-31'),(234,'2015-12-31'),(236,'2015-12-31'),(237,'2015-12-31'),(238,'2015-12-31'),(239,'2015-12-31'),(226,'2016-01-06'),(231,'2016-01-06'),(227,'2016-01-11'),(218,'2016-01-18'),(233,'2016-01-18'),(239,'2016-01-25'),(228,'2016-02-08'),(239,'2016-02-08'),(228,'2016-02-09'),(239,'2016-02-09'),(228,'2016-02-10'),(227,'2016-02-11'),(218,'2016-02-15'),(233,'2016-02-15'),(227,'2016-03-21'),(232,'2016-03-24'),(234,'2016-03-24'),(217,'2016-03-25'),(218,'2016-03-25'),(220,'2016-03-25'),(221,'2016-03-25'),(222,'2016-03-25'),(223,'2016-03-25'),(224,'2016-03-25'),(225,'2016-03-25'),(226,'2016-03-25'),(228,'2016-03-25'),(231,'2016-03-25'),(232,'2016-03-25'),(233,'2016-03-25'),(234,'2016-03-25'),(236,'2016-03-25'),(237,'2016-03-25'),(238,'2016-03-25'),(239,'2016-03-25'),(217,'2016-03-28'),(220,'2016-03-28'),(221,'2016-03-28'),(222,'2016-03-28'),(223,'2016-03-28'),(224,'2016-03-28'),(225,'2016-03-28'),(226,'2016-03-28'),(228,'2016-03-28'),(231,'2016-03-28'),(232,'2016-03-28'),(234,'2016-03-28'),(236,'2016-03-28'),(237,'2016-03-28'),(238,'2016-03-28'),(228,'2016-04-04'),(239,'2016-04-21'),(234,'2016-04-22'),(227,'2016-04-29'),(224,'2016-05-02'),(228,'2016-05-02'),(227,'2016-05-03'),(227,'2016-05-04'),(217,'2016-05-05'),(225,'2016-05-05'),(226,'2016-05-05'),(227,'2016-05-05'),(231,'2016-05-05'),(232,'2016-05-05'),(234,'2016-05-05'),(237,'2016-05-05'),(234,'2016-05-06'),(217,'2016-05-16'),(220,'2016-05-16'),(225,'2016-05-16'),(226,'2016-05-16'),(232,'2016-05-16'),(234,'2016-05-16'),(236,'2016-05-16'),(237,'2016-05-16'),(232,'2016-05-17'),(226,'2016-05-26'),(239,'2016-05-26'),(218,'2016-05-30'),(224,'2016-05-30'),(233,'2016-05-30'),(231,'2016-06-06'),(228,'2016-06-09'),(231,'2016-06-24'),(228,'2016-07-01'),(218,'2016-07-04'),(233,'2016-07-04'),(227,'2016-07-18'),(217,'2016-08-01'),(225,'2016-08-01'),(237,'2016-08-01'),(228,'2016-08-02'),(227,'2016-08-11'),(223,'2016-08-15'),(226,'2016-08-15'),(224,'2016-08-29'),(218,'2016-09-05'),(233,'2016-09-05'),(239,'2016-09-07'),(228,'2016-09-16'),(227,'2016-09-19'),(227,'2016-09-22'),(220,'2016-10-03'),(236,'2016-10-03'),(227,'2016-10-10'),(228,'2016-10-10'),(239,'2016-10-12'),(228,'2016-10-21'),(226,'2016-10-26'),(226,'2016-11-01'),(239,'2016-11-02'),(227,'2016-11-03'),(239,'2016-11-15'),(227,'2016-11-23'),(218,'2016-11-24'),(233,'2016-11-24'),(226,'2016-12-08'),(227,'2016-12-23'),(217,'2016-12-26'),(218,'2016-12-26'),(220,'2016-12-26'),(221,'2016-12-26'),(222,'2016-12-26'),(223,'2016-12-26'),(224,'2016-12-26'),(225,'2016-12-26'),(226,'2016-12-26'),(228,'2016-12-26'),(231,'2016-12-26'),(232,'2016-12-26'),(233,'2016-12-26'),(234,'2016-12-26'),(236,'2016-12-26'),(237,'2016-12-26'),(238,'2016-12-26'),(224,'2016-12-27'),(228,'2016-12-27'),(239,'2016-12-30'),(217,'2017-01-02'),(218,'2017-01-02'),(222,'2017-01-02'),(224,'2017-01-02'),(225,'2017-01-02'),(227,'2017-01-02'),(228,'2017-01-02'),(233,'2017-01-02'),(236,'2017-01-02'),(237,'2017-01-02'),(227,'2017-01-03'),(226,'2017-01-06'),(231,'2017-01-06'),(227,'2017-01-09'),(218,'2017-01-16'),(233,'2017-01-16'),(239,'2017-01-25'),(228,'2017-01-30'),(228,'2017-01-31'),(218,'2017-02-20'),(233,'2017-02-20'),(239,'2017-02-27'),(239,'2017-02-28'),(227,'2017-03-20'),(228,'2017-04-04'),(232,'2017-04-13'),(234,'2017-04-13'),(217,'2017-04-14'),(218,'2017-04-14'),(220,'2017-04-14'),(221,'2017-04-14'),(222,'2017-04-14'),(224,'2017-04-14'),(225,'2017-04-14'),(226,'2017-04-14'),(228,'2017-04-14'),(231,'2017-04-14'),(232,'2017-04-14'),(233,'2017-04-14'),(234,'2017-04-14'),(236,'2017-04-14'),(237,'2017-04-14'),(238,'2017-04-14'),(239,'2017-04-14'),(217,'2017-04-17'),(220,'2017-04-17'),(221,'2017-04-17'),(222,'2017-04-17'),(223,'2017-04-17'),(224,'2017-04-17'),(225,'2017-04-17'),(226,'2017-04-17'),(228,'2017-04-17'),(231,'2017-04-17'),(232,'2017-04-17'),(234,'2017-04-17'),(236,'2017-04-17'),(237,'2017-04-17'),(238,'2017-04-17'),(239,'2017-04-21'),(217,'2017-05-01'),(220,'2017-05-01'),(221,'2017-05-01'),(222,'2017-05-01'),(223,'2017-05-01'),(224,'2017-05-01'),(225,'2017-05-01'),(226,'2017-05-01'),(228,'2017-05-01'),(231,'2017-05-01'),(232,'2017-05-01'),(236,'2017-05-01'),(237,'2017-05-01'),(238,'2017-05-01'),(239,'2017-05-01'),(227,'2017-05-03'),(228,'2017-05-03'),(227,'2017-05-04'),(227,'2017-05-05'),(234,'2017-05-12'),(232,'2017-05-17'),(217,'2017-05-25'),(225,'2017-05-25'),(226,'2017-05-25'),(231,'2017-05-25'),(232,'2017-05-25'),(234,'2017-05-25'),(237,'2017-05-25'),(234,'2017-05-26'),(218,'2017-05-29'),(224,'2017-05-29'),(233,'2017-05-29'),(228,'2017-05-30'),(217,'2017-06-05'),(220,'2017-06-05'),(225,'2017-06-05'),(226,'2017-06-05'),(232,'2017-06-05'),(234,'2017-06-05'),(236,'2017-06-05'),(237,'2017-06-05'),(231,'2017-06-06'),(226,'2017-06-15'),(239,'2017-06-15'),(231,'2017-06-23'),(220,'2017-06-30'),(218,'2017-07-04'),(233,'2017-07-04'),(220,'2017-07-10'),(227,'2017-07-17'),(217,'2017-08-01'),(225,'2017-08-01'),(237,'2017-08-01'),(227,'2017-08-11'),(226,'2017-08-15'),(228,'2017-08-23'),(224,'2017-08-28'),(218,'2017-09-04'),(233,'2017-09-04'),(236,'2017-09-04'),(239,'2017-09-07'),(227,'2017-09-18'),(228,'2017-10-02'),(220,'2017-10-03'),(236,'2017-10-03'),(228,'2017-10-05'),(227,'2017-10-09'),(239,'2017-10-12'),(226,'2017-10-26'),(220,'2017-10-31'),(236,'2017-10-31'),(226,'2017-11-01'),(239,'2017-11-02'),(239,'2017-11-20'),(218,'2017-11-23'),(227,'2017-11-23'),(233,'2017-11-23'),(226,'2017-12-08'),(217,'2017-12-26'),(220,'2017-12-26'),(221,'2017-12-26'),(222,'2017-12-26'),(223,'2017-12-26'),(224,'2017-12-26'),(225,'2017-12-26'),(226,'2017-12-26'),(228,'2017-12-26'),(231,'2017-12-26'),(232,'2017-12-26'),(234,'2017-12-26'),(236,'2017-12-26'),(237,'2017-12-26'),(238,'2017-12-26'),(217,'2018-01-02'),(225,'2018-01-02'),(227,'2018-01-02'),(237,'2018-01-02'),(227,'2018-01-03'),(227,'2018-01-08'),(218,'2018-01-15'),(233,'2018-01-15'),(227,'2018-02-12'),(239,'2018-02-12'),(239,'2018-02-13'),(239,'2018-02-14'),(228,'2018-02-16'),(218,'2018-02-19'),(228,'2018-02-19'),(233,'2018-02-19'),(227,'2018-03-21'),(232,'2018-03-29'),(234,'2018-03-29'),(217,'2018-03-30'),(218,'2018-03-30'),(220,'2018-03-30'),(221,'2018-03-30'),(222,'2018-03-30'),(224,'2018-03-30'),(225,'2018-03-30'),(226,'2018-03-30'),(228,'2018-03-30'),(231,'2018-03-30'),(232,'2018-03-30'),(233,'2018-03-30'),(234,'2018-03-30'),(236,'2018-03-30'),(237,'2018-03-30'),(238,'2018-03-30'),(239,'2018-03-30'),(217,'2018-04-02'),(220,'2018-04-02'),(221,'2018-04-02'),(222,'2018-04-02'),(223,'2018-04-02'),(224,'2018-04-02'),(225,'2018-04-02'),(226,'2018-04-02'),(228,'2018-04-02'),(231,'2018-04-02'),(232,'2018-04-02'),(234,'2018-04-02'),(236,'2018-04-02'),(237,'2018-04-02'),(238,'2018-04-02'),(220,'2018-04-03'),(228,'2018-04-05'),(234,'2018-04-27'),(227,'2018-04-30'),(217,'2018-05-01'),(220,'2018-05-01'),(221,'2018-05-01'),(222,'2018-05-01'),(225,'2018-05-01'),(226,'2018-05-01'),(228,'2018-05-01'),(231,'2018-05-01'),(232,'2018-05-01'),(236,'2018-05-01'),(237,'2018-05-01'),(238,'2018-05-01'),(239,'2018-05-01'),(227,'2018-05-03'),(227,'2018-05-04'),(224,'2018-05-07'),(217,'2018-05-10'),(225,'2018-05-10'),(226,'2018-05-10'),(231,'2018-05-10'),(232,'2018-05-10'),(234,'2018-05-10'),(237,'2018-05-10'),(234,'2018-05-11'),(232,'2018-05-17'),(217,'2018-05-21'),(220,'2018-05-21'),(225,'2018-05-21'),(226,'2018-05-21'),(232,'2018-05-21'),(234,'2018-05-21'),(236,'2018-05-21'),(237,'2018-05-21'),(228,'2018-05-22'),(228,'2018-05-24'),(218,'2018-05-28'),(224,'2018-05-28'),(233,'2018-05-28'),(226,'2018-05-31'),(239,'2018-05-31'),(234,'2018-06-05'),(231,'2018-06-06'),(228,'2018-06-18'),(231,'2018-06-22'),(228,'2018-07-02'),(218,'2018-07-04'),(233,'2018-07-04'),(239,'2018-07-09'),(217,'2018-08-01'),(225,'2018-08-01'),(237,'2018-08-01'),(226,'2018-08-15'),(224,'2018-08-27'),(218,'2018-09-03'),(233,'2018-09-03'),(239,'2018-09-07'),(227,'2018-09-17'),(227,'2018-09-24'),(228,'2018-09-25'),(227,'2018-09-26'),(228,'2018-10-01'),(220,'2018-10-03'),(236,'2018-10-03'),(227,'2018-10-08'),(239,'2018-10-12'),(228,'2018-10-17'),(226,'2018-10-26'),(226,'2018-11-01'),(239,'2018-11-02'),(239,'2018-11-15'),(239,'2018-11-20'),(218,'2018-11-22'),(233,'2018-11-22'),(227,'2018-11-23'),(218,'2018-12-05'),(233,'2018-12-05'),(217,'2018-12-24'),(220,'2018-12-24'),(223,'2018-12-24'),(225,'2018-12-24'),(226,'2018-12-24'),(227,'2018-12-24'),(231,'2018-12-24'),(232,'2018-12-24'),(234,'2018-12-24'),(236,'2018-12-24'),(237,'2018-12-24'),(239,'2018-12-24'),(217,'2018-12-26'),(220,'2018-12-26'),(221,'2018-12-26'),(222,'2018-12-26'),(223,'2018-12-26'),(224,'2018-12-26'),(225,'2018-12-26'),(226,'2018-12-26'),(228,'2018-12-26'),(231,'2018-12-26'),(232,'2018-12-26'),(234,'2018-12-26'),(236,'2018-12-26'),(237,'2018-12-26'),(238,'2018-12-26'),(217,'2018-12-31'),(220,'2018-12-31'),(223,'2018-12-31'),(225,'2018-12-31'),(226,'2018-12-31'),(227,'2018-12-31'),(231,'2018-12-31'),(232,'2018-12-31'),(234,'2018-12-31'),(236,'2018-12-31'),(237,'2018-12-31'),(239,'2018-12-31'),(217,'2019-01-02'),(225,'2019-01-02'),(227,'2019-01-02'),(237,'2019-01-02'),(227,'2019-01-03'),(227,'2019-01-14'),(227,'2019-01-16'),(228,'2019-01-16'),(218,'2019-01-21'),(233,'2019-01-21'),(239,'2019-01-25'),(227,'2019-02-04'),(228,'2019-02-05'),(228,'2019-02-06'),(228,'2019-02-07'),(227,'2019-02-11'),(218,'2019-02-18'),(233,'2019-02-18'),(227,'2019-02-20'),(228,'2019-02-20'),(227,'2019-02-25'),(228,'2019-02-25'),(239,'2019-03-04'),(239,'2019-03-05'),(239,'2019-03-06'),(227,'2019-03-21'),(228,'2019-04-05'),(232,'2019-04-18'),(234,'2019-04-18'),(217,'2019-04-19'),(218,'2019-04-19'),(220,'2019-04-19'),(221,'2019-04-19'),(222,'2019-04-19'),(224,'2019-04-19'),(225,'2019-04-19'),(226,'2019-04-19'),(228,'2019-04-19'),(231,'2019-04-19'),(232,'2019-04-19'),(233,'2019-04-19'),(234,'2019-04-19'),(236,'2019-04-19'),(237,'2019-04-19'),(238,'2019-04-19'),(239,'2019-04-19'),(217,'2019-04-22'),(220,'2019-04-22'),(221,'2019-04-22'),(222,'2019-04-22'),(223,'2019-04-22'),(224,'2019-04-22'),(225,'2019-04-22'),(226,'2019-04-22'),(228,'2019-04-22'),(231,'2019-04-22'),(232,'2019-04-22'),(234,'2019-04-22'),(236,'2019-04-22'),(237,'2019-04-22'),(238,'2019-04-22'),(227,'2019-04-29'),(227,'2019-04-30'),(217,'2019-05-01'),(220,'2019-05-01'),(221,'2019-05-01'),(222,'2019-05-01'),(225,'2019-05-01'),(226,'2019-05-01'),(227,'2019-05-01'),(228,'2019-05-01'),(231,'2019-05-01'),(232,'2019-05-01'),(236,'2019-05-01'),(237,'2019-05-01'),(238,'2019-05-01'),(239,'2019-05-01'),(227,'2019-05-02'),(227,'2019-05-03'),(224,'2019-05-06'),(227,'2019-05-06'),(228,'2019-05-13'),(232,'2019-05-17'),(234,'2019-05-17'),(227,'2019-05-22'),(228,'2019-05-22'),(218,'2019-05-27'),(224,'2019-05-27'),(233,'2019-05-27'),(217,'2019-05-30'),(225,'2019-05-30'),(231,'2019-05-30'),(232,'2019-05-30'),(234,'2019-05-30'),(237,'2019-05-30'),(234,'2019-05-31'),(234,'2019-06-05'),(231,'2019-06-06'),(228,'2019-06-07'),(217,'2019-06-10'),(220,'2019-06-10'),(225,'2019-06-10'),(226,'2019-06-10'),(232,'2019-06-10'),(234,'2019-06-10'),(236,'2019-06-10'),(237,'2019-06-10'),(239,'2019-06-20'),(231,'2019-06-21'),(228,'2019-07-01'),(218,'2019-07-04'),(233,'2019-07-04'),(239,'2019-07-09'),(227,'2019-07-15'),(217,'2019-08-01'),(225,'2019-08-01'),(237,'2019-08-01'),(227,'2019-08-12'),(223,'2019-08-15'),(224,'2019-08-26'),(218,'2019-09-02'),(233,'2019-09-02'),(227,'2019-09-16'),(227,'2019-09-23'),(228,'2019-10-01'),(220,'2019-10-03'),(228,'2019-10-03'),(236,'2019-10-03'),(228,'2019-10-07'),(227,'2019-10-14'),(227,'2019-10-22'),(227,'2019-11-04'),(228,'2019-11-04'),(239,'2019-11-15'),(239,'2019-11-20'),(218,'2019-11-28'),(233,'2019-11-28'),(227,'2019-12-09'),(228,'2019-12-09'),(227,'2019-12-16'),(228,'2019-12-16'),(217,'2019-12-24'),(220,'2019-12-24'),(223,'2019-12-24'),(225,'2019-12-24'),(226,'2019-12-24'),(231,'2019-12-24'),(232,'2019-12-24'),(234,'2019-12-24'),(236,'2019-12-24'),(237,'2019-12-24'),(239,'2019-12-24'),(217,'2019-12-26'),(220,'2019-12-26'),(221,'2019-12-26'),(222,'2019-12-26'),(223,'2019-12-26'),(224,'2019-12-26'),(225,'2019-12-26'),(226,'2019-12-26'),(228,'2019-12-26'),(231,'2019-12-26'),(232,'2019-12-26'),(234,'2019-12-26'),(236,'2019-12-26'),(237,'2019-12-26'),(238,'2019-12-26'),(217,'2019-12-31'),(220,'2019-12-31'),(223,'2019-12-31'),(225,'2019-12-31'),(226,'2019-12-31'),(227,'2019-12-31'),(231,'2019-12-31'),(232,'2019-12-31'),(234,'2019-12-31'),(236,'2019-12-31'),(237,'2019-12-31'),(239,'2019-12-31'),(217,'2020-01-02'),(225,'2020-01-02'),(227,'2020-01-02'),(237,'2020-01-02'),(227,'2020-01-03'),(231,'2020-01-06'),(227,'2020-01-13'),(218,'2020-01-20'),(233,'2020-01-20'),(228,'2020-01-27'),(228,'2020-01-28'),(227,'2020-02-05'),(228,'2020-02-05'),(227,'2020-02-11'),(218,'2020-02-17'),(233,'2020-02-17'),(227,'2020-02-24'),(239,'2020-02-24'),(239,'2020-02-25'),(239,'2020-02-26'),(227,'2020-03-20'),(232,'2020-04-09'),(234,'2020-04-09'),(217,'2020-04-10'),(218,'2020-04-10'),(220,'2020-04-10'),(221,'2020-04-10'),(222,'2020-04-10'),(223,'2020-04-10'),(224,'2020-04-10'),(225,'2020-04-10'),(226,'2020-04-10'),(228,'2020-04-10'),(231,'2020-04-10'),(232,'2020-04-10'),(233,'2020-04-10'),(234,'2020-04-10'),(236,'2020-04-10'),(237,'2020-04-10'),(238,'2020-04-10'),(239,'2020-04-10'),(217,'2020-04-13'),(220,'2020-04-13'),(221,'2020-04-13'),(222,'2020-04-13'),(223,'2020-04-13'),(224,'2020-04-13'),(225,'2020-04-13'),(226,'2020-04-13'),(228,'2020-04-13'),(231,'2020-04-13'),(232,'2020-04-13'),(234,'2020-04-13'),(236,'2020-04-13'),(237,'2020-04-13'),(238,'2020-04-13'),(239,'2020-04-21'),(227,'2020-04-29'),(228,'2020-04-30'),(217,'2020-05-01'),(220,'2020-05-01'),(221,'2020-05-01'),(222,'2020-05-01'),(223,'2020-05-01'),(225,'2020-05-01'),(226,'2020-05-01'),(228,'2020-05-01'),(231,'2020-05-01'),(232,'2020-05-01'),(236,'2020-05-01'),(237,'2020-05-01'),(238,'2020-05-01'),(239,'2020-05-01'),(227,'2020-05-04'),(228,'2020-05-04'),(227,'2020-05-05'),(227,'2020-05-06'),(224,'2020-05-08'),(234,'2020-05-08'),(217,'2020-05-21'),(225,'2020-05-21'),(231,'2020-05-21'),(232,'2020-05-21'),(234,'2020-05-21'),(237,'2020-05-21'),(234,'2020-05-22'),(218,'2020-05-25'),(224,'2020-05-25'),(233,'2020-05-25'),(217,'2020-06-01'),(220,'2020-06-01'),(225,'2020-06-01'),(226,'2020-06-01'),(227,'2020-06-01'),(228,'2020-06-01'),(232,'2020-06-01'),(234,'2020-06-01'),(236,'2020-06-01'),(237,'2020-06-01'),(234,'2020-06-05'),(239,'2020-06-11'),(231,'2020-06-19'),(228,'2020-06-25'),(227,'2020-07-01'),(228,'2020-07-01'),(218,'2020-07-03'),(233,'2020-07-03'),(227,'2020-07-13'),(228,'2020-07-13'),(227,'2020-07-23'),(227,'2020-07-24'),(227,'2020-08-10'),(224,'2020-08-31'),(218,'2020-09-07'),(233,'2020-09-07'),(239,'2020-09-07'),(227,'2020-09-21'),(228,'2020-09-21'),(227,'2020-09-22'),(228,'2020-10-01'),(228,'2020-10-02'),(239,'2020-10-12'),(228,'2020-10-13'),(223,'2020-10-15'),(223,'2020-10-19'),(226,'2020-10-26'),(228,'2020-10-26'),(239,'2020-11-02'),(227,'2020-11-03'),(239,'2020-11-20'),(227,'2020-11-23'),(218,'2020-11-26'),(233,'2020-11-26'),(217,'2020-12-24'),(220,'2020-12-24'),(223,'2020-12-24'),(225,'2020-12-24'),(226,'2020-12-24'),(231,'2020-12-24'),(232,'2020-12-24'),(234,'2020-12-24'),(236,'2020-12-24'),(239,'2020-12-24'),(224,'2020-12-28'),(217,'2020-12-31'),(220,'2020-12-31'),(223,'2020-12-31'),(225,'2020-12-31'),(226,'2020-12-31'),(227,'2020-12-31'),(231,'2020-12-31'),(232,'2020-12-31'),(234,'2020-12-31'),(236,'2020-12-31'),(239,'2020-12-31'),(231,'2021-01-06'),(227,'2021-01-11'),(218,'2021-01-18'),(233,'2021-01-18'),(239,'2021-01-25'),(227,'2021-02-01'),(228,'2021-02-01'),(227,'2021-02-03'),(228,'2021-02-03'),(227,'2021-02-08'),(228,'2021-02-08'),(228,'2021-02-10'),(227,'2021-02-11'),(228,'2021-02-12'),(218,'2021-02-15'),(227,'2021-02-15'),(228,'2021-02-15'),(233,'2021-02-15'),(239,'2021-02-15'),(239,'2021-02-16'),(227,'2021-02-23'),(227,'2021-02-25');
/*!40000 ALTER TABLE `trading_days_minus` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `trading_platform_plan`
--

LOCK TABLES `trading_platform_plan` WRITE;
/*!40000 ALTER TABLE `trading_platform_plan` DISABLE KEYS */;
INSERT INTO `trading_platform_plan` VALUES (1,517,0,NULL,1,'2021-02-08 17:49:37',1,'2018-01-01 23:00:00',1),(2,653,1,2,1,'2021-02-08 17:49:37',1,'2021-02-25 15:24:07',7),(3,643,4,3,1,'2021-02-08 17:49:37',1,'2020-12-15 12:24:32',2),(4,521,4,1,1,'2021-02-08 17:49:37',1,'2018-01-01 23:00:00',1),(5,560,3,4,1,'2021-02-08 17:49:37',1,'2018-07-05 10:29:35',3),(6,522,4,NULL,1,'2021-02-08 17:49:37',1,'2018-01-01 23:00:00',1),(7,548,4,NULL,1,'2021-02-08 17:49:37',1,'2018-05-18 04:23:39',5),(8,572,0,NULL,1,'2021-02-08 17:49:37',1,'2018-09-08 05:17:10',1),(9,571,1,NULL,1,'2021-02-08 17:49:37',1,'2018-09-08 05:16:45',1),(10,570,2,NULL,1,'2021-02-08 17:49:37',1,'2018-09-08 05:16:08',0),(11,573,5,NULL,1,'2021-02-08 17:49:37',1,'2018-09-08 05:18:38',0),(12,574,6,NULL,1,'2021-02-08 17:49:37',1,'2018-09-08 05:19:53',0),(13,575,7,NULL,1,'2021-02-08 17:49:37',1,'2018-09-08 05:20:55',0),(14,576,3,NULL,1,'2021-02-08 17:49:37',1,'2018-09-08 05:22:29',0),(15,577,4,NULL,1,'2021-02-08 17:49:37',1,'2018-09-08 05:24:44',0),(16,595,1,1,1,'2021-02-08 17:49:37',1,'2019-02-12 20:13:55',1),(17,644,6,5,1,'2021-02-08 17:49:37',1,'2021-01-03 09:30:58',0),(18,654,7,6,1,'2021-03-01 09:25:59',1,'2021-03-01 09:25:59',0);
/*!40000 ALTER TABLE `trading_platform_plan` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `security_derived_link`
--

LOCK TABLES `security_derived_link` WRITE;
/*!40000 ALTER TABLE `security_derived_link` DISABLE KEYS */;
INSERT INTO `security_derived_link` VALUES (3716,'p',1941),(3717,'q',1941),(3714,'p',3063),(3717,'r',3069),(3717,'s',3715);
/*!40000 ALTER TABLE `security_derived_link` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2021-03-27 16:23:58
