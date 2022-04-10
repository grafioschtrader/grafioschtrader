ALTER TABLE `security` CHANGE IF EXISTS `short_security` `leverage_factor` FLOAT(2,1) NOT NULL DEFAULT '1.0'; 
# Thus, only simple inverse instruments are correctly mapped. Leveraged ones must be corrected manually.
UPDATE security SET leverage_factor = -1 WHERE leverage_factor = 1;
UPDATE security SET leverage_factor = 1 WHERE leverage_factor = 0;