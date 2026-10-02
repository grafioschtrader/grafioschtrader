package grafioschtrader.service;

import java.util.Set;

/**
 * NLS keys persisted in {@code algo_alert_evaluation_state.reason}. The alert diagnostics dialog translates the column
 * value, so every fixed reason is stored as a key of the application message bundle. Free text, such as the message of
 * an unexpected exception, is shown untranslated.
 */
public final class AlgoAlertReason {
  public static final String INSTRUMENT_INACTIVE = "ALERT_REASON_INSTRUMENT_INACTIVE";
  public static final String MISSING_EXCHANGE = "ALERT_REASON_MISSING_EXCHANGE";
  public static final String INVALID_EXCHANGE_HOURS = "ALERT_REASON_INVALID_EXCHANGE_HOURS";
  public static final String INVALID_EXCHANGE_ZONE = "ALERT_REASON_INVALID_EXCHANGE_ZONE";
  public static final String EXCHANGE_WEEKEND = "ALERT_REASON_EXCHANGE_WEEKEND";
  public static final String EXCHANGE_NON_TRADING_DAY = "ALERT_REASON_EXCHANGE_NON_TRADING_DAY";
  public static final String EXCHANGE_NOT_OPEN = "ALERT_REASON_EXCHANGE_NOT_OPEN";
  public static final String WAITING_CLOSING_QUOTE = "ALERT_REASON_WAITING_CLOSING_QUOTE";
  public static final String QUOTE_REFRESH_FAILED = "ALERT_REASON_QUOTE_REFRESH_FAILED";
  public static final String QUOTE_MISSING_OR_STALE = "ALERT_REASON_QUOTE_MISSING_OR_STALE";
  public static final String EVALUATION_FAILED = "ALERT_REASON_EVALUATION_FAILED";
  public static final String HOLDING_PERCENTAGE_UNAVAILABLE = "ALERT_REASON_HOLDING_PERCENTAGE_UNAVAILABLE";

  /** Configuration failures: they receive a diagnostic at the ordinary interval without downloading anything. */
  public static final Set<String> CONFIGURATION = Set.of(MISSING_EXCHANGE, INVALID_EXCHANGE_HOURS,
      INVALID_EXCHANGE_ZONE);

  private AlgoAlertReason() {
  }
}
