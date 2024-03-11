# Remove connector ariva.de
UPDATE securitycurrency sc JOIN security s ON sc.id_securitycurrency = s.id_securitycurrency SET sc.id_connector_history = "gt.datafeed.yahoo", sc.url_history_extend = sc.url_intra_extend, sc.retry_history_load = 0 WHERE sc.id_connector_history="gt.datafeed.ariva" AND s.active_to_date > "2024-01-19" AND sc.id_connector_intra = "gt.datafeed.yahoo"; 
UPDATE securitycurrency sc JOIN security s ON sc.id_securitycurrency = s.id_securitycurrency SET sc.id_connector_history = "gt.datafeed.xetra", sc.url_history_extend = sc.url_intra_extend, sc.retry_history_load = 0 WHERE sc.id_connector_history="gt.datafeed.ariva" AND s.active_to_date > "2024-01-19" AND sc.id_connector_intra = "gt.datafeed.xetra";
UPDATE securitycurrency sc JOIN security s ON sc.id_securitycurrency = s.id_securitycurrency SET sc.id_connector_history = null, sc.url_history_extend = null, sc.retry_history_load = 4 WHERE sc.id_connector_history="gt.datafeed.ariva" AND s.active_to_date > "2024-01-19";
UPDATE securitycurrency sc JOIN security s ON sc.id_securitycurrency = s.id_securitycurrency SET sc.id_connector_history = null, sc.url_history_extend = null WHERE sc.id_connector_history="gt.datafeed.ariva";  

# Add settings for task "monitor historical price data"
DELETE FROM globalparameters WHERE property_name = 'gt.history.oberservation.days.back';
INSERT INTO globalparameters (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.history.oberservation.days.back', 60, NULL, NULL, NULL, '0');

DELETE FROM globalparameters WHERE property_name = 'gt.history.oberservation.retry.minus';
INSERT INTO globalparameters (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.history.oberservation.retry.minus', 1, NULL, NULL, NULL, '0');

DELETE FROM globalparameters WHERE property_name = 'gt.history.oberservation.falling.percentage';
INSERT INTO globalparameters (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.history.oberservation.falling.percentage', 80, NULL, NULL, NULL, '0');
