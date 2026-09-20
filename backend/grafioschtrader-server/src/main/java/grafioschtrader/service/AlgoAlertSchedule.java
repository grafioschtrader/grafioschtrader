package grafioschtrader.service;

import java.time.*;
import java.util.Set;

import grafioschtrader.entities.AlgoAlertEvaluationState;

/**
 * Clock-independent market-session and due-time rules for the alerts. Persisted timestamps and quote timestamps are
 * UTC.
 */
public final class AlgoAlertSchedule {
  private AlgoAlertSchedule() {
  }

  /** An eligible session, or a reason why automatic evaluation must wait. */
  public record Window(LocalDateTime open, LocalDateTime close, boolean closing, String reason) {
    public boolean eligible() {
      return reason == null;
    }
  }

  /**
   * Resolves the current session or today's final closing check. Weekends refer to the exchange's local date. Equal
   * opening/closing times are invalid; cryptocurrency instruments explicitly bypass exchange restrictions.
   */
  public static Window window(Instant now, boolean crypto, String zone, LocalTime open, LocalTime close,
      Set<LocalDate> holidays, int delaySeconds) {
    if (crypto)
      return new Window(null, null, false, null);
    if (zone == null || open == null || close == null || open.equals(close))
      return new Window(null, null, false, "Missing or invalid exchange hours");
    try {
      ZoneId exchangeZone = ZoneId.of(zone);
      ZonedDateTime local = now.atZone(exchangeZone);
      LocalDate date = local.toLocalDate();
      if (weekend(date))
        return new Window(null, null, false, "Exchange weekend");
      boolean overnight = open.isAfter(close);
      LocalDate start = overnight && local.toLocalTime().isBefore(open) ? date.minusDays(1) : date;
      if (weekend(start) || holidays.contains(start))
        return new Window(null, null, false, "Exchange non-trading day");
      Instant opening = start.atTime(open).atZone(exchangeZone).toInstant();
      Instant closing = (overnight ? start.plusDays(1) : start).atTime(close).atZone(exchangeZone).toInstant();
      LocalDateTime openingUtc = LocalDateTime.ofInstant(opening, ZoneOffset.UTC);
      LocalDateTime closingUtc = LocalDateTime.ofInstant(closing, ZoneOffset.UTC);
      if (now.isBefore(opening))
        return new Window(openingUtc, closingUtc, false, "Exchange not open yet");
      if (now.isBefore(closing))
        return new Window(openingUtc, closingUtc, false, null);
      if (now.isBefore(closing.plusSeconds(Math.max(0, delaySeconds))))
        return new Window(openingUtc, closingUtc, false, "Waiting for closing quote");
      return new Window(openingUtc, closingUtc, true, null);
    } catch (DateTimeException e) {
      return new Window(null, null, false, "Invalid exchange time zone");
    }
  }

  private static boolean weekend(LocalDate date) {
    return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
  }

  /** Checks attempt and success times separately: failed attempts must not cause a five-minute retry loop. */
  public static boolean due(AlgoAlertEvaluationState state, String fingerprint, LocalDateTime now, int hours,
      Window window) {
    if (!window.eligible())
      return false;
    if (state == null)
      return true;
    if (state.getLeaseUntil() != null && state.getLeaseUntil().isAfter(now))
      return false;
    if (!fingerprint.equals(state.getConfigFingerprint()))
      return true;
    if (window.closing()) {
      return !window.close().equals(state.getClosingAttempt())
          && (state.getQuoteTimestamp() == null || state.getQuoteTimestamp().isBefore(window.close()));
    }
    LocalDateTime threshold = now.minusHours(hours);
    return (state.getLastAttempt() == null || !state.getLastAttempt().isAfter(threshold))
        && (state.getLastSuccess() == null || !state.getLastSuccess().isAfter(threshold));
  }

  /** A successful HTTP request alone does not make a quote fresh. */
  public static boolean fresh(Double price, LocalDateTime quote, LocalDateTime now, int hours, Window window) {
    return price != null && Double.isFinite(price) && quote != null && !quote.isAfter(now)
        && !quote.isBefore(now.minusHours(hours)) && (window.open() == null || !quote.isBefore(window.open()))
        && (!window.closing() || !quote.isBefore(window.close()));
  }
}
