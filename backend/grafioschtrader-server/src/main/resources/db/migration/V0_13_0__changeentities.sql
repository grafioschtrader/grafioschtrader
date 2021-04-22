/*!50003 DROP PROCEDURE IF EXISTS `moveCreatedByUserToOtherUser` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION' */ ;
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