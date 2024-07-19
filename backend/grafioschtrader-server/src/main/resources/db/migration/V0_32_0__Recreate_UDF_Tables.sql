-- phpMyAdmin SQL Dump
-- version 5.2.0
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Jul 16, 2024 at 07:43 AM
-- Server version: 10.4.27-MariaDB
-- PHP Version: 8.1.12

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

DROP TABLE IF EXISTS `udf_data`;
DROP TABLE IF EXISTS `udf_metadata_security`;
DROP TABLE IF EXISTS `udf_metadata_general`;
DROP TABLE IF EXISTS `udf_metadata`;
-- --------------------------------------------------------

--
-- Table structure for table `udf_data`
--

CREATE TABLE `udf_data` (
  `id_user` int(11) NOT NULL,
  `entity` varchar(20) NOT NULL,
  `id_entity` int(11) NOT NULL,
  `json_values` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`json_values`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `udf_metadata`
--

CREATE TABLE `udf_metadata` (
  `id_udf_metadata` int(11) NOT NULL,
  `id_user` int(11) DEFAULT NULL,
  `udf_special_type` tinyint(3) DEFAULT NULL,
  `description` varchar(24) NOT NULL,
  `description_help` varchar(80) DEFAULT NULL,
  `udf_data_type` tinyint(2) NOT NULL,
  `field_size` varchar(20) DEFAULT NULL,
  `ui_order` smallint(2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `udf_metadata_general`
--

CREATE TABLE `udf_metadata_general` (
  `id_udf_metadata` int(11) NOT NULL,
  `entity` varchar(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `udf_metadata_security`
--

CREATE TABLE `udf_metadata_security` (
  `id_udf_metadata` int(11) NOT NULL,
  `category_types` bigint(20) NOT NULL,
  `spec_invest_instruments` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `udf_data`
--
ALTER TABLE `udf_data`
  ADD PRIMARY KEY (`entity`,`id_entity`,`id_user`) USING BTREE,
  ADD KEY `FK_udfData_user` (`id_user`);

--
-- Indexes for table `udf_metadata`
--
ALTER TABLE `udf_metadata`
  ADD PRIMARY KEY (`id_udf_metadata`),
  ADD KEY `I_UDF_Metadata_IdUser` (`id_user`);

--
-- Indexes for table `udf_metadata_general`
--
ALTER TABLE `udf_metadata_general`
  ADD PRIMARY KEY (`id_udf_metadata`);

--
-- Indexes for table `udf_metadata_security`
--
ALTER TABLE `udf_metadata_security`
  ADD PRIMARY KEY (`id_udf_metadata`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `udf_metadata`
--
ALTER TABLE `udf_metadata`
  MODIFY `id_udf_metadata` int(11) NOT NULL AUTO_INCREMENT;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `udf_data`
--
ALTER TABLE `udf_data`
  ADD CONSTRAINT `FK_udfData_user` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`);

--
-- Constraints for table `udf_metadata`
--
ALTER TABLE `udf_metadata`
  ADD CONSTRAINT `FK_udfMetadata_user` FOREIGN KEY (`id_user`) REFERENCES `user` (`id_user`);

--
-- Constraints for table `udf_metadata_general`
--
ALTER TABLE `udf_metadata_general`
  ADD CONSTRAINT `FK_MetadataGeneral_Metadata` FOREIGN KEY (`id_udf_metadata`) REFERENCES `udf_metadata` (`id_udf_metadata`) ON DELETE CASCADE;

--
-- Constraints for table `udf_metadata_security`
--
ALTER TABLE `udf_metadata_security`
  ADD CONSTRAINT `FK_MetadataSecurity_Metadata` FOREIGN KEY (`id_udf_metadata`) REFERENCES `udf_metadata` (`id_udf_metadata`) ON DELETE CASCADE;
COMMIT;


DELETE FROM user WHERE id_user = 0;

-- Adding the user with the user ID 0. Will want referential integrity to the user table, 
-- but in composite key no NULL value. Therefore, we need the user with the ID 0.
INSERT INTO `user` (`id_user`, `id_tenant`, `nickname`, `email`, `password`, `locale`, `timezone_offset`, `enabled`, `ui_show_my_property`, `security_breach_count`, `limit_request_exceed_count`, `last_role_modified_time`, `created_by`, `creation_time`, `last_modified_by`, `last_modified_time`, `version`) VALUES
(0, NULL, 'User Zero', NULL, '-- No Login user --', 'de-CH', -120, 0, 0, 1000, 1000, '2023-03-06 10:32:19', 0, '2024-07-16 05:30:54', 1, '2024-07-11 03:03:54', 1);


INSERT INTO `udf_metadata` (`id_udf_metadata`, `id_user`, `udf_special_type`, `description`, `description_help`, `udf_data_type`, `field_size`, `ui_order`) VALUES
(1, 0, 1, 'UDF_YTM', 'UDF_YTM_TOOLTIP', 1, '5,2', 20);
INSERT INTO `udf_metadata_security` (`id_udf_metadata`, `category_types`, `spec_invest_instruments`) VALUES
(1, 2, 1);


/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
