import { Securitycurrency } from '../../entities/securitycurrency';
import { DestroyRef, inject } from '@angular/core';
import { PerformanceReportDialogService } from '../../performanceperiod/service/performance-report-dialog.service';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { SecurityaccountTable } from './securityaccountTable';
import { Params } from '@angular/router';
import { SecurityPositionGrandSummary } from '../../entities/view/security.position.grand.summary';
import { TransactionCallParam } from '../../transaction/component/transaction.call.parm';
import { TranslateModule } from '@ngx-translate/core';
import { AppSettings } from '../../shared/app.settings';
import { BaseSettings } from '../../lib/base.settings';
import { TreeNavigationStateService } from '../../lib/maintree/service/tree.navigation.state.service';
import { Subscription } from 'rxjs';
import { OptionalParameters } from '../../historyquote/service/time.series.quotes.service';

import { TableModule } from '@openng/optimus-ui/table';
import { DatePicker } from '@openng/optimus-ui/datepicker';
import { FormsModule } from '@angular/forms';
import { SelectModule } from '@openng/optimus-ui/select';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { TransactionSecurityTableComponent } from '../../transaction/component/transaction-security-table.component';
import { TransactionSecurityMarginTreetableComponent } from '../../transaction/component/transaction-security-margin-treetable.component';
import { TransactionCashaccountTableComponent } from '../../transaction/component/transaction-cashaccount-table.component';
import { TransactionSecurityEditComponent } from '../../transaction/component/transaction-security-edit.component';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { NgxFileDropModule } from 'ngx-file-drop';

/**
 * It is the summary for all security accounts with its securities of a certain portfolio. The id of a portfolio
 * is expected.
 */
@Component({
  templateUrl: '../view/securityaccount.table.html',
  imports: [
    TranslateModule,
    TableModule,
    DatePicker,
    FormsModule,
    SelectModule,
    TooltipModule,
    ContextMenuModule,
    NgxFileDropModule,
    AngularSvgIconModule,
    TransactionSecurityTableComponent,
    TransactionSecurityMarginTreetableComponent,
    TransactionCashaccountTableComponent,
    TransactionSecurityEditComponent
  ]
})
export class SecurityaccountSummariesComponent extends SecurityaccountTable implements OnInit, OnDestroy {
  private treeNavState: TreeNavigationStateService = inject(TreeNavigationStateService);

  private routeSubscribe: Subscription;

  private statementReports = inject(PerformanceReportDialogService);
  private reportDestroyRef = inject(DestroyRef);

  override getMenuShowOptionsParam(selected: Securitycurrency) {
    return [
      ...(super.getMenuShowOptionsParam(selected) || []),
      this.statementReports.menu(
        () => this.untilDate,
        () => this.idPortfolio,
        this.reportDestroyRef
      )
    ];
  }

  constructor() {
    super();
    this.createColumns();
  }

  ngOnInit() {
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      this.idPortfolio = +params['id'];
      this.portfolio = this.treeNavState.getEntity<any>(
        BaseSettings.MAINVIEW_KEY + '/' + AppSettings.SECURITYACCOUNT_SUMMARIES_ROUTE_KEY,
        this.idPortfolio
      );
      this.readData();
    });
  }

  readData() {
    this.selectedSecurityPositionSummary = null;
    this.securityaccountService
      .getSecurityPositionSummaryPortfolio(
        this.idPortfolio,
        this.securityaccountGroupBase.defaultGroup,
        this.includeClosedPosition,
        this.untilDate
      )
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
    return { idPortfolio: this.idPortfolio };
  }
}
