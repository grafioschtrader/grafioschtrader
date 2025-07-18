import {WatchlistTable, WatchListType} from './watchlist.table';
import {ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {WatchlistService} from '../service/watchlist.service';
import {ActivatedRoute, Router} from '@angular/router';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {AppSettings} from '../../shared/app.settings';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {SecuritycurrencyGroup} from '../../entities/view/securitycurrency.group';
import {SecuritycurrencyPosition} from '../../entities/view/securitycurrency.position';
import {HelpIds} from '../../shared/help/help.ids';
import {TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {combineLatest, Observable} from 'rxjs';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {SecurityCurrencyHelper} from '../../securitycurrency/service/security.currency.helper';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {AlarmSetupService} from '../../algo/service/alarm.setup.service';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {BaseSettings} from '../../lib/base.settings';

/**
 * View to check the reliability of the price data feeds. It has some special function implemented to update price data.
 */
@Component({
    templateUrl: '../view/watchlist.data.html',
    styles: [`
    .cell-move {
      cursor: move !important;
    }
  `],
    providers: [DialogService],
    standalone: false
})
export class WatchlistPriceFeedComponent extends WatchlistTable implements OnInit, OnDestroy {

  feedConnectorsKV: { [id: string]: string } = {};
  public visibleAddPriceProblemDialog = false;
  private readonly f_retryHistoryLoad = 'retryHistoryLoad';
  private readonly f_retryIntraLoad = 'retryIntraLoad';

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

  ngOnInit(): void {
    this.init();
    this.loadData();
  }

  getFeedConnectorReadableName(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.feedConnectorsKV[valueField];
  }

  protected override getWatchlistWithoutUpdate(): void {
    const watchListObservable: Observable<SecuritycurrencyGroup> =
      this.watchlistService.getWatchlistWithoutUpdateAndMaxHistoryquote(this.idWatchlist);
    const tenantLimitObservable: Observable<TenantLimit[]> = this.watchlistService.getSecuritiesCurrenciesWatchlistLimits(this.idWatchlist);
    combineLatest([watchListObservable, tenantLimitObservable]).subscribe(result => {
      this.createSecurityPositionList(result[0]);
      this.tenantLimits = result[1];
      this.loading = false;
    });
  }

  public override getHelpContextId(): HelpIds {
    return HelpIds.HELP_WATCHLIST_PRICE_FEED;
  }

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

  protected override updateAllPrice(): void {
    this.loading = true;
    this.getWatchlistWithoutUpdate();
  }

  private disableUpToDateFeedDataMenu(propName: string, untilDate: Date): boolean {
    return this.securityPositionList.every(sp => sp.securitycurrency[propName] === 0
      || (!(sp.securitycurrency instanceof CurrencypairWatchlist)
        && new Date((<Security>sp.securitycurrency).activeToDate) <= (untilDate === null ? sp.youngestHistoryDate : untilDate)));
  }

  private loadData(): void {
    SecurityCurrencyHelper.loadAllConnectors(this.securityService, this.currencypairService, this.feedConnectorsKV,
      this.getWatchlistWithoutUpdate.bind(this));
  }


  handleCloseAddPriceProblemInstrument(processedActionData: ProcessedActionData): void {
    this.visibleAddPriceProblemDialog = false;
    this.updateAllPrice();
  }

}

