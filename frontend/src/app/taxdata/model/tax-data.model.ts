export interface TaxCountry {
  idTaxCountry: number;
  countryCode: string;
  taxYears?: TaxYear[];
}

export interface TaxYear {
  idTaxYear: number;
  idTaxCountry: number;
  taxYear: number;
  taxUploads?: TaxUpload[];
  /** Number of official exchange rates imported for this year; 0 means there is nothing to expand. */
  exchangeRateCount?: number;
}

export interface TaxUpload {
  idTaxUpload: number;
  idTaxYear: number;
  fileName: string;
  filePath: string;
  uploadDate: string;
  recordCount: number;
}

/**
 * Official year-end and annual mean exchange rate of one currency for one tax year, as published by the tax authority.
 * Only the two override fields are writable — they correct a published rate, for instance one rounded too coarsely.
 */
export interface IctaxExchangeRate {
  idIctaxExchangeRate: number;
  idTaxYear: number;
  currency: string;
  /** Number of currency units both published rates refer to; the Kursliste quotes JPY and DKK per 100. */
  denomination: number;
  yearEndRate: number;
  annualMeanRate: number;
  yearEndRateOverride: number;
  annualMeanRateOverride: number;
}
