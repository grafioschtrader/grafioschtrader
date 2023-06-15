import {Security} from '../../entities/security';
import {SecurityPositionGroupSummary} from '../../entities/view/security.position.group.summary';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {ColumnConfig} from '../../shared/datashowbase/column.config';
import {TranslateService} from '@ngx-translate/core';
import {SecurityPositionGrandSummary} from '../../entities/view/security.position.grand.summary';

export abstract class SecurityaccountGroupBase<T extends SecurityPositionGroupSummary, U extends SecurityPositionGrandSummary> {

  /**
   *
   * @param translateService Service for translation
   * @param datatableConfigBase
   * @param defaultGroup Is used as a part of the url in the service call.
   * @param defaultEntityGroup
   * @param groupedBy
   */
  constructor(protected translateService: TranslateService,
              protected datatableConfigBase: TableConfigBase,
              public defaultGroup: string,
              public defaultEntityGroup: string,
              public groupedBy: string) {
  }

  /**
   * Add some columns to the base definition
   *
   * @param {ColumnConfig[]} internalColumnConfigs
   */
  public abstract extendColumns(internalColumnConfigs: ColumnConfig[]);

  public abstract getGroupValue(security: Security);

  public abstract translateGroupValues(securityPositionGroupSummary: T[]): void;

  public abstract getGroupValueFromGroupRow(securityPositionGroupSummary: T): any;

  public abstract getGroupRowFieldText(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any): string;

  public abstract getGroupRowFieldTextByRowIndex(columnConfig: ColumnConfig, arrIndex: number, data: any, rowIndex: number): string;

  public abstract getChartDefinition(title: string, securityPositionGrandSummary: U): any;
}
