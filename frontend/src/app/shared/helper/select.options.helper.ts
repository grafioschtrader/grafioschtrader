import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {TranslateService} from '@ngx-translate/core';
import {EnumI} from './enumI';
import {SelectItem} from 'primeng/api';

export class SelectOptionsHelper {

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

  public static createHtmlOptionsFromStringArray(keysAndValues: string[], uppercaseValue = false): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [];
    keysAndValues.forEach(keyAndValue => valueKeyHtmlSelectOptions.push(
      new ValueKeyHtmlSelectOptions(keyAndValue, uppercaseValue ? keyAndValue.toUpperCase() : keyAndValue)));
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
   * Translate an existing value of ValueKeyHtmlSelectOptions.
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

  private static translateValueKeyHtmlSelectOptions(translateService: TranslateService, key: string | number,
    value: string): ValueKeyHtmlSelectOptions {
    const valueKeyHtmlSelectOptions = new ValueKeyHtmlSelectOptions(key, value);
    value !== '' && translateService.get(value).subscribe(translated => valueKeyHtmlSelectOptions.value = translated);

    return valueKeyHtmlSelectOptions;
  }
}
