import {FilterType} from './filter.type';
import {ValueLabelHtmlSelectOptions} from './value.label.html.select.options';
import {BaseFieldDefinition, PropertyEditShare} from '../dynamic-form/models/base.field.definition';
import {ValidationErrorRule} from '../dynamic-form/models/base.field.fieldgroup.config';
import {ValueKeyHtmlSelectOptions} from '../dynamic-form/models/value.key.html.select.options';

/**
 * Comprehensive configuration interface for defining table columns and single data field displays.
 * Extends base field definitions with table-specific properties including visibility, formatting,
 * translation, filtering, and grouping capabilities.
 *
 * This interface serves as the primary configuration object for all column-based data display
 * components throughout the application, supporting both simple data display and complex
 * table operations with sorting, filtering, and editing capabilities.
 */
export interface ColumnConfig extends BaseFieldDefinition {
  /** Translation key for the column header text */
  headerKey: string;

  /** Actual translated header text populated by translation service */
  headerTranslated: string;

  /** Translated tooltip text for the column header */
  headerTooltipTranslated: string;

  /** Whether this column is currently visible in the display */
  visible: boolean;

  /** Whether users can toggle the visibility of this column */
  changeVisibility: boolean;

  /** Fixed width of the column in pixels */
  width?: number;

  /**
   * Custom function to transform field values before display.
   * Allows for complex formatting, calculations, or conditional display logic.
   *
   * @param dataobject - The source data object
   * @param field - The current column configuration
   * @param valueField - The raw field value
   * @returns The transformed value for display
   */
  fieldValueFN?: (dataobject: any, field: ColumnConfig, valueField: any) => any;

  /** Text to append after the header text */
  headerSuffix?: string;

  /** Text to prepend before the header text */
  headerPrefix?: string;

  /** Type of filtering to apply to this column */
  filterType?: FilterType;

  /** Predefined filter options for dropdown-style filtering */
  filterValues?: ValueLabelHtmlSelectOptions[];

  /**
   * Specifies how field values should be translated. Controls whether values are translated as-is,
   * converted to uppercase first, or processed as arrays before translation. For example, enums must be translated
   * into the corresponding language of the user.
   */
  translateValues: TranslateValue;

  /**
  * Cached mapping of original field values to their translated equivalents.
  * Populated automatically by the translation service for performance optimization.
  *
  * Key: Original field value from data object (e.g., "ACTIVE", "PENDING")
  * Value: Translated display text (e.g., "Active", "Pending")
  *
  * Used for filter dropdown generation, consistent value display, and enabling
  * proper alphabetical sorting of translated content in PrimeNG tables.
  * Rebuilt when language changes or new unique values appear in dataset.
  *
  * **Field Creation**: For sorting support, translated values are also added to each
  * data object with a "$" suffix (e.g., field "status" gets "status$" with translated value).
  * The `fieldTranslated` property points to this suffixed field name for PrimeNG sorting.
  *
  * @example
  * // translatedValueMap: { "ACTIVE": "Active", "INACTIVE": "Inactive" }
  * // Data object gets: { status: "ACTIVE", status$: "Active" }
  */
  translatedValueMap: { [key: string]: string };

  /**
   * Field name for accessing translated values, typically original field name with '$' suffix.
   * Used internally for PrimeNG table sorting of translated content.
   */
  fieldTranslated: string;

  /**
   * Template identifier for Angular ngSwitch directives.
   * Commonly used for conditional styling (e.g., 'check', 'icon', 'owner').
   */
  templateName?: string;

  /** Minimum number of decimal places to display for numeric values */
  minFractionDigits?: number;

  /** Maximum number of decimal places to display for numeric values */
  maxFractionDigits?: number;
  /**
   * Some columns may have a row group, for example the total of values of this cholumn.
   * The format is the same but the field property is different.
   */
  columnGroupConfigs?: ColumnGroupConfig[];

  /**
   * Visual grouping identifier for organizing related fields.
   * Fields with the same fieldsetName are displayed together with visual separators.
   */
  fieldsetName: string;

  /** If true this field will be exported when export functionality is available  */
  export?: boolean;

  /** Whether this column should remain fixed during horizontal scrolling */
  frozenColumn?: boolean;

  /**
   * This value can, of course, also be set by the system and not just by the user.
   * This makes it an additional attribute to the clearly specified attributes.
   */
  userValue: any;

  /** Configuration for making this column editable in table editing scenarios */
  cec: ColumnEditConfig;
}

/**
 * Optional parameters interface for customizing column configurations during creation.
 * Provides a subset of ColumnConfig properties that can be specified when adding
 * columns to table configurations. Used by column creation methods to avoid
 * requiring all properties upfront.
 */
 export interface OptionalParams {
  /** Fixed width of the column in pixels */
  width?: number;

  /** Custom value transformation function */
  fieldValueFN?: (dataobject: any, field: ColumnConfig, valueField: any) => any;

  /** Text to append after the header text */
  headerSuffix?: string;

  /** Text to prepend before the header text */
  headerPrefix?: string;

  /** How field values should be translated */
  translateValues?: TranslateValue;

  /** Template identifier for conditional display logic */
  templateName?: string;

  /** Maximum decimal places for numeric display */
  maxFractionDigits?: number;

  /** Minimum decimal places for numeric display */
  minFractionDigits?: number;

  /** Group/total row configurations */
  columnGroupConfigs?: ColumnGroupConfig[];

  /** Type of filtering to enable */
  filterType?: FilterType;

  /** Predefined filter options */
  filterValues?: ValueLabelHtmlSelectOptions[];

  /** Visual grouping identifier */
  fieldsetName?: string;

  /** Pre-translated header text */
  headerTranslated?: string;

  /** Include in data exports */
  export?: boolean;

  /** Fix column during scrolling */
  frozenColumn?: boolean;

  /** User-defined value */
  userValue?: any;
}


/**
 * Enumeration defining input types for inline table cell editing.
 * Maps to appropriate PrimeNG components or native HTML input elements.
 * Used by EditableTableComponent to render the correct input control.
 */
export enum EditInputType {
  /** Native HTML text input for string values */
  Text = 'text',
  /** Native HTML number input for integer values */
  Number = 'number',
  /** Native HTML select (dropdown) for enumerated values */
  Select = 'select',
  /** PrimeNG InputNumber component for formatted numeric values */
  InputNumber = 'inputNumber',
  /** PrimeNG DatePicker component for date values */
  DatePicker = 'datePicker',
  /** PrimeNG Checkbox component for boolean values */
  Checkbox = 'checkbox',
  /** Read-only display - cell is not editable even in edit mode */
  ReadOnly = 'readonly'
}

/**
 * Configuration interface for editable table columns.
 * Combines property editing capabilities with validation rules to enable
 * in-place table editing functionality. Extends base editing and validation
 * interfaces to provide comprehensive editing support.
 *
 * @extends PropertyEditShare - provides valueKeyHtmlOptions for dropdown options
 * @extends ValidationErrorRule - provides validation and errors arrays
 */
export interface ColumnEditConfig extends PropertyEditShare, ValidationErrorRule {
  /**
   * Explicit input type for editing. If not set, will be inferred from the column's DataType.
   * Use this to override the default input type (e.g., use Select instead of Text for a String field).
   */
  inputType?: EditInputType;

  /**
   * Callback to determine if this specific cell is editable for a given row.
   * If not set, the cell is always editable when the row is in edit mode.
   * Useful for conditional editability (e.g., ID field editable only for new rows).
   *
   * @param row - The data object for the current row
   * @param field - The column configuration
   * @returns true if the cell should be editable
   */
  canEditFn?: (row: any, field: ColumnConfig) => boolean;

  /**
   * Declarative approach for dependent dropdowns: field name that this dropdown depends on.
   * When the parent field value changes, this dropdown's options will be updated.
   * Use with optionsMap for simple static mappings.
   */
  dependsOnField?: string;

  /**
   * Declarative mapping of parent field values to dropdown options.
   * Used together with dependsOnField for simple dependent dropdown scenarios.
   * Key is the parent field value, value is the array of options to display.
   *
   * @example
   * optionsMap: {
   *   'CATEGORY_A': [{ key: 'A1', value: 'Option A1' }, { key: 'A2', value: 'Option A2' }],
   *   'CATEGORY_B': [{ key: 'B1', value: 'Option B1' }]
   * }
   */
  optionsMap?: { [parentValue: string]: ValueKeyHtmlSelectOptions[] };

  /**
   * Callback approach for dependent dropdowns: dynamically provides options based on current row state.
   * Takes precedence over optionsMap when both are defined.
   * Use this for complex logic that cannot be expressed as a simple mapping.
   *
   * @param row - The data object for the current row
   * @param field - The column configuration
   * @returns Array of options to display in the dropdown
   */
  optionsProviderFn?: (row: any, field: ColumnConfig) => ValueKeyHtmlSelectOptions[];

  /**
   * Callback triggered when this field's value changes during editing.
   * Useful for cascading updates, validation, or updating other fields.
   *
   * @param row - The data object for the current row
   * @param field - The column configuration
   * @param newValue - The new value that was set
   */
  onChangeFn?: (row: any, field: ColumnConfig, newValue: any) => void;

  /** Placeholder text for text/number input fields */
  placeholder?: string;

  /** Maximum decimal places for InputNumber inputs (overrides column's maxFractionDigits in edit mode) */
  maxFractionDigits?: number;

  /** Minimum allowed value for number inputs */
  min?: number;

  /** Maximum allowed value for number inputs */
  max?: number;

  /** Minimum allowed date for DatePicker inputs */
  minDate?: Date;

  /** Maximum allowed date for DatePicker inputs */
  maxDate?: Date;

  /** Maximum character length for text inputs */
  maxLength?: number;
}

/**
 * Configuration class for grouped column displays in table sections such as subtotals and group footers.
 * Defines how to access and display values from backend model data in table group sections.
 *
 * The backend provides all numeric and data values within the model objects, but this configuration
 * specifies how to access the correct field values and optionally apply custom formatting or
 * extraction logic when simple field access is insufficient.
 *
 * This is primarily used in financial and reporting tables where group sections need to display
 * values like subtotals, counts, or other aggregated data that already exists in the backend model.

 */
export class ColumnGroupConfig {
  /** Translated text for display, populated by translation service */
  textValueTranslated: string;

  /** Number of columns this group configuration should span */
  colspan: number;

  /**
   * Creates a new column group configuration for accessing backend model data.
   *
   * @param fieldValue - Field path to access the value directly from the backend model data.
   *                    Use empty string when using fieldTextFN for custom value extraction.
   * @param textValueKey - Translation key for the group label text (e.g., "TOTAL", "SUBTOTAL")
   * @param fieldTextFN - Custom function for extracting values when simple field access is insufficient.
   *                     Used when the required value needs special logic to extract from the model data.
   *                     Function receives the group data object containing all backend values.
   * @param optionalsGropuParams - Additional configuration options including column spanning
   */
  constructor(public fieldValue: string, public textValueKey?: string, public fieldTextFN?: (columnConfig: ColumnConfig,
      arrIndex: number, groupChangeIndexMap: any, rowIndex: number) => any,
    optionalsGropuParams?: OptionalGroupParams) {
    if (optionalsGropuParams) {
      this.colspan = optionalsGropuParams.colspan;
    }
  }
}

/**
 * Optional parameters for configuring column group display properties.
 * Provides additional layout and spanning options for group configurations.
 */
export interface OptionalGroupParams {
  /**
   * Number of columns this group should span. Special value 99 indicates spanning all remaining available columns.
   * This value may be modified programmatically during layout calculations.
   */
  colspan: number;
}

/**
 * Enumeration defining different translation processing modes for column values.
 * Controls how field values are processed before being sent to the translation service,
 * enabling consistent handling of various data formats across the application.
 *
 * Values start at 1 to avoid conflicts with boolean falsy checks in conditional logic.
 */
export enum TranslateValue {
  /** Standard translation without modification. Values are passed directly to the translation service as-is.  */
  NORMAL = 1,
  /** Convert values to uppercase before translation. Useful for ensuring consistent translation key format.  */
  UPPER_CASE = 2,

  /**
   * Process array values by converting each element to uppercase, translating individually, then joining with comma separation.
   * Ideal for displaying lists of translated enum values.
   */
  UPPER_CASE_ARRAY_TO_COMMA_SEPERATED = 3

}

