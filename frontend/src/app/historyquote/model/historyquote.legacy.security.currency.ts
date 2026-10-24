import { HistoryquoteLegacy } from '../../entities/historyquote.legacy';
import { Securitycurrency } from '../../entities/securitycurrency';
import { Security } from '../../entities/security';
import { Currencypair } from '../../entities/currencypair';

/**
 * Call parameter for HistoryquoteLegacyEditComponent: the archived row being edited together with its owning security
 * or currency pair (the auditable parent used for rights/propose-change decisions). Kept apart from the components,
 * because the propose-change setup of the application shell creates it.
 */
export class HistoryquoteLegacySecurityCurrency {
  constructor(
    public historyquoteLegacy: HistoryquoteLegacy,
    public securitycurrency: Securitycurrency
  ) {}

  get showName(): string {
    return this.securitycurrency.hasOwnProperty('name')
      ? (<Security>this.securitycurrency).name
      : (<Currencypair>this.securitycurrency).fromCurrency + '/' + (<Currencypair>this.securitycurrency).toCurrency;
  }
}
