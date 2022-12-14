import {SecurityaccountService} from '../../securityaccount/service/securityaccount.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {ActivatedRoute, Router} from '@angular/router';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {Component, OnDestroy, OnInit} from '@angular/core';
import {SecurityPositionGrandSummary} from '../../entities/view/security.position.grand.summary';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {TranslateService} from '@ngx-translate/core';
import {SecurityaccountAssetclassCategortypeGroup} from '../../securityaccount/component/securityaccount.assetclass.categortype.group';
import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {SecurityaccountBaseTable} from '../../securityaccount/component/securityaccount.base.table';
import {AppSettings} from '../../shared/app.settings';
import {OptionalParameters, TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {FilterService} from 'primeng/api';

/**
 * It groups asset classes of securities and includes saldo of cash accounts as an asset class.
 */
@Component({
  templateUrl: '../../securityaccount/view/securityaccount.table.html'
})
export class TenantSummariesAssetclassComponent extends SecurityaccountBaseTable implements OnInit, OnDestroy {

  constructor(private parentChildRegisterService: ParentChildRegisterService,
              timeSeriesQuotesService: TimeSeriesQuotesService,
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

  protected getTitleChart(): string {
    return this.translatedTitle;
  }

  protected getComponentId(): string {
    return AppSettings.DEPOT_CASH_KEY;
  }

  protected extendTransactionParamData(transactionCallParam: TransactionCallParam): void {
  }

  protected getOptionalParameters(): OptionalParameters {
    return null;
  }

}
