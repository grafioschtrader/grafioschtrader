import { TranslateService } from '@ngx-translate/core';
import { Security } from '../../entities/security';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { SecurityPositionDynamicGroupSummary } from '../../entities/view/security.position.dynamic.group.summary';
import { SecurityaccountGroupBaseDynamic } from './securityaccount.group.base.dynamic';

/**
 * Groups the positions by the buckets of the selected strategy.
 *
 * Unlike the asset class groupings, the group value is not derived from the security at all: which bucket a position
 * belongs to is a property of the strategy, not of the instrument, and a bucket may be a freely named category that no
 * asset class corresponds to. The backend therefore assigns the groups and sends their already translated names, and
 * this class only has to pass them through instead of resolving a translation key.
 */
export class SecurityaccountAlgoBucketGroup extends SecurityaccountGroupBaseDynamic<string> {
  constructor(translateService: TranslateService, datatableConfigBase: TableConfigBase) {
    super(translateService, datatableConfigBase, 'rebalancing', 'security.assetClass.categoryType', 'GROUP_BY_ALGO');
  }

  public getGroupValue(security: Security): string {
    return null;
  }

  /** The names arrive ready for display, so there is nothing to look up and nothing to wait for. */
  public override translateGroupValues(groupSummaries: SecurityPositionDynamicGroupSummary<string>[]): void {
    groupSummaries.forEach((summary) => (this.translatedGroupValues[summary.groupField] = summary.groupField));
  }

  protected getGroupFieldAsString(groupField: string): string {
    return groupField;
  }
}
