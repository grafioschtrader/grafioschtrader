import { httpResource } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  OnDestroy,
  effect,
  inject,
  input,
  numberAttribute,
  signal,
  untracked
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { DatePicker } from '@openng/optimus-ui/datepicker';
import { SelectModule } from '@openng/optimus-ui/select';
import { TableModule } from '@openng/optimus-ui/table';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { NgxFileDropModule } from 'ngx-file-drop';
import { Securitycurrency } from '../../entities/securitycurrency';
import type { SecurityPositionGrandSummary } from '../../entities/view/security.position.grand.summary';
import { OptionalParameters } from '../../historyquote/service/time.series.quotes.service';
import { BaseSettings } from '../../lib/base.settings';
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';
import { TreeNavigationStateService } from '../../lib/maintree/service/tree.navigation.state.service';
import { PerformanceReportDialogService } from '../../performanceperiod/service/performance-report-dialog.service';
import { SecurityaccountService } from '../../securityaccount/service/securityaccount.service';
import { AppSettings } from '../../shared/app.settings';
import { TransactionCashaccountTableComponent } from '../../transaction/component/transaction-cashaccount-table.component';
import { TransactionSecurityEditComponent } from '../../transaction/component/transaction-security-edit.component';
import { TransactionSecurityMarginTreetableComponent } from '../../transaction/component/transaction-security-margin-treetable.component';
import { TransactionSecurityTableComponent } from '../../transaction/component/transaction-security-table.component';
import { TransactionCallParam } from '../../transaction/component/transaction.call.parm';
import { SecurityaccountTable } from './securityaccountTable';

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
export class SecurityaccountSummariesComponent extends SecurityaccountTable implements OnDestroy {
  readonly id = input.required({ transform: numberAttribute });

  private treeNavState: TreeNavigationStateService = inject(TreeNavigationStateService);

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

  private readonly summaryRequest = signal<
    ReturnType<SecurityaccountService['getSecurityPositionSummaryPortfolioRequest']> | undefined
  >(undefined);
  readonly positionResource = httpResource<SecurityPositionGrandSummary>(() => this.summaryRequest());
  constructor() {
    super();
    this.createColumns();

    effect(() => {
      const id = this.id();
      untracked(() => {
        this.idPortfolio = id;
        this.portfolio = this.treeNavState.getEntity<any>(
          BaseSettings.MAINVIEW_KEY + '/' + AppSettings.SECURITYACCOUNT_SUMMARIES_ROUTE_KEY,
          this.idPortfolio
        );
        this.readData();
      });
    });

    reportResourceErrors(this.positionResource, this.securityaccountService);
    effect(() => {
      const loading = this.positionResource.isLoading();
      const data = this.positionResource.hasValue() ? this.positionResource.value() : undefined;
      untracked(() => {
        this.dataLoading.set(loading);
        if (!loading && data) {
          this.getDataToView(data);
          this.initTableTextTranslation();
        }
      });
    });
  }

  override readData(): void {
    const idPortfolio = this.idPortfolio;
    if (idPortfolio == null) return;
    this.selectedSecurityPositionSummary = null;
    const request = this.securityaccountService.getSecurityPositionSummaryPortfolioRequest(
      idPortfolio,
      this.securityaccountGroupBase.defaultGroup,
      this.includeClosedPosition,
      this.untilDate
    );
    const current = this.summaryRequest();
    if (current?.url === request.url && current.params.toString() === request.params.toString())
      this.positionResource.reload();
    else this.summaryRequest.set(request);
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  protected extendTransactionParamData(transactionCallParam: TransactionCallParam): void {
    transactionCallParam.portfolio = this.portfolio;
  }

  protected getOptionalParameters(): OptionalParameters {
    return { idPortfolio: this.idPortfolio };
  }
}
