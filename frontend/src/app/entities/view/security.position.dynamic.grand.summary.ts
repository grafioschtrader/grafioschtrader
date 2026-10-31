import type { SecurityPositionGroupSummary } from './security.position.group.summary';
import type { SecurityPositionGrandSummary } from './security.position.grand.summary';
import type { HoldingsTreemap } from './holdings.treemap';

/** Security-report totals with short exposure and optional holdings treemap. */
export interface SecurityPositionDynamicGrandSummary<
  S extends SecurityPositionGroupSummary
> extends SecurityPositionGrandSummary<S> {
  grandValueSecurityShort: number;
  grandSecurityRiskMC: number;
  /** Only set by the report "security asset classes with cash". */
  holdingsTreemap?: HoldingsTreemap;
}
