import {StandingOrder} from '../../entities/standing.order';
import {Transaction} from '../../entities/transaction';

/**
 * Parameter object passed to standing order edit dialogs. When standingOrder is set,
 * the dialog opens in edit mode. When transaction is set, the dialog pre-fills from
 * the selected transaction (create from context menu).
 */
export class StandingOrderCallParam {
  constructor(
    public standingOrder: StandingOrder | null,
    public transaction: Transaction | null
  ) {
  }
}
