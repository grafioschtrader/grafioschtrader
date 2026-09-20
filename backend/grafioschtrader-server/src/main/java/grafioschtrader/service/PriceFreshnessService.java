package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.dto.IStockexchangeTradingDate;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;

/**
 * Measures how old the price of an instrument is, counted in trading sessions of the exchange it belongs to.
 *
 * <p>
 * Counting calendar days would report every Monday morning as two days of delay and would turn a market wide holiday
 * into an alarm, so the age is expressed in sessions the exchange really held. A price from the last completed session
 * is current and yields zero, no matter how many days of weekend or holiday lie in between.
 * </p>
 *
 * <p>
 * Currency pairs belong to no exchange. They are measured against the worldwide calendar alone, which is the closest
 * thing to a session list a foreign exchange rate has.
 * </p>
 */
@Service
public class PriceFreshnessService {

  /**
   * Highest age reported. Beyond it an instrument is simply stale; distinguishing forty from four hundred sessions
   * carries no information for the user and would only stretch the colour ramp of the user interface.
   */
  public static final int MAX_STALE_TRADING_SESSIONS = 10;

  /**
   * Length of the loaded session window in calendar days. It has to cover {@link #MAX_STALE_TRADING_SESSIONS} sessions
   * even when a long holiday period falls into it, which ninety days do with a wide margin.
   */
  private static final int SESSION_WINDOW_DAYS = 90;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  /**
   * Loads the sessions needed to age prices of the given exchanges up to the current day.
   *
   * @param idsStockexchange the exchanges appearing in the report, may be empty
   * @param today            the current day, the exclusive upper bound of every age
   * @return sessions per exchange, newest first; an exchange without any session is absent
   */
  @Transactional(readOnly = true)
  public Map<Integer, List<LocalDate>> loadSessions(final Collection<Integer> idsStockexchange, final LocalDate today) {
    if (idsStockexchange.isEmpty()) {
      return Collections.emptyMap();
    }
    final Map<Integer, List<LocalDate>> sessionsByStockexchange = new HashMap<>();
    for (IStockexchangeTradingDate session : tradingDaysPlusJpaRepository
        .getSessionsByStockexchange(new ArrayList<>(idsStockexchange), today.minusDays(SESSION_WINDOW_DAYS), today)) {
      sessionsByStockexchange.computeIfAbsent(session.getIdStockexchange(), _ -> new ArrayList<>())
          .add(session.getTradingDate());
    }
    return sessionsByStockexchange;
  }

  /**
   * Loads the worldwide calendar of the same window, used for instruments that belong to no exchange.
   *
   * @param today the current day
   * @return the possible trading days, newest first
   */
  @Transactional(readOnly = true)
  public List<LocalDate> loadGlobalSessions(final LocalDate today) {
    return tradingDaysPlusJpaRepository
        .findByTradingDateBetweenOrderByTradingDateDesc(today.minusDays(SESSION_WINDOW_DAYS), today).stream()
        .map(TradingDaysPlus::getTradingDate).toList();
  }

  /**
   * Counts the completed sessions that went by without the price being renewed.
   *
   * <p>
   * The current day itself is left out on purpose: a market that has not opened yet legitimately shows the price of the
   * previous session, and reporting that as a delay would paint every instrument as stale each morning.
   * </p>
   *
   * @param sessionsDesc sessions of the exchange, newest first, as returned by {@link #loadSessions}
   * @param priceDate    the day the displayed price belongs to, may be null
   * @param today        the current day
   * @return zero when the price is current, otherwise the number of missed sessions capped at
   *         {@link #MAX_STALE_TRADING_SESSIONS}; null when the age cannot be told
   */
  public Integer sessionsBehind(final List<LocalDate> sessionsDesc, final LocalDate priceDate, final LocalDate today) {
    if (priceDate == null || sessionsDesc == null || sessionsDesc.isEmpty()) {
      return null;
    }
    int behind = 0;
    for (LocalDate session : sessionsDesc) {
      if (!session.isBefore(today)) {
        continue;
      }
      if (!session.isAfter(priceDate)) {
        break;
      }
      if (++behind >= MAX_STALE_TRADING_SESSIONS) {
        break;
      }
    }
    return behind;
  }
}
