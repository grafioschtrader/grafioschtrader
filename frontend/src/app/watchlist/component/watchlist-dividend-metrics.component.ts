import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { ButtonModule } from '@openng/optimus-ui/button';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { IGlobalMenuAttach, PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { AppSettings } from '../../shared/app.settings';
import { DividendService } from '../service/dividend.service';
import { DividendChartService } from '../service/dividend.chart.service';
import type { SecurityDividendMetrics } from '../../entities/view/securitydividends/security.dividend.metrics';

/**
 * Box of dividend key figures in the expanded row of the dividend and split feed. The figures describe the instrument
 * as the shared dividend table records it — trailing and forward yield, growth rates, years without cut, payout ratio —
 * and not the distributions the tenant received. They are computed by the backend; this component only formats them.
 *
 * A click activates the box as a panel, so its "show" menu offers the dividend chart and adding the security to the
 * shown dividend chart, as the buttons of the box do. The chart itself is drawn by {@link DividendChartService}. The
 * box renders nothing for an instrument without dividends.
 */
@Component({
  selector: 'watchlist-dividend-metrics',
  template: `
    @let m = metrics();
    @if (m && m.dividendCount > 0) {
      <div
        class="dividend-metrics"
        [class.active-border]="isActivated()"
        [class.passiv-border]="!isActivated()"
        (click)="onComponentClick($event)">
        <div class="dm-header">
          <h5>{{ 'DIVIDEND_METRICS' | translate }} ({{ m.currency }})</h5>
          <span>
            <p-button
              [text]="true"
              size="small"
              [pTooltip]="'DIVIDEND_METRICS_CHART' | translate"
              (click)="dividendChartService.showChart(idSecuritycurrency())">
              <i class="pi pi-chart-bar" pButtonIcon></i>
            </p-button>
            <p-button
              [text]="true"
              size="small"
              [pTooltip]="'ADD_TO_DIVIDEND_CHART' | translate"
              (click)="dividendChartService.addToChart(idSecuritycurrency())">
              <i class="pi pi-plus" pButtonIcon></i>
            </p-button>
          </span>
        </div>
        <div class="dm-grid">
          @for (f of visibleFields(); track f.field) {
            <div class="dm-item">
              <span>{{ f.headerTranslated }}</span>
              <span class="dm-value">{{ getValueByPath(m, f) }}</span>
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: [
    `
      .dividend-metrics {
        margin-top: 0.5rem;
        padding: 0.25rem 0.5rem 0.5rem;
      }
      .dm-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .dm-header h5 {
        margin: 0;
      }
      .dm-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
        column-gap: 2rem;
        row-gap: 0.15rem;
      }
      .dm-item {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
      }
      .dm-value {
        font-weight: 600;
        white-space: nowrap;
      }
    `
  ],
  imports: [TranslateModule, ButtonModule, TooltipModule]
})
export class WatchlistDividendMetricsComponent
  extends ShowRecordConfigBase
  implements OnInit, OnDestroy, IGlobalMenuAttach
{
  /** The security whose key figures are shown */
  readonly idSecuritycurrency = input.required<number>();

  /** Key figures, null until loaded */
  readonly metrics = signal<SecurityDividendMetrics | null>(null);

  /** Only the figures that have a value; a missing figure is left out instead of shown empty. */
  readonly visibleFields = computed<ColumnConfig[]>(() => {
    const m = this.metrics();
    this.fieldsRevision();
    return m ? this.fields.filter((f) => m[f.field as keyof SecurityDividendMetrics] != null) : [];
  });

  private readonly dividendService = inject(DividendService);
  private readonly activePanelService = inject(ActivePanelService);
  protected readonly dividendChartService = inject(DividendChartService);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    super();
    // Percentages use the fraction digits GT prescribes for them. maxFractionDigits would not do: the shared number
    // format honours it only for values below 10. Dividend amounts are shown raw, as in the dividend table, so a
    // dividend of 0.2675 keeps its digits; the price uses the precision of the currency of the security.
    const percentFraction = { fixedFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION };
    const percent = { headerSuffix: '%', ...percentFraction };
    this.addColumnFeqH(DataType.DateString, 'lastExDate', true, false);
    this.addColumnFeqH(DataType.NumericRaw, 'lastAmount', true, false);
    this.addColumnFeqH(DataType.DateString, 'nextExDate', true, false);
    this.addColumnFeqH(DataType.NumericRaw, 'nextAmount', true, false);
    this.addColumnFeqH(DataType.NumericRaw, 'dividendTTM', true, false);
    this.addColumnFeqH(DataType.Numeric, 'price', true, false, { currencyPrecisionField: 'currency' });
    this.addColumnFeqH(DataType.Numeric, 'yieldTTM', true, false, percent);
    this.addColumnFeqH(DataType.NumericInteger, 'paymentsPerYear', true, false);
    this.addColumnFeqH(DataType.Numeric, 'forwardYield', true, false, percent);
    this.addColumnFeqH(DataType.Numeric, 'cagr1', true, false, percent);
    this.addColumnFeqH(DataType.Numeric, 'cagr3', true, false, percent);
    this.addColumnFeqH(DataType.Numeric, 'cagr5', true, false, percent);
    this.addColumnFeqH(DataType.Numeric, 'cagr10', true, false, percent);
    // Explicit key: addColumnFeqH strips the field prefix "year" and would derive S_WITHOUT_CUT
    this.addColumn(DataType.NumericInteger, 'yearsWithoutCut', 'YEARS_WITHOUT_CUT', true, false);
    // A year is shown as a plain string, an integer column would insert a thousands separator
    this.addColumnFeqH(DataType.String, 'lastCutYear', true, false);
    this.addColumnFeqH(DataType.Numeric, 'lastCutPercent', true, false, percent);
    this.addColumnFeqH(DataType.Numeric, 'chowder', true, false, percentFraction);
    this.addColumnFeqH(DataType.Numeric, 'payoutRatio', true, false, percent);
    this.addColumnFeqH(DataType.DateString, 'firstExDate', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'dividendCount', true, false);
    this.translateHeadersAndColumns();
  }

  ngOnInit(): void {
    this.activePanelService.registerPanel(this);
    this.dividendService
      .getDividendMetrics(this.idSecuritycurrency())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((metrics) => this.metrics.set(metrics));
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  /**
   * Activates the box with its show menu. The event is marked as consumed, so the watchlist table around the expanded
   * row does not take the activation back.
   */
  onComponentClick(event: PanelClickEvent): void {
    event.consumedGT = true;
    TranslateHelper.translateMenuItems$(
      this.dividendChartService.getMenuItems(this.idSecuritycurrency()),
      this.translateService
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((showMenu) => this.activePanelService.activatePanel(this, { showMenu }));
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_WATCHLIST_DIVIDEND_SPLIT_FEED;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }
}
