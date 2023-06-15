import {SecurityaccountOpenPositionUnits} from './securityaccount.open.position.units';
import {SecurityPositionSummary} from './security.position.summary';

export class SecurityOpenPositionPerSecurityaccount {
  constructor(public securityPositionSummary: SecurityPositionSummary,
              public securityaccountOpenPositionUnitsList: SecurityaccountOpenPositionUnits[]) {
  }
}
