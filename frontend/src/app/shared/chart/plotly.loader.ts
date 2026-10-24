import { Injectable } from '@angular/core';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';

/** The Plotly library as the charts use it, typed with the declarations shipped by plotly.js. */
export type PlotlyInstance = typeof import('plotly.js');

let plotlyPromise: Promise<PlotlyInstance> | null = null;

/**
 * Loads the full Plotly bundle on first use. It is the only distributed bundle containing every trace type GT draws
 * (scatter, bar, candlestick, ohlc, pie, heatmap, treemap). The import becomes a hashed lazy chunk, so neither the
 * login page nor a page without a chart downloads it, and an upgraded plotly.js is never served from a stale cache.
 *
 * Every caller shares one import. A failed import is forgotten, so the next chart tries again.
 *
 * @returns Promise of the Plotly library
 */
export function loadPlotly(): Promise<PlotlyInstance> {
  plotlyPromise ??= import('plotly.js/dist/plotly.min.js').then(
    (module: any) => (module.default ?? module) as PlotlyInstance,
    (error) => {
      plotlyPromise = null;
      throw error;
    }
  );
  return plotlyPromise;
}

/**
 * Gives the chart components access to {@link loadPlotly} and reports a failed import once per attempt, however many
 * charts wait for it.
 */
@Injectable()
export class PlotlyLoaderService {
  private reportedAttempt: Promise<PlotlyInstance> | null = null;

  constructor(private messageToastService: MessageToastService) {}

  /**
   * @returns Promise of the Plotly library; it rejects when the bundle cannot be loaded
   */
  load(): Promise<PlotlyInstance> {
    const attempt = loadPlotly();
    attempt.catch(() => {
      if (this.reportedAttempt !== attempt) {
        this.reportedAttempt = attempt;
        this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'MSG_LOAD_NEW_VERSION');
      }
    });
    return attempt;
  }
}
