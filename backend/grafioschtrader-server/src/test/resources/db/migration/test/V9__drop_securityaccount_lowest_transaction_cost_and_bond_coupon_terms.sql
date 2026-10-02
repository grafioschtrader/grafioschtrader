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

-- Remembered PDF report presentation, matching the production migration.
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS report_settings JSON DEFAULT NULL;

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

-- Longest phase of a replay below a previous high of its cash-flow adjusted equity, in calendar days.
ALTER TABLE algo_simulation_result ADD COLUMN IF NOT EXISTS max_drawdown_duration_days INT DEFAULT NULL
  COMMENT 'Calendar days of the longest phase below a previous equity high, including one still open on the end date'
  AFTER max_drawdown;

-- Daily equity and invested capital of a completed replay, for its equity curve.
ALTER TABLE algo_simulation_result ADD COLUMN IF NOT EXISTS equity_series_json LONGTEXT DEFAULT NULL
  COMMENT 'JSON list of date, equity and invested capital of every fully valued day of a completed run';
