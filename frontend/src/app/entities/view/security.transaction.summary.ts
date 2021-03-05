import {SecurityTransactionPosition} from './security.transaction.position';
import {SecurityPositionSummary} from './security.position.summary';
import {NameSecuritycurrency} from './name.securitycurrency';
import {Securitycurrency} from '../securitycurrency';

export class SecurityTransactionSummary implements NameSecuritycurrency {
  constructor(public transactionPositionList: SecurityTransactionPosition[],
              public securityPositionSummary: SecurityPositionSummary) {
  }

  public getName(): string {
    return this.securityPositionSummary.security.name;
  }

  public getSecuritycurrency(): Securitycurrency {
    return this.securityPositionSummary.security;

  }
}
