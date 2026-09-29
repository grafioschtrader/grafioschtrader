import { SecaccountTradingPeriod } from './secaccount.trading.period';
import { Securitycashaccount } from './securitycashaccount';
import { TradingPlatformPlan } from './tradingplatformplan';

export class Securityaccount extends Securitycashaccount {
  tradingPlatformPlan: TradingPlatformPlan = null;
  hasTransaction: boolean;
  tradingPeriods: SecaccountTradingPeriod[] = [];
  feeModelYaml?: string = null;
  taxExemptInvestor?: boolean = null;
}
