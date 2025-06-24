UPDATE globalparameters SET property_name = CONCAT('g.', SUBSTRING(property_name, 4)) WHERE property_name = 'gt.jwt.expiration.minutes'; 
UPDATE globalparameters SET property_name = CONCAT('g.', SUBSTRING(property_name, 4)) WHERE property_name = 'gt.limit.day.MailSendRecv';
UPDATE globalparameters SET property_name = CONCAT('g.', SUBSTRING(property_name, 4)) WHERE property_name = 'gt.max.limit.request.exceeded.count';
UPDATE globalparameters SET property_name = CONCAT('g.', SUBSTRING(property_name, 4)) WHERE property_name = 'gt.max.security.breach.count';
UPDATE globalparameters SET property_name = CONCAT('g.', SUBSTRING(property_name, 4)) WHERE property_name = 'gt.password.regex.properties';
UPDATE globalparameters SET property_name = CONCAT('g.', SUBSTRING(property_name, 4)) WHERE property_name = 'gt.task.data.days.preserve';
