import { Component, OnDestroy, OnInit, signal, computed, input, inject } from '@angular/core';
import { MenuItem, TreeNode } from '@openng/optimus-ui/api';
import { TransactionContextMenu } from './transaction.context.menu';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { PageFirstRowSelectedRow } from '../../shared/service/parent.child.register.service';
import { Transaction } from '../../entities/transaction';
import { Security } from '../../entities/security';
import { SecurityTransactionSummary } from '../../entities/view/security.transaction.summary';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { SecurityTransactionPosition } from '../../entities/view/security.transaction.position';
import { CloseMarginPosition, TransactionCallParam } from './transaction.call.parm';
import { TransactionType } from '../../shared/types/transaction.type';
import { ProposedMarginFinanceCost } from '../model/proposed.margin.finance.cost';
import { TransactionSecurityFieldDefinition } from './transaction.security.field.definition';
import { TransactionSecurityOptionalParam } from '../model/transaction.security.optional.param';
import { HelpIds } from '../../lib/help/help.ids';

import { TreeTableModule } from '@openng/optimus-ui/treetable';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { TransactionSecurityEditComponent } from './transaction-security-edit.component';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

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
    <div
      #cmDiv
      class="data-container"
      (click)="onComponentClick($event)"
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <div class="datatable nestedtable">
        <p-treeTable
          [value]="transactionNodes()"
          [columns]="fields"
          [(selection)]="selectedNode"
          selectionMode="single"
          [scrollable]="false"
          dataKey="transaction.idTransaction"
          (onNodeSelect)="onNodeSelect($event)"
          (onNodeUnselect)="onNodeUnselect($event)"
          [paginator]="true"
          [rows]="ROWS_PER_PAGE"
          [first]="firstRowIndexOnPage"
          (onPage)="onPage($event)"
          showGridlines="true">
          <ng-template #header let-fields>
            <tr>
              @for (field of fields; track field.field) {
                <th
                  [style.width.px]="field.width"
                  [pTooltip]="field.headerTooltipTranslated"
                  class="word-break-header"
                  [attr.lang]="lang">
                  {{ field.headerTranslated }}
                </th>
              }
            </tr>
          </ng-template>
          <ng-template #body let-rowNode let-rowData="rowData" let-columns="fields">
            <tr
              [ttSelectableRow]="rowNode"
              [ttSelectableRowDisabled]="rowData.transaction.idTransaction < 0"
              [class.rowgroup-total]="rowData.transaction.idTransaction < 0">
              @for (field of fields; track field.field; let i = $index) {
                <td
                  [class.text-end]="
                    field.dataType === DataType.NumericInteger ||
                    field.dataType === DataType.Numeric ||
                    field.dataType === DataType.DateTimeNumeric
                  "
                  [style.width.px]="field.width">
                  @if (i === 0) {
                    <p-treeTableToggler [rowNode]="rowNode" />
                  }
                  @switch (field.templateName) {
                    @case ('greenRed') {
                      <span [style.color]="isValueByPathMinus(rowData, field) ? 'red' : 'inherit'">
                        {{ getValueByPath(rowData, field) }}
                      </span>
                    }
                    @default {
                      <span>{{ getValueByPath(rowData, field) }}</span>
                    }
                  }
                </td>
              }
            </tr>
          </ng-template>
        </p-treeTable>
        <p-contextMenu #cm [target]="cmDiv" [model]="contextMenuItems()" appendTo="body" />
      </div>
      @if (visibleSecurityTransactionDialog()) {
        <transaction-security-edit
          [transactionCallParam]="transactionCallParam"
          [visibleSecurityTransactionDialog]="visibleSecurityTransactionDialog()"
          (closeDialog)="handleCloseTransactionDialog($event)" />
      }
    </div>
  `,
  imports: [TreeTableModule, TooltipModule, ContextMenuModule, TransactionSecurityEditComponent]
})
export class TransactionSecurityMarginTreetableComponent extends TransactionContextMenu implements OnInit, OnDestroy {
  private securityService: SecurityService = inject(SecurityService);

  /** Array of security account IDs to filter transactions */
  readonly idsSecurityaccount = input<number[]>(undefined);

  /** ID of the specific security currency to display transactions for */
  readonly idSecuritycurrency = input<number>(undefined);

  /** Portfolio ID for filtering transactions by portfolio */
  readonly idPortfolio = input<number>(undefined);

  /** Tenant ID for filtering transactions by tenant */
  readonly idTenant = input<number>(undefined);

  /** Optional parameters for configuring transaction security display options */
  readonly transactionSecurityOptionalParam = input<TransactionSecurityOptionalParam[]>(undefined);

  /** Optional cutoff date (yyyy-MM-dd) to limit transactions */
  readonly untilDate = input<string>();

  /** Property key used to store hypothetical transaction data in tree node data */
  readonly HYPOTHETICAL_TRANSACTION_PROPERTY = 'hypotheticalTransactionProperty';

  /** Property key used to indicate if an open position has a corresponding close position */
  readonly OPEN_HAS_CLOSE_POSITION = 'closeHasOpenPosition';

  /** Array of tree nodes representing the hierarchical transaction structure */
  readonly transactionNodes = signal<TreeNode[]>([]);

  /** Currently selected node in the tree table */
  readonly selectedNode = signal<TreeNode | null>(null);

  /** Column configurations for main currency display */
  currencyColumnConfigMC: ColumnConfig[] = [];

  /** Summary data containing security information and transaction positions */
  readonly securityTransactionSummary = signal(new SecurityTransactionSummary(null, null));

  /** List of security transaction positions to be displayed in the tree */
  readonly transactionPositionList = computed(() => this.securityTransactionSummary().transactionPositionList ?? []);

  /** Current user language setting for display purposes */
  lang: string;

  /** Root nodes per paginator page; matches the [rows] binding on the tree-table. */
  readonly ROWS_PER_PAGE = 20;

  /**
   * Creates a new instance of TransactionSecurityMarginTreetableComponent.
   *
   */
  constructor() {
    super();
    this.lang = this.gps.getUserLang();
  }

  /**
   * Angular lifecycle hook that initializes the component after dependency injection.
   */
  ngOnInit(): void {
    this.currencyColumnConfigMC = TransactionSecurityFieldDefinition.getFieldDefinition(
      this,
      this.idTenant(),
      true,
      this.transactionSecurityOptionalParam(),
      this.gps
    );
    this.initialize();
  }

  /**
   * Retrieves the security associated with a given transaction.
   *
   * @param transaction The transaction to get the security for
   * @returns The security object from the transaction summary
   */
  getSecurity(transaction: Transaction): Security {
    return this.securityTransactionSummary().securityPositionSummary.security;
  }

  /**
   * Handles the selection of a tree node and updates the selected transaction.
   *
   * @param event The node selection event containing the selected node data
   */
  onNodeSelect(event: { node?: TreeNode<SecurityTransactionPosition> }): void {
    const securityTransactionPosition: SecurityTransactionPosition = event.node.data;
    this.selectedTransaction.set(securityTransactionPosition.transaction);
    this.setMenuItemsToActivePanel();
  }

  /**
   * Handles the deselection of a tree node.
   *
   * @param event The node unselection event
   */
  onNodeUnselect(event: unknown): void {
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
    this.dataSubscription?.unsubscribe();
    this.dataSubscription = BusinessHelper.getSecurityTransactionSummary(
      this.securityService,
      this.idSecuritycurrency(),
      this.idsSecurityaccount(),
      this.idPortfolio(),
      false,
      this.untilDate()
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.securityTransactionSummary.set(result);
        this.createTranslatedValueStoreAndFilterField(this.securityTransactionSummary().transactionPositionList);
        this.currencyColumnConfigMC.forEach((cc) => {
          cc.headerSuffix = this.securityTransactionSummary().securityPositionSummary.mainCurrency;
          cc.fixedCurrency = this.securityTransactionSummary().securityPositionSummary.mainCurrency;
          this.setFieldHeaderTranslation(cc);
        });
        TransactionSecurityFieldDefinition.applySecurityCurrency(
          this.fields,
          this.securityTransactionSummary().securityPositionSummary.security.currency
        );
        // Build the tree locally, then set firstRowIndexOnPage BEFORE transactionNodes so
        // Optimus's TreeTable.updateSerializedValue (triggered by [value]) reads the
        // correct `first` and slices the right page in one CD cycle. setTimeout would land
        // first in a later cycle and only update the paginator UI, not the rendered rows.
        const rootNodes = this.buildTreeNodes();
        this.firstRowIndexOnPage = this.computeRestoredFirstIndex(rootNodes);
        this.transactionNodes.set(rootNodes);
      });
  }

  /**
   * Generates context menu items specific to margin transactions.
   *
   * @param transaction The transaction to generate menu items for
   * @returns Array of menu items for the given transaction
   */
  protected override getMenuItemsOnTransaction(transaction: Transaction | null): MenuItem[] {
    const hypothetical = this.selectedNode()?.data[this.HYPOTHETICAL_TRANSACTION_PROPERTY];
    const localContextMenu: MenuItem[] = [];
    if (transaction && !transaction.connectedIdTransaction) {
      localContextMenu.push({
        label: 'CLOSE_MARGIN_POSITION',
        command: (e) => this.handleClosePosition(transaction, hypothetical),
        disabled: !hypothetical
      });
      localContextMenu.push({
        label: 'MARGIN_FINANCE_COST',
        command: (e) => this.handleFinanceCost(transaction)
      });
      localContextMenu.push({ separator: true });
    }
    return localContextMenu.concat(super.getMenuItemsOnTransaction(transaction));
  }

  /**
   * Determines if a transaction can be deleted based on margin position rules.
   *
   * @param transaction The transaction to check for deletion eligibility
   * @returns True if the transaction can be deleted, false otherwise
   */
  protected override canDeleteTransaction(transaction: Transaction): boolean {
    const selected = this.selectedNode();
    return !!selected && (transaction.connectedIdTransaction !== null || !selected.data[this.OPEN_HAS_CLOSE_POSITION]);
  }

  /**
   * Prepares transaction call parameters for dialogs and operations.
   *
   * @param transactionCallParam The transaction call parameter object to prepare
   */
  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {}

  /**
   * Saves the current page and the just-saved transaction id before the parent refreshes
   * (which destroys this component instance). The new instance reads this state in
   * applySavedPagePosition() to navigate to the page containing the saved transaction.
   */
  override handleCloseTransactionDialog(processedActionData: ProcessedActionData): void {
    if (processedActionData.action !== ProcessedAction.NO_CHANGE && processedActionData.data) {
      const tx: Transaction = Array.isArray(processedActionData.data)
        ? processedActionData.data[0]
        : processedActionData.data;
      if (tx?.idTransaction != null) {
        this.parentChildRegisterService.saveRowPosition(
          this.idSecuritycurrency(),
          new PageFirstRowSelectedRow(this.firstRowIndexOnPage, tx.idTransaction)
        );
      }
    }
    super.handleCloseTransactionDialog(processedActionData);
  }

  /**
   * Preserves only the current page on delete — the deleted id won't exist after refresh,
   * so applySavedPagePosition() falls back to topPageRow.
   */
  override afterDelete(transaction: Transaction): void {
    this.parentChildRegisterService.saveRowPosition(
      this.idSecuritycurrency(),
      new PageFirstRowSelectedRow(this.firstRowIndexOnPage, null)
    );
    super.afterDelete(transaction);
  }

  /**
   * Computes the page index (firstRowIndexOnPage value) that the paginator should land on
   * after a refresh. Prefers the page of the most-recently saved transaction id; falls
   * back to the previously saved page (e.g. on delete or when no target id was stored).
   */
  private computeRestoredFirstIndex(rootNodes: TreeNode[]): number {
    const saved = this.parentChildRegisterService.getRowPosition(this.idSecuritycurrency());
    const targetId = saved?.selectedRow as number | null;
    let rootIndex = -1;
    if (targetId != null) {
      rootIndex = this.findRootIndexForTransaction(rootNodes, targetId);
    }
    return rootIndex >= 0 ? Math.floor(rootIndex / this.ROWS_PER_PAGE) * this.ROWS_PER_PAGE : (saved?.topPageRow ?? 0);
  }

  /**
   * Finds the root index whose data — or any direct child's data — matches the given
   * transaction id. Children include both close positions and hypothetical buy/sell rows.
   */
  private findRootIndexForTransaction(rootNodes: TreeNode[], idTransaction: number): number {
    for (let i = 0; i < rootNodes.length; i++) {
      const root = rootNodes[i];
      if (root.data?.transaction?.idTransaction === idTransaction) {
        return i;
      }
      if (root.children?.some((c) => c.data?.transaction?.idTransaction === idTransaction)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Builds the hierarchical tree structure from the flat list of transaction positions and
   * returns the new root array. Caller assigns the result to transactionNodes.
   */
  private buildTreeNodes(): TreeNode[] {
    const rootNodes: TreeNode[] = [];
    let parentNode: TreeNode;
    this.transactionPositionList().forEach((stp: SecurityTransactionPosition) => {
      const leaf = stp.transaction.connectedIdTransaction !== null;
      const treeNode: TreeNode = {
        data: { ...stp },
        children: leaf ? null : [],
        expanded: !leaf,
        leaf,
        parent: parentNode
      };
      if (
        parentNode &&
        stp.transaction.connectedIdTransaction &&
        parentNode.data.transaction.idTransaction === stp.transaction.connectedIdTransaction
      ) {
        // Close position or hypothetical sell/buy
        parentNode.children.push(treeNode);
        if (
          stp.transaction.transactionType === TransactionType[TransactionType.HYPOTHETICAL_BUY] ||
          stp.transaction.transactionType === TransactionType[TransactionType.HYPOTHETICAL_SELL]
        ) {
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
    return rootNodes;
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
    closeTransaction.transactionType =
      hypoTransaction.transactionType === TransactionType[TransactionType.HYPOTHETICAL_BUY]
        ? TransactionType[TransactionType.ACCUMULATE]
        : TransactionType[TransactionType.REDUCE];
    closeTransaction.quotation = hypoTransaction.quotation;
    super.handleEditTransaction(
      closeTransaction,
      new CloseMarginPosition(
        openTransaction.quotation,
        openTransaction.units,
        hypoTransaction.units,
        openTransaction.idSecurityaccount,
        openTransaction.idTransaction
      )
    );
  }

  /**
   * Handles the creation of a finance cost transaction for a margin position.
   *
   * @param openTransaction The open margin transaction to calculate finance costs for
   */
  private handleFinanceCost(openTransaction: Transaction): void {
    this.transactionService
      .getEstimatedMarginFinanceCost(openTransaction.idTransaction)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((pMFC: ProposedMarginFinanceCost) => {
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
