import { Component, effect, input, numberAttribute, OnDestroy, OnInit, untracked } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { combineLatest } from 'rxjs';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';
import { PageFirstRowSelectedRow } from '../../shared/service/parent.child.register.service';
import { StandingOrderCashaccountEditComponent } from '../../standingorder/component/standing-order-cashaccount-edit.component';
import { StandingOrderSecurityEditComponent } from '../../standingorder/component/standing-order-security-edit.component';
import { TransactionCashaccountConnectDebitCreditDialogComponent } from '../../transaction/component/transaction-cashaccount-connect-debit-credit-dialog.component';
import { TransactionCashaccountEditDoubleComponent } from '../../transaction/component/transaction-cashaccount-editdouble.component';
import { TransactionCashaccountEditSingleComponent } from '../../transaction/component/transaction-cashaccount-editsingle.component';
import { TransactionSecurityEditComponent } from '../../transaction/component/transaction-security-edit.component';
import { TransactionTable } from '../../transaction/component/transaction.table';

/**
 * Component that displays all transactions belonging to a specific portfolio in a tabular format.
 * Extends the base TransactionTable functionality to provide portfolio-specific transaction management.
 * The component monitors route parameters to determine which portfolio's transactions to display
 * and automatically loads the associated currency pairs for proper transaction display.
 * Supports full transaction lifecycle management including editing, deletion, and filtering capabilities.
 */
@Component({
  templateUrl: '../../transaction/view/transaction.cashaccount.table.html',
  imports: [
    ConfigurableTableComponent,
    TransactionCashaccountEditSingleComponent,
    TransactionCashaccountEditDoubleComponent,
    TransactionSecurityEditComponent,
    TransactionCashaccountConnectDebitCreditDialogComponent,
    StandingOrderCashaccountEditComponent,
    StandingOrderSecurityEditComponent
  ]
})
export class PortfolioTransactionTableComponent extends TransactionTable implements OnInit, OnDestroy {
  readonly id = input.required({ transform: numberAttribute });

  /** The current portfolio's transactions and the currency pairs needed to display them, cancelled together. */
  readonly transactionResource = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) =>
      combineLatest([
        this.transactionService.getTransactionsByIdPortfolio(id),
        this.currencypairService.getCurrencypairInTransactionByTenant()
      ])
  });
  constructor() {
    super();

    reportResourceErrors(this.transactionResource, this.transactionService);
    effect(() => {
      if (this.transactionResource.isLoading()) return;
      const result = this.transactionResource.hasValue() ? this.transactionResource.value() : undefined;
      untracked(() => {
        this.cashaccountTransactionPositions.set(result ? this.addCurrencypairToTransaction(result[0], result[1]) : []);
        this.prepareFilter(this.cashaccountTransactionPositions());
        this.pageFirstRowSelectedRow.set(
          new PageFirstRowSelectedRow(this.firstRowIndexOnPage, this.selectedTransaction())
        );
      });
    });
  }

  /**
   * Configures table sorting and translations before the route input effect loads transactions.
   */
  ngOnInit(): void {
    this.multiSortMeta.push({ field: 'transactionTime', order: -1 });
    this.prepareTableAndTranslate();
  }

  /**
   * Cleans up component resources by unsubscribing from observables and destroying parent resources.
   * Ensures proper memory management and prevents potential memory leaks.
   */
  ngOnDestroy(): void {
    super.destroy();
  }

  /**
   * Restricts the CSV export action of the base class to the securities accounts of this portfolio.
   *
   * @returns The ID of the portfolio shown in this table
   */
  protected override getExportIdPortfolio(): number {
    return this.id();
  }

  /**
   * Loads and processes transaction data for the current portfolio.
   * Combines transaction data with currency pair information to provide complete transaction details.
   * Updates the table display and configures filtering options based on the loaded data.
   */
  protected override initialize(): void {
    this.transactionResource.reload();
  }
}
