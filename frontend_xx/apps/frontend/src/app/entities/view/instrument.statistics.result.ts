export interface InstrumentStatisticsResult {
  annualisedPerformance: AnnualisedPerformance;
  statisticsSummary: StatisticsSummary;
}

export interface AnnualisedPerformance {
  securityCurrency: string;
  mainCurrency: string;
  dateFrom: Date;
  dateTo: Date;
  lastYears: LastYears[];
  annualisedYears: AnnualisedYears[];
}

export interface StatisticsSummary {
  statsPropertyMap: { [key: string]: StatsProperty[] };
}

export interface StatsProperty {
  property: string;
  value: number;
  valueMC: number;
}

export interface LastYears {
  year: number;
  performanceYear: number;
  performanceYearMC: number;
}

export interface AnnualisedYears {
  numberOfYears: number;
  performanceAnnualised: number;
  performanceAnnualisedMC: number;
}
