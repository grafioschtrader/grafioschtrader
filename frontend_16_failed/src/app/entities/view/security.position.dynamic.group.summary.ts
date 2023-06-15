import {SecurityPositionGroupSummary} from './security.position.group.summary';

export class SecurityPositionDynamicGroupSummary<T> extends SecurityPositionGroupSummary {

  groupField: T;
  groupValueSecurityShort: number;
  groupSecurityRiskMC: number;
}
