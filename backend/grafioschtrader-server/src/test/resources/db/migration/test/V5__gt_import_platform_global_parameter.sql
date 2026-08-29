-- Test-database counterpart of the production migration V0_36_9. The test schema comes from the generated
-- V1__schema.sql, a dump of the development database, and the production V0_*.sql migrations never run against
-- grafioschtrader_t - so until nv.bat regenerates V1 from a migrated development database, the tenant table here
-- still carries id_gt_import_platform and lacks use_gt_import_templates. Same role as V3 and V4: repair what the
-- generated dump predates, and go no-op once it no longer does.
--
-- One part deliberately differs from the production migration. There the global parameter is written only while
-- the old column still exists, so that a repeated run cannot overwrite a platform an administrator has chosen
-- since. Here the id has to be derived from the local imp_trans_platform table on every fresh bootstrap, because
-- V3 nulls the instance-specific globalparameters values that leak in from the development dump, and because the
-- platform ids of grafioschtrader_t differ from those of the development database. Writing only when the row is
-- absent keeps that safe.

ALTER TABLE tenant ADD COLUMN IF NOT EXISTS use_gt_import_templates TINYINT(1) NOT NULL DEFAULT 0;

DELIMITER //
CREATE OR REPLACE PROCEDURE MigrateTestGtImportPlatform()
BEGIN
    IF EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'tenant'
        AND COLUMN_NAME = 'id_gt_import_platform'
    ) THEN
        UPDATE tenant SET use_gt_import_templates = 1 WHERE id_gt_import_platform IS NOT NULL;
    END IF;
END //
DELIMITER ;
CALL MigrateTestGtImportPlatform();
DROP PROCEDURE IF EXISTS MigrateTestGtImportPlatform;

ALTER TABLE tenant DROP FOREIGN KEY IF EXISTS FK_Tenant_GtImportPlatform;
ALTER TABLE tenant DROP COLUMN IF EXISTS id_gt_import_platform;

-- The platform is matched by name because its id differs between databases. Nothing is written when the platform
-- has not been created in this database yet; an administrator then picks it in the import template screen.
INSERT INTO globalparameters (property_name, property_int, changed_by_system)
SELECT 'gt.import.platform.id', MIN(id_trans_imp_platform), 0
  FROM imp_trans_platform
 WHERE name = 'Grafioschtrader'
HAVING MIN(id_trans_imp_platform) IS NOT NULL
    ON DUPLICATE KEY UPDATE property_int = COALESCE(globalparameters.property_int, VALUES(property_int));
