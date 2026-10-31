import { requireInitialized } from '../../lib/helper/required-state';
import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { Transaction } from '../../entities/transaction';
import { CloseMarginPosition, TransactionCallParam } from './transaction.call.parm';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { TransactionService } from '../service/transaction.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { DestroyRef, Directive, inject, signal, output, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { Security } from '../../entities/security';
import { AppHelper } from '../../lib/helper/app.helper';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import {
  PageFirstRowSelectedRow,
  ParentChildRegisterService
} from '../../shared/service/parent.child.register.service';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { ConfirmationService, MenuItem } from '@openng/optimus-ui/api';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';
import { StandingOrderCallParam } from '../../standingorder/model/standing.order.call.param';
import { BaseSettings } from '../../lib/base.settings';
import { transactionMenuPermissions } from './transaction.menu.permissions';
import { ColumnConfig } from '../../lib/datashowbase/column.config';

/**
 * Abstract base class for displaying transactions in a table format with comprehensive editing capabilities.
 * Provides context menu functionality, transaction editing dialogs, and menu integration for all transaction types.
 * Handles security transactions, cash account transactions, and account transfers with proper validation and user interaction.
 */
@Directive()
export abstract class TransactionContextMenu extends TableConfigBase implements IGlobalMenuAttach {
  protected parentChildRegisterService: ParentChildRegisterService = inject(ParentChildRegisterService);
  protected activePanelService: ActivePanelService = inject(ActivePanelService);
  protected transactionService: TransactionService = inject(TransactionService);
  protected confirmationService: ConfirmationService = inject(ConfirmationService);
  protected messageToastService: MessageToastService = inject(MessageToastService);

  protected readonly destroyRef = inject(DestroyRef);
  private menuSubscription?: Subscription;
  private menuTransaction?: Transaction | null;
  private menuSignature?: string;
  protected dataSubscription?: Subscription;
  private editSubscription?: Subscription;
  readonly negativeValue = (row: any, field: ColumnConfig) => this.isValueByPathMinus(row, field);
  /** Reference to special investment instruments enum for template usage */
  SpecialInvestmentInstruments: typeof SpecialInvestmentInstruments = SpecialInvestmentInstruments;

  /** ViewChild reference to the Optimus context menu component */
  readonly contextMenu = viewChild<any>('cm');

  /** Emits processed action data when transaction operations complete */
  readonly dateChanged = output<ProcessedActionData>();

  /** Currently selected transaction in the table */
  readonly selectedTransaction = signal<Transaction | null>(null);

  /** Menu items displayed in the context menu for the selected transaction */
  readonly contextMenuItems = signal<MenuItem[]>([]);

  /** Controls visibility of the security transaction edit dialog */
  readonly visibleSecurityTransactionDialog = signal(false);

  /** Controls visibility of the single cash account transaction edit dialog */
  readonly visibleCashaccountTransactionSingleDialog = signal(false);

  /** Controls visibility of the double cash account transaction edit dialog */
  readonly visibleCashaccountTransactionDoubleDialog = signal(false);

  /** Controls visibility of the connect debit/credit transaction dialog */
  readonly visibleConnectDebitCreditDialog = signal(false);

  /** Controls visibility of the cashaccount standing order edit dialog */
  readonly visibleStandingOrderCashaccountDialog = signal(false);

  /** Controls visibility of the security standing order edit dialog */
  readonly visibleStandingOrderSecurityDialog = signal(false);

  /** Parameters passed to transaction editing dialogs */
  private initializedTransactionCallParam?: TransactionCallParam;

  get transactionCallParam(): TransactionCallParam {
    return requireInitialized(this.initializedTransactionCallParam, 'TransactionContextMenu.transactionCallParam');
  }

  set transactionCallParam(value: TransactionCallParam) {
    this.initializedTransactionCallParam = value;
  }

  /** Parameters passed to standing order creation dialog */
  private initializedStandingOrderCallParam?: StandingOrderCallParam;

  get standingOrderCallParam(): StandingOrderCallParam {
    return requireInitialized(this.initializedStandingOrderCallParam, 'TransactionContextMenu.standingOrderCallParam');
  }

  set standingOrderCallParam(value: StandingOrderCallParam) {
    this.initializedStandingOrderCallParam = value;
  }

  /** Maintains page position and selected row state for table navigation */
  readonly pageFirstRowSelectedRow = signal<PageFirstRowSelectedRow>(new PageFirstRowSelectedRow(0, null));

  /**
   * Creates a new TransactionContextMenu instance with required services.
   */
  constructor() {
    super();
    this.pageFirstRowSelectedRow.set(this.parentChildRegisterService.getRowPosition(null));
    this.destroyRef.onDestroy(() => this.destroy());
  }

  /**
   * Handles the deletion of a transaction with appropriate confirmation dialog.
   * Shows different confirmation messages for connected transactions vs single transactions.
   * @param transaction The transaction to be deleted
   */
  handleDeleteTransaction(transaction: Transaction): void {
    const id = transaction.idTransaction;
    if (id == null) return;
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      transaction.connectedIdTransaction != null
        ? 'MSG_DELETE_ACCOUNTTRANSFER'
        : 'MSG_CONFIRM_DELETE_RECORD|TRANSACTION',
      () => {
        this.transactionService
          .deleteTransaction(id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe((response) => {
            this.messageToastService.showMessageI18n(
              InfoLevelType.SUCCESS,
              transaction.connectedIdTransaction != null ? 'MSG_DELETE_TRANSACTIONS' : 'MSG_DELETE_RECORD',
              { i18nRecord: transaction.connectedIdTransaction != null ? 'TRANSACTIONS' : 'TRANSACTIONS' }
            );
            this.afterDelete(transaction);
          });
      }
    );
  }

  /**
   * Initiates the process to connect a withdrawal/deposit transaction to create an account transfer.
   * @param transaction The transaction to be connected for account transfer creation
   */
  handleConnectDebitCreditTransaction(transaction: Transaction): void {
    this.visibleConnectDebitCreditDialog.set(true);
  }

  /**
   * Opens the appropriate standing order creation dialog pre-filled with data from the selected transaction.
   * @param transaction The transaction to use as template for the standing order
   */
  handleCreateStandingOrderFromTransaction(transaction: Transaction): void {
    this.standingOrderCallParam = new StandingOrderCallParam(null, transaction);
    if (Transaction.isSecurityTransaction(transaction.transactionType)) {
      this.visibleStandingOrderSecurityDialog.set(true);
    } else if (Transaction.isWithdrawalOrDeposit(transaction.transactionType)) {
      this.visibleStandingOrderCashaccountDialog.set(true);
    }
  }

  /**
   * Handles the closing of standing order creation dialogs.
   * @param processedActionData The result data from the dialog operation
   */
  handleCloseStandingOrderDialog(processedActionData: ProcessedActionData): void {
    this.visibleStandingOrderCashaccountDialog.set(false);
    this.visibleStandingOrderSecurityDialog.set(false);
  }

  /**
   * Toggles the taxable flag of a dividend or interest transaction, bypassing the closedUntil period lock.
   * Emits a change event afterward so dependent views (e.g. the dividends overview) recalculate.
   * @param transaction The dividend or interest transaction whose taxable flag is toggled
   */
  handleToggleTaxableInterest(transaction: Transaction): void {
    const id = transaction.idTransaction;
    if (id == null) return;
    this.transactionService
      .updateTaxableInterest(id, !(transaction.taxableInterest === true))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.dateChanged.emit(new ProcessedActionData(ProcessedAction.UPDATED, transaction)));
  }

  /**
   * Performs cleanup and notification after a transaction has been successfully deleted.
   * @param transaction The transaction that was deleted
   */
  afterDelete(transaction: Transaction): void {
    this.dateChanged.emit(new ProcessedActionData(ProcessedAction.DELETED, this.getSecurity(transaction)));
  }

  /**
   * Handles the closing of transaction editing dialogs and processes the result.
   * Updates UI state and emits change events if data was modified.
   * @param processedActionData The result data from the dialog operation
   */
  handleCloseTransactionDialog(processedActionData: ProcessedActionData): void {
    this.visibleSecurityTransactionDialog.set(false);
    this.visibleCashaccountTransactionSingleDialog.set(false);
    this.visibleCashaccountTransactionDoubleDialog.set(false);
    this.visibleConnectDebitCreditDialog.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.dateChanged.emit(new ProcessedActionData(ProcessedAction.UPDATED, processedActionData.data));
      // this.getSecurity(processedActionData.data)));
    }
  }

  /**
   * Activates the current panel and sets up the context menu items based on the selected transaction.
   */
  setMenuItemsToActivePanel(): void {
    const items = this.getMenuItemsOnTransaction(this.selectedTransaction());
    const signature = JSON.stringify(items);
    // Replacing an unchanged model while its context menu is open detaches the clicked menu item.
    if (this.menuTransaction === this.selectedTransaction() && this.menuSignature === signature) {
      this.activePanelService.activatePanel(this, { editMenu: this.contextMenuItems() });
      return;
    }
    this.menuTransaction = this.selectedTransaction();
    this.menuSignature = signature;
    this.menuSubscription?.unsubscribe();
    this.menuSubscription = TranslateHelper.translateMenuItems$(items, this.translateService)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((items) => {
        this.contextMenuItems.set(items);
        this.activePanelService.activatePanel(this, { editMenu: items });
      });
  }

  /**
   * Handles row unselection events by clearing the selected transaction.
   * @param event The unselection event from the table
   */
  onRowUnselect(event: unknown): void {
    this.selectedTransaction.set(null);
    this.setMenuItemsToActivePanel();
  }

  selectPageRow(selectedRow: any): void {
    this.pageFirstRowSelectedRow.update((position) => new PageFirstRowSelectedRow(position.topPageRow, selectedRow));
  }

  override onPage(event: any): void {
    super.onPage(event);
    this.pageFirstRowSelectedRow.update((position) => new PageFirstRowSelectedRow(event.first, position.selectedRow));
  }

  /**
   * Checks if this component is currently the activated panel.
   * @returns True if this component is the active panel
   */
  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  /** Called when the component is deactivated, can be used for cleanup */
  callMeDeactivate(): void {}

  /** Hides the context menu if it is currently visible */
  hideContextMenu(): void {
    const contextMenu = this.contextMenu();
    contextMenu && contextMenu.hide();
  }

  /**
   * Handles component click events, manages event propagation and menu state.
   * @param event The click event from the component
   */
  onComponentClick(event: PanelClickEvent): void {
    event[this.consumedGT] = true;
    const contextMenu = this.contextMenu();
    contextMenu && contextMenu.hide();
    this.setMenuItemsToActivePanel();
  }

  public getHelpContextId(): string | null {
    return null;
  }

  /** Destroys the component and cleans up panel registration */
  destroy(): void {
    this.menuSubscription?.unsubscribe();
    this.dataSubscription?.unsubscribe();
    this.editSubscription?.unsubscribe();
    this.activePanelService.destroyPanel(this);
  }

  /**
   * Handles the editing of a transaction, determining the appropriate edit dialog based on transaction type.
   * @param transaction The transaction to be edited
   * @param closeMarginPosition Optional margin position data for closing margin transactions
   */
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

  /**
   * Handles editing of connected transactions by retrieving the connected transaction data.
   * @param transaction The transaction with a connected transaction to be edited
   */
  protected handleEditConnectedTransaction(transaction: Transaction): void {
    const connectedId = transaction.connectedIdTransaction;
    if (connectedId == null) return;
    this.editSubscription?.unsubscribe();
    this.editSubscription = this.transactionService
      .getTransactionByIdTransaction(connectedId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((transactionConnected) => {
        // The counterpart of a cash transfer has no quotation, units or security account; its dialog still opens.
        const { quotation, units, idSecurityaccount, idTransaction } = transactionConnected;
        const closeMarginPosition =
          quotation == null || units == null || idSecurityaccount == null || idTransaction == null
            ? undefined
            : new CloseMarginPosition(quotation, units, units, idSecurityaccount, idTransaction);
        this.handleSecurityEditTransaction(transaction, closeMarginPosition);
      });
  }

  /**
   * Opens the appropriate transaction editing dialog based on the transaction type.
   * Configures dialog parameters and displays security or cash account transaction dialogs.
   * @param transaction The transaction to be edited
   * @param closeMarginPosition Optional margin position data for closing margin positions
   */
  protected handleSecurityEditTransaction(transaction: Transaction, closeMarginPosition?: CloseMarginPosition): void {
    // this.menuedit.hide();
    this.transactionCallParam = new TransactionCallParam();
    this.transactionCallParam.transaction = transaction;
    this.transactionCallParam.closeMarginPosition = closeMarginPosition;
    if (Transaction.isSecurityTransaction(transaction.transactionType)) {
      const security = this.getSecurity(transaction);
      if (!security) return;
      this.transactionCallParam.security = security;
      this.visibleSecurityTransactionDialog.set(true);
    } else if (Transaction.isOnlyCashAccountTransaction(transaction.transactionType)) {
      this.prepareTransactionCallParam(this.transactionCallParam);
      if (transaction.connectedIdTransaction != null) {
        this.visibleCashaccountTransactionDoubleDialog.set(true);
      } else {
        this.visibleCashaccountTransactionSingleDialog.set(true);
      }
    }
  }

  /**
   * Abstract method to retrieve the security associated with a transaction.
   * @param transaction The transaction for which to get the security
   * @returns The security associated with the transaction
   */
  protected abstract getSecurity(transaction: Transaction): Security | undefined;

  /** Abstract method for initializing component data and state */
  protected abstract initialize(): void;

  /**
   * Abstract method for preparing transaction call parameters before opening edit dialogs.
   * @param transactionCallParam The transaction call parameters to be prepared
   */
  protected abstract prepareTransactionCallParam(transactionCallParam: TransactionCallParam): void;

  /**
   * Navigates to the first row position in the table based on the provided ID.
   * @param id The identifier used to determine the row position
   */
  protected goToFirsRowPosition(id: number | null | undefined): void {
    const saved = this.parentChildRegisterService.getRowPosition(id);
    this.firstRowIndexOnPage = saved.topPageRow;
    this.pageFirstRowSelectedRow.set(new PageFirstRowSelectedRow(saved.topPageRow, saved.selectedRow));
  }

  /**
   * Generates context menu items for the selected transaction based on transaction state and type.
   * @param transaction The transaction for which to generate menu items
   * @returns Array of menu items appropriate for the transaction
   */
  protected getMenuItemsOnTransaction(transaction: Transaction | null): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (transaction?.idTransaction != null && transaction.idTransaction >= 0) {
      const permissions = transactionMenuPermissions(transaction, this.canDeleteTransaction(transaction));
      menuItems.push({
        label: 'EDIT_RECORD|TRANSACTION',
        command: (e) => (transaction ? this.handleEditTransaction(transaction) : null),
        disabled: !permissions.edit
      });

      menuItems.push({
        label: 'DELETE_RECORD|TRANSACTION',
        command: (e) => (transaction ? this.handleDeleteTransaction(transaction) : null),
        disabled: !permissions.delete
      });

      menuItems.push({
        label: 'CHANGE_TO_ACCOUNT_TRANSFER',
        command: (e) => (transaction ? this.handleConnectDebitCreditTransaction(transaction) : null),
        disabled: !permissions.transfer
      });

      menuItems.push({
        label: 'CREATE|STANDING_ORDER',
        command: (e) => (transaction ? this.handleCreateStandingOrderFromTransaction(transaction) : null),
        disabled: !permissions.standingOrder
      });

      menuItems.push({
        label: 'TAXABLE_INTEREST_TOGGLE',
        icon:
          transaction.taxableInterest === true
            ? BaseSettings.ICONNAME_SQUARE_CHECK
            : BaseSettings.ICONNAME_SQUARE_EMTPY,
        command: (e) => (transaction ? this.handleToggleTaxableInterest(transaction) : null),
        disabled: !permissions.taxableInterest
      });
    }
    return menuItems;
  }

  /**
   * Determines if a transaction can be deleted based on business rules.
   * @param transaction The transaction to check for deletion eligibility
   * @returns True if the transaction can be deleted
   */
  protected canDeleteTransaction(transaction: Transaction): boolean {
    return true;
  }
}
