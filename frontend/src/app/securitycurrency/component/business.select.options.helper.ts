import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {Assetclass} from '../../entities/assetclass';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {combineLatest} from 'rxjs';
import {Helper} from '../../lib/helper/helper';
import {AppHelper, Comparison} from '../../lib/helper/app.helper';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {Security} from '../../entities/security';
import moment from 'moment';

export class BusinessSelectOptionsHelper {
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

}
