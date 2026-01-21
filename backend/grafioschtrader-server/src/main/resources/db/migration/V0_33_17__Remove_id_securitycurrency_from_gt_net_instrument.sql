-- Remove id_securitycurrency column from gt_net_instrument table
-- Locality (whether a matching local security/currencypair exists) is now determined dynamically
-- by JOIN to the security/currencypair tables using ISIN+currency or fromCurrency+toCurrency.
-- This allows the instrument pool to remain independent of local database state changes.

-- First, drop the foreign key constraint if it exists
-- Note: This constraint may not exist on all installations depending on when they were created

-- Check and drop foreign key constraint
SET @fk_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'gt_net_instrument'
    AND CONSTRAINT_NAME = 'FK_GtNetInstrument_Securitycurrency');

SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE gt_net_instrument DROP FOREIGN KEY FK_GtNetInstrument_Securitycurrency',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check and drop index if it exists
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'gt_net_instrument'
    AND INDEX_NAME = 'FK_GtNetInstrument_Securitycurrency');

SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE gt_net_instrument DROP INDEX FK_GtNetInstrument_Securitycurrency',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Drop the column
ALTER TABLE gt_net_instrument DROP COLUMN id_securitycurrency;
