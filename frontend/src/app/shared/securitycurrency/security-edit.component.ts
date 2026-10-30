import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, computed, OnInit, signal, viewChild, input, inject } from '@angular/core';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { AppHelper } from '../../lib/helper/app.helper';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { TranslateModule } from '@ngx-translate/core';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { combineLatest, Observable, Subscription } from 'rxjs';
import { startWith } from 'rxjs/operators';
import { Stockexchange } from '../../entities/stockexchange';
import { StockexchangeService } from '../../stockexchange/service/stockexchange.service';
import { AssetclassService } from '../../assetclass/service/assetclass.service';
import { Assetclass } from '../../entities/assetclass';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { FeedIdentifier, FeedSupport, IFeedConnector } from './ifeed.connector';
import { Security } from '../../entities/security';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { SecuritycurrencyEdit } from './securitycurrency.edit';
import { SecuritysplitService } from '../../securitycurrency/service/securitysplit.service';
import { HelpIds } from '../../lib/help/help.ids';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { DistributionFrequency } from '../../shared/types/distribution.frequency';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { FormHelper } from '../../lib/dynamic-form/components/FormHelper';
import { CallbackValueChanged, SecurityDerived, SecurityEditSupport } from './security.edit.support';
import { SaveSecuritySuccess } from './split.period.table.base';
import { SecuritysplitEditTableComponent } from './securitysplit-edit-table.component';
import { HistoryquotePeriod } from '../../entities/historyquote.period';
import { SecurityHistoryquotePeriodEditTableComponent } from './security-historyquote-period-edit-table.component';
import { HistoryquotePeriodService } from '../../securitycurrency/service/historyquote.period.service';
import { Securitysplit } from '../../entities/dividend.split';
import { Helper } from '../../lib/helper/helper';
import { AppSettings } from '../../shared/app.settings';
import { FormConfig } from '../../lib/dynamic-form/models/form.config';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { TabsModule } from '@openng/optimus-ui/tabs';
import { GtnetSecurityLookupDialogComponent } from '../../gtnet/component/gtnet-security-lookup-dialog.component';
import { SecurityGtnetLookupDTO } from '../../gtnet/model/gtnet-security-lookup';
import { GtnetSecurityLookupService } from '../../gtnet/service/gtnet-security-lookup.service';
import { SecurityBondTermsComponent } from './security-bond-terms.component';
import { SecurityLookupDialogComponent } from '../../securitylookup/component/security-lookup-dialog.component';
import { SecurityLookupSelection } from '../../securitylookup/model/security-lookup';
import { chooseDistributionFrequency } from '../../securitylookup/model/security-lookup-selection';

export function isManualIssuerCountryChange(
  dataLoaded: boolean,
  applyingPrefill: boolean,
  controlDirty: boolean,
  issuerCountry: string | null,
  lastPrefill: string | null
): boolean {
  return dataLoaded && !applyingPrefill && controlDirty && issuerCountry !== lastPrefill;
}

/**
 * Edit a security with possible security split and history quote period
 */
@Component({
  selector: 'security-edit',
  template: ` <p-dialog
      styleClass="big-dialog"
      header="{{ 'SECURITY' | translate }}"
      [visible]="visibleEditSecurityDialog()"
      [style]="{ width: '600px' }"
      [resizable]="false"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <p-tabs [value]="activeTabValue">
        <p-tablist>
          <p-tab value="security">{{ 'SECURITY' | translate }}</p-tab>
          @if (canHaveSplits() || !dataLoaded()) {
            <p-tab value="splits">{{ 'SECURITY_SPLITS' | translate }}</p-tab>
          }
          @if (!this.securityEditSupport.hasMarketValue || !dataLoaded()) {
            <p-tab value="periods">{{ 'HISTORYQUOTE_FOR_PERIOD' | translate }}</p-tab>
          }
          @if (canEditBondTerms()) {
            <p-tab value="bondTerms">{{ 'SIMULATION_BOND_TERMS' | translate }}</p-tab>
          }
        </p-tablist>
        <p-tabpanels>
          <p-tabpanel value="security">
            <dynamic-form
              [config]="config"
              [formConfig]="formConfig"
              [translateService]="translateService"
              #dynamicFieldsetForm="dynamicForm"
              (submitBt)="submit($event)" />
          </p-tabpanel>
          <p-tabpanel value="splits">
            <dynamic-form
              [config]="configSplit"
              [formConfig]="formConfig"
              [translateService]="translateService"
              #splitForm="dynamicForm"
              (submitBt)="addSplit($event)" />
            <securitysplit-edit-table
              (editData)="onSelectedSecuritysplit($event)"
              (savedData)="onDependingDialogSave($event)"
              [maxRows]="maxSplits()" />
          </p-tabpanel>
          <p-tabpanel value="periods">
            <p>{{ 'HISTORYQUOTE_FOR_PERIOD_COMMENT' | translate }}</p>
            <dynamic-form
              [config]="periodPrices"
              [formConfig]="formConfigPeriod"
              [translateService]="translateService"
              #periodPriceForm="dynamicForm"
              (submitBt)="addHistoryquotePeriod($event)" />
            <security-historyquote-period-edit-table
              (editData)="onSelectedHistoryquote($event)"
              (savedData)="onDependingDialogSave($event)"
              [maxRows]="maxHistoryquotePeriods()" />
          </p-tabpanel>
          <p-tabpanel value="bondTerms">
            <security-bond-terms
              [entity]="$any(securityCurrencypairCallParam())"
              [securityName]="currentSecurityName()"
              [currency]="currentSecurityCurrency()" />
          </p-tabpanel>
        </p-tabpanels>
      </p-tabs>
    </p-dialog>
    @if (visibleGtnetLookupDialog()) {
      <gtnet-security-lookup-dialog
        [visibleDialog]="visibleGtnetLookupDialog()"
        [isin]="$safeNavigationMigration(configObject.isin.formControl?.value)"
        [currency]="$safeNavigationMigration(configObject.currency.formControl?.value)"
        [tickerSymbol]="$safeNavigationMigration(configObject.tickerSymbol.formControl?.value)"
        (closeDialog)="handleCloseGtnetLookupDialog($event)" />
    }
    @if (visibleSecurityLookupDialog()) {
      <security-lookup-dialog
        [visibleDialog]="visibleSecurityLookupDialog()"
        [isin]="requireFormControl(configObject.isin).value"
        [currency]="requireFormControl(configObject.currency).value"
        [mic]="getSelectedStockexchangeMic()"
        [idAssetClass]="requireFormControl(configObject.assetClass).value"
        [tickerSymbol]="requireFormControl(configObject.tickerSymbol).value"
        (closeDialog)="handleCloseSecurityLookupDialog($event)" />
    }`,
  imports: [
    TranslateModule,
    DialogModule,
    TabsModule,
    DynamicFormComponent,
    SecuritysplitEditTableComponent,
    SecurityHistoryquotePeriodEditTableComponent,
    GtnetSecurityLookupDialogComponent,
    SecurityLookupDialogComponent,
    SecurityBondTermsComponent
  ]
})
export class SecurityEditComponent extends SecuritycurrencyEdit implements OnInit, CallbackValueChanged {
  protected readonly requireFormControl = requireFormControl;
  private messageToastService: MessageToastService = inject(MessageToastService);
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);
  private stockexchangeService: StockexchangeService = inject(StockexchangeService);
  private assetclassService: AssetclassService = inject(AssetclassService);
  private securityService: SecurityService = inject(SecurityService);
  private securitysplitService: SecuritysplitService = inject(SecuritysplitService);
  private historyquotePeriodService: HistoryquotePeriodService = inject(HistoryquotePeriodService);
  private gtnetSecurityLookupService: GtnetSecurityLookupService = inject(GtnetSecurityLookupService);

  readonly bondTerms = viewChild(SecurityBondTermsComponent);
  // Access child components
  readonly dynamicSplitForm = viewChild.required<DynamicFormComponent>('splitForm');
  readonly seetc = viewChild.required(SecuritysplitEditTableComponent);
  readonly dynamicPeriodPriceForm = viewChild.required<DynamicFormComponent>('periodPriceForm');
  readonly shpetc = viewChild.required(SecurityHistoryquotePeriodEditTableComponent);

  // Per-instrument limits sourced from the backend (gt.max.instrument.splits /
  // gt.max.instrument.historyquote.periods), loaded in loadHelperData(). The 20 defaults are a safe fallback while
  // the backend value is in flight and only matter if a user adds a row before the request resolves.
  readonly maxSplits = signal(20);
  readonly maxHistoryquotePeriods = signal(20);

  // Input from parent view
  readonly visibleEditSecurityDialog = input(false);

  // Tab management
  activeTabValue: string = 'security';

  securityEditSupport: SecurityEditSupport;
  formConfigPeriod: FormConfig;
  configSplit: FieldConfig[] = [];
  periodPrices: FieldConfig[] = [];
  configSplitObject: { [name: string]: FieldConfig };
  configPeriodPrices: { [name: string]: FieldConfig };
  private readonly selectedAssetClass = signal<Assetclass | null>(null);
  readonly canHaveSplits = computed(() =>
    Security.canHaveSplitConnector(this.selectedAssetClass(), this.securityEditSupport.hasMarketValue ?? true)
  );
  readonly canEditBondTerms = computed(() => Security.isBondDirectInvestment(this.selectedAssetClass()));
  readonly currentSecurityName = signal('');
  readonly currentSecurityCurrency = signal('');

  /** Visibility flag for GTNet security lookup dialog */
  readonly visibleGtnetLookupDialog = signal(false);

  /** Visibility flag for the security lookup at the data providers */
  readonly visibleSecurityLookupDialog = signal(false);

  readonly dataLoaded = signal(false);
  private connectorLoad: Subscription;

  private applyingIssuerCountryPrefill = false;
  private issuerCountryOverridden = false;
  private lastIssuerCountryPrefill: string | null = null;

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.securityEditSupport = new SecurityEditSupport(this.translateService, this.gps, this);
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.formConfigPeriod = AppHelper.getDefaultFormConfig(this.gps, 2, this.helpLinkPeriod.bind(this));

    this.config = SecurityEditSupport.getSecurityBaseFieldDefinition(SecurityDerived.Security, this.gps);

    // The lookup at the data providers follows the currency field; it is enabled once ISIN, currency and stock
    // exchange are set.
    const currencyFieldIndex = this.config.findIndex((fc) => fc.field === 'currency');
    if (currencyFieldIndex >= 0) {
      this.config.splice(
        currencyFieldIndex + 1,
        0,
        DynamicFieldHelper.createFunctionButtonFieldName(
          'securityLookup',
          'SECURITY_LOOKUP',
          () => this.openSecurityLookup(),
          { fieldsetName: 'BASE_DATA', disabled: true }
        )
      );
    }

    // Add GTNet lookup button after currency field if GTNet is enabled (invisible until we check for accessible peers)
    if (this.gps.useGtnet()) {
      const currencyIndex = this.config.findIndex((fc) => fc.field === 'currency');
      if (currencyIndex >= 0) {
        const gtnetButton = DynamicFieldHelper.createFunctionButtonFieldName(
          'gtnetLookup',
          'GTNET_SECURITY_LOOKUP',
          () => this.openGtnetLookup(),
          { fieldsetName: 'BASE_DATA', disabled: true }
        );
        gtnetButton.invisible = true; // Initially invisible, will be shown if accessible peers exist
        this.config.splice(currencyIndex + 1, 0, gtnetButton);
      }
    }

    this.connectorPriceFieldConfig = SecurityEditSupport.getIntraHistoryFieldDefinition(
      SecurityDerived.Security,
      this.gps
    );
    this.securityEditSupport.connectorDividendConfig = this.securityEditSupport.getDividendFieldDefinition();
    this.securityEditSupport.connectorSplitConfig = this.securityEditSupport.getSplitDefinition();
    this.config.push(
      ...this.connectorPriceFieldConfig,
      ...this.securityEditSupport.connectorDividendConfig,
      ...this.securityEditSupport.connectorSplitConfig
    );
    this.config.push(...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this));

    this.configSplit = [
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'splitDate', true),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'fromFactor', true, 1, 99_999_999),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'toFactor', true, 1, 99_999_999),
      DynamicFieldHelper.createSubmitButton('APPLY')
    ];

    this.periodPrices = [
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'fromDate', true),
      DynamicFieldHelper.createFieldInputNumber('price', 'CLOSE', true, 6, 10, false),
      DynamicFieldHelper.createSubmitButton('APPLY')
    ];

    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.configPeriodPrices = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.periodPrices);
    this.configSplitObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.configSplit);
    const frequencies = Object.keys(DistributionFrequency).filter((key) => Number.isNaN(Number(key)));
    this.translateService
      .get(frequencies)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((texts) => {
        this.configObject.distributionFrequency.valueKeyHtmlOptions = frequencies
          .map((key) => new ValueKeyHtmlSelectOptions(key, texts[key]))
          .sort((a, b) => a.value.localeCompare(b.value));
      });
  }

  /**
   * Connector field are hidden if stock exchange does not support quotes
   */
  valueChangedOnStockexchange(): void {
    requireFormControl(this.configObject.stockexchange)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((idStockexchange: number) => {
        this.setHasMarkedValue(idStockexchange);
        this.hideVisibleFeedConnectorsFields(
          [...this.connectorPriceFieldConfig],
          !this.securityEditSupport.hasMarketValue,
          FeedIdentifier.SECURITY
        );
        this.enableDisableDividendSplitConnector(Helper.getReferencedDataObject(this.configObject.assetClass, null));
        this.securityEditSupport.enableDisableDenominationStockexchangeAssetclass(this.configObject);
        this.reloadFeedConnectorsForContext();
      });
  }

  valueChangedOnDistributionFrequency(): void {
    requireFormControl(this.configObject.distributionFrequency)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((distributionFrequency) => {
        this.enableDisableDividendConnector(
          Helper.getReferencedDataObject(this.configObject.assetClass, null),
          distributionFrequency
        );
      });
  }

  valueChangedOnAssetClassExtend(assetClass: Assetclass | undefined): void {
    this.enableDisableDividendSplitConnector(assetClass);
    this.applySimulationFieldAvailability(assetClass);
    this.reloadFeedConnectorsForContext();
  }

  /**
   * Re-fetches the connector list whenever the stockexchange or asset class changes while the dialog is open, so the
   * intraday / history / dividend / split dropdowns reflect the new instrument context. Only active in
   * gt.force.connector.match mode 2 (where the backend pre-filters connectors) and only after the initial load has
   * completed — the guard on dataLoaded avoids a redundant fetch while the existing security is transferred into the
   * form, which triggers the same value-change subscriptions.
   */
  private reloadFeedConnectorsForContext(): void {
    this.connectorLoad?.unsubscribe();
    if (this.gpsGT.getForceConnectorMatch() !== 2 || !this.dataLoaded()) {
      return;
    }
    const idStockexchange: number = requireFormControl(this.configObject.stockexchange).value;
    const assetClass: Assetclass = Helper.getReferencedDataObject(this.configObject.assetClass, null);
    if (!idStockexchange || !assetClass) {
      return;
    }
    this.connectorLoad = this.securityService
      .getFeedConnectors(
        idStockexchange,
        <string>assetClass.categoryType,
        <string>assetClass.specialInvestmentInstrument
      )
      .pipe(this.untilDialogClosed())
      .subscribe((connectors: IFeedConnector[]) => {
        this.prepareFeedConnectors(connectors, false);
        this.prepareSplitDividendConnector(connectors);
        this.clearInvalidConnectorSelections();
      });
  }

  /**
   * Clears any selected connector that is no longer offered after the connector list was rebuilt for a changed
   * context. Without this an incompatible connector would silently remain selected; clearing it also drives the
   * connector value-change subscription to hide the associated url-extend field.
   */
  private clearInvalidConnectorSelections(): void {
    [
      this.configObject[this.ID_CONNECTOR_HISTORY],
      this.configObject[this.ID_CONNECTOR_INTRA],
      this.configObject[this.securityEditSupport.ID_CONNECTOR_DIVIDEND],
      this.configObject.idConnectorSplit
    ].forEach((fieldConfig) => {
      const selected = requireFormControl(fieldConfig).value;
      if (selected && !fieldConfig.valueKeyHtmlOptions?.some((option) => option.key === selected)) {
        requireFormControl(fieldConfig).setValue(null);
      }
    });
  }

  addSplit(value: { [name: string]: any }): void {
    const securitysplit = new Securitysplit();
    this.dynamicSplitForm().cleanMaskAndTransferValuesToBusinessObject(securitysplit);
    this.seetc().addDataRow(securitysplit);
    this.dynamicSplitForm().setDefaultValuesAndEnableSubmit();
  }

  addHistoryquotePeriod(value: { [name: string]: any }): void {
    const historyquotePeriod = new HistoryquotePeriod();
    this.dynamicPeriodPriceForm().cleanMaskAndTransferValuesToBusinessObject(historyquotePeriod);
    this.shpetc().addDataRow(historyquotePeriod);
    this.dynamicPeriodPriceForm().setDefaultValuesAndEnableSubmit();
  }

  submit(value: { [name: string]: any }): void {
    const security = this.securityEditSupport.prepareForSave(
      this,
      this.proposeChangeEntityWithEntity(),
      <Security>this.securityCurrencypairCallParam(),
      this.dynamicForm(),
      value
    );
    if (this.canEditBondTerms()) {
      if (!this.bondTerms()?.transfer(security)) {
        this.configObject.submit.disabled = false;
        return;
      }
    } else {
      security.simulationMetadata = null;
    }
    this.securityService
      .update(security)
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: (newSecurity) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
            i18nRecord: AppSettings.SECURITY.toUpperCase()
          });
          let savedDepending = false;
          if (this.securityEditSupport.hasMarketValue) {
            const seetc = this.seetc();
            if (this.canHaveSplits() && seetc) {
              savedDepending = true;
              seetc.save(newSecurity, requireFormControl(this.configObject[AuditHelper.NOTE_REQUEST_INPUT]).value);
            }
          } else {
            // Save historical periods
            const shpetc = this.shpetc();
            if (shpetc) {
              savedDepending = true;
              shpetc.save(newSecurity, requireFormControl(this.configObject[AuditHelper.NOTE_REQUEST_INPUT]).value);
            }
          }
          if (!savedDepending) {
            this.onDependingDialogSave(new SaveSecuritySuccess(newSecurity, true));
          }
        },
        error: () => (this.configObject.submit.disabled = false)
      });
  }

  onDependingDialogSave(saveSecuritySuccess: SaveSecuritySuccess): void {
    if (saveSecuritySuccess.success) {
      this.closeDialog.emit(
        new ProcessedActionData(
          this.securityCurrencypairCallParam() ? ProcessedAction.UPDATED : ProcessedAction.CREATED,
          saveSecuritySuccess.security
        )
      );
    } else {
      this.configObject.submit.disabled = false;
    }
  }

  onSelectedSecuritysplit(securitysplit: Securitysplit) {
    this.dynamicSplitForm().transferBusinessObjectToForm(securitysplit);
  }

  onSelectedHistoryquote(historyquotePeriod: HistoryquotePeriod) {
    this.dynamicPeriodPriceForm().transferBusinessObjectToForm(historyquotePeriod);
  }

  override onHide(event: unknown) {
    this.securityEditSupport.destroy();
    this.visibleGtnetLookupDialog.set(false);
    this.visibleSecurityLookupDialog.set(false);
    this.dataLoaded.set(false);
    this.selectedAssetClass.set(null);
    this.currentSecurityName.set('');
    this.currentSecurityCurrency.set('');
    this.issuerCountryOverridden = false;
    this.lastIssuerCountryPrefill = null;
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
    this.visibleGtnetLookupDialog.set(true);
  }

  /**
   * Handles the close of the GTNet lookup dialog and applies the selected security data to the form.
   */
  handleCloseGtnetLookupDialog(processedActionData: ProcessedActionData): void {
    this.visibleGtnetLookupDialog.set(false);
    if (processedActionData.action === ProcessedAction.CREATED && processedActionData.data) {
      this.applyGtnetLookupData(processedActionData.data as SecurityGtnetLookupDTO);
    }
  }

  /** Opens the lookup of the security at the data providers. */
  openSecurityLookup(): void {
    this.visibleSecurityLookupDialog.set(true);
  }

  /**
   * MIC of the stock exchange chosen in the form.
   *
   * @returns The MIC, or null when no stock exchange is chosen
   */
  getSelectedStockexchangeMic(): string | null {
    const idStockexchange: number = requireFormControl(this.configObject.stockexchange).value;
    const stockexchanges = this.configObject.stockexchange.referencedDataObject as Stockexchange[];
    return stockexchanges?.find((se) => se.idStockexchange === idStockexchange)?.mic ?? null;
  }

  /**
   * Handles the close of the security lookup dialog. The chosen listing and connectors are taken over through the same
   * path as a GTNet lookup, so both fill the form identically. Stock exchange and currency stay as entered.
   */
  handleCloseSecurityLookupDialog(processedActionData: ProcessedActionData): void {
    this.visibleSecurityLookupDialog.set(false);
    if (processedActionData.action === ProcessedAction.CREATED && processedActionData.data) {
      this.applyGtnetLookupData(this.toLookupDto(processedActionData.data as SecurityLookupSelection));
    }
  }

  /**
   * Converts the selection of the security lookup into the DTO the form takeover expects.
   *
   * @param selection - Chosen listing and connectors; a missing connector leaves that feed unchanged
   * @returns The DTO for applyGtnetLookupData
   */
  private toLookupDto(selection: SecurityLookupSelection): SecurityGtnetLookupDTO {
    const c = selection.candidate;
    return {
      isin: c.isin,
      currency: c.currency,
      // The name may come from another listing of the same instrument whose providers spell it better
      name: (selection.nameCandidate ?? c).name,
      tickerSymbol: c.tickerSymbol,
      categoryType: c.categoryType,
      specialInvestmentInstrument: c.specialInvestmentInstrument,
      matchedAssetClassId: c.matchedAssetClassId,
      stockexchangeMic: null,
      denomination: c.denomination,
      // A bond brings its coupon frequency; otherwise the frequency of the chosen dividend connector, or the one the
      // other dividend connectors agree on
      distributionFrequency:
        c.distributionFrequency ?? chooseDistributionFrequency(selection.dividend, c.dividendConnectors),
      productLink: c.productLink,
      stockexchangeLink: c.stockexchangeLink,
      activeFromDate: c.activeFromDate ? Helper.parseDateOnlyAsLocal(c.activeFromDate) : undefined,
      activeToDate: c.activeToDate ? Helper.parseDateOnlyAsLocal(c.activeToDate) : undefined,
      matchedHistoryConnector: selection.history?.idConnector,
      matchedHistoryUrlExtension: selection.history?.urlExtension,
      matchedIntraConnector: selection.intra?.idConnector,
      matchedIntraUrlExtension: selection.intra?.urlExtension,
      matchedDividendConnector: selection.dividend?.idConnector,
      matchedDividendUrlExtension: selection.dividend?.urlExtension,
      matchedSplitConnector: selection.split?.idConnector,
      matchedSplitUrlExtension: selection.split?.urlExtension
    };
  }

  /**
   * Applies data from GTNet lookup to the security form fields.
   * Maps enum values to local IDs for asset class and stock exchange.
   * Applies matched connector configurations and URL extensions when provided by backend.
   */
  private applyGtnetLookupData(dto: SecurityGtnetLookupDTO): void {
    // Set direct field values
    requireFormControl(this.configObject.name).setValue(dto.name);
    if (dto.isin) {
      requireFormControl(this.configObject.isin).setValue(dto.isin);
    }
    if (dto.tickerSymbol) {
      requireFormControl(this.configObject.tickerSymbol).setValue(dto.tickerSymbol);
      requireFormControl(this.configObject.tickerSymbol).markAsTouched();
    }
    if (dto.distributionFrequency) {
      requireFormControl(this.configObject.distributionFrequency).setValue(dto.distributionFrequency);
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
      this.configObject.activeFromDate?.formControl?.setValue(Helper.parseDateOnlyAsLocal(dto.activeFromDate));
    }
    if (dto.activeToDate) {
      this.configObject.activeToDate?.formControl?.setValue(Helper.parseDateOnlyAsLocal(dto.activeToDate));
    }
    if (dto.stockexchangeLink) {
      this.configObject.stockexchangeLink?.formControl?.setValue(dto.stockexchangeLink);
    }

    // Map asset class - prefer backend-matched ID (considers subCategoryNLS), fallback to enum matching
    if (dto.matchedAssetClassId) {
      // Backend already matched the asset class including subCategoryNLS consideration
      requireFormControl(this.configObject.assetClass).setValue(dto.matchedAssetClassId);
    } else if (dto.categoryType && dto.specialInvestmentInstrument) {
      // Fallback: match by enum values only
      const assetclasses = this.configObject.assetClass.referencedDataObject as Assetclass[];
      const matchingAssetclass = assetclasses?.find(
        (ac) =>
          ac.categoryType === dto.categoryType && ac.specialInvestmentInstrument === dto.specialInvestmentInstrument
      );
      if (matchingAssetclass) {
        requireFormControl(this.configObject.assetClass).setValue(matchingAssetclass.idAssetClass);
      }
    }

    // Map stock exchange by MIC code. The match is taken from the full list, so it may be one the dropdown does not
    // offer because the instrument is bound to stock exchanges of its own kind; such a value is not set.
    if (dto.stockexchangeMic) {
      const stockexchanges = this.configObject.stockexchange.referencedDataObject as Stockexchange[];
      const matchingExchange = stockexchanges?.find((se) => se.mic === dto.stockexchangeMic);
      if (
        matchingExchange?.idStockexchange != null &&
        this.securityEditSupport.isStockexchangeOffered(this.configObject, matchingExchange.idStockexchange)
      ) {
        requireFormControl(this.configObject.stockexchange).setValue(matchingExchange.idStockexchange);
      }
    }

    // Apply matched connector configurations
    this.applyMatchedConnectors(dto);
  }

  /**
   * Applies matched connector IDs and URL extensions from GTNet lookup.
   * These fields are populated by the backend after matching against local connectors.
   */
  private applyMatchedConnectors(dto: SecurityGtnetLookupDTO): void {
    // History connector
    if (dto.matchedHistoryConnector && this.configObject[this.ID_CONNECTOR_HISTORY]) {
      requireFormControl(this.configObject[this.ID_CONNECTOR_HISTORY]).setValue(dto.matchedHistoryConnector);
      if (dto.matchedHistoryUrlExtension && this.configObject.urlHistoryExtend) {
        requireFormControl(this.configObject.urlHistoryExtend).setValue(dto.matchedHistoryUrlExtension);
      }
    }

    // Intraday connector
    if (dto.matchedIntraConnector && this.configObject[this.ID_CONNECTOR_INTRA]) {
      requireFormControl(this.configObject[this.ID_CONNECTOR_INTRA]).setValue(dto.matchedIntraConnector);
      if (dto.matchedIntraUrlExtension && this.configObject.urlIntraExtend) {
        requireFormControl(this.configObject.urlIntraExtend).setValue(dto.matchedIntraUrlExtension);
      }
    }

    // Dividend connector
    if (dto.matchedDividendConnector && this.configObject[this.securityEditSupport.ID_CONNECTOR_DIVIDEND]) {
      requireFormControl(this.configObject[this.securityEditSupport.ID_CONNECTOR_DIVIDEND]).setValue(
        dto.matchedDividendConnector
      );
      if (dto.matchedDividendUrlExtension && this.configObject.urlDividendExtend) {
        requireFormControl(this.configObject.urlDividendExtend).setValue(dto.matchedDividendUrlExtension);
      }
    }

    // Split connector
    if (dto.matchedSplitConnector && this.configObject.idConnectorSplit) {
      requireFormControl(this.configObject.idConnectorSplit).setValue(dto.matchedSplitConnector);
      if (dto.matchedSplitUrlExtension && this.configObject.urlSplitExtend) {
        requireFormControl(this.configObject.urlSplitExtend).setValue(dto.matchedSplitUrlExtension);
      }
    }
  }

  /**
   * Subscribes to form value changes to enable/disable the GTNet lookup button.
   * Button is enabled when (ISIN or ticker) AND currency are filled.
   */
  /**
   * Enables the security lookup button once ISIN, currency, stock exchange and asset class are set. The asset class
   * comes first because its type decides which data providers and connectors the lookup asks.
   */
  private valueChangedOnSecurityLookupFields(): void {
    combineLatest([
      requireFormControl(this.configObject.isin).valueChanges.pipe(
        startWith(requireFormControl(this.configObject.isin).value)
      ),
      requireFormControl(this.configObject.currency).valueChanges.pipe(
        startWith(requireFormControl(this.configObject.currency).value)
      ),
      requireFormControl(this.configObject.stockexchange).valueChanges.pipe(
        startWith(requireFormControl(this.configObject.stockexchange).value)
      ),
      requireFormControl(this.configObject.assetClass).valueChanges.pipe(
        startWith(requireFormControl(this.configObject.assetClass).value)
      )
    ])
      .pipe(this.untilDialogClosed())
      .subscribe(([isin, currency, idStockexchange, idAssetClass]) => {
        this.configObject.securityLookup.disabled = !(
          isin?.trim().length > 0 &&
          currency?.trim().length > 0 &&
          idStockexchange &&
          idAssetClass
        );
      });
  }

  private valueChangedOnGtnetLookupFields(): void {
    if (this.configObject.gtnetLookup) {
      combineLatest([
        requireFormControl(this.configObject.isin).valueChanges.pipe(
          startWith(requireFormControl(this.configObject.isin).value)
        ),
        requireFormControl(this.configObject.tickerSymbol).valueChanges.pipe(
          startWith(requireFormControl(this.configObject.tickerSymbol).value)
        ),
        requireFormControl(this.configObject.currency).valueChanges.pipe(
          startWith(requireFormControl(this.configObject.currency).value)
        )
      ])
        .pipe(this.untilDialogClosed())
        .subscribe(([isin, ticker, currency]) => {
          const hasIdentifier = (isin && isin.trim().length > 0) || (ticker && ticker.trim().length > 0);
          const hasCurrency = currency && currency.trim().length > 0;
          this.configObject.gtnetLookup.disabled = !(hasIdentifier && hasCurrency);
        });
    }
  }

  protected override loadHelperData(): void {
    this.securityEditSupport.destroy();
    this.onDialogClosed(() => this.securityEditSupport.destroy());
    this.gpsGT
      .getMaxInstrumentLimits()
      .pipe(this.untilDialogClosed())
      .subscribe((limits) => {
        this.maxSplits.set(limits.maxInstrumentSplits);
        this.maxHistoryquotePeriods.set(limits.maxInstrumentHistoryquotePeriods);
      });
    this.securityEditSupport.registerValueOnChanged(SecurityDerived.Security, this.configObject);
    this.valueChangedOnStockexchange();
    this.valueChangedOnDistributionFrequency();
    this.valueChangedOnGtnetLookupFields();
    this.valueChangedOnSecurityLookupFields();
    this.valueChangedOnIssuerAndNameFields();
    this.hideVisibleFeedConnectorsFields(this.config, false, FeedIdentifier.SECURITY);

    const observables: Observable<
      | Stockexchange[]
      | ValueKeyHtmlSelectOptions[]
      | Assetclass[]
      | IFeedConnector[]
      | Securitysplit[]
      | HistoryquotePeriod[]
      | boolean
    >[] = [];
    observables.push(this.stockexchangeService.getAllStockexchanges(false));
    observables.push(this.gpsGT.getCurrencies());
    const securityCurrencypairCallParam = this.securityCurrencypairCallParam();
    const id = securityCurrencypairCallParam?.idSecuritycurrency;
    observables.push(
      id != null
        ? this.assetclassService.getPossibleAssetclassForExistingSecurityOrAll(id)
        : this.assetclassService.getAllAssetclass()
    );
    // Pre-filter the connector list only in gt.force.connector.match mode 2, and only when editing an existing
    // security (create mode has no stockexchange/asset class chosen yet — the save-time check still catches
    // incompatible picks).
    if (this.gpsGT.getForceConnectorMatch() === 2 && securityCurrencypairCallParam) {
      const existing = <Security>securityCurrencypairCallParam;
      observables.push(
        this.securityService.getFeedConnectors(
          existing.stockexchange?.idStockexchange,
          <string>existing.assetClass?.categoryType,
          <string>existing.assetClass?.specialInvestmentInstrument
        )
      );
    } else {
      observables.push(this.securityService.getFeedConnectors());
    }
    const idxCountries = observables.length;
    observables.push(this.gps.getCountriesForSelectBox());

    if (securityCurrencypairCallParam) {
      this.securityEditSupport.hasMarketValue =
        (<Security>securityCurrencypairCallParam).stockexchange?.noMarketValue !== true;
    }

    // The offer of stock exchanges is narrowed to the own kind once this instrument carries prices or transactions.
    let idxCategoryLocked = -1;
    if (id != null) {
      idxCategoryLocked = observables.length;
      observables.push(this.securityService.isStockexchangeCategoryLocked(id));
    }

    let idxSplitPeriod = -1;
    if (securityCurrencypairCallParam && id != null) {
      if (this.securityEditSupport.hasMarketValue) {
        // Only load security splits for an existing security
        if ((<Security>securityCurrencypairCallParam).splitPropose) {
          // Propose change
          this.seetc().setDataList((<Security>securityCurrencypairCallParam).splitPropose, true);
        } else {
          idxSplitPeriod = observables.length;
          observables.push(this.securitysplitService.getSecuritysplitsByIdSecuritycurrency(id));
        }
      } else {
        if ((<Security>securityCurrencypairCallParam).hpPropose) {
          this.shpetc().setDataList((<Security>securityCurrencypairCallParam).hpPropose, true);
        } else {
          idxSplitPeriod = observables.length;
          observables.push(this.historyquotePeriodService.getHistoryquotePeriodByIdSecuritycurrency(id));
        }
      }
    }

    combineLatest(observables)
      .pipe(this.untilDialogClosed())
      .subscribe((data: any[]) => {
        this.securityEditSupport.assignLoadedValues(
          this.configObject,
          <Stockexchange[]>data[0],
          <ValueKeyHtmlSelectOptions[]>data[1],
          <Assetclass[]>data[2]
        );
        this.restrictStockexchangeOptions(idxCategoryLocked >= 0 && <boolean>data[idxCategoryLocked]);

        this.prepareFeedConnectors(<IFeedConnector[]>data[3], false);
        this.prepareSplitDividendConnector(<IFeedConnector[]>data[3]);
        this.configObject.issuerCountry.groupItem = SelectOptionsHelper.createGroupItemsFromValueKeyHtmlSelectOptions(
          <ValueKeyHtmlSelectOptions[]>data[idxCountries],
          true
        );
        this.prepareExistingSecuritycurrency(this.configObject.name);
        const securityCurrencypairCallParamValue = this.securityCurrencypairCallParam();
        const isPrivatePaper =
          securityCurrencypairCallParamValue && (<Security>securityCurrencypairCallParamValue).idTenantPrivate !== null;
        requireFormControl(this.configObject.isTenantPrivate).setValue(isPrivatePaper);

        if (idxSplitPeriod >= 0) {
          if (this.securityEditSupport.hasMarketValue) {
            this.seetc().setDataList(<Securitysplit[]>data[idxSplitPeriod], false);
          } else {
            this.shpetc().setDataList(<HistoryquotePeriod[]>data[idxSplitPeriod], false);
          }
        }
        this.dataLoaded.set(true);
        this.currentSecurityName.set(requireFormControl(this.configObject.name).value ?? '');
        this.currentSecurityCurrency.set(requireFormControl(this.configObject.currency).value ?? '');
        this.issuerCountryOverridden = !!(<Security>this.securityCurrencypairCallParam())?.issuerCountry;
        this.disableEnableInputForExisting();
        const assetClass = this.securityEditSupport.disableEnableFieldsOnAssetclass(
          SecurityDerived.Security,
          this.configObject,
          requireFormControl(this.configObject.assetClass).value
        );
        this.applySimulationFieldAvailability(assetClass);
        this.prefillIssuerCountryFromIsin(requireFormControl(this.configObject.isin).value);
        this.securityEditSupport.setPrivatePaper(SecurityDerived.Security, isPrivatePaper, this.configObject);

        // Check for accessible GTNet peers and show lookup button if available
        this.checkGtnetLookupAvailability();
      });
  }

  /**
   * Narrows the offered stock exchanges to the kind the edited instrument already belongs to. The kind of a stock
   * exchange decides whether the prices of its instruments are history quotes or periods entered by the user, so an
   * instrument which already carries prices, or which was traded, must stay on its side of that boundary. A new
   * instrument, and one for which nothing was recorded and nothing was traded yet, is offered every stock exchange.
   *
   * @param categoryLocked whether the edited instrument is already bound to its kind of stock exchange
   */
  private restrictStockexchangeOptions(categoryLocked: boolean): void {
    const existing = this.securityCurrencypairCallParam();
    const noMarketValue = existing && 'stockexchange' in existing ? existing.stockexchange?.noMarketValue : undefined;
    this.securityEditSupport.setStockexchangeOptions(this.configObject, categoryLocked ? noMarketValue : null);
  }

  /**
   * Checks if GTNet security lookup is available and shows/hides the button accordingly.
   * The button is only shown if GTNet is enabled and there are accessible peers.
   */
  private checkGtnetLookupAvailability(): void {
    if (this.configObject.gtnetLookup && this.gps.useGtnet()) {
      this.gtnetSecurityLookupService
        .hasAccessiblePeers()
        .pipe(this.untilDialogClosed())
        .subscribe({
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
    this.splitDividendCreateValueKeyHtmlSelectOptions(
      this.configObject[this.securityEditSupport.ID_CONNECTOR_DIVIDEND],
      FeedSupport.FS_DIVIDEND
    );
  }

  private enableDisableDividendSplitConnector(assetClass: Assetclass | undefined): void {
    this.selectedAssetClass.set(assetClass ?? null);
    this.hideVisibleFeedConnectorsFields(this.securityEditSupport.connectorSplitConfig, !this.canHaveSplits(), null);
    this.enableDisableDividendConnector(assetClass, requireFormControl(this.configObject.distributionFrequency).value);
  }

  private enableDisableDividendConnector(assetClass: Assetclass | undefined, distributionFrequency: string): void {
    this.hideVisibleFeedConnectorsFields(
      this.securityEditSupport.connectorDividendConfig,
      !Security.canHaveDividendConnector(
        assetClass,
        !distributionFrequency || distributionFrequency === ''
          ? null
          : DistributionFrequency[distributionFrequency as keyof typeof DistributionFrequency],
        this.securityEditSupport.hasMarketValue
      ),
      null
    );
  }

  private valueChangedOnIssuerAndNameFields(): void {
    requireFormControl(this.configObject.isin)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((isin) => this.prefillIssuerCountryFromIsin(isin));
    requireFormControl(this.configObject.issuerCountry)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((issuerCountry) => {
        if (
          isManualIssuerCountryChange(
            this.dataLoaded(),
            this.applyingIssuerCountryPrefill,
            requireFormControl(this.configObject.issuerCountry).dirty,
            issuerCountry,
            this.lastIssuerCountryPrefill
          )
        ) {
          this.issuerCountryOverridden = true;
        }
      });
    requireFormControl(this.configObject.name)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((name) => this.currentSecurityName.set(name ?? ''));
    requireFormControl(this.configObject.currency)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((currency) => this.currentSecurityCurrency.set(currency ?? ''));
  }

  private applySimulationFieldAvailability(assetClass: Assetclass | undefined): void {
    this.selectedAssetClass.set(assetClass ?? null);
    const canHaveIssuerCountry = Security.canHaveIssuerCountry(assetClass);
    this.configObject.issuerCountry.invisible = !canHaveIssuerCountry;
    if (canHaveIssuerCountry) {
      requireFormControl(this.configObject.issuerCountry).enable({ emitEvent: false });
    } else {
      requireFormControl(this.configObject.issuerCountry).disable({ emitEvent: false });
    }
    if (canHaveIssuerCountry) {
      this.prefillIssuerCountryFromIsin(requireFormControl(this.configObject.isin).value);
    }
    this.applyDistributionFrequencyAvailability(assetClass);
    this.dynamicForm().notifier.notify();
  }

  /**
   * Hides the distribution frequency for a non-investable index and fixes it to DF_NONE. The disabled value is still
   * transferred on save, so the index is stored without a distribution frequency.
   */
  private applyDistributionFrequencyAvailability(assetClass: Assetclass | undefined): void {
    const distributionFrequency = this.configObject.distributionFrequency;
    const canHaveDistributionFrequency = Security.canHaveDistributionFrequency(assetClass);
    distributionFrequency.invisible = !canHaveDistributionFrequency;
    if (canHaveDistributionFrequency) {
      requireFormControl(distributionFrequency).enable({ emitEvent: false });
    } else {
      requireFormControl(distributionFrequency).setValue(DistributionFrequency[DistributionFrequency.DF_NONE], {
        emitEvent: false
      });
      requireFormControl(distributionFrequency).disable({ emitEvent: false });
    }
  }

  private prefillIssuerCountryFromIsin(isin: string): void {
    if (
      !this.dataLoaded() ||
      this.issuerCountryOverridden ||
      !Security.canHaveIssuerCountry(Helper.getReferencedDataObject(this.configObject.assetClass, null))
    ) {
      return;
    }
    const prefix = Security.issuerCountryFromIsin(isin);
    const offered = this.configObject.issuerCountry.groupItem?.some(
      (country) => String(country.key).toUpperCase() === prefix
    );
    const issuerCountry = offered ? prefix : null;
    const currentValue = requireFormControl(this.configObject.issuerCountry).value;
    if (!currentValue || currentValue === this.lastIssuerCountryPrefill) {
      this.setIssuerCountry(issuerCountry);
    }
  }

  private setIssuerCountry(issuerCountry: string | null): void {
    this.applyingIssuerCountryPrefill = true;
    requireFormControl(this.configObject.issuerCountry).setValue(issuerCountry);
    this.lastIssuerCountryPrefill = issuerCountry;
    this.applyingIssuerCountryPrefill = false;
  }

  private setHasMarkedValue(idStockexchange: number): void {
    const stockexchange = (<Stockexchange[]>this.configObject.stockexchange.referencedDataObject).find(
      (stockexchange: Stockexchange) => stockexchange.idStockexchange === +idStockexchange
    );
    this.securityEditSupport.hasMarketValue = !!stockexchange && !stockexchange.noMarketValue;
  }

  private disableEnableInputForExisting(): void {
    if (this.securityCurrencypairCallParam() !== null) {
      FormHelper.disableEnableFieldConfigsWhenAlreadySet(true, [
        this.configObject.isTenantPrivate,
        this.configObject.isin,
        this.configObject.currency
      ]);
    }
  }

  private splitDividendCreateValueKeyHtmlSelectOptions(fieldConfig: FieldConfig, filterType: FeedSupport): void {
    const provider: IFeedConnector[] = this.feedPriceConnectors.filter(
      (feedConnector) => !!feedConnector.securitycurrencyFeedSupport[FeedSupport[filterType]]
    );
    fieldConfig.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
      'id',
      'readableName',
      provider,
      true
    );
  }
}
