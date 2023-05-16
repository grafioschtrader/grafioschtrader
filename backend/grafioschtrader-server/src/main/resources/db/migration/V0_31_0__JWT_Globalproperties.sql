DELETE FROM globalparameters WHERE property_name = 'gt.jwt.expiration.minutes';
INSERT INTO globalparameters (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.jwt.expiration.minutes', 1440, NULL, NULL, NULL, '0');

ALTER TABLE gt_net_message DROP COLUMN IF EXISTS id_source_gt_net_message;
ALTER TABLE gt_net_message ADD id_source_gt_net_message INT NULL AFTER send_recv; 