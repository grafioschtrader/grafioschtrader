import {FieldConfig} from '../../dynamic-form/models/field.config';
import {DynamicFieldHelper, VALIDATION_SPECIAL} from '../../shared/helper/dynamic.field.helper';
import {DataType} from '../../dynamic-form/models/data.type';
import {Assetclass} from '../../entities/assetclass';
import {Helper} from '../../helper/helper';
import {Subscription} from 'rxjs';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {Stockexchange} from '../../entities/stockexchange';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {Security} from '../../entities/security';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {FormBase} from '../../shared/edit/form.base';
import {ProposeChangeEntityWithEntity} from '../../entities/proposechange/propose.change.entity.whit.entity';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {ValidatorFn} from '@angular/forms';
import {ErrorMessageRules} from '../../dynamic-form/error/error.message.rules';
import {AppSettings} from '../../shared/app.settings';
import {AppHelper} from '../../shared/helper/app.helper';

/**
 * Some definition of fields are shared between the different edit components of instruments. Those aee
 * static methods. Other members or methods can be shared thru an instance of this class.
 */
export class SecurityEditSupport {
  static readonly FIELD_HELP_CONNECTOR = 'FIELD_HELP_CONNECTOR';

  private static readonly HISTORY_SETTINGS = 'HISTORY_SETTINGS';
  private static readonly INTRA_SETTINGS = 'INTRA_SETTINGS';

  private static denominationValidation: ValidatorFn[];
  private static denominationErrors?: ErrorMessageRules[];

  public readonly ID_CONNECTOR_DIVIDEND = 'idConnectorDividend';

  public connectorDividendConfig: FieldConfig[];
  public connectorSplitConfig: FieldConfig[];

  public hasMarketValue = true;

  private assetClassSubscribe: Subscription;
  private activeFromDateSubscribe: Subscription;
  private securitySubscribe: Subscription;


  constructor(private translateService: TranslateService,
    private gps: GlobalparameterService,
    private callbackValueChanged: CallbackValueChanged) {
  }


  static getSecurityBaseFieldDefinition(securityDerived: SecurityDerived): FieldConfig[] {
    const fc: FieldConfig[] = [];

    fc.push(DynamicFieldHelper.createFieldInputString('name', 'NAME_SECURITY', 80, true,
      {minLength: 2, fieldsetName: 'BASE_DATA'}));
    fc.push(DynamicFieldHelper.createFieldSelectNumber('assetClass', AppSettings.ASSETCLASS.toUpperCase(), true,
      {dataproperty: 'assetClass.idAssetClass', fieldsetName: 'BASE_DATA'}));

    fc.push(DynamicFieldHelper.createFieldCheckbox('isTenantPrivate', 'PRIVATE_SECURITY', {fieldsetName: 'BASE_DATA'}));

    fc.push(DynamicFieldHelper.createFieldSelectNumberHeqF(AppSettings.STOCKEXCHANGE.toLowerCase(), true,
      {dataproperty: 'stockexchange.idStockexchange', fieldsetName: 'BASE_DATA'}));

    if (securityDerived === SecurityDerived.Security) {
      fc.push(DynamicFieldHelper.createFieldInputStringVSHeqF('isin', 12, false,
        [VALIDATION_SPECIAL.ISIN], {fieldsetName: 'BASE_DATA'}));
      fc.push(DynamicFieldHelper.createFieldInputStringHeqF('tickerSymbol', 6, false,
        {fieldsetName: 'BASE_DATA', upperCase: true}));
    }

    fc.push(DynamicFieldHelper.createFieldSelectStringHeqF('currency', true,
      {inputWidth: 10, fieldsetName: 'BASE_DATA'}));

    if (securityDerived === SecurityDerived.Security) {
      const fieldConfig = DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'denomination', true,
        1, 1_000_000, {fieldsetName: 'BASE_DATA'});
      fc.push(fieldConfig);
      this.denominationValidation = fieldConfig.validation;
      this.denominationErrors = fieldConfig.errors;
    }

    fc.push(DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'activeFromDate',
      true, {fieldsetName: 'BASE_DATA', defaultValue: new Date(2000, 1, 3)}));

    fc.push(DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'activeToDate',
      true, {fieldsetName: 'BASE_DATA', defaultValue: new Date(2025, 1, 1)}));

    fc.push(DynamicFieldHelper.createFieldSelectStringHeqF('distributionFrequency', true,
      {fieldsetName: 'BASE_DATA'}));

    fc.push(DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.Numeric, 'leverageFactor', false,
        -9.99, 9.99, {fieldsetName: 'BASE_DATA', defaultValue: 1}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', AppSettings.FID_MAX_LETTERS, false,
        {fieldsetName: 'BASE_DATA'}));
    if (securityDerived === SecurityDerived.Security) {
      fc.push(DynamicFieldHelper.createFieldInputStringVSHeqF('stockexchangeLink', 254, false,
        [VALIDATION_SPECIAL.WEB_URL], {fieldsetName: 'BASE_DATA'}));

      fc.push(DynamicFieldHelper.createFieldInputStringVSHeqF('productLink', 254, false,
        [VALIDATION_SPECIAL.WEB_URL], {fieldsetName: 'BASE_DATA'}));
    }
    return fc;
  }

  static getIntraHistoryFieldDefinition(securityDerived: SecurityDerived): FieldConfig[] {
    const fc: FieldConfig[] = [];
    if (securityDerived === SecurityDerived.Security || securityDerived === SecurityDerived.Currencypair) {
      fc.push(DynamicFieldHelper.createFieldSelectString('idConnectorHistory', 'HISTORY_DATA_PROVIDER', false,
        {fieldsetName: this.HISTORY_SETTINGS}));
      fc.push(DynamicFieldHelper.createFieldInputStringHeqF('urlHistoryExtend', 254, false,
        {fieldsetName: this.HISTORY_SETTINGS, labelHelpText: SecurityEditSupport.FIELD_HELP_CONNECTOR}));
    }
    fc.push(DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.Numeric, 'retryHistoryLoad',
      true, 0, 3, {defaultValue: 0, fieldsetName: this.HISTORY_SETTINGS}));
    if (securityDerived === SecurityDerived.Security || securityDerived === SecurityDerived.Currencypair) {
      fc.push(DynamicFieldHelper.createFieldSelectString('idConnectorIntra', 'INTRA_DATA_PROVIDER', false,
        {fieldsetName: this.INTRA_SETTINGS}));
      fc.push(DynamicFieldHelper.createFieldInputStringHeqF('urlIntraExtend', 254, false,
        {fieldsetName: this.INTRA_SETTINGS, labelHelpText: SecurityEditSupport.FIELD_HELP_CONNECTOR}));
    }
    fc.push(DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.Numeric, 'retryIntraLoad',
      true, 0, 3, {defaultValue: 0, fieldsetName: this.INTRA_SETTINGS}));
    return fc;
  }

  public getDividendFieldDefinition(): FieldConfig[] {
    const fc: FieldConfig[] = [];
    fc.push(DynamicFieldHelper.createFieldSelectStringHeqF(this.ID_CONNECTOR_DIVIDEND, false,
      {fieldsetName: AppSettings.DIVIDEND_SETTINGS}));
    fc.push(DynamicFieldHelper.createFieldInputStringHeqF('urlDividendExtend', 254, false,
      {fieldsetName: AppSettings.DIVIDEND_SETTINGS, labelHelpText: SecurityEditSupport.FIELD_HELP_CONNECTOR}));
    fc.push(DynamicFieldHelper.createFieldSelectStringHeqF('dividendCurrency', false,
      {inputWidth: 10, fieldsetName: AppSettings.DIVIDEND_SETTINGS}));
    fc.push(DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.Numeric, 'retryDividendLoad',
      true, 0, 3, {defaultValue: 0, fieldsetName: AppSettings.DIVIDEND_SETTINGS}));
    return fc;
  }

  public getSplitDefinition(): FieldConfig[] {
    const fc: FieldConfig[] = [];
    fc.push(DynamicFieldHelper.createFieldSelectStringHeqF('idConnectorSplit', false,
      {fieldsetName: AppSettings.SPLIT_SETTINGS}));
    fc.push(DynamicFieldHelper.createFieldInputStringHeqF('urlSplitExtend', 254, false,
      {fieldsetName: AppSettings.SPLIT_SETTINGS, labelHelpText: SecurityEditSupport.FIELD_HELP_CONNECTOR}));
    fc.push(DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.Numeric, 'retrySplitLoad',
      true, 0, 3, {defaultValue: 0, fieldsetName: AppSettings.SPLIT_SETTINGS}));
    return fc;
  }

  assignLoadedValues(configObject: { [name: string]: FieldConfig }, stockexchanges: Stockexchange[],
    vksoCurrency: ValueKeyHtmlSelectOptions[], assetclasses: Assetclass[]): void {
    configObject.stockexchange.referencedDataObject = stockexchanges;
    configObject.stockexchange.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idStockexchange', 'name',
      configObject.stockexchange.referencedDataObject, true);

    configObject.currency.valueKeyHtmlOptions = [new ValueKeyHtmlSelectOptions('', '')].concat(vksoCurrency);
    configObject.dividendCurrency && (configObject.dividendCurrency.valueKeyHtmlOptions =
      [new ValueKeyHtmlSelectOptions('', '')].concat(vksoCurrency));
    configObject.assetClass.referencedDataObject = assetclasses;
    configObject.assetClass.valueKeyHtmlOptions = SelectOptionsHelper.assetclassCreateValueKeyHtmlSelectOptions(
      this.gps, this.translateService, configObject.assetClass.referencedDataObject);

  }

  removeFilterAssetclass(configObject: { [name: string]: FieldConfig }): void {
    configObject.assetClass.valueKeyHtmlOptions = SelectOptionsHelper.assetclassCreateValueKeyHtmlSelectOptions(
      this.gps, this.translateService, configObject.assetClass.referencedDataObject);
  }

  filterAssetclasses(configObject: { [name: string]: FieldConfig }, assetclassType: AssetclassType): void {
    configObject.assetClass.valueKeyHtmlOptions = SelectOptionsHelper.assetclassCreateValueKeyHtmlSelectOptions(
      this.gps, this.translateService,
      (<Assetclass[]>configObject.assetClass.referencedDataObject).filter(assetclass =>
        assetclass.categoryType === AssetclassType[assetclassType]));

  }

  registerValueOnChanged(securityDerived: SecurityDerived, configObject: { [name: string]: FieldConfig }): void {
    this.valueChangedOnAssetClass(securityDerived, configObject);
    this.valueChangedOnaActiveFromDate(configObject);
    !configObject.isTenantPrivate.formControl.disabled && this.valueChangedOnPrivateSecurity(securityDerived, configObject);
  }

  valueChangedOnAssetClass(securityDerived: SecurityDerived, configObject: { [name: string]: FieldConfig }): void {
    this.assetClassSubscribe = configObject.assetClass.formControl.valueChanges.subscribe((idAssetclass: number) => {
      const assetClass: Assetclass = this.disableEnableFieldsOnAssetclass(securityDerived, configObject, idAssetclass);
      this.callbackValueChanged && this.callbackValueChanged.valueChangedOnAssetClassExtend(assetClass);
    });
  }

  disableEnableFieldsOnAssetclass(securityDerived: SecurityDerived, configObject: { [name: string]: FieldConfig },
    idAssetclass: number): Assetclass {
    let assetClass: Assetclass;
    if (idAssetclass) {
      assetClass = Helper.getReferencedDataObject(configObject.assetClass, null);
      this.enableDisableDenomination(configObject, assetClass, this.hasMarketValue);
      this.hideShowSomeFields(securityDerived, configObject, assetClass);
    }
    return assetClass;
  }

  public enableDisableDenominationStockexchangeAssetclass(configObject: { [name: string]: FieldConfig }): void {
    const assetClass = Helper.getReferencedDataObject(configObject.assetClass, null);
    this.enableDisableDenomination(configObject, assetClass, this.hasMarketValue);
  }

  valueChangedOnaActiveFromDate(configObject: { [name: string]: FieldConfig }): void {
    this.activeFromDateSubscribe = configObject.activeFromDate.formControl.valueChanges.subscribe((activeFromDate: Date) => {
      if (activeFromDate) {
        configObject.activeToDate.calendarConfig.minDate = new Date(activeFromDate.getTime() + 86400000);
        if (configObject.activeToDate.formControl.value
          && configObject.activeToDate.formControl.value.getTime() < configObject.activeToDate.calendarConfig.minDate.getTime()) {
          configObject.activeToDate.formControl.setValue(null);
        }
      }
    });
  }

  /**
   * Private security has no ISIN, ticker symbol and can not be short
   */
  valueChangedOnPrivateSecurity(securityDerived: SecurityDerived, configObject: { [name: string]: FieldConfig }): void {
    this.securitySubscribe = configObject.isTenantPrivate.formControl.valueChanges.subscribe(isPrivate => this
      .setPrivatePaper(securityDerived, isPrivate, configObject));
  }

  setPrivatePaper(securityDerived: SecurityDerived, isPrivate: boolean, configObject: { [name: string]: FieldConfig }): void {
    if (isPrivate != null) {
      FormHelper.hideVisibleFieldConfigs(this.isHideIsinAndTicker(configObject),
        this.getIsinTickerLeverageFields(securityDerived, configObject));
    }
  }

  prepareForSave(formBase: FormBase, proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity, existingSecurity: Security,
    dynamicForm: DynamicFormComponent, value: { [name: string]: any }): Security {
    const security: Security = new Security();
    if (existingSecurity) {
      Object.assign(security, existingSecurity);
    }

    AuditHelper.copyProposeChangeEntityToEntityAfterEdit(formBase, security, proposeChangeEntityWithEntity);
    dynamicForm.cleanMaskAndTransferValuesToBusinessObject(security);

    security.idTenantPrivate = value.isTenantPrivate ? this.gps.getIdTenant() : null;
    return security;
  }

  destroy(): void {
    this.assetClassSubscribe && this.assetClassSubscribe.unsubscribe();
    this.activeFromDateSubscribe && this.activeFromDateSubscribe.unsubscribe();
    this.securitySubscribe && this.securitySubscribe.unsubscribe();
  }

  private getIsinTickerLeverageFields(securityDerived: SecurityDerived,
    configObject: { [name: string]: FieldConfig }): FieldConfig[] {
    const fieldConfigs: FieldConfig[] = [];
    if (securityDerived === SecurityDerived.Security) {
      fieldConfigs.push(configObject.tickerSymbol);
      fieldConfigs.push(configObject.isin);
    }
    return fieldConfigs;
  }

  /**
   * Only fixed income and similar can have denomination property.
   */
  private enableDisableDenomination(configObject: { [name: string]: FieldConfig }, assetClass: Assetclass,
    hasMarkedPrice: boolean): void {
    if (configObject.denomination) {
      const hsd = BusinessHelper.hasSecurityDenomination(assetClass, hasMarkedPrice);
      AppHelper.invisibleAndHide(configObject.denomination, !hsd);
      DynamicFieldHelper.resetValidator(configObject.denomination, hsd ? SecurityEditSupport.denominationValidation : null,
        hsd ? SecurityEditSupport.denominationErrors : null);
    }
  }

  private hideShowSomeFields(securityDerived: SecurityDerived, configObject: { [name: string]: FieldConfig },
    assetClass: Assetclass): void {
    FormHelper.hideVisibleFieldConfigs(this.isHideIsinAndTicker(configObject),
      this.getIsinTickerLeverageFields(securityDerived, configObject));
    FormHelper.hideVisibleFieldConfigs(assetClass.specialInvestmentInstrument
      !== SpecialInvestmentInstruments[SpecialInvestmentInstruments.ETF] && assetClass.specialInvestmentInstrument
      !== SpecialInvestmentInstruments[SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT], [configObject.leverageFactor]);
  }

  private isHideIsinAndTicker(configObject: { [name: string]: FieldConfig }): boolean {
    const assetClass: Assetclass = Helper.getReferencedDataObject(configObject.assetClass, null);
    return assetClass && assetClass.specialInvestmentInstrument === SpecialInvestmentInstruments[SpecialInvestmentInstruments.CFD]
      || configObject.isTenantPrivate.formControl.value;
  }
}

export enum SecurityDerived {
  Security,
  Currencypair,
  Derived
}

export interface CallbackValueChanged {
  valueChangedOnAssetClassExtend(assetClass: Assetclass): void;
}
