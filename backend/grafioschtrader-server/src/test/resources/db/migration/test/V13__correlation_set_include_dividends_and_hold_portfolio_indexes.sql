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
