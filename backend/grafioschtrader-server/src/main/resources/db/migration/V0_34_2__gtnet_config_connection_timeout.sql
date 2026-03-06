ALTER TABLE gt_net_config ADD COLUMN IF NOT EXISTS connection_timeout TINYINT DEFAULT NULL;

DELETE FROM globalparameters WHERE property_name = 'g.gnet.connection.timeout';
INSERT INTO globalparameters (property_name, property_int, changed_by_system, input_rule) VALUES ('g.gnet.connection.timeout', 30, 0, 'min:5,max:40');
