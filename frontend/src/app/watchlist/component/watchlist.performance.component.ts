import {TimeFrame, WatchlistTable, WatchListType} from './watchlist.table';
import {ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {WatchlistService} from '../service/watchlist.service';
import {ActivatedRoute, Router} from '@angular/router';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import * as moment from 'moment';
import {DataType} from '../../dynamic-form/models/data.type';
import {AppSettings} from '../../shared/app.settings';
import {SecuritycurrencyGroup} from '../../entities/view/securitycurrency.group';
import {HelpIds} from '../../shared/help/help.ids';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {SecuritycurrencyPosition} from '../../entities/view/securitycurrency.position';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {ViewSizeChangedService} from '../../shared/layout/service/view.size.changed.service';
import {combineLatest, Observable, Subscription} from 'rxjs';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {InjectableRxStompConfig, RxStompService} from '@stomp/ng2-stompjs';
import {GlobalSessionNames} from '../../shared/global.session.names';


@Component({
  templateUrl: '../view/watchlist.data.html',
  styles: [`
    .cell-move {
      cursor: move !important;
    }
  `],
  providers: [DialogService]
  // changeDetection: ChangeDetectionStrategy.OnPush
})
export class WatchlistPerformanceComponent extends WatchlistTable implements OnInit, OnDestroy {

  private subscriptionViewSizeChanged: Subscription;
  private topicSubscription: Subscription;

  stompConfig: InjectableRxStompConfig = {
    heartbeatIncoming: 0,
    heartbeatOutgoing: 20000,
    reconnectDelay: 1000,
    connectHeaders: {'x-auth-token': sessionStorage.getItem('jwt')},
    debug: (str) => {
      console.log(str);
    }
  };

  constructor(private stompService: RxStompService,
              private updatedStompConfig: InjectableRxStompConfig,
              private viewSizeChangedService: ViewSizeChangedService,
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
              globalparameterService: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(WatchListType.PERFORMANCE, AppSettings.WATCHLIST_PERFORMANCE_TABLE_SETTINGS_STORE, dialogService, timeSeriesQuotesService,
      dataChangedService, activePanelService, watchlistService, router, activatedRoute, confirmationService,
      messageToastService, productIconService, changeDetectionStrategy, filterService, translateService, globalparameterService,
      usersettingsService, WatchlistTable.SINGLE);
    const date = new Date();
    // Update StompJs configuration.
    this.stompConfig = {...this.stompConfig, ...this.updatedStompConfig};
    console.log('stomconfig', this.stompConfig);
    this.stompService.configure(this.stompConfig);


    this.timeFrames.push(new TimeFrame('THIS_WEEK', moment().weekday()));
    this.timeFrames.push(new TimeFrame('DAYS_30', 30));
    this.timeFrames.push(new TimeFrame('DAYS_90', 90));
    this.timeFrames.push(new TimeFrame('DAYS_180', 180));
    this.timeFrames.push(new TimeFrame('YEAR_1', moment(date).diff(moment(date).subtract(1, 'years'), 'days')));
    this.timeFrames.push(new TimeFrame('YEAR_2', moment(date).diff(moment(date).subtract(2, 'years'), 'days')));
    this.timeFrames.push(new TimeFrame('YEAR_3', moment(date).diff(moment(date).subtract(3, 'years'), 'days')));
    this.choosenTimeFrame = this.timeFrames[0];
  this.addBaseColumns();
       this.addColumn(DataType.String, 'securitycurrency.assetClass.categoryType', 'ASSETCLASS', true, true,
      {translateValues: true, width: 60});
    this.addColumn(DataType.String, 'securitycurrency.assetClass.specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', false, true,
      {translateValues: true, width: 60});

    this.addColumn(DataType.String, 'securitycurrency.assetClass.subCategoryNLS.map.' + this.globalparameterService.getUserLang(),
      'SUB_ASSETCLASS', true, true, {width: 80});

    this.addColumn(DataType.Boolean, 'securitycurrency.shortSecurity', 'SHORT_SECURITY', true, true, {
      templateName: 'check'
    });


    this.addColumn(DataType.DateTimeNumeric, 'securitycurrency.sTimestamp', 'TIMEDATE', true, true, {width: 80});
    this.addColumn(DataType.Numeric, 'securitycurrency.sLast', 'LAST', true, true, {maxFractionDigits: 5});
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
    this.addColumn(DataType.Numeric, 'timeFrameAnualChangePercentage', 'TIME_FRAME_ANUAL', true, true, {
      headerSuffix: '%', templateName: 'greenRed'
    });
    this.addColumn(DataType.Numeric, 'units', 'QUANTITY', true, true,
      {templateName: 'greenRed'});

    this.addColumnFeqH(DataType.Numeric, 'positionGainLossPercentage', true, true, {
      headerSuffix: '%', templateName: 'greenRed'
    });

    this.addColumn(DataType.Numeric, 'valueSecurity', 'TOTAL_AMOUNT', true, true);
    this.addColumn(DataType.Numeric, 'securitycurrency.sPrevClose', 'DAY_BEFORE_CLOSE', true, true, {maxFractionDigits: 5});
    this.addColumn(DataType.Numeric, 'securitycurrency.sHigh', 'HIGH', true, true, {maxFractionDigits: 5});
    this.addColumn(DataType.Numeric, 'securitycurrency.sLow', 'LOW', true, true, {maxFractionDigits: 5});

    this.prepareTableAndTranslate();
    this.watchlistHasModifiedFromOutside();
  }

  ngOnInit(): void {
    this.init();
    this.getWatchlistWithoutUpdate();
  }

  protected updateAllPriceWebSocket() {
    if (this.topicSubscription) {
      this.sendMessage(this.idWatchlist, this.choosenTimeFrame.days);
    } else {
      this.sendMessage(this.idWatchlist, this.choosenTimeFrame.days);
      this.getAndSetDataWebsocket();
    }
  }


  private getAndSetDataWebsocket(): void {
    this.topicSubscription = this.stompService.watch('/user/queue/security',
      this.stompConfig.connectHeaders).subscribe(securitycurrencyGroup => {
      const sg: SecuritycurrencyGroup = JSON.parse(securitycurrencyGroup.body);
      if (this.watchlist.idWatchlist === sg.idWatchlist) {
        this.selectedSecuritycurrencyPosition = null;
        this.createSecurityPositionList(sg);
      }
    });
  }

  sendMessage(idWatchlist: number, daysFrameDate: number) {

    this.stompService.publish({
      destination: '/app/ws/watchlist',
      headers: this.stompConfig.connectHeaders,
      body: JSON.stringify({idWatchlist: idWatchlist, daysFrameDate: daysFrameDate})
    });
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_WATCHLIST_PERFORMANCE;
  }

  ngOnDestroy(): void {
    this.topicSubscription && this.topicSubscription.unsubscribe();
    super.ngOnDestroy();
  }

  protected getWatchlistWithoutUpdate() {
    const watchListObservable: Observable<SecuritycurrencyGroup> = this.watchlistService.getWatchlistWithoutUpdate(this.idWatchlist);
    const tenantLimitObservable: Observable<TenantLimit[]> = this.watchlistService.getSecuritiesCurrenciesWatchlistLimits(this.idWatchlist);
    combineLatest([watchListObservable, tenantLimitObservable]).subscribe(result => {
      this.createSecurityPositionList(result[0]);
      this.tenantLimits = result[1];
      this.loading = false;
      this.updateAllPrice();
    });
  }

  protected updateAllPrice(): void {
    if (this.globalparameterService.useWebsocket()) {
      this.updateAllPriceWebSocket();
    } else {
      this.updateAllPriceThruRest();
    }
  }


  private updateAllPriceThruRest(): void {
    this.watchlistService.getWatchlistWithPeriodPerformance(this.idWatchlist, this.choosenTimeFrame.days)
      .subscribe((data: SecuritycurrencyGroup) => {

        if (this.watchlist.idWatchlist === data.idWatchlist) {
          this.selectedSecuritycurrencyPosition = null;
          this.createSecurityPositionList(data);
        }
      });
  }

  protected getShowMenu(securitycurrencyPosition: SecuritycurrencyPosition<Security | Currencypair>): MenuItem[] {
    const menuItems = [...this.getShowContextMenuItems(securitycurrencyPosition, false), {separator: true},
      this.getMenuTimeFrame(), ...this.getMenuShowOptions()];
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

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

  private handleTimeFrame(event, timeFrame: TimeFrame, childMenuItems: MenuItem[]) {

    this.choosenTimeFrame = timeFrame;
    childMenuItems.forEach(menuItem => menuItem.icon = AppSettings.ICONNAME_CIRCLE_EMTPY);
    event.item.icon = AppSettings.ICONNAME_CIRCLE_CHECK;
    this.hideShowColumnByFileHeader('TIME_FRAME_ANUAL', timeFrame.days > 600);
    this.updateAllPrice();

  }

}

