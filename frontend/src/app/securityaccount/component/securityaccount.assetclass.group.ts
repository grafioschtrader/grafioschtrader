import {SecurityaccountGroupBaseDynamic} from './securityaccount.group.base.dynamic';
import {TranslateService} from '@ngx-translate/core';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {Security} from '../../entities/security';
import {SecurityPositionDynamicGroupSummary} from '../../entities/view/security.position.dynamic.group.summary';
import {BusinessSelectOptionsHelper} from '../../shared/securitycurrency/business.select.options.helper';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';

/**
 * Group by asset class by all properties of asset class. It produces more groups than other groupings.
 */
export class SecurityaccountAssetclassGroup extends SecurityaccountGroupBaseDynamic<number> {
  private readonly language: string;

  constructor(gps: GlobalparameterService,
    translateService: TranslateService,
    datatableConfigBase: TableConfigBase) {

    super(translateService, datatableConfigBase, 'idassetclass',
      'security.assetClass', 'GROUP_BY_ASSETCLASS_COMBINATION');
    this.language = gps.getUserLang();
  }

  public override translateGroupValues(securityPositionGroupSummaries: SecurityPositionDynamicGroupSummary<number>[]) {
    securityPositionGroupSummaries.forEach(spgs => {
      const groupFieldValue: string = this.getGroupFieldAsString(spgs.groupField);
      const valueKeyHtmlSelectOptions = BusinessSelectOptionsHelper.translateAssetclass(this.translateService, this.language,
        spgs.securityPositionSummaryList[0].security.assetClass, null);
      this.translatedGroupValues[groupFieldValue] = valueKeyHtmlSelectOptions.value;
    });
  }


  public getGroupValue(security: Security) {
    return security.assetClass.idAssetClass;
  }

  protected getGroupFieldAsString(idAssetclass: number): string {
    return '' + idAssetclass;
  }
}
