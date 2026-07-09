-- Records the rounding difference between the calculated cash amount (units * quotation +/- costs)
-- and the imported (bank/document) amount for a transaction created through the document import.
-- The cash account is booked with the exact imported amount; this column keeps the residual so the
-- security-side math (units * quotation) and the cash-side amount stay reconciled. Nullable: only
-- set when an import accepted a rounding difference within the template's calcRounding tolerance.
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS cashaccount_rounding_diff DOUBLE NULL;

-- Accepted rounding tolerance (calcRounding) resolved from the import template for the position's
-- cash-account currency, persisted at import time so it is still available when the transaction is
-- created later. Nullable: only set when the template configures a tolerance for that currency.
ALTER TABLE imp_trans_pos ADD COLUMN IF NOT EXISTS calc_rounding_step DOUBLE NULL;

-- Manual per-tenant override of the ICTax-computed taxable income (ictaxTotalPaymentValueChf) for one
-- security and tax year. use_taxable_amount = 1 replaces the value with the position's taxableAmountMC;
-- alternatively taxable_income holds a directly entered value. The two override fields are mutually
-- exclusive (enforced in TaxYearCorrectionJpaRepositoryImpl). A row may also be comment-only (note set,
-- no override) to document a tax position.
CREATE TABLE IF NOT EXISTS tax_year_correction (
  id_tax_year_correction INT NOT NULL AUTO_INCREMENT,
  id_tenant              INT NOT NULL,
  id_securitycurrency    INT NOT NULL,
  tax_year               SMALLINT NOT NULL,
  use_taxable_amount     TINYINT(1) NOT NULL DEFAULT 0,
  taxable_income         DOUBLE NULL,
  note                   VARCHAR(1024) NULL,
  PRIMARY KEY (id_tax_year_correction),
  UNIQUE KEY uq_tyc_tenant_year_security (id_tenant, tax_year, id_securitycurrency),
  CONSTRAINT fk_tyc_tenant FOREIGN KEY (id_tenant) REFERENCES tenant (id_tenant) ON DELETE CASCADE,
  CONSTRAINT fk_tyc_security FOREIGN KEY (id_securitycurrency)
    REFERENCES securitycurrency (id_securitycurrency) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- stockanalysis.com quotes LSE securities in pence (GBX) and JSE securities in cents (ZAc), verified via
-- api/quotes (LON-GSK p=1993 pence, JSE-SOL p=16182 cents). Enable the minor-unit divider so prices of
-- XLON/XJSE securities fed by this generic connector are stored in the major currency (GitHub issue #10).
UPDATE generic_connector_def SET gbx_divider_enabled = 1 WHERE short_id = 'stockanalysis';

-- Library-owned GTNet keys missed by the V0_33_16 g.gnet rename (prefix convention, issue #75):
-- keys read by grafiosch-server-base must use the g. prefix. Idempotent: a re-run matches 0 rows.
UPDATE globalparameters SET property_name = 'g.gnet.del.message.recv' WHERE property_name = 'gt.gtnet.del.message.recv';
UPDATE globalparameters SET property_name = 'g.gnet.log.aggregate.days' WHERE property_name = 'gt.gtnet.log.aggregate.days';
