import {Security} from '../../entities/security';
import {ChangeDetectorRef, Directive, OnDestroy, ViewChild} from '@angular/core';
import {ActivatedRoute, Params, Router} from '@angular/router';
import {WatchlistService} from '../service/watchlist.service';
import {SecuritycurrencyGroup} from '../../entities/view/securitycurrency.group';
import {SecuritycurrencyPosition} from '../../entities/view/securitycurrency.position';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {Watchlist} from '../../entities/watchlist';
import {TransactionType} from '../../shared/types/transaction.type';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {AppHelper} from '../../lib/helper/app.helper';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {Currencypair} from '../../entities/currencypair';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {Securitycurrency} from '../../entities/securitycurrency';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {Subscription} from 'rxjs';
import {HelpIds} from '../../shared/help/help.ids';
import {TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {WatchlistSecurityExists} from '../../entities/dnd/watchlist.security.exists';
import {DynamicDialogHelper} from '../../shared/dynamicdialog/component/dynamicDialogHelper';
import {MailSendParam} from '../../shared/dynamicdialog/component/mail.send.dynamic.component';
import {DataType} from '../../dynamic-form/models/data.type';
import {AppSettings} from '../../shared/app.settings';
import {UDFGeneralCallParam} from '../../shared/udfmeta/model/udf.metadata';
import {SecurityUDFHelper} from '../../securitycurrency/component/security.udf.helper';
import {UDFMetadataHelper} from '../../shared/udfmeta/components/udf.metadata.helper';
import {WatchlistHelper} from './watchlist.helper';
import {AlarmSetupService} from '../../algo/service/alarm.setup.service';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {DynamicDialogs} from '../../shared/dynamicdialog/component/dynamic.dialogs';
import {BaseSettings} from '../../lib/base.settings';

@Directive()
export abstract class WatchlistTable extends TableConfigBase implements OnDestroy, IGlobalMenuAttach {
  public static readonly SINGLE = 'single';
  public static readonly MULTIPLE = 'multiple';

  WatchListType: typeof WatchListType = WatchListType;
  SpecialInvestmentInstruments: typeof SpecialInvestmentInstruments = SpecialInvestmentInstruments;
  securitycurrencyGroup: SecuritycurrencyGroup;
  securityPositionList: SecuritycurrencyPosition<Security | Currencypair>[];
  // For child transaction dialogs
  visibleSecurityTransactionDialog: boolean;
  transactionCallParam: TransactionCallParam;
  // For child Security dialogs
  visibleAddInstrumentDialog: boolean;
  visibleEditSecurityDialog: boolean;
  visibleEditCurrencypairDialog: boolean;
  visibleEditSecurityDerivedDialog: boolean;
  visibleUDFSecurityDialog: boolean;
  visibleUDFGeneralDialog: boolean;
  securityCallParam: Security;
  securityCurrencypairCallParam: Security | Securitycurrency;
  idTenant: number;
  loading: boolean;
  idWatchlist: number;
  paginator = false;
  intraUpdateTimoutSeconds: number;
  watchlist: Watchlist;
  readonly SECURITYCURRENCY_NAME = WatchlistHelper.SECURITYCURRENCY + '.name';
  contextMenuItems: MenuItem[] = [];
  timeFrames: TimeFrame[] = [];
  choosenTimeFrame: TimeFrame;
  tenantLimits: TenantLimit[];
  singleMultiSelection: SecuritycurrencyPosition<Security | Currencypair> | SecuritycurrencyPosition<Security | Currencypair>[];
  selectedSecuritycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>;
  @ViewChild('contextMenu') protected contextMenu: any;
  uDFGeneralCallParam: UDFGeneralCallParam;
  protected udfValuesMap = new Map<number, any>;
  private routeSubscribe: Subscription;
  private subscriptionWatchlistAdded: Subscription;
  private lazyMapHasUDF: { [idSecuritycurrency: number]: boolean } = {};

  protected constructor(public watchlistType: WatchListType,
    protected storeKey: string,
    protected dialogService: DialogService,
    protected alarmSetupService: AlarmSetupService,
    protected timeSeriesQuotesService: TimeSeriesQuotesService,
    protected dataChangedService: DataChangedService,
    protected activePanelService: ActivePanelService,
    protected watchlistService: WatchlistService,
    protected router: Router,
    protected activatedRoute: ActivatedRoute,
    protected confirmationService: ConfirmationService,
    protected messageToastService: MessageToastService,
    protected productIconService: ProductIconService,
    protected changeDetectionStrategy: ChangeDetectorRef,
    filterService: FilterService,
    translateService: TranslateService,
    private gpsGT: GlobalparameterGTService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    public selectMultiMode: string) {
    super(filterService, usersettingsService, translateService, gps);
    if (selectMultiMode === WatchlistTable.MULTIPLE) {
      this.singleMultiSelection = [];
    }
    this.multiSortMeta.push({field: 'securitycurrency.name', order: 1});
  }


  createSecurityPositionList(data: SecuritycurrencyGroup) {
    this.createTranslatedValueStoreAndFilterField(data.securityPositionList);
    this.securitycurrencyGroup = data;
    this.securityPositionList = data.securityPositionList;

    this.securitycurrencyGroup.currencypairPositionList.forEach((sp: SecuritycurrencyPosition<Currencypair>) => {
      const currencypairWatchlist: CurrencypairWatchlist = new CurrencypairWatchlist(sp.securitycurrency.fromCurrency,
        sp.securitycurrency.toCurrency);
      Object.assign(currencypairWatchlist, sp.securitycurrency);
      sp.securitycurrency = currencypairWatchlist;
      this.securityPositionList.push(sp);
    });

    this.translateFormulaToUserLanguage();
  }

  getInstrumentIcon(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>, field: ColumnConfig,
    valueField: any): string {


    const currencypair: Currencypair = securitycurrencyPosition.securitycurrency instanceof CurrencypairWatchlist ?
      securitycurrencyPosition.securitycurrency : null;
    return this.productIconService.getIconForInstrument(currencypair ? null : <Security>securitycurrencyPosition.securitycurrency,
      currencypair?.isCryptocurrency);
  }

  ngOnDestroy(): void {
    this.writeTableDefinition(this.storeKey);
    this.activePanelService.destroyPanel(this);
    this.subscriptionWatchlistAdded.unsubscribe();
    this.routeSubscribe.unsubscribe();
  }

  addExistingSecurity(event) {
    this.visibleAddInstrumentDialog = true;
  }

  removeInstrument(securityCurrency: Security | Currencypair) {
    this.watchlistService.removeSecuritycurrenciesFromWatchlist(this.idWatchlist, securityCurrency).subscribe(watchlist => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'REMOVED_SECURITY_FROM_WATCHLIST',
        {count: 1});
      this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.DELETED, new Watchlist()));
    });
  }

  removeSecuritiesAndCurrencypairs(selectedSecurityCurrencies: SecuritycurrencyPosition<Security | Currencypair>[]): void {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'REMOVE_INSTRUMENT_FROM_WATCHLIST_CONFIRM', () => {
        this.watchlistService.removeMultipleFromWatchlist(this.idWatchlist,
          selectedSecurityCurrencies.map(sc => sc.securitycurrency.idSecuritycurrency)).subscribe(count => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'REMOVED_SECURITY_FROM_WATCHLIST',
            {count});
          this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.DELETED, new Watchlist()));
        });
      });
  }

  removeAndDeleteSecuritycurrency(securityCurrency: Securitycurrency, domainKey: string) {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + domainKey, () => {
        this.watchlistService.removeSecuritycurrencyFromWatchlistAndDelete(this.idWatchlist, securityCurrency).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: domainKey});

          this.getWatchlistWithoutUpdate();
          this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.DELETED, new Watchlist()));
        });
      });
  }

  modifySecurityOrSecurityDerived(security: Security): void {
    if (security.idLinkSecuritycurrency) {
      this.modifyOrCreateAndAddSecurityDerived(security);
    } else {
      this.modifyOrCreateAndAddSecurity(security);
    }
  }

  modifyOrCreateAndAddSecurity(security: Security): void {
    this.securityCurrencypairCallParam = security;
    this.visibleEditSecurityDialog = true;
  }

  modifyOrCreateAndAddSecurityDerived(security: Security): void {
    this.securityCallParam = security;
    this.visibleEditSecurityDerivedDialog = true;
  }

  modifyOrCreateUDFData(securityCurrency: Securitycurrency): void {
    const udfValues = this.udfValuesMap.get(securityCurrency.idSecuritycurrency);
    if (securityCurrency instanceof CurrencypairWatchlist) {
      this.uDFGeneralCallParam = new UDFGeneralCallParam(AppSettings.CURRENCYPAIR, securityCurrency, udfValues, 'UDF_CURRENCYPAIR');
      this.visibleUDFGeneralDialog = true;
    } else {
      this.uDFGeneralCallParam = new UDFGeneralCallParam(AppSettings.SECURITY, securityCurrency, udfValues, 'UDF_SECURITY');
      this.visibleUDFSecurityDialog = true;
    }
  }

  modifyOrCreateAndAddCurrencypair(securityCurrency: Securitycurrency): void {
    this.securityCurrencypairCallParam = securityCurrency;
    this.visibleEditCurrencypairDialog = true;
  }

  handleTransaction(transactionType: TransactionType, security: Security) {
    this.transactionCallParam = Object.assign(new TransactionCallParam(), {
      transactionType,
      idSecuritycurrency: security.idSecuritycurrency,
      security: transactionType !== TransactionType.ACCUMULATE ? security : null
    });
    this.transactionCallParam.idWatchList = this.idWatchlist;
    const activeToDate: Date = new Date(security.activeToDate);
    this.transactionCallParam.defaultTransactionTime = activeToDate.getTime() < new Date().getTime() ? activeToDate : new Date();

    this.visibleSecurityTransactionDialog = true;
  }

  /**
   * Data of child was changed.
   */
  handleCloseTransactionDialog(processedActionData: ProcessedActionData): void {
    this.visibleSecurityTransactionDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.updateAllPrice();
    }
  }

  handleCloseAddInstrumentDialog(processedActionData: ProcessedActionData): void {
    this.visibleAddInstrumentDialog = false;
  }

  handleCloseEditSecuritycurrencyDialog(processedActionData: ProcessedActionData): void {
    this.visibleEditSecurityDialog = false;
    this.visibleEditCurrencypairDialog = false;
    this.visibleEditSecurityDerivedDialog = false;
    this.visibleUDFSecurityDialog = false;
    this.visibleUDFGeneralDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (processedActionData.action === ProcessedAction.CREATED) {
        this.watchlistService.addSecurityToWatchlist(this.idWatchlist, processedActionData.data).subscribe(watchlist => {
          this.updateAllPrice();
        });
      } else {
        this.updateAllPrice();
      }
    }
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  callMeDeactivate(): void {
  }

  hideContextMenu(): void {
    this.contextMenu && this.contextMenu.hide();
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_WATCHLIST;
  }

  onRightClick(event): void {
    //  this.isActivated() ? this.contextMenu.show() : this.hideContextMenu();
  }

  onComponentClick(event): void {
    if (!event[this.consumedGT]) {
      this.contextMenu && this.contextMenu.hide();
      this.resetMenu(this.getSSP(this.singleMultiSelection));
    }
  }

  isMarginProduct(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>): boolean {
    return BusinessHelper.isMarginProduct(<Security>securitycurrencyPosition.securitycurrency);
  }

  dragStart(event: DragEvent, data: SecuritycurrencyPosition<Security | Currencypair>) {
    this.changeDetectionStrategy.detach();
    event.dataTransfer.setData('text/plain',
      JSON.stringify(new WatchlistSecurityExists(this.watchlist.idWatchlist, data.securitycurrency.idSecuritycurrency)));
  }

  public dragEnd(event: DragEvent, item: any) {
    this.changeDetectionStrategy.reattach();
  }

  protected addBaseColumns(): void {
    this.addColumn(DataType.String, this.SECURITYCURRENCY_NAME, 'NAME', true, false,
      {width: 200, frozenColumn: true, templateName: AppSettings.OWNER_TEMPLATE});
    this.addColumn(DataType.String, 'securitycurrency', AppSettings.INSTRUMENT_HEADER, true, false,
      {fieldValueFN: this.getInstrumentIcon.bind(this), templateName: 'icon', width: 20});
    this.addColumnFeqH(DataType.String, WatchlistHelper.SECURITYCURRENCY + '.isin', true, true, {width: 90});
    this.addColumnFeqH(DataType.String, WatchlistHelper.SECURITYCURRENCY + '.tickerSymbol', true, true);
    this.addColumnFeqH(DataType.String, WatchlistHelper.SECURITYCURRENCY + '.currency', true, true);
  }

  /**
   * Loading the specific basic data of the watchlist; price data, for example, does not have to be loaded.
   */
  protected abstract getWatchlistWithoutUpdate(): void;

  /**
   * This method is used if the data needs to be reloaded. For example, if the watchlist has been changed by the user
   * or if additional price data is loaded.
   */
  protected abstract updateAllPrice(): void;

  protected watchlistHasModifiedFromOutside(): void {
    this.subscriptionWatchlistAdded = this.dataChangedService.dateChanged$.subscribe(processedActionData => {
      if (processedActionData.data instanceof Watchlist && processedActionData.action === ProcessedAction.UPDATED) {
        this.getWatchlistWithoutUpdate();
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'ADDED_SECURITY_TO_WATCHLIST');
      } else if (processedActionData.data instanceof Watchlist && processedActionData.action === ProcessedAction.DELETED) {
        this.getWatchlistWithoutUpdate();
      }
    });
  }

  protected init(): void {
    this.idTenant = this.gps.getIdTenant();
    this.activePanelService.registerPanel(this);
    this.loading = true;
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      if (this.watchlist) {
        // not first time
        this.writeTableDefinition(this.storeKey);
      } else {
        //  first time
        this.gpsGT.getIntraUpdateTimeout()
          .subscribe((updateTimeout: number) => this.intraUpdateTimoutSeconds = updateTimeout);
        this.readTableDefinition(this.storeKey);
      }

      this.idWatchlist = +params['id'];
      this.activePanelService.activatePanel(this, {
        showMenu: this.getShowMenu(this.selectedSecuritycurrencyPosition),
        editMenu: this.getEditMenu(this.selectedSecuritycurrencyPosition)
      });
      this.watchlist = JSON.parse(params[AppSettings.WATCHLIST.toLowerCase()]);
    });
  }

  protected getShowMenu(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>): MenuItem[] {
    const menuItems = [...this.getShowContextMenuItems(securitycurrencyPosition, false), {separator: true},
      ...this.getMenuShowOptions()];
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  protected getShowContextMenuItems(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>,
    translate: boolean): MenuItem[] {
    let menuItems: MenuItem[] = [];

    if (securitycurrencyPosition) {
      const isCurrencypair = this.selectedSecuritycurrencyPosition.securitycurrency instanceof CurrencypairWatchlist;
      const optionalParameters = {
        noMarketValue: !isCurrencypair
          && (<Security>this.selectedSecuritycurrencyPosition.securitycurrency).stockexchange.noMarketValue
      };
      menuItems = this.timeSeriesQuotesService.getMenuItems(this.selectedSecuritycurrencyPosition.securitycurrency.idSecuritycurrency,
        isCurrencypair ? null : (<Security>this.selectedSecuritycurrencyPosition.securitycurrency).currency,
        true, optionalParameters);

      menuItems.push(...BusinessHelper.getUrlLinkMenus(securitycurrencyPosition.securitycurrency));
      menuItems.push(
        {
          label: '_INTRADAY_URL',
          command: (e) => this.getDownloadLinkHistoricalIntra(securitycurrencyPosition.intradayUrl,
            'intra', securitycurrencyPosition, true),
          disabled: !securitycurrencyPosition.intradayUrl
        }
      );
      menuItems.push(
        {
          label: '_HISTORICAL_URL',
          command: (e) => this.getDownloadLinkHistoricalIntra(securitycurrencyPosition.historicalUrl,
            'historical', securitycurrencyPosition, false),
          disabled: !securitycurrencyPosition.historicalUrl
        }
      );
      menuItems.push(...this.alarmSetupService.getMenuItem(securitycurrencyPosition.securitycurrency));
      menuItems.push({separator: true});
      menuItems.push({
        label: '_MAIL_TO_CREATOR' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: (e) => this.mailToCreator(securitycurrencyPosition.securitycurrency)
      });
    }
    translate && TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  private getDownloadLinkHistoricalIntra(url: string, targetPage: string, securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>,
    isIntra: boolean): void {
    WatchlistHelper.getDownloadLinkHistoricalIntra(url, targetPage, securitycurrencyPosition.securitycurrency, isIntra, this.watchlistService);
  }

  protected getEditMenuItems(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>): MenuItem[] {
    const menuItems: MenuItem[] = [];

    menuItems.push(
      {
        label: 'ADD_EXISTING_SECURITY' + BaseSettings.DIALOG_MENU_SUFFIX, command: (e) => this.addExistingSecurity(e),
        disabled: this.reachedWatchlistLimits()
      }
    );

    if (Array.isArray(this.singleMultiSelection) && this.singleMultiSelection.length > 1) {
      menuItems.push({separator: true});
      menuItems.push(
        {
          label: 'REMOVE_SELECTED_INSTRUMENTS',
          command: (e) => this.removeSecuritiesAndCurrencypairs(
            this.singleMultiSelection as SecuritycurrencyPosition<Security | Currencypair>[])
        }
      );
    }

    if (securitycurrencyPosition) {
      menuItems.push(
        {
          label: 'REMOVE_INSTRUMENT',
          command: (e) => this.removeInstrument(securitycurrencyPosition.securitycurrency)
        }
      );
      menuItems.push(
        {
          label: '_UPDATE_INTRADAY', command: (e) => this.handleUpdateAllPrice(),
          disabled: !this.securitycurrencyGroup || !this.intraUpdateTimoutSeconds
        }
      );
    }
    menuItems.push({separator: true});
    menuItems.push(
      {
        label: 'CREATE_AND_ADD_SECURITY' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: (e) => this.modifyOrCreateAndAddSecurity(null),
        disabled: this.reachedWatchlistLimits()
      }
    );
    menuItems.push(
      {
        label: 'CREATE_AND_ADD_SECURITY_DERIVED' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: (e) => this.modifyOrCreateAndAddSecurityDerived(null),
        disabled: this.reachedWatchlistLimits()
      }
    );
    menuItems.push(
      {
        label: 'EDIT_SECURITY_UDF' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: (e) => this.modifyOrCreateUDFData(securitycurrencyPosition.securitycurrency),
        disabled: !securitycurrencyPosition || !this.enableMenuItemUDF(securitycurrencyPosition.securitycurrency)
      }
    );
    if (securitycurrencyPosition && !(securitycurrencyPosition.securitycurrency instanceof CurrencypairWatchlist)) {
      menuItems.push(
        {
          label: 'REMOVE_DELETE_INSTRUMENT',
          command: (e) => this.removeAndDeleteSecuritycurrency(<Security>securitycurrencyPosition.securitycurrency,
            AppSettings.SECURITY.toUpperCase()), disabled: securitycurrencyPosition.isUsedElsewhere
            || (!AuditHelper.hasHigherPrivileges(this.gps) && (!!(<Security>securitycurrencyPosition.securitycurrency).idTenantPrivate
              && (<Security>securitycurrencyPosition.securitycurrency).idTenantPrivate !== this.gps.getIdTenant()))
        }
      );

      menuItems.push(
        {
          label: 'EDIT_RECORD|INSTRUMENT' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: (e) => this.modifySecurityOrSecurityDerived(<Security>securitycurrencyPosition.securitycurrency)
        }
      );

      if ((<Security>securitycurrencyPosition.securitycurrency).assetClass.specialInvestmentInstrument
        !== SpecialInvestmentInstruments[SpecialInvestmentInstruments.NON_INVESTABLE_INDICES]) {
        menuItems.push({separator: true});

        menuItems.push({
          label: 'ACCUMULATE' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: (e) => this.handleTransaction(TransactionType.ACCUMULATE,
            <Security>securitycurrencyPosition.securitycurrency)
        });

        menuItems.push({
          label: 'REDUCE' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: (e) => (securitycurrencyPosition) ? this.handleTransaction(TransactionType.REDUCE,
            <Security>securitycurrencyPosition.securitycurrency) : null,
          disabled: (securitycurrencyPosition.units === null || securitycurrencyPosition.units === 0)
            && !this.isMarginProduct(securitycurrencyPosition)
        });

        if (!this.isMarginProduct(securitycurrencyPosition)) {
          menuItems.push({
            label: AppSettings.DIVIDEND.toUpperCase() + BaseSettings.DIALOG_MENU_SUFFIX,
            command: (e) => this.handleTransaction(TransactionType.DIVIDEND,
              <Security>securitycurrencyPosition.securitycurrency)
          });
        }
      }
    }

    menuItems.push({separator: true});
    menuItems.push(
      {
        label: 'CREATE_AND_ADD_CURRENCYPAIR' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: (e) => this.modifyOrCreateAndAddCurrencypair(null),
        disabled: this.reachedWatchlistLimits()
      }
    );
    if (securitycurrencyPosition) {
      if (securitycurrencyPosition.securitycurrency instanceof CurrencypairWatchlist) {
        menuItems.push(
          {
            label: 'EDIT_RECORD|CURRENCYPAIR' + BaseSettings.DIALOG_MENU_SUFFIX,
            command: (e) => this.modifyOrCreateAndAddCurrencypair(securitycurrencyPosition.securitycurrency)
          }
        );
        menuItems.push(
          {
            label: 'REMOVE_DELETE_CURRENCYPAIR',
            command: (e) => this.removeAndDeleteSecuritycurrency(<Security>securitycurrencyPosition.securitycurrency,
              AppSettings.CURRENCYPAIR.toUpperCase()), disabled: securitycurrencyPosition.isUsedElsewhere
          }
        );
      }
    }
    return menuItems;
  }

  private enableMenuItemUDF(securitycurreny: Securitycurrency): boolean {
    const key = securitycurreny instanceof CurrencypairWatchlist? -1: securitycurreny.idSecuritycurrency;
    let hasUDF = this.lazyMapHasUDF[key];
    if (this.lazyMapHasUDF[key] === undefined) {
      const fd = securitycurreny instanceof CurrencypairWatchlist
        ? UDFMetadataHelper.getFieldDescriptorByEntity(AppSettings.CURRENCYPAIR)
        : SecurityUDFHelper.getFieldDescriptorInputAndShowExtendedSecurity((<Security>securitycurreny).assetClass, true);
      hasUDF = this.lazyMapHasUDF[key] = fd.length > 0;
    }
    return hasUDF;
  }

  private translateFormulaToUserLanguage(): void {
    if (this.gps.getDecimalSymbol() !== '.') {
      this.securitycurrencyGroup.securityPositionList.filter(sp => sp.securitycurrency.formulaPrices)
        .map(sp => sp.securitycurrency.formulaPrices = sp.securitycurrency.formulaPrices.split('.')
          .join(this.gps.getDecimalSymbol()));
    }
  }

  private getSSP(singleMultiSelection: SecuritycurrencyPosition<Security | Currencypair>
    | SecuritycurrencyPosition<Security | Currencypair>[]): SecuritycurrencyPosition<Security | Currencypair> {
    if (Array.isArray(singleMultiSelection)) {
      return singleMultiSelection.length === 1 ? singleMultiSelection[0] : null;
    } else {
      return singleMultiSelection as SecuritycurrencyPosition<Security | Currencypair>;
    }
  }

  private mailToCreator(securitycurrency: Securitycurrency): void {
    const subject = securitycurrency instanceof CurrencypairWatchlist ? (<CurrencypairWatchlist>securitycurrency).name
      : (<Security>securitycurrency).name;
    DynamicDialogs.getOpenedMailSendComponent(this.translateService, this.dialogService,
      new MailSendParam(securitycurrency.createdBy, null, subject));
  }

  private handleUpdateAllPrice() {
    if (Date.now() < +this.securitycurrencyGroup.lastTimestamp + this.intraUpdateTimoutSeconds * 1000) {
      const minutes = this.millisToMinutesAndSeconds(+this.securitycurrencyGroup.lastTimestamp
        + this.intraUpdateTimoutSeconds * 1000 - Date.now());
      this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'UPDATE_TIMEOUT', {time: minutes});
    } else {
      this.updateAllPrice();
    }
  }

  private millisToMinutesAndSeconds(millis: number): string {
    const minutes: number = Math.floor(millis / 60000);
    const seconds: number = +((millis % 60000) / 1000).toFixed(0);
    return minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
  }

  private getEditMenu(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>): MenuItem[] {
    const menuItems: MenuItem[] = this.getEditMenuItems(securitycurrencyPosition);
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  private reachedWatchlistLimits(): boolean {
    if (this.tenantLimits) {
      for (const tenantLimit of this.tenantLimits) {
        if (tenantLimit.actual >= tenantLimit.limit) {
          return true;
        }
      }
    }
    return false;
  }

  private resetMenu(selectedSecuritycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>) {
    this.selectedSecuritycurrencyPosition = selectedSecuritycurrencyPosition;
    this.contextMenuItems = [...this.getEditMenu(selectedSecuritycurrencyPosition),
      ...this.getShowContextMenuItems(selectedSecuritycurrencyPosition, true)];
    this.activePanelService.activatePanel(this, {
      showMenu: this.getShowMenu(selectedSecuritycurrencyPosition),
      editMenu: this.getEditMenu(selectedSecuritycurrencyPosition)
    });
  }

}

export class TimeFrame {
  constructor(public name: string, public days: number) {
  }
}

export enum WatchListType {
  PERFORMANCE,
  PRICE_FEED,
  UDF,
  DIVIDEND_SPLIT_FEED
}
