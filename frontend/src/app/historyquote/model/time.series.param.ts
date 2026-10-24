/**
 * One instrument shown in the time series chart. Kept apart from the chart component, because
 * TimeSeriesQuotesService creates it from the application shell, which must not import the chart itself.
 */
export class TimeSeriesParam {
  constructor(
    public idSecuritycurrency: number,
    public currencySecurity: string,
    public idPortfolio: number,
    public idSecurityaccount: number,
    fileGaps = true
  ) {}
}
