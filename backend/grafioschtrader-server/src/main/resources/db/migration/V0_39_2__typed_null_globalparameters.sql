-- Instance-specific ids in globalparameters are reset to NULL whenever a database is built from the dump of another
-- instance: g.gnet.my.entry.id names the own GTNet entry, gt.import.platform.id the import platform of the
-- Grafioschtrader templates. A row whose value columns are all NULL has no type, so the global settings dialog offered
-- a text field and the save was refused. The input rule min:1 types both rows as integers again - min, max and enum
-- constrain numbers only - and rejects 0 and negative ids. A rule set by hand is left alone.
UPDATE globalparameters SET input_rule = 'min:1'
  WHERE property_name IN ('g.gnet.my.entry.id', 'gt.import.platform.id') AND input_rule IS NULL;
