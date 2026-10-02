import {
  ClassDescriptorInputAndShow,
  ConstraintValidatorType,
  DynamicFormPropertyHelps,
  FieldDescriptorInputAndShow,
  FieldDescriptorInputAndShowExtended
} from '../dynamicfield/field.descriptor.input.and.show';
import { FieldConfig } from '../dynamic-form/models/field.config';
import { AppHelper } from './app.helper';
import { DataType } from '../dynamic-form/models/data.type';
import { BaseParam } from '../entities/base.param';
import { DynamicFieldHelper, FieldOptions, FieldOptionsCc, VALIDATION_SPECIAL } from './dynamic.field.helper';
import { ValidatorFn, Validators } from '@angular/forms';
import { RuleEvent } from '../dynamic-form/error/error.message.rules';
import { atLeastOneNotNull, dateRange, gteDate, numberRange } from '../validator/validator';
import { ErrorMessageRules } from '../dynamic-form/error/error.message.rules';
import { FieldFormGroup, FormGroupDefinition } from '../dynamic-form/models/form.group.definition';
import { ValueKeyHtmlSelectOptions } from '../dynamic-form/models/value.key.html.select.options';
import { TranslateService } from '@ngx-translate/core';
import { SelectOptionsHelper } from './select.options.helper';

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
   * The class-level constraints of the backend model (`@DateRange`, `@AtLeastOneNotNull`, `@NumberRange`) become
   * form groups carrying the same rules: the fields a constraint names are placed in one group, at the position of
   * the first of them, and constraints that share a field share the group. Without constraints the fields are
   * generated one by one.
   *
   * The values of grouped fields are nested under the group name in the form value; a component reading them by
   * field name flattens the value first (`Helper.flattenObject`), while `cleanMaskAndTransferValuesToBusinessObject`
   * and `transferBusinessObjectToForm` work on the flattened controls anyway.
   *
   * @param translateService Angular TranslateService for translating enum options
   * @param cdias Class descriptor containing field definitions and optional constraint validators map
   * @param labelPrefix Prefix added to field labels for translation key generation (e.g., 'USER_FORM_')
   * @param addSubmitButton Whether to automatically add a submit button to the form
   * @param submitText Custom text for the submit button (defaults to 'SAVE' if not provided)
   * @returns Array of FieldFormGroup objects representing the complete form configuration, or empty array if cdias is null
   */
  public static createFieldsFromClassDescriptorInputAndShow(
    translateService: TranslateService,
    cdias: ClassDescriptorInputAndShow,
    labelPrefix: string,
    addSubmitButton = false,
    submitText?: string
  ): FieldFormGroup[] {
    if (!cdias) {
      return [];
    }
    return this.ccFieldsFromDescriptorWithGroup(
      translateService,
      cdias.fieldDescriptorInputAndShows,
      labelPrefix,
      addSubmitButton,
      this.createConstraintGroups(translateService, cdias, labelPrefix),
      submitText
    );
  }

  /**
   * Turns the class-level constraints of a descriptor into form groups. Constraints whose fields overlap are merged
   * into one group, because a field can belong to only one form group.
   *
   * @param translateService Angular TranslateService for translating enum options
   * @param cdias Class descriptor whose constraintValidatorMap is read
   * @param labelPrefix Prefix for field label translation keys
   * @returns One entry per form group, with the names of the fields it replaces in descriptor order
   * @private
   */
  private static createConstraintGroups(
    translateService: TranslateService,
    cdias: ClassDescriptorInputAndShow,
    labelPrefix: string
  ): ConstraintFieldGroup[] {
    const merged: ConstraintDefinition[] = [];
    this.getConstraintEntries(cdias.constraintValidatorMap).forEach(([type, value]) => {
      const definition = this.createConstraintDefinition(type, value);
      if (definition) {
        merged
          .filter((m) => m.fields.some((f) => definition.fields.includes(f)))
          .forEach((m) => {
            definition.fields = [...definition.fields, ...m.fields.filter((f) => !definition.fields.includes(f))];
            definition.validation.push(...m.validation);
            definition.errors.push(...m.errors);
            merged.splice(merged.indexOf(m), 1);
          });
        merged.push(definition);
      }
    });
    return merged.map((definition, i) => {
      const fds = cdias.fieldDescriptorInputAndShows.filter((fd) => definition.fields.includes(fd.fieldName));
      const fieldFormGroup: FormGroupDefinition = {
        formGroupName: 'constraintGroup' + (i + 1),
        fieldConfig: this.createConfigFieldsFromDescriptor(translateService, fds, labelPrefix, false),
        validation: definition.validation,
        errors: definition.errors
      };
      return { fieldNames: fds.map((fd) => fd.fieldName), fieldFormGroup };
    });
  }

  /**
   * Reads the constraint map, which arrives from the server as a plain JSON object keyed by the constraint name.
   *
   * @param constraintValidatorMap the map of the descriptor, a plain object or a Map
   * @returns the constraints as type and configuration pairs
   * @private
   */
  private static getConstraintEntries(
    constraintValidatorMap: { [key: string]: any } | Map<ConstraintValidatorType, any>
  ): [ConstraintValidatorType, any][] {
    if (!constraintValidatorMap) {
      return [];
    }
    const entries: [string | ConstraintValidatorType, any][] =
      constraintValidatorMap instanceof Map
        ? [...constraintValidatorMap.entries()]
        : Object.entries(constraintValidatorMap);
    return entries.map(([key, value]) => [
      typeof key === 'number' ? key : ConstraintValidatorType[key as keyof typeof ConstraintValidatorType],
      value
    ]);
  }

  /**
   * Creates the group validator and its error rule for one backend class constraint.
   *
   * @param type the constraint type
   * @param value the constraint configuration delivered by the backend
   * @returns the definition, or null for a constraint type this client does not know
   * @private
   */
  private static createConstraintDefinition(type: ConstraintValidatorType, value: any): ConstraintDefinition {
    switch (type) {
      case ConstraintValidatorType.DateRange:
        return {
          fields: [value.startField, value.endField],
          validation: [dateRange(value.startField, value.endField, value.endField)],
          errors: [{ name: 'dateRange', keyi18n: 'dateRange', rules: [RuleEvent.DIRTY] }]
        };
      case ConstraintValidatorType.AtLeastOneNotNull:
        return {
          fields: [...value.fields],
          validation: [atLeastOneNotNull(value.fields)],
          errors: [{ name: 'atLeastOneNotNull', keyi18n: 'atLeastOneNotNull', rules: [RuleEvent.DIRTY] }]
        };
      case ConstraintValidatorType.NumberRange:
        return {
          fields: [value.lowerField, value.upperField],
          validation: [numberRange(value.lowerField, value.upperField)],
          errors: [{ name: 'numberRange', keyi18n: 'numberRange', rules: [RuleEvent.DIRTY] }]
        };
      default:
        return null;
    }
  }

  /**
   * Creates a single input field configuration using field name as the label key.
   * Convenience method that auto-generates label key from field name using naming conventions
   * (converts camelCase to UPPER_CASE_WITH_UNDERSCORES and removes common prefixes).
   *
   * @param translateService Angular TranslateService for translating enum options
   * @param fieldName Name of the field to create configuration for
   * @param fieldDescriptorInputAndShows Array of field descriptors to search through for matching field
   * @param fieldOptionsCc Additional field options and configuration overrides (target field, styling, etc.)
   * @returns FieldConfig object for the specified field, or null if field not found in descriptors
   */
  public static ccWithFieldsFromDescriptorHeqF(
    translateService: TranslateService,
    fieldName: string,
    fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
    fieldOptionsCc?: FieldOptionsCc
  ): FieldConfig {
    return this.ccWithFieldsFromDescriptor(
      translateService,
      fieldName,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(fieldName),
      fieldDescriptorInputAndShows,
      fieldOptionsCc
    );
  }

  /**
   * Creates a single input field configuration with custom label key.
   * Searches through field descriptors to find matching field and generates appropriate input element
   * based on data type, constraints, and field properties (email, password, select options, etc.).
   *
   * @param translateService Angular TranslateService for translating enum options
   * @param fieldName Name of the field to create configuration for
   * @param labelKey Custom translation key for the field label (bypasses auto-generation)
   * @param fieldDescriptorInputAndShows Array of field descriptors to search through
   * @param fieldOptionsCc Additional field options and configuration overrides (width, validation, etc.)
   * @returns FieldConfig object for the specified field with appropriate input type and validation
   */
  public static ccWithFieldsFromDescriptor(
    translateService: TranslateService,
    fieldName: string,
    labelKey: string,
    fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
    fieldOptionsCc?: FieldOptionsCc
  ): FieldConfig {
    const fd = fieldDescriptorInputAndShows.filter((fdias) => fdias.fieldName === fieldName)[0];
    return this.createConfigFieldFromDescriptor(translateService, fd, null, labelKey, fieldOptionsCc);
  }

  /**
   * Creates field configurations from extended descriptors with translation support.
   * Handles asterisk prefixes for labels and help text to skip translation. When text doesn't
   * match translation key pattern (^[A-Z_]+$), it gets asterisk-prefixed to display as literal text.
   *
   * @param translateService Angular TranslateService for translating enum options
   * @param fdExtendedList Array of extended field descriptors with description and descriptionHelp properties
   * @param labelPrefix Prefix for field label translation keys
   * @param addSubmitButton Whether to add a submit button to the form
   * @param submitText Custom submit button text
   * @returns Array of FieldConfig objects with asterisk-prefixed labels for literal text (non-translation keys)
   */
  public static createConfigFieldsFromExtendedDescriptor(
    translateService: TranslateService,
    fdExtendedList: FieldDescriptorInputAndShowExtended[],
    labelPrefix: string,
    addSubmitButton = false,
    submitText?: string
  ): FieldConfig[] {
    const fieldConfigs: FieldConfig[] = <FieldConfig[]>(
      this.ccFieldsFromDescriptorWithGroup(
        translateService,
        fdExtendedList,
        labelPrefix,
        addSubmitButton,
        [],
        submitText
      )
    );
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
  private static addAsterisksToLabelAndHelpText(
    fdExtendedList: FieldDescriptorInputAndShowExtended[],
    fieldConfigs: FieldConfig[]
  ): void {
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
   * @param translateService Angular TranslateService for translating enum options
   * @param fieldDescriptorInputAndShows Array of field descriptors from server
   * @param labelPrefix Prefix for field label translation keys
   * @param addSubmitButton Whether to add a submit button at the end
   * @param submitText Custom submit button text
   * @returns Array of FieldConfig objects cast as FieldFormGroup for compatibility
   */
  public static createConfigFieldsFromDescriptor(
    translateService: TranslateService,
    fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
    labelPrefix: string,
    addSubmitButton = false,
    submitText?: string
  ): FieldConfig[] {
    return <FieldConfig[]>(
      this.ccFieldsFromDescriptorWithGroup(
        translateService,
        fieldDescriptorInputAndShows,
        labelPrefix,
        addSubmitButton,
        [],
        submitText
      )
    );
  }

  /**
   * Creates field configurations, placing grouped fields into their form group.
   * A field named by a group is not generated on its own: the group takes the position of its first field and the
   * other fields of the group are left out at their own position.
   *
   * @param translateService Angular TranslateService for translating enum options
   * @param fieldDescriptorInputAndShows Array of field descriptors to process
   * @param labelPrefix Prefix for field label translation keys
   * @param addSubmitButton Whether to add a submit button at the end
   * @param groups Form groups of cross-field constraints, empty when there are none
   * @param submitText Custom submit button text
   * @returns Array of FieldFormGroup objects (mix of individual fields and form groups)
   */
  private static ccFieldsFromDescriptorWithGroup(
    translateService: TranslateService,
    fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
    labelPrefix: string,
    addSubmitButton = false,
    groups: ConstraintFieldGroup[],
    submitText?: string
  ): FieldFormGroup[] {
    const fieldConfigs: FieldFormGroup[] = [];
    fieldDescriptorInputAndShows.forEach((fd) => {
      const group = groups.find((g) => g.fieldNames.includes(fd.fieldName));
      if (group) {
        if (fd.fieldName === group.fieldNames[0]) {
          fieldConfigs.push(group.fieldFormGroup);
        }
      } else {
        const fieldConfig: FieldConfig = this.createConfigFieldFromDescriptor(translateService, fd, labelPrefix, null);
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
   * @param translateService Angular TranslateService for translating enum options
   * @param fd Field descriptor containing field metadata (dataType, required, min/max, dynamicFormPropertyHelps)
   * @param labelPrefix Prefix for label translation key generation
   * @param labelKey Custom label key (overrides auto-generated prefix + field name)
   * @param fieldOptionsCc Additional field options and overrides (target field, styling, etc.)
   * @returns FieldConfig object with appropriate input type, validation, and calendar config, or null if unsupported type
   */
  private static createConfigFieldFromDescriptor(
    translateService: TranslateService,
    fd: FieldDescriptorInputAndShow,
    labelPrefix: string,
    labelKey: string,
    fieldOptionsCc?: FieldOptionsCc
  ): FieldConfig {
    let fieldConfig: FieldConfig;
    const targetField = fieldOptionsCc && fieldOptionsCc.targetField ? fieldOptionsCc.targetField : fd.fieldName;
    labelKey = labelKey
      ? labelKey
      : fd.labelKey
        ? fd.labelKey
        : labelPrefix + AppHelper.toUpperCaseWithUnderscore(fd.fieldName);

    switch (DataType[fd.dataType]) {
      case DataType.Boolean:
        fieldConfig = DynamicFieldHelper.createFieldCheckbox(targetField, labelKey);
        break;
      case DataType.String:
        fieldConfig = this.createStringInputFromDescriptor(translateService, fd, labelKey, targetField, fieldOptionsCc);
        break;
      case DataType.Numeric:
      case DataType.NumericInteger:
        fieldConfig = this.createNumericInputFromDescriptor(fd, labelKey, targetField, fieldOptionsCc);
        break;
      case DataType.URLString:
        fieldConfig = DynamicFieldHelper.createFieldInputWebUrl(targetField, labelKey, fd.required, fieldOptionsCc);
        break;
      case DataType.EnumSet:
        fieldConfig = this.createEnumSetMultiSelect(translateService, fd, labelKey, targetField, fieldOptionsCc);
        break;
      case DataType.DateString:
      case DataType.DateNumeric:
      case DataType.DateTimeNumeric:
      case DataType.DateTimeString:
      case DataType.DateStringShortUS:
        fieldConfig = DynamicFieldHelper.createFieldPcalendar(
          DataType[fd.dataType],
          targetField,
          labelKey,
          fd.required
        );
        if (
          fd.dynamicFormPropertyHelps &&
          DynamicFormPropertyHelps[fd.dynamicFormPropertyHelps[0]] === DynamicFormPropertyHelps.DATE_FUTURE
        ) {
          fieldConfig.defaultValue = new Date();
          this.applyMinDate(fieldConfig, fieldConfig.defaultValue, fd.required);
        } else {
          // Only required dates pre-fill today; an optional date (e.g. Cashaccount.activeToDate) stays empty/null.
          if (fd.required) {
            fieldConfig.defaultValue = new Date();
          }
          if (fd.dateMin) {
            this.applyMinDate(fieldConfig, new Date(fd.dateMin), fd.required);
          }
        }
        break;
    }
    return fieldConfig;
  }

  /**
   * Adds a minimum-date constraint to a calendar field: sets the calendar's minDate and appends a
   * gteDate validator with its error rule. Used both for @Future (DATE_FUTURE) and @AfterEqual (dateMin).
   *
   * @param fieldConfig the calendar field configuration to constrain
   * @param minDate the earliest selectable/valid date
   * @param required whether the field is required (kept for symmetry with the existing required validators)
   */
  private static applyMinDate(fieldConfig: FieldConfig, minDate: Date, required: boolean): void {
    fieldConfig.calendarConfig = { ...fieldConfig.calendarConfig, minDate };
    const validator = gteDate(minDate);
    fieldConfig.validation = fieldConfig.validation ? [...fieldConfig.validation, validator] : [validator];
    const emr: ErrorMessageRules = { name: 'gteDate', keyi18n: 'gteDate', param1: <any>minDate, rules: ['dirty'] };
    fieldConfig.errors = fieldConfig.errors ? [...fieldConfig.errors, emr] : [emr];
  }

  /**
   * Creates a numeric input field configuration from descriptor properties. A SELECT_OPTIONS hint yields a
   * numeric select (options filled at runtime by the component), a @Digits precision yields an input-number
   * with integer/fraction limits, otherwise a min/max number input is produced.
   *
   * @param fd descriptor with numeric metadata (min, max, digitsInteger/digitsFraction, helps)
   * @param labelKey translation key for the field label
   * @param targetField target field name for data binding
   * @param fieldOptionsCc additional field options
   * @returns FieldConfig for the appropriate numeric input type
   */
  private static createNumericInputFromDescriptor(
    fd: FieldDescriptorInputAndShow,
    labelKey: string,
    targetField: string,
    fieldOptionsCc?: FieldOptionsCc
  ): FieldConfig {
    const helpNames = <string[]>(fd.dynamicFormPropertyHelps || []);
    if (helpNames.indexOf(DynamicFormPropertyHelps[DynamicFormPropertyHelps.SELECT_OPTIONS]) >= 0) {
      return DynamicFieldHelper.createFieldSelectNumber(targetField, labelKey, fd.required, fieldOptionsCc);
    }
    if (fd.digitsInteger != null) {
      const allowNegative = fd.min == null || fd.min < 0;
      return DynamicFieldHelper.createFieldInputNumber(
        targetField,
        labelKey,
        fd.required,
        fd.digitsInteger,
        fd.digitsFraction != null ? fd.digitsFraction : 0,
        allowNegative,
        fieldOptionsCc
      );
    }
    return DynamicFieldHelper.createFieldMinMaxNumber(
      DataType[fd.dataType],
      targetField,
      labelKey,
      fd.required,
      fd.min,
      fd.max,
      { ...fieldOptionsCc, fieldSuffix: DynamicFieldModelHelper.getFieldPercentageSuffix(fd) }
    );
  }

  /**
   * Appends a regular-expression validator (from a backend @Pattern annotation) and its error rule to a field.
   *
   * @param fieldConfig the field configuration to constrain
   * @param pattern the regular expression the value must match
   */
  private static applyPattern(fieldConfig: FieldConfig, pattern: string): void {
    const validator = Validators.pattern(pattern);
    fieldConfig.validation = fieldConfig.validation ? [...fieldConfig.validation, validator] : [validator];
    const emr: ErrorMessageRules = { name: 'pattern', keyi18n: 'pattern', rules: [RuleEvent.FOCUSOUT] };
    fieldConfig.errors = fieldConfig.errors ? [...fieldConfig.errors, emr] : [emr];
  }

  /**
   * Creates string input field configuration from descriptor properties.
   * Handles various string input types based on dynamicFormPropertyHelps: EMAIL (with email validation),
   * PASSWORD (masked input), SELECT_OPTIONS (dropdown whose options the component supplies). Without a helper, a field
   * backed by a Java enum becomes a dropdown of its translated constants, any other field a standard input or a
   * textarea based on max length.
   *
   * @param translateService Angular TranslateService for translating the constants of an enum-backed field
   * @param fd Field descriptor containing string field metadata (max length, min length, required status)
   * @param labelKey Translation key for field label
   * @param targetField Target field name (may differ from descriptor field name for aliasing)
   * @param fieldOptionsCc Additional field options and configuration (merged with min length from descriptor)
   * @returns FieldConfig object configured for appropriate string input type with validation
   */
  private static createStringInputFromDescriptor(
    translateService: TranslateService,
    fd: FieldDescriptorInputAndShow,
    labelKey: string,
    targetField: string,
    fieldOptionsCc?: FieldOptionsCc
  ): FieldConfig {
    let fieldConfig: FieldConfig;
    const fieldOptions: FieldOptions = Object.assign({}, fieldOptionsCc, { minLength: fd.min });
    if (fd.dynamicFormPropertyHelps) {
      switch (DynamicFormPropertyHelps[fd.dynamicFormPropertyHelps[0]]) {
        case DynamicFormPropertyHelps.EMAIL:
          fieldConfig = DynamicFieldHelper.createFieldDAInputStringVSHeqF(
            DataType.Email,
            targetField,
            fd.max,
            fd.required,
            [VALIDATION_SPECIAL.EMail],
            fieldOptions
          );
          break;
        case DynamicFormPropertyHelps.PASSWORD:
          fieldConfig = DynamicFieldHelper.createFieldDAInputStringHeqF(
            DataType.Password,
            targetField,
            fd.max,
            fd.required,
            fieldOptions
          );
          break;
        case DynamicFormPropertyHelps.SELECT_OPTIONS:
          // A missing @Size leaves max null; assigning that makes the select 1em wide (null + 1 in the template).
          if (fd.max) {
            fieldOptions.inputWidth = fd.max;
          }
          fieldConfig = DynamicFieldHelper.createFieldSelectStringHeqF(targetField, fd.required, fieldOptions);
          break;

        default:
      }
    } else if (fd.enumValues?.length) {
      // The backend sends the constants of an enum field, so they are the complete list of valid values.
      fieldConfig = DynamicFieldHelper.createFieldSelectString(targetField, labelKey, fd.required, {
        ...fieldOptions,
        valueKeyHtmlOptions: this.translatedEnumOptions(translateService, fd, !fd.required)
      });
    } else {
      if (fd.max && fd.max > 80) {
        fieldOptions.textareaRows = fieldOptions.textareaRows ? fieldOptions.textareaRows : Math.ceil(fd.max / 80);
        fieldConfig = DynamicFieldHelper.createFieldTextareaInputString(
          targetField,
          labelKey,
          fd.max,
          fd.required,
          fieldOptions
        );
      } else {
        fieldConfig = DynamicFieldHelper.createFieldInputString(
          targetField,
          labelKey,
          fd.max,
          fd.required,
          fieldOptions
        );
      }
      if (fd.pattern) {
        this.applyPattern(fieldConfig, fd.pattern);
      }
    }
    return fieldConfig;
  }

  /**
   * Creates a multi-select input for Set&lt;Enum&gt; fields.
   * Converts enum values from descriptor to ValueKeyHtmlSelectOptions for the dropdown.
   * Options are translated using the enum value as translation key.
   *
   * @param translateService Angular TranslateService for translating enum options
   * @param fd Field descriptor containing enumType and enumValues from backend
   * @param labelKey Translation key for field label
   * @param targetField Target field name for data binding
   * @param fieldOptionsCc Additional field options (merged with generated options)
   * @returns FieldConfig for multi-select component with translated enum options and empty array default
   */
  private static createEnumSetMultiSelect(
    translateService: TranslateService,
    fd: FieldDescriptorInputAndShow,
    labelKey: string,
    targetField: string,
    fieldOptionsCc?: FieldOptionsCc
  ): FieldConfig {
    const fieldOptions: FieldOptions = Object.assign({}, fieldOptionsCc, {
      valueKeyHtmlOptions: this.translatedEnumOptions(translateService, fd, false)
    });

    return DynamicFieldHelper.createFieldMultiSelectString(targetField, labelKey, fd.required, fieldOptions);
  }

  /**
   * Converts the enum constants of a descriptor into select options. The key is the constant name, which is the value
   * submitted to the backend; the displayed text is its translation, with the constant name as translation key.
   *
   * @param translateService Angular TranslateService for translating the constants
   * @param fd Field descriptor whose enumValues hold the constant names
   * @param addEmpty Whether an empty entry precedes the constants, so that an optional field can be cleared
   * @returns The translated options, empty when the descriptor carries no constants
   */
  private static translatedEnumOptions(
    translateService: TranslateService,
    fd: FieldDescriptorInputAndShow,
    addEmpty: boolean
  ): ValueKeyHtmlSelectOptions[] {
    const untranslatedOptions: ValueKeyHtmlSelectOptions[] =
      fd.enumValues?.map((enumValue) => new ValueKeyHtmlSelectOptions(enumValue, enumValue)) || [];
    return SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(
      translateService,
      untranslatedOptions,
      addEmpty
    );
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
  public static createAndSetValuesInDynamicModel(
    e: any,
    targetSelectionField: string,
    paramMap: Map<string, BaseParam> | { [key: string]: BaseParam },
    fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
    addStrategyImplField = false
  ): any {
    const dynamicModel: any = {};
    if (addStrategyImplField) {
      dynamicModel[targetSelectionField] = e;
    }
    return DynamicFieldModelHelper.setValuesOfMapModelToDynamicModel(
      fieldDescriptorInputAndShows,
      paramMap,
      dynamicModel
    );
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
  public static setValuesOfMapModelToDynamicModel(
    fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
    paramMap: Map<string, BaseParam> | { [key: string]: BaseParam },
    dynamicModel: any = {}
  ): any {
    fieldDescriptorInputAndShows.forEach((fieldDescriptorInputAndShow) => {
      // An optional field left empty is not stored at all, so its parameter may be missing.
      let value = paramMap[fieldDescriptorInputAndShow.fieldName]?.paramValue ?? null;
      switch (DataType[fieldDescriptorInputAndShow.dataType]) {
        case DataType.Numeric:
        case DataType.NumericInteger:
          // Number(null) would turn an empty optional bound into 0
          value = value === null || value === '' ? null : Number(value);
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
    return fDIAS.dynamicFormPropertyHelps &&
      (<string[]>fDIAS.dynamicFormPropertyHelps).indexOf(
        DynamicFormPropertyHelps[DynamicFormPropertyHelps.PERCENTAGE]
      ) >= 0
      ? '%'
      : null;
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

/**
 * A form group built from the class-level constraints of a descriptor, with the fields it takes the place of.
 */
interface ConstraintFieldGroup {
  /** Names of the grouped fields in descriptor order; the group is placed where the first of them would be. */
  fieldNames: string[];
  fieldFormGroup: FormGroupDefinition;
}

/**
 * Fields, group validators and error rules of one class constraint, or of several that share fields.
 */
interface ConstraintDefinition {
  fields: string[];
  validation: ValidatorFn[];
  errors: ErrorMessageRules[];
}
