import {Injectable} from '@angular/core';
import {Subject} from 'rxjs';
import {ChartData} from '../plotly.helper';


/**
 * Data can be a large object, because of that, it is not used as parameter in the route. This service is the better
 * way to transfer the Data to the child.
 * 1) Data provider route to Chart component --> Chart component is created
 * 2) chart component --> request data from Data provider
 * 3) Data provider Component --> sends data to chart component
 */
@Injectable()
export class ChartDataService {

  // lastCallBackFN: (traceIndex: number, dataPointIndex: number) => void;
  private idShownChart: string = null;


  private requestFromChart = new Subject<string>();
  requestFromChart$ = this.requestFromChart.asObservable();

  private chartDataChanged = new Subject<Partial<ChartData>>();
  chartDataChanged$ = this.chartDataChanged.asObservable();

  clearShownChart(): void {
    this.idShownChart = null;
  }

  isChartOfIdShown(idShownChart: string): boolean {
    return this.idShownChart === idShownChart;
  }

  /**
   * Request data for chart.
   *
   * @param id Identification which allows to recognize whether the data of this component is requested.
   */
  requestDataForChart(id: string) {
    this.idShownChart = id;
    this.requestFromChart.next(id);
  }

  /**
   * Send data to the chart when the showing chart data schuld be changed
   *
   * @param chartData Chart data
   */
  sentToChart(chartData: Partial<ChartData>) {
    this.chartDataChanged.next(chartData);
  }
}



