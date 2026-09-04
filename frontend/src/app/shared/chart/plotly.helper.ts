import { TranslateService } from '@ngx-translate/core';
import { Helper } from '../../lib/helper/helper';
import { ElementRef } from '@angular/core';
import tippy from 'tippy.js';

declare let Plotly: any;

export interface ChartData {
  data: any;
  layout: any;
  options: any;
  legendTooltipMap: Map<string, string>;
  callBackFN?: (traceIndex: number, dataPointIndex: number) => void;
}

/**
 * Minimal shape of a Plotly legend layout object, limited to the properties Grafioschtrader sets.
 * Declared locally rather than imported from 'plotly.js': the library is loaded as a global UMD
 * script and consumed through `declare let Plotly: any`, so this single type is the only reason a
 * module import would exist at all.
 */
export interface PlotlyLegend {
  xanchor: 'auto' | 'left' | 'center' | 'right';
  yanchor: 'auto' | 'top' | 'middle' | 'bottom';
  orientation: 'v' | 'h';
  x: number;
  y: number;
  font: { family?: string; size?: number; color?: string };
}

export interface ChartTrace {
  x: (string | number)[];
  y: number[];
  name: string;
  type: string;
  mode: string;
  visible: boolean | 'legendonly';
}

export class PlotlyHelper {
  public static initializeChartTrace(name: string, type: string, mode?: string): Partial<ChartTrace> {
    const trace: Partial<ChartTrace> = { x: [], y: [], name, type, mode };
    return trace;
  }

  public static registerPlotlyClick(
    nativeElement: any,
    callBackFN: (traceIndex: number, dataPointIndex: number) => void
  ) {
    nativeElement.on('plotly_click', (data) => {
      let point: string;
      let traceIndex: number;
      let dataPointIndex: number;

      for (let i = 0; i < 1; i++) {
        point = `x=  + ${data.points[i].x}; y= ${data.points[i].y.toPrecision(4)}, pn=${data.points[i].curveNumber}`;
        traceIndex = data.points[i].curveNumber;
        dataPointIndex = data.points[i].pointNumber;
      }
      callBackFN(traceIndex, dataPointIndex);
    });
  }

  /**
   * Search all properties 'label' in the layout tree and translate it. It is expecting, that all
   * the property is an array element.
   */
  public static translateLayout(translateService: TranslateService, layout: any): void {
    PlotlyHelper.translateLayoutTitles(translateService, layout);
    const LABEL = 'label';
    const founds: any = [];
    this.searchArrayInObjectTree(layout, founds);

    founds.forEach((elements) =>
      elements
        .filter((e) => e && typeof e === 'object' && e.hasOwnProperty(LABEL) && typeof e[LABEL] === 'string')
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

      const params = {};
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
        if (value.constructor === Array) {
          founds.push(value);
        } else {
          this.searchArrayInObjectTree(value, founds);
        }
      });
    } else {
      // jsonObj is a number or string
    }
  }

  public static attachTooltip(
    plotly: any,
    legendTooltipMap = new Map<string, string>(),
    chartElement: ElementRef
  ): void {
    const legendLayer = chartElement.nativeElement.querySelector('g.legend');
    const items: any[] = legendLayer.querySelectorAll('g.traces');

    items.forEach((i) => {
      tippy(i, { content: legendTooltipMap.get(i.textContent) });
    });
  }

  public static getLegendUnderChart(fontSize: number): Partial<PlotlyLegend> {
    return {
      xanchor: 'left',
      yanchor: 'top',
      orientation: 'h',
      y: -0.4, // play with it
      x: 0, // play with it
      font: {
        family: 'sans-serif',
        size: fontSize,
        color: '#000'
      }
    };
  }
}
