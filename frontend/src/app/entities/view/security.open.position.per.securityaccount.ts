import { SecurityaccountOpenPositionUnits } from './securityaccount.open.position.units';
import type { SecurityPositionSummary } from './security.position.summary';

export class SecurityOpenPositionPerSecurityaccount {
  constructor(
    public securityPositionSummary: SecurityPositionSummary,
    public securityaccountOpenPositionUnitsList: SecurityaccountOpenPositionUnits[]
  ) {}
}
