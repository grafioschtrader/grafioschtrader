import {Component, OnDestroy, OnInit} from '@angular/core';
import {SecurityaccountTable} from '../../securityaccount/component/securityaccountTable';
import {SecurityPositionGrandSummary} from '../../entities/view/security.position.grand.summary';

import {ActivatedRoute, Router} from '@angular/router';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {SecurityaccountService} from '../../securityaccount/service/securityaccount.service';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {AppSettings} from '../../shared/app.settings';
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
 * It is the summary for all security accounts with its securities of a for a certain tenant.
 * The user can change the grouping for example by currency or financial instrument.
 */
@Component({
    templateUrl: '../../securityaccount/view/securityaccount.table.html',
    standalone: true,
    imports: [CommonModule, TranslateModule, TableModule, DatePicker, FormsModule, SelectModule, TooltipModule, ContextMenuModule,
      TransactionSecurityTableComponent, TransactionSecurityMarginTreetableComponent,
      TransactionCashaccountTableComponent, TransactionSecurityEditComponent]
})
export class TenantSummariesSecurityaccountComponent extends SecurityaccountTable implements OnInit, OnDestroy {

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

  protected override getComponentId(): string {
    return AppSettings.DEPOT_KEY;
  }

  protected extendTransactionParamData(transactionCallParam: TransactionCallParam): void {
  }

  protected getOptionalParameters(): OptionalParameters {
    return null;
  }

}
