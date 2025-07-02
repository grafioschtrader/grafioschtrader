import {BaseID} from '../lib/entities/base.id';
import {MultilanguageString} from '../lib/entities/multilanguage.string';
import {ImportTransactionPlatform} from './import.transaction.platform';
import {Auditable} from '../lib/entities/auditable';

export class TradingPlatformPlan extends Auditable implements BaseID {
  idTradingPlatformPlan?: number = null;
  platformPlanNameNLS: MultilanguageString = new MultilanguageString();
  transactionFeePlan: string = null;
  importTransactionPlatform: ImportTransactionPlatform;

  public override getId(): number {
    return this.idTradingPlatformPlan;
  }

}
