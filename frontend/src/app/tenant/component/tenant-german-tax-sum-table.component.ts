import { Component, computed, effect, input, untracked } from '@angular/core';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import type { GermanTaxSum } from '../../entities/view/germantax/german.tax.year.report';
import { GermanTaxLabels } from '../model/german.tax.labels';

/** A sum with its translated texts. */
interface SumRow {
  rowKey: string;
  sortKey: string;
  blockLabel: string;
  lineLabel: string;
  fundTaxClassLabel: string;
  amountEur?: number;
  amountAfterPartialExemptionEur?: number;
  status: string;
}

/**
 * The sums of the German tax report in one table. A sum that a gap affects has no amount and says why it is not
 * computable, the warnings below the table name the cause. The form takes the amounts before the partial exemption.
 */
@Component({
  selector: 'tenant-german-tax-sum-table',
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
export class TenantGermanTaxSumTableComponent extends TableConfigBase {
  /** The sums as the server delivered them. */
  readonly sums = input<GermanTaxSum[]>([]);

  readonly rows = computed<SumRow[]>(() =>
    this.sums().map((sum) => ({
      rowKey: [sum.block, sum.category ?? '', sum.line, sum.fundTaxClass ?? ''].join('/'),
      sortKey: GermanTaxLabels.sortKey(sum.block, sum.line, sum.fundTaxClass),
      blockLabel: GermanTaxLabels.block(this.translateService, sum.block, sum.category),
      lineLabel: GermanTaxLabels.line(this.translateService, sum.line),
      fundTaxClassLabel: GermanTaxLabels.fundTaxClass(this.translateService, sum.fundTaxClass),
      amountEur: sum.amountEur,
      amountAfterPartialExemptionEur: sum.amountAfterPartialExemptionEur,
      status: sum.complete ? '' : this.translateService.instant('GERMAN_TAX_NOT_COMPUTABLE')
    }))
  );

  constructor() {
    super();
    this.addColumn(DataType.String, 'sortKey', 'GERMAN_TAX_COL_LINE', false, false);
    this.addColumn(DataType.String, 'blockLabel', 'GERMAN_TAX_COL_BLOCK', true, false);
    this.addColumn(DataType.String, 'lineLabel', 'GERMAN_TAX_COL_LINE', true, false);
    this.addColumn(DataType.String, 'fundTaxClassLabel', 'FUND_TAX_CLASS', true, false);
    this.addColumn(DataType.Numeric, 'amountEur', 'GERMAN_TAX_COL_AMOUNT', true, false, { fixedCurrency: 'EUR' });
    this.addColumn(DataType.Numeric, 'amountAfterPartialExemptionEur', 'GERMAN_TAX_COL_AFTER_EXEMPTION', true, false, {
      fixedCurrency: 'EUR'
    });
    this.addColumn(DataType.String, 'status', 'GERMAN_TAX_COL_STATUS', true, false);
    this.multiSortMeta.push({ field: 'sortKey', order: 1 });
    this.prepareTableAndTranslate();
    effect(() => {
      const rows = this.rows();
      untracked(() => this.createTranslatedValueStore(rows));
    });
  }
}
