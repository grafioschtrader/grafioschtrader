import {
  afterRenderEffect,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { IonLabel, IonSegment, IonSegmentButton, IonSpinner, IonToggle } from '@ionic/angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import moment from 'moment';
import type { Config, PlotRelayoutEvent } from 'plotly.js';
import { BaseSettings } from '../../../app/lib/base.settings';
import { PlotlyInstance, PlotlyLoaderService } from '../../../app/shared/chart/plotly.loader';
import { PlotlyLocales } from '../../../app/shared/plotlylocale/plotly.locales';
import { MobileFormatService } from '../service/mobile.format.service';
import { MobileInstrumentService } from '../service/mobile.instrument.service';
import {
  isMobileChartRange,
  MobileChartRange,
  mobileChartFirstTrade,
  mobileChartFromDate,
  mobileChart,
  mobileChartLayout,
  mobileChartPriceRange,
  mobileChartRelayoutWindow,
  MobileChartResponse,
  MobileChartSurface,
  MobileChartTransactionPosition,
  mobileChartWindow,
  MobilePriceSeries
} from './mobile.instrument.chart';

/** Remembers the chosen period and switches in this browser. */
const SETTINGS_STORAGE_KEY = 'gtMobile.chartSettings';

interface ChartSettings {
  range: MobileChartRange;
  showHolding: boolean;
  includeDividends: boolean;
}

/** Value of an Ionic segment or toggle change. */
interface IonChangeEvent {
  detail: { value?: string | number | null; checked?: boolean };
}

interface RangeOption {
  range: MobileChartRange;
  prefix: string;
  labelKey: string | null;
}

/** The periods offered, with the label of the desktop buttons. */
const RANGES: RangeOption[] = [
  { range: '5D', prefix: '5', labelKey: 'D' },
  { range: '1M', prefix: '1M', labelKey: null },
  { range: '3M', prefix: '3M', labelKey: null },
  { range: 'YTD', prefix: 'YTD', labelKey: null },
  { range: '1Y', prefix: '1', labelKey: 'Y' },
  { range: '3Y', prefix: '3', labelKey: 'Y' },
  { range: '5Y', prefix: '5', labelKey: 'Y' },
  { range: '10Y', prefix: '10', labelKey: 'Y' },
  { range: 'MAX', prefix: '', labelKey: 'CHART_RANGE_MAX' }
];
/** Offered once the first transaction is known. */
const TRADE_RANGE: RangeOption = { range: 'TRADE', prefix: '', labelKey: 'OLDEST_TRADE_TOOLTIP' };

/**
 * The price history of an instrument: the price line, the purchases, sales and dividends of the tenant as markers and
 * the units held on a second axis, optionally with reinvested dividends. A period sets the window; a finger moves it
 * sideways and the range slider below widens or narrows it, while the price axis follows the window. It is the part of
 * the desktop chart that suits a finger; indicators, drawings and comparisons stay on the desktop.
 */
@Component({
  selector: 'mobile-instrument-chart',
  imports: [IonSegment, IonSegmentButton, IonLabel, IonToggle, IonSpinner, TranslatePipe],
  template: `
    <!-- After the window was moved no period is marked, so a tap on the chosen one returns to it. -->
    <ion-segment [scrollable]="true" [value]="windowMoved() ? '' : settings().range" (ionChange)="onRange($event)">
      @for (r of ranges(); track r.range) {
        <ion-segment-button [value]="r.range">
          <ion-label>{{ r.prefix }}{{ r.labelKey ? (r.labelKey | translate) : '' }}</ion-label>
        </ion-segment-button>
      }
    </ion-segment>
    @if (withTransactions() || isSecurity()) {
      <div class="chart-switches">
        @if (withTransactions()) {
          <ion-toggle [checked]="settings().showHolding" (ionChange)="onHolding($event)" labelPlacement="end">
            {{ 'HOLDING' | translate }}
          </ion-toggle>
        }
        @if (isSecurity()) {
          <ion-toggle [checked]="settings().includeDividends" (ionChange)="onDividends($event)" labelPlacement="end">
            {{ 'INCLUDE_DIVIDENDS' | translate }}
          </ion-toggle>
        }
      </div>
    }
    @if (chartResource.value()?.dividendDataMissing && settings().includeDividends) {
      <p class="widget-note">{{ 'TOTAL_RETURN_DIVIDEND_MISSING' | translate }}</p>
    }
    @if (chartResource.error()) {
      <p class="widget-note loss">{{ 'DASHBOARD_LOAD_ERROR' | translate }}</p>
    } @else if (chartResource.isLoading() && !chartResource.hasValue()) {
      <div class="widget-loading"><ion-spinner /></div>
    }
    <div #plot class="instrument-chart" [hidden]="!chartResource.hasValue()"></div>
  `,
  styles: [
    `
      .chart-switches {
        display: flex;
        flex-wrap: wrap;
        gap: 8px 24px;
        margin: 4px 0 8px;
      }
      .instrument-chart {
        height: 60vh;
        min-height: 300px;
      }
      @media (orientation: landscape) {
        .instrument-chart {
          height: 85vh;
        }
      }
    `
  ]
})
export class InstrumentChartComponent {
  readonly idSecuritycurrency = input.required<number>();
  /** Currency of the prices. */
  readonly currency = input.required<string>();
  /** A security, which can include dividends; false for a currency pair. */
  readonly isSecurity = input.required<boolean>();
  /** The tenant traded the security, so its transactions and units held are shown. */
  readonly withTransactions = input.required<boolean>();
  /** Fraction digits of the prices the backend decided on for the instrument. */
  readonly priceFractionDigits = input<number | null>(null);

  private readonly format = inject(MobileFormatService);
  private readonly instrumentService = inject(MobileInstrumentService);
  private readonly translateService = inject(TranslateService);
  private readonly plotlyLoader = inject(PlotlyLoaderService);
  private readonly elementRef: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly plotElement = viewChild.required<ElementRef<HTMLDivElement>>('plot');

  protected readonly settings = signal<ChartSettings>(readStoredSettings());

  protected readonly chartResource = rxResource({
    params: () => ({
      id: this.idSecuritycurrency(),
      includeDividends: this.isSecurity() && this.settings().includeDividends
    }),
    stream: ({ params }) => this.instrumentService.getChart(params.id, params.includeDividends)
  });
  /** Transactions with prices adjusted for splits, read only for a security the tenant traded. */
  private readonly transactionsResource = rxResource({
    params: () => (this.withTransactions() ? this.idSecuritycurrency() : undefined),
    stream: ({ params }) => this.instrumentService.getTransactions(params, true)
  });
  private readonly positions = computed<MobileChartTransactionPosition[]>(() =>
    this.transactionsResource.hasValue() ? (this.transactionsResource.value().transactionPositionList ?? []) : []
  );
  private readonly firstTrade = computed(() => mobileChartFirstTrade(this.positions()));
  /** The period since the first transaction is offered once one is known. */
  protected readonly ranges = computed(() => (this.firstTrade() ? [...RANGES, TRADE_RANGE] : RANGES));

  /** The window was moved by a finger or the range slider away from the chosen period. */
  protected readonly windowMoved = signal(false);
  /** The series on the price axis of the chart drawn last, to fit that axis to a moved window. */
  private priceSeries: MobilePriceSeries[] = [];
  /** First day with a price of the chart drawn last, for a window reset to the whole history. */
  private firstDate: string | null = null;

  /** Set once the library is loaded; cleanup never triggers the import. */
  private plotly: PlotlyInstance | null = null;
  private destroyed = false;
  /** Raised by every draw, so a draw still waiting for the library is dropped when a newer one follows. */
  private drawVersion = 0;

  constructor() {
    afterRenderEffect(() => {
      const response = this.chartResource.hasValue() ? this.chartResource.value() : null;
      const positions = this.positions();
      const settings = this.settings();
      const firstTrade = this.firstTrade();
      const element = this.plotElement().nativeElement;
      untracked(() => this.draw(element, response, positions, settings, firstTrade));
    });
    const resizeObserver = new ResizeObserver(() => this.resize());
    afterRenderEffect(() => resizeObserver.observe(this.plotElement().nativeElement));
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      resizeObserver.disconnect();
      this.plotly?.purge(this.plotElement().nativeElement);
    });
  }

  protected onRange(event: IonChangeEvent): void {
    const range = event.detail.value;
    if (isMobileChartRange(range)) {
      this.windowMoved.set(false);
      this.updateSettings({ range });
    }
  }

  protected onHolding(event: IonChangeEvent): void {
    this.updateSettings({ showHolding: !!event.detail.checked });
  }

  protected onDividends(event: IonChangeEvent): void {
    this.updateSettings({ includeDividends: !!event.detail.checked });
  }

  private updateSettings(change: Partial<ChartSettings>): void {
    const settings = { ...this.settings(), ...change };
    this.settings.set(settings);
    storeSettings(settings);
  }

  private draw(
    element: HTMLDivElement,
    response: MobileChartResponse | null,
    positions: MobileChartTransactionPosition[],
    settings: ChartSettings,
    firstTrade: string | null
  ): void {
    const version = ++this.drawVersion;
    if (!response) {
      this.plotly?.purge(element);
      return;
    }
    const today = moment().format(BaseSettings.FORMAT_DATE_SHORT_NATIVE);
    // Without a known first transaction the period since it falls back to the whole history.
    const range = settings.range === 'TRADE' && !firstTrade ? 'MAX' : settings.range;
    const fromDate = mobileChartFromDate(range, today, firstTrade);
    const surface = this.surface();
    const chart = mobileChart(response, positions, today, settings.showHolding, this.texts(), surface);
    const span = mobileChartWindow(fromDate, chart.firstDate, today);
    const layout = mobileChartLayout(
      this.currency(),
      this.priceFractionDigits(),
      chart.withHolding,
      span,
      mobileChartPriceRange(chart.priceSeries, span[0], span[1]),
      surface
    );
    this.priceSeries = chart.priceSeries;
    this.firstDate = chart.firstDate;
    this.plotlyLoader.load().then(
      (plotly) => {
        if (!this.destroyed && version === this.drawVersion) {
          this.plotly = plotly;
          plotly.react(element, chart.traces, layout, this.plotlyConfig()).then(
            (plot) => {
              // A react that rebuilds the plot drops its listeners, so the listener is bound after every draw.
              plot.removeAllListeners('plotly_relayout');
              plot.on('plotly_relayout', (event) => this.fitPriceAxis(element, event));
            },
            () => {}
          );
        }
      },
      () => {}
    );
  }

  /**
   * Fits the price axis to the window after a finger or the range slider moved it. The relayout of the price axis
   * itself carries no window and ends here.
   */
  private fitPriceAxis(element: HTMLDivElement, event: PlotRelayoutEvent): void {
    const today = moment().format(BaseSettings.FORMAT_DATE_SHORT_NATIVE);
    const span = mobileChartRelayoutWindow(event, [this.firstDate ?? today, today]);
    if (!span || !this.plotly) {
      return;
    }
    this.windowMoved.set(true);
    const range = mobileChartPriceRange(this.priceSeries, span[0], span[1]);
    if (range) {
      this.plotly.relayout(element, { 'yaxis.autorange': false, 'yaxis.range': range });
    }
  }

  private texts() {
    return {
      price: this.translateService.instant('PRICE'),
      holding: this.translateService.instant('HOLDING'),
      types: {
        ACCUMULATE: this.translateService.instant('ACCUMULATE'),
        REDUCE: this.translateService.instant('REDUCE'),
        DIVIDEND: this.translateService.instant('DIVIDEND')
      }
    };
  }

  /** Colors of the Ionic theme, so the chart follows the light and the dark mode. */
  private surface(): MobileChartSurface {
    const style = getComputedStyle(this.elementRef.nativeElement);
    const read = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback;
    return {
      textColor: read('--ion-text-color', '#222428'),
      gridColor: read('--ion-border-color', '#d7d8da'),
      gainColor: read('--gt-gain-color', '#1e8e3e'),
      lossColor: read('--gt-loss-color', '#c5221f'),
      separators: this.format.plotlySeparators()
    };
  }

  private plotlyConfig(): Partial<Config> {
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

function readStoredSettings(): ChartSettings {
  const settings: ChartSettings = { range: '1Y', showHolding: true, includeDividends: false };
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(SETTINGS_STORAGE_KEY) ?? 'null');
    if (typeof stored === 'object' && stored !== null) {
      if ('range' in stored && isMobileChartRange(stored.range)) {
        settings.range = stored.range;
      }
      if ('showHolding' in stored && typeof stored.showHolding === 'boolean') {
        settings.showHolding = stored.showHolding;
      }
      if ('includeDividends' in stored && typeof stored.includeDividends === 'boolean') {
        settings.includeDividends = stored.includeDividends;
      }
    }
  } catch {
    // Unreadable storage keeps the defaults.
  }
  return settings;
}

function storeSettings(settings: ChartSettings): void {
  try {
    localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // Without storage the next start shows one year with the units held.
  }
}
