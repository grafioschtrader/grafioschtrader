export interface TransactionCostEstimateRequest {
  idTradingPlatformPlan: number;
  tradeValue?: number;
  units?: number;
  specInvestInstrument?: number;
  categoryType?: number;
  mic?: string;
  currency?: string;
  fixedAssets?: number;
  tradeDirection?: number;
  transactionDate?: string;
  yaml?: string;
}

export interface TransactionCostEstimateResult {
  estimatedCost?: number;
  matchedRuleName?: string;
  error?: string;
}
