-- phpMyAdmin SQL Dump
-- version 5.2.0
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Jul 29, 2024 at 03:00 PM
-- Server version: 10.4.27-MariaDB
-- PHP Version: 8.1.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = 'SYSTEM';


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `grafioschtrader`
--

-- --------------------------------------------------------

--
-- Table structure for table `mic_provider_map`
--

DROP TABLE IF EXISTS `mic_provider_map`;
CREATE TABLE `mic_provider_map` (
  `id_provider` varchar(15) NOT NULL,
  `mic` char(4) NOT NULL,
  `code_provider` varchar(5) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `mic_provider_map`
--

INSERT INTO `mic_provider_map` (`id_provider`, `mic`, `code_provider`) VALUES
('yahoo', '4AXE', '4AX'),
('yahoo', 'A2XX', 'A2X'),
('yahoo', 'AQEU', 'AQE'),
('yahoo', 'AQXE', 'AQX'),
('yahoo', 'ARCX', 'PSE'),
('yahoo', 'BATE', 'BTE'),
('yahoo', 'BATS', 'BAT'),
('yahoo', 'BATY', 'BTY'),
('yahoo', 'BIVA', 'BIV'),
('yahoo', 'BJSE', 'BJS'),
('yahoo', 'BOTC', 'BCO'),
('yahoo', 'BVCA', 'CCS'),
('yahoo', 'BVMF', 'SAO'),
('yahoo', 'CBSX', 'WCB'),
('yahoo', 'CEUX', 'DXE'),
('yahoo', 'CHIA', 'CHA'),
('yahoo', 'CHIC', 'CXC'),
('yahoo', 'CHIJ', 'CHJ'),
('yahoo', 'CHIX', 'CHI'),
('yahoo', 'DGCX', 'DGX'),
('yahoo', 'DIFX', 'DIX'),
('yahoo', 'DSMD', 'DSM'),
('yahoo', 'EDGA', 'DEA'),
('yahoo', 'EDGX', 'DEX'),
('yahoo', 'ENXL', 'EML'),
('yahoo', 'EPRL', 'MPG'),
('yahoo', 'EQTB', 'EQD'),
('yahoo', 'ETLX', 'ETX'),
('yahoo', 'EUWX', 'EWX'),
('yahoo', 'HMOD', 'HMT'),
('yahoo', 'HSTC', 'HNX'),
('yahoo', 'IEXG', 'IEG'),
('yahoo', 'IPSX', 'IPX'),
('yahoo', 'LMAD', 'LMX'),
('yahoo', 'LTSE', 'LTE'),
('yahoo', 'LYNX', 'LNX'),
('yahoo', 'MCXX', 'MCD'),
('yahoo', 'MEMX', 'XMG'),
('yahoo', 'MISX', 'MCX'),
('yahoo', 'MLXB', 'EMB'),
('yahoo', 'MSAX', 'MAA'),
('yahoo', 'MTAA', 'MIL'),
('yahoo', 'NEOD', 'NED'),
('yahoo', 'NEOE', 'NLB'),
('yahoo', 'NEON', 'NEO'),
('yahoo', 'NEXG', 'NXX'),
('yahoo', 'NOTC', 'NFF'),
('yahoo', 'NSME', 'NGM'),
('yahoo', 'OMGA', 'OMG'),
('yahoo', 'OTCM', 'PNK'),
('yahoo', 'PFTS', 'PFT'),
('yahoo', 'PURE', 'PTX'),
('yahoo', 'ROCO', 'TWO'),
('yahoo', 'ROFX', 'RFX'),
('yahoo', 'ROTC', 'RSE'),
('yahoo', 'RUSX', 'RTB'),
('yahoo', 'SBIJ', 'JNX'),
('yahoo', 'SEPE', 'SEP'),
('yahoo', 'SGMU', 'SIU'),
('yahoo', 'SGMX', 'SIG'),
('yahoo', 'SPBE', 'SBX'),
('yahoo', 'TFEX', 'TFX'),
('yahoo', 'TMXS', 'TMX'),
('yahoo', 'TNLK', 'EBT'),
('yahoo', 'TQEX', 'TQE'),
('yahoo', 'TRQX', 'TRQ'),
('yahoo', 'UKEX', 'UAX'),
('yahoo', 'VFEX', 'VFX'),
('yahoo', 'WBAH', 'VIE'),
('yahoo', 'XADF', 'ADS'),
('yahoo', 'XADS', 'ABD'),
('yahoo', 'XAMM', 'AMM'),
('yahoo', 'XAMS', 'AEX'),
('yahoo', 'XARM', 'ARM'),
('yahoo', 'XASE', 'ASE'),
('yahoo', 'XASX', 'ASX'),
('yahoo', 'XATH', 'ATH'),
('yahoo', 'XATS', 'ALP'),
('yahoo', 'XBAB', 'BRB'),
('yahoo', 'XBAH', 'BAH'),
('yahoo', 'XBAR', 'BAR'),
('yahoo', 'XBCL', 'BEC'),
('yahoo', 'XBEL', 'BEL'),
('yahoo', 'XBER', 'BER'),
('yahoo', 'XBEY', 'BDB'),
('yahoo', 'XBKK', 'SET'),
('yahoo', 'XBLB', 'BNL'),
('yahoo', 'XBNV', 'CRI'),
('yahoo', 'XBOG', 'COL'),
('yahoo', 'XBOL', 'BBV'),
('yahoo', 'XBOM', 'BSE'),
('yahoo', 'XBOS', 'BOS'),
('yahoo', 'XBOT', 'BSM'),
('yahoo', 'XBRA', 'BRA'),
('yahoo', 'XBRN', 'BRN'),
('yahoo', 'XBRU', 'BRU'),
('yahoo', 'XBRV', 'ABJ'),
('yahoo', 'XBSE', 'BUH'),
('yahoo', 'XBUD', 'BUD'),
('yahoo', 'XBUE', 'BUE'),
('yahoo', 'XCAI', 'CAI'),
('yahoo', 'XCAS', 'CAS'),
('yahoo', 'XCAY', 'CSX'),
('yahoo', 'XCBO', 'CBO'),
('yahoo', 'XCHI', 'MID'),
('yahoo', 'XCIE', 'CIE'),
('yahoo', 'XCIS', 'CIN'),
('yahoo', 'XCNQ', 'CNX'),
('yahoo', 'XCOL', 'CSE'),
('yahoo', 'XCSE', 'CPH'),
('yahoo', 'XCX2', 'CXX'),
('yahoo', 'XCYS', 'CYS'),
('yahoo', 'XDAR', 'DSS'),
('yahoo', 'XDFM', 'DBX'),
('yahoo', 'XDHA', 'DSE'),
('yahoo', 'XDSE', 'DSX'),
('yahoo', 'XDUB', 'ISE'),
('yahoo', 'XDUS', 'DUS'),
('yahoo', 'XEMD', 'MDE'),
('yahoo', 'XEQY', 'IST'),
('yahoo', 'XETR', 'GER'),
('yahoo', 'XEUR', 'EUX'),
('yahoo', 'XFKA', 'FKA'),
('yahoo', 'XFRA', 'FRA'),
('yahoo', 'XGAT', 'TDG'),
('yahoo', 'XGHA', 'GSE'),
('yahoo', 'XGUA', 'GYQ'),
('yahoo', 'XHAM', 'HAM'),
('yahoo', 'XHAN', 'HAN'),
('yahoo', 'XHEL', 'HEX'),
('yahoo', 'XHKF', 'HFE'),
('yahoo', 'XHKG', 'HKG'),
('yahoo', 'XHNX', 'UPC'),
('yahoo', 'XICE', 'ICX'),
('yahoo', 'XIDX', 'JKT'),
('yahoo', 'XIQS', 'ISX'),
('yahoo', 'XIST', 'ISF'),
('yahoo', 'XISX', 'ISS'),
('yahoo', 'XJAM', 'JAM'),
('yahoo', 'XJAS', 'JSD'),
('yahoo', 'XJSE', 'JNB'),
('yahoo', 'XKAR', 'PSX'),
('yahoo', 'XKAZ', 'KAZ'),
('yahoo', 'XKLS', 'KLS'),
('yahoo', 'XKON', 'KNX'),
('yahoo', 'XKOS', 'KOE'),
('yahoo', 'XKRX', 'KSC'),
('yahoo', 'XKUW', 'KUW'),
('yahoo', 'XLAO', 'LSX'),
('yahoo', 'XLAT', 'LAT'),
('yahoo', 'XLIM', 'LMA'),
('yahoo', 'XLIS', 'LIS'),
('yahoo', 'XLIT', 'VLX'),
('yahoo', 'XLJU', 'LJU'),
('yahoo', 'XLME', 'LME'),
('yahoo', 'XLON', 'LSE'),
('yahoo', 'XLUS', 'LUS'),
('yahoo', 'XLUX', 'LUX'),
('yahoo', 'XMAB', 'MAE'),
('yahoo', 'XMAD', 'MCE'),
('yahoo', 'XMAE', 'MKE'),
('yahoo', 'XMAL', 'MLT'),
('yahoo', 'XMAU', 'MAU'),
('yahoo', 'XMEX', 'MEX'),
('yahoo', 'XMLI', 'EMA'),
('yahoo', 'XMNX', 'MOT'),
('yahoo', 'XMSW', 'MLS'),
('yahoo', 'XMUN', 'MUN'),
('yahoo', 'XMUS', 'MUS'),
('yahoo', 'XNAI', 'NAI'),
('yahoo', 'XNAM', 'NSE'),
('yahoo', 'XNAS', 'NGM'),
('yahoo', 'XNCM', 'NAS'),
('yahoo', 'XNEC', 'NSX'),
('yahoo', 'XNGO', 'NGO'),
('yahoo', 'XNGS', 'NSM'),
('yahoo', 'XNIM', 'THM'),
('yahoo', 'XNMS', 'NMS'),
('yahoo', 'XNSA', 'LAG'),
('yahoo', 'XNSE', 'NSI'),
('yahoo', 'XNYS', 'NYQ'),
('yahoo', 'XNZE', 'NZC'),
('yahoo', 'XOCH', 'ONE'),
('yahoo', 'XOSE', 'OSA'),
('yahoo', 'XOSL', 'OSL'),
('yahoo', 'XOTC', 'OBB'),
('yahoo', 'XPAE', 'PLS'),
('yahoo', 'XPAR', 'PAR'),
('yahoo', 'XPHS', 'PHS'),
('yahoo', 'XPIC', 'SPC'),
('yahoo', 'XPRA', 'PRA'),
('yahoo', 'XPSX', 'XPH'),
('yahoo', 'XPTY', 'PAN'),
('yahoo', 'XQMH', 'QMH'),
('yahoo', 'XQUI', 'QTO'),
('yahoo', 'XRIS', 'RIX'),
('yahoo', 'XSAP', 'SAP'),
('yahoo', 'XSAT', 'AKT'),
('yahoo', 'XSAU', 'SAU'),
('yahoo', 'XSES', 'SES'),
('yahoo', 'XSGO', 'SGO'),
('yahoo', 'XSHE', 'SHZ'),
('yahoo', 'XSHG', 'SHH'),
('yahoo', 'XSSE', 'SRJ'),
('yahoo', 'XSTC', 'HSX'),
('yahoo', 'XSTO', 'STO'),
('yahoo', 'XSTU', 'STU'),
('yahoo', 'XSWX', 'SWX'),
('yahoo', 'XTAE', 'TLV'),
('yahoo', 'XTAI', 'TAI'),
('yahoo', 'XTAL', 'TLX'),
('yahoo', 'XTKS', 'TYO'),
('yahoo', 'XTNX', 'NEX'),
('yahoo', 'XTRN', 'TTS'),
('yahoo', 'XTSE', 'TOR'),
('yahoo', 'XTSX', 'CVE'),
('yahoo', 'XTUN', 'TUN'),
('yahoo', 'XUGA', 'UGS'),
('yahoo', 'XULA', 'MGS'),
('yahoo', 'XVAL', 'VAL'),
('yahoo', 'XVTX', 'VTX'),
('yahoo', 'XWAR', 'WSE'),
('yahoo', 'XXXX', 'NYQ'),
('yahoo', 'XZAG', 'ZAG'),
('yahoo', 'XZIM', 'ZSE'),
('yahoo', 'ZARX', 'ZAX'),
('yahoo', 'ZBUL', 'BLG');


-- --------------------------------------------------------

--
-- Table structure for table `udf_special_type_disable_user`
--

DROP TABLE IF EXISTS `udf_special_type_disable_user`;
CREATE TABLE `udf_special_type_disable_user` (
  `id_user` int(11) NOT NULL,
  `udf_special_type` tinyint(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `mic_provider_map`
--
ALTER TABLE `mic_provider_map`
  ADD PRIMARY KEY (`id_provider`,`mic`) USING BTREE;

--
-- Indexes for table `udf_special_type_disable_user`
--
ALTER TABLE `udf_special_type_disable_user`
  ADD PRIMARY KEY (`id_user`,`udf_special_type`);

--
-- Constraints for dumped tables
--

--
-- Constraints for table `udf_special_type_disable_user`
--
ALTER TABLE `udf_special_type_disable_user`
  ADD CONSTRAINT `FK_UdfSpecialTypeDisableUser_user` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`);
COMMIT;


DELETE u FROM udf_metadata u WHERE u.udf_special_type = 2;
INSERT INTO `udf_metadata` (`id_user`, `udf_special_type`, `description`, `description_help`, `udf_data_type`, `field_size`, `ui_order`) VALUES (0, 2, 'UDF_YAHOO_EARNING_LINK', 'UDF_YAHOO_EARNING_TOOLTIP', 20, NULL, 80);
INSERT INTO `udf_metadata_security` (`id_udf_metadata`, `category_types`, `spec_invest_instruments`) VALUES
(LAST_INSERT_ID(), 1, 17);

DELETE u FROM udf_metadata u WHERE u.udf_special_type = 3;
INSERT INTO `udf_metadata` (`id_user`, `udf_special_type`, `description`, `description_help`, `udf_data_type`, `field_size`, `ui_order`) VALUES (0, 3, 'UDF_YAHOO_EARNING_N_DATE', 'UDF_YAHOO_EARNING_N_TOOLTIP', 8, NULL, 60);
INSERT INTO `udf_metadata_security` (`id_udf_metadata`, `category_types`, `spec_invest_instruments`) VALUES
(LAST_INSERT_ID(), 1, 17);

DELETE u FROM udf_metadata u WHERE u.udf_special_type = 4;
INSERT INTO `udf_metadata` (`id_user`, `udf_special_type`, `description`, `description_help`, `udf_data_type`, `field_size`, `ui_order`) VALUES (0, 4, 'UDF_YAHOO_SYMBOL', 'UDF_YAHOO_SYMBOL_TOOLTIP', 7, "1,10", 101);
INSERT INTO `udf_metadata_security` (`id_udf_metadata`, `category_types`, `spec_invest_instruments`) VALUES
(LAST_INSERT_ID(), 1, 17);

DELETE u FROM udf_metadata u WHERE u.udf_special_type = 5;
INSERT INTO `udf_metadata` (`id_user`, `udf_special_type`, `description`, `description_help`, `udf_data_type`, `field_size`, `ui_order`) VALUES (0, 5, 'UDF_YAHOO_STAT_LINK', 'UDF_YAHOO_STAT_LINK_TOOLTIP', 20, NULL, 50);
INSERT INTO `udf_metadata_security` (`id_udf_metadata`, `category_types`, `spec_invest_instruments`) VALUES
(LAST_INSERT_ID(), 1, 17);

DELETE FROM udf_data WHERE id_user = 0;
# Fill general user defined fields with Yahoo content for user 0 with background task
INSERT INTO `task_data_change` (`id_task_data_change`, `id_task`, `execution_priority`, `entity`, `id_entity`, `earliest_start_time`, `creation_time`, `exec_start_time`, `exec_end_time`, `old_value_varchar`, `old_value_number`, `progress_state`, `failed_message_code`) VALUES (NULL, '19', '20', NULL, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), NULL, NULL, NULL, NULL, '0', NULL);


/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
