import {Security} from '../../entities/security';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {SecurityaccountGroupBaseDynamic} from './securityaccount.group.base.dynamic';

/**
 * Group by asset class, like bond, stock, commodities, real estate, ....
 */
export class SecurityaccountAssetclassCategortypeGroup extends SecurityaccountGroupBaseDynamic<AssetclassType> {


  constructor(translateService: TranslateService, datatableConfigBase: TableConfigBase) {
    super(translateService, datatableConfigBase, 'assetclasstype', 'security.assetClass.categoryType', 'GROUP_BY_ASSETCLASS');
    // this.translateService.get('short').subscribe(translated => this.translatedShort = translated);
  }

  /*
    public extendColumns(internalColumnConfigs: ColumnConfig[]) {
      super.extendColumns(internalColumnConfigs);
      internalColumnConfigs.push(
        this.datatableConfigBase.insertColumn(7, DataType.Numeric, 'valueSecurityMC',
          SecurityaccountAssetclassCategortypeGroup.VALUE_SECURITY_MAIN_CURRENCY_HEADER, true, true,
          {
            width: 75,
            columnGroupConfigs: [new ColumnGroupConfig('groupSecurityRiskMC'),
              new ColumnGroupConfig('grandSecurityRiskMC')]
          }));
    }
  */
  public getGroupValue(security: Security) {
    return security.assetClass.categoryType;
  }


  protected getGroupFieldAsString(enumType: AssetclassType): string {
    return AssetclassType[AssetclassType[enumType]];
  }


}
