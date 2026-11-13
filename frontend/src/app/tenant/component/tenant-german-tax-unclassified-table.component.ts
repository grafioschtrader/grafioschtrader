import { Component, computed, effect, input, untracked } from '@angular/core';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import type { GermanTaxUnclassified } from '../../entities/view/germantax/german.tax.year.report';

/** A booking with its translated texts. */
interface UnclassifiedRow {
  rowKey: string;
  date?: string;
  amountKindLabel: string;
  reasonLabel: string;
  securityName?: string;
  amountEur?: number;
}

/**
 * The bookings of instruments the German tax help does not calculate. Each shows the value of the booking in euro and
 * the reason, and none of them enters a sum.
 */
@Component({
  selector: 'tenant-german-tax-unclassified-table',
  template: `
    <configurable-table
      [data]="rows()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      dataKey="rowKey"
      [customSortFn]="customSorter"
      [multiSortMeta]="multiSortMeta"
      [valueGetterFn]="valueGetter"
      [contextMenuEnabled]="false"
      [scrollable]="false"
      [baseLocale]="baseLocale" />
  `,
  imports: [ConfigurableTableComponent]
})
export class TenantGermanTaxUnclassifiedTableComponent extends TableConfigBase {
  /** The bookings as the server delivered them. */
  readonly unclassified = input<GermanTaxUnclassified[]>([]);

  readonly rows = computed<UnclassifiedRow[]>(() =>
    this.unclassified().map((item, index) => ({
      rowKey: String(index),
      date: item.date,
      amountKindLabel: this.translateService.instant('GERMAN_TAX_AMOUNTKIND_' + item.amountKind),
      reasonLabel: this.translateService.instant('GERMAN_TAX_REASON_' + item.reason),
      securityName: item.securityName,
      amountEur: item.amountEur
    }))
  );

  constructor() {
    super();
    this.addColumn(DataType.DateString, 'date', 'DATE', true, false, { width: 100 });
    this.addColumn(DataType.String, 'securityName', 'SECURITY', true, false);
    this.addColumn(DataType.String, 'amountKindLabel', 'GERMAN_TAX_COL_AMOUNT_KIND', true, false);
    this.addColumn(DataType.Numeric, 'amountEur', 'GERMAN_TAX_COL_AMOUNT', true, false, { fixedCurrency: 'EUR' });
    this.addColumn(DataType.String, 'reasonLabel', 'GERMAN_TAX_COL_REASON', true, false);
    this.multiSortMeta.push({ field: 'date', order: 1 });
    this.prepareTableAndTranslate();
    effect(() => {
      const rows = this.rows();
      untracked(() => this.createTranslatedValueStore(rows));
    });
  }
}
