import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { SecurityPositionDynamicGrandSummary } from '../../entities/view/security.position.dynamic.grand.summary';
import { SecurityPositionDynamicGroupSummary } from '../../entities/view/security.position.dynamic.group.summary';

/**
 * What the report "security asset classes with cash" hands to its charts after every load.
 */
export interface HoldingsChartInput {
  /** Title of the report, used as chart title and as label of the treemap root. */
  title: string;
  /** The loaded report; carries the holdings treemap unless a rebalancing strategy is selected. */
  summary: SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<any>>;
  /** Plotly data and layout of the net risk and share per asset class chart, built by the table's group definition. */
  assetclassChart: { data: any[]; layout: any };
  /** True when the report compares against a rebalancing strategy; the treemap is then not available. */
  rebalancing: boolean;
}

/**
 * Couples the report "security asset classes with cash" with its charts in the lower display area. The table
 * publishes every loaded report; the chart component draws the chart the user selected. The latest value is kept, so a
 * chart opened after the load still receives it.
 */
@Injectable()
export class TenantHoldingsChartService {
  private chartInput = new BehaviorSubject<HoldingsChartInput>(null);

  /** Latest loaded report; null until the table has loaded once. */
  readonly chartInput$: Observable<HoldingsChartInput> = this.chartInput.asObservable();

  /**
   * Publishes a newly loaded report.
   *
   * @param chartInput - The report and the chart definition derived from it
   */
  publish(chartInput: HoldingsChartInput): void {
    this.chartInput.next(chartInput);
  }
}
