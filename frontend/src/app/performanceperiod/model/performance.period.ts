import { WeekYear } from '../service/holding.service';

/**
 * Response of GET /api/holding/{dateFrom}/{dateTo}/{periodSplit}. Mirrors the backend
 * grafioschtrader.reportviews.performance.PerformancePeriod.
 */
export interface PerformancePeriod {
  periodSplit: WeekYear | string;
  firstDayTotals: PeriodHoldingAndDiff;
  lastDayTotals: PeriodHoldingAndDiff;
  /** Last day less first day; the derived figures are recomputed from the subtracted ones, not subtracted themselves. */
  difference: PeriodHoldingAndDiff;
  /** Column-wise gain totals: five entries for a weekly split, twelve for a yearly one. */
  sumPeriodColSteps: number[];
  performanceChartDayDiff: PerformanceChartDayDiff[];
  periodWindows: PeriodWindow[];
  /** Return, risk and cost figures of the whole period; null while no holdings exist. */
  metrics: PerformancePeriodMetrics | null;
}

export interface PeriodWindow {
  startDate: string;
  endDate: Date | string;
  /** Gain of the whole window, null while it could not be formed from the window before it. */
  gainPeriodMC: number | null;
  /**
   * Time-weighted return of the window in percent, chained from the base of its first step to its last step. Null when
   * the window holds no step, for example only holidays or days with missing prices.
   */
  twrPercent: number | null;
  periodStepList: (PeriodStepMissingHoliday | PeriodStep)[];
}

export class PeriodWindowWithField {
  constructor(
    public showField: string,
    public periodWindow: PeriodWindow
  ) {}
}

export interface PeriodStepMissingHoliday {
  holidayMissing: HolidayMissing | string;
}

/**
 * One step of a window: a trading day in the weekly split, a month in the yearly split. Every amount is the change since
 * the previous step (its {@link baseDate}), not a level, and {@link totalBalanceMC} here is the change of cash plus
 * securities - unlike the field of the same name on {@link PeriodHoldingAndDiff}, which is a level and includes the
 * margin result. {@link twrPercent} is the time-weighted return from {@link baseDate} to {@link lastDate}.
 */
export interface PeriodStep extends PeriodStepMissingHoliday {
  lastDate: string;
  externalCashTransferMC: number;
  gainMC: number;
  marginCloseGainMC: number;
  cashBalanceMC: number;
  securitiesMC: number;
  totalBalanceMC: number;
  totalGainMC: number;
  missingDayCount: number;
  /** Date of the valuation the step is measured from; in the yearly split a month earlier unless a valuation is missing. */
  baseDate: string;
  /** Time-weighted return from baseDate to lastDate in percent; null without a usable daily return in between. */
  twrPercent: number | null;
  /** Whether the step covers exactly one trading day or exactly one month; only such steps are ranked best or worst. */
  complete: boolean;
}

export enum HolidayMissing {
  HM_NONE = 0,
  HM_TRADING_DAY = 1,
  HM_HOLIDAY = 2,
  HM_HISTORY_DATA_MISSING = 3,
  // Added for client only
  HM_OTHER_CELL = 4
}

/**
 * Aggregated totals of one day, or the difference between two of them. The cumulative columns are kept in the currency
 * of the cash account and revalued once with the exchange rate of the reporting day, so for a foreign-currency account
 * they deliberately differ from the cash account summary, which converts every booking at the rate of its own date.
 */
export interface PeriodHoldingAndDiff {
  date: string;
  dividendRealMC: number;
  /** Separately booked account and depot fees, delivered as a positive number although it is a cost. */
  feeRealMC: number;
  interestCashaccountRealMC: number;
  accumulateReduceMC: number;
  cashBalanceMC: number;
  /** Deposits less withdrawals. */
  externalCashTransferMC: number;
  securitiesMC: number;
  marginCloseGainMC: number;
  securityRiskMC: number;
  gainMC: number;
  /** Derived: cash + securities + margin result. */
  totalBalanceMC: number;
  /** Derived: securities + margin result. */
  securitiesAndMarginGainMC: number;
  /** Derived: gain + margin result. */
  totalGainMC: number;
}

export interface PerformanceChartDayDiff {
  date: string;
  externalCashTransferDiffMC: number;
  gainDiffMC: number;
  cashBalanceDiffMC: number;
  securitiesDiffMC: number;
  totalBalanceMC: number;
}

/**
 * Relative return, risk and cost figures of the period performance report. Mirrors the backend
 * grafioschtrader.reportviews.performance.PerformancePeriodMetrics. Percentages are in percent (12.34 means 12.34 %);
 * a figure that cannot be determined is null.
 */
export interface PerformancePeriodMetrics {
  /** Calendar days from the excluded base date to the last date. */
  calendarDays: number;
  /** Weekdays of the period that are no holiday of an exchange of the held instruments. */
  expectedSessions: number;
  /** Days of the period with complete prices. */
  valuedSessions: number;
  /** Returns spanning at least one expected trading day without complete prices. */
  gapIntervals: number;
  feesWithoutRate: number;
  /** Daily returns entering the volatility. */
  returnObservations: number;
  /** Time-weighted return in percent; null without a usable daily return. */
  twrPercent: number | null;
  /** Time-weighted return p.a. in percent; null below 360 calendar days. */
  twrAnnualizedPercent: number | null;
  /** Money-weighted return (internal rate of return) in percent; mwrStatus explains an empty value. */
  mwrPercent: number | null;
  /** Money-weighted return p.a. in percent; null below 360 calendar days. */
  mwrAnnualizedPercent: number | null;
  /** Outcome of the internal rate of return search, an NLS key such as MWR_CALCULATED. */
  mwrStatus: string;
  /** Largest decline from a previous high in percent (at most 0); null without a usable return. */
  maxDrawdownPercent: number | null;
  /** Date of the high before the largest decline. */
  drawdownPeakDate: string | null;
  /** Date of the low of the largest decline. */
  drawdownTroughDate: string | null;
  /** Date the previous high was regained; null when not regained within the period. */
  drawdownRecoveryDate: string | null;
  /** Distance of the last day from the highest value of the period in percent. */
  currentDrawdownPercent: number | null;
  /** Annualized volatility of the regular daily returns in percent; null below 20 returns. */
  volatilityAnnualizedPercent: number | null;
  /** Best complete step (day or month) in percent. */
  bestStepPercent: number | null;
  /** Last date of the best complete step. */
  bestStepDate: string | null;
  /** Worst complete step (day or month) in percent. */
  worstStepPercent: number | null;
  /** Last date of the worst complete step. */
  worstStepDate: string | null;
  /** Capital at the start of each interval weighted with its calendar days, in main currency. */
  averageCapitalMC: number | null;
  /** Separately booked account and custody fees of the period in main currency, a charge is positive. */
  feesMC: number;
  /** Fees in percent of the average invested capital; null when that capital is not positive. */
  feeRatioPercent: number | null;
  /** Fee ratio scaled linearly to 365 days; null below 360 calendar days. */
  feeRatioAnnualizedPercent: number | null;
}
