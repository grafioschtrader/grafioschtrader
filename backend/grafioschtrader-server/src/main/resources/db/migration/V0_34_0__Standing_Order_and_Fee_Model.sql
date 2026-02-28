-- This script does little but looks complicated because it is idempotent and also supports older MariaDB versions.
-- =============================================================================
-- V0_33_19: Algo Phase 1 - Schema changes for strategy config, simulation,
--           alarm extensions, execution state, event log, recommendations
--           (Idempotent for MariaDB)
-- =============================================================================

ALTER TABLE connector_apikey MODIFY COLUMN subscription_type SMALLINT NULL DEFAULT NULL;

-- ---------------------------------------------------------------------------
-- Helper: idempotent ADD FOREIGN KEY via procedure
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_fk_if_not_exists;
DELIMITER $$
CREATE PROCEDURE add_fk_if_not_exists(
  IN p_table      VARCHAR(64),
  IN p_fk_name    VARCHAR(64),
  IN p_fk_def     VARCHAR(1000)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = p_table
      AND CONSTRAINT_NAME = p_fk_name
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
  ) THEN
    SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD CONSTRAINT `', p_fk_name, '` ', p_fk_def);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------------
-- 0. Pre-cleanup: drop FKs on transaction that reference standing_order
--    BEFORE we drop standing_order tables (required on re-runs)
-- ---------------------------------------------------------------------------
ALTER TABLE transaction DROP FOREIGN KEY IF EXISTS fk_txn_standing_order;
DROP INDEX IF EXISTS idx_txn_standing_order ON transaction;

-- ---------------------------------------------------------------------------
-- 1. Drop legacy algo_rule tables and column (once)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS algo_rule_param2;
DROP TABLE IF EXISTS algo_rule;
ALTER TABLE algo_top DROP COLUMN IF EXISTS rule_or_strategy;

-- ---------------------------------------------------------------------------
-- 3.1 Add JSON config column to algo_strategy
-- ---------------------------------------------------------------------------
ALTER TABLE algo_strategy
  ADD COLUMN IF NOT EXISTS strategy_config JSON DEFAULT NULL;

-- ---------------------------------------------------------------------------
-- 3.7 Extend algo_top with simulation date range
-- ---------------------------------------------------------------------------
ALTER TABLE algo_top
  ADD COLUMN IF NOT EXISTS simulation_start_date DATE DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS simulation_end_date DATE DEFAULT NULL;

-- ---------------------------------------------------------------------------
-- 3.6 Extend tenant for simulation parent reference
-- ---------------------------------------------------------------------------
ALTER TABLE tenant
  ADD COLUMN IF NOT EXISTS id_parent_tenant INT DEFAULT NULL;

ALTER TABLE `algo_security` DROP FOREIGN KEY `FK_AlgoSecurity_AlgoAssetClass`; 
ALTER TABLE `algo_security` ADD CONSTRAINT `FK_AlgoSecurity_AlgoAssetClass` FOREIGN KEY (`id_algo_security_parent`) REFERENCES `algo_assetclass`(`id_algo_assetclass_security`) ON DELETE CASCADE ON UPDATE RESTRICT;

-- ---------------------------------------------------------------------------
-- 6.2 Extend algo_message_alert with alarm type, details, recommendation link
-- ---------------------------------------------------------------------------
ALTER TABLE algo_message_alert
  ADD COLUMN IF NOT EXISTS alarm_type TINYINT DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS alarm_details JSON DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS id_algo_recommendation INT DEFAULT NULL;

-- ---------------------------------------------------------------------------
-- 3.2 Per-asset runtime state for simulation execution
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS algo_execution_state (
  id_algo_execution_state INT NOT NULL AUTO_INCREMENT,
  id_algo_top             INT NOT NULL,
  id_securitycurrency     INT NOT NULL,
  id_tenant               INT NOT NULL,
  position_qty            DOUBLE DEFAULT 0,
  position_direction      TINYINT DEFAULT 1,
  avg_cost                DOUBLE DEFAULT 0,
  initial_entry_price     DOUBLE DEFAULT NULL,
  initial_entry_qty       DOUBLE DEFAULT NULL,
  adds_done               INT DEFAULT 0,
  last_buy_date           DATE DEFAULT NULL,
  last_sell_date          DATE DEFAULT NULL,
  tranche_state           JSON DEFAULT NULL,
  state_data              JSON DEFAULT NULL,
  updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_algo_execution_state)
);

-- Unique key: drop-then-add to allow column changes on re-runs
DROP INDEX IF EXISTS UK_ExecState ON algo_execution_state;
ALTER TABLE algo_execution_state
  ADD UNIQUE KEY UK_ExecState (id_algo_top, id_securitycurrency, id_tenant);

-- ---------------------------------------------------------------------------
-- 3.3 Action/event tracking for simulation and alarm audit trail
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS algo_event_log (
  id_algo_event       INT NOT NULL AUTO_INCREMENT,
  id_algo_top         INT NOT NULL,
  id_securitycurrency INT DEFAULT NULL,
  id_tenant           INT NOT NULL,
  event_type          VARCHAR(50) NOT NULL,
  event_date          DATE NOT NULL,
  details             JSON DEFAULT NULL,
  created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_algo_event)
);

-- ---------------------------------------------------------------------------
-- 3.4 Trading signals / rebalancing recommendations
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS algo_recommendation (
  id_algo_recommendation    INT NOT NULL AUTO_INCREMENT,
  id_tenant                 INT NOT NULL,
  id_algo_assetclass_security INT NOT NULL,
  id_securitycurrency       INT NOT NULL,
  recommendation_type       TINYINT NOT NULL,
  recommended_units         DOUBLE DEFAULT NULL,
  recommended_price         DOUBLE DEFAULT NULL,
  rationale                 VARCHAR(500) DEFAULT NULL,
  created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  executed_at               TIMESTAMP NULL DEFAULT NULL,
  id_transaction            INT DEFAULT NULL,
  PRIMARY KEY (id_algo_recommendation)
);

-- ---------------------------------------------------------------------------
-- 4.1 Add activatable flag to algo_security for standalone alert toggling
-- ---------------------------------------------------------------------------
ALTER TABLE algo_security
  ADD COLUMN IF NOT EXISTS activatable TINYINT(1) NOT NULL DEFAULT 1;

-- ---------------------------------------------------------------------------
-- 4.2 Add activatable flag to algo_strategy for per-strategy alert toggling
-- ---------------------------------------------------------------------------
ALTER TABLE algo_strategy
  ADD COLUMN IF NOT EXISTS activatable TINYINT(1) NOT NULL DEFAULT 1;

-- ---------------------------------------------------------------------------
-- 3.5 Simulation result metrics
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS algo_simulation_result (
  id_simulation_result INT NOT NULL AUTO_INCREMENT,
  id_tenant            INT NOT NULL,
  id_algo_top          INT NOT NULL,
  total_return         DOUBLE DEFAULT NULL,
  annualized_return    DOUBLE DEFAULT NULL,
  max_drawdown         DOUBLE DEFAULT NULL,
  sharpe_ratio         DOUBLE DEFAULT NULL,
  total_trades         INT DEFAULT NULL,
  winning_trades       INT DEFAULT NULL,
  losing_trades        INT DEFAULT NULL,
  calculated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_simulation_result)
);

-- ---------------------------------------------------------------------------
-- UC7: Simulation Environment
-- ---------------------------------------------------------------------------

-- 1. Persist UC6 reference date on algo_top for transaction cutoff
ALTER TABLE algo_top
  ADD COLUMN IF NOT EXISTS reference_date DATE DEFAULT NULL;

-- 2. Link simulation tenant to its shared AlgoTop
ALTER TABLE tenant
  ADD COLUMN IF NOT EXISTS id_algo_top INT DEFAULT NULL;

-- 3. Global parameter for max simulation environments per tenant
DELETE FROM globalparameters WHERE property_name = 'gt.max.simulation.environments';
INSERT INTO globalparameters (property_name, property_int)
  VALUES ('gt.max.simulation.environments', 5);

-- ---------------------------------------------------------------------------
-- Standing order (recurring transaction) support
-- JOINED inheritance hierarchy:
--   Base:  standing_order              (common scheduling + discriminator)
--   Child: standing_order_cashaccount  (WITHDRAWAL / DEPOSIT)
--   Child: standing_order_security     (ACCUMULATE / REDUCE)
-- ---------------------------------------------------------------------------

-- Drop children first (FK ordering), then parent
DROP TABLE IF EXISTS standing_order_failure;
DROP TABLE IF EXISTS standing_order_security;
DROP TABLE IF EXISTS standing_order_cashaccount;
DROP TABLE IF EXISTS standing_order;

-- Base table
CREATE TABLE standing_order (
  id_standing_order    INT AUTO_INCREMENT PRIMARY KEY,
  dtype                VARCHAR(1)   NOT NULL,
  id_tenant            INT          NOT NULL,
  transaction_type     TINYINT      NOT NULL,
  id_cash_account      INT          NOT NULL,
  note                 VARCHAR(500) DEFAULT NULL,
  repeat_unit          TINYINT      NOT NULL,
  repeat_interval      SMALLINT     NOT NULL DEFAULT 1,
  day_of_execution     TINYINT      DEFAULT NULL,
  month_of_execution   TINYINT      DEFAULT NULL,
  period_day_position  TINYINT      NOT NULL DEFAULT 0,
  weekend_adjust       TINYINT      NOT NULL DEFAULT 0,
  valid_from           DATE         NOT NULL,
  valid_to             DATE         NOT NULL,
  last_execution_date  DATE         DEFAULT NULL,
  next_execution_date  DATE         DEFAULT NULL,
  transaction_cost     DOUBLE       DEFAULT NULL,
  CONSTRAINT fk_so_tenant FOREIGN KEY (id_tenant)
    REFERENCES tenant(id_tenant),
  CONSTRAINT fk_so_cashaccount FOREIGN KEY (id_cash_account)
    REFERENCES securitycashaccount(id_securitycash_account)
);

-- Child: cash-account standing orders (WITHDRAWAL=0, DEPOSIT=1)
CREATE TABLE standing_order_cashaccount (
  id_standing_order    INT NOT NULL PRIMARY KEY,
  cashaccount_amount   DOUBLE NOT NULL,
  CONSTRAINT fk_soc_parent FOREIGN KEY (id_standing_order)
    REFERENCES standing_order(id_standing_order) ON DELETE CASCADE
);

-- Child: security standing orders (ACCUMULATE=4, REDUCE=5)
CREATE TABLE standing_order_security (
  id_standing_order        INT          NOT NULL PRIMARY KEY,
  id_securitycurrency      INT          NOT NULL,
  id_security_account      INT          NOT NULL,
  id_currency_pair         INT          DEFAULT NULL,
  units                    DOUBLE       DEFAULT NULL,
  invest_amount            DOUBLE       DEFAULT NULL,
  amount_includes_costs    TINYINT      NOT NULL DEFAULT 0,
  fractional_units         TINYINT      NOT NULL DEFAULT 1,
  tax_cost_formula         VARCHAR(200) DEFAULT NULL,
  transaction_cost_formula VARCHAR(200) DEFAULT NULL,
  tax_cost                 DOUBLE       DEFAULT NULL,
  CONSTRAINT fk_sos_parent FOREIGN KEY (id_standing_order)
    REFERENCES standing_order(id_standing_order) ON DELETE CASCADE,
  CONSTRAINT fk_sos_security FOREIGN KEY (id_securitycurrency)
    REFERENCES security(id_securitycurrency),
  CONSTRAINT fk_sos_securityaccount FOREIGN KEY (id_security_account)
    REFERENCES securitycashaccount(id_securitycash_account),
  CONSTRAINT fk_sos_currencypair FOREIGN KEY (id_currency_pair)
    REFERENCES currencypair(id_securitycurrency)
);

-- ---------------------------------------------------------------------------
-- Add traceability column + FK + index on transaction
-- (FK was already dropped at the top of this script)
-- ---------------------------------------------------------------------------
ALTER TABLE transaction
  ADD COLUMN IF NOT EXISTS id_standing_order INT DEFAULT NULL;

-- Clear orphaned references after standing_order tables were recreated (idempotent re-run)
UPDATE transaction SET id_standing_order = NULL
WHERE id_standing_order IS NOT NULL
  AND id_standing_order NOT IN (SELECT id_standing_order FROM standing_order);

CALL add_fk_if_not_exists(
  'transaction',
  'fk_txn_standing_order',
  'FOREIGN KEY (id_standing_order) REFERENCES standing_order(id_standing_order)'
);

CREATE INDEX IF NOT EXISTS idx_txn_standing_order
  ON transaction (id_standing_order);

-- ---------------------------------------------------------------------------
-- Tenant-level limit for maximum standing orders
-- ---------------------------------------------------------------------------
DELETE FROM globalparameters WHERE property_name = 'gt.max.standing.order';
INSERT INTO globalparameters (property_name, property_int, changed_by_system)
  VALUES ('gt.max.standing.order', 50, 0);

-- ---------------------------------------------------------------------------
-- Borrowing rate for overdraft control on cash accounts
-- ---------------------------------------------------------------------------
ALTER TABLE cashaccount
  ADD COLUMN IF NOT EXISTS borrowing_rate DOUBLE DEFAULT NULL;

-- ---------------------------------------------------------------------------
-- Standing order execution failure log
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS standing_order_failure;
CREATE TABLE standing_order_failure (
  id_standing_order_failure  INT AUTO_INCREMENT PRIMARY KEY,
  id_standing_order          INT            NOT NULL,
  execution_date             DATE           NOT NULL,
  business_error             VARCHAR(2000)  DEFAULT NULL,
  unexpected_error           VARCHAR(4096)  DEFAULT NULL,
  created_at                 TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CALL add_fk_if_not_exists('standing_order_failure','fk_sof_standing_order',
  'FOREIGN KEY (id_standing_order) REFERENCES standing_order(id_standing_order) ON DELETE CASCADE');
DROP INDEX IF EXISTS idx_sof_standing_order ON standing_order_failure;
ALTER TABLE standing_order_failure ADD INDEX idx_sof_standing_order (id_standing_order);

-- ---------------------------------------------------------------------------
-- Securityaccount trading period (replaces 6 hardcoded *_use_until columns)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS securityaccount_trading_period;
CREATE TABLE securityaccount_trading_period (
  id_secaccount_trading_period INT AUTO_INCREMENT PRIMARY KEY,
  id_securitycash_account      INT      NOT NULL,
  category_type                TINYINT  DEFAULT NULL,
  spec_invest_instrument       TINYINT  NOT NULL,
  date_from                    DATE     NOT NULL DEFAULT '2000-01-01',
  date_to                      DATE     DEFAULT NULL
);

DROP INDEX IF EXISTS idx_stp_secaccount ON securityaccount_trading_period;
ALTER TABLE securityaccount_trading_period
  ADD INDEX idx_stp_secaccount (id_securitycash_account);

CALL add_fk_if_not_exists(
  'securityaccount_trading_period',
  'fk_stp_securitycash_account',
  'FOREIGN KEY (id_securitycash_account) REFERENCES securitycashaccount(id_securitycash_account) ON DELETE CASCADE'
);



-- Migrate from existing transactions ------------------------------------------
INSERT INTO securityaccount_trading_period
  (id_securitycash_account, category_type, spec_invest_instrument, date_from, date_to)
SELECT DISTINCT t.id_security_account, ac.category_type, ac.spec_invest_instrument, '2000-01-01', NULL
FROM transaction t
JOIN security s ON t.id_securitycurrency = s.id_securitycurrency
JOIN assetclass ac ON s.id_asset_class = ac.id_asset_class
WHERE t.id_security_account IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM securityaccount_trading_period stp
    WHERE stp.id_securitycash_account = t.id_security_account
      AND (stp.category_type IS NULL OR stp.category_type = ac.category_type)
      AND stp.spec_invest_instrument = ac.spec_invest_instrument
  );

-- Safety net: update any NULL date_from values (e.g. from older migration runs)
UPDATE securityaccount_trading_period SET date_from = '2000-01-01' WHERE date_from IS NULL;

-- Drop old columns ------------------------------------------------------------
ALTER TABLE securityaccount DROP COLUMN IF EXISTS share_use_until;
ALTER TABLE securityaccount DROP COLUMN IF EXISTS bond_use_until;
ALTER TABLE securityaccount DROP COLUMN IF EXISTS etf_use_until;
ALTER TABLE securityaccount DROP COLUMN IF EXISTS fond_use_until;
ALTER TABLE securityaccount DROP COLUMN IF EXISTS forex_use_until;
ALTER TABLE securityaccount DROP COLUMN IF EXISTS cfd_use_until;

-- ---------------------------------------------------------------------------
-- EvalEx-based fee model on trading_platform_plan
-- Temporarily relax sql_mode: the existing table has last_modified_time with
-- DEFAULT '0000-00-00 00:00:00' which blocks any ALTER TABLE when NO_ZERO_DATE
-- is active.  After fixing the default we restore the original mode.
-- ---------------------------------------------------------------------------
SET @old_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = REPLACE(REPLACE(@@SESSION.sql_mode, 'NO_ZERO_DATE', ''), 'STRICT_TRANS_TABLES', '');
ALTER TABLE trading_platform_plan
  MODIFY COLUMN last_modified_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
SET SESSION sql_mode = @old_sql_mode;
ALTER TABLE trading_platform_plan ADD COLUMN IF NOT EXISTS fee_model_yaml TEXT DEFAULT NULL;

-- ---------------------------------------------------------------------------
-- Extend multilinguestrings.text to varchar(2000) for connector descriptions
-- ---------------------------------------------------------------------------
ALTER TABLE multilinguestrings MODIFY COLUMN text VARCHAR(2000) NOT NULL;

-- ---------------------------------------------------------------------------
-- Generic Configurable Feed Connector tables
-- ---------------------------------------------------------------------------

-- Drop in reverse FK order for idempotent re-runs
DROP TABLE IF EXISTS generic_connector_field_mapping;
DROP TABLE IF EXISTS generic_connector_http_header;
DROP TABLE IF EXISTS generic_connector_endpoint;
DROP TABLE IF EXISTS generic_connector_def;

-- Main connector definition with Auditable columns + MultilanguageString
CREATE TABLE generic_connector_def (
  id_generic_connector    INT(11) NOT NULL AUTO_INCREMENT,
  short_id                VARCHAR(32) NOT NULL,
  readable_name           VARCHAR(100) NOT NULL,
  domain_url              VARCHAR(255) NOT NULL,
  needs_api_key           TINYINT(1) NOT NULL DEFAULT 0,
  rate_limit_type         TINYINT(3) NOT NULL DEFAULT 0,
  rate_limit_requests     SMALLINT(6) DEFAULT NULL,
  rate_limit_period_sec   SMALLINT(6) DEFAULT NULL,
  rate_limit_concurrent   SMALLINT(6) DEFAULT NULL,
  intraday_delay_seconds  INT(11) NOT NULL DEFAULT 900,
  regex_url_pattern       VARCHAR(255) DEFAULT NULL,
  supports_security       TINYINT(1) NOT NULL DEFAULT 1,
  supports_currency       TINYINT(1) NOT NULL DEFAULT 0,
  need_history_gap_filler TINYINT(1) NOT NULL DEFAULT 0,
  gbx_divider_enabled     TINYINT(1) NOT NULL DEFAULT 0,
  description_nls         INT(11) DEFAULT NULL,
  activated               TINYINT(1) NOT NULL DEFAULT 0,
  created_by              INT(11) DEFAULT NULL,
  creation_time           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_modified_by        INT(11) DEFAULT NULL,
  last_modified_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version                 INT(11) DEFAULT 0,
  PRIMARY KEY (id_generic_connector),
  UNIQUE KEY uk_generic_connector_short_id (short_id),
  CONSTRAINT FK_GenericConnector_DescNLS FOREIGN KEY (description_nls)
    REFERENCES multilinguestring (id)
);

-- URL template and parsing config per feed type + instrument type
CREATE TABLE generic_connector_endpoint (
  id_endpoint             INT(11) NOT NULL AUTO_INCREMENT,
  id_generic_connector    INT(11) NOT NULL,
  feed_support            VARCHAR(10) NOT NULL,
  instrument_type         VARCHAR(10) NOT NULL,
  url_template            VARCHAR(1000) NOT NULL,
  http_method             VARCHAR(6) NOT NULL DEFAULT 'GET',
  response_format         TINYINT(3) NOT NULL,
  number_format           TINYINT(3) NOT NULL DEFAULT 4,
  date_format_type        TINYINT(3) NOT NULL DEFAULT 4,
  date_format_pattern     VARCHAR(64) DEFAULT NULL,
  json_data_structure     TINYINT(3) DEFAULT NULL,
  json_data_path          VARCHAR(255) DEFAULT NULL,
  json_status_path        VARCHAR(128) DEFAULT NULL,
  json_status_ok_value    VARCHAR(64) DEFAULT NULL,
  csv_delimiter           VARCHAR(4) DEFAULT NULL,
  csv_skip_header_lines   TINYINT(3) DEFAULT 1,
  html_css_selector       VARCHAR(255) DEFAULT NULL,
  html_extract_mode       TINYINT(3) DEFAULT NULL,
  html_text_cleanup       VARCHAR(255) DEFAULT NULL,
  html_extract_regex      VARCHAR(512) DEFAULT NULL,
  html_split_delimiter    VARCHAR(16) DEFAULT NULL,
  ticker_build_strategy   TINYINT(3) NOT NULL DEFAULT 1,
  currency_pair_separator VARCHAR(4) DEFAULT NULL,
  currency_pair_suffix    VARCHAR(20) DEFAULT NULL,
  ticker_uppercase        TINYINT(1) NOT NULL DEFAULT 1,
  max_data_points         INT(11) DEFAULT NULL,
  pagination_enabled      TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id_endpoint),
  UNIQUE KEY uk_endpoint_feed (id_generic_connector, feed_support, instrument_type),
  CONSTRAINT FK_Endpoint_GenericConnector FOREIGN KEY (id_generic_connector)
    REFERENCES generic_connector_def (id_generic_connector) ON DELETE CASCADE
);

-- Field mappings from response fields to target entity fields
CREATE TABLE generic_connector_field_mapping (
  id_field_mapping        INT(11) NOT NULL AUTO_INCREMENT,
  id_endpoint             INT(11) NOT NULL,
  target_field            VARCHAR(20) NOT NULL,
  source_expression       VARCHAR(255) NOT NULL,
  csv_column_index        SMALLINT(6) DEFAULT NULL,
  divider_expression      VARCHAR(64) DEFAULT NULL,
  is_required             TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id_field_mapping),
  CONSTRAINT FK_FieldMapping_Endpoint FOREIGN KEY (id_endpoint)
    REFERENCES generic_connector_endpoint (id_endpoint) ON DELETE CASCADE
);

-- Custom HTTP headers per connector
CREATE TABLE generic_connector_http_header (
  id_http_header          INT(11) NOT NULL AUTO_INCREMENT,
  id_generic_connector    INT(11) NOT NULL,
  header_name             VARCHAR(64) NOT NULL,
  header_value            VARCHAR(512) NOT NULL,
  PRIMARY KEY (id_http_header),
  CONSTRAINT FK_HttpHeader_GenericConnector FOREIGN KEY (id_generic_connector)
    REFERENCES generic_connector_def (id_generic_connector) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Generic connector endpoint: bitmask processing options
-- ---------------------------------------------------------------------------
ALTER TABLE generic_connector_endpoint ADD COLUMN IF NOT EXISTS endpoint_options BIGINT DEFAULT NULL;

-- ---------------------------------------------------------------------------
-- Generic connector: YAML-based automatic token acquisition config
-- When NOT NULL, the connector auto-acquires and refreshes JWT tokens
-- instead of requiring a static API key from connector_apikey.
-- ---------------------------------------------------------------------------
ALTER TABLE generic_connector_def ADD COLUMN IF NOT EXISTS token_config_yaml TEXT DEFAULT NULL;

-- ---------------------------------------------------------------------------
-- Per-security-account fee model override (YAML)
-- ---------------------------------------------------------------------------
ALTER TABLE securityaccount ADD COLUMN IF NOT EXISTS fee_model_yaml TEXT DEFAULT NULL;

-- ---------------------------------------------------------------------------
-- Cleanup helper procedure
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_fk_if_not_exists;

-- =============================================================================
-- Gettex (Munich) Generic Connector Configuration
-- Uses LSEG Widget API (lseg-widgets.financial.com) for price data.
--
-- Auto-token: The connector automatically acquires and refreshes JWT tokens
-- via the SAML SSO flow on gettex.de. No manual API key needed.
--
-- ISIN-to-RIC resolution is manual: set urlHistoryExtend / urlIntraExtend
-- on each security to the RIC (e.g., 'DEZG.GTX').
--
-- Idempotent: safe to re-run. Deletes existing gettex config first.
-- =============================================================================

-- 0) Remove old gettex config (child tables cascade)
DELETE FROM generic_connector_def WHERE short_id = 'gettex';

-- 1a) Multilanguage description (help text shown in security edit dialog)
INSERT INTO multilinguestring () VALUES ();
SET @nls_id = LAST_INSERT_ID();

DELETE FROM multilinguestrings WHERE id_string = @nls_id;
INSERT INTO multilinguestrings (id_string, language, text) VALUES
(@nls_id, 'en', 'Enter the RIC (Reuters Instrument Code) for the security. The RIC format is <b>SYMBOL.GTX</b>, e.g. <b>DEZG.GTX</b>. Find the RIC on the <a href="https://www.gettex.de/" target="_blank">Gettex</a> website via the network tab in your browser''s developer tools. No API key needed; the connector acquires tokens automatically via the Gettex SAML flow. Intraday data is delayed by 15 minutes.'),
(@nls_id, 'de', 'Geben Sie den RIC (Reuters Instrument Code) des Wertpapiers ein. Das RIC-Format ist <b>SYMBOL.GTX</b>, z.B. <b>DEZG.GTX</b>. Den RIC finden Sie auf der <a href="https://www.gettex.de/" target="_blank">Gettex</a>-Webseite über den Netzwerk-Tab der Browser-Entwicklertools. Kein API-Schlüssel nötig; der Connector bezieht Tokens automatisch über den Gettex-SAML-Flow. Intraday-Daten sind um 15 Minuten verzögert.');

-- 1b) Connector Definition
INSERT INTO generic_connector_def (
  short_id, readable_name, domain_url, needs_api_key,
  rate_limit_type, rate_limit_requests, rate_limit_period_sec, rate_limit_concurrent,
  intraday_delay_seconds, regex_url_pattern,
  supports_security, supports_currency, need_history_gap_filler, gbx_divider_enabled,
  activated, token_config_yaml, description_nls
) VALUES (
  'gettex', 'Gettex (Munich)', 'https://lseg-widgets.financial.com/', 0,
  1, 5, 1, NULL,
  900, '^[A-Z0-9]+\\.GTX$',
  1, 0, 1, 0,
  1,
  'seed:\n  url: "https://www.gettex.de/"\n  regex: ''const\\s+samlRequest\\s*=\\s*`([\\s\\S]*?)`''\nlogin:\n  url: "https://lseg-widgets.financial.com/auth/api/v1/sessions/samllogin?fetchToken=true"\n  body: ''SAMLResponse={seedValue}''\n  contentType: "application/x-www-form-urlencoded"\n  base64EncodeSeed: true\n  jwtPath: "token"\n  sessionPath: "sid"\nrefresh:\n  url: "https://lseg-widgets.financial.com/auth/api/v1/tokens"\n  sidHeader: "sid"\nttlSeconds: 240\n',
  @nls_id
);

SET @def_id = LAST_INSERT_ID();

-- 2) FS_HISTORY Endpoint (daily OHLCV)
INSERT INTO generic_connector_endpoint (
  id_generic_connector, feed_support, instrument_type,
  url_template, http_method,
  response_format, number_format, date_format_type,
  json_data_structure, json_data_path, json_status_path, json_status_ok_value,
  ticker_build_strategy, ticker_uppercase
) VALUES (
  @def_id, 'FS_HISTORY', 'SECURITY',
  'rest/api/timeseries/historical?ric={ticker}&fids=_DATE_END,OPEN_PRC,HIGH_1,LOW_1,CLOSE_PRC,ACVOL_1&samples=D&appendRecentData=all&fromDate={fromDate}&toDate={toDate}',
  'GET',
  1, 4, 5,
  1, 'data', 'status', 'OK',
  1, 1
);

SET @hist_ep_id = LAST_INSERT_ID();

-- 3) FS_HISTORY Field Mappings
INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@hist_ep_id, 'date', '_DATE_END', 1);

INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@hist_ep_id, 'open', 'OPEN_PRC', 0);

INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@hist_ep_id, 'high', 'HIGH_1', 0);

INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@hist_ep_id, 'low', 'LOW_1', 0);

INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@hist_ep_id, 'close', 'CLOSE_PRC', 1);

INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@hist_ep_id, 'volume', 'ACVOL_1', 0);

-- 4) FS_INTRA Endpoint (reuses daily endpoint with today's date for intraday)
INSERT INTO generic_connector_endpoint (
  id_generic_connector, feed_support, instrument_type,
  url_template, http_method,
  response_format, number_format, date_format_type,
  json_data_structure, json_data_path, json_status_path, json_status_ok_value,
  ticker_build_strategy, ticker_uppercase
) VALUES (
  @def_id, 'FS_INTRA', 'SECURITY',
  'rest/api/timeseries/historical?ric={ticker}&fids=_DATE_END,OPEN_PRC,HIGH_1,LOW_1,CLOSE_PRC,ACVOL_1&samples=D&appendRecentData=all&fromDate={fromDate}&toDate={toDate}',
  'GET',
  1, 4, 5,
  1, 'data', 'status', 'OK',
  1, 1
);

SET @intra_ep_id = LAST_INSERT_ID();

-- 5) FS_INTRA Field Mappings
INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@intra_ep_id, 'last', 'CLOSE_PRC', 1);

INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@intra_ep_id, 'open', 'OPEN_PRC', 0);

INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@intra_ep_id, 'high', 'HIGH_1', 0);

INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@intra_ep_id, 'low', 'LOW_1', 0);

INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@intra_ep_id, 'volume', 'ACVOL_1', 0);

-- 6) HTTP Header (jwt: {apiKey} -- auto-acquired by token config)
INSERT INTO generic_connector_http_header (id_generic_connector, header_name, header_value)
VALUES (@def_id, 'jwt', '{apiKey}');

-- =============================================================================
-- OTC-X (Berner Kantonalbank) Generic Connector Configuration
-- Uses the public OTC-X API (www.otc-x.ch/api/) for price data of Swiss
-- OTC-traded securities.
--
-- No API key needed (public API). Ticker is the ISIN of the security.
-- OTC securities may not trade daily; the gap filler compensates for missing data.
--
-- Idempotent: safe to re-run. Deletes existing otcx config first.
-- =============================================================================

-- 0) Remove old otcx config (child tables cascade)
DELETE FROM generic_connector_def WHERE short_id = 'otcx';

-- 1a) Multilanguage description (help text shown in security edit dialog)
INSERT INTO multilinguestring () VALUES ();
SET @otcx_nls_id = LAST_INSERT_ID();

DELETE FROM multilinguestrings WHERE id_string = @otcx_nls_id;
INSERT INTO multilinguestrings (id_string, language, text) VALUES
(@otcx_nls_id, 'en', 'Enter the ISIN of the security traded on <a href="https://www.otc-x.ch/" target="_blank">OTC-X</a> (Berner Kantonalbank), e.g. <b>CH1350861261</b>. Find the ISIN on the OTC-X website. No API key needed (public API). Note: OTC securities may not trade daily — the gap filler compensates for missing data. Intraday data is delayed by 15 minutes.'),
(@otcx_nls_id, 'de', 'Geben Sie die ISIN des auf <a href="https://www.otc-x.ch/" target="_blank">OTC-X</a> (Berner Kantonalbank) gehandelten Wertpapiers ein, z.B. <b>CH1350861261</b>. Die ISIN finden Sie auf der OTC-X-Webseite. Kein API-Schlüssel nötig (öffentliche API). Hinweis: OTC-Wertpapiere werden nicht täglich gehandelt — der Lückenfüller gleicht fehlende Daten aus. Intraday-Daten sind um 15 Minuten verzögert.');

-- 1b) Connector Definition
INSERT INTO generic_connector_def (
  short_id, readable_name, domain_url, needs_api_key,
  rate_limit_type, rate_limit_requests, rate_limit_period_sec, rate_limit_concurrent,
  intraday_delay_seconds, regex_url_pattern,
  supports_security, supports_currency, need_history_gap_filler, gbx_divider_enabled,
  activated, description_nls
) VALUES (
  'otcx', 'OTC-X (Berner Kantonalbank)', 'https://www.otc-x.ch/api/', 0,
  0, NULL, NULL, NULL,
  900, '^[A-Z]{2}[A-Z0-9]{9}[0-9]$',
  1, 0, 1, 0,
  0,
  @otcx_nls_id
);

SET @otcx_def_id = LAST_INSERT_ID();

-- 2) FS_HISTORY Endpoint (daily chart data)
INSERT INTO generic_connector_endpoint (
  id_generic_connector, feed_support, instrument_type,
  url_template, http_method,
  response_format, number_format, date_format_type,
  json_data_structure, json_data_path,
  ticker_build_strategy, ticker_uppercase,
  max_data_points, endpoint_options
) VALUES (
  @otcx_def_id, 'FS_HISTORY', 'SECURITY',
  'market/securities/{ticker}/chartData?type=PRICE_HISTORY&from={fromDate}&until={toDate}&interval=DAY',
  'GET',
  1, 4, 4,
  1, 'data',
  1, 1,
  1500, 3
);

SET @otcx_hist_ep_id = LAST_INSERT_ID();

-- 3) FS_HISTORY Field Mappings
INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@otcx_hist_ep_id, 'date', 'x', 1);
INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@otcx_hist_ep_id, 'close', 'values.0', 1);


-- 4) FS_INTRA Endpoint (current price)
INSERT INTO generic_connector_endpoint (
  id_generic_connector, feed_support, instrument_type,
  url_template, http_method,
  response_format, number_format, date_format_type,
  json_data_structure,
  ticker_build_strategy, ticker_uppercase
) VALUES (
  @otcx_def_id, 'FS_INTRA', 'SECURITY',
  'market/securities/{ticker}',
  'GET',
  1, 4, 5,
  3,
  1, 1
);

SET @otcx_intra_ep_id = LAST_INSERT_ID();

-- 5) FS_INTRA Field Mappings
INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@otcx_intra_ep_id, 'high', 'askPricePoint', 0);

INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@otcx_intra_ep_id, 'last', 'lastPrice', 1);

INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required)
VALUES (@otcx_intra_ep_id, 'low', 'bidPricePoint', 0);

UPDATE trading_platform_plan tpp JOIN multilinguestring ms ON ms.id = tpp.platform_plan_name_nls JOIN multilinguestrings mss ON mss.id_string = ms.id AND mss.language = 'de' SET tpp.fee_model_yaml = 'rules:\n  - name: "Keine Transaktionskosten"\n    condition: "true"\n    expression: "0.0"' WHERE mss.text = 'Migros Bank Vorsorge';
UPDATE trading_platform_plan tpp JOIN multilinguestring ms ON ms.id = tpp.platform_plan_name_nls JOIN multilinguestrings mss ON mss.id_string = ms.id AND mss.language = 'de' SET tpp.fee_model_yaml = 'rules:\n  # Schweizer Börse ab CHF 100''000 (offizieller Zuschlag + Ausführungsgebühr)\n  - name: "Börsenplatz Schweiz ab CHF 100''000"\n    condition: ''tradeValue >= 100000 && mic == "XSWX"''\n    expression: "40.0 + tradeValue * 0.00035"\n\n  # Schweizer Börse unter CHF 100''000 (Pauschale + Ausführungsgebühr Börse ~0.015%)\n  - name: "Börsenplatz Schweiz"\n    condition: ''mic == "XSWX"''\n    expression: "40.0 + tradeValue * 0.00015"\n\n  # Ausland ab CHF 100''000 (offizieller Zuschlag + Ausführungsgebühr)\n  - name: "Börsenplatz Ausland ab CHF 100''000"\n    condition: "tradeValue >= 100000"\n    expression: "40.0 + tradeValue * 0.0012"\n\n  # Standard-Pauschale (inkl. geschätzte Ausführungsgebühr Ausland ~0.015%)\n  - name: "Standard E-Banking Pauschale"\n    condition: "true"\n    expression: "40.0 + tradeValue * 0.00015"' WHERE mss.text = 'Migros Bank Standard';
UPDATE trading_platform_plan tpp JOIN multilinguestring ms ON ms.id = tpp.platform_plan_name_nls JOIN multilinguestrings mss ON mss.id_string = ms.id AND mss.language = 'de' SET tpp.fee_model_yaml = '# ============================================================================\n# PostFinance E-Trading Gebührenmodell (kalibriert)\n# Stand: 1. Januar 2026\n# ============================================================================\n# Kalibrierung:\n#   - Basierend auf 749 verglichenen Transaktionen (2006–2023).\n#   - Transaktionen mit Depotgebühren-Gutschrift (Anfang Jahr/Quartal)\n#     sowie 2024/2025-Daten wurden bei der Kalibrierung ausgeschlossen.\n#   - SIX-Gebühren enthalten neben der Courtage auch Börsengebühren\n#     (Umsatzabgabe, SIX Trading Fee etc.).\n#   - Bei Obligationen können Stückzinsen den Abrechnungsbetrag über die\n#     nächste Tarifstufe heben – dies ist modellbedingt nicht abbildbar.\n# ============================================================================\n\nrules:\n\n  # ==========================================================================\n  # FONDS\n  # ==========================================================================\n  - name: "Fonds Zeichnung (Subscription)"\n    condition: ''instrument == "MUTUAL_FUND" && tradeDirection == 0''\n    expression: "MAX(20.0, MIN(1000.0, tradeValue * 0.01))"\n\n  - name: "Fonds Rücknahme (Redemption)"\n    condition: ''instrument == "MUTUAL_FUND" && tradeDirection == 1''\n    expression: "0.0"\n\n  # ==========================================================================\n  # SPARPLÄNE\n  # ==========================================================================\n  - name: "Sparplan Ausführung"\n    condition: ''instrument == "PENSION_FUNDS"''\n    expression: "MAX(1.0, tradeValue * 0.01)"\n\n  # ==========================================================================\n  # OBLIGATIONEN OTC\n  # ==========================================================================\n  - name: "Obligationen OTC"\n    condition: ''assetclass == "FIXED_INCOME" && mic == "OTCB"''\n    expression: "MAX(50.0, tradeValue * 0.0025)"\n\n  # ==========================================================================\n  # BÖRSENPLATZ-GRUPPE 1: SIX Swiss Exchange, BX Swiss (CHF)\n  # MICs: XSWX, XBRN\n  # --------------------------------------------------------------------------\n  # Kalibriert auf Basis von ~500 Transaktionen. Enthält Courtage plus\n  # Börsengebühren. Typische Ist-Werte pro Stufe:\n  #   ≤ 5''000:   ~27–29      ≤ 20''000:  ~72–73\n  #   ≤ 10''000:  ~37–38.50   ≤ 30''000:  ~97–101\n  #   ≤ 15''000:  ~52–53.50   ≤ 50''000:  ~132–135\n  # ==========================================================================\n  - name: "SIX Swiss Exchange / BX Swiss"\n    condition: ''mic == "XSWX" || mic == "XBRN"''\n    expression: >-\n      IF(tradeValue <= 500, 8.0,\n      IF(tradeValue <= 1000, 15.0,\n      IF(tradeValue <= 5000, 28.0,\n      IF(tradeValue <= 10000, 38.0,\n      IF(tradeValue <= 15000, 53.0,\n      IF(tradeValue <= 20000, 73.0,\n      IF(tradeValue <= 30000, 100.0,\n      IF(tradeValue <= 50000, 135.0,\n      IF(tradeValue <= 100000, 185.0,\n      IF(tradeValue <= 150000, 265.0,\n      330.0))))))))))\n\n  # ==========================================================================\n  # BÖRSENPLATZ-GRUPPE 2: SIX Structured Products, Off-exchange Switzerland\n  # MICs: XSTX, OFCH\n  # ==========================================================================\n  - name: "SIX Structured Products / Off-exchange CH"\n    condition: ''mic == "XSTX" || mic == "OFCH"''\n    expression: >-\n      IF(tradeValue <= 1000, 15.0,\n      IF(tradeValue <= 5000, 25.0,\n      IF(tradeValue <= 10000, 38.0,\n      IF(tradeValue <= 15000, 50.0,\n      IF(tradeValue <= 20000, 70.0,\n      IF(tradeValue <= 30000, 95.0,\n      95.0))))))\n\n  # ==========================================================================\n  # BÖRSENPLATZ-GRUPPE 3: Swiss DOTS (CHF)\n  # MIC: SDOT\n  # ==========================================================================\n  - name: "Swiss DOTS"\n    condition: ''mic == "SDOT"''\n    expression: >-\n      IF(tradeValue <= 1000, 9.0,\n      IF(tradeValue <= 5000, 12.0,\n      IF(tradeValue <= 10000, 20.0,\n      IF(tradeValue <= 50000, 28.0,\n      36.0))))\n\n  # ==========================================================================\n  # BÖRSENPLATZ-GRUPPE 4: Europäische Börsen (EUR)\n  # MICs: XFRA, XETR, XPAR, XAMS, XBRU, XCSE, XSTO, XHEL, XMIL, XMAD, XWBO\n  # --------------------------------------------------------------------------\n  # Kalibriert: Mindestgebühr 35 CHF, konsistent über alle Transaktionen.\n  #   ≤ 5''000:  immer 35    ≤ 10''000: 40    ≤ 15''000: 50\n  # ==========================================================================\n  - name: "Europäische Börsen (EUR)"\n    condition: >-\n      mic == "XFRA" || mic == "XETR" || mic == "XPAR" ||\n      mic == "XAMS" || mic == "XBRU" || mic == "XCSE" ||\n      mic == "XSTO" || mic == "XHEL" || mic == "XMIL" ||\n      mic == "XMAD" || mic == "XWBO"\n    expression: >-\n      IF(tradeValue <= 5000, 35.0,\n      IF(tradeValue <= 10000, 40.0,\n      IF(tradeValue <= 15000, 50.0,\n      IF(tradeValue <= 20000, 65.0,\n      IF(tradeValue <= 30000, 90.0,\n      IF(tradeValue <= 50000, 120.0,\n      IF(tradeValue <= 100000, 180.0,\n      IF(tradeValue <= 150000, 260.0,\n      320.0))))))))\n\n  # ==========================================================================\n  # BÖRSENPLATZ-GRUPPE 5: EUWAX, Off-exchange Germany (EUR)\n  # MICs: XSTU, OFDE\n  # ==========================================================================\n  - name: "EUWAX / Off-exchange DE"\n    condition: ''mic == "XSTU" || mic == "OFDE"''\n    expression: >-\n      IF(tradeValue <= 1000, 15.0,\n      IF(tradeValue <= 5000, 25.0,\n      IF(tradeValue <= 10000, 35.0,\n      IF(tradeValue <= 15000, 45.0,\n      IF(tradeValue <= 20000, 65.0,\n      IF(tradeValue <= 30000, 90.0,\n      90.0))))))\n\n  # ==========================================================================\n  # BÖRSENPLATZ-GRUPPE 6: London Stock Exchange (GBP)\n  # MIC: XLON\n  # ==========================================================================\n  - name: "London Stock Exchange (GBP)"\n    condition: ''mic == "XLON"''\n    expression: >-\n      IF(tradeValue <= 5000, 35.0,\n      IF(tradeValue <= 10000, 40.0,\n      IF(tradeValue <= 15000, 50.0,\n      IF(tradeValue <= 20000, 65.0,\n      IF(tradeValue <= 30000, 90.0,\n      IF(tradeValue <= 50000, 120.0,\n      IF(tradeValue <= 100000, 180.0,\n      IF(tradeValue <= 150000, 260.0,\n      320.0))))))))\n\n  # ==========================================================================\n  # BÖRSENPLATZ-GRUPPE 7: NYSE, NASDAQ, NYSE American (USD)\n  # MICs: XNYS, XNAS, XASE\n  # --------------------------------------------------------------------------\n  # Kalibriert: ≤ 5''000 → 35 (beobachtet bei TV 3''000–3''600)\n  # ==========================================================================\n  - name: "US-Börsen (USD)"\n    condition: ''mic == "XNYS" || mic == "XNAS" || mic == "XASE"''\n    expression: >-\n      IF(tradeValue <= 5000, 35.0,\n      IF(tradeValue <= 10000, 40.0,\n      IF(tradeValue <= 15000, 50.0,\n      IF(tradeValue <= 20000, 65.0,\n      IF(tradeValue <= 30000, 90.0,\n      IF(tradeValue <= 50000, 120.0,\n      IF(tradeValue <= 100000, 180.0,\n      IF(tradeValue <= 150000, 260.0,\n      320.0))))))))\n\n  # ==========================================================================\n  # BÖRSENPLATZ-GRUPPE 8: Toronto TSX, TSX Venture (CAD)\n  # MICs: XTSE, XTSX\n  # ==========================================================================\n  - name: "Kanadische Börsen (CAD)"\n    condition: ''mic == "XTSE" || mic == "XTSX"''\n    expression: >-\n      IF(tradeValue <= 5000, 35.0,\n      IF(tradeValue <= 10000, 40.0,\n      IF(tradeValue <= 15000, 50.0,\n      IF(tradeValue <= 20000, 65.0,\n      IF(tradeValue <= 30000, 90.0,\n      IF(tradeValue <= 50000, 120.0,\n      IF(tradeValue <= 100000, 180.0,\n      IF(tradeValue <= 150000, 260.0,\n      320.0))))))))\n\n  # ==========================================================================\n  # AUFFANGREGEL (Fallback)\n  # ==========================================================================\n  - name: "Standard (unbekannter Börsenplatz)"\n    condition: "true"\n    expression: >-\n      IF(tradeValue <= 5000, 35.0,\n      IF(tradeValue <= 10000, 40.0,\n      IF(tradeValue <= 15000, 50.0,\n      IF(tradeValue <= 20000, 65.0,\n      IF(tradeValue <= 30000, 90.0,\n      IF(tradeValue <= 50000, 120.0,\n      IF(tradeValue <= 100000, 180.0,\n      IF(tradeValue <= 150000, 260.0,\n      320.0))))))))' WHERE mss.text = 'E-Trading - PostFinance Standard';
UPDATE trading_platform_plan tpp JOIN multilinguestring ms ON ms.id = tpp.platform_plan_name_nls JOIN multilinguestrings mss ON mss.id_string = ms.id AND mss.language = 'de' SET tpp.fee_model_yaml = '# ============================================================================\n# Swissquote E-Trading Gebührenmodell (verbessert)\n# Quellen: Swissquote Fee Schedule PDF + moneyland.ch (aktuell 2025/2026)\n#          + Regressionsanalyse historischer Transaktionsdaten\n# ============================================================================\n# Änderungen gegenüber Vorgängerversion:\n#   - Perioden-Format: ab 2024 leicht angepasste Börsengebühren (SIX-Revision)\n#   - ETF auf SIX: Courtage + geschätzte SIX-Börsengebühren\n#     (Handelsgebühr, Clearing, Settlement ≈ 1.5 bps + Fixanteil)\n#   - Kleinere ETF-Tiers (0-500, 500-1000) ebenfalls mit Börsengebühren\n#   - Fonds, Obligationen, Strukturierte Produkte, Aktien: unverändert\n# ============================================================================\n# Hinweise:\n#   - Regeln werden von oben nach unten ausgewertet (first match wins).\n#   - Die Echtzeit-Gebühr von CHF 0.85 pro Transaktion (falls kein\n#     Realtime-Abo) ist NICHT enthalten – separat abzuhandeln.\n#   - Die SIX-Börsengebühren sind als Näherung modelliert (Regression\n#     über ~110 historische ETF-Transaktionen, MRE < 1%).\n# ============================================================================\n\nperiods:\n\n  # ==========================================================================\n  # PERIODE 1: bis Ende 2023\n  # ==========================================================================\n  - validFrom: "2000-01-01"\n    validTo: "2023-12-31"\n    rules:\n\n      # ── ETF LEADERS (SIX) – Courtage + SIX-Börsengebühren ──────────────\n      - name: "ETF Leaders SIX (0-500)"\n        condition: ''instrument == "ETF" && mic == "XSWX" && tradeValue <= 500''\n        expression: "3.0 + MAX(2.5, tradeValue * 0.00025)"\n\n      - name: "ETF Leaders SIX (500-1000)"\n        condition: ''instrument == "ETF" && mic == "XSWX" && tradeValue <= 1000''\n        expression: "5.0 + MAX(2.8, tradeValue * 0.00020)"\n\n      - name: "ETF Leaders SIX (ab 1000)"\n        condition: ''instrument == "ETF" && mic == "XSWX"''\n        expression: "9.0 + MAX(3.0, 2.2 + tradeValue * 0.000155)"\n\n      # ── SWISS DOTS – Flat Fee CHF 9 ─────────────────────────────────────\n      - name: "Swiss DOTS Flat Fee"\n        condition: ''mic == "SDOT"''\n        expression: "9.0"\n\n      # ── FONDS ───────────────────────────────────────────────────────────\n      - name: "Fonds (Tier 0 – Swissquote Depotbank)"\n        condition: ''instrument == "MUTUAL_FUND" && mic == "SQT0"''\n        expression: "0.0"\n\n      - name: "Fonds (Tier A+ – Prime Partner, Flat 9)"\n        condition: ''instrument == "MUTUAL_FUND" && mic == "SQAP"''\n        expression: "9.0"\n\n      - name: "Fonds (Standard – 0.5%, min 9, max 250)"\n        condition: ''instrument == "MUTUAL_FUND"''\n        expression: "MAX(9.0, MIN(250.0, tradeValue * 0.005))"\n\n      # ── OTC ─────────────────────────────────────────────────────────────\n      - name: "OTC Orders"\n        condition: ''mic == "OTCX"''\n        expression: "MAX(100.0, tradeValue * 0.005)"\n\n      # ── OBLIGATIONEN – SIX ─────────────────────────────────────────────\n      - name: "Obligationen CH (0-500)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX" && tradeValue <= 500''\n        expression: "9.0"\n\n      - name: "Obligationen CH (500-2000)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX" && tradeValue <= 2000''\n        expression: "20.0"\n\n      - name: "Obligationen CH (2000-10000)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX" && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Obligationen CH (10000-15000)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX" && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Obligationen CH (15000-25000)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX" && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Obligationen CH (25000-50000)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX" && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Obligationen CH (ab 50000)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX"''\n        expression: "190.0"\n\n      - name: "Obligationen OTC / andere Märkte"\n        condition: ''assetclass == "FIXED_INCOME"''\n        expression: "MAX(50.0, tradeValue * 0.003)"\n\n      # ── STRUKTURIERTE PRODUKTE – SIX ───────────────────────────────────\n      - name: "Strukturierte Produkte SIX (0-500)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSWX" && tradeValue <= 500''\n        expression: "9.0"\n\n      - name: "Strukturierte Produkte SIX (500-2000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSWX" && tradeValue <= 2000''\n        expression: "20.0"\n\n      - name: "Strukturierte Produkte SIX (2000-10000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSWX" && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Strukturierte Produkte SIX (10000-15000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSWX" && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Strukturierte Produkte SIX (ab 15000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSWX"''\n        expression: "80.0"\n\n      # ── STRUKTURIERTE PRODUKTE – EUWAX ─────────────────────────────────\n      - name: "Strukturierte Produkte EUWAX (0-500)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSTU" && tradeValue <= 500''\n        expression: "15.0"\n\n      - name: "Strukturierte Produkte EUWAX (500-2000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSTU" && tradeValue <= 2000''\n        expression: "20.0"\n\n      - name: "Strukturierte Produkte EUWAX (2000-10000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSTU" && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Strukturierte Produkte EUWAX (10000-15000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSTU" && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Strukturierte Produkte EUWAX (ab 15000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSTU"''\n        expression: "80.0"\n\n      # ── AKTIEN – SIX / BX Swiss (CHF) ──────────────────────────────────\n      - name: "SIX / BX Swiss (0-500)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && tradeValue <= 500''\n        expression: "3.0"\n\n      - name: "SIX / BX Swiss (500-2000)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && tradeValue <= 2000''\n        expression: "20.0"\n\n      - name: "SIX / BX Swiss (2000-10000)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "SIX / BX Swiss (10000-15000)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "SIX / BX Swiss (15000-25000)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "SIX / BX Swiss (25000-50000)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "SIX / BX Swiss (ab 50000)"\n        condition: ''mic == "XSWX" || mic == "XBRN"''\n        expression: "190.0"\n\n      # ── AKTIEN – Xetra (EUR) ───────────────────────────────────────────\n      - name: "Xetra (0-1000)"\n        condition: ''mic == "XETR" && tradeValue <= 1000''\n        expression: "5.0"\n\n      - name: "Xetra (1000-2000)"\n        condition: ''mic == "XETR" && tradeValue <= 2000''\n        expression: "10.0"\n\n      - name: "Xetra (2000-10000)"\n        condition: ''mic == "XETR" && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Xetra (10000-15000)"\n        condition: ''mic == "XETR" && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Xetra (15000-25000)"\n        condition: ''mic == "XETR" && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Xetra (25000-50000)"\n        condition: ''mic == "XETR" && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Xetra (ab 50000)"\n        condition: ''mic == "XETR"''\n        expression: "190.0"\n\n      # ── AKTIEN – Frankfurt Parkett (EUR) ────────────────────────────────\n      - name: "Frankfurt (0-1000)"\n        condition: ''mic == "XFRA" && tradeValue <= 1000''\n        expression: "5.0"\n\n      - name: "Frankfurt (1000-2000)"\n        condition: ''mic == "XFRA" && tradeValue <= 2000''\n        expression: "10.0"\n\n      - name: "Frankfurt (2000-10000)"\n        condition: ''mic == "XFRA" && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Frankfurt (10000-15000)"\n        condition: ''mic == "XFRA" && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Frankfurt (15000-25000)"\n        condition: ''mic == "XFRA" && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Frankfurt (25000-50000)"\n        condition: ''mic == "XFRA" && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Frankfurt (ab 50000)"\n        condition: ''mic == "XFRA"''\n        expression: "190.0"\n\n      # ── AKTIEN – EUWAX / Off-exchange DE (EUR) ─────────────────────────\n      - name: "EUWAX / Off-exchange DE (0-1000)"\n        condition: ''(mic == "XSTU" || mic == "OFDE") && tradeValue <= 1000''\n        expression: "5.0"\n\n      - name: "EUWAX / Off-exchange DE (1000-2000)"\n        condition: ''(mic == "XSTU" || mic == "OFDE") && tradeValue <= 2000''\n        expression: "10.0"\n\n      - name: "EUWAX / Off-exchange DE (2000-10000)"\n        condition: ''(mic == "XSTU" || mic == "OFDE") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "EUWAX / Off-exchange DE (10000-15000)"\n        condition: ''(mic == "XSTU" || mic == "OFDE") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "EUWAX / Off-exchange DE (15000-25000)"\n        condition: ''(mic == "XSTU" || mic == "OFDE") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "EUWAX / Off-exchange DE (25000-50000)"\n        condition: ''(mic == "XSTU" || mic == "OFDE") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "EUWAX / Off-exchange DE (ab 50000)"\n        condition: ''mic == "XSTU" || mic == "OFDE"''\n        expression: "190.0"\n\n      # ── AKTIEN – Euronext (Paris, Amsterdam, Brüssel) (EUR) ────────────\n      - name: "Euronext (0-2000)"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU") && tradeValue <= 2000''\n        expression: "25.0"\n\n      - name: "Euronext (2000-10000)"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Euronext (10000-15000)"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Euronext (15000-25000)"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Euronext (25000-50000)"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Euronext (ab 50000)"\n        condition: ''mic == "XPAR" || mic == "XAMS" || mic == "XBRU"''\n        expression: "190.0"\n\n      # ── AKTIEN – Borsa Italiana, Wiener Börse (EUR) ────────────────────\n      - name: "Mailand / Wien (0-2000)"\n        condition: ''(mic == "XMIL" || mic == "XWBO") && tradeValue <= 2000''\n        expression: "25.0"\n\n      - name: "Mailand / Wien (2000-10000)"\n        condition: ''(mic == "XMIL" || mic == "XWBO") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Mailand / Wien (10000-15000)"\n        condition: ''(mic == "XMIL" || mic == "XWBO") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Mailand / Wien (15000-25000)"\n        condition: ''(mic == "XMIL" || mic == "XWBO") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Mailand / Wien (25000-50000)"\n        condition: ''(mic == "XMIL" || mic == "XWBO") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Mailand / Wien (ab 50000)"\n        condition: ''mic == "XMIL" || mic == "XWBO"''\n        expression: "190.0"\n\n      # ── AKTIEN – Skandinavien (EUR) ────────────────────────────────────\n      - name: "Skandinavien (0-2000)"\n        condition: ''(mic == "XCSE" || mic == "XSTO" || mic == "XHEL") && tradeValue <= 2000''\n        expression: "25.0"\n\n      - name: "Skandinavien (2000-10000)"\n        condition: ''(mic == "XCSE" || mic == "XSTO" || mic == "XHEL") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Skandinavien (10000-15000)"\n        condition: ''(mic == "XCSE" || mic == "XSTO" || mic == "XHEL") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Skandinavien (15000-25000)"\n        condition: ''(mic == "XCSE" || mic == "XSTO" || mic == "XHEL") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Skandinavien (25000-50000)"\n        condition: ''(mic == "XCSE" || mic == "XSTO" || mic == "XHEL") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Skandinavien (ab 50000)"\n        condition: ''mic == "XCSE" || mic == "XSTO" || mic == "XHEL"''\n        expression: "190.0"\n\n      # ── AKTIEN – London Stock Exchange (GBP) ───────────────────────────\n      - name: "London (0-2000)"\n        condition: ''mic == "XLON" && tradeValue <= 2000''\n        expression: "25.0"\n\n      - name: "London (2000-10000)"\n        condition: ''mic == "XLON" && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "London (10000-15000)"\n        condition: ''mic == "XLON" && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "London (15000-25000)"\n        condition: ''mic == "XLON" && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "London (25000-50000)"\n        condition: ''mic == "XLON" && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "London (ab 50000)"\n        condition: ''mic == "XLON"''\n        expression: "190.0"\n\n      # ── AKTIEN – NYSE, NASDAQ, NYSE American (USD) ─────────────────────\n      - name: "US-Börsen (0-500)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 500''\n        expression: "3.0"\n\n      - name: "US-Börsen (500-1000)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 1000''\n        expression: "5.0"\n\n      - name: "US-Börsen (1000-2000)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 2000''\n        expression: "10.0"\n\n      - name: "US-Börsen (2000-10000)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "US-Börsen (10000-15000)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "US-Börsen (15000-25000)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "US-Börsen (25000-50000)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "US-Börsen (ab 50000)"\n        condition: ''mic == "XNYS" || mic == "XNAS" || mic == "XASE"''\n        expression: "190.0"\n\n      # ── AKTIEN – Toronto TSX, TSX Venture (CAD) ────────────────────────\n      - name: "Kanada (0-2000)"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && tradeValue <= 2000''\n        expression: "25.0"\n\n      - name: "Kanada (2000-10000)"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Kanada (10000-15000)"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Kanada (15000-25000)"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Kanada (25000-50000)"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Kanada (ab 50000)"\n        condition: ''mic == "XTSE" || mic == "XTSX"''\n        expression: "190.0"\n\n      # ── AUFFANGREGEL ───────────────────────────────────────────────────\n      - name: "Standard (unbekannter Börsenplatz)"\n        condition: "true"\n        expression: >-\n          IF(tradeValue <= 2000, 25.0,\n          IF(tradeValue <= 10000, 30.0,\n          IF(tradeValue <= 15000, 55.0,\n          IF(tradeValue <= 25000, 80.0,\n          IF(tradeValue <= 50000, 135.0,\n          190.0)))))\n\n  # ==========================================================================\n  # PERIODE 2: ab 2024 (leicht angepasste SIX-Börsengebühren)\n  # ==========================================================================\n  - validFrom: "2024-01-01"\n    rules:\n\n      # ── ETF LEADERS (SIX) – Courtage + SIX-Börsengebühren (angepasst) ──\n      - name: "ETF Leaders SIX (0-500)"\n        condition: ''instrument == "ETF" && mic == "XSWX" && tradeValue <= 500''\n        expression: "3.0 + MAX(2.0, tradeValue * 0.00025)"\n\n      - name: "ETF Leaders SIX (500-1000)"\n        condition: ''instrument == "ETF" && mic == "XSWX" && tradeValue <= 1000''\n        expression: "5.0 + MAX(2.2, tradeValue * 0.00020)"\n\n      - name: "ETF Leaders SIX (ab 1000)"\n        condition: ''instrument == "ETF" && mic == "XSWX"''\n        expression: "9.0 + MAX(2.2, 1.3 + tradeValue * 0.00016)"\n\n      # ── SWISS DOTS – Flat Fee CHF 9 ─────────────────────────────────────\n      - name: "Swiss DOTS Flat Fee"\n        condition: ''mic == "SDOT"''\n        expression: "9.0"\n\n      # ── FONDS ───────────────────────────────────────────────────────────\n      - name: "Fonds (Tier 0 – Swissquote Depotbank)"\n        condition: ''instrument == "MUTUAL_FUND" && mic == "SQT0"''\n        expression: "0.0"\n\n      - name: "Fonds (Tier A+ – Prime Partner, Flat 9)"\n        condition: ''instrument == "MUTUAL_FUND" && mic == "SQAP"''\n        expression: "9.0"\n\n      - name: "Fonds (Standard – 0.5%, min 9, max 250)"\n        condition: ''instrument == "MUTUAL_FUND"''\n        expression: "MAX(9.0, MIN(250.0, tradeValue * 0.005))"\n\n      # ── OTC ─────────────────────────────────────────────────────────────\n      - name: "OTC Orders"\n        condition: ''mic == "OTCX"''\n        expression: "MAX(100.0, tradeValue * 0.005)"\n\n      # ── OBLIGATIONEN – SIX ─────────────────────────────────────────────\n      - name: "Obligationen CH (0-500)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX" && tradeValue <= 500''\n        expression: "9.0"\n\n      - name: "Obligationen CH (500-2000)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX" && tradeValue <= 2000''\n        expression: "20.0"\n\n      - name: "Obligationen CH (2000-10000)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX" && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Obligationen CH (10000-15000)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX" && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Obligationen CH (15000-25000)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX" && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Obligationen CH (25000-50000)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX" && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Obligationen CH (ab 50000)"\n        condition: ''assetclass == "FIXED_INCOME" && mic == "XSWX"''\n        expression: "190.0"\n\n      - name: "Obligationen OTC / andere Märkte"\n        condition: ''assetclass == "FIXED_INCOME"''\n        expression: "MAX(50.0, tradeValue * 0.003)"\n\n      # ── STRUKTURIERTE PRODUKTE – SIX ───────────────────────────────────\n      - name: "Strukturierte Produkte SIX (0-500)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSWX" && tradeValue <= 500''\n        expression: "9.0"\n\n      - name: "Strukturierte Produkte SIX (500-2000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSWX" && tradeValue <= 2000''\n        expression: "20.0"\n\n      - name: "Strukturierte Produkte SIX (2000-10000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSWX" && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Strukturierte Produkte SIX (10000-15000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSWX" && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Strukturierte Produkte SIX (ab 15000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSWX"''\n        expression: "80.0"\n\n      # ── STRUKTURIERTE PRODUKTE – EUWAX ─────────────────────────────────\n      - name: "Strukturierte Produkte EUWAX (0-500)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSTU" && tradeValue <= 500''\n        expression: "15.0"\n\n      - name: "Strukturierte Produkte EUWAX (500-2000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSTU" && tradeValue <= 2000''\n        expression: "20.0"\n\n      - name: "Strukturierte Produkte EUWAX (2000-10000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSTU" && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Strukturierte Produkte EUWAX (10000-15000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSTU" && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Strukturierte Produkte EUWAX (ab 15000)"\n        condition: ''instrument == "ISSUER_RISK_PRODUCT" && mic == "XSTU"''\n        expression: "80.0"\n\n      # ── AKTIEN – SIX / BX Swiss (CHF) ──────────────────────────────────\n      - name: "SIX / BX Swiss (0-500)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && tradeValue <= 500''\n        expression: "3.0"\n\n      - name: "SIX / BX Swiss (500-2000)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && tradeValue <= 2000''\n        expression: "20.0"\n\n      - name: "SIX / BX Swiss (2000-10000)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "SIX / BX Swiss (10000-15000)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "SIX / BX Swiss (15000-25000)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "SIX / BX Swiss (25000-50000)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "SIX / BX Swiss (ab 50000)"\n        condition: ''mic == "XSWX" || mic == "XBRN"''\n        expression: "190.0"\n\n      # ── AKTIEN – Xetra (EUR) ───────────────────────────────────────────\n      - name: "Xetra (0-1000)"\n        condition: ''mic == "XETR" && tradeValue <= 1000''\n        expression: "5.0"\n\n      - name: "Xetra (1000-2000)"\n        condition: ''mic == "XETR" && tradeValue <= 2000''\n        expression: "10.0"\n\n      - name: "Xetra (2000-10000)"\n        condition: ''mic == "XETR" && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Xetra (10000-15000)"\n        condition: ''mic == "XETR" && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Xetra (15000-25000)"\n        condition: ''mic == "XETR" && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Xetra (25000-50000)"\n        condition: ''mic == "XETR" && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Xetra (ab 50000)"\n        condition: ''mic == "XETR"''\n        expression: "190.0"\n\n      # ── AKTIEN – Frankfurt Parkett (EUR) ────────────────────────────────\n      - name: "Frankfurt (0-1000)"\n        condition: ''mic == "XFRA" && tradeValue <= 1000''\n        expression: "5.0"\n\n      - name: "Frankfurt (1000-2000)"\n        condition: ''mic == "XFRA" && tradeValue <= 2000''\n        expression: "10.0"\n\n      - name: "Frankfurt (2000-10000)"\n        condition: ''mic == "XFRA" && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Frankfurt (10000-15000)"\n        condition: ''mic == "XFRA" && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Frankfurt (15000-25000)"\n        condition: ''mic == "XFRA" && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Frankfurt (25000-50000)"\n        condition: ''mic == "XFRA" && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Frankfurt (ab 50000)"\n        condition: ''mic == "XFRA"''\n        expression: "190.0"\n\n      # ── AKTIEN – EUWAX / Off-exchange DE (EUR) ─────────────────────────\n      - name: "EUWAX / Off-exchange DE (0-1000)"\n        condition: ''(mic == "XSTU" || mic == "OFDE") && tradeValue <= 1000''\n        expression: "5.0"\n\n      - name: "EUWAX / Off-exchange DE (1000-2000)"\n        condition: ''(mic == "XSTU" || mic == "OFDE") && tradeValue <= 2000''\n        expression: "10.0"\n\n      - name: "EUWAX / Off-exchange DE (2000-10000)"\n        condition: ''(mic == "XSTU" || mic == "OFDE") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "EUWAX / Off-exchange DE (10000-15000)"\n        condition: ''(mic == "XSTU" || mic == "OFDE") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "EUWAX / Off-exchange DE (15000-25000)"\n        condition: ''(mic == "XSTU" || mic == "OFDE") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "EUWAX / Off-exchange DE (25000-50000)"\n        condition: ''(mic == "XSTU" || mic == "OFDE") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "EUWAX / Off-exchange DE (ab 50000)"\n        condition: ''mic == "XSTU" || mic == "OFDE"''\n        expression: "190.0"\n\n      # ── AKTIEN – Euronext (Paris, Amsterdam, Brüssel) (EUR) ────────────\n      - name: "Euronext (0-2000)"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU") && tradeValue <= 2000''\n        expression: "25.0"\n\n      - name: "Euronext (2000-10000)"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Euronext (10000-15000)"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Euronext (15000-25000)"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Euronext (25000-50000)"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Euronext (ab 50000)"\n        condition: ''mic == "XPAR" || mic == "XAMS" || mic == "XBRU"''\n        expression: "190.0"\n\n      # ── AKTIEN – Borsa Italiana, Wiener Börse (EUR) ────────────────────\n      - name: "Mailand / Wien (0-2000)"\n        condition: ''(mic == "XMIL" || mic == "XWBO") && tradeValue <= 2000''\n        expression: "25.0"\n\n      - name: "Mailand / Wien (2000-10000)"\n        condition: ''(mic == "XMIL" || mic == "XWBO") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Mailand / Wien (10000-15000)"\n        condition: ''(mic == "XMIL" || mic == "XWBO") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Mailand / Wien (15000-25000)"\n        condition: ''(mic == "XMIL" || mic == "XWBO") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Mailand / Wien (25000-50000)"\n        condition: ''(mic == "XMIL" || mic == "XWBO") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Mailand / Wien (ab 50000)"\n        condition: ''mic == "XMIL" || mic == "XWBO"''\n        expression: "190.0"\n\n      # ── AKTIEN – Skandinavien (EUR) ────────────────────────────────────\n      - name: "Skandinavien (0-2000)"\n        condition: ''(mic == "XCSE" || mic == "XSTO" || mic == "XHEL") && tradeValue <= 2000''\n        expression: "25.0"\n\n      - name: "Skandinavien (2000-10000)"\n        condition: ''(mic == "XCSE" || mic == "XSTO" || mic == "XHEL") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Skandinavien (10000-15000)"\n        condition: ''(mic == "XCSE" || mic == "XSTO" || mic == "XHEL") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Skandinavien (15000-25000)"\n        condition: ''(mic == "XCSE" || mic == "XSTO" || mic == "XHEL") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Skandinavien (25000-50000)"\n        condition: ''(mic == "XCSE" || mic == "XSTO" || mic == "XHEL") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Skandinavien (ab 50000)"\n        condition: ''mic == "XCSE" || mic == "XSTO" || mic == "XHEL"''\n        expression: "190.0"\n\n      # ── AKTIEN – London Stock Exchange (GBP) ───────────────────────────\n      - name: "London (0-2000)"\n        condition: ''mic == "XLON" && tradeValue <= 2000''\n        expression: "25.0"\n\n      - name: "London (2000-10000)"\n        condition: ''mic == "XLON" && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "London (10000-15000)"\n        condition: ''mic == "XLON" && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "London (15000-25000)"\n        condition: ''mic == "XLON" && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "London (25000-50000)"\n        condition: ''mic == "XLON" && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "London (ab 50000)"\n        condition: ''mic == "XLON"''\n        expression: "190.0"\n\n      # ── AKTIEN – NYSE, NASDAQ, NYSE American (USD) ─────────────────────\n      - name: "US-Börsen (0-500)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 500''\n        expression: "3.0"\n\n      - name: "US-Börsen (500-1000)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 1000''\n        expression: "5.0"\n\n      - name: "US-Börsen (1000-2000)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 2000''\n        expression: "10.0"\n\n      - name: "US-Börsen (2000-10000)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "US-Börsen (10000-15000)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "US-Börsen (15000-25000)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "US-Börsen (25000-50000)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "US-Börsen (ab 50000)"\n        condition: ''mic == "XNYS" || mic == "XNAS" || mic == "XASE"''\n        expression: "190.0"\n\n      # ── AKTIEN – Toronto TSX, TSX Venture (CAD) ────────────────────────\n      - name: "Kanada (0-2000)"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && tradeValue <= 2000''\n        expression: "25.0"\n\n      - name: "Kanada (2000-10000)"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && tradeValue <= 10000''\n        expression: "30.0"\n\n      - name: "Kanada (10000-15000)"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && tradeValue <= 15000''\n        expression: "55.0"\n\n      - name: "Kanada (15000-25000)"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && tradeValue <= 25000''\n        expression: "80.0"\n\n      - name: "Kanada (25000-50000)"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && tradeValue <= 50000''\n        expression: "135.0"\n\n      - name: "Kanada (ab 50000)"\n        condition: ''mic == "XTSE" || mic == "XTSX"''\n        expression: "190.0"\n\n      # ── AUFFANGREGEL ───────────────────────────────────────────────────\n      - name: "Standard (unbekannter Börsenplatz)"\n        condition: "true"\n        expression: >-\n          IF(tradeValue <= 2000, 25.0,\n          IF(tradeValue <= 10000, 30.0,\n          IF(tradeValue <= 15000, 55.0,\n          IF(tradeValue <= 25000, 80.0,\n          IF(tradeValue <= 50000, 135.0,\n          190.0)))))' WHERE mss.text = 'Swissquote Pauschal oder Transaktionsbetrag';
UPDATE trading_platform_plan tpp JOIN multilinguestring ms ON ms.id = tpp.platform_plan_name_nls JOIN multilinguestrings mss ON mss.id_string = ms.id AND mss.language = 'de' SET tpp.fee_model_yaml = '# ============================================================================\n# Cornèrtrader (Cornèr Bank AG) Gebührenmodell – Version 2\n# Quelle: cornertrader.ch/de/preise/ (Stand 2026)\n#         moneyland.ch (Mindestgebühren und Detaildaten)\n#         Backtest gegen 369 reale Transaktionen (2009–2026)\n# ============================================================================\n# ÄNDERUNGEN gegenüber v1:\n#   - NEU: periods-Format (alt vs. aktuell) statt flat rules\n#   - NEU: FOREX-Regeln (vorher Fallback → 2''000–5''000% Fehler)\n#   - NEU: CFD-Regeln (vorher Standard-Aktien-Tarif → 27–63% Fehler)\n#   - FIX: Historische Mindestgebühren (SIX CHF 18, Xetra EUR 18, US USD 12)\n#   - FIX: Madrid/IBEX eigener Tarif (0.20% statt 0.15%)\n#\n# HINWEIS fixedAssets:\n#   Die Stufe (Consistency/Solidity/Opportunity/Energy) hängt vom\n#   Gesamt-Depotwert ab. Wenn fixedAssets=0 oder nicht gesetzt ist,\n#   wird immer Consistency (teuerster Tarif) angewendet.\n#   Für korrekte Ergebnisse muss fixedAssets dem tatsächlichen\n#   Depotwert entsprechen!\n# ============================================================================\n\nperiods:\n\n  # ========================================================================\n  # PERIODE 1: Altes Preismodell (2009 – Ende 2023)\n  # Höhere Mindestgebühren, nur Consistency-Stufe relevant\n  # ========================================================================\n  - validFrom: "2009-01-01"\n    validTo: "2023-12-31"\n    rules:\n\n      # --- FOREX (OTC, kein MIC) -----------------------------------------\n      # Forex-Gebühren sind sehr tief (~0.01% in der alten Periode)\n      # 2022-06-30 EUR/CHF: 12.70 / 100''000 = 0.0127%\n      - name: "Forex (alt)"\n        condition: ''instrument == "FOREX"''\n        expression: "MAX(10.0, tradeValue * 0.00013)"\n\n      # --- CFD US-Börsen --------------------------------------------------\n      # Flat Fee USD 20 für US-CFDs (Pfizer, Alphabet, Oracle)\n      # Ausnahme: Peloton hatte USD 40 – möglicherweise Sonderfall\n      - name: "CFD US (alt)"\n        condition: ''instrument == "CFD" && (mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX")''\n        expression: "20.0"\n\n      # --- SIX SWISS EXCHANGE / BX SWISS (altes Minimum CHF 18) ----------\n      - name: "Aktien/ETF SIX – Energy (alt, Depot ab 500k)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && fixedAssets >= 500000''\n        expression: "MAX(10.0, tradeValue * 0.0007)"\n\n      - name: "Aktien/ETF SIX – Opportunity (alt, Depot ab 250k)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && fixedAssets >= 250000''\n        expression: "MAX(14.0, tradeValue * 0.0008)"\n\n      - name: "Aktien/ETF SIX – Solidity (alt, Depot ab 100k)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && fixedAssets >= 100000''\n        expression: "MAX(18.0, tradeValue * 0.001)"\n\n      - name: "Aktien/ETF SIX – Consistency (alt)"\n        condition: ''mic == "XSWX" || mic == "XBRN"''\n        expression: "MAX(18.0, tradeValue * 0.0012)"\n\n      # --- SIX STRUCTURED PRODUCTS (altes Minimum CHF 18) ----------------\n      - name: "Strukturierte Produkte SIX – Consistency (alt)"\n        condition: ''mic == "XSTX"''\n        expression: "MAX(18.0, tradeValue * 0.0012)"\n\n      # --- XETRA / FRANKFURT (altes Minimum EUR 18) ----------------------\n      - name: "Aktien/ETF Xetra – Energy (alt)"\n        condition: ''(mic == "XETR" || mic == "XETF" || mic == "XFRA") && fixedAssets >= 500000''\n        expression: "MAX(12.0, tradeValue * 0.0008)"\n\n      - name: "Aktien/ETF Xetra – Opportunity (alt)"\n        condition: ''(mic == "XETR" || mic == "XETF" || mic == "XFRA") && fixedAssets >= 250000''\n        expression: "MAX(12.0, tradeValue * 0.001)"\n\n      - name: "Aktien/ETF Xetra – Solidity (alt)"\n        condition: ''(mic == "XETR" || mic == "XETF" || mic == "XFRA") && fixedAssets >= 100000''\n        expression: "MAX(18.0, tradeValue * 0.0012)"\n\n      - name: "Aktien/ETF Xetra – Consistency (alt)"\n        condition: ''mic == "XETR" || mic == "XETF" || mic == "XFRA"''\n        expression: "MAX(18.0, tradeValue * 0.0015)"\n\n      # --- US-BÖRSEN (altes Minimum USD 12) -------------------------------\n      - name: "Aktien/ETF US – Energy (alt)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX") && fixedAssets >= 500000''\n        expression: "MAX(10.0, tradeValue * 0.0008)"\n\n      - name: "Aktien/ETF US – Opportunity (alt)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX") && fixedAssets >= 250000''\n        expression: "MAX(10.0, tradeValue * 0.001)"\n\n      - name: "Aktien/ETF US – Solidity (alt)"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX") && fixedAssets >= 100000''\n        expression: "MAX(12.0, tradeValue * 0.0012)"\n\n      - name: "Aktien/ETF US – Consistency (alt)"\n        condition: ''mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX"''\n        expression: "MAX(12.0, tradeValue * 0.0015)"\n\n      # --- LONDON STOCK EXCHANGE (altes Minimum GBP 15) ------------------\n      - name: "Aktien/ETF London – Consistency (alt)"\n        condition: ''mic == "XLON"''\n        expression: "MAX(15.0, tradeValue * 0.0015)"\n\n      # --- EURONEXT (altes Minimum EUR 18) --------------------------------\n      - name: "Aktien/ETF Euronext – Consistency (alt)"\n        condition: ''mic == "XPAR" || mic == "XAMS" || mic == "XBRU" || mic == "XLIS" || mic == "XDUB"''\n        expression: "MAX(18.0, tradeValue * 0.0015)"\n\n      # --- MADRID / IBEX (0.20% basierend auf Backtest-Daten) -------------\n      # 2022-06-30: TV=20080, actual=40.16 → 0.20%\n      # 2023-01-18: TV=22370, actual=44.74 → 0.20%\n      - name: "Aktien/ETF Madrid – Consistency (alt)"\n        condition: ''mic == "XMAD"''\n        expression: "MAX(18.0, tradeValue * 0.002)"\n\n      # --- ÜBRIGE EUROPÄISCHE BÖRSEN (altes Minimum EUR 18) ---------------\n      - name: "Aktien/ETF übrige EU – Consistency (alt)"\n        condition: ''mic == "XMIL" || mic == "XWBO" || mic == "XOSL" || mic == "XSTO" || mic == "XCSE" || mic == "XHEL"''\n        expression: "MAX(18.0, tradeValue * 0.0015)"\n\n      # --- KANADISCHE BÖRSEN (altes Minimum CAD 18) -----------------------\n      - name: "Aktien/ETF Kanada – Consistency (alt)"\n        condition: ''mic == "XTSE" || mic == "XTSX"''\n        expression: "MAX(18.0, tradeValue * 0.002)"\n\n      # --- FALLBACK -------------------------------------------------------\n      - name: "Fallback (alt)"\n        condition: "true"\n        expression: "MAX(18.0, tradeValue * 0.0015)"\n\n  # ========================================================================\n  # PERIODE 2: Aktuelles Preismodell (ab 2024)\n  # Tiefere Mindestgebühren, alle 4 Stufen\n  # Quelle: cornertrader.ch/de/preise/ (Stand 2026)\n  # ========================================================================\n  - validFrom: "2024-01-01"\n    rules:\n\n      # --- FOREX (OTC, kein MIC) -----------------------------------------\n      # Forex-Gebühren bei Cornèrtrader sind sehr tief.\n      # Analyse der Backtest-Daten:\n      #   2024-12:  ~0.003% (2.64 CHF / 87k)\n      #   2025 Q1:  ~0.003–0.005%\n      #   2025 Q3+: ~0.003% (2.40 CHF / 80k)\n      # Modellierung: ~0.005% mit Minimum CHF 2\n      # Hinweis: Die tatsächlichen Kosten könnten Spread-basiert sein\n      # und sind daher schwer exakt zu modellieren.\n      - name: "Forex – Energy (Depot ab 500k)"\n        condition: ''instrument == "FOREX" && fixedAssets >= 500000''\n        expression: "MAX(2.0, tradeValue * 0.00003)"\n\n      - name: "Forex – Opportunity (Depot ab 250k)"\n        condition: ''instrument == "FOREX" && fixedAssets >= 250000''\n        expression: "MAX(2.0, tradeValue * 0.00005)"\n\n      - name: "Forex – Consistency"\n        condition: ''instrument == "FOREX"''\n        expression: "MAX(5.0, tradeValue * 0.0001)"\n\n      # --- CFD US-BÖRSEN --------------------------------------------------\n      # Backtest zeigt: Flat Fee USD 20 für US-CFDs\n      # (Pfizer, Alphabet, Oracle alle bei exakt 20.00)\n      - name: "CFD US"\n        condition: ''instrument == "CFD" && (mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX")''\n        expression: "20.0"\n\n      # --- CFD Übrige (Fallback für nicht-US CFDs) ------------------------\n      - name: "CFD übrige"\n        condition: ''instrument == "CFD"''\n        expression: "20.0"\n\n      # --- SIX SWISS EXCHANGE / BX SWISS ----------------------------------\n      # ENERGY:      0.07%, min CHF 5\n      # OPPORTUNITY: 0.08%, min CHF 7\n      # SOLIDITY:    0.10%, min CHF 9\n      # CONSISTENCY: 0.12%, min CHF 9\n      - name: "Aktien/ETF SIX – Energy (Depot ab 500k)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && fixedAssets >= 500000''\n        expression: "MAX(5.0, tradeValue * 0.0007)"\n\n      - name: "Aktien/ETF SIX – Opportunity (Depot ab 250k)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && fixedAssets >= 250000''\n        expression: "MAX(7.0, tradeValue * 0.0008)"\n\n      - name: "Aktien/ETF SIX – Solidity (Depot ab 100k)"\n        condition: ''(mic == "XSWX" || mic == "XBRN") && fixedAssets >= 100000''\n        expression: "MAX(9.0, tradeValue * 0.001)"\n\n      - name: "Aktien/ETF SIX – Consistency"\n        condition: ''mic == "XSWX" || mic == "XBRN"''\n        expression: "MAX(9.0, tradeValue * 0.0012)"\n\n      # --- SIX STRUCTURED PRODUCTS ----------------------------------------\n      - name: "Strukturierte Produkte SIX – Energy"\n        condition: ''mic == "XSTX" && fixedAssets >= 500000''\n        expression: "MAX(5.0, tradeValue * 0.0007)"\n\n      - name: "Strukturierte Produkte SIX – Opportunity"\n        condition: ''mic == "XSTX" && fixedAssets >= 250000''\n        expression: "MAX(7.0, tradeValue * 0.0008)"\n\n      - name: "Strukturierte Produkte SIX – Solidity"\n        condition: ''mic == "XSTX" && fixedAssets >= 100000''\n        expression: "MAX(9.0, tradeValue * 0.001)"\n\n      - name: "Strukturierte Produkte SIX – Consistency"\n        condition: ''mic == "XSTX"''\n        expression: "MAX(9.0, tradeValue * 0.0012)"\n\n      # --- XETRA / FRANKFURT ----------------------------------------------\n      # ENERGY:      0.08%, min EUR 8\n      # OPPORTUNITY: 0.10%, min EUR 8\n      # SOLIDITY:    0.12%, min EUR 12\n      # CONSISTENCY: 0.15%, min EUR 12\n      - name: "Aktien/ETF Xetra – Energy"\n        condition: ''(mic == "XETR" || mic == "XETF" || mic == "XFRA") && fixedAssets >= 500000''\n        expression: "MAX(8.0, tradeValue * 0.0008)"\n\n      - name: "Aktien/ETF Xetra – Opportunity"\n        condition: ''(mic == "XETR" || mic == "XETF" || mic == "XFRA") && fixedAssets >= 250000''\n        expression: "MAX(8.0, tradeValue * 0.001)"\n\n      - name: "Aktien/ETF Xetra – Solidity"\n        condition: ''(mic == "XETR" || mic == "XETF" || mic == "XFRA") && fixedAssets >= 100000''\n        expression: "MAX(12.0, tradeValue * 0.0012)"\n\n      - name: "Aktien/ETF Xetra – Consistency"\n        condition: ''mic == "XETR" || mic == "XETF" || mic == "XFRA"''\n        expression: "MAX(12.0, tradeValue * 0.0015)"\n\n      # --- US-BÖRSEN ------------------------------------------------------\n      # ENERGY:      0.08%, min USD 10\n      # OPPORTUNITY: 0.10%, min USD 10\n      # SOLIDITY:    0.12%, min USD 15\n      # CONSISTENCY: 0.15%, min USD 15\n      - name: "Aktien/ETF US – Energy"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX") && fixedAssets >= 500000''\n        expression: "MAX(10.0, tradeValue * 0.0008)"\n\n      - name: "Aktien/ETF US – Opportunity"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX") && fixedAssets >= 250000''\n        expression: "MAX(10.0, tradeValue * 0.001)"\n\n      - name: "Aktien/ETF US – Solidity"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX") && fixedAssets >= 100000''\n        expression: "MAX(15.0, tradeValue * 0.0012)"\n\n      - name: "Aktien/ETF US – Consistency"\n        condition: ''mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX"''\n        expression: "MAX(15.0, tradeValue * 0.0015)"\n\n      # --- LONDON STOCK EXCHANGE ------------------------------------------\n      - name: "Aktien/ETF London – Energy"\n        condition: ''mic == "XLON" && fixedAssets >= 500000''\n        expression: "MAX(8.0, tradeValue * 0.0008)"\n\n      - name: "Aktien/ETF London – Opportunity"\n        condition: ''mic == "XLON" && fixedAssets >= 250000''\n        expression: "MAX(8.0, tradeValue * 0.001)"\n\n      - name: "Aktien/ETF London – Solidity"\n        condition: ''mic == "XLON" && fixedAssets >= 100000''\n        expression: "MAX(12.0, tradeValue * 0.0012)"\n\n      - name: "Aktien/ETF London – Consistency"\n        condition: ''mic == "XLON"''\n        expression: "MAX(12.0, tradeValue * 0.0015)"\n\n      # --- EURONEXT -------------------------------------------------------\n      - name: "Aktien/ETF Euronext – Energy"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU" || mic == "XLIS" || mic == "XDUB") && fixedAssets >= 500000''\n        expression: "MAX(8.0, tradeValue * 0.0008)"\n\n      - name: "Aktien/ETF Euronext – Opportunity"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU" || mic == "XLIS" || mic == "XDUB") && fixedAssets >= 250000''\n        expression: "MAX(8.0, tradeValue * 0.001)"\n\n      - name: "Aktien/ETF Euronext – Solidity"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU" || mic == "XLIS" || mic == "XDUB") && fixedAssets >= 100000''\n        expression: "MAX(12.0, tradeValue * 0.0012)"\n\n      - name: "Aktien/ETF Euronext – Consistency"\n        condition: ''mic == "XPAR" || mic == "XAMS" || mic == "XBRU" || mic == "XLIS" || mic == "XDUB"''\n        expression: "MAX(12.0, tradeValue * 0.0015)"\n\n      # --- MADRID (höherer Satz basierend auf Backtest) -------------------\n      - name: "Aktien/ETF Madrid – Energy"\n        condition: ''mic == "XMAD" && fixedAssets >= 500000''\n        expression: "MAX(8.0, tradeValue * 0.001)"\n\n      - name: "Aktien/ETF Madrid – Opportunity"\n        condition: ''mic == "XMAD" && fixedAssets >= 250000''\n        expression: "MAX(8.0, tradeValue * 0.0012)"\n\n      - name: "Aktien/ETF Madrid – Solidity"\n        condition: ''mic == "XMAD" && fixedAssets >= 100000''\n        expression: "MAX(12.0, tradeValue * 0.0015)"\n\n      - name: "Aktien/ETF Madrid – Consistency"\n        condition: ''mic == "XMAD"''\n        expression: "MAX(12.0, tradeValue * 0.002)"\n\n      # --- ÜBRIGE EUROPÄISCHE BÖRSEN --------------------------------------\n      - name: "Aktien/ETF übrige EU – Energy"\n        condition: ''(mic == "XMIL" || mic == "XWBO" || mic == "XOSL" || mic == "XSTO" || mic == "XCSE" || mic == "XHEL") && fixedAssets >= 500000''\n        expression: "MAX(8.0, tradeValue * 0.0008)"\n\n      - name: "Aktien/ETF übrige EU – Opportunity"\n        condition: ''(mic == "XMIL" || mic == "XWBO" || mic == "XOSL" || mic == "XSTO" || mic == "XCSE" || mic == "XHEL") && fixedAssets >= 250000''\n        expression: "MAX(8.0, tradeValue * 0.001)"\n\n      - name: "Aktien/ETF übrige EU – Solidity"\n        condition: ''(mic == "XMIL" || mic == "XWBO" || mic == "XOSL" || mic == "XSTO" || mic == "XCSE" || mic == "XHEL") && fixedAssets >= 100000''\n        expression: "MAX(12.0, tradeValue * 0.0012)"\n\n      - name: "Aktien/ETF übrige EU – Consistency"\n        condition: ''mic == "XMIL" || mic == "XWBO" || mic == "XOSL" || mic == "XSTO" || mic == "XCSE" || mic == "XHEL"''\n        expression: "MAX(12.0, tradeValue * 0.0015)"\n\n      # --- KANADISCHE BÖRSEN ----------------------------------------------\n      - name: "Aktien/ETF Kanada – Energy"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && fixedAssets >= 500000''\n        expression: "MAX(10.0, tradeValue * 0.001)"\n\n      - name: "Aktien/ETF Kanada – Opportunity"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && fixedAssets >= 250000''\n        expression: "MAX(10.0, tradeValue * 0.0012)"\n\n      - name: "Aktien/ETF Kanada – Solidity"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && fixedAssets >= 100000''\n        expression: "MAX(15.0, tradeValue * 0.0015)"\n\n      - name: "Aktien/ETF Kanada – Consistency"\n        condition: ''mic == "XTSE" || mic == "XTSX"''\n        expression: "MAX(15.0, tradeValue * 0.002)"\n\n      # --- FALLBACK -------------------------------------------------------\n      - name: "Fallback – Energy"\n        condition: "fixedAssets >= 500000"\n        expression: "MAX(8.0, tradeValue * 0.0008)"\n\n      - name: "Fallback – Opportunity"\n        condition: "fixedAssets >= 250000"\n        expression: "MAX(8.0, tradeValue * 0.001)"\n\n      - name: "Fallback – Solidity"\n        condition: "fixedAssets >= 100000"\n        expression: "MAX(12.0, tradeValue * 0.0012)"\n\n      - name: "Fallback – Consistency"\n        condition: "true"\n        expression: "MAX(12.0, tradeValue * 0.0015)"' WHERE mss.text = 'CornèTrader Transaktionsbetrag';
UPDATE trading_platform_plan tpp JOIN multilinguestring ms ON ms.id = tpp.platform_plan_name_nls JOIN multilinguestrings mss ON mss.id_string = ms.id AND mss.language = 'de' SET tpp.fee_model_yaml = '# ============================================================================\n# Raiffeisen Schweiz E-Banking Gebührenmodell\n# Quelle: Dienstleistungspreise Anlegen, gültig ab 1. Januar 2026\n#         (Raiffeisenbank Regio Frick-Mettauertal als Referenz)\n# ============================================================================\n# WICHTIG:\n#   - Raiffeisen ist ein Genossenschaftsverbund – die Tarife können je\n#     nach lokaler Raiffeisenbank leicht abweichen!\n#   - Raiffeisen verwendet einen STAFFELTARIF:\n#       Für die ersten CHF 100''000: Satz A\n#       Für Beträge über CHF 100''000: Satz B (nur auf den Mehrbetrag)\n#       Die Teilbeträge werden addiert.\n#   - E-Banking-Tarif = 50% des Standard-Tarifs\n#   - Alle Regeln hier bilden den E-BANKING-Tarif ab.\n#   - Fonds-Rücknahmen sind generell kostenlos.\n#   - Courtagen sind MwSt-frei. Steuern/Börsengebühren separat.\n# ============================================================================\n\nrules:\n\n  # ==========================================================================\n  # FONDS – RÜCKNAHME (alle Fonds: Raiffeisen + Drittanbieter)\n  # Kostenlos gemäss Webseite\n  # ==========================================================================\n  - name: "Fonds Rücknahme (kostenlos)"\n    condition: ''instrument == "MUTUAL_FUND" && tradeDirection == 1''\n    expression: "0.0"\n\n  # ==========================================================================\n  # FONDS – Geldmarkt- & Obligationenfonds (Zeichnung/Kauf)\n  # E-Banking: 0.40% erste 100k, 0.075% darüber, min CHF 5\n  # (Standard: 0.80% / 0.15% / min CHF 10)\n  # Steuerung über assetclass: MONEY_MARKET oder FIXED_INCOME + MUTUAL_FUND\n  # ==========================================================================\n  - name: "Geldmarkt-/Obligationenfonds E-Banking (bis 100k)"\n    condition: ''instrument == "MUTUAL_FUND" && (assetclass == "MONEY_MARKET" || assetclass == "FIXED_INCOME") && tradeValue <= 100000''\n    expression: "MAX(5.0, tradeValue * 0.004)"\n\n  - name: "Geldmarkt-/Obligationenfonds E-Banking (ab 100k)"\n    condition: ''instrument == "MUTUAL_FUND" && (assetclass == "MONEY_MARKET" || assetclass == "FIXED_INCOME")''\n    expression: "MAX(5.0, 100000 * 0.004 + (tradeValue - 100000) * 0.00075)"\n\n  # ==========================================================================\n  # FONDS – Aktien-, Anlageziel- & übrige Fonds (Zeichnung/Kauf)\n  # E-Banking: 0.75% erste 100k, 0.15% darüber, min CHF 5\n  # (Standard: 1.50% / 0.30% / min CHF 10)\n  # ==========================================================================\n  - name: "Aktienfonds / übrige Fonds E-Banking (bis 100k)"\n    condition: ''instrument == "MUTUAL_FUND" && tradeValue <= 100000''\n    expression: "MAX(5.0, tradeValue * 0.0075)"\n\n  - name: "Aktienfonds / übrige Fonds E-Banking (ab 100k)"\n    condition: ''instrument == "MUTUAL_FUND"''\n    expression: "MAX(5.0, 100000 * 0.0075 + (tradeValue - 100000) * 0.0015)"\n\n  # ==========================================================================\n  # OBLIGATIONEN – Börsenplatz Schweiz\n  # E-Banking: 0.35% erste 100k, 0.075% darüber, min CHF 5\n  # (Standard: 0.70% / 0.15% / min CHF 10)\n  # MICs: XSWX, XBRN, XSTX\n  # ==========================================================================\n  - name: "Obligationen CH E-Banking (bis 100k)"\n    condition: ''assetclass == "FIXED_INCOME" && (mic == "XSWX" || mic == "XBRN" || mic == "XSTX") && tradeValue <= 100000''\n    expression: "MAX(5.0, tradeValue * 0.0035)"\n\n  - name: "Obligationen CH E-Banking (ab 100k)"\n    condition: ''assetclass == "FIXED_INCOME" && (mic == "XSWX" || mic == "XBRN" || mic == "XSTX")''\n    expression: "MAX(5.0, 100000 * 0.0035 + (tradeValue - 100000) * 0.00075)"\n\n  # ==========================================================================\n  # OBLIGATIONEN – Börsenplatz Ausland / OTC\n  # Gleicher Tarif wie Aktien Ausland:\n  # E-Banking: 0.75% erste 100k, 0.15% darüber, min CHF 10\n  # ==========================================================================\n  - name: "Obligationen Ausland/OTC E-Banking (bis 100k)"\n    condition: ''assetclass == "FIXED_INCOME" && tradeValue <= 100000''\n    expression: "MAX(10.0, tradeValue * 0.0075)"\n\n  - name: "Obligationen Ausland/OTC E-Banking (ab 100k)"\n    condition: ''assetclass == "FIXED_INCOME"''\n    expression: "MAX(10.0, 100000 * 0.0075 + (tradeValue - 100000) * 0.0015)"\n\n  # ==========================================================================\n  # AKTIEN / ETF / STRUKTURIERTE PRODUKTE – Börsenplatz Schweiz\n  # E-Banking: 0.50% erste 100k, 0.10% darüber, min CHF 5\n  # (Standard: 1.00% / 0.20% / min CHF 10)\n  # Schweiz = SIX Swiss Exchange, BX Swiss, SIX Structured Products\n  # MICs: XSWX, XBRN, XSTX\n  # ==========================================================================\n  - name: "Aktien/ETF/Struki Schweiz E-Banking (bis 100k)"\n    condition: ''(mic == "XSWX" || mic == "XBRN" || mic == "XSTX") && tradeValue <= 100000''\n    expression: "MAX(5.0, tradeValue * 0.005)"\n\n  - name: "Aktien/ETF/Struki Schweiz E-Banking (ab 100k)"\n    condition: ''mic == "XSWX" || mic == "XBRN" || mic == "XSTX"''\n    expression: "MAX(5.0, 100000 * 0.005 + (tradeValue - 100000) * 0.001)"\n\n  # ==========================================================================\n  # AKTIEN / ETF / STRUKTURIERTE PRODUKTE – Börsenplatz Ausland & OTC\n  # E-Banking: 0.75% erste 100k, 0.15% darüber, min CHF 10\n  # (Standard: 1.50% / 0.30% / min CHF 20)\n  # Alle anderen MICs (inkl. XETR, XFRA, XPAR, XLON, XNYS, XNAS etc.)\n  # ==========================================================================\n  - name: "Aktien/ETF/Struki Ausland E-Banking (bis 100k)"\n    condition: "tradeValue <= 100000"\n    expression: "MAX(10.0, tradeValue * 0.0075)"\n\n  - name: "Aktien/ETF/Struki Ausland E-Banking (ab 100k)"\n    condition: "true"\n    expression: "MAX(10.0, 100000 * 0.0075 + (tradeValue - 100000) * 0.0015)"' WHERE mss.text = 'Raiffeisen Schweiz';
UPDATE trading_platform_plan tpp JOIN multilinguestring ms ON ms.id = tpp.platform_plan_name_nls JOIN multilinguestrings mss ON mss.id_string = ms.id AND mss.language = 'de' SET tpp.fee_model_yaml = '# ============================================================================\n# TradeDirect (Banque Cantonale Vaudoise BCV) Gebührenmodell\n# Quelle: tradedirect.ch/de/home/vorteile-und-tarife.html\n#         Tarife und Bedingungen PDF (42-102a/25.02)\n#         moneyland.ch (ergänzende Staffeln ab CHF 25''000)\n# ============================================================================\n# Hinweise:\n#   - TradeDirect arbeitet mit Flat-Fee-Staffeln pro Auftrag.\n#   - 4 Börsengruppen mit unterschiedlichen Tarifen und Währungen.\n#   - SIX Structured Products: gedeckelt auf CHF 69.90 ab CHF 15''000.\n#   - Nicht kotierte Fonds: Kauf = Courtage, Verkauf = kostenlos.\n#   - BCV-Emissionen (Struki, Kassenobligationen): Zeichnung kostenlos.\n#   - Stempel-, Börsen- und gesetzliche Abgaben NICHT inbegriffen.\n#   - Regeln werden von oben nach unten ausgewertet (first match wins).\n# ============================================================================\n\nrules:\n\n  # ==========================================================================\n  # FONDS – VERKAUF / RÜCKNAHME (nicht kotierte Fonds)\n  # Verkauf kostenlos\n  # ==========================================================================\n  - name: "Fonds Verkauf/Rücknahme (kostenlos)"\n    condition: ''instrument == "MUTUAL_FUND" && tradeDirection == 1''\n    expression: "0.0"\n\n  # ==========================================================================\n  # BCV-EMISSIONEN – Zeichnung kostenlos\n  # Strukturierte Produkte der BCV, BCV-Kassenobligationen\n  # Platzhalter-MIC: BCVE (BCV Emission)\n  # ==========================================================================\n  - name: "BCV Emissionen – Zeichnung (kostenlos)"\n    condition: ''mic == "BCVE" && tradeDirection == 0''\n    expression: "0.0"\n\n  # ==========================================================================\n  # SIX STRUCTURED PRODUCTS EXCHANGE\n  # Selber Tarif wie SIX Swiss Exchange, aber GEDECKELT auf CHF 69.90\n  # für Aufträge über CHF 15''000.\n  # MIC: XSTX\n  # ==========================================================================\n  - name: "SIX Structured Products (gedeckelt ab 15k)"\n    condition: ''mic == "XSTX"''\n    expression: >-\n      IF(tradeValue <= 500, 3.90,\n      IF(tradeValue <= 750, 5.90,\n      IF(tradeValue <= 1000, 5.90,\n      IF(tradeValue <= 2000, 10.90,\n      IF(tradeValue <= 10000, 29.90,\n      IF(tradeValue <= 15000, 44.90,\n      69.90))))))\n\n  # ==========================================================================\n  # GRUPPE 1: Schweizer Börsen, London SETS, Xetra\n  # Währung: CHF (SIX), GBP (London), EUR (Xetra)\n  # MICs: XSWX, XBRN (SIX, BX Swiss)\n  #        XLON (London SETS)\n  #        XETR, XETF (Xetra, Xetra ETF)\n  # Staffeln: 3.90 → 5.90 → 5.90 → 10.90 → 29.90 → 44.90 → 69.90\n  #           → 114.90 → 164.90 → 199.90\n  # ==========================================================================\n  - name: "Aktien/ETF CH + London + Xetra"\n    condition: ''mic == "XSWX" || mic == "XBRN" || mic == "XLON" || mic == "XETR" || mic == "XETF"''\n    expression: >-\n      IF(tradeValue <= 500, 3.90,\n      IF(tradeValue <= 1000, 5.90,\n      IF(tradeValue <= 2000, 10.90,\n      IF(tradeValue <= 10000, 29.90,\n      IF(tradeValue <= 15000, 44.90,\n      IF(tradeValue <= 25000, 69.90,\n      IF(tradeValue <= 50000, 114.90,\n      IF(tradeValue <= 75000, 164.90,\n      199.90))))))))\n\n  # ==========================================================================\n  # GRUPPE 2: US-Börsen (NYSE, NASDAQ, NYSE American)\n  # Währung: USD\n  # MICs: XNYS, XNAS, XASE, ARCX (NYSE Arca)\n  # Identische Staffeln wie Gruppe 1\n  # ==========================================================================\n  - name: "Aktien/ETF US-Börsen"\n    condition: ''mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX"''\n    expression: >-\n      IF(tradeValue <= 500, 3.90,\n      IF(tradeValue <= 1000, 5.90,\n      IF(tradeValue <= 2000, 10.90,\n      IF(tradeValue <= 10000, 29.90,\n      IF(tradeValue <= 15000, 44.90,\n      IF(tradeValue <= 25000, 69.90,\n      IF(tradeValue <= 50000, 114.90,\n      IF(tradeValue <= 75000, 164.90,\n      199.90))))))))\n\n  # ==========================================================================\n  # FRANKFURT PARKETT (Deutsche Börse AG)\n  # Sondertarif: 0–15''000 = pauschal EUR 44.90\n  # Ab 15''000: gleicher Tarif wie europäische Börsen\n  # MIC: XFRA\n  # ==========================================================================\n  - name: "Frankfurt Parkett"\n    condition: ''mic == "XFRA"''\n    expression: >-\n      IF(tradeValue <= 15000, 44.90,\n      IF(tradeValue <= 25000, 69.90,\n      IF(tradeValue <= 50000, 114.90,\n      IF(tradeValue <= 75000, 164.90,\n      199.90))))\n\n  # ==========================================================================\n  # GRUPPE 3: Europäische Börsen (ohne Xetra/Frankfurt)\n  # Euronext Paris, Amsterdam, Brüssel, Lissabon, Madrid,\n  # Borsa Italiana, Helsinki, Nasdaq Stockholm, Oslo,\n  # Nasdaq Copenhagen, Wiener Börse\n  # Währung: EUR\n  # Staffeln: 10.90 → 10.90 → 10.90 → 29.90 → 44.90 → 69.90\n  #           → 114.90 → 164.90 → 199.90\n  # ==========================================================================\n  - name: "Aktien/ETF Europäische Börsen"\n    condition: >-\n      mic == "XPAR" || mic == "XAMS" || mic == "XBRU" || mic == "XLIS"\n      || mic == "XMAD" || mic == "XMIL" || mic == "XHEL" || mic == "XSTO"\n      || mic == "XOSL" || mic == "XCSE" || mic == "XWBO"\n      || mic == "FNSE" || mic == "FNDK" || mic == "FNFI"\n    expression: >-\n      IF(tradeValue <= 2000, 10.90,\n      IF(tradeValue <= 10000, 29.90,\n      IF(tradeValue <= 15000, 44.90,\n      IF(tradeValue <= 25000, 69.90,\n      IF(tradeValue <= 50000, 114.90,\n      IF(tradeValue <= 75000, 164.90,\n      199.90))))))\n\n  # ==========================================================================\n  # GRUPPE 4: Kanadische Börsen (TSX, TSX Venture, CNSX)\n  # Währung: CAD\n  # Staffeln: 9.90 → 9.90 → 21.90 → 21.90 → 32.90 → 44.90 → 69.90\n  #           → 114.90 → 164.90 → 199.90\n  # MICs: XTSE, XTSX, XCNQ\n  # ==========================================================================\n  - name: "Aktien/ETF Kanada"\n    condition: ''mic == "XTSE" || mic == "XTSX" || mic == "XCNQ"''\n    expression: >-\n      IF(tradeValue <= 750, 9.90,\n      IF(tradeValue <= 2000, 21.90,\n      IF(tradeValue <= 10000, 32.90,\n      IF(tradeValue <= 15000, 44.90,\n      IF(tradeValue <= 25000, 69.90,\n      IF(tradeValue <= 50000, 114.90,\n      IF(tradeValue <= 75000, 164.90,\n      199.90)))))))\n\n  # ==========================================================================\n  # FALLBACK – Unbekannte Börsenplätze\n  # Europäischer Tarif als konservative Annahme\n  # ==========================================================================\n  - name: "Fallback (unbekannter Börsenplatz)"\n    condition: "true"\n    expression: >-\n      IF(tradeValue <= 2000, 10.90,\n      IF(tradeValue <= 10000, 29.90,\n      IF(tradeValue <= 15000, 44.90,\n      IF(tradeValue <= 25000, 69.90,\n      IF(tradeValue <= 50000, 114.90,\n      IF(tradeValue <= 75000, 164.90,\n      199.90))))))' WHERE mss.text = 'TradeDirect Schweiz';
UPDATE trading_platform_plan tpp JOIN multilinguestring ms ON ms.id = tpp.platform_plan_name_nls JOIN multilinguestrings mss ON mss.id_string = ms.id AND mss.language = 'de' SET tpp.fee_model_yaml = '# ============================================================================\n# Saxo Bank (Schweiz) AG – Gebührenmodell\n# Quellen: home.saxo/de-ch/rates-and-conditions/ (Stand Februar 2026)\n#          moneyland.ch, schweizerfinanzblog.ch, thepoorswiss.com,\n#          becomewealthy.ch, brokerchooser.com\n# ============================================================================\n# Saxo verwendet ein PROZENTBASIERTES Modell mit 3 Kontostufen:\n#\n#   CLASSIC:   Standard (Depot ab CHF 0)\n#   PLATINUM:  Depot ab CHF 250''000  → fixedAssets >= 250000\n#   VIP:       Depot ab CHF 1''000''000 → fixedAssets >= 1000000\n#\n# Die Stufe wird über "Saxo Rewards" ermittelt und hängt von\n# Depotgrösse, Handelsvolumen und -frequenz ab. Für die Modellierung\n# wird hier fixedAssets als Proxy verwendet (analog Cornèrtrader).\n#\n# Allgemeine Hinweise (NICHT im Modell enthalten):\n#   - Keine Depotgebühren (seit 01.02.2025).\n#   - Gratis e-Steuerauszug (seit Steuerjahr 2025).\n#   - Währungsumrechnungsgebühr: 0.25% pro Conversion.\n#   - Schweizer Stempelsteuer: 0.075% CH / 0.15% Ausland.\n#   - AutoInvest ETF-Sparplan: Kaufkommission = 0 (Plattform-Feature).\n#   - Manuelle Orders per Telefon: CHF 50 Zuschlag (Classic).\n#   - Wertpapierübertrag ausgehend: CHF 50 pro Position.\n#   - Aktien und ETFs haben IDENTISCHE Gebühren.\n# ============================================================================\n\nperiods:\n\n  # ========================================================================\n  # PERIODE 1: Aktuelles Preismodell (ab Januar 2024)\n  # Massiv reduzierte Gebühren seit Jan 2024; Depotgebühren weg seit Feb 2025\n  # ========================================================================\n  - validFrom: "2024-01-01"\n    rules:\n\n      # --- FOREX (OTC, kein MIC) -----------------------------------------\n      # Forex wird bei Saxo über Spreads abgerechnet (Classic: ab 1.6 Pips\n      # auf EUR/USD). Keine fixe Kommission im Classic-Modell.\n      # Hier als Näherungswert modelliert; tatsächliche Kosten hängen vom\n      # Spread ab, der je Paar und Marktlage variiert.\n      # Hinweis: Platimum/VIP erhalten engere Spreads (~0.8 / ~0.4 Pips).\n      # --------------------------------------------------------------------\n      - name: "Forex – VIP (Depot ab 1M)"\n        condition: ''instrument == "FOREX" && fixedAssets >= 1000000''\n        expression: "MAX(1.0, tradeValue * 0.00003)"\n\n      - name: "Forex – Platinum (Depot ab 250k)"\n        condition: ''instrument == "FOREX" && fixedAssets >= 250000''\n        expression: "MAX(1.0, tradeValue * 0.00005)"\n\n      - name: "Forex – Classic"\n        condition: ''instrument == "FOREX"''\n        expression: "MAX(1.0, tradeValue * 0.00008)"\n\n      # --- INVESTMENTFONDS ------------------------------------------------\n      # Flat-Rate Kommission: CHF 8 pro Trade (alle Stufen gleich)\n      # --------------------------------------------------------------------\n      - name: "Investmentfonds"\n        condition: ''instrument == "MUTUAL_FUND"''\n        expression: "8.0"\n\n      # --- ANLEIHEN -------------------------------------------------------\n      # Classic:  0.20%, min EUR/USD 20\n      # Platinum: 0.10%, min EUR/USD 20\n      # VIP:      0.05%, min EUR/USD 20\n      # Mindestordergrösse: EUR/USD 10''000\n      # --------------------------------------------------------------------\n      - name: "Anleihen – VIP"\n        condition: ''instrument == "BOND" && fixedAssets >= 1000000''\n        expression: "MAX(20.0, tradeValue * 0.0005)"\n\n      - name: "Anleihen – Platinum"\n        condition: ''instrument == "BOND" && fixedAssets >= 250000''\n        expression: "MAX(20.0, tradeValue * 0.001)"\n\n      - name: "Anleihen – Classic"\n        condition: ''instrument == "BOND"''\n        expression: "MAX(20.0, tradeValue * 0.002)"\n\n      # --- CFD AUF EINZELAKTIEN ------------------------------------------\n      # CFDs haben eigene Kommissionssätze:\n      # Classic:  0.05%, min variiert je Markt (US: USD 10)\n      # Platinum: 0.03%\n      # VIP:      0.02%\n      # Hinweis: Index-/Rohstoff-/Anleihen-CFDs = kommissionsfrei (Spread)\n      # --------------------------------------------------------------------\n      - name: "CFD Aktien US – VIP"\n        condition: ''instrument == "CFD" && (mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX") && fixedAssets >= 1000000''\n        expression: "MAX(3.0, tradeValue * 0.0002)"\n\n      - name: "CFD Aktien US – Platinum"\n        condition: ''instrument == "CFD" && (mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX") && fixedAssets >= 250000''\n        expression: "MAX(5.0, tradeValue * 0.0003)"\n\n      - name: "CFD Aktien US – Classic"\n        condition: ''instrument == "CFD" && (mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX")''\n        expression: "MAX(10.0, tradeValue * 0.0005)"\n\n      - name: "CFD Aktien EU/CH – VIP"\n        condition: ''instrument == "CFD" && fixedAssets >= 1000000''\n        expression: "MAX(3.0, tradeValue * 0.0002)"\n\n      - name: "CFD Aktien EU/CH – Platinum"\n        condition: ''instrument == "CFD" && fixedAssets >= 250000''\n        expression: "MAX(3.0, tradeValue * 0.0003)"\n\n      - name: "CFD Aktien EU/CH – Classic"\n        condition: ''instrument == "CFD"''\n        expression: "MAX(5.0, tradeValue * 0.0005)"\n\n      # ====================================================================\n      # AKTIEN & ETFs – nach Börsenplatz\n      # Gleiche Prozentsätze auf allen Hauptbörsen:\n      #   Classic:  0.08%\n      #   Platinum: 0.05%\n      #   VIP:      0.03%\n      # Unterschied nur beim Mindestbetrag (Lokalwährung).\n      # ====================================================================\n\n      # --- SIX SWISS EXCHANGE / BX SWISS ---------------------------------\n      # Classic:  0.08%, min CHF 3\n      # Platinum: 0.05%, min CHF 3\n      # VIP:      0.03%, min CHF 3\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF SIX – VIP (Depot ab 1M)"\n        condition: ''(mic == "XSWX" || mic == "XBRN" || mic == "XSTX") && fixedAssets >= 1000000''\n        expression: "MAX(3.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF SIX – Platinum (Depot ab 250k)"\n        condition: ''(mic == "XSWX" || mic == "XBRN" || mic == "XSTX") && fixedAssets >= 250000''\n        expression: "MAX(3.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF SIX – Classic"\n        condition: ''mic == "XSWX" || mic == "XBRN" || mic == "XSTX"''\n        expression: "MAX(3.0, tradeValue * 0.0008)"\n\n      # --- XETRA / DEUTSCHE BÖRSE (Frankfurt) ----------------------------\n      # Classic:  0.08%, min EUR 3\n      # Platinum: 0.05%, min EUR 3\n      # VIP:      0.03%, min EUR 3\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Xetra – VIP"\n        condition: ''(mic == "XETR" || mic == "XETF" || mic == "XFRA") && fixedAssets >= 1000000''\n        expression: "MAX(3.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Xetra – Platinum"\n        condition: ''(mic == "XETR" || mic == "XETF" || mic == "XFRA") && fixedAssets >= 250000''\n        expression: "MAX(3.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Xetra – Classic"\n        condition: ''mic == "XETR" || mic == "XETF" || mic == "XFRA"''\n        expression: "MAX(3.0, tradeValue * 0.0008)"\n\n      # --- US-BÖRSEN (NYSE, NASDAQ, NYSE Arca, NYSE American) ------------\n      # Classic:  0.08%, min USD 1\n      # Platinum: 0.05%, min USD 1\n      # VIP:      0.03%, min USD 1\n      # Tiefster Mindestbetrag aller Börsen!\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF US – VIP"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX") && fixedAssets >= 1000000''\n        expression: "MAX(1.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF US – Platinum"\n        condition: ''(mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX") && fixedAssets >= 250000''\n        expression: "MAX(1.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF US – Classic"\n        condition: ''mic == "XNYS" || mic == "XNAS" || mic == "XASE" || mic == "ARCX"''\n        expression: "MAX(1.0, tradeValue * 0.0008)"\n\n      # --- LONDON STOCK EXCHANGE -----------------------------------------\n      # Classic:  0.08%, min GBP 3\n      # Platinum: 0.05%, min GBP 3\n      # VIP:      0.03%, min GBP 3\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF London – VIP"\n        condition: ''mic == "XLON" && fixedAssets >= 1000000''\n        expression: "MAX(3.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF London – Platinum"\n        condition: ''mic == "XLON" && fixedAssets >= 250000''\n        expression: "MAX(3.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF London – Classic"\n        condition: ''mic == "XLON"''\n        expression: "MAX(3.0, tradeValue * 0.0008)"\n\n      # --- EURONEXT (Paris, Amsterdam, Brüssel, Lissabon, Dublin) --------\n      # Classic:  0.08%, min EUR 3\n      # Platinum: 0.05%, min EUR 3\n      # VIP:      0.03%, min EUR 3\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Euronext – VIP"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU" || mic == "XLIS" || mic == "XDUB") && fixedAssets >= 1000000''\n        expression: "MAX(3.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Euronext – Platinum"\n        condition: ''(mic == "XPAR" || mic == "XAMS" || mic == "XBRU" || mic == "XLIS" || mic == "XDUB") && fixedAssets >= 250000''\n        expression: "MAX(3.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Euronext – Classic"\n        condition: ''mic == "XPAR" || mic == "XAMS" || mic == "XBRU" || mic == "XLIS" || mic == "XDUB"''\n        expression: "MAX(3.0, tradeValue * 0.0008)"\n\n      # --- BORSA ITALIANA (Mailand) --------------------------------------\n      # Classic:  0.08%, min EUR 3\n      # Platinum: 0.05%, min EUR 3\n      # VIP:      0.03%, min EUR 3\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Mailand – VIP"\n        condition: ''mic == "XMIL" && fixedAssets >= 1000000''\n        expression: "MAX(3.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Mailand – Platinum"\n        condition: ''mic == "XMIL" && fixedAssets >= 250000''\n        expression: "MAX(3.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Mailand – Classic"\n        condition: ''mic == "XMIL"''\n        expression: "MAX(3.0, tradeValue * 0.0008)"\n\n      # --- BOLSA DE MADRID -----------------------------------------------\n      # Classic:  0.08%, min EUR 3\n      # Platinum: 0.05%, min EUR 3\n      # VIP:      0.03%, min EUR 3\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Madrid – VIP"\n        condition: ''mic == "XMAD" && fixedAssets >= 1000000''\n        expression: "MAX(3.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Madrid – Platinum"\n        condition: ''mic == "XMAD" && fixedAssets >= 250000''\n        expression: "MAX(3.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Madrid – Classic"\n        condition: ''mic == "XMAD"''\n        expression: "MAX(3.0, tradeValue * 0.0008)"\n\n      # --- WIENER BÖRSE --------------------------------------------------\n      # Classic:  0.08%, min EUR 3\n      # Platinum: 0.05%, min EUR 3\n      # VIP:      0.03%, min EUR 3\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Wien – VIP"\n        condition: ''mic == "XWBO" && fixedAssets >= 1000000''\n        expression: "MAX(3.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Wien – Platinum"\n        condition: ''mic == "XWBO" && fixedAssets >= 250000''\n        expression: "MAX(3.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Wien – Classic"\n        condition: ''mic == "XWBO"''\n        expression: "MAX(3.0, tradeValue * 0.0008)"\n\n      # --- NORDISCHE BÖRSEN (OMX: Stockholm, Kopenhagen, Helsinki) -------\n      # Classic:  0.08%, min EUR/SEK/DKK 3 (lokal)\n      # Platinum: 0.05%, min 3\n      # VIP:      0.03%, min 3\n      # Hinweis: Saxo-Heimatmarkt (Kopenhagen) – gleiche Konditionen\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Nordische Börsen – VIP"\n        condition: ''(mic == "XSTO" || mic == "XCSE" || mic == "XHEL") && fixedAssets >= 1000000''\n        expression: "MAX(3.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Nordische Börsen – Platinum"\n        condition: ''(mic == "XSTO" || mic == "XCSE" || mic == "XHEL") && fixedAssets >= 250000''\n        expression: "MAX(3.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Nordische Börsen – Classic"\n        condition: ''mic == "XSTO" || mic == "XCSE" || mic == "XHEL"''\n        expression: "MAX(3.0, tradeValue * 0.0008)"\n\n      # --- OSLO BØRS (Norwegen) ------------------------------------------\n      # Classic:  0.08%, min NOK 30\n      # Platinum: 0.05%, min NOK 30\n      # VIP:      0.03%, min NOK 30\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Oslo – VIP"\n        condition: ''mic == "XOSL" && fixedAssets >= 1000000''\n        expression: "MAX(30.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Oslo – Platinum"\n        condition: ''mic == "XOSL" && fixedAssets >= 250000''\n        expression: "MAX(30.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Oslo – Classic"\n        condition: ''mic == "XOSL"''\n        expression: "MAX(30.0, tradeValue * 0.0008)"\n\n      # --- TORONTO STOCK EXCHANGE (Kanada) -------------------------------\n      # Classic:  0.08%, min CAD 3\n      # Platinum: 0.05%, min CAD 3\n      # VIP:      0.03%, min CAD 3\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Kanada – VIP"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && fixedAssets >= 1000000''\n        expression: "MAX(3.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Kanada – Platinum"\n        condition: ''(mic == "XTSE" || mic == "XTSX") && fixedAssets >= 250000''\n        expression: "MAX(3.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Kanada – Classic"\n        condition: ''mic == "XTSE" || mic == "XTSX"''\n        expression: "MAX(3.0, tradeValue * 0.0008)"\n\n      # --- HONGKONG (SEHK) ----------------------------------------------\n      # Classic:  0.08%, min HKD 15\n      # Platinum: 0.05%, min HKD 15\n      # VIP:      0.03%, min HKD 15\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Hongkong – VIP"\n        condition: ''mic == "XHKG" && fixedAssets >= 1000000''\n        expression: "MAX(15.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Hongkong – Platinum"\n        condition: ''mic == "XHKG" && fixedAssets >= 250000''\n        expression: "MAX(15.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Hongkong – Classic"\n        condition: ''mic == "XHKG"''\n        expression: "MAX(15.0, tradeValue * 0.0008)"\n\n      # --- TOKYO STOCK EXCHANGE ------------------------------------------\n      # Classic:  0.08%, min JPY 500\n      # Platinum: 0.05%, min JPY 500\n      # VIP:      0.03%, min JPY 500\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Tokio – VIP"\n        condition: ''mic == "XJPX" && fixedAssets >= 1000000''\n        expression: "MAX(500.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Tokio – Platinum"\n        condition: ''mic == "XJPX" && fixedAssets >= 250000''\n        expression: "MAX(500.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Tokio – Classic"\n        condition: ''mic == "XJPX"''\n        expression: "MAX(500.0, tradeValue * 0.0008)"\n\n      # --- AUSTRALIAN SECURITIES EXCHANGE (ASX) --------------------------\n      # Classic:  0.08%, min AUD 5\n      # Platinum: 0.05%, min AUD 5\n      # VIP:      0.03%, min AUD 5\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Australien – VIP"\n        condition: ''mic == "XASX" && fixedAssets >= 1000000''\n        expression: "MAX(5.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Australien – Platinum"\n        condition: ''mic == "XASX" && fixedAssets >= 250000''\n        expression: "MAX(5.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Australien – Classic"\n        condition: ''mic == "XASX"''\n        expression: "MAX(5.0, tradeValue * 0.0008)"\n\n      # --- SINGAPORE EXCHANGE (SGX) -------------------------------------\n      # Classic:  0.08%, min SGD 3\n      # Platinum: 0.05%, min SGD 3\n      # VIP:      0.03%, min SGD 3\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Singapur – VIP"\n        condition: ''mic == "XSES" && fixedAssets >= 1000000''\n        expression: "MAX(3.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Singapur – Platinum"\n        condition: ''mic == "XSES" && fixedAssets >= 250000''\n        expression: "MAX(3.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Singapur – Classic"\n        condition: ''mic == "XSES"''\n        expression: "MAX(3.0, tradeValue * 0.0008)"\n\n      # --- SHANGHAI STOCK EXCHANGE (SSE) ---------------------------------\n      # Classic:  0.08%, min CNH 15\n      # Platinum: 0.05%, min CNH 15\n      # VIP:      0.03%, min CNH 15\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Shanghai – VIP"\n        condition: ''mic == "XSHG" && fixedAssets >= 1000000''\n        expression: "MAX(15.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Shanghai – Platinum"\n        condition: ''mic == "XSHG" && fixedAssets >= 250000''\n        expression: "MAX(15.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Shanghai – Classic"\n        condition: ''mic == "XSHG"''\n        expression: "MAX(15.0, tradeValue * 0.0008)"\n\n      # --- JOHANNESBURG STOCK EXCHANGE (JSE) -----------------------------\n      # Classic:  0.08%, min ZAR 30\n      # Platinum: 0.05%, min ZAR 30\n      # VIP:      0.03%, min ZAR 30\n      # --------------------------------------------------------------------\n      - name: "Aktien/ETF Südafrika – VIP"\n        condition: ''mic == "XJSE" && fixedAssets >= 1000000''\n        expression: "MAX(30.0, tradeValue * 0.0003)"\n\n      - name: "Aktien/ETF Südafrika – Platinum"\n        condition: ''mic == "XJSE" && fixedAssets >= 250000''\n        expression: "MAX(30.0, tradeValue * 0.0005)"\n\n      - name: "Aktien/ETF Südafrika – Classic"\n        condition: ''mic == "XJSE"''\n        expression: "MAX(30.0, tradeValue * 0.0008)"\n\n      # ====================================================================\n      # FALLBACK – Unbekannte Börsenplätze\n      # Konservativer Ansatz: Classic 0.08%, min Lokalwährung ~3\n      # Saxo verwendet für die meisten Märkte einheitlich 0.08% (Classic)\n      # ====================================================================\n      - name: "Fallback – VIP"\n        condition: "fixedAssets >= 1000000"\n        expression: "MAX(3.0, tradeValue * 0.0003)"\n\n      - name: "Fallback – Platinum"\n        condition: "fixedAssets >= 250000"\n        expression: "MAX(3.0, tradeValue * 0.0005)"\n\n      - name: "Fallback – Classic"\n        condition: "true"\n        expression: "MAX(3.0, tradeValue * 0.0008)"' WHERE mss.text = 'Saxo Trader';




-- Generic connector endpoint usage tracking
ALTER TABLE generic_connector_endpoint ADD COLUMN IF NOT EXISTS ever_used_successfully TINYINT(1) NOT NULL DEFAULT 0;

-- COLUMN_ROW_ARRAYS support: path to column names array
ALTER TABLE generic_connector_endpoint ADD COLUMN IF NOT EXISTS json_column_names_path VARCHAR(255) NULL;

-- V0.34.0 Release Notes
DELETE FROM release_note WHERE version = '0.34.0';
INSERT INTO release_note (version, language, note) VALUES
('0.34.0', 'EN', 'Standing orders for recurring transactions, generic database-configurable feed connectors, YAML fee models on trading platform plans, flexible trading periods per instrument, borrowing rate on cash accounts, inline table editing, and stock exchange close-based price updates.'),
('0.34.0', 'DE', 'Daueraufträge für wiederkehrende Transaktionen, generische datenbankbasierte Feed-Konnektoren, YAML-Gebührenmodelle auf Handelsplattform-Plänen, flexible Handelsperioden pro Instrument, Sollzins auf Kassakonten, Inline-Tabellenbearbeitung und börsenschlussbasierte Kursaktualisierungen.');
