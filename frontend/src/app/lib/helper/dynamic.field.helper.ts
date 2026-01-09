import {DataType} from '../dynamic-form/models/data.type';
import {CalendarConfig, FieldConfig} from '../dynamic-form/models/field.config';
import {InputType} from '../dynamic-form/models/input.type';
import {AppHelper} from './app.helper';
import {ValidatorFn, Validators} from '@angular/forms';
import {ErrorMessageRules, RuleEvent} from '../dynamic-form/error/error.message.rules';
import {MenuItem} from 'primeng/api';

import {
  email,
  gtWithMask,
  maxValue,
  notContainStringInList,
  notZero,
  range,
  rangeLength,
  webUrl
} from '../validator/validator';
import {ValueKeyHtmlSelectOptions} from '../dynamic-form/models/value.key.html.select.options';
import {FileRequiredValidator} from '../dynamic-form/components/form-input-file/file-input.validator';


/**
 * Enumeration of special validation types available for form fields.
 * These correspond to predefined validation functions and error messages.
 */
export enum VALIDATION_SPECIAL {
  /** Email address format validation */
  EMail,
  /** Web URL format validation */
  WEB_URL,
  /** Greater than validation with mask support for formatted numbers */
  GT_With_Mask_Param,
  /** Validation to ensure value is not in a specified list of strings */
  NOT_CONTAIN_STRING_IN_LIST,
  /** Validation to ensure value is not zero (allows negative and positive) */
  NOT_ZERO
}

/**
 * Interface for registering custom validation types.
 * Applications can extend validation capabilities by implementing this interface.
 */
export interface CustomValidationConfig {
  /** Unique identifier for the validation type */
  key: string;
  /** Validator function (optional if created dynamically with parameters) */
  validatorFn?: ValidatorFn;
  /** Error message configuration */
  errorConfig: ErrorMessageRules;
  /** Factory function for creating parameterized validators */
  validatorFactory?: (param1?: any, param2?: any) => ValidatorFn;
}

/**
 * Internal interface combining validation function and error message configuration.
 * Used in the validationsErrorMap to associate validators with their error messages.
 */
interface ValidError {
  /** Optional validator function (some validators are created dynamically with parameters) */
  vFN?: ValidatorFn;
  /** Error message configuration including translation key and trigger rules */
  msgR: ErrorMessageRules;

  factory?: (param1?: any, param2?: any) => ValidatorFn;
}

/**
 * Creates the corresponding input element from the definition of a field.
 * Optionally, the corresponding input checks are given to the input element.
 */
export class DynamicFieldHelper {

  /** Pre-configured error rule for required fields triggered on touch */
  public static readonly RULE_REQUIRED_TOUCHED = {name: 'required', keyi18n: 'required', rules: [RuleEvent.TOUCHED]};

  /** Pre-configured error rule for required fields triggered on dirty state */
  public static readonly RULE_REQUIRED_DIRTY = {name: 'required', keyi18n: 'required', rules: [RuleEvent.DIRTY]};

  /** Minimum allowed date for calendar components (January 1, 2000) */
  static readonly minDateCalendar = new Date(2000, 0, 1);

  /** Maximum allowed date for calendar components (December 31, 2099) */
  static readonly maxDateCalendar = new Date(2099, 11, 31);

  /**
   * Map of validation types to their validator functions and error configurations.
   * Can be extended at runtime with application-specific validators.
   */
  private static validationsErrorMap: { [key: string]: ValidError } = {
    [VALIDATION_SPECIAL.EMail]: {
      vFN: email,
      msgR: {name: 'email', keyi18n: 'patternEmail', rules: [RuleEvent.FOCUSOUT]}
    },
    [VALIDATION_SPECIAL.WEB_URL]: {
      vFN: webUrl,
      msgR: {name: 'webUrl', keyi18n: 'webUrl', rules: [RuleEvent.TOUCHED, RuleEvent.DIRTY]}
    },
    [VALIDATION_SPECIAL.GT_With_Mask_Param]: {
      msgR: {name: 'gt', keyi18n: 'gt', rules: [RuleEvent.DIRTY]},
      factory: (param1) => gtWithMask(param1)
    },
    [VALIDATION_SPECIAL.NOT_CONTAIN_STRING_IN_LIST]: {
      msgR: {name: 'notContainStringInList', keyi18n: 'notContainStringInList', rules: [RuleEvent.DIRTY]},
      factory: (param1) => notContainStringInList(param1)
    },
    [VALIDATION_SPECIAL.NOT_ZERO]: {
      vFN: notZero,
      msgR: {name: 'notZero', keyi18n: 'notZero', rules: [RuleEvent.DIRTY]}
    }
  };

  /** Maximum input length threshold for automatic width adjustment */
  private static readonly ADJUST_INPUT_WITH_UNTIL_MAX_LENGTH = 25;

  public static registerCustomValidation(config: CustomValidationConfig): void {
    this.validationsErrorMap[config.key] = {
      vFN: config.validatorFn,
      msgR: config.errorConfig,
      factory: config.validatorFactory
    };
  }

  /**
   * Creates a function button with auto-generated field name.
   * Convenience method for buttons that don't need specific field names.
   *
   * @param labelKey Translation key for button label
   * @param buttonFN Function to execute when button is clicked
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for PrimeNG button component
   */
  public static createFunctionButton(labelKey: string, buttonFN: (event?: any) => void, fieldOptions?: FieldOptions): FieldConfig {
    return this.createFunctionButtonFieldName(null, labelKey, buttonFN, fieldOptions);
  }

  /**
   * Creates a function button with specified field name.
   * Used for buttons that need specific field names for form identification.
   *
   * @param fieldName Unique field identifier within the form
   * @param labelKey Translation key for button label
   * @param buttonFN Function to execute when button is clicked
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for PrimeNG button component
   */
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

  /**
   * Creates a submit button with default field name 'submit'.
   * Standard submit button for form submission with customizable label.
   *
   * @param labelKey Translation key for button label (defaults to 'SAVE')
   * @returns FieldConfig for submit button
   */
  public static createSubmitButton(labelKey = 'SAVE'): FieldConfig {
    return this.createSubmitButtonFieldName('submit', labelKey);
  }

  /**
   * Creates a submit button with custom field name.
   * Allows multiple submit buttons in a form with different identifiers.
   *
   * @param fieldName Unique field identifier for the submit button
   * @param labelKey Translation key for button label (defaults to 'SAVE')
   * @returns FieldConfig for submit button
   */
  public static createSubmitButtonFieldName(fieldName: string, labelKey = 'SAVE'): FieldConfig {
    return {
      dataType: DataType.None,
      inputType: InputType.Pbutton,
      field: fieldName,
      labelKey
    };
  }

  /**
   * Creates a file upload field with validation and type restrictions.
   * Supports single or multiple file uploads with file type validation.
   *
   * @param dataType File upload type (DataType.File or DataType.Files)
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param acceptFileUploadType Allowed file extension (without dot)
   * @param required Whether file selection is mandatory
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for file upload component
   */
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

  /**
   * Creates a text input field with autocomplete suggestions.
   * Provides typeahead functionality for enhanced user experience.
   *
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param fieldOptions Additional configuration options including suggestion function
   * @returns FieldConfig for suggestion input component
   */
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

  /**
   * Creates a dropdown field with auto-generated label key.
   * Convenience method that generates label key from field name using naming conventions.
   *
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param required Whether selection is mandatory
   * @param fieldOptions Additional configuration options including dropdown items
   * @returns FieldConfig for dropdown component with empty string default value
   */
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

  /**
   * Creates a textarea field with auto-generated label key.
   * Large text input area with automatic label key generation from field name.
   *
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param fieldOptions Additional configuration options including row count
   * @returns FieldConfig for textarea component
   */
  public static createFieldTextareaInputStringHeqF(fieldName: string, maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldInputAndTeString(DataType.String, InputType.Pinputtextarea, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), maxLength, required, fieldOptions);
  }

  /**
   * Creates a textarea field with custom label key.
   * Large text input area for multi-line text with specified label.
   *
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param fieldOptions Additional configuration options including row count
   * @returns FieldConfig for textarea component
   */
  public static createFieldTextareaInputString(fieldName: string, labelKey: string, maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldInputAndTeString(DataType.String, InputType.Pinputtextarea, fieldName, labelKey,
      maxLength, required, fieldOptions);
  }

  /**
   * Creates a web URL input field with validation.
   * Text input with built-in URL format validation (max 254 characters).
   *
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param required Whether input is mandatory
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for URL input with web URL validation
   */
  public static createFieldInputWebUrl(fieldName: string, labelKey: string, required: boolean, fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldInputStringVS(fieldName, labelKey, 254, required, [VALIDATION_SPECIAL.WEB_URL], fieldOptions);
  }

  /**
   * Creates a web URL input field with auto-generated label and custom max length.
   * URL input with automatic label generation and configurable length limit.
   *
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for URL input with web URL validation
   */
  public static createFieldInputWebUrlHeqF(fieldName: string, maxLength: number, required: boolean, fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldInputStringVSHeqF(fieldName, maxLength, required, [VALIDATION_SPECIAL.WEB_URL], fieldOptions);
  }

  /**
   * Creates a string input field with special validation and auto-generated label.
   * Text input with custom validation rules and automatic label key generation.
   *
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param validationSpecials Array of special validation types to apply
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for string input with specified validations
   */
  public static createFieldInputStringVSHeqF(fieldName: string, maxLength: number, required: boolean,
    validationSpecials: (VALIDATION_SPECIAL | string)[], fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldDAInputStringVS(DataType.String, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), maxLength, required,
      validationSpecials, fieldOptions);
  }

  /**
   * Creates a string input field with parameterized validation.
   * Text input with custom validation that accepts parameters (e.g., greater than value).
   *
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param validationSpecial Single validation type that requires parameters
   * @param param Parameter value for the validation function
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for string input with parameterized validation
   */
  public static createFieldInputStringVSParam(fieldName: string, labelKey: string, maxLength: number, required: boolean,
    validationSpecial: (VALIDATION_SPECIAL | string), param: any, fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.addValidationParam(DynamicFieldHelper.createFieldDAInputString(DataType.String, fieldName,
      labelKey, maxLength, required, fieldOptions), validationSpecial, param);
  }

  /**
   * Creates a string input field with special validation and custom label.
   * Text input with multiple validation rules and specified label key.
   *
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param validationSpecials Array of special validation types to apply
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for string input with specified validations
   */
  public static createFieldInputStringVS(fieldName: string, labelKey: string, maxLength: number, required: boolean,
    validationSpecials: VALIDATION_SPECIAL[], fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldDAInputStringVS(DataType.String, fieldName, labelKey, maxLength, required,
      validationSpecials, fieldOptions);
  }

  /**
   * Creates a data type specific input with special validation and auto-generated label.
   * Input field for custom data types (Email, Password, etc.) with validation.
   *
   * @param dataType Specific data type for the input field
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param validationSpecials Array of special validation types to apply
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for typed input with specified validations
   */
  public static createFieldDAInputStringVSHeqF(dataType: DataType, fieldName: string, maxLength: number,
    required: boolean, validationSpecials: VALIDATION_SPECIAL[],
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.addValidations(DynamicFieldHelper.createFieldDAInputString(dataType, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), maxLength, required, fieldOptions), validationSpecials);
  }

  /**
   * Creates a data type specific input with special validation and custom label.
   * Input field for custom data types with multiple validation rules.
   *
   * @param dataType Specific data type for the input field
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param validationSpecials Array of special validation types to apply
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for typed input with specified validations
   */
  public static createFieldDAInputStringVS(dataType: DataType, fieldName: string, labelKey: string, maxLength: number,
    required: boolean, validationSpecials: (VALIDATION_SPECIAL | string)[],
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.addValidations(DynamicFieldHelper.createFieldDAInputString(dataType, fieldName,
      labelKey, maxLength, required, fieldOptions), validationSpecials);
  }

  /**
   * Creates a string input field with auto-generated label.
   * Basic text input with automatic label key generation from field name.
   *
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for basic string input
   */
  public static createFieldInputStringHeqF(fieldName: string, maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldDAInputString(DataType.String, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), maxLength, required, fieldOptions);
  }

  /**
   * Creates an input field with attached button and auto-generated label.
   * Text input with integrated button for actions like search or clear.
   *
   * @param dataType Data type for the input field
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param buttonFN Function to execute when button is clicked
   * @param required Whether input is mandatory
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for input with integrated button
   */
  public static createFieldInputButtonHeqF(dataType: DataType, fieldName: string, buttonFN: (event?: any) => void,
    required: boolean, fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldInputButton(dataType, fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName),
      buttonFN, required, fieldOptions);
  }

  /**
   * Creates an input field with attached button and custom label.
   * Text input with integrated button for enhanced functionality.
   *
   * @param dataType Data type for the input field
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param buttonFN Function to execute when button is clicked
   * @param required Whether input is mandatory
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for input with integrated button
   */
  public static createFieldInputButton(dataType: DataType, fieldName: string, labelKey: string, buttonFN: (event?: any) => void,
    required: boolean, fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig = DynamicFieldHelper.createFieldInputAndTeString(dataType, InputType.InputButton, fieldName, labelKey, null,
      required, fieldOptions);
    fieldConfig.buttonFN = buttonFN;
    return fieldConfig;
  }

  /**
   * Creates a basic string input field with custom label.
   * Standard text input field with specified label and validation.
   *
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for basic string input
   */
  public static createFieldInputString(fieldName: string, labelKey: string, maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldDAInputString(DataType.String, fieldName, labelKey, maxLength, required, fieldOptions);
  }

  /**
   * Creates a data type specific input with auto-generated label.
   * Input field for specific data types (Email, Password, etc.) with automatic labeling.
   *
   * @param dataType Specific data type for the input field
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for typed input field
   */
  public static createFieldDAInputStringHeqF(dataType: DataType, fieldName: string, maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return this.createFieldDAInputString(dataType, fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName),
      maxLength, required, fieldOptions);
  }

  /**
   * Creates a data type specific input with custom label.
   * Input field for specific data types with automatic width adjustment for short inputs.
   *
   * @param dataType Specific data type for the input field
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for typed input field with optimized width
   */
  public static createFieldDAInputString(dataType: DataType, fieldName: string, labelKey: string, maxLength: number, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    if (maxLength < DynamicFieldHelper.ADJUST_INPUT_WITH_UNTIL_MAX_LENGTH && (!fieldOptions || !fieldOptions.inputWidth)) {
      fieldOptions = fieldOptions || {};
      fieldOptions.inputWidth = maxLength;
    }
    return DynamicFieldHelper.createFieldInputAndTeString(dataType, InputType.Input, fieldName, labelKey, maxLength, required,
      fieldOptions);
  }

  /**
   * Creates a numeric select field with auto-generated label.
   * Dropdown for numeric values with automatic label key generation.
   *
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param required Whether selection is mandatory
   * @param fieldOptions Additional configuration options including dropdown items
   * @returns FieldConfig for numeric select component
   */
  public static createFieldSelectNumberHeqF(fieldName: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldSelectNumberString(InputType.Select, DataType.Numeric, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), required,
      fieldOptions);
  }

  /**
   * Creates a numeric select field with custom label.
   * Dropdown for numeric values with specified label.
   *
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param required Whether selection is mandatory
   * @param fieldOptions Additional configuration options including dropdown items
   * @returns FieldConfig for numeric select component
   */
  public static createFieldSelectNumber(fieldName: string, labelKey: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return DynamicFieldHelper.createFieldSelectNumberString(InputType.Select, DataType.Numeric, fieldName, labelKey, required,
      fieldOptions);
  }

  /**
   * Creates a calendar date picker with auto-generated label.
   * Date input component with automatic label generation and date range constraints.
   *
   * @param dataType Date data type (DateString, DateNumeric, DateTimeNumeric, or DateStringShortUS)
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param required Whether date selection is mandatory
   * @param fieldOptions Additional configuration options including calendar constraints
   * @returns FieldConfig for calendar component with default date range (2000-2099)
   */
  public static createFieldPcalendarHeqF(dataType: DataType.DateString | DataType.DateNumeric | DataType.DateTimeNumeric
      | DataType.DateStringShortUS, fieldName: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    return this.createFieldPcalendar(dataType, fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName),
      required, fieldOptions);
  }

  /**
   * Creates a calendar date picker with custom label.
   * Date input component with specified label and configurable date constraints.
   *
   * @param dataType Date data type (DateString, DateNumeric, DateTimeNumeric, or DateStringShortUS)
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param required Whether date selection is mandatory
   * @param fieldOptions Additional configuration options including calendar constraints
   * @returns FieldConfig for calendar component with default date range (2000-2099)
   */
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

  /**
   * Creates a multi-select dropdown with auto-generated label.
   * Multiple selection dropdown with automatic label generation and empty array default.
   *
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param required Whether at least one selection is mandatory
   * @param fieldOptions Additional configuration options including selection items
   * @returns FieldConfig for multi-select component with empty array default value
   */
  public static createFieldMultiSelectStringHeqF(fieldName: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig = DynamicFieldHelper.createFieldSelectNumberString(InputType.MultiSelect, DataType.String, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), required, fieldOptions);
    fieldConfig.defaultValue = fieldConfig.defaultValue || [];
    return fieldConfig;
  }

  /**
   * Creates a multi-select dropdown with custom label.
   * Multiple selection dropdown with specified label and empty array default.
   *
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param required Whether at least one selection is mandatory
   * @param fieldOptions Additional configuration options including selection items
   * @returns FieldConfig for multi-select component with empty array default value
   */
  public static createFieldMultiSelectString(fieldName: string, labelKey: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig = DynamicFieldHelper.createFieldSelectNumberString(InputType.MultiSelect, DataType.String,
      fieldName, labelKey, required, fieldOptions);
    fieldConfig.defaultValue = fieldConfig.defaultValue || [];
    return fieldConfig;
  }

  /**
   * Creates a single-select dropdown with auto-generated label.
   * Single selection dropdown with automatic label generation and empty string default.
   *
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param required Whether selection is mandatory
   * @param fieldOptions Additional configuration options including selection items
   * @returns FieldConfig for select component with empty string default value
   */
  public static createFieldSelectStringHeqF(fieldName: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig = DynamicFieldHelper.createFieldSelectNumberString(InputType.Select, DataType.String, fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), required, fieldOptions);
    fieldConfig.defaultValue = fieldConfig.defaultValue || '';
    return fieldConfig;
  }

  /**
   * Creates a single-select dropdown with custom label.
   * Single selection dropdown with specified label and empty string default.
   *
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param required Whether selection is mandatory
   * @param fieldOptions Additional configuration options including selection items
   * @returns FieldConfig for select component with empty string default value
   */
  public static createFieldSelectString(fieldName: string, labelKey: string, required: boolean,
    fieldOptions?: FieldOptions): FieldConfig {
    const fieldConfig = DynamicFieldHelper.createFieldSelectNumberString(InputType.Select, DataType.String,
      fieldName, labelKey, required, fieldOptions);
    fieldConfig.defaultValue = fieldConfig.defaultValue || '';
    return fieldConfig;
  }

  /**
   * Creates a PrimeNG input number field with auto-generated label.
   * Note: PrimeNG p-inputNumber component may have compatibility issues.
   *
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param required Whether input is mandatory
   * @param integerLimit Maximum number of integer digits
   * @param maxFractionDigits Maximum number of decimal places
   * @param allowNegative Whether negative values are permitted
   * @param fieldOptions Additional configuration options
   * @param excludeZero When true, zero is not allowed as input (allows both positive and negative but not zero)
   * @returns FieldConfig for PrimeNG number input with calculated min/max values
   */
  public static createFieldInputNumberHeqF(fieldName: string, required: boolean, integerLimit: number,
    maxFractionDigits: number, allowNegative: boolean, fieldOptions?: FieldOptions,
    excludeZero: boolean = false): FieldConfig {
    return this.createFieldInputNumber(fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), required,
      integerLimit, maxFractionDigits, allowNegative, fieldOptions, excludeZero);
  }

  /**
   * Creates a PrimeNG input number field with custom label.
   * Note: PrimeNG p-inputNumber component may have compatibility issues.
   *
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param required Whether input is mandatory
   * @param integerLimit Maximum number of integer digits
   * @param maxFractionDigits Maximum number of decimal places
   * @param allowNegative Whether negative values are permitted
   * @param fieldOptions Additional configuration options
   * @param excludeZero When true, zero is not allowed as input (allows both positive and negative but not zero)
   * @returns FieldConfig for PrimeNG number input with calculated min/max values
   */
  public static createFieldInputNumber(fieldName: string, labelKey: string, required: boolean, integerLimit: number,
    maxFractionDigits: number, allowNegative: boolean, fieldOptions?: FieldOptions,
    excludeZero: boolean = false): FieldConfig {
    const validations: ValidatorFn[] = required ? [Validators.required] : [];
    const errorMessageRules: ErrorMessageRules[] = required ? [this.RULE_REQUIRED_TOUCHED] : [];
    if (excludeZero) {
      validations.push(notZero);
      errorMessageRules.push(this.validationsErrorMap[VALIDATION_SPECIAL.NOT_ZERO].msgR);
    }
    const fieldConfig: FieldConfig = this.setFieldBaseAndOptions({
        dataType: DataType.Numeric,
        inputType: InputType.InputNumber
      },
      fieldName, labelKey, validations.length > 0 ? validations : null,
      errorMessageRules.length > 0 ? errorMessageRules : null, fieldOptions);
    fieldConfig.inputNumberSettings = {
      maxFractionDigits: maxFractionDigits,
      allowNegative: allowNegative,
      allowEmpty: true,
      treatZeroAsNull: !required,
      excludeZero: excludeZero
    };
    fieldConfig.max = Number('9'.repeat(integerLimit) + '.' + '9'.repeat(maxFractionDigits));
    fieldConfig.min = allowNegative ? fieldConfig.max * -1 : required ? 1 / Math.pow(10, maxFractionDigits) : null;
    return fieldConfig;
  }


  /**
   * Sets currency prefix for number input fields.
   * Updates currency display based on field type (currency mask vs input number).
   *
   * @param fieldConfig Field configuration to update
   * @param currency Currency code to display as prefix (gets space appended)
   */
  public static setCurrency(fieldConfig: FieldConfig, currency: string): void {
    fieldConfig.inputNumberSettings.currency = currency;
  }

  /**
   * Adjusts number precision and recalculates min/max values.
   * Updates decimal places and value constraints for number fields.
   *
   * @param fieldConfig Field configuration to update
   * @param integerDigits Maximum number of integer digits
   * @param precision Number of decimal places
   */
  public static adjustNumberFraction(fieldConfig: FieldConfig, integerDigits: number, precision: number): void {
    this.setMinMaxValues(fieldConfig, integerDigits, precision, fieldConfig.inputNumberSettings.allowNegative);
    fieldConfig.inputNumberSettings.maxFractionDigits = precision;
  }


  /**
   * Checks if a field configuration has required validation.
   * Utility method to determine if field is marked as mandatory.
   *
   * @param fieldConfig Field configuration to check
   * @returns True if field has required validation
   */
  public static isRequired(fieldConfig: FieldConfig): boolean {
    return fieldConfig.validation && fieldConfig.validation.includes(Validators.required);
  }

  /**
   * Creates a checkbox field with auto-generated label.
   * Boolean input with automatic label key generation from field name.
   *
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for checkbox component
   */
  public static createFieldCheckboxHeqF(fieldName: string, fieldOptions?: FieldOptions): FieldConfig {
    return this.setFieldBaseAndOptions({dataType: DataType.Boolean, inputType: InputType.Checkbox},
      fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), null, null, fieldOptions);
  }

  /**
   * Creates a checkbox field with custom label.
   * Boolean input with specified label.
   *
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for checkbox component
   */
  public static createFieldCheckbox(fieldName: string, labelKey: string, fieldOptions?: FieldOptions): FieldConfig {
    return this.setFieldBaseAndOptions({dataType: DataType.Boolean, inputType: InputType.Checkbox},
      fieldName, labelKey, null, null, fieldOptions);
  }

  /**
   * Creates a tri-state checkbox with auto-generated label.
   * Three-state boolean input (true/false/null) with automatic labeling.
   *
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for tri-state checkbox component
   */
  public static createFieldTriStateCheckboxHeqF(fieldName: string, fieldOptions?: FieldOptions): FieldConfig {
    return this.setFieldBaseAndOptions({dataType: DataType.Boolean, inputType: InputType.TriStateCheckbox},
      fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName), null, null, fieldOptions);
  }

  /**
   * Creates a tri-state checkbox with custom label.
   * Three-state boolean input (true/false/null) with specified label.
   *
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for tri-state checkbox component
   */
  public static createFieldTriStateCheckbox(fieldName: string, labelKey: string, fieldOptions?: FieldOptions): FieldConfig {
    return this.setFieldBaseAndOptions({dataType: DataType.Boolean, inputType: InputType.TriStateCheckbox},
      fieldName, labelKey, null, null, fieldOptions);
  }

  /**
   * Creates a numeric input with min/max validation and auto-generated label.
   * Number input with range constraints and automatic label generation.
   *
   * @param dataType Numeric data type (Numeric or NumericInteger)
   * @param fieldName Unique field identifier (also used for label key generation)
   * @param required Whether input is mandatory
   * @param min Minimum allowed value
   * @param max Maximum allowed value
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for number input with range validation and auto-sized width
   */
  public static createFieldMinMaxNumberHeqF(dataType: DataType.Numeric | DataType.NumericInteger, fieldName: string,
    required: boolean, min: number, max: number, fieldOptions?: FieldOptions): FieldConfig {
    return this.createFieldMinMaxNumber(dataType, fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName),
      required, min, max, fieldOptions);
  }

  /**
   * Creates a numeric input with min/max validation and custom label.
   * Number input with range constraints, validation error messages, and auto-sized width.
   *
   * @param dataType Numeric data type (Numeric or NumericInteger)
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param required Whether input is mandatory
   * @param min Minimum allowed value (null for no minimum)
   * @param max Maximum allowed value
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for number input with range validation and calculated width
   */
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

  /**
   * Creates select or dropdown field configuration.
   * Internal helper for creating various select input types.
   *
   * @param intputType Input type for select component (Select, MultiSelect, etc.)
   * @param dataType Data type for selected values
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param required Whether selection is mandatory
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for select component
   */
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

  /**
   * Creates input or textarea field configuration.
   * Internal helper for creating text-based input fields with optional range length validation.
   *
   * @param dataType Data type for the input
   * @param inputType Specific input type (Input, Textarea, etc.)
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param maxLength Maximum allowed character length
   * @param required Whether input is mandatory
   * @param fieldOptions Additional configuration options
   * @returns FieldConfig for text-based input with optional range validation
   */
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

  /**
   * Calculates min/max values for numeric fields based on digit constraints.
   * Internal helper that sets appropriate value ranges for number inputs.
   *
   * @param fieldConfig Field configuration to update
   * @param integerDigits Maximum number of integer digits
   * @param maxFractionDigits Maximum number of decimal places
   * @param allowNegative Whether negative values are permitted
   */
  private static setMinMaxValues(fieldConfig: FieldConfig, integerDigits: number, maxFractionDigits: number,
    allowNegative: boolean): void {
    fieldConfig.max = Number('9'.repeat(integerDigits) + '.' + '9'.repeat(maxFractionDigits));
    fieldConfig.min = allowNegative ?
      fieldConfig.max * -1 : DynamicFieldHelper.isRequired(fieldConfig) ? 1 / Math.pow(10, maxFractionDigits) : 0;
  }

  /**
   * Sets base field properties and merges with additional options.
   * Internal helper that configures core field properties and applies custom options.
   *
   * @param fieldConfig Base field configuration object
   * @param fieldName Unique field identifier
   * @param labelKey Translation key for field label
   * @param validations Array of validator functions
   * @param errorMessageRules Array of error message configurations
   * @param fieldOptions Additional configuration options to merge
   * @returns Complete FieldConfig with all properties set
   */
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

  /**
   * Resets field validation configuration and updates form control.
   * Used to dynamically change validation rules on existing fields.
   *
   * @param fieldConfig Field configuration to update
   * @param validation New array of validator functions (null to clear)
   * @param errors New array of error message configurations (optional)
   */
  public static resetValidator(fieldConfig: FieldConfig, validation: ValidatorFn[], errors?: ErrorMessageRules[]): void {
    fieldConfig.validation = validation;
    fieldConfig.formControl.setValidators(fieldConfig.validation);
    fieldConfig.errors = errors;
    fieldConfig.formControl.updateValueAndValidity();
    fieldConfig.baseInputComponent?.reEvaluateRequired();
  }

  /**
   * Applies multiple special validations to a field configuration.
   * Internal helper that adds multiple validation types from the validationsErrorMap.
   *
   * @param fieldConfig Field configuration to enhance with validations
   * @param validationSpecials Array of special validation types to apply
   * @returns Enhanced FieldConfig with applied validations
   */
  private static addValidations(fieldConfig: FieldConfig, validationSpecials: (VALIDATION_SPECIAL | string)[]): FieldConfig {
    for (const validationSpecial of validationSpecials) {
      const key = validationSpecial.toString();
      if (this.validationsErrorMap[key]) {
        this.addValidationParam(fieldConfig, key, null);
      } else {
        console.warn(`Validation type '${key}' is not registered. Use DynamicFieldHelper.registerCustomValidation() to add it.`);
      }
    }
    return fieldConfig;
  }

  /**
   * Adds parameterized validation to a field configuration.
   * Applies special validation with custom parameters and updates error messages.
   *
   * @param fieldConfig Field configuration to enhance
   * @param validationSpecial Type of special validation to apply
   * @param param1 Primary parameter for the validation function
   * @param param2 Optional secondary parameter
   * @returns Enhanced FieldConfig with parameterized validation
   */
  public static addValidationParam(fieldConfig: FieldConfig, validationSpecial: VALIDATION_SPECIAL | string, param1: any,
    param2?: any): FieldConfig {
    const key = validationSpecial.toString();
    const validError: ValidError = this.validationsErrorMap[key];
    if (!validError) {
      console.warn(`Validation type '${key}' is not registered. Use DynamicFieldHelper.registerCustomValidation() to add it.`);
      return fieldConfig;
    }
    (fieldConfig.errors = fieldConfig.errors || []).push({...validError.msgR});
    let validatorFn: ValidatorFn;
    if (validError.factory) {
      validatorFn = validError.factory(param1, param2);
      // Update error message with parameters
      const lastError = fieldConfig.errors[fieldConfig.errors.length - 1];
      lastError.param1 = param1;
      lastError.param2 = param2;
    } else {
      validatorFn = validError.vFN;
    }
    (fieldConfig.validation = fieldConfig.validation || []).push(validatorFn);
    return fieldConfig;
  }

}

/**
 * Configuration options interface for field creation methods.
 * Provides extensive customization options for form field appearance and behavior.
 */
export interface FieldOptions {
  /** Default value for the field */
  defaultValue?: number | string | boolean | Date;
  /** Text to display after the input field */
  fieldSuffix?: string;
  /** Name of the fieldset group for visual organization */
  fieldsetName?: string;
  /** Number of rows for textarea fields */
  textareaRows?: number;
  /** Help text to display (usually as tooltip or expandable section) */
  labelHelpText?: string;
  /** Text to display after the field label */
  labelSuffix?: string;
  /** Icon to display with the field */
  icon?: string;
  /** Number of columns this field should occupy in the layout */
  usedLayoutColumns?: number;
  /** Property path for data binding (alternative to field name) */
  dataproperty?: string;
  /** Whether the field should be disabled */
  disabled?: boolean;
  /** Whether the field should be read-only */
  readonly?: boolean;
  /** Whether to show loading state or use group items */
  groupItemUseOrLoading?: boolean;
  /** Specific width for the input element */
  inputWidth?: number;
  /** Whether to convert input to uppercase */
  upperCase?: boolean;
  /** User-defined value associated with the field */
  userDefinedValue?: number | string;
  /** Minimum length for string inputs (used with rangeLength validation) */
  minLength?: number;
  /** Function to provide autocomplete suggestions */
  suggestionsFN?: (any) => void;
  /** Whether the field should be hidden */
  invisible?: boolean;
  /** Options for select/dropdown fields */
  valueKeyHtmlOptions?: ValueKeyHtmlSelectOptions[];
  /** Whether button should be displayed within the form layout */
  buttonInForm?: boolean;
  /** Calendar-specific configuration (date ranges, disabled dates) */
  calendarConfig?: CalendarConfig;
  /** Function to handle file input changes immediately */
  handleChangeFileInputFN?: (fileList: FileList) => void;
  /** Context menu items for right-click menu on input fields */
  contextMenuItems?: MenuItem[];
}

/**
 * Extended field options interface with target field mapping support.
 * Allows field name aliasing for scenarios where display name differs from data property.
 */
export interface FieldOptionsCc extends FieldOptions {
  /** Alternative target field name for data binding */
  targetField?: string;
}

