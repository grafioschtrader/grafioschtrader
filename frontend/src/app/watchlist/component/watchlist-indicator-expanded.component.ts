import { Component, computed, input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import type { SecuritycurrencyPosition } from '../../entities/view/securitycurrency.position';
import { Security } from '../../entities/security';
import { Currencypair } from '../../entities/currencypair';
import {
  IndicatorCategory,
  SecuritycurrencyIndicatorGroup,
  WatchlistIndicatorColumn
} from '../model/watchlist-indicator';
import {
  indicatorField,
  indicatorMarkStyle,
  indicatorNeedsMark,
  indicatorTooltip
} from '../model/watchlist-indicator.display';

/** One indicator of the expanded row with the column configuration that formats its value. */
interface ExpandedIndicator {
  id: number;
  column: WatchlistIndicatorColumn;
  cc: ColumnConfig;
}

/** The indicators of one category, in the order of the columns. */
interface ExpandedIndicatorGroup {
  category: IndicatorCategory;
  indicators: ExpandedIndicator[];
}

/**
 * Content of an expanded row of the indicator tab. It shows the indicators the user has marked for the expanded row,
 * split into technical and fundamental ones, with the same formatting, marks and tooltips as the table columns. The
 * values are already part of the row, so expanding a row requests nothing from the server.
 */
@Component({
  selector: 'watchlist-indicator-expanded',
  styles: [
    `
      .indicator-groups {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
      }
      .indicator-group {
        flex: 1 1 22rem;
        border: 1px solid var(--p-content-border-color, #dee2e6);
        border-radius: 4px;
        padding: 0.25rem 0.75rem 0.5rem;
      }
      .indicator-group legend {
        float: none;
        width: auto;
        font-size: 1rem;
        font-weight: 600;
        padding: 0 0.25rem;
        margin: 0;
      }
      .indicator-items {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
        column-gap: 1.5rem;
        row-gap: 0.15rem;
      }
      .indicator-item {
        display: flex;
        justify-content: space-between;
        gap: 0.5rem;
      }
      .indicator-value {
        white-space: nowrap;
        padding: 0 0.25rem;
      }
    `
  ],
  template: `
    <div class="indicator-groups">
      @for (group of groups(); track group.category) {
        <fieldset class="indicator-group">
          <legend>{{ 'WIC_CATEGORY_' + group.category | translate }}</legend>
          <div class="indicator-items">
            @for (indicator of group.indicators; track indicator.cc.field) {
              <div class="indicator-item">
                <span [pTooltip]="indicator.column.expression ?? ''" tooltipPosition="top">
                  {{ indicator.cc.headerTranslated }}
                </span>
                <span
                  class="indicator-value"
                  [style]="markStyle(indicator)"
                  [pTooltip]="tooltip(indicator)"
                  tooltipPosition="top">
                  @if (indicator.column.resultType === 'BOOLEAN') {
                    <i [class.fa]="value(indicator)" [class.fa-check]="value(indicator)" aria-hidden="true"></i>
                  } @else {
                    {{ getValueByPath(position(), indicator.cc) }}
                  }
                  @if (needsMark(indicator)) {
                    <i class="fa fa-info-circle" aria-hidden="true"></i>
                  }
                </span>
              </div>
            }
          </div>
        </fieldset>
      }
    </div>
  `,
  imports: [TranslateModule, TooltipModule]
})
export class WatchlistIndicatorExpandedComponent extends ShowRecordConfigBase {
  /** The instrument of the expanded row; its indicator values are stored under {@link indicatorField}. */
  readonly position = input.required<SecuritycurrencyPosition<Security | Currencypair>>();
  /** The indicator report the row belongs to, with the columns, cells and categories. */
  readonly group = input.required<SecuritycurrencyIndicatorGroup>();

  /** Technical before fundamental indicators; a category without indicators is left out. */
  readonly groups = computed<ExpandedIndicatorGroup[]>(() => {
    const report = this.group();
    const indicators: ExpandedIndicator[] = report.columns.flatMap((column) => {
      const id = column.idWatchlistIndicatorColumn;
      return column.showInExpansion && id != null ? [{ id, column, cc: this.createColumn(column, id) }] : [];
    });
    return (['TECHNICAL', 'FUNDAMENTAL'] as IndicatorCategory[])
      .map((category) => ({
        category,
        indicators: indicators.filter((i) => (report.columnCategories?.[i.id] ?? 'TECHNICAL') === category)
      }))
      .filter((g) => g.indicators.length > 0);
  });

  value(indicator: ExpandedIndicator): unknown {
    return (this.position() as unknown as Record<string, unknown>)[indicator.cc.field];
  }

  markStyle(indicator: ExpandedIndicator): { [key: string]: string } | null {
    return indicatorMarkStyle(indicator.column, this.value(indicator));
  }

  needsMark(indicator: ExpandedIndicator): boolean {
    return indicatorNeedsMark(this.group(), indicator.column, this.position().securitycurrency.idSecuritycurrency);
  }

  tooltip(indicator: ExpandedIndicator): string {
    const instrument = this.position().securitycurrency;
    const currency = (instrument as Security).currency ?? (instrument as Currencypair).toCurrency;
    return indicatorTooltip(this.group(), indicator.column, instrument.idSecuritycurrency, currency, (key) =>
      this.translateService.instant(key)
    );
  }

  private createColumn(column: WatchlistIndicatorColumn, id: number): ColumnConfig {
    const cc = ShowRecordConfigBase.createColumnConfig(
      column.resultType === 'BOOLEAN' ? DataType.Boolean : DataType.NumericShowZero,
      indicatorField(id),
      null,
      true,
      false,
      { fixedFractionDigits: column.decimals }
    );
    cc.headerTranslated = column.label ?? undefined;
    return cc;
  }
}
