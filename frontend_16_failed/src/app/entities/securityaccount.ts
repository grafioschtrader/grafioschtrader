import {Securitycashaccount} from './securitycashaccount';
import {TradingPlatformPlan} from './tradingplatformplan';

export class Securityaccount extends Securitycashaccount {
  tradingPlatformPlan: TradingPlatformPlan = null;
  hasTransaction: boolean;
  shareUseUntil = null;
  bondUseUntil = null;
  etfUseUntil = null;
  fondUseUntil = null;
  forexUseUntil = null;
  cfdUseUntil = null;
  lowestTransactionCost = null;
}
