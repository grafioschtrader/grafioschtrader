import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, OnInit } from '@angular/core';
import { PageFirstRowSelectedRow } from '../../shared/service/parent.child.register.service';
import { Transaction } from '../../entities/transaction';
import { Currencypair } from '../../entities/currencypair';
import { combineLatest, Observable } from 'rxjs';
import { TransactionTable } from '../../transaction/component/transaction.table';
import { MenuItem } from '@openng/optimus-ui/api';
import { AppHelper } from '../../lib/helper/app.helper';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { CashTransferRelinkResult } from '../../transaction/service/transaction.service';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TransactionCashaccountEditSingleComponent } from '../../transaction/component/transaction-cashaccount-editsingle.component';
import { TransactionCashaccountEditDoubleComponent } from '../../transaction/component/transaction-cashaccount-editdouble.component';
import { TransactionSecurityEditComponent } from '../../transaction/component/transaction-security-edit.component';
import { TransactionCashaccountConnectDebitCreditDialogComponent } from '../../transaction/component/transaction-cashaccount-connect-debit-credit-dialog.component';
import { StandingOrderCashaccountEditComponent } from '../../standingorder/component/standing-order-cashaccount-edit.component';
import { StandingOrderSecurityEditComponent } from '../../standingorder/component/standing-order-security-edit.component';

/**
 * Angular component that displays all financial transactions for a tenant in a comprehensive table view. This component
 * provides functionality for viewing, filtering, and managing transactions across all portfolios and accounts within a
 * tenant's scope. It supports sorting, pagination, and context menu operations for transaction management. The component
 * integrates with currency pair data to display accurate exchange rate information and transaction details in both
 * original and main currency formats.
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
export class TenantTransactionTableComponent extends TransactionTable implements OnInit {
  /**
   * Creates a new tenant transaction table component with all necessary services for transaction management and display.
   *
   */
  constructor() {
    super();
  }

  /**
   * Initializes the component by configuring table sorting, preparing translations, and loading transaction data.
   * Sets up default sort order by transaction time in descending order.
   */
  ngOnInit(): void {
    this.multiSortMeta.push({ field: 'transactionTime', order: -1 });
    this.prepareTableAndTranslate();
    this.initialize();
  }

  /**
   * Appends the tenant-wide maintenance action that reconnects the unlinked sides of cash account transfers, e.g.
   * after re-importing the CSV export of another GT instance. Only unambiguous one-to-one matches are linked; the
   * result counts are shown in a toast and the table is reloaded when something was connected.
   *
   * @param transaction The currently selected transaction, may be null
   * @returns The menu items of the base class plus the connect transfers action
   */
  protected override getMenuItemsOnTransaction(transaction: Transaction): MenuItem[] {
    const menuItems = super.getMenuItemsOnTransaction(transaction);
    const connectMenuItem: MenuItem = {
      label: 'CONNECT_CASH_TRANSFERS',
      command: () => this.handleConnectCashTransfers()
    };
    menuItems.push(connectMenuItem);
    return menuItems;
  }

  /**
   * Asks for confirmation, runs the transfer relink on the backend and reports the result counts. Pairs and single
   * transactions are counted separately, so the transactions without any counterpart are derived from the four counts
   * and shown as well. A rejected pair turns the message into a warning.
   */
  private handleConnectCashTransfers(): void {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService, 'MSG_CONNECT_CASH_TRANSFERS', () =>
      this.transactionService
        .connectCashTransfers()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((result: CashTransferRelinkResult) => {
          const unmatched = result.checked - 2 * result.linkedPairs - result.ambiguous - 2 * result.failed;
          this.messageToastService.showMessageI18n(
            result.failed > 0 ? InfoLevelType.WARNING : InfoLevelType.SUCCESS,
            'CONNECT_CASH_TRANSFERS_RESULT',
            {
              checked: result.checked,
              linkedPairs: result.linkedPairs,
              ambiguous: result.ambiguous,
              failed: result.failed,
              unmatched
            }
          );
          if (result.linkedPairs > 0) {
            this.initialize();
          }
        })
    );
  }

  /**
   * Loads and processes all transaction data for the tenant, combining transaction records with corresponding currency
   * pair information. Sets up table pagination and filtering capabilities after data is loaded.
   */
  protected initialize(): void {
    const transactionsObservable: Observable<Transaction[]> = this.transactionService.getTransactionByTenant();
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
