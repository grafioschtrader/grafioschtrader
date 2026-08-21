import {Component, EventEmitter, Injector, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {FilterService, TreeNode} from '@openng/optimus-ui/api';
import {TreeTableModule} from '@openng/optimus-ui/treetable';
import {TooltipModule} from '@openng/optimus-ui/tooltip';

import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {Transaction} from '../../entities/transaction';
import {AppSettings} from '../../shared/app.settings';
import {buildTransactionReceiptTree, getSelectedTransactions} from './transaction-receipt-tree';

/**
 * Multi-select table of a security's transactions, used inside the transaction receipt dialog. The user checks the
 * transactions a receipt PDF should be created for; the current selection is emitted to the hosting dialog.
 */
@Component({
  selector: 'transaction-receipt-table',
  template: `
    @if (marginBased) {
      <p-treeTable [value]="transactionNodes" [columns]="fields" dataKey="idTransaction"
                   selectionMode="checkbox" [(selection)]="selectedNodes"
                   (selectionChange)="onTreeSelectionChange($event)"
                   [scrollable]="true" scrollHeight="60vh" showGridlines="true">
        <ng-template #header let-fields>
          <tr>
            <th style="width: 2.25em">
              <p-treeTableHeaderCheckbox></p-treeTableHeaderCheckbox>
            </th>
            @for (field of fields; track field.field) {
              <th [style.width.px]="field.width" [pTooltip]="field.headerTooltipTranslated"
                  class="word-break-header" [attr.lang]="baseLocale.language">
                {{field.headerTranslated}}
              </th>
            }
          </tr>
        </ng-template>
        <ng-template #body let-rowNode let-rowData="rowData" let-columns="columns">
          <tr>
            <td style="width: 2.25em">
              <p-treeTableCheckbox [value]="rowNode"></p-treeTableCheckbox>
            </td>
            @for (field of columns; track field.field; let i = $index) {
              <td [class.text-end]="field.dataType === DataType.NumericInteger
                  || field.dataType === DataType.Numeric || field.dataType === DataType.DateTimeNumeric"
                  [style.width.px]="field.width">
                @if (i === 0) {
                  <p-treeTableToggler [rowNode]="rowNode"></p-treeTableToggler>
                }
                <span>{{getValueByPath(rowData, field)}}</span>
              </td>
            }
          </tr>
        </ng-template>
      </p-treeTable>
    } @else {
      <configurable-table [data]="transactions" [fields]="fields" dataKey="idTransaction"
                          selectionMode="multiple" [selection]="selected" (selectionChange)="onSelectionChange($event)"
                          [customSortFn]="customSort.bind(this)" [multiSortMeta]="multiSortMeta"
                          [valueGetterFn]="getValueByPath.bind(this)" [baseLocale]="baseLocale"
                          [scrollable]="true" scrollHeight="60vh">
      </configurable-table>
    }
  `,
  standalone: true,
  imports: [ConfigurableTableComponent, TreeTableModule, TooltipModule]
})
export class TransactionReceiptTableComponent extends TableConfigBase implements OnChanges {
  /** Transactions of the security shown for selection. */
  @Input() transactions: Transaction[] = [];

  /** Whether transactions must be grouped into opening and connected margin-position branches. */
  @Input() marginBased = false;

  /** Emits the currently checked transactions whenever the selection changes. */
  @Output() selectedChange = new EventEmitter<Transaction[]>();

  selected: Transaction[] = [];
  selectedNodes: TreeNode[] = [];
  transactionNodes: TreeNode[] = [];

  constructor(filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    injector: Injector) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.addColumn(DataType.DateTimeString, 'transactionTime', 'DATE', true, false, {width: 120});
    this.addColumnFeqH(DataType.String, 'transactionType', true, false, {translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'cashaccount.name', AppSettings.CASHACCOUNT.toUpperCase(), true, false);
    this.addColumn(DataType.Numeric, 'units', 'QUANTITY', true, false);
    this.addColumn(DataType.Numeric, 'quotation', 'QUOTATION_DIV', true, false,
      {maxFractionDigits: gps.getMaxFractionDigits()});
    this.addColumnFeqH(DataType.Numeric, 'cashaccountAmount', true, false,
      {currencyPrecisionField: 'cashaccount.currency'});
    this.multiSortMeta.push({field: 'transactionTime', order: 1});
    this.prepareTableAndTranslate();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['transactions'] || changes['marginBased']) && this.transactions) {
      this.selected = [];
      this.selectedNodes = [];
      this.transactionNodes = this.marginBased ? buildTransactionReceiptTree(this.transactions) : [];
      this.selectedChange.emit(this.selected);
      this.createTranslatedValueStore(this.transactions);
    }
  }

  onSelectionChange(selection: Transaction | Transaction[] | null): void {
    this.selected = Array.isArray(selection) ? selection : selection ? [selection] : [];
    this.selectedChange.emit(this.selected);
  }

  onTreeSelectionChange(selection: TreeNode | TreeNode[] | null): void {
    this.selectedNodes = Array.isArray(selection) ? selection : selection ? [selection] : [];
    this.selected = getSelectedTransactions(this.transactions, this.selectedNodes);
    this.selectedChange.emit(this.selected);
  }
}
