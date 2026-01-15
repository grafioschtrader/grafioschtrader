import {Component, Input, OnInit, ViewChild} from '@angular/core';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {AppHelper} from '../../lib/helper/app.helper';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {ProcessedAction} from '../../lib/types/processed.action';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {combineLatest, Observable, Subscription} from 'rxjs';
import {startWith} from 'rxjs/operators';
import {Stockexchange} from '../../entities/stockexchange';
import {StockexchangeService} from '../../stockexchange/service/stockexchange.service';
import {AssetclassService} from '../../assetclass/service/assetclass.service';
import {Assetclass} from '../../entities/assetclass';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {FeedIdentifier, FeedSupport, IFeedConnector} from './ifeed.connector';
import {Security} from '../../entities/security';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {SecuritycurrencyEdit} from './securitycurrency.edit';
import {SecuritysplitService} from '../../securitycurrency/service/securitysplit.service';
import {HelpIds} from '../../lib/help/help.ids';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {DistributionFrequency} from '../../shared/types/distribution.frequency';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {FormHelper} from '../../lib/dynamic-form/components/FormHelper';
import {CallbackValueChanged, SecurityDerived, SecurityEditSupport} from './security.edit.support';
import {SaveSecuritySuccess} from './split.period.table.base';
import {SecuritysplitEditTableComponent} from './securitysplit-edit-table.component';
import {HistoryquotePeriod} from '../../entities/historyquote.period';
import {SecurityHistoryquotePeriodEditTableComponent} from './security-historyquote-period-edit-table.component';
import {HistoryquotePeriodService} from '../../securitycurrency/service/historyquote.period.service';
import {Securitysplit} from '../../entities/dividend.split';
import {Helper} from '../../lib/helper/helper';
import {AppSettings} from '../../shared/app.settings';
import {FormConfig} from '../../lib/dynamic-form/models/form.config';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {DialogModule} from 'primeng/dialog';
import {TabsModule} from 'primeng/tabs';
import {GtnetSecurityLookupDialogComponent} from '../../gtnet/component/gtnet-security-lookup-dialog.component';
import {SecurityGtnetLookupWithMatch} from '../../gtnet/model/gtnet-security-lookup';
import {GtnetSecurityLookupService} from '../../gtnet/service/gtnet-security-lookup.service';

/**
 * Edit a security with possible security split and history quote period
 */
@Component({
  selector: 'security-edit',
  template: `
    <p-dialog class="big-dialog"
              header="{{'SECURITY' | translate}}" [(visible)]="visibleEditSecurityDialog"
              [style]="{width: '600px', minHeight: '500px'}"
              [resizable]="false"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <p-tabs [value]="activeTabValue">
        <p-tablist>
          <p-tab value="security">{{ 'SECURITY' | translate }}</p-tab>
          @if (canHaveSplits || !dataLoaded) {
            <p-tab value="splits">{{ 'SECURITY_SPLITS' | translate }}</p-tab>
          }
          @if (!this.securityEditSupport?.hasMarketValue || !dataLoaded) {
            <p-tab value="periods">{{ 'HISTORYQUOTE_FOR_PERIOD' | translate }}</p-tab>
          }
        </p-tablist>
        <p-tabpanels>
          <p-tabpanel value="security">
            <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                          #dynamicFieldsetForm="dynamicForm" (submitBt)="submit($event)">
            </dynamic-form>
          </p-tabpanel>
          <p-tabpanel value="splits">
            <dynamic-form [config]="configSplit" [formConfig]="formConfig" [translateService]="translateService"
                          #splitForm="dynamicForm" (submitBt)="addSplit($event)">
            </dynamic-form>
            <securitysplit-edit-table (editData)="onSelectedSecuritysplit($event)"
                                      (savedData)="onDependingDialogSave($event)"
                                      [maxRows]="maxSplits">
            </securitysplit-edit-table>
          </p-tabpanel>
          <p-tabpanel value="periods">
            <p>{{ 'HISTORYQUOTE_FOR_PERIOD_COMMENT' | translate }}</p>
            <dynamic-form [config]="periodPrices" [formConfig]="formConfigPeriod"
                          [translateService]="translateService"
                          #periodPriceForm="dynamicForm" (submitBt)="addHistoryquotePeriod($event)">
            </dynamic-form>
            <security-historyquote-period-edit-table (editData)="onSelectedHistoryquote($event)"
                                                     (savedData)="onDependingDialogSave($event)"
                                                     [maxRows]="maxHistoryquotePeriods">
            </security-historyquote-period-edit-table>
          </p-tabpanel>
        </p-tabpanels>
      </p-tabs>
    </p-dialog>
    @if (visibleGtnetLookupDialog) {
      <gtnet-security-lookup-dialog
        [visibleDialog]="visibleGtnetLookupDialog"
        [isin]="configObject?.isin?.formControl?.value"
        [currency]="configObject?.currency?.formControl?.value"
        [tickerSymbol]="configObject?.tickerSymbol?.formControl?.value"
        [feedConnectors]="feedPriceConnectors"
        (closeDialog)="handleCloseGtnetLookupDialog($event)">
      </gtnet-security-lookup-dialog>
    }`,
  standalone: true,
  imports: [
    TranslateModule,
    DialogModule,
    TabsModule,
    DynamicFormComponent,
    SecuritysplitEditTableComponent,
    SecurityHistoryquotePeriodEditTableComponent,
    GtnetSecurityLookupDialogComponent
  ]
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

  // Tab management
  activeTabValue: string = 'security';

  securityEditSupport: SecurityEditSupport;
  formConfigPeriod: FormConfig;
  configSplit: FieldConfig[] = [];
  periodPrices: FieldConfig[] = [];
  configSplitObject: { [name: string]: FieldConfig };
  configPeriodPrices: { [name: string]: FieldConfig };
  canHaveSplits = true;

  /** Visibility flag for GTNet security lookup dialog */
  visibleGtnetLookupDialog = false;

  dataLoaded = false;
  private stockexchangeSubscribe: Subscription;
  private distributionFrequencySubscribe: Subscription;
  private gtnetLookupSubscribe: Subscription;

  constructor(private messageToastService: MessageToastService,
    private gpsGT: GlobalparameterGTService,
    private stockexchangeService: StockexchangeService,
    private assetclassService: AssetclassService,
    private securityService: SecurityService,
    private securitysplitService: SecuritysplitService,
    private historyquotePeriodService: HistoryquotePeriodService,
    private gtnetSecurityLookupService: GtnetSecurityLookupService,
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

    this.config = SecurityEditSupport.getSecurityBaseFieldDefinition(SecurityDerived.Security, this.gps);

    // Add GTNet lookup button after currency field if GTNet is enabled (invisible until we check for accessible peers)
    if (this.gps.useGtnet()) {
      const currencyIndex = this.config.findIndex(fc => fc.field === 'currency');
      if (currencyIndex >= 0) {
        const gtnetButton = DynamicFieldHelper.createFunctionButtonFieldName('gtnetLookup', 'GTNET_SECURITY_LOOKUP',
          () => this.openGtnetLookup(), {fieldsetName: 'BASE_DATA', disabled: true});
        gtnetButton.invisible = true; // Initially invisible, will be shown if accessible peers exist
        this.config.splice(currencyIndex + 1, 0, gtnetButton);
      }
    }

    this.connectorPriceFieldConfig = SecurityEditSupport.getIntraHistoryFieldDefinition(SecurityDerived.Security, this.gps);
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
      DynamicFieldHelper.createFieldInputNumber('price', 'CLOSE', true, 6,
        10, false),
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
    this.securityService.update(security).subscribe({
      next: newSecurity => {
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
      }, error: () => this.configObject.submit.disabled = false
    });
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
    this.gtnetLookupSubscribe && this.gtnetLookupSubscribe.unsubscribe();
    super.onHide(event);
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_WATCHLIST_SECURITY);
  }

  helpLinkPeriod(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_WATCHLIST_WITHOUT_PRICE_DATA);
  }

  /**
   * Opens the GTNet security lookup dialog.
   */
  openGtnetLookup(): void {
    this.visibleGtnetLookupDialog = true;
  }

  /**
   * Handles the close of the GTNet lookup dialog and applies the selected security data to the form.
   */
  handleCloseGtnetLookupDialog(processedActionData: ProcessedActionData): void {
    this.visibleGtnetLookupDialog = false;
    if (processedActionData.action === ProcessedAction.CREATED && processedActionData.data) {
      this.applyGtnetLookupData(processedActionData.data as SecurityGtnetLookupWithMatch);
    }
  }

  /**
   * Applies data from GTNet lookup to the security form fields.
   * Maps enum values to local IDs for asset class and stock exchange.
   * Applies matched connector configurations and URL extensions.
   */
  private applyGtnetLookupData(dto: SecurityGtnetLookupWithMatch): void {
    // Set direct field values
    this.configObject.name.formControl.setValue(dto.name);
    if (dto.tickerSymbol) {
      this.configObject.tickerSymbol.formControl.setValue(dto.tickerSymbol);
    }
    if (dto.distributionFrequency) {
      this.configObject.distributionFrequency.formControl.setValue(dto.distributionFrequency);
    }
    if (dto.denomination) {
      this.configObject.denomination?.formControl?.setValue(dto.denomination);
    }
    if (dto.leverageFactor) {
      this.configObject.leverageFactor?.formControl?.setValue(dto.leverageFactor);
    }
    if (dto.productLink) {
      this.configObject.productLink?.formControl?.setValue(dto.productLink);
    }

    // Apply additional fields
    if (dto.activeFromDate) {
      this.configObject.activeFromDate?.formControl?.setValue(new Date(dto.activeFromDate));
    }
    if (dto.activeToDate) {
      this.configObject.activeToDate?.formControl?.setValue(new Date(dto.activeToDate));
    }
    if (dto.stockexchangeLink) {
      this.configObject.stockexchangeLink?.formControl?.setValue(dto.stockexchangeLink);
    }

    // Map asset class by enum values
    if (dto.categoryType && dto.specialInvestmentInstrument) {
      const assetclasses = this.configObject.assetClass.referencedDataObject as Assetclass[];
      const matchingAssetclass = assetclasses?.find(ac =>
        ac.categoryType === dto.categoryType && ac.specialInvestmentInstrument === dto.specialInvestmentInstrument);
      if (matchingAssetclass) {
        this.configObject.assetClass.formControl.setValue(matchingAssetclass.idAssetClass);
      }
    }

    // Map stock exchange by MIC code
    if (dto.stockexchangeMic) {
      const stockexchanges = this.configObject.stockexchange.referencedDataObject as Stockexchange[];
      const matchingExchange = stockexchanges?.find(se => se.mic === dto.stockexchangeMic);
      if (matchingExchange) {
        this.configObject.stockexchange.formControl.setValue(matchingExchange.idStockexchange);
      }
    }

    // Apply matched connector configurations
    this.applyMatchedConnectors(dto);
  }

  /**
   * Applies matched connector IDs and URL extensions from GTNet lookup.
   */
  private applyMatchedConnectors(dto: SecurityGtnetLookupWithMatch): void {
    // History connector
    if (dto.matchedHistoryConnector && this.configObject[this.ID_CONNECTOR_HISTORY]) {
      this.configObject[this.ID_CONNECTOR_HISTORY].formControl.setValue(dto.matchedHistoryConnector);
      if (dto.matchedHistoryUrlExtension && this.configObject.urlHistoryExtend) {
        this.configObject.urlHistoryExtend.formControl.setValue(dto.matchedHistoryUrlExtension);
      }
    }

    // Intraday connector
    if (dto.matchedIntraConnector && this.configObject[this.ID_CONNECTOR_INTRA]) {
      this.configObject[this.ID_CONNECTOR_INTRA].formControl.setValue(dto.matchedIntraConnector);
      if (dto.matchedIntraUrlExtension && this.configObject.urlIntraExtend) {
        this.configObject.urlIntraExtend.formControl.setValue(dto.matchedIntraUrlExtension);
      }
    }

    // Dividend connector
    if (dto.matchedDividendConnector && this.configObject[this.securityEditSupport.ID_CONNECTOR_DIVIDEND]) {
      this.configObject[this.securityEditSupport.ID_CONNECTOR_DIVIDEND].formControl.setValue(dto.matchedDividendConnector);
      if (dto.matchedDividendUrlExtension && this.configObject.urlDividendExtend) {
        this.configObject.urlDividendExtend.formControl.setValue(dto.matchedDividendUrlExtension);
      }
    }

    // Split connector
    if (dto.matchedSplitConnector && this.configObject.idConnectorSplit) {
      this.configObject.idConnectorSplit.formControl.setValue(dto.matchedSplitConnector);
      if (dto.matchedSplitUrlExtension && this.configObject.urlSplitExtend) {
        this.configObject.urlSplitExtend.formControl.setValue(dto.matchedSplitUrlExtension);
      }
    }
  }

  /**
   * Subscribes to form value changes to enable/disable the GTNet lookup button.
   * Button is enabled when (ISIN or ticker) AND currency are filled.
   */
  private valueChangedOnGtnetLookupFields(): void {
    if (this.configObject.gtnetLookup) {
      this.gtnetLookupSubscribe = combineLatest([
        this.configObject.isin.formControl.valueChanges.pipe(
          startWith(this.configObject.isin.formControl.value)),
        this.configObject.tickerSymbol.formControl.valueChanges.pipe(
          startWith(this.configObject.tickerSymbol.formControl.value)),
        this.configObject.currency.formControl.valueChanges.pipe(
          startWith(this.configObject.currency.formControl.value))
      ]).subscribe(([isin, ticker, currency]) => {
        const hasIdentifier = (isin && isin.trim().length > 0) || (ticker && ticker.trim().length > 0);
        const hasCurrency = currency && currency.trim().length > 0;
        this.configObject.gtnetLookup.disabled = !(hasIdentifier && hasCurrency);
      });
    }
  }

  protected override loadHelperData(): void {
    this.securityEditSupport.registerValueOnChanged(SecurityDerived.Security, this.configObject);
    this.valueChangedOnStockexchange();
    this.valueChangedOnDistributionFrequency();
    this.valueChangedOnGtnetLookupFields();
    this.hideVisibleFeedConnectorsFields(this.config, false, FeedIdentifier.SECURITY);

    const observables: Observable<Stockexchange[] | ValueKeyHtmlSelectOptions[] | Assetclass[] | IFeedConnector[]
      | Securitysplit[] | HistoryquotePeriod[]>[] = [];
    observables.push(this.stockexchangeService.getAllStockexchanges(false));
    observables.push(this.gpsGT.getCurrencies());
    observables.push(this.securityCurrencypairCallParam ?
      this.assetclassService.getPossibleAssetclassForExistingSecurityOrAll(this.securityCurrencypairCallParam.idSecuritycurrency) :
      this.assetclassService.getAllAssetclass());
    observables.push(this.securityService.getFeedConnectors());

    if (this.securityCurrencypairCallParam) {
      this.securityEditSupport.hasMarketValue = !(<Security>this.securityCurrencypairCallParam).stockexchange.noMarketValue;
    }

    if (this.securityCurrencypairCallParam) {
      if (this.securityEditSupport.hasMarketValue) {
        // Only load security splits for an existing security
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

        // Check for accessible GTNet peers and show lookup button if available
        this.checkGtnetLookupAvailability();
      });
  }

  /**
   * Checks if GTNet security lookup is available and shows/hides the button accordingly.
   * The button is only shown if GTNet is enabled and there are accessible peers.
   */
  private checkGtnetLookupAvailability(): void {
    if (this.configObject.gtnetLookup && this.gps.useGtnet()) {
      this.gtnetSecurityLookupService.hasAccessiblePeers().subscribe({
        next: (hasAccessiblePeers) => {
          // Show button if accessible peers exist
          AppHelper.invisibleAndHide(this.configObject.gtnetLookup, !hasAccessiblePeers);
        },
        error: () => {
          // Keep button hidden on error
          AppHelper.invisibleAndHide(this.configObject.gtnetLookup, true);
        }
      });
    }
  }

  protected prepareSplitDividendConnector(feedConnectors: IFeedConnector[]): void {
    this.splitDividendCreateValueKeyHtmlSelectOptions(this.configObject.idConnectorSplit, FeedSupport.FS_SPLIT);
    this.splitDividendCreateValueKeyHtmlSelectOptions(this.configObject[this.securityEditSupport.ID_CONNECTOR_DIVIDEND],
      FeedSupport.FS_DIVIDEND);
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
