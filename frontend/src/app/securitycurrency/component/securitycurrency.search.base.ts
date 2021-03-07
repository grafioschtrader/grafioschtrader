import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import { Input, OnInit, ViewChild, Directive } from '@angular/core';
import {FormConfig} from '../../dynamic-form/models/form.config';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {DataType} from '../../dynamic-form/models/data.type';
import {AppHelper} from '../../shared/helper/app.helper';
import {AssetclassService} from '../../assetclass/service/assetclass.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {HelpIds} from '../../shared/help/help.ids';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {atLeastOneFieldValidator} from '../../shared/validator/validator';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {Security} from '../../entities/security';
import {combineLatest} from 'rxjs';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {DynamicFieldHelper, VALIDATION_SPECIAL} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {SupplementCriteria} from '../model/supplement.criteria';


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


  // securitySearchCriteria: Security;

  constructor(protected multiplyAddClose: boolean,
              protected globalparameterService: GlobalparameterService,
              protected assetclassService: AssetclassService,
              public translateService: TranslateService) {
  }

  abstract childClearList(): void;

  abstract childLoadData(securitycurrencySearch: SecuritycurrencySearch): void;

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.globalparameterService,
      3, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputStringVSHeqF('isin', 12, false,
        [VALIDATION_SPECIAL.ISIN], {userDefinedValue: this.firstGroup}),
      DynamicFieldHelper.createFieldInputStringHeqF('name', 80, false,
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldInputStringHeqF('tickerSymbol', 6, false,
        {upperCase: true, userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateStringShortUS, 'activeDate', false,
        {userDefinedValue: <string> this.secondGroup}),
      DynamicFieldHelper.createFieldTriStateCheckbox('onlyTenantPrivate', 'PRIVATE_SECURITY',
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldTriStateCheckboxHeqF('shortSecurity',
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldSelectString('assetclassType', 'ASSETCLASS', false,
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldSelectString('subCategoryNLS', 'SUB_ASSETCLASS', false,
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldSelectString('specialInvestmentInstruments', 'FINANCIAL_INSTRUMENT', false,
        {userDefinedValue: this.secondGroup}),
      DynamicFieldHelper.createFieldSelectStringHeqF('currency', false,
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

  helpLink() {
    BusinessHelper.toExternalHelpWebpage(this.globalparameterService.getUserLang(), HelpIds.HELP_WATCHLIST);
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

    this.valueChangedOnForm();
    combineLatest([this.globalparameterService.getCurrencies(),
      this.assetclassService.getSubcategoryForLanguage()]).subscribe(data => {
      this.configObject.currency.valueKeyHtmlOptions = data[0];
      this.configObject.currency.valueKeyHtmlOptions.splice(0, 0, new ValueKeyHtmlSelectOptions('', ''));
      this.configObject.subCategoryNLS.valueKeyHtmlOptions = data[1];
      this.configObject.subCategoryNLS.valueKeyHtmlOptions.splice(0, 0, new ValueKeyHtmlSelectOptions('', ''));

      this.dynamicFormComponent.setDefaultValuesAndEnableSubmit();
      this.configObject.isin.elementRef.nativeElement.focus();
    });
  }

  private valueChangedOnForm(): void {
    this.dynamicFormComponent.form.valueChanges.subscribe((security: Security) => {

      this.dynamicFormComponent.controls.filter(fieldConfig => fieldConfig.userDefinedValue === this.secondGroup)
        .forEach(fieldConfig => fieldConfig.invisible = (security.isin && security.isin !== ''));

      if (!security.isin || security.isin === '') {
        const secondGroupIsEmpty = this.isSecondGroupEmpty();
        this.dynamicFormComponent.controls.filter(fieldConfig => fieldConfig.userDefinedValue === this.firstGroup)
          .forEach(fieldConfig => fieldConfig.invisible = !secondGroupIsEmpty);
        this.configObject.name.invisible = this.configObject.name.invisible || this.isExactCurrecny();
        this.configObject.tickerSymbol.invisible = this.isCurrency();
        this.configObject.onlyTenantPrivate.invisible = this.isCurrency();
        this.configObject.shortSecurity.invisible = this.isCurrency();
        this.configObject.activeDate.invisible = this.isCurrency();
        this.configObject.specialInvestmentInstruments.invisible = this.isCurrency();
        this.configObject.subCategoryNLS.invisible = this.isCurrency();
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
