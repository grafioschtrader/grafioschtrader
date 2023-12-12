import {SecurityaccountGroupBase} from './securityaccount.group.base';
import {Security} from '../../entities/security';
import {SecurityPositionCurrenyGroupSummary} from '../../entities/view/security.position.curreny.group.summary';
import {ColumnConfig, ColumnGroupConfig} from '../../shared/datashowbase/column.config';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {SecurityPositionGrandSummary} from '../../entities/view/security.position.grand.summary';
import {DataType} from '../../dynamic-form/models/data.type';
import {SecurityaccountGroupBaseDynamic} from './securityaccount.group.base.dynamic';
import {BusinessHelper} from '../../shared/helper/business.helper';

/**
 * Show securities grouped by currency.
 */
export class SecurityaccountCurrencyGroup extends SecurityaccountGroupBase<SecurityPositionCurrenyGroupSummary,
  SecurityPositionGrandSummary> {

  constructor(translateService: TranslateService, datatableConfigBase: TableConfigBase) {
    super(translateService, datatableConfigBase, 'currency', 'security.currency', 'GROUP_BY_CURRENCY');
  }

  public extendColumns(internalColumnConfigs: ColumnConfig[]) {
    // It is possible a grand total for group, because it is grouped by currency
    internalColumnConfigs.push(
      this.datatableConfigBase.insertColumn(7, DataType.Numeric, 'securityRiskMC',
        SecurityaccountGroupBaseDynamic.VALUE_SECURITY_MAIN_CURRENCY_HEADER, true, true,
        {
          width: 75,
          columnGroupConfigs: [new ColumnGroupConfig('groupSecurityRiskMC'),
            new ColumnGroupConfig('grandSecurityRiskMC')]
        }));
    this.datatableConfigBase.insertColumnFeqH(8, DataType.NumericRaw, 'security.leverageFactor',  true, true,
      {templateName: 'greenRed', fieldValueFN:  BusinessHelper.getDisplayLeverageFactor.bind(this), width: 30});
  }

  public getGroupValue(security: Security) {
    return security.currency;
  }

  public getGroupValueFromGroupRow(securityPositionGroupSummary: SecurityPositionCurrenyGroupSummary) {
    return securityPositionGroupSummary.currency;
  }


  override getGroupRowFieldText(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any): string {
    const text = this.datatableConfigBase.getTextValueColumnTotal(columnConfig, arrIndex);
    const exchangeRate = this.datatableConfigBase.getFieldValueForFieldColumnTotal(columnConfig, arrIndex, data,
      mapKey, 'currencyExchangeRate');
    return `${mapKey} ( ${text}: ${exchangeRate} )`;
  }

  override getGroupRowFieldTextByRowIndex(columnConfig: ColumnConfig, arrIndex: number, data: any, rowIndex: number): string {
    const text = this.datatableConfigBase.getTextValueColumnTotal(columnConfig, arrIndex);
    const exchangeRate = this.datatableConfigBase.getFieldValueForFieldColumnTotal(columnConfig, arrIndex, data,
      rowIndex, 'currencyExchangeRate');
    const securityaccountGroupBase = data.get(rowIndex);
    return `${securityaccountGroupBase.currency} ( ${text}: ${exchangeRate} )`;
  }

  public translateGroupValues(securityPositionGroupSummary: SecurityPositionCurrenyGroupSummary[]): void {
  }

   public override getChartDefinition(title: string, securityPositionGrandSummary: SecurityPositionGrandSummary): any {
    const values: number[] = [];
    const labels: string[] = [];

    securityPositionGrandSummary.securityPositionGroupSummaryList.forEach((spgs: SecurityPositionCurrenyGroupSummary) => {
      values.push(spgs.groupAccountValueSecurityMC / securityPositionGrandSummary.grandAccountValueSecurityMC * 100);
      labels.push(spgs.currency);
    });

    const data = [{
      values,
      labels,
      type: 'pie'
    }];

    const layout = {
      title
    };

    return {data, layout};
  }
}
