import {Component, OnDestroy, OnInit} from '@angular/core';
import {SecurityaccountTable} from '../../securityaccount/component/securityaccountTable';
import {SecurityPositionGrandSummary} from '../../entities/view/security.position.grand.summary';

import {ActivatedRoute, Router} from '@angular/router';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {SecurityaccountService} from '../../securityaccount/service/securityaccount.service';
import {TranslateService} from '@ngx-translate/core';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {AppSettings} from '../../shared/app.settings';
import {OptionalParameters, TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {FilterService} from 'primeng/api';

/**
 * It is the summary for all security accounts with its securities of a for a certain tenant.
 * The user can change the grouping for example by currency or financial instrument.
 */
@Component({
  templateUrl: '../../securityaccount/view/securityaccount.table.html'
})
export class TenantSummariesSecurityaccountComponent extends SecurityaccountTable implements OnInit, OnDestroy {

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
  }

  ngOnInit() {
    this.idTenant = this.gps.getIdTenant();
    this.readData();
  }

  readData() {
    this.selectedSecurityPositionSummary = null;
    this.securityaccountService.getSecurityPositionSummaryTenant(this.securityaccountGroupBase.defaultGroup,
      this.includeClosedPosition, this.untilDate)
      .subscribe((data: SecurityPositionGrandSummary) => {
        this.getDataToView(data);
        this.initTableTextTranslation();
      });
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  protected getComponentId(): string {
    return AppSettings.DEPOT_KEY;
  }

  protected extendTransactionParamData(transactionCallParam: TransactionCallParam): void {
  }

  protected getOptionalParameters(): OptionalParameters {
    return null;
  }

}
