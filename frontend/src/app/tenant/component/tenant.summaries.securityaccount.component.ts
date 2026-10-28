import { Securitycurrency } from '../../entities/securitycurrency';
import { DestroyRef, inject } from '@angular/core';
import { PerformanceReportDialogService } from '../../performanceperiod/service/performance-report-dialog.service';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { SecurityaccountTable } from '../../securityaccount/component/securityaccountTable';
import { SecurityPositionGrandSummary } from '../../entities/view/security.position.grand.summary';

import { TransactionCallParam } from '../../transaction/component/transaction.call.parm';
import { TranslateModule } from '@ngx-translate/core';
import { AppSettings } from '../../shared/app.settings';
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
 * It is the summary for all security accounts with its securities of a for a certain tenant.
 * The user can change the grouping for example by currency or financial instrument.
 */
@Component({
  templateUrl: '../../securityaccount/view/securityaccount.table.html',
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
export class TenantSummariesSecurityaccountComponent extends SecurityaccountTable implements OnInit, OnDestroy {
  private statementReports = inject(PerformanceReportDialogService);
  private reportDestroyRef = inject(DestroyRef);

  override getMenuShowOptionsParam(selected: Securitycurrency) {
    return [
      ...(super.getMenuShowOptionsParam(selected) || []),
      this.statementReports.menu(
        () => this.untilDate,
        () => null,
        this.reportDestroyRef
      )
    ];
  }

  constructor() {
    super();
  }

  ngOnInit() {
    this.idTenant = this.gps.getIdTenant();
    this.readData();
  }

  readData() {
    this.selectedSecurityPositionSummary = null;
    this.securityaccountService
      .getSecurityPositionSummaryTenant(
        this.securityaccountGroupBase.defaultGroup,
        this.includeClosedPosition,
        this.untilDate
      )
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

  protected extendTransactionParamData(transactionCallParam: TransactionCallParam): void {}

  protected getOptionalParameters(): OptionalParameters {
    return null;
  }
}
