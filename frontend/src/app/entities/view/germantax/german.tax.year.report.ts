/** A sum of the German tax report. It is complete or it has no amount at all. */
export interface GermanTaxSum {
  /** SHARES, OTHER_INCOME, FUNDS, FOREIGN_TAX, WITHHELD, UNREVIEWED or LEGACY. */
  block: string;
  /** Only in the blocks WITHHELD, UNREVIEWED and LEGACY. */
  category?: string;
  line: string;
  /** Name of the fund class, only for funds. */
  fundTaxClass?: string;
  amountEur?: number;
  /** Only for funds; the form takes the amount before the partial exemption. */
  amountAfterPartialExemptionEur?: number;
  complete: boolean;
}

/** A gap in the derivation and the sums it leaves without an amount. */
export interface GermanTaxWarning {
  /** The cause, for example MISSING_RATE. */
  code: string;
  year?: number;
  idTransaction?: number;
  idSecuritycurrency?: number;
  securityName?: string;
  detail?: string;
  affectedSums: string[];
}

/** A lot or a part of it a sale consumed. */
export interface GermanTaxLotPart {
  idTransaction?: number;
  acquisitionTime?: string;
  units: number;
  costEur: number;
  preliminaryEur: number;
}

/** A row of the derivation. */
export interface GermanTaxDetail {
  kind: string;
  date?: string;
  idTransaction?: number;
  idSecuritycurrency?: number;
  securityName?: string;
  isin?: string;
  idSecurityaccount?: number;
  idPortfolio?: number;
  block?: string;
  category?: string;
  fundTaxClass?: string;
  currency?: string;
  units?: number;
  grossEur?: number;
  proceedsEur?: number;
  costEur?: number;
  gainEur?: number;
  preliminaryDeductedEur?: number;
  foreignTaxEur?: number;
  taxCeilingEur?: number;
  rate?: number;
  rateSource?: string;
  complete: boolean;
  lots: GermanTaxLotPart[];
}

/** A booking of an instrument that is in no sum. */
export interface GermanTaxUnclassified {
  /** SALE_PROCEEDS, INCOME or MARGIN_CLOSE. */
  amountKind: string;
  reason: string;
  idTransaction?: number;
  idSecuritycurrency?: number;
  securityName?: string;
  date?: string;
  amountEur?: number;
}

/** A portfolio and the withholding assigned to it. */
export interface GermanTaxPortfolioAssignment {
  idPortfolio: number;
  name: string;
  /** Name of TaxWithholding, absent while the portfolio is not assigned. */
  taxWithholding?: string;
  hasAccounts: boolean;
}

/** The starting values of a tax year for the German tax return. It is no tax calculation. */
export interface GermanTaxYearReport {
  taxYear: number;
  provisional: boolean;
  mainCurrency: string;
  portfolioAssignmentRequired: boolean;
  portfolios: GermanTaxPortfolioAssignment[];
  sums: GermanTaxSum[];
  warnings: GermanTaxWarning[];
  unclassified: GermanTaxUnclassified[];
  details: GermanTaxDetail[];
}
