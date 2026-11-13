-- Cost basis method: decides which purchases a sale of a security consumes. The tenant holds the default (0 = average
-- cost, 1 = FIFO, 2 = LIFO), a portfolio may override it and follows the tenant while its value is NULL. Nothing is
-- stored per transaction, the reports calculate every result from the transactions when they are requested.
-- TINYINT without a display width, because the JDBC driver returns TINYINT(1) as Boolean.
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS cost_basis_method TINYINT NOT NULL DEFAULT 0;
ALTER TABLE portfolio ADD COLUMN IF NOT EXISTS cost_basis_method TINYINT NULL DEFAULT NULL;

-- German tax help. The class of an investment fund (0 = equity fund, 1 = mixed fund, 2 = real estate fund, 3 = foreign
-- real estate fund, 4 = other fund, NULL = not classified) decides the partial exemption of its income. A portfolio
-- states whether a domestic paying agent withholds the tax (0 = domestic withholding, 1 = none, NULL = not assigned
-- yet, which is the state of every existing portfolio). The base interest rate in percent of a tax year feeds the
-- advance lump sum of the funds. TINYINT without a display width, because the JDBC driver returns TINYINT(1) as Boolean.
ALTER TABLE security ADD COLUMN IF NOT EXISTS fund_tax_class TINYINT NULL DEFAULT NULL;
ALTER TABLE portfolio ADD COLUMN IF NOT EXISTS tax_withholding TINYINT NULL DEFAULT NULL;
ALTER TABLE tax_year ADD COLUMN IF NOT EXISTS fund_base_rate DOUBLE NULL DEFAULT NULL;
INSERT IGNORE INTO tax_country (country_code) VALUES ('DE');
-- Base interest rates of the Federal Ministry of Finance. A rate an administrator has already entered stays.
INSERT INTO tax_year (id_tax_country, tax_year, fund_base_rate)
  SELECT c.id_tax_country, r.tax_year, r.fund_base_rate FROM tax_country c
    JOIN (SELECT 2018 AS tax_year, 0.87 AS fund_base_rate UNION ALL SELECT 2019, 0.52 UNION ALL SELECT 2020, 0.07
      UNION ALL SELECT 2021, -0.45 UNION ALL SELECT 2022, -0.05 UNION ALL SELECT 2023, 2.55
      UNION ALL SELECT 2024, 2.29 UNION ALL SELECT 2025, 2.53 UNION ALL SELECT 2026, 3.20) r
    WHERE c.country_code = 'DE'
  ON DUPLICATE KEY UPDATE fund_base_rate = COALESCE(tax_year.fund_base_rate, VALUES(fund_base_rate));

-- Visibility of the nickname. 0 = a user sees only the id of another user (the creator of shared data, the sender of a
-- change request), 1 = every user sees the nickname. The administrator of an instance decides; the default keeps the
-- nickname hidden. INSERT IGNORE keeps a value the administrator has already set.
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('g.user.nickname.visible', 0, 0, 'enum:0,1');

-- Sharing of user-defined fields. share_mode 0 = not shared, 1 = other users may read the values, 2 = other users may
-- read and write them. A shared field keeps one value per entity in udf_data_shared instead of the udf_data rows of
-- its owner. A user adopts a shared field with a row in udf_metadata_subscription and chooses the own ui_order there.
-- udf_metadata.id_user NULL marks a shared field whose owner deleted it while other users still use it.
-- TINYINT without a display width, because the JDBC driver returns TINYINT(1) as Boolean.
ALTER TABLE udf_metadata ADD COLUMN IF NOT EXISTS share_mode TINYINT NOT NULL DEFAULT 0
  COMMENT 'UDFShareMode: 0 not shared, 1 read, 2 read and write';

CREATE TABLE IF NOT EXISTS udf_metadata_subscription (
  id_udf_metadata_subscription int(11) NOT NULL AUTO_INCREMENT,
  id_udf_metadata int(11) NOT NULL,
  id_user int(11) NOT NULL,
  ui_order smallint(2) NOT NULL,
  PRIMARY KEY (id_udf_metadata_subscription),
  UNIQUE KEY UK_UdfMetadataSubscription_Field_User (id_udf_metadata, id_user),
  KEY FK_UdfMetadataSubscription_User (id_user),
  CONSTRAINT FK_UdfMetadataSubscription_Metadata FOREIGN KEY (id_udf_metadata)
    REFERENCES udf_metadata (id_udf_metadata) ON DELETE CASCADE,
  CONSTRAINT FK_UdfMetadataSubscription_User FOREIGN KEY (id_user) REFERENCES user (id_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS udf_data_shared (
  id_udf_metadata int(11) NOT NULL,
  id_entity int(11) NOT NULL,
  entity varchar(20) NOT NULL COMMENT 'Entity name as in udf_data.entity',
  field_value text NOT NULL COMMENT 'Canonical text of the value, true or false for a boolean',
  PRIMARY KEY (id_udf_metadata, id_entity),
  KEY I_UdfDataShared_Entity (entity, id_entity),
  CONSTRAINT FK_UdfDataShared_Metadata FOREIGN KEY (id_udf_metadata)
    REFERENCES udf_metadata (id_udf_metadata) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT IGNORE INTO entity_limit
  (limit_type, entity_name, relation_entity_name, count_scope, owner_scope, limit_value, created_by, last_modified_by, version)
SELECT 0, x.entity_name, x.relation_entity_name, x.count_scope, x.owner_scope, x.default_value, 0, 0, 0
FROM (SELECT 'UDFMetadataSubscription' AS entity_name, CAST(NULL AS CHAR(40)) AS relation_entity_name,
             CAST(NULL AS SIGNED) AS count_scope, 2 AS owner_scope,
             CAST(NULL AS CHAR) AS property_name, 50 AS default_value) x;

INSERT IGNORE INTO entity_limit
  (limit_type, entity_name, limit_value, created_by, last_modified_by, version)
SELECT 1, 'UDFMetadataSubscription', 20, 0, 0, 0;

-- SECTION-BEGIN fundamental-shares
-- Outstanding shares are a stock at a date, not the diluted average used for fiscal-year EPS. Eulerpool's
-- other.shares can be that average (e.g. Adobe and Booking). Keep it as SHARES_FY (10), never as SHARES (6).
-- No schema change is needed: SEC (source 4) uses period_end as the measurement date of the stock.
INSERT INTO security_fundamental
  (id_securitycurrency, metric, source, value, currency, period_end, fetched_time, source_check, state)
SELECT id_securitycurrency, 10, source, value, currency, period_end, fetched_time, 0, 0
FROM security_fundamental WHERE metric = 6 AND source = 3
ON DUPLICATE KEY UPDATE value = VALUES(value), currency = VALUES(currency), period_end = VALUES(period_end),
  fetched_time = VALUES(fetched_time), source_check = 0, state = 0;
DELETE FROM security_fundamental WHERE metric = 6 AND source = 3;

-- A previously contradicted Yahoo stock becomes usable again if its own share-basis check passed. The read query
-- still enforces freshness and splits; the next load selects the dated SEC stock when it is available.
UPDATE security_fundamental SET state = CASE WHEN source_check = 1 THEN 2 ELSE 0 END
WHERE metric = 6 AND source = 1
  AND NOT EXISTS (SELECT 1 FROM (SELECT id_securitycurrency FROM security_fundamental
    WHERE metric = 6 AND source = 4 AND state IN (1, 2, 3)) sec
    WHERE sec.id_securitycurrency = security_fundamental.id_securitycurrency);

-- Refresh the existing universe after the update, rather than waiting for Saturday. A configured empty source list
-- still disables every request. Re-running the migration does not enqueue another whole-universe pending load.
INSERT INTO task_data_change (id_task, execution_priority, entity, id_entity, earliest_start_time, creation_time,
  exec_start_time, exec_end_time, old_value_varchar, old_value_number, progress_state, failed_message_code)
SELECT 56, 30, NULL, NULL, UTC_TIMESTAMP() + INTERVAL 5 MINUTE, UTC_TIMESTAMP(), NULL, NULL, NULL, NULL, 0, NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM task_data_change WHERE id_task = 56 AND id_entity IS NULL AND progress_state IN (0, 4));
-- SECTION-END fundamental-shares
