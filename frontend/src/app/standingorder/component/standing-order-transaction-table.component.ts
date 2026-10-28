import { Component, Injector, Input, OnDestroy, OnChanges, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, switchMap } from 'rxjs';

import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService, FilterService } from '@openng/optimus-ui/api';
import { TransactionContextMenu } from '../../transaction/component/transaction.context.menu';
import { TransactionService } from '../../transaction/service/transaction.service';
import { TransactionCallParam } from '../../transaction/component/transaction.call.parm';
import { Transaction } from '../../entities/transaction';
import { Security } from '../../entities/security';
import { Portfolio } from '../../entities/portfolio';
import { StandingOrder } from '../../entities/standing.order';
import { StandingOrderService } from '../service/standing.order.service';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { ParentChildRegisterService } from '../../shared/service/parent.child.register.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { AppSettings } from '../../shared/app.settings';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TransactionCashaccountEditSingleComponent } from '../../transaction/component/transaction-cashaccount-editsingle.component';
import { TransactionCashaccountEditDoubleComponent } from '../../transaction/component/transaction-cashaccount-editdouble.component';
import { TransactionSecurityEditComponent } from '../../transaction/component/transaction-security-edit.component';
import { TransactionCashaccountConnectDebitCreditDialogComponent } from '../../transaction/component/transaction-cashaccount-connect-debit-credit-dialog.component';
import { StandingOrderCashaccountEditComponent } from './standing-order-cashaccount-edit.component';
import { StandingOrderSecurityEditComponent } from './standing-order-security-edit.component';

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
      [data]="transactions()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="'idTransaction'"
      [(selection)]="selectedTransaction"
      [customSortFn]="customSorter"
      [multiSortMeta]="multiSortMeta"
      (rowSelect)="onRowSelect($event)"
      (rowUnselect)="onRowUnselect($event)"
      (componentClick)="onComponentClick($event)"
      [contextMenuItems]="contextMenuItems()"
      [showContextMenu]="isActivated()"
      [containerClass]="{
        'data-container': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [customClass]="'datatable nestedtable'"
      [scrollable]="false"
      [valueGetterFn]="valueGetter"
      [negativeValueFn]="negativeValue"
      [baseLocale]="baseLocale"
      [contextMenuAppendTo]="'body'" />

    @if (visibleSecurityTransactionDialog()) {
      <transaction-security-edit
        (closeDialog)="handleCloseTransactionDialog($event)"
        [transactionCallParam]="transactionCallParam"
        [visibleSecurityTransactionDialog]="visibleSecurityTransactionDialog()" />
    }

    @if (visibleCashaccountTransactionSingleDialog()) {
      <transaction-cashaccount-editsingle
        (closeDialog)="handleCloseTransactionDialog($event)"
        [transactionCallParam]="transactionCallParam"
        [visibleCashaccountTransactionSingleDialog]="visibleCashaccountTransactionSingleDialog()" />
    }

    @if (visibleCashaccountTransactionDoubleDialog()) {
      <transaction-cashaccount-editdouble
        (closeDialog)="handleCloseTransactionDialog($event)"
        [transactionCallParam]="transactionCallParam"
        [visibleCashaccountTransactionDoubleDialog]="visibleCashaccountTransactionDoubleDialog()" />
    }

    @if (visibleConnectDebitCreditDialog()) {
      <transaction-cashaccount-connect-debit-credit
        (closeDialog)="handleCloseTransactionDialog($event)"
        [transactionCallParam]="transactionCallParam"
        [visibleDialog]="visibleConnectDebitCreditDialog()" />
    }

    @if (visibleStandingOrderCashaccountDialog()) {
      <standing-order-cashaccount-edit
        [visibleDialog]="visibleStandingOrderCashaccountDialog()"
        [callParam]="standingOrderCallParam"
        (closeDialog)="handleCloseStandingOrderDialog($event)" />
    }

    @if (visibleStandingOrderSecurityDialog()) {
      <standing-order-security-edit
        [visibleDialog]="visibleStandingOrderSecurityDialog()"
        [callParam]="standingOrderCallParam"
        (closeDialog)="handleCloseStandingOrderDialog($event)" />
    }
  `,
  imports: [
    TranslateModule,
    ConfigurableTableComponent,
    TransactionCashaccountEditSingleComponent,
    TransactionCashaccountEditDoubleComponent,
    TransactionSecurityEditComponent,
    TransactionCashaccountConnectDebitCreditDialogComponent,
    StandingOrderCashaccountEditComponent,
    StandingOrderSecurityEditComponent
  ]
})
export class StandingOrderTransactionTableComponent extends TransactionContextMenu implements OnChanges, OnDestroy {
  @Input() standingOrder: StandingOrder;

  readonly transactions = signal<Transaction[]>([]);
  private readSubscription?: Subscription;
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
    super(
      parentChildRegisterService,
      activePanelService,
      transactionService,
      confirmationService,
      messageToastService,
      filterService,
      translateService,
      gps,
      usersettingsService,
      injector
    );
    this.addColumn(DataType.DateString, 'transactionTime', 'DATE', true, false);
    this.addColumnFeqH(DataType.String, 'transactionType', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.String, 'security.name', AppSettings.SECURITY.toUpperCase(), true, false);
    this.addColumn(DataType.Numeric, 'units', 'QUANTITY', true, false);
    this.addColumn(DataType.Numeric, 'quotation', 'QUOTATION_DIV', true, false);
    this.addColumnFeqH(DataType.Numeric, 'taxCost', true, false);
    this.addColumnFeqH(DataType.Numeric, 'transactionCost', true, false);
    this.addColumnFeqH(DataType.Numeric, 'cashaccountAmount', true, false, {
      templateName: 'greenRed'
    });
  }

  ngOnChanges(): void {
    this.prepareTableAndTranslate();
    this.multiSortMeta = [{ field: 'transactionTime', order: -1 }];
    this.initialize();
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  onRowSelect(event: any): void {
    this.selectedTransaction.set(event.data);
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
    this.readSubscription?.unsubscribe();
    this.readSubscription = this.portfolioService
      .getPortfolioByIdSecuritycashaccount(this.standingOrder.cashaccount.idSecuritycashAccount)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap((portfolio) => {
          this.portfolio = portfolio;
          return this.standingOrderService.getTransactions(this.standingOrder.idStandingOrder);
        })
      )
      .subscribe((transactions) => this.publishTransactions(transactions));
  }

  private publishTransactions(transactions: Transaction[]): void {
    this.createTranslatedValueStoreAndFilterField(transactions);
    this.transactions.set(transactions);
  }

  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam): void {
    transactionCallParam.portfolio = this.portfolio;
  }

  private loadTransactions(): void {
    this.initialize();
  }
}
