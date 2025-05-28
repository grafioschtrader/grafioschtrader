import {FilterType} from './filter.type';
import {ValueLabelHtmlSelectOptions} from './value.label.html.select.options';
import {BaseFieldDefinition, PropertyEditShare} from '../../dynamic-form/models/base.field.definition';
import {ValidationErrorRule} from '../../dynamic-form/models/base.field.fieldgroup.config';

/**
 * It represents the field configuration for a table view. It may also be used to show some single data definition.
 */
export interface ColumnConfig extends BaseFieldDefinition {

  headerKey: string;
  headerTranslated: string;
  headerTooltipTranslated: string;
  visible: boolean;

  /**
   * Visibility of this column can be changed
   */
  changeVisibility: boolean;
  width?: number;
  fieldValueFN?: (dataobject: any, field: ColumnConfig, valueField: any) => any;
  headerSuffix?: string;
  headerPrefix?: string;
  filterType?: FilterType;
  filterValues?: ValueLabelHtmlSelectOptions[];

  /**
   * Value of the object will be translated with the translation service. The value must be in the objects value is
   * the key for the translation. This key must be defined in the translation files.
   */
  translateValues: TranslateValue;
  translatedValueMap: { [key: string]: string };
  fieldTranslated: string;
  /**
   * Normal used fpr a ngSwitchCase. For example, for the green/red number representation
   */
  templateName?: string;
  minFractionDigits?: number;
  maxFractionDigits?: number;
  /**
   * Some columns may have a row group, for example the total of values of this cholumn.
   * The format is the same but the field property is different.
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

  /**
   * A column may be editable
   */
  cec: ColumnEditConfig;
}

export interface OptionalParams {
  width?: number;
  fieldValueFN?: (dataobject: any, field: ColumnConfig, valueField: any) => any;
  headerSuffix?: string;
  headerPrefix?: string;
  /**
   * Cell content may be translated into another language
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


export interface ColumnEditConfig extends PropertyEditShare, ValidationErrorRule {
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
  UPPER_CASE = 2,

  UPPER_CASE_ARRAY_TO_COMMA_SEPERATED = 3

}

