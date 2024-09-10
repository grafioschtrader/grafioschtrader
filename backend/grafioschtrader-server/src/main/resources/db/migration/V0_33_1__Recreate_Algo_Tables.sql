SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `grafioschtrader`
--

-- --------------------------------------------------------
DROP TABLE IF EXISTS `algo_message_alert`;
DROP TABLE IF EXISTS `algo_rule_param2`;
DROP TABLE IF EXISTS `algo_rule`;
DROP TABLE IF EXISTS `algo_rule_strategy_param`;
DROP TABLE IF EXISTS `algo_strategy`;
DROP TABLE IF EXISTS `algo_rule_strategy`;
DROP TABLE IF EXISTS `algo_strategy`;

DROP TABLE IF EXISTS `algo_security`;
DROP TABLE IF EXISTS `algo_assetclass`;
DROP TABLE IF EXISTS `algo_top`;
DROP TABLE IF EXISTS `algo_assetclass_security`;
DROP TABLE IF EXISTS `algo_top_asset_security`;

--
-- Table structure for table `algo_assetclass`
--


CREATE TABLE `algo_assetclass` (
  `id_algo_assetclass_security` int(11) NOT NULL,
  `id_algo_assetclass_parent` int(11) DEFAULT NULL,
  `id_asset_class` int(11) DEFAULT NULL,
  `category_type` smallint(6) DEFAULT NULL,
  `spec_invest_instrument` smallint(6) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;

-- --------------------------------------------------------

--
-- Table structure for table `algo_assetclass_security`
--


CREATE TABLE `algo_assetclass_security` (
  `id_algo_assetclass_security` int(11) NOT NULL,
  `id_securitycash_account_1` int(11) DEFAULT NULL,
  `id_securitycash_account_2` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `algo_message_alert`
--


CREATE TABLE `algo_message_alert` (
  `id_algo_message_alert` int(11) NOT NULL,
  `id_security_currency` int(11) DEFAULT NULL,
  `id_tenant` int(11) NOT NULL,
  `id_algo_strategy` int(11) NOT NULL,
  `alert_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `algo_rule`
--


CREATE TABLE `algo_rule` (
  `id_algo_rule_strategy` int(11) NOT NULL,
  `buy_sell` tinyint(1) NOT NULL,
  `and_or_not` tinyint(1) NOT NULL,
  `trading_rule` tinyint(4) NOT NULL,
  `rule_param1` tinyint(4) NOT NULL,
  `rule_param2` tinyint(4) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;

-- --------------------------------------------------------

--
-- Table structure for table `algo_rule_param2`
--


CREATE TABLE `algo_rule_param2` (
  `id_algo_rule_strategy` int(11) NOT NULL,
  `param_name` varchar(32) NOT NULL,
  `param_value` varchar(24) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;

-- --------------------------------------------------------

--
-- Table structure for table `algo_rule_strategy`
--


CREATE TABLE `algo_rule_strategy` (
  `id_algo_rule_strategy` int(11) NOT NULL,
  `id_algo_assetclass_security` int(11) NOT NULL,
  `dtype` varchar(1) NOT NULL,
  `id_tenant` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `algo_rule_strategy_param`
--


CREATE TABLE `algo_rule_strategy_param` (
  `id_algo_rule_strategy` int(11) NOT NULL,
  `param_name` varchar(32) NOT NULL,
  `param_value` varchar(24) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci ROW_FORMAT=COMPACT;

-- --------------------------------------------------------

--
-- Table structure for table `algo_security`
--


CREATE TABLE `algo_security` (
  `id_algo_assetclass_security` int(11) NOT NULL,
  `id_algo_security_parent` int(11) DEFAULT NULL,
  `id_securitycurrency` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `algo_strategy`
--


CREATE TABLE `algo_strategy` (
  `id_algo_rule_strategy` int(11) NOT NULL,
  `algo_strategy_impl` tinyint(4) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `algo_top`
--


CREATE TABLE `algo_top` (
  `id_algo_assetclass_security` int(11) NOT NULL,
  `rule_or_strategy` tinyint(1) NOT NULL,
  `name` varchar(32) NOT NULL,
  `activatable` tinyint(1) NOT NULL,
  `id_watchlist` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `algo_top_asset_security`
--


CREATE TABLE `algo_top_asset_security` (
  `id_algo_assetclass_security` int(11) NOT NULL,
  `dtype` varchar(1) NOT NULL,
  `id_tenant` int(11) NOT NULL,
  `percentage` float DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `algo_assetclass`
--
ALTER TABLE `algo_assetclass`
  ADD PRIMARY KEY (`id_algo_assetclass_security`),
  ADD KEY `FK_AlgoAssetClass_Assetclass` (`id_asset_class`),
  ADD KEY `FK_AlgoAssetClass_AlgoTop` (`id_algo_assetclass_parent`);

--
-- Indexes for table `algo_assetclass_security`
--
ALTER TABLE `algo_assetclass_security`
  ADD PRIMARY KEY (`id_algo_assetclass_security`),
  ADD KEY `FK_AlgoAssetClassSecurity_SecurityAccount_1` (`id_securitycash_account_1`),
  ADD KEY `FK_AlgoAssetClassSecurity_SecurityAccount_2` (`id_securitycash_account_2`);

--
-- Indexes for table `algo_message_alert`
--
ALTER TABLE `algo_message_alert`
  ADD PRIMARY KEY (`id_algo_message_alert`);

--
-- Indexes for table `algo_rule`
--
ALTER TABLE `algo_rule`
  ADD PRIMARY KEY (`id_algo_rule_strategy`);

--
-- Indexes for table `algo_rule_param2`
--
ALTER TABLE `algo_rule_param2`
  ADD PRIMARY KEY (`id_algo_rule_strategy`,`param_name`) USING BTREE;

--
-- Indexes for table `algo_rule_strategy`
--
ALTER TABLE `algo_rule_strategy`
  ADD PRIMARY KEY (`id_algo_rule_strategy`) USING BTREE,
  ADD KEY `FK_AlgoRuleStrategy_AlgoTopAssetSecurity` (`id_algo_assetclass_security`);

--
-- Indexes for table `algo_rule_strategy_param`
--
ALTER TABLE `algo_rule_strategy_param`
  ADD PRIMARY KEY (`id_algo_rule_strategy`,`param_name`) USING BTREE;

--
-- Indexes for table `algo_security`
--
ALTER TABLE `algo_security`
  ADD PRIMARY KEY (`id_algo_assetclass_security`),
  ADD KEY `FK_AlgoSecurity_Security` (`id_securitycurrency`),
  ADD KEY `FK_AlgoSecurity_AlgoAssetClass` (`id_algo_security_parent`);

--
-- Indexes for table `algo_strategy`
--
ALTER TABLE `algo_strategy`
  ADD PRIMARY KEY (`id_algo_rule_strategy`);

--
-- Indexes for table `algo_top`
--
ALTER TABLE `algo_top`
  ADD PRIMARY KEY (`id_algo_assetclass_security`),
  ADD KEY `FK_AlgoTop_Watchlist` (`id_watchlist`);

--
-- Indexes for table `algo_top_asset_security`
--
ALTER TABLE `algo_top_asset_security`
  ADD PRIMARY KEY (`id_algo_assetclass_security`),
  ADD KEY `FK_AlgoTopAssetSecurity_Tenant` (`id_tenant`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `algo_message_alert`
--
ALTER TABLE `algo_message_alert`
  MODIFY `id_algo_message_alert` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `algo_rule_strategy`
--
ALTER TABLE `algo_rule_strategy`
  MODIFY `id_algo_rule_strategy` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `algo_top_asset_security`
--
ALTER TABLE `algo_top_asset_security`
  MODIFY `id_algo_assetclass_security` int(11) NOT NULL AUTO_INCREMENT;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `algo_assetclass`
--
ALTER TABLE `algo_assetclass`
  ADD CONSTRAINT `FK_AlgoAssetClass_AlgoAssetClassSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_assetclass_security` (`id_algo_assetclass_security`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_AlgoAssetClass_AlgoTop` FOREIGN KEY (`id_algo_assetclass_parent`) REFERENCES `algo_top` (`id_algo_assetclass_security`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_AlgoAssetClass_Assetclass` FOREIGN KEY (`id_asset_class`) REFERENCES `assetclass` (`id_asset_class`);

--
-- Constraints for table `algo_assetclass_security`
--
ALTER TABLE `algo_assetclass_security`
  ADD CONSTRAINT `FK_AlgoAssetClassSecurity_AlgoTopAssetSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_top_asset_security` (`id_algo_assetclass_security`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_AlgoAssetClassSecurity_SecurityAccount_1` FOREIGN KEY (`id_securitycash_account_1`) REFERENCES `securityaccount` (`id_securitycash_account`),
  ADD CONSTRAINT `FK_AlgoAssetClassSecurity_SecurityAccount_2` FOREIGN KEY (`id_securitycash_account_2`) REFERENCES `securityaccount` (`id_securitycash_account`);

--
-- Constraints for table `algo_rule`
--
ALTER TABLE `algo_rule`
  ADD CONSTRAINT `FK_AlgoRule_AlgoRuleStrategy` FOREIGN KEY (`id_algo_rule_strategy`) REFERENCES `algo_rule_strategy` (`id_algo_rule_strategy`) ON DELETE CASCADE;

--
-- Constraints for table `algo_rule_param2`
--
ALTER TABLE `algo_rule_param2`
  ADD CONSTRAINT `FK_AlgoRuleParam_AlgoRule` FOREIGN KEY (`id_algo_rule_strategy`) REFERENCES `algo_rule` (`id_algo_rule_strategy`);

--
-- Constraints for table `algo_rule_strategy`
--
ALTER TABLE `algo_rule_strategy`
  ADD CONSTRAINT `FK_AlgoRuleStrategy_AlgoTopAssetSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_top_asset_security` (`id_algo_assetclass_security`) ON DELETE CASCADE;

--
-- Constraints for table `algo_rule_strategy_param`
--
ALTER TABLE `algo_rule_strategy_param`
  ADD CONSTRAINT `FK_AlgoRuleStrategyParam_AlgoRuleStrategy` FOREIGN KEY (`id_algo_rule_strategy`) REFERENCES `algo_rule_strategy` (`id_algo_rule_strategy`);

--
-- Constraints for table `algo_security`
--
ALTER TABLE `algo_security`
  ADD CONSTRAINT `FK_AlgoSecurity_AlgoAssetClass` FOREIGN KEY (`id_algo_security_parent`) REFERENCES `algo_assetclass` (`id_algo_assetclass_security`),
  ADD CONSTRAINT `FK_AlgoSecurity_AlgoAssetClassSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_assetclass_security` (`id_algo_assetclass_security`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_AlgoSecurity_Security` FOREIGN KEY (`id_securitycurrency`) REFERENCES `security` (`id_securitycurrency`);

--
-- Constraints for table `algo_strategy`
--
ALTER TABLE `algo_strategy`
  ADD CONSTRAINT `FK_AlgoStrategy_AlgoRuleStrategy` FOREIGN KEY (`id_algo_rule_strategy`) REFERENCES `algo_rule_strategy` (`id_algo_rule_strategy`) ON DELETE CASCADE;

--
-- Constraints for table `algo_top`
--
ALTER TABLE `algo_top`
  ADD CONSTRAINT `FK_AlgoTop_AlgoTopAssetSecurity` FOREIGN KEY (`id_algo_assetclass_security`) REFERENCES `algo_top_asset_security` (`id_algo_assetclass_security`) ON DELETE CASCADE,
  ADD CONSTRAINT `FK_AlgoTop_Watchlist` FOREIGN KEY (`id_watchlist`) REFERENCES `watchlist` (`id_watchlist`);

--
-- Constraints for table `algo_top_asset_security`
--
ALTER TABLE `algo_top_asset_security`
  ADD CONSTRAINT `FK_AlgoTopAssetSecurity_Tenant` FOREIGN KEY (`id_tenant`) REFERENCES `tenant` (`id_tenant`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
