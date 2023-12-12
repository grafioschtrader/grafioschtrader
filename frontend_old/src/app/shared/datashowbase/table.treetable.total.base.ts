import {ShowRecordConfigBase} from './show.record.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../service/globalparameter.service';
import {ColumnConfig, ColumnGroupConfig} from './column.config';
import {AppHelper} from '../helper/app.helper';
import {Auditable} from '../../entities/auditable';

export abstract class TableTreetableTotalBase extends ShowRecordConfigBase {
  protected constructor(translateService: TranslateService,
                        gps: GlobalparameterService) {
    super(translateService, gps);
  }

  setSameFieldNameForGroupField(startIndex: number): void {
    for (let i = startIndex; i < this.fields.length; i++) {
      this.fields[i].columnGroupConfigs = [new ColumnGroupConfig(this.fields[i].field)];
    }
  }

  isValueColumnTotalMinus(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any): boolean {
    return this.getValueColumnTotal(columnConfig, arrIndex, data, mapKey).startsWith('-');
  }

  getValueColumnTotal(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any) {
    let value = '';
    if (columnConfig.columnGroupConfigs && arrIndex < columnConfig.columnGroupConfigs.length) {
      if (columnConfig.columnGroupConfigs[arrIndex].fieldTextFN) {
        value = columnConfig.columnGroupConfigs[arrIndex].fieldTextFN(columnConfig, arrIndex, data, mapKey);
      } else {
        value = this.getTextValueColumnTotal(columnConfig, arrIndex);
        if (columnConfig.columnGroupConfigs[arrIndex].fieldValue) {
          const dataValue = this.getFieldValueColumnTotal(columnConfig, arrIndex, data, mapKey);
          if (dataValue) {
            value += (value.length > 0) ? ' ' + dataValue : dataValue;
          }
        }
      }
    }
    return value;
  }

  isColumnTotalByRowIndexMinus(columnConfig: ColumnConfig, arrIndex: number, groupChangeIndexMap: any, rowIndex: number): boolean {
    return this.getValueColumnTotalByRowIndex(columnConfig, arrIndex, groupChangeIndexMap, rowIndex).startsWith('-');
  }

  getValueColumnTotalByRowIndex(columnConfig: ColumnConfig, arrIndex: number, groupChangeIndexMap: any, rowIndex: number): string {
    let value = '';
    if (columnConfig.columnGroupConfigs && arrIndex < columnConfig.columnGroupConfigs.length) {
      if (columnConfig.columnGroupConfigs[arrIndex].fieldTextFN) {
        value = columnConfig.columnGroupConfigs[arrIndex].fieldTextFN(columnConfig, arrIndex, groupChangeIndexMap, rowIndex);
      } else {
        value = this.getTextValueColumnTotal(columnConfig, arrIndex);
        if (columnConfig.columnGroupConfigs[arrIndex].fieldValue) {
          const dataValue = this.getFieldValueColumnTotal(columnConfig, arrIndex, groupChangeIndexMap, rowIndex);
          if (dataValue) {
            value += (value.length > 0) ? ' ' + dataValue : dataValue;
          }
        }
      }
    }
    return value;
  }

  public isNotSingleModeAndOwner(columnConfig: ColumnConfig, entity: Auditable): boolean {
    return this.gps.isUiShowMyProperty() && columnConfig.templateName === 'owner' && this.gps.isEntityCreatedByUser(entity);
  }

  getTextValueColumnTotal(columnConfig: ColumnConfig, arrIndex: number): string {
    if (columnConfig.columnGroupConfigs[arrIndex].textValueKey) {
      return columnConfig.columnGroupConfigs[arrIndex].textValueTranslated;
    }
    return '';
  }

  getFieldValueForFieldColumnTotal(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any, field: string) {
    if (mapKey !== null) {
      data = data.get(mapKey);
    }
    return AppHelper.getValueByPathWithField(this.gps, this.translateService, data, columnConfig,
      field);
  }

  getFieldValueColumnTotal(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any) {
    return this.getFieldValueForFieldColumnTotal(columnConfig, arrIndex, data, mapKey,
      columnConfig.columnGroupConfigs[arrIndex].fieldValue);
  }
}
