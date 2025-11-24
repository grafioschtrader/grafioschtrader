import {Security} from '../../entities/security';
import {ChangeDetectorRef, Directive, OnDestroy, ViewChild} from '@angular/core';
import {ActivatedRoute, Params, Router} from '@angular/router';
import {WatchlistService} from '../service/watchlist.service';
import {SecuritycurrencyGroup} from '../../entities/view/securitycurrency.group';
import {SecuritycurrencyPosition} from '../../entities/view/securitycurrency.position';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {Watchlist} from '../../entities/watchlist';
import {TransactionType} from '../../shared/types/transaction.type';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {AppHelper} from '../../lib/helper/app.helper';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {Currencypair} from '../../entities/currencypair';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {DataChangedService} from '../../lib/maintree/service/data.changed.service';
import {Securitycurrency} from '../../entities/securitycurrency';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {Subscription} from 'rxjs';
import {HelpIds} from '../../lib/help/help.ids';
import {TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {TenantLimit} from '../../shared/types/tenant.limit';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {WatchlistSecurityExists} from '../../entities/dnd/watchlist.security.exists';
import {MailSendParam} from '../../lib/dynamicdialog/component/mail.send.dynamic.component';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {AppSettings} from '../../shared/app.settings';
import {UDFGeneralCallParam} from '../../lib/udfmeta/model/udf.metadata';
import {SecurityUDFHelper} from '../../securitycurrency/component/security.udf.helper';
import {UDFMetadataHelper} from '../../lib/udfmeta/components/udf.metadata.helper';
import {WatchlistHelper} from './watchlist.helper';
import {AlarmSetupService} from '../../algo/service/alarm.setup.service';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {DynamicDialogs} from '../../lib/dynamicdialog/component/dynamic.dialogs';
import {BaseSettings} from '../../lib/base.settings';

/**
 * Abstract base class for watchlist table components that provides comprehensive functionality for displaying
 * and managing securities and currency pairs in tabular format. Handles CRUD operations, context menus,
 * transaction dialogs, drag-and-drop, and various editing capabilities.
 */
@Directive()
export abstract class WatchlistTable extends TableConfigBase implements OnDestroy, IGlobalMenuAttach {

  /**
   * Key-value mapping of feed connector IDs to human-readable names.
   * Used for displaying user-friendly connector names in the UI instead of technical IDs.
   */
  feedConnectorsKV: { [id: string]: string } = {};

  /** Single selection mode constant. */
  public static readonly SINGLE = 'single';
  /** Multiple selection mode constant. */
  public static readonly MULTIPLE = 'multiple';
  /** Enum reference for watchlist types used in templates. */
  WatchListType: typeof WatchListType = WatchListType;
  /** Enum reference for special investment instruments used in templates. */
  SpecialInvestmentInstruments: typeof SpecialInvestmentInstruments = SpecialInvestmentInstruments;
  /** Security and currency group data containing positions and metadata. */
  securitycurrencyGroup: SecuritycurrencyGroup;
  /** List of security and currency positions displayed in the table. */
  securityPositionList: SecuritycurrencyPosition<Security | Currencypair>[];
  /** Controls visibility of the security transaction dialog. */
  visibleSecurityTransactionDialog: boolean;
  /** Parameters for the transaction dialog. */
  transactionCallParam: TransactionCallParam;
  /** Controls visibility of the add instrument dialog. */
  visibleAddInstrumentDialog: boolean;
  /** Controls visibility of the security edit dialog. */
  visibleEditSecurityDialog: boolean;
  /** Controls visibility of the currency pair edit dialog. */
  visibleEditCurrencypairDialog: boolean;
  /** Controls visibility of the derived security edit dialog. */
  visibleEditSecurityDerivedDialog: boolean;

  /** Controls visibility of the UDF security edit dialog. */
  visibleUDFSecurityDialog: boolean;

  /** Controls visibility of the UDF general edit dialog. */
  visibleUDFGeneralDialog: boolean;

  /**
   * Controls the visibility of the dialog for adding instruments with price data problems.
   * When true, displays a dialog allowing users to add instruments that have feed issues.
   */
  public visibleAddPriceProblemDialog = false;

  /** Security parameter for derived security dialogs. */
  securityCallParam: Security;

  /** Security or currency parameter for edit dialogs. */
  securityCurrencypairCallParam: Security | Securitycurrency;

  /** Current tenant ID. */
  idTenant: number;

  /** Loading state indicator. */
  loading: boolean;

  /** Current watchlist ID. */
  idWatchlist: number;

  /** Pagination enabled flag. */
  paginator = false;

  /** Intraday update timeout in seconds. */
  intraUpdateTimoutSeconds: number;

  /** Current watchlist object. */
  watchlist: Watchlist;

  /** Constant for security currency name field path. */
  readonly SECURITYCURRENCY_NAME = WatchlistHelper.SECURITYCURRENCY + '.name';

  /** Context menu items for the table. */
  contextMenuItems: MenuItem[] = [];

  /** Available time frames for performance views. */
  timeFrames: TimeFrame[] = [];

  /** Currently selected time frame. */
  choosenTimeFrame: TimeFrame;

  /** Tenant limits for securities and currencies. */
  tenantLimits: TenantLimit[];

  /** Single or multiple selection state. */
  singleMultiSelection: SecuritycurrencyPosition<Security | Currencypair> | SecuritycurrencyPosition<Security | Currencypair>[];

  /** Currently selected security currency position. */
  selectedSecuritycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>;

  /** Reference to the context menu component. */
  @ViewChild('contextMenu') protected contextMenu: any;

  /** Parameters for UDF general dialogs. */
  uDFGeneralCallParam: UDFGeneralCallParam;

  /** Map of UDF values by security currency ID. */
  protected udfValuesMap = new Map<number, any>;

  /** Subscription to route parameter changes. */
  private routeSubscribe: Subscription;

  /** Subscription to watchlist modification events. */
  private subscriptionWatchlistAdded: Subscription;

  /** Cache for UDF availability by security currency ID. */
  private lazyMapHasUDF: { [idSecuritycurrency: number]: boolean } = {};

  /**
   * Creates an instance of WatchlistTable.
   *
   * @param {WatchListType} watchlistType - Type of watchlist (performance, price feed, etc.)
   * @param {string} storeKey - Local storage key for table settings
   * @param {DialogService} dialogService - PrimeNG dialog service
   * @param {AlarmSetupService} alarmSetupService - Service for alarm setup functionality
   * @param {TimeSeriesQuotesService} timeSeriesQuotesService - Service for time series operations
   * @param {DataChangedService} dataChangedService - Service for data change notifications
   * @param {ActivePanelService} activePanelService - Service for active panel management
   * @param {WatchlistService} watchlistService - Service for watchlist operations
   * @param {Router} router - Angular router for navigation
   * @param {ActivatedRoute} activatedRoute - Current activated route
   * @param {ConfirmationService} confirmationService - PrimeNG confirmation dialog service
   * @param {MessageToastService} messageToastService - Service for toast notifications
   * @param {ProductIconService} productIconService - Service for product icons
   * @param {ChangeDetectorRef} changeDetectionStrategy - Angular change detection reference
   * @param {FilterService} filterService - PrimeNG filter service
   * @param {TranslateService} translateService - Angular translation service
   * @param {GlobalparameterGTService} gpsGT - GT-specific global parameters service
   * @param {GlobalparameterService} gps - Global parameters service
   * @param {UserSettingsService} usersettingsService - User settings service
   * @param {string} selectMultiMode - Selection mode (single or multiple)
   */
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
    public selectMultiMode: 'single' | 'multiple' ) {
    super(filterService, usersettingsService, translateService, gps);
    if (selectMultiMode === WatchlistTable.MULTIPLE) {
      this.singleMultiSelection = [];
    }
    this.multiSortMeta.push({field: 'securitycurrency.name', order: 1});
  }


  /**
   * Creates security position list from security currency group data and handles currency pair transformations.
   *
   * @param {SecuritycurrencyGroup} data - Security currency group containing separate lists of security and currency positions
   */
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

  /**
   * Gets the appropriate icon for a security or currency pair instrument.
   *
   * @param {SecuritycurrencyPosition<Security | Currencypair>} securitycurrencyPosition - Position containing the instrument to get icon for
   * @param {ColumnConfig} field - Column configuration object containing display settings
   * @param {any} valueField - Current field value (not used in this implementation)
   * @returns {string} Icon name/path for the instrument type
   */
  getInstrumentIcon(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>, field: ColumnConfig,
    valueField: any): string {
    const currencypair: Currencypair = securitycurrencyPosition.securitycurrency instanceof CurrencypairWatchlist ?
      securitycurrencyPosition.securitycurrency : null;
    return this.productIconService.getIconForInstrument(currencypair ? null : <Security>securitycurrencyPosition.securitycurrency,
      currencypair?.isCryptocurrency);
  }

  /** Cleans up subscriptions and saves table configuration on component destruction. */
  ngOnDestroy(): void {
    this.writeTableDefinition(this.storeKey);
    this.activePanelService.destroyPanel(this);
    this.subscriptionWatchlistAdded.unsubscribe();
    this.routeSubscribe.unsubscribe();
  }

  /**
   * Opens the add existing security dialog.
   *
   * @param event - Click event that triggered the action (event details not used)
   */
  addExistingSecurity(event) {
    this.visibleAddInstrumentDialog = true;
  }

  /**
   * Removes a single instrument from the watchlist.
   *
   * @param {Security | Currencypair} securityCurrency - Security or currency pair entity to remove from watchlist
   */
  removeInstrument(securityCurrency: Security | Currencypair) {
    this.watchlistService.removeSecuritycurrenciesFromWatchlist(this.idWatchlist, securityCurrency).subscribe(watchlist => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'REMOVED_SECURITY_FROM_WATCHLIST',
        {count: 1});
      this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.DELETED, new Watchlist()));
    });
  }

  /**
   * Removes multiple selected securities and currency pairs from the watchlist with confirmation.
   *
   * @param {SecuritycurrencyPosition<Security | Currencypair>[]} selectedSecurityCurrencies - Array of selected positions to remove
   */
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

  /**
   * Removes and deletes a security or currency from both watchlist and system with confirmation.
   *
   * @param {Securitycurrency} securityCurrency - Security or currency entity to remove and permanently delete
   * @param {string} domainKey - Translation key for the entity type used in confirmation messages
   */
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

  /**
   * Determines whether to edit a regular security or derived security based on link presence.
   *
   * @param {Security} security - Security entity to check for derived security properties
   */
  modifySecurityOrSecurityDerived(security: Security): void {
    if (security.idLinkSecuritycurrency) {
      this.modifyOrCreateAndAddSecurityDerived(security);
    } else {
      this.modifyOrCreateAndAddSecurity(security);
    }
  }

  /**
   * Opens the security edit dialog for creating or modifying a security.
   *
   * @param {Security} security - Security entity to edit, or null to create a new security
   */
  modifyOrCreateAndAddSecurity(security: Security): void {
    this.securityCurrencypairCallParam = security;
    this.visibleEditSecurityDialog = true;
  }

  /**
   * Opens the derived security edit dialog for creating or modifying a derived security.
   *
   * @param {Security} security - Derived security entity to edit, or null to create a new derived security
   */
  modifyOrCreateAndAddSecurityDerived(security: Security): void {
    this.securityCallParam = security;
    this.visibleEditSecurityDerivedDialog = true;
  }

  /**
   * Opens the appropriate UDF edit dialog based on security currency type.
   *
   * @param {Securitycurrency} securityCurrency - Security or currency entity to edit user-defined field data for
   */
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

  /**
   * Opens the currency pair edit dialog for creating or modifying a currency pair.
   *
   * @param {Securitycurrency} securityCurrency - Currency pair entity to edit, or null to create a new currency pair
   */
  modifyOrCreateAndAddCurrencypair(securityCurrency: Securitycurrency): void {
    this.securityCurrencypairCallParam = securityCurrency;
    this.visibleEditCurrencypairDialog = true;
  }

  /**
   * Prepares and opens the transaction dialog for the specified transaction type and security.
   *
   * @param {TransactionType} transactionType - Type of transaction to create (ACCUMULATE, REDUCE, DIVIDEND, etc.)
   * @param {Security} security - Security entity for which to create the transaction
   */
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
   * Handles transaction dialog close event and updates prices if changes were made.
   *
   * @param {ProcessedActionData} processedActionData - Result data containing action type and any created/modified data
   */
  handleCloseTransactionDialog(processedActionData: ProcessedActionData): void {
    this.visibleSecurityTransactionDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.updateAllPrice();
    }
  }

  /**
   * Handles the closing of the add price problem instrument dialog.
   * Hides the dialog and triggers a complete price data update to reflect any changes.
   *
   * @param processedActionData - Data about the action performed in the dialog
   */
  handleCloseAddPriceProblemInstrument(processedActionData: ProcessedActionData): void {
  }

  /**
   * Handles add instrument dialog close event.
   *
   * @param {ProcessedActionData} processedActionData - Result data from the add instrument dialog operation
   */
  handleCloseAddInstrumentDialog(processedActionData: ProcessedActionData): void {
    this.visibleAddInstrumentDialog = false;
  }

  /**
   * Handles security/currency edit dialog close event and manages post-edit actions.
   *
   * @param {ProcessedActionData} processedActionData - Result data containing action type and any modified entity data
   */
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

  /** Returns whether this component is currently the active panel. */
  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  /** Called when component is deactivated (empty implementation for subclasses to override). */
  callMeDeactivate(): void {
  }


  /** Hides the context menu if it exists. */
  hideContextMenu(): void {
    this.contextMenu && this.contextMenu.hide();
  }

  /** Returns the help context ID for this component. */
  public getHelpContextId(): string {
    return HelpIds.HELP_WATCHLIST;
  }

  /**
   * Handles component click events and manages context menu visibility and selection.
   *
   * @param event - Mouse click event with potential consumed flag
   */
  onRightClick(event): void {
    //  this.isActivated() ? this.contextMenu.show() : this.hideContextMenu();
  }

  /**
   * Handles component click events and manages context menu visibility and selection.
   *
   * @param event - Mouse click event with potential consumed flag
   */
  onComponentClick(event): void {
    if (!event[this.consumedGT]) {
      this.contextMenu && this.contextMenu.hide();
      this.resetMenu(this.getSSP(this.singleMultiSelection));
    }
  }

  /**
   * Determines if a security position represents a margin product (CFD or FOREX).
   *
   * @param {SecuritycurrencyPosition<Security | Currencypair>} securitycurrencyPosition - Position to check for margin product type
   * @returns {boolean} True if the position represents a CFD or FOREX instrument
   */
  isMarginProduct(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>): boolean {
    return BusinessHelper.isMarginProduct(<Security>securitycurrencyPosition.securitycurrency);
  }

  /**
   * Initiates drag operation with watchlist security data transfer.
   *
   * @param {DragEvent} event - Drag start event containing data transfer object
   * @param {SecuritycurrencyPosition<Security | Currencypair>} data - Position data being dragged for drop operations
   */
  dragStart(event: DragEvent, data: SecuritycurrencyPosition<Security | Currencypair>) {
    this.changeDetectionStrategy.detach();
    event.dataTransfer.setData('text/plain',
      JSON.stringify(new WatchlistSecurityExists(this.watchlist.idWatchlist, data.securitycurrency.idSecuritycurrency)));
  }

  /**
   * Completes drag operation and reattaches change detection.
   *
   * @param {DragEvent} event - Drag end event
   * @param {any} item - Item that was being dragged (not used in current implementation)
   */
  public dragEnd(event: DragEvent, item: any) {
    this.changeDetectionStrategy.reattach();
  }

  /**
   * Determines if a row can be expanded based on watchlist type and row data.
   * Used by configurable-table component to control expansion toggle visibility.
   *
   * @param {SecuritycurrencyPosition<Security | Currencypair>} row - Security position to check for expansion eligibility
   * @returns {boolean} True if the row can be expanded
   */
  canExpandRow(row: SecuritycurrencyPosition<Security | Currencypair>): boolean {
    return row.watchlistSecurityHasEver
      || this.watchlistType === WatchListType.PRICE_FEED
      || this.watchlistType === WatchListType.UDF;
  }

  /**
   * Determines if owner field should be highlighted (bold font weight).
   * Used as callback for configurable-table component's owner template rendering.
   *
   * @param {SecuritycurrencyPosition<Security | Currencypair>} row - Row data containing security/currency information
   * @param {ColumnConfig} field - Column configuration for the owner field
   * @returns {boolean} True if owner should be highlighted with bold font
   */
  ownerHighlightFn(row: SecuritycurrencyPosition<Security | Currencypair>, field: ColumnConfig): boolean {
    return this.isNotSingleModeAndOwner(row.securitycurrency, field);
  }

  /** Adds the base columns common to all watchlist table types. */
  protected addBaseColumns(): void {
    this.addColumn(DataType.String, this.SECURITYCURRENCY_NAME, 'NAME', true, false,
      {width: 200, frozenColumn: false, templateName: BaseSettings.OWNER_TEMPLATE});
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

  /** Sets up subscription to external watchlist modification events. */
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

  /** Initializes the component with route subscriptions and panel registration. */
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

  /**
   * Creates the show menu items for the selected security currency position.
   *
   * @param {SecuritycurrencyPosition<Security | Currencypair>} securitycurrencyPosition - Currently selected position or null
   * @returns {MenuItem[]} Array of show menu items with translations applied
   */
  protected getShowMenu(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>): MenuItem[] {
    const menuItems = [...this.getShowContextMenuItems(securitycurrencyPosition, false), {separator: true},
      ...this.getMenuShowOptions()];
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  /**
   * Creates context menu items for display operations like charts and external links.
   *
   * @param {SecuritycurrencyPosition<Security | Currencypair>} securitycurrencyPosition - Selected position for context operations
   * @param {boolean} translate - Whether to apply translations to menu items
   * @returns {MenuItem[]} Array of show context menu items
   */
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

  /**
   * Gets download link for historical or intraday data.
   *
   * @param {string} url - Data provider URL or 'lazy' for dynamic URL generation
   * @param {string} targetPage - Target page name for opening external links
   * @param {SecuritycurrencyPosition<Security | Currencypair>} securitycurrencyPosition - Position containing the security/currency
   * @param {boolean} isIntra - True for intraday data, false for historical data
   */
  private getDownloadLinkHistoricalIntra(url: string, targetPage: string, securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>,
    isIntra: boolean): void {
    WatchlistHelper.getDownloadLinkHistoricalIntra(url, targetPage, securitycurrencyPosition.securitycurrency, isIntra, this.watchlistService);
  }

  /**
   * Creates context menu items for edit operations like add, remove, and transaction handling.
   *
   * @param {SecuritycurrencyPosition<Security | Currencypair>} securitycurrencyPosition - Selected position for edit operations
   * @returns {MenuItem[]} Array of edit menu items with command handlers
   */
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

  /**
   * Determines if UDF menu item should be enabled based on available UDF fields for the security type.
   *
   * @param {Securitycurrency} securitycurreny - Security or currency entity to check for UDF field availability
   * @returns {boolean} True if UDF fields are defined and available for this entity type
   */
  private enableMenuItemUDF(securitycurreny: Securitycurrency): boolean {
    const key = securitycurreny instanceof CurrencypairWatchlist ? -1 : securitycurreny.idSecuritycurrency;
    let hasUDF = this.lazyMapHasUDF[key];
    if (this.lazyMapHasUDF[key] === undefined) {
      const fd = securitycurreny instanceof CurrencypairWatchlist
        ? UDFMetadataHelper.getFieldDescriptorByEntity(AppSettings.CURRENCYPAIR)
        : SecurityUDFHelper.getFieldDescriptorInputAndShowExtendedSecurity((<Security>securitycurreny).assetClass, true);
      hasUDF = this.lazyMapHasUDF[key] = fd.length > 0;
    }
    return hasUDF;
  }

  /** Translates formula prices to user's decimal symbol format. */
  private translateFormulaToUserLanguage(): void {
    if (this.gps.getDecimalSymbol() !== '.') {
      this.securitycurrencyGroup.securityPositionList.filter(sp => sp.securitycurrency.formulaPrices)
        .map(sp => sp.securitycurrency.formulaPrices = sp.securitycurrency.formulaPrices.split('.')
          .join(this.gps.getDecimalSymbol()));
    }
  }

  /**
   * Gets single security position from single or multi-selection.
   *
   * @param {SecuritycurrencyPosition<Security | Currencypair> | SecuritycurrencyPosition<Security | Currencypair>[]} singleMultiSelection - Current selection state (single item or array)
   * @returns {SecuritycurrencyPosition<Security | Currencypair>} Single position or null if multiple items selected
   */
  private getSSP(singleMultiSelection: SecuritycurrencyPosition<Security | Currencypair>
    | SecuritycurrencyPosition<Security | Currencypair>[]): SecuritycurrencyPosition<Security | Currencypair> {
    if (Array.isArray(singleMultiSelection)) {
      return singleMultiSelection.length === 1 ? singleMultiSelection[0] : null;
    } else {
      return singleMultiSelection as SecuritycurrencyPosition<Security | Currencypair>;
    }
  }

  /**
   * Opens mail dialog to send message to security/currency creator.
   *
   * @param {Securitycurrency} securitycurrency - Security or currency entity whose creator will receive the message
   */
  private mailToCreator(securitycurrency: Securitycurrency): void {
    const subject = securitycurrency instanceof CurrencypairWatchlist ? (<CurrencypairWatchlist>securitycurrency).name
      : (<Security>securitycurrency).name;
    DynamicDialogs.getOpenedMailSendComponent(this.translateService, this.dialogService,
      new MailSendParam(securitycurrency.createdBy, null, subject));
  }

  /** Handles price update with timeout check to prevent excessive API calls. */
  private handleUpdateAllPrice() {
    if (Date.now() < +this.securitycurrencyGroup.lastTimestamp + this.intraUpdateTimoutSeconds * 1000) {
      const minutes = this.millisToMinutesAndSeconds(+this.securitycurrencyGroup.lastTimestamp
        + this.intraUpdateTimoutSeconds * 1000 - Date.now());
      this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'UPDATE_TIMEOUT', {time: minutes});
    } else {
      this.updateAllPrice();
    }
  }

  /**
   * Converts milliseconds to MM:SS format for display.
   *
   * @param {number} millis - Milliseconds value to convert to readable time format
   * @returns {string} Formatted time string in MM:SS format
   */
  private millisToMinutesAndSeconds(millis: number): string {
    const minutes: number = Math.floor(millis / 60000);
    const seconds: number = +((millis % 60000) / 1000).toFixed(0);
    return minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
  }

  /**
   * Creates edit menu with translated menu items.
   *
   * @param {SecuritycurrencyPosition<Security | Currencypair>} securitycurrencyPosition - Selected position for edit menu context
   * @returns {MenuItem[]} Array of translated edit menu items
   */
  private getEditMenu(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>): MenuItem[] {
    const menuItems: MenuItem[] = this.getEditMenuItems(securitycurrencyPosition);
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  /**
   * Checks if any tenant limits have been reached for the watchlist.
   *
   * @returns {boolean} True if any security or currency limits have been reached
   */
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

  /**
   * Updates selected position and refreshes context menus.
   *
   * @param {SecuritycurrencyPosition<Security | Currencypair>} selectedSecuritycurrencyPosition - The security position to select
   */
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

/**
 * Data class representing a time frame for performance analysis and filtering.
 * Used in performance watchlists to filter data by specific time periods and calculate
 * period-based performance metrics and changes.
 *
 * @param {string} name - Display name of the time frame (e.g., 'THIS_WEEK', 'DAYS_30', 'YEAR_1')
 * @param {number} days - Number of days from current date to calculate performance metrics
 */
export class TimeFrame {
  constructor(public name: string, public days: number) {
  }
}
/**
 * Enumeration defining different types of watchlist views and their specific functionality.
 * Each type determines which columns are displayed and which expanded row content is shown.
 */
export enum WatchListType {
  /** Performance view showing price changes, holdings, and gains/losses with transaction history in expanded rows */
  PERFORMANCE,
  /** Price feed reliability view showing data provider information and feed status with detailed connection info in expanded rows */
  PRICE_FEED,
  /** User-defined fields view displaying custom fields and metadata with UDF details in expanded rows */
  UDF,
  /** Dividend and split feed view showing distribution data with dividend and split history tables in expanded rows */
  DIVIDEND_SPLIT_FEED
}
