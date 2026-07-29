-- The generated baseline V0_10_0__init.sql seeded 'g.gnet.my.entry.id' with the GTNet entry id of
-- the machine the dump was taken from, while the gt_net tables are dumped structure-only. In
-- grafiosch_t that id therefore points at a row that does not exist, and the GTNet background tasks
-- warn on every boot. NULL is the fresh-install state: getGTNetMyEntryID() returns null and no GTNet
-- task is queued or executed until an own entry is created through the GTNet setup UI.
-- Idempotent, repeats harmlessly.
UPDATE globalparameters SET property_int = NULL WHERE property_name = 'g.gnet.my.entry.id';
