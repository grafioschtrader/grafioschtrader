import {SecurityTransactionPosition} from './security.transaction.position';
import {SecurityPositionSummary} from './security.position.summary';
import {INameSecuritycurrency} from './iname.securitycurrency';
import {Securitycurrency} from '../securitycurrency';

export class SecurityTransactionSummary implements INameSecuritycurrency {
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
