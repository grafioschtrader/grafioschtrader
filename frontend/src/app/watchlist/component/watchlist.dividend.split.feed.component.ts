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
import {ConfirmationService} from 'primeng/api';
import {MessageToastService} from '../../shared/message/message.toast.service';
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
import {ColumnConfig} from '../../shared/datashowbase/column.config';
import {HelpIds} from '../../shared/help/help.ids';

/**
 * View to check the reliability of the dividend, split feed.
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

    table {
      border: 1px solid #555;
    }

  `],
  providers: [DialogService]
})
export class WatchlistDividendSplitFeedComponent extends WatchlistTable implements OnInit, OnDestroy {
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
              translateService: TranslateService,
              globalparameterService: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(WatchListType.DIVIDEND_SPLIT_FEDED, AppSettings.WATCHLIST_DIVIDEND_SPLIT_FEED_TABLE_SETTINGS_STORE,
      dialogService, timeSeriesQuotesService, dataChangedService, activePanelService, watchlistService, router,
      activatedRoute, confirmationService, messageToastService, productIconService, changeDetectionStrategy,
      translateService, globalparameterService, usersettingsService
    );
    this.addBaseColumns();
      this.addColumnFeqH(DataType.String, 'securitycurrency.distributionFrequency', true,
      true, {translateValues: true});
    this.addColumnFeqH(DataType.String, 'securitycurrency.idConnectorDividend', true,
      true, {fieldValueFN: this.getFeedConnectorReadableName.bind(this)});
    this.addColumnFeqH(DataType.NumericInteger, 'securitycurrency.retryDividendLoad', true,
      true);
    this.addColumnFeqH(DataType.String, 'securitycurrency.idConnectorSplit', true,
      true, {fieldValueFN: this.getFeedConnectorReadableName.bind(this)});
    this.addColumnFeqH(DataType.NumericInteger, 'securitycurrency.retrySplitLoad', true, true);
    this.prepareTableAndTranslate();
    this.watchlistHasModifiedFromOutside();
  }


  getFeedConnectorReadableName(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.feedConnectorsKV[valueField];
  }

  ngOnInit(): void {
    this.init();
    this.loadData();
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

  protected updateAllPrice() {
    this.loading = true;
    this.getWatchlistWithoutUpdate();
  }

  private loadData(): void {
    SecurityCurrencyHelper.loadAllConnectors(this.securityService, this.currencypairService, this.feedConnectorsKV,
      this.getWatchlistWithoutUpdate.bind(this));
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_WATCHLIST_DIVIDEND_SPLIT_FEED;
  }


}
