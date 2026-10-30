export interface TransactionCostEstimateRequest {
  idSecurityaccount?: number | null;
  /** Inline YAML can be evaluated before a plan has been saved. */
  idTradingPlatformPlan?: number | null;
  tradeValue?: number;
  units?: number;
  specInvestInstrument?: number;
  categoryType?: number;
  mic?: string;
  currency?: string;
  fixedAssets?: number;
  portfolioTotal?: number;
  tenantTotal?: number;
  tradeDirection?: number;
  settlementCurrency?: string;
  tradesInMonth?: number;
  tradesInQuarter?: number;
  tradesInYear?: number;
  securityTradesInMonth?: number;
  transactionDate?: string | null;
  yaml?: string | null;
}

export interface TransactionCostEstimateResult {
  estimatedCost?: number;
  matchedRuleName?: string;
  error?: string;
}
