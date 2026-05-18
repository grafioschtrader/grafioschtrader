-- Maps an ISO currency code to a synthetic securitycurrency row (NON_INVESTABLE_INDICES) whose
-- historical quotes serve as that currency's risk-free interest rate (used e.g. by Sharpe ratio).
-- One row per currency (UNIQUE). The Integer surrogate PK is required so the entity can extend
-- BaseID<Integer> (Auditable's parent) and reuse the project's standard CRUD admin stack.
-- Deleting the underlying securitycurrency cascades to the mapping.
--
-- DROP first to handle re-applies that may have an older shape (currency-PK only). The seed at
-- the bottom of this script re-creates the 5 standard mappings; any user-added mapping rows are
-- intentionally dropped. The underlying FRED Security rows survive because they're owned by
-- securitycurrency, not by this table.
DROP TABLE IF EXISTS risk_free_rate_mapping;
CREATE TABLE risk_free_rate_mapping (
  id_risk_free_rate_mapping INT       NOT NULL AUTO_INCREMENT,
  currency                  CHAR(3)   NOT NULL,
  id_securitycurrency       INT       NOT NULL,
  created_by                INT       NOT NULL,
  creation_time             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_modified_by          INT       NOT NULL,
  last_modified_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version                   INT       NOT NULL,
  PRIMARY KEY (id_risk_free_rate_mapping),
  UNIQUE KEY uq_rfr_currency (currency),
  CONSTRAINT fk_rfr_securitycurrency FOREIGN KEY (id_securitycurrency)
    REFERENCES securitycurrency (id_securitycurrency) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Seed: synthetic securities + mapping for primary currencies (USD/EUR/GBP/CHF/JPY).
-- Each (currency, FRED series_id) becomes a NON_INVESTABLE_INDICES Security whose historical
-- quotes are populated by the FRED feed connector once an admin registers the FRED API key
-- under "Connector API Keys" (id_provider = 'fred').
-- Idempotency: each step does find-or-insert against a stable natural key, so re-running the
-- migration is safe if an admin manually created any of these rows beforehand.
-- ---------------------------------------------------------------------------

DELIMITER //

DROP PROCEDURE IF EXISTS SeedRiskFreeRateCurrency //
DROP PROCEDURE IF EXISTS SeedRiskFreeRate //

CREATE PROCEDURE SeedRiskFreeRateCurrency(
    IN p_currency CHAR(3),
    IN p_fred_series VARCHAR(254),
    IN p_security_name VARCHAR(80),
    IN p_assetclass_id INT,
    IN p_stockexchange_id INT)
BEGIN
  DECLARE v_sc_id INT DEFAULT NULL;

  -- Find existing synthetic security by (connector, series). Avoids duplicates when an admin
  -- already created one manually before this migration runs.
  SELECT id_securitycurrency INTO v_sc_id
    FROM securitycurrency
   WHERE id_connector_history = 'gt.datafeed.fred'
     AND url_history_extend  = p_fred_series
   LIMIT 1;

  IF v_sc_id IS NULL THEN
    INSERT INTO securitycurrency (
        dtype, id_connector_history, url_history_extend,
        full_load_timestamp, s_timestamp,
        retry_history_load, retry_intra_load,
        gt_net_lastprice_recv, gt_net_historical_recv,
        gt_net_lastprice_send, gt_net_historical_send,
        created_by, creation_time, last_modified_by, last_modified_time, version)
      VALUES (
        'S', 'gt.datafeed.fred', p_fred_series,
        '2000-01-01 00:00:00', UTC_TIMESTAMP(),
        0, 0,
        0, 0,
        0, 0,
        0, UTC_TIMESTAMP(), 0, UTC_TIMESTAMP(), 0);
    SET v_sc_id = LAST_INSERT_ID();

    -- active_from_date MUST be >= GlobalConstants.OLDEST_TRADING_DAY ('2000-01-03'); otherwise the
    -- @AfterEqual constraint on Historyquote.date rejects observations that FRED dates earlier
    -- (e.g. monthly series carry observations dated on the 1st of each month, including 2000-01-01).
    INSERT INTO security (
        id_securitycurrency, name, currency,
        id_asset_class, id_stockexchange,
        active_from_date, active_to_date,
        dist_frequency, leverage_factor,
        retry_dividend_load, retry_split_load)
      VALUES (
        v_sc_id, p_security_name, p_currency,
        p_assetclass_id, p_stockexchange_id,
        '2000-01-03', '2099-12-31',
        0, 1.0,
        0, 0);

    -- Queue a background task to populate the historical price data for this new security.
    -- id_task=5 = TaskTypeExtended.SECURITY_LOAD_HISTORICAL_INTRA_PRICE_DATA
    -- execution_priority=20 = TaskDataExecPriority.PRIO_NORMAL
    -- progress_state=0 = ProgressStateType.PROG_WAITING
    -- Only enqueued when the security was just created, so re-running the migration is a no-op
    -- (no duplicate tasks). If the FRED API key isn't registered yet, the task fails and the admin
    -- can manually re-trigger via the task admin UI after setting the key.
    --
    -- Timestamps use UTC_TIMESTAMP(), not NOW(): the application (BaseConstants.TIME_ZONE='UTC')
    -- stores LocalDateTime as UTC, while MariaDB's NOW() returns server-local time. Mixing the two
    -- makes the executor read the task as scheduled HOURS in the future and never run it.
    -- earliest_start_time is bumped by +1 hour so the admin has a window after the migration to
    -- register the FRED API key under "Connector API Keys" before the executor first picks it up.
    INSERT INTO task_data_change (
        id_task, execution_priority, entity, id_entity,
        earliest_start_time, creation_time, progress_state)
      VALUES (
        5, 20, 'Security', v_sc_id,
        UTC_TIMESTAMP() + INTERVAL 1 HOUR, UTC_TIMESTAMP(), 0);
  END IF;

  -- INSERT IGNORE on the UNIQUE currency constraint is a no-op when a row already exists.
  -- Audit fields seeded as system-user (id 0) so the row reads as "system-created" in the admin
  -- UI; real user actions overwrite created_by/last_modified_by via the standard CRUD path.
  INSERT IGNORE INTO risk_free_rate_mapping (
      currency, id_securitycurrency,
      created_by, creation_time, last_modified_by, last_modified_time, version)
    VALUES (
      p_currency, v_sc_id,
      0, UTC_TIMESTAMP(), 0, UTC_TIMESTAMP(), 0);
END //

CREATE PROCEDURE SeedRiskFreeRate()
BEGIN
  DECLARE v_mlid INT DEFAULT NULL;
  DECLARE v_ac_id INT DEFAULT NULL;
  DECLARE v_se_id INT DEFAULT NULL;

  -- 1. multilinguestring "Risk-free rate" / "Risikofreier Zinssatz" (asset-class subcategory label)
  SELECT m.id INTO v_mlid
    FROM multilinguestring m
    JOIN multilinguestrings ms ON m.id = ms.id_string
   WHERE ms.language = 'en' AND ms.text = 'Risk-free rate'
   LIMIT 1;
  IF v_mlid IS NULL THEN
    INSERT INTO multilinguestring () VALUES ();
    SET v_mlid = LAST_INSERT_ID();
    INSERT INTO multilinguestrings (id_string, language, text) VALUES (v_mlid, 'en', 'Risk-free rate');
    INSERT INTO multilinguestrings (id_string, language, text) VALUES (v_mlid, 'de', 'Risikofreier Zinssatz');
  END IF;

  -- 2. assetclass (category_type=1 FIXED_INCOME, spec_invest_instrument=10 NON_INVESTABLE_INDICES)
  SELECT id_asset_class INTO v_ac_id
    FROM assetclass
   WHERE category_type = 1 AND spec_invest_instrument = 10 AND sub_category_nls = v_mlid
   LIMIT 1;
  IF v_ac_id IS NULL THEN
    INSERT INTO assetclass (
        category_type, spec_invest_instrument, sub_category_nls,
        created_by, creation_time, last_modified_by, last_modified_time, version)
      VALUES (1, 10, v_mlid, 0, UTC_TIMESTAMP(), 0, UTC_TIMESTAMP(), 0);
    SET v_ac_id = LAST_INSERT_ID();
  END IF;

  -- 3. stockexchange. Name is UNIQUE so it's a stable lookup key.
  --    no_market_value=0 because the quote data IS publicly available (fetched from FRED) — the
  --    "no_market_value" flag means "no publicly available quote data, quotes are user-entered",
  --    which would (wrongly) make the system store these rates in historyquote_period instead of
  --    historyquote and bypass the connector's auto-pull.
  SELECT id_stockexchange INTO v_se_id
    FROM stockexchange
   WHERE name = 'Risk-Free Rate Sources'
   LIMIT 1;
  IF v_se_id IS NULL THEN
    INSERT INTO stockexchange (
        mic, name, country_code,
        no_market_value, secondary_market,
        time_open, time_close, time_zone,
        created_by, creation_time, last_modified_by, last_modified_time, version)
      VALUES (
        NULL, 'Risk-Free Rate Sources', 'US',
        0, 0,
        '00:00:00', '23:59:00', 'UTC',
        0, UTC_TIMESTAMP(), 0, UTC_TIMESTAMP(), 0);
    SET v_se_id = LAST_INSERT_ID();
  END IF;

  -- 4. Synthetic security + mapping per primary currency.
  CALL SeedRiskFreeRateCurrency('USD', 'DGS3MO',                'USD 3M Risk-Free Rate (FRED DGS3MO)',  v_ac_id, v_se_id);
  CALL SeedRiskFreeRateCurrency('EUR', 'ECBESTRVOLWGTTRMDMNRT', 'EUR Risk-Free Rate (ESTR)',            v_ac_id, v_se_id);
  CALL SeedRiskFreeRateCurrency('GBP', 'IUDSOIA',                'GBP Risk-Free Rate (SONIA)',          v_ac_id, v_se_id);
  CALL SeedRiskFreeRateCurrency('CHF', 'IR3TIB01CHM156N',        'CHF Risk-Free Rate (3M Interbank)',   v_ac_id, v_se_id);
  CALL SeedRiskFreeRateCurrency('JPY', 'IRSTCI01JPM156N',        'JPY Risk-Free Rate (Call Rate)',      v_ac_id, v_se_id);
END //

DELIMITER ;

CALL SeedRiskFreeRate();

DROP PROCEDURE IF EXISTS SeedRiskFreeRate;
DROP PROCEDURE IF EXISTS SeedRiskFreeRateCurrency;
