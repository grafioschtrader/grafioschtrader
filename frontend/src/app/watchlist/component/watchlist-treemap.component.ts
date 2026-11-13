import { httpResource } from '@angular/common/http';
import {
  afterNextRender,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  numberAttribute,
  OnDestroy,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { ButtonModule } from '@openng/optimus-ui/button';
import { ContextMenu, ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { TimeSeriesQuotesService } from '../../historyquote/service/time.series.quotes.service';
import { HelpIds } from '../../lib/help/help.ids';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { ViewSizeChangedService } from '../../lib/layout/service/view.size.changed.service';
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';
import type { IGlobalMenuAttach, PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { PlotlyInstance, PlotlyLoaderService } from '../../shared/chart/plotly.loader';
import { PlotlyLocales } from '../../shared/plotlylocale/plotly.locales';
import { WatchlistTreemap, WatchlistTreemapNode } from '../model/watchlist-treemap';
import { WatchlistService } from '../service/watchlist.service';
import { WatchlistTreemapChart } from './watchlist.treemap.chart';

/**
 * Market map of a watchlist: the stocks are grouped by the sub category of their asset class, the area of a tile is the
 * market capitalisation in the main currency of the tenant and its colour the current price change. Stocks that cannot
 * be drawn are named below the chart with their reason. A right click on the tile of a stock opens the menu of its price
 * history, which is also offered in the show menu of the main menu bar.
 */
@Component({
  template: `
    <div
      class="data-container watchlist-treemap"
      (click)="onComponentClick($event)"
      [class]="{ 'active-border': isActivated(), 'passiv-border': !isActivated() }">
      <div>
        <p-button
          [label]="'WATCHLIST_TREEMAP_REFRESH' | translate"
          [loading]="treemap.isLoading()"
          (click)="treemap.reload()">
          <i class="pi pi-refresh" pButtonIcon></i>
        </p-button>
      </div>
      @if (noEquities()) {
        <h4>{{ 'WATCHLIST_TREEMAP_NO_EQUITIES' | translate }}</h4>
      }
      <div
        #chart
        class="watchlist-treemap-plot"
        [hidden]="noEquities()"
        (contextmenu)="onChartContextMenu($event)"></div>
      <p-contextMenu #contextMenu [model]="contextMenuItems()" appendTo="body" />
      @if (data(); as loaded) {
        @if (excludedByReason().length > 0) {
          <div>
            <strong>{{ 'TREEMAP_NOT_SHOWN' | translate }}</strong>
            @for (group of excludedByReason(); track group.reasonKey) {
              <div>{{ group.reasonKey | translate }}: {{ group.names }}</div>
            }
          </div>
        }
        @if (loaded.notApplicableCount > 0) {
          <div>{{ 'WATCHLIST_TREEMAP_NOT_APPLICABLE' | translate: { count: loaded.notApplicableCount } }}</div>
        }
      }
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        height: 100%;
      }
      .watchlist-treemap {
        display: flex;
        flex-direction: column;
        height: calc(100% - 8px);
      }
      .watchlist-treemap-plot {
        flex: 1 1 auto;
        min-height: 320px;
        width: 100%;
      }
    `
  ],
  imports: [TranslateModule, ButtonModule, ContextMenuModule]
})
export class WatchlistTreemapComponent implements OnDestroy, IGlobalMenuAttach {
  readonly id = input.required({ transform: numberAttribute });

  private readonly watchlistService = inject(WatchlistService);
  private readonly activePanelService = inject(ActivePanelService);
  private readonly viewSizeChangedService = inject(ViewSizeChangedService);
  private readonly translateService = inject(TranslateService);
  private readonly gps = inject(GlobalparameterService);
  private readonly plotlyLoader = inject(PlotlyLoaderService);
  private readonly renderInjector = inject(Injector);
  private readonly timeSeriesQuotesService = inject(TimeSeriesQuotesService);

  private readonly chartElement = viewChild.required<ElementRef<HTMLElement>>('chart');
  private readonly contextMenu = viewChild.required<ContextMenu>('contextMenu');

  /** Translated price history menu of the stock that was right clicked last. */
  readonly contextMenuItems = signal<MenuItem[]>([]);

  /** The market map of the watchlist; a reload also loads current prices on the server. */
  readonly treemap = httpResource<WatchlistTreemap>(() => this.watchlistService.getWatchlistTreemapRequest(this.id()));

  /** The loaded market map, null before the first answer and after a failed request. */
  readonly data = computed(() => (this.treemap.hasValue() ? this.treemap.value() : null));

  /** True when the answer contains no stock that can be drawn. */
  readonly noEquities = computed(() => this.data()?.nodes.length === 0);

  /** The stocks that are not drawn, one entry per reason. */
  readonly excludedByReason = computed(() => {
    const namesByReason = new Map<string, string[]>();
    this.data()?.excluded.forEach((e) =>
      namesByReason.set(e.reasonKey, [...(namesByReason.get(e.reasonKey) ?? []), e.label])
    );
    return [...namesByReason].map(([reasonKey, names]) => ({ reasonKey, names: names.join(', ') }));
  });

  private readonly plotlyConfig = {
    ...PlotlyLocales.setPlotyLocales(this.gps),
    displaylogo: false,
    responsive: true
  };

  /** Set once the first chart has loaded the library; cleanup never triggers the import. */
  private plotly?: PlotlyInstance;
  private destroyed = false;
  /** Raised by every draw, so a draw still waiting for the library is dropped when a newer one follows. */
  private drawVersion = 0;
  /** Stock tile under the mouse pointer, reported by the hover events of Plotly; null over a group or outside. */
  private hoveredNode: WatchlistTreemapNode | null = null;
  /** Raised by every right click, so a late translation of an earlier menu is not shown anymore. */
  private menuVersion = 0;

  constructor() {
    reportResourceErrors(this.treemap, this.watchlistService);
    effect(() => {
      if (!this.treemap.isLoading() && this.treemap.hasValue()) {
        const loaded = this.treemap.value();
        untracked(() => this.draw(loaded));
      }
    });
    this.viewSizeChangedService.viewSizeChanged$.pipe(takeUntilDestroyed()).subscribe(() => this.resizeChart());
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(_event: PanelClickEvent): void {
    this.activePanelService.activatePanel(this);
  }

  hideContextMenu(): void {
    this.contextMenu().hide();
  }

  /**
   * Opens the price history menu when the right click hits the tile of a stock. Elsewhere the browser keeps its own
   * menu, because a group or the path bar has no price history.
   *
   * @param event - Native right click on the chart
   */
  onChartContextMenu(event: MouseEvent): void {
    const node = this.hoveredNode;
    // The chart takes an instrument without currency for a currency pair, so a stock is only offered with its currency.
    if (node?.idSecuritycurrency == null || !node.currency) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    const version = ++this.menuVersion;
    const menuItems = this.timeSeriesQuotesService.getMenuItems(node.idSecuritycurrency, node.currency, false, {
      noTransactionReceipts: true
    });
    TranslateHelper.translateMenuItems$(menuItems, this.translateService).subscribe((translated) => {
      if (this.destroyed || version !== this.menuVersion) {
        return;
      }
      this.contextMenuItems.set(translated);
      this.activePanelService.activatePanel(this, { showMenu: translated, editMenu: null });
      // The menu must render the new model before it measures its position.
      afterNextRender(() => this.contextMenu().show(event), { injector: this.renderInjector });
    });
  }

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_WATCHLIST;
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.plotly?.purge(this.chartElement().nativeElement);
    this.activePanelService.destroyPanel(this);
  }

  private draw(loaded: WatchlistTreemap): void {
    const version = ++this.drawVersion;
    if (loaded.nodes.length === 0) {
      afterNextRender(() => this.plotly?.purge(this.chartElement().nativeElement), { injector: this.renderInjector });
      return;
    }
    const rootLabel = this.translateService.instant('WATCHLIST_TREEMAP');
    const figure = new WatchlistTreemapChart(this.translateService, this.gps).getChartDefinition(rootLabel, loaded);
    // Wait until the chart element is no longer hidden, otherwise Plotly measures a zero size.
    afterNextRender(
      () =>
        this.plotlyLoader.load().then(
          (plotly) => {
            if (!this.destroyed && version === this.drawVersion) {
              this.plotly = plotly;
              this.hoveredNode = null;
              plotly
                .newPlot(this.chartElement().nativeElement, figure.data, figure.layout, this.plotlyConfig)
                .then((element) => this.registerHover(element as unknown as PlotlyEventElement, loaded.nodes));
            }
          },
          () => {}
        ),
      { injector: this.renderInjector }
    );
  }

  /**
   * Tracks the stock tile under the mouse pointer. Plotly reports no right clicks, so the native context menu event
   * relies on the last hover. A new plot removes these listeners, therefore every draw registers them again.
   */
  private registerHover(element: PlotlyEventElement, nodes: WatchlistTreemapNode[]): void {
    element.on('plotly_hover', (data) => {
      const node = nodes[data.points[0]?.pointNumber];
      this.hoveredNode = node?.nodeType === 'SECURITY' ? node : null;
    });
    element.on('plotly_unhover', () => (this.hoveredNode = null));
  }

  private resizeChart(): void {
    const element = this.chartElement().nativeElement as HTMLElement & { data?: unknown };
    if (!this.noEquities() && element.data) {
      this.plotly?.Plots.resize(element);
    }
  }
}

/** Chart element after Plotly has attached its event emitter. */
type PlotlyEventElement = HTMLElement & {
  on(event: 'plotly_hover', handler: (data: { points: { pointNumber: number }[] }) => void): void;
  on(event: 'plotly_unhover', handler: () => void): void;
};
