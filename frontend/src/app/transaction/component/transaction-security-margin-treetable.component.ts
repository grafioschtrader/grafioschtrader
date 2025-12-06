import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {ConfirmationService, FilterService, MenuItem, TreeNode} from 'primeng/api';
import {TransactionContextMenu} from './transaction.context.menu';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {TransactionService} from '../service/transaction.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {Transaction} from '../../entities/transaction';
import {Security} from '../../entities/security';
import {SecurityTransactionSummary} from '../../entities/view/security.transaction.summary';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {SecurityTransactionPosition} from '../../entities/view/security.transaction.position';
import {CloseMarginPosition, TransactionCallParam} from './transaction.call.parm';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {TransactionType} from '../../shared/types/transaction.type';
import {ProposedMarginFinanceCost} from '../model/proposed.margin.finance.cost';
import {TransactionSecurityFieldDefinition} from './transaction.security.field.definition';
import {TransactionSecurityOptionalParam} from '../model/transaction.security.optional.param';
import {HelpIds} from '../../lib/help/help.ids';
import {CommonModule} from '@angular/common';
import {TreeTableModule} from 'primeng/treetable';
import {TooltipModule} from 'primeng/tooltip';
import {ContextMenuModule} from 'primeng/contextmenu';
import {TransactionSecurityEditComponent} from './transaction-security-edit.component';

/**
 * Angular component that displays margin-based security transactions in a hierarchical tree table format.
 * This component provides functionality for viewing, editing, and managing margin transactions including
 * open positions, closed positions, and hypothetical transactions. It supports operations like closing
 * margin positions and adding finance costs. The tree structure shows the relationship between opening
 * transactions and their corresponding closing or hypothetical transactions.
 */
@Component({
    selector: 'transaction-security-margin-treetable',
  template: `
    <div #cmDiv class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <div class="datatable nestedtable">
        <p-treeTable [value]="transactionNodes" [columns]="fields" [(selection)]="selectedNode"
                     selectionMode="single" [scrollable]="false" dataKey="transaction.idTransaction"
                     (onNodeSelect)="onNodeSelect($event)" (onNodeUnselect)="onNodeUnselect($event)"
                     [paginator]="true" [rows]="20" showGridlines="true">
          <ng-template #header let-fields>
            <tr>
              @for (field of fields; track field.field) {
                <th [style.width.px]="field.width"
                    [pTooltip]="field.headerTooltipTranslated" class="word-break-header" [attr.lang]="lang">
                  {{field.headerTranslated}}
                </th>
              }
            </tr>
          </ng-template>
          <ng-template #body let-rowNode let-rowData="rowData" let-columns="fields">
            <tr [ttSelectableRow]="rowNode" [ttSelectableRowDisabled]="rowData.transaction.idTransaction < 0"
                [ngClass]="{'rowgroup-total': rowData.transaction.idTransaction < 0}">
              @for (field of fields; track field.field; let i = $index) {
                <td [ngClass]="{'text-end': (field.dataType===DataType.NumericInteger  || field.dataType===DataType.Numeric
            || field.dataType===DataType.DateTimeNumeric)}" [style.width.px]="field.width">
                  @if (i === 0) {
                    <p-treeTableToggler [rowNode]="rowNode"></p-treeTableToggler>
                  }
                  @switch (field.templateName) {
                    @case ('greenRed') {
                      <span [style.color]='isValueByPathMinus(rowData, field)? "red": "inherit"'>
                      {{getValueByPath(rowData, field)}}
                    </span>
                    }
                    @default {
                      <span>{{getValueByPath(rowData, field)}}</span>
                    }
                  }
                </td>
              }
            </tr>
          </ng-template>
        </p-treeTable>
        <p-contextMenu #cm [target]="cmDiv" [model]="contextMenuItems" appendTo="body">
        </p-contextMenu>
      </div>
      @if (visibleSecurityTransactionDialog) {
        <transaction-security-edit [transactionCallParam]="transactionCallParam"
                                   [visibleSecurityTransactionDialog]="visibleSecurityTransactionDialog"
                                   (closeDialog)="handleCloseTransactionDialog($event)">
        </transaction-security-edit>
      }
    </div>
  `,
    standalone: true,
    imports: [
      CommonModule,
      TreeTableModule,
      TooltipModule,
      ContextMenuModule,
      TransactionSecurityEditComponent
    ]
})
export class TransactionSecurityMarginTreetableComponent extends TransactionContextMenu implements OnInit, OnDestroy {
  /** Array of security account IDs to filter transactions */
  @Input() idsSecurityaccount: number[];

  /** ID of the specific security currency to display transactions for */
  @Input() idSecuritycurrency: number;

  /** Portfolio ID for filtering transactions by portfolio */
  @Input() idPortfolio: number;

  /** Tenant ID for filtering transactions by tenant */
  @Input() idTenant: number;

  /** Optional parameters for configuring transaction security display options */
  @Input() transactionSecurityOptionalParam: TransactionSecurityOptionalParam[];

  /** Property key used to store hypothetical transaction data in tree node data */
  readonly HYPOTHETICAL_TRANSACTION_PROPERTY = 'hypotheticalTransactionProperty';

  /** Property key used to indicate if an open position has a corresponding close position */
  readonly OPEN_HAS_CLOSE_POSITION = 'closeHasOpenPosition';

  /** Array of tree nodes representing the hierarchical transaction structure */
  transactionNodes: TreeNode[] = [];

  /** Currently selected node in the tree table */
  selectedNode: TreeNode;

  /** Column configurations for main currency display */
  currencyColumnConfigMC: ColumnConfig[] = [];

  /** Summary data containing security information and transaction positions */
  securityTransactionSummary: SecurityTransactionSummary = new SecurityTransactionSummary(null, null);

  /** List of security transaction positions to be displayed in the tree */
  transactionPositionList: SecurityTransactionPosition[] = [];

  /** Current user language setting for display purposes */
  lang: string;

  /**
   * Creates a new instance of TransactionSecurityMarginTreetableComponent.
   *
   * @param securityService Service for security-related operations and data retrieval
   * @param parentChildRegisterService Service for managing parent-child component relationships
   * @param activePanelService Service for managing active panel state
   * @param transactionService Service for transaction operations and data management
   * @param confirmationService PrimeNG service for displaying confirmation dialogs
   * @param messageToastService Service for displaying toast messages to users
   * @param filterService PrimeNG service for table filtering functionality
   * @param translateService Angular service for internationalization and translations
   * @param gps Global parameter service for application-wide settings
   * @param usersettingsService Service for managing user-specific settings
   */
  constructor(private securityService: SecurityService,
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
    this.lang = this.gps.getUserLang();
  }

  /**
   * Angular lifecycle hook that initializes the component after dependency injection.
   */
  ngOnInit(): void {
    this.currencyColumnConfigMC = TransactionSecurityFieldDefinition.getFieldDefinition(this, this.idTenant, true,
      this.transactionSecurityOptionalParam, this.gps);
    this.initialize();
  }

  /**
   * Retrieves the security associated with a given transaction.
   *
   * @param transaction The transaction to get the security for
   * @returns The security object from the transaction summary
   */
  getSecurity(transaction: Transaction): Security {
    return this.securityTransactionSummary.securityPositionSummary.security;
  }

  /**
   * Handles the selection of a tree node and updates the selected transaction.
   *
   * @param event The node selection event containing the selected node data
   */
  onNodeSelect(event): void {
    const securityTransactionPosition: SecurityTransactionPosition = event.node.data;
    this.selectedTransaction = securityTransactionPosition.transaction;
    this.setMenuItemsToActivePanel();
  }

  /**
   * Handles the deselection of a tree node.
   *
   * @param event The node unselection event
   */
  onNodeUnselect(event): void {
    super.onRowUnselect(event);
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_TRANSACTION_MARGIN_BASED;
  }

  /**
   * Angular lifecycle hook that cleans up resources when the component is destroyed.
   */
  ngOnDestroy(): void {
    super.destroy();
  }

  /**
   * Initializes the component by loading transaction data and setting up the tree structure.
   */
  protected initialize(): void {
    BusinessHelper.getSecurityTransactionSummary(this.securityService, this.idSecuritycurrency, this.idsSecurityaccount,
      this.idPortfolio, false).subscribe(result => {
      this.securityTransactionSummary = result;
      this.createTranslatedValueStoreAndFilterField(this.securityTransactionSummary.transactionPositionList);
      this.transactionPositionList = this.securityTransactionSummary.transactionPositionList;
      this.currencyColumnConfigMC.forEach(cc => {
        cc.headerSuffix = this.securityTransactionSummary.securityPositionSummary.mainCurrency;
        this.setFieldHeaderTranslation(cc);
      });
      this.addTreeNodes();
    });
  }

  /**
   * Generates context menu items specific to margin transactions.
   *
   * @param transaction The transaction to generate menu items for
   * @returns Array of menu items for the given transaction
   */
  protected override getMenuItemsOnTransaction(transaction: Transaction): MenuItem[] {
    const localContextMenu: MenuItem[] = [];
    if (transaction && !transaction.connectedIdTransaction) {
      localContextMenu.push({
        label: 'CLOSE_MARGIN_POSITION',
        command: (e) => this.handleClosePosition(transaction, this.selectedNode.data[this.HYPOTHETICAL_TRANSACTION_PROPERTY]),
        disabled: !this.selectedNode.data[this.HYPOTHETICAL_TRANSACTION_PROPERTY]
      });
      localContextMenu.push({
        label: 'MARGIN_FINANCE_COST',
        command: (e) => this.handleFinanceCost(transaction)
      });
      localContextMenu.push({separator: true});
    }
    TranslateHelper.translateMenuItems(localContextMenu, this.translateService);
    return localContextMenu.concat(super.getMenuItemsOnTransaction(transaction));
  }

  /**
   * Determines if a transaction can be deleted based on margin position rules.
   *
   * @param transaction The transaction to check for deletion eligibility
   * @returns True if the transaction can be deleted, false otherwise
   */
  protected override canDeleteTransaction(transaction): boolean {
    return transaction.connectedIdTransaction !== null || !this.selectedNode.data[this.OPEN_HAS_CLOSE_POSITION];
  }

  /**
   * Prepares transaction call parameters for dialogs and operations.
   *
   * @param transactionCallParam The transaction call parameter object to prepare
   */
  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {
  }

  /**
   * Builds the hierarchical tree structure from the flat list of transaction positions.
   */
  private addTreeNodes(): void {
    const rootNodes: TreeNode[] = [];
    let parentNode: TreeNode;
    this.transactionPositionList.forEach((stp: SecurityTransactionPosition) => {
      const leaf = stp.transaction.connectedIdTransaction !== null;
      const treeNode: TreeNode = {
        data: stp,
        children: leaf ? null : [],
        expanded: !leaf,
        leaf,
        parent: parentNode
      };
      if (parentNode && stp.transaction.connectedIdTransaction
        && parentNode.data.transaction.idTransaction === stp.transaction.connectedIdTransaction) {
        // Close position or hypothetical sell/buy
        parentNode.children.push(treeNode);
        if (stp.transaction.transactionType === TransactionType[TransactionType.HYPOTHETICAL_BUY]
          || stp.transaction.transactionType === TransactionType[TransactionType.HYPOTHETICAL_SELL]) {
          parentNode.data[this.HYPOTHETICAL_TRANSACTION_PROPERTY] = stp.transaction;
        } else {
          parentNode.data[this.OPEN_HAS_CLOSE_POSITION] = true;
        }
      } else {
        // Open position
        rootNodes.push(treeNode);
        parentNode = treeNode;
        parentNode.data[this.OPEN_HAS_CLOSE_POSITION] = false;
      }
    });
    this.transactionNodes = rootNodes;
  }

  /**
   * Handles the closing of a margin position by creating a closing transaction.
   *
   * @param openTransaction The original opening transaction
   * @param hypoTransaction The hypothetical transaction containing closing details
   */
  private handleClosePosition(openTransaction: Transaction, hypoTransaction: Transaction): void {
    const closeTransaction = this.getNewTransaction(openTransaction);
    closeTransaction.units = hypoTransaction.units;
    closeTransaction.assetInvestmentValue2 = hypoTransaction.assetInvestmentValue2;
    closeTransaction.transactionType = hypoTransaction.transactionType === TransactionType[TransactionType.HYPOTHETICAL_BUY] ?
      TransactionType[TransactionType.ACCUMULATE] : TransactionType[TransactionType.REDUCE];
    closeTransaction.quotation = hypoTransaction.quotation;
    super.handleEditTransaction(closeTransaction, new CloseMarginPosition(openTransaction.quotation,
      openTransaction.units, hypoTransaction.units, openTransaction.idSecurityaccount, openTransaction.idTransaction));
  }

  /**
   * Handles the creation of a finance cost transaction for a margin position.
   *
   * @param openTransaction The open margin transaction to calculate finance costs for
   */
  private handleFinanceCost(openTransaction: Transaction): void {
    this.transactionService.getEstimatedMarginFinanceCost(openTransaction.idTransaction).subscribe((pMFC: ProposedMarginFinanceCost) => {
      const fcTransaction = this.getNewTransaction(openTransaction);
      fcTransaction.units = pMFC.daysToPay;
      fcTransaction.quotation = pMFC.financeCost && pMFC.daysToPay ? pMFC.financeCost / pMFC.daysToPay : 0;
      fcTransaction.transactionType = TransactionType[TransactionType.FINANCE_COST];
      if (pMFC.untilDate) {
        fcTransaction.transactionTime = pMFC.untilDate;
      }
      this.handleEditTransaction(fcTransaction);
    });
  }

  /**
   * Creates a new transaction based on an existing open transaction for closing operations.
   *
   * @param openTransaction The original open transaction to base the new transaction on
   * @returns A new transaction object with properties copied from the open transaction
   */
  private getNewTransaction(openTransaction: Transaction): Transaction {
    const newTransaction = new Transaction();
    newTransaction.connectedIdTransaction = openTransaction.idTransaction;
    newTransaction.idSecurityaccount = openTransaction.idSecurityaccount;
    newTransaction.idCashaccount = openTransaction.cashaccount.idSecuritycashAccount;
    newTransaction.cashaccount = openTransaction.cashaccount;
    newTransaction.transactionTime = new Date();
    return newTransaction;
  }
}
