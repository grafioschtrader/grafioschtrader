import {Security} from '../../entities/security';
import {SecurityPositionDynamicGroupSummary} from '../../entities/view/security.position.dynamic.group.summary';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {SecurityPositionDynamicGrandSummary} from '../../entities/view/security.position.dynamic.grand.summary';
import {SecurityaccountGroupBaseDynamic} from './securityaccount.group.base.dynamic';

/**
 * Group by asset class, like bond, stock, commodities, real estate, ....
 */
export class SecurityaccountAssetclassCategorytpeGroup extends SecurityaccountGroupBaseDynamic<AssetclassType> {


  constructor(translateService: TranslateService, datatableConfigBase: TableConfigBase) {
    super(translateService, datatableConfigBase, 'assetclasstype', 'security.assetClass.categoryType', 'GROUP_BY_ASSETCLASS');
   // this.translateService.get('short').subscribe(translated => this.translatedShort = translated);
  }

  /*
    public extendColumns(internalColumnConfigs: ColumnConfig[]) {
      super.extendColumns(internalColumnConfigs);
      internalColumnConfigs.push(
        this.datatableConfigBase.insertColumn(7, DataType.Numeric, 'valueSecurityMC',
          SecurityaccountAssetclassCategorytpeGroup.VALUE_SECURITY_MAIN_CURRENCY_HEADER, true, true,
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

  public getChartDefinition(title: string,
                            spdgs: SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<AssetclassType>>): any {

    const valuesNet: number[] = [];
    const valuesGross: number [] = [];

    const labels: string[] = [];

    spdgs.securityPositionGroupSummaryList.forEach((spgs: SecurityPositionDynamicGroupSummary<AssetclassType>) => {
      labels.push(this.translatedGroupValues[spgs.groupField]);
      valuesGross.push(spgs.groupAccountValueSecurityMC / spdgs.grandAccountValueSecurityMC * 100);

      valuesNet.push(spgs.groupSecurityRiskMC);

    });

    const barChartNet: any = {
      x: valuesNet,
      y: labels,
      type: 'bar',
      orientation: 'h',
      domain: {
        x: [0.1, .48],
        y: [0, 0.8]
      },
      sort: false
    };
    const circleChartGross = {
      values: valuesGross,
      labels: labels,
      type: 'pie',
      domain: {
        x: [0.52, 1],
        y: [0, 0.8]
      },
      hoverinfo: 'label+percent',
      sort: false
    };

    const data = [barChartNet, circleChartGross];

    const layout = {
      title: title,
      grid: {rows: 1, columns: 2},
    };

    this.translateService.get(SecurityaccountGroupBaseDynamic.VALUE_SECURITY_MAIN_CURRENCY_HEADER).subscribe(
      translated => barChartNet.name = translated);
    return {data: data, layout: layout};
  }

  protected getGroupFieldAsString(enumType: AssetclassType): string {
    return AssetclassType[AssetclassType[enumType]];
  }


}
