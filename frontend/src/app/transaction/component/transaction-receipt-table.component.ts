import {Component, EventEmitter, Injector, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {FilterService} from 'primeng/api';

import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {Transaction} from '../../entities/transaction';
import {AppSettings} from '../../shared/app.settings';

/**
 * Multi-select table of a security's transactions, used inside the transaction receipt dialog. The user checks the
 * transactions a receipt PDF should be created for; the current selection is emitted to the hosting dialog.
 */
@Component({
  selector: 'transaction-receipt-table',
  template: `
    <configurable-table [data]="transactions" [fields]="fields" dataKey="idTransaction"
                        selectionMode="multiple" [selection]="selected" (selectionChange)="onSelectionChange($event)"
                        [customSortFn]="customSort.bind(this)" [multiSortMeta]="multiSortMeta"
                        [valueGetterFn]="getValueByPath.bind(this)" [baseLocale]="baseLocale">
    </configurable-table>
  `,
  standalone: true,
  imports: [ConfigurableTableComponent]
})
export class TransactionReceiptTableComponent extends TableConfigBase implements OnChanges {
  /** Transactions of the security shown for selection. */
  @Input() transactions: Transaction[] = [];

  /** Emits the currently checked transactions whenever the selection changes. */
  @Output() selectedChange = new EventEmitter<Transaction[]>();

  selected: Transaction[] = [];

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
    if (changes['transactions'] && this.transactions) {
      this.selected = [];
      this.selectedChange.emit(this.selected);
      this.createTranslatedValueStore(this.transactions);
    }
  }

  onSelectionChange(selection: Transaction | Transaction[] | null): void {
    this.selected = Array.isArray(selection) ? selection : selection ? [selection] : [];
    this.selectedChange.emit(this.selected);
  }
}
