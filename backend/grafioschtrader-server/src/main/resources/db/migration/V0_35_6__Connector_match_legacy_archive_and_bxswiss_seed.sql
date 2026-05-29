-- Three-state switch for connector / asset class compatibility enforcement (see GLOB_KEY_FORCE_CONNECTOR_MATCH).
-- 0 = off (no validation), 1 = server-side enforcement only, 2 = server-side + frontend dropdown pre-filter.
DELETE FROM globalparameters WHERE property_name = 'gt.force.connector.match';
INSERT INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.force.connector.match', 0, 0, 'enum:0,1,2');

-- Shadow archive for historyquotes that the active connector can no longer supply.
-- Populated automatically when a security's connector or url_history_extend changes
-- (existing connector-change branch of historyNeedToBeReloaded). Consulted on every
-- subsequent reload to supplement the live historyquote table with rows the new
-- connector does not cover. create_type preserves provenance (MANUAL_IMPORTED,
-- ADD_MODIFIED_USER, etc.) so restored rows keep their original origin label;
-- synthetic FILL_GAP_BY_CONNECTOR (create_type = 6) rows are filtered out at
-- copy time and so never appear here.
DROP TABLE IF EXISTS historyquote_legacy;
CREATE TABLE IF NOT EXISTS historyquote_legacy (
  id_historyquote_legacy INT NOT NULL AUTO_INCREMENT,
  id_securitycurrency INT NOT NULL,
  transfer_date DATE NOT NULL,
  `date` DATE NOT NULL,
  `close` DOUBLE NOT NULL,
  `open` DOUBLE NULL,
  high DOUBLE NULL,
  low DOUBLE NULL,
  volume BIGINT NULL,
  create_type TINYINT NULL,
  PRIMARY KEY (id_historyquote_legacy),
  UNIQUE KEY uq_hql_sc_date (id_securitycurrency, `date`),
  KEY ix_hql_sc_transfer (id_securitycurrency, transfer_date),
  CONSTRAINT FK_HQL_Security FOREIGN KEY (id_securitycurrency)
    REFERENCES securitycurrency (id_securitycurrency) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================================
-- BX Swiss (Bern stock exchange) Generic Connector Configuration
-- Uses the undocumented but public chart JSON endpoint family at
-- /quotedataprovider/candlestick/ for EOD history and 5-minute intraday bars.
-- Ticker = ISIN. No API key needed. Idempotent: safe to re-run.
-- =============================================================================

-- 0) Remove old bxswiss config (child tables cascade)
DELETE FROM generic_connector_def WHERE short_id = 'bxswiss';

-- 1a) Multilanguage description (help text shown in security edit dialog)
INSERT INTO multilinguestring () VALUES ();
SET @bxs_nls_id = LAST_INSERT_ID();

DELETE FROM multilinguestrings WHERE id_string = @bxs_nls_id;
INSERT INTO multilinguestrings (id_string, language, text) VALUES
(@bxs_nls_id, 'en',
 'Enter the ISIN of a security listed on <a href="https://www.bxswiss.com/" target="_blank">BX Swiss</a> (Bern stock exchange), e.g. <b>IE00B1FZS574</b>. No API key needed (undocumented but public chart API). Provides full end-of-day OHLC history and 5-minute intraday bars over the last week; no volume, dividends or splits.'),
(@bxs_nls_id, 'de',
 'Geben Sie die ISIN eines an der <a href="https://www.bxswiss.com/" target="_blank">BX Swiss</a> (Berner Börse) gehandelten Wertpapiers ein, z.B. <b>IE00B1FZS574</b>. Kein API-Schlüssel nötig (undokumentierte aber öffentliche Chart-API). Liefert vollständige End-of-Day-OHLC-Historie und 5-Minuten-Intraday-Balken der letzten Woche; ohne Volumen, Dividenden oder Splits.');

-- 1b) Connector definition
INSERT INTO generic_connector_def (
  short_id, readable_name, domain_url, needs_api_key,
  rate_limit_type, rate_limit_requests, rate_limit_period_sec, rate_limit_concurrent,
  intraday_delay_seconds, regex_url_pattern,
  supports_security, supports_currency, need_history_gap_filler, gbx_divider_enabled,
  activated, supported_categories, geo_restrictions, description_nls
) VALUES (
  'bxswiss', 'BX Swiss (Bern)', 'https://www.bxswiss.com/', 0,
  0, NULL, NULL, NULL,
  900, '^[A-Z]{2}[A-Z0-9]{9}[0-9]$',
  1, 0, 1, 0,
  0,
  'EQUITIES,ETF,FIXED_INCOME,ISSUER_RISK_PRODUCT', 'XBRN EQWB',
  @bxs_nls_id
);
SET @bxs_def_id = LAST_INSERT_ID();

-- 2) FS_HISTORY endpoint — full EOD history via /candlestick/alltime/
INSERT INTO generic_connector_endpoint (
  id_generic_connector, feed_support, instrument_type,
  url_template, http_method,
  response_format, number_format, date_format_type,
  json_data_structure, json_data_path,
  ticker_build_strategy, ticker_uppercase
) VALUES (
  @bxs_def_id, 'FS_HISTORY', 'SECURITY',
  'quotedataprovider/candlestick/alltime/{ticker}',
  'GET',
  1, 4, 2,
  1, 'ohlcMid',
  1, 1
);
SET @bxs_hist_ep_id = LAST_INSERT_ID();

-- 3) FS_HISTORY field mappings — each row is [ts_ms, open, high, low, close]
INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required) VALUES
  (@bxs_hist_ep_id, 'date',  '0', 1),
  (@bxs_hist_ep_id, 'open',  '1', 0),
  (@bxs_hist_ep_id, 'high',  '2', 0),
  (@bxs_hist_ep_id, 'low',   '3', 0),
  (@bxs_hist_ep_id, 'close', '4', 1);

-- 4) FS_INTRA endpoint — 5-minute bars over the last week; last bar = latest tick
--    endpoint_options bit 2 (= 4) sets INTRADAY_USE_LAST_BAR
INSERT INTO generic_connector_endpoint (
  id_generic_connector, feed_support, instrument_type,
  url_template, http_method,
  response_format, number_format, date_format_type,
  json_data_structure, json_data_path,
  ticker_build_strategy, ticker_uppercase,
  endpoint_options
) VALUES (
  @bxs_def_id, 'FS_INTRA', 'SECURITY',
  'quotedataprovider/candlestick/week/{ticker}',
  'GET',
  1, 4, 2,
  1, 'ohlcMid',
  1, 1,
  4
);
SET @bxs_intra_ep_id = LAST_INSERT_ID();

-- 5) FS_INTRA field mappings — close of latest bar maps to sLast
INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required) VALUES
  (@bxs_intra_ep_id, 'open', '1', 0),
  (@bxs_intra_ep_id, 'high', '2', 0),
  (@bxs_intra_ep_id, 'low',  '3', 0),
  (@bxs_intra_ep_id, 'last', '4', 1);

-- =============================================================================
-- Backfill supported_categories / geo_restrictions for the gettex and otcx
-- generic connectors (originally seeded in V0_34_0 without correct filter
-- values). These drive IFeedConnector.supports(mic, country, assetclass, ...)
-- when gt.force.connector.match >= 1. MIC-based geo codes replace gettex's
-- over-broad 'DE' country code (which matched every German venue):
--   gettex  -> XMUN (Boerse Muenchen) + MUNC/MUND (gettex regulated/Freiverkehr)
--   otcx    -> OTXB (BEKB OTC-X)
-- Categories mirror each venue's real instrument coverage; gettex matches the
-- code-based Xetra connector. Idempotent: UPDATE by short_id, safe to re-run.
-- =============================================================================
UPDATE generic_connector_def
  SET supported_categories = 'EQUITIES,FIXED_INCOME,ETF,MUTUAL_FUND,REAL_ESTATE_FUND,ISSUER_RISK_PRODUCT,CFD_DERIVATIVE',
      geo_restrictions = 'XMUN MUNC MUND'
  WHERE short_id = 'gettex';

UPDATE generic_connector_def
  SET supported_categories = 'EQUITIES',
      geo_restrictions = 'OTXB'
  WHERE short_id = 'otcx';

-- =============================================================================
-- Daily CUD limits for limited-rights users on shared historical price data.
-- Both Historyquote (live) and HistoryquoteLegacy (shadow archive) are public,
-- shared data; without a per-day cap a limited user could automate change
-- proposals against every quote row and bloat the propose-change tables (DoS).
-- The limit applies only to individual-record edits via the REST update path;
-- bulk provider/CSV history loading bypasses it. Admin-editable here; the
-- in-memory default (15) lives in GlobalParamKeyDefault. Idempotent.
-- =============================================================================
DELETE FROM globalparameters WHERE property_name = 'gt.limit.day.Historyquote';
INSERT INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.limit.day.Historyquote', 15, 0, 'min:1,max:100');

DELETE FROM globalparameters WHERE property_name = 'gt.limit.day.HistoryquoteLegacy';
INSERT INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.limit.day.HistoryquoteLegacy', 15, 0, 'min:1,max:100');
