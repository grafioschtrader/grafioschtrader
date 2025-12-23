import {DataType} from '../dynamic-form/models/data.type';
import {FieldFormGroup} from '../dynamic-form/models/form.group.definition';

/**
 * Specialized form property helpers that provide UI hints and behaviors for dynamic form generation.
 * These helpers influence how input fields are rendered and validated in the user interface,
 * extending beyond basic data type constraints.
 *
 * Must mirror the backend enum `grafiosch.dynamic.model.DynamicFormPropertyHelps`.
 */
export enum DynamicFormPropertyHelps {
  /** Input field displays and handles values as percentages */
  PERCENTAGE,
  /** Input field masks characters for password entry */
  PASSWORD,
  /** Input field validates email address format */
  EMAIL,
  /** Input field renders as a dropdown with predefined options */
  SELECT_OPTIONS,
  /** Date input validates that the selected date is in the future. Value must match backend ordinal. */
  DATE_FUTURE = 4
}

/**
 * Types of cross-field constraint validators that apply validation rules across multiple fields.
 * Used in ClassDescriptorInputAndShow.constraintValidatorMap to define class-level validation.
 *
 * Must mirror the backend enum `grafiosch.dynamic.model.ConstraintValidatorType`.
 */
export enum ConstraintValidatorType {
  /** Validates that a start date field value is before or equal to an end date field value */
  DateRange
}

/**
 * Complete metadata definition for a dynamic form class received from the backend.
 * Contains both individual field descriptors for form elements and class-level constraint validators
 * that apply across multiple fields (such as date range validation).
 *
 * This class is the frontend counterpart to `grafiosch.dynamic.model.ClassDescriptorInputAndShow`.
 * It is used to generate dynamic input forms and validation rules based on backend model analysis.
 *
 * @example
 * // Received from backend after analyzing a model class like MaintenanceMsg
 * const descriptor: ClassDescriptorInputAndShow = await this.gtnetService.getFormDescriptor();
 * // descriptor.fieldDescriptorInputAndShows contains metadata for each field
 * // descriptor.constraintValidatorMap may contain {DateRange: {startField: 'fromDateTime', endField: 'toDateTime'}}
 */
export class ClassDescriptorInputAndShow {
  /**
   * Array of field descriptors defining the individual input elements and their properties.
   * Each descriptor specifies field access, data type, validation constraints, and UI hints.
   */
  fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[];

  /**
   * Map of class-level constraint validators that apply validation rules across multiple fields.
   * The key is the constraint type (e.g., DateRange), and the value contains the constraint configuration
   * (e.g., {startField: 'fromDateTime', endField: 'toDateTime'} for DateRange).
   */
  constraintValidatorMap: { [key: string]: any} | Map<ConstraintValidatorType, any>;
}

/**
 * Metadata for a single input element in a dynamic form. An instance corresponds to one form field
 * and may be created from backend analysis of a Java class (using annotations like @NotNull, @Size, etc.)
 * or from persisted metadata for user-defined fields.
 *
 * This interface is the frontend counterpart to `grafiosch.dynamic.model.FieldDescriptorInputAndShow`.
 *
 * @example
 * // A field descriptor for a required date field from MaintenanceMsg
 * const field: FieldDescriptorInputAndShow = {
 *   fieldName: 'fromDateTime',
 *   dataType: DataType.DateTimeNumeric,
 *   required: true,
 *   min: null,
 *   max: null,
 *   enumType: null,
 *   dynamicFormPropertyHelps: [DynamicFormPropertyHelps.DATE_FUTURE]
 * };
 */
export interface FieldDescriptorInputAndShow {
  /** The name of the field as it appears in the model class or form. Used for data binding. */
  fieldName: string;

  /**
   * Array of specialized UI hints that modify input behavior. May be string values when received
   * from JSON deserialization, or DynamicFormPropertyHelps enum values after processing.
   */
  dynamicFormPropertyHelps: string[] | DynamicFormPropertyHelps[];

  /**
   * The data type that determines the input component type and validation rules.
   * May be a string when received from JSON deserialization, or DataType enum value after processing.
   */
  dataType: string | DataType;

  /** The simple name of the enum class if this field represents an enum type. Null for non-enum fields. */
  enumType: string;

  /** Whether this field is required and must have a value before form submission. */
  required: boolean;

  /** The minimum value for numeric types or minimum length for string types. Null if no minimum constraint. */
  min: number;

  /** The maximum value for numeric types or maximum length for string types. Null if no maximum constraint. */
  max: number;

  /** Available enum constant names when dataType is EnumSet. Used to populate multi-select options. */
  enumValues: string[];
}

/**
 * Extended field descriptor that includes additional metadata for enhanced dynamic form generation.
 * This interface extends the basic field descriptor with user interface presentation information,
 * field ordering, and user-specific configurations.
 *
 * This interface is the frontend counterpart to `grafiosch.dynamic.model.FieldDescriptorInputAndShowExtended`
 * and is typically used for user-defined fields (UDF) where users can create custom form fields
 * with their own labels, help text, and display order.
 */
export interface FieldDescriptorInputAndShowExtended extends FieldDescriptorInputAndShow {
  /** User-facing label displayed for this field in the form interface. */
  description: string;

  /** Optional help text shown as a tooltip or help popup. May be null if no help is needed. */
  descriptionHelp: string;

  /** Display order of this field relative to other fields. Lower values appear first in the form. */
  uiOrder: number;

  /** Special type identifier for user-defined field behaviors and custom processing. May be null. */
  udfSpecialType: number;

  /** ID of the user who created this field configuration. Used for user-specific customizations. */
  idUser: number;
}

/**
 * Configuration for replacing a simple field with a grouped field structure in dynamic forms.
 * This is used when a single backend field needs to be represented as multiple related input
 * components in the frontend, or when custom field grouping is required.
 *
 * @example
 * // Replace a single 'dateRange' field with a group containing 'startDate' and 'endDate' inputs
 * const replacement = new ReplaceFieldWithGroup('dateRange', dateRangeFieldGroup, 'dateRange');
 */
export class ReplaceFieldWithGroup {
  /**
   * Creates a new field replacement configuration.
   *
   * @param replaceFieldName - The name of the field in the descriptor to be replaced with the group
   * @param fieldFormGroup - The form group definition that will replace the simple field
   * @param removeFieldName - The name of the field to remove from the original descriptor list
   */
  constructor(public replaceFieldName: string, public fieldFormGroup: FieldFormGroup, public removeFieldName: string) {
  }
}
