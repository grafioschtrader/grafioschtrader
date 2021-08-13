import {TransactionTable} from '../../transaction/component/transaction.table';
import {Component, OnDestroy, OnInit} from '@angular/core';
import {PageFirstRowSelectedRow, ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {Currencypair} from '../../entities/currencypair';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {combineLatest, Observable, Subscription} from 'rxjs';
import {TransactionService} from '../../transaction/service/transaction.service';
import {Transaction} from '../../entities/transaction';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ActivatedRoute, Params} from '@angular/router';
import {ConfirmationService, FilterService} from 'primeng/api';

/**
 * Shows all transactions of a Portfolio
 */
@Component({
  templateUrl: '../../transaction/view/transaction.cashaccount.table.html'
})
export class PortfolioTransactionTableComponent extends TransactionTable implements OnInit, OnDestroy {
  private routeSubscribe: Subscription;
  private idPortfolio: number;

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

  ngOnInit(): void {
    this.multiSortMeta.push({field: 'transactionTime', order: -1});
    this.prepareTableAndTranslate();
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      this.idPortfolio = +params['id'];
      this.initialize();
    });
  }

  ngOnDestroy(): void {
    super.destroy();
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
  }

  protected initialize(): void {
    const transactionsObserable: Observable<Transaction[]> =
      this.transactionService.getTransactionsByIdPortfolio(this.idPortfolio);
    const currencypairObservable: Observable<Currencypair[]> = this.currencypairService
      .getCurrencypairInTransactionByTenant();
    this.pageFirstRowSelectedRow = new PageFirstRowSelectedRow(0, this.selectedTransaction);
    combineLatest([transactionsObserable, currencypairObservable]).subscribe(result => {
      this.cashaccountTransactionPositions = this.addCurrencypairToTransaction(result[0], result[1]);
      this.prepareFilter(this.cashaccountTransactionPositions);
      setTimeout(() => this.pageFirstRowSelectedRow = new PageFirstRowSelectedRow(this.firstRowIndexOnPage,
        this.selectedTransaction));
    });
  }
}
