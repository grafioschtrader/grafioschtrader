import {WatchlistTable, WatchListType} from './watchlist.table';
import {ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {WatchlistService} from '../service/watchlist.service';
import {ActivatedRoute, Router} from '@angular/router';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {AppSettings} from '../../shared/app.settings';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {ColumnConfig} from '../../shared/datashowbase/column.config';
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
import {AuditHelper} from '../../shared/helper/audit.helper';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {SecurityCurrencyHelper} from '../../securitycurrency/service/security.currency.helper';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';

/**
 * View to check the reliability of the price data feeds. It has some special function implemented to update price data.
 */
@Component({
  templateUrl: '../view/watchlist.data.html',
  styles: [`
    :host ::ng-deep .ui-table .ui-table-thead > tr > th {
      position: -webkit-sticky;
      position: sticky;
      top: 0px;
    }

    .cell-move {
      cursor: move !important;
    }
  `],
  providers: [DialogService]
})
export class WatchlistPriceFeedComponent extends WatchlistTable implements OnInit, OnDestroy {

  private readonly f_retryHistoryLoad = 'retryHistoryLoad';
  private readonly f_retryIntraLoad = 'retryIntraLoad';
  private feedConnectorsKV: { [id: string]: string } = {};


  constructor(private securityService: SecurityService,
              private currencypairService: CurrencypairService,
              dialogService: DialogService,
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
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(WatchListType.PRICE_FEED, AppSettings.WATCHLIST_PRICE_FEED_TABLE_SETTINGS_STORE, dialogService, timeSeriesQuotesService,
      dataChangedService, activePanelService, watchlistService, router, activatedRoute, confirmationService,
      messageToastService, productIconService, changeDetectionStrategy, filterService, translateService,
      gps, usersettingsService, WatchlistTable.SINGLE);
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

  getWatchlistWithoutUpdate() {
    const watchListObservable: Observable<SecuritycurrencyGroup> =
      this.watchlistService.getWatchlistWithoutUpdateAndMaxHistoryquote(this.idWatchlist);
    const tenantLimitObservable: Observable<TenantLimit[]> = this.watchlistService.getSecuritiesCurrenciesWatchlistLimits(this.idWatchlist);

    combineLatest([watchListObservable, tenantLimitObservable]).subscribe(result => {
      this.createSecurityPositionList(result[0]);
      this.tenantLimits = result[1];
      this.loading = false;
    });
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_WATCHLIST_PRICE_FEED;
  }

  protected getEditMenuItems(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>): MenuItem[] {
    if (this.securityPositionList && AuditHelper.hasHigherPrivileges(this.gps)) {
      const menuItems: MenuItem[] = [
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

  protected updateAllPrice() {
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

}

