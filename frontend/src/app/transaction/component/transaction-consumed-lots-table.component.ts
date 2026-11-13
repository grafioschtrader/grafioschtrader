import { Component, effect, input, untracked } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import type { CostLot } from '../../entities/view/security.transaction.position';

/**
 * Shows the purchases a FIFO or LIFO sale consumed, in the expanded row of that sale. Each line names the purchase
 * by its date, the units taken from it on the split basis of the report, the cost assigned to them in the currency of
 * the security and the exchange rate to the main currency on the day of the purchase.
 */
@Component({
  selector: 'transaction-consumed-lots-table',
  template: `
    <h5 class="consumed-lots-title">{{ 'CONSUMED_LOTS' | translate }}</h5>
    <configurable-table
      [data]="lots()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      dataKey="idTransaction"
      [customSortFn]="customSorter"
      [multiSortMeta]="multiSortMeta"
      [valueGetterFn]="valueGetter"
      [contextMenuEnabled]="false"
      [scrollable]="false"
      [baseLocale]="baseLocale" />
  `,
  styles: [
    `
      .consumed-lots-title {
        margin: 0.25rem 0;
      }
    `
  ],
  imports: [ConfigurableTableComponent, TranslateModule]
})
export class TransactionConsumedLotsTableComponent extends TableConfigBase {
  /** The lots of one sale, in the order the sale consumed them. */
  readonly lots = input<CostLot[]>([]);

  /**
   * Defines the columns and prepares the rows whenever the lots change.
   */
  constructor() {
    super();
    this.addColumn(DataType.DateString, 'transactionTime', 'DATE', true, false, { width: 100 });
    this.addColumn(DataType.Numeric, 'units', 'QUANTITY', true, false);
    this.addColumn(DataType.Numeric, 'cost', 'LOT_COST', true, false);
    this.addColumn(DataType.Numeric, 'exchangeRate', 'EXCHANGE_RATE', true, false, {
      maxFractionDigits: this.gps.getMaxFractionDigits()
    });
    this.prepareTableAndTranslate();
    effect(() => {
      const lots = this.lots();
      untracked(() => this.createTranslatedValueStore(lots));
    });
  }
}
