DELETE FROM globalparameters WHERE property_name = 'gt.gtnet.lastprice.delay.seconds';
INSERT INTO globalparameters(property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.gtnet.lastprice.delay.seconds', 300, 0, 'min:60,max:7200');
  
-- Add new columns to gt_net_config_entity with default SCL_OVERVIEW (1)
ALTER TABLE gt_net_config_entity DROP COLUMN IF EXISTS supplier_log;
ALTER TABLE gt_net_config_entity DROP COLUMN IF EXISTS consumer_log;
ALTER TABLE gt_net_config_entity ADD COLUMN supplier_log TINYINT NOT NULL DEFAULT 1;
ALTER TABLE gt_net_config_entity ADD COLUMN consumer_log TINYINT NOT NULL DEFAULT 1;

UPDATE gt_net_config_entity SET exchange = CASE WHEN exchange > 0 THEN 1 ELSE 0 END;

-- Remove old columns
ALTER TABLE gt_net_entity DROP COLUMN IF EXISTS enable_log;
ALTER TABLE gt_net_config_entity DROP COLUMN IF EXISTS use_detail_log;  