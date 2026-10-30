import { Historyquote } from '../../entities/historyquote';
import { Securitycurrency } from '../../entities/securitycurrency';
import { Security } from '../../entities/security';
import { Currencypair } from '../../entities/currencypair';

/**
 * Call parameter for HistoryquoteEditComponent: the history quote being edited together with its owning security or
 * currency pair. Kept apart from the components, because the propose-change setup of the application shell creates it.
 */
export class HistoryquoteSecurityCurrency {
  constructor(
    public historyquote: Historyquote | null,
    public securitycurrency: Securitycurrency
  ) {}

  get showName(): string {
    return this.securitycurrency.hasOwnProperty('name')
      ? (<Security>this.securitycurrency).name
      : (<Currencypair>this.securitycurrency).fromCurrency + '/' + (<Currencypair>this.securitycurrency).toCurrency;
  }
}
