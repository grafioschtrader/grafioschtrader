ALTER TABLE `stockexchange` DROP COLUMN IF EXISTS `time_open`;
ALTER TABLE `stockexchange` ADD `time_open` TIME NOT NULL DEFAULT '09:00:00' AFTER `symbol`; 
ALTER TABLE `stockexchange` DROP COLUMN IF EXISTS `id_index_upd_calendar`;
ALTER TABLE `stockexchange` ADD `id_index_upd_calendar` INT NULL AFTER `time_zone`; 

ALTER TABLE `trading_days_minus` DROP COLUMN IF EXISTS `create_type`;
ALTER TABLE `trading_days_minus` ADD `create_type` TINYINT(1) NOT NULL DEFAULT '0' AFTER `trading_date_minus`; 


UPDATE stockexchange SET time_open = "09:00:00", time_close = "17:40:00" WHERE symbol='XAMS';
UPDATE stockexchange SET time_open = "07:00:00", time_close = "19:30:00" WHERE symbol='ASX';
UPDATE stockexchange SET time_open = "08:00:00", time_close = "20:00:00" WHERE symbol='BER';
UPDATE stockexchange SET time_open = "09:30:00", time_close = "17:15:00" WHERE symbol='BMFBOVES';
UPDATE stockexchange SET time_open = "08:00:00", time_close = "17:35:00" WHERE symbol='MCE';
UPDATE stockexchange SET time_open = "09:00:00", time_close = "17:00:00" WHERE symbol='OMXC';
UPDATE stockexchange SET time_open = "09:00:00", time_close = "17:30:00", NAME ='Frankfurt und Xetra' WHERE symbol='FSX';
UPDATE stockexchange SET time_open = "09:00:00", time_close = "16:00:00" WHERE symbol='HKEX'; 
UPDATE stockexchange SET time_open = "05:05:00", time_close = "17:15:00" WHERE symbol='LSE';
UPDATE stockexchange SET time_open = "08:00:00", time_close = "17:40:00" WHERE symbol='MIL';
UPDATE stockexchange SET time_open = "04:00:00", time_close = "20:00:00" WHERE symbol='NAS';
UPDATE stockexchange SET time_open = "10:00:00", time_close = "16:45:00" WHERE symbol='NZE';
UPDATE stockexchange SET time_open = "06:30:00", time_close = "20:00:00" WHERE symbol='NYSE';
UPDATE stockexchange SET time_open = "08:15:00", time_close = "17:30:00" WHERE symbol='OSE';
UPDATE stockexchange SET time_open = "09:00:00", time_close = "17:30:00" WHERE symbol='OMX';
UPDATE stockexchange SET time_open = "09:00:00", time_close = "15:00:00" WHERE symbol='JPX';
UPDATE stockexchange SET time_open = "09:30:00", time_close = "16:00:00" WHERE symbol='TSX';
UPDATE stockexchange SET time_open = "08:00:00", time_close = "17:50:00" WHERE symbol='VIE';
 
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
INSERT INTO trading_days_minus (id_stockexchange, trading_date_minus, create_type) SELECT targetIdStockexchange, trading_date_minus, create_type FROM trading_days_minus WHERE id_stockexchange = sourceIdStockexchange AND trading_date_minus >= dateFrom AND trading_date_minus <= dateTo;
END ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 DROP PROCEDURE IF EXISTS `updCalendarStockexchangeByIndex` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION' */ ;
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
  END LOOP testLoop;
  CLOSE cur;
END ;;
DELIMITER ;

INSERT INTO `task_data_change` (`id_task_data_change`, `id_task`, `execution_priority`, `entity`, `id_entity`, `earliest_start_time`, `creation_time`, `exec_start_time`, `exec_end_time`, `old_value_varchar`, `old_value_number`, `progress_state`, `failed_message_code`) VALUES (NULL, '51', '5', NULL, NULL, '2021-03-28 06:08:00', '2021-03-28 06:08:00', '', '', NULL, NULL, '0', NULL);

