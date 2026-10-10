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
