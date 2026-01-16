import {SecurityaccountService} from '../../securityaccount/service/securityaccount.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {ActivatedRoute, Router} from '@angular/router';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {Component, Injector, OnDestroy, OnInit} from '@angular/core';
import {SecurityPositionGrandSummary} from '../../entities/view/security.position.grand.summary';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {SecurityaccountAssetclassCategortypeGroup} from '../../securityaccount/component/securityaccount.assetclass.categortype.group';
import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {SecurityaccountBaseTable} from '../../securityaccount/component/securityaccount.base.table';
import {AppSettings} from '../../shared/app.settings';
import {OptionalParameters, TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {FilterService} from 'primeng/api';
import {AlarmSetupService} from '../../algo/service/alarm.setup.service';
import {HelpIds} from '../../lib/help/help.ids';
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
 * It groups asset classes of securities and includes balance of cash accounts as an asset class.
 */
@Component({
    templateUrl: '../../securityaccount/view/securityaccount.table.html',
    standalone: true,
    imports: [CommonModule, TranslateModule, TableModule, DatePicker, FormsModule, SelectModule, TooltipModule, ContextMenuModule,
      TransactionSecurityTableComponent, TransactionSecurityMarginTreetableComponent,
      TransactionCashaccountTableComponent, TransactionSecurityEditComponent]
})
export class TenantSummariesAssetclassComponent extends SecurityaccountBaseTable implements OnInit, OnDestroy {

  constructor(private parentChildRegisterService: ParentChildRegisterService,
              timeSeriesQuotesService: TimeSeriesQuotesService,
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
              usersettingsService: UserSettingsService,
              injector: Injector) {
    super(timeSeriesQuotesService, alarmSetupService, activePanelService, messageToastService, securityaccountService,
      productIconService, activatedRoute, router, chartDataService, filterService, translateService, gps, usersettingsService, injector);
    this.securityaccountGroupBase = new SecurityaccountAssetclassCategortypeGroup(translateService, this);
    this.createColumns();
  }

  ngOnInit() {
    this.translateService.get('SECURITY_ASSETCLASS_WITH_CASH').subscribe(translatedTitle => this.translatedTitle = translatedTitle);
    this.parentChildRegisterService.initRegistry();
    this.readData();
    this.onComponentClick(null);
  }

  readData() {
    this.selectedSecurityPositionSummary = null;
    this.securityaccountService.getSecurityPositionSummaryTenant('assetclasstypewithcash',
      this.includeClosedPosition, this.untilDate)
      .subscribe((data: SecurityPositionGrandSummary) => {
        this.getDataToView(data);
        this.initTableTextTranslation();
      });
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_SECURITY_CASH_ACCOUNT_REPORT;
  }

  protected override getTitleChart(): string {
    return this.translatedTitle;
  }

  protected override getComponentId(): string {
    return AppSettings.DEPOT_CASH_KEY;
  }

  protected extendTransactionParamData(transactionCallParam: TransactionCallParam): void {
  }

  protected getOptionalParameters(): OptionalParameters {
    return null;
  }

}
