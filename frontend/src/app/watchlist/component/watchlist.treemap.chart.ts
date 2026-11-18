import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { AppHelper } from '../../lib/helper/app.helper';
import { WatchlistTreemap, WatchlistTreemapNode } from '../model/watchlist-treemap';

/** Changes beyond this percentage take the end colour, so that a single outlier does not bleach the other tiles. */
const COLOR_LIMIT_PERCENTAGE = 3;
const LOSS_COLOR = '#b3261e';
const NEUTRAL_COLOR = '#5f6368';
const GAIN_COLOR = '#1e8e3e';
/**
 * Narrower header band and borders than Plotly's default, so a group with a small capitalisation, such as a single
 * foreign stock beside large domestic ones, keeps enough room to show the label of its stock without zooming.
 */
const GROUP_PADDING = { t: 18, l: 2, r: 2, b: 2 };

/**
 * Maps the market map built by the backend to a Plotly treemap trace. It only maps and formats; every value comes from
 * the backend. The change is printed on every tile, so the colour is never its only carrier.
 */
export class WatchlistTreemapChart {
  private readonly amountField: ColumnConfig;
  private readonly percentageField: ColumnConfig;
  private readonly timeField: ColumnConfig;

  /**
   * @param translateService - Translates the labels and formats values
   * @param gps - Provides the locale dependent number and date formats
   */
  constructor(
    private translateService: TranslateService,
    private gps: GlobalparameterService
  ) {
    this.amountField = ShowRecordConfigBase.createColumnConfig(DataType.NumericInteger, 'value', null);
    this.percentageField = ShowRecordConfigBase.createColumnConfig(DataType.NumericRaw, 'value', null);
    this.timeField = ShowRecordConfigBase.createColumnConfig(DataType.DateTimeString, 'value', null);
  }

  /**
   * Creates the Plotly figure of the market map.
   *
   * @param rootLabel - Label of the root node
   * @param treemap - Nodes from the backend, at least the root
   * @returns Plotly data and layout
   */
  getChartDefinition(rootLabel: string, treemap: WatchlistTreemap): { data: any[]; layout: any } {
    const nodes = treemap.nodes;
    const data = [
      {
        type: 'treemap',
        ids: nodes.map((n) => n.id),
        parents: nodes.map((n) => n.parentId ?? ''),
        labels: nodes.map((n) => n.label ?? (n.nodeType === 'ROOT' ? rootLabel : '?')),
        values: nodes.map((n) => n.valueMC),
        branchvalues: 'remainder',
        sort: false,
        marker: {
          colors: nodes.map((n) => n.changePercentage),
          colorscale: [
            [0, LOSS_COLOR],
            [0.5, NEUTRAL_COLOR],
            [1, GAIN_COLOR]
          ],
          cmin: -COLOR_LIMIT_PERCENTAGE,
          cmid: 0,
          cmax: COLOR_LIMIT_PERCENTAGE,
          showscale: true,
          colorbar: { ticksuffix: ' %', thickness: 12 },
          pad: GROUP_PADDING
        },
        customdata: nodes.map((n) => this.getCustomData(n, treemap.mainCurrency)),
        texttemplate: '%{label}<br>%{customdata[1]}',
        hovertemplate: '<b>%{label}</b><br>%{customdata[0]}<br>%{customdata[1]}%{customdata[2]}<extra></extra>',
        pathbar: { visible: true }
      }
    ];
    return { data, layout: { margin: { t: 30, l: 10, r: 10, b: 10 } } };
  }

  /** Per node: formatted capitalisation with currency, formatted change and the hover line with the time of the price. */
  private getCustomData(node: WatchlistTreemapNode, mainCurrency: string): string[] {
    const time = node.timestamp ? `<br>${this.format(node.timestamp, this.timeField)}` : '';
    return [`${this.format(node.totalValueMC, this.amountField)} ${mainCurrency}`, this.formatChange(node), time];
  }

  private formatChange(node: WatchlistTreemapNode): string {
    const sign = node.changePercentage > 0 ? '+' : '';
    return `${sign}${this.format(node.changePercentage, this.percentageField) ?? '0'} %`;
  }

  private format(value: number | string, field: ColumnConfig): string {
    return AppHelper.getValueByPathWithField(this.gps, this.translateService, { value }, field, 'value');
  }
}
