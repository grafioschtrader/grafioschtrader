-- =============================================================================
-- Finanz und Wirtschaft (fuw) generic connector: move to the new market data API
--
-- FuW relaunched its market data section on www.fuw.ch/boerse. The former WordPress API at
-- https://wp.fuw.ch/wp-json/fuw/v1/ no longer answers (Cloudflare 522), so history and intraday
-- loads of every fuw instrument fail since September 2026. The new API is public and needs no key:
--   History : /boerse/api/marketdata/chartdata/{listingKey}?timeRange=max
--             payload.prices.max = array of [tsMillis, open, high, low, close, volume] (unchanged)
--   Intraday: /boerse/api/marketdata/quotes/{listingKey}
--             {"status":"Success","payload":[{currentPrice, openPrice, ...}]}
-- The former parameters keyType=LISTING_ID&dataSource=wfg are rejected with HTTP 400. The listing
-- key (url_*_extend) and the field mappings stay the same.
--
-- Rows are addressed by natural keys, because the ids differ between installations.
-- Idempotent: plain UPDATEs, safe to re-run.
-- =============================================================================

UPDATE generic_connector_def SET domain_url = 'https://www.fuw.ch/boerse/api/' WHERE short_id = 'fuw';

UPDATE generic_connector_endpoint e JOIN generic_connector_def d ON d.id_generic_connector = e.id_generic_connector
SET e.url_template = 'marketdata/chartdata/{ticker}?timeRange=max'
WHERE d.short_id = 'fuw' AND e.feed_support = 'FS_HISTORY';

UPDATE generic_connector_endpoint e JOIN generic_connector_def d ON d.id_generic_connector = e.id_generic_connector
SET e.url_template = 'marketdata/quotes/{ticker}', e.json_data_path = 'payload', e.json_status_path = 'status'
WHERE d.short_id = 'fuw' AND e.feed_support = 'FS_INTRA';

-- The failed loads have pushed the retry counters up to the limit, which stops further attempts.
UPDATE securitycurrency SET retry_history_load = 0
WHERE id_connector_history = 'gt.datafeed.fuw' AND retry_history_load > 0;

UPDATE securitycurrency SET retry_intra_load = 0
WHERE id_connector_intra = 'gt.datafeed.fuw' AND retry_intra_load > 0;

-- =============================================================================
-- Disposal cost estimate of the hypothetical sale (GitHub issue #258)
--
-- Switch 0/1: when on, the position reports and the hypothetical sell of the transaction list estimate commission,
-- transaction tax and currency conversion markup from the fee model and the simulation tax model YAML.
-- Off by default. INSERT IGNORE keeps a value an administrator has already set.
-- =============================================================================

INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.disposal.cost.estimate', 0, 0, 'enum:0,1');

-- A tenant can switch the estimate off for itself. The estimate and its report columns are effective only while both
-- the global switch and the tenant switch are on. On by default, so that switching the global parameter on takes
-- effect for every tenant at once. Idempotent: ADD COLUMN IF NOT EXISTS.
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS disposal_cost_estimate TINYINT(1) NOT NULL DEFAULT 1
  COMMENT 'Tenant opt-out of the disposal cost estimate; effective only while gt.disposal.cost.estimate is 1';

-- =============================================================================
-- Daily total value per tenant and portfolio (GitHub issue #270)
--
-- hold_daily_total keeps one row per trading day for each portfolio (portfolio currency) and one for the tenant
-- (id_portfolio NULL, tenant currency). The rows are written by the task HOLD_DAILY_TOTAL_UPDATE from the period
-- holdings queries of the performance report, so they match that report by construction. totalBalanceMC is not stored;
-- it is the sum of cash_balance_mc, securities_mc and margin_close_gain_mc.
-- There is no UNIQUE key: MariaDB lets rows with id_portfolio NULL repeat anyway. The task is the only writer and
-- deletes and inserts a range in one transaction.
--
-- hold_daily_total_state says per tenant from which day the rows have to be recomputed. A tenant without a state row
-- has never been computed and is computed in full; there is no backfill here, the first task run does it.
-- Both tables are derived data: not exported, removed with the tenant or portfolio through the cascades.
-- Idempotent: CREATE TABLE IF NOT EXISTS.
-- =============================================================================

CREATE TABLE IF NOT EXISTS hold_daily_total (
  id_hold_daily_total int(11) NOT NULL AUTO_INCREMENT,
  id_tenant int(11) NOT NULL,
  id_portfolio int(11) DEFAULT NULL COMMENT 'NULL for the total of the tenant in the tenant currency',
  hold_date date NOT NULL,
  cash_balance_mc double NOT NULL,
  securities_mc double NOT NULL,
  margin_close_gain_mc double NOT NULL,
  security_risk_mc double NOT NULL,
  external_cash_transfer_mc double NOT NULL,
  dividend_real_mc double NOT NULL,
  fee_real_mc double NOT NULL,
  interest_cashaccount_real_mc double NOT NULL,
  accumulate_reduce_mc double NOT NULL,
  gain_mc double NOT NULL,
  PRIMARY KEY (id_hold_daily_total),
  KEY IHoldDailyTotal_tenant_portfolio_date (id_tenant, id_portfolio, hold_date),
  KEY FK_HoldDailyTotal_Portfolio (id_portfolio),
  CONSTRAINT FK_HoldDailyTotal_Tenant FOREIGN KEY (id_tenant) REFERENCES tenant (id_tenant) ON DELETE CASCADE,
  CONSTRAINT FK_HoldDailyTotal_Portfolio FOREIGN KEY (id_portfolio) REFERENCES portfolio (id_portfolio)
    ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Derived daily total value (securities and balance) per tenant and portfolio, see HOLD_DAILY_TOTAL_UPDATE';

CREATE TABLE IF NOT EXISTS hold_daily_total_state (
  id_tenant int(11) NOT NULL,
  recalc_from_date date NOT NULL COMMENT 'First day whose hold_daily_total rows are missing or outdated',
  quote_checked_time datetime DEFAULT NULL COMMENT 'UTC time historyquote was last scanned for changed quotes',
  PRIMARY KEY (id_tenant),
  CONSTRAINT FK_HoldDailyTotalState_Tenant FOREIGN KEY (id_tenant) REFERENCES tenant (id_tenant) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Per tenant marker from which day hold_daily_total has to be recomputed';

-- First computation of every main tenant right after the upgrade, instead of waiting for the next run of the cron
-- gt.hold.daily.total.update. Task 55 = HOLD_DAILY_TOTAL_UPDATE without entity, priority 30 = PRIO_LOW, state 0 =
-- waiting. It starts 30 minutes later, after the price update the startup queues. The hold tables themselves are
-- unchanged, so no rebuild (task 39) is needed. Idempotent: skipped while such a task is still waiting.
INSERT INTO task_data_change (id_task, execution_priority, entity, id_entity, earliest_start_time, creation_time,
  exec_start_time, exec_end_time, old_value_varchar, old_value_number, progress_state, failed_message_code)
SELECT 55, 30, NULL, NULL, UTC_TIMESTAMP() + INTERVAL 30 MINUTE, UTC_TIMESTAMP(), NULL, NULL, NULL, NULL, 0, NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM task_data_change WHERE id_task = 55 AND id_entity IS NULL AND progress_state = 0);

-- =============================================================================
-- Swissquote fee model: ETC/ETN on SIX take the ETF Leaders tariff
--
-- An exchange traded product such as the 21Shares Bitcoin Core ETP is an ISSUER_RISK_PRODUCT in GT and therefore fell
-- into the structured products tariff (flat CHF 20 to 80). Swissquote bills it like an ETF Leaders fund: CHF 9 plus the
-- SIX exchange fees, which the ETF formula reproduces within a few cents of the settled trades. In GT an ETC or ETN is
-- an ISSUER_RISK_PRODUCT whose asset class is COMMODITIES or CURRENCY_PAIR (crypto); a certificate on shares or bonds
-- keeps the structured products tariff. The fee rules know no ISIN, so an ETP of an issuer outside the ETF Leaders
-- programme is priced with the same reduced tariff.
-- The three ETF Leaders SIX conditions of both periods are widened in place, in every plan that still contains them,
-- so a fee model an administrator has otherwise edited is kept. Idempotent: the widened condition no longer contains
-- the searched text.
-- =============================================================================
UPDATE trading_platform_plan
SET fee_model_yaml = REPLACE(fee_model_yaml, '''instrument == "ETF" && mic == "XSWX"',
  '''(instrument == "ETF" || (instrument == "ISSUER_RISK_PRODUCT" && (assetclass == "COMMODITIES" || assetclass == "CURRENCY_PAIR"))) && mic == "XSWX"')
WHERE fee_model_yaml LIKE '%''instrument == "ETF" && mic == "XSWX"%';
