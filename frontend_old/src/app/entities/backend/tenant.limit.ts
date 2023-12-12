export class TenantLimit {
  limit: number;
  actual: number;
  msgKey: string;
  className: string;
}

export enum TenantLimitTypes {
  MAX_CASH_ACCOUNT = 'MAX_CASH_ACCOUNT',
  MAX_SECURITY_ACCOUNT = 'MAX_SECURITY_ACCOUNT',
  MAX_PORTFOLIO = 'MAX_PORTFOLIO',
  MAX_WATCHLIST = 'MAX_WATCHLIST'
}


