import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {ColumnConfig, ColumnGroupConfig} from '../../shared/datashowbase/column.config';
import {TranslateService} from '@ngx-translate/core';
import {SecurityaccountGroupBase} from './securityaccount.group.base';
import {DataType} from '../../dynamic-form/models/data.type';
import {SecurityPositionDynamicGroupSummary} from '../../entities/view/security.position.dynamic.group.summary';
import {SecurityPositionDynamicGrandSummary} from '../../entities/view/security.position.dynamic.grand.summary';
import {Assetclass} from '../../entities/assetclass';
import {SecurityaccountAssetclassCategorytpeGroup} from './securityaccount.assetclass.categorytpe.group';

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
      this.datatableConfigBase.insertColumn(7, DataType.Numeric, 'valueSecurityMC',
        SecurityaccountGroupBaseDynamic.VALUE_SECURITY_MAIN_CURRENCY_HEADER, true, true,
        {
          width: 75,
          columnGroupConfigs: [new ColumnGroupConfig('groupSecurityRiskMC'),
            new ColumnGroupConfig('grandSecurityRiskMC')]
        }));
    this.datatableConfigBase.insertColumn(8, DataType.Boolean, 'security.shortSecurity', 'SHORT_SECURITY', true, true,
      {templateName: 'check', width: 30});
  }

  public getGroupValueFromGroupRow(securityPositionGroupSummary: SecurityPositionDynamicGroupSummary<S>) {
    return securityPositionGroupSummary.groupField;
  }

  public getGroupRowFieldText(columnConfig: ColumnConfig,
                              arrIndex: number, data: any, mapKey: any): string {
    return this.translatedGroupValues[mapKey];
  }

  getGroupRowFieldTextByRowIndex(columnConfig: ColumnConfig, arrIndex: number, data: any, rowIndex: number): string {
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

  public getChartDefinition(title: string,
                            spdgs: SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<S>>): any {
    const values: number[] = [];
    const labels: string[] = [];

    spdgs.securityPositionGroupSummaryList.forEach((spgs: SecurityPositionDynamicGroupSummary<string>) => {
      values.push(spgs.groupAccountValueSecurityMC/ spdgs.grandAccountValueSecurityMC* 100);
      const fieldValue = spgs.groupField;
      if (this.translatedGroupValues) {
        labels.push(this.translatedGroupValues[fieldValue]);
      } else {
        labels.push(fieldValue);
      }
    });

    const data = [{
      values: values,
      labels: labels,
      type: 'pie'
    }];

    const layout = {
      title: title
    };

    return {data: data, layout: layout};
  }

  protected abstract getGroupFieldAsString(enumType: S): string;


}
