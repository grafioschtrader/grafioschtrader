import { Component, EventEmitter, Injector, input, computed, effect, signal, untracked, Output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { FilterService, TreeNode } from '@openng/optimus-ui/api';
import { TreeTableModule } from '@openng/optimus-ui/treetable';
import { TooltipModule } from '@openng/optimus-ui/tooltip';

import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { Transaction } from '../../entities/transaction';
import { AppSettings } from '../../shared/app.settings';
import { buildTransactionReceiptTree, getSelectedTransactions } from './transaction-receipt-tree';

/**
 * Multi-select table of a security's transactions, used inside the transaction receipt dialog. The user checks the
 * transactions a receipt PDF should be created for; the current selection is emitted to the hosting dialog.
 */
@Component({
  selector: 'transaction-receipt-table',
  template: `
    @if (marginBased()) {
      <p-treeTable
        [value]="transactionNodes()"
        [columns]="fields"
        dataKey="idTransaction"
        selectionMode="checkbox"
        [(selection)]="selectedNodes"
        (selectionChange)="onTreeSelectionChange($event)"
        [scrollable]="true"
        scrollHeight="60vh"
        showGridlines="true">
        <ng-template #header let-fields>
          <tr>
            <th style="width: 2.25em">
              <p-treeTableHeaderCheckbox />
            </th>
            @for (field of fields; track field.field) {
              <th
                [style.width.px]="field.width"
                [pTooltip]="field.headerTooltipTranslated"
                class="word-break-header"
                [attr.lang]="baseLocale.language">
                {{ field.headerTranslated }}
              </th>
            }
          </tr>
        </ng-template>
        <ng-template #body let-rowNode let-rowData="rowData" let-columns="columns">
          <tr>
            <td style="width: 2.25em">
              <p-treeTableCheckbox [value]="rowNode" />
            </td>
            @for (field of columns; track field.field; let i = $index) {
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
                <span>{{ getValueByPath(rowData, field) }}</span>
              </td>
            }
          </tr>
        </ng-template>
      </p-treeTable>
    } @else {
      <configurable-table
        [data]="transactions()"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        dataKey="idTransaction"
        selectionMode="multiple"
        [selection]="selected()"
        (selectionChange)="onSelectionChange($event)"
        [customSortFn]="customSorter"
        [multiSortMeta]="multiSortMeta"
        [valueGetterFn]="valueGetter"
        [baseLocale]="baseLocale"
        [scrollable]="true"
        scrollHeight="60vh" />
    }
  `,
  imports: [ConfigurableTableComponent, TreeTableModule, TooltipModule]
})
export class TransactionReceiptTableComponent extends TableConfigBase {
  /** Transactions of the security shown for selection. */
  readonly transactions = input<Transaction[]>([]);

  /** Whether transactions must be grouped into opening and connected margin-position branches. */
  readonly marginBased = input(false);

  /** Emits the currently checked transactions whenever the selection changes. */
  @Output() selectedChange = new EventEmitter<Transaction[]>();

  readonly selected = signal<Transaction[]>([]);
  readonly selectedNodes = signal<TreeNode[]>([]);
  readonly transactionNodes = computed(() =>
    this.marginBased() ? buildTransactionReceiptTree(this.transactions()) : []
  );

  constructor(
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.addColumn(DataType.DateTimeString, 'transactionTime', 'DATE', true, false, { width: 120 });
    this.addColumnFeqH(DataType.String, 'transactionType', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.String, 'cashaccount.name', AppSettings.CASHACCOUNT.toUpperCase(), true, false);
    this.addColumn(DataType.Numeric, 'units', 'QUANTITY', true, false);
    this.addColumn(DataType.Numeric, 'quotation', 'QUOTATION_DIV', true, false, {
      maxFractionDigits: gps.getMaxFractionDigits()
    });
    this.addColumnFeqH(DataType.Numeric, 'cashaccountAmount', true, false, {
      currencyPrecisionField: 'cashaccount.currency'
    });
    this.multiSortMeta.push({ field: 'transactionTime', order: 1 });
    this.prepareTableAndTranslate();
    effect(() => {
      const transactions = this.transactions();
      this.marginBased();
      untracked(() => {
        this.selected.set([]);
        this.selectedNodes.set([]);
        this.selectedChange.emit([]);
        this.createTranslatedValueStore(transactions);
      });
    });
  }

  onSelectionChange(selection: Transaction | Transaction[] | null): void {
    this.selected.set(Array.isArray(selection) ? selection : selection ? [selection] : []);
    this.selectedChange.emit(this.selected());
  }

  onTreeSelectionChange(selection: TreeNode | TreeNode[] | null): void {
    this.selectedNodes.set(Array.isArray(selection) ? selection : selection ? [selection] : []);
    this.selected.set(getSelectedTransactions(this.transactions(), this.selectedNodes()));
    this.selectedChange.emit(this.selected());
  }
}
