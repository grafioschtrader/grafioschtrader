import {Component, Injector, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ConfirmationService, FilterService} from 'primeng/api';
import {TransactionContextMenu} from '../../transaction/component/transaction.context.menu';
import {TransactionService} from '../../transaction/service/transaction.service';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {Transaction} from '../../entities/transaction';
import {Security} from '../../entities/security';
import {Portfolio} from '../../entities/portfolio';
import {StandingOrder} from '../../entities/standing.order';
import {StandingOrderService} from '../service/standing.order.service';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {TransactionCashaccountEditSingleComponent} from '../../transaction/component/transaction-cashaccount-editsingle.component';
import {TransactionCashaccountEditDoubleComponent} from '../../transaction/component/transaction-cashaccount-editdouble.component';
import {TransactionSecurityEditComponent} from '../../transaction/component/transaction-security-edit.component';
import {TransactionCashaccountConnectDebitCreditComponent} from '../../transaction/component/transaction-cashaccount-connect-debit-credit-component';
import {StandingOrderCashaccountEditComponent} from './standing-order-cashaccount-edit.component';
import {StandingOrderSecurityEditComponent} from './standing-order-security-edit.component';

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
      [data]="transactions"
      [fields]="fields"
      [dataKey]="'idTransaction'"
      [(selection)]="selectedTransaction"
      [customSortFn]="customSort.bind(this)"
      [multiSortMeta]="multiSortMeta"
      (rowSelect)="onRowSelect($event)"
      (rowUnselect)="onRowUnselect($event)"
      (componentClick)="onComponentClick($event)"
      [contextMenuItems]="contextMenuItems"
      [showContextMenu]="isActivated()"
      [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [customClass]="'datatable nestedtable'"
      [scrollable]="false"
      [valueGetterFn]="getValueByPath.bind(this)"
      [negativeValueFn]="isValueByPathMinus.bind(this)"
      [baseLocale]="baseLocale"
      [contextMenuAppendTo]="'body'">
    </configurable-table>

    @if (visibleSecurityTransactionDialog) {
      <transaction-security-edit (closeDialog)="handleCloseTransactionDialog($event)"
                                 [transactionCallParam]="transactionCallParam"
                                 [visibleSecurityTransactionDialog]="visibleSecurityTransactionDialog">
      </transaction-security-edit>
    }

    @if (visibleCashaccountTransactionSingleDialog) {
      <transaction-cashaccount-editsingle (closeDialog)="handleCloseTransactionDialog($event)"
                                          [transactionCallParam]="transactionCallParam"
                                          [visibleCashaccountTransactionSingleDialog]="visibleCashaccountTransactionSingleDialog">
      </transaction-cashaccount-editsingle>
    }

    @if (visibleCashaccountTransactionDoubleDialog) {
      <transaction-cashaccount-editdouble (closeDialog)="handleCloseTransactionDialog($event)"
                                          [transactionCallParam]="transactionCallParam"
                                          [visibleCashaccountTransactionDoubleDialog]="visibleCashaccountTransactionDoubleDialog">
      </transaction-cashaccount-editdouble>
    }

    @if (visibleConnectDebitCreditDialog) {
      <transaction-cashaccount-connect-debit-credit (closeDialog)="handleCloseTransactionDialog($event)"
                                                    [transactionCallParam]="transactionCallParam"
                                                    [visibleDialog]="visibleConnectDebitCreditDialog">
      </transaction-cashaccount-connect-debit-credit>
    }

    @if (visibleStandingOrderCashaccountDialog) {
      <standing-order-cashaccount-edit
        [visibleDialog]="visibleStandingOrderCashaccountDialog"
        [callParam]="standingOrderCallParam"
        (closeDialog)="handleCloseStandingOrderDialog($event)">
      </standing-order-cashaccount-edit>
    }

    @if (visibleStandingOrderSecurityDialog) {
      <standing-order-security-edit
        [visibleDialog]="visibleStandingOrderSecurityDialog"
        [callParam]="standingOrderCallParam"
        (closeDialog)="handleCloseStandingOrderDialog($event)">
      </standing-order-security-edit>
    }
  `,
  standalone: true,
  imports: [
    CommonModule, TranslateModule, ConfigurableTableComponent,
    TransactionCashaccountEditSingleComponent,
    TransactionCashaccountEditDoubleComponent,
    TransactionSecurityEditComponent,
    TransactionCashaccountConnectDebitCreditComponent,
    StandingOrderCashaccountEditComponent,
    StandingOrderSecurityEditComponent
  ]
})
export class StandingOrderTransactionTableComponent extends TransactionContextMenu implements OnInit, OnDestroy {

  @Input() standingOrder: StandingOrder;

  transactions: Transaction[] = [];
  private portfolio: Portfolio;

  constructor(
    private portfolioService: PortfolioService,
    private standingOrderService: StandingOrderService,
    parentChildRegisterService: ParentChildRegisterService,
    activePanelService: ActivePanelService,
    transactionService: TransactionService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(parentChildRegisterService, activePanelService, transactionService, confirmationService,
      messageToastService, filterService, translateService, gps, usersettingsService, injector);
    this.addColumn(DataType.DateString, 'transactionTime', 'DATE', true, false);
    this.addColumnFeqH(DataType.String, 'transactionType', true, false, {translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'security.name', AppSettings.SECURITY.toUpperCase(), true, false);
    this.addColumn(DataType.Numeric, 'units', 'QUANTITY', true, false);
    this.addColumn(DataType.Numeric, 'quotation', 'QUOTATION_DIV', true, false);
    this.addColumnFeqH(DataType.Numeric, 'taxCost', true, false);
    this.addColumnFeqH(DataType.Numeric, 'transactionCost', true, false);
    this.addColumnFeqH(DataType.Numeric, 'cashaccountAmount', true, false, {templateName: 'greenRed'});
  }

  ngOnInit(): void {
    this.prepareTableAndTranslate();
    this.multiSortMeta.push({field: 'transactionTime', order: -1});
    this.initialize();
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  onRowSelect(event: any): void {
    this.selectedTransaction = event.data;
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

  protected getSecurity(transaction: Transaction): Security {
    return transaction.security;
  }

  protected initialize(): void {
    this.portfolioService.getPortfolioByIdSecuritycashaccount(
      this.standingOrder.cashaccount.idSecuritycashAccount
    ).subscribe(portfolio => {
      this.portfolio = portfolio;
      this.loadTransactions();
    });
  }

  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam): void {
    transactionCallParam.portfolio = this.portfolio;
  }

  private loadTransactions(): void {
    this.standingOrderService.getTransactions(this.standingOrder.idStandingOrder).subscribe(transactions => {
      this.transactions = transactions;
      this.createTranslatedValueStoreAndFilterField(this.transactions);
    });
  }
}
