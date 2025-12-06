import {Component, OnInit} from '@angular/core';
import {PageFirstRowSelectedRow, ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {TransactionService} from '../../transaction/service/transaction.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {Transaction} from '../../entities/transaction';
import {Currencypair} from '../../entities/currencypair';
import {combineLatest, Observable} from 'rxjs';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {TransactionTable} from '../../transaction/component/transaction.table';
import {ConfirmationService, FilterService} from 'primeng/api';
import {CommonModule} from '@angular/common';
import {TableModule} from 'primeng/table';
import {DatePicker} from 'primeng/datepicker';
import {FormsModule} from '@angular/forms';
import {SelectModule} from 'primeng/select';
import {ContextMenuModule} from 'primeng/contextmenu';
import {TooltipModule} from 'primeng/tooltip';
import {TransactionCashaccountEditSingleComponent} from '../../transaction/component/transaction-cashaccount-editsingle.component';
import {TransactionCashaccountEditDoubleComponent} from '../../transaction/component/transaction-cashaccount-editdouble.component';
import {TransactionSecurityEditComponent} from '../../transaction/component/transaction-security-edit.component';
import {TransactionCashaccountConnectDebitCreditComponent} from '../../transaction/component/transaction-cashaccount-connect-debit-credit-component';

/**
 * Angular component that displays all financial transactions for a tenant in a comprehensive table view. This component
 * provides functionality for viewing, filtering, and managing transactions across all portfolios and accounts within a
 * tenant's scope. It supports sorting, pagination, and context menu operations for transaction management. The component
 * integrates with currency pair data to display accurate exchange rate information and transaction details in both
 * original and main currency formats.
 */
@Component({
    templateUrl: '../../transaction/view/transaction.cashaccount.table.html',
    standalone: true,
    imports: [
      CommonModule,
      TranslateModule,
      FormsModule,
      TableModule,
      SelectModule,
      DatePicker,
      ContextMenuModule,
      TooltipModule,
      TransactionCashaccountEditSingleComponent,
      TransactionCashaccountEditDoubleComponent,
      TransactionSecurityEditComponent,
      TransactionCashaccountConnectDebitCreditComponent
    ]
})
export class TenantTransactionTableComponent extends TransactionTable implements OnInit {

  /**
   * Creates a new tenant transaction table component with all necessary services for transaction management and display.
   *
   * @param currencypairService Service for managing currency pair operations and exchange rates
   * @param parentChildRegisterService Service for managing parent-child component relationships and state
   * @param activePanelService Service for managing active panel state and menu interactions
   * @param transactionService Service for transaction data operations and persistence
   * @param confirmationService PrimeNG service for displaying confirmation dialogs
   * @param messageToastService Service for displaying user notification messages
   * @param filterService PrimeNG service for table filtering functionality
   * @param translateService Angular service for internationalization and text translation
   * @param gps Global parameter service for application-wide settings and configuration
   * @param usersettingsService Service for managing user-specific settings and preferences
   */
  constructor(currencypairService: CurrencypairService,
              parentChildRegisterService: ParentChildRegisterService,
              activePanelService: ActivePanelService,
              transactionService: TransactionService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(currencypairService, parentChildRegisterService, activePanelService, transactionService, confirmationService,
      messageToastService, filterService, translateService, gps, usersettingsService);
  }

  /**
   * Initializes the component by configuring table sorting, preparing translations, and loading transaction data.
   * Sets up default sort order by transaction time in descending order.
   */
  ngOnInit(): void {
    this.multiSortMeta.push({field: 'transactionTime', order: -1});
    this.prepareTableAndTranslate();
    this.initialize();
  }

  /**
   * Loads and processes all transaction data for the tenant, combining transaction records with corresponding currency
   * pair information. Sets up table pagination and filtering capabilities after data is loaded.
   */
  protected initialize(): void {
    const transactionsObservable: Observable<Transaction[]> =
      this.transactionService.getTransactionByTenant();
    const currencypairObservable: Observable<Currencypair[]> = this.currencypairService
      .getCurrencypairInTransactionByTenant();
    this.pageFirstRowSelectedRow = new PageFirstRowSelectedRow(0, this.selectedTransaction);
    combineLatest([transactionsObservable, currencypairObservable]).subscribe((result: [Transaction[], Currencypair[]] ) => {
      this.cashaccountTransactionPositions = this.addCurrencypairToTransaction(result[0], result[1]);
      this.prepareFilter(this.cashaccountTransactionPositions);
      setTimeout(() => this.pageFirstRowSelectedRow = new PageFirstRowSelectedRow(this.firstRowIndexOnPage,
        this.selectedTransaction));

    });
  }

}
