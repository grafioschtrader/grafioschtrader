import {TranslateService} from '@ngx-translate/core';
import {Helper} from '../../helper/helper';
import {Legend} from 'plotly.js';

export interface ChartData {
  data: any;
  layout: any;
  options: any;
  callBackFN?: (traceIndex: number, dataPointIndex: number) => void;
}

export interface ChartTrace {
  x: (string | number) [];
  y: number[];
  name: string;
  type: string;
  mode: string;
  visible: (boolean | 'legendonly');
}

export class PlotlyHelper {

  public static initializeChartTrace(name: string, type: string, mode?: string): Partial<ChartTrace> {
    const trace: Partial<ChartTrace> = {x: [], y: [], name: name, type: type, mode: mode};
    return trace;
  }

  public static registerPlotlyClick(nativeElement: any, callBackFN: (traceIndex: number, dataPointIndex: number) => void) {
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
    translateService.get(layout.title).subscribe(translated => layout.title = translated);
    const LABEL = 'label';
    const founds: any = [];
    this.searchArrayInTree(layout, founds);

    founds.forEach(elements => elements.filter(e => e.hasOwnProperty(LABEL)).
    filter(f => f[LABEL] = f[LABEL].toUpperCase()).map(match =>
      translateService.get(Helper.getValueByPath(match, LABEL)).subscribe(trans => {
        Helper.setValueByPath(match, LABEL, trans);
        console.log(match);
      })));
  }

  public static searchArrayInTree(tree: any, founds: any[]): void {
    if (tree !== null && typeof tree === 'object') {
      Object.entries(tree).forEach(([key, value]) => {
        // key is either an array index or object key
        if (value.constructor === Array) {
           founds.push(value);
        } else {
          this.searchArrayInTree(value, founds);
        }
      });
    } else {
      // jsonObj is a number or string
    }
  }

  public static getLegendUnderChart(fontSize: number): Partial<Legend> {
    return {
      xanchor: 'left',
      yanchor: 'top',
      orientation: 'h',
      y: -0.4, // play with it
      x: 0,   // play with it
      font: {
        family: 'sans-serif',
        size: fontSize,
        color: '#000'
      }
    };
  }
}
