import {Transaction} from '../../entities/transaction';
import {CloseMarginPosition, TransactionCallParam} from './transaction.call.parm';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {TransactionService} from '../service/transaction.service';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {Directive, EventEmitter, Injector, Output, ViewChild} from '@angular/core';
import {Security} from '../../entities/security';
import {AppHelper} from '../../lib/helper/app.helper';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {PageFirstRowSelectedRow, ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {HelpIds} from '../../lib/help/help.ids';


/**
 * Abstract base class for displaying transactions in a table format with comprehensive editing capabilities.
 * Provides context menu functionality, transaction editing dialogs, and menu integration for all transaction types.
 * Handles security transactions, cash account transactions, and account transfers with proper validation and user interaction.
 */
@Directive()
export abstract class TransactionContextMenu extends TableConfigBase implements IGlobalMenuAttach {
  /** Reference to special investment instruments enum for template usage */
  SpecialInvestmentInstruments: typeof SpecialInvestmentInstruments = SpecialInvestmentInstruments;

  /** ViewChild reference to the PrimeNG context menu component */
  @ViewChild('cm') contextMenu: any;

  /** Emits processed action data when transaction operations complete */
  @Output() dateChanged = new EventEmitter<ProcessedActionData>();

  /** Menu items displayed in the page header for editing operations */
  editMenuItems: MenuItem[] = [];

  /** Currently selected transaction in the table */
  selectedTransaction: Transaction;

  /** Menu items displayed in the context menu for the selected transaction */
  contextMenuItems: MenuItem[] = [];

  /** Controls visibility of the security transaction edit dialog */
  visibleSecurityTransactionDialog = false;

  /** Controls visibility of the single cash account transaction edit dialog */
  visibleCashaccountTransactionSingleDialog = false;

  /** Controls visibility of the double cash account transaction edit dialog */
  visibleCashaccountTransactionDoubleDialog = false;

  /** Controls visibility of the connect debit/credit transaction dialog */
  visibleConnectDebitCreditDialog = false;

  /** Parameters passed to transaction editing dialogs */
  transactionCallParam: TransactionCallParam;

  /** Maintains page position and selected row state for table navigation */
  pageFirstRowSelectedRow: PageFirstRowSelectedRow;

  /**
   * Creates a new TransactionContextMenu instance with required services.
   * @param parentChildRegisterService Service for managing parent-child component relationships and state
   * @param activePanelService Service for managing active panel state and menu integration
   * @param transactionService Service for transaction CRUD operations and business logic
   * @param confirmationService PrimeNG service for displaying confirmation dialogs
   * @param messageToastService Service for displaying user notifications and messages
   * @param filterService PrimeNG service for table filtering functionality
   * @param translateService Angular service for internationalization and translation
   * @param gps Global parameter service for application-wide settings and configuration
   * @param usersettingsService Service for managing user-specific settings and preferences
   */
  protected constructor(protected parentChildRegisterService: ParentChildRegisterService,
              protected activePanelService: ActivePanelService,
              protected transactionService: TransactionService,
              protected confirmationService: ConfirmationService,
              protected messageToastService: MessageToastService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService,
              injector: Injector) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.pageFirstRowSelectedRow = this.parentChildRegisterService.getRowPosition(null);
  }

  /**
   * Handles the deletion of a transaction with appropriate confirmation dialog.
   * Shows different confirmation messages for connected transactions vs single transactions.
   * @param transaction The transaction to be deleted
   */
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

  /**
   * Initiates the process to connect a withdrawal/deposit transaction to create an account transfer.
   * @param transaction The transaction to be connected for account transfer creation
   */
  handleConnectDebitCreditTransaction(transaction: Transaction): void {
    this.visibleConnectDebitCreditDialog = true;
  }

  /**
   * Performs cleanup and notification after a transaction has been successfully deleted.
   * @param transaction The transaction that was deleted
   */
  afterDelete(transaction: Transaction): void {
    this.dateChanged.emit(new ProcessedActionData(ProcessedAction.DELETED,
      this.getSecurity(transaction)));
  }

  /**
   * Handles the closing of transaction editing dialogs and processes the result.
   * Updates UI state and emits change events if data was modified.
   * @param processedActionData The result data from the dialog operation
   */
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

  /**
   * Activates the current panel and sets up the context menu items based on the selected transaction.
   */
  setMenuItemsToActivePanel(): void {
    this.activePanelService.activatePanel(this,
      {editMenu: this.getMenuItemsOnTransaction(this.selectedTransaction)});
    this.contextMenuItems = this.getMenuItemsOnTransaction(this.selectedTransaction);
  }

  /**
   * Handles row unselection events by clearing the selected transaction.
   * @param event The unselection event from the table
   */
  onRowUnselect(event): void {
    this.selectedTransaction = null;
  }

  /**
   * Checks if this component is currently the activated panel.
   * @returns True if this component is the active panel
   */
  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  /** Called when the component is deactivated, can be used for cleanup */
  callMeDeactivate(): void {
  }

  /** Hides the context menu if it is currently visible */
  hideContextMenu(): void {
    this.contextMenu && this.contextMenu.hide();
  }

  /**
   * Handles component click events, manages event propagation and menu state.
   * @param event The click event from the component
   */
  onComponentClick(event): void {
    event[this.consumedGT] = true;
    this.contextMenu && this.contextMenu.hide();
    this.setMenuItemsToActivePanel();
  }

  public getHelpContextId(): string {
    return null;
  }

  /** Destroys the component and cleans up panel registration */
  destroy(): void {
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
    this.transactionService.getTransactionByIdTransaction(transaction.connectedIdTransaction).subscribe(transactionConnected =>
      this.handleSecurityEditTransaction(transaction, new CloseMarginPosition(transactionConnected.quotation,
        transactionConnected.units,
        transactionConnected.units, transactionConnected.idSecurityaccount, transactionConnected.idTransaction))
    );
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

  /**
   * Abstract method to retrieve the security associated with a transaction.
   * @param transaction The transaction for which to get the security
   * @returns The security associated with the transaction
   */
  protected abstract getSecurity(transaction: Transaction): Security;

  /** Abstract method for initializing component data and state */
  protected abstract initialize(): void;

  /**
   * Abstract method for preparing transaction call parameters before opening edit dialogs.
   * @param transactionCallParam The transaction call parameters to be prepared
   */
  protected abstract prepareTransactionCallParam(transactionCallParam: TransactionCallParam);

  /**
   * Navigates to the first row position in the table based on the provided ID.
   * @param id The identifier used to determine the row position
   */
  protected goToFirsRowPosition(id: number): void {
    setTimeout(() => {
      this.pageFirstRowSelectedRow = this.parentChildRegisterService.getRowPosition(id);
      this.firstRowIndexOnPage = this.pageFirstRowSelectedRow.topPageRow;
    });
  }

  /**
   * Generates context menu items for the selected transaction based on transaction state and type.
   * @param transaction The transaction for which to generate menu items
   * @returns Array of menu items appropriate for the transaction
   */
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

  /**
   * Determines if a transaction can be deleted based on business rules.
   * @param transaction The transaction to check for deletion eligibility
   * @returns True if the transaction can be deleted
   */
  protected canDeleteTransaction(transaction): boolean {
    return true;
  }

}


