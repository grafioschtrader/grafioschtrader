-- Cash standing orders can now hold a base amount in a foreign currency plus an optional EvalEx
-- formula. This covers recurring charges that a bank bills in one currency but debits in another
-- (for example a monthly account fee of CHF 3.00 debited from a USD account), where the booked
-- amount changes every month with the exchange rate.
--
-- amount_currency            currency of cashaccount_amount when it differs from the cash account
--                            currency; NULL keeps the previous behaviour (amount used verbatim)
-- id_currency_pair           derived from amount_currency and the cash account currency at save
--                            time, so the pair exists and its history is loaded before the first
--                            execution
-- cashaccount_amount_formula optional EvalEx expression over a (base amount) and r (exchange rate
--                            on the execution date), e.g. 'a * r * 1.019'

ALTER TABLE standing_order_cashaccount
  ADD COLUMN IF NOT EXISTS amount_currency VARCHAR(3) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS id_currency_pair INT(11) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS cashaccount_amount_formula VARCHAR(200) DEFAULT NULL;

-- ADD CONSTRAINT has no IF NOT EXISTS in MariaDB, so drop first. Dropping the foreign key leaves
-- its backing index behind, which would collide with the re-add.
ALTER TABLE standing_order_cashaccount DROP FOREIGN KEY IF EXISTS fk_soc_currencypair;
DROP INDEX IF EXISTS fk_soc_currencypair ON standing_order_cashaccount;
ALTER TABLE standing_order_cashaccount ADD CONSTRAINT fk_soc_currencypair
  FOREIGN KEY (id_currency_pair) REFERENCES currencypair (id_securitycurrency);

-- How far the price or exchange rate may deviate from the execution date. 0 requires the quote to
-- exist exactly on that day. The magnitude is the window in days on BOTH sides, the sign decides
-- which side is tried first: a negative value prefers the past. The admissible range is capped by
-- the global parameter gt.standing.order.quote.tolerance.
-- TINYINT without a display width on purpose: TINYINT(1) is reported as BOOLEAN by the JDBC driver.
ALTER TABLE standing_order
  ADD COLUMN IF NOT EXISTS quote_tolerance_days TINYINT NOT NULL DEFAULT -3;

-- Bounds an administrator allows for quote_tolerance_days, written as 'min:max'. The default keeps
-- both directions open; '0:3' would forbid reaching into the past, '0:0' would enforce exact dates.
DELETE FROM globalparameters WHERE property_name = 'gt.standing.order.quote.tolerance';
INSERT INTO globalparameters (property_name, property_string, changed_by_system, input_rule)
  VALUES ('gt.standing.order.quote.tolerance', '-3:3', 0, 'pattern:^-?[0-3]:-?[0-3]$');

-- =============================================================================
-- Currency pair support for the FUW generic connector (seeded by V0_35_7)
--
-- FUW lists currency pairs and cryptocurrencies under /boerse/waehrungen/ and serves them through the
-- very same JSON API as securities, with an identical response shape. Only instrument_type differs,
-- so the URL templates and the field mappings below are copies of the SECURITY endpoints.
--
--   BTC/CHF  listing key 999999915312-9910014-1  (~3650 daily OHLCV rows)
--   EUR/CHF  listing key 897789-178-1            (~2600 daily OHLC rows, volume is null)
--
-- ticker_build_strategy = 1 (URL_EXTEND) rather than 2 (CURRENCY_PAIR): the listing key cannot be
-- derived from the two ISO currency codes, it is entered per currency pair in url_history_extend /
-- url_intra_extend and validated by the connector's regex_url_pattern '^[0-9]+-[0-9]+-[0-9]+$'.
--
-- Note that supports_currency is only the flag behind the UI checkbox. What actually makes the
-- connector selectable for a currency pair is the presence of these endpoint rows, from which
-- GenericFeedConnector.buildSupportedFeed() derives FeedIdentifier.CURRENCY_URL.
--
-- Idempotent: existing CURRENCY endpoints are removed first, and everything is skipped when an
-- administrator has deleted the fuw connector on this instance.
-- =============================================================================

DROP PROCEDURE IF EXISTS AddFuwCurrencyEndpoints;
DELIMITER //
CREATE PROCEDURE AddFuwCurrencyEndpoints()
BEGIN
  DECLARE v_def_id INT DEFAULT NULL;
  DECLARE v_nls_id INT DEFAULT NULL;
  DECLARE v_ep_id INT DEFAULT NULL;

  -- Scalar subqueries instead of SELECT ... INTO: they yield NULL without a warning when the
  -- connector is absent, so the whole block simply does nothing.
  SET v_def_id = (SELECT id_generic_connector FROM generic_connector_def WHERE short_id = 'fuw');
  SET v_nls_id = (SELECT description_nls FROM generic_connector_def WHERE short_id = 'fuw');

  IF v_def_id IS NOT NULL THEN
    -- CRYPTOCURRENCY (CURRENCY_PAIR + CFD) is already listed and covers BTC/CHF, but CURRENCY_PAIR
    -- (CURRENCY_PAIR + FOREX) is missing, so with gt.force.connector.match = 2 the connector would be
    -- filtered out of the dropdown of a fiat pair such as EUR/CHF. Prepended only when absent, so an
    -- administrator's own category selection survives.
    UPDATE generic_connector_def
       SET supports_currency = 1,
           supported_categories = CASE
             WHEN supported_categories IS NULL OR supported_categories = '' THEN 'CURRENCY_PAIR'
             WHEN FIND_IN_SET('CURRENCY_PAIR', supported_categories) > 0 THEN supported_categories
             ELSE CONCAT('CURRENCY_PAIR,', supported_categories)
           END
     WHERE id_generic_connector = v_def_id;

    -- Field mappings cascade with their endpoint (FK ON DELETE CASCADE)
    DELETE FROM generic_connector_endpoint
      WHERE id_generic_connector = v_def_id AND instrument_type = 'CURRENCY';

    -- FS_HISTORY — payload.prices.max is an array of [tsMillis, open, high, low, close, volume].
    -- response_format=1 (JSON), number_format=4 (PLAIN), date_format_type=2 (UNIX_MILLIS),
    -- json_data_structure=1 (ARRAY_OF_OBJECTS), ticker_build_strategy=1 (URL_EXTEND)
    INSERT INTO generic_connector_endpoint (
      id_generic_connector, feed_support, instrument_type,
      url_template, http_method,
      response_format, number_format, date_format_type,
      json_data_structure, json_data_path, json_status_path, json_status_ok_value,
      ticker_build_strategy, ticker_uppercase
    ) VALUES (
      v_def_id, 'FS_HISTORY', 'CURRENCY',
      'marketdata/chartdata/{ticker}?keyType=LISTING_ID&dataSource=wfg&refCurrency=undefined&timeRange=max',
      'GET',
      1, 4, 2,
      1, 'payload.prices.max', 'status', 'Success',
      1, 0
    );
    SET v_ep_id = LAST_INSERT_ID();

    -- Only date and close are mandatory; FX pairs are delivered without a volume.
    INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required) VALUES
      (v_ep_id, 'date',   '0', 1),
      (v_ep_id, 'open',   '1', 0),
      (v_ep_id, 'high',   '2', 0),
      (v_ep_id, 'low',    '3', 0),
      (v_ep_id, 'close',  '4', 1),
      (v_ep_id, 'volume', '5', 0);

    -- FS_INTRA — single snapshot object at Values[0]
    INSERT INTO generic_connector_endpoint (
      id_generic_connector, feed_support, instrument_type,
      url_template, http_method,
      response_format, number_format, date_format_type,
      json_data_structure, json_data_path, json_status_path, json_status_ok_value,
      ticker_build_strategy, ticker_uppercase
    ) VALUES (
      v_def_id, 'FS_INTRA', 'CURRENCY',
      'marketdata/quotes/{ticker}?keyType=LISTING_ID&dataSource=wfg&dataheader=true',
      'GET',
      1, 4, 2,
      1, 'Values', 'Status', 'Success',
      1, 0
    );
    SET v_ep_id = LAST_INSERT_ID();

    INSERT INTO generic_connector_field_mapping (id_endpoint, target_field, source_expression, is_required) VALUES
      (v_ep_id, 'last',             'currentPrice',     1),
      (v_ep_id, 'open',             'openPrice',        0),
      (v_ep_id, 'high',             'dailyHighPrice',   0),
      (v_ep_id, 'low',              'dailyLowPrice',    0),
      (v_ep_id, 'volume',           'volume',           0),
      (v_ep_id, 'changePercentage', 'dailyChangeRel',   0),
      (v_ep_id, 'prevClose',        'previousDayClose', 0);

    -- Help text of the connector: the currency pair edit dialog shows the same description, so it has
    -- to explain where the listing key of a currency pair is found.
    IF v_nls_id IS NOT NULL THEN
      DELETE FROM multilinguestrings WHERE id_string = v_nls_id;
      INSERT INTO multilinguestrings (id_string, language, text) VALUES
      (v_nls_id, 'en',
       '[historical]Enter the FUW <b>listing key</b> of an instrument shown on <a href="https://www.fuw.ch/" target="_blank">Finanz und Wirtschaft</a>. Open its page on fuw.ch; the URL ends with <code>valor-VALOR/BC/CURRENCY</code> &ndash; enter these three numbers joined by hyphens, e.g. <b>322646-44-814</b> for Allianz (Xetra). Currency pairs and cryptocurrencies are listed under <code>/boerse/waehrungen/</code> and use the same notation, e.g. <b>897789-178-1</b> for EUR/CHF or <b>999999915312-9910014-1</b> for BTC/CHF. No API key is required. <b>Important:</b> FUW provides only a rolling history of about the last 10 years (back to roughly today minus 10 years), regardless of when the instrument was actually launched; older end-of-day data is not available from this source.[intra]Same listing key as for historical data. Delivers the last price together with open, daily high, daily low, volume and the percentage change for the day. Currency pairs are delivered without a volume.'),
      (v_nls_id, 'de',
       '[historical]Geben Sie den FUW-<b>Listing-Key</b> eines auf <a href="https://www.fuw.ch/" target="_blank">Finanz und Wirtschaft</a> aufgeführten Instruments ein. Öffnen Sie dessen Seite auf fuw.ch; die URL endet mit <code>valor-VALOR/BC/CURRENCY</code> &ndash; geben Sie diese drei Zahlen mit Bindestrichen verbunden ein, z.B. <b>322646-44-814</b> für Allianz (Xetra). Währungspaare und Kryptowährungen sind unter <code>/boerse/waehrungen/</code> aufgeführt und verwenden dieselbe Schreibweise, z.B. <b>897789-178-1</b> für EUR/CHF oder <b>999999915312-9910014-1</b> für BTC/CHF. Kein API-Schlüssel nötig. <b>Wichtig:</b> FUW liefert nur eine rollende Historie von rund 10 Jahren (zurück bis etwa heute minus 10 Jahre), unabhängig vom tatsächlichen Auflagedatum des Instruments; ältere End-of-Day-Daten sind über diese Quelle nicht verfügbar.[intra]Gleicher Listing-Key wie für die historischen Daten. Liefert den letzten Kurs zusammen mit Eröffnung, Tageshoch, Tagestief, Volumen und prozentualer Veränderung des Tages. Währungspaare werden ohne Volumen geliefert.');
    END IF;
  END IF;
END //
DELIMITER ;

CALL AddFuwCurrencyEndpoints();
DROP PROCEDURE IF EXISTS AddFuwCurrencyEndpoints;
