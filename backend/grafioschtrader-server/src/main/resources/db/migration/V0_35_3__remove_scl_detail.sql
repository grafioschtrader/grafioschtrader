-- Migrate SCL_DETAIL (2) to SCL_OVERVIEW (1) since SCL_DETAIL is no longer supported
UPDATE gt_net_config_entity SET supplier_log = 1 WHERE supplier_log = 2;
UPDATE gt_net_config_entity SET consumer_log = 1 WHERE consumer_log = 2;

-- Fix historyquote records where open/high/low are 0 but close is valid
UPDATE historyquote SET open = NULL WHERE open = 0 AND close <> 0;
UPDATE historyquote SET high = NULL WHERE high = 0 AND close <> 0;
UPDATE historyquote SET low = NULL WHERE low = 0 AND close <> 0;

-- Extend GTNet message deletion retention to include SL (SecurityLookup codes 90-95)
DELETE FROM globalparameters WHERE property_name = 'gt.gtnet.del.message.recv';
INSERT INTO globalparameters(property_name, property_string, changed_by_system, input_rule)
VALUES ('gt.gtnet.del.message.recv', 'LP=1,HP=5,SL=5', 0, 'pattern:^LP=([1-9]|10),HP=([1-9]|10),SL=([1-9]|10)$');
