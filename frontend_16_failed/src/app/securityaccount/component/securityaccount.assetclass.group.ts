import {SecurityaccountGroupBaseDynamic} from './securityaccount.group.base.dynamic';
import {TranslateService} from '@ngx-translate/core';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {Security} from '../../entities/security';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {SecurityPositionDynamicGroupSummary} from '../../entities/view/security.position.dynamic.group.summary';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';

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

  public translateGroupValues(securityPositionGroupSummaries: SecurityPositionDynamicGroupSummary<number>[]) {
    securityPositionGroupSummaries.forEach(spgs => {
      const groupFieldValue: string = this.getGroupFieldAsString(spgs.groupField);
      const valueKeyHtmlSelectOptions = SelectOptionsHelper.translateAssetclass(this.translateService, this.language,
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
