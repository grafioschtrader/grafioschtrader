export interface GenericConnectorTestRequest {
  idGenericConnector: number;
  feedSupport: string;
  instrumentType: string;
  ticker?: string;
  fromCurrency?: string;
  toCurrency?: string;
  fromDate?: string;
  toDate?: string;
}

export interface GenericConnectorTestResult {
  success: boolean;
  errorMessage: string;
  requestUrl: string;
  httpStatus: number;
  rawResponseSnippet: string;
  parsedRows: { [key: string]: string }[];
  executionTimeMs: number;
}
