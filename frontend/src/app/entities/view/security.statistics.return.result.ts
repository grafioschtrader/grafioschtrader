export interface SecurityStatisticsReturnResult {
   annualisedPerformance: AnnualisedSecurityPerformance;
  summaryResult: SecurityStatisticsSummaryResult ;

}

export interface  AnnualisedSecurityPerformance {
  securityCurrency: string;
  mainCurrency: string;
  dateFrom: Date;
  dateTo: Date;
  lastYears: LastYears[];
  annualisedYears: AnnualisedYears[];
}

export interface SecurityStatisticsSummaryResult {
  dailyStandardDeviation: number;
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
