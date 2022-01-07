import {DataType} from '../../dynamic-form/models/data.type';
import {FilterType} from './filter.type';
import {ValueLabelHtmlSelectOptions} from './value.label.html.select.options';

/**
 * It represents the field configuration for a table view. It may also be used to show some single data definition.
 */
export class ColumnConfig {

  public dataType: DataType;
  public field: string;
  public headerKey: string;
  public headerTranslated: string;
  public headerTooltipTranslated: string;
  public visible: boolean;

  /**
   * Visibility of this column can be changed
   */
  public changeVisibility: boolean;
  public width?: number;
  public fieldValueFN?: (dataobject: any, field: ColumnConfig, valueField: any) => any;
  public headerSuffix?: string;
  public headerPrefix?: string;
  public filterType?: FilterType;
  public filterValues?: ValueLabelHtmlSelectOptions[];

  /**
   * Value of the object will be translated with the translation service. The value must be in the objects value is
   * the key for the translation. This key must be defined in the translation files.
   */
  public translateValues: TranslateValue;
  public translatedValueMap: { [key: string]: string };
  public fieldTranslated: string;
  /**
   * Normal used fpr a ngSwitchCase. For example, for the green/red number representation
   */
  templateName?: string;
  minFractionDigits?: number;
  maxFractionDigits?: number;
  /**
   * Some columns may have a row group, for example the total of values of this cholumn.
   * The format are the same but the field property is different.
   */
  columnGroupConfigs?: ColumnGroupConfig[];

  /**
   * IFields are displayed grouped visibly for the user
   */
  fieldsetName: string;

  /**
   * If true this field will be exported when export functionality is available
   */
  export?: boolean;

  frozenColumn?: boolean;

  /**
   * Any value
   */
  userValue: any;

  constructor(dataType: DataType, field: string, headerKey: string, visible: boolean,
    changeVisibility: boolean, optionalsParams?: OptionalParams) {
    this.dataType = dataType;
    this.field = field;
    this.headerKey = headerKey;
    this.visible = visible;
    this.changeVisibility = changeVisibility;


    if (optionalsParams) {
      this.width = optionalsParams.width;
      this.fieldValueFN = optionalsParams.fieldValueFN;
      this.headerSuffix = optionalsParams.headerSuffix;
      this.translateValues = optionalsParams.translateValues;
      this.headerPrefix = optionalsParams.headerPrefix;
      this.templateName = (optionalsParams.templateName) ? optionalsParams.templateName : '';
      this.maxFractionDigits = optionalsParams.maxFractionDigits;
      this.minFractionDigits = optionalsParams.minFractionDigits;
      this.columnGroupConfigs = optionalsParams.columnGroupConfigs;
      this.filterType = optionalsParams.filterType;
      this.filterValues = optionalsParams.filterValues;
      this.headerTranslated = optionalsParams.headerTranslated;
      this.export = optionalsParams.export;
      this.frozenColumn = optionalsParams.frozenColumn;
      this.userValue = optionalsParams.userValue;

      this.fieldsetName = optionalsParams.fieldsetName;
    }
  }
}

export interface OptionalParams {
  width?: number;
  fieldValueFN?: (dataobject: any, field: ColumnConfig, valueField: any) => any;
  headerSuffix?: string;
  headerPrefix?: string;
  /**
   * Cell content may be translated in an other language
   */
  translateValues?: TranslateValue;
  templateName?: string;
  maxFractionDigits?: number;
  minFractionDigits?: number;
  columnGroupConfigs?: ColumnGroupConfig[];
  filterType?: FilterType;
  filterValues?: ValueLabelHtmlSelectOptions[];
  fieldsetName?: string;
  headerTranslated?: string;
  export?: boolean;
  frozenColumn?: boolean;
  userValue?: any;
}

/**
 * Some table views present data with a group or grand total. The data to be shown is defined with this class.
 */
export class ColumnGroupConfig {
  textValueTranslated: string;
  colspan: number;

  constructor(public fieldValue: string, public textValueKey?: string, public fieldTextFN?: (columnConfig: ColumnConfig,
      arrIndex: number, groupChangeIndexMap: any, rowIndex: number) => any,
    optionalsGropuParams?: OptionalGroupParams) {
    if (optionalsGropuParams) {
      this.colspan = optionalsGropuParams.colspan;
    }
  }
}

export interface OptionalGroupParams {
  /**
   * 99: use all free columns. This field will also be change programmatically.
   */
  colspan: number;
}

export enum TranslateValue {
  // Start with one otherwise a boolean check could be wrong
  // No other action than translation
  NORMAL = 1,
  // To uppercase before translation into store
  UPPER_CASE = 2
}

