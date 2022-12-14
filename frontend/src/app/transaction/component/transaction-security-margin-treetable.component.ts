import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {ConfirmationService, FilterService, MenuItem, TreeNode} from 'primeng/api';
import {TransactionContextMenu} from './transaction.context.menu';
import {ColumnConfig} from '../../shared/datashowbase/column.config';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TransactionService} from '../service/transaction.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {Transaction} from '../../entities/transaction';
import {Security} from '../../entities/security';
import {SecurityTransactionSummary} from '../../entities/view/security.transaction.summary';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {SecurityTransactionPosition} from '../../entities/view/security.transaction.position';
import {CloseMarginPosition, TransactionCallParam} from './transaction.call.parm';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {TransactionType} from '../../shared/types/transaction.type';
import {ProposedMarginFinanceCost} from '../model/proposed.margin.finance.cost';
import {TransactionSecurityFieldDefinition} from './transaction.security.field.definition';
import {TransactionSecurityOptionalParam} from '../model/transaction.security.optional.param';
import {HelpIds} from '../../shared/help/help.ids';

/**
 * It shows margin transactions as a tree. It supports editing existing transaction and close an open position.
 */
@Component({
  selector: 'transaction-security-margin-treetable',
  template: `
    <div #cmDiv class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <div class="datatable nestedtable">
        <p-treeTable [value]="transactionNodes" [columns]="fields" [(selection)]="selectedNode"
                     selectionMode="single" [scrollable]="true" dataKey="transaction.idTransaction"
                     (onNodeSelect)="onNodeSelect($event)" (onNodeUnselect)="onNodeUnselect($event)"
                     [paginator]="true" [rows]="20" styleClass="p-treetable-gridlines">
          <ng-template pTemplate="header" let-fields>
            <tr>
              <th *ngFor="let field of fields" [style.width.px]="field.width"
                  [pTooltip]="field.headerTooltipTranslated">
                {{field.headerTranslated}}
              </th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="fields">
            <tr [ttSelectableRow]="rowNode" [ttSelectableRowDisabled]="rowData.transaction.idTransaction < 0"
                [ngClass]="{'rowgroup-total': rowData.transaction.idTransaction < 0}">
              <td *ngFor="let field of fields; let i = index"
                  [ngClass]="{'text-right': (field.dataType===DataType.NumericInteger  || field.dataType===DataType.Numeric
              || field.dataType===DataType.DateTimeNumeric)}" [style.width.px]="field.width">
                <p-treeTableToggler [rowNode]="rowNode" *ngIf="i === 0"></p-treeTableToggler>
                <ng-container [ngSwitch]="field.templateName">
                  <ng-container *ngSwitchCase="'greenRed'">
                <span [style.color]='isValueByPathMinus(rowData, field)? "red": "inherit"'>
                  {{getValueByPath(rowData, field)}}
                </span>
                  </ng-container>
                  <ng-container *ngSwitchDefault>
                    <span>{{getValueByPath(rowData, field)}}</span>
                  </ng-container>
                </ng-container>
              </td>
            </tr>
          </ng-template>
        </p-treeTable>
        <p-contextMenu *ngIf="contextMenuItems && contextMenuItems.length >0" #cm
                       [target]="cmDiv" [model]="contextMenuItems" appendTo="body">
        </p-contextMenu>
      </div>
      <transaction-security-edit *ngIf="visibleSecurityTransactionDialog"
                                 [transactionCallParam]="transactionCallParam"
                                 [visibleSecurityTransactionDialog]="visibleSecurityTransactionDialog"
                                 (closeDialog)="handleCloseTransactionDialog($event)">
      </transaction-security-edit>

    </div>
  `
})
export class TransactionSecurityMarginTreetableComponent extends TransactionContextMenu implements OnInit, OnDestroy {
  @Input() idsSecurityaccount: number[];
  @Input() idSecuritycurrency: number;
  @Input() idPortfolio: number;
  @Input() idTenant: number;
  @Input() transactionSecurityOptionalParam: TransactionSecurityOptionalParam[];

  readonly HYPOTHETICAL_TRANSACTION_PROPERTY = 'hypotheticalTransactionProperty';
  readonly OPEN_HAS_CLOSE_POSITION = 'closeHasOpenPosition';

  transactionNodes: TreeNode[] = [];

  selectedNode: TreeNode;
  currencyColumnConfigMC: ColumnConfig[] = [];
  // Data to be shown
  securityTransactionSummary: SecurityTransactionSummary = new SecurityTransactionSummary(null, null);
  transactionPositionList: SecurityTransactionPosition[] = [];

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
  }

  ngOnInit(): void {
    this.currencyColumnConfigMC = TransactionSecurityFieldDefinition.getFieldDefinition(this, this.idTenant, true,
      this.transactionSecurityOptionalParam);
    this.initialize();
  }

  getSecurity(transaction: Transaction): Security {
    return this.securityTransactionSummary.securityPositionSummary.security;
  }

  onNodeSelect(event): void {
    const securityTransactionPosition: SecurityTransactionPosition = event.node.data;
    this.selectedTransaction = securityTransactionPosition.transaction;
    this.setMenuItemsToActivePanel();
  }

  onNodeUnselect(event): void {
    super.onRowUnselect(event);
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_TRANSACTION_MARGIN_BASED;
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  protected initialize(): void {
    BusinessHelper.setSecurityTransactionSummary(this.securityService, this.idSecuritycurrency, this.idsSecurityaccount,
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

  protected getMenuItemsOnTransaction(transaction: Transaction): MenuItem[] {
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

  protected canDeleteTransaction(transaction): boolean {
    return transaction.connectedIdTransaction !== null || !this.selectedNode.data[this.OPEN_HAS_CLOSE_POSITION];
  }

  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {
  }

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
