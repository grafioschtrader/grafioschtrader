import { DestroyRef, inject, signal, Component, OnInit, output, input, effect, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { TranslateModule } from '@ngx-translate/core';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { Security } from '../../entities/security';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { IFeedConnector } from '../../shared/securitycurrency/ifeed.connector';
import { Helper } from '../../lib/helper/helper';

/**
 * Shows a table with the missing instruments that do not have a complete price history.
 */
@Component({
  selector: 'tenant-performance-eod-missing-table',
  template: `
    <configurable-table
      [data]="securities()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      dataKey="idSecuritycurrency"
      selectionMode="single"
      [selection]="selectedSecurity"
      (selectionChange)="onSelectionChange($event)"
      [enableCustomSort]="true"
      [customSortFn]="customSorter"
      sortMode="multiple"
      [multiSortMeta]="multiSortMeta"
      [valueGetterFn]="valueGetter"
      [baseLocale]="baseLocale"
      [rowClassFn]="rowHighlightClass">
      <h5 caption>{{ 'MISSING_DAY_TABLE_MARK' | translate }}</h5>
    </configurable-table>
  `,
  imports: [TranslateModule, ConfigurableTableComponent]
})
export class TenantPerformanceEodMissingTableComponent extends TableConfigBase implements OnInit {
  private securityService: SecurityService = inject(SecurityService);

  /**
   * Contains the missings days -> key = idSecuritycurrency
   */
  readonly countIdSecurityMissingsMap = input.required<{
    [key: number]: number;
  }>({});
  readonly securities = input.required<Security[]>();
  readonly selectedDayIdSecurities = input.required<number[]>();
  readonly changedSecurity = output<Security | null>();

  selectedSecurity: Security | null = null;
  private readonly feedConnectorsKV = signal<{ [id: string]: string }>({});
  private readonly destroyRef = inject(DestroyRef);
  readonly rowHighlightClass = (row: Security) => this.getRowHighlightClass(row);

  constructor() {
    super();
    this.addColumnFeqH(DataType.String, 'name', true, false, { width: 250 });
    this.addColumnFeqH(DataType.String, 'isin');
    this.addColumnFeqH(DataType.String, 'currency');
    this.addColumnFeqH(DataType.DateString, 'activeFromDate');
    this.addColumnFeqH(DataType.DateString, 'activeToDate');
    this.addColumn(DataType.String, 'idConnectorHistory', 'HISTORY_DATA_PROVIDER', true, true, {
      fieldValueFN: this.getFeedConnectorReadableName.bind(this)
    });

    this.addColumn(DataType.Numeric, 'count', 'COUNT_MISSING_YEAR', true, true, {
      fieldValueFN: this.getSecurityMissingCount.bind(this)
    });
    this.multiSortMeta.push({ field: 'name', order: 1 });
    this.prepareTableAndTranslate();

    effect(() => {
      this.selectedDayIdSecurities();
      this.securities();
      this.countIdSecurityMissingsMap();
      untracked(() => {
        if (this.selectedDayIdSecurities().length > 0) this.selectedSecurity = null;
      });
    });
  }

  getSecurityMissingCount(dataobject: any, field: ColumnConfig, valueField: any): number {
    return this.countIdSecurityMissingsMap()[dataobject.idSecuritycurrency];
  }

  getFeedConnectorReadableName(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.feedConnectorsKV()[valueField] ?? null;
  }

  onSelectionChange(selection: Security | Security[] | null): void {
    const security = Helper.singleSelection(selection);
    this.selectedSecurity = security;
    this.changedSecurity.emit(security);
  }

  getRowHighlightClass(row: Security): string | null {
    return row.idSecuritycurrency != null && this.selectedDayIdSecurities().indexOf(row.idSecuritycurrency) >= 0
      ? 'rowgroup-total'
      : null;
  }

  ngOnInit(): void {
    this.securityService
      .getFeedConnectors()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((feedConnectors: IFeedConnector[]) => {
        this.feedConnectorsKV.set(
          feedConnectors.reduce(
            (acc, feedConnector) => {
              acc[feedConnector.id] = feedConnector.readableName;
              return acc;
            },
            {} as { [key: string]: string }
          )
        );
        this.notifyViewChanged();
      });
  }
}
