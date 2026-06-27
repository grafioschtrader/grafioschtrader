import {Component, Injector, Input, OnChanges, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {TableModule} from 'primeng/table';
import {FilterService} from 'primeng/api';
import moment from 'moment';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {ShowRecordConfigBase} from '../../lib/datashowbase/show.record.config.base';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {SeasonalColumnStat, SeasonalPeriodType, SeasonalReturnsResult, SeasonalYearRow}
  from '../../entities/seasonal.returns.result';

/**
 * Renders the seasonality heat map as a table: one row per year, one column per month or quarter, a trailing annual
 * column, and footer rows with per-column statistics. Cells are colored on a diverging red→white→green scale centered
 * at 0 %, with the intensity scaled to the return magnitude (clamped to the 95th percentile so outliers do not wash
 * out the scale). All values are formatted through {@link getValueByPath} so they honour the user's locale.
 */
@Component({
  selector: 'seasonality-table',
  template: `
    @if (result && result.yearRows.length > 0) {
      <p-table [columns]="fields" [value]="result.yearRows" dataKey="year"
               (sortFunction)="customSort($event)" [customSort]="true" stripedRows showGridlines>
        <ng-template #header let-fields>
          <tr>
            @for (field of fields; track field.field) {
              <th [pSortableColumn]="field.field" [style.width.px]="field.width" class="text-end">
                {{ field.headerTranslated }}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            }
          </tr>
        </ng-template>
        <ng-template #body let-el let-columns="fields">
          <tr>
            @for (field of fields; track field.field) {
              <td [style.background-color]="getBackgroundColor(el, field)"
                  [class.text-end]="field.field !== 'year'">
                {{ getValueByPath(el, field) }}
              </td>
            }
          </tr>
        </ng-template>
        <ng-template #footer>
          @for (footerRow of footerRows; track footerRow.labelKey) {
            <tr>
              <td><strong>{{ footerRow.labelKey | translate }}</strong></td>
              @for (cs of result.columnStats; track $index) {
                <td class="text-end">{{ formatStat(footerRow.value(cs)) }}</td>
              }
            </tr>
          }
        </ng-template>
      </p-table>
    }
  `,
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, TableModule]
})
export class SeasonalityTableComponent extends TableConfigBase implements OnChanges {

  @Input() result: SeasonalReturnsResult;

  /** Footer statistic rows; each knows its label and how to read its value from a column statistic. */
  readonly footerRows: { labelKey: string; value: (cs: SeasonalColumnStat) => number | null }[] = [
    {labelKey: 'AVERAGE', value: cs => cs.mean},
    {labelKey: 'MEDIAN', value: cs => cs.median},
    {labelKey: 'HIT_RATE', value: cs => cs.pctPositive}
  ];

  /**
   * Sorted magnitudes per sign used for rank-based (empirical-CDF) cell coloring. The period cells and the annual
   * column are scaled independently so the inherently larger annual returns cannot wash out the monthly palette, and
   * positives and negatives are ranked separately so the strongest loss is always full red regardless of how large
   * the gains get.
   */
  private periodNeg: number[] = [];
  private periodPos: number[] = [];
  private annualNeg: number[] = [];
  private annualPos: number[] = [];

  /** Column config used solely to format the footer statistic numbers via getValueByPath. */
  private readonly statColumnConfig: ColumnConfig =
    ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'v', '', true, true,
      {minFractionDigits: 2, maxFractionDigits: 2});

  constructor(filterService: FilterService, usersettingsService: UserSettingsService,
    translateService: TranslateService, gps: GlobalparameterService, injector: Injector) {
    super(filterService, usersettingsService, translateService, gps, injector);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['result'] && this.result) {
      this.buildColumns();
      this.computeColorScale();
    }
  }

  /** Cell value accessor for a period column; bound via the column's fieldValueFN. */
  getPeriodReturn(row: SeasonalYearRow, field: ColumnConfig): number | null {
    return row.periodReturns[field.userValue];
  }

  /** Diverging background color for a return cell; null for the non-numeric year column. */
  getBackgroundColor(row: SeasonalYearRow, field: ColumnConfig): string {
    const value = this.cellValue(row, field);
    return value == null ? null : this.colorForValue(value, field.field === 'annualReturn');
  }

  /** Formats a footer statistic through the GT formatting pipeline; blank for null. */
  formatStat(value: number | null): string {
    return value == null ? '' : this.getValueByPath({v: value}, this.statColumnConfig);
  }

  private cellValue(row: SeasonalYearRow, field: ColumnConfig): number | null {
    if (field.field === 'annualReturn') {
      return row.annualReturn;
    }
    return typeof field.userValue === 'number' ? row.periodReturns[field.userValue] : null;
  }

  private buildColumns(): void {
    this.removeAllColumns();
    this.addColumn(DataType.String, 'year', 'YEAR', true, false, {width: 60});
    const labels = this.result.periodType === SeasonalPeriodType.MONTHLY
      ? moment.monthsShort() : ['Q1', 'Q2', 'Q3', 'Q4'];
    labels.forEach((label, index) => {
      const cc = this.addColumn(DataType.Numeric, 'p' + index, label, true, false,
        {width: 64, fieldValueFN: this.getPeriodReturn.bind(this), userValue: index,
          minFractionDigits: 2, maxFractionDigits: 2});
      cc.headerTranslated = label;
    });
    this.addColumn(DataType.Numeric, 'annualReturn', 'ANNUAL_RETURN', true, false,
      {width: 72, minFractionDigits: 2, maxFractionDigits: 2});
    this.translateHeadersAndColumns();
    // Month/quarter headers are not NLS keys, so restore them after the translation pass overwrote them.
    labels.forEach((label, index) => this.fields[index + 1].headerTranslated = label);
  }

  private computeColorScale(): void {
    const periodVals: number[] = [];
    const annualVals: number[] = [];
    this.result.yearRows.forEach(row => {
      row.periodReturns.forEach(v => v != null && periodVals.push(v));
      row.annualReturn != null && annualVals.push(row.annualReturn);
    });
    this.periodNeg = periodVals.filter(v => v < 0).map(v => -v).sort((a, b) => a - b);
    this.periodPos = periodVals.filter(v => v > 0).sort((a, b) => a - b);
    this.annualNeg = annualVals.filter(v => v < 0).map(v => -v).sort((a, b) => a - b);
    this.annualPos = annualVals.filter(v => v > 0).sort((a, b) => a - b);
  }

  private colorForValue(value: number, isAnnual: boolean): string {
    const positive = value >= 0;
    const sorted = isAnnual ? (positive ? this.annualPos : this.annualNeg)
      : (positive ? this.periodPos : this.periodNeg);
    // Rank within the same-sign group (empirical CDF): the most extreme value reaches full intensity, the bulk gets
    // spread across the mid-range — independent of the absolute magnitude of outliers.
    const t = this.rankFraction(sorted, Math.abs(value));
    const hue = positive ? 120 : 0;
    const saturation = Math.round(t * 70);
    const lightness = Math.round(100 - t * 45);
    return `hsl(${hue}, ${saturation}%, ${lightness}%)`;
  }

  /** Fraction of values in the ascending-sorted group not greater than the magnitude (0..1). */
  private rankFraction(sortedAsc: number[], magnitude: number): number {
    if (sortedAsc.length === 0) {
      return 0;
    }
    let count = 0;
    for (const v of sortedAsc) {
      if (v <= magnitude) {
        count++;
      } else {
        break;
      }
    }
    return count / sortedAsc.length;
  }
}
