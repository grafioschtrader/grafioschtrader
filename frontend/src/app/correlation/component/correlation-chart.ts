import { CorrelationChartInput } from '../service/correlation-chart.service';
import { Security } from '../../entities/security';
import { Securitycurrency } from '../../entities/securitycurrency';

/** Display-only choices; no persisted business value is sent to the server. */
export enum CorrelationChartType {
  Combined = 'CORRELATION_CHART_COMBINED',
  Correlation = 'CORRELATION_CHART_ONLY'
}

/** Expresses closes as percentage changes from the common start, without bridging missing quotes. */
export function percentageCloses(closes: number[]): (number | null)[] | null {
  const start = closes?.[0];
  if (start == null || !Number.isFinite(start) || start === 0) {
    return null;
  }
  return closes.map((close) => {
    const value = close == null ? NaN : (close / start - 1) * 100;
    return Number.isFinite(value) ? value : null;
  });
}

/** Builds the two figures from the same response; changing the view never reloads or rebases the data. */
export function buildCorrelationFigure(input: CorrelationChartInput, type: CorrelationChartType) {
  const combined = type === CorrelationChartType.Combined;
  const results = input.results.filter((result) => result.dates.length > 0);
  const data: any[] = [];
  const legendTooltipMap = new Map<string, string>();
  const instruments = new Set<number>();
  let missingCloses = false;
  const name = (sc: Securitycurrency) => (sc as Security).tickerSymbol || sc.name;
  results.forEach((result) => {
    if (combined) {
      result.securitycurrencyList.forEach((sc, index) => {
        if (instruments.has(sc.idSecuritycurrency)) {
          return;
        }
        instruments.add(sc.idSecuritycurrency);
        const values = percentageCloses(result.closes?.[index]);
        if (!values || !result.closeDates?.length || values.length !== result.closeDates.length) {
          missingCloses = true;
          return;
        }
        const label = name(sc);
        legendTooltipMap.set(label, sc.name);
        data.push({
          type: 'scatter',
          mode: 'lines',
          name: label,
          uid: `price-${sc.idSecuritycurrency}`,
          x: result.closeDates,
          y: values,
          xaxis: 'x',
          yaxis: 'y2',
          connectgaps: false,
          hovertemplate: '%{x}<br>%{y:.2f}%<extra>%{fullData.name}</extra>'
        });
      });
    }
    const label = result.securitycurrencyList.map(name).join(' ↔ ');
    legendTooltipMap.set(label, result.securitycurrencyList.map((sc) => sc.name).join(' ↔ '));
    data.push({
      type: 'scatter',
      mode: 'lines',
      name: label,
      uid: `correlation-${result.securitycurrencyList.map((sc) => sc.idSecuritycurrency).join('-')}`,
      x: result.dates,
      y: result.correlation,
      xaxis: 'x',
      yaxis: 'y',
      connectgaps: false
    });
  });
  const dates = results.flatMap((result) => [result.dates[0], result.dates[result.dates.length - 1]]).sort();
  const range = dates.length ? [dates[0], dates[dates.length - 1]] : undefined;
  const layout: any = {
    title: { text: input.title },
    showlegend: true,
    legend: { orientation: 'h', x: 0, y: -0.3, xanchor: 'left', yanchor: 'top', font: { size: 11 } },
    margin: { l: 65, r: 25, t: 50, b: 65 },
    xaxis: { type: 'date', range, anchor: 'y', rangeslider: { range, thickness: 0.06 } },
    yaxis: {
      domain: combined ? [0, 0.32] : [0, 1],
      range: [-1, 1],
      autorange: false,
      fixedrange: true,
      title: { text: 'CORRELATION_CHART_ONLY' }
    }
  };
  if (combined) {
    layout.yaxis2 = {
      domain: [0.43, 1],
      anchor: 'x',
      autorange: true,
      ticksuffix: '%',
      fixedrange: false,
      title: { text: 'CORRELATION_CHART_PRICES' }
    };
  }
  return { data, layout, legendTooltipMap, missingCloses };
}
