import {TranslateService} from '@ngx-translate/core';
import {FieldFormGroup, FormGroupDefinition} from '../../dynamic-form/models/form.group.definition';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {MenuItem} from 'primeng/api';
import {ColumnConfig, TranslateValue} from '../datashowbase/column.config';
import {Helper} from '../../helper/helper';
import {AppSettings} from '../app.settings';

export class TranslateHelper {

  public static camelToUnderscoreCase(camelCaseStr: string): string {
    return camelCaseStr.replace(/(.)([A-Z][a-z]+)/, '$1_$2')
      .replace(/([a-z0-9])([A-Z])/, '$1_$2').toUpperCase();
  }

  public static prepareFieldsAndErrors(translateService: TranslateService,
                                       fieldFormGroup: FieldFormGroup[]): { [name: string]: FieldConfig } {
    const fieldConfigs = FormHelper.flattenConfigMap(fieldFormGroup);
    const flattenFieldConfigObject: { [name: string]: FieldConfig } = Object.assign({}, ...fieldConfigs.map(d => ({[d.field]: d})),
      ...FormHelper.getFormGroupDefinition(fieldFormGroup).map(d => ({[d.formGroupName]: d})));

    this.translateMessageErrors(translateService, fieldFormGroup);
    return flattenFieldConfigObject;
  }

  public static translateMessageErrors(translateService: TranslateService, fieldConfigs: FieldFormGroup[]) {
    fieldConfigs.forEach((fieldConfig: FieldFormGroup) => {
      if (!FormHelper.isFieldConfig(fieldConfig)) {
        fieldConfig.errors && this.translateMessageError(translateService, <FieldConfig> fieldConfig);
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

  public static translateLabelHelpText(translateService: TranslateService, fieldConfig: FieldConfig) {
    translateService.get(fieldConfig.labelHelpText).subscribe(transText => fieldConfig.labelHelpText
      = transText);
  }

  public static translateMessageError(translateService: TranslateService, fieldConfig: FieldConfig) {
    fieldConfig.errors.filter(e => !e.text).forEach(error => translateService.get(error.keyi18n, {
      param1: error.param1,
      param2: error.param2
    })
      .subscribe(text => error.text = text));
  }


  /**
   * This can be used to translate the menu entries. If the label begins with a "_", a tooltip is also expected.
   * The key for the tooltip corresponds to the label key plus the sufix "_TOOLTIP".
   * Sometimes the label to be translated consists of two keys. These are separated by a "|".
   * For example, "CREATE|USER" where the translation contains "Create {{i18nRecord}}".
   * USER should also be translated here; this is achieved with the "translateParam" parameter.
   *
   * Translated with DeepL.com (free version)
   * @param menuItems Menus to be translated. These are processed recursively.
   * @param translateService The service that carries out the translation.
   * @param translateParam If true, the label key after "|" is also translated.
   */
  public static translateMenuItems(menuItems: MenuItem[], translateService: TranslateService, translateParam = true) {
    menuItems.forEach((menuItem: MenuItem) => {
      if (menuItem.label) {
        if (menuItem.label.startsWith('_')) {
          menuItem.label = menuItem.label.slice(1);
          menuItem.tooltipOptions = {tooltipLabel: TranslateHelper.cutOffDialogDots(menuItem.label) + '_TITLE'};
        }
        TranslateHelper.translateMenuItem(menuItem, 'label', translateService, translateParam);
        if(menuItem.tooltipOptions) {
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
   * Create a column which contains a translated value. A $ is added to the existing field name, which will bo shown.
   *
   * @param translateService Translation service
   * @param fields All fields also fields which does not need a translation.
   * @param data Which will be translated.
   */
  public static createTranslatedValueStore(translateService: TranslateService, fields: ColumnConfig[], data: any[]): void {
    const columnConfigs = fields.filter(columnConfig => !!columnConfig.translateValues);
    if (columnConfigs.length > 0) {
      data.forEach(dataValue => TranslateHelper.createTranslatedValueStoreForTranslation(translateService, columnConfigs, dataValue));
      columnConfigs.forEach(columnConfig => columnConfig.fieldTranslated = columnConfig.field + AppSettings.FIELD_SUFFIX);
    }
  }

  public static createTranslatedValueStoreForTranslation(translateService: TranslateService,
                                                         fields: ColumnConfig[], dataObject: any): void {
    fields.forEach(columnConfig => {
      if (!columnConfig.translatedValueMap) {
        columnConfig.translatedValueMap = {};
      }
      let value = Helper.getValueByPath(dataObject, columnConfig.field);
      if(columnConfig.translateValues === TranslateValue.UPPER_CASE_ARRAY_TO_COMMA_SEPERATED) {
        this.translateArrayIntoCommaSeparatorString(translateService, columnConfig, value, dataObject);
      } else {
        this.translateSingleValue(translateService, columnConfig, value, dataObject);
      }
    });
  }

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

  private static translateArrayIntoCommaSeparatorString(translateService: TranslateService,
    columnConfig: ColumnConfig, values: Array<any>, dataObject: any): void {
    const commaSpace = ', ';
    let commaSeparatorValue = '';

    values.forEach(value => {
      if (columnConfig.translatedValueMap.hasOwnProperty(value)) {
        commaSeparatorValue = commaSeparatorValue + (commaSeparatorValue.length === 0? '': commaSpace) + columnConfig.translatedValueMap[value];
      } else {
        value = columnConfig.translateValues === TranslateValue.UPPER_CASE ? value.toUpperCase() : value;
        translateService.get(value).subscribe(translated => {
          columnConfig.translatedValueMap[value] = translated;
          commaSeparatorValue = commaSeparatorValue + (commaSeparatorValue.length === 0? '': commaSpace) + translated;
        });
      }
    });
    Helper.setValueByPath(dataObject, columnConfig.field + AppSettings.FIELD_SUFFIX, commaSeparatorValue);
  }

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

  private static cutOffDialogDots(label: string): string {
    return label.endsWith(AppSettings.DIALOG_MENU_SUFFIX) ? label.slice(0, -AppSettings.DIALOG_MENU_SUFFIX.length) : label;
  }

}
