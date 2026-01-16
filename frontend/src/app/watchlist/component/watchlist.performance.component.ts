import {TimeFrame, WatchlistTable, WatchListType} from './watchlist.table';
import {ChangeDetectorRef, Component, Injector, OnDestroy, OnInit} from '@angular/core';
import {DataChangedService} from '../../lib/maintree/service/data.changed.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {WatchlistService} from '../service/watchlist.service';
import {ActivatedRoute, Router} from '@angular/router';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import moment from 'moment';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {AppSettings} from '../../shared/app.settings';
import {SecuritycurrencyGroup} from '../../entities/view/securitycurrency.group';
import {HelpIds} from '../../lib/help/help.ids';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {SecuritycurrencyPosition} from '../../entities/view/securitycurrency.position';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {ViewSizeChangedService} from '../../lib/layout/service/view.size.changed.service';
import {combineLatest, Observable} from 'rxjs';
import {TenantLimit} from '../../shared/types/tenant.limit';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {AlarmSetupService} from '../../algo/service/alarm.setup.service';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
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
 * Shows the performance watchlist. It has no special function implemented.
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
export class WatchlistPerformanceComponent extends WatchlistTable implements OnInit, OnDestroy {

  /**
   * Creates a new WatchlistPerformanceComponent with all required dependencies and initializes performance columns
   * and time frames.
   */
  constructor(private viewSizeChangedService: ViewSizeChangedService,
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
    usersettingsService: UserSettingsService,
    injector: Injector) {
    super(WatchListType.PERFORMANCE, AppSettings.WATCHLIST_PERFORMANCE_TABLE_SETTINGS_STORE, dialogService, alarmSetupService,
      timeSeriesQuotesService, dataChangedService, activePanelService, watchlistService, router, activatedRoute, confirmationService,
      messageToastService, productIconService, changeDetectionStrategy, filterService, translateService, gpsGT, gps,
      usersettingsService, WatchlistTable.SINGLE, injector);
    const date = new Date();


    this.timeFrames.push(new TimeFrame('THIS_WEEK', moment().weekday()));
    this.timeFrames.push(new TimeFrame('DAYS_30', 30));
    this.timeFrames.push(new TimeFrame('DAYS_90', 90));
    this.timeFrames.push(new TimeFrame('DAYS_180', 180));
    this.timeFrames.push(new TimeFrame('YEAR_1', moment(date).diff(moment(date).subtract(1, 'years'), 'days')));
    this.timeFrames.push(new TimeFrame('YEAR_2', moment(date).diff(moment(date).subtract(2, 'years'), 'days')));
    this.timeFrames.push(new TimeFrame('YEAR_3', moment(date).diff(moment(date).subtract(3, 'years'), 'days')));
    this.choosenTimeFrame = this.timeFrames[0];
    this.addBaseColumns();
    this.addColumn(DataType.String, 'securitycurrency.assetClass.categoryType', AppSettings.ASSETCLASS.toUpperCase(), true, true,
      {translateValues: TranslateValue.NORMAL, width: 60});
    this.addColumn(DataType.String, 'securitycurrency.assetClass.specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', false, true,
      {translateValues: TranslateValue.NORMAL, width: 60});

    this.addColumn(DataType.String, 'securitycurrency.assetClass.subCategoryNLS.map.' + this.gps.getUserLang(),
      'SUB_ASSETCLASS', true, true, {width: 80});

    this.addColumnFeqH(DataType.NumericRaw, 'securitycurrency.leverageFactor', true, true, {
      templateName: 'greenRed', fieldValueFN: BusinessHelper.getDisplayLeverageFactor.bind(this)
    });

    this.addColumn(DataType.DateTimeNumeric, 'securitycurrency.sTimestamp', 'TIMEDATE', true, true, {width: 80});
    this.addColumn(DataType.Numeric, 'securitycurrency.sLast', 'LAST', true, true,
      {maxFractionDigits: gps.getMaxFractionDigits()});
    this.addColumn(DataType.Numeric, 'securitycurrency.sChangePercentage', 'DAILY_CHANGE', true, true, {
      headerSuffix: '%', templateName: 'greenRed'
    });
    this.addColumn(DataType.Numeric, 'ytdChangePercentage', 'YTD', true, true, {
      headerSuffix: '%',
      templateName: 'greenRed'
    });
    this.addColumn(DataType.Numeric, 'timeFrameChangePercentage', 'TIME_FRAME', true, true, {
      headerSuffix: '%', templateName: 'greenRed'
    });
    this.addColumn(DataType.Numeric, 'timeFrameAnnualChangePercentage', 'TIME_FRAME_ANNUAL', true, true, {
      headerSuffix: '%', templateName: 'greenRed'
    });
    this.addColumn(DataType.Numeric, 'units', 'HOLDING', true, true,
      {templateName: 'greenRed'});

    this.addColumnFeqH(DataType.Numeric, 'positionGainLossPercentage', true, true, {
      headerSuffix: '%', templateName: 'greenRed'
    });
    this.addColumnFeqH(DataType.Numeric, 'valueSecurity', true, true);
    this.addColumn(DataType.Numeric, 'securitycurrency.sPrevClose', 'DAY_BEFORE_CLOSE',
      true, true, {maxFractionDigits: gps.getMaxFractionDigits()});
    this.addColumn(DataType.Numeric, 'securitycurrency.sHigh', 'HIGH', true, true,
      {maxFractionDigits: gps.getMaxFractionDigits()});
    this.addColumn(DataType.Numeric, 'securitycurrency.sLow', 'LOW', true, true,
      {maxFractionDigits: gps.getMaxFractionDigits()});

    this.prepareTableAndTranslate();
    this.watchlistHasModifiedFromOutside();
  }

  ngOnInit(): void {
    this.init();
    this.getWatchlistWithoutUpdate();
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_WATCHLIST_PERFORMANCE;
  }


  /** Loads watchlist data without price updates and combines with tenant limit information. */
  protected override getWatchlistWithoutUpdate(): void {
    const watchListObservable: Observable<SecuritycurrencyGroup> = this.watchlistService.getWatchlistWithoutUpdate(this.idWatchlist);
    const tenantLimitObservable: Observable<TenantLimit[]> = this.watchlistService.getSecuritiesCurrenciesWatchlistLimits(this.idWatchlist);
    combineLatest([watchListObservable, tenantLimitObservable]).subscribe((result: [SecuritycurrencyGroup, TenantLimit[]]) => {
      this.createSecurityPositionList(result[0]);
      this.tenantLimits = result[1];
      this.loading = false;
      this.updateAllPrice();
    });
  }

  /** Updates all price data for the watchlist by delegating to REST service implementation. */
  protected override updateAllPrice(): void {
    this.updateAllPriceThruRest();
  }

  /**
   * Creates the show menu with context items, time frame selection, and column visibility options.
   * @param securitycurrencyPosition The currently selected security or currency position
   * @returns Array of translated menu items for the show menu
   */
  protected override getShowMenu(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>): MenuItem[] {
    const menuItems = [...this.getShowContextMenuItems(securitycurrencyPosition, false), {separator: true},
      this.getMenuTimeFrame(), ...this.getMenuShowOptions()];
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  /** Updates price data through REST service call with period performance data for the selected time frame. */
  private updateAllPriceThruRest(): void {
    this.watchlistService.getWatchlistWithPeriodPerformance(this.idWatchlist, this.choosenTimeFrame.days)
      .subscribe((data: SecuritycurrencyGroup) => {
        if (this.watchlist.idWatchlist === data.idWatchlist) {
          this.selectedSecuritycurrencyPosition = null;
          this.createSecurityPositionList(data);
        }
      });
  }

  /** Creates the time frame selection menu with checkable items for different time periods. */
  private getMenuTimeFrame(): MenuItem {
    const childMenuItems: MenuItem[] = [];
    this.timeFrames.forEach(timeFrame => {
      childMenuItems.push({
        label: timeFrame.name,
        icon: (this.choosenTimeFrame === timeFrame) ? AppSettings.ICONNAME_CIRCLE_CHECK : AppSettings.ICONNAME_CIRCLE_EMTPY,
        command: (event) => this.handleTimeFrame(event, timeFrame, childMenuItems)
      });
    });
    return {label: 'TIME_FRAME', items: childMenuItems};
  }

  /**
   * Handles time frame selection by updating the chosen time frame, menu icons, column visibility, and refreshing data.
   * @param event The menu click event
   * @param timeFrame The selected time frame object
   * @param childMenuItems The array of child menu items to update icons for
   */
  private handleTimeFrame(event, timeFrame: TimeFrame, childMenuItems: MenuItem[]) {
    this.choosenTimeFrame = timeFrame;
    childMenuItems.forEach(menuItem => menuItem.icon = AppSettings.ICONNAME_CIRCLE_EMTPY);
    event.item.icon = AppSettings.ICONNAME_CIRCLE_CHECK;
    this.hideShowColumnByFileHeader('TIME_FRAME_ANNUAL', timeFrame.days > 600);
    this.updateAllPrice();
  }

}

