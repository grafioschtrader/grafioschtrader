-- =============================================================================
-- Total return in the correlation matrix (GitHub issue #269)
--
-- A correlation set may add the reinvested gross dividends to the prices of every
-- dividend-capable security before the returns are calculated. Off by default, so existing sets keep their
-- price-return results.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS.
-- =============================================================================

ALTER TABLE correlation_set ADD COLUMN IF NOT EXISTS include_dividends TINYINT(1) NOT NULL DEFAULT 0
  COMMENT 'Add reinvested dividends to the prices before the returns are calculated' AFTER adjust_currency;

-- =============================================================================
-- Portfolio indexes on the hold tables (GitHub issue #273)
--
-- The *ByPortfolio hold queries filter by id_portfolio alone, but every existing index containing it starts with
-- id_tenant, so MariaDB scanned the whole table for each evaluated trading day. An index leading with
-- id_portfolio and from_hold_date serves those queries directly.
--
-- Idempotent: DROP INDEX IF EXISTS before each ADD INDEX.
-- =============================================================================

DROP INDEX IF EXISTS idPortfolioFromHoldDateHCS ON hold_cashaccount_balance;
ALTER TABLE hold_cashaccount_balance ADD INDEX idPortfolioFromHoldDateHCS (id_portfolio, from_hold_date);
DROP INDEX IF EXISTS idPortfolioFromHoldDateHSS ON hold_securityaccount_security;
ALTER TABLE hold_securityaccount_security ADD INDEX idPortfolioFromHoldDateHSS (id_portfolio, from_hold_date);
DROP INDEX IF EXISTS idPortfolioFromHoldDateHCD ON hold_cashaccount_deposit;
ALTER TABLE hold_cashaccount_deposit ADD INDEX idPortfolioFromHoldDateHCD (id_portfolio, from_hold_date);

-- =============================================================================
-- Yahoo exchange code of NYSE Arca
--
-- Yahoo identifies NYSE Arca listings (e.g. SPY) by the exchange code PCX, not PSE. With PSE the security lookup
-- could not place such a listing on ARCX and dropped it.
--
-- Idempotent: a plain UPDATE of one mapping row.
-- =============================================================================

UPDATE mic_provider_map SET code_provider = 'PCX' WHERE id_provider = 'yahoo' AND mic = 'ARCX';

-- =============================================================================
-- Yahoo exchange mapping of the Vienna Stock Exchange
--
-- Yahoo identifies Vienna listings by the exchange code VIE and the symbol suffix .VI (e.g. ANDR.VI). The mapping row
-- pointed to WBAH without suffix, which is no stock exchange in GT - Vienna is XVIE - so the security lookup dropped
-- every Yahoo listing of Vienna and could not derive a Yahoo symbol there.
--
-- Idempotent: the row is deleted under both MICs and inserted again.
-- =============================================================================

DELETE FROM mic_provider_map WHERE id_provider = 'yahoo' AND mic IN ('WBAH', 'XVIE');
INSERT INTO mic_provider_map (id_provider, mic, code_provider, symbol_suffix) VALUES ('yahoo', 'XVIE', 'VIE', '.VI');

-- =============================================================================
-- EODHD exchange codes
--
-- The EODHD search answers an ISIN with one listing per exchange, identified by an exchange code (US, XETRA, SW, F,
-- LSE ...), and the EODHD connector addresses an instrument as ticker.code (SAP.XETRA). The rows place a search hit on
-- a GT stock exchange and let the connector derive its URL extension from a listing another provider found. The codes
-- come from the EODHD exchanges-list API; where EODHD names another MIC than GT - Toronto XTSE, Vienna XWBO, Madrid
-- BMEX, Dublin XDUB, Hamburg XHAM, Cairo NILX - the row uses the GT MIC. US stands for every US exchange, so both
-- XNAS and XNYS carry it. The symbol suffix is always "." plus the code and longer than the column, so it stays NULL.
--
-- Idempotent: all EODHD rows are deleted and inserted again.
-- =============================================================================

DELETE FROM mic_provider_map WHERE id_provider = 'eodhd';
INSERT INTO mic_provider_map (id_provider, mic, code_provider, symbol_suffix) VALUES
('eodhd', 'XNAS', 'US', NULL),
('eodhd', 'XNYS', 'US', NULL),
('eodhd', 'XLON', 'LSE', NULL),
('eodhd', 'XTSX', 'TO', NULL),
('eodhd', 'XFRA', 'F', NULL),
('eodhd', 'XSTU', 'STU', NULL),
('eodhd', 'XMUN', 'MU', NULL),
('eodhd', 'HAMA', 'HM', NULL),
('eodhd', 'XETR', 'XETRA', NULL),
('eodhd', 'XVIE', 'VI', NULL),
('eodhd', 'XPAR', 'PA', NULL),
('eodhd', 'XBRU', 'BR', NULL),
('eodhd', 'XSWX', 'SW', NULL),
('eodhd', 'XMAD', 'MC', NULL),
('eodhd', 'XLIS', 'LS', NULL),
('eodhd', 'XAMS', 'AS', NULL),
('eodhd', 'XCSE', 'CO', NULL),
('eodhd', 'XSTO', 'ST', NULL),
('eodhd', 'XOSL', 'OL', NULL),
('eodhd', 'XMSM', 'IR', NULL),
('eodhd', 'XKRX', 'KO', NULL),
('eodhd', 'XWAR', 'WAR', NULL),
('eodhd', 'XASX', 'AU', NULL),
('eodhd', 'XSHE', 'SHE', NULL),
('eodhd', 'XSHG', 'SHG', NULL),
('eodhd', 'XIDX', 'JK', NULL),
('eodhd', 'BVMF', 'SA', NULL),
('eodhd', 'XCAI', 'EGX', NULL);

-- =============================================================================
-- onvista exchange codes
--
-- onvista quotes an instrument per trading venue, identified by its own exchange code (GER for Xetra, STU, NYS, MIL
-- ...) and a notation; the notation is part of the URL extension of the onvista connector. The rows place a venue of
-- the onvista search on a GT stock exchange. The codes are taken from onvista snapshots; venues without GT stock
-- exchange (Tradegate, gettex, Lang & Schwarz, Quotrix, Hannover, Düsseldorf) have no row.
--
-- Idempotent: all onvista rows are deleted and inserted again.
-- =============================================================================

DELETE FROM mic_provider_map WHERE id_provider = 'onvista';
INSERT INTO mic_provider_map (id_provider, mic, code_provider, symbol_suffix) VALUES
('onvista', 'XETR', 'GER', NULL),
('onvista', 'XFRA', 'FRA', NULL),
('onvista', 'XSTU', 'STU', NULL),
('onvista', 'XMUN', 'MUN', NULL),
('onvista', 'HAMA', 'HAM', NULL),
('onvista', 'XSWX', 'SWX', NULL),
('onvista', 'XVIE', 'VIE', NULL),
('onvista', 'XNYS', 'NYS', NULL),
('onvista', 'XNAS', 'NMS', NULL),
('onvista', 'XLON', 'LSE', NULL),
('onvista', 'XAMS', 'AMS', NULL),
('onvista', 'XPAR', 'PAR', NULL),
('onvista', 'XBRU', 'BRU', NULL),
('onvista', 'XLIS', 'LIS', NULL),
('onvista', 'XMIL', 'MIL', NULL),
('onvista', 'XMAD', 'MCE', NULL);

-- =============================================================================
-- Boursorama market prefixes
--
-- A Boursorama symbol starts with a market prefix (1rP Euronext Paris, 1rA Amsterdam, 1rL Lisbon, 1z Xetra, 1u
-- London with suffix .L, 2a SIX, 1g Milan, 3t Toronto); a US symbol has none and is stored under the code US for both
-- US exchanges, its exchange being named by the venue of the search hit. The rows place a hit of the Boursorama search
-- on a GT stock exchange and let the Boursorama connector build its symbol from a ticker another provider found. The
-- prefixes are taken from Boursorama search answers; markets without a GT stock exchange have no row.
--
-- Idempotent: all Boursorama rows are deleted and inserted again.
-- =============================================================================

DELETE FROM mic_provider_map WHERE id_provider = 'boursorama';
INSERT INTO mic_provider_map (id_provider, mic, code_provider, symbol_suffix) VALUES
('boursorama', 'XPAR', '1rP', NULL),
('boursorama', 'XAMS', '1rA', NULL),
('boursorama', 'XLIS', '1rL', NULL),
('boursorama', 'XETR', '1z', NULL),
('boursorama', 'XLON', '1u', '.L'),
('boursorama', 'XSWX', '2a', NULL),
('boursorama', 'XMIL', '1g', NULL),
('boursorama', 'XTSX', '3t', NULL),
('boursorama', 'XNAS', 'US', NULL),
('boursorama', 'XNYS', 'US', NULL);

-- =============================================================================
-- Stock Analysis becomes a built-in connector
--
-- The connector StockAnalysisFeedConnector replaces the generic definition 'stockanalysis' under the same id, so
-- securities keep gt.datafeed.stockanalysis. Endpoints, field mappings and HTTP headers of the definition go with it
-- through ON DELETE CASCADE; its description text is removed separately.
--
-- The rows of mic_provider_map hold the exchange code of the Stock Analysis path a/{CODE}-{TICKER}; the code US marks
-- the US exchanges, whose path is s/{ticker}. The security lookup derives the path from a ticker another provider
-- found. German regional exchanges have no code at Stock Analysis.
--
-- Idempotent: the definition is deleted only when present, all Stock Analysis rows are deleted and inserted again.
-- =============================================================================

SET @sa_nls_id = (SELECT description_nls FROM generic_connector_def WHERE short_id = 'stockanalysis');
DELETE FROM generic_connector_def WHERE short_id = 'stockanalysis';
DELETE FROM multilinguestrings WHERE id_string = @sa_nls_id;
DELETE FROM multilinguestring WHERE id = @sa_nls_id;

DELETE FROM mic_provider_map WHERE id_provider = 'stockanalysis';
INSERT INTO mic_provider_map (id_provider, mic, code_provider, symbol_suffix) VALUES
('stockanalysis', 'XNAS', 'US', NULL),
('stockanalysis', 'XNYS', 'US', NULL),
('stockanalysis', 'XSWX', 'SWX', NULL),
('stockanalysis', 'XETR', 'ETR', NULL),
('stockanalysis', 'XFRA', 'FRA', NULL),
('stockanalysis', 'XPAR', 'EPA', NULL),
('stockanalysis', 'XAMS', 'AMS', NULL),
('stockanalysis', 'XBRU', 'EBR', NULL),
('stockanalysis', 'XLIS', 'ELI', NULL),
('stockanalysis', 'XMIL', 'BIT', NULL),
('stockanalysis', 'XMAD', 'BME', NULL),
('stockanalysis', 'XVIE', 'VIE', NULL),
('stockanalysis', 'XLON', 'LON', NULL),
('stockanalysis', 'XMSM', 'ISE', NULL),
('stockanalysis', 'XTSX', 'TSX', NULL),
('stockanalysis', 'XASX', 'ASX', NULL),
('stockanalysis', 'XCSE', 'CPH', NULL),
('stockanalysis', 'XSTO', 'STO', NULL),
('stockanalysis', 'XOSL', 'OSL', NULL),
('stockanalysis', 'XWAR', 'WSE', NULL),
('stockanalysis', 'XHKG', 'HKG', NULL),
('stockanalysis', 'XTKS', 'TYO', NULL),
('stockanalysis', 'XSHG', 'SHA', NULL),
('stockanalysis', 'XSHE', 'SHE', NULL),
('stockanalysis', 'XKRX', 'KRX', NULL),
('stockanalysis', 'XIDX', 'IDX', NULL),
('stockanalysis', 'BVMF', 'BVMF', NULL),
('stockanalysis', 'XNZE', 'NZE', NULL),
('stockanalysis', 'XCAI', 'EGX', NULL);

-- =============================================================================
-- Consorsbank exchange codes
--
-- The Consorsbank search lists every notation of an ISIN with its exchange code; these rows place a code on a GT
-- stock exchange, and the notation plus code is the URL extension of the Consorsbank connector. US shares are quoted
-- only as Nasdaq Basic (NSB), which therefore stands on both US exchanges and is placed on the queried one. Venues
-- without a GT stock exchange (Tradegate, gettex, Quotrix, Lang & Schwarz, Düsseldorf, Hannover, off-exchange quotes)
-- have no row.
--
-- Idempotent: all Consorsbank rows are deleted and inserted again.
-- =============================================================================

DELETE FROM mic_provider_map WHERE id_provider = 'consorsbank';
INSERT INTO mic_provider_map (id_provider, mic, code_provider, symbol_suffix) VALUES
('consorsbank', 'XETR', 'GER', NULL),
('consorsbank', 'XFRA', 'FRA', NULL),
('consorsbank', 'XSTU', 'STU', NULL),
('consorsbank', 'XMUN', 'MUN', NULL),
('consorsbank', 'HAMA', 'HAM', NULL),
('consorsbank', 'XSWX', 'SWX', NULL),
('consorsbank', 'XVIE', 'VIE', NULL),
('consorsbank', 'XPAR', 'PAR', NULL),
('consorsbank', 'XAMS', 'AMS', NULL),
('consorsbank', 'XBRU', 'BRU', NULL),
('consorsbank', 'XLIS', 'LIS', NULL),
('consorsbank', 'XMIL', 'MIL', NULL),
('consorsbank', 'XMAD', 'MCE', NULL),
('consorsbank', 'XLON', 'LSE', NULL),
('consorsbank', 'XMSM', 'ISE', NULL),
('consorsbank', 'XTSX', 'TOR', NULL),
('consorsbank', 'XASX', 'ASX', NULL),
('consorsbank', 'XCSE', 'CPH', NULL),
('consorsbank', 'XSTO', 'STO', NULL),
('consorsbank', 'XOSL', 'OSL', NULL),
('consorsbank', 'XTKS', 'TYO', NULL),
('consorsbank', 'XNAS', 'NSB', NULL),
('consorsbank', 'XNYS', 'NSB', NULL);

-- =============================================================================
-- Yahoo codes of the GT stock exchanges Milan, Dublin and Hamburg
--
-- The Yahoo rows follow the ISO MIC list, which names Milan by its segment MTAA, Dublin as XDUB and Hamburg as XHAM;
-- GT keeps these exchanges as XMIL, XMSM and HAMA. Without a row of the GT MIC the security lookup neither placed a
-- Yahoo hit on the exchange nor derived the Yahoo symbol, so Yahoo was missing as connector, e.g. as split connector
-- for Intesa Sanpaolo (ISP.MI). Exchange code and suffix were checked against Yahoo: ISP.MI (MIL), RYA.IR (ISE),
-- SAP.HM (HAM).
--
-- Idempotent: the three rows are deleted and inserted again.
-- =============================================================================

DELETE FROM mic_provider_map WHERE id_provider = 'yahoo' AND mic IN ('XMIL', 'XMSM', 'HAMA');
INSERT INTO mic_provider_map (id_provider, mic, code_provider, symbol_suffix) VALUES
('yahoo', 'XMIL', 'MIL', '.MI'),
('yahoo', 'XMSM', 'ISE', '.IR'),
('yahoo', 'HAMA', 'HAM', '.HM');

-- =============================================================================
-- Yahoo suffix of the Bolsa Institucional de Valores (BIVA)
--
-- Yahoo quotes Mexican shares only with the suffix .MX (exchange code MEX, e.g. GMEXICOB.MX); a symbol without suffix
-- is unknown to Yahoo. The BIVA row had no suffix, so a Yahoo hit was not placed on BIVA and the lookup offered the bare
-- ticker. The exchange code stays BIV.
--
-- Idempotent: an UPDATE of one column.
-- =============================================================================

UPDATE mic_provider_map SET symbol_suffix = '.MX' WHERE id_provider = 'yahoo' AND mic = 'BIVA';

-- =============================================================================
-- EODHD exchange code of the Bolsa Institucional de Valores (BIVA)
--
-- EODHD quotes Mexican shares under the exchange code MX (e.g. GMEXICOB.MX), which its exchange list names with the
-- MIC of the Bolsa Mexicana; GT keeps the Mexican exchange as BIVA. Without the row the EODHD search hit was not placed
-- on BIVA and the EODHD connector derived no symbol.
--
-- Idempotent: the row is deleted and inserted again.
-- =============================================================================

DELETE FROM mic_provider_map WHERE id_provider = 'eodhd' AND mic = 'BIVA';
INSERT INTO mic_provider_map (id_provider, mic, code_provider, symbol_suffix) VALUES ('eodhd', 'BIVA', 'MX', NULL);

-- =============================================================================
-- Longer exchange ticker of a security
--
-- Six characters do not hold every exchange ticker: a Mexican listing combines issuer code and series (GMEXICO B,
-- GMEXICOB at the data providers), and NSE symbols reach ten characters. The security lookup takes such a ticker over,
-- which the form then rejected. Both columns follow GlobalConstants.FIELD_SIZE_MAX_Security_Ticker_Symbol.
--
-- Idempotent: MODIFY COLUMN to the same type is a no-op.
-- =============================================================================

ALTER TABLE security MODIFY COLUMN ticker_symbol VARCHAR(12) DEFAULT NULL;
ALTER TABLE gt_net_security_imp_pos MODIFY COLUMN ticker_symbol VARCHAR(12) DEFAULT NULL;

-- =============================================================================
-- No distribution frequency for a non-investable index
--
-- An index pays nothing, so the security dialog no longer offers the distribution frequency for it and
-- Security.clearProperties() stores DF_NONE (0). Indices entered earlier with another frequency are reset.
--
-- Idempotent: an UPDATE that only touches rows still deviating.
-- =============================================================================

UPDATE security s JOIN assetclass a ON a.id_asset_class = s.id_asset_class
  SET s.dist_frequency = 0 WHERE a.spec_invest_instrument = 10 AND s.dist_frequency <> 0;

-- =============================================================================
-- Websites of the Korea Exchange and the Egyptian Exchange
--
-- The hosts eng.krx.co.kr, www.egyptse.com and www.nilex.egyptse.com no longer exist in DNS. The English site of the
-- Korea Exchange is global.krx.co.kr; the Egyptian Exchange, including its former Nile Stock Exchange, is
-- www.egx.com.eg. Only the dead values are replaced, so a website a user maintained otherwise stays as it is.
--
-- Idempotent: UPDATE of the old values only.
-- =============================================================================

UPDATE stockexchange SET website = 'https://global.krx.co.kr'
  WHERE website IN ('http://eng.krx.co.kr', 'https://eng.krx.co.kr', '//eng.krx.co.kr', 'eng.krx.co.kr');
UPDATE stockexchange_mic SET website = 'https://global.krx.co.kr'
  WHERE website IN ('http://eng.krx.co.kr', 'https://eng.krx.co.kr', '//eng.krx.co.kr', 'eng.krx.co.kr');
UPDATE stockexchange SET website = 'https://www.egx.com.eg'
  WHERE website IN ('//www.egyptse.com', 'http://www.egyptse.com', 'https://www.egyptse.com', 'www.egyptse.com',
    '//www.nilex.egyptse.com');
UPDATE stockexchange_mic SET website = 'https://www.egx.com.eg'
  WHERE website IN ('//www.egyptse.com', 'http://www.egyptse.com', 'https://www.egyptse.com', 'www.egyptse.com',
    '//www.nilex.egyptse.com');
