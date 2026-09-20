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
}

export interface PeriodWindow {
  startDate: string;
  endDate: Date | string;
  /** Gain of the whole window, null while it could not be formed from the window before it. */
  gainPeriodMC: number | null;
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
 * One trading day of a window. Every amount is the change since the day before, not a level, and
 * {@link totalBalanceMC} here is the change of cash plus securities - unlike the field of the same name on
 * {@link PeriodHoldingAndDiff}, which is a level and includes the margin result.
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
