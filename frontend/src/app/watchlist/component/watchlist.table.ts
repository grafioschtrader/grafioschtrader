import {Security} from '../../entities/security';
import {AfterViewInit, ChangeDetectorRef, Directive, Injector, OnDestroy, ViewChild} from '@angular/core';
import {ActivatedRoute, Params, Router} from '@angular/router';
import {WatchlistService} from '../service/watchlist.service';
import {SecuritycurrencyGroup} from '../../entities/view/securitycurrency.group';
import {SecuritycurrencyPosition} from '../../entities/view/securitycurrency.position';
import {DialogService} from '@openng/optimus-ui/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem, SortEvent, SortMeta} from '@openng/optimus-ui/api';
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
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
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
import {TreeNavigationStateService} from '../../lib/maintree/service/tree.navigation.state.service';
import {FilterType} from '../../lib/datashowbase/filter.type';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {WatchlistFilterSortStateService} from '../service/watchlist.filter.sort.state.service';
import {
  WatchlistFilterSortSettingsDialogComponent,
  WatchlistFilterSortSettingsData
} from './watchlist-filter-sort-settings-dialog.component';

/**
 * Abstract base class for watchlist table components that provides comprehensive functionality for displaying
 * and managing securities and currency pairs in tabular format. Handles CRUD operations, context menus,
 * transaction dialogs, drag-and-drop, and various editing capabilities.
 */
@Directive()
export abstract class WatchlistTable extends TableConfigBase implements AfterViewInit, OnDestroy, IGlobalMenuAttach {

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

  /** NLS key for the distribution column value of an instrument which pays out interest or dividends. */
  private static readonly DISTRIBUTION_YES = 'DISTRIBUTION_YES';

  /** NLS key for the distribution column value of an instrument which never pays out. */
  private static readonly DISTRIBUTION_NO = 'DISTRIBUTION_NO';

  /** The two translated distribution column values, resolved once in {@link addBaseColumns}. */
  private distributionTexts: { [key: string]: string } = {};

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

  /** Reference to the table component, used to apply and to reset the stored filters. */
  @ViewChild(ConfigurableTableComponent) protected configurableTable: ConfigurableTableComponent;

  /** Parameters for UDF general dialogs. */
  uDFGeneralCallParam: UDFGeneralCallParam;

  /** Map of UDF values by security currency ID. */
  protected udfValuesMap = new Map<number, any>;

  /** Subscription to route parameter changes. */
  private routeSubscribe: Subscription;

  /** Subscription to watchlist modification events. */
  private subscriptionWatchlistAdded: Subscription;

  /** Holds the filters and the sort order beyond the lifetime of this component, resolved in {@link init}. */
  private filterSortState: WatchlistFilterSortStateService;

  /** Subscription to changes made in the filter and sort settings dialog. */
  private subscriptionFilterSortChanged: Subscription;

  /** True as soon as the stored filters were handed to the table, guards against capturing an empty start state. */
  private storedFiltersApplied = false;

  /** Cache for UDF availability by security currency ID. */
  private lazyMapHasUDF: { [idSecuritycurrency: number]: boolean } = {};

  /**
   * Creates an instance of WatchlistTable.
   *
   * @param {WatchListType} watchlistType - Type of watchlist (performance, price feed, etc.)
   * @param {string} storeKey - Local storage key for table settings
   * @param {DialogService} dialogService - Optimus dialog service
   * @param {AlarmSetupService} alarmSetupService - Service for alarm setup functionality
   * @param {TimeSeriesQuotesService} timeSeriesQuotesService - Service for time series operations
   * @param {DataChangedService} dataChangedService - Service for data change notifications
   * @param {ActivePanelService} activePanelService - Service for active panel management
   * @param {WatchlistService} watchlistService - Service for watchlist operations
   * @param {Router} router - Angular router for navigation
   * @param {ActivatedRoute} activatedRoute - Current activated route
   * @param {ConfirmationService} confirmationService - Optimus confirmation dialog service
   * @param {MessageToastService} messageToastService - Service for toast notifications
   * @param {ProductIconService} productIconService - Service for product icons
   * @param {ChangeDetectorRef} changeDetectionStrategy - Angular change detection reference
   * @param {FilterService} filterService - Optimus filter service
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
    public selectMultiMode: 'single' | 'multiple',
    injector: Injector) {
    super(filterService, usersettingsService, translateService, gps, injector);
    if (selectMultiMode === WatchlistTable.MULTIPLE) {
      this.singleMultiSelection = [];
    }
    this.filterRowToggleable = true;
    this.multiSortMeta.push({field: 'securitycurrency.name', order: 1});
  }

  /**
   * Shows or hides the filter row. A watchlist can be long, so the filter row is off by default and the user turns it
   * on through the show menu. Hiding it suspends the filters, all instruments become visible again, because otherwise
   * rows would stay hidden without any visible control to reset them. The filters themselves are kept and are back as
   * soon as the row is shown again; they are only removed through the settings dialog.
   */
  override toggleFilterRow(): void {
    super.toggleFilterRow();
    this.applyStoredFilters();
  }

  /**
   * Hands the stored filters to the table, or removes them from it while the filter row is hidden. Can be called as
   * often as needed; it does nothing until the table exists.
   */
  protected applyStoredFilters(): void {
    const table = this.configurableTable?.table;
    if (!table || !this.idWatchlist) {
      return;
    }
    if (this.showFilterRow) {
      const effective = this.filterSortState.getEffectiveFilters(this.idWatchlist, this.fields);
      Object.keys(table.filters).filter(key => !effective[key]).forEach(key => {
        const meta = table.filters[key];
        if (Array.isArray(meta)) {
          // The filter menu of a column creates its constraint objects only once, therefore they are emptied and not
          // removed. Otherwise the menu of a column whose filter was removed in the dialog would stay unusable.
          meta.forEach(constraint => constraint.value = null);
        } else {
          delete table.filters[key];
        }
      });
      Object.keys(effective).forEach(key => table.filters[key] = effective[key]);
      table._filter();
      this.storedFiltersApplied = true;
    } else {
      this.storedFiltersApplied = false;
      this.configurableTable.clearFilters();
    }
  }

  /**
   * Hands the stored sort order to the table. Assigning a new array is required, Optimus only re-sorts when the bound
   * reference changes. Without a stored sort order the instruments are sorted by name, as before.
   */
  protected applyStoredSorts(): void {
    const sorts = this.filterSortState.getEffectiveSorts(this.idWatchlist, this.fields);
    this.multiSortMeta = sorts.length > 0 ? sorts : [{field: this.SECURITYCURRENCY_NAME, order: 1}];
  }

  /**
   * Takes over the filters the user has entered. Nothing is taken over while the filter row is hidden, because the
   * table is deliberately unfiltered then and that state must not be mistaken for the user having cleared everything.
   *
   * @param event - Filter event of the table, its filters property holds the complete filter map
   */
  onFilterChanged(event: any): void {
    if (this.showFilterRow && this.storedFiltersApplied && this.idWatchlist) {
      this.filterSortState.captureFilters(this.idWatchlist, this.fields, event?.filters);
    }
  }

  /**
   * Sorts the table and remembers by what. The sort order the table falls back to when nothing is stored is not
   * remembered, otherwise every watchlist would end up with a stored sort by name which the user never asked for.
   *
   * @param event - Sort event of the table
   */
  override customSort(event: SortEvent): void {
    super.customSort(event);
    if (this.idWatchlist && !this.isFallbackSort(event.multiSortMeta)) {
      this.filterSortState.captureSorts(this.idWatchlist, this.fields, event.multiSortMeta);
    }
  }

  /**
   * Checks whether a sort order is only the fallback of a table without a stored sort order.
   *
   * @param sortMeta - Sort order to check
   * @returns True when it is the fallback sort by name and nothing is stored for this table
   */
  private isFallbackSort(sortMeta: SortMeta[]): boolean {
    return sortMeta?.length === 1 && sortMeta[0].field === this.SECURITYCURRENCY_NAME && sortMeta[0].order === 1
      && this.filterSortState.getEffectiveSorts(this.idWatchlist, this.fields).length === 0;
  }

  /** Opens the dialog for the scope of filtering and sorting and for removing what is set. */
  protected openFilterSortSettingsDialog(): void {
    const data: WatchlistFilterSortSettingsData = {idWatchlist: this.idWatchlist};
    this.injector.get(DialogService).open(WatchlistFilterSortSettingsDialogComponent, {
      header: this.translateService.instant('FILTER_SORT_SETTINGS'),
      width: '620px', modal: true, draggable: true, closable: true, closeOnEscape: true, data
    });
  }


  /**
   * Creates security position list from security currency group data and handles currency pair transformations.
   *
   * @param {SecuritycurrencyGroup} data - Security currency group containing separate lists of security and currency positions
   */
  createSecurityPositionList(data: SecuritycurrencyGroup) {
    this.securitycurrencyGroup = data;
    this.securityPositionList = data.securityPositionList;
    this.securitycurrencyGroup.currencypairPositionList.forEach((sp: SecuritycurrencyPosition<Currencypair>) => {
      const currencypairWatchlist: CurrencypairWatchlist = new CurrencypairWatchlist(sp.securitycurrency.fromCurrency,
        sp.securitycurrency.toCurrency);
      Object.assign(currencypairWatchlist, sp.securitycurrency);
      sp.securitycurrency = currencypairWatchlist;
      this.securityPositionList.push(sp);
    });
    // Must run on the complete list, the currency pairs are appended to the same array above and would otherwise miss
    // their translated and filter fields.
    this.createTranslatedValueStoreAndFilterField(this.securityPositionList);
    this.prepareFilter(this.securityPositionList);
    // Only now do the columns know the field the table filters on, so only now can the stored filters be applied. It
    // happens before the new data reaches the table, which then filters and sorts it in one go.
    this.applyStoredFilters();
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

  /**
   * Gets the icon marking an instrument which pays out interest or dividends. Called from the cell template instead of
   * being the column value, because the column value is the sortable and filterable text of
   * {@link getDistributionText}.
   *
   * @param {SecuritycurrencyPosition<Security | Currencypair>} securitycurrencyPosition - Position containing the instrument to get icon for
   * @returns {string} Icon name for a distributing security, otherwise null which leaves the cell empty
   */
  getDistributionIcon(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>): string {
    return securitycurrencyPosition.securitycurrency instanceof CurrencypairWatchlist ? null
      : this.productIconService.getDistributionIcon(<Security>securitycurrencyPosition.securitycurrency);
  }

  /**
   * Produces the value of the distribution column, the translated answer to whether the instrument pays out interest
   * or dividends. It is deliberately not the distribution frequency: the column exists to tell a distributing
   * instrument from an accumulating one, so its sort order and its dropdown filter offer these two answers and nothing
   * else. The text is deliberately a short yes or no: it is what the dropdown filter of the column lists, and a long
   * option label would widen the column to fit it. What the column means is spelled out by the tooltip of the header
   * and of the icon. A currency pair yields null, it has no distribution at all and thus also contributes no filter
   * entry.
   *
   * The column is bound to the path 'securitycurrency.distribution', which does not exist on the instrument. Like the
   * instrument icon column it takes its value solely from this function, and an own path keeps it apart from the
   * distribution frequency column of the dividend and split feed view, which must keep sorting by frequency.
   *
   * @param {SecuritycurrencyPosition<Security | Currencypair>} securitycurrencyPosition - Position containing the instrument
   * @param {ColumnConfig} field - Column configuration object (not used in this implementation)
   * @param {any} valueField - Raw distribution frequency of the row (not used in this implementation)
   * @returns {string} Translated distribution or no distribution, null for a currency pair
   */
  getDistributionText(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>, field: ColumnConfig,
    valueField: any): string {
    return securitycurrencyPosition.securitycurrency instanceof CurrencypairWatchlist ? null
      : this.distributionTexts[this.getDistributionIcon(securitycurrencyPosition) ? WatchlistTable.DISTRIBUTION_YES
        : WatchlistTable.DISTRIBUTION_NO];
  }

  /** Applies the stored filters as soon as the table exists, the data may already have arrived before that. */
  ngAfterViewInit(): void {
    this.applyStoredFilters();
  }

  /** Cleans up subscriptions and saves table configuration on component destruction. */
  ngOnDestroy(): void {
    this.writeTableDefinition(this.storeKey);
    this.activePanelService.destroyPanel(this);
    this.subscriptionWatchlistAdded.unsubscribe();
    this.subscriptionFilterSortChanged?.unsubscribe();
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
          // The event below already reloads the rows through watchlistHasModifiedFromOutside().
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
    const dragPayload = JSON.stringify(new WatchlistSecurityExists(this.watchlist.idWatchlist, data.securitycurrency.idSecuritycurrency));
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', dragPayload);
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

  /**
   * Adds the base columns common to all watchlist table types. Besides the identification of the instrument this also
   * includes its asset class classification, which is relevant in every watchlist type and not only in the performance
   * view. Because the columns exist in all of them, a filter or sort on one of them can also be shared over the
   * watchlist types.
   *
   * The same reasoning applies to the distribution column: whether an instrument pays out interest or dividends at all
   * is of interest in every watchlist type, not only in the dividend and split feed view. It answers that question
   * only, the frequency itself is not part of it, so its value is the translated yes or no of
   * {@link getDistributionText} on which sorting and the two entry dropdown filter operate, while the cell itself
   * shows nothing but the icon of {@link getDistributionIcon}. The exact frequency stays available in the dividend and
   * split feed view, which keeps its own distribution frequency column.
   */
  protected addBaseColumns(): void {
    this.addColumn(DataType.String, this.SECURITYCURRENCY_NAME, 'NAME', true, false,
      {width: 200, frozenColumn: false, templateName: BaseSettings.OWNER_TEMPLATE, filterType: FilterType.likeDataType});
    this.addColumn(DataType.String, 'securitycurrency', AppSettings.INSTRUMENT_HEADER, true, false,
      {fieldValueFN: this.getInstrumentIcon.bind(this), templateName: 'icon', width: 20});
    this.translateService.get([WatchlistTable.DISTRIBUTION_YES, WatchlistTable.DISTRIBUTION_NO]).subscribe(
      texts => this.distributionTexts = texts);
    this.addColumn(DataType.String, WatchlistHelper.SECURITYCURRENCY + '.distribution',
      AppSettings.DISTRIBUTION_HEADER, true, true,
      {fieldValueFN: this.getDistributionText.bind(this), filterType: FilterType.withOptions,
        templateName: 'svgIcon', width: 60});
    this.addColumnFeqH(DataType.String, WatchlistHelper.SECURITYCURRENCY + '.isin', true, true,
      {width: 90, filterType: FilterType.likeDataType});
    this.addColumnFeqH(DataType.String, WatchlistHelper.SECURITYCURRENCY + '.tickerSymbol', true, true,
      {filterType: FilterType.likeDataType});
    this.addColumnFeqH(DataType.String, WatchlistHelper.SECURITYCURRENCY + '.currency', true, true,
      {filterType: FilterType.withOptions, width: 40});
    this.addColumn(DataType.String, WatchlistHelper.SECURITYCURRENCY + '.assetClass.categoryType',
      AppHelper.toUpperCaseWithUnderscore(AppSettings.ASSETCLASS), true, true,
      {translateValues: TranslateValue.NORMAL, width: 60, filterType: FilterType.withOptions});
    this.addColumn(DataType.String, WatchlistHelper.SECURITYCURRENCY + '.assetClass.specialInvestmentInstrument',
      'FINANCIAL_INSTRUMENT', true, true,
      {translateValues: TranslateValue.NORMAL, width: 60, filterType: FilterType.withOptions});
    this.addColumn(DataType.String,
      WatchlistHelper.SECURITYCURRENCY + '.assetClass.subCategoryNLS.map.' + this.gps.getUserLang(),
      'SUB_ASSETCLASS', true, true, {width: 80, filterType: FilterType.withOptions});
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

  /**
   * Sets up subscription to external watchlist modification events.
   *
   * An instrument removed or moved away only changes the rows, therefore the cheaper updateAllPrice() is used instead
   * of the full getWatchlistWithoutUpdate(). This matters for the drag-and-drop of instruments between watchlists,
   * where every saved request counts against the per-user REST rate limit. The consequence is that the instrument
   * count of the watchlist limit stays one stale until the view is opened again; the limit itself is enforced by the
   * backend anyway.
   */
  protected watchlistHasModifiedFromOutside(): void {
    this.subscriptionWatchlistAdded = this.dataChangedService.dateChanged$.subscribe(processedActionData => {
      if (processedActionData.data instanceof Watchlist && processedActionData.action === ProcessedAction.UPDATED) {
        this.getWatchlistWithoutUpdate();
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'ADDED_SECURITY_TO_WATCHLIST');
      } else if (processedActionData.data instanceof Watchlist && processedActionData.action === ProcessedAction.DELETED) {
        this.updateAllPrice();
      }
    });
  }

  /** Initializes the component with route subscriptions and panel registration. */
  protected init(): void {
    this.idTenant = this.gps.getIdTenant();
    this.filterSortState = this.injector.get(WatchlistFilterSortStateService);
    this.subscriptionFilterSortChanged = this.filterSortState.changed$.subscribe(() => {
      this.applyStoredSorts();
      this.applyStoredFilters();
    });
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
      this.applyStoredSorts();
      this.activePanelService.activatePanel(this, {
        showMenu: this.getShowMenu(this.selectedSecuritycurrencyPosition),
        editMenu: this.getEditMenu(this.selectedSecuritycurrencyPosition)
      });
      const treeNavState = this.injector.get(TreeNavigationStateService);
      const route = this.activatedRoute.snapshot.routeConfig?.path?.split('/')[0] ?? '';
      this.watchlist = treeNavState.getEntity<Watchlist>(route, this.idWatchlist);
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
      ...(this.getMenuShowOptions() ?? []),
      {
        label: 'FILTER_SORT_SETTINGS' + BaseSettings.DIALOG_MENU_SUFFIX,
        icon: 'fa fa-sliders',
        command: () => this.openFilterSortSettingsDialog()
      }];
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
      menuItems.push({
        label: '_MAIL_TO_ADMIN' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: (e) => this.mailToAdmin(securitycurrencyPosition.securitycurrency)
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
      new MailSendParam(securitycurrency.createdBy, null, subject, undefined, AppSettings.SECURITYCURRENCY,
        securitycurrency.idSecuritycurrency));
  }

  /**
   * Opens mail dialog to send message to admin about a security/currency.
   *
   * @param {Securitycurrency} securitycurrency - Security or currency entity referenced in the message
   */
  private mailToAdmin(securitycurrency: Securitycurrency): void {
    const subject = securitycurrency instanceof CurrencypairWatchlist ? (<CurrencypairWatchlist>securitycurrency).name
      : (<Security>securitycurrency).name;
    DynamicDialogs.getOpenedMailSendComponent(this.translateService, this.dialogService,
      new MailSendParam(0, null, subject, BaseSettings.ROLE_ADMIN));
  }

  /** Handles price update with timeout check to prevent excessive API calls. */
  private handleUpdateAllPrice() {
    const lastTs = new Date(this.securitycurrencyGroup.lastTimestamp).getTime();
    if (Date.now() < lastTs + this.intraUpdateTimoutSeconds * 1000) {
      const minutes = this.millisToMinutesAndSeconds(lastTs
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
