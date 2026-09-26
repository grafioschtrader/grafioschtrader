export interface TransactionCostEstimateRequest {
  idSecurityaccount?: number;
  idTradingPlatformPlan: number;
  tradeValue?: number;
  units?: number;
  specInvestInstrument?: number;
  categoryType?: number;
  mic?: string;
  currency?: string;
  fixedAssets?: number;
  tradeDirection?: number;
  settlementCurrency?: string;
  tradesInMonth?: number;
  tradesInQuarter?: number;
  tradesInYear?: number;
  securityTradesInMonth?: number;
  transactionDate?: string;
  yaml?: string;
}

export interface TransactionCostEstimateResult {
  estimatedCost?: number;
  matchedRuleName?: string;
  error?: string;
}
