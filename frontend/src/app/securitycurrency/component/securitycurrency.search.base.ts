import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {Directive, Input, OnInit, ViewChild} from '@angular/core';
import {FormConfig} from '../../dynamic-form/models/form.config';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {DataType} from '../../dynamic-form/models/data.type';
import {AppHelper} from '../../shared/helper/app.helper';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {HelpIds} from '../../shared/help/help.ids';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {atLeastOneFieldValidator} from '../../shared/validator/validator';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {Security} from '../../entities/security';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {DynamicFieldHelper, VALIDATION_SPECIAL} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {SupplementCriteria} from '../model/supplement.criteria';
import {AppSettings} from '../../shared/app.settings';
import {
  DataForCurrencySecuritySearch,
  MultipleRequestToOneService
} from '../../shared/service/multiple.request.to.one.service';


/**
 * Base class for a search dialog for security or currency pair.
 */
@Directive()
export abstract class SecuritycurrencySearchBase implements OnInit {
  // Access child components
  @ViewChild(DynamicFormComponent) dynamicFormComponent: DynamicFormComponent;
  @Input() supplementCriteria: SupplementCriteria;

  // Groups are manly for the handling of inputs visibility
  readonly firstGroup = 'G1';
  readonly secondGroup = 'G2';

  config: FieldConfig[] = [];
  configObject: { [name: string]: FieldConfig };
  formConfig: FormConfig;
  private monitor: boolean;

  constructor(protected multiplyAddClose: boolean,
              protected gps: GlobalparameterService,
              protected multipleRequestToOneService: MultipleRequestToOneService,
              public translateService: TranslateService) {
  }

  abstract childClearList(): void;

  abstract childLoadData(securitycurrencySearch: SecuritycurrencySearch): void;

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      3, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputStringVSHeqF('isin', 12, false,
        [VALIDATION_SPECIAL.ISIN], {userDefinedValue: this.firstGroup}),
      DynamicFieldHelper.createFieldCheckboxHeqF('withHoldings',
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldInputStringHeqF('name', 80, false,
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldInputStringHeqF('tickerSymbol', 6, false,
        {upperCase: true, userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateStringShortUS, 'activeDate', false,
        {userDefinedValue: <string>this.secondGroup}),
      DynamicFieldHelper.createFieldTriStateCheckbox('onlyTenantPrivate', 'PRIVATE_SECURITY',
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.Numeric, 'leverageFactor', false, -9.99, 9.99,
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldSelectString('assetclassType', AppSettings.ASSETCLASS.toUpperCase(), false,
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldSelectString('subCategoryNLS', 'SUB_ASSETCLASS', false,
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldSelectString('specialInvestmentInstruments', 'FINANCIAL_INSTRUMENT', false,
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldSelectString('idConnectorHistory', 'HISTORY_DATA_PROVIDER', false,
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldSelectString('idConnectorIntra', 'INTRA_DATA_PROVIDER', false,
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldSelectNumber('idStockexchange', AppSettings.STOCKEXCHANGE.toUpperCase(), false,
        {userDefinedValue: this.secondGroup}), DynamicFieldHelper.createFieldSelectStringHeqF('currency', false,
        {userDefinedValue: this.secondGroup}),
    ];
    if (this.multiplyAddClose) {
      this.config.push(DynamicFieldHelper.createFunctionButton('EXIT', (e) => this.closeSearchDialog(e)));
    }
    this.config.push(DynamicFieldHelper.createSubmitButton('SEARCH'));
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  onShow(event) {
    setTimeout(() => this.initialize());
  }

  submit(value: { [name: string]: any }): void {
    const securitycurrencySearch: SecuritycurrencySearch = new SecuritycurrencySearch();
    this.dynamicFormComponent.cleanMaskAndTransferValuesToBusinessObject(securitycurrencySearch);
    this.childLoadData(securitycurrencySearch);
    this.configObject.submit.disabled = false;
  }

  closeSearchDialog(event): void {
  }

  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_WATCHLIST_SEARCHDIALOG);
  }

  protected initialize(): void {
    this.dynamicFormComponent.form.setValidators(atLeastOneFieldValidator);
    const denyAssetClass = [AssetclassType.CURRENCY_CASH, AssetclassType.CURRENCY_FOREIGN];
    if (this.supplementCriteria && this.supplementCriteria.onlySecurity) {
      denyAssetClass.push(AssetclassType.CURRENCY_PAIR);
    }

    this.configObject.assetclassType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnumAddEmpty(this.translateService,
      AssetclassType, [AssetclassType.CURRENCY_CASH, AssetclassType.CURRENCY_FOREIGN], true);

    this.configObject.specialInvestmentInstruments.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnumAddEmpty(
      this.translateService, SpecialInvestmentInstruments);

    this.childClearList();

    this.multipleRequestToOneService.getDataForCurrencySecuritySearch().subscribe((dfcss: DataForCurrencySecuritySearch) => {
      this.setValueKeyHtmlOptions(this.configObject.currency, dfcss.currencies);
      this.setValueKeyHtmlOptions(this.configObject.subCategoryNLS, dfcss.assetclasses);
      this.setValueKeyHtmlOptions(this.configObject.idConnectorHistory, dfcss.feedConnectorsHistory);
      this.setValueKeyHtmlOptions(this.configObject.idConnectorIntra, dfcss.feedConnectorsIntra);
      this.configObject.idStockexchange.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
        'idStockexchange', 'name',
        dfcss.stockexchanges, true);
      this.dynamicFormComponent.setDefaultValuesAndEnableSubmit();
      this.valueChangedOnForm();
      this.configObject.isin.elementRef.nativeElement.focus();
    });
  }

  private setValueKeyHtmlOptions(fieldConfig: FieldConfig, values: ValueKeyHtmlSelectOptions[]): void {
    fieldConfig.valueKeyHtmlOptions = values;
    fieldConfig.valueKeyHtmlOptions.splice(0, 0, new ValueKeyHtmlSelectOptions('', ''));
  }

  private valueChangedOnForm(): void {
    this.dynamicFormComponent.form.valueChanges.subscribe((security: Security) => {
      if (!this.monitor) {
        this.monitor = true;
        this.dynamicFormComponent.controls.filter(fieldConfig => fieldConfig.userDefinedValue === this.secondGroup)
          .forEach(fieldConfig => AppHelper.invisibleAndHide(fieldConfig, security.isin && security.isin !== ''));

        if (!security.isin || security.isin === '') {
          const secondGroupIsEmpty = this.isSecondGroupEmpty();
          this.dynamicFormComponent.controls.filter(fieldConfig => fieldConfig.userDefinedValue === this.firstGroup)
            .forEach(fieldConfig => AppHelper.invisibleAndHide(fieldConfig, !secondGroupIsEmpty));
          AppHelper.invisibleAndHide(this.configObject.name, this.configObject.name.invisible || this.isExactCurrecny());
          AppHelper.invisibleAndHide(this.configObject.tickerSymbol, this.isCurrency());
          AppHelper.invisibleAndHide(this.configObject.onlyTenantPrivate, this.isCurrency());
          AppHelper.invisibleAndHide(this.configObject.leverageFactor, this.isCurrency());
          AppHelper.invisibleAndHide(this.configObject.activeDate, this.isCurrency());
          AppHelper.invisibleAndHide(this.configObject.idStockexchange, this.isCurrency());
          AppHelper.invisibleAndHide(this.configObject.specialInvestmentInstruments, this.isCurrency());
          AppHelper.invisibleAndHide(this.configObject.subCategoryNLS, this.isCurrency());
        }
        this.monitor = false;
      }
    });
  }

  private isSecondGroupEmpty(): boolean {
    const foundFieldConfig = this.dynamicFormComponent.controls.filter(fieldConfig =>
      fieldConfig.userDefinedValue === this.secondGroup)
      .find(fieldConfig => fieldConfig.dataType === DataType.Boolean && fieldConfig.formControl.value
        || fieldConfig.dataType !== DataType.Boolean
        && fieldConfig.formControl.value && fieldConfig.formControl.value !== '');
    return !foundFieldConfig;
  }

  private isCurrency(): boolean {
    return this.configObject.assetclassType.formControl.value === AssetclassType[AssetclassType.CURRENCY_PAIR];
  }

  private isExactCurrecny(): boolean {
    return this.isCurrency() && !!this.configObject.currency.formControl.value;
  }

}
