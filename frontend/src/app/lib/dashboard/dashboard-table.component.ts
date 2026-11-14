import { Component, input, effect, signal, untracked } from '@angular/core';
import { TableConfigBase } from '../datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../datashowbase/configurable-table.component';
import { DataType } from '../dynamic-form/models/data.type';
import { DashboardRow } from './dashboard.types';
import { ColumnConfig, TranslateValue } from '../datashowbase/column.config';
import { requestAgeCellStyle } from './request-age.color';

/** Compact projection table; processing remains on the destination screen. */
@Component({
  selector: 'dashboard-table',
  imports: [ConfigurableTableComponent],
  template: `<configurable-table
    [data]="displayRows()"
    [fields]="fields"
    [fieldsRevision]="fieldsRevision()"
    dataKey="id"
    [cellStyleFn]="cellStyle"
    [valueGetterFn]="valueGetter" />`
})
export class DashboardTableComponent extends TableConfigBase {
  readonly rows = input<DashboardRow[]>([]);
  readonly type = input('');

  /**
   * Copies of the rows carrying the translated values. The translation store adds a `$` field to every row, which must
   * not happen to the objects of the input.
   */
  protected readonly displayRows = signal<DashboardRow[]>([]);

  /** Marks the creation time of an open administrator request in red, the stronger the more recent it is. */
  readonly cellStyle = (row: DashboardRow, field: ColumnConfig): { [key: string]: string } | null =>
    this.type() === 'USER_LIMIT_REQUESTS' && field.field === 'creationTime'
      ? requestAgeCellStyle(row.creationTime, Date.now())
      : null;

  constructor() {
    super();

    effect(() => {
      this.type();
      untracked(() => {
        this.configureColumns();
      });
    });
    effect(() => {
      const rows = this.rows();
      this.type();
      untracked(() => {
        const copies = rows.map((row) => ({ ...row }));
        this.createTranslatedValueStore(copies);
        this.displayRows.set(copies);
      });
    });
  }
  private configureColumns(): void {
    this.removeAllColumns();
    const type = this.type();
    if (type !== 'PROPOSE_CHANGE_OPEN') this.addColumnFeqH(DataType.String, 'nickname');
    // The age of an open administrator request is what matters most, so its creation time follows the user directly.
    if (type === 'USER_LIMIT_REQUESTS') this.addColumn(DataType.DateTimeString, 'creationTime', 'CREATION_TIME');
    if (type === 'UNREAD_MAIL') this.addColumnFeqH(DataType.String, 'subject');
    else {
      this.addColumn(DataType.String, 'entity', 'ENTITY', true, false, { translateValues: TranslateValue.UPPER_CASE });
      if (type === 'USER_LIMIT_REQUESTS') {
        this.addColumn(DataType.NumericInteger, 'dayLimit', 'DASHBOARD_REQUESTED_LIMIT');
        this.addColumn(DataType.DateString, 'validUntil', 'VALID_UNTIL');
      }
      this.addColumnFeqH(DataType.String, 'noteRequest');
    }
    if (type !== 'USER_LIMIT_REQUESTS') this.addColumn(DataType.DateTimeString, 'creationTime', 'CREATION_TIME');
    this.prepareTableAndTranslate();
  }
}
