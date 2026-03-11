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
}

export interface TaxUpload {
  idTaxUpload: number;
  idTaxYear: number;
  fileName: string;
  filePath: string;
  uploadDate: string;
  recordCount: number;
}
