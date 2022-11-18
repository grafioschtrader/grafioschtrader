import {Security} from '../../entities/security';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {GlobalparameterService} from '../service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {Assetclass} from '../../entities/assetclass';
import {combineLatest} from 'rxjs';
import {Helper} from '../../helper/helper';
import {AppHelper, Comparison} from './app.helper';
import {EnumI} from './enumI';
import {SelectItem} from 'primeng/api';
import * as moment from 'moment';

export class SelectOptionsHelper {

  /**
   * Create the options for a html select, where the options contain the name of security and its currency. The key
   * is the id of security.
   *
   * @param securities Securities which are transformed to options
   * @param fieldConfig The input field which is shown
   */
  public static securityCreateValueKeyHtmlSelectOptions(securities: Security[], fieldConfig: FieldConfig) {
    fieldConfig.valueKeyHtmlOptions = [];
    securities.forEach(security => {
      fieldConfig.valueKeyHtmlOptions.push(
        new ValueKeyHtmlSelectOptions(security.idSecuritycurrency, security.name + ' / ' + security.currency));
    });
  }

  public static securitiesEnableDisableOptionsByActiveDate(securities: Security[],
                                                           fieldConfig: FieldConfig, activeDateNol: number) {
    const activeDate: string = moment(activeDateNol).format('YYYYMMDD');
    fieldConfig.valueKeyHtmlOptions.forEach(vkho => {
      const security = securities.find(s => s.idSecuritycurrency === vkho.key);
      vkho.disabled = !(activeDate >= security.activeFromDate && activeDate <= security.activeToDate);
    });
  }

  public static assetclassCreateValueKeyHtmlSelectOptions(gps: GlobalparameterService,
    translateService: TranslateService,
    assetClasses: Assetclass[]): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [new ValueKeyHtmlSelectOptions('', '')];
    assetClasses.forEach(assetclass => {
      // const observableTranslateCategoryType = translateService.get(<string>assetclass.categoryType);
      // const observableTranslateSpecialInvestment = translateService.get(<string>assetclass.specialInvestmentInstrument);
      this.translateAssetclass(translateService, gps.getUserLang(), assetclass, valueKeyHtmlSelectOptions);
    });
    return valueKeyHtmlSelectOptions;
  }

  public static translateAssetclass(translateService: TranslateService, language: string, assetclass: Assetclass,
    valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[]): ValueKeyHtmlSelectOptions {
    const observableTranslateCategoryType = translateService.get(<string>assetclass.categoryType);
    const observableTranslateSpecialInvestment = translateService.get(<string>assetclass.specialInvestmentInstrument);
    const valueKeyHtmlSelectOption = new ValueKeyHtmlSelectOptions(assetclass.idAssetClass, null);
    combineLatest([observableTranslateCategoryType, observableTranslateSpecialInvestment]).subscribe(translated => {
      let subCategory: string = Helper.getValueByPath(assetclass, 'subCategoryNLS.map.' + language);
      subCategory = (subCategory) ? subCategory + ' / ' : '';
      valueKeyHtmlSelectOption.value = `${translated[0]} / ${subCategory}${translated[1]}`;
      if (valueKeyHtmlSelectOptions) {
        const indexPos = AppHelper.binarySearch(valueKeyHtmlSelectOptions, valueKeyHtmlSelectOption.value, (option, value) =>
          option.value === value ? Comparison.EQ : option.value > value ? Comparison.GT : Comparison.LT);
        valueKeyHtmlSelectOptions.splice(Math.abs(indexPos), 0, valueKeyHtmlSelectOption);
      }
    });
    return valueKeyHtmlSelectOption;
  }

  public static createHtmlOptionsFromStringArray(keysAndValues: string[], uppecaseValue = false): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [];
    keysAndValues.forEach(keyAndValue => valueKeyHtmlSelectOptions.push(
      new ValueKeyHtmlSelectOptions(keyAndValue, uppecaseValue ? keyAndValue.toUpperCase() : keyAndValue)));
    return valueKeyHtmlSelectOptions;
  }

  public static createHtmlOptionsFromEnumAddEmpty(translateService: TranslateService, e: EnumI, allowedEnums?: any[],
    deny?: boolean): ValueKeyHtmlSelectOptions[] {
    const transactionHtmlOptions: ValueKeyHtmlSelectOptions[] = [new ValueKeyHtmlSelectOptions('', '')];
    return this.createHtmlOptionsFromEnumWithEmptyOrNot(transactionHtmlOptions, translateService, e, allowedEnums, deny);
  }

  public static createHtmlOptionsFromEnum(translateService: TranslateService, e: EnumI, allowedEnums?: any[],
    deny?: boolean): ValueKeyHtmlSelectOptions[] {
    const transactionHtmlOptions: ValueKeyHtmlSelectOptions[] = [];
    return this.createHtmlOptionsFromEnumWithEmptyOrNot(transactionHtmlOptions, translateService, e, allowedEnums, deny);
  }

  public static createHtmlOptionsFromEnumDisabled(translateService: TranslateService, e: EnumI,
    disabledEnums?: any[]): ValueKeyHtmlSelectOptions[] {
    const transactionHtmlOptions: ValueKeyHtmlSelectOptions[] = [];
    return this.createHtmlOptionsFromEnumWithEmptyOrNot(transactionHtmlOptions, translateService, e, null, false,
      disabledEnums);
  }

  public static createValueKeyHtmlSelectOptionsFromArray(key: string, propertyName: string, values: any[],
    addEmpty: boolean): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [];
    addEmpty && valueKeyHtmlSelectOptions.push(new ValueKeyHtmlSelectOptions('', ''));
    values.forEach(element => {
      valueKeyHtmlSelectOptions.push(new ValueKeyHtmlSelectOptions(element[key], element[propertyName]));
    });
    return valueKeyHtmlSelectOptions;
  }

  public static createValueKeyHtmlSelectOptionsFromObject(propertyName: string, values: { [keySN: string | number]: any },
    addEmpty: boolean, excludeKeys: string[] = []): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [];
    addEmpty && valueKeyHtmlSelectOptions.push(new ValueKeyHtmlSelectOptions('', ''));
    Object.keys(values).filter(value => excludeKeys.indexOf(value) < 0).forEach(k => valueKeyHtmlSelectOptions.push(new ValueKeyHtmlSelectOptions(k, values[k][propertyName])));
    return valueKeyHtmlSelectOptions;
  }

  /**
   * Translate a existing vaule of ValueKeyHtmlSelectOptions.
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


  public static translateArrayKeyEqualValue<T extends number | string>(translateService: TranslateService,
    arr: T[]): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [new ValueKeyHtmlSelectOptions('', '')];
    arr.forEach(value =>
      valueKeyHtmlSelectOptions.push(this.translateValueKeyHtmlSelectOptions(translateService, value, '' + value)));
    return valueKeyHtmlSelectOptions;
  }

  public static disableEnableExistingHtmlOptionsFromEnum(valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[], e: EnumI, disabled: any[]):
    ValueKeyHtmlSelectOptions[] {
    for (const vkhso of valueKeyHtmlSelectOptions) {
      vkhso.disabled = disabled.indexOf(e[vkhso.key]) >= 0;
    }
    return valueKeyHtmlSelectOptions;
  }

  public static createSelectItemForEnum(translateService: TranslateService, e: EnumI, items: SelectItem[]): void {
    for (const n in e) {
      if (typeof e[n] === 'number') {
        const stringType: string = e[e[n]];
        translateService.get(stringType).subscribe(result => items.push({value: stringType, label: result}));
      }
    }
  }

  private static createHtmlOptionsFromEnumWithEmptyOrNot(valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[],
    translateService: TranslateService, e: EnumI, allowedEnums?: any[],
    deny?: boolean, disabledEnums?: any[]): ValueKeyHtmlSelectOptions[] {
    for (const n in e) {
      if (typeof e[n] === 'number') {
        const stringType: string = e[e[n]];
        if (!allowedEnums || (allowedEnums.indexOf(e[stringType]) >= 0 && !deny
          || allowedEnums.indexOf(e[stringType]) < 0 && deny)) {
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

  // Primeng SelectItem[]
  /////////////////////////////////////////////////////////////

  private static translateValueKeyHtmlSelectOptions(translateService: TranslateService, key: string | number,
    value: string): ValueKeyHtmlSelectOptions {
    const valueKeyHtmlSelectOptions = new ValueKeyHtmlSelectOptions(key, value);
    value !== '' && translateService.get(value).subscribe(translated => valueKeyHtmlSelectOptions.value = translated);

    return valueKeyHtmlSelectOptions;
  }
}
