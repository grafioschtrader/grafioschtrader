import {Component, Input, OnInit, ViewChild} from '@angular/core';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {DataType} from '../../dynamic-form/models/data.type';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {AppHelper} from '../../shared/helper/app.helper';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {ProcessedAction} from '../../shared/types/processed.action';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {combineLatest, Observable, Subscription} from 'rxjs';
import {Stockexchange} from '../../entities/stockexchange';
import {StockexchangeService} from '../../stockexchange/service/stockexchange.service';
import {AssetclassService} from '../../assetclass/service/assetclass.service';
import {Assetclass} from '../../entities/assetclass';
import {SecurityService} from '../service/security.service';
import {FeedIdentifier, FeedSupport, IFeedConnector} from './ifeed.connector';
import {Security} from '../../entities/security';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {SecuritycurrencyEdit} from './securitycurrency.edit';
import {SecuritysplitService} from '../service/securitysplit.service';
import {HelpIds} from '../../shared/help/help.ids';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {DistributionFrequency} from '../../shared/types/distribution.frequency';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {CallbackValueChanged, SecurityDerived, SecurityEditSupport} from './security.edit.support';
import {SaveSecuritySuccess} from './split.period.table.base';
import {SecuritysplitEditTableComponent} from './securitysplit-edit-table.component';
import {HistoryquotePeriod} from '../../entities/historyquote.period';
import {SecurityHistoryquotePeriodEditTableComponent} from './security-historyquote-period-edit-table.component';
import {HistoryquotePeriodService} from '../service/historyquote.period.service';
import {Securitysplit} from '../../entities/dividend.split';
import {Helper} from '../../helper/helper';
import {AppSettings} from '../../shared/app.settings';
import {FormConfig} from '../../dynamic-form/models/form.config';

/**
 * Edit a security with possible security split and history quote period
 */
@Component({
  selector: 'security-edit',
  template: `
    <p-dialog styleClass="big-dialog"
              header="{{'SECURITY' | translate}}" [(visible)]="visibleEditSecurityDialog"
              [style]="{width: '600px', minHeight: '500px'}"
              [responsive]="true" [resizable]="false"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <p-tabView>
        <p-tabPanel header="{{'SECURITY' | translate}}">
          <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                        #dynamicFieldsetForm="dynamicForm" (submitBt)="submit($event)">
          </dynamic-form>
        </p-tabPanel>
        <p-tabPanel header="{{'SECURITY_SPLITS' | translate}}" *ngIf="canHaveSplits || !dataLoaded">
          <dynamic-form [config]="configSplit" [formConfig]="formConfig" [translateService]="translateService"
                        #splitForm="dynamicForm" (submitBt)="addSplit($event)">
          </dynamic-form>
          <securitysplit-edit-table (editData)="onSelectedSecuritysplit($event)"
                                    (savedData)="onDependingDialogSave($event)"
                                    [maxRows]="maxSplits">
          </securitysplit-edit-table>
        </p-tabPanel>
        <p-tabPanel header="{{'HISTORYQUOTE_FOR_PERIOD' | translate}}" *ngIf="!this.securityEditSupport?.hasMarketValue
        || !dataLoaded">
          <p>{{'HISTORYQUOTE_FOR_PERIOD_COMMENT' | translate}}</p>
          <dynamic-form [config]="periodPrices" [formConfig]="formConfigPeriod" [translateService]="translateService"
                        #periodPriceForm="dynamicForm" (submitBt)="addHistoryquotePeriod($event)">
          </dynamic-form>
          <security-historyquote-period-edit-table (editData)="onSelectedHistoryquote($event)"
                                                   (savedData)="onDependingDialogSave($event)"
                                                   [maxRows]="maxHistoryquotePeriods">
          </security-historyquote-period-edit-table>
        </p-tabPanel>
      </p-tabView>
    </p-dialog>`
})
export class SecurityEditComponent extends SecuritycurrencyEdit implements OnInit, CallbackValueChanged {
  // Access child components
  @ViewChild('splitForm') dynamicSplitForm: DynamicFormComponent;
  @ViewChild(SecuritysplitEditTableComponent) seetc: SecuritysplitEditTableComponent;
  @ViewChild('periodPriceForm') dynamicPeriodPriceForm: DynamicFormComponent;
  @ViewChild(SecurityHistoryquotePeriodEditTableComponent) shpetc: SecurityHistoryquotePeriodEditTableComponent;

  readonly maxSplits = 20;
  readonly maxHistoryquotePeriods = 20;

  // Input from parent view
  @Input() visibleEditSecurityDialog: boolean;

  securityEditSupport: SecurityEditSupport;
  formConfigPeriod: FormConfig;
  configSplit: FieldConfig[] = [];
  periodPrices: FieldConfig[] = [];
  configSplitObject: { [name: string]: FieldConfig };
  configPeriodPrices: { [name: string]: FieldConfig };
  canHaveSplits = true;

  dataLoaded = false;
  private stockexchangeSubscribe: Subscription;
  private distributionFrequencySubscribe: Subscription;

  constructor(private messageToastService: MessageToastService,
    private stockexchangeService: StockexchangeService,
    private assetclassService: AssetclassService,
    private securityService: SecurityService,
    private securitysplitService: SecuritysplitService,
    private historyquotePeriodService: HistoryquotePeriodService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.securityEditSupport = new SecurityEditSupport(this.translateService, this.gps, this);
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));
    this.formConfigPeriod = AppHelper.getDefaultFormConfig(this.gps,
      2, this.helpLinkPeriod.bind(this));

    this.config = SecurityEditSupport.getSecurityBaseFieldDefinition(SecurityDerived.Security);
    this.connectorPriceFieldConfig = SecurityEditSupport.getIntraHistoryFieldDefinition(SecurityDerived.Security);
    this.securityEditSupport.connectorDividendConfig = this.securityEditSupport.getDividendFieldDefinition();
    this.securityEditSupport.connectorSplitConfig = this.securityEditSupport.getSplitDefinition();
    this.config.push(...this.connectorPriceFieldConfig, ...this.securityEditSupport.connectorDividendConfig,
      ...this.securityEditSupport.connectorSplitConfig);
    this.config.push(...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this));

    this.configSplit = [
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'splitDate', true),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'fromFactor', true,
        1, 99_999_999),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'toFactor', true,
        1, 99_999_999),
      DynamicFieldHelper.createSubmitButton('APPLY')
    ];

    this.periodPrices = [
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'fromDate', true),
      DynamicFieldHelper.createFieldCurrencyNumber('price', 'CLOSE', true, 6,
        10, false, {
          ...this.gps.getNumberCurrencyMask(),
          allowZero: false
        }, false),
      DynamicFieldHelper.createSubmitButton('APPLY')
    ];

    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.configPeriodPrices = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.periodPrices);
    this.configSplitObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.configSplit);
    this.configObject.distributionFrequency.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
      DistributionFrequency);
  }

  /**
   * Connector field are hidden if stock exchange does not support quotes
   */
  valueChangedOnStockexchange(): void {
    this.stockexchangeSubscribe = this.configObject.stockexchange.formControl.valueChanges.subscribe((idStockexchange: number) => {
      this.setHasMarkedValue(idStockexchange);
      this.hideVisibleFeedConnectorsFields([...this.connectorPriceFieldConfig],
        !this.securityEditSupport.hasMarketValue, FeedIdentifier.SECURITY);
      this.enableDisableDividendSplitConnector(Helper.getReferencedDataObject(this.configObject.assetClass, null));
      this.securityEditSupport.enableDisableDenominationStockexchangeAssetclass(this.configObject);
    });
  }

  valueChangedOnDistributionFrequency(): void {
    this.distributionFrequencySubscribe = this.configObject.distributionFrequency
      .formControl.valueChanges.subscribe(distributionFrequency => {
        this.enableDisableDividendConnector(Helper.getReferencedDataObject(this.configObject.assetClass, null),
          distributionFrequency);
      });
  }

  valueChangedOnAssetClassExtend(assetClass: Assetclass): void {
    this.enableDisableDividendSplitConnector(assetClass);
  }

  addSplit(value: { [name: string]: any }): void {
    const securitysplit = new Securitysplit();
    this.dynamicSplitForm.cleanMaskAndTransferValuesToBusinessObject(securitysplit);
    this.seetc.addDataRow(securitysplit);
    this.dynamicSplitForm.setDefaultValuesAndEnableSubmit();
  }

  addHistoryquotePeriod(value: { [name: string]: any }): void {
    const historyquotePeriod = new HistoryquotePeriod();
    this.dynamicPeriodPriceForm.cleanMaskAndTransferValuesToBusinessObject(historyquotePeriod);
    this.shpetc.addDataRow(historyquotePeriod);
    this.dynamicPeriodPriceForm.setDefaultValuesAndEnableSubmit();
  }

  submit(value: { [name: string]: any }): void {
    const security = this.securityEditSupport.prepareForSave(this, this.proposeChangeEntityWithEntity,
      <Security>this.securityCurrencypairCallParam, this.dynamicForm, value);
    this.securityService.update(security).subscribe(newSecurity => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {i18nRecord: AppSettings.SECURITY.toUpperCase()});
      let savedDepending = false;
      if (this.securityEditSupport.hasMarketValue) {
        if (this.canHaveSplits && this.seetc) {
          savedDepending = true;
          this.seetc.save(newSecurity, this.configObject[AuditHelper.NOTE_REQUEST_INPUT].formControl.value);
        }
      } else {
        // Save historical periods
        if (this.shpetc) {
          savedDepending = true;
          this.shpetc.save(newSecurity, this.configObject[AuditHelper.NOTE_REQUEST_INPUT].formControl.value);
        }
      }
      if (!savedDepending) {
        this.onDependingDialogSave(new SaveSecuritySuccess(newSecurity, true));
      }

    }, () => this.configObject.submit.disabled = false);
  }

  onDependingDialogSave(saveSecuritySuccess: SaveSecuritySuccess): void {
    if (saveSecuritySuccess.success) {
      this.closeDialog.emit(new ProcessedActionData((this.securityCurrencypairCallParam) ? ProcessedAction.UPDATED
        : ProcessedAction.CREATED, saveSecuritySuccess.security));
    } else {
      this.configObject.submit.disabled = false;
    }
  }

  onSelectedSecuritysplit(securitysplit: Securitysplit) {
    this.dynamicSplitForm.transferBusinessObjectToForm(securitysplit);
  }

  onSelectedHistoryquote(historyquotePeriod: HistoryquotePeriod) {
    this.dynamicPeriodPriceForm.transferBusinessObjectToForm(historyquotePeriod);
  }

  override onHide(event) {
    this.securityEditSupport.destroy();
    this.stockexchangeSubscribe && this.stockexchangeSubscribe.unsubscribe();
    this.distributionFrequencySubscribe && this.distributionFrequencySubscribe.unsubscribe();
    super.onHide(event);
  }

  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_WATCHLIST_SECURITY);
  }

  helpLinkPeriod(): void {
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_WATCHLIST_WITHOUT_PRICE_DATA);
  }

  protected override loadHelperData(): void {
    this.securityEditSupport.registerValueOnChanged(SecurityDerived.Security, this.configObject);
    this.valueChangedOnStockexchange();
    this.valueChangedOnDistributionFrequency();
    this.hideVisibleFeedConnectorsFields(this.config, false, FeedIdentifier.SECURITY);

    const observables: Observable<Stockexchange[] | ValueKeyHtmlSelectOptions[] | Assetclass[] | IFeedConnector[]
      | Securitysplit[] | HistoryquotePeriod[]>[] = [];
    observables.push(this.stockexchangeService.getAllStockexchanges(false));
    observables.push(this.gps.getCurrencies());
    observables.push(this.assetclassService.getAllAssetclass());
    observables.push(this.securityService.getFeedConnectors());

    if (this.securityCurrencypairCallParam) {
      this.securityEditSupport.hasMarketValue = !(<Security>this.securityCurrencypairCallParam).stockexchange.noMarketValue;
    }

    if (this.securityCurrencypairCallParam) {
      if (this.securityEditSupport.hasMarketValue) {
        // Only load security splits for a existing security
        if ((<Security>this.securityCurrencypairCallParam).splitPropose) {
          // Propose change
          this.seetc.setDataList((<Security>this.securityCurrencypairCallParam).splitPropose, true);
        } else {
          observables.push(this.securitysplitService.getSecuritysplitsByIdSecuritycurrency(
            this.securityCurrencypairCallParam.idSecuritycurrency));
        }
      } else {
        if ((<Security>this.securityCurrencypairCallParam).hpPropose) {
          this.shpetc.setDataList((<Security>this.securityCurrencypairCallParam).hpPropose, true);
        } else {
          observables.push(this.historyquotePeriodService.getHistoryquotePeriodByIdSecuritycurrency(
            this.securityCurrencypairCallParam.idSecuritycurrency));
        }
      }
    }

    combineLatest(observables)
      .subscribe((data: [Stockexchange[], ValueKeyHtmlSelectOptions[], Assetclass[], IFeedConnector[],
          Securitysplit[] | HistoryquotePeriod[]]) => {
        this.securityEditSupport.assignLoadedValues(this.configObject, data[0], data[1], data[2]);

        this.prepareFeedConnectors(data[3], false);
        this.prepareSplitDividendConnector(data[3]);
        this.prepareExistingSecuritycurrency(this.configObject.name);
        const isPrivatePaper = this.securityCurrencypairCallParam
          && (<Security>this.securityCurrencypairCallParam).idTenantPrivate !== null;
        this.configObject.isTenantPrivate.formControl.setValue(isPrivatePaper);

        if (data.length === 5) {
          if (this.securityEditSupport.hasMarketValue) {
            this.seetc.setDataList(<Securitysplit[]>data[4], false);
          } else {
            this.shpetc.setDataList(<HistoryquotePeriod[]>data[4], false);
          }
        }
        this.dataLoaded = true;
        this.disableEnableInputForExisting();
        this.securityEditSupport.disableEnableFieldsOnAssetclass(SecurityDerived.Security,
          this.configObject, this.configObject.assetClass.formControl.value);
        this.securityEditSupport.setPrivatePaper(SecurityDerived.Security, isPrivatePaper, this.configObject);
      });
  }

  protected prepareSplitDividendConnector(feedConnectors: IFeedConnector[]): void {
    this.splitDividendCreateValueKeyHtmlSelectOptions(this.configObject.idConnectorSplit, FeedSupport.SPLIT);
    this.splitDividendCreateValueKeyHtmlSelectOptions(this.configObject[this.securityEditSupport.ID_CONNECTOR_DIVIDEND],
      FeedSupport.DIVIDEND);
  }

  private enableDisableDividendSplitConnector(assetClass: Assetclass): void {
    this.canHaveSplits = Security.canHaveSplitConnector(assetClass, this.securityEditSupport.hasMarketValue);
    this.hideVisibleFeedConnectorsFields(this.securityEditSupport.connectorSplitConfig,
      !this.canHaveSplits, null);
    this.enableDisableDividendConnector(assetClass, this.configObject.distributionFrequency.formControl.value);
  }

  private enableDisableDividendConnector(assetClass: Assetclass, distributionFrequency: string): void {
    this.hideVisibleFeedConnectorsFields(this.securityEditSupport.connectorDividendConfig,
      !Security.canHaveDividendConnector(assetClass, !distributionFrequency
        || distributionFrequency === '' ? null : DistributionFrequency[distributionFrequency],
        this.securityEditSupport.hasMarketValue), null);
  }

  private setHasMarkedValue(idStockexchange: number): void {
    this.securityEditSupport.hasMarketValue = !!idStockexchange
      && !(<Stockexchange[]>this.configObject.stockexchange.referencedDataObject)
        .find((stockexchange: Stockexchange) =>
          stockexchange.idStockexchange === +idStockexchange).noMarketValue;
  }

  private disableEnableInputForExisting(): void {
    if (this.securityCurrencypairCallParam !== null) {
      FormHelper.disableEnableFieldConfigsWhenAlreadySet(true,
        [this.configObject.isTenantPrivate, this.configObject.isin, this.configObject.currency]);
    }
  }

  private splitDividendCreateValueKeyHtmlSelectOptions(fieldConfig: FieldConfig, filterType: FeedSupport): void {
    const provider: IFeedConnector[] = this.feedPriceConnectors.filter(feedConnector =>
      !!feedConnector.securitycurrencyFeedSupport[FeedSupport[filterType]]);
    fieldConfig.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('id', 'readableName', provider, true);
  }

}
