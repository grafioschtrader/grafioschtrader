-- ============================================================================
-- Idempotent DDL Script for gt_net_security_imp_pos
-- MariaDB 10.3+ compatible
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Stored Procedure: Add Foreign Key if it doesn't exist
-- ----------------------------------------------------------------------------
DELIMITER //

DROP PROCEDURE IF EXISTS sp_add_foreign_key_if_not_exists//

CREATE PROCEDURE sp_add_foreign_key_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_constraint_name VARCHAR(64),
    IN p_fk_definition VARCHAR(500)
)
BEGIN
    DECLARE v_constraint_exists INT DEFAULT 0;
    
    SELECT COUNT(*) INTO v_constraint_exists
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND CONSTRAINT_NAME = p_constraint_name
      AND CONSTRAINT_TYPE = 'FOREIGN KEY';
    
    IF v_constraint_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' ADD CONSTRAINT ', 
                          p_constraint_name, ' ', p_fk_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

-- ----------------------------------------------------------------------------
-- Add columns to gt_net_security_imp_pos for tracking historical price import metadata
-- These columns track which GTNet peer provided metadata/historyquotes and the date range of imported data
-- ----------------------------------------------------------------------------

-- Add column for tracking which GTNet peer provided the security metadata
ALTER TABLE gt_net_security_imp_pos
    ADD COLUMN IF NOT EXISTS id_gt_net_metadata INT DEFAULT NULL;

-- Add column for tracking which GTNet peer provided historical price data
ALTER TABLE gt_net_security_imp_pos
    ADD COLUMN IF NOT EXISTS id_gt_net_historyquote INT DEFAULT NULL;

-- Add column for earliest historical date available from GTNet peer
ALTER TABLE gt_net_security_imp_pos
    ADD COLUMN IF NOT EXISTS historyquote_min_date DATE DEFAULT NULL;

-- Add column for most recent historical date from GTNet peer at import time
ALTER TABLE gt_net_security_imp_pos
    ADD COLUMN IF NOT EXISTS historyquote_max_date DATE DEFAULT NULL;

-- Add column for historyquote import status:
-- 0=PENDING, 1=GTNET_LOADED, 2=CONNECTOR_LOADED, 3=FAILED
ALTER TABLE gt_net_security_imp_pos
    ADD COLUMN IF NOT EXISTS historyquote_status TINYINT(1) NOT NULL DEFAULT 0;

-- ----------------------------------------------------------------------------
-- Add foreign key constraints for the new GTNet reference columns
-- ----------------------------------------------------------------------------

CALL sp_add_foreign_key_if_not_exists(
    'gt_net_security_imp_pos',
    'fk_gt_net_sec_imp_pos_metadata',
    'FOREIGN KEY (id_gt_net_metadata) REFERENCES gt_net(id_gt_net)'
);

CALL sp_add_foreign_key_if_not_exists(
    'gt_net_security_imp_pos',
    'fk_gt_net_sec_imp_pos_historyquote',
    'FOREIGN KEY (id_gt_net_historyquote) REFERENCES gt_net(id_gt_net)'
);

-- ----------------------------------------------------------------------------
-- Cleanup: Drop the helper procedure (optional - remove if you want to keep it)
-- ----------------------------------------------------------------------------
-- DROP PROCEDURE IF EXISTS sp_add_foreign_key_if_not_exists;