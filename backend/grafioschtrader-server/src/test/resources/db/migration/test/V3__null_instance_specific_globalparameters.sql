-- Instance-specific globalparameters values that leak from the dumped development database into the
-- generated V2__testdata.sql. nv.bat dumps the whole globalparameters table, but the rows those values
-- point at are not dumped with it, so each one references something that does not exist in
-- grafioschtrader_t. NULL is the fresh-install state of all of them.
--
-- Idempotent, repeats harmlessly. This is the only migration left that repairs the generated dump: the
-- others were deleted once nv.bat regenerated V1/V2 from a fully migrated development database, and this
-- one stays because a full globalparameters dump keeps producing these rows.

-- gt_net is dumped structure-only, so 'g.gnet.my.entry.id' points at a GTNet entry that does not exist
-- here and GTNetServerStatusCheckTask / GTNetFutureMessageDeliveryTask warn on every boot. With NULL,
-- getGTNetMyEntryID() returns null and no GTNet task is queued or executed until an own entry is
-- created through the GTNet setup UI. nv.bat writes the same statement into V2__testdata.sql, so this
-- one is a no-op on a freshly generated dump and repairs databases bootstrapped from an older V2.
UPDATE globalparameters SET property_int = NULL WHERE property_name = 'g.gnet.my.entry.id';

-- imp_trans_platform is not dumped at all - its rows are created by ImportTransactionPlatformResourceTest
-- from testdata/imptransplatform.csv and therefore carry different ids here - so the dumped
-- 'gt.import.platform.id' names a platform of the development database. GlobalparametersService
-- .getGtImportPlatformId() treats an absent value as "no platform configured", which is the correct
-- state for this database: the Grafioschtrader import templates are then simply not offered, instead of
-- being offered for a platform that is not there.
UPDATE globalparameters SET property_int = NULL WHERE property_name = 'gt.import.platform.id';
