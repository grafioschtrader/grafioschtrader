-- Adds the gt.gtnet.quote.retry global parameter, which sets the additional GTNet retry budget
-- that activates once a connector's retry counter has reached its cap (gt.history.retry / gt.intra.retry).
DELETE FROM globalparameters WHERE property_name = 'gt.gtnet.quote.retry';
INSERT INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.gtnet.quote.retry', 8, 0, 'min:0,max:50');
