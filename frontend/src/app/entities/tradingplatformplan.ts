import { BaseID } from '../lib/entities/base.id';
import { MultilanguageString } from '../lib/entities/multilanguage.string';
import { ImportTransactionPlatform } from './import.transaction.platform';
import { Auditable } from '../lib/entities/auditable';

export class TradingPlatformPlan extends Auditable implements BaseID {
  idTradingPlatformPlan?: number | null = null;
  platformPlanNameNLS: MultilanguageString = new MultilanguageString();
  transactionFeePlan: string | null = null;
  importTransactionPlatform?: ImportTransactionPlatform | null;
  feeModelYaml?: string | null = null;
  countryCode?: string | null = null;

  public override getId(): number | null | undefined {
    return this.idTradingPlatformPlan;
  }
}
