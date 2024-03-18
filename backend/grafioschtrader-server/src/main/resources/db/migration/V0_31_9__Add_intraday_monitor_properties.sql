# Add settings for task "monitor historical price data"
# Fix type in oberservation => observation
DELETE FROM globalparameters WHERE property_name = 'gt.history.oberservation.days.back';
DELETE FROM globalparameters WHERE property_name = 'gt.history.observation.days.back';
INSERT INTO globalparameters (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.history.observation.days.back', 60, NULL, NULL, NULL, '0');

DELETE FROM globalparameters WHERE property_name = 'gt.history.oberservation.retry.minus';
DELETE FROM globalparameters WHERE property_name = 'gt.history.observation.retry.minus';
INSERT INTO globalparameters (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.history.observation.retry.minus', 1, NULL, NULL, NULL, '0');

DELETE FROM globalparameters WHERE property_name = 'gt.history.oberservation.falling.percentage';
DELETE FROM globalparameters WHERE property_name = 'gt.history.observation.falling.percentage';
INSERT INTO globalparameters (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.history.observation.falling.percentage', 80, NULL, NULL, NULL, '0');

# Add settings for task "monitor intraday price data"
DELETE FROM globalparameters WHERE property_name = 'gt.intraday.oberservation.or.days.back';
DELETE FROM globalparameters WHERE property_name = 'gt.intraday.observation.or.days.back';
INSERT INTO globalparameters (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.intraday.observation.or.days.back', 60, NULL, NULL, NULL, '0');

DELETE FROM globalparameters WHERE property_name = 'gt.intraday.oberservation.retry.minus';
DELETE FROM globalparameters WHERE property_name = 'gt.intraday.observation.retry.minus';
INSERT INTO globalparameters (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.intraday.observation.retry.minus', 0, NULL, NULL, NULL, '0');

DELETE FROM globalparameters WHERE property_name = 'gt.intraday.oberservation.falling.percentage';
DELETE FROM globalparameters WHERE property_name = 'gt.intraday.observation.falling.percentage';
INSERT INTO globalparameters (`property_name`, `property_int`, `property_string`, `property_date`, `property_blob`, `changed_by_system`) VALUES ('gt.intraday.observation.falling.percentage', 80, NULL, NULL, NULL, '0');
