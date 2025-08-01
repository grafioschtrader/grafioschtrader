import {DataType} from '../dynamic-form/models/data.type';
import {ColumnConfig, OptionalParams} from './column.config';
import {BaseLocale} from '../dynamic-form/models/base.locale';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../helper/app.helper';
import {Helper} from '../helper/helper';
import {TranslateHelper} from '../helper/translate.helper';

/**
 * Abstract base class that provides foundational functionality for displaying data in non-editable
 * table and record formats. Supports column management, data formatting, internationalization,
 * and various display configurations.
 *
 * This class serves as the core building block for table configurations throughout the application,
 * handling column definitions, data access patterns, and translation services. It can be extended
 * to create specialized display components for tables, single records, or tree structures.
 */
export abstract class ShowRecordConfigBase {
  /** Otherwise enum DataType can't be used in a html template */
  DataType: typeof DataType = DataType;

  /**
   * Locale configuration for date and number formatting.Initialized from global parameter service settings during
   * construction. Used by formatting methods for consistent localized display.
   */
  baseLocale: BaseLocale;

  /**
   * Array of column configurations defining the structure and behavior of data display.
   * Each ColumnConfig specifies field access, formatting, translation, and display properties.
   * This is the primary configuration store for all table/record display functionality.
   */
  fields: ColumnConfig[] = [];

  /**
   * Creates a new show record configuration base.
   * Initializes locale settings from global parameters for consistent formatting across the application.
   *
   * @param translateService - Angular translation service for internationalization support
   * @param gps - Global parameter service providing user locale and formatting preferences
   * @protected
   */
  protected constructor(protected translateService: TranslateService, protected gps: GlobalparameterService) {
    this.baseLocale = {
      language: gps.getUserLang(),
      dateFormat: gps.getCalendarTwoNumberDateFormat().toLocaleLowerCase()
    };
  }

  /**
   * Removes all column definitions from the fields array.
   * Useful for completely resetting the table configuration.
   */
  removeAllColumns(): void {
    this.fields = [];
  }

  /**
   * Removes a range of columns from the fields array.
   *
   * @param start - The starting index for removal (inclusive)
   * @param deleteCount - The number of columns to remove
   */
  spliceColumns(start: number, deleteCount: number): void {
    this.fields.splice(start, deleteCount);
  }

  /**
   * Replaces the entire column configuration with a new set of column definitions.
   *
   * @param columnConfig - Array of column configurations to set
   */
  setColumnConfig(columnConfig: ColumnConfig[]) {
    this.fields = columnConfig;
  }

  /**
   * Adds a new column to the table configuration with explicit header key.
   *
   * @param dataType - The data type for formatting and display
   * @param field - The property name used to access data from objects
   * @param headerKey - The translation key for the column header
   * @param visible - Whether the column is initially visible (default: true)
   * @param changeVisibility - Whether users can toggle column visibility (default: true)
   * @param optionalParams - Additional configuration options (width, formatting, etc.)
   * @returns The created ColumnConfig object
   */
  addColumn(dataType: DataType, field: string, headerKey: string, visible: boolean = true, changeVisibility: boolean = true,
            optionalParams?: OptionalParams): ColumnConfig {
    return this.addColumnToFields(this.fields, dataType, field, headerKey, visible, changeVisibility, optionalParams);
  }

  /**
   * Adds a new column with auto-generated header key derived from field name.
   * The header key is automatically generated by converting the field name to uppercase
   * with underscores (e.g., "firstName" becomes "FIRST_NAME").
   *
   * @param dataType - The data type for formatting and display
   * @param field - The property name used to access data and generate header key
   * @param visible - Whether the column is initially visible (default: true)
   * @param changeVisibility - Whether users can toggle column visibility (default: true)
   * @param optionalParams - Additional configuration options (width, formatting, etc.)
   * @returns The created ColumnConfig object
   */
  addColumnFeqH(dataType: DataType, field: string, visible: boolean = true, changeVisibility: boolean = true,
                optionalParams?: OptionalParams): ColumnConfig {
    return this.addColumnToFields(this.fields, dataType, field, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(field), visible,
      changeVisibility, optionalParams);
  }

  /**
   * Adds a column configuration to a specific fields array.
   * Internal helper method used by other column addition methods.
   *
   * @param fields - The target fields array to add the column to
   * @param dataType - The data type for formatting and display
   * @param field - The property name used to access data from objects
   * @param headerKey - The translation key for the column header
   * @param visible - Whether the column is initially visible (default: true)
   * @param changeVisibility - Whether users can toggle column visibility (default: true)
   * @param optionalParams - Additional configuration options
   * @returns The created ColumnConfig object
   */
  addColumnToFields(fields: ColumnConfig[], dataType: DataType, field: string, headerKey: string,
                    visible: boolean = true, changeVisibility: boolean = true, optionalParams?: OptionalParams): ColumnConfig {
    const cc: ColumnConfig = ShowRecordConfigBase.createColumnConfig(dataType, field, headerKey, visible, changeVisibility, optionalParams);
    fields.push(cc);
    return cc;
  }

  /**
   * Creates a column configuration object with the specified parameters.
   * Static factory method for creating column configurations without adding them to a fields array.
   *
   * @param dataType - The data type for formatting and display
   * @param field - The property name used to access data from objects
   * @param headerKey - The translation key for the column header
   * @param visible - Whether the column is initially visible (default: true)
   * @param changeVisibility - Whether users can toggle column visibility (default: true)
   * @param optionalParams - Additional configuration options
   * @returns A fully configured ColumnConfig object
   * @static
   */
  public static createColumnConfig(dataType: DataType, field: string, headerKey: string,
                    visible: boolean = true, changeVisibility: boolean = true, optionalParams?: OptionalParams): ColumnConfig {
    const cc: Partial<ColumnConfig> = {};
    cc.dataType = dataType;
    cc.field = field;
    cc.headerKey = headerKey;
    cc.visible = visible;
    cc.changeVisibility = changeVisibility;
    const columConfig: ColumnConfig = cc as ColumnConfig;
    this.setOptionalColumnConfigParams(columConfig, optionalParams);
    return columConfig;
  }

  private static setOptionalColumnConfigParams(cc: ColumnConfig, optionalParams?: OptionalParams) {
    if (optionalParams) {
      cc.width = optionalParams.width;
      cc.fieldValueFN = optionalParams.fieldValueFN;
      cc.headerSuffix = optionalParams.headerSuffix;
      cc.translateValues = optionalParams.translateValues;
      cc.headerPrefix = optionalParams.headerPrefix;
      cc.templateName = (optionalParams.templateName) ? optionalParams.templateName : '';
      cc.maxFractionDigits = optionalParams.maxFractionDigits;
      cc.minFractionDigits = optionalParams.minFractionDigits;
      cc.columnGroupConfigs = optionalParams.columnGroupConfigs;
      cc.filterType = optionalParams.filterType;
      cc.filterValues = optionalParams.filterValues;
      cc.headerTranslated = optionalParams.headerTranslated;
      cc.export = optionalParams.export;
      cc.frozenColumn = optionalParams.frozenColumn;
      cc.userValue = optionalParams.userValue;
      cc.fieldsetName = optionalParams.fieldsetName;
    }
  }

  /**
   * Inserts a column at a specific index with auto-generated header key.
   *
   * @param index - The position where the column should be inserted
   * @param dataType - The data type for formatting and display
   * @param field - The property name used to access data and generate header key
   * @param visible - Whether the column is initially visible
   * @param changeVisibility - Whether users can toggle column visibility
   * @param optionalParams - Additional configuration options
   * @returns The created ColumnConfig object
   */
  insertColumnFeqH(index: number, dataType: DataType, field: string, visible: boolean, changeVisibility: boolean,
                   optionalParams?: OptionalParams): ColumnConfig {
    return this.insertColumn(index, dataType, field, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(field), visible,
      changeVisibility, optionalParams);
  }

  /**
   * Inserts a column at a specific index with explicit header key.
   *
   * @param index - The position where the column should be inserted
   * @param dataType - The data type for formatting and display
   * @param field - The property name used to access data from objects
   * @param header - The translation key for the column header
   * @param visible - Whether the column is initially visible
   * @param changeVisibility - Whether users can toggle column visibility
   * @param optionalParams - Additional configuration options
   * @returns The created ColumnConfig object
   */
  insertColumn(index: number, dataType: DataType, field: string, header: string, visible: boolean, changeVisibility: boolean,
               optionalParams?: OptionalParams): ColumnConfig {
    const columnConfig: ColumnConfig = ShowRecordConfigBase.createColumnConfig(dataType, field, header, visible, changeVisibility, optionalParams);
    this.fields = [...this.fields.slice(0, index), columnConfig, ...this.fields.slice(index)];
    return columnConfig;
  }

  /**
   * Translates table headers, tooltips, and column content using the translation service.
   * This method handles the complete translation process for all configured columns.
   */
  translateHeadersAndColumns(): void {
    const headerKeys: string [] = this.fields.map(field => field.headerKey);
    this.translateHeaders(headerKeys, this.fields);
    this.translateHeadersTooltips(headerKeys, this.fields);
    this.translateColumnTotalTextValue(this.fields);
  }

  /**
   * Finds a column configuration by its field name.
   *
   * @param field - The field name to search for
   * @returns The matching ColumnConfig or undefined if not found
   */
  getColumnConfigByField(field: string): ColumnConfig {
    return this.fields.find(columnConfig => columnConfig.field === field);
  }

  /**
   * Finds a column configuration by its header key.
   *
   * @param header - The header key to search for
   * @returns The matching ColumnConfig or undefined if not found
   */
  getColumnConfigByHeader(header: string): ColumnConfig {
    return this.fields.find(columnConfig => columnConfig.headerKey === header);
  }

  /**
   * Finds a column configuration by both field name and header key.
   *
   * @param field - The field name to match
   * @param header - The header key to match
   * @returns The matching ColumnConfig or undefined if not found
   */
  getColumnConfigByFieldAndHeader(field: string, header: string): ColumnConfig {
    return this.fields.find(columnConfig => columnConfig.field === field && columnConfig.headerKey === header);
  }

  /**
   * Finds a column configuration by its translated header text.
   *
   * @param headerTranslated - The translated header text to search for
   * @returns The matching ColumnConfig or undefined if not found
   */
  getColumnConfigByHeaderTranslated(headerTranslated: string): ColumnConfig {
    return this.fields.find(columnConfig => columnConfig.headerTranslated === headerTranslated);
  }

  /**
   * Checks if a numeric value accessed by the field path is negative.
   *
   * @param dataobject - The object containing the data
   * @param field - The column configuration containing the field path
   * @returns True if the value is negative, false otherwise
   */
  isValueByPathMinus(dataobject: any, field: ColumnConfig): boolean {
    return +Helper.getValueByPath(dataobject, field.field) < 0;
  }

  /**
   * Creates translated value store for PrimeNG table sorting.
   * Adds translated fields with '$' suffix to support proper sorting of translated values.
   *
   * @param data - Array of data objects to process for translation
   */
  createTranslatedValueStore(data: any[]): void {
    TranslateHelper.createTranslatedValueStore(this.translateService, this.fields, data);
  }

  /**
   * Retrieves and formats a value from an object using the column configuration.
   * Applies data type formatting, translation, and any custom field value functions.
   *
   * @param dataobject - The object containing the data
   * @param field - The column configuration specifying how to access and format the value
   * @returns The formatted value ready for display
   */
  getValueByPath(dataobject: any, field: ColumnConfig) {
    return this.getValueByPathWithField(dataobject, field, field.fieldTranslated || field.field);
  }

  /**
   * Retrieves and formats a value using a specific field path.
   * Lower-level method that allows overriding the field path from the column configuration.
   *
   * @param dataobject - The object containing the data
   * @param field - The column configuration for formatting rules
   * @param valueField - The specific field path to use for data access
   * @returns The formatted value ready for display
   */
  getValueByPathWithField(dataobject: any, field: ColumnConfig, valueField: string) {
    let value = AppHelper.getValueByPathWithField(this.gps, this.translateService, dataobject, field, valueField);
    if (field.fieldValueFN) {
      value = field.fieldValueFN(dataobject, field, value);
    }
    return value;
  }

  /**
   * Translates header keys to localized header text.
   * Internal method that processes header translation for column configurations.
   *
   * @param translateHeaderKeys - Array of header keys to translate
   * @param columConfig - Array of column configurations to update with translations
   * @protected
   */
  protected translateHeaders(translateHeaderKeys: string[], columConfig: ColumnConfig[]): void {
    this.translateService.get(translateHeaderKeys.filter(thk => !!thk)).subscribe((allTranslatedTexts: any) =>
      columConfig.map(field => field.headerTranslated =
        ((field.headerPrefix == null) ? '' : field.headerSuffix + ' ')
        + allTranslatedTexts[field.headerKey]
        + ((field.headerSuffix == null) ? '' : ' ' + field.headerSuffix))
    );
  }

  /**
   * Translates header tooltips using header keys with '_TOOLTIP' suffix.
   * Internal method that processes tooltip translation for column configurations.
   *
   * @param translateHeaderKeys - Array of header keys to use for tooltip translation
   * @param columConfig - Array of column configurations to update with tooltip translations
   * @protected
   */
  protected translateHeadersTooltips(translateHeaderKeys: string[], columConfig: ColumnConfig[]): void {
    const tSuffix = '_TOOLTIP';
    this.translateService.get(translateHeaderKeys.map(thtk => thtk + tSuffix)).subscribe((allTranslatedTexts: any) =>
      columConfig.map(field => {
        const key = field.headerKey + tSuffix;
        field.headerTooltipTranslated = (allTranslatedTexts[key] !== key) ? allTranslatedTexts[key] : field.headerTooltipTranslated;
      }));
  }

  /**
  * Translates text values for column group configurations (subtotals, totals, etc.).
  * Processes columnGroupConfigs that have textValueKey properties and populates
  * their textValueTranslated properties with localized text.
  *
  * @param columConfigs - Array of column configurations to process for group text translation
  */
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
