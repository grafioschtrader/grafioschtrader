import {Component, Input, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {TransactionCostPosition} from '../../entities/view/transactioncost/transaction.cost.position';
import {TransactionCostGrandSummary} from '../../entities/view/transactioncost/transaction.cost.grand.summary';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {TransactionContextMenu} from '../../transaction/component/transaction.context.menu';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TransactionService} from '../../transaction/service/transaction.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {Transaction} from '../../entities/transaction';
import {Security} from '../../entities/security';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {Table} from 'primeng/table';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {TranslateValue} from '../../shared/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';

/**
 * Shows Transaction of a security account, that means there are only transaction with security involved.
 * Editing of security transaction is supported.
 * It is used as nested table.
 */
@Component({
  selector: 'tenant-transaction-cost-extended',
  template: `
    <div #cmDiv class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <div class="datatable nestedtable">
        <p-table [columns]="fields" [value]="transactionCostPositions" selectionMode="single"
                 [(selection)]="baseSelectedRow" #dataTable [first]="firstRowIndex" (onRowSelect)="onRowSelect($event)"
                 (onRowUnselect)="onRowUnselect($event)" (onPage)="onPage($event)"
                 responsiveLayout="scroll"
                 sortMode="multiple" [multiSortMeta]="multiSortMeta"
                 dataKey="transaction.idTransaction" [paginator]="true" [rows]="20"
                 styleClass="sticky-table p-datatable-striped p-datatable-gridlines">

          <ng-template pTemplate="header" let-fields>
            <tr>
              <th *ngFor="let field of fields" [pSortableColumn]="field.field" [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                {{field.headerTranslated}}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            </tr>
          </ng-template>

          <ng-template pTemplate="body" let-el let-columns="fields">
            <tr [pSelectableRow]="el">
              <ng-container *ngFor="let field of fields">
                <td *ngIf="field.visible" [style.max-width.px]="field.width"
                    [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                    [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric)? 'text-right': ''">
                  <span [pTooltip]="getValueByPath(el, field)"
                        tooltipPosition="top">{{getValueByPath(el, field)}}</span>
                </td>
              </ng-container>
            </tr>
          </ng-template>
        </p-table>
        <p-contextMenu #cm [target]="cmDiv" [model]="contextMenuItems" appendTo="body"></p-contextMenu>
      </div>
    </div>
    <transaction-security-edit *ngIf="visibleSecurityTransactionDialog"
                               [transactionCallParam]="transactionCallParam"
                               [visibleSecurityTransactionDialog]="visibleSecurityTransactionDialog"
                               (closeDialog)="handleCloseTransactionDialog($event)">
    </transaction-security-edit>
  `
})
export class TenantTransactionCostExtendedComponent extends TransactionContextMenu implements OnInit, OnDestroy {
  @Input() transactionCostPositions: TransactionCostPosition[];
  @Input() transactionCostGrandSummary: TransactionCostGrandSummary;
  @Input() baseSelectedRow: TransactionCostPosition;
  @Input() firstRowIndex: number;
  @ViewChild('dataTable', {static: true}) dataTable: Table;

  private selectedTransactionCostPosition: TransactionCostPosition;

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
    this.addColumn(DataType.DateString, 'transaction.transactionTime', 'DATE', true, false);
    this.addColumn(DataType.String, 'transaction.security.name', 'NAME', true, false, {width: 200});

    this.addColumn(DataType.String, 'transaction.security.stockexchange.name', AppSettings.STOCKEXCHANGE.toUpperCase(), true, false);
    this.addColumn(DataType.String, 'transaction.transactionType', 'TRANSACTION_TYPE', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'transaction.cashaccount.currency', 'CURRENCY_CASHACCOUNT', true, false);
    this.addColumn(DataType.String, 'transaction.security.currency', 'CURRENCY_SECURITY', true, false);
    this.addColumn(DataType.Numeric, 'transaction.transactionCost', 'TRANSACTION_COST', true, false);

    this.addColumn(DataType.Numeric, 'taxCostMC', 'TAX_COST', true, false,
      {headerSuffix: this.transactionCostGrandSummary.mainCurrency});
    this.addColumn(DataType.Numeric, 'basePriceForTransactionCostMC', 'NETTO_VALUE_TRANSACTION', true, false,
      {headerSuffix: this.transactionCostGrandSummary.mainCurrency});


    this.addColumn(DataType.Numeric, 'transaction.currencyExRate', 'EXCHANGE_RATE', true, false);
    this.addColumn(DataType.Numeric, 'transactionCostMC', 'TRANSACTION_COST', true, false,
      {headerSuffix: this.transactionCostGrandSummary.mainCurrency});
    this.multiSortMeta.push({field: 'transaction.transactionTime', order: -1});
    this.prepareTableAndTranslate();
    this.createTranslatedValueStoreAndFilterField(this.transactionCostPositions);
    if (this.baseSelectedRow) {
      this.selectedTransaction = this.baseSelectedRow.transaction;
    }
  }

  getSecurity(transaction: Transaction): Security {
    return this.selectedTransactionCostPosition.transaction.security;
  }

  onRowSelect(event): void {
    this.selectedTransactionCostPosition = event.data;
    this.selectedTransaction = this.selectedTransactionCostPosition.transaction;
    this.setMenuItemsToActivePanel();
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  protected getMenuItemsOnTransaction(transaction: Transaction): MenuItem[] {
    const localContextMenu: MenuItem[] = [
      {separator: true},

      {label: 'EXPORT_CSV', command: (e) => this.dataTable.exportCSV()}
    ];
    TranslateHelper.translateMenuItems(localContextMenu, this.translateService);
    return super.getMenuItemsOnTransaction(transaction).concat(localContextMenu);
  }

  protected initialize(): void {
  }

  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {
  }

}
