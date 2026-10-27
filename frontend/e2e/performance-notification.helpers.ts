import type { PerformancePeriod, PeriodHoldingAndDiff } from '../src/app/performanceperiod/model/performance.period';

/** Display fixtures only: no transaction, valuation, report setting or generated PDF reaches the database. */
export const performanceDates = {
  maxWeekLimit: 104,
  minIncludeMonthLimit: 0,
  firstEverTradingDay: '2025-01-02',
  secondEverTradingDay: '2025-01-03',
  lastTradingDayOfLastYear: '2025-12-31',
  secondLatestTradingDay: '2026-03-30',
  latestTradingDay: '2026-03-31',
  holidayAndMissingQuoteDays: []
};

function totals(date: string, gain = 0): PeriodHoldingAndDiff {
  return {
    date,
    dividendRealMC: 0,
    feeRealMC: 0,
    interestCashaccountRealMC: 0,
    accumulateReduceMC: 0,
    cashBalanceMC: 1000,
    externalCashTransferMC: 0,
    securitiesMC: 2000,
    marginCloseGainMC: 0,
    securityRiskMC: 2000,
    gainMC: gain,
    totalBalanceMC: 3000,
    securitiesAndMarginGainMC: 2000,
    totalGainMC: gain
  };
}

export function periodResult(split = 'WM_YEAR', gain = 333.75): PerformancePeriod {
  const count = split === 'WM_YEAR' ? 12 : 5;
  const steps = Array.from({ length: count }, (_, index) => ({
    holidayMissing: 'HM_TRADING_DAY',
    lastDate: '2026-03-31',
    baseDate: '2025-12-31',
    externalCashTransferMC: 0,
    gainMC: index === 0 ? gain : 0,
    marginCloseGainMC: 0,
    cashBalanceMC: 0,
    securitiesMC: index === 0 ? gain : 0,
    totalBalanceMC: index === 0 ? gain : 0,
    totalGainMC: index === 0 ? gain : 0,
    missingDayCount: 0,
    twrPercent: 12.34,
    complete: true
  }));
  return {
    periodSplit: split,
    firstDayTotals: totals('2025-12-31'),
    lastDayTotals: totals('2026-03-31', gain),
    difference: totals('2026-03-31', gain),
    sumPeriodColSteps: steps.map((step) => step.totalGainMC),
    performanceChartDayDiff: [],
    periodWindows: [
      { startDate: '2026-01-01', endDate: '2026-03-31', gainPeriodMC: gain, twrPercent: 12.34, periodStepList: steps }
    ],
    metrics: {
      calendarDays: 90,
      expectedSessions: 63,
      valuedSessions: 63,
      gapIntervals: 0,
      feesWithoutRate: 0,
      returnObservations: 62,
      twrPercent: 12.34,
      twrAnnualizedPercent: null,
      mwrPercent: 12.34,
      mwrAnnualizedPercent: null,
      mwrStatus: 'MWR_CALCULATED',
      maxDrawdownPercent: -2.5,
      drawdownPeakDate: '2026-01-05',
      drawdownTroughDate: '2026-01-06',
      drawdownRecoveryDate: '2026-01-07',
      currentDrawdownPercent: 0,
      volatilityAnnualizedPercent: 8,
      bestStepPercent: 2,
      bestStepDate: '2026-01-07',
      worstStepPercent: -2.5,
      worstStepDate: '2026-01-06',
      averageCapitalMC: 3000,
      feesMC: 0,
      feeRatioPercent: 0,
      feeRatioAnnualizedPercent: null
    }
  };
}
