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

  /**
   * Tenant-level closed-until date for transaction period locking.
   * Transactions on or before this date are protected from modification.
   */
  TENANT_CLOSED_UNTIL = 'tenantClosedUntil',

  /**
   * The import platform of this instance holding the GT authored import templates, chosen by an administrator.
   * Empty when no platform is configured, in which case the templates are unavailable to every client.
   */
  GT_IMPORT_PLATFORM_ID = 'gtImportPlatformId',

  /**
   * Whether this tenant opted in to the GT authored import templates. Together with a configured
   * GT_IMPORT_PLATFORM_ID it makes the transaction import entry points offer the "use GT platform" choice.
   */
  TENANT_USE_GT_IMPORT_TEMPLATES = 'tenantUseGtImportTemplates',

  /**
   * Connector / asset class compatibility enforcement mode (gt.force.connector.match): 0/1/2.
   */
  FORCE_CONNECTOR_MATCH = 'forceConnectorMatch',

  /**
   * Per-instrument editing limits (max splits / max history-quote periods) as a JSON-serialized
   * {@link MaxInstrumentLimits} object.
   */
  MAX_INSTRUMENT_LIMITS = 'maxInstrumentLimits'
}
