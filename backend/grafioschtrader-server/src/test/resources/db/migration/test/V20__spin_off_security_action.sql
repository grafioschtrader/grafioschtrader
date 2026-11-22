ALTER TABLE security_action ADD COLUMN IF NOT EXISTS action_type TINYINT NOT NULL DEFAULT 0 AFTER id_security_action,
  ADD COLUMN IF NOT EXISTS cost_allocation DOUBLE NULL AFTER to_factor,
  ADD COLUMN IF NOT EXISTS cost_allocation_source VARCHAR(255) NULL AFTER cost_allocation,
  ADD COLUMN IF NOT EXISTS tax_treatment_de TINYINT NULL AFTER cost_allocation_source,
  ADD COLUMN IF NOT EXISTS cost_allocation_de DOUBLE NULL AFTER tax_treatment_de;
ALTER TABLE security_action_application ADD COLUMN IF NOT EXISTS cost_allocation DOUBLE NULL AFTER id_transaction_buy;
ALTER TABLE `transaction` ADD COLUMN IF NOT EXISTS security_action_role TINYINT NULL AFTER id_security_action_app;
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS provider_split_factor DOUBLE NULL AFTER cost_allocation_de,
  ADD COLUMN IF NOT EXISTS history_basis TINYINT NULL AFTER provider_split_factor,
  ADD COLUMN IF NOT EXISTS history_factor DOUBLE NULL AFTER history_basis,
  ADD COLUMN IF NOT EXISTS history_basis_origin TINYINT NULL AFTER history_factor,
  ADD COLUMN IF NOT EXISTS history_connector VARCHAR(35) NULL AFTER history_basis_origin,
  ADD COLUMN IF NOT EXISTS history_url_extend VARCHAR(254) NULL AFTER history_connector,
  ADD COLUMN IF NOT EXISTS history_basis_applied TINYINT(1) NOT NULL DEFAULT 0 AFTER history_url_extend;
UPDATE security_action SET history_basis = 0 WHERE action_type = 1 AND history_basis IS NULL;

ALTER TABLE stockexchange ADD COLUMN IF NOT EXISTS regular_close TIME NULL AFTER time_close;

UPDATE stockexchange SET regular_close = '16:00:00'
WHERE mic IN ('XNYS', 'XNAS') AND time_zone = 'America/New_York' AND time_close > '16:00:00' AND regular_close IS NULL;

UPDATE stockexchange SET regular_close = '15:30:00'
WHERE mic = 'XTKS' AND time_zone = 'Asia/Tokyo' AND regular_close IS NULL;
