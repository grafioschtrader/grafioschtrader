import {Component, OnDestroy, OnInit} from '@angular/core';
import {SecurityaccountTable} from './securityaccountTable';
import {ActivatedRoute, Params, Router} from '@angular/router';
import {SecurityPositionGrandSummary} from '../../entities/view/security.position.grand.summary';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {SecurityaccountService} from '../service/securityaccount.service';
import {TranslateService} from '@ngx-translate/core';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {Subscription} from 'rxjs';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {OptionalParameters, TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {FilterService} from 'primeng/api';


/**
 * It is the summary for all security accounts with its Securities of a certain portfolio. The id of a portfolio
 * is expected.
 */
@Component({
  templateUrl: '../view/securityaccount.table.html'
})
export class SecurityaccountSummariesComponent extends SecurityaccountTable implements OnInit, OnDestroy {

  private routeSubscribe: Subscription;

  constructor(timeSeriesQuotesService: TimeSeriesQuotesService,
              activePanelService: ActivePanelService,
              messageToastService: MessageToastService,
              securityaccountService: SecurityaccountService,
              productIconService: ProductIconService,
              activatedRoute: ActivatedRoute,
              router: Router,
              chartDataService: ChartDataService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(timeSeriesQuotesService, activePanelService, messageToastService, securityaccountService, productIconService,
      activatedRoute, router, chartDataService, filterService, translateService, gps, usersettingsService);
    this.createColumns();
  }

  ngOnInit() {
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      this.idPortfolio = +params['id'];
      this.portfolio = JSON.parse(params['object']);
      this.readData();
    });
  }

  readData() {
    this.selectedSecurityPositionSummary = null;
    this.securityaccountService.getSecurityPositionSummaryPortfolio(this.idPortfolio, this.securityaccountGroupBase.defaultGroup,
      this.includeClosedPosition, this.untilDate)
      .subscribe((data: SecurityPositionGrandSummary) => {
        this.getDataToView(data);
        this.initTableTextTranslation();
        // this.changeToOpenChart();
      });
  }

  ngOnDestroy(): void {
    super.destroy();
    this.routeSubscribe.unsubscribe();
  }

  protected extendTransactionParamData(transactionCallParam: TransactionCallParam): void {
    transactionCallParam.portfolio = this.portfolio;

  }

  protected getOptionalParameters(): OptionalParameters {
    return {idPortfolio: this.idPortfolio};
  }

}

