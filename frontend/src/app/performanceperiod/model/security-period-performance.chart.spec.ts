import { describe, expect, it } from 'vitest';
import type { SecurityPeriodPerformanceRow } from './security-period-performance';
import { securityPerformanceChartPoints, securityPerformanceTreemap } from './security-period-performance.chart';

function row(
  id: number,
  holdingDays: number,
  gainPeriodMC: number | null,
  twrPercent: number | null
): SecurityPeriodPerformanceRow {
  return {
    idSecuritycurrency: id,
    name: 'S' + id,
    isin: null,
    currency: 'CHF',
    marginInstrument: false,
    holdingDays,
    unitsStart: null,
    unitsEnd: null,
    valueStartMC: null,
    valueEndMC: null,
    inflowMC: null,
    outflowMC: null,
    gainPeriod: gainPeriodMC,
    gainPeriodMC,
    twrPercent,
    twrSecurityCurrencyPercent: twrPercent,
    remark: null
  };
}

describe('securityPerformanceChartPoints', () => {
  it('draws only rows with return and gain, keeps their securities aligned and sizes the points by days held', () => {
    const points = securityPerformanceChartPoints(
      [row(1, 360, 500, 12), row(2, 90, -40, -8), row(3, 360, 200, null), row(4, 0, 15, 3)],
      { title: 'T', gain: 'Gain', twr: 'TWR', holdingDays: 'Days' },
      { amount: String, percent: String, days: String }
    );
    expect(points.idsSecuritycurrency).toEqual([1, 2, 4]);
    expect(points.notShown).toBe(1);
    expect(points.trace.x).toEqual([12, -8, 3]);
    expect(points.trace.y).toEqual([500, -40, 15]);
    const marker = points.trace.marker as { size: number[]; color: string[] };
    expect(marker.size).toEqual([26, 16.5, 7]);
    expect(marker.color[1]).not.toBe(marker.color[0]);
    expect(points.trace.customdata).toEqual([
      ['S1', '500', '12', '360'],
      ['S2', '-40', '-8', '90'],
      ['S4', '15', '3', '0']
    ]);
  });
});

describe('securityPerformanceTreemap', () => {
  it('puts profits and losses into two branches whose tiles are the absolute amounts', () => {
    const treemap = securityPerformanceTreemap(
      [row(1, 360, 500, null), row(2, 90, -40, -8), row(3, 360, null, null), row(4, 30, 0, 0), row(5, 10, 100, 2)],
      { root: 'Period', gains: 'Profits', losses: 'Losses', holdingDays: 'Days', share: 'Share' },
      { amount: String, percent: String, days: String }
    );
    expect(treemap.trace['ids']).toEqual(['root', 'gains', 'gains-1', 'gains-5', 'losses', 'losses-2']);
    expect(treemap.trace['parents']).toEqual(['', 'root', 'gains', 'gains', 'root', 'losses']);
    expect(treemap.trace['values']).toEqual([0, 0, 500, 100, 0, 40]);
    expect(treemap.idsSecuritycurrency).toEqual([null, null, 1, 5, null, 2]);
    expect(treemap.gainsMC).toBe(600);
    expect(treemap.lossesMC).toBe(40);
    expect(treemap.notShown).toBe(2);
    expect((treemap.trace['customdata'] as string[][])[5]).toEqual(['-40', '<br>Days: 90']);
  });

  it('leaves out a branch without securities', () => {
    const treemap = securityPerformanceTreemap(
      [row(1, 360, 500, 12)],
      { root: 'Period', gains: 'Profits', losses: 'Losses', holdingDays: 'Days', share: 'Share' },
      { amount: String, percent: String, days: String }
    );
    expect(treemap.trace['ids']).toEqual(['root', 'gains', 'gains-1']);
    expect(treemap.lossesMC).toBe(0);
  });
});
