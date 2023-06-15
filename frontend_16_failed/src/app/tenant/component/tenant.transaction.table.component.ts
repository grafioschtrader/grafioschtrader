import {Component, OnInit} from '@angular/core';
import {PageFirstRowSelectedRow, ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {TranslateService} from '@ngx-translate/core';
import {TransactionService} from '../../transaction/service/transaction.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {Transaction} from '../../entities/transaction';
import {Currencypair} from '../../entities/currencypair';
import {combineLatest, Observable} from 'rxjs';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {TransactionTable} from '../../transaction/component/transaction.table';
import {ConfirmationService, FilterService} from 'primeng/api';

/**
 * Shows all transactions of a tenant
 */
@Component({
  templateUrl: '../../transaction/view/transaction.cashaccount.table.html'
})
export class TenantTransactionTableComponent extends TransactionTable implements OnInit {

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

  ngOnInit(): void {
    this.multiSortMeta.push({field: 'transactionTime', order: -1});
    this.prepareTableAndTranslate();
    this.initialize();
  }

  protected initialize(): void {

    const transactionsObserable: Observable<Transaction[]> =
      this.transactionService.getTransactionByTenant();
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
