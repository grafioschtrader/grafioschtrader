import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { AppHelper } from '../../lib/helper/app.helper';
import { HoldingsTreemap, HoldingsTreemapNode } from '../../entities/view/holdings.treemap';

/**
 * Maps the holdings treemap built by the backend to a Plotly treemap trace. It only maps and formats; every value and
 * every share comes from the backend, so nothing is summed here. Each tile shows the share of the report total, the
 * same number as the column "Share %" of the table; the hover adds the share in the parent node.
 */
export class HoldingsTreemapChart {
  private readonly amountField: ColumnConfig;
  private readonly percentageField: ColumnConfig;

  /**
   * @param translateService - Translates asset class names, labels and the reasons of excluded values
   * @param gps - Provides the locale dependent number formats
   * @param mainCurrency - Main currency of the tenant, determines the precision of the amounts
   */
  constructor(
    private translateService: TranslateService,
    private gps: GlobalparameterService,
    private mainCurrency: string
  ) {
    this.amountField = ShowRecordConfigBase.createColumnConfig(DataType.NumericShowZero, 'value', null);
    this.amountField.fixedCurrency = mainCurrency;
    this.percentageField = ShowRecordConfigBase.createColumnConfig(DataType.NumericRaw, 'value', null);
  }

  /**
   * Creates the Plotly figure of the treemap.
   *
   * @param title - Report title, used as chart title and as label of the root node
   * @param treemap - Nodes and excluded values from the backend
   * @returns Plotly data and layout
   */
  getChartDefinition(title: string, treemap: HoldingsTreemap): { data: any[]; layout: any } {
    const nodes = treemap.nodes;
    const data = [
      {
        type: 'treemap',
        ids: nodes.map((n) => n.id),
        parents: nodes.map((n) => n.parentId ?? ''),
        labels: nodes.map((n) => this.getLabel(n, title)),
        values: nodes.map((n) => n.valueMC),
        branchvalues: 'remainder',
        sort: false,
        customdata: nodes.map((n) => this.getCustomData(n)),
        texttemplate: '%{label}<br>%{customdata[1]}',
        hovertemplate:
          '<b>%{label}</b><br>%{customdata[0]}<br>%{customdata[1]} / %{percentParent:.1%}' +
          '%{customdata[2]}%{customdata[3]}<extra></extra>',
        pathbar: { visible: true }
      }
    ];
    const layout: any = {
      title: { text: title, font: { size: 15 } },
      margin: { t: 50, l: 10, r: 10, b: treemap.excluded.length > 0 ? 50 : 10 }
    };
    if (treemap.excluded.length > 0) {
      layout.annotations = [this.createExcludedAnnotation(treemap)];
    }
    return { data, layout };
  }

  private getLabel(node: HoldingsTreemapNode, title: string): string {
    switch (node.nodeType) {
      case 'ROOT':
        return title;
      case 'ASSETCLASS':
        return node.label ? this.translateService.instant(node.label) : '?';
      default:
        return node.label;
    }
  }

  /** Per node: formatted value with currency, formatted share, gain/loss line and margin line of the hover. */
  private getCustomData(node: HoldingsTreemapNode): string[] {
    const gainLoss =
      node.positionGainLossPercentage != null
        ? `<br>${this.translateService.instant('POSITION_GAIN_LOSS_PERCENTAGE')}: ` +
          `${this.formatPercentage(node.positionGainLossPercentage)} %`
        : '';
    const margin =
      node.marginGainLossMC != null
        ? `<br>${this.translateService.instant('TREEMAP_MARGIN_UNREALIZED')}: ` +
          `${this.formatAmount(node.marginGainLossMC)} ${this.mainCurrency}` +
          (node.marginPositionNames?.length ? ` (${node.marginPositionNames.join(', ')})` : '')
        : '';
    const share = node.shareOfTotalPercentage != null ? `${this.formatPercentage(node.shareOfTotalPercentage)} %` : '';
    return [`${this.formatAmount(node.totalValueMC)} ${this.mainCurrency}`, share, gainLoss, margin];
  }

  /** One line below the plot naming every value that is not drawn, so the tiles still reconcile with the total. */
  private createExcludedAnnotation(treemap: HoldingsTreemap): any {
    const entries = treemap.excluded.map(
      (e) =>
        `${e.label} ${this.formatAmount(e.valueMC)} ${this.mainCurrency} ` +
        `(${this.translateService.instant(e.reasonKey)})`
    );
    return {
      text: `${this.translateService.instant('TREEMAP_NOT_SHOWN')}: ${entries.join('; ')}`,
      showarrow: false,
      xref: 'paper',
      yref: 'paper',
      x: 0,
      y: 0,
      xanchor: 'left',
      yanchor: 'top',
      yshift: -10,
      align: 'left'
    };
  }

  private formatAmount(value: number): string {
    return AppHelper.getValueByPathWithField(this.gps, this.translateService, { value }, this.amountField, 'value');
  }

  private formatPercentage(value: number): string {
    return (
      AppHelper.getValueByPathWithField(this.gps, this.translateService, { value }, this.percentageField, 'value') ??
      '0'
    );
  }
}
