import {SecurityaccountGroupBaseDynamic} from './securityaccount.group.base.dynamic';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {Security} from '../../entities/security';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';

/**
 * Group by sub category of asset class
 */
export class SecurityaccountAssetclassSubCategoryGroup extends SecurityaccountGroupBaseDynamic<string> {

  private readonly language: string;

  constructor(gps: GlobalparameterService,
              translateService: TranslateService,
              datatableConfigBase: TableConfigBase) {

    super(translateService, datatableConfigBase, 'subcategorynls', 'security.assetClass.subCategoryNLS.map.'
      + gps.getUserLang(), 'GROUP_BY_SUB_CATEGORY');
    this.language = gps.getUserLang();
  }

  public getGroupValue(security: Security) {
    return security.assetClass.subCategoryNLS.map[this.language];
  }

  protected getGroupFieldAsString(value: string): string {
    return value;
  }

}
