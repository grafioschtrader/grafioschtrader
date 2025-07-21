import {ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import {DialogService} from 'primeng/dynamicdialog';
import {DataType} from '../../dynamic-form/models/data.type';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {WatchlistService} from '../service/watchlist.service';
import {ActivatedRoute, Router} from '@angular/router';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {AppSettings} from '../../shared/app.settings';
import {WatchlistTable, WatchListType} from './watchlist.table';
import {combineLatest, Observable} from 'rxjs';
import {SecuritycurrencyGroup} from '../../entities/view/securitycurrency.group';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {SecurityCurrencyHelper} from '../../securitycurrency/service/security.currency.helper';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {HelpIds} from '../../shared/help/help.ids';
import {AlarmSetupService} from '../../algo/service/alarm.setup.service';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';

/**
 * Angular component for displaying and managing watchlist dividend and split feed data.
 * Provides functionality to check the reliability of dividend and split feeds with multi-selection support for removal operations.
 * Extends the base WatchlistTable to add specific dividend and split feed columns and functionality.
 */
@Component({
  templateUrl: '../view/watchlist.data.html',
  styles: [`
    .cell-move {
      cursor: move !important;
    }

    table {
      border: 1px solid #555;
    }

  `],
  providers: [DialogService],
  standalone: false
})
export class WatchlistDividendSplitFeedComponent extends WatchlistTable implements OnInit, OnDestroy {

  /** Map of feed connector IDs to their human-readable names for display purposes */
 // feedConnectorsKV: { [id: string]: string } = {};

  /**
   * Creates a new WatchlistDividendSplitFeedComponent with all required services and dependencies.
   * Initializes the watchlist table with dividend and split feed specific columns and configurations.
   * @param securityService Service for security-related operations and data access
   * @param currencypairService Service for currency pair operations and data access
   * @param dialogService PrimeNG service for managing dynamic dialogs
   * @param alarmSetupService Service for setting up and managing security alarms
   * @param timeSeriesQuotesService Service for time series quote data and operations
   * @param dataChangedService Service for handling data change notifications across components
   * @param activePanelService Service for managing active panel state in the application
   * @param watchlistService Service for watchlist operations and data management
   * @param router Angular router for navigation between routes
   * @param activatedRoute Current activated route for accessing route parameters
   * @param confirmationService PrimeNG service for displaying confirmation dialogs
   * @param messageToastService Service for displaying toast notification messages
   * @param productIconService Service for retrieving product-specific icons
   * @param changeDetectionStrategy Angular change detection strategy for performance optimization
   * @param filterService PrimeNG service for table filtering operations
   * @param translateService Angular service for internationalization and text translation
   * @param gpsGT Global parameter service for GT-specific configurations
   * @param gps Global parameter service for application-wide settings and parameters
   * @param usersettingsService Service for managing user-specific settings and preferences
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
    super(WatchListType.DIVIDEND_SPLIT_FEED, AppSettings.WATCHLIST_DIVIDEND_SPLIT_FEED_TABLE_SETTINGS_STORE,
      dialogService, alarmSetupService, timeSeriesQuotesService, dataChangedService, activePanelService, watchlistService, router,
      activatedRoute, confirmationService, messageToastService, productIconService, changeDetectionStrategy,
      filterService, translateService, gpsGT, gps, usersettingsService, WatchlistTable.MULTIPLE);
    this.addBaseColumns();
    this.addColumnFeqH(DataType.String, 'securitycurrency.distributionFrequency', true,
      true, {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.String, 'securitycurrency.idConnectorDividend', true,
      true, {fieldValueFN: this.getFeedConnectorReadableName.bind(this)});
    this.addColumnFeqH(DataType.NumericInteger, 'securitycurrency.retryDividendLoad', true,
      true);
    this.addColumnFeqH(DataType.DateNumeric, 'securitycurrency.dividendEarliestNextCheck', true, true);
    this.addColumnFeqH(DataType.String, 'securitycurrency.idConnectorSplit', true,
      true, {fieldValueFN: this.getFeedConnectorReadableName.bind(this)});
    this.addColumnFeqH(DataType.NumericInteger, 'securitycurrency.retrySplitLoad', true, true);
    this.prepareTableAndTranslate();
    this.watchlistHasModifiedFromOutside();
  }

  /**
   * Transforms feed connector IDs into human-readable names for display in table columns.
   * Used as a field value function to convert technical connector identifiers into user-friendly text.
   * @param dataobject The data object containing the row data (unused in current implementation)
   * @param field The column configuration object (unused in current implementation)
   * @param valueField The connector ID value to be transformed into readable name
   */
  getFeedConnectorReadableName(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.feedConnectorsKV[valueField];
  }

  /** Initializes the component by calling base initialization and loading connector data */
  ngOnInit(): void {
    this.init();
    this.loadData();
  }

  /**
   * Retrieves watchlist data specific to dividend and split feeds without triggering price updates.
   * Combines watchlist data and tenant limits in a single operation for efficient loading.
   */
  protected override getWatchlistWithoutUpdate(): void {
    const watchListObservable: Observable<SecuritycurrencyGroup> =
      this.watchlistService.getWatchlistForSplitAndDividend(this.idWatchlist);
    const tenantLimitObservable: Observable<TenantLimit[]> =
      this.watchlistService.getSecuritiesCurrenciesWatchlistLimits(this.idWatchlist);
    combineLatest([watchListObservable, tenantLimitObservable]).subscribe((result: [SecuritycurrencyGroup, TenantLimit[]]) => {
      this.createSecurityPositionList(result[0]);
      this.tenantLimits = result[1];
      this.loading = false;
    });
  }

  public override getHelpContextId(): HelpIds {
    return HelpIds.HELP_WATCHLIST_DIVIDEND_SPLIT_FEED;
  }

  /** Refreshes all watchlist data by setting loading state and retrieving updated information */
  protected override updateAllPrice(): void {
    this.loading = true;
    this.getWatchlistWithoutUpdate();
  }

  /**
   * Loads feed connector mappings and initializes watchlist data.
   * Uses SecurityCurrencyHelper to populate the connector name mappings before loading watchlist content.
   */
  private loadData(): void {
    SecurityCurrencyHelper.loadAllConnectors(this.securityService, this.currencypairService, this.feedConnectorsKV,
      this.getWatchlistWithoutUpdate.bind(this));
  }

}
