import {Component, OnDestroy, OnInit} from '@angular/core';
import {SecurityaccountTable} from './securityaccountTable';
import {ActivatedRoute, Params, Router} from '@angular/router';
import {SecurityPositionGrandSummary} from '../../entities/view/security.position.grand.summary';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {SecurityaccountService} from '../service/securityaccount.service';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {Subscription} from 'rxjs';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {OptionalParameters, TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {FilterService} from 'primeng/api';
import {AlarmSetupService} from '../../algo/service/alarm.setup.service';
import {CommonModule} from '@angular/common';
import {TableModule} from 'primeng/table';
import {DatePicker} from 'primeng/datepicker';
import {FormsModule} from '@angular/forms';
import {SelectModule} from 'primeng/select';
import {TooltipModule} from 'primeng/tooltip';
import {ContextMenuModule} from 'primeng/contextmenu';
import {TransactionSecurityTableComponent} from '../../transaction/component/transaction-security-table.component';
import {TransactionSecurityMarginTreetableComponent} from '../../transaction/component/transaction-security-margin-treetable.component';
import {TransactionCashaccountTableComponent} from '../../transaction/component/transaction-cashaccount-table.component';
import {TransactionSecurityEditComponent} from '../../transaction/component/transaction-security-edit.component';


/**
 * It is the summary for all security accounts with its securities of a certain portfolio. The id of a portfolio
 * is expected.
 */
@Component({
    templateUrl: '../view/securityaccount.table.html',
    standalone: true,
    imports: [CommonModule, TranslateModule, TableModule, DatePicker, FormsModule, SelectModule, TooltipModule, ContextMenuModule,
      TransactionSecurityTableComponent, TransactionSecurityMarginTreetableComponent,
      TransactionCashaccountTableComponent, TransactionSecurityEditComponent]
})
export class SecurityaccountSummariesComponent extends SecurityaccountTable implements OnInit, OnDestroy {

  private routeSubscribe: Subscription;

  constructor(timeSeriesQuotesService: TimeSeriesQuotesService,
              alarmSetupService: AlarmSetupService,
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
    super(timeSeriesQuotesService, alarmSetupService, activePanelService, messageToastService, securityaccountService,
      productIconService, activatedRoute, router, chartDataService, filterService, translateService, gps, usersettingsService);
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

