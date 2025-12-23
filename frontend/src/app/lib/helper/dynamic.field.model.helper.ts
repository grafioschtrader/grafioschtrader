import {
  ClassDescriptorInputAndShow,
  ConstraintValidatorType,
  DynamicFormPropertyHelps,
  FieldDescriptorInputAndShow,
  FieldDescriptorInputAndShowExtended,
  ReplaceFieldWithGroup
} from '../dynamicfield/field.descriptor.input.and.show';
import {FieldConfig} from '../dynamic-form/models/field.config';
import {AppHelper} from './app.helper';
import {DataType} from '../dynamic-form/models/data.type';
import {BaseParam} from '../entities/base.param';
import {DynamicFieldHelper, FieldOptions, FieldOptionsCc, VALIDATION_SPECIAL} from './dynamic.field.helper';
import {dateRange, gteDate} from '../validator/validator';
import {ErrorMessageRules} from '../dynamic-form/error/error.message.rules';
import {FieldFormGroup} from '../dynamic-form/models/form.group.definition';
import {ValueKeyHtmlSelectOptions} from '../dynamic-form/models/value.key.html.select.options';

/**
 * Utility class for automatically generating dynamic form fields from class descriptors.
 * Enables creation of input/output forms based on server-side field definitions,
 * avoiding duplication of model programming between client and server. The field
 * definitions are typically received directly from the server as JSON descriptors
 * and converted into Angular reactive form configurations.
 *
 * Key features:
 * - Automatic form generation from server descriptors with full validation
 * - Advanced validation support (date ranges, cross-field validation, ISIN, email)
 * - Data type conversion and mapping between server models and form controls
 * - Internationalization support with translation skipping via asterisk prefixes
 * - Business object to form model conversion utilities with type safety
 */
export class DynamicFieldModelHelper {

  /**
   * Creates form field configurations from a class descriptor with constraint validation support.
   * Handles special validators like date ranges and generates appropriate form groups. If constraint
   * validators are present, creates specialized form groups; otherwise generates standard field configs.
   *
   * @param cdias Class descriptor containing field definitions and optional constraint validators map
   * @param labelPrefix Prefix added to field labels for translation key generation (e.g., 'USER_FORM_')
   * @param addSubmitButton Whether to automatically add a submit button to the form
   * @param submitText Custom text for the submit button (defaults to 'SAVE' if not provided)
   * @returns Array of FieldFormGroup objects representing the complete form configuration, or empty array if cdias is null
   */
  public static createFieldsFromClassDescriptorInputAndShow(cdias: ClassDescriptorInputAndShow, labelPrefix: string,
    addSubmitButton = false, submitText?: string): FieldFormGroup[] {
    let config: FieldFormGroup[];
    if (cdias?.constraintValidatorMap && cdias.constraintValidatorMap.size > 0) {
      let validatorCounter = 0;
      for (const [key, value] of Object.entries(cdias.constraintValidatorMap)) {
        validatorCounter++;
        switch (ConstraintValidatorType[key]) {
          case ConstraintValidatorType.DateRange:
            config = this.createDateRangeFields(cdias, labelPrefix, validatorCounter,
              value, addSubmitButton, submitText);
            break;
        }
      }
      return config;
    } else {
      return cdias ? this.ccFieldsFromDescriptorWithGroup(cdias.fieldDescriptorInputAndShows, labelPrefix, addSubmitButton,
        null, submitText) : [];
    }
  }

  /**
   * Creates a form group for date range validation with cross-field validation.
   * Generates two date input fields where the end date must be later than the start date.
   * Creates a FormGroup with dateRange validator and replaces individual date fields.
   *
   * @param cdias Class descriptor containing field definitions including start and end date fields
   * @param labelPrefix Prefix for field label translation keys
   * @param validatorCounter Counter to ensure unique form group names (e.g., 'dateRange1', 'dateRange2')
   * @param fields Configuration object containing startField and endField property names for validation
   * @param addSubmitButton Whether to add a submit button to the final form
   * @param submitText Custom submit button text
   * @returns Array of FieldFormGroup with dateRange validator applied and original fields replaced
   */
  private static createDateRangeFields(cdias: ClassDescriptorInputAndShow, labelPrefix: string, validatorCounter: number,
    fields: any, addSubmitButton = false, submitText?: string): FieldFormGroup[] {
    const fdDate1 = cdias.fieldDescriptorInputAndShows.find(f => f.fieldName === fields.startField);
    const fdDate2 = cdias.fieldDescriptorInputAndShows.find(f => f.fieldName === fields.endField);
    const fieldConfigs = this.createConfigFieldsFromDescriptor([fdDate1, fdDate2], labelPrefix, addSubmitButton, submitText);
    const fieldFormGroup: FieldFormGroup = {formGroupName: 'dateRange' + validatorCounter, fieldConfig: fieldConfigs};
    fieldFormGroup.validation = [dateRange(fdDate1.fieldName, fdDate2.fieldName, fdDate2.fieldName)];
    fieldFormGroup.errors = [{name: 'dateRange', keyi18n: 'dateRange', rules: ['dirty']}];
    const rfwg = new ReplaceFieldWithGroup(fdDate1.fieldName, fieldFormGroup, fdDate2.fieldName);
    return this.ccFieldsFromDescriptorWithGroup(cdias.fieldDescriptorInputAndShows, labelPrefix, addSubmitButton,
      rfwg, submitText);

  }

  /**
   * Creates a single input field configuration using field name as the label key.
   * Convenience method that auto-generates label key from field name using naming conventions
   * (converts camelCase to UPPER_CASE_WITH_UNDERSCORES and removes common prefixes).
   *
   * @param fieldName Name of the field to create configuration for
   * @param fieldDescriptorInputAndShows Array of field descriptors to search through for matching field
   * @param fieldOptionsCc Additional field options and configuration overrides (target field, styling, etc.)
   * @returns FieldConfig object for the specified field, or null if field not found in descriptors
   */
  public static ccWithFieldsFromDescriptorHeqF(fieldName: string, fieldDescriptorInputAndShows:
  FieldDescriptorInputAndShow[], fieldOptionsCc?: FieldOptionsCc): FieldConfig {
    return this.ccWithFieldsFromDescriptor(fieldName, AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName),
      fieldDescriptorInputAndShows, fieldOptionsCc);
  }

  /**
   * Creates a single input field configuration with custom label key.
   * Searches through field descriptors to find matching field and generates appropriate input element
   * based on data type, constraints, and field properties (email, password, select options, etc.).
   *
   * @param fieldName Name of the field to create configuration for
   * @param labelKey Custom translation key for the field label (bypasses auto-generation)
   * @param fieldDescriptorInputAndShows Array of field descriptors to search through
   * @param fieldOptionsCc Additional field options and configuration overrides (width, validation, etc.)
   * @returns FieldConfig object for the specified field with appropriate input type and validation
   */
  public static ccWithFieldsFromDescriptor(fieldName: string, labelKey: string, fieldDescriptorInputAndShows:
  FieldDescriptorInputAndShow[], fieldOptionsCc?: FieldOptionsCc): FieldConfig {
    const fd = fieldDescriptorInputAndShows.filter(fdias => fdias.fieldName === fieldName)[0];
    return this.createConfigFieldFromDescriptor(fd, null, labelKey, fieldOptionsCc);
  }

  /**
   * Creates field configurations from extended descriptors with translation support.
   * Handles asterisk prefixes for labels and help text to skip translation. When text doesn't
   * match translation key pattern (^[A-Z_]+$), it gets asterisk-prefixed to display as literal text.
   *
   * @param fdExtendedList Array of extended field descriptors with description and descriptionHelp properties
   * @param labelPrefix Prefix for field label translation keys
   * @param addSubmitButton Whether to add a submit button to the form
   * @param submitText Custom submit button text
   * @returns Array of FieldConfig objects with asterisk-prefixed labels for literal text (non-translation keys)
   */
  public static createConfigFieldsFromExtendedDescriptor(fdExtendedList: FieldDescriptorInputAndShowExtended[],
    labelPrefix: string, addSubmitButton = false, submitText?: string): FieldConfig[] {
    const fieldConfigs: FieldConfig[] = <FieldConfig[]>this.ccFieldsFromDescriptorWithGroup(fdExtendedList, labelPrefix, addSubmitButton,
      null, submitText);
    DynamicFieldModelHelper.addAsterisksToLabelAndHelpText(fdExtendedList, fieldConfigs);
    return fieldConfigs;
  }

  /**
   * Adds asterisk prefixes to labels and help text to skip translation.
   * Labels and help text that don't match translation key pattern (^[A-Z_]+$) get asterisk prefix
   * to indicate they should be displayed as literal text without translation lookup.
   *
   * @param fdExtendedList Array of extended field descriptors containing description text
   * @param fieldConfigs Array of field configurations to modify with asterisk prefixes for literal text
   */
  private static addAsterisksToLabelAndHelpText(fdExtendedList: FieldDescriptorInputAndShowExtended[],
    fieldConfigs: FieldConfig[]): void {
    const regex = /^[A-Z_]+$/;
    for (let i: number = 0; i < fdExtendedList.length; i++) {
      if (!regex.test(fdExtendedList[i].description)) {
        fieldConfigs[i].labelKey = '*' + fdExtendedList[i].description;
      }
      if (fdExtendedList[i].descriptionHelp) {
        if (!regex.test(fdExtendedList[i].descriptionHelp)) {
          fieldConfigs[i].labelHelpText = '*' + fdExtendedList[i].descriptionHelp;
        }
      }
    }
  }

  /**
   * Creates field configurations from standard field descriptors.
   * Main method for converting server field definitions into form field configurations.
   * Delegates to ccFieldsFromDescriptorWithGroup with no field replacement.
   *
   * @param fieldDescriptorInputAndShows Array of field descriptors from server
   * @param labelPrefix Prefix for field label translation keys
   * @param addSubmitButton Whether to add a submit button at the end
   * @param submitText Custom submit button text
   * @returns Array of FieldConfig objects cast as FieldFormGroup for compatibility
   */
  public static createConfigFieldsFromDescriptor(fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
    labelPrefix: string, addSubmitButton = false, submitText?: string): FieldConfig[] {
    return <FieldConfig[]>this.ccFieldsFromDescriptorWithGroup(fieldDescriptorInputAndShows, labelPrefix, addSubmitButton,
      null, submitText);
  }

  /**
   * Creates field configurations with optional field replacement and grouping.
   * Processes field descriptors and handles special field replacement scenarios for
   * grouped validation (e.g., replacing two date fields with a date range group).
   *
   * @param fieldDescriptorInputAndShows Array of field descriptors to process
   * @param labelPrefix Prefix for field label translation keys
   * @param addSubmitButton Whether to add a submit button at the end
   * @param rpg Optional field replacement configuration for grouping fields into form groups
   * @param submitText Custom submit button text
   * @returns Array of FieldFormGroup objects (mix of individual fields and form groups)
   */
  private static ccFieldsFromDescriptorWithGroup(fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
    labelPrefix: string, addSubmitButton = false,
    rpg: ReplaceFieldWithGroup, submitText?: string): FieldFormGroup[] {
    const fieldConfigs: FieldFormGroup[] = [];
    fieldDescriptorInputAndShows.forEach(fd => {
      if (rpg && (fd.fieldName === rpg.replaceFieldName || fd.fieldName === rpg.removeFieldName)) {
        if (fd.fieldName === rpg.replaceFieldName) {
          fieldConfigs.push(rpg.fieldFormGroup);
        }
      } else {
        const fieldConfig: FieldConfig = this.createConfigFieldFromDescriptor(fd, labelPrefix, null);
        if (fieldConfig) {
          fieldConfigs.push(fieldConfig);
        }
      }
    });
    if (addSubmitButton) {
      fieldConfigs.push(DynamicFieldHelper.createSubmitButton(submitText ? submitText : undefined));
    }
    return fieldConfigs;
  }

  /**
   * Creates a single field configuration from a field descriptor.
   * Determines appropriate input type and validation based on data type (Boolean, String, Numeric,
   * URL, Date variants) and special properties (email, password, percentage, future dates).
   *
   * @param fd Field descriptor containing field metadata (dataType, required, min/max, dynamicFormPropertyHelps)
   * @param labelPrefix Prefix for label translation key generation
   * @param labelKey Custom label key (overrides auto-generated prefix + field name)
   * @param fieldOptionsCc Additional field options and overrides (target field, styling, etc.)
   * @returns FieldConfig object with appropriate input type, validation, and calendar config, or null if unsupported type
   */
  private static createConfigFieldFromDescriptor(fd: FieldDescriptorInputAndShow, labelPrefix: string,
    labelKey: string, fieldOptionsCc?: FieldOptionsCc): FieldConfig {
    let fieldConfig: FieldConfig;
    const targetField = fieldOptionsCc && fieldOptionsCc.targetField ? fieldOptionsCc.targetField : fd.fieldName;
    labelKey = labelKey ? labelKey : labelPrefix + AppHelper.toUpperCaseWithUnderscore(fd.fieldName);

    switch (DataType[fd.dataType]) {
      case DataType.Boolean:
        fieldConfig = DynamicFieldHelper.createFieldCheckbox(targetField, labelKey);
        break;
      case DataType.String:
        fieldConfig = this.createStringInputFromDescriptor(fd, labelKey, targetField, fieldOptionsCc);
        break;
      case DataType.Numeric:
      case DataType.NumericInteger:
        fieldConfig = DynamicFieldHelper.createFieldMinMaxNumber(DataType[fd.dataType], targetField,
          labelKey, fd.required, fd.min, fd.max,
          {fieldSuffix: DynamicFieldModelHelper.getFieldPercentageSuffix(fd)});
        break;
      case DataType.URLString:
        fieldConfig = DynamicFieldHelper.createFieldInputWebUrl(targetField, labelKey, fd.required,
          fieldOptionsCc);
        break;
      case DataType.EnumSet:
        fieldConfig = this.createEnumSetMultiSelect(fd, labelKey, targetField, fieldOptionsCc);
        break;
      case DataType.DateString:
      case DataType.DateNumeric:
      case DataType.DateTimeNumeric:
      case DataType.DateTimeString:
      case DataType.DateStringShortUS:
        fieldConfig = DynamicFieldHelper.createFieldPcalendar(DataType[fd.dataType], targetField, labelKey, fd.required);
        fieldConfig.defaultValue = new Date();
        if (fd.dynamicFormPropertyHelps
          && DynamicFormPropertyHelps[fd.dynamicFormPropertyHelps[0]] === DynamicFormPropertyHelps.DATE_FUTURE) {
          fieldConfig.calendarConfig = {minDate: fieldConfig.defaultValue};
          fieldConfig.validation = fd.required ? [...fieldConfig.validation, gteDate(fieldConfig.defaultValue)]
            : [gteDate(fieldConfig.defaultValue)];
          const emr: ErrorMessageRules = {
            name: 'gteDate',
            keyi18n: 'gteDate',
            param1: fieldConfig.defaultValue,
            rules: ['dirty']
          };
          fieldConfig.errors = fd.required ? [...fieldConfig.errors, emr] : [emr];
        }
        break;
    }
    return fieldConfig;
  }

  /**
   * Creates string input field configuration from descriptor properties.
   * Handles various string input types based on dynamicFormPropertyHelps: EMAIL (with email validation),
   * PASSWORD (masked input), SELECT_OPTIONS (dropdown), or standard input/textarea based on max length.
   *
   * @param fd Field descriptor containing string field metadata (max length, min length, required status)
   * @param labelKey Translation key for field label
   * @param targetField Target field name (may differ from descriptor field name for aliasing)
   * @param fieldOptionsCc Additional field options and configuration (merged with min length from descriptor)
   * @returns FieldConfig object configured for appropriate string input type with validation
   */
  private static createStringInputFromDescriptor(fd: FieldDescriptorInputAndShow, labelKey: string, targetField: string,
    fieldOptionsCc?: FieldOptionsCc): FieldConfig {
    let fieldConfig: FieldConfig;
    const fieldOptions: FieldOptions = Object.assign({}, fieldOptionsCc, {minLength: fd.min});
    if (fd.dynamicFormPropertyHelps) {
      switch (DynamicFormPropertyHelps[fd.dynamicFormPropertyHelps[0]]) {
        case DynamicFormPropertyHelps.EMAIL:
          fieldConfig = DynamicFieldHelper.createFieldDAInputStringVSHeqF(DataType.Email, targetField, fd.max, fd.required,
            [VALIDATION_SPECIAL.EMail], fieldOptions);
          break;
        case DynamicFormPropertyHelps.PASSWORD:
          fieldConfig = DynamicFieldHelper.createFieldDAInputStringHeqF(DataType.Password, targetField, fd.max, fd.required,
            fieldOptions);
          break;
        case DynamicFormPropertyHelps.SELECT_OPTIONS:
          fieldOptions.inputWidth = fd.max;
          fieldConfig = DynamicFieldHelper.createFieldSelectStringHeqF(targetField, fd.required,
            fieldOptions);
          break;

        default:
      }
    } else {
      if (fd.max && fd.max > 80) {
        fieldOptions.textareaRows = fieldOptions.textareaRows ? fieldOptions.textareaRows : Math.ceil(fd.max / 80);
        fieldConfig = DynamicFieldHelper.createFieldTextareaInputString(targetField, labelKey, fd.max, fd.required,
          fieldOptions);
      } else {
        fieldConfig = DynamicFieldHelper.createFieldInputString(targetField, labelKey, fd.max, fd.required,
          fieldOptions);
      }
    }
    return fieldConfig;
  }

  /**
   * Creates a multi-select input for Set&lt;Enum&gt; fields.
   * Converts enum values from descriptor to ValueKeyHtmlSelectOptions for the dropdown.
   * Translation keys follow the pattern: ENUM_TYPE.ENUM_VALUE (e.g., GTNetExchangeKindType.LAST_PRICE).
   *
   * @param fd Field descriptor containing enumType and enumValues from backend
   * @param labelKey Translation key for field label
   * @param targetField Target field name for data binding
   * @param fieldOptionsCc Additional field options (merged with generated options)
   * @returns FieldConfig for multi-select component with enum options and empty array default
   */
  private static createEnumSetMultiSelect(fd: FieldDescriptorInputAndShow, labelKey: string,
    targetField: string, fieldOptionsCc?: FieldOptionsCc): FieldConfig {
    // Convert enum values to select options
    // key = enum value name (submitted value), value = translation key for display
    // Translation key pattern: ENUM_TYPE.VALUE (e.g., GTNetExchangeKindType.LAST_PRICE)
    const valueKeyHtmlOptions: ValueKeyHtmlSelectOptions[] = fd.enumValues?.map(enumValue =>
      new ValueKeyHtmlSelectOptions(enumValue, `${fd.enumType}.${enumValue}`)
    ) || [];

    const fieldOptions: FieldOptions = Object.assign({}, fieldOptionsCc, {
      valueKeyHtmlOptions
    });

    return DynamicFieldHelper.createFieldMultiSelectString(targetField, labelKey, fd.required, fieldOptions);
  }

  /**
   * Creates and populates a dynamic model object from form selection and parameter map.
   * Converts parameter values to appropriate data types and builds model object.
   * Useful for creating request objects from form data and selections.
   *
   * @param e Selected value to add to model (if addStrategyImplField is true)
   * @param targetSelectionField Field name for the selected value in the resulting model
   * @param paramMap Map or object containing parameter values keyed by field name
   * @param fieldDescriptorInputAndShows Array of field descriptors for type conversion rules
   * @param addStrategyImplField Whether to add the selection field to the model (for strategy pattern implementations)
   * @returns Dynamic model object with converted values ready for server submission
   */
  public static createAndSetValuesInDynamicModel(e: any,
    targetSelectionField: string,
    paramMap: Map<string, BaseParam> | { [key: string]: BaseParam },
    fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
    addStrategyImplField = false): any {
    const dynamicModel: any = {};
    if (addStrategyImplField) {
      dynamicModel[targetSelectionField] = e;
    }
    return DynamicFieldModelHelper.setValuesOfMapModelToDynamicModel(fieldDescriptorInputAndShows,
      paramMap, dynamicModel);
  }

  /**
   * Converts parameter map values to dynamic model with appropriate data type conversion.
   * Handles numeric conversion for Numeric and NumericInteger data types, array handling for EnumSet,
   * and leaves other types as strings.
   *
   * @param fieldDescriptorInputAndShows Array of field descriptors providing data type information
   * @param paramMap Map or object containing parameter values with paramValue property
   * @param dynamicModel Target model object to populate (created if not provided)
   * @returns Dynamic model object with type-converted values matching field descriptor data types
   */
  public static setValuesOfMapModelToDynamicModel(fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
    paramMap: Map<string, BaseParam> | { [key: string]: BaseParam },
    dynamicModel: any = {}): any {
    fieldDescriptorInputAndShows.forEach(fieldDescriptorInputAndShow => {
      let value = paramMap[fieldDescriptorInputAndShow.fieldName].paramValue;
      switch (DataType[fieldDescriptorInputAndShow.dataType]) {
        case DataType.Numeric:
        case DataType.NumericInteger:
          value = Number(value);
          break;
        case DataType.EnumSet:
          // Backend stores comma-separated string - convert to array for MultiSelect display
          value = typeof value === 'string' && value ? value.split(',') : [];
          break;
        default:
        // Nothing
      }
      dynamicModel[fieldDescriptorInputAndShow.fieldName] = value;
    });
    return dynamicModel;
  }

  /**
   * Determines if a field should display percentage suffix based on field properties.
   * Checks for PERCENTAGE property in dynamicFormPropertyHelps array and returns '%' symbol.
   * Used for fields representing percentage values that need visual indication.
   *
   * @param fDIAS Field descriptor to check for percentage property in dynamicFormPropertyHelps
   * @returns Percentage symbol (%) if field has PERCENTAGE property, null otherwise
   */
  public static getFieldPercentageSuffix(fDIAS: FieldDescriptorInputAndShow): string {
    return (fDIAS.dynamicFormPropertyHelps
      && (<string[]>fDIAS.dynamicFormPropertyHelps)
        .indexOf(DynamicFormPropertyHelps[DynamicFormPropertyHelps.PERCENTAGE]) >= 0) ? '%' : null;
  }

  /**
   * Compares two data models for equality based on specified field descriptors.
   * Only compares fields that are defined in the field descriptors array using strict equality.
   * Returns false if either model is null/undefined or if any field values differ.
   *
   * @param model1 First model object to compare
   * @param model2 Second model object to compare
   * @param fDIASs Array of field descriptors defining which fields to compare by fieldName
   * @returns True if both models exist and all specified fields are equal, false otherwise
   */
  public static isDataModelEqual(model1: any, model2: any, fDIASs: FieldDescriptorInputAndShow[]) {
    if (model1 && model2) {
      for (const fDIAS of fDIASs) {
        if (model1[fDIAS.fieldName] !== model2[fDIAS.fieldName]) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

}
