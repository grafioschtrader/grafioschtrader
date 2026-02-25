export interface FeeModelComparisonDetail {
  transactionDate: string;
  transactionType: string;
  securityName: string;
  categoryType: string;
  specInvestInstrument: string;
  mic: string;
  currency: string;
  quotation: number;
  units: number;
  tradeValue: number;
  actualCost: number;
  estimatedCost: number;
  relativeError: number;
  matchedRuleName: string;
  error: string;
}

export interface FeeModelComparisonResponse {
  planName: string;
  totalTransactions: number;
  skippedCount: number;
  errorCount: number;
  comparedCount: number;
  meanActualCost: number;
  meanEstimatedCost: number;
  meanAbsoluteError: number;
  meanRelativeError: number;
  rmse: number;
  details: FeeModelComparisonDetail[];
}
