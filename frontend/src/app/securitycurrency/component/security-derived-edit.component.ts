import {Component, Input, OnInit, ViewChild} from '@angular/core';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {SimpleEditBase} from '../../shared/edit/simple.edit.base';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {LoginService} from '../../shared/login/service/log-in.service';
import {MainDialogService} from '../../shared/mainmenubar/service/main.dialog.service';
import {HelpIds} from '../../shared/help/help.ids';
import {Security} from '../../entities/security';
import {AppHelper} from '../../shared/helper/app.helper';
import {SecurityDerived, SecurityEditSupport} from './security.edit.support';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {AfterSetSecurity, CallBackSetSecurityWithAfter} from './securitycurrency-search-and-set.component';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {DataType} from '../../dynamic-form/models/data.type';
import {combineLatest, Observable, Subscription} from 'rxjs';
import {Stockexchange} from '../../entities/stockexchange';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {Assetclass} from '../../entities/assetclass';
import {IFeedConnector} from './ifeed.connector';
import {StockexchangeService} from '../../stockexchange/service/stockexchange.service';
import {AssetclassService} from '../../assetclass/service/assetclass.service';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {DistributionFrequency} from '../../shared/types/distribution.frequency';
import {ProposeChangeEntityWithEntity} from '../../entities/proposechange/propose.change.entity.whit.entity';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {SecurityService} from '../service/security.service';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {CurrencypairService} from '../service/currencypair.service';
import {SupplementCriteria} from '../model/supplement.criteria';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {SecurityDerivedLink} from '../../entities/security.derived.link';
import {SecurityCurrencypairDerivedLinks} from '../model/security.currencypair.derived.links';
import {AppSettings} from '../../shared/app.settings';
import {FormHelper} from '../../dynamic-form/components/FormHelper';

/**
 * To create a derived instrument a base instrument is required. Additional a formula can be added. Prices depend on other instrument,
 * so there is no connector.
 */
@Component({
  selector: 'security-derived-edit',
  template: `
    <p-dialog header="{{'DERIVED_DATA' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '600px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>

      <securitycurrency-search-and-set *ngIf="visibleSetSecurityDialog"
                                       [visibleDialog]="visibleSetSecurityDialog"
                                       [supplementCriteria]="supplementCriteria"
                                       [callBackSetSecurityWithAfter]="this"
                                       (closeDialog)="handleOnCloseSetDialog($event)">
      </securitycurrency-search-and-set>
    </p-dialog>
  `
})
export class SecurityDerivedEditComponent extends SimpleEditBase implements OnInit, CallBackSetSecurityWithAfter {
// Input from parent component
  @Input() securityCallParam: Security;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;

  // Access child components
  @ViewChild(DynamicFormComponent, {static: true}) dynamicForm: DynamicFormComponent;

  readonly derivedData = 'DERIVED_DATA';
  supplementCriteria: SupplementCriteria;
  formulaSubscribe: Subscription;

  visibleSetSecurityDialog = false;
  private dialogSecurityTargetFieldname: string;
  private baseInstrument: Security | CurrencypairWatchlist;
  private additionalInstruments: { [fieldName: string]: Security | CurrencypairWatchlist } = {};
  private securityEditSupport: SecurityEditSupport;

  private readonly BASE_PRODUCT_NAME = 'baseProductName';

  private readonly FORMULA_PRICES = 'formulaPrices';
  private usedFormulaVars: string[];

  constructor(public translateService: TranslateService,
              private messageToastService: MessageToastService,
              private loginService: LoginService,
              private mainDialogService: MainDialogService,
              private stockexchangeService: StockexchangeService,
              private assetclassService: AssetclassService,
              private securityService: SecurityService,
              private currencypairService: CurrencypairService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_WATCHLIST_DERIVED_INSTRUMENT, gps);
    this.supplementCriteria = new SupplementCriteria(false, true);
  }

  ngOnInit(): void {
    this.securityEditSupport = new SecurityEditSupport(this.translateService, this.gps, null);
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputButtonHeqF(DataType.String, this.BASE_PRODUCT_NAME,
        this.handleSecurityClick.bind(this), true, {fieldsetName: this.derivedData}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF(this.FORMULA_PRICES, 255, false, {fieldsetName: this.derivedData})
    ];

    this.config.push(...SecurityEditSupport.getSecurityBaseFieldDefinition(SecurityDerived.Derived));
    this.config.push(...SecurityEditSupport.getIntraHistoryFieldDefinition(SecurityDerived.Derived));
    this.config.push(...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this));

    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.configObject.distributionFrequency.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
      DistributionFrequency);
  }

  disableBaseInstrumentFields(): void {
    if (!AuditHelper.hasRightsForEditingOrDeleteAuditable(this.gps, this.securityCallParam)) {
      this.config.filter(fieldConfig => fieldConfig.fieldsetName === this.derivedData).forEach(
        fieldConfig => fieldConfig.formControl.disable());
    }
  }

  valueChangedOnFormula(): void {
    this.formulaSubscribe = this.configObject[this.FORMULA_PRICES].formControl.valueChanges.subscribe((formulaPrices: string) => {
      if (!this.configObject[this.FORMULA_PRICES].formControl.disabled) {
        if (formulaPrices) {
          let match = SecurityCurrencypairDerivedLinks.VAR_NAME_REGEX.exec(formulaPrices);
          this.usedFormulaVars = [];
          while (match != null) {
            this.usedFormulaVars.push(match[1]);
            match = SecurityCurrencypairDerivedLinks.VAR_NAME_REGEX.exec(formulaPrices);
          }
          this.addInvestmentInstrumentFieldRow();
        }
        this.reduceExpandChoosableAssetclass();
      }
    });
  }

  handleSecurityClick(fieldConfig: FieldConfig): void {
    this.visibleSetSecurityDialog = true;
    this.dialogSecurityTargetFieldname = fieldConfig.field;
  }

  setSecurity(security: Security | CurrencypairWatchlist, afterSetSecurity: AfterSetSecurity): void {
    afterSetSecurity.afterSetSecurity();
    if (this.dialogSecurityTargetFieldname === this.BASE_PRODUCT_NAME) {
      this.setSecurityBaseInstrument(security);
    } else {
      this.additionalInstruments[this.dialogSecurityTargetFieldname] = security;
    }
    this.configObject[this.dialogSecurityTargetFieldname].formControl.setValue(security.name);
  }

  handleOnCloseSetDialog(processedActionData: ProcessedActionData): void {
    this.visibleSetSecurityDialog = false;
  }

  submit(value: { [name: string]: any }): void {
    const security = this.securityEditSupport.prepareForSave(this, this.proposeChangeEntityWithEntity, this.securityCallParam,
      this.dynamicForm, value);
    if (this.baseInstrument) {
      security.idLinkSecuritycurrency = this.baseInstrument.idSecuritycurrency;
    }
    this.translateFormulaFromUserLanguage(security);
    this.setSecurityDerivedLinks(security);
    this.securityService.update(security).subscribe(newSecurity => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {i18nRecord: AppSettings.SECURITY.toUpperCase()});
      this.closeDialog.emit(new ProcessedActionData(this.securityCallParam ? ProcessedAction.UPDATED
        : ProcessedAction.CREATED, newSecurity));
    }, () => this.configObject.submit.disabled = false);
  }

  onHide(event): void {
    this.securityEditSupport.destroy();
    this.formulaSubscribe && this.formulaSubscribe.unsubscribe();
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  protected initialize(): void {
    this.securityEditSupport.registerValueOnChanged(SecurityDerived.Derived, this.configObject);
    this.valueChangedOnFormula();
    const observables: Observable<Stockexchange[] | ValueKeyHtmlSelectOptions[]
      | Assetclass[] | IFeedConnector[] | SecurityCurrencypairDerivedLinks>[] = [];
    observables.push(this.stockexchangeService.getAllStockexchanges(false));
    observables.push(this.gps.getCurrencies());
    observables.push(this.assetclassService.getAllAssetclass());
    if (this.securityCallParam) {
      observables.push(this.securityService.getDerivedInstrumentsLinksForSecurity(this.securityCallParam.idSecuritycurrency));
    }

    combineLatest(observables).subscribe((data: [Stockexchange[], ValueKeyHtmlSelectOptions[], Assetclass[],
      SecurityCurrencypairDerivedLinks]) => {
      this.securityEditSupport.assignLoadedValues(this.configObject, data[0], data[1], data[2]);
      this.form.setDefaultValuesAndEnableSubmit();
      AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.gps,
        this.securityCallParam, this.form, this.configObject, this.proposeChangeEntityWithEntity);
      this.securityCallParam && this.setInstrumentsForExistingSecurity(<SecurityCurrencypairDerivedLinks>data[data.length - 1]);
      this.disableEnableInputForExisting(!!this.securityCallParam);
    });
  }

  private addInvestmentInstrumentFieldRow(): void {
    const fieldConfig: FieldConfig[] = [];
    this.usedFormulaVars.forEach((ufv: string) => {
      if (SecurityCurrencypairDerivedLinks.ALLOWED_VAR_NAMES.indexOf(ufv) >= 0 && ufv
        !== SecurityCurrencypairDerivedLinks.ALLOWED_VAR_NAMES[0]) {
        const fieldName = SecurityCurrencypairDerivedLinks.ADDITIONAL_INSTRUMENT_NAME + '_' + ufv;

        if (!this.configObject[fieldName]) {
          fieldConfig.push(DynamicFieldHelper.createFieldInputButton(DataType.String, fieldName, 'ADDITIONAL_INSTRUMENT_NAME',
            this.handleSecurityClick.bind(this), true, {fieldsetName: this.derivedData, labelSuffix: `(${ufv})`}));
        }
      }
    });

    const unusedInstruments = this.config.filter(fc => fc.field.startsWith(
        SecurityCurrencypairDerivedLinks.ADDITIONAL_INSTRUMENT_NAME + '_')
      && this.usedFormulaVars.indexOf(fc.field.charAt(fc.field.length - 1)) < 0);
    unusedInstruments.forEach(ffc => this.config.splice(this.config.findIndex(fc => fc.field === ffc.field), 1));
    if (fieldConfig.length > 0 || unusedInstruments.length > 0) {
      this.config = [...this.config.slice(0, 2), ...fieldConfig, ...this.config.slice(2, this.config.length)];
      this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    }
  }

  private setInstrumentsForExistingSecurity(scdl: SecurityCurrencypairDerivedLinks): void {
    this.setBaseInstrumentForExistingSecurity(scdl);
    setTimeout(() => this.setAdditionalInstrumentsForExistingSecurity(scdl));
  }

  private setBaseInstrumentForExistingSecurity(scdl: SecurityCurrencypairDerivedLinks): void {
    this.configObject[this.BASE_PRODUCT_NAME].disabled = true;
    this.baseInstrument = SecurityCurrencypairDerivedLinks.getBaseInstrument(scdl, this.securityCallParam.idLinkSecuritycurrency);
    this.reduceExpandChoosableAssetclass();
    this.configObject[this.BASE_PRODUCT_NAME].formControl.setValue(this.baseInstrument.name);
  }

  private setAdditionalInstrumentsForExistingSecurity(scdl: SecurityCurrencypairDerivedLinks): void {
    this.additionalInstruments = SecurityCurrencypairDerivedLinks.getAdditionalInstrumentsForExistingSecurity(scdl);
    for (const [fieldName, securitycurrency] of Object.entries(this.additionalInstruments)) {
      this.configObject[fieldName].formControl.setValue(securitycurrency.name);
    }
    this.disableBaseInstrumentFields();
  }

  private reduceExpandChoosableAssetclass(): void {
    const formula: string = this.configObject[this.FORMULA_PRICES].formControl.value;
    if (this.baseInstrument && this.baseInstrument.assetClass.categoryType === AssetclassType[AssetclassType.CURRENCY_PAIR]
      && (!formula || formula.length === 0)) {
      this.securityEditSupport.filterAssetclasses(this.configObject, AssetclassType.CURRENCY_PAIR);
    } else {
      this.securityEditSupport.removeFilterAssetclass(this.configObject);
    }
  }

  private setSecurityBaseInstrument(security: Security | CurrencypairWatchlist): void {
    this.baseInstrument = security;
    if (security instanceof CurrencypairWatchlist) {
      this.configObject.currency.formControl.setValue((<CurrencypairWatchlist>security).fromCurrency);
      this.reduceExpandChoosableAssetclass();
    } else {
      this.form.transferBusinessObjectToForm(security);
    }
  }

  private disableEnableInputForExisting(disable: boolean): void {
    FormHelper.disableEnableFieldConfigsWhenAlreadySet(disable, [this.configObject.currency]);
  }

  private translateFormulaFromUserLanguage(security: Security): void {
    if (this.gps.getDecimalSymbol() !== '.' && security.formulaPrices) {
      security.formulaPrices = security.formulaPrices.split(this.gps.getDecimalSymbol()).join('.');
    }
  }

  private setSecurityDerivedLinks(security: Security): void {
    security.securityDerivedLinks = [];
    if (this.usedFormulaVars) {
      this.usedFormulaVars.forEach((ufv: string) => {
        if (SecurityCurrencypairDerivedLinks.ALLOWED_VAR_NAMES.indexOf(ufv) >= 0
          && ufv !== SecurityCurrencypairDerivedLinks.ALLOWED_VAR_NAMES[0]) {
          const fieldName = SecurityCurrencypairDerivedLinks.ADDITIONAL_INSTRUMENT_NAME + '_' + ufv;
          security.securityDerivedLinks.push(new SecurityDerivedLink(this.securityCallParam ?
            this.securityCallParam.idSecuritycurrency : null, ufv, this.additionalInstruments[fieldName].idSecuritycurrency));
        }
      });
    }
  }
}
