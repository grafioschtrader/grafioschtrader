import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {TransactionService} from '../service/transaction.service';
import {Transaction} from '../../entities/transaction';
import {Currencypair} from '../../entities/currencypair';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {CashaccountTransactionPosition} from '../../entities/view/cashaccount.transaction.position';
import {TransactionContextMenu} from './transaction.context.menu';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {Security} from '../../entities/security';
import {Portfolio} from '../../entities/portfolio';
import {TransactionCallParam} from './transaction.call.parm';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {combineLatest, Observable} from 'rxjs';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {
  ChildPreservePage,
  PageFirstRowSelectedRow,
  ParentChildRegisterService
} from '../../shared/service/parent.child.register.service';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {ConfirmationService, FilterService} from 'primeng/api';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';
import {TransactionType} from '../../shared/types/transaction.type';

/**
 * Angular component that displays transactions for a cash account in a table format with context menu functionality.
 * Supports filtering, pagination, and transaction management operations. Provides capabilities for editing, deleting,
 * and viewing transaction details. Integrates with currency pair data to display exchange rate information and
 * maintains page state for user navigation consistency.
 */
@Component({
  selector: 'transaction-cashaccount-table',
  templateUrl: '../view/transaction.cashaccount.table.html',
  standalone: false
})
export class TransactionCashaccountTableComponent extends TransactionContextMenu
  implements ChildPreservePage, OnInit, OnDestroy {
  /** The unique identifier of the security cash account for which transactions are displayed */
  @Input() idSecuritycashAccount: number;

  /** The portfolio containing the cash account */
  @Input() portfolio: Portfolio;

  /** Filter criteria for limiting the displayed transactions */
  @Input() cashAccountTableInputFilter: CashAccountTableInputFilter;

  /** Array of cash account transaction positions to be displayed in the table */
  cashaccountTransactionPositions: CashaccountTransactionPosition[];

  /** Currently selected cash account transaction position */
  cashaccountTransactionPositionSelected: CashaccountTransactionPosition;

  /**
   * Creates an instance of TransactionCashaccountTableComponent.
   * @param currencypairService Service for handling currency pair operations
   * @param portfolioService Service for portfolio-related operations
   * @param parentChildRegisterService Service for managing parent-child component relationships
   * @param activePanelService Service for managing active panel state
   * @param transactionService Service for transaction operations
   * @param confirmationService PrimeNG service for confirmation dialogs
   * @param messageToastService Service for displaying toast messages
   * @param filterService PrimeNG service for table filtering
   * @param translateService Angular service for internationalization
   * @param gps Global parameter service for application settings
   * @param usersettingsService Service for user-specific settings
   */
  constructor(private currencypairService: CurrencypairService,
    private portfolioService: PortfolioService,
    parentChildRegisterService: ParentChildRegisterService,
    activePanelService: ActivePanelService,
    transactionService: TransactionService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(parentChildRegisterService, activePanelService, transactionService, confirmationService, messageToastService,
      filterService, translateService, gps, usersettingsService);
  }

  /** Initializes the component by setting up table columns, sorting, and registering with parent-child service */
  ngOnInit(): void {
    this.addColumn(DataType.DateString, 'transaction.transactionTime', 'DATE', true, false,);
    this.addColumnFeqH(DataType.String, 'transaction.transactionType', true, false,
      {width: 100, translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'transaction.security.name', AppSettings.SECURITY.toUpperCase(), true, false, {width: 150});
    this.addColumn(DataType.Numeric, 'transaction.units', 'QUANTITY', true, false);
    this.addColumn(DataType.Numeric, 'transaction.quotation', 'QUOTATION_DIV', true, false);
    this.addColumn(DataType.String, 'transaction.currencypair.fromCurrency', 'CURRENCY', true, false);
    this.addColumn(DataType.String, 'transaction.currencyExRate', 'EXCHANGE_RATE', true, false);
    this.addColumn(DataType.Numeric, 'transaction.taxCost', 'TAX_COST', true, false);
    this.addColumnFeqH(DataType.Numeric, 'transaction.transactionCost', true, false);
    this.addColumnFeqH(DataType.Numeric, 'transaction.cashaccountAmount', true, false,
      {templateName: 'greenRed'});
    this.addColumnFeqH(DataType.Numeric, 'balance', true, false, {templateName: 'greenRed'});
    this.prepareTableAndTranslate();
    // this.pageFirstRowSelectedRow = this.parentChildRegisterService.getRowPostion(this.idSecuritycashAccount);
    this.multiSortMeta.push({field: 'transaction.transactionTime', order: -1});
    this.initialize();
    this.parentChildRegisterService.registerChildComponent(this);
  }

  /**
   * Preserves the current page state and selected row position for navigation consistency.
   * @param data Transaction data that may affect the current selection
   */
  preservePage(data: any) {
    if (data && this.cashaccountTransactionPositionSelected) {
      const transactions: Transaction[] = Array.isArray(data) ? data : [data];
      if (!transactions.find(transaction => transaction.idTransaction === this.cashaccountTransactionPositionSelected.idTransaction)) {
        this.cashaccountTransactionPositionSelected = this.cashaccountTransactionPositions.find(ctps =>
          ctps.idTransaction === transactions[0].idTransaction);
        if (!this.cashaccountTransactionPositionSelected && transactions.length > 1) {
          this.cashaccountTransactionPositionSelected = this.cashaccountTransactionPositions.find(ctps =>
            ctps.idTransaction === transactions[1].idTransaction);
        }
      }
    }
    this.parentChildRegisterService.saveRowPosition(this.idSecuritycashAccount,
      new PageFirstRowSelectedRow(this.firstRowIndexOnPage,
        (data) ? this.cashaccountTransactionPositionSelected : null));
  }

  /**
   * Retrieves the security associated with a transaction.
   * @param transaction The transaction for which to get the security
   * @returns The security associated with the transaction
   */
  getSecurity(transaction: Transaction): Security {
    return transaction.security;
  }

  /**
   * Handles row selection events in the table.
   * @param event The row selection event containing the selected data
   */
  onRowSelect(event): void {
    this.cashaccountTransactionPositionSelected = event.data;
    this.selectedTransaction = this.cashaccountTransactionPositionSelected.transaction;
  }

  /** Cleans up component resources and unregisters from parent-child service */
  ngOnDestroy(): void {
    this.parentChildRegisterService.unregisterChildComponent(this);
    super.destroy();
  }

  /** Initializes component data by loading portfolio information if needed and fetching transaction data */
  protected initialize(): void {
    if (this.portfolio) {
      this.loadData();
    } else {
      this.portfolioService.getPortfolioByIdSecuritycashaccount(this.idSecuritycashAccount).subscribe(portfolio => {
        this.portfolio = portfolio;
        this.loadData();
      });
    }
  }

  /**
   * Prepares transaction call parameters with portfolio information.
   * @param transactionCallParam The transaction call parameter object to be configured
   */
  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {
    transactionCallParam.portfolio = this.portfolio;
  }

  /** Loads transaction and currency pair data simultaneously and processes the results */
  private loadData() {
    const transactionsObservable: Observable<CashaccountTransactionPosition[]> =
      this.transactionService.getTransactionsWithBalanceForCashaccount(this.idSecuritycashAccount,
        this.cashAccountTableInputFilter ?? new CashAccountTableInputFilter());
    const currencypairObservable: Observable<Currencypair[]> = this.currencypairService
      .getTransactionCurrencypairsByPortfolioId(this.portfolio.idPortfolio);
    combineLatest([transactionsObservable, currencypairObservable]).subscribe(
      (result: [CashaccountTransactionPosition[], Currencypair[]]) => {
        this.cashaccountTransactionPositions = this.addCurrencypairToCashaccountTransactionPosition(result[0], result[1]);
        this.goToFirsRowPosition(this.idSecuritycashAccount);
      });
  }

  /**
   * Associates currency pairs with transaction positions and prepares data for display.
   * @param cashaccountTransactionPositions Array of cash account transaction positions
   * @param currencypairs Array of available currency pairs
   * @returns Enhanced transaction positions with currency pair information
   */
  private addCurrencypairToCashaccountTransactionPosition(cashaccountTransactionPositions: CashaccountTransactionPosition[],
    currencypairs: Currencypair[]): CashaccountTransactionPosition[] {
    const currencypairMap: Map<number, Currencypair> = new Map();
    currencypairs.forEach(currencypair => currencypairMap.set(currencypair.idSecuritycurrency, currencypair));
    for (const cashaccountTransactionPosition of cashaccountTransactionPositions) {
      cashaccountTransactionPosition['idTransaction'] = cashaccountTransactionPosition.transaction.idTransaction;
      if (cashaccountTransactionPosition.transaction.idCurrencypair != null) {
        cashaccountTransactionPosition.transaction.currencypair = currencypairMap.get(cashaccountTransactionPosition
          .transaction.idCurrencypair);
      }
    }
    this.createTranslatedValueStoreAndFilterField(cashaccountTransactionPositions);
    return cashaccountTransactionPositions;
  }
}

/**
 * Filter criteria class for limiting cash account table transactions by type and year.
 */
export class CashAccountTableInputFilter {
  /**
   * Creates an instance of CashAccountTableInputFilter.
   * @param transactionTypes Array of transaction types to include in the filter
   * @param year Specific year to filter transactions, 0 for all years
   */
  constructor(public transactionTypes: TransactionType[] = [], public year: number = 0) {
  }
}
