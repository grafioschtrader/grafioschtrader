-- The import platform holding the Grafioschtrader authored import templates (receipt PDFs, transaction CSV
-- export) moves from the tenant to a global parameter.
--
-- V0_36_3 seeded exactly one platform named 'Grafioschtrader' and let every tenant point at it through
-- tenant.id_gt_import_platform. That expressed a choice which does not exist: the tenant dialog offered the
-- full list of import platforms, so any other platform was equally selectable and equally wrong. Which
-- platform carries the GT templates is a property of the instance, not of the client.
--
-- The platform id therefore becomes the global parameter 'gt.import.platform.id', set by an administrator,
-- and the tenant keeps only the opt-in flag use_gt_import_templates. As a side effect the tenant table loses
-- FK_Tenant_GtImportPlatform - its only foreign key, and the reason a personal data export could not be
-- re-imported: the export writes the tenant row before the application contributed imp_trans_platform rows.
--
-- imp_trans_head.use_gt_platform is untouched; it stays the per-import-session switch.

ALTER TABLE tenant ADD COLUMN IF NOT EXISTS use_gt_import_templates TINYINT(1) NOT NULL DEFAULT 0;

-- Carrying the old values over reads the column that is dropped further down, so a second run of this
-- migration must not attempt it. Both steps are therefore guarded by the presence of that column.
DELIMITER //
CREATE OR REPLACE PROCEDURE MigrateGtImportPlatformToGlobalparameter()
BEGIN
    IF EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'tenant'
        AND COLUMN_NAME = 'id_gt_import_platform'
    ) THEN
        -- The platform the tenants already agreed on wins; on an installation where nobody opted in, the
        -- platform seeded by V0_36_3 is taken. Both can be absent, in which case no row is written and an
        -- administrator has to pick the platform in the import template screen.
        SET @id_gt_platform = COALESCE(
            (SELECT id_gt_import_platform FROM tenant WHERE id_gt_import_platform IS NOT NULL
              GROUP BY id_gt_import_platform ORDER BY COUNT(*) DESC, id_gt_import_platform LIMIT 1),
            (SELECT MIN(id_trans_imp_platform) FROM imp_trans_platform WHERE name = 'Grafioschtrader'));

        IF @id_gt_platform IS NOT NULL THEN
            DELETE FROM globalparameters WHERE property_name = 'gt.import.platform.id';
            INSERT INTO globalparameters (property_name, property_int, changed_by_system)
              VALUES ('gt.import.platform.id', @id_gt_platform, 0);
        END IF;

        UPDATE tenant SET use_gt_import_templates = 1 WHERE id_gt_import_platform IS NOT NULL;
    END IF;
END //
DELIMITER ;
CALL MigrateGtImportPlatformToGlobalparameter();
DROP PROCEDURE IF EXISTS MigrateGtImportPlatformToGlobalparameter;

ALTER TABLE tenant DROP FOREIGN KEY IF EXISTS FK_Tenant_GtImportPlatform;
ALTER TABLE tenant DROP COLUMN IF EXISTS id_gt_import_platform;
