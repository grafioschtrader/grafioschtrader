import { SecurityPositionGroupSummary } from './security.position.group.summary';
import { SecurityPositionGrandSummary } from './security.position.grand.summary';
import { HoldingsTreemap } from './holdings.treemap';

export class SecurityPositionDynamicGrandSummary<
  S extends SecurityPositionGroupSummary
> extends SecurityPositionGrandSummary {
  grandValueSecurityShort: number;
  grandSecurityRiskMC: number;
  /** Only set by the report "security asset classes with cash". */
  holdingsTreemap?: HoldingsTreemap;
}
