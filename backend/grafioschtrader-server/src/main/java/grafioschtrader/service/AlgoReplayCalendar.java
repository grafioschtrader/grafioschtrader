package grafioschtrader.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.repository.TradingDaysMinusJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;

/**
 * The trading calendar as a decision engine sees it: which days a run visits at all, which day an instrument last had a
 * close on, and which day an order decided today can actually be filled on.
 *
 * <p>
 * The three questions are separate on purpose. A run walks the application wide trading calendar, because a portfolio
 * has to be valued on every day the application considers a trading day. An instrument, however, follows the calendar
 * of its own exchange, and a crypto currency follows none at all - so a day the run visits may still be a day on which
 * a particular instrument neither has a price nor can be traded.
 * </p>
 *
 * <p>
 * The instrument side of this used to live privately in {@code AlgoMeanReversionEvaluationService}. It is shared here
 * because a historical replay has to answer exactly the same question for a past day that the live evaluation answers
 * for yesterday, and two implementations of a trading calendar would eventually disagree.
 * </p>
 */
@Service
public class AlgoReplayCalendar {

  /**
   * How far back a search for the last session may go. An instrument whose exchange has been closed for longer than
   * this is not "on holiday" any more; treating it as tradeable would invent a price for a market that is gone.
   */
  private static final int MAX_CLOSED_DAYS = 31;

  private final TradingDaysMinusJpaRepository holidays;
  private final TradingDaysPlusJpaRepository tradingDays;

  public AlgoReplayCalendar(TradingDaysMinusJpaRepository holidays, TradingDaysPlusJpaRepository tradingDays) {
    this.holidays = holidays;
    this.tradingDays = tradingDays;
  }

  /**
   * The days a run evaluates, which are the application wide trading days after the opening date up to and including
   * the end date. The opening date itself is excluded: its closing state is the immutable starting point of the
   * environment and no decision may be taken on it.
   *
   * @param openingDate the immutable opening date of the simulation environment
   * @param endDate     the last day to evaluate, which the caller has already checked to be in the past
   * @return the ordered days to evaluate, empty when the range contains no trading day
   */
  public List<LocalDate> runDates(LocalDate openingDate, LocalDate endDate) {
    return tradingDays.findByTradingDateBetweenOrderByTradingDate(openingDate.plusDays(1), endDate).stream()
        .map(TradingDaysPlus::getTradingDate).toList();
  }

  /**
   * The last day up to {@code through} on which the instrument had a session, which is the day its closing price and
   * therefore a decision about it belongs to.
   *
   * @param security the instrument
   * @param through  the day the caller wants a completed close for, inclusive
   * @return that day, which is {@code through} itself whenever the instrument traded on it
   * @throws IllegalArgumentException {@code MEAN_REVERSION_CALENDAR_REQUIRED} when the instrument has no exchange to
   *                                  read a calendar from, or its exchange has been closed for longer than a month;
   *                                  {@code MEAN_REVERSION_INACTIVE_INSTRUMENT} when the resulting day lies outside the
   *                                  instrument's active period
   */
  public LocalDate completedDate(Security security, LocalDate through) {
    boolean crypto = isCrypto(security);
    if (!crypto && security.getStockexchange() == null) {
      throw new IllegalArgumentException("MEAN_REVERSION_CALENDAR_REQUIRED");
    }
    Set<LocalDate> closed = closedDays(security, crypto, through.minusDays(MAX_CLOSED_DAYS), through);
    LocalDate date = through;
    while (!hasSession(date, crypto, closed)) {
      date = date.minusDays(1);
      if (date.isBefore(through.minusDays(MAX_CLOSED_DAYS))) {
        throw new IllegalArgumentException("MEAN_REVERSION_CALENDAR_REQUIRED");
      }
    }
    requireActive(security, date);
    return date;
  }

  /**
   * The day an order decided at the close of {@code decisionDate} is filled on: the first session of the instrument
   * strictly after that day, still inside the run.
   *
   * <p>
   * A decision is never executed at the price it was taken on. A run that ends before the instrument trades again
   * leaves the order unfilled, which is why this returns an empty result rather than a date beyond the horizon.
   * </p>
   *
   * @param security     the instrument to be traded
   * @param decisionDate the closing day the decision was taken on
   * @param horizonEnd   the last day of the run
   * @return the fill day, or empty when the instrument has no further session within the run
   */
  public Optional<LocalDate> nextEligibleClose(Security security, LocalDate decisionDate, LocalDate horizonEnd) {
    return nextEligibleClose(security, decisionDate, horizonEnd, null, null, null);
  }

  /**
   * The fill day of a replay order, using captured life dates when the run snapshot has them.
   *
   * @param from       captured {@code activeFromDate}, or null to read the entity
   * @param to         captured {@code activeToDate}, or null to read the entity
   * @param directBond captured direct-bond flag, or null to read the entity
   */
  public Optional<LocalDate> nextEligibleClose(Security security, LocalDate decisionDate, LocalDate horizonEnd,
      LocalDate from, LocalDate to, Boolean directBond) {
    if (!decisionDate.isBefore(horizonEnd)) {
      return Optional.empty();
    }
    boolean crypto = isCrypto(security);
    if (!crypto && security.getStockexchange() == null) {
      return Optional.empty();
    }
    Set<LocalDate> closed = closedDays(security, crypto, decisionDate, horizonEnd);
    for (LocalDate date = decisionDate.plusDays(1); !date.isAfter(horizonEnd); date = date.plusDays(1)) {
      if (hasSession(date, crypto, closed) && isMarketFillDate(security, date, from, to, directBond)) {
        return Optional.of(date);
      }
    }
    return Optional.empty();
  }

  private boolean isCrypto(Security security) {
    var asset = security.getAssetClass();
    return asset != null && IFeedConnector.AssetclassCategory.CRYPTOCURRENCY.matches(asset.getCategoryType(),
        asset.getSpecialInvestmentInstrument());
  }

  private Set<LocalDate> closedDays(Security security, boolean crypto, LocalDate from, LocalDate to) {
    Set<LocalDate> closed = new HashSet<>();
    if (!crypto) {
      holidays
          .findByTradingDaysMinusKey_IdStockexchangeAndTradingDaysMinusKey_TradingDateMinusBetween(
              security.getStockexchange().getIdStockexchange(), from, to)
          .forEach(day -> closed.add(day.getTradingDateMinus()));
    }
    return closed;
  }

  private boolean hasSession(LocalDate date, boolean crypto, Set<LocalDate> closed) {
    return crypto || date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY
        && !closed.contains(date);
  }

  /**
   * Whether {@code date} may carry a market fill. A directly held bond is not traded on {@code activeToDate}; that day
   * is the par redemption.
   */
  public boolean isMarketFillDate(Security security, LocalDate date, LocalDate from, LocalDate to, Boolean directBond) {
    LocalDate start = from != null ? from : security.getActiveFromDate();
    LocalDate end = to != null ? to : security.getActiveToDate();
    boolean bond = directBond != null ? directBond : isDirectBond(security);
    if (start != null && date.isBefore(start)) {
      return false;
    }
    if (end == null) {
      return true;
    }
    return bond ? date.isBefore(end) : !date.isAfter(end);
  }

  private boolean isActive(Security security, LocalDate date) {
    return isMarketFillDate(security, date, null, null, null);
  }

  private boolean isDirectBond(Security security) {
    return security.getAssetClass() != null && security.isBondDirectInvestment();
  }

  private void requireActive(Security security, LocalDate date) {
    if (!isActive(security, date)) {
      throw new IllegalArgumentException("MEAN_REVERSION_INACTIVE_INSTRUMENT");
    }
  }
}
