import { Component, computed, effect, input, untracked } from '@angular/core';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import type { GermanTaxWarning } from '../../entities/view/germantax/german.tax.year.report';
import { GermanTaxLabels } from '../model/german.tax.labels';

/** A warning with its translated texts. */
interface WarningRow {
  rowKey: string;
  causeLabel: string;
  year?: number;
  securityName?: string;
  detail?: string;
  affected: string;
}

/**
 * The gaps of the derivation of the German tax report: their cause, year, security and the sums that stay without an
 * amount because of them.
 */
@Component({
  selector: 'tenant-german-tax-warning-table',
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
export class TenantGermanTaxWarningTableComponent extends TableConfigBase {
  /** The warnings as the server delivered them. */
  readonly warnings = input<GermanTaxWarning[]>([]);

  readonly rows = computed<WarningRow[]>(() =>
    this.warnings().map((warning, index) => ({
      rowKey: String(index),
      causeLabel: this.translateService.instant('GERMAN_TAX_WARN_' + warning.code),
      year: warning.year,
      securityName: warning.securityName,
      detail: warning.detail,
      affected: warning.affectedSums.map((key) => GermanTaxLabels.sumKey(this.translateService, key)).join('; ')
    }))
  );

  constructor() {
    super();
    this.addColumn(DataType.String, 'causeLabel', 'GERMAN_TAX_COL_CAUSE', true, false);
    this.addColumn(DataType.String, 'year', 'YEAR', true, false, { width: 70 });
    this.addColumn(DataType.String, 'securityName', 'SECURITY', true, false);
    this.addColumn(DataType.String, 'detail', 'GERMAN_TAX_COL_DETAIL', true, false, { width: 100 });
    this.addColumn(DataType.String, 'affected', 'GERMAN_TAX_COL_AFFECTED', true, false);
    this.prepareTableAndTranslate();
    effect(() => {
      const rows = this.rows();
      untracked(() => this.createTranslatedValueStore(rows));
    });
  }
}
