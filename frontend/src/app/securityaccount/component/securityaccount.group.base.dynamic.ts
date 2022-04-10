import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {ColumnConfig, ColumnGroupConfig} from '../../shared/datashowbase/column.config';
import {TranslateService} from '@ngx-translate/core';
import {SecurityaccountGroupBase} from './securityaccount.group.base';
import {DataType} from '../../dynamic-form/models/data.type';
import {SecurityPositionDynamicGroupSummary} from '../../entities/view/security.position.dynamic.group.summary';
import {SecurityPositionDynamicGrandSummary} from '../../entities/view/security.position.dynamic.grand.summary';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {BusinessHelper} from '../../shared/helper/business.helper';

/**
 * Allow dynamic grouping, but is general a base class of asset class grouping.
 */
export abstract class SecurityaccountGroupBaseDynamic<S> extends SecurityaccountGroupBase<SecurityPositionDynamicGroupSummary<S>,
  SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<S>>> {

  static readonly VALUE_SECURITY_MAIN_CURRENCY_HEADER = 'SECURITY_RISK';

  // protected translatedShort: string;
  protected translatedGroupValues: { [key: string]: string } = {};

  constructor(translateService: TranslateService,
              datatableConfigBase: TableConfigBase,
              defaultGroup: string,
              defaultEntityGroup: string,
              groupedBy: string) {
    super(translateService, datatableConfigBase, defaultGroup, defaultEntityGroup, groupedBy);
    // this.translateService.get('short').subscribe(translated => this.translatedShort = translated);
  }

  public extendColumns(internalColumnConfigs: ColumnConfig[]) {
    internalColumnConfigs.push(
      this.datatableConfigBase.insertColumn(7, DataType.Numeric, 'securityRiskMC',
        SecurityaccountGroupBaseDynamic.VALUE_SECURITY_MAIN_CURRENCY_HEADER, true, true,
        {
          width: 75,
          columnGroupConfigs: [new ColumnGroupConfig('groupSecurityRiskMC'),
            new ColumnGroupConfig('grandSecurityRiskMC')]
        }));
    this.datatableConfigBase.insertColumnFeqH(8, DataType.NumericRaw, 'security.leverageFactor',  true, true,
      {templateName: 'greenRed', fieldValueFN:  BusinessHelper.getDisplayLeverageFactor.bind(this)});
  }

  public getGroupValueFromGroupRow(securityPositionGroupSummary: SecurityPositionDynamicGroupSummary<S>) {
    return securityPositionGroupSummary.groupField;
  }

  public override getGroupRowFieldText(columnConfig: ColumnConfig,
                              arrIndex: number, data: any, mapKey: any): string {
    return this.translatedGroupValues[mapKey];
  }

  override getGroupRowFieldTextByRowIndex(columnConfig: ColumnConfig, arrIndex: number, data: any, rowIndex: number): string {
    const securityaccountGroupBase = data.get(rowIndex);
    return this.translatedGroupValues[securityaccountGroupBase.groupField];
  }

  public translateGroupValues(securityPositionGroupSummaries: SecurityPositionDynamicGroupSummary<S>[]) {
    securityPositionGroupSummaries.forEach(spgs => {
      const groupFieldValue: string = this.getGroupFieldAsString(spgs.groupField);
      if (!this.translatedGroupValues[groupFieldValue]) {
        this.translateService.get(groupFieldValue).subscribe(groupValueTranslated =>
          this.translatedGroupValues[groupFieldValue] = groupValueTranslated);
      }
    });
  }

  public override getChartDefinition(title: string,
                            spdgs: SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<AssetclassType>>): any {

    const valuesNet: number[] = [];
    const valuesGross: number [] = [];

    const labels: string[] = [];

    spdgs.securityPositionGroupSummaryList.forEach((spgs: SecurityPositionDynamicGroupSummary<AssetclassType>) => {
      labels.push(this.translatedGroupValues[spgs.groupField]);
      valuesGross.push(spgs.groupAccountValueSecurityMC / spdgs.grandAccountValueSecurityMC * 100);
      const enumGroup = (<any>AssetclassType)[spgs.groupField];
      if (enumGroup === AssetclassType.CURRENCY_FOREIGN || enumGroup === AssetclassType.CURRENCY_CASH) {
        valuesNet.push(spgs.groupAccountValueSecurityMC);
      } else {
        valuesNet.push(spgs.groupSecurityRiskMC);
      }
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
      sort: false,
      hoverinfo: 'label'
    };
    const circleChartGross = {
      values: valuesGross,
      labels,
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
      title,
      grid: {rows: 1, columns: 2},
      yaxis: {
        automargin: true
      }
    };

    this.translateService.get(SecurityaccountGroupBaseDynamic.VALUE_SECURITY_MAIN_CURRENCY_HEADER).subscribe(
      translated => barChartNet.name = translated);
    return {data, layout};
  }

  protected abstract getGroupFieldAsString(enumType: S): string;

}
