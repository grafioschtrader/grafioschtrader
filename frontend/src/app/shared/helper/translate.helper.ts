import {TranslateService} from '@ngx-translate/core';
import {FieldFormGroup, FormGroupDefinition} from '../../dynamic-form/models/form.group.definition';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {MenuItem} from 'primeng/api';
import {ColumnConfig, TranslateValue} from '../datashowbase/column.config';
import {Helper} from '../../helper/helper';
import {AppSettings} from '../app.settings';

/**
 * Utility class providing static helper methods for handling internationalization (i18n) and translation operations.
 * This class supports translation of various UI components including form fields, menu items, column configurations,
 * and dynamic content values throughout the application.
 *
 * The class integrates with Angular's TranslateService to provide comprehensive translation support for:
 * - Form field configurations and error messages
 * - Menu items with dynamic parameters and tooltips
 * - Table column headers and data values
 * - Complex translation scenarios with nested parameters
 *
 * @abstract This class cannot be instantiated and only provides static utility methods
 */
export class TranslateHelper {

  /**
   * Converts a camelCase string to UPPER_CASE_WITH_UNDERSCORES format.
   * This is commonly used for generating translation keys from property names.
   *
   * @param camelCaseStr The camelCase string to convert
   * @returns The converted string in UPPER_CASE_WITH_UNDERSCORES format
   *
   * @example
   * ```typescript
   * TranslateHelper.camelToUnderscoreCase('firstName'); // Returns 'FIRST_NAME'
   * TranslateHelper.camelToUnderscoreCase('portfolioName'); // Returns 'PORTFOLIO_NAME'
   * ```
   */
  public static camelToUnderscoreCase(camelCaseStr: string): string {
    return camelCaseStr.replace(/(.)([A-Z][a-z]+)/, '$1_$2')
      .replace(/([a-z0-9])([A-Z])/, '$1_$2').toUpperCase();
  }

  /**
   * Processes form field configurations and creates a flattened object map for easy field access.
   * This method extracts all FieldConfig objects from potentially nested FormGroupDefinition structures,
   * translates error messages, and returns a convenient lookup object.
   *
   * @param translateService Angular's translation service for handling error message translations
   * @param fieldFormGroup Array containing mix of FieldConfig and FormGroupDefinition objects
   * @returns Object map where keys are field names and values are FieldConfig objects
   *
   * @example
   * ```typescript
   * const configObject = TranslateHelper.prepareFieldsAndErrors(translateService, formConfig);
   * // Access specific field: configObject.userName.formControl.setValue('value');
   * // Access field element: configObject.email.elementRef.nativeElement.focus();
   * ```
   */
  public static prepareFieldsAndErrors(translateService: TranslateService,
    fieldFormGroup: FieldFormGroup[]): { [name: string]: FieldConfig } {
    const fieldConfigs = FormHelper.flattenConfigMap(fieldFormGroup);
    const flattenFieldConfigObject: {
      [name: string]: FieldConfig
    } = Object.assign({}, ...fieldConfigs.map(d => ({[d.field]: d})),
      ...FormHelper.getFormGroupDefinition(fieldFormGroup).map(d => ({[d.formGroupName]: d})));

    this.translateMessageErrors(translateService, fieldFormGroup);
    return flattenFieldConfigObject;
  }

  /**
   * Recursively translates error messages for all fields within form configurations.
   * Processes both individual FieldConfig objects and nested FormGroupDefinition structures.
   * Also handles translation of help text labels that match the uppercase pattern.
   *
   * @param translateService Angular's translation service
   * @param fieldConfigs Array of form field configurations to process
   *
   * @example
   * ```typescript
   * // Automatically called by prepareFieldsAndErrors, but can be used standalone
   * TranslateHelper.translateMessageErrors(translateService, formConfigs);
   * ```
   */
  public static translateMessageErrors(translateService: TranslateService, fieldConfigs: FieldFormGroup[]) {
    fieldConfigs.forEach((fieldConfig: FieldFormGroup) => {
      if (!FormHelper.isFieldConfig(fieldConfig)) {
        fieldConfig.errors && this.translateMessageError(translateService, <FieldConfig>fieldConfig);
        this.translateMessageErrors(translateService, (<FormGroupDefinition>fieldConfig).fieldConfig);
      } else {
        fieldConfigs.filter(fc => (<FieldConfig>fc).labelHelpText &&
          (/^[A-Z,_]*$/).test((<FieldConfig>fc).labelHelpText)).forEach(fc =>
          this.translateLabelHelpText(translateService, <FieldConfig>fc));

        fieldConfigs.filter(() => (<FieldConfig>fieldConfig).errors).forEach(() =>
          this.translateMessageError(translateService, <FieldConfig>fieldConfig));
      }
    });
  }

  /**
   * Translates the help text label for a specific field configuration.
   * The translation is applied asynchronously and updates the field's labelHelpText property.
   *
   * @param translateService Angular's translation service
   * @param fieldConfig The field configuration whose help text should be translated
   *
   * @example
   * ```typescript
   * TranslateHelper.translateLabelHelpText(translateService, fieldConfig);
   * // fieldConfig.labelHelpText will be updated with translated text
   * ```
   */
  public static translateLabelHelpText(translateService: TranslateService, fieldConfig: FieldConfig) {
    translateService.get(fieldConfig.labelHelpText).subscribe(transText => fieldConfig.labelHelpText
      = transText);
  }

  /**
   * Translates error messages for a specific field configuration.
   * Processes all error objects that don't already have translated text, supporting parameterized translations.
   *
   * @param translateService Angular's translation service
   * @param fieldConfig The field configuration whose errors should be translated
   *
   * @example
   * ```typescript
   * TranslateHelper.translateMessageError(translateService, fieldConfig);
   * // All error.text properties will be populated with translated messages
   * ```
   */
  public static translateMessageError(translateService: TranslateService, fieldConfig: FieldConfig) {
    fieldConfig.errors.filter(e => !e.text).forEach(error => translateService.get(error.keyi18n, {
      param1: error.param1,
      param2: error.param2
    })
      .subscribe(text => error.text = text));
  }


  /**
   * Translates menu items recursively, supporting complex label structures and tooltips.
   * Handles special menu formatting including:
   * - Labels starting with "_" (generates tooltips)
   * - Parameterized translations with "|" separator
   * - Dialog menu suffixes
   * - Nested menu structures
   *
   * @param menuItems Array of menu items to translate (processed recursively)
   * @param translateService Angular's translation service
   * @param translateParam Whether to translate the parameter part of split labels (default: true)
   *
   * @example
   * ```typescript
   * const menuItems = [
   *   { label: '_CREATE|USER...' }, // Creates tooltip and translates "CREATE" with "USER" parameter
   *   { label: 'SIMPLE_LABEL' }      // Simple translation
   * ];
   * TranslateHelper.translateMenuItems(menuItems, translateService);
   * ```
   */
  public static translateMenuItems(menuItems: MenuItem[], translateService: TranslateService, translateParam = true) {
    menuItems.forEach((menuItem: MenuItem) => {
      if (menuItem.label) {
        if (menuItem.label.startsWith('_')) {
          menuItem.label = menuItem.label.slice(1);
          menuItem.tooltipOptions = {tooltipLabel: TranslateHelper.cutOffDialogDots(menuItem.label) + '_TITLE'};
        }
        TranslateHelper.translateMenuItem(menuItem, 'label', translateService, translateParam);
        if (menuItem.tooltipOptions) {
          TranslateHelper.translateMenuItem(menuItem.tooltipOptions, 'tooltipLabel', translateService, translateParam);
        }
        if (menuItem.items) {
          // For child menu
          this.translateMenuItems(<MenuItem[]>menuItem.items, translateService, translateParam);
        }
      }
    });
  }

  /**
   * Creates translated value stores for table columns that require translation.
   * Adds a new field (with $ suffix) to each data object containing the translated value,
   * enabling proper sorting and display of translated content in tables.
   *
   * @param translateService Angular's translation service
   * @param fields Array of column configurations, only those with translateValues will be processed
   * @param data Array of data objects to be enhanced with translated values
   *
   * @example
   * ```typescript
   * const data = [{ status: 'ACTIVE' }, { status: 'INACTIVE' }];
   * TranslateHelper.createTranslatedValueStore(translateService, columnConfigs, data);
   * // Results in: [{ status: 'ACTIVE', status$: 'Active' }, { status: 'INACTIVE', status$: 'Inactive' }]
   * ```
   */
  public static createTranslatedValueStore(translateService: TranslateService, fields: ColumnConfig[], data: any[]): void {
    const columnConfigs = fields.filter(columnConfig => !!columnConfig.translateValues);
    if (columnConfigs.length > 0) {
      data.forEach(dataValue => TranslateHelper.createTranslatedValueStoreForTranslation(translateService, columnConfigs, dataValue));
      columnConfigs.forEach(columnConfig => columnConfig.fieldTranslated = columnConfig.field + AppSettings.FIELD_SUFFIX);
    }
  }

  /**
   * Creates translated values for a single data object based on column configurations.
   * Handles different translation modes including single values, uppercase conversion, and array-to-comma-separated conversion.
   * Uses caching (translatedValueMap) to avoid redundant translations.
   *
   * @param translateService Angular's translation service
   * @param fields Array of column configurations that require translation
   * @param dataObject The data object to enhance with translated values
   *
   * @example
   * ```typescript
   * const dataObject = { categories: ['CAT1', 'CAT2'], status: 'PENDING' };
   * TranslateHelper.createTranslatedValueStoreForTranslation(translateService, columnConfigs, dataObject);
   * // Adds translated fields: categories$: "Category 1, Category 2", status$: "Pending"
   * ```
   */
  public static createTranslatedValueStoreForTranslation(translateService: TranslateService,
    fields: ColumnConfig[], dataObject: any): void {
    fields.forEach(columnConfig => {
      if (!columnConfig.translatedValueMap) {
        columnConfig.translatedValueMap = {};
      }
      let value = Helper.getValueByPath(dataObject, columnConfig.field);
      if (columnConfig.translateValues === TranslateValue.UPPER_CASE_ARRAY_TO_COMMA_SEPERATED) {
        this.translateArrayIntoCommaSeparatorString(translateService, columnConfig, value, dataObject);
      } else {
        this.translateSingleValue(translateService, columnConfig, value, dataObject);
      }
    });
  }

  /**
   * Translates a single value and caches the result for future use.
   * Supports uppercase conversion based on TranslateValue enum settings.
   *
   * @param translateService Angular's translation service
   * @param columnConfig Column configuration containing translation settings
   * @param value The value to be translated
   * @param dataObject The data object to update with the translated value
   *
   * @private Internal method used by createTranslatedValueStoreForTranslation
   */
  private static translateSingleValue(translateService: TranslateService,
    columnConfig: ColumnConfig, value: any, dataObject: any): void {
    if (columnConfig.translatedValueMap.hasOwnProperty(value)) {
      // Expand data with a field and existing translation
      Helper.setValueByPath(dataObject, columnConfig.field + AppSettings.FIELD_SUFFIX, columnConfig.translatedValueMap[value]);
    } else {
      if (value) {
        // Add value and translation
        value = columnConfig.translateValues === TranslateValue.UPPER_CASE ? value.toUpperCase() : value;
        translateService.get(value).subscribe(translated => {
          columnConfig.translatedValueMap[value] = translated;
          // Expand data with a field that contains the value
          Helper.setValueByPath(dataObject, columnConfig.field + AppSettings.FIELD_SUFFIX, translated);
        });
      }
    }
  }

  /**
   * Translates an array of values into a comma-separated string of translated values.
   * Each array element is translated individually and then joined with commas.
   *
   * @param translateService Angular's translation service
   * @param columnConfig Column configuration containing translation settings
   * @param values Array of values to be translated and joined
   * @param dataObject The data object to update with the comma-separated translated string
   *
   * @private Internal method used by createTranslatedValueStoreForTranslation
   */
  private static translateArrayIntoCommaSeparatorString(translateService: TranslateService,
    columnConfig: ColumnConfig, values: Array<any>, dataObject: any): void {
    const commaSpace = ', ';
    let commaSeparatorValue = '';
    values.forEach(value => {
      if (columnConfig.translatedValueMap.hasOwnProperty(value)) {
        commaSeparatorValue = commaSeparatorValue + (commaSeparatorValue.length === 0 ? '' : commaSpace) + columnConfig.translatedValueMap[value];
      } else {
        value = columnConfig.translateValues === TranslateValue.UPPER_CASE ? value.toUpperCase() : value;
        translateService.get(value).subscribe(translated => {
          columnConfig.translatedValueMap[value] = translated;
          commaSeparatorValue = commaSeparatorValue + (commaSeparatorValue.length === 0 ? '' : commaSpace) + translated;
        });
      }
    });
    Helper.setValueByPath(dataObject, columnConfig.field + AppSettings.FIELD_SUFFIX, commaSeparatorValue);
  }

  /**
   * Translates a specific property of a menu item, handling complex label structures.
   * Supports parameterized translations where labels contain "|" separators for dynamic content.
   *
   * @param menuItem The menu item object to translate
   * @param targetProperty The property name to translate ('label', 'tooltipLabel', etc.)
   * @param translateService Angular's translation service
   * @param translateParam Whether to translate parameter parts of split labels
   *
   * @private Internal method used by translateMenuItems
   *
   * @example
   * ```typescript
   * // For label "CREATE|USER", translates "CREATE" with "USER" as parameter
   * // Results in something like "Create User" depending on translation template
   * ```
   */
  private static translateMenuItem(menuItem: MenuItem, targetProperty: string, translateService: TranslateService, translateParam: boolean): void {
    if (menuItem[targetProperty] && menuItem[targetProperty].toUpperCase() === menuItem[targetProperty]) {
      // Translate only once
      const dialogMenuItem = menuItem[targetProperty].endsWith(AppSettings.DIALOG_MENU_SUFFIX);
      if (dialogMenuItem) {
        menuItem[targetProperty] = TranslateHelper.cutOffDialogDots(menuItem[targetProperty]);
      }
      if (menuItem[targetProperty].indexOf('|') >= 0) {
        const labelWord: string[] = menuItem[targetProperty].split('|');

        if (translateParam) {
          translateService.get(labelWord[1]).subscribe(param =>
            translateService.get(labelWord[0], {i18nRecord: param}).subscribe(message =>
              menuItem[targetProperty] = message + (dialogMenuItem ? AppSettings.DIALOG_MENU_SUFFIX : ''))
          );
        } else {
          translateService.get(labelWord[0], {i18nRecord: labelWord[1]}).subscribe(
            message => menuItem[targetProperty] = message);
        }
      } else {
        translateService.get(menuItem[targetProperty]).subscribe(translated => menuItem[targetProperty] =
          translated + (dialogMenuItem ? AppSettings.DIALOG_MENU_SUFFIX : ''));
      }
    }
  }

  /**
   * Removes dialog menu suffix from a label string if present.
   * Used for cleaning up menu labels before generating related keys (like tooltip keys).
   *
   * @param label The label string to process
   * @returns The label with dialog suffix removed if it was present
   *
   * @private Internal utility method
   *
   * @example
   * ```typescript
   * TranslateHelper.cutOffDialogDots('CREATE_USER...'); // Returns 'CREATE_USER'
   * TranslateHelper.cutOffDialogDots('SIMPLE_LABEL');   // Returns 'SIMPLE_LABEL'
   * ```
   */
  private static cutOffDialogDots(label: string): string {
    return label.endsWith(AppSettings.DIALOG_MENU_SUFFIX) ? label.slice(0, -AppSettings.DIALOG_MENU_SUFFIX.length) : label;
  }

}
