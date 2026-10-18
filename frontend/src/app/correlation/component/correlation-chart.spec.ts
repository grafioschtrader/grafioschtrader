import { describe, expect, it } from 'vitest';
import { buildCorrelationFigure, CorrelationChartType, percentageCloses } from './correlation-chart';
import { CorrelationChartInput } from '../service/correlation-chart.service';
import { Security } from '../../entities/security';

describe('correlation price comparison', () => {
  it('shows gains and losses relative to a zero-percent start without bridging missing prices', () => {
    expect(percentageCloses([80, 100, null, 60])).toEqual([0, 25, null, -25]);
    expect(percentageCloses([0, 100])).toBeNull();
    expect(percentageCloses([null, 100])).toBeNull();
    expect(percentageCloses([])).toBeNull();
  });

  const security = (id: number) => Object.assign(new Security(), { idSecuritycurrency: id, name: `Instrument ${id}` });
  const input: CorrelationChartInput = {
    title: 'CORRELATION_CHART_ONLY',
    includeDividends: false,
    adjustCurrency: false,
    results: [
      {
        securitycurrencyList: [security(1), security(2)],
        dates: ['2026-03-31', '2026-04-30'],
        correlation: [0.5, -0.5],
        closeDates: ['2026-03-31', '2026-04-01', '2026-04-30'],
        closes: [
          [80, 100, 60],
          [100, 110, 120]
        ]
      },
      {
        securitycurrencyList: [security(2), security(3)],
        dates: ['2026-03-31', '2026-04-30'],
        correlation: [0.7, 0.8],
        closeDates: ['2026-03-31', '2026-04-01', '2026-04-30'],
        closes: [
          [100, 110, 120],
          [20, 30, 40]
        ]
      }
    ]
  };

  it('plots each shared instrument once, preserves daily points and both monthly correlations', () => {
    const combined = buildCorrelationFigure(input, CorrelationChartType.Combined);
    const prices = combined.data.filter((trace) => trace.yaxis === 'y2');
    expect(prices).toHaveLength(3);
    expect(prices[0].x).toEqual(['2026-03-31', '2026-04-01', '2026-04-30']);
    expect(prices[0].y).toEqual([0, 25, -25]);
    const correlations = combined.data.filter((trace) => trace.yaxis === 'y');
    expect(correlations.map((trace) => trace.y)).toEqual([
      [0.5, -0.5],
      [0.7, 0.8]
    ]);
    const standalone = buildCorrelationFigure(input, CorrelationChartType.Correlation);
    expect(standalone.data).toEqual(correlations);
    expect(buildCorrelationFigure(input, CorrelationChartType.Combined).data).toEqual(combined.data);
  });

  it('keeps the correlation visible when a price series cannot be normalized', () => {
    const invalid = structuredClone(input);
    invalid.results[0].closes[0][0] = 0;
    const figure = buildCorrelationFigure(invalid, CorrelationChartType.Combined);
    expect(figure.missingCloses).toBe(true);
    expect(figure.data.filter((trace) => trace.yaxis === 'y')).toHaveLength(2);
    expect(figure.data.filter((trace) => trace.yaxis === 'y2')).toHaveLength(2);
    expect(buildCorrelationFigure({ ...input, results: [] }, CorrelationChartType.Combined).data).toEqual([]);
  });
});
