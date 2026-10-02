-- Transaction costs are derived from the YAML fee models; the manually entered cheapest cost was never read.
ALTER TABLE securityaccount DROP COLUMN IF EXISTS lowest_transaction_cost;

-- Regular coupon terms for simulations, taken from the naming convention "<coupon in percent> <name>" that the
-- yield-to-maturity UDF also relies on. The day count follows the currency of the issue as in
-- CouponDayCount.defaultForCurrency. Only bonds without a stored coupon rate are touched, an existing day count is
-- kept, and frequencies the replay coupon schedule cannot handle are skipped.
UPDATE security s
JOIN assetclass a ON a.id_asset_class = s.id_asset_class
SET s.simulation_metadata = JSON_MERGE_PATCH(COALESCE(s.simulation_metadata, '{}'),
  JSON_OBJECT('bondTerms', JSON_OBJECT(
    'couponRate', REPLACE(TRIM(REGEXP_SUBSTR(s.name, '^[0-9]{1,2}([.,][0-9]{1,4})? ')), ',', '.') + 0,
    'couponDayCount', COALESCE(JSON_VALUE(s.simulation_metadata, '$.bondTerms.couponDayCount'),
      IF(UPPER(s.currency) = 'CHF', 'THIRTY_E_360', 'ACT_ACT_ICMA')))))
WHERE a.category_type IN (1, 6)
  AND a.spec_invest_instrument = 0
  AND s.dist_frequency IN (1, 2, 4, 12)
  AND JSON_VALUE(s.simulation_metadata, '$.bondTerms.couponRate') IS NULL
  AND s.name REGEXP '^[0-9]{1,2}([.,][0-9]{1,4})? ';

-- GTNet message retention group SS = server status announcements (GT_NET_OFFLINE_ALL_C 20,
-- GT_NET_SETTINGS_UPDATED_ALL_C 28), range 1-10 days. A retention an administrator already changed is kept; only the
-- SS part is appended once.
UPDATE globalparameters
  SET property_string = CONCAT(property_string, ',SS=10')
  WHERE property_name = 'g.gnet.del.message.recv' AND property_string NOT LIKE '%SS=%';
UPDATE globalparameters
  SET input_rule = 'pattern:^LP=([1-9]|10),HP=([1-9]|10),SL=([1-9]|10),SS=([1-9]|10)$'
  WHERE property_name = 'g.gnet.del.message.recv';

-- Remembered report presentation settings, shared by the tenant.
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS report_settings JSON DEFAULT NULL;

-- Mean reversion dip: dip_buy is the only entry type. A draft that named breakout or indicator_signal becomes a dip
-- draft; it stays a draft because activatable is not touched.
UPDATE algo_strategy SET strategy_config = JSON_REMOVE(strategy_config, '$.entry.type')
WHERE algo_strategy_impl = 68 AND JSON_VALID(strategy_config)
AND JSON_UNQUOTE(JSON_EXTRACT(strategy_config, '$.entry.type')) IN ('breakout', 'indicator_signal');

-- The position exposure limit always applies, so the two switches that claimed to lift it are removed. The strategy
-- configuration is read with FAIL_ON_UNKNOWN_PROPERTIES, so a stored switch would make the strategy unreadable.
UPDATE algo_strategy SET strategy_config = JSON_REMOVE(strategy_config,
  '$.risk_controls.block_entry_if_exposure_exceeded')
WHERE algo_strategy_impl = 68 AND JSON_VALID(strategy_config)
AND JSON_CONTAINS_PATH(strategy_config, 'one', '$.risk_controls.block_entry_if_exposure_exceeded');
UPDATE algo_strategy SET strategy_config = JSON_REMOVE(strategy_config,
  '$.risk_controls.block_add_if_exposure_exceeded')
WHERE algo_strategy_impl = 68 AND JSON_VALID(strategy_config)
AND JSON_CONTAINS_PATH(strategy_config, 'one', '$.risk_controls.block_add_if_exposure_exceeded');

-- A plan line outside its tolerance band, so that a breach is reported once per episode: the daily evaluation compares
-- the lines of the new plan with the flags of the plan it replaces.
ALTER TABLE algo_recommendation ADD COLUMN IF NOT EXISTS allocation_breach TINYINT(1) NOT NULL DEFAULT 0
  COMMENT 'The line is outside its tolerance band: class tolerance, security band or exposure ceiling';

-- The tranche key also names the plan line of an allocation breach.
ALTER TABLE algo_message_alert MODIFY COLUMN tranche_key VARCHAR(32) NOT NULL DEFAULT ''
  COMMENT 'Profit taking tranche, or the plan line of an allocation breach; empty for every other signal';

-- A strategy parameter holds the flat form value of an alert, and the expression alert accepts up to 500 characters.
ALTER TABLE algo_rule_strategy_param MODIFY COLUMN param_value VARCHAR(500) NOT NULL;

-- Retention of the recorded alert notifications, which otherwise grow without limit per tenant. A notification is
-- deleted as soon as it exceeds one of the two limits, but only once its delivery has finished and its alert day lies
-- more than 10 days back. An administrator's value is kept; only the input rule is refreshed.
INSERT INTO globalparameters (property_name, property_string, changed_by_system, input_rule)
  VALUES ('gt.algo.alert.retention', 'Days=180,MaxRecords=200', 0,
    'pattern:^Days=[1-9][0-9]{1,2},MaxRecords=([2-9][0-9]|[1-9][0-9]{2}|1[0-9]{3}|2000)$')
  ON DUPLICATE KEY UPDATE input_rule = VALUES(input_rule);

-- The alert diagnostics translate the evaluation reason, so a fixed reason is stored as an NLS key. Rows written
-- before still carry the English text, which the dialog would show untranslated until the pair is evaluated again.
UPDATE algo_alert_evaluation_state SET reason = CASE reason
  WHEN 'Instrument outside its active dates' THEN 'ALERT_REASON_INSTRUMENT_INACTIVE'
  WHEN 'Missing exchange' THEN 'ALERT_REASON_MISSING_EXCHANGE'
  WHEN 'Missing or invalid exchange hours' THEN 'ALERT_REASON_INVALID_EXCHANGE_HOURS'
  WHEN 'Invalid exchange time zone' THEN 'ALERT_REASON_INVALID_EXCHANGE_ZONE'
  WHEN 'Exchange weekend' THEN 'ALERT_REASON_EXCHANGE_WEEKEND'
  WHEN 'Exchange non-trading day' THEN 'ALERT_REASON_EXCHANGE_NON_TRADING_DAY'
  WHEN 'Exchange not open yet' THEN 'ALERT_REASON_EXCHANGE_NOT_OPEN'
  WHEN 'Waiting for closing quote' THEN 'ALERT_REASON_WAITING_CLOSING_QUOTE'
  WHEN 'Quote refresh failed' THEN 'ALERT_REASON_QUOTE_REFRESH_FAILED'
  WHEN 'Required quote is missing or stale' THEN 'ALERT_REASON_QUOTE_MISSING_OR_STALE'
  WHEN 'Evaluation failed' THEN 'ALERT_REASON_EVALUATION_FAILED'
  WHEN 'Holding percentage unavailable: cost basis is zero' THEN 'ALERT_REASON_HOLDING_PERCENTAGE_UNAVAILABLE'
  ELSE reason END
WHERE reason IN ('Instrument outside its active dates', 'Missing exchange', 'Missing or invalid exchange hours',
  'Invalid exchange time zone', 'Exchange weekend', 'Exchange non-trading day', 'Exchange not open yet',
  'Waiting for closing quote', 'Quote refresh failed', 'Required quote is missing or stale', 'Evaluation failed',
  'Holding percentage unavailable: cost basis is zero');

-- Longest phase of a replay below a previous high of its cash-flow adjusted equity, in calendar days.
ALTER TABLE algo_simulation_result ADD COLUMN IF NOT EXISTS max_drawdown_duration_days INT DEFAULT NULL
  COMMENT 'Calendar days of the longest phase below a previous equity high, including one still open on the end date'
  AFTER max_drawdown;

-- Daily equity and invested capital of a completed replay, for its equity curve.
ALTER TABLE algo_simulation_result ADD COLUMN IF NOT EXISTS equity_series_json LONGTEXT DEFAULT NULL
  COMMENT 'JSON list of date, equity and invested capital of every fully valued day of a completed run';
