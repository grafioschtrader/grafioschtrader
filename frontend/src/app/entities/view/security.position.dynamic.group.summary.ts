import type { SecurityPositionGroupSummary } from './security.position.group.summary';

/** Security-position group identified by the report-specific grouping value. */
export interface SecurityPositionDynamicGroupSummary<T> extends SecurityPositionGroupSummary {
  groupField: T;
  groupValueSecurityShort: number;
  groupSecurityRiskMC: number;
}
