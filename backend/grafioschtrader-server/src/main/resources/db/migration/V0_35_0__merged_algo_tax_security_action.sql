UPDATE security SET active_from_date = "2000-01-03" WHERE active_from_date < "2000-01-03";

-- ---------------------------------------------------------------------------
-- algo_assetclass custom name
-- ---------------------------------------------------------------------------
ALTER TABLE algo_assetclass ADD COLUMN IF NOT EXISTS name VARCHAR(40) DEFAULT NULL;

-- ---------------------------------------------------------------------------
-- gtnet_config connection timeout
-- ---------------------------------------------------------------------------
ALTER TABLE gt_net_config ADD COLUMN IF NOT EXISTS connection_timeout TINYINT DEFAULT NULL;

DELETE FROM globalparameters WHERE property_name = 'g.gnet.connection.timeout';
INSERT INTO globalparameters (property_name, property_int, changed_by_system, input_rule) VALUES ('g.gnet.connection.timeout', 30, 0, 'min:5,max:40');

-- ---------------------------------------------------------------------------
-- ICTax Kursliste — tables for Swiss tax data import
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tax_country (
  id_tax_country INT AUTO_INCREMENT PRIMARY KEY,
  country_code   VARCHAR(2) NOT NULL,
  UNIQUE INDEX uq_tax_country_code (country_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tax_year (
  id_tax_year    INT AUTO_INCREMENT PRIMARY KEY,
  id_tax_country INT NOT NULL,
  tax_year       SMALLINT NOT NULL,
  UNIQUE INDEX uq_tax_country_year (id_tax_country, tax_year),
  CONSTRAINT fk_tax_year_country FOREIGN KEY (id_tax_country)
    REFERENCES tax_country (id_tax_country) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tax_upload (
  id_tax_upload  INT AUTO_INCREMENT PRIMARY KEY,
  id_tax_year    INT NOT NULL,
  file_name      VARCHAR(255) NOT NULL,
  file_path      VARCHAR(500) NOT NULL,
  upload_date    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  record_count   INT DEFAULT 0,
  CONSTRAINT fk_tax_upload_year FOREIGN KEY (id_tax_year)
    REFERENCES tax_year (id_tax_year) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ictax_security_tax_data (
  id_ictax_data    INT AUTO_INCREMENT PRIMARY KEY,
  id_tax_upload    INT NOT NULL,
  isin             VARCHAR(12) NOT NULL,
  valor_number     INT DEFAULT NULL,
  tax_value_chf    DOUBLE DEFAULT NULL,
  quotation_type   VARCHAR(10) DEFAULT NULL,
  security_group   VARCHAR(20) DEFAULT NULL,
  institution_name VARCHAR(200) DEFAULT NULL,
  country          VARCHAR(5) DEFAULT NULL,
  currency         VARCHAR(3) DEFAULT NULL,
  INDEX idx_ictax_data_isin (isin),
  CONSTRAINT fk_ictax_data_upload FOREIGN KEY (id_tax_upload)
    REFERENCES tax_upload (id_tax_upload) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ictax_payment (
  id_ictax_payment   INT AUTO_INCREMENT PRIMARY KEY,
  id_ictax_data      INT NOT NULL,
  payment_date       DATE DEFAULT NULL,
  ex_date            DATE DEFAULT NULL,
  currency           VARCHAR(3) DEFAULT NULL,
  payment_value      DOUBLE DEFAULT NULL,
  exchange_rate      DOUBLE DEFAULT NULL,
  payment_value_chf  DOUBLE DEFAULT NULL,
  capital_gain       TINYINT(1) DEFAULT 0,
  CONSTRAINT fk_ictax_payment_data FOREIGN KEY (id_ictax_data)
    REFERENCES ictax_security_tax_data (id_ictax_data) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO tax_country (country_code) VALUES ('CH');

-- Per-security tax exclusion config
CREATE TABLE IF NOT EXISTS tax_security_year_config (
  id_tenant           INT NOT NULL,
  tax_year            SMALLINT NOT NULL,
  id_securitycurrency INT NOT NULL,
  PRIMARY KEY (id_tenant, tax_year, id_securitycurrency),
  CONSTRAINT fk_tsyc_tenant FOREIGN KEY (id_tenant)
    REFERENCES tenant (id_tenant) ON DELETE CASCADE,
  CONSTRAINT fk_tsyc_security FOREIGN KEY (id_securitycurrency)
    REFERENCES securitycurrency (id_securitycurrency) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- Security Action (ISIN changes) and Security Transfer (account transfers)
-- ---------------------------------------------------------------------------

-- Drop in correct FK order for idempotency
ALTER TABLE transaction DROP FOREIGN KEY IF EXISTS fk_trans_security_action_app;
ALTER TABLE transaction DROP FOREIGN KEY IF EXISTS fk_trans_security_transfer;
DROP TABLE IF EXISTS security_action_application;
DROP TABLE IF EXISTS security_action;
DROP TABLE IF EXISTS security_transfer;

CREATE TABLE security_action (
  id_security_action INT AUTO_INCREMENT,
  id_security_old INT NOT NULL,
  id_security_new INT NULL,
  isin_old VARCHAR(12) NOT NULL,
  isin_new VARCHAR(12) NOT NULL,
  action_date DATE NOT NULL,
  note VARCHAR(1024),
  affected_count INT NOT NULL DEFAULT 0,
  applied_count INT NOT NULL DEFAULT 0,
  created_by INT NOT NULL,
  creation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_security_action),
  CONSTRAINT fk_sa_security_old FOREIGN KEY (id_security_old) REFERENCES securitycurrency(id_securitycurrency),
  CONSTRAINT fk_sa_security_new FOREIGN KEY (id_security_new) REFERENCES securitycurrency(id_securitycurrency),
  CONSTRAINT fk_sa_created_by FOREIGN KEY (created_by) REFERENCES user(id_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE TABLE security_action_application (
  id_security_action_app INT AUTO_INCREMENT,
  id_security_action INT NOT NULL,
  id_tenant INT NOT NULL,
  id_transaction_sell INT NULL,
  id_transaction_buy INT NULL,
  applied_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_reversed TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id_security_action_app),
  CONSTRAINT fk_saa_security_action FOREIGN KEY (id_security_action) REFERENCES security_action(id_security_action),
  CONSTRAINT fk_saa_tenant FOREIGN KEY (id_tenant) REFERENCES tenant(id_tenant),
  CONSTRAINT fk_saa_transaction_sell FOREIGN KEY (id_transaction_sell) REFERENCES transaction(id_transaction),
  CONSTRAINT fk_saa_transaction_buy FOREIGN KEY (id_transaction_buy) REFERENCES transaction(id_transaction),
  UNIQUE KEY uq_saa_action_tenant (id_security_action, id_tenant)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

CREATE TABLE security_transfer (
  id_security_transfer INT AUTO_INCREMENT,
  id_tenant INT NOT NULL,
  id_security INT NOT NULL,
  id_securityaccount_source INT NOT NULL,
  id_securityaccount_target INT NOT NULL,
  transfer_date DATE NOT NULL,
  units DOUBLE NOT NULL,
  quotation DOUBLE NOT NULL,
  id_transaction_sell INT NULL,
  id_transaction_buy INT NULL,
  note VARCHAR(1024),
  creation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_security_transfer),
  CONSTRAINT fk_st_tenant FOREIGN KEY (id_tenant) REFERENCES tenant(id_tenant),
  CONSTRAINT fk_st_security FOREIGN KEY (id_security) REFERENCES securitycurrency(id_securitycurrency),
  CONSTRAINT fk_st_source FOREIGN KEY (id_securityaccount_source) REFERENCES securitycashaccount(id_securitycash_account),
  CONSTRAINT fk_st_target FOREIGN KEY (id_securityaccount_target) REFERENCES securitycashaccount(id_securitycash_account),
  CONSTRAINT fk_st_transaction_sell FOREIGN KEY (id_transaction_sell) REFERENCES transaction(id_transaction),
  CONSTRAINT fk_st_transaction_buy FOREIGN KEY (id_transaction_buy) REFERENCES transaction(id_transaction)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- Add id_security_action_app column to transaction table
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS id_security_action_app INT NULL;
-- Clean up orphaned references before adding FK
UPDATE transaction SET id_security_action_app = NULL
  WHERE id_security_action_app IS NOT NULL
  AND id_security_action_app NOT IN (SELECT id_security_action_app FROM security_action_application);
ALTER TABLE transaction DROP FOREIGN KEY IF EXISTS fk_trans_security_action_app;
ALTER TABLE transaction ADD CONSTRAINT fk_trans_security_action_app
  FOREIGN KEY (id_security_action_app) REFERENCES security_action_application(id_security_action_app);

-- ---------------------------------------------------------------------------
-- Split ratio support for ISIN changes
-- ---------------------------------------------------------------------------
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS from_factor INT DEFAULT NULL;
ALTER TABLE security_action ADD COLUMN IF NOT EXISTS to_factor INT DEFAULT NULL;

-- ---------------------------------------------------------------------------
-- FK from transaction to security_transfer (mirrors id_security_action_app)
-- ---------------------------------------------------------------------------
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS id_security_transfer INT NULL;

ALTER TABLE transaction DROP FOREIGN KEY IF EXISTS fk_trans_security_transfer;
ALTER TABLE transaction ADD CONSTRAINT fk_trans_security_transfer
  FOREIGN KEY (id_security_transfer) REFERENCES security_transfer(id_security_transfer);

-- ---------------------------------------------------------------------------
-- Tenant country code and tax export settings
-- ---------------------------------------------------------------------------
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS country VARCHAR(2) DEFAULT NULL;
UPDATE tenant SET country = 'CH' WHERE currency = 'CHF' AND country IS NULL;
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS tax_export_settings JSON DEFAULT NULL;

