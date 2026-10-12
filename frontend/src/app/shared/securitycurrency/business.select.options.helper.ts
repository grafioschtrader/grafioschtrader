import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { TranslateService } from '@ngx-translate/core';
import { Assetclass } from '../../entities/assetclass';
import { GroupItem, ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { combineLatest } from 'rxjs';
import { Helper } from '../../lib/helper/helper';
import { AppHelper, Comparison } from '../../lib/helper/app.helper';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { Security } from '../../entities/security';
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
    securities.forEach((security) => {
      fieldConfig.valueKeyHtmlOptions.push(
        new ValueKeyHtmlSelectOptions(security.idSecuritycurrency, security.name + ' / ' + security.currency)
      );
    });
  }

  public static securitiesEnableDisableOptionsByActiveDate(
    securities: Security[],
    fieldConfig: FieldConfig,
    activeDateNol: number
  ) {
    const activeDate: string = moment(activeDateNol).format('YYYYMMDD');
    fieldConfig.valueKeyHtmlOptions.forEach((vkho) => {
      const security = securities.find((s) => s.idSecuritycurrency === vkho.key);
      vkho.disabled = !(activeDate >= security.activeFromDate && activeDate <= security.activeToDate);
    });
  }

  /**
   * Enables/disables cash or security account options depending on whether the account is still active at the given
   * reference date. An account is active when it has no active-until date or that date is not before the reference
   * date. Mirrors {@link securitiesEnableDisableOptionsByActiveDate} so a terminated account cannot be chosen for a
   * transaction (or standing order) dated after its active-until date. A previously selected account stays visible
   * but disabled rather than being removed.
   *
   * @param accounts the cash or security accounts backing the options
   * @param fieldConfig the select field whose options are toggled
   * @param activeDateNol the reference date (e.g. transaction time) as epoch milliseconds
   */
  public static accountsEnableDisableOptionsByActiveDate(
    accounts: { idSecuritycashAccount: number; activeToDate?: string | Date }[],
    fieldConfig: FieldConfig,
    activeDateNol: number
  ): void {
    const activeDate: string = moment(activeDateNol).format('YYYYMMDD');
    fieldConfig.valueKeyHtmlOptions.forEach((vkho) => {
      const account = accounts.find((a) => a.idSecuritycashAccount === vkho.key);
      if (account) {
        vkho.disabled = account.activeToDate != null && activeDate > moment(account.activeToDate).format('YYYYMMDD');
      }
    });
  }

  public static assetclassCreateValueKeyHtmlSelectOptions(
    gps: GlobalparameterService,
    translateService: TranslateService,
    assetClasses: Assetclass[]
  ): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [new ValueKeyHtmlSelectOptions('', '')];
    assetClasses.forEach((assetclass) => {
      // const observableTranslateCategoryType = translateService.get(<string>assetclass.categoryType);
      // const observableTranslateSpecialInvestment = translateService.get(<string>assetclass.specialInvestmentInstrument);
      this.translateAssetclass(translateService, gps.getUserLang(), assetclass, valueKeyHtmlSelectOptions);
    });
    return valueKeyHtmlSelectOptions;
  }

  /**
   * Creates the asset class options for an Optimus dropdown, so its overlay filter can search the composed label
   * "{categoryType} / {subCategory} / {specialInvestmentInstrument}". The first entry is the empty "no selection" row.
   *
   * The labels are translated synchronously here: the texts are loaded before the application bootstraps, so
   * translateService.get() emits at once and the options are complete when they are converted to group items.
   *
   * @param gps Provides the user language for the sub category
   * @param translateService Translates the category type and the special investment instrument
   * @param assetClasses Asset classes which are transformed to options
   * @returns GroupItem array for FieldConfig.groupItem, sorted by label
   */
  public static assetclassCreateGroupItems(
    gps: GlobalparameterService,
    translateService: TranslateService,
    assetClasses: Assetclass[]
  ): GroupItem[] {
    return SelectOptionsHelper.createGroupItemsFromValueKeyHtmlSelectOptions(
      this.assetclassCreateValueKeyHtmlSelectOptions(gps, translateService, assetClasses)
    );
  }

  public static translateAssetclass(
    translateService: TranslateService,
    language: string,
    assetclass: Assetclass,
    valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[]
  ): ValueKeyHtmlSelectOptions {
    const observableTranslateCategoryType = translateService.get(<string>assetclass.categoryType);
    const observableTranslateSpecialInvestment = translateService.get(<string>assetclass.specialInvestmentInstrument);
    const valueKeyHtmlSelectOption = new ValueKeyHtmlSelectOptions(assetclass.idAssetClass, null);
    combineLatest([observableTranslateCategoryType, observableTranslateSpecialInvestment]).subscribe((translated) => {
      let subCategory: string = Helper.getValueByPath(assetclass, 'subCategoryNLS.map.' + language);
      subCategory = subCategory ? subCategory + ' / ' : '';
      valueKeyHtmlSelectOption.value = `${translated[0]} / ${subCategory}${translated[1]}`;
      if (valueKeyHtmlSelectOptions) {
        const indexPos = AppHelper.binarySearch(
          valueKeyHtmlSelectOptions,
          valueKeyHtmlSelectOption.value,
          (option, value) =>
            option.value === value ? Comparison.EQ : option.value > value ? Comparison.GT : Comparison.LT
        );
        valueKeyHtmlSelectOptions.splice(Math.abs(indexPos), 0, valueKeyHtmlSelectOption);
      }
    });
    return valueKeyHtmlSelectOption;
  }
}
