import {BaseID} from './base.id';
import {MultilanguageString} from './multilanguage.string';
import {ImportTransactionPlatform} from './import.transaction.platform';
import {Auditable} from './auditable';

export class TradingPlatformPlan extends Auditable implements BaseID {
  idTradingPlatformPlan?: number = null;
  platformPlanNameNLS: MultilanguageString = new MultilanguageString();
  transactionFeePlan: string = null;
  importTransactionPlatform: ImportTransactionPlatform;

  public getId(): number {
    return this.idTradingPlatformPlan;
  }

}
