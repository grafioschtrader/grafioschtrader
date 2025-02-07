import {DataType} from '../../dynamic-form/models/data.type';
import {CalendarConfig, FieldConfig} from '../../dynamic-form/models/field.config';
import {InputType} from '../../dynamic-form/models/input.type';
import {AppHelper} from './app.helper';
import {ValidatorFn, Validators} from '@angular/forms';
import {ErrorMessageRules, RuleEvent} from '../../dynamic-form/error/error.message.rules';

import {
  email,
  gtWithMask,
  maxValue,
  notContainStringInList,
  range,
  rangeLength,
  validISIN,
  webUrl
} from '../validator/validator';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {FileRequiredValidator} from '../../dynamic-form/components/form-input-file/file-input.validator';
import {NgxCurrencyConfig} from 'ngx-currency';

export enum VALIDATION_SPECIAL {
  ISIN,
  EMail,
  WEB_URL,
  GT_With_Mask_Param,
  NOT_CONTAIN_STRING_IN_LIST
}


interface ValidError {
  vFN?: ValidatorFn;
  msgR: ErrorMessageRules;
}

/**
 * Creates the corresponding input element from the definition of a field.
 * Optionally, the corresponding input checks are given to the input element.
 */
export class DynamicFieldHelper {
  public static readonly RULE_REQUIRED_TOUCHED = {name: 'required', keyi18n: 'required', rules: [RuleEvent.TOUCHED]};
  public static readonly RULE_REQUIRED_DIRTY = {name: 'required', keyi18n: 'required', rules: [RuleEvent.DIRTY]};

  static readonly minDateCalendar = new Date(2000, 0, 1);
  static readonly maxDateCalendar = new Date(2099, 11, 31);

  public static readonly validationsErrorMap: { [key: string]: ValidError } = {
    [VALIDATION_SPECIAL.ISIN]: {
      vFN: validISIN, msgR: {name: 'validISIN', keyi18n: 'validISIN', rules: [RuleEvent.TOUCHED, RuleEvent.DIRTY]
      }
    },
    [VALIDATION_SPECIAL.EMail]: {
      vFN: email,
      msgR: {name: 'email', keyi18n: 'patternEmail', rules: [RuleEvent.FOCUSOUT]}
    },
    [VALIDATION_SPECIAL.WEB_URL]: {
      vFN: webUrl, msgR: {
        name: 'webUrl', keyi18n: 'webUrl',
        rules: [RuleEvent.TOUCHED, RuleEvent.DIRTY]
      }
    },
    [VALIDATION_SPECIAL.GT_With_Mask_Param]: {
      msgR: {name: 'gt', keyi18n: 'gt', rules: [RuleEvent.DIRTY]}
    },
    [VALIDATION_SPECIAL.NOT_CONTAIN_STRING_IN_LIST]: {
      msgR: {name: 'notContainStringInList', keyi18n: 'notContainStringInList', rules: [RuleEvent.DIRTY]}
    },
  };
  private static readonly ADJUST_INPUT_WITH_UNTIL_MAX_LENGTH = 25;

  public static createFunctionButton(labelKey: string, buttonFN: (event?: any) => void, fieldOptions?: FieldOptions): FieldConfig {
    return this.createFunctionButtonFieldName(null, labelKey, buttonFN, fieldOptions);
  }

  public static createFunctionButtonFieldName(fieldName: string, labelKey: string, buttonFN: (event?: any) => void,
    fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig: FieldConfig = {
      dataType: DataType.None,
      inputType: InputType.Pbutton,
      field: fieldName,
      labelKey,
      buttonFN
    };
    fieldOptions && Object.assign(fieldConfig, fieldOptions);
    return fieldConfig;
  }

  public static createSubmitButton(labelKey = 'SAVE'): FieldConfig {
    return this.createSubmitButtonFieldName('submit', labelKey);
  }

  public static createSubmitButtonFieldName(fieldName: string, labelKey = 'SAVE'): FieldConfig {
    return {
      dataType: DataType.None,
      inputType: InputType.Pbutton,
      field: fieldName,
      labelKey
    };
  }

  public static createFileUpload(dataType: DataType.File | DataType.Files, fieldName: string, labelKey: string,
    acceptFileUploadType: string, required: boolean, fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig: FieldConfig = this.setFieldBaseAndOptions({
        dataType,
        inputType: InputType.FileUpload,
        acceptFileUploadType: '.' + acceptFileUploadType
      },
      fieldName, labelKey,
      required ? [FileRequiredValidator.validate] : null,
      required ? [this.RULE_REQUIRED_DIRTY] : null, fieldOptions);
    return fieldConfig;
  }


  public static createFieldSuggestionInputString(fieldName: string, labelKey: string, maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldInputAndTeString(DataType.String, InputType.InputSuggestion, fieldName, labelKey,
      maxLength, required, fieldOptions);
  }

  public static createFieldDropdownStringHeqF(fieldName: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig = DynamicFieldHelper.createFieldDropdownNumberString(DataType.String, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), required, fieldOptions);
    fieldConfig.defaultValue = fieldConfig.defaultValue || '';
    return fieldConfig;
  }

  private static createFieldDropdownNumberString(dataType: DataType, fieldName: string, labelKey: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return this.setFieldBaseAndOptions({
        dataType,
        inputType: InputType.InputDropdown
      },
      fieldName, labelKey,
      required ? [Validators.required] : null,
      required ? [this.RULE_REQUIRED_TOUCHED] : null, fieldOptions);
  }


  public static createFieldTextareaInputStringHeqF(fieldName: string, maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldInputAndTeString(DataType.String, InputType.Pinputtextarea, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), maxLength, required, fieldOptions);
  }

  public static createFieldTextareaInputString(fieldName: string, labelKey: string, maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldInputAndTeString(DataType.String, InputType.Pinputtextarea, fieldName, labelKey,
      maxLength, required, fieldOptions);
  }

  public static createFieldInputWebUrl(fieldName: string, labelKey: string, required: boolean, fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldInputStringVS(fieldName, labelKey, 254, required, [VALIDATION_SPECIAL.WEB_URL], fieldOptions);
  }

  public static createFieldInputWebUrlHeqF(fieldName: string, maxLength: number, required: boolean, fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldInputStringVSHeqF(fieldName, maxLength, required, [VALIDATION_SPECIAL.WEB_URL], fieldOptions);
  }

  public static createFieldInputStringVSHeqF(fieldName: string, maxLength: number, required: boolean,
    validationSpecials: VALIDATION_SPECIAL[], fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldDAInputStringVS(DataType.String, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), maxLength, required,
      validationSpecials, fieldOptions);
  }

  public static createFieldInputStringVSParam(fieldName: string, labelKey: string, maxLength: number, required: boolean,
    validationSpecial: VALIDATION_SPECIAL, param: any, fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.addValidationParam(DynamicFieldHelper.createFieldDAInputString(DataType.String, fieldName,
      labelKey, maxLength, required, fieldOptions), validationSpecial, param);
  }

  public static createFieldInputStringVS(fieldName: string, labelKey: string, maxLength: number, required: boolean,
    validationSpecials: VALIDATION_SPECIAL[], fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldDAInputStringVS(DataType.String, fieldName, labelKey, maxLength, required,
      validationSpecials, fieldOptions);
  }

  public static createFieldDAInputStringVSHeqF(dataType: DataType, fieldName: string, maxLength: number,
    required: boolean, validationSpecials: VALIDATION_SPECIAL[],
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.addValidations(DynamicFieldHelper.createFieldDAInputString(dataType, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), maxLength, required, fieldOptions), validationSpecials);
  }

  public static createFieldDAInputStringVS(dataType: DataType, fieldName: string, labelKey: string, maxLength: number,
    required: boolean, validationSpecials: VALIDATION_SPECIAL[],
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.addValidations(DynamicFieldHelper.createFieldDAInputString(dataType, fieldName,
      labelKey, maxLength, required, fieldOptions), validationSpecials);
  }

  public static createFieldInputStringHeqF(fieldName: string, maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldDAInputString(DataType.String, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), maxLength, required, fieldOptions);
  }

  public static createFieldInputButtonHeqF(dataType: DataType, fieldName: string, buttonFN: (event?: any) => void,
    required: boolean, fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldInputButton(dataType, fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName),
      buttonFN, required, fieldOptions);
  }

  public static createFieldInputButton(dataType: DataType, fieldName: string, labelKey: string, buttonFN: (event?: any) => void,
    required: boolean, fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig = DynamicFieldHelper.createFieldInputAndTeString(dataType, InputType.InputButton, fieldName, labelKey, null,
      required, fieldOptions);
    fieldConfig.buttonFN = buttonFN;
    return fieldConfig;
  }

  public static createFieldInputString(fieldName: string, labelKey: string, maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldDAInputString(DataType.String, fieldName, labelKey, maxLength, required, fieldOptions);
  }

  public static createFieldDAInputStringHeqF(dataType: DataType, fieldName: string, maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return this.createFieldDAInputString(dataType, fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName),
      maxLength, required, fieldOptions);
  }

  public static createFieldDAInputString(dataType: DataType, fieldName: string, labelKey: string, maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    if (maxLength < DynamicFieldHelper.ADJUST_INPUT_WITH_UNTIL_MAX_LENGTH && (!fieldOptions || !fieldOptions.inputWidth)) {
      fieldOptions = fieldOptions || {};
      fieldOptions.inputWidth = maxLength;
    }
    return DynamicFieldHelper.createFieldInputAndTeString(dataType, InputType.Input, fieldName, labelKey, maxLength, required,
      fieldOptions);
  }

  public static createFieldSelectNumberHeqF(fieldName: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldSelectNumberString(InputType.Select, DataType.Numeric, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), required,
      fieldOptions);
  }

  public static createFieldSelectNumber(fieldName: string, labelKey: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldSelectNumberString(InputType.Select, DataType.Numeric, fieldName, labelKey, required,
      fieldOptions);
  }

  public static createFieldPcalendarHeqF(dataType: DataType.DateString | DataType.DateNumeric | DataType.DateTimeNumeric
      | DataType.DateStringShortUS, fieldName: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return this.createFieldPcalendar(dataType, fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName),
      required, fieldOptions);
  }

  public static createFieldPcalendar(dataType: DataType.DateString | DataType.DateNumeric | DataType.DateTimeNumeric
      | DataType.DateStringShortUS, fieldName: string, labelKey: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig = this.setFieldBaseAndOptions({dataType, inputType: InputType.Pcalendar},
      fieldName, labelKey,
      required ? [Validators.required] : null,
      required ? [this.RULE_REQUIRED_TOUCHED] : null, fieldOptions);
    fieldConfig.calendarConfig = Object.assign({}, {minDate: this.minDateCalendar, maxDate: this.maxDateCalendar},
      fieldConfig?.calendarConfig);

    return fieldConfig;
  }

  public static createFieldMultiSelectStringHeqF(fieldName: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig = DynamicFieldHelper.createFieldSelectNumberString(InputType.MultiSelect, DataType.String, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), required, fieldOptions);
    fieldConfig.defaultValue = fieldConfig.defaultValue || [];
    return fieldConfig;
  }

  public static createFieldMultiSelectString(fieldName: string, labelKey: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig = DynamicFieldHelper.createFieldSelectNumberString(InputType.MultiSelect, DataType.String,
      fieldName, labelKey, required, fieldOptions);
    fieldConfig.defaultValue = fieldConfig.defaultValue || [];
    return fieldConfig;
  }

  public static createFieldSelectStringHeqF(fieldName: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig = DynamicFieldHelper.createFieldSelectNumberString(InputType.Select, DataType.String, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), required, fieldOptions);
    fieldConfig.defaultValue = fieldConfig.defaultValue || '';
    return fieldConfig;
  }

  public static createFieldSelectString(fieldName: string, labelKey: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig = DynamicFieldHelper.createFieldSelectNumberString(InputType.Select, DataType.String,
      fieldName, labelKey, required, fieldOptions);
    fieldConfig.defaultValue = fieldConfig.defaultValue || '';
    return fieldConfig;
  }

  /**
   * Primeng p-inputNumber maybe not working
   */
  public static createFieldInputNumberHeqF(fieldName: string, required: boolean, integerLimit: number,
    maxFractionDigits: number, allowNegative: boolean, fieldOptions?: FieldOptions): FieldConfig {

    return this.createFieldInputNumber(fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), required,
      integerLimit, maxFractionDigits, allowNegative, fieldOptions);
  }


  /**
   * Primeng p-inputNumber maybe not working
   */
  public static createFieldInputNumber(fieldName: string, labelKey: string, required: boolean, integerLimit: number,
    maxFractionDigits: number, allowNegative: boolean, fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig: FieldConfig = this.setFieldBaseAndOptions({
        dataType: DataType.Numeric,
        inputType: InputType.InputNumber
      },
      fieldName, labelKey, required ? [Validators.required] : null, required ? [this.RULE_REQUIRED_TOUCHED] : null, fieldOptions);
    fieldConfig.inputNumberSettings = {maxFractionDigits};
    fieldConfig.max = Number('9'.repeat(integerLimit) + '.' + '9'.repeat(maxFractionDigits));
    fieldConfig.min = allowNegative ? fieldConfig.max * -1 : required ? 1 / Math.pow(10, maxFractionDigits) : 0;
    return fieldConfig;
  }

  public static createFieldCurrencyNumber(fieldName: string, labelKey: string, required: boolean,
    integerLimit: number, maxFractionDigits: number,
    allowNegative: boolean, defaultCurrencyMaskConfig: NgxCurrencyConfig,
    isCurrency: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return this.createFieldCurrencyNumberVSParam(fieldName, labelKey, required, integerLimit, maxFractionDigits, allowNegative,
      defaultCurrencyMaskConfig, null, null, isCurrency, fieldOptions);
  }

  public static createFieldCurrencyNumberHeqF(fieldName: string, required: boolean,
    integerLimit: number, maxFractionDigits: number,
    allowNegative: boolean, defaultCurrencyMaskConfig: NgxCurrencyConfig,
    isCurrency: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return this.createFieldCurrencyNumberVSParamHeqF(fieldName, required, integerLimit, maxFractionDigits, allowNegative,
      defaultCurrencyMaskConfig, null, null, isCurrency, fieldOptions);
  }

  public static createFieldCurrencyNumberVSParamHeqF(fieldName: string, required: boolean,
    integerLimit: number, maxFractionDigits: number,
    allowNegative: boolean, defaultCurrencyMaskConfig: NgxCurrencyConfig,
    validationSpecials: VALIDATION_SPECIAL, param: number,
    isCurrency: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return this.createFieldCurrencyNumberVSParam(fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), required,
      integerLimit, maxFractionDigits, allowNegative, defaultCurrencyMaskConfig, validationSpecials, param,
      isCurrency, fieldOptions);
  }

  public static createFieldCurrencyNumberVSParam(fieldName: string, labelKey: string, required: boolean,
    integerDigits: number, maxFractionDigits: number,
    allowNegative: boolean, defaultCurrencyMaskConfig: NgxCurrencyConfig,
    validationSpecials: VALIDATION_SPECIAL, param: number,
    isCurrency: boolean,
    fieldOptions?: FieldOptions): FieldConfig {

    const width = fieldOptions?.inputWidth || (integerDigits + maxFractionDigits) + (isCurrency ? 4 : 0);
    const fieldConfig: FieldConfig = this.setFieldBaseAndOptions({
        dataType: DataType.Numeric,
        inputType: InputType.InputCurrencyNumber
      }, fieldName, labelKey,
      required ? [Validators.required] : null, required ? [this.RULE_REQUIRED_TOUCHED] : null, {
        ...fieldOptions,
        inputWidth: width
      });
    this.setMinMaxValues(fieldConfig, integerDigits, maxFractionDigits, allowNegative);
    fieldConfig.currencyMaskConfig = {
      ...defaultCurrencyMaskConfig,
      ...{
        precision: maxFractionDigits, allowNegative, min: fieldConfig.min, max: fieldConfig.max
      }
    };

    return validationSpecials ? this.addValidationParam(fieldConfig, validationSpecials, param) : fieldConfig;
  }

  public static setCurrency(fieldConfig: FieldConfig, currency: string): void {
    if (fieldConfig.inputType === InputType.InputCurrencyNumber) {
      fieldConfig.currencyMaskConfig.prefix = AppHelper.addSpaceToCurrency(currency);
    } else {
      fieldConfig.inputNumberSettings.currency = currency;
    }
  }


  public static adjustNumberFraction(fieldConfig: FieldConfig, integerDigits: number, precision: number): void {
    if (fieldConfig.inputType === InputType.InputCurrencyNumber) {
      fieldConfig.currencyMaskConfig.precision = precision;
      DynamicFieldHelper.setCurrencyMaskMaxMin(fieldConfig, integerDigits, precision);
    } else {
      this.setMinMaxValues(fieldConfig, integerDigits, precision, fieldConfig.currencyMaskConfig.allowNegative);
      fieldConfig.inputNumberSettings.maxFractionDigits = precision;
    }
  }

  public static setCurrencyMaskMaxMin(fieldConfig: FieldConfig, integerDigits: number, maxFractionDigits: number): void {
    this.setMinMaxValues(fieldConfig, integerDigits, maxFractionDigits, fieldConfig.currencyMaskConfig.allowNegative);
    const newMask = fieldConfig.currencyMaskConfig = {
      ...fieldConfig.currencyMaskConfig,
      ...{min: fieldConfig.min, max: fieldConfig.max}
    };
    fieldConfig.currencyMaskConfig = newMask;
  }

  public static isRequired(fieldConfig: FieldConfig): boolean {
    return fieldConfig.validation && fieldConfig.validation.includes(Validators.required);
  }

  public static createFieldCheckboxHeqF(fieldName: string, fieldOptions?: FieldOptions): FieldConfig {
    return this.setFieldBaseAndOptions({dataType: DataType.Boolean, inputType: InputType.Checkbox},
      fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), null, null, fieldOptions);
  }

  public static createFieldCheckbox(fieldName: string, labelKey: string, fieldOptions?: FieldOptions): FieldConfig {
    return this.setFieldBaseAndOptions({dataType: DataType.Boolean, inputType: InputType.Checkbox},
      fieldName, labelKey, null, null, fieldOptions);
  }

  public static createFieldTriStateCheckboxHeqF(fieldName: string, fieldOptions?: FieldOptions): FieldConfig {
    return this.setFieldBaseAndOptions({dataType: DataType.Boolean, inputType: InputType.TriStateCheckbox},
      fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), null, null, fieldOptions);
  }

  public static createFieldTriStateCheckbox(fieldName: string, labelKey: string, fieldOptions?: FieldOptions): FieldConfig {
    return this.setFieldBaseAndOptions({dataType: DataType.Boolean, inputType: InputType.TriStateCheckbox},
      fieldName, labelKey, null, null, fieldOptions);
  }

  public static createFieldMinMaxNumberHeqF(dataType: DataType.Numeric | DataType.NumericInteger, fieldName: string,
    required: boolean, min: number, max: number, fieldOptions?: FieldOptions): FieldConfig {
    return this.createFieldMinMaxNumber(dataType, fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName),
      required, min, max, fieldOptions);
  }

  public static createFieldMinMaxNumber(dataType: DataType.Numeric | DataType.NumericInteger, fieldName: string, labelKey: string,
    required: boolean, min: number, max: number, fieldOptions?: FieldOptions): FieldConfig {
    const validations: ValidatorFn[] = required ? [Validators.required] : [];
    const errorMessageRules: ErrorMessageRules[] = required ? [this.RULE_REQUIRED_TOUCHED] : [];

    if (min !== null && max) {
      validations.push(range([min, max]));
      errorMessageRules.push({name: 'range', keyi18n: 'range', param1: min, param2: max, rules: [RuleEvent.DIRTY]});
    } else if (max) {
      validations.push(maxValue(max));
      errorMessageRules.push({name: 'max', keyi18n: 'max', param1: max, rules: [RuleEvent.DIRTY]});
    }

    const maxLength = Math.max(min ? min.toString().length : 0, max.toString().length);
    (fieldOptions = fieldOptions || {}).inputWidth = maxLength + 2;

    return this.setFieldBaseAndOptions({
        dataType,
        inputType: InputType.Input,
        min,
        max,
        maxLength
      },
      fieldName, labelKey, validations, errorMessageRules, fieldOptions);
  }

  private static createFieldSelectNumberString(intputType: InputType, dataType: DataType, fieldName: string, labelKey: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return this.setFieldBaseAndOptions({
        dataType,
        inputType: intputType
      },
      fieldName, labelKey,
      required ? [Validators.required] : null,
      required ? [this.RULE_REQUIRED_TOUCHED] : null, fieldOptions);
  }

  private static createFieldInputAndTeString(dataType: DataType, inputType: InputType, fieldName: string, labelKey: string,
    maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig: FieldConfig = this.setFieldBaseAndOptions({
        dataType,
        inputType,
        maxLength
      },
      fieldName, labelKey,
      required ? [Validators.required] : null,
      required ? [this.RULE_REQUIRED_TOUCHED] : null, fieldOptions);
    if (fieldOptions && fieldOptions.minLength) {
      (fieldConfig.validation = fieldConfig.validation || []).push(
        rangeLength([fieldOptions.minLength, maxLength]));
      (fieldConfig.errors = fieldConfig.errors || []).push({
        name: 'rangeLength', keyi18n: 'rangeLength', param1: fieldOptions.minLength, param2: maxLength,
        rules: [RuleEvent.TOUCHED]
      });
    }

    return fieldConfig;
  }

  private static setMinMaxValues(fieldConfig: FieldConfig, integerDigits: number, maxFractionDigits: number,
    allowNegative: boolean): void {
    fieldConfig.max = Number('9'.repeat(integerDigits) + '.' + '9'.repeat(maxFractionDigits));
    fieldConfig.min = allowNegative ?
      fieldConfig.max * -1 : DynamicFieldHelper.isRequired(fieldConfig) ? 1 / Math.pow(10, maxFractionDigits) : 0;
  }

  private static setFieldBaseAndOptions(fieldConfig: FieldConfig, fieldName: string,
    labelKey: string, validations: ValidatorFn[], errorMessageRules: ErrorMessageRules[],
    fieldOptions: FieldOptions): FieldConfig {
    fieldConfig.field = fieldName;
    fieldConfig.labelKey = labelKey;
    fieldConfig.validation = validations;
    fieldConfig.errors = errorMessageRules;
    if (fieldOptions) {
      Object.assign(fieldConfig, fieldOptions);
    }
    return fieldConfig;
  }

  // For validator
  //////////////////////////////////////////////////////////////
  public static resetValidator(fieldConfig: FieldConfig, validation: ValidatorFn[], errors?: ErrorMessageRules[]): void {
    fieldConfig.validation = validation;
    fieldConfig.formControl.setValidators(fieldConfig.validation);
    fieldConfig.errors = errors;
    fieldConfig.formControl.updateValueAndValidity();
    fieldConfig.baseInputComponent?.reEvaluateRequired();
  }

  private static addValidations(fieldConfig: FieldConfig, validationSpecials: VALIDATION_SPECIAL[]): FieldConfig {
    for (const validationSpecial of validationSpecials) {
      const validError: ValidError = DynamicFieldHelper.validationsErrorMap[validationSpecial];
      this.addValidationParam(fieldConfig, validationSpecial, null);
    }
    return fieldConfig;
  }

  public static addValidationParam(fieldConfig: FieldConfig, validationSpecial: VALIDATION_SPECIAL, param1: any,
    param2?: any): FieldConfig {
    const validError: ValidError = DynamicFieldHelper.validationsErrorMap[validationSpecial];
    (fieldConfig.errors = fieldConfig.errors || []).push(validError.msgR);
    switch (validationSpecial) {
      case VALIDATION_SPECIAL.GT_With_Mask_Param:
        validError.vFN = gtWithMask(param1);
        validError.msgR.param1 = param1;
        break;
      case VALIDATION_SPECIAL.NOT_CONTAIN_STRING_IN_LIST:
        validError.vFN = notContainStringInList(param1);
        validError.msgR.param1 = param1;
        break;

    }
    (fieldConfig.validation = fieldConfig.validation || []).push(validError.vFN);
    return fieldConfig;
  }

}

export interface FieldOptions {
  defaultValue?: number | string | boolean | Date;
  fieldSuffix?: string;
  fieldsetName?: string;
  textareaRows?: number;
  labelHelpText?: string;
  labelSuffix?: string;
  icon?: string;
  usedLayoutColumns?: number;
  dataproperty?: string;
  disabled?: boolean;
  readonly?: boolean;
  groupItemUseOrLoading?: boolean;
  inputWidth?: number;
  upperCase?: boolean;
  userDefinedValue?: number | string;
  minLength?: number;
  suggestionsFN?: (any) => void;
  invisible?: boolean;
  valueKeyHtmlOptions?: ValueKeyHtmlSelectOptions[];
  buttonInForm?: boolean;
  calendarConfig?: CalendarConfig;
  handleChangeFileInputFN?: (fileList: FileList) => void;
}

export interface FieldOptionsCc extends FieldOptions {
  targetField?: string;
}

