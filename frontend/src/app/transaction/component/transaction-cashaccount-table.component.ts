import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {TransactionService} from '../service/transaction.service';
import {Transaction} from '../../entities/transaction';
import {Currencypair} from '../../entities/currencypair';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {CashaccountTransactionPosition} from '../../entities/view/cashaccount.transaction.position';
import {TransactionContextMenu} from './transaction.context.menu';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Security} from '../../entities/security';
import {Portfolio} from '../../entities/portfolio';
import {TransactionCallParam} from './transaction.call.parm';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {combineLatest, Observable} from 'rxjs';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';

import {
  ChildPreservePage,
  PageFirstRowSelectedRow,
  ParentChildRegisterService
} from '../../shared/service/parent.child.register.service';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {ConfirmationService, FilterService} from 'primeng/api';
import {TranslateValue} from '../../shared/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';

/**
 * It shows the transactions for a cash account.
 * The contextmenu has the table component as target.
 */
@Component({
  selector: 'transaction-cashaccount-table',
  templateUrl: '../view/transaction.cashaccount.table.html'
})
export class TransactionCashaccountTableComponent extends TransactionContextMenu
  implements ChildPreservePage, OnInit, OnDestroy {

  @Input() idSecuritycashAccount: number;
  @Input() portfolio: Portfolio;

  cashaccountTransactionPositions: CashaccountTransactionPosition[];
  cashaccountTransactionPositionSelected: CashaccountTransactionPosition;

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

  getSecurity(transaction: Transaction): Security {
    return transaction.security;
  }

  onRowSelect(event): void {
    this.cashaccountTransactionPositionSelected = event.data;
    this.selectedTransaction = this.cashaccountTransactionPositionSelected.transaction;
  }

  ngOnDestroy(): void {
    this.parentChildRegisterService.unregisterChildComponent(this);
    super.destroy();
  }

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

  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {
    transactionCallParam.portfolio = this.portfolio;
  }

  private loadData() {
    const transactionsObserable: Observable<CashaccountTransactionPosition[]> =
      this.transactionService.getTransactionsWithSaldoForCashaccount(this.idSecuritycashAccount);
    const currencypairObservable: Observable<Currencypair[]> = this.currencypairService
      .getCurrencypairByPortfolioId(this.portfolio.idPortfolio);

    combineLatest([transactionsObserable, currencypairObservable]).subscribe(result => {
      this.cashaccountTransactionPositions = this.addCurrencypairToCashaccountTransactionPosition(result[0], result[1]);
      this.goToFirsRowPosition(this.idSecuritycashAccount);
    });
  }

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
