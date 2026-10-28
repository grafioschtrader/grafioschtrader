import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TransactionTable } from '../../transaction/component/transaction.table';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { PageFirstRowSelectedRow } from '../../shared/service/parent.child.register.service';
import { Currencypair } from '../../entities/currencypair';
import { combineLatest, Observable, Subscription } from 'rxjs';
import { Transaction } from '../../entities/transaction';
import { ActivatedRoute, Params } from '@angular/router';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TransactionCashaccountEditSingleComponent } from '../../transaction/component/transaction-cashaccount-editsingle.component';
import { TransactionCashaccountEditDoubleComponent } from '../../transaction/component/transaction-cashaccount-editdouble.component';
import { TransactionSecurityEditComponent } from '../../transaction/component/transaction-security-edit.component';
import { TransactionCashaccountConnectDebitCreditDialogComponent } from '../../transaction/component/transaction-cashaccount-connect-debit-credit-dialog.component';
import { StandingOrderCashaccountEditComponent } from '../../standingorder/component/standing-order-cashaccount-edit.component';
import { StandingOrderSecurityEditComponent } from '../../standingorder/component/standing-order-security-edit.component';

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
  private activatedRoute: ActivatedRoute = inject(ActivatedRoute);

  /** Subscription for monitoring route parameter changes */
  private routeSubscribe: Subscription;

  /** Identifier of the currently selected portfolio */
  private idPortfolio: number;

  /**
   * Constructs the portfolio transaction table component with all required dependencies.
   *
   */

  constructor() {
    super();
  }

  /**
   * Initializes the component by setting up table configuration and subscribing to route parameter changes.
   * Configures sorting behavior and establishes the route parameter subscription to monitor portfolio changes.
   */
  ngOnInit(): void {
    this.multiSortMeta.push({ field: 'transactionTime', order: -1 });
    this.prepareTableAndTranslate();
    this.routeSubscribe = this.activatedRoute.params
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params: Params) => {
        this.idPortfolio = +params['id'];
        this.initialize();
      });
  }

  /**
   * Cleans up component resources by unsubscribing from observables and destroying parent resources.
   * Ensures proper memory management and prevents potential memory leaks.
   */
  ngOnDestroy(): void {
    super.destroy();
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
  }

  /**
   * Restricts the CSV export action of the base class to the securities accounts of this portfolio.
   *
   * @returns The ID of the portfolio shown in this table
   */
  protected override getExportIdPortfolio(): number {
    return this.idPortfolio;
  }

  /**
   * Loads and processes transaction data for the current portfolio.
   * Combines transaction data with currency pair information to provide complete transaction details.
   * Updates the table display and configures filtering options based on the loaded data.
   */
  protected override initialize(): void {
    const transactionsObservable: Observable<Transaction[]> = this.transactionService.getTransactionsByIdPortfolio(
      this.idPortfolio
    );
    const currencypairObservable: Observable<Currencypair[]> =
      this.currencypairService.getCurrencypairInTransactionByTenant();
    this.pageFirstRowSelectedRow.set(new PageFirstRowSelectedRow(0, this.selectedTransaction()));
    this.dataSubscription?.unsubscribe();
    this.dataSubscription = combineLatest([transactionsObservable, currencypairObservable])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result: [Transaction[], Currencypair[]]) => {
        this.cashaccountTransactionPositions.set(this.addCurrencypairToTransaction(result[0], result[1]));
        this.prepareFilter(this.cashaccountTransactionPositions());
        this.pageFirstRowSelectedRow.set(
          new PageFirstRowSelectedRow(this.firstRowIndexOnPage, this.selectedTransaction())
        );
      });
  }
}
