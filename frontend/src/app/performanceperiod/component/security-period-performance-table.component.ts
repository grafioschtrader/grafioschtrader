import { Component, ElementRef, computed, effect, inject, input, untracked } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { ColumnConfig, ColumnGroupConfig, TranslateValue } from '../../lib/datashowbase/column.config';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FilterType } from '../../lib/datashowbase/filter.type';
import { AppSettings } from '../../shared/app.settings';
import { TransactionSecurityTableComponent } from '../../transaction/component/transaction-security-table.component';
import { TransactionSecurityMarginTreetableComponent } from '../../transaction/component/transaction-security-margin-treetable.component';
import type { SecurityPeriodPerformance, SecurityPeriodPerformanceRow } from '../model/security-period-performance';

/**
 * Table of the period performance per security: one row per security held or traded in the period, with its values
 * at the start and end, its flows, its gain and its time-weighted return. Every figure, the totals and the default
 * order come from the server; a figure that could not be formed stays blank and the remark column names the cause. A
 * row expands to the transactions of the security up to the end of the period.
 */
@Component({
  selector: 'security-period-performance-table',
  template: `
    <configurable-table
      [data]="result().rows"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      dataKey="idSecuritycurrency"
      [customSortFn]="customSorter"
      [multiSortMeta]="multiSortMeta"
      [valueGetterFn]="valueGetter"
      [negativeValueFn]="negativeValue"
      [hasFilter]="true"
      [contextMenuEnabled]="false"
      [scrollable]="false"
      [expandable]="true"
      [expandedRowTemplate]="transactions"
      [expandedRowKeys]="expandedRowKeys()"
      [footerValueFn]="footerValue"
      [footerNegativeFn]="footerNegative"
      footerCellClass="row-total"
      [baseLocale]="baseLocale">
      <h4 caption>{{ 'PERFORMANCE_SECURITIES' | translate }}: {{ periodText() }}</h4>
    </configurable-table>
    <ng-template #transactions let-row>
      <div [attr.data-id-securitycurrency]="row.idSecuritycurrency">
        @if (row.marginInstrument) {
          <transaction-security-margin-treetable
            [idTenant]="idTenant"
            [idSecuritycurrency]="row.idSecuritycurrency"
            [idPortfolio]="idPortfolio() ?? undefined"
            [untilDate]="result().dateTo" />
        } @else {
          <transaction-security-table
            [idTenant]="idTenant"
            [idSecuritycurrency]="row.idSecuritycurrency"
            [idPortfolio]="idPortfolio() ?? undefined"
            [untilDate]="result().dateTo" />
        }
      </div>
    </ng-template>
  `,
  imports: [
    ConfigurableTableComponent,
    TranslateModule,
    TransactionSecurityTableComponent,
    TransactionSecurityMarginTreetableComponent
  ]
})
export class SecurityPeriodPerformanceTableComponent extends TableConfigBase {
  /** The calculated period as the server delivered it. */
  readonly result = input.required<SecurityPeriodPerformance>();
  /** The portfolio of the view, null for the whole client; scopes the transactions of an expanded row. */
  readonly idPortfolio = input<number | null>(null);
  /**
   * Expanded rows by idSecuritycurrency. The parent owns the object, so a click in the chart can expand a row; the
   * table mutates it in place when the user toggles a row.
   */
  readonly expandedRowKeys = input<{ [idSecuritycurrency: string]: boolean }>({});

  readonly idTenant = this.gps.getIdTenant() ?? undefined;
  private readonly elementRef: ElementRef<HTMLElement> = inject(ElementRef);

  /** Columns whose amounts are in the main currency of the response. */
  private readonly mainCurrencyColumns = ['valueStartMC', 'valueEndMC', 'inflowMC', 'outflowMC', 'gainPeriodMC'];
  private totalLabels: Record<string, string> = {};

  readonly negativeValue = (row: SecurityPeriodPerformanceRow, field: ColumnConfig) =>
    this.isValueByPathMinus(row, field);

  /** Totals of the response, labelled as a partial total when rows without gain exist. */
  readonly footerValue = (field: ColumnConfig): string =>
    this.getValueColumnTotal(field, 0, this.result().totals, null);

  /** A negative total is shown in red, like the negative values of its column. */
  readonly footerNegative = (field: ColumnConfig): boolean => this.isValueByPathMinus(this.result().totals, field);

  constructor() {
    super();
    this.addColumn(DataType.String, 'name', 'NAME', true, false, {
      width: 220,
      filterType: FilterType.likeDataType,
      columnGroupConfigs: [new ColumnGroupConfig(null, null, () => this.totalLabel())]
    });
    this.addColumn(DataType.String, 'isin', 'ISIN', false, true, { width: 110 });
    this.addColumn(DataType.String, 'currency', 'CURRENCY', true, true, { filterType: FilterType.withOptions });
    this.addColumn(DataType.NumericInteger, 'holdingDays', 'HOLDING_DAYS', true, true, {
      filterType: FilterType.likeDataType
    });
    this.addColumn(DataType.Numeric, 'unitsStart', 'UNITS_START', false, true, {
      maxFractionDigits: 8,
      templateName: GREEN_RED
    });
    this.addColumn(DataType.Numeric, 'unitsEnd', 'UNITS_END', false, true, {
      maxFractionDigits: 8,
      templateName: GREEN_RED
    });
    this.addMainCurrencyColumn('valueStartMC', 'VALUE_START', true);
    this.addMainCurrencyColumn('valueEndMC', 'VALUE_END', true);
    this.addMainCurrencyColumn('inflowMC', 'POSITION_INFLOW', false);
    this.addMainCurrencyColumn('outflowMC', 'POSITION_OUTFLOW', false);
    this.addColumn(DataType.Numeric, 'gainPeriod', 'GAIN_PERIOD_SECURITY_CURRENCY', false, true, {
      currencyPrecisionField: 'currency',
      templateName: GREEN_RED
    });
    this.addMainCurrencyColumn('gainPeriodMC', 'GAIN_PERIOD', true);
    this.addPercentColumn('twrSecurityCurrencyPercent', 'TWR_SECURITY_CURRENCY_PERCENT', false);
    this.addPercentColumn('twrPercent', 'TWR_POSITION_PERCENT', true);
    // Shown only for a result with a benchmark; the tooltip of an empty cell says why the row has no comparison
    this.addPercentColumn('benchmarkTwrPercent', 'BENCHMARK_TWR_PERCENT', false, this.benchmarkCellTooltip);
    this.addPercentColumn('excessTwrPercent', 'EXCESS_TWR_PERCENT', false, this.benchmarkCellTooltip, false);
    this.addColumn(DataType.String, 'remark', 'SECURITY_PERIOD_REMARK', true, true, {
      translateValues: TranslateValue.NORMAL,
      width: 220
    });
    this.multiSortMeta.push({ field: 'gainPeriodMC', order: -1 });
    this.translateService.get(['GRAND_TOTAL', 'PARTIAL_TOTAL']).subscribe((labels) => (this.totalLabels = labels));
    this.prepareTableAndTranslate();
    effect(() => {
      const result = this.result();
      untracked(() => this.applyResult(result));
    });
  }

  /** The period of the shown result in the date format of the user, so it stays recognisable after form edits. */
  readonly periodText = computed(() => {
    const result = this.result();
    return `${this.formatDate(result.dateFrom)} - ${this.formatDate(result.dateTo)}`;
  });

  /**
   * Brings the expanded row of a security into view, for a click in the chart. Does nothing when the row is not
   * expanded or not rendered, for example because a filter of the table hides it.
   *
   * @param idSecuritycurrency The security whose row is shown
   */
  scrollToExpandedRow(idSecuritycurrency: number): void {
    const expansion = this.elementRef.nativeElement.querySelector(
      `[data-id-securitycurrency="${idSecuritycurrency}"]`
    ) as HTMLElement | null;
    const row = expansion?.closest('tr')?.previousElementSibling ?? expansion;
    row?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  private applyResult(result: SecurityPeriodPerformance): void {
    this.fields
      .filter((field) => field.field === 'benchmarkTwrPercent' || field.field === 'excessTwrPercent')
      .forEach((field) => (field.visible = !!result.benchmark));
    this.fields
      .filter((field) => this.mainCurrencyColumns.includes(field.field))
      .forEach((field) => {
        field.headerSuffix = result.mainCurrency;
        field.fixedCurrency = result.mainCurrency;
      });
    this.prepareTableAndTranslate();
    this.createTranslatedValueStoreAndFilterField(result.rows);
  }

  private totalLabel(): string {
    const key = this.result().omittedRows > 0 ? 'PARTIAL_TOTAL' : 'GRAND_TOTAL';
    return this.totalLabels[key] ?? '';
  }

  private formatDate(isoDate: string): string {
    return this.getValueByPath({ date: isoDate }, ColumnConfigDate) ?? isoDate;
  }

  private addMainCurrencyColumn(field: string, headerKey: string, visible: boolean): void {
    this.addColumn(DataType.Numeric, field, headerKey, visible, true, {
      columnGroupConfigs: [new ColumnGroupConfig(field)],
      templateName: GREEN_RED
    });
  }

  /** Why a row has no benchmark comparison; nothing for a compared row. */
  private readonly benchmarkCellTooltip = (row: SecurityPeriodPerformanceRow): string | undefined =>
    row.benchmarkCoverage && row.benchmarkCoverage !== 'BENCHMARK_COVERED'
      ? this.translateService.instant(row.benchmarkCoverage)
      : undefined;

  /**
   * A difference of two returns is in percentage points, which its label says, so it gets no percent sign.
   */
  private addPercentColumn(
    field: string,
    headerKey: string,
    visible: boolean,
    cellTooltipFN?: (row: SecurityPeriodPerformanceRow) => string | undefined,
    percentSuffix = true
  ): void {
    this.addColumn(DataType.Numeric, field, headerKey, visible, true, {
      cellTooltipFN,
      headerSuffix: percentSuffix ? '%' : undefined,
      maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION,
      filterType: FilterType.likeDataType,
      templateName: GREEN_RED
    });
  }
}

/** Cell template of the table that shows a negative value in red, as the other reports do. */
const GREEN_RED = 'greenRed';

/** Formats an ISO date string with the date format of the user. */
const ColumnConfigDate: ColumnConfig = TableConfigBase.createColumnConfig(DataType.DateString, 'date', '');
