-- =============================================================================
-- Finanz und Wirtschaft (FUW) Generic Connector Configuration
-- Public, no-key JSON API at https://wp.fuw.ch/wp-json/fuw/v1/.
--   History : /marketdata/chartdata/{listingKey}?...&timeRange=max
--             payload.prices.max = array of [tsMillis, open, high, low, close, volume]
--   Intraday: /marketdata/quotes/{listingKey}?...&dataheader=true
--             Values[0] = object with currentPrice/openPrice/dailyHighPrice/... fields
-- Ticker = FUW listing key (e.g. 322646-44-814), keyType is fixed to LISTING_ID.
-- NOTE: FUW serves only a rolling ~10-year history window for every instrument.
-- Idempotent: safe to re-run.
-- =============================================================================

-- 0) Remove old fuw config (child tables cascade via FK ON DELETE CASCADE)
DELETE FROM generic_connector_def WHERE short_id = 'fuw';

-- 1a) Multilanguage description (help text shown in the security edit dialog)
INSERT INTO multilinguestring () VALUES ();
SET @fuw_nls_id = LAST_INSERT_ID();

DELETE FROM multilinguestrings WHERE id_string = @fuw_nls_id;
INSERT INTO multilinguestrings (id_string, language, text) VALUES
(@fuw_nls_id, 'en',
 '[historical]Enter the FUW <b>listing key</b> of a security shown on <a href="https://www.fuw.ch/" target="_blank">Finanz und Wirtschaft</a>. Open the instrument page on fuw.ch; its URL ends with <code>valor-VALOR/BC/CURRENCY</code> &ndash; enter these three numbers joined by hyphens, e.g. <b>322646-44-814</b> for Allianz (Xetra). No API key is required. <b>Important:</b> FUW provides only a rolling history of about the last 10 years (back to roughly today minus 10 years), regardless of when the instrument was actually launched; older end-of-day data is not available from this source.[intra]Same listing key as for historical data. Delivers the last price together with open, daily high, daily low, volume and the percentage change for the day.'),
(@fuw_nls_id, 'de',
 '[historical]Geben Sie den FUW-<b>Listing-Key</b> eines auf <a href="https://www.fuw.ch/" target="_blank">Finanz und Wirtschaft</a> aufgeführten Wertpapiers ein. Öffnen Sie die Instrumentenseite auf fuw.ch; ihre URL endet mit <code>valor-VALOR/BC/CURRENCY</code> &ndash; geben Sie diese drei Zahlen mit Bindestrichen verbunden ein, z.B. <b>322646-44-814</b> für Allianz (Xetra). Kein API-Schlüssel nötig. <b>Wichtig:</b> FUW liefert nur eine rollende Historie von rund 10 Jahren (zurück bis etwa heute minus 10 Jahre), unabhängig vom tatsächlichen Auflagedatum des Instruments; ältere End-of-Day-Daten sind über diese Quelle nicht verfügbar.[intra]Gleicher Listing-Key wie für die historischen Daten. Liefert den letzten Kurs zusammen mit Eröffnung, Tageshoch, Tagestief, Volumen und prozentualer Veränderung des Tages.');

-- 1b) Connector definition
INSERT INTO generic_connector_def (
  short_id, readable_name, domain_url, needs_api_key,
  rate_limit_type, rate_limit_requests, rate_limit_period_sec, rate_limit_concurrent,
  intraday_delay_seconds, regex_url_pattern,
  supports_security, supports_currency, need_history_gap_filler, gbx_divider_enabled,
  activated, supported_categories, geo_restrictions, description_nls
) VALUES (
  'fuw', 'Finanz und Wirtschaft', 'https://wp.fuw.ch/wp-json/fuw/v1/', 0,
  0, NULL, NULL, NULL,
  900, '^[0-9]+-[0-9]+-[0-9]+$',
  1, 0, 1, 0,
  1,
  'EQUITIES,NON_INVESTABLE_INDICES,CRYPTOCURRENCY,FIXED_INCOME,ETF,MUTUAL_FUND,ISSUER_RISK_PRODUCT,CFD_DERIVATIVE',
  NULL,
  @fuw_nls_id
);
SET @fuw_def_id = LAST_INSERT_ID();

-- 2) FS_HISTORY endpoint — full EOD history via /chartdata/...&timeRange=max
--    response_format=1 (JSON), number_format=4 (PLAIN), date_format_type=2 (UNIX_MILLIS),
--    json_data_structure=1 (ARRAY_OF_OBJECTS), ticker_build_strategy=1 (URL_EXTEND)
INSERT INTO generic_connector_endpoint (
  id_generic_connector, feed_support, instrument_type,
  url_template, http_method,
  response_format, number_format, date_format_type,
  json_data_structure, json_data_path, json_status_path, json_status_ok_value,
  ticker_build_strategy, ticker_uppercase
) VALUES (
  @fuw_def_id, 'FS_HISTORY', 'SECURITY',
  'marketdata/chartdata/{ticker}?keyType=LISTING_ID&dataSource=wfg&refCurrency=undefined&timeRange=max',
  'GET',
  1, 4, 2,
  1, 'payload.prices.max', 'status', 'Success',
  1, 0
);
SET @fuw_hist_ep_id = LAST_INSERT_ID();

-- 3) FS_HISTORY field mappings — each row is [ts_ms, open, high, low, close, volume]
INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required) VALUES
  (@fuw_hist_ep_id, 'date',   '0', 1),
  (@fuw_hist_ep_id, 'open',   '1', 0),
  (@fuw_hist_ep_id, 'high',   '2', 0),
  (@fuw_hist_ep_id, 'low',    '3', 0),
  (@fuw_hist_ep_id, 'close',  '4', 1),
  (@fuw_hist_ep_id, 'volume', '5', 0);

-- 4) FS_INTRA endpoint — single snapshot object at Values[0] via /quotes/...&dataheader=true
INSERT INTO generic_connector_endpoint (
  id_generic_connector, feed_support, instrument_type,
  url_template, http_method,
  response_format, number_format, date_format_type,
  json_data_structure, json_data_path, json_status_path, json_status_ok_value,
  ticker_build_strategy, ticker_uppercase
) VALUES (
  @fuw_def_id, 'FS_INTRA', 'SECURITY',
  'marketdata/quotes/{ticker}?keyType=LISTING_ID&dataSource=wfg&dataheader=true',
  'GET',
  1, 4, 2,
  1, 'Values', 'Status', 'Success',
  1, 0
);
SET @fuw_intra_ep_id = LAST_INSERT_ID();

-- 5) FS_INTRA field mappings — named fields of the quote object (volume is null for indices: skipped)
INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required) VALUES
  (@fuw_intra_ep_id, 'last',             'currentPrice',     1),
  (@fuw_intra_ep_id, 'open',             'openPrice',        0),
  (@fuw_intra_ep_id, 'high',             'dailyHighPrice',   0),
  (@fuw_intra_ep_id, 'low',              'dailyLowPrice',    0),
  (@fuw_intra_ep_id, 'volume',           'volume',           0),
  (@fuw_intra_ep_id, 'changePercentage', 'dailyChangeRel',   0),
  (@fuw_intra_ep_id, 'prevClose',        'previousDayClose', 0);


-- =============================================================================
-- Stock Analysis (stockanalysis.com) Generic Connector Configuration
-- Public, no-key internal JSON API at https://stockanalysis.com/.
--   History : /api/charts/{type}/{symbol}/MAX/c?chartiq=true
--             { "status":200, "data":[ {t:"YYYY-MM-DD", o, h, l, c, v}, ... ] },
--             full split-adjusted EOD history since inception.
--   Intraday: /api/quotes/{type}/{symbol}
--             { "status":200, "data": { p:last, cp:%change, cl:prevClose, o, h, l, v, ... } }
-- Ticker = the "{type}/{symbol}" path portion, stored verbatim in urlHistoryExtend /
-- urlIntraExtend (slash preserved by the connector, no URL-encoding). The type letter differs
-- between the two feeds:
--   History (charts) : US stock + ETF = e/SYMBOL  (e.g. e/nvda, e/qqq)
--                      International   = a/EXCH-SYMBOL  (e.g. a/FRA-RHM)
--   Intraday (quotes): US stock = s/SYMBOL (e.g. s/nvda), US ETF = e/SYMBOL (e.g. e/qqq),
--                      International = a/EXCH-SYMBOL (e.g. a/FRA-RHM)
-- EXCH is the exchange slug from the quote URL stockanalysis.com/quote/{exch}/{SYMBOL}/.
-- Shipped DEACTIVATED (activated=0): the data is non-redistributable and this is a private
-- endpoint, so an admin must enable it explicitly (POST /gt/genericconnector/reload).
-- Idempotent: safe to re-run.
-- =============================================================================

-- 0) Remove old stockanalysis config (child tables cascade via FK ON DELETE CASCADE)
DELETE FROM generic_connector_def WHERE short_id = 'stockanalysis';

-- 1a) Multilanguage description (help text shown in the security edit dialog)
INSERT INTO multilinguestring () VALUES ();
SET @sa_nls_id = LAST_INSERT_ID();

DELETE FROM multilinguestrings WHERE id_string = @sa_nls_id;
INSERT INTO multilinguestrings (id_string, language, text) VALUES
(@sa_nls_id, 'en',
 '[historical]Enter the <a href="https://stockanalysis.com/" target="_blank">stockanalysis.com</a> chart path of the instrument. For US stocks and ETFs use <b>e/SYMBOL</b> (e.g. <b>e/nvda</b>, <b>e/qqq</b>); for non-US listings use <b>a/EXCHANGE-SYMBOL</b> (e.g. <b>a/FRA-RHM</b> for Rheinmetall on Frankfurt). The exchange code is the slug in the quote URL <code>stockanalysis.com/quote/EXCHANGE/SYMBOL</code>. No API key is required. Delivers the full split-adjusted end-of-day history back to the instrument''s inception.[intra]Enter the quotes path: US stocks <b>s/SYMBOL</b> (e.g. <b>s/nvda</b>), US ETFs <b>e/SYMBOL</b> (e.g. <b>e/qqq</b>), non-US listings <b>a/EXCHANGE-SYMBOL</b> (e.g. <b>a/FRA-RHM</b>). Delivers the last price with day open, daily high, daily low, volume, previous close and the daily percentage change. Non-US quotes are delayed about 15 minutes.'),
(@sa_nls_id, 'de',
 '[historical]Geben Sie den Chart-Pfad des Instruments auf <a href="https://stockanalysis.com/" target="_blank">stockanalysis.com</a> ein. Für US-Aktien und -ETFs verwenden Sie <b>e/SYMBOL</b> (z.B. <b>e/nvda</b>, <b>e/qqq</b>); für nicht-US-Titel <b>a/BÖRSE-SYMBOL</b> (z.B. <b>a/FRA-RHM</b> für Rheinmetall an der Börse Frankfurt). Der Börsencode ist der Slug in der Quote-URL <code>stockanalysis.com/quote/BÖRSE/SYMBOL</code>. Kein API-Schlüssel nötig. Liefert die vollständige splitbereinigte End-of-Day-Historie zurück bis zur Auflage des Instruments.[intra]Geben Sie den Quotes-Pfad ein: US-Aktien <b>s/SYMBOL</b> (z.B. <b>s/nvda</b>), US-ETFs <b>e/SYMBOL</b> (z.B. <b>e/qqq</b>), nicht-US-Titel <b>a/BÖRSE-SYMBOL</b> (z.B. <b>a/FRA-RHM</b>). Liefert den letzten Kurs mit Tageseröffnung, Tageshoch, Tagestief, Volumen, Vortagesschluss und der prozentualen Tagesveränderung. Nicht-US-Kurse sind rund 15 Minuten verzögert.');

-- 1b) Connector definition (shipped deactivated; admin enables via /gt/genericconnector/reload)
INSERT INTO generic_connector_def (
  short_id, readable_name, domain_url, needs_api_key,
  rate_limit_type, rate_limit_requests, rate_limit_period_sec, rate_limit_concurrent,
  intraday_delay_seconds, regex_url_pattern,
  supports_security, supports_currency, need_history_gap_filler, gbx_divider_enabled,
  activated, supported_categories, geo_restrictions, description_nls
) VALUES (
  'stockanalysis', 'Stock Analysis', 'https://stockanalysis.com/', 0,
  0, NULL, NULL, NULL,
  900, '^[aAeEsS]/[A-Za-z0-9.-]+$',
  1, 0, 1, 0,
  0,
  'EQUITIES,NON_INVESTABLE_INDICES,FIXED_INCOME,ETF,MUTUAL_FUND,ISSUER_RISK_PRODUCT,CFD_DERIVATIVE',
  NULL,
  @sa_nls_id
);
SET @sa_def_id = LAST_INSERT_ID();

-- 2) FS_HISTORY endpoint — full EOD history via /api/charts/...&chartiq=true
--    response_format=1 (JSON), number_format=4 (PLAIN), date_format_type=4 (ISO_DATE: "YYYY-MM-DD"),
--    json_data_structure=1 (ARRAY_OF_OBJECTS); the array sits under "data" and the wrapper carries
--    a numeric "status":200 (same envelope as the quotes endpoint), ticker_build_strategy=1 (URL_EXTEND)
INSERT INTO generic_connector_endpoint (
  id_generic_connector, feed_support, instrument_type,
  url_template, http_method,
  response_format, number_format, date_format_type,
  json_data_structure, json_data_path, json_status_path, json_status_ok_value,
  ticker_build_strategy, ticker_uppercase
) VALUES (
  @sa_def_id, 'FS_HISTORY', 'SECURITY',
  'api/charts/{ticker}/MAX/c?chartiq=true',
  'GET',
  1, 4, 4,
  1, 'data', 'status', '200',
  1, 0
);
SET @sa_hist_ep_id = LAST_INSERT_ID();

-- 3) FS_HISTORY field mappings — each array element is an object {t, o, h, l, c, v}
INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required) VALUES
  (@sa_hist_ep_id, 'date',   't', 1),
  (@sa_hist_ep_id, 'open',   'o', 0),
  (@sa_hist_ep_id, 'high',   'h', 0),
  (@sa_hist_ep_id, 'low',    'l', 0),
  (@sa_hist_ep_id, 'close',  'c', 1),
  (@sa_hist_ep_id, 'volume', 'v', 0);

-- 4) FS_INTRA endpoint — single snapshot object under "data" via /api/quotes/...
--    json_data_structure=3 (SINGLE_OBJECT), json_data_path='data', status check on top-level "status"==200
INSERT INTO generic_connector_endpoint (
  id_generic_connector, feed_support, instrument_type,
  url_template, http_method,
  response_format, number_format, date_format_type,
  json_data_structure, json_data_path, json_status_path, json_status_ok_value,
  ticker_build_strategy, ticker_uppercase
) VALUES (
  @sa_def_id, 'FS_INTRA', 'SECURITY',
  'api/quotes/{ticker}',
  'GET',
  1, 4, 4,
  3, 'data', 'status', '200',
  1, 0
);
SET @sa_intra_ep_id = LAST_INSERT_ID();

-- 5) FS_INTRA field mappings — named fields of data{}: p=last, cp=%change, cl=prevClose, o/h/l/v
INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required) VALUES
  (@sa_intra_ep_id, 'last',             'p',  1),
  (@sa_intra_ep_id, 'open',             'o',  0),
  (@sa_intra_ep_id, 'high',             'h',  0),
  (@sa_intra_ep_id, 'low',              'l',  0),
  (@sa_intra_ep_id, 'volume',           'v',  0),
  (@sa_intra_ep_id, 'changePercentage', 'cp', 0),
  (@sa_intra_ep_id, 'prevClose',        'cl', 0);
