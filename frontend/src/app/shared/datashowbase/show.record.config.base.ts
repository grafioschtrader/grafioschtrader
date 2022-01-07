import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnConfig, OptionalParams} from './column.config';
import {BaseLocale} from '../../dynamic-form/models/base.locale';
import {GlobalparameterService} from '../service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../helper/app.helper';
import {Helper} from '../../helper/helper';
import {TranslateHelper} from '../helper/translate.helper';

/**
 * It is the base class which supports the definition of non editable fields.
 */
export abstract class ShowRecordConfigBase {
  // Otherwise enum DataType can't be used in a html template
  DataType: typeof DataType = DataType;

  baseLocale: BaseLocale;
  fields: ColumnConfig[] = [];

  protected constructor(protected translateService: TranslateService, protected gps: GlobalparameterService) {
    this.baseLocale = {
      language: gps.getUserLang(),
      dateFormat: gps.getCalendarTwoNumberDateFormat().toLocaleLowerCase()
    };
  }

  removeAllColumns(): void {
    this.fields = [];
  }

  spliceColumns(start: number, deleteCount: number): void {
    this.fields.splice(start, deleteCount);
  }

  setColumnConfig(columnConfig: ColumnConfig[]) {
    this.fields = columnConfig;
  }

  addColumn(dataType: DataType, field: string, headerKey: string, visible: boolean = true, changeVisibility: boolean = true,
            optionalParams?: OptionalParams): ColumnConfig {

    return this.addColumnToFields(this.fields, dataType, field, headerKey, visible, changeVisibility, optionalParams);
  }

  /**
   * Header key name is derived from the field name.
   *
   * @param dataType Type of the property
   * @param field Name of the property, the value is accessed by this name.
   * @param visible Is this column initial visible.
   * @param changeVisibility Can the visibility of the column be changed.
   * @param optionalParams Addional parameters.
   */
  addColumnFeqH(dataType: DataType, field: string, visible: boolean = true, changeVisibility: boolean = true,
                optionalParams?: OptionalParams): ColumnConfig {

    return this.addColumnToFields(this.fields, dataType, field, AppHelper.convertPropertyForLabelOrHeaderKey(field), visible,
      changeVisibility, optionalParams);
  }


  addColumnToFields(fields: ColumnConfig[], dataType: DataType, field: string, header: string,
                    visible: boolean = true, changeVisibility: boolean = true, optionalParams?: OptionalParams): ColumnConfig {
    const columnConfig = new ColumnConfig(dataType, field, header, visible, changeVisibility, optionalParams);
    fields.push(columnConfig);
    return columnConfig;
  }

  insertColumnFeqH(index: number, dataType: DataType, field: string, visible: boolean, changeVisibility: boolean,
                   optionalParams?: OptionalParams): ColumnConfig {
    return this.insertColumn(index, dataType, field, AppHelper.convertPropertyForLabelOrHeaderKey(field), visible,
      changeVisibility, optionalParams);
  }

  insertColumn(index: number, dataType: DataType, field: string, header: string, visible: boolean, changeVisibility: boolean,
               optionalParams?: OptionalParams): ColumnConfig {
    const columnConfig: ColumnConfig = new ColumnConfig(dataType, field, header, visible, changeVisibility, optionalParams);
    this.fields = [...this.fields.slice(0, index), columnConfig, ...this.fields.slice(index)];
    return columnConfig;
  }

  /**
   * Translate table header and text in the row footer or total footer. It is done by
   * the definition of the columns.
   */
  translateHeadersAndColumns(): void {
    const headerKeys: string [] = this.fields.map(field => field.headerKey);
    this.translateHeaders(headerKeys, this.fields);
    this.translateHeadersTooltips(headerKeys, this.fields);
    this.translateColumnTotalTextValue(this.fields);
  }

  getColumnConfigByField(field: string): ColumnConfig {
    return this.fields.find(columnConfig => columnConfig.field === field);
  }

  getColumnConfigByHeader(header: string): ColumnConfig {
    return this.fields.find(columnConfig => columnConfig.headerKey === header);
  }

  getColumnConfigByFieldAndHeader(field: string, header: string): ColumnConfig {
    return this.fields.find(columnConfig => columnConfig.field === field && columnConfig.headerKey === header);
  }

  getColumnConfigByHeaderTranslated(headerTranslated: string): ColumnConfig {
    return this.fields.find(columnConfig => columnConfig.headerTranslated === headerTranslated);
  }

  isValueByPathMinus(dataobject: any, field: ColumnConfig): boolean {
    return +Helper.getValueByPath(dataobject, field.field) < 0;
  }

  /**
   * PrimeNg sorts the content a field by its value in the array. To get the right sort order an extra
   * field is added to structure to achieving that. It is the original name with a appended '$'.
   */
  createTranslatedValueStore(data: any[]): void {
    TranslateHelper.createTranslatedValueStore(this.translateService, this.fields, data);
  }

  /**
   * It returns the value in the format specified by DataType
   */
  getValueByPath(dataobject: any, field: ColumnConfig) {
    return this.getValueByPathWithField(dataobject, field, field.fieldTranslated || field.field);
  }

  getValueByPathWithField(dataobject: any, field: ColumnConfig, valueField: string) {
    let value = AppHelper.getValueByPathWithField(this.gps, this.translateService, dataobject, field, valueField);
    if (field.fieldValueFN) {
      value = field.fieldValueFN(dataobject, field, value);
    }
    return value;
  }

  protected translateHeaders(translateHeaderKeys: string[], columConfig: ColumnConfig[]): void {
    this.translateService.get(translateHeaderKeys).subscribe((allTranslatedTexts: any) =>
      columConfig.map(field => field.headerTranslated =
        ((field.headerPrefix == null) ? '' : field.headerSuffix + ' ')
        + allTranslatedTexts[field.headerKey]
        + ((field.headerSuffix == null) ? '' : ' ' + field.headerSuffix))
    );
  }

  protected translateHeadersTooltips(translateHeaderKeys: string[], columConfig: ColumnConfig[]): void {
    const tSuffix = '_TOOLTIP';
    this.translateService.get(translateHeaderKeys.map(thtk => thtk + tSuffix)).subscribe((allTranslatedTexts: any) =>
      columConfig.map(field => {
        const key = field.headerKey + tSuffix;
        field.headerTooltipTranslated = (allTranslatedTexts[key] !== key) ? allTranslatedTexts[key] : null;
      }));
  }

  private translateColumnTotalTextValue(columConfigs: ColumnConfig[]): void {
    columConfigs.forEach(columConfig => {
      if (columConfig.columnGroupConfigs) {
        columConfig.columnGroupConfigs.forEach(cc => {
            if (cc.textValueKey) {
              this.translateService.get(cc.textValueKey).subscribe(translatedText => cc.textValueTranslated = translatedText);
            }
          }
        );
      }
    });
  }

}
