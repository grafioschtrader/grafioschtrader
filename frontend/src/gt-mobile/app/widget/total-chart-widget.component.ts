import {
  afterRenderEffect,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  linkedSignal,
  untracked,
  viewChild
} from '@angular/core';
import { IonLabel, IonSegment, IonSegmentButton } from '@ionic/angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DashboardResult } from '../../../app/lib/dashboard/dashboard.types';
import { PerformanceTotalChart } from '../../../app/dashboard/model/performance.total.chart';
import {
  performanceTotalChartLayout,
  performanceTotalChartTraces
} from '../../../app/dashboard/model/performance.total.chart.plot';
import { PlotlyInstance, PlotlyLoaderService } from '../../../app/shared/chart/plotly.loader';
import { PlotlyLocales } from '../../../app/shared/plotlylocale/plotly.locales';
import { MobileDashboardService } from '../service/mobile.dashboard.service';
import { MobileFormatService } from '../service/mobile.format.service';

/**
 * Total value of the client next to its invested capital. The period can be changed on the card; like on the desktop
 * that change applies to one read and is never saved, so a refresh of the dashboard returns to the saved period.
 */
@Component({
  selector: 'mobile-total-chart-widget',
  imports: [IonSegment, IonSegmentButton, IonLabel, TranslatePipe],
  template: `
    @let shown = chart();
    @if (shown) {
      @if (shown.ranges.length > 1) {
        <ion-segment [scrollable]="true" [value]="shown.range" (ionChange)="onRange($event.detail.value)">
          @for (range of shown.ranges; track range) {
            <ion-segment-button [value]="range">
              <ion-label>{{ range | translate }}</ion-label>
            </ion-segment-button>
          }
        </ion-segment>
      }
      @if (shown.reasonKey) {
        <p class="widget-note">{{ shown.reasonKey | translate }}</p>
      }
    }
    <div #plot class="total-chart-plot" [class.total-chart-tall]="tall()" [hidden]="!shown || !!shown.reasonKey"></div>
    @if (shown && !shown.reasonKey) {
      <p class="widget-note">
        {{ 'DASHBOARD_TOTAL_CHART_NEWEST' | translate }}: {{ format.date(shown.newestDate) }}
        @if (shown.recalcPending) {
          · {{ 'DASHBOARD_TOTAL_CHART_RECALC' | translate }}
        }
        @if (shown.sampling !== 'DAY') {
          · {{ 'DASHBOARD_TOTAL_CHART_' + shown.sampling | translate }}
        }
      </p>
    }
  `,
  styles: [
    `
      .total-chart-plot {
        height: 260px;
      }
      .total-chart-tall {
        height: 60vh;
      }
    `
  ]
})
export class TotalChartWidgetComponent {
  readonly result = input.required<DashboardResult>();
  /** Set by the full-screen view, where the chart takes most of the height. */
  readonly tall = input(false);

  protected readonly format = inject(MobileFormatService);
  private readonly dashboardService = inject(MobileDashboardService);
  private readonly translateService = inject(TranslateService);
  private readonly plotlyLoader = inject(PlotlyLoaderService);
  private readonly elementRef: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly plotElement = viewChild.required<ElementRef<HTMLDivElement>>('plot');

  /** A server result replaces the chart; a period chosen on the card replaces it until the next result. */
  protected readonly chart = linkedSignal(() => this.result().payload?.custom as PerformanceTotalChart | undefined);

  /** Set once the library is loaded; cleanup never triggers the import. */
  private plotly: PlotlyInstance | null = null;
  private destroyed = false;

  constructor() {
    afterRenderEffect(() => {
      const chart = this.chart();
      this.tall();
      const element = this.plotElement().nativeElement;
      untracked(() => this.draw(element, chart));
    });
    const resizeObserver = new ResizeObserver(() => this.resize());
    afterRenderEffect(() => resizeObserver.observe(this.plotElement().nativeElement));
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      resizeObserver.disconnect();
      this.plotly?.purge(this.plotElement().nativeElement);
    });
  }

  /** Reads the card again for another period of the same client or portfolio. */
  protected onRange(range: unknown): void {
    const chart = this.chart();
    if (typeof range !== 'string' || !chart || range === chart.range) {
      return;
    }
    this.dashboardService
      .refresh(this.result().instanceId, { idPortfolio: chart.idPortfolio, range })
      .subscribe((result) => {
        const refreshed = result.payload?.custom as PerformanceTotalChart | undefined;
        if (refreshed) {
          this.chart.set(refreshed);
        }
      });
  }

  private draw(element: HTMLDivElement, chart: PerformanceTotalChart | undefined): void {
    if (!chart || chart.reasonKey || !chart.points.length) {
      this.plotly?.purge(element);
      return;
    }
    this.plotlyLoader.load().then(
      (plotly) => {
        // A newer result replaces the pending one, so only the latest chart is drawn
        if (!this.destroyed && chart === this.chart()) {
          this.plotly = plotly;
          plotly.react(element, this.traces(chart), this.layout(chart), this.plotlyConfig());
        }
      },
      () => {}
    );
  }

  private traces(chart: PerformanceTotalChart): any[] {
    return performanceTotalChartTraces(
      chart.points,
      {
        totalBalance: this.translateService.instant('TOTAL_BALANCE'),
        investedCapital: this.translateService.instant('INVESTED_CAPITAL'),
        difference: this.translateService.instant('DIFFERENCE')
      },
      {
        date: (point) => this.format.date(point.date),
        amount: (value) => this.format.amount(value, chart.currency)
      }
    );
  }

  /** Text and grid colors of the Ionic theme, so the chart follows the light and the dark mode. */
  private layout(chart: PerformanceTotalChart): any {
    const style = getComputedStyle(this.elementRef.nativeElement);
    const layout = performanceTotalChartLayout(chart, {
      textColor: style.getPropertyValue('--ion-text-color').trim() || '#222428',
      gridColor: style.getPropertyValue('--ion-border-color').trim() || '#d7d8da',
      separators: this.format.plotlySeparators()
    });
    // A finger drags the page, not the chart; the tooltip still follows a tap.
    layout.dragmode = false;
    return layout;
  }

  private plotlyConfig(): any {
    return {
      ...PlotlyLocales.setPlotyLocales(this.format),
      responsive: false,
      displaylogo: false,
      displayModeBar: false,
      scrollZoom: false
    };
  }

  private resize(): void {
    const element = this.plotElement().nativeElement as HTMLDivElement & { data?: unknown };
    if (element.data && element.offsetParent) {
      this.plotly?.Plots.resize(element);
    }
  }
}
