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

  /**
   * This string stores the limits and definitions of decimal numbers and
   * also the maximum length for entering comments.
   */
  STANDARD_CURRENCY_PRECISIONS_AND_LIMITS = 'standardPrecision'
}
