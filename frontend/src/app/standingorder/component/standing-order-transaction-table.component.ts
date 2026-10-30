import { Component, computed, effect, inject, input, linkedSignal, OnDestroy, untracked } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { map, switchMap } from 'rxjs';
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';

import { TranslateModule } from '@ngx-translate/core';
import { Security } from '../../entities/security';
import { StandingOrder } from '../../entities/standing.order';
import { Transaction } from '../../entities/transaction';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { AppSettings } from '../../shared/app.settings';
import { TransactionCashaccountConnectDebitCreditDialogComponent } from '../../transaction/component/transaction-cashaccount-connect-debit-credit-dialog.component';
import { TransactionCashaccountEditDoubleComponent } from '../../transaction/component/transaction-cashaccount-editdouble.component';
import { TransactionCashaccountEditSingleComponent } from '../../transaction/component/transaction-cashaccount-editsingle.component';
import { TransactionSecurityEditComponent } from '../../transaction/component/transaction-security-edit.component';
import { TransactionCallParam } from '../../transaction/component/transaction.call.parm';
import { TransactionContextMenu } from '../../transaction/component/transaction.context.menu';
import { StandingOrderService } from '../service/standing.order.service';
import { StandingOrderCashaccountEditComponent } from './standing-order-cashaccount-edit.component';
import { StandingOrderSecurityEditComponent } from './standing-order-security-edit.component';

/**
 * Displays transactions created by a standing order in an editable table within the row expansion.
 * Extends TransactionContextMenu to provide full transaction editing capabilities (edit, delete,
 * connect debit/credit, create standing order) via context menu.
 */
@Component({
  selector: 'standing-order-transaction-table',
  template: `
    <h5>{{ 'SUCCESSFUL_TRANSACTIONS' | translate }}</h5>
    <configurable-table
      [data]="transactions()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="'idTransaction'"
      [(selection)]="selectedTransaction"
      [customSortFn]="customSorter"
      [multiSortMeta]="multiSortMeta"
      (rowSelect)="onRowSelect($event)"
      (rowUnselect)="onRowUnselect($event)"
      (componentClick)="onComponentClick($event)"
      [contextMenuItems]="contextMenuItems()"
      [showContextMenu]="isActivated()"
      [containerClass]="{
        'data-container': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [customClass]="'datatable nestedtable'"
      [scrollable]="false"
      [valueGetterFn]="valueGetter"
      [negativeValueFn]="negativeValue"
      [baseLocale]="baseLocale"
      [contextMenuAppendTo]="'body'" />

    @if (visibleSecurityTransactionDialog()) {
      <transaction-security-edit
        (closeDialog)="handleCloseTransactionDialog($event)"
        [transactionCallParam]="transactionCallParam"
        [visibleSecurityTransactionDialog]="visibleSecurityTransactionDialog()" />
    }

    @if (visibleCashaccountTransactionSingleDialog()) {
      <transaction-cashaccount-editsingle
        (closeDialog)="handleCloseTransactionDialog($event)"
        [transactionCallParam]="transactionCallParam"
        [visibleCashaccountTransactionSingleDialog]="visibleCashaccountTransactionSingleDialog()" />
    }

    @if (visibleCashaccountTransactionDoubleDialog()) {
      <transaction-cashaccount-editdouble
        (closeDialog)="handleCloseTransactionDialog($event)"
        [transactionCallParam]="transactionCallParam"
        [visibleCashaccountTransactionDoubleDialog]="visibleCashaccountTransactionDoubleDialog()" />
    }

    @if (visibleConnectDebitCreditDialog()) {
      <transaction-cashaccount-connect-debit-credit
        (closeDialog)="handleCloseTransactionDialog($event)"
        [transactionCallParam]="transactionCallParam"
        [visibleDialog]="visibleConnectDebitCreditDialog()" />
    }

    @if (visibleStandingOrderCashaccountDialog()) {
      <standing-order-cashaccount-edit
        [visibleDialog]="visibleStandingOrderCashaccountDialog()"
        [callParam]="standingOrderCallParam"
        (closeDialog)="handleCloseStandingOrderDialog($event)" />
    }

    @if (visibleStandingOrderSecurityDialog()) {
      <standing-order-security-edit
        [visibleDialog]="visibleStandingOrderSecurityDialog()"
        [callParam]="standingOrderCallParam"
        (closeDialog)="handleCloseStandingOrderDialog($event)" />
    }
  `,
  imports: [
    TranslateModule,
    ConfigurableTableComponent,
    TransactionCashaccountEditSingleComponent,
    TransactionCashaccountEditDoubleComponent,
    TransactionSecurityEditComponent,
    TransactionCashaccountConnectDebitCreditDialogComponent,
    StandingOrderCashaccountEditComponent,
    StandingOrderSecurityEditComponent
  ]
})
export class StandingOrderTransactionTableComponent extends TransactionContextMenu implements OnDestroy {
  private portfolioService: PortfolioService = inject(PortfolioService);
  private standingOrderService: StandingOrderService = inject(StandingOrderService);

  readonly standingOrder = input<StandingOrder>();

  readonly transactionResource = rxResource({
    params: () => {
      const order = this.standingOrder();
      const idStandingOrder = order?.idStandingOrder;
      const idCashaccount = order?.cashaccount?.idSecuritycashAccount;
      return idStandingOrder == null || idCashaccount == null ? undefined : { idStandingOrder, idCashaccount };
    },
    stream: ({ params }) =>
      this.portfolioService
        .getPortfolioByIdSecuritycashaccount(params.idCashaccount)
        .pipe(
          switchMap((portfolio) =>
            this.standingOrderService
              .getTransactions(params.idStandingOrder)
              .pipe(map((transactions) => ({ portfolio, transactions })))
          )
        )
  });
  readonly transactions = computed(() =>
    this.transactionResource.hasValue() ? this.transactionResource.value().transactions : []
  );

  override readonly selectedTransaction = linkedSignal<Transaction[], Transaction | null>({
    source: this.transactions,
    computation: (rows, previous) => rows.find((row) => row.idTransaction === previous?.value?.idTransaction) ?? null
  });
  constructor() {
    super();
    this.addColumn(DataType.DateString, 'transactionTime', 'DATE', true, false);
    this.addColumnFeqH(DataType.String, 'transactionType', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.String, 'security.name', AppSettings.SECURITY.toUpperCase(), true, false);
    this.addColumn(DataType.Numeric, 'units', 'QUANTITY', true, false);
    this.addColumn(DataType.Numeric, 'quotation', 'QUOTATION_DIV', true, false);
    this.addColumnFeqH(DataType.Numeric, 'taxCost', true, false);
    this.addColumnFeqH(DataType.Numeric, 'transactionCost', true, false);
    this.addColumnFeqH(DataType.Numeric, 'cashaccountAmount', true, false, {
      templateName: 'greenRed'
    });

    this.prepareTableAndTranslate();
    this.multiSortMeta = [{ field: 'transactionTime', order: -1 }];
    reportResourceErrors(this.transactionResource, this.standingOrderService);
    effect(() => {
      const rows = this.transactions();
      untracked(() => this.createTranslatedValueStoreAndFilterField(rows));
    });
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  onRowSelect(event: any): void {
    this.selectedTransaction.set(event.data);
    this.setMenuItemsToActivePanel();
  }

  override handleCloseTransactionDialog(processedActionData: ProcessedActionData): void {
    super.handleCloseTransactionDialog(processedActionData);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.loadTransactions();
    }
  }

  override afterDelete(transaction: Transaction): void {
    super.afterDelete(transaction);
    this.loadTransactions();
  }

  protected getSecurity(transaction: Transaction): Security | undefined {
    return transaction.security;
  }

  protected initialize(): void {
    this.transactionResource.reload();
  }

  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam): void {
    transactionCallParam.portfolio = this.transactionResource.hasValue()
      ? this.transactionResource.value().portfolio
      : undefined;
  }

  private loadTransactions(): void {
    this.initialize();
  }
}
