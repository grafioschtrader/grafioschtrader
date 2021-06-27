DROP INDEX IF EXISTS Unique_idTransaction ON imp_trans_pos; 
ALTER TABLE `grafioschtrader`.`imp_trans_pos` ADD UNIQUE `Unique_idTransaction` (`id_transaction`);

ALTER TABLE `imp_trans_pos` DROP CONSTRAINT IF EXISTS transacton_maybe;
ALTER TABLE `imp_trans_pos` DROP COLUMN IF EXISTS `id_transaction_maybe`;
ALTER TABLE `imp_trans_pos` ADD `id_transaction_maybe` INT NULL AFTER `id_transaction`; 
ALTER TABLE `imp_trans_pos`ADD CONSTRAINT transacton_maybe CHECK(id_transaction_maybe IS NULL OR id_transaction_maybe IS NOT NULL AND id_transaction IS NULL);

ALTER TABLE `grafioschtrader`.`imp_trans_head` DROP INDEX `FK_ImpTransHead_Tenant`, 
ADD UNIQUE `UNIQUE_ImpTransHead` (`id_tenant`, `id_securitycash_account`, `name`) USING BTREE;  
 
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


DROP TABLE IF EXISTS `gt_network_exchange`;
DROP TABLE IF EXISTS `gt_network`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_network` (
  `id_gt_network` int(11) NOT NULL AUTO_INCREMENT,
  `domain_remote_name` varchar(128) NOT NULL,
  `allow_give_away` tinyint(1) NOT NULL DEFAULT 0,
  `accept_request` tinyint(1) NOT NULL DEFAULT 0,
  `daily_req_limit` int(11) DEFAULT NULL,
  `daily_req_limit_count` int(11) DEFAULT NULL,
  `daily_req_limit_remote` int(11) DEFAULT NULL,
  `daily_req_limit_remote_count` int(11) DEFAULT NULL,
  PRIMARY KEY (`id_gt_network`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;


LOCK TABLES `gt_network` WRITE;
/*!40000 ALTER TABLE `gt_network` DISABLE KEYS */;
/*!40000 ALTER TABLE `gt_network` ENABLE KEYS */;
UNLOCK TABLES;



/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gt_network_exchange` (
  `id_gt_network_exchange` int(11) NOT NULL AUTO_INCREMENT,
  `id_gt_network` int(11) NOT NULL,
  `in_out` tinyint(1) NOT NULL,
  `entity` varchar(40) NOT NULL,
  `id_entity` int(11) NOT NULL,
  `id_entity_remote` int(11) NOT NULL,
  `send_msg_code` tinyint(3) DEFAULT NULL,
  `send_msg_timestamp` timestamp NULL DEFAULT NULL,
  `recv_msg_code` tinyint(3) DEFAULT NULL,
  `recv_msg_timestamp` timestamp NULL DEFAULT NULL,
  `request_entity_timestamp` timestamp NULL DEFAULT NULL,
  `give_entity_timestamp` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id_gt_network_exchange`),
  KEY `FK_GTNetwork_GTNetworkExchange` (`id_gt_network`),
  CONSTRAINT `FK_GTNetwork_GTNetworkExchange` FOREIGN KEY (`id_gt_network`) REFERENCES `gt_network` (`id_gt_network`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;



LOCK TABLES `gt_network_exchange` WRITE;
/*!40000 ALTER TABLE `gt_network_exchange` DISABLE KEYS */;
/*!40000 ALTER TABLE `gt_network_exchange` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;