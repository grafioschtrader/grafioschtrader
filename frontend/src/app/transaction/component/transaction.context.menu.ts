import {Transaction} from '../../entities/transaction';
import {CloseMarginPosition, TransactionCallParam} from './transaction.call.parm';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {InfoLevelType} from '../../shared/message/info.leve.type';

import {TransactionService} from '../service/transaction.service';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Directive, EventEmitter, Output, ViewChild} from '@angular/core';
import {Security} from '../../entities/security';
import {AppHelper} from '../../shared/helper/app.helper';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {PageFirstRowSelectedRow, ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {HelpIds} from '../../shared/help/help.ids';


/**
 * Base class for showing transaction in a table format. It supports the editing capabilities for every
 * kind of transaction.
 */
@Directive()
export abstract class TransactionContextMenu extends TableConfigBase implements IGlobalMenuAttach {
  SpecialInvestmentInstruments: typeof SpecialInvestmentInstruments = SpecialInvestmentInstruments;

  // @ViewChild('cm', { static: false }) contextMenu: ContextMenu;
  @ViewChild('cm') contextMenu: any;

  // Output
  @Output() dateChanged = new EventEmitter<ProcessedActionData>();

  // Menu on pageheader
  editMenuItems: MenuItem[] = [];
  selectedTransaction: Transaction;

  contextMenuItems: MenuItem[] = [];

  // For child transaction dialogs
  visibleSecurityTransactionDialog = false;
  visibleCashaccountTransactionSingleDialog = false;
  visibleCashaccountTransactionDoubleDialog = false;
  visibleConnectDebitCreditDialog = false;
  transactionCallParam: TransactionCallParam;

  pageFirstRowSelectedRow: PageFirstRowSelectedRow;

  constructor(protected parentChildRegisterService: ParentChildRegisterService,
              protected activePanelService: ActivePanelService,
              protected transactionService: TransactionService,
              protected confirmationService: ConfirmationService,
              protected messageToastService: MessageToastService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);
    this.pageFirstRowSelectedRow = this.parentChildRegisterService.getRowPostion(null);
  }

  handleDeleteTransaction(transaction: Transaction): void {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      (transaction.connectedIdTransaction != null) ? 'MSG_DELETE_ACCOUNTTRANSFER' : 'MSG_CONFIRM_DELETE_RECORD|TRANSACTION', () => {
        this.transactionService.deleteTransaction(transaction.idTransaction).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            (transaction.connectedIdTransaction != null) ? 'MSG_DELETE_TRANSACTIONS'
              : 'MSG_DELETE_RECORD', {i18nRecord: (transaction.connectedIdTransaction != null) ? 'TRANSACTIONS' : 'TRANSACTIONS'});
          this.afterDelete(transaction);
        });
      });
  }

  handleConnectDebitCreditTransaction(transaction: Transaction): void {
    this.visibleConnectDebitCreditDialog = true;
  }

  afterDelete(transaction): void {
    this.dateChanged.emit(new ProcessedActionData(ProcessedAction.DELETED,
      this.getSecurity(transaction)));
  }

  handleCloseTransactionDialog(processedActionData: ProcessedActionData): void {
    this.visibleSecurityTransactionDialog = false;
    this.visibleCashaccountTransactionSingleDialog = false;
    this.visibleCashaccountTransactionDoubleDialog = false;
    this.visibleConnectDebitCreditDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.dateChanged.emit(new ProcessedActionData(ProcessedAction.UPDATED,
        processedActionData.data));
      // this.getSecurity(processedActionData.data)));
    }
  }

  setMenuItemsToActivePanel(): void {
    this.activePanelService.activatePanel(this,
      {editMenu: this.getMenuItemsOnTransaction(this.selectedTransaction)});
    this.contextMenuItems = this.getMenuItemsOnTransaction(this.selectedTransaction);
  }

  onRowUnselect(event): void {
    this.selectedTransaction = null;
  }

  ////////////////////////////////////////////////
  // Event handler
  ////////////////////////////////////////////////

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  callMeDeactivate(): void {
  }

  hideContextMenu(): void {
    this.contextMenu && this.contextMenu.hide();
  }

  onComponentClick(event): void {
    event[this.consumedGT] = true;
    this.contextMenu && this.contextMenu.hide();
    this.setMenuItemsToActivePanel();
  }

  public getHelpContextId(): HelpIds {
    return null;
  }

  destroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  protected handleEditTransaction(transaction: Transaction, closeMarginPosition?: CloseMarginPosition): void {
    if (!!transaction.connectedIdTransaction) {
      if (closeMarginPosition) {
        this.handleSecurityEditTransaction(transaction, closeMarginPosition);
      } else {
        this.handleEditConnectedTransaction(transaction);
      }
    } else {
      this.handleSecurityEditTransaction(transaction);
    }
  }

  protected handleEditConnectedTransaction(transaction: Transaction): void {
    this.transactionService.getTransactionByIdTransaction(transaction.connectedIdTransaction).subscribe(transactionConnected =>
      this.handleSecurityEditTransaction(transaction, new CloseMarginPosition(transactionConnected.quotation,
        transactionConnected.units,
        transactionConnected.units, transactionConnected.idSecurityaccount, transactionConnected.idTransaction))
    );
  }

  protected handleSecurityEditTransaction(transaction: Transaction, closeMarginPosition?: CloseMarginPosition): void {
    // this.menuedit.hide();
    this.transactionCallParam = new TransactionCallParam();
    this.transactionCallParam.transaction = transaction;
    this.transactionCallParam.closeMarginPosition = closeMarginPosition;

    if (Transaction.isSecurityTransaction(transaction.transactionType)) {
      this.transactionCallParam.security = this.getSecurity(transaction);
      this.visibleSecurityTransactionDialog = true;
    } else if (Transaction.isOnlyCashAccountTransaction(transaction.transactionType)) {
      this.prepareTransactionCallParam(this.transactionCallParam);
      if (transaction.connectedIdTransaction !== null) {
        this.visibleCashaccountTransactionDoubleDialog = true;
      } else {
        this.visibleCashaccountTransactionSingleDialog = true;
      }
    }
  }

  protected abstract getSecurity(transaction: Transaction): Security;

  protected abstract initialize(): void;

  protected abstract prepareTransactionCallParam(transactionCallParam: TransactionCallParam);

  protected goToFirsRowPosition(id: number): void {
    setTimeout(() => {
      this.pageFirstRowSelectedRow = this.parentChildRegisterService.getRowPostion(id);
      this.firstRowIndexOnPage = this.pageFirstRowSelectedRow.topPageRow;
    });
  }

  protected getMenuItemsOnTransaction(transaction: Transaction): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (transaction && transaction.idTransaction >= 0) {
      menuItems.push({
        label: 'EDIT_RECORD|TRANSACTION',
        command: (e) => (transaction) ? this.handleEditTransaction(transaction) : null,
        disabled: !transaction || !transaction.idTransaction
      });

      menuItems.push({
        label: 'DELETE_RECORD|TRANSACTION',
        command: (e) => (transaction) ? this.handleDeleteTransaction(transaction) : null,
        disabled: !transaction || !transaction.idTransaction || !this.canDeleteTransaction(transaction)
      });

      menuItems.push({
        label: 'CHANGE_TO_ACCOUNT_TRANSFER',
        command: (e) => (transaction) ? this.handleConnectDebitCreditTransaction(transaction) : null,
        disabled: !transaction || !transaction.idTransaction
          || !Transaction.isWithdrawalOrDeposit(transaction.transactionType) || !!transaction.connectedIdTransaction
      });

      TranslateHelper.translateMenuItems(menuItems, this.translateService);
    }
    return menuItems;
  }

  protected canDeleteTransaction(transaction): boolean {
    return true;
  }

}


