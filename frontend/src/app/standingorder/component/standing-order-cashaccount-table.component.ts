import { Component, OnInit } from '@angular/core';

import { TranslateModule } from '@ngx-translate/core';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { StandingOrderCashaccountEditComponent } from './standing-order-cashaccount-edit.component';
import { StandingOrderFailureTableComponent } from './standing-order-failure-table.component';
import { StandingOrderTransactionTableComponent } from './standing-order-transaction-table.component';
import { StandingOrderTableBase } from './standing-order-table-base';
import { TransactionType } from '../../shared/types/transaction.type';

/**
 * Table component displaying cashaccount standing orders (dtype='C') for the current tenant.
 * Shown as a tab in TenantTabMenuComponent.
 */
@Component({
  selector: 'standing-order-cashaccount-table',
  template: `
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <configurable-table
        [data]="standingOrders()"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        [customSortFn]="customSorter"
        [multiSortMeta]="multiSortMeta"
        [dataKey]="'idStandingOrder'"
        [(selection)]="selectedEntity"
        (rowSelect)="onRowSelect($event)"
        (rowUnselect)="onRowUnselect($event)"
        [contextMenuItems]="contextMenuItems()"
        [contextMenuAppendTo]="'body'"
        [showContextMenu]="isActivated()"
        [expandable]="true"
        [canExpandFn]="canExpandRow"
        [expandedRowTemplate]="detailExpansion"
        (rowExpand)="onRowExpand($event)"
        [valueGetterFn]="valueGetter"
        [baseLocale]="baseLocale" />
    </div>

    <ng-template #detailExpansion let-so>
      @if ((so.transactionCount ?? 0) > 0) {
        <standing-order-transaction-table [standingOrder]="so" (dateChanged)="onTransactionChanged($event)" />
      }
      @if ((so.failureCount ?? 0) > 0) {
        <standing-order-failure-table [failures]="getFailuresForOrder(so)" />
      }
    </ng-template>

    @if (visibleEditDialog()) {
      <standing-order-cashaccount-edit
        [visibleDialog]="visibleEditDialog()"
        [callParam]="callParam"
        (closeDialog)="onDialogClose($event)" />
    }
  `,
  imports: [
    TranslateModule,
    ConfigurableTableComponent,
    StandingOrderCashaccountEditComponent,
    StandingOrderFailureTableComponent,
    StandingOrderTransactionTableComponent
  ]
})
export class StandingOrderCashaccountTableComponent extends StandingOrderTableBase implements OnInit {
  constructor() {
    super();
  }

  ngOnInit(): void {
    this.initializeData();
  }

  protected override addSubtypeColumns(): void {
    this.addColumn(DataType.String, 'cashaccount.name', 'CASHACCOUNT', true, false);
    this.addColumnFeqH(DataType.Numeric, 'cashaccountAmount', true, false);
    // Only relevant for orders billed in a foreign currency, so hidden until the user enables them.
    this.addColumnFeqH(DataType.String, 'amountCurrency', false, true);
    this.addColumnFeqH(DataType.String, 'cashaccountAmountFormula', false, true);
    this.addColumnFeqH(DataType.Numeric, 'transactionCost', true, false);
  }

  protected override getDtype(): string {
    return 'C';
  }

  protected override getAllowedTransactionTypes(): TransactionType[] {
    return [
      TransactionType.WITHDRAWAL,
      TransactionType.DEPOSIT,
      TransactionType.INTEREST_CASHACCOUNT,
      TransactionType.FEE
    ];
  }
}
