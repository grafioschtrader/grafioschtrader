/**
 * Manual per-tenant override of the ICTax-computed taxable income (ictaxTotalPaymentValueChf) for one security
 * and one tax year. Either the position's computed taxableAmountMC is used instead (useTaxableAmount) or a
 * directly entered value (taxableIncome) replaces the ICTax figure; both override fields are mutually exclusive.
 * A record may also be comment-only (note set, no override).
 */
export class TaxYearCorrection {
  idTaxYearCorrection: number = null;
  idSecuritycurrency: number = null;
  taxYear: number = null;
  useTaxableAmount = false;
  taxableIncome: number = null;
  note: string = null;
}

/**
 * Payload of the tax year correction maintenance dialog for one security: the tenant's existing correction
 * records across all tax years plus the tax years for which ICTax data exists for the security's ISIN.
 */
export interface TaxYearCorrectionSecurityInfo {
  corrections: TaxYearCorrection[];
  ictaxTaxYears: number[];
}
