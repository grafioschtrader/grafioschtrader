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
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `role`
--

LOCK TABLES `role` WRITE;
/*!40000 ALTER TABLE `role` DISABLE KEYS */;
INSERT INTO `role` (`id_role`, `rolename`) VALUES (5,'ROLE_ADMIN'),(6,'ROLE_ALLEDIT'),(7,'ROLE_USER'),(8,'ROLE_LIMITEDIT');
/*!40000 ALTER TABLE `role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `globalparameters`
--

LOCK TABLES `globalparameters` WRITE;
/*!40000 ALTER TABLE `globalparameters` DISABLE KEYS */;
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('g.jwt.expiration.minutes',1440,NULL,NULL,NULL,0),('g.limit.day.MailSendRecv',200,NULL,NULL,NULL,0),('g.max.limit.request.exceeded.count',20,NULL,NULL,NULL,0),('g.max.security.breach.count',5,NULL,NULL,NULL,0),('g.password.regex.properties',NULL,NULL,NULL,'de=Das Passwort besteht aus mindestens acht Zeichen mit mindestens einem Buchstaben und einer Zahl.\nregex=^(?\\=.*[A-Za-z])(?\\=.*\\\\d)[A-Za-z\\\\d]{8,}$\nforceRegex=false\nen=The password has at least eight characters with at least one letter and one number.\n\n\n\n\n',0),('g.task.data.days.preserve',10,NULL,NULL,NULL,0),('gt.core.data.feed.start.date',NULL,NULL,'2000-01-01',NULL,0),('gt.cryptocurrency.history.connector',NULL,'gt.datafeed.cryptocompare',NULL,NULL,0),('gt.cryptocurrency.intra.connector',NULL,'gt.datafeed.cryptocompare',NULL,NULL,0),('gt.currency.history.connector',NULL,'gt.datafeed.eodhistoricaldata',NULL,NULL,0),('gt.currency.intra.connector',NULL,'gt.datafeed.yahoo',NULL,NULL,0),('gt.currency.precision',NULL,'BTC=8,ETH=7,JPY=0,KWD=3',NULL,NULL,0),('gt.dividend.retry',2,NULL,NULL,NULL,0),('gt.gtnet.my.entry.id',10,NULL,NULL,NULL,1),('gt.history.max.filldays.currency',5,NULL,NULL,NULL,0),('gt.history.observation.days.back',60,NULL,NULL,NULL,0),('gt.history.observation.falling.percentage',80,NULL,NULL,NULL,0),('gt.history.observation.retry.minus',1,NULL,NULL,NULL,0),('gt.history.retry',4,NULL,NULL,NULL,0),('gt.historyquote.quality.update.date',NULL,NULL,'2025-11-04',NULL,1),('gt.intra.retry',4,NULL,NULL,NULL,0),('gt.intraday.observation.falling.percentage',80,NULL,NULL,NULL,0),('gt.intraday.observation.or.days.back',60,NULL,NULL,NULL,0),('gt.intraday.observation.retry.minus',0,NULL,NULL,NULL,0),('gt.limit.day.Assetclass',2,NULL,NULL,NULL,0),('gt.limit.day.Stockexchange',10,NULL,NULL,NULL,0),('gt.max.cash.account',25,NULL,NULL,NULL,0),('gt.max.correlation.instruments',20,NULL,NULL,NULL,0),('gt.max.correlation.set',10,NULL,NULL,NULL,0),('gt.max.instrument.historyquote.periods',20,NULL,NULL,NULL,0),('gt.max.instrument.splits',20,NULL,NULL,NULL,0),('gt.max.portfolio',20,NULL,NULL,NULL,0),('gt.max.securities.currencies',1000,NULL,NULL,NULL,0),('gt.max.transaction',5000,NULL,NULL,NULL,0),('gt.max.watchlist',30,NULL,NULL,NULL,0),('gt.max.watchlist.length',200,NULL,NULL,NULL,0),('gt.sc.intra.update.timeout.seconds',60,NULL,NULL,NULL,0),('gt.securitydividend.append.date',NULL,NULL,'2025-11-04',NULL,1),('gt.securitysplit.append.date',NULL,NULL,'2025-11-04',NULL,1),('gt.source.demo.idtenant.de',22,NULL,NULL,NULL,0),('gt.source.demo.idtenant.en',29,NULL,NULL,NULL,0),('gt.split.retry',2,NULL,NULL,NULL,0),('gt.udf.general.recreate',0,NULL,NULL,NULL,1),('gt.update.price.by.exchange',0,NULL,NULL,NULL,0),('gt.w.intra.update.timeout.seconds',900,NULL,NULL,NULL,0);
/*!40000 ALTER TABLE `globalparameters` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-11-04  9:39:16
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
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `udf_metadata`
--
-- WHERE:  id_user=0

LOCK TABLES `udf_metadata` WRITE;
/*!40000 ALTER TABLE `udf_metadata` DISABLE KEYS */;
INSERT INTO `udf_metadata` (`id_udf_metadata`, `id_user`, `udf_special_type`, `description`, `description_help`, `udf_data_type`, `field_size`, `ui_order`) VALUES (1,0,1,'UDF_YTM','UDF_YTM_TOOLTIP',1,'5,2',20),(243,0,2,'UDF_YAHOO_EARNING_LINK','UDF_YAHOO_EARNING_TOOLTIP',20,NULL,80),(244,0,3,'UDF_YAHOO_EARNING_N_DATE','UDF_YAHOO_EARNING_N_TOOLTIP',8,NULL,60),(245,0,4,'UDF_YAHOO_SYMBOL','UDF_YAHOO_SYMBOL_TOOLTIP',7,'1,10',101),(246,0,5,'UDF_YAHOO_STAT_LINK','UDF_YAHOO_STAT_LINK_TOOLTIP',20,NULL,50);
/*!40000 ALTER TABLE `udf_metadata` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-11-04  9:39:16
