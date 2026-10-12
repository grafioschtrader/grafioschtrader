-- Mirrors the total return switch of the correlation set of V0_38_2 (GitHub issue #269). Off, as in production.
ALTER TABLE correlation_set ADD COLUMN IF NOT EXISTS include_dividends TINYINT(1) NOT NULL DEFAULT 0
  COMMENT 'Add reinvested dividends to the prices before the returns are calculated' AFTER adjust_currency;

-- Mirrors the portfolio indexes on the hold tables of V0_38_2 (GitHub issue #273).
DROP INDEX IF EXISTS idPortfolioFromHoldDateHCS ON hold_cashaccount_balance;
ALTER TABLE hold_cashaccount_balance ADD INDEX idPortfolioFromHoldDateHCS (id_portfolio, from_hold_date);
DROP INDEX IF EXISTS idPortfolioFromHoldDateHSS ON hold_securityaccount_security;
ALTER TABLE hold_securityaccount_security ADD INDEX idPortfolioFromHoldDateHSS (id_portfolio, from_hold_date);
DROP INDEX IF EXISTS idPortfolioFromHoldDateHCD ON hold_cashaccount_deposit;
ALTER TABLE hold_cashaccount_deposit ADD INDEX idPortfolioFromHoldDateHCD (id_portfolio, from_hold_date);

-- =============================================================================
-- Longer exchange ticker of a security
--
-- Mirrors V0_38_2. Six characters do not hold every exchange ticker: a Mexican listing combines issuer code and series (GMEXICO B,
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
