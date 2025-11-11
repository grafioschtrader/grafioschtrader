import {TransactionTable} from '../../transaction/component/transaction.table';
import {Component, OnDestroy, OnInit} from '@angular/core';
import {PageFirstRowSelectedRow, ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {Currencypair} from '../../entities/currencypair';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {combineLatest, Observable, Subscription} from 'rxjs';
import {TransactionService} from '../../transaction/service/transaction.service';
import {Transaction} from '../../entities/transaction';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ActivatedRoute, Params} from '@angular/router';
import {ConfirmationService, FilterService} from 'primeng/api';

/**
 * Component that displays all transactions belonging to a specific portfolio in a tabular format.
 * Extends the base TransactionTable functionality to provide portfolio-specific transaction management.
 * The component monitors route parameters to determine which portfolio's transactions to display
 * and automatically loads the associated currency pairs for proper transaction display.
 * Supports full transaction lifecycle management including editing, deletion, and filtering capabilities.
 */
@Component({
  templateUrl: '../../transaction/view/transaction.cashaccount.table.html',
  standalone: false
})
export class PortfolioTransactionTableComponent extends TransactionTable implements OnInit, OnDestroy {

  /** Subscription for monitoring route parameter changes */
  private routeSubscribe: Subscription;

  /** Identifier of the currently selected portfolio */
  private idPortfolio: number;

  /**
   * Constructs the portfolio transaction table component with all required dependencies.
   *
   * @param activatedRoute Service for accessing route parameters and data
   * @param currencypairService Service for managing currency pair operations and data retrieval
   * @param parentChildRegisterService Service for coordinating parent-child component relationships
   * @param activePanelService Service for managing active panel states and menu interactions
   * @param transactionService Service for transaction data operations and business logic
   * @param confirmationService PrimNG service for displaying confirmation dialogs
   * @param messageToastService Service for displaying user notification messages
   * @param filterService PrimNG service for table filtering functionality
   * @param translateService Angular service for internationalization and text translation
   * @param gps Global parameter service for application-wide settings and configurations
   * @param usersettingsService Service for managing user-specific preferences and settings
   */

  constructor(private activatedRoute: ActivatedRoute,
    currencypairService: CurrencypairService,
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
   * Initializes the component by setting up table configuration and subscribing to route parameter changes.
   * Configures sorting behavior and establishes the route parameter subscription to monitor portfolio changes.
   */
  ngOnInit(): void {
    this.multiSortMeta.push({field: 'transactionTime', order: -1});
    this.prepareTableAndTranslate();
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
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
   * Loads and processes transaction data for the current portfolio.
   * Combines transaction data with currency pair information to provide complete transaction details.
   * Updates the table display and configures filtering options based on the loaded data.
   */
  protected override initialize(): void {
    const transactionsObservable: Observable<Transaction[]> =
      this.transactionService.getTransactionsByIdPortfolio(this.idPortfolio);
    const currencypairObservable: Observable<Currencypair[]> = this.currencypairService
      .getCurrencypairInTransactionByTenant();
    this.pageFirstRowSelectedRow = new PageFirstRowSelectedRow(0, this.selectedTransaction);
    combineLatest([transactionsObservable, currencypairObservable]).subscribe((result: [Transaction[], Currencypair[]]) => {
      this.cashaccountTransactionPositions = this.addCurrencypairToTransaction(result[0], result[1]);
      this.prepareFilter(this.cashaccountTransactionPositions);
      setTimeout(() => this.pageFirstRowSelectedRow = new PageFirstRowSelectedRow(this.firstRowIndexOnPage,
        this.selectedTransaction));
    });
  }
}
