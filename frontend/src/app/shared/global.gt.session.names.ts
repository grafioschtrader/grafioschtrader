export enum GlobalGTSessionNames {
  /** As long as you wait from one intraday price update to the next. */
  UPDATE_TIME_OUT = 'updateTimeout',

  /** This represents the earliest date from which the system will attempt to collect historical data.  */
  START_FEED_DATE = 'startFeedDate',

  /** Contains the supported crypto currencies. */
  CRYPTOS = 'crypotcurrencies',

  /** Which tab was last selected in the Tenant tab. */
  TAB_MENU_TENANT = 'tabMenuTenant',

  /** Which tab was last selected in the Portfolio tab. */
  TAB_MENU_PORTFOLIO = 'tabMenuPortfolio',

  /**
   * Tenants and portfolios can calculate performance from a specific date.
   * This date should not be lost when changing views.
   */
  PERFORMANCE_DATE_FROM = 'performanceDateFrom',

  /** In certain reports there is a "to date", this can be set by the user. It is stored in the session storage so that
   *  it works across all reports.
   */
  REPORT_UNTIL_DATE = 'untilDate',


}
