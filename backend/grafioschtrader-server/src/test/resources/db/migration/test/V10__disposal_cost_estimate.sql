-- Mirrors the disposal cost estimate switch of V0_38_1 (GitHub issue #258). Off, as in production.
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.disposal.cost.estimate', 0, 0, 'enum:0,1');
