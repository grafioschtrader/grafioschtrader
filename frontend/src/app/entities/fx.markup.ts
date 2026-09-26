/** Side-effect-free preview of a conversion's tariff, including missing-coverage outcomes. */
export interface FxMarkupRequest {
  payCurrency: string;
  receiveCurrency: string;
  kind: string;
  amount: number;
  date: string;
  mic?: string;
}

export interface FxMarkupPreviewRequest {
  idSecurityaccount?: number;
  idTradingPlatformPlan?: number;
  yaml: string;
  request: FxMarkupRequest;
}

export interface FxQuote {
  percent: number;
  outcome: 'MATCHED' | 'NO_SECTION' | 'NO_PERIOD' | 'NO_RULE' | 'NO_TIER_RATE' | 'INVALID';
  periodValidFrom?: string;
  ruleName?: string;
  status?: string;
  error?: string;
}
