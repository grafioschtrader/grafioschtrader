import { Component, computed, effect, input, untracked } from '@angular/core';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import type { GermanTaxDetail } from '../../entities/view/germantax/german.tax.year.report';
import { GermanTaxLabels } from '../model/german.tax.labels';

/** A row of the derivation with its translated texts. */
interface DetailRow extends GermanTaxDetail {
  rowKey: string;
  kindLabel: string;
  blockLabel: string;
  fundTaxClassLabel: string;
}

/**
 * The derivation of the sums of the German tax report: every sale, income and advance lump sum with its amounts in
 * euro, the exchange rate and where it came from. It is no second addend of a sum.
 */
@Component({
  selector: 'tenant-german-tax-detail-table',
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
      [paginator]="true"
      [rows]="20"
      [scrollable]="false"
      [baseLocale]="baseLocale" />
  `,
  imports: [ConfigurableTableComponent]
})
export class TenantGermanTaxDetailTableComponent extends TableConfigBase {
  /** The derivation as the server delivered it. */
  readonly details = input<GermanTaxDetail[]>([]);

  readonly rows = computed<DetailRow[]>(() =>
    this.details().map((detail, index) => ({
      ...detail,
      rowKey: String(index),
      kindLabel: this.translateService.instant('GERMAN_TAX_KIND_' + detail.kind),
      blockLabel: detail.block ? GermanTaxLabels.block(this.translateService, detail.block, detail.category) : '',
      fundTaxClassLabel: GermanTaxLabels.fundTaxClass(this.translateService, detail.fundTaxClass)
    }))
  );

  constructor() {
    super();
    this.addColumn(DataType.DateString, 'date', 'DATE', true, false, { width: 100 });
    this.addColumn(DataType.String, 'kindLabel', 'GERMAN_TAX_COL_KIND', true, false);
    this.addColumn(DataType.String, 'securityName', 'SECURITY', true, false);
    this.addColumn(DataType.String, 'blockLabel', 'GERMAN_TAX_COL_BLOCK', true, false);
    this.addColumn(DataType.String, 'fundTaxClassLabel', 'FUND_TAX_CLASS', false, true);
    this.addColumn(DataType.Numeric, 'units', 'QUANTITY', true, false);
    this.addColumn(DataType.Numeric, 'grossEur', 'GERMAN_TAX_COL_GROSS', true, false, { fixedCurrency: 'EUR' });
    this.addColumn(DataType.Numeric, 'proceedsEur', 'REALIZED_PROCEEDS', true, false, { fixedCurrency: 'EUR' });
    this.addColumn(DataType.Numeric, 'costEur', 'REALIZED_COST_BASE', true, false, { fixedCurrency: 'EUR' });
    this.addColumn(DataType.Numeric, 'preliminaryDeductedEur', 'GERMAN_TAX_COL_PREPAYMENT_DEDUCTED', true, false, {
      fixedCurrency: 'EUR'
    });
    this.addColumn(DataType.Numeric, 'gainEur', 'GERMAN_TAX_COL_GAIN', true, false, { fixedCurrency: 'EUR' });
    this.addColumn(DataType.Numeric, 'foreignTaxEur', 'GERMAN_TAX_COL_FOREIGN_TAX', true, false, {
      fixedCurrency: 'EUR'
    });
    this.addColumn(DataType.Numeric, 'taxCeilingEur', 'GERMAN_TAX_COL_TAX_CEILING', true, false, {
      fixedCurrency: 'EUR'
    });
    this.addColumn(DataType.Numeric, 'rate', 'EXCHANGE_RATE', true, false, {
      maxFractionDigits: this.gps.getMaxFractionDigits()
    });
    this.addColumn(DataType.String, 'rateSource', 'GERMAN_TAX_COL_RATE_SOURCE', false, true);
    this.addColumn(DataType.Boolean, 'complete', 'GERMAN_TAX_COL_COMPLETE', true, false);
    this.multiSortMeta.push({ field: 'date', order: 1 });
    this.prepareTableAndTranslate();
    effect(() => {
      const rows = this.rows();
      untracked(() => this.createTranslatedValueStore(rows));
    });
  }
}
