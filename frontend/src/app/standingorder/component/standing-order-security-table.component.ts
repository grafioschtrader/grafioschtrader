import { Component, OnInit } from '@angular/core';

import { TranslateModule } from '@ngx-translate/core';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { StandingOrderSecurityEditComponent } from './standing-order-security-edit.component';
import { StandingOrderFailureTableComponent } from './standing-order-failure-table.component';
import { StandingOrderTransactionTableComponent } from './standing-order-transaction-table.component';
import { StandingOrderTableBase } from './standing-order-table-base';
import { TransactionType } from '../../shared/types/transaction.type';

/**
 * Table component displaying security standing orders (dtype='S') for the current tenant.
 * Shown as a tab in TenantTabMenuComponent.
 */
@Component({
  selector: 'standing-order-security-table',
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
      <standing-order-security-edit
        [visibleDialog]="visibleEditDialog()"
        [callParam]="callParam"
        (closeDialog)="onDialogClose($event)" />
    }
  `,
  imports: [
    TranslateModule,
    ConfigurableTableComponent,
    StandingOrderSecurityEditComponent,
    StandingOrderFailureTableComponent,
    StandingOrderTransactionTableComponent
  ]
})
export class StandingOrderSecurityTableComponent extends StandingOrderTableBase implements OnInit {
  constructor() {
    super();
  }

  ngOnInit(): void {
    this.initializeData();
  }

  protected override addSubtypeColumns(): void {
    this.addColumn(DataType.String, 'security.name', 'SECURITY', true, false);
    this.addColumn(DataType.String, 'cashaccount.name', 'CASHACCOUNT', true, false);
    this.addColumnFeqH(DataType.Numeric, 'units', true, false);
    this.addColumnFeqH(DataType.Numeric, 'investAmount', true, false);
  }

  protected override getDtype(): string {
    return 'S';
  }

  protected override getAllowedTransactionTypes(): TransactionType[] {
    return [TransactionType.ACCUMULATE, TransactionType.REDUCE];
  }
}
