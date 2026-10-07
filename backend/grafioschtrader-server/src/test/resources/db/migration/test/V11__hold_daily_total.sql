-- Mirrors the hold_daily_total section of V0_38_1 (GitHub issue #270).

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
