import {WatchlistTable, WatchListType} from './watchlist.table';
import {ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import {DataChangedService} from '../../lib/maintree/service/data.changed.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {WatchlistService} from '../service/watchlist.service';
import {ActivatedRoute, Router} from '@angular/router';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {AppSettings} from '../../shared/app.settings';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {SecuritycurrencyGroup} from '../../entities/view/securitycurrency.group';
import {SecuritycurrencyPosition} from '../../entities/view/securitycurrency.position';
import {HelpIds} from '../../lib/help/help.ids';
import {TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {combineLatest, Observable} from 'rxjs';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {TenantLimit} from '../../shared/types/tenant.limit';
import {SecurityCurrencyHelper} from '../../securitycurrency/service/security.currency.helper';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {AlarmSetupService} from '../../algo/service/alarm.setup.service';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {BaseSettings} from '../../lib/base.settings';
import {CommonModule} from '@angular/common';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {AngularSvgIconModule} from 'angular-svg-icon';
import {TooltipModule} from 'primeng/tooltip';
import {TransactionSecurityTableComponent} from '../../transaction/component/transaction-security-table.component';
import {TransactionSecurityMarginTreetableComponent} from '../../transaction/component/transaction-security-margin-treetable.component';
import {SecuritycurrencyUdfComponent} from './securitycurrency-udf.component';
import {SecuritycurrencyExtendedInfoComponent} from './securitycurrency-extended-info.component';
import {WatchlistDividendTableComponent} from './watchlist-dividend-table.component';
import {WatchlistSecuritysplitTableComponent} from './watchlist-securitysplit-table.component';
import {WatchlistAddInstrumentComponent} from './watchlist-add-instrument.component';
import {CurrencypairEditComponent} from '../../shared/securitycurrency/currencypair-edit.component';
import {SecurityEditComponent} from '../../shared/securitycurrency/security-edit.component';
import {SecurityDerivedEditComponent} from '../../securitycurrency/component/security-derived-edit.component';
import {SecurityUDFEditComponent} from '../../securitycurrency/component/security-udf-edit.component';
import {AlgoStrategyEditComponent} from '../../algo/component/algo-strategy-edit.component';
import {WatchlistAddEditPriceProblemInstrumentComponent} from './watchlist-add-edit-price-problem-instrument.component';
import {TransactionSecurityEditComponent} from '../../transaction/component/transaction-security-edit.component';
import {UDFGeneralEditComponent} from '../../lib/udfmeta/components/udf-general-edit.component';

/**
 * Angular component for monitoring and managing price data feed reliability in watchlists.
 * Extends WatchlistTable to provide specialized functionality for checking feed status,
 * retry counts, and data provider connectivity. Includes special functions for updating
 * price data and managing problematic instruments.
 *
 * Key features:
 * - Display feed connector information and retry counts
 * - Show youngest historical data dates and full load timestamps
 * - Provide repair functions for failed history/intraday loads
 * - Support adding instruments with price data problems
 * - Integration with security and currency pair services
 */
@Component({
  templateUrl: '../view/watchlist.data.html',
  styles: [`
    .cell-move {
      cursor: move !important;
    }
  `],
  providers: [DialogService],
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    ConfigurableTableComponent,
    AngularSvgIconModule,
    TooltipModule,
    TransactionSecurityTableComponent,
    TransactionSecurityMarginTreetableComponent,
    SecuritycurrencyUdfComponent,
    SecuritycurrencyExtendedInfoComponent,
    WatchlistDividendTableComponent,
    WatchlistSecuritysplitTableComponent,
    WatchlistAddInstrumentComponent,
    CurrencypairEditComponent,
    SecurityEditComponent,
    SecurityDerivedEditComponent,
    SecurityUDFEditComponent,
    AlgoStrategyEditComponent,
    WatchlistAddEditPriceProblemInstrumentComponent,
    TransactionSecurityEditComponent,
    UDFGeneralEditComponent
  ]
})
export class WatchlistPriceFeedComponent extends WatchlistTable implements OnInit, OnDestroy {

  /**
   * Field name constant for accessing retry history load count in security currency objects.
   * Used to reference the retryHistoryLoad property in data objects.
   */
  private readonly f_retryHistoryLoad = 'retryHistoryLoad';
  /**
   * Field name constant for accessing retry intraday load count in security currency objects.
   * Used to reference the retryIntraLoad property in data objects.
   */
  private readonly f_retryIntraLoad = 'retryIntraLoad';

  /**
   * Creates a new instance of WatchlistPriceFeedComponent with comprehensive dependency injection.
   * Initializes the table with price feed specific columns including data providers, retry counts,
   * youngest history dates, and full load timestamps.
   *
   * @param securityService - Service for security-related operations and data retrieval
   * @param currencypairService - Service for currency pair operations and data retrieval
   * @param dialogService - PrimeNG service for managing dynamic dialogs
   * @param alarmSetupService - Service for setting up and managing alarms
   * @param timeSeriesQuotesService - Service for time series quote operations
   * @param dataChangedService - Service for tracking data changes across components
   * @param activePanelService - Service for managing active panel state
   * @param watchlistService - Service for watchlist operations and data management
   * @param router - Angular router for navigation
   * @param activatedRoute - Current activated route for parameter access
   * @param confirmationService - PrimeNG service for confirmation dialogs
   * @param messageToastService - Service for displaying toast messages to users
   * @param productIconService - Service for retrieving product-specific icons
   * @param changeDetectionStrategy - Angular change detection reference
   * @param filterService - PrimeNG service for table filtering functionality
   * @param translateService - Angular service for internationalization
   * @param gpsGT - Global parameter service for GT-specific settings
   * @param gps - Global parameter service for application-wide settings
   * @param usersettingsService - Service for managing user preferences and settings
   */
  constructor(private securityService: SecurityService,
    private currencypairService: CurrencypairService,
    dialogService: DialogService,
    alarmSetupService: AlarmSetupService,
    timeSeriesQuotesService: TimeSeriesQuotesService,
    dataChangedService: DataChangedService,
    activePanelService: ActivePanelService,
    watchlistService: WatchlistService,
    router: Router,
    activatedRoute: ActivatedRoute,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    productIconService: ProductIconService,
    changeDetectionStrategy: ChangeDetectorRef,
    filterService: FilterService,
    translateService: TranslateService,
    gpsGT: GlobalparameterGTService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(WatchListType.PRICE_FEED, AppSettings.WATCHLIST_PRICE_FEED_TABLE_SETTINGS_STORE, dialogService, alarmSetupService,
      timeSeriesQuotesService, dataChangedService, activePanelService, watchlistService, router, activatedRoute, confirmationService,
      messageToastService, productIconService, changeDetectionStrategy, filterService, translateService,
      gpsGT, gps, usersettingsService, WatchlistTable.SINGLE);
    const date = new Date();
    this.addBaseColumns();
    this.addColumn(DataType.String, 'securitycurrency.idConnectorIntra', 'INTRA_DATA_PROVIDER', true, true,
      {fieldValueFN: this.getFeedConnectorReadableName.bind(this)});
    this.addColumn(DataType.NumericInteger, 'securitycurrency.' + this.f_retryIntraLoad, 'RETRY_INTRA_LOAD', true, true);
    this.addColumn(DataType.String, 'securitycurrency.idConnectorHistory', 'HISTORY_DATA_PROVIDER', true, true,
      {fieldValueFN: this.getFeedConnectorReadableName.bind(this)});
    this.addColumn(DataType.DateString, 'youngestHistoryDate', 'YOUNGEST_EOD', true, true);
    this.addColumn(DataType.NumericInteger, 'securitycurrency.' + this.f_retryHistoryLoad, 'RETRY_HISTORY_LOAD', true, true);
    this.addColumn(DataType.DateTimeNumeric, 'securitycurrency.fullLoadTimestamp', 'FULL_LOAD_DATE', true, true);
    this.prepareTableAndTranslate();
    this.watchlistHasModifiedFromOutside();
  }

  /**
   * Angular lifecycle hook called after component initialization.
   * Initializes the watchlist table and loads feed connector data.
   */
  ngOnInit(): void {
    this.init();
    this.loadData();
  }

  /**
   * Retrieves the human-readable name for a feed connector based on its ID.
   * Used as a field value function to transform connector IDs into user-friendly names
   * for display in the table columns.
   *
   * @param dataobject - The row data object (unused in current implementation)
   * @param field - The column configuration object (unused in current implementation)
   * @param valueField - The feed connector ID to transform
   * @returns The human-readable name of the feed connector, or undefined if not found
   */
  getFeedConnectorReadableName(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.feedConnectorsKV[valueField];
  }

  /**
   * Loads watchlist data without triggering price updates, focusing on feed reliability metrics.
   * Combines watchlist data with security position limits and updates the component state.
   * Specifically retrieves data with maximum history quote information for feed analysis.
   */
  protected override getWatchlistWithoutUpdate(): void {
    const watchListObservable: Observable<SecuritycurrencyGroup> =
      this.watchlistService.getWatchlistWithoutUpdateAndMaxHistoryquote(this.idWatchlist);
    const tenantLimitObservable: Observable<TenantLimit[]> = this.watchlistService.getSecuritiesCurrenciesWatchlistLimits(this.idWatchlist);
    combineLatest([watchListObservable, tenantLimitObservable]).subscribe((result: [SecuritycurrencyGroup, TenantLimit[]]) => {
      this.createSecurityPositionList(result[0]);
      this.tenantLimits = result[1];
      this.loading = false;
    });
  }

  /** Returns the help context identifier for this component. Used by the help system to display relevant documentation. */
  public override getHelpContextId(): string {
    return HelpIds.HELP_WATCHLIST_PRICE_FEED;
  }

  /**
   * Creates edit menu items with price feed specific options for users with appropriate privileges.
   * Extends the base edit menu with options to add problematic instruments and repair failed loads.
   * Menu items include:
   * - Add problem instrument dialog (for empty watchlists)
   * - Repair history load (for instruments with retry count > 0)
   * - Repair intraday load (for instruments with retry count > 0)
   *
   * @param securitycurrencyPosition - The currently selected security or currency position
   * @returns Array of menu items for edit operations, or base menu items if user lacks privileges
   */
  protected override getEditMenuItems(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>): MenuItem[] {
    if (this.securityPositionList && AuditHelper.hasHigherPrivileges(this.gps)) {
      const menuItems: MenuItem[] = [
        {
          label: 'WATCHLIST_ADD_PROBLEM_INSTRUMENT' + BaseSettings.DIALOG_MENU_SUFFIX,
          command: (e) => this.visibleAddPriceProblemDialog = true,
          disabled: this.securityPositionList.length > 0
        },
        {
          label: 'REPAIR_HISTORY_LOAD',
          command: (e) => this.watchlistService.tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(this.idWatchlist)
            .subscribe(() => this.getWatchlistWithoutUpdate()),
          disabled: this.disableUpToDateFeedDataMenu(this.f_retryHistoryLoad, null)
        },
        {
          label: 'REPAIR_INTRADAY_LOAD',
          command: (e) => this.watchlistService.tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(this.idWatchlist)
            .subscribe(() => this.getWatchlistWithoutUpdate()),
          disabled: this.disableUpToDateFeedDataMenu(this.f_retryIntraLoad, new Date())
        },
        {separator: true},
        ...super.getEditMenuItems(securitycurrencyPosition)
      ];
      return menuItems;
    } else {
      return super.getEditMenuItems(securitycurrencyPosition);
    }
  }

  /**
   * Triggers a complete price data update by reloading watchlist data.
   * Sets loading state and refreshes the watchlist to get updated feed information.
   */
  protected override updateAllPrice(): void {
    this.loading = true;
    this.getWatchlistWithoutUpdate();
  }

  /**
   * Determines whether feed repair menu items should be disabled based on retry counts and active dates.
   * Checks if all securities have zero retry counts or are past their active date.
   *
   * @param propName - The property name to check ('retryHistoryLoad' or 'retryIntraLoad')
   * @param untilDate - The date to compare against active dates (null for history, current date for intraday)
   * @returns True if the repair menu should be disabled, false otherwise
   */
  private disableUpToDateFeedDataMenu(propName: string, untilDate: Date): boolean {
    return this.securityPositionList.every(sp => sp.securitycurrency[propName] === 0
      || (!(sp.securitycurrency instanceof CurrencypairWatchlist)
        && new Date((<Security>sp.securitycurrency).activeToDate) <= (untilDate === null ? sp.youngestHistoryDate : untilDate)));
  }

  /**
   * Loads feed connector data and initializes the watchlist.
   * Uses SecurityCurrencyHelper to load all available connectors into the feedConnectorsKV mapping,
   * then triggers the watchlist data loading process.
   */
  private loadData(): void {
    SecurityCurrencyHelper.loadAllConnectors(this.securityService, this.currencypairService, this.feedConnectorsKV,
      this.getWatchlistWithoutUpdate.bind(this));
  }

  /**
   * Handles the closing of the add price problem instrument dialog.
   * Hides the dialog and triggers a complete price data update to reflect any changes.
   *
   * @param processedActionData - Data about the action performed in the dialog
   */
  override handleCloseAddPriceProblemInstrument(processedActionData: ProcessedActionData): void {
    this.visibleAddPriceProblemDialog = false;
    this.updateAllPrice();
  }

}

