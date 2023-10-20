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
-- Tabellenstruktur für Tabelle `udf_data`
--

DROP TABLE IF EXISTS `udf_data`;
CREATE TABLE `udf_data` (
  `id_user` int(11) NOT NULL,
  `entity` varchar(20) NOT NULL,
  `id_entity` int(11) NOT NULL,
  `json_values` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`json_values`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `udf_metadata`
--

DROP TABLE IF EXISTS `udf_metadata`;
CREATE TABLE `udf_metadata` (
  `id_udf_metadata` int(11) NOT NULL,
  `id_user` int(11) DEFAULT NULL,
  `description` varchar(24) NOT NULL,
  `description_help` varchar(80) DEFAULT NULL,
  `udf_data_type` tinyint(2) NOT NULL,
  `field_size` varchar(5) NOT NULL,
  `ui_order` smallint(2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `udf_metadata_general`
--

DROP TABLE IF EXISTS `udf_metadata_general`;
CREATE TABLE `udf_metadata_general` (
  `id_udf_metadata` int(11) NOT NULL,
  `entity` varchar(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `udf_metadata_security`
--

DROP TABLE IF EXISTS `udf_metadata_security`;
CREATE TABLE `udf_metadata_security` (
  `id_udf_metadata` int(11) NOT NULL,
  `category_type` smallint(6) DEFAULT NULL,
  `spec_invest_instrument` smallint(6) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Indizes der exportierten Tabellen
--

--
-- Indizes für die Tabelle `udf_data`
--
ALTER TABLE `udf_data`
  ADD PRIMARY KEY (`entity`,`id_entity`,`id_user`) USING BTREE;

--
-- Indizes für die Tabelle `udf_metadata`
--
ALTER TABLE `udf_metadata`
  ADD PRIMARY KEY (`id_udf_metadata`),
  ADD KEY `I_UDF_Metadata_IdUser` (`id_user`);

--
-- Indizes für die Tabelle `udf_metadata_general`
--
ALTER TABLE `udf_metadata_general`
  ADD PRIMARY KEY (`id_udf_metadata`);

--
-- Indizes für die Tabelle `udf_metadata_security`
--
ALTER TABLE `udf_metadata_security`
  ADD PRIMARY KEY (`id_udf_metadata`);

--
-- AUTO_INCREMENT für exportierte Tabellen
--

--
-- AUTO_INCREMENT für Tabelle `udf_metadata`
--
ALTER TABLE `udf_metadata`
  MODIFY `id_udf_metadata` int(11) NOT NULL AUTO_INCREMENT;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
