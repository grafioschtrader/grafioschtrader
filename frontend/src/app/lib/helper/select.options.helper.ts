import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {TranslateService} from '@ngx-translate/core';
import {EnumI} from './enumI';
import {SelectItem} from 'primeng/api';

/**
 * Comprehensive utility class for creating select/dropdown options from various data sources.
 * Provides factory methods for generating ValueKeyHtmlSelectOptions and PrimeNG SelectItems
 * with built-in support for internationalization, filtering, sorting, and state management.
 *
 * Key features:
 * - Multiple data source support (arrays, objects, enums, number ranges)
 * - Automatic translation integration with TranslateService
 * - Filtering and exclusion capabilities
 * - Optional empty value handling
 * - Option state management (disabled/enabled)
 * - Automatic sorting with locale awareness
 * - PrimeNG SelectItem compatibility
 *
 * Supports common dropdown scenarios:
 * - Static option lists from arrays or objects
 * - Dynamic options from enums with translation
 * - Numeric ranges with exclusions
 * - Translated options with allow/deny filtering
 * - Conditional option disabling
 */
export class SelectOptionsHelper {

  /**
   * Creates select options from a numeric range with optional exclusions.
   * Generates consecutive integer options within specified bounds, excluding specified values.
   * Useful for year selectors, pagination controls, or numeric configuration dropdowns.
   *
   * @param startNum Starting number (inclusive)
   * @param endNum Ending number (inclusive)
   * @param excludeNum Array of numbers to exclude from the range
   * @returns Array of ValueKeyHtmlSelectOptions with numbers as both key and value
   *
   * @example
   * // Create year options from 2020-2025, excluding 2022 and 2023
   * const yearOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsForNumberRange(
   *   2020, 2025, [2022, 2023]
   * );
   * // Result: [{key: 2020, value: "2020"}, {key: 2021, value: "2021"}, {key: 2024, value: "2024"}, {key: 2025, value: "2025"}]
   */
  public static createValueKeyHtmlSelectOptionsForNumberRange(startNum: number, endNum: number,
    excludeNum: number[]): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [];
    for (let i = startNum; i <= endNum; i += 1) {
      if (excludeNum.indexOf(i) < 0) {
        valueKeyHtmlSelectOptions.push(new ValueKeyHtmlSelectOptions(i, '' + i));
      }
    }
    return valueKeyHtmlSelectOptions
  }

  /**
   * Creates select options from string array with optional case transformation.
   * Each string serves as both key and value, with optional uppercase transformation for display.
   * Ideal for simple lists where the stored value matches the display value.
   *
   * @param keysAndValues Array of strings to convert to options
   * @param uppercaseValue Whether to display values in uppercase (default: false)
   * @returns Array of ValueKeyHtmlSelectOptions with strings as both key and value
   *
   * @example
   * // Create status options
   * const statusOptions = SelectOptionsHelper.createHtmlOptionsFromStringArray(
   *   ['active', 'inactive', 'pending'], true
   * );
   * // Result: [{key: 'active', value: 'ACTIVE'}, {key: 'inactive', value: 'INACTIVE'}, {key: 'pending', value: 'PENDING'}]
   */
  public static createHtmlOptionsFromStringArray(keysAndValues: string[], uppercaseValue = false): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [];
    keysAndValues.forEach(keyAndValue => valueKeyHtmlSelectOptions.push(
      new ValueKeyHtmlSelectOptions(keyAndValue, uppercaseValue ? keyAndValue.toUpperCase() : keyAndValue)));
    return valueKeyHtmlSelectOptions;
  }

  /**
   * Creates select options from enum with translation, including empty option.
   * Converts enum values to translated select options with optional filtering.
   * Automatically adds empty option at the beginning for "no selection" scenarios.
   *
   * @param translateService Angular TranslateService for value translation
   * @param e Enum object implementing EnumI interface
   * @param allowedEnums Optional array of enum values to include/exclude
   * @param deny If true, allowedEnums are excluded; if false, only allowedEnums are included
   * @returns Array of ValueKeyHtmlSelectOptions with empty option first, followed by translated enum values
   *
   * @example
   * // Create transaction type options with empty option, excluding certain types
   * const options = SelectOptionsHelper.createHtmlOptionsFromEnumAddEmpty(
   *   translateService, TransactionType, [TransactionType.INTERNAL], true
   * );
   * // Result: [{key: '', value: ''}, {key: 'BUY', value: 'Buy'}, {key: 'SELL', value: 'Sell'}]
   */
  public static createHtmlOptionsFromEnumAddEmpty(translateService: TranslateService, e: EnumI, allowedEnums?: any[],
    deny?: boolean): ValueKeyHtmlSelectOptions[] {
    const transactionHtmlOptions: ValueKeyHtmlSelectOptions[] = [new ValueKeyHtmlSelectOptions('', '')];
    return this.createHtmlOptionsFromEnumWithEmptyOrNot(transactionHtmlOptions, translateService, e, allowedEnums, deny);
  }

  /**
   * Creates select options from enum with translation, without empty option.
   * Converts enum values to translated select options with optional filtering.
   * Used when a selection is mandatory and no "empty" option should be available.
   *
   * @param translateService Angular TranslateService for value translation
   * @param e Enum object implementing EnumI interface
   * @param allowedEnums Optional array of enum values to include/exclude
   * @param deny If true, allowedEnums are excluded; if false, only allowedEnums are included
   * @returns Array of ValueKeyHtmlSelectOptions with translated enum values only
   *
   * @example
   * // Create priority options without empty option, only specific priorities
   * const priorityOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
   *   translateService, Priority, [Priority.HIGH, Priority.CRITICAL], false
   * );
   * // Result: [{key: 'HIGH', value: 'High'}, {key: 'CRITICAL', value: 'Critical'}]
   */
  public static createHtmlOptionsFromEnum(translateService: TranslateService, e: EnumI, allowedEnums?: any[],
    deny?: boolean): ValueKeyHtmlSelectOptions[] {
    const transactionHtmlOptions: ValueKeyHtmlSelectOptions[] = [];
    return this.createHtmlOptionsFromEnumWithEmptyOrNot(transactionHtmlOptions, translateService, e, allowedEnums, deny);
  }

  /**
   * Creates select options from enum with specific options disabled.
   * Converts enum values to translated options with certain values marked as disabled.
   * Useful for showing all options but preventing selection of certain values based on context.
   *
   * @param translateService Angular TranslateService for value translation
   * @param e Enum object implementing EnumI interface
   * @param disabledEnums Array of enum values that should be disabled
   * @returns Array of ValueKeyHtmlSelectOptions with specified options marked as disabled
   *
   * @example
   * // Create status options with some statuses disabled based on user permissions
   * const statusOptions = SelectOptionsHelper.createHtmlOptionsFromEnumDisabled(
   *   translateService, OrderStatus, [OrderStatus.CANCELLED, OrderStatus.DELETED]
   * );
   * // Result: All statuses with CANCELLED and DELETED marked as disabled: true
   */
  public static createHtmlOptionsFromEnumDisabled(translateService: TranslateService, e: EnumI,
    disabledEnums?: any[]): ValueKeyHtmlSelectOptions[] {
    const transactionHtmlOptions: ValueKeyHtmlSelectOptions[] = [];
    return this.createHtmlOptionsFromEnumWithEmptyOrNot(transactionHtmlOptions, translateService, e, null, false,
      disabledEnums);
  }

  /**
   * Creates select options from object array using specified properties.
   * Extracts key and display value from object properties, with optional empty option.
   * Perfect for converting API response arrays to dropdown options.
   *
   * @param key Property name to use as option key/value
   * @param propertyName Property name to use as display text
   * @param values Array of objects containing the specified properties
   * @param addEmpty Whether to include empty option at the beginning
   * @returns Array of ValueKeyHtmlSelectOptions built from object properties
   *
   * @example
   * // Create user options from API response
   * const users = [{id: 1, name: 'John Doe'}, {id: 2, name: 'Jane Smith'}];
   * const userOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
   *   'id', 'name', users, true
   * );
   * // Result: [{key: '', value: ''}, {key: 1, value: 'John Doe'}, {key: 2, value: 'Jane Smith'}]
   */
  public static createValueKeyHtmlSelectOptionsFromArray(key: string, propertyName: string, values: any[],
    addEmpty: boolean): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [];
    addEmpty && valueKeyHtmlSelectOptions.push(new ValueKeyHtmlSelectOptions('', ''));
    values.forEach(element => {
      valueKeyHtmlSelectOptions.push(new ValueKeyHtmlSelectOptions(element[key], element[propertyName]));
    });
    return valueKeyHtmlSelectOptions;
  }

  /**
   * Creates select options from object with nested properties.
   * Converts object keys to options, using nested property for display values.
   * Supports exclusion of specific keys and optional empty option.
   *
   * @param propertyName Nested property name to use for display text
   * @param values Object where keys become option keys and nested properties become display values
   * @param addEmpty Whether to include empty option at the beginning
   * @param excludeKeys Array of object keys to exclude from options
   * @returns Array of ValueKeyHtmlSelectOptions built from object keys and nested properties
   *
   * @example
   * // Create category options from configuration object
   * const categories = {
   *   'tech': {label: 'Technology'},
   *   'finance': {label: 'Finance'},
   *   'health': {label: 'Healthcare'}
   * };
   * const categoryOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromObject(
   *   'label', categories, true, ['health']
   * );
   * // Result: [{key: '', value: ''}, {key: 'tech', value: 'Technology'}, {key: 'finance', value: 'Finance'}]
   */
  public static createValueKeyHtmlSelectOptionsFromObject(propertyName: string, values: {
      [keySN: string | number]: any
    },
    addEmpty: boolean, excludeKeys: string[] = []): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [];
    addEmpty && valueKeyHtmlSelectOptions.push(new ValueKeyHtmlSelectOptions('', ''));
    Object.keys(values).filter(value => excludeKeys.indexOf(value) < 0).forEach(k =>
      valueKeyHtmlSelectOptions.push(new ValueKeyHtmlSelectOptions(k, values[k][propertyName])));
    return valueKeyHtmlSelectOptions;
  }

  /**
   * Translates existing ValueKeyHtmlSelectOptions values to current locale.
   * Takes existing options and creates new translated versions, preserving keys but translating values.
   * Automatically sorts results alphabetically by translated value.
   *
   * @param translateService Angular TranslateService for value translation
   * @param hSelOpt Existing array of ValueKeyHtmlSelectOptions to translate
   * @param addEmpty Whether to include empty option at the beginning (default: true)
   * @returns New array of ValueKeyHtmlSelectOptions with translated values, sorted alphabetically
   *
   * @example
   * // Translate existing options when language changes
   * const existingOptions = [{key: 'ACTIVE', value: 'ACTIVE'}, {key: 'INACTIVE', value: 'INACTIVE'}];
   * const translatedOptions = SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(
   *   translateService, existingOptions, true
   * );
   * // Result: [{key: '', value: ''}, {key: 'ACTIVE', value: 'Active'}, {key: 'INACTIVE', value: 'Inactive'}] (sorted)
   */
  public static translateExistingValueKeyHtmlSelectOptions(translateService: TranslateService,
    hSelOpt: ValueKeyHtmlSelectOptions[],
    addEmpty = true): ValueKeyHtmlSelectOptions[] {
    const newValueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = addEmpty ? [new ValueKeyHtmlSelectOptions('', '')] :
      [];
    hSelOpt.forEach(h => newValueKeyHtmlSelectOptions.push(
      this.translateValueKeyHtmlSelectOptions(translateService, h.key, h.value)));
    return newValueKeyHtmlSelectOptions.sort((a, b) => a.value.toLowerCase() < b.value.toLowerCase() ? -1 :
      a.value.toLowerCase() > b.value.toLowerCase() ? 1 : 0);
  }

  /**
   * Creates translated select options from array where key equals value.
   * Converts array values to options where each value serves as both key and display text,
   * then translates the display text. Always includes empty option.
   *
   * @param translateService Angular TranslateService for value translation
   * @param arr Array of values to convert to translated options
   * @returns Array of ValueKeyHtmlSelectOptions with translated values, starting with empty option
   *
   * @example
   * // Create translated language options
   * const languages = ['EN', 'DE', 'FR'];
   * const languageOptions = SelectOptionsHelper.translateArrayKeyEqualValue(
   *   translateService, languages
   * );
   * // Result: [{key: '', value: ''}, {key: 'EN', value: 'English'}, {key: 'DE', value: 'German'}, {key: 'FR', value: 'French'}]
   */
  public static translateArrayKeyEqualValue<T extends number | string>(translateService: TranslateService,
    arr: T[]): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [new ValueKeyHtmlSelectOptions('', '')];
    arr.forEach(value =>
      valueKeyHtmlSelectOptions.push(this.translateValueKeyHtmlSelectOptions(translateService, value, '' + value)));
    return valueKeyHtmlSelectOptions;
  }

  /**
   * Enables or disables specific options in existing ValueKeyHtmlSelectOptions array.
   * Modifies existing options in-place by setting disabled property based on enum values.
   * Used for dynamic option state management based on business rules or permissions.
   *
   * @param valueKeyHtmlSelectOptions Existing array of options to modify
   * @param e Enum object for value lookup
   * @param disabled Array of enum values that should be disabled
   * @returns The same array with disabled properties updated (modified in-place)
   *
   * @example
   * // Disable certain transaction types based on user permissions
   * const options = [...]; // existing transaction type options
   * SelectOptionsHelper.disableEnableExistingHtmlOptionsFromEnum(
   *   options, TransactionType, [TransactionType.ADMIN_ONLY, TransactionType.RESTRICTED]
   * );
   * // Modifies options array, setting disabled: true for specified enum values
   */
  public static disableEnableExistingHtmlOptionsFromEnum(valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[], e: EnumI, disabled: any[]):
    ValueKeyHtmlSelectOptions[] {
    for (const vkhso of valueKeyHtmlSelectOptions) {
      vkhso.disabled = disabled.indexOf(e[vkhso.key]) >= 0;
    }
    return valueKeyHtmlSelectOptions;
  }

  /**
   * Creates PrimeNG SelectItem array from enum with translation.
   * Converts enum values to PrimeNG-compatible SelectItem format with asynchronous translation.
   * Modifies the provided items array in-place, adding translated options as they become available.
   *
   * @param translateService Angular TranslateService for label translation
   * @param e Enum object implementing EnumI interface
   * @param items Target array to populate with SelectItem objects (modified in-place)
   *
   * @example
   * // Create PrimeNG dropdown items for status enum
   * const statusItems: SelectItem[] = [];
   * SelectOptionsHelper.createSelectItemForEnum(translateService, Status, statusItems);
   * // statusItems will be populated asynchronously as translations complete
   * // Result: [{value: 'ACTIVE', label: 'Active'}, {value: 'INACTIVE', label: 'Inactive'}]
   */
  public static createSelectItemForEnum(translateService: TranslateService, e: EnumI, items: SelectItem[]): void {
    for (const n in e) {
      if (typeof e[n] === 'number') {
        const stringType: string = e[e[n]];
        translateService.get(stringType).subscribe(result => items.push({value: stringType, label: result}));
      }
    }
  }

  /**
   * Internal helper for creating enum-based options with various configurations.
   * Handles the core logic for enum processing including filtering, translation, and state management.
   * Supports both inclusion/exclusion filtering and option disabling.
   *
   * @param valueKeyHtmlSelectOptions Target array to populate (may already contain empty option)
   * @param translateService Angular TranslateService for value translation
   * @param e Enum object implementing EnumI interface
   * @param allowedEnums Optional array of enum values for filtering
   * @param deny If true, allowedEnums are excluded; if false, only allowedEnums are included
   * @param disabledEnums Optional array of enum values to mark as disabled
   * @returns The populated and sorted array of ValueKeyHtmlSelectOptions
   */
  private static createHtmlOptionsFromEnumWithEmptyOrNot(valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[],
    translateService: TranslateService, e: EnumI, allowedEnums?: any[],
    deny?: boolean, disabledEnums?: any[]): ValueKeyHtmlSelectOptions[] {
    let typeAllowedEnums = null;
    if (allowedEnums && allowedEnums.length > 0) {
      typeAllowedEnums = typeof allowedEnums[0];
    }
    for (const n in e) {
      if (typeof e[n] === 'number') {
        const stringType: string = e[e[n]];
        if (this.checkAllowEnum(typeAllowedEnums, e, stringType, allowedEnums, deny)) {
          const valueKeyHtmlSelectOption = this.translateValueKeyHtmlSelectOptions(translateService, stringType, stringType);
          if (disabledEnums && disabledEnums.indexOf(e[stringType]) >= 0) {
            valueKeyHtmlSelectOption.disabled = true;
          }
          valueKeyHtmlSelectOptions.push(valueKeyHtmlSelectOption);
        }
      }
    }
    valueKeyHtmlSelectOptions.sort((a, b) =>
      (a.value > b.value) ? 1 : ((b.value > a.value) ? -1 : 0));
    return valueKeyHtmlSelectOptions;
  }

  /**
   * Internal helper for checking if enum value should be included based on filter criteria.
   * Handles the logic for allow/deny filtering with support for both numeric and string enum types.
   *
   * @param typeAllowedEnums Type of the first element in allowedEnums array ('number' or other)
   * @param e Enum object for value lookup
   * @param stringType String representation of the enum value
   * @param allowedEnums Array of enum values for filtering
   * @param deny If true, allowedEnums are excluded; if false, only allowedEnums are included
   * @returns True if the enum value should be included in the options
   */
  private static checkAllowEnum(typeAllowedEnums: string, e: EnumI, stringType: string, allowedEnums?: any[], deny?: boolean,): boolean {
    if (!typeAllowedEnums || typeAllowedEnums === 'number') {
      return !allowedEnums || (allowedEnums.indexOf(e[stringType]) >= 0 && !deny
        || allowedEnums.indexOf(e[stringType]) < 0 && deny);
    } else {
      return allowedEnums.indexOf(stringType) >= 0 && !deny
        || allowedEnums.indexOf(stringType) < 0 && deny;
    }
  }


  // Primeng SelectItem[]
  /////////////////////////////////////////////////////////////

  /**
   * Internal helper for creating translated ValueKeyHtmlSelectOptions.
   * Creates option object and sets up asynchronous translation for non-empty values.
   * The translation updates the value property when the translation completes.
   *
   * @param translateService Angular TranslateService for value translation
   * @param key Key value for the option (stored value)
   * @param value Display value (translation key or literal text)
   * @returns ValueKeyHtmlSelectOptions with async translation setup
   */
  private static translateValueKeyHtmlSelectOptions(translateService: TranslateService, key: string | number,
    value: string): ValueKeyHtmlSelectOptions {
    const valueKeyHtmlSelectOptions = new ValueKeyHtmlSelectOptions(key, value);
    value !== '' && translateService.get(value).subscribe(translated => valueKeyHtmlSelectOptions.value = translated);
    return valueKeyHtmlSelectOptions;
  }
}
