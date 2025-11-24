import {WeekYear} from '../service/holding.service';


export interface PerformancePeriod {
  periodSplit: WeekYear | string;
  firstDayTotals: PeriodHoldingAndDiff;
  lastDayTotals: PeriodHoldingAndDiff;
  difference: PeriodHoldingAndDiff;
  sumPeriodColSteps: number[];
  performanceChartDayDiff: PerformanceChartDayDiff[];
  periodWindows: PeriodWindow[];
}

export interface PeriodWindow {
  startDate: string;
  endDate: Date | string;
  periodStepList: (PeriodStepMissingHoliday | PeriodStep)[];
}

export class PeriodWindowWithField {
  constructor(public showField: string, public periodWindow: PeriodWindow) {
  }
}

export interface PeriodStepMissingHoliday {
  holidayMissing: HolidayMissing | string;
}

export interface PeriodStep extends PeriodStepMissingHoliday {
  depositMC: number;
  gainMC: number;
  balanceMC: number;
  securitiesMC: number;
  totalBalanceMC: number;
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

export interface PeriodHoldingAndDiff {
  date: string;
  dividendMC: number;
  feeMC: number;
  interestCashaccountMC: number;
  accumulateReduceMC: number;
  balanceMC: number;
  depositMC: number;
  securitiesMC: number;
  gainMC: number;
  totalBalanceMC: number;
}

export interface PerformanceChartDayDiff {
  date: string;
  externalCashTransferDiffMC: number;
  gainDiffMC: number;
  cashBalanceDiffMC: number;
  securitiesDiffMC: number;
  totalBalanceMC: number;
}
