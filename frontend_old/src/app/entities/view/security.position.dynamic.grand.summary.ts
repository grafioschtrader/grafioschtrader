import {SecurityPositionGroupSummary} from './security.position.group.summary';
import {SecurityPositionGrandSummary} from './security.position.grand.summary';

export class SecurityPositionDynamicGrandSummary<S extends SecurityPositionGroupSummary> extends SecurityPositionGrandSummary {
  grandValueSecurityShort: number;
  grandSecurityRiskMC: number;
}
