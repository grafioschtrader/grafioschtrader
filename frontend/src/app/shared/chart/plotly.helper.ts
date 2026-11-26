import { TranslateService } from '@ngx-translate/core';
import { Helper } from '../../lib/helper/helper';
import { ElementRef } from '@angular/core';
import tippy from 'tippy.js';

export interface ChartData {
  data: any;
  layout: any;
  options: any;
  legendTooltipMap: Map<string, string>;
  callBackFN?: (traceIndex: number, dataPointIndex: number) => void;
}

/**
 * Minimal shape of a Plotly legend layout object, limited to the properties Grafioschtrader sets.
 * Declared locally, so that the many callers building chart layouts need no import of the plotly.js type
 * declarations.
 */
export interface PlotlyLegend {
  xanchor: 'auto' | 'left' | 'center' | 'right';
  yanchor: 'auto' | 'top' | 'middle' | 'bottom';
  orientation: 'v' | 'h';
  x: number;
  y: number;
  font: { family?: string; size?: number; color?: string };
}

/**
 * Colors of the active scheme that the shared chart chrome uses. They are read from the Optimus custom properties at
 * draw time, so a chart drawn after a scheme change uses the new colors.
 */
export interface PlotlyThemeSurface {
  /** Default text color, inherited by axis labels, titles and the legend. */
  textColor: string;
  /** Secondary text, for annotations and auxiliary labels. */
  mutedColor: string;
  /** Grid, axis and zero lines. */
  gridColor: string;
  /** Background of the content area behind the transparent chart, for borders that must match it. */
  surfaceBg: string;
}

export interface ChartTrace {
  x: (string | number)[];
  y: number[];
  name: string | null | undefined;
  type: string;
  mode: string;
  visible: boolean | 'legendonly';
}

export class PlotlyHelper {
  public static initializeChartTrace(
    name: string | null | undefined,
    type: string,
    mode?: string
  ): Pick<ChartTrace, 'x' | 'y' | 'name' | 'type'> & Partial<ChartTrace> {
    const trace: Pick<ChartTrace, 'x' | 'y' | 'name' | 'type'> & Partial<ChartTrace> = {
      x: [],
      y: [],
      name,
      type,
      mode
    };
    return trace;
  }

  public static registerPlotlyClick(
    nativeElement: any,
    callBackFN: (traceIndex: number, dataPointIndex: number) => void
  ) {
    nativeElement.on(
      'plotly_click',
      (data: { points: { pointNumber: number; curveNumber: number; x: string | number; y: number }[] }) => {
        const point = data.points[0];
        if (point) callBackFN(point.curveNumber, point.pointNumber);
      }
    );
  }

  /**
   * Search all properties 'label' in the layout tree and translate it. It is expecting, that all
   * the property is an array element.
   */
  public static translateLayout(translateService: TranslateService, layout: any): void {
    PlotlyHelper.translateLayoutTitles(translateService, layout);
    const LABEL = 'label';
    const founds: Record<string, unknown>[][] = [];
    this.searchArrayInObjectTree(layout, founds);

    founds.forEach((elements) =>
      elements
        .filter(
          (e): e is Record<string, unknown> & { label: string } =>
            e && typeof e === 'object' && e.hasOwnProperty(LABEL) && typeof e[LABEL] === 'string'
        )
        .filter((f) => (f[LABEL] = f[LABEL].toUpperCase()))
        .map((match) =>
          translateService
            .get(Helper.getValueByPath(match, LABEL))
            .subscribe((trans) => Helper.setValueByPath(match, LABEL, trans))
        )
    );
  }

  /**
   * Translates every title in the layout tree. Plotly expects a title as the object form
   * <code>{text: '…'}</code>; <code>findPropertyNamesInObjectTree</code> reports the path of the
   * <code>title</code> node itself, so a path whose value is not a string is redirected to its
   * <code>text</code> child. Paths that resolve to neither are skipped.
   */
  private static translateLayoutTitles(translateService: TranslateService, layout: any): void {
    const TITLE = 'title';

    Helper.findPropertyNamesInObjectTree(layout, TITLE)
      .map((keywordPath) =>
        typeof Helper.getValueByPath(layout, keywordPath) === 'string' ? keywordPath : `${keywordPath}.text`
      )
      .filter((keywordPath) => typeof Helper.getValueByPath(layout, keywordPath) === 'string')
      .forEach((keywordPath) => this.translateLayoutTitle(translateService, layout, keywordPath));
  }

  private static translateLayoutTitle(translateService: TranslateService, layout: any, keywordPath: string): void {
    const labelParts: string[] = Helper.getValueByPath(layout, keywordPath).split('|');
    if (labelParts.length > 1) {
      const paramKey: string[] = [];
      const wordKey: string[] = [];
      for (let i = 1; i < labelParts.length; i++) {
        const entry: string[] = labelParts[i].split('@');
        paramKey.push(entry[0]);
        wordKey.push(entry[1]);
      }

      const params: Record<string, string> = {};
      translateService.get(wordKey).subscribe((paramTrans) => {
        for (let i = 0; i < wordKey.length; i++) {
          params['p' + i] = paramTrans[wordKey[i]];
        }
        translateService
          .get(labelParts[0], params)
          .subscribe((trans) => Helper.setValueByPath(layout, keywordPath, trans));
      });
    } else {
      translateService.get(labelParts[0]).subscribe((trans) => Helper.setValueByPath(layout, keywordPath, trans));
    }
  }

  public static searchArrayInObjectTree(tree: any, founds: any[]): void {
    if (tree !== null && typeof tree === 'object') {
      Object.entries(tree).forEach(([key, value]) => {
        // key is either an array index or object key
        if (Array.isArray(value)) {
          founds.push(value);
        } else {
          this.searchArrayInObjectTree(value, founds);
        }
      });
    } else {
      // jsonObj is a number or string
    }
  }

  public static attachTooltip(legendTooltipMap = new Map<string, string>(), chartElement: ElementRef): void {
    const legendLayer = chartElement.nativeElement.querySelector('g.legend');
    const items: any[] = legendLayer.querySelectorAll('g.traces');

    items.forEach((i) => {
      tippy(i, { content: legendTooltipMap.get(i.textContent) });
    });
  }

  /**
   * Legend placed below the plot area. Its text color is not set, so it inherits `font.color` of the layout, which
   * {@link applyThemeToLayout} sets for the active scheme.
   *
   * @param fontSize - Font size of the legend entries
   * @returns The partial legend layout
   */
  public static getLegendUnderChart(fontSize: number): Partial<PlotlyLegend> {
    return {
      xanchor: 'left',
      yanchor: 'top',
      orientation: 'h',
      y: -0.4, // play with it
      x: 0, // play with it
      font: {
        family: 'sans-serif',
        size: fontSize
      }
    };
  }

  /**
   * Reads the chart colors of the active scheme from the Optimus custom properties. Called at draw time, after Plotly has
   * been loaded, so a lazily loaded chart uses the scheme that is active then.
   *
   * @param element - Element whose computed style is read; the document root when omitted
   * @returns The colors, with the light scheme's values as fallback for a property that is not defined
   */
  public static getThemeSurface(element?: Element | null): PlotlyThemeSurface {
    const style = getComputedStyle(element ?? document.documentElement);
    const read = (name: string, fallback: string): string => style.getPropertyValue(name).trim() || fallback;
    return {
      textColor: read('--p-text-color', '#334155'),
      mutedColor: read('--p-text-muted-color', '#64748b'),
      gridColor: read('--p-content-border-color', '#e2e8f0'),
      surfaceBg: read('--p-content-background', '#ffffff')
    };
  }

  /**
   * Applies the scheme to the chrome shared by all charts: transparent paper and plot background, so the chart shows
   * the page behind it, the default font color, and grid, axis and zero line colors of the default and every further x
   * and y axis. It
   * sets no tick, title or trace color: those inherit the font color unless the producer of the figure chose a
   * deliberate accent, which stays untouched.
   *
   * @param layout - The Plotly layout; modified in place
   * @param surface - The colors of the active scheme
   * @returns The same layout, for chaining
   */
  public static applyThemeToLayout<T extends object>(layout: T, surface: PlotlyThemeSurface): T {
    const target = layout as Record<string, any>;
    target['paper_bgcolor'] = 'rgba(0,0,0,0)';
    target['plot_bgcolor'] = 'rgba(0,0,0,0)';
    target['font'] = { ...(target['font'] ?? {}), color: surface.textColor };
    // Without an explicit axis Plotly draws its light default grid, so the two default axes always get the colors.
    target['xaxis'] ??= {};
    target['yaxis'] ??= {};
    for (const key of Object.keys(target)) {
      if (/^[xy]axis\d*$/.test(key) && target[key] && typeof target[key] === 'object') {
        target[key] = {
          ...target[key],
          gridcolor: surface.gridColor,
          linecolor: surface.gridColor,
          zerolinecolor: surface.gridColor
        };
      }
    }
    return layout;
  }
}
