DELETE FROM globalparameters WHERE property_name = 'gt.task.data.days.preserve';
INSERT INTO `globalparameters` (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.task.data.days.preserve', '10', NULL, NULL, NULL, '0'); 

ALTER TABLE `task_data_change` CHANGE `creation_time` `creation_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP; 
DELETE FROM task_data_change WHERE exec_end_time < DATE_SUB(NOW(), INTERVAL 30 DAY);


ALTER TABLE `task_data_change` CHANGE `failed_message_code` `failed_message_code` VARCHAR(40) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL; 

UPDATE task_data_change SET id_task = 31 WHERE id_task = 13;
UPDATE task_data_change SET entity="Security" WHERE id_task IN (1, 2, 5, 8);
UPDATE task_data_change SET entity = CONCAT(UCASE(LEFT(entity, 1)), SUBSTRING(entity, 2));


UPDATE task_data_change SET execution_priority = 40 WHERE execution_priority >= 35;
UPDATE task_data_change SET execution_priority = 30 WHERE execution_priority BETWEEN 30 AND 34;
UPDATE task_data_change SET execution_priority = 20 WHERE execution_priority BETWEEN 15 AND 29;
UPDATE task_data_change SET execution_priority = 10 WHERE execution_priority BETWEEN 6 AND 14;
UPDATE task_data_change SET execution_priority = 5 WHERE execution_priority <= 5;

DROP TABLE IF EXISTS ex_security_currencypair; 